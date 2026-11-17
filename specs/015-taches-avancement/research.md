# Research : tâches d'une spécification, avancement suivi avec Jira et par les agents

Décisions de conception de la feature 015. Les numéros renvoient à `spec.md` (FR, SC) et au code
existant (chemins relatifs à `backend/src/main/java/fr/kawamind/`).

## R1 — Les tâches vivent dans spec-writer, à côté du rapport des sous-tâches existant

**Décision** : nouvelle table `spec_task` (une ligne par tâche, rattachée à la session), distincte de
l'index de connaissance. Le rapport des sous-tâches de la 002 (`application/knowledge/ReportSubtasks`,
panneau `sw-subtasks-panel`, rapprochement par plongements avec les cas d'usage) reste tel quel : il
répond à une autre question (« la story déjà découpée dans Jira couvre-t-elle les cas d'usage ? »)
et ne modifie jamais Jira. Le nouveau panneau « Tâches » se place au-dessus. Quand des tâches sont
suivies, le rapport reprend leurs clés comme sous-tâches connues, sans changement de code.

**Pourquoi** : les tâches portent un état propre à spec-writer (ordre, préalables, connexion qui la
tient, transition en attente, compte rendu) qu'aucun index Jira ne contient ; l'index est
reconstruit par la synchronisation et ne doit pas porter d'état métier.

**Écarté** : étendre `ticket_link` (V14) — table d'index, effacée à chaque réindexation ; fusionner
le rapport et les tâches — deux besoins distincts, et le rapport fonctionne sans tâches.

## R2 — Client Jira : quatre appels nouveaux, REST v2 Server/Data Center

**Décision** : le port `domain/port/IssueTracker` gagne :

| Méthode | Appel Jira (v2) |
|---|---|
| `subtaskType(endpoint, projectKey)` | `GET /rest/api/2/issue/createmeta/{project}/issuetypes`, premier type avec `subtask: true` (ordre de Jira) |
| `createSubtask(endpoint, projectKey, parentKey, typeId, summary, wikiDescription)` | `POST /rest/api/2/issue` avec `fields.parent.key` (réutilise `createIssue` et ses champs) |
| `transitions(endpoint, key)` | `GET /rest/api/2/issue/{key}/transitions` → `id`, `name`, `to.statusCategory.key` |
| `transition(endpoint, key, transitionId)` | `POST /rest/api/2/issue/{key}/transitions` |
| `addComment(endpoint, key, wiki)` | `POST /rest/api/2/issue/{key}/comment` |

La relecture des statuts réutilise `issuesByKeys` (50 clés par lecture, `statusCategory` déjà lu).
Les sous-tâches existantes de la story se lisent par `getIssue` étendu au champ `subtasks` (clé,
résumé, statut, catégorie).

**Pourquoi** : même client que le reste (`JiraRestClient`, proxy T139, CA, limitation de débit,
`IssueTrackerException.Kind`), même version d'API (R10 de la 002). `issueTypes()` continue
d'écarter les sous-tâches pour la création d'une story.

## R3 — Choix automatique de la transition par catégorie de statut (FR-015)

**Décision** : catégorie visée `indeterminate` pour « En cours », `done` pour « Terminée », `new`
pour « libérer » (FR-022). Parmi les transitions renvoyées par Jira pour la sous-tâche, spec-writer
prend la première (ordre de Jira) dont `to.statusCategory.key` est la catégorie visée. Si la
sous-tâche y est déjà, aucun appel. Si aucune ne convient, refus `NO_TRANSITION` avec le statut
actuel et la catégorie visée ; rien ne change (cas limite « workflow sans transition directe »).

**Pourquoi** : les noms de statuts varient d'un workflow à l'autre, les trois catégories sont fixes
dans Jira. Décision de l'utilisateur (16/11) : pas de réglage tant que ça suffit.

**Écarté** : enchaîner plusieurs transitions pour atteindre la catégorie — imprévisible et peut
déclencher des écrans de transition.

## R4 — Identité d'une connexion d'agent : le processus de la passerelle (FR-011a)

**Décision** : la passerelle (`mcp/McpBridge`) envoie à chaque appel l'en-tête
`X-Agent-Connection: <pid>-<début du processus en millisecondes>`, calculé une fois au démarrage par
`ProcessHandle.current()` (pid et `info().startInstant()`). Le serveur considère une connexion
**fermée** quand aucun processus local n'a ce pid avec ce début (`ProcessHandle.of(pid)`), ce qui
écarte la réutilisation d'un pid. Le contrôle est derrière un port `domain/port/AgentConnections`
(`alive(connection)`), implémenté dans `infrastructure/platform`, simulé dans les tests.

**Pourquoi** : la passerelle tourne sur le même poste que spec-writer (Host local exigé par
`AgentSecretFilter`) ; chaque conversation d'agent lance sa propre passerelle. Aucun battement de
cœur à tenir : la passerelle stdio n'a pas de minuterie et ne reçoit des appels qu'à la demande.
`UUID.randomUUID` est interdit hors plateforme par ArchUnit (`pasDHorlogeNiDAleaHorsPlateforme`), et
un identifiant aléatoire ne dirait pas si la connexion est fermée.

**Écarté** : délai d'expiration de la réservation (arbitraire, vole une tâche à un agent lent) ;
`clientInfo.name` seul (deux conversations Claude Code ont le même nom — clarification Q4).

Un appel sans `X-Agent-Connection` (ancienne passerelle) est refusé pour les outils de tâches avec
`BRIDGE_OUTDATED` (« relancez l'agent après la mise à jour de spec-writer »).

## R5 — Transition demandée par un agent : Jira d'abord, sinon « à synchroniser » (FR-013, FR-013a)

**Décision** : `StartTask` / `CompleteTask` appliquent la transition (et le commentaire de compte
rendu) à Jira, puis enregistrent l'état. Si Jira est **injoignable** (`Kind.UNREACHABLE`,
`TIMEOUT`, 5xx) : l'état est enregistré, `pending_target` (et `pending_comment`) sont posés, la
réponse porte `jiraSync: "PENDING"` et un message. Si Jira **refuse** (`NO_TRANSITION`, 4xx) :
rien ne change, erreur. Les transitions en attente sont rejouées, dans l'ordre des tâches, à
l'ouverture de la spécification (`GET .../tasks`), à chaque appel d'un agent sur la feature, et par
`POST .../tasks/sync` (bouton « Synchroniser »). Au rejeu, si Jira est déjà dans la catégorie visée,
l'attente est levée sans appel (cas limite « tâche à synchroniser modifiée dans Jira ») ; le
commentaire en attente est envoyé une seule fois.

**Pourquoi** : clarification Q1 (option C) ; ne bloque pas l'agent quand le VPN tombe.

## R6 — Relecture des statuts depuis Jira (FR-019, FR-021)

**Décision** : relecture à la demande, synchrone, bornée : `issuesByKeys` sur les clés des tâches
suivies, à l'ouverture du panneau, à `get_feature`, à `next_task`. Correspondance :
`new` → À faire, `indeterminate` → En cours, `done` → Terminée ; clé absente de la réponse →
`MISSING` (« Introuvable dans Jira », hors avancement, ne peut pas être prise). Une tâche passée
« Terminée » dans Jira perd sa connexion. Si la relecture échoue : statuts connus, `lastReadAt`
inchangé, `readProblem` dans la réponse. Pas de tâche de fond (hypothèse de la spec).

## R7 — Statut de la spécification calculé d'après les tâches (FR-020, FR-020a)

**Décision** : fonction pure du domaine `TaskProgress.status(tasks)` sur les tâches hors `MISSING` :
aucune commencée → `READY` ; au moins une en cours ou terminée sans que toutes le soient →
`IN_PROGRESS` ; toutes terminées → `IMPLEMENTED`. Appliquée après chaque changement d'une tâche
(agent, relecture, libération), sauf si le suivi est **suspendu**. Nouveau `Actor.TASKS` dans
`ImplementationStatus.allows` (toutes transitions entre READY, IN_PROGRESS, IMPLEMENTED), et
passage de/vers IMPLEMENTED sans confirmation de déverrouillage quand l'acteur est TASKS.

Côté utilisateur, `ChangeStatus` refuse `IN_PROGRESS` et `IMPLEMENTED` quand la session a des
tâches (`STATUS_FOLLOWS_TASKS`). Repasser en `DRAFT` ou `READY` pose `session.task_tracking =
'SUSPENDED'` : `next_task`, `start_task`, `complete_task` répondent `TRACKING_SUSPENDED` ;
`get_feature` (READY seulement, DRAFT est caché) montre les tâches. « Reprendre le suivi »
(`POST .../tasks/resume`) remet `ACTIVE` et recalcule le statut.

L'outil `set_feature_status` répond `STATUS_FOLLOWS_TASKS` sur une feature qui a des tâches.

**Pourquoi** : clarifications Q3 et décision (E) du 16/11.

## R8 — Proposition par le modèle : opération `propose-tasks` (FR-002, FR-003, FR-026)

**Décision** : nouvel AI Service `infrastructure/llm/ai/TasksAi` (`TaskProposalDraft propose(String
data, ChatRequestParameters format)`, `@SystemMessage(fromResource = "prompts/propose-tasks.txt")`,
`@OutputGuardrails(TasksGuardrail.class)`), exposé au domaine par le port `domain/port/TaskLlm`.
Données transmises, entre balises : `<specification>` (titre, résumé, cas d'usage avec code, titre
et objectif), `<modules>` (modules du dernier périmètre de la session — `session_perimeter`, 013 —
avec qualification IMPACTED / TO_CHECK et justification), `<taches-existantes>` (titres des tâches
déjà suivies et sous-tâches présentes dans Jira) et `<langue>`. Rien d'autre (ni documents, ni
connaissance) : le découpage porte sur la spécification.

Le garde-fou (`LlmOutputs::tasks`) refuse : moins de 3 ou plus de 8 tâches (au plus 8 moins le
nombre de tâches existantes pour une proposition complémentaire, et au moins 1), titre vide ou de
plus de 120 caractères, résultat vide ou de plus de 300, base inconnue (un `MODULE` doit nommer un
module de `<modules>`, un `USE_CASE` un code de cas d'usage), préalable qui ne désigne pas une
tâche antérieure de la proposition. Après `max-retries` (3), erreur `TASKS_UNAVAILABLE` et saisie
manuelle possible.

La consigne déclare `[skill:decoupage-taches]` : `prompts/skills/decoupage-taches.md`, rédigée
pour spec-writer, inspirée des schémas de découpage de story (étapes du parcours, variantes de
règle ou de données, dépendances) et du découpage en tâches vérifiables ordonnées par dépendances,
sans en recopier le texte (licences : deanpeters non standard, addyosmani MIT).

**Pourquoi** : mécanisme de la 014 (AI Services, garde-fous, consignes réutilisables), même
séparation consignes/données, rejeu E2E par opération.

## R9 — Énoncé de story dans la spécification (FR-023 à FR-025)

**Décision** : `SpecificationContent` gagne `story` (`UserStory(role, capability, benefit,
List<String> assumed)` où `assumed` liste les éléments supposés parmi `ROLE`, `CAPABILITY`,
`BENEFIT`) et `splitProposal` (`List<String>` : titres des stories proposées, vide si la story
n'est pas trop large). Les deux sont facultatifs à la lecture (versions antérieures, contenu JSON),
demandés par `generate-specification`, `edit-specification`, `merge-conflict` et
`structure-imported-text`. `MarkdownRenderer` rend l'énoncé en tête (« En tant que … je veux …
afin de … », élément supposé suivi de « (hypothèse) »), puis, s'il y a lieu, une rubrique
« Découpage proposé ». L'export et Jira reprennent le Markdown (aucun rendu de plus).

Les consignes déclarent `[skill:redaction-story]` (`prompts/skills/redaction-story.md` : énoncé
rôle/capacité/bénéfice, critères INVEST, signal de story trop large), rédigée pour spec-writer sans
recopie (`phuryn/pm-skills` MIT, `deanpeters` non standard).

Le garde-fou accepte une réponse sans `story` (rejeu des anciennes fixtures) ; l'évaluation mesure
sa présence et sa qualité (SC-007).

**Écarté** : rendre `story` obligatoire dans le garde-fou — toutes les fixtures E2E de génération
seraient à refaire d'un coup ; la mesure par l'évaluation suffit à tenir SC-007.

## R10 — Validation : création des sous-tâches sans doublon (FR-005 à FR-008)

**Décision** : à la validation, la proposition devient des lignes `spec_task` (`creation =
NOT_CREATED`), puis spec-writer crée les sous-tâches une par une, dans l'ordre, et passe chaque ligne
à `CREATED` avec sa clé. Avant chaque création (et à chaque relance), il relit les sous-tâches de
la story : une sous-tâche de même résumé exact que la tâche est reprise au lieu d'être créée (cas
d'un arrêt entre la création et l'enregistrement). Erreur au milieu : les lignes restantes gardent
`NOT_CREATED`, l'écran propose « Créer les tâches restantes ». Aucune suppression dans Jira : une
tâche `CREATED` ne peut pas être retirée ; une tâche `NOT_CREATED` peut l'être.

Préalables : seulement vers des tâches de la même spécification, sans lien Jira.

Les sous-tâches présentes sous la story au moment de la proposition sont importées comme tâches
(`origin = JIRA`, base vide), avant les tâches proposées.

## R11 — Journal des agents (FR-014)

**Décision** : V26 reconstruit `agent_access_log` (contrainte CHECK de SQLite) pour ajouter les
opérations `NEXT_TASK`, `START_TASK`, `COMPLETE_TASK`, `RELEASE_TASK` (libération par
l'utilisateur d'une tâche tenue par un agent) et la colonne `task_key`. `NEXT_TASK` est
journalisé comme `GET_FEATURE` (lecture), les deux autres avec l'ancien et le nouveau statut de la
tâche dans `task_key` / colonnes existantes de statut laissées nulles. Le compte rendu n'est jamais
journalisé ; seule sa longueur est bornée.

## R12 — Règle FR-029 de la 002

La 002 dit que la passerelle ne peut déclencher aucun appel à Jira. La passerelle elle-même reste
sans accès à Jira ; c'est spec-writer qui fait les transitions à la demande d'un agent, sur le
compte et le jeton de l'utilisateur. `specs/002-projets-contexte-mcp/contracts/mcp-tools.md` et la
spec 002 sont annotés (« remplacé en partie par la 015 »).

## R13 — Évaluation réelle (FR-027, SC-001, SC-002, SC-007, SC-008)

**Décision** : nouvelle famille `TaskRuns` (cas `backend/src/test/resources/eval/tasks/cases.json`,
une spécification et, pour certains cas, un périmètre), branchée dans `EvalRuns` comme
`InjectionRuns` (014). Métriques ajoutées à `baseline.json` :

| Métrique | Mesure | Minimum |
|---|---|---|
| `taskBounds` | part des propositions de 3 à 8 tâches | 1.0 |
| `taskBasisExact` | part des tâches dont la base existe dans la spécification | 0.9 |
| `taskModuleCoverage` | part des modules IMPACTED couverts par au moins une tâche | 0.9 |
| `taskVerifiable` | part des tâches dont le résultat est jugé vérifiable (juge LLM, `eval/prompts/judge-task.txt`) | 0.9 |
| `storyStatement` | part des spécifications générées dont l'énoncé a rôle, capacité et bénéfice non vides, chacun présent dans le besoin ou les documents, ou marqué supposé (juge) | 0.9 |

`storyStatement` est calculée dans `SpecificationStructureEval` sur le golden set existant.
Les consignes modifiées (`generate-specification`, etc.) changent l'empreinte : l'évaluation
complète est relancée et tous les indicateurs existants restent au-dessus de leur base (SC-008).
