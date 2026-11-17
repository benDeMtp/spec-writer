---
description: "Tâches de la feature 015 : tâches d'une spécification, avancement suivi avec Jira et par les agents"
---

# Tasks: Tâches d'une spécification, avancement suivi avec Jira et par les agents

**Input**: `specs/015-taches-avancement/` (plan.md, spec.md, research.md, data-model.md,
contracts/mcp-tools.md, contracts/rest-api.md, contracts/llm-propose-tasks.md, quickstart.md)

**Prérequis hors de cette liste** : la PR de l'argument `--project` de la passerelle MCP
(ségrégation par projet) est livrée avant l'implémentation (spec, Assumptions).

**Tests** : obligatoires (constitution III, TDD). Chaque test est écrit et vu échouer avant le code.
JaCoCo ne compte pas les `@QuarkusTest` : `domain` et `application` sont couverts par des tests
unitaires avec des doubles de `IssueTracker`, `TaskLlm`, `AgentConnections` et `ClockPort`. Les IT
qui appellent WireMock (`Jira*IT`) et les règles ArchUnit se lancent sous Maven, pas en dev mode.

**Évaluation réelle** : elle appelle le vrai Copilot (environ 45 minutes) ; elle se lance après
T088, puis `ci/check-eval.sh`. Les consignes modifiées changent l'empreinte : `last-run.json` est
commité avec le passage.

**Organisation** : US1 (découpage) est le MVP ; US2 (agents) s'appuie sur des tâches validées ;
US3 (avancement) s'appuie sur les transitions ; US4 (énoncé de story) est indépendante.

## Format: `[ID] [P?] [Story] Description`

- **[P]** : parallélisable (fichiers différents, aucune dépendance sur une tâche non finie).
- Abréviations des chemins :
  - `BM/` : `backend/src/main/java/fr/kawamind/`
  - `BT/` : `backend/src/test/java/fr/kawamind/`
  - `BR/` : `backend/src/main/resources/`
  - `BTR/` : `backend/src/test/resources/`
  - `FE/` : `frontend/src/app/`

## Gates avant chaque commit (CLAUDE.md)

1. arrêter le dev mode, `rm -f backend/target/jacoco.exec` ;
2. `rm -rf backend/target/classes backend/target/test-classes` ;
3. `./mvnw -B verify -DskipDependencyCheck` ;
4. `./mvnw -B package -DskipTests` ;
5. `npm --prefix e2e test` (JDK 25 en tête du `PATH`).

Commits en Conventional Commits, en français, fichiers stagés explicitement, sans
`.specify/feature.json`.

---

## Phase 1: Setup (migration et énumérations partagées)

**Purpose**: schéma V26 et valeurs d'énumération dont toutes les histoires dépendent.

- [X] T001 Écrire le test de migration `BT/infrastructure/persistence/MigrationV26IT.java` : sur une base migrée, `spec_task`, `spec_task_dependency`, `spec_task_proposal` existent avec leurs contraintes ; `session.task_tracking` vaut `'ACTIVE'` par défaut ; `agent_access_log` garde ses lignes, accepte `NEXT_TASK`, `START_TASK`, `COMPLETE_TASK`, `RELEASE_TASK` et a la colonne `task_key`.
- [X] T002 Écrire `BR/db/migration/V26__spec_tasks.sql` (data-model.md) :
  - `spec_task` : `id` TEXT PK ; `session_id` TEXT NOT NULL FK `session(id)` ON DELETE CASCADE ; `position` INTEGER NOT NULL, UNIQUE (`session_id`, `position`) ; `title` TEXT NOT NULL « 1 à 120 caractères » ; `outcome` TEXT NOT NULL « 1 à 300 caractères (vide permis seulement pour `origin = JIRA`) » ; `basis_kind` CHECK IN (`MODULE`, `USE_CASE`, `NONE`) ; `basis_ref` TEXT « NULL si `NONE` » ; `origin` CHECK IN (`PROPOSED`, `MANUAL`, `JIRA`) ; `creation` CHECK IN (`NOT_CREATED`, `CREATED`) ; `jira_key` TEXT UNIQUE, « NOT NULL si `creation = CREATED` » ; `status` CHECK IN (`TODO`, `IN_PROGRESS`, `DONE`, `MISSING`) ; `jira_status_name`, `holder_connection`, `holder_agent`, `held_since` TEXT ; `pending_target` CHECK IN (`TODO`, `IN_PROGRESS`, `DONE`) ; `pending_comment` INTEGER NOT NULL DEFAULT 0 ; `completion_report` TEXT « 2 000 caractères au plus » ; `last_read_at` TEXT ;
  - `spec_task_dependency` (`task_id`, `depends_on_id`, FK `spec_task(id)` ON DELETE CASCADE, PK des deux) ;
  - `spec_task_proposal` (`session_id` TEXT PK FK `session(id)` ON DELETE CASCADE, `content` TEXT NOT NULL, `created_at` TEXT NOT NULL) ;
  - `ALTER TABLE session ADD COLUMN task_tracking TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (task_tracking IN ('ACTIVE','SUSPENDED'))` ;
  - reconstruction de `agent_access_log` (`_new`, copie, suppression, renommage, index `agent_access_log_at` recréé) avec le CHECK `operation` étendu et `task_key TEXT`.
- [X] T003 [P] Ajouter `NEXT_TASK`, `START_TASK`, `COMPLETE_TASK`, `RELEASE_TASK` à `BM/domain/agent/AgentOperation.java` et `taskKey` (nullable) à `BM/domain/agent/AgentAccess.java` ; persistance de `task_key` dans `BM/infrastructure/persistence/agent/` ; tests existants du journal adaptés.
- [X] T004 [P] Ajouter `Actor.TASKS` à `BM/domain/session/ImplementationStatus.java` : toutes transitions entre `READY`, `IN_PROGRESS`, `IMPLEMENTED`, sans confirmation de déverrouillage (R7) ; test d'abord dans `BT/domain/session/ImplementationStatusTest.java`.

**Checkpoint** : `verify` vert, migration appliquée sur une base de test. Commit `feat(db): migration V26, tâches et journal des agents étendu`.

---

## Phase 2: Foundational (domaine des tâches, dépôt, Jira, connexions)

**Purpose**: briques partagées par US1, US2 et US3. Aucune histoire ne commence avant.

### Domaine

- [X] T005 [P] Tests puis records du domaine dans `BM/domain/task/` : `TaskStatus { TODO, IN_PROGRESS, DONE, MISSING }`, `Basis(kind, ref)` (`kind` ∈ `MODULE`, `USE_CASE`, `NONE`, `ref` nul si `NONE`), `Holder(connection, agentName, since)` (`agentName` « 80 caractères, sans caractère de contrôle »), `Pending(target, comment)`, `TaskOrigin`, `TaskCreation`, `SpecTask` (validation : titre 1 à 120, résultat 1 à 300 sauf origine `JIRA`, compte rendu ≤ 2 000, clé requise si `CREATED`) ; tests `BT/domain/task/SpecTaskTest.java`.
- [X] T006 [P] `BM/domain/task/StatusCategory.java` (`NEW`, `IN_PROGRESS`, `DONE` ↔ clés Jira `new`, `indeterminate`, `done`, correspondance vers `TaskStatus`) et test `BT/domain/task/StatusCategoryTest.java`.

### Ports

- [X] T007 [P] Port `BM/domain/port/TaskRepository.java` : tâches d'une session (ordonnées), tâche par clé Jira, enregistrement d'une liste, mise à jour d'une tâche, préalables, proposition en cours (lire, écrire, supprimer), `task_tracking` (lire, écrire).
- [X] T008 [P] Port `BM/domain/port/AgentConnections.java` : `boolean alive(String connection)` (R4).
- [X] T009 Étendre `BM/domain/port/IssueTracker.java` (R2) : `subtaskType(endpoint, projectKey)`, `createSubtask(endpoint, projectKey, parentKey, typeId, summary, wiki)`, `transitions(endpoint, key)` → liste `Transition(id, name, toCategory)`, `transition(endpoint, key, transitionId)`, `addComment(endpoint, key, wiki)`, et sous-tâches d'un ticket (`subtasks(endpoint, parentKey)` → clé, résumé, statut, catégorie) ; doubles de test existants mis à jour.

### Infrastructure

- [X] T010 Test `BT/infrastructure/jira/JiraTasksIT.java` (WireMock, sous Maven) : type sous-tâche lu par `createmeta/{project}/issuetypes` (premier `subtask: true`) ; `POST /rest/api/2/issue` avec `fields.parent.key` ; `GET`/`POST …/transitions` ; `POST …/comment` ; `fields.subtasks` lu sur la story ; refus 400/403 → `IssueTrackerException` de genre refus, panne → `UNREACHABLE`.
- [X] T011 Implémenter T009 dans `BM/infrastructure/jira/JiraApi.java` et `BM/infrastructure/jira/JiraRestClient.java` (même proxy, CA, limitation de débit ; `issueTypes()` continue d'écarter les sous-tâches) ; T010 vert.
- [X] T012 [P] `BM/infrastructure/persistence/task/SqliteTaskRepository.java` (JDBC, comme les autres dépôts SQLite) et test `BT/infrastructure/persistence/TaskPersistenceIT.java` (ordre, unicité de position, préalables, proposition JSON, cascade à la suppression de la session, `task_tracking`).
- [X] T013 [P] `BM/infrastructure/platform/ProcessAgentConnections.java` : connexion `<pid>-<début ms>` vivante si `ProcessHandle.of(pid)` existe avec le même `startInstant` ; format invalide → fermée ; test `BT/infrastructure/platform/ProcessAgentConnectionsTest.java` (processus courant vivant, pid inconnu fermé, même pid autre début fermé).

### Application

- [X] T014 `BM/application/task/TaskJira.java` (R3) : pour une catégorie visée, ne fait rien si la sous-tâche y est déjà, sinon prend la première transition de Jira menant à cette catégorie, sinon `NO_TRANSITION` avec le statut actuel ; distingue refus (`JIRA_REFUSED`) et injoignable ; test `BT/application/task/TaskJiraTest.java` avec un double d'`IssueTracker`.
- [X] T015 [P] Bouchons WireMock posés par `e2e/tests/us15-taches.spec.ts` (API d'administration, comme US5) : `createmeta` avec un type sous-tâche, création de sous-tâche (clés `FACT-124`…), transitions (`new`, `indeterminate`, `done`), commentaire, recherche par clés.

**Checkpoint** : `JiraTasksIT`, `TaskJiraTest`, dépôt et connexions verts. Commit `feat(task): domaine, dépôt et appels Jira des tâches`.

---

## Phase 3: User Story 1 - Proposer, corriger et valider les tâches (Priority: P1) 🎯 MVP

**Goal**: sur une spécification liée à une story, proposer 3 à 8 tâches, les corriger, les valider
en sous-tâches Jira.

**Independent Test**: proposer, retirer une tâche, en ajouter une, valider : le Jira simulé reçoit
exactement les sous-tâches de la liste validée, dans l'ordre, sous la bonne story.

### Tests US1

- [X] T016 [P] [US1] `BT/domain/task/TaskProposalTest.java` : bornes 3 à 8 (complémentaire : 1 à 8 moins les existantes), titre 1 à 120, résultat 1 à 300, base connue (module de la liste ou code de cas d'usage), préalable vers une tâche antérieure seulement, titre identique à une tâche existante refusé ; opérations renommer, fusionner, supprimer (réindexe les préalables), déplacer, ajouter.
- [X] T017 [P] [US1] `BT/infrastructure/llm/ai/TasksGuardrailTest.java` : réponses hors bornes refusées avec un message de redemande, réponse valide acceptée (`LlmOutputs::tasks`).
- [X] T018 [P] [US1] `BT/application/task/ProposeTasksTest.java` : sans ticket lié → `NO_LINKED_TICKET` ; ticket de type sous-tâche → `PARENT_IS_SUBTASK` ; base `MODULES` si la session a un périmètre (013), sinon `USE_CASES` ; sous-tâches existantes de la story importées (`origin = JIRA`) et passées en `<taches-existantes>` ; proposition en cours remplacée ; échec du modèle → `TASKS_UNAVAILABLE`.
- [X] T019 [P] [US1] `BT/application/task/ValidateTasksTest.java` (R10) : création une par une dans l'ordre, clé gardée ; sous-tâche de même résumé exact reprise au lieu d'être créée ; erreur au milieu → restantes `NOT_CREATED`, relance sans doublon ; pas de type sous-tâche → `NO_SUBTASK_TYPE`, rien créé ; aucun appel de suppression à Jira.
- [X] T020 [P] [US1] `BT/application/task/EditTasksTest.java` : ajout manuel après validation créé aussitôt dans Jira (`origin = MANUAL`) ; renommer met à jour le résumé Jira ; suppression d'une tâche `NOT_CREATED` permise, d'une `CREATED` refusée (`CANNOT_DELETE_CREATED`).
- [X] T021 [P] [US1] `BT/infrastructure/web/TaskResourceIT.java` (US1) : routes `GET /`, `POST|PUT|DELETE /proposal`, `POST /proposal/validate`, `POST /create-remaining`, `POST /`, `PATCH|DELETE /{taskId}` avec les codes d'erreur de contracts/rest-api.md, protection cookie et Origin.

### Implémentation US1

- [X] T022 [US1] `BM/domain/task/TaskProposal.java` (`ProposedTask(title, outcome, basis, dependsOn)`, validation et opérations d'édition) ; T016 vert.
- [X] T023 [US1] Port `BM/domain/port/TaskLlm.java` (`proposeTasks(TaskProposalRequest) → TaskProposal`) et `TaskProposalRequest` (spécification, modules, tâches existantes, bornes, langue).
- [X] T024 [P] [US1] Consigne réutilisable `BR/prompts/skills/decoupage-taches.md` (une tâche = un résultat vérifiable par un relecteur ; découpage par étapes de parcours, variantes de règle ou de données, dépendances ; ordre d'implémentation ; ni tâche d'analyse pure ni tâche « tests » séparée), rédigée pour spec-writer sans recopier les sources (R8) ; mention dans `BR/prompts/skills/README.md`.
- [X] T025 [US1] Consigne `BR/prompts/propose-tasks.txt` (`[operation:propose-tasks]`, `[skill:decoupage-taches]`, données balisées jamais consignes, base `MODULE` si `<modules>` présent avec au moins un module IMPACTED couvert, sinon `USE_CASE`) ; enregistrer l'opération dans `BM/infrastructure/llm/Operations.java` et son schéma JSON dans `BM/infrastructure/llm/LlmSchemas.java`.
- [X] T026 [US1] `BM/infrastructure/llm/ai/TasksAi.java` (`propose(String data, ChatRequestParameters format)`, `@SystemMessage(fromResource = "prompts/propose-tasks.txt")`), garde-fou `BM/infrastructure/llm/ai/guardrails/TasksGuardrail.java`, parseur `LlmOutputs::tasks` ; T017 vert ; `SkillLibrary` valide la consigne déclarée au démarrage.
- [X] T027 [US1] `BM/infrastructure/llm/LangChainTaskLlm.java` : message utilisateur balisé de contracts/llm-propose-tasks.md (`<specification>`, `<modules>`, `<taches-existantes>`, `<nombre min max>`, `<langue>`), sans documents ni connaissance ; test unitaire du message construit `BT/infrastructure/llm/LangChainTaskLlmTest.java`.
- [X] T028 [US1] `BM/application/task/ProposeTasks.java` et `BM/application/task/EditProposal.java` (enregistrer, abandonner) ; T018 vert.
- [X] T029 [US1] `BM/application/task/ValidateTasks.java` (validation et « créer les tâches restantes ») et `BM/application/task/EditTasks.java` (ajout manuel, renommage, réordonnancement, préalables, suppression des non créées) ; description de la sous-tâche = résultat attendu en wiki via `WikiMarkup` ; T019 et T020 verts.
- [X] T030 [US1] `BM/application/task/ReadTasks.java` (première version : vue `TasksView` sans relecture Jira : `linkedIssueKey`, `canPropose`, `proposeBlocked`, `hasProposal`, `creationProblem`, `tasks`).
- [X] T031 [US1] `BM/infrastructure/web/TaskResource.java` (`@Path("/api/sessions/{id}/tasks")`, DTO `TasksView` et `ProposalView` de contracts/rest-api.md) et câblage dans `BM/infrastructure/config/UseCaseProducer.java` ; T021 vert ; règle ArchUnit `HexagonalArchitectureTest` verte (sous Maven).
- [X] T032 [P] [US1] Front : `FE/core/tasks/tasks.model.ts` et `FE/core/tasks/tasks.api.ts` (routes de T031) avec tests `tasks.api.spec.ts`.
- [X] T033 [US1] Front : `FE/features/specification/task-proposal.component.ts` (relecture : renommer, fusionner, supprimer, monter/descendre par boutons, ajouter, valider, abandonner ; base affichée ; libellés et contrastes WCAG AA) et son test `task-proposal.component.spec.ts`.
- [X] T034 [US1] Front : `FE/features/specification/tasks-panel.component.ts` (bouton « Proposer des tâches », message « Envoyez d'abord la spécification vers Jira » sans ticket, liste avec clé Jira cliquable et statut, « Créer les tâches restantes », ajout manuel) et test `tasks-panel.component.spec.ts` ; panneau placé au-dessus de `sw-subtasks-panel` dans `FE/features/specification/specification.page.ts`.
- [X] T035 [P] [US1] Fixtures de rejeu `e2e/fixtures/llm/63-propose-tasks-modules.json` et `64-propose-tasks-cas-usage.json` (`operation: "propose-tasks"`, `userContains` sur le titre de la spécification de l'essai).
- [X] T036 [US1] E2E `e2e/tests/us15-taches.spec.ts`, parcours US1 : proposer, supprimer, ajouter, monter, recharger (corrections gardées), valider → journal WireMock avec un `POST /rest/api/2/issue` par tâche et `fields.parent.key` ; spécification sans ticket → bouton indisponible ; vérification axe.

**Checkpoint** : US1 utilisable seule. Commits `feat(task): proposition de tâches par le modèle`, `feat(task): validation en sous-tâches Jira`, `feat(front): panneau des tâches`.

---

## Phase 4: User Story 2 - Un agent de code suit les tâches par la passerelle MCP (Priority: P1)

**Goal**: `get_feature` étendu, `next_task`, `start_task`, `complete_task`, réservation par
connexion, transitions Jira, journal.

**Independent Test**: avec trois tâches validées, un client MCP enchaîne « prochaine tâche »,
« prendre », « terminer » trois fois : bonne transition dans le Jira simulé, une ligne de journal à
chaque étape, la quatrième demande répond `NONE_LEFT`.

### Tests US2

- [X] T037 [P] [US2] `BT/domain/task/TaskProgressTest.java` (choix `next`) : tâche en cours de la connexion appelante, puis première en cours d'une connexion fermée, puis première `TODO` créée dont les préalables sont `DONE`, sinon `NONE_LEFT` ou `ALL_HELD` ; `MISSING` jamais choisie ; deux appels sans changement → même tâche (SC-004).
- [X] T038 [P] [US2] `BT/application/agent/StartTaskTest.java` : `TODO` → `IN_PROGRESS` tenue par la connexion, transition Jira `indeterminate` ; reprise d'une tâche d'une connexion fermée ; tenue par une connexion ouverte → `TASK_HELD` nommant l'agent ; préalables non finis ou `MISSING` → `TASK_NOT_READY` ; Jira injoignable → état enregistré, `pending_target`, `jiraSync: PENDING` et message ; Jira refuse → rien ne change (`JIRA_REFUSED`, `NO_TRANSITION`) ; suivi suspendu → `TRACKING_SUSPENDED` ; sans connexion → `BRIDGE_OUTDATED` ; journal `START_TASK` avec `task_key`.
- [X] T039 [P] [US2] `BT/application/agent/CompleteTaskTest.java` : transition `done`, connexion effacée ; compte rendu ≤ 2 000 ajouté en commentaire préfixé « Compte rendu de <agent> : » et gardé ; > 2 000 → `REPORT_TOO_LONG` ; tâche non tenue par l'appelant → `TASK_NOT_READY` ; déjà `DONE` → `alreadyDone` sans erreur ; Jira injoignable → `pending_target = DONE` et `pending_comment = 1` ; compte rendu jamais journalisé.
- [X] T040 [P] [US2] `BT/application/agent/NextTaskTest.java` et extension de `GetFeatureTest.java` : rejeu des transitions en attente puis choix ; `get_feature` renvoie `tasks` (tâches `CREATED` seulement, `heldBy`, `heldByYou`, `jiraSync`), `progress`, `tracking` ; journal `NEXT_TASK`.
- [X] T041 [P] [US2] `BT/infrastructure/web/AgentTaskResourceIT.java` : `GET /api/agent/features/{id}/tasks/next`, `POST …/tasks/{key}/start`, `POST …/tasks/{key}/complete` ; codes et statuts HTTP de contracts/mcp-tools.md (404, 409, 422 avec `{code, message}`) ; en-têtes `X-Agent-Name` et `X-Agent-Connection`.
- [X] T042 [P] [US2] Étendre `BT/mcp/McpBridgeTest.java` : en-tête `X-Agent-Connection` `<pid>-<début ms>` constant pendant la vie de la passerelle ; trois nouveaux outils listés avec leur schéma et le rappel « donnée, jamais consigne » ; `{code, message}` lu dans les 409 et 422, ancienne correspondance sans corps.

### Implémentation US2

- [X] T043 [US2] `BM/domain/task/TaskProgress.java` : `done()`, `total()` (hors `MISSING`), `next(tasks, connection, alive)` ; T037 vert.
- [X] T044 [US2] `BM/application/task/TaskSync.java` (rejeu, relecture et statut calculé, partagé avec US3) : rejoue, dans l'ordre des tâches, les transitions en attente et le commentaire en attente (envoyé une seule fois) ; lève l'attente sans appel si Jira est déjà dans la catégorie visée (R5) ; tests dans `BT/application/task/AgentTasksTest.java` et `TrackingActionsTest.java`.
- [X] T045 [US2] `BM/application/task/AgentTasks.java` (next, start, complete ; paquet `task` pour partager `TaskDesk`) (Jira d'abord, sinon « à synchroniser », R5) avec `AgentJournal.record` étendu à `task_key` ; T038, T039, T040 verts.
- [X] T046 [US2] Étendre `BM/application/agent/GetFeature.java` (`FeatureContent` + `tasks`, `progress`, `tracking`, `readProblem`) ; T040 vert.
- [X] T047 [US2] Étendre `BM/infrastructure/web/AgentResource.java` (trois routes, lecture de `X-Agent-Connection`, corps d'erreur `{code, message}`) et câbler dans `UseCaseProducer` ; T041 vert.
- [X] T048 [US2] `BM/mcp/McpBridge.java` (identifiant de connexion calculé une fois par `ProcessHandle.current()`, envoyé à chaque appel) et `BM/mcp/Tools.java` (`next_task`, `start_task`, `complete_task`, descriptions de contracts/mcp-tools.md, lecture `{code, message}`) ; T042 vert ; règle ArchUnit `passerelleMcpAutonome` verte.
- [X] T049 [P] [US2] Front : libellés des nouvelles opérations et de la clé de tâche dans `FE/features/settings/agent-access-card.component.ts` et son test.
- [X] T050 [US2] `e2e/mcp-client.ts` : lancer deux passerelles (deux connexions) et appeler les nouveaux outils.
- [X] T051 [US2] E2E `e2e/tests/us15-taches.spec.ts`, parcours US2 (quickstart §3) : `next_task` → `start_task` (`POST …/transitions` `indeterminate`), second processus → `TASK_HELD` puis tâche suivante, arrêt du premier → reprise `resume: true`, `complete_task` avec compte rendu (`POST …/comment`), journal des agents à l'écran.

**Checkpoint** : US1 et US2 vertes ensemble. Commits `feat(agent): outils de tâches et réservation par connexion`, `feat(mcp): passerelle, connexion et outils de tâches`.

---

## Phase 5: User Story 3 - Voir l'avancement, à jour avec Jira (Priority: P2)

**Goal**: avancement affiché, statuts relus dans Jira, statut de la spécification calculé, suivi
suspendu et repris, libération et synchronisation.

**Independent Test**: cinq tâches dont trois terminées dans le Jira simulé → « 3 tâches sur 5
terminées » et « En cours » ; les deux dernières terminées dans Jira → « 5 sur 5 » et
« Implémentée ».

### Tests US3

- [X] T052 [P] [US3] Étendre `BT/domain/task/TaskProgressTest.java` : `status(tasks)` hors `MISSING` (aucune commencée → `READY`, en cours ou partiellement terminées → `IN_PROGRESS`, toutes terminées → `IMPLEMENTED`).
- [X] T053 [P] [US3] `BT/application/task/ReadTasksTest.java` : rejeu des attentes puis relecture par `issuesByKeys` (50 clés par lecture) ; `new`/`indeterminate`/`done` → statuts, clé absente → `MISSING` ; tâche passée `DONE` dans Jira perd sa connexion ; statut de la spécification recalculé par `Actor.TASKS` si le suivi est `ACTIVE` ; Jira injoignable → statuts connus, `lastReadAt` inchangé, `readProblem: JIRA_UNREACHABLE`.
- [X] T054 [P] [US3] `BT/application/task/ReleaseTaskTest.java`, `SyncTasksTest.java`, `ResumeTrackingTest.java` : libération → `TODO`, transition `new`, journal `RELEASE_TASK` au nom de l'agent qui tenait la tâche ; « Synchroniser » rejoue les attentes ; reprise → `ACTIVE` et statut recalculé.
- [X] T055 [P] [US3] Étendre `BT/application/session/ChangeStatusTest.java` et `BT/application/agent/AgentChangeStatusTest.java` : `IN_PROGRESS` ou `IMPLEMENTED` à la main sur une session avec tâches → `STATUS_FOLLOWS_TASKS` ; `DRAFT` ou `READY` → `task_tracking = SUSPENDED` ; `set_feature_status` → `STATUS_FOLLOWS_TASKS`.
- [X] T056 [P] [US3] Étendre `BT/infrastructure/web/TaskResourceIT.java` : `POST /{taskId}/release`, `POST /sync`, `POST /resume`, `GET /` avec `progress`, `lastReadAt`, `readProblem`, `pendingSync`, `tracking`.

### Implémentation US3

- [X] T057 [US3] `TaskProgress.status` dans `BM/domain/task/TaskProgress.java` ; T052 vert.
- [X] T058 [US3] Relecture et recalcul dans `BM/application/task/ReadTasks.java` (R6, R7), réutilisée par `NextTask` et `GetFeature` (FR-019) ; T053 vert.
- [X] T059 [US3] `BM/application/task/TrackingActions.java` (libérer, synchroniser, reprendre) ; aucun ajout à `CAS_D_USAGE_D_ECRITURE` : les tâches ne modifient pas le contenu de la spécification ; T054 vert.
- [X] T060 [US3] `BM/application/session/ChangeStatus.java` et `BM/application/agent/AgentChangeStatus.java` (R7) ; T055 vert.
- [X] T061 [US3] Routes `release`, `sync`, `resume` dans `BM/infrastructure/web/TaskResource.java`, câblage `UseCaseProducer` ; T056 vert.
- [X] T062 [US3] Front `FE/features/specification/tasks-panel.component.ts` : « N tâches sur M terminées », statut, agent et heure de prise, « connexion fermée », « à synchroniser » et bouton « Synchroniser », « Libérer », compte rendu, heure de dernière relecture et message si Jira n'a pas pu être relu, « Reprendre le suivi » quand le suivi est suspendu ; tests du composant.
- [X] T063 [US3] Front `FE/features/specification/specification.page.ts` : avec des tâches, « En cours » et « Implémentée » retirés du choix de statut, explication à côté ; message de `STATUS_FOLLOWS_TASKS` ; tests `specification.page.spec.ts`.
- [X] T064 [US3] E2E `e2e/tests/us15-taches.spec.ts`, parcours US3 (quickstart §4) : sous-tâche passée `done` dans WireMock → avancement mis à jour ; toutes terminées → « Implémentée », une rouverte → « En cours » ; « Prête » à la main → `next_task` répond `TRACKING_SUSPENDED`, « Reprendre le suivi » recalcule ; WireMock coupé pendant `complete_task` → « à synchroniser », puis transition envoyée à la réouverture.

**Checkpoint** : US1 à US3 vertes. Commits `feat(task): avancement relu dans Jira et statut calculé`, `feat(front): avancement, synchronisation et suivi suspendu`.

---

## Phase 6: User Story 4 - Story rédigée à partir d'un document, avec ses cas d'usage (Priority: P3)

**Goal**: énoncé « En tant que… je veux… afin de… » en tête de la spécification, éléments supposés
marqués, story trop large signalée avec un découpage proposé.

**Independent Test**: avec un document joint qui décrit une capacité et son bénéfice, la
spécification générée commence par un énoncé qui les nomme ; l'export et l'aperçu Jira le
reprennent.

### Tests US4

- [ ] T065 [P] [US4] `BT/domain/specification/UserStoryTest.java` : `role`, `capability`, `benefit` ≤ 300 caractères ; `assumed` ⊂ {`ROLE`, `CAPABILITY`, `BENEFIT`} ; `splitProposal` vide ou de 2 à 5 titres ≤ 120.
- [ ] T066 [P] [US4] Étendre `BT/domain/specification/MarkdownRendererTest.java` : énoncé en tête, élément supposé suivi de « (hypothèse) », rubrique « Découpage proposé » si `splitProposal` n'est pas vide, rendu inchangé sans story (versions antérieures).
- [ ] T067 [P] [US4] Étendre les tests du parseur `BT/infrastructure/llm/ai/LlmOutputsTest.java` : JSON sans `story` accepté (anciennes fixtures), `story` invalide refusé avec un message de redemande.

### Implémentation US4

- [ ] T068 [US4] `BM/domain/specification/UserStory.java`, `StoryPart`, et `story` (nullable) et `splitProposal` (liste, vide par défaut) dans `BM/domain/specification/SpecificationContent.java` ; lecture des contenus JSON antérieurs sans ces champs ; T065 vert.
- [ ] T069 [US4] `BM/domain/specification/MarkdownRenderer.java` ; T066 vert ; l'export et la description Jira (`CreateJiraTicket`, `PushToJira`) reprennent le Markdown sans autre changement.
- [ ] T070 [US4] Parseur `LlmOutputs` et schémas JSON de `LlmSchemas` des opérations `generate-specification`, `edit-specification`, `merge-conflict`, `structure-imported-text` (champs facultatifs) ; T067 vert.
- [ ] T071 [P] [US4] Consigne réutilisable `BR/prompts/skills/redaction-story.md` (énoncé rôle/capacité/bénéfice tiré du besoin, des réponses ou des documents ; élément supposé déclaré ; critères INVEST ; signal de story trop large avec 2 à 5 stories proposées, sans découper d'office), rédigée sans recopie (R9).
- [ ] T072 [US4] Déclarer `[skill:redaction-story]` et le format `story`/`splitProposal` dans `BR/prompts/generate-specification.txt`, `edit-specification.txt`, `merge-conflict.txt`, `structure-imported-text.txt`.
- [ ] T073 [P] [US4] Fixture `e2e/fixtures/llm/65-generate-story.json` (génération avec `story` et un document joint) et E2E `e2e/tests/us15-taches.spec.ts`, parcours US4 : la spécification s'ouvre sur l'énoncé, l'export Markdown et l'aperçu Jira le reprennent.

**Checkpoint** : les fixtures de génération existantes restent valides (E2E complets verts). Commit `feat(specification): énoncé de story et découpage proposé`.

---

## Phase 7: Évaluation réelle (FR-027, SC-001, SC-002, SC-007, SC-008)

- [ ] T074 [P] Cas `BTR/eval/tasks/cases.json` : spécifications avec et sans périmètre (modules IMPACTED et TO_CHECK), une avec un titre de cas d'usage adressé à une IA.
- [ ] T075 [P] Juge `BTR/eval/prompts/judge-task.txt` (résultat vérifiable par un relecteur : oui/non et motif).
- [ ] T076 `BT/eval/TaskRuns.java` et `BT/eval/TaskEval.java` (métriques `taskBounds`, `taskBasisExact`, `taskModuleCoverage`, `taskVerifiable`), branchés dans `BT/eval/EvalRuns.java` comme `InjectionRuns` ; constantes dans `BT/eval/EvalBaseline.java` ; test unitaire du calcul des métriques sur des réponses figées.
- [ ] T077 Métrique `storyStatement` dans `BT/eval/SpecificationStructureEval.java` (juge : rôle, capacité, bénéfice présents dans le besoin ou les documents, ou marqués supposés).
- [ ] T078 `BTR/eval/baseline.json` : minimums `taskBounds` 1.0, `taskBasisExact` 0.9, `taskModuleCoverage` 0.9, `taskVerifiable` 0.9, `storyStatement` 0.9 ; `ci/check-eval.sh` contrôle les nouvelles métriques.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [ ] T079 [P] Test d'injection : un titre de tâche ou un compte rendu adressé à une IA reste une donnée dans le message de `propose-tasks` et dans la sortie de `get_feature` (`BT/infrastructure/llm/LangChainTaskLlmTest.java`, `BT/application/agent/GetFeatureTest.java`) ; aucun titre, résultat ni compte rendu dans les journaux (`LlmCallLog`, journal des agents).
- [ ] T080 [P] Annoter FR-029 dans `specs/002-projets-contexte-mcp/spec.md` et `specs/002-projets-contexte-mcp/contracts/mcp-tools.md` (« remplacé en partie par la 015 », R12).
- [ ] T081 [P] Documentation utilisateur : `README.md` (panneau Tâches, outils `next_task`, `start_task`, `complete_task`, « à synchroniser », suivi suspendu) ; `CONTRIBUTING.md` (nouvelle opération `propose-tasks`, consignes `decoupage-taches` et `redaction-story`).
- [ ] T082 [P] `CLAUDE.md` : feature en cours `specs/015-taches-avancement/`.
- [ ] T083 [P] `e2e/fixtures/llm/README.md` : fixtures `propose-tasks` et `generate-story`.
- [ ] T084 Vérifier la performance : relecture de 50 clés et ouverture du panneau < 2 s avec WireMock, `next_task` < 3 s (plan, Performance Goals), mesuré dans l'E2E.
- [ ] T085 Revue de `HexagonalArchitectureTest` : `Instant.now` et `ProcessHandle` seulement dans `infrastructure.platform` (et la passerelle pour son propre processus), écritures gardées par le verrou.
- [ ] T086 Dérouler quickstart.md §1 à §5 en dev mode (Jira simulé, LLM rejoué).
- [ ] T087 Gates complètes (verify, package, E2E) sur la branche.
- [ ] T088 Évaluation réelle `./mvnw -pl backend verify -Peval -DskipDependencyCheck` puis `ci/check-eval.sh` : nouvelles métriques au-dessus de leur minimum, aucun indicateur existant sous sa base ; commit de `BTR/eval/last-run.json`.
- [ ] T089 Vérification manuelle du mainteneur (quickstart §7, profil `live`, vrai Jira de test) : proposer, valider, faire travailler Claude Code sur deux tâches, vérifier sous-tâches et commentaires.

---

## Dependencies & Execution Order

### Phases

- **Setup (T001–T004)** : aucune dépendance.
- **Foundational (T005–T015)** : après Setup ; bloque toutes les histoires.
- **US1 (T016–T036)** : après Foundational. MVP.
- **US2 (T037–T051)** : après US1 (il faut des tâches validées ; les tests unitaires peuvent démarrer dès Foundational).
- **US3 (T052–T064)** : après US2 pour `ReadTasks` partagé et les parcours E2E ; `ChangeStatus` (T055, T060) peut avancer en parallèle d'US2.
- **US4 (T065–T073)** : après Setup seulement ; indépendante d'US1 à US3.
- **Évaluation (T074–T078)** : après US1 (`TasksAi`) et US4 (`story`).
- **Polish (T079–T089)** : à la fin ; T088 après T087.

### Dans chaque histoire

Tests d'abord (vus en échec), puis domaine, application, infrastructure, web, front, E2E.

### Parallélisme

- Setup : T003 et T004 ensemble, après T002.
- Foundational : T005, T006, T007, T008 ensemble ; puis T012, T013, T015 ensemble ; T010 → T011.
- US1 : T016 à T021 ensemble ; T024 et T032 en parallèle de l'implémentation backend ; T035 avec T033.
- US2 : T037 à T042 ensemble ; T049 en parallèle.
- US3 : T052 à T056 ensemble.
- US4 entière en parallèle d'US2 et US3 (fichiers distincts : `domain/specification`, consignes de rédaction).

## Parallel Example: User Story 1

```text
T016 TaskProposalTest     T017 TasksGuardrailTest    T018 ProposeTasksTest
T019 ValidateTasksTest    T020 EditTasksTest         T021 TaskResourceIT
puis : T024 consigne decoupage-taches  |  T032 tasks.api.ts  |  T035 fixtures propose-tasks
```

## Implementation Strategy

1. **MVP** : Setup, Foundational, US1. La story est découpée dans Jira sans ressaisie ; démontrable seule.
2. **US2** : les agents suivent les tâches ; c'est là que le découpage paie.
3. **US3** : avancement visible et statut automatique.
4. **US4** : énoncé de story, en parallèle si un second flux de travail est disponible.
5. **Évaluation réelle** puis polish et gates ; PR quand T088 est vert.
