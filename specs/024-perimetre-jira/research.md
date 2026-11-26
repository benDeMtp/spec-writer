# Research: Périmètre Jira de spec-writer (024)

## R1 — Où garder le label du projet

- **Decision** : une table `project_jira_label` (V36) : `project_id` (clé, cascade), `label`
  (1 à 255 caractères, sans espace), `updated_at`. Pas de ligne : pas de label (FR-006). Un port
  `JiraLabelRepository`, un cas d'usage `ManageJiraLabel`, une ressource
  `/api/projects/{id}/jira-label`. La saisie est faite dans la rubrique « Suivi des tâches dans
  Jira » des réglages du projet, au-dessus du statut d'arrivée.
- **Rationale** : même forme que les réglages par projet de la 023 (`project_task_workflow`) ; le
  record `Project` reste inchangé.
- **Alternatives considered** : une colonne sur `project_task_workflow` (lie deux réglages
  indépendants, écarté) ; un réglage global (écarté par la réponse du 26/11).

## R2 — Labels des tickets

- **Decision** : une table `ticket_label (project_id, ticket_key, label)`, clé sur les trois, index
  sur `(project_id, label)`. Elle est alimentée par trois chemins :
  - la synchronisation de Jira lit le champ `labels` (ajouté à `TRACKED_FIELDS`) et remplace les
    labels de chaque ticket relu, comme les liens (`ticket_link`) ;
  - la relecture des tâches (`TaskSync`, même recherche par clés) les remplace pour les sous-tâches
    suivies et la story ;
  - spec-writer ajoute le label quand il le pose (création, reprise), sans attendre la
    synchronisation.

  Elle suit les retraits de l'index (`retain`) et la suppression d'un ticket (R8).
- **Rationale** : une story tout juste créée n'est pas encore dans l'index ; le périmètre du MCP doit
  pourtant la reconnaître. `KnowledgeItem` est construit en de nombreux endroits : un champ de plus
  y serait invasif pour une donnée qui ne sert ni à la recherche ni au modèle.
- **Alternatives considered** : un champ `labels` sur `knowledge_item` (voir ci-dessus) ; relire le
  ticket dans Jira à chaque appel MCP (un appel par feature listée, écarté) ; un filtre JQL
  `labels = x` à la synchronisation (l'index ne contiendrait plus les autres tickets, dont la vue a
  besoin quand le filtre est levé, écarté).

## R3 — Poser le label

- **Decision** :
  - **Création** (story par `CreateJiraTicket`, sous-tâche par `TaskDesk`) : le label est ajouté à
    `fields.labels`, après les labels saisis dans les champs obligatoires (`extraFields`), sans
    doublon.
  - **Reprise** (`ImportJiraIssue`, sous-tâche existante reprise par `TaskDesk`) : si le ticket
    n'a pas le label (lu dans `ticket_label` ou dans le ticket relu), `IssueTracker.addLabel`
    envoie `PUT /rest/api/2/issue/{key}` avec `{"update":{"labels":[{"add":"<label>"}]}}`, qui
    garde les autres labels.
  - **Refus** : la création échoue-t-elle à cause du label (champ absent de l'écran de création) ?
    Jira répond 400 avec `errors.labels`. Le ticket est alors recréé sans le label, puis le label
    est ajouté par `addLabel` ; si l'ajout est refusé aussi, la création réussit et le message le
    dit (FR-005). À la reprise, un refus donne un message, rien d'autre.
- **Rationale** : `update.add` est l'opération de Jira Server / Data Center qui n'écrase pas les
  labels existants. La création en un seul envoi évite un ticket visible sans label.
- **Alternatives considered** : toujours créer puis ajouter (deux appels à chaque création,
  écarté) ; `fields.labels` à la reprise (écrase les labels de l'équipe, écarté).

## R4 — Validation du label

- **Decision** : `JiraLabel.of(String)` dans le domaine : `strip`, 1 à 255 caractères, aucun
  caractère blanc (Jira refuse les espaces dans un label). Comparaison exacte (sensible à la casse,
  comme Jira).
- **Rationale** : spec, FR-002 et Edge Cases.

## R5 — Périmètre du MCP

- **Decision** : `JiraScope` (application/agent), à partir du label du projet, du ticket lié et de
  `ticket_label` :
  - `feature(session)` : vrai sans label réglé, sans ticket lié, ou si la story porte le label ;
  - `task(projectId, task)` : vrai sans label réglé, ou si la sous-tâche porte le label.

  `ListFeatures` écarte les features hors périmètre ; `GetFeature`, `AgentChangeStatus` et
  `AgentTasks` (`forFeature`, `next`, `start`, `complete`) refusent une feature hors périmètre avec
  `NOT_IN_JIRA_SCOPE` (« La feature X est liée à PROJ-12, qui ne porte pas le label spec-writer :
  elle n'est pas suivie par spec-writer dans Jira. »). Les tâches hors périmètre ne sont ni
  proposées par `next_task`, ni prises, ni terminées (même code, message sur la tâche) ; elles
  restent listées dans `get_feature` avec `inScope: false`.
- **Rationale** : une seule règle, testée seule. `get_feature` garde les tâches hors périmètre en
  lecture pour que l'agent comprenne l'avancement, sans pouvoir les prendre.
- **Alternatives considered** : masquer complètement les tâches hors périmètre (l'avancement
  « 2 sur 3 » serait inexplicable, écarté).

## R6 — Filtres de la vue Jira

- **Decision** : `ListProjectIssues.run(projectId, sort, filters, startAt, size)` avec
  `IssueFilters(boolean labelled, boolean done)`.
  - `labelled` vrai et un label réglé : `exists (select 1 from ticket_label …)` sur le ticket de
    premier niveau. Sans label réglé, le filtre est ignoré et la réponse dit `label: null`.
  - `done` faux : `coalesce(k.status_category, '') <> 'done'` sur le ticket de premier niveau.
  - Les sous-tâches d'un ticket affiché ne sont pas filtrées (FR-009). Le compte suit les mêmes
    conditions.

  L'API prend `labelled` et `done` en paramètres (défauts `true` et `false`) et rend le label et
  les filtres appliqués. Le front garde le choix en `sessionStorage` (clé par projet) : il est
  gardé pendant l'ouverture de l'outil dans l'onglet, et revient aux défauts au lancement suivant.
- **Rationale** : filtres en SQL, donc le pager et le tri restent justes (017, R1).
- **Alternatives considered** : filtrer dans le front (pager faux, écarté) ; garder le choix en
  base (l'utilisateur veut les défauts à chaque lancement, écarté).

## R7 — Sous-tâches hors recherche

- **Decision** : `SqliteKnowledgeIndex.search` écarte les tickets qui ont un lien `PARENT` dans
  `ticket_link`, dans la recherche par mots (FTS) comme par le sens (vecteurs). Toutes les
  recherches passent par cette méthode : connaissance de l'utilisateur (`KnowledgeQueries`), agent
  (`search_knowledge`, `ConversationSearch`), rédaction (`SelectKnowledge`), tickets proches
  (`FindRelatedIssues`). Les sous-tâches gardent texte et vecteur : `ReportSubtasks` les rattache
  aux cas d'usage par le sens, et lit leurs vecteurs directement.
- **Mise en conformité (FR-017)** : aucune migration de données ; le filtre s'applique dès la mise à
  jour à l'index existant. Un ticket dont le parent n'est pas encore lu (premier index) peut être
  cherché jusqu'à la lecture de ses liens : c'est le cas déjà admis par la 022.
- **Évaluation** : `SelectKnowledge` est hors de l'empreinte (`ci/check-eval.sh` couvre les
  prompts, `infrastructure/llm`, le golden set et `eval`). Le jeu de référence de l'évaluation
  ne contient pas de sous-tâche cherchée : la mesure n'est pas affectée.
- **Alternatives considered** : ne plus calculer de vecteur pour les sous-tâches (casse
  `ReportSubtasks`, écarté) ; une colonne `searchable` (redondante avec le lien, écarté).

## R8 — Tickets supprimés

- **Decision** :
  - **Détection** : chaque synchronisation de Jira, incrémentale comme complète, appelle
    `listKeys` (déjà utilisé par « Tout réindexer »), puis retire les tickets de l'index absents de
    la liste (`index.retain`, `links.retain`, `labels.retain`). Les clés retirées sont calculées
    avant le retrait.
  - **Garde-fou (FR-021)** : rien n'est retiré si `listKeys` échoue, si le projet n'est plus visible
    (erreur `NOT_FOUND` sur le projet), ou si la liste est vide alors que l'index contient des
    tickets ; une erreur d'indexation `JIRA` est signalée.
  - **Suites** (`ForgetDeletedTickets`, application/knowledge) : pour chaque clé retirée,
    - une tâche suivie dont c'est la sous-tâche est supprimée de la feature ; l'avancement et le
      statut d'implémentation sont recalculés par `TaskProgress` ;
    - un lien de spécification vers cette clé est supprimé, et `lost_jira_link` garde la clé et la
      date ; la page de la spécification affiche « La story PROJ-12 a été supprimée dans Jira »,
      avec « Envoyer vers Jira » de nouveau possible. Un nouveau lien efface la trace.
  - **Relecture des tâches** : `TaskSync` garde le comportement actuel (`MISSING`, hors de
    l'avancement) ; la suppression elle-même passe par la synchronisation, qui a le garde-fou.
  - **Agent** : une tâche supprimée n'existe plus ; `complete_task` sur sa clé reçoit `NOT_FOUND`
    avec « La tâche PROJ-302 n'est plus une tâche de cette feature : elle a peut-être été supprimée
    dans Jira. »
- **Rationale** : un seul endroit décide d'une suppression, avec un garde-fou contre la perte d'un
  projet entier (jeton changé, droits retirés).
- **Alternatives considered** : supprimer dès la relecture d'une tâche introuvable (un droit retiré
  ferait perdre les tâches, écarté) ; garder les tâches `MISSING` indéfiniment (demande du 26/11,
  écarté).

## R9 — Réponse et erreurs

- **Decision** : nouveau code d'erreur `NOT_IN_JIRA_SCOPE` (HTTP 409). `TaskInfo` gagne `inScope`
  (booléen) ; `ListFeatures.Feature` ne change pas (les features hors périmètre n'y sont pas). Le
  message d'un label refusé est ajouté aux réponses existantes de la création (`warning` de la
  création du ticket, message de la validation des tâches).
- **Rationale** : aucun champ MCP retiré (022, FR-014).
