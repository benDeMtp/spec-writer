---
description: "Tâches de la feature 023 : statut Jira d'une tâche implémentée, réglé par projet, et story qui suit ses tâches"
---

# Tasks: Statut Jira d'une tâche implémentée, réglé par projet

**Input**: `specs/023-statut-fin-tache/` (plan.md, spec.md, research.md, data-model.md,
contracts/rest-api.md, contracts/mcp-tools.md, quickstart.md)

**Tests** : obligatoires (constitution III, TDD). Chaque test est écrit et vu échouer avant le
code. JaCoCo ne compte pas les `@QuarkusTest` : `domain` et `application` sont couverts par des
tests unitaires (`TaskWorld`, `FakeIssueTracker`, `InMemoryTasks`, un `InMemoryTaskWorkflows`
nouveau). Les IT qui appellent WireMock et ArchUnit se lancent sous Maven.

**Évaluation** : aucun fichier de `BR/prompts`, `BM/infrastructure/llm` ni du golden set n'est
touché ; `ci/check-eval.sh` reste vert sans nouvelle évaluation.

**Organisation** : la Phase 2 (domaine, V35, dépôt) bloque tout. US1 (fin vers le statut
d'arrivée) et US2 (statuts finis à la relecture) sont le cœur, et US3 (rubrique) les rend
réglables par l'utilisateur. US4 (story) s'appuie sur US1 et US2.

## Format: `[ID] [P?] [Story] Description`

- **[P]** : parallélisable (fichiers différents, aucune dépendance sur une tâche non finie).
- Abréviations des chemins :
  - `BM/` : `backend/src/main/java/fr/kawamind/`
  - `BT/` : `backend/src/test/java/fr/kawamind/`
  - `BR/` : `backend/src/main/resources/`
  - `FE/` : `frontend/src/app/`

## Gates avant chaque commit (CLAUDE.md)

1. arrêter le dev mode et l'outil installé (port 47615), `rm -f backend/target/jacoco.exec` ;
2. `rm -rf backend/target/classes backend/target/test-classes backend/target/test-data` ;
3. `./mvnw -B verify -DskipDependencyCheck` ;
4. `npm --prefix e2e test` sur le jar construit par `verify` (JDK 25 en tête du `PATH`) ;
5. `bash ci/check-eval.sh`.

Commits en Conventional Commits, en français, fichiers stagés explicitement, sans
`.specify/feature.json`.

---

## Phase 1: Setup (migration)

- [ ] T001 Écrire `BT/infrastructure/persistence/MigrationV35IT.java` (modèle : `MigrationV34IT`). Vérifier que la table `project_task_workflow` existe et que :
  - une ligne valide s'insère ;
  - `arrival_status` vide ou de 101 caractères est refusé ;
  - supprimer le projet supprime la ligne (cascade).

  Le voir échouer.
- [ ] T002 Créer `BR/db/migration/V35__task_workflow.sql` :
  - `create table project_task_workflow (project_id text primary key references project (id) on delete cascade, arrival_status text not null check (length(arrival_status) between 1 and 100), finished_statuses text not null, updated_at text not null);`
  - un commentaire d'en-tête (023, research R1).

  T001 passe.

---

## Phase 2: Foundational (domaine, port, dépôt)

**⚠️ CRITICAL** : bloque toutes les stories.

### Tests

- [ ] T003 [P] Écrire `BT/domain/task/StatusNameTest.java`. `same("En Cours", "en cours")`, `same("Réalisée", "Realisee")` et `same(" Fermée ", "fermée")` sont vrais ; `same("Réalisée", "Réalisé")` est faux ; `normalize(null)` donne `""`.
- [ ] T004 [P] Écrire `BT/domain/task/TaskWorkflowTest.java`. Vérifier que :
  - le statut d'arrivée est ajouté aux finis ;
  - les doublons au sens de `StatusName.same` sont refusés ;
  - un statut d'arrivée vide ou de plus de 100 caractères, ou plus de 50 statuts finis, est refusé (`VALIDATION_FAILED`) ;
  - `finished("Réalisée", "indeterminate")`, `finished("en cours de test", "indeterminate")` et `finished("Fermée", "done")` sont vrais ;
  - `finished("En Cours", "indeterminate")` est faux ;
  - `statusOf` rend `DONE`, `IN_PROGRESS` ou `TODO` selon data-model ;
  - `completion()` rend `Target.Status("Réalisée")` ;
  - `TaskWorkflow.NONE.completion()` rend `Target.Category(DONE)` ;
  - `NONE.statusOf("Réalisée", "indeterminate")` rend `IN_PROGRESS` (comportement de la 015).
- [ ] T005 [P] Écrire `BT/infrastructure/persistence/SqliteTaskWorkflowRepositoryIT.java`. Vérifier que :
  - `find` est vide sans réglage ;
  - `save` puis `find` rend le même `TaskWorkflow`, statuts finis dans l'ordre ;
  - `save` remplace ;
  - `delete` retire ;
  - `knownStatuses(projectId)` rend les statuts des tickets indexés (`KnowledgeItem.ticket`), chacun une fois au sens de `StatusName.same`, classés par catégorie (`new`, `indeterminate`, `done`) puis par nom, sans les tickets d'un autre projet.

### Implémentation

- [ ] T006 [P] Créer `BM/domain/task/StatusName.java` (`normalize`, `same` : NFD, sans diacritiques, minuscules, `strip`). T003 passe.
- [ ] T007 [P] Créer `BM/domain/task/Target.java`, interface scellée avec deux records :
  - `Category(StatusCategory category)` ;
  - `Status(String name, TaskWorkflow workflow)`.
- [ ] T008 Créer `BM/domain/task/TaskWorkflow.java` : record `(String arrivalStatus, List<String> finishedStatuses)`, avec `NONE`, `finished`, `statusOf` et `completion`.
  - Validation : « 1 à 100 caractères », « 50 statuts finis au plus », pas de doublon.
  - Le statut d'arrivée est ajouté en tête des finis s'il manque.

  T004 passe.
- [ ] T009 [P] Créer `BM/domain/task/KnownStatus.java` (record `name`, `category`, `seen`).
- [ ] T010 Créer `BM/domain/port/TaskWorkflowRepository.java` :
  - `Optional<TaskWorkflow> find(UUID projectId)` ;
  - `void save(UUID projectId, TaskWorkflow workflow, Instant at)` ;
  - `void delete(UUID projectId)` ;
  - `List<KnownStatus> knownStatuses(UUID projectId)`.

  L'implémenter dans `BM/infrastructure/persistence/task/SqliteTaskWorkflowRepository.java` (JSON par le `JsonMapper` du projet, `upsert` par `insert … on conflict(project_id) do update`). Le double est `BT/support/InMemoryTaskWorkflows.java`. T005 passe.
- [ ] T011 Ajouter à `BT/support/TaskWorld.java` un champ `workflows` (`InMemoryTaskWorkflows`) et le passer à `TaskDesk`, ou à un nouvel argument de `TaskSync` et `AgentTasks`. Câbler `TaskWorkflowRepository` dans `BM/infrastructure/config/UseCaseProducer.java` (`TaskDesk` ou les cas d'usage concernés).

**Checkpoint** : domaine et dépôt verts. Commit `feat(taches): réglage du statut d'arrivée et des statuts finis d'un projet, domaine et base (023)`.

---

## Phase 3: User Story 1 - Une tâche terminée par un agent passe au statut choisi (Priority: P1) 🎯 MVP

**Goal**: `complete_task` vise le statut d'arrivée du projet, par une transition directe, jamais
une annulation ; sans transition, rien ne change ; le rejeu « à synchroniser » vise aussi ce statut
(FR-006 à FR-010).

**Independent Test**: projet réglé sur « Réalisée », sous-tâche « En Cours » dont le workflow offre
« Réaliser » → Réalisée et « Fermer » → Fermée ; `complete_task` envoie « Réaliser » (quickstart Q2).

### Tests US1

- [ ] T012 [P] [US1] Étendre `BT/application/task/TaskJiraTest.java` avec `moveTo(endpoint, key, Target.Status)`. Vérifier que :
  - parmi « Réaliser » → Réalisée (indeterminate), « Fermer » → Fermée (done) et « Annuler la réalisation » → Réalisée, la transition « Réaliser » est prise ;
  - une sous-tâche déjà « Réalisée » ou « En cours de test » (finie) rend `ALREADY`, sans transition envoyée ;
  - sans transition vers « Réalisée », `NO_TRANSITION` est levé avec le message « Le workflow Jira de K-1 n'offre aucune transition directe de « En Cours » vers « Réalisée ». Faites-la dans Jira. » ;
  - la seule transition vers « Réalisée » qui est une annulation n'est pas prise, et `NO_TRANSITION` est levé ;
  - `Target.Category` garde le comportement existant (tests existants inchangés).
- [ ] T013 [P] [US1] Étendre `BT/application/task/AgentTasksTest.java`. Projet de `TaskWorld` réglé sur « Réalisée », avec un `FakeIssueTracker` dont les transitions ont un `toStatus`. Vérifier que :
  - `complete` envoie la transition vers « Réalisée » ;
  - la tâche est `DONE` et `TaskInfo.jiraStatus` vaut « Réalisée » ;
  - le compte rendu est en commentaire ;
  - sans transition, `NO_TRANSITION` est levé et la tâche reste `IN_PROGRESS` ;
  - Jira injoignable, la tâche est `DONE` « à synchroniser », et le rejeu par `next` envoie la transition vers « Réalisée » ;
  - sans réglage, la transition vers la catégorie `done` est envoyée (comportement de la 015).

### Implémentation US1

- [ ] T014 [US1] Si besoin, étendre `BT/support/FakeIssueTracker.java` : des transitions configurables par clé, avec `toStatus`, et le statut courant mis à jour par `transition` d'après le `toStatus` de la transition prise. Garder le comportement par défaut pour les tests existants.
- [ ] T015 [US1] Dans `BM/application/task/TaskJira.java` :
  - ajouter `moveTo(JiraEndpoint, String, Target)` ;
  - pour `Category`, déléguer à la méthode existante ;
  - pour `Status`, suivre research R4 : `ALREADY` si `workflow.finished(current.status(), current.statusCategory())` ; sinon les transitions avec `StatusName.same(t.toStatus(), name)` et sans `cancellation()`, la première ; sinon `NO_TRANSITION` avec le message de T012.

  T012 passe.
- [ ] T016 [US1] Dans `BM/application/task/AgentTasks.complete`, viser `workflow(featureId).completion()`, où `workflow` lit le réglage du projet de la session ou rend `TaskWorkflow.NONE`. Dans `BM/application/task/TaskSync.replay`, une cible `DONE` devient `workflow.completion()` (research R5). Ajouter `jiraStatus` à `TaskInfo` (après `issueType`, depuis `SpecTask.jiraStatusName()`). T013 passe.

**Checkpoint** : commit `feat(taches): une tâche terminée par un agent passe au statut d'arrivée du projet (023, US1)`.

---

## Phase 4: User Story 2 - Une tâche « Réalisée » ou plus loin reste terminée (Priority: P1)

**Goal**: à la relecture des statuts Jira, un statut fini donne `DONE` ; l'avancement, `next_task`
et le statut de la spécification suivent (FR-011 à FR-013).

**Independent Test**: tâche « Réalisée » ; relecture : `DONE` ; « En cours de test » : `DONE` ;
« En Cours » : `IN_PROGRESS` ; toutes finies : spécification « implémentée » (quickstart Q3).

### Tests US2

- [ ] T017 [P] [US2] Étendre `BT/application/task/AgentTasksTest.java` (ou `TrackingActionsTest`, là où la relecture est testée). Projet réglé sur « Réalisée » et « En cours de test ». Vérifier que :
  - une sous-tâche « Réalisée » (indeterminate) dans Jira est `DONE` après la relecture, avec `jiraStatus` « Réalisée » ;
  - passée à « En cours de test », elle reste `DONE` ;
  - renvoyée à « En Cours », elle redevient `IN_PROGRESS` ;
  - `next` ne la donne pas quand elle est finie ;
  - toutes les tâches finies, `w.sessions.implementation(...)` vaut `IMPLEMENTED` ;
  - sans réglage, « Réalisée » (indeterminate) donne `IN_PROGRESS` (comportement de la 015).

### Implémentation US2

- [ ] T018 [US2] Dans `BM/application/task/TaskSync.reread`, calculer le statut par `workflow.statusOf(issue.status(), issue.statusCategory())` au lieu de la seule catégorie. Le réglage est lu une fois par relecture. Garder `MISSING` pour une clé absente. T017 passe.

**Checkpoint** : commit `feat(taches): une tâche dans un statut fini reste terminée à la relecture de Jira (023, US2)`.

---

## Phase 5: User Story 3 - Régler le statut d'arrivée et les statuts finis (Priority: P1)

**Goal**: la rubrique « Suivi des tâches dans Jira » des réglages du projet propose les statuts
connus et enregistre le réglage ; on peut le retirer (FR-001 à FR-005).

**Independent Test**: projet indexé avec les statuts d'AURORA ; choisir « Réalisée », cocher
« En cours de test », enregistrer, recharger : gardé ; « Prête » : refusé ; retirer : plus de
réglage (quickstart Q1).

### Tests US3

- [ ] T019 [P] [US3] Écrire `BT/application/task/ManageTaskWorkflowTest.java`. Vérifier que :
  - `view(projectId)` rend le réglage et les statuts connus ;
  - un statut réglé absent de l'index est ajouté avec `seen=false` ;
  - `save` d'un statut d'arrivée connu dans la catégorie `new` est refusé (`VALIDATION_FAILED`, message de `contracts/rest-api.md`) ;
  - `save` d'un statut inconnu de l'index est accepté ;
  - `remove` retire le réglage ;
  - un projet inconnu donne `PROJECT_NOT_FOUND`.
- [ ] T020 [P] [US3] Écrire `BT/infrastructure/web/TaskWorkflowResourceIT.java`. `GET`, `PUT` (200, 422 sur une valeur trop longue et sur plus de 50 statuts) et `DELETE` (204) suivent `contracts/rest-api.md`. Un `PUT` sans `Origin` est refusé comme les autres écritures, et un projet inconnu donne 404.
- [ ] T021 [P] [US3] Écrire `FE/features/project-settings/task-workflow.component.spec.ts`. Vérifier que :
  - les statuts connus s'affichent, groupés par catégorie (« À faire », « En cours », « Terminé ») ;
  - le statut d'arrivée se choisit par des boutons radio (seuls ceux « en cours » et « terminé » sont actifs) ;
  - les statuts finis se cochent ; le statut d'arrivée est coché et désactivé, et les statuts « terminé » sont cochés et désactivés avec la mention « toujours fini » ;
  - « Enregistrer » envoie `PUT` et annonce « Réglage enregistré » ;
  - « Retirer le réglage » envoie `DELETE` ;
  - le message d'erreur de l'API s'affiche en `role="alert"` ;
  - sans statut connu, le texte dit que les statuts apparaîtront après la synchronisation de Jira.

### Implémentation US3

- [ ] T022 [US3] Créer `BM/application/task/ManageTaskWorkflow.java` (`view`, `save`, `remove`) selon T019. Le câbler dans `UseCaseProducer`. T019 passe.
- [ ] T023 [US3] Créer `BM/infrastructure/web/TaskWorkflowResource.java` (`/api/projects/{id}/task-workflow`, GET, PUT, DELETE), avec une entrée `record Input(String arrivalStatus, List<String> finishedStatuses)`. T020 passe.
- [ ] T024 [US3] Créer `FE/core/projects/task-workflow.api.ts` et `FE/features/project-settings/task-workflow.component.ts` (formulaire de T021, fieldset et legend, sans saisie libre). L'intégrer à `FE/features/project-settings/project-settings.page.ts` dans une `section.sheet` « Suivi des tâches dans Jira », visible quand le projet a une clé Jira. T021 passe.

**Checkpoint** : commit `feat(projet): rubrique « Suivi des tâches dans Jira » des réglages du projet (023, US3)`.

---

## Phase 6: User Story 4 - La story de la feature suit ses tâches (Priority: P2)

**Goal**:
- au démarrage, une story « à faire » passe en cours ;
- à la dernière tâche, ou pour une feature sans tâches déclarée implémentée, la story passe au statut d'arrivée ;
- un échec ne bloque pas, mais il est signalé (FR-014 à FR-018).

**Independent Test**: story « Prête » ; première prise : « En Cours » ; dernière fin : « Réalisée » ;
story bloquée : tâche finie quand même, `story.status` à `FAILED`, `WARN` au journal (quickstart Q4).

### Tests US4

- [ ] T025 [P] [US4] Écrire `BT/application/task/StoryFollowTest.java` (`FakeIssueTracker`, `InMemoryTaskWorkflows`). Vérifier :
  - **`started`** :
    - une story « Prête » (new) passe en cours : `MOVED` ;
    - une story « En Cours » n'est pas touchée : `null` ;
    - une transition vers la catégorie « en cours » qui mène à un statut fini (« Réalisée ») n'est pas prise ;
  - **`allDone`** :
    - avec un projet réglé, la story passe à « Réalisée » : `MOVED` ;
    - une story déjà finie : `ALREADY`, sans envoi ;
    - un projet sans réglage : `SKIPPED`, sans envoi ;
    - sans transition, ou si Jira refuse : `FAILED`, avec le message, une ligne `WARN` au journal (clé et motif seuls) et aucune exception ;
    - si Jira est injoignable : `FAILED`, sans rejeu.
- [ ] T026 [P] [US4] Étendre `BT/application/task/AgentTasksTest.java`. Vérifier que :
  - `start` de la première tâche porte `story` `MOVED` vers « en cours » ;
  - `complete` de la dernière tâche porte `story` `MOVED` vers « Réalisée » ;
  - les tâches précédentes ne déplacent pas la story ;
  - un échec de la story n'empêche pas la tâche d'être `DONE`.
- [ ] T027 [P] [US4] Étendre `BT/application/agent/AgentFeaturesTest.java`. Une feature sans tâches liée à une story, dans un projet réglé, passée à `IMPLEMENTED` par `AgentChangeStatus`, déplace la story vers « Réalisée ». La réponse porte `story` ; sans réglage, `story` est `SKIPPED`.

### Implémentation US4

- [ ] T028 [US4] Créer `BM/application/task/StoryMove.java` (record `key`, `Outcome status`, `to`, `message` ; `Outcome` vaut `MOVED`, `ALREADY`, `SKIPPED` ou `FAILED`) et `BM/application/task/StoryFollow.java` (`started(featureId)`, `allDone(featureId)`), selon research R7. Utiliser `TaskJira.moveTo` et le journal (`Logger` WARN, sans contenu). T025 passe.
- [ ] T029 [US4] Dans `BM/application/task/AgentTasks.java` :
  - ajouter `story` à `TaskAnswer` (dernier champ) ;
  - appeler `StoryFollow.started` après une prise réussie ;
  - après `complete`, si toutes les tâches créées sont `DONE`, appeler `StoryFollow.allDone`.

  T026 passe.
- [ ] T030 [US4] Dans `BM/application/agent/AgentChangeStatus.java`, à `IMPLEMENTED` d'une feature sans tâches liée à un ticket, appeler `StoryFollow.allDone`, et ajouter `story` à `Changed`. Câbler dans `UseCaseProducer`. T027 passe.

**Checkpoint** : commit `feat(taches): la story de la feature suit ses tâches dans Jira (023, US4)`.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T031 [P] Dans `BM/mcp/Tools.java`, compléter les descriptions de `complete_task` (statut d'arrivée réglé dans le projet, sinon « terminé » ; `story`), de `start_task` (la story « à faire » passe en cours) et de `set_feature_status` (`story`). Étendre `BT/mcp/McpBridgeTest.java` sur ces mentions.
- [ ] T032 Écrire `e2e/tests/us23-statut-fin.spec.ts` (WireMock, modèle : `us15-taches`) :
  - **workflow simulé** : story « Prête » ; deux sous-tâches « Créée » ; transitions « Démarrer » vers « En Cours » (indeterminate), « Réaliser » vers « Réalisée » (indeterminate), « Fermer » vers « Fermée » (done) et « Annuler » vers « Annulée » (done) ;
  - **réglage** : régler le projet par la rubrique (« Réalisée », « En cours de test ») ;
  - **par la passerelle** : `start_task` déplace la story en cours ; `complete_task` envoie « Réaliser », pas « Fermer » ; la relecture garde la tâche `DONE` ; la dernière fin déplace la story vers « Réalisée » ;
  - **accessibilité** : `expectNoA11yViolations` sur la rubrique.
- [ ] T033 [P] Mettre à jour `README.md`, rubrique « Agents de code (MCP) » et réglages du projet :
  - le statut d'arrivée et les statuts finis, et l'exemple d'AURORA ;
  - la story qui suit ses tâches ;
  - le comportement sans réglage.
- [ ] T034 [P] Renvois de contrats : `specs/015-taches-avancement/contracts/mcp-tools.md` vers `specs/023-statut-fin-tache/contracts/mcp-tools.md`. Dans `CLAUDE.md`, la feature en cours devient la 023, devant la 022.
- [ ] T035 Dérouler `quickstart.md` Q1 à Q5 sur le jar des gates et noter les résultats ; Q6 (AURORA réel) revient au mainteneur.
- [ ] T036 Gates complètes, `bash ci/check-eval.sh` vert. Cocher les tâches `[X]` ; commit `docs(023): README, contrats et quickstart`.

---

## Dependencies & Execution Order

- **Phase 1** (T001–T002), puis **Phase 2** (T003–T011) : bloquent tout.
- **US1** (T012–T016) puis **US2** (T017–T018) : US2 réutilise le réglage lu par US1 (`workflow(featureId)`) ; les fichiers sont communs (`AgentTasks`, `TaskSync`), d'où l'ordre.
- **US3** (T019–T024) : après la Phase 2 ; indépendante de US1 et US2 (fichiers disjoints), donc parallélisable avec elles.
- **US4** (T025–T030) : après US1 et US2 (cible de fin, statuts finis).
- **Polish** (T031–T036) : après tout ; T032 a besoin de US3 (rubrique) et de US4.

```text
Phase 1 ─► Phase 2 ─┬─► US1 ─► US2 ─► US4 ─┐
                    └─► US3 ───────────────┴─► Polish
```

## Parallel Example

```text
# Phase 2, tests ensemble :
T003 (StatusNameTest) | T004 (TaskWorkflowTest) | T005 (SqliteTaskWorkflowRepositoryIT)

# US3 en parallèle de US1/US2 :
T019 (ManageTaskWorkflowTest) | T020 (TaskWorkflowResourceIT) | T021 (task-workflow.component.spec.ts)

# US4, tests ensemble :
T025 (StoryFollowTest) | T026 (AgentTasksTest) | T027 (AgentFeaturesTest)
```

## Implementation Strategy

1. **MVP** : Phases 1 et 2, US1 et US2. La sous-tâche n'est plus fermée et reste finie ; le réglage
   se pose par l'API.
2. **Incrément 2** : US3, la rubrique dans les réglages du projet.
3. **Incrément 3** : US4, la story suit.
4. **Polish** : MCP, E2E, documentation, gates.

Chaque incrément passe les gates et fait l'objet d'un commit.
