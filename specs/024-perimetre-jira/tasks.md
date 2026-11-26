---
description: "Tâches de la feature 024 : périmètre Jira de spec-writer (label, filtres de la vue, sous-tâches en référentiel, tickets supprimés)"
---

# Tasks: Périmètre Jira de spec-writer

**Input**: `specs/024-perimetre-jira/` (plan.md, spec.md, research.md, data-model.md,
contracts/rest-api.md, contracts/mcp-tools.md, quickstart.md)

**Tests** : obligatoires (constitution III, TDD). Chaque test est écrit et vu échouer avant le
code. JaCoCo ne compte pas les `@QuarkusTest` : `domain` et `application` sont couverts par des
tests unitaires (`TaskWorld`, `JiraWorld`, `FakeIssueTracker`, nouveaux `InMemoryJiraLabels`,
`InMemoryTicketLabels`, `InMemoryLostJiraLinks`). Les IT qui appellent WireMock et ArchUnit se
lancent sous Maven.

**Évaluation** : aucun fichier de `BR/prompts`, `BM/infrastructure/llm` ni du golden set n'est
touché ; `ci/check-eval.sh` reste vert sans nouvelle évaluation (R7).

**Organisation** : la Phase 2 (V36, domaine, ports, labels lus dans Jira) bloque tout. US1 (label
posé) et US2 (filtres de la vue) sont le MVP ; US3 (périmètre MCP) s'appuie sur US1. US4 (sous-tâches
hors recherche) et US5 (suppressions) sont indépendantes.

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

- [X] T001 Écrire `BT/infrastructure/persistence/MigrationV36IT.java` : les trois tables existent ; `project_jira_label.label` refuse une chaîne vide, plus de 255 caractères ; cascade à la suppression d'un projet (`project_jira_label`, `ticket_label`) et d'une session (`lost_jira_link`) ; clé `(project_id, ticket_key, label)` unique.
- [X] T002 Créer `BR/db/migration/V36__jira_scope.sql` (data-model.md) : `project_jira_label (project_id text primary key references project (id) on delete cascade, label text not null check (length(label) between 1 and 255), updated_at text not null)` ; `ticket_label (project_id text not null references project (id) on delete cascade, ticket_key text not null, label text not null check (length(label) between 1 and 255), primary key (project_id, ticket_key, label))` et l'index `ticket_label_by_label (project_id, label)` ; `lost_jira_link (session_id text primary key references session (id) on delete cascade, issue_key text not null, lost_at text not null)`.

---

## Phase 2: Foundational (domaine, ports, labels lus)

### Tests

- [X] T003 [P] Écrire `BT/domain/jira/JiraLabelTest.java` : `JiraLabel.of` fait `strip`, refuse vide, plus de 255 caractères et tout caractère blanc (« spec writer », tabulation) avec `VALIDATION_FAILED` et le message « Un label Jira ne contient pas d'espace et compte au plus 255 caractères. » ; égalité exacte, sensible à la casse.
- [X] T004 [P] Écrire `BT/infrastructure/persistence/SqliteJiraScopeIT.java` : `SqliteJiraLabelRepository` (find, save en upsert, delete) ; `SqliteTicketLabels` (`replace`, `add` idempotent, `has`, `retain`, `remove`) ; `SqliteLostJiraLinks` (`record` en upsert, `find`, `clear`).
- [X] T005 [P] Étendre `BT/infrastructure/jira/JiraTasksIT.java` et `JiraKnowledgeSyncIT.java` (WireMock) : `labels` est demandé dans `fields` des recherches suivies et lu dans `TrackedIssue.labels()` (vide si absent) ; `createIssue` et `createSubtask` envoient `fields.labels` ; `addLabel` envoie `PUT /rest/api/2/issue/{key}` avec `{"update":{"labels":[{"add":"spec-writer"}]}}` ; un refus 400 `errors.labels` donne `Kind.REJECTED` avec le détail.

### Implémentation

- [X] T006 [P] Créer `BM/domain/jira/JiraLabel.java` (record, `of`, `value`) d'après T003.
- [X] T007 [P] Ajouter `labels` (`List<String>`, copie immuable, vide si nul) à `BM/domain/jira/TrackedIssue.java`, avec un constructeur qui garde l'ancienne signature (sans labels) ; ajouter à `BM/domain/port/IssueTracker.java` `addLabel(endpoint, key, label)` et un paramètre `List<String> labels` à `createIssue` et `createSubtask` (surcharges sans labels gardées pour les appelants existants).
- [X] T008 Mettre à jour `BM/infrastructure/jira/JiraApi.java` et `JiraRestClient.java` : `labels` dans `TRACKED_FIELDS` ; lecture de `fields.labels` ; `fields.labels` à la création (fusion avec les labels de `extraFields`, sans doublon) ; `addLabel` par `updateIssue` avec `update.labels.add`. Faire passer T005.
- [X] T009 [P] Créer les ports `BM/domain/port/JiraLabelRepository.java`, `TicketLabels.java`, `LostJiraLinks.java` (data-model.md) et leurs adaptateurs `BM/infrastructure/persistence/jira/SqliteJiraLabelRepository.java`, `SqliteTicketLabels.java`, `SqliteLostJiraLinks.java` (JDBC natif, littéral `null` à part, comme `SqliteTaskWorkflowRepository`). Faire passer T004.
- [X] T010 [P] Créer les doubles `BT/support/InMemoryJiraLabels.java`, `InMemoryTicketLabels.java`, `InMemoryLostJiraLinks.java` ; étendre `BT/support/FakeIssueTracker.java` (`labels` par ticket, `addLabel` noté `label KEY x`, `labelFailure`, labels reçus à la création) et `BT/support/JiraWorld.java` (les trois doubles, publics).
- [X] T011 Synchronisation : dans `BM/application/knowledge/SynchronizeKnowledge.java`, remplacer les labels de chaque ticket relu (`TicketLabels.replace`) et les retirer avec l'index (`retain`) ; câbler les ports dans `BM/infrastructure/config/UseCaseProducer.java`. Test dans `BT/application/knowledge/SynchronizeKnowledgeTest.java` (labels écrits pour chaque ticket relu).

**Checkpoint** : `MigrationV36IT`, `JiraLabelTest`, `SqliteJiraScopeIT`, `JiraTasksIT`, `JiraKnowledgeSyncIT` verts.

---

## Phase 3: User Story 1 - Reconnaître dans Jira les tickets de spec-writer par un label (Priority: P1) 🎯 MVP

**Goal**: le label réglé est posé sur ce que spec-writer crée et reprend (FR-001 à FR-006).

**Independent Test**: régler `spec-writer`, envoyer une spécification, valider ses tâches, créer une spécification depuis un ticket existant : chaque ticket porte le label.

### Tests US1

- [X] T012 [P] [US1] Écrire `BT/application/project/ManageJiraLabelTest.java` : lire (nul sans réglage), enregistrer (validation de T003), retirer ; projet inconnu `PROJECT_NOT_FOUND`.
- [X] T013 [P] [US1] Étendre `BT/application/jira/CreateJiraTicketTest.java` : avec label, la story est créée avec `labels` = labels saisis + label, sans doublon, et `ticket_label` reçoit le label ; sans label, aucun ; création refusée sur `errors.labels` : recréation sans label puis `addLabel` ; ajout refusé : ticket créé et `labelWarning` « Le label spec-writer n'a pas pu être posé sur PROJ-12 : … ».
- [X] T014 [P] [US1] Étendre `BT/application/jira/ImportJiraIssueTest.java` : la story reprise reçoit `addLabel` si elle n'a pas le label, rien sinon ; refus : import réussi avec `labelWarning`.
- [X] T015 [P] [US1] Étendre `BT/application/task/TaskDeskTest.java` (ou `ValidateTasksTest.java`) : sous-tâche créée avec le label ; sous-tâche existante reprise reçoit `addLabel` ; un refus donne le message de validation sans arrêter la création des suivantes.
- [X] T016 [P] [US1] Écrire `BT/infrastructure/web/JiraLabelResourceIT.java` : `GET`, `PUT` (400 sur « spec writer »), `DELETE` ; écriture sans `Origin` : 403.
- [X] T017 [P] [US1] Écrire `FE/features/project-settings/jira-label.component.spec.ts` : champ « Label des tickets de spec-writer », aide (« sans espace »), erreur annoncée (`role=alert`), enregistrement annoncé, « Retirer le label » ; changement d'un label existant : avertissement « Les tickets qui portent l'ancien label ne seront plus reconnus. ».

### Implémentation US1

- [X] T018 [US1] Créer `BM/application/project/ManageJiraLabel.java` et `BM/infrastructure/web/JiraLabelResource.java` (`/api/projects/{id}/jira-label`, contrat rest-api.md) ; câbler dans `UseCaseProducer`. Faire passer T012 et T016.
- [X] T019 [US1] Pose du label à la création et à la reprise (R3) dans `BM/application/jira/CreateJiraTicket.java`, `ImportJiraIssue.java` et `BM/application/task/TaskDesk.java` ; `labelWarning` dans les réponses (`BM/infrastructure/web/` des créations, validation des tâches). Faire passer T013 à T015.
- [X] T020 [US1] Créer `FE/core/projects/jira-label.api.ts` et `FE/features/project-settings/jira-label.component.ts` (`sw-jira-label`), placé en tête de la section « Suivi des tâches dans Jira » de `FE/features/project-settings/project-settings.page.ts` ; afficher `labelWarning` là où le résultat de la création et de la validation est annoncé. Faire passer T017 ; mettre à jour `project-settings.page.spec.ts`.

**Checkpoint** : US1 testable seule.

---

## Phase 4: User Story 2 - La vue « Tickets Jira » montre d'abord les tickets utiles (Priority: P1)

**Goal**: filtres « label » et « terminés » sur le premier niveau, en SQL (FR-007 à FR-010, R6).

**Independent Test**: trois stories (labellisée ouverte, labellisée fermée, non labellisée ouverte) : une à l'ouverture, deux avec les terminés, trois sans le filtre du label.

### Tests US2

- [X] T021 [P] [US2] Étendre `BT/infrastructure/knowledge/SqliteListedIssuesIT.java` (ou le créer) : `labelled` et `done` filtrent le premier niveau et le compte ; les sous-tâches d'un ticket retenu restent toutes rendues ; une orpheline est filtrée comme un ticket de premier niveau ; `labelled` sans label réglé est ignoré.
- [X] T022 [P] [US2] Étendre `BT/application/jira/ListProjectIssuesTest.java` et `BT/infrastructure/web/ProjectIssuesResourceIT.java` : défauts `labelled=true`, `done=false` ; réponse avec `label`, `labelled`, `done` (contrat rest-api.md).
- [X] T023 [P] [US2] Étendre le spec Vitest de la vue `FE/features/jira-issues/` : cases « Avec le label spec-writer » (absente sans label) et « Afficher les tickets terminés », état gardé en `sessionStorage` par projet, liste vide avec « Retirer les filtres », pager remis à la première page au changement d'un filtre.

### Implémentation US2

- [X] T024 [US2] `BM/application/jira/ListProjectIssues.java` (`IssueFilters`, label du projet) et `BM/infrastructure/knowledge/SqliteListedIssues.java` (conditions sur `ticket_label` et `status_category` du premier niveau, dans `page` et `count`) ; `BM/infrastructure/web/ProjectIssuesResource.java` (`labelled`, `done`). Faire passer T021 et T022.
- [X] T025 [US2] Front : filtres dans la vue `FE/features/jira-issues/` et son API (`labelled`, `done`), groupe de cases étiqueté, annonce du nombre de tickets ; faire passer T023.

**Checkpoint** : US2 testable seule (labels posés par la synchronisation, Phase 2).

---

## Phase 5: User Story 3 - Les agents de code ne voient que les tickets de spec-writer (Priority: P2)

**Goal**: `JiraScope` filtre features et tâches du MCP (FR-011 à FR-013, R5).

**Independent Test**: deux features prêtes, une story labellisée et une non : `list_features` n'en rend qu'une ; une sous-tâche sans label n'est jamais proposée.

### Tests US3

- [X] T026 [P] [US3] Écrire `BT/application/agent/JiraScopeTest.java` : sans label tout est dans le périmètre ; feature sans ticket lié dans le périmètre ; story avec ou sans le label ; tâche avec ou sans le label ; label posé à la main (lu par la synchronisation) reconnu.
- [X] T027 [P] [US3] Étendre `BT/application/agent/AgentFeaturesTest.java` : `list` écarte la feature hors périmètre ; `get` et `AgentChangeStatus` la refusent avec `NOT_IN_JIRA_SCOPE` et le message du contrat mcp-tools.md.
- [X] T028 [P] [US3] Étendre `BT/application/task/AgentTasksTest.java` : `TaskInfo.inScope` ; `next_task` saute une tâche hors périmètre ; `start_task` et `complete_task` la refusent (`NOT_IN_JIRA_SCOPE`) ; la relecture des tâches (`TaskSync`) remplace les labels des sous-tâches et de la story.

### Implémentation US3

- [X] T029 [US3] Ajouter `NOT_IN_JIRA_SCOPE` (409) à `BM/domain/error/ErrorCode.java` et son message par défaut ; créer `BM/application/agent/JiraScope.java` ; l'appeler dans `ListFeatures`, `GetFeature`, `AgentChangeStatus` (`BM/application/agent/`) et `AgentTasks` (`BM/application/task/`) ; `inScope` dans `TaskInfo` ; `TaskSync` remplace les labels relus ; câbler dans `UseCaseProducer`. Faire passer T026 à T028.
- [X] T030 [US3] Descriptions de `list_features` et `next_task` dans `BM/mcp/Tools.java` (contrat mcp-tools.md) ; étendre `BT/mcp/McpBridgeTest.java`.

**Checkpoint** : US3 testable seule, sur US1.

---

## Phase 6: User Story 4 - Les sous-tâches ne sont plus de la connaissance (Priority: P2)

**Goal**: les sous-tâches hors de toute recherche, gardées comme référence (FR-014 à FR-017, R7).

**Independent Test**: un besoin aux mots d'une sous-tâche : ni tickets proches, ni `search_knowledge` ; la sous-tâche reste sous sa story.

### Tests US4

- [X] T031 [P] [US4] Étendre `BT/infrastructure/knowledge/SqliteKnowledgeIndexIT.java` : `search` (mots et sens) ne rend pas un ticket qui a un lien `PARENT` ; sa story reste rendue ; `all(TICKET)`, `find` et les vecteurs lus par `ReportSubtasks` le gardent.
- [X] T032 [P] [US4] Couvert par `SqliteKnowledgeIndexIT` : toutes les recherches (tickets proches, connaissance, agent, rédaction) passent par `SqliteKnowledgeIndex.search` ; l'index en mémoire des tests unitaires n'a pas les liens. La rubrique « Sous-tâches » lit l'index sans recherche : inchangée.

### Implémentation US4

- [X] T033 [US4] Dans `BM/infrastructure/knowledge/SqliteKnowledgeIndex.java`, écarter des recherches par mots (`byTerms`) et par le sens (`byMeaning` et le chargement des vecteurs) les tickets qui ont un lien `PARENT` dans `ticket_link`. Faire passer T031 et T032 ; vérifier `InMemoryKnowledgeIndex` (même règle si des tests en dépendent).

**Checkpoint** : US4 testable seule.

---

## Phase 7: User Story 5 - Un ticket supprimé dans Jira disparaît de spec-writer (Priority: P2)

**Goal**: retrait à chaque synchronisation, avec garde-fou ; tâches et lien suivent (FR-018 à FR-022, R8).

**Independent Test**: sous-tâche suivie et story liée supprimées, « Resynchroniser Jira » : plus dans la vue ; une tâche de moins ; la page de la spécification dit que la story a été supprimée.

### Tests US5

- [X] T034 [P] [US5] Étendre `BT/application/knowledge/SynchronizeKnowledgeTest.java` : en mode incrémental, `listKeys` puis retrait des tickets disparus (index, liens, labels), compté dans le bilan ; garde-fou : `listKeys` en échec, projet `NOT_FOUND`, liste vide et index non vide : rien retiré, erreur `JIRA` ; un ticket fermé ou annulé reste.
- [X] T035 [P] [US5] Écrire `BT/application/knowledge/ForgetDeletedTicketsTest.java` : la tâche d'une sous-tâche retirée est supprimée, l'avancement et le statut de la spécification recalculés ; le lien d'une spécification vers une story retirée est supprimé et `lost_jira_link` écrit ; un nouveau lien efface la trace (`CreateJiraTicket`, `ImportJiraIssue`).
- [X] T036 [P] [US5] Étendre `BT/application/task/AgentTasksTest.java` : `complete_task` sur une tâche supprimée : `NOT_FOUND` avec « La tâche PROJ-302 n'est plus une tâche de cette feature : elle a peut-être été supprimée dans Jira. »
- [X] T037 [P] [US5] Spec Vitest de la page de version `FE/features/specification/` : bandeau « La story PROJ-12 a été supprimée dans Jira » quand `lostJiraIssueKey` est rendu, avec l'envoi vers Jira de nouveau proposé.

### Implémentation US5

- [X] T038 [US5] `BM/application/knowledge/SynchronizeKnowledge.java` : `listKeys` à chaque synchronisation de Jira, clés retirées calculées avant `retain`, garde-fou ; créer `BM/application/knowledge/ForgetDeletedTickets.java` (tâches, liens, `lost_jira_link`), appelé après le retrait ; message `NOT_FOUND` de `AgentTasks.byKey` ; effacer `lost_jira_link` au nouveau lien ; câbler dans `UseCaseProducer`. Faire passer T034 à T036.
- [X] T039 [US5] `lostJiraIssueKey` dans la réponse de `GET /api/sessions/{id}` (`BM/infrastructure/web/SessionResource.java` ou sa vue) et bandeau dans `FE/features/specification/` ; faire passer T037.

**Checkpoint** : US5 testable seule.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [X] T040 E2E (fait : `us22-hierarchie` pour le label, les filtres et la suppression, `us10-jira-paging` ajusté ; le périmètre MCP reste couvert par les tests unitaires) : étendre `e2e/tests/us22-hierarchie.spec.ts` (label réglé par la rubrique, stories labellisée, fermée et sans label, cases de filtre au clavier, `expectNoA11yViolations`) ; `e2e/tests/us15-taches.spec.ts` (label posé à la création des sous-tâches, `inScope`, feature hors périmètre refusée par la passerelle) ; `e2e/tests/us17-synchro.spec.ts` (ticket retiré de WireMock puis resynchronisation : retiré de la vue, bilan). Mettre à jour les listes de rubriques et de stubs touchées (`us15-navigation`, `fields` des recherches suivies).
- [X] T041 [P] README : rubriques « Projets et connaissance du projet » (sous-tâches référence seulement, suppressions à chaque synchronisation), vue Jira (filtres), « Agents de code (MCP) » (périmètre du label), réglages du projet (label).
- [X] T042 [P] Renvois de contrats : `specs/015-taches-avancement/contracts/mcp-tools.md` et `specs/017-synchro-auto-sources/` vers la 024 ; `CLAUDE.md` : feature en cours 024, devant la 023.
- [X] T043 Dérouler `quickstart.md` Q1 à Q6 sur le jar des gates et noter les résultats.
- [X] T044 Gates complètes, `bash ci/check-eval.sh` vert. Cocher les tâches `[X]` ; commits par groupe (fondation, US1, US2, US3, US4, US5, docs).

---

## Dependencies & Execution Order

- **Phase 1** (T001–T002), puis **Phase 2** (T003–T011) : bloquent tout.
- **US1** (T012–T020) : après la Phase 2.
- **US2** (T021–T025) : après la Phase 2 ; indépendante de US1 (les labels viennent de la synchronisation), parallélisable.
- **US3** (T026–T030) : après US1 (labels posés à la création, `ticket_label` à jour sans synchronisation).
- **US4** (T031–T033) : après la Phase 2 ; fichiers disjoints, parallélisable avec tout.
- **US5** (T034–T039) : après la Phase 2 ; touche `SynchronizeKnowledge` comme T011 et `AgentTasks` comme US3 : à faire après T011 et après US3.
- **Polish** (T040–T044) : après tout.

```text
Phase 1 ─► Phase 2 ─┬─► US1 ─► US3 ─► US5 ─┐
                    ├─► US2 ───────────────┤
                    └─► US4 ───────────────┴─► Polish
```

## Parallel Example

```text
# Phase 2, tests ensemble :
T003 (JiraLabelTest) | T004 (SqliteJiraScopeIT) | T005 (JiraTasksIT, JiraKnowledgeSyncIT)

# US1, tests ensemble :
T012 | T013 | T014 | T015 | T016 | T017

# US2 et US4 en parallèle de US1 :
T021–T025 (vue) | T031–T033 (recherche)
```

## Implementation Strategy

1. **MVP** : Phases 1 et 2, US1 et US2. Les tickets de spec-writer sont reconnaissables dans Jira
   et la vue s'ouvre sur eux, sans les terminés.
2. **Incrément 2** : US3, le périmètre des agents.
3. **Incrément 3** : US4 et US5, recherche sans sous-tâches et suppressions suivies.
4. **Polish** : E2E, documentation, gates.

Chaque incrément passe les gates et fait l'objet d'un commit.
