# Research: Statut Jira d'une tâche implémentée, réglé par projet (023)

## R1 — Où garder le réglage

- **Decision** : une table `project_task_workflow` (V35), une ligne par projet au plus :
  `project_id` (clé, cascade à la suppression du projet), `arrival_status` (nom affiché par Jira,
  1 à 100 caractères), `finished_statuses` (tableau JSON de noms, 50 au plus), `updated_at`.
  Pas de ligne : pas de réglage (FR-005). Un port `TaskWorkflowRepository` (find, save, delete), un
  cas d'usage `ManageTaskWorkflow` (lire, enregistrer, retirer, statuts connus), une ressource
  `/api/projects/{id}/task-workflow`.
- **Rationale** : même forme que les autres réglages par projet (fiabilité V27, synchro V28), qui
  ont leur table et leur ressource sous `/api/projects/{id}/…`. Le record `Project` reste inchangé.
- **Alternatives considered** : des colonnes sur `project` (étend `Project` et tous ses appelants,
  écarté) ; un réglage global de l'application (un projet par workflow Jira, écarté).

## R2 — Statuts connus

- **Decision** : `select status, status_category from knowledge_item where project_id = ? and
  kind = 'TICKET' and chunk = 0 and status is not null group by …`. Le résultat est dédoublonné par
  nom normalisé (sans accents, minuscules) et classé par catégorie (`new`, `indeterminate`,
  `done`), puis par nom. Il est exposé par `GET /api/projects/{id}/task-workflow` avec le réglage.
  Un statut réglé mais absent de l'index reste affiché (« plus vu dans l'index »).
- **Rationale** : aucun appel à Jira. L'index d'AURORA donne Créée, Prête, En Cours, Réalisée, A
  valider, En cours de test, Fermée et Annulée. Les statuts jamais vus (Testée, Déployable…)
  apparaîtront dès qu'un ticket les portera ; c'est une limite assumée de l'option A.
- **Alternatives considered** : lire `/rest/api/2/project/{key}/statuses` (exhaustif, mais un appel
  Jira et un consentement de plus ; gardé pour plus tard si l'index ne suffit pas).

## R3 — Comparaison des noms

- **Decision** : `StatusName.same(a, b)` (domaine) compare après NFD, suppression des diacritiques,
  minuscules et `strip`. « En Cours » = « en cours » ; « Réalisée » = « Realisee ».
- **Rationale** : spec (Assumptions). Jira affiche des noms à la casse variable selon l'endroit.
- **Alternatives considered** : l'identifiant de statut Jira (absent de l'index, écarté).

## R4 — Fin d'une tâche vers le statut d'arrivée

- **Decision** : `TaskJira.moveTo(endpoint, key, Target)`, où `Target` est un type scellé du
  domaine (`Category(StatusCategory)` ou `Status(String name, Set<String> finished)`).
  - Pour `Status` : relire le statut courant ; s'il est déjà fini, renvoyer `ALREADY` (FR-008) ;
    sinon, retenir parmi `transitions()` celles dont `toStatus` est le statut d'arrivée (R3), sans
    annulation ; en prendre la première ; aucune : `NO_TRANSITION`, avec un message qui nomme le
    statut courant et le statut visé (FR-007).
  - Pour `Category`, rien ne change.

  `AgentTasks.complete` choisit la cible d'après le réglage du projet de la feature.
- **Rationale** : le nom du statut d'arrivée de chaque transition est déjà lu (`Transition.toStatus`).
  Une seule transition directe, comme aujourd'hui (015, R3).
- **Alternatives considered** : un chemin de plusieurs transitions (interdit par la spec).

## R5 — Rejeu « à synchroniser »

- **Decision** : `pending_target` reste `DONE`. Au rejeu, `TaskSync.replay` calcule la cible avec le
  réglage **actuel** du projet. La spec (FR-009) est précisée : la cible est le statut d'arrivée du
  projet au moment du rejeu.
- **Rationale** : un changement de réglage pendant une coupure de Jira est improbable ; le stocker
  par tâche ajouterait une colonne pour ce seul cas.
- **Alternatives considered** : une colonne `pending_status` (écarté, voir ci-dessus).

## R6 — Statuts finis à la relecture

- **Decision** : `TaskSync.reread` (et `TrackingActions` s'il relit) calcule le statut d'une tâche
  par une fonction du domaine `TaskWorkflow.statusOf(name, category)`.
  - Un nom fini (le statut d'arrivée, un statut coché, ou la catégorie `done`) donne `DONE`.
  - Sinon, la catégorie donne le statut, comme aujourd'hui.

  L'avancement (`TaskProgress`), `next_task` et le statut de la spécification s'appuient déjà sur
  `TaskStatus.DONE` : rien d'autre à changer (FR-012). `jira_status_name` garde le vrai nom (FR-013).
- **Rationale** : un seul point de vérité pour « fini ». Sans réglage, `TaskWorkflow.NONE` rend la
  catégorie, ce qui donne le comportement d'aujourd'hui (FR-005).

## R7 — La story suit ses tâches

- **Decision** : un service d'application `StoryFollow`, appelé par `AgentTasks` et
  `AgentChangeStatus`.
  - **Démarrage** : après la prise d'une tâche, si le ticket lié est dans la catégorie `new`, le
    passer à la catégorie « en cours » (`Target.Category(IN_PROGRESS)`), en écartant une transition
    dont le statut d'arrivée est un statut fini (spec, Edge Cases).
  - **Fin** : après `complete` et `applyStatus`, si toutes les tâches créées sont `DONE` et que le
    projet est réglé, passer la story au statut d'arrivée (`Target.Status`) ; si elle est déjà dans
    un statut fini, rien n'est envoyé.
  - **Feature sans tâches** : `AgentChangeStatus` vers `IMPLEMENTED`, projet réglé, ticket lié :
    même règle de fin.
  - **Échec** (pas de transition, refus, Jira injoignable) : la tâche ou la feature avance quand
    même. Un message est ajouté à la réponse de l'agent (`story`), et une entrée `WARN` est écrite
    dans le journal (visible dans la page Journal), avec la clé de la story et le motif, sans
    contenu. Le déplacement n'est pas rejoué (FR-017).
- **Rationale** : la story est déplacée par le même `TaskJira` (même refus des annulations, mêmes
  transitions directes). Le journal est le canal existant pour prévenir l'utilisateur d'un
  incident non bloquant, et l'agent le dit dans sa conversation.
- **Alternatives considered** : rejouer le déplacement de la story comme une transition « à
  synchroniser » (nouvel état sur la session, écarté par la spec) ; une notification du navigateur
  (017, réservée aux sources).

## R8 — Réponse à l'agent

- **Decision** : `TaskAnswer` (start et complete) et la réponse de `set_feature_status` gagnent un
  champ `story` : `{key, status: "MOVED" | "ALREADY" | "SKIPPED" | "FAILED", to, message}`. Il est
  nul quand la story n'est pas concernée. `SKIPPED` couvre le projet sans réglage, à la fin.
- **Rationale** : l'agent sait ce qui est arrivé à la story et peut le dire ; FR-014 de la 022
  (aucun champ retiré) reste tenu.
