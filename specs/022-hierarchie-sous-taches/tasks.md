---
description: "Tâches de la feature 022 : hiérarchie des tâches et sous-tâches Jira, dans la vue Jira et la passerelle MCP"
---

# Tasks: Hiérarchie des tâches et sous-tâches Jira

**Input**: `specs/022-hierarchie-sous-taches/` (plan.md, spec.md, research.md, data-model.md,
contracts/rest-api.md, contracts/mcp-tools.md, quickstart.md)

**Tests** : obligatoires (constitution III, TDD). Chaque test est écrit et vu échouer avant le
code. JaCoCo ne compte pas les `@QuarkusTest` : `domain` et `application` sont couverts par des
tests unitaires (`InMemoryTasks`, `FakeIssueTracker`, doubles de `ListedIssues`). Les IT SQLite se
lancent sous Maven ou en dev mode ; les IT qui appellent WireMock et ArchUnit, sous Maven.

**Évaluation** : aucun fichier de `BR/prompts`, `BM/infrastructure/llm` ni du golden set n'est
touché ; `ci/check-eval.sh` doit rester vert sans nouvelle évaluation.

**Organisation** : US1 (vue Jira) et US2 (parent dans le MCP) sont indépendantes ; US3 (consigne
de branche) s'appuie sur le `parentKey` de US2.

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

- [X] T001 Écrire `BT/infrastructure/persistence/MigrationV34IT.java` (modèle : `MigrationV32IT`) : après migration, `spec_task` a la colonne `jira_issue_type`, nulle par défaut, et refuse une valeur de plus de 100 caractères ; le voir échouer.
- [X] T002 Créer `BR/db/migration/V34__task_issue_type.sql` : `alter table spec_task add column jira_issue_type text check (jira_issue_type is null or length(jira_issue_type) <= 100);`, avec un commentaire d'en-tête (022, R4) ; T001 passe.

---

## Phase 2: Foundational

Aucune tâche bloquante commune : US1 touche la vue Jira, US2 et US3 la passerelle MCP.

---

## Phase 3: User Story 1 - Voir les sous-tâches sous leur ticket parent (Priority: P1) 🎯 MVP

**Goal**: la vue « Tickets Jira » ne liste au premier niveau que les tickets sans parent indexé ;
chacun porte ses sous-tâches, repliées et dépliables ; tri, pagination et total portent sur le
premier niveau (FR-001 à FR-008).

**Independent Test**: index avec une story et trois sous-tâches, une sous-tâche orpheline ; la vue
montre la story avec « 3 sous-tâches », repliées ; aucune sous-tâche ailleurs ; l'orpheline est au
premier niveau avec la clé de son parent (quickstart Q1, Q2).

### Tests US1

- [X] T003 [P] [US1] (fait dans `BT/infrastructure/knowledge/SqliteListedIssuesHierarchyIT.java`, jeu de données dédié) Étendre `BT/infrastructure/knowledge/SqliteListedIssuesIT.java` : indexer (par `KnowledgeIndex` et les liens `ticket_link`) une story S-1, ses sous-tâches S-10, S-2, S-3 (lien `PARENT` vers S-1), une sous-tâche orpheline O-5 dont le parent P-9 n'est pas indexé, et un lien `LINK` « Lier » entre deux stories. Vérifier que :
  - `page()` ne rend que S-1, O-5 et les stories du premier niveau ;
  - S-1 porte `subtasks` dans l'ordre numérique S-2, S-3, S-10 ;
  - O-5 porte `parentKey` = P-9 et pas de `subtasks` ;
  - `count()` compte les seuls tickets du premier niveau ;
  - le lien « Lier » ne crée aucune hiérarchie ;
  - une sous-tâche n'a jamais de `subtasks`.
- [X] T004 [P] [US1] Dans le même IT : pour chaque tri (`UPDATED`, `STATUS`, `SYNC`) et dans les deux sens, une page de taille 1 contenant S-1 contient aussi ses trois sous-tâches, et une sous-tâche plus récente que sa story ne remonte pas la story (R3).
- [X] T005 [P] [US1] Étendre `BT/infrastructure/web/ProjectIssuesResourceIT.java` : la réponse JSON de `GET /api/projects/{id}/jira-issues` suit `contracts/rest-api.md`, avec `parentKey` et `subtasks` sur chaque ticket, et `total` du premier niveau.
- [X] T006 [P] [US1] Étendre `BT/application/jira/ListProjectIssuesTest.java` avec un double de `ListedIssues` qui rend des sous-tâches : `IssuePage.issues` les transmet telles quelles, et `total` vient de `count()`.
- [X] T007 [P] [US1] Étendre `FE/features/jira-issues/jira-issues.page.spec.ts`. Vérifier que :
  - une ligne avec 3 `subtasks` affiche un bouton `aria-expanded="false"`, de nom « Afficher les 3 sous-tâches de S-1 » ;
  - un clic montre trois lignes en retrait, avec un lien vers `/jira/S-2`… et le texte masqué « Sous-tâche de S-1 », et passe le bouton à `aria-expanded="true"`, de nom « Masquer les 3 sous-tâches de S-1 » ;
  - un second clic les masque ;
  - une ligne sans sous-tâche n'a pas de bouton ;
  - une ligne avec `parentKey` affiche « Sous-tâche de P-9 » ;
  - changer de page ou de tri replie tout.

### Implémentation US1

- [X] T008 [US1] Dans `BM/domain/jira/ListedIssue.java`, ajouter `parentKey` (nul) et `subtasks` (liste, copiée, vide par défaut). Garder un constructeur à l'ancienne signature pour les appelants existants. Documenter : « `parentKey` : parent d'une sous-tâche du premier niveau dont le parent n'est pas indexé (FR-005) ; `subtasks` : sous-tâches indexées, classées par clé dans l'ordre numérique, vides pour une sous-tâche ».
- [X] T009 [US1] Dans `BM/infrastructure/knowledge/SqliteListedIssues.java`, compléter `FROM` :
  - ajouter le filtre du premier niveau `and not exists (select 1 from ticket_link p join knowledge_item pk on pk.project_id = p.project_id and pk.ref = p.target_key and pk.kind = 'TICKET' and pk.chunk = 0 where p.project_id = k.project_id and p.source_key = k.ref and p.kind = 'PARENT')` ;
  - joindre le parent non indexé (`left join ticket_link op on op.project_id = k.project_id and op.source_key = k.ref and op.kind = 'PARENT'`) pour `parentKey`.

  `count()` utilise le même `FROM`. T003 (premier niveau, orpheline, count) et T004 passent.
- [X] T010 [US1] Dans `SqliteListedIssues.page()`, ajouter une seconde requête sur les clés de la page, en paramètres liés : `select … from ticket_link l join knowledge_item k … where l.project_id = ?1 and l.kind = 'PARENT' and l.target_key in (…)`, avec la même jointure `linked_jira_ticket` pour `sync` et `sessionId`.
  - Rattacher chaque sous-tâche à son parent, classée par clé dans l'ordre numérique (préfixe, puis numéro après le dernier `-`) ;
  - ne pas lancer de seconde requête pour une page vide.

  T003 passe en entier.
- [X] T011 [US1] Dans `BM/infrastructure/web/ProjectIssuesResource.java` (ou son DTO), sérialiser `parentKey` et `subtasks` selon `contracts/rest-api.md`, une sous-tâche ayant `subtasks: []` et `parentKey: null`. T005 et T006 passent.
- [X] T012 [P] [US1] Dans `FE/core/jira/jira.model.ts`, ajouter à l'issue listée `parentKey: string | null` et `subtasks: ListedIssue[]`, avec un commentaire (022).
- [X] T013 [US1] Dans `FE/features/jira-issues/jira-issues.page.ts` :
  - signal `open = signal<ReadonlySet<string>>(new Set())`, vidé au changement de page ou de tri ;
  - dans la cellule « Ticket » d'un parent, un `mat-icon-button` (chevron SVG `aria-hidden`) avec `aria-expanded`, `aria-controls` vers les lignes des sous-tâches, l'`aria-label` de T007 et le nombre visible « 3 » ;
  - les lignes `tr.subtask` en retrait (padding en `rem`, filet gauche `--sw-rule`), clé précédée d'un `span.sw-visually-hidden` « Sous-tâche de S-1 » ;
  - pour une orpheline, une mention visible « Sous-tâche de P-9 » sous le titre.

  Contrastes WCAG AA. T007 passe.
- [X] T014 [US1] (fait dans un E2E dédié, `e2e/tests/us22-hierarchie.spec.ts`, pour ne pas décaler les comptes de us10) Étendre `e2e/tests/us10-jira-paging.spec.ts`. Donner à trois des 120 tickets simulés `fields.parent = { key: <story> }` et `issuetype: { name: 'Sous-tâche' }`, et à un quatrième un parent absent de la liste. Après synchronisation, vérifier que :
  - la story affiche le bouton « Afficher les 3 sous-tâches de … » et que ses sous-tâches ne sont pas des lignes du premier niveau ;
  - le dépliage les montre ;
  - le pager compte 117 tickets (120 − 3) ;
  - l'orpheline affiche « Sous-tâche de … » ;
  - `expectNoA11yViolations` passe, dépliée et repliée.

  Ajuster les attentes existantes de comptes (« 1 à 50 sur 120 »).

**Checkpoint** : US1 livrable seule (MVP). Commit `feat(jira): sous-tâches repliées sous leur ticket parent dans la vue Jira (022, US1)`.

---

## Phase 4: User Story 2 - L'agent de code sait qu'il travaille sur une sous-tâche, et de quel ticket (Priority: P1)

**Goal**: chaque tâche rendue à l'agent porte `issueType` et `parentKey` ; `get_feature` porte
`jiraTicket` ; Jira injoignable, les dernières valeurs restent (FR-009 à FR-011, FR-014).

**Independent Test**: feature liée à une story, tâches créées ; `next_task` rend une tâche avec
`parentKey` = la story et `issueType` ; Jira coupé, les deux restent (quickstart Q3).

### Tests US2

- [X] T015 [P] [US2] Étendre `BT/infrastructure/persistence/TaskPersistenceIT.java` : `issueTypes(sessionId)` est vide au départ ; `type(taskId, "Sous-Tâche (AGILE)")` est relu ; `type(taskId, null)` le retire ; un `update()` de la tâche ne le perd pas.
- [X] T016 [P] [US2] Étendre `BT/application/task/AgentTasksTest.java` (session liée à `FACT-120` dans `TaskWorld`). Vérifier que :
  - `next`, `start`, `complete` et `forFeature` rendent `parentKey = "FACT-120"` pour chaque tâche ;
  - `issueType` vaut le type de `FakeIssueTracker.tracked` (« Sous-tâche ») après la relecture ;
  - un changement de type dans Jira est relu à l'appel suivant ;
  - Jira injoignable (`w.tracker` en panne réseau), `parentKey` et le dernier `issueType` restent.
- [X] T017 [P] [US2] Écrire ou étendre `BT/application/agent/AgentFeaturesTest.java` (ou un `GetFeatureTest` unitaire si `AgentFeaturesTest` n'appelle pas `GetFeature`). Vérifier que :
  - `jiraTicket` vaut `{key, issueType, parentKey}` pour une feature liée à une sous-tâche indexée (type et lien `PARENT` lus par le port de l'index) ;
  - `parentKey` est nul pour une story ;
  - `issueType` et `parentKey` sont nuls pour un ticket non indexé ;
  - `jiraTicket` est nul sans ticket lié ;
  - `jiraIssueKey` est inchangé.
- [X] T018 [P] [US2] Étendre `BT/infrastructure/web/AgentTaskResourceIT.java` et `BT/infrastructure/web/AgentResourceIT.java` : le JSON de `next_task`, `start_task` et `get_feature` contient `parentKey`, `issueType` et `jiraTicket` selon `contracts/mcp-tools.md`, sans retirer un champ existant (`jiraIssueKey`, `outcome`, `description`).

### Implémentation US2

- [X] T019 [US2] Dans `BM/domain/port/TaskRepository.java`, ajouter `Map<UUID, String> issueTypes(UUID sessionId)` et `void type(UUID taskId, String issueType)`, documentés (« libellé du type de la sous-tâche dans Jira, relu avec le statut, 100 caractères au plus, V34 »). Les implémenter dans `BM/infrastructure/persistence/task/SqliteTaskRepository.java` (null écrit en littéral SQL, comme `describe`) et dans `BT/support/InMemoryTasks.java`. T015 passe.
- [X] T020 [US2] Dans `BM/application/task/TaskSync.reread`, garder le type relu (`TrackedIssue.issueType()`, coupé à 100 caractères, nul si vide) quand il change, à côté de la description. Jira injoignable : rien n'est écrit.
- [X] T021 [US2] Dans `BM/application/task/AgentTasks.java`, ajouter `issueType` et `parentKey` à `TaskInfo` (après `description`, documentés). `parentKey` = `desk.tickets.findBySession(featureId)` → `issueKey()`, lu une fois par appel et passé à `info()`. Lire les types une fois par appel (`issueTypes`), sans requête par tâche. T016 passe.
- [X] T022 [US2] (aucun port nouveau : `KnowledgeIndex.find` et `TicketLinkRepository.of` suffisent) Port de lecture du ticket indexé. Si `KnowledgeIndex` ne l'offre pas déjà, ajouter à un port existant de l'index une méthode `Optional<IndexedTicket> ticket(UUID projectId, String key)` rendant le type (`issue_type` du chunk 0) et le parent (lien `PARENT`). L'implémenter dans l'adaptateur SQLite de l'index, avec un test dans son IT.
- [X] T023 [US2] Dans `BM/application/agent/GetFeature.java`, ajouter `jiraTicket` à `FeatureContent` : un record `JiraTicket(String key, String issueType, String parentKey)`, construit depuis le ticket lié et T022, et nul sans ticket lié. Câbler la dépendance dans `BM/infrastructure/config/UseCaseProducer.java`. T017 et T018 passent.
- [X] T024 [US2] Dans `BM/mcp/Tools.java`, compléter les descriptions de `get_feature` (`jiraTicket` : type et parent du ticket lié), `next_task` et `start_task` (chaque tâche porte `parentKey` et `issueType`). Ne pas encore parler de `branch`. Étendre `BT/mcp/McpBridgeTest.java` (`tools/list` contient ces mentions).

**Checkpoint** : commit `feat(taches): type et ticket parent de chaque tâche et du ticket d'une feature dans le MCP (022, US2)`.

---

## Phase 5: User Story 3 - Une seule branche pour une story et ses sous-tâches (Priority: P1)

**Goal**: les instructions du serveur MCP et les réponses de `next_task` et `start_task` donnent
la consigne de branche avec la clé du parent (FR-012, FR-013).

**Independent Test**: `initialize` mentionne la branche du parent ; `next_task` (TASK) et
`start_task` portent `branch` avec la clé du parent ; `complete_task` n'en porte pas (quickstart Q4).

### Tests US3

- [X] T025 [P] [US3] Étendre `BT/application/task/AgentTasksTest.java`. Vérifier que :
  - `next` (TASK) et `start` portent `branch` = « FACT-124 est une sous-tâche de FACT-120 : si une branche est nécessaire, travaillez sur celle de FACT-120 (reprenez-la si elle existe). Toutes les sous-tâches de FACT-120 s'y font. » ;
  - `complete` a `branch` nul ;
  - `NONE_LEFT` et `ALL_HELD` ont `branch` nul.
- [X] T026 [P] [US3] Étendre `BT/mcp/McpBridgeTest.java` : la réponse à `initialize` contient la consigne de `contracts/mcp-tools.md` (« se fait sur la branche de son ticket parent », « jamais une branche par sous-tâche ») ; les descriptions de `next_task` et `start_task` mentionnent `branch`.

### Implémentation US3

- [X] T027 [US3] Dans `BM/application/task/AgentTasks.java`, ajouter `branch` à `NextAnswer` et `TaskAnswer` (dernier champ, documenté). Une méthode statique `branchRule(String key, String parentKey)` rend le texte de T025, ou `null` sans parent. Le remplir pour `next` (TASK) et `start`, pas pour `complete`. T025 passe.
- [X] T028 [US3] Dans `BM/mcp/McpBridge.java`, compléter `instructions` avec la phrase de `contracts/mcp-tools.md`. Dans `BM/mcp/Tools.java`, compléter les descriptions de `next_task` et `start_task`. T026 passe.
- [X] T029 [US3] (fait dans `us15-taches`) Étendre `e2e/tests/us15-taches.spec.ts` (ou `us11-agents.spec.ts`, là où la passerelle est appelée). Vérifier que :
  - `next_task` rend `task.parentKey` = la story liée et un `branch` qui la cite ;
  - `start_task` aussi ;
  - `complete_task` n'a pas de `branch` ;
  - les instructions d'`initialize` mentionnent la branche du parent.

**Checkpoint** : commit `feat(mcp): consigne de travailler sur la branche du ticket parent d'une sous-tâche (022, US3)`.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T030 [P] Mettre à jour `README.md` :
  - vue Jira : les sous-tâches repliées sous leur parent, le compte du premier niveau ;
  - passerelle MCP : `parentKey`, `issueType`, `jiraTicket`, et la consigne de branche.
- [X] T031 [P] Mettre à jour `specs/015-taches-avancement/contracts/mcp-tools.md` et `specs/017-synchro-auto-sources/contracts/rest-api.md` d'un renvoi vers les contrats de la 022.
- [X] T032 [P] Mettre à jour `CLAUDE.md` : la feature en cours est `specs/022-hierarchie-sous-taches/` (vue Jira par premier niveau, lien `PARENT` de `ticket_link`, V34, `parentKey` et `branch` dans le MCP), devant la 021.
- [X] T033 Vérifier la visite guidée (`FE/features/shell/tour-stops.ts`, cible `data-tour="jira-table"`) : l'arrêt de la vue Jira reste valide, et son texte dit que les sous-tâches se déplient sous leur parent si le texte décrit le tableau.
- [X] T034 Dérouler `quickstart.md` Q1 à Q4 sur le jar des gates ; noter les résultats dans `quickstart.md`. Q5 (agent réel) est laissé au mainteneur.
- [X] T035 Gates complètes (voir en tête), `bash ci/check-eval.sh` vert sans nouvelle évaluation. Cocher les tâches `[X]` au fil de l'eau ; commit final `docs(022): README, contrats et quickstart`.

---

## Dependencies & Execution Order

- **Phase 1** (T001–T002) : avant US2 (colonne `jira_issue_type`). US1 n'en dépend pas.
- **US1** (T003–T014) : indépendante. Ordre interne : tests T003–T007, puis T008 → T009 → T010 → T011, puis T012 → T013 → T014.
- **US2** (T015–T024) : après la Phase 1. Ordre interne : tests T015–T018, puis T019 → T020 → T021, puis T022 → T023, puis T024.
- **US3** (T025–T029) : après T021 (`parentKey`). Ordre interne : T025–T026 → T027 → T028 → T029.
- **Polish** (T030–T035) : après les trois stories.

```text
Phase 1 ──► US2 ──► US3 ──► Polish
US1 ───────────────────────► Polish
```

## Parallel Example

```text
# US1, tests écrits ensemble :
T003, T004 (SqliteListedIssuesIT) | T005 (ProjectIssuesResourceIT) | T006 (ListProjectIssuesTest) | T007 (page.spec.ts)

# US2, tests écrits ensemble :
T015 (TaskPersistenceIT) | T016 (AgentTasksTest) | T017 (AgentFeaturesTest) | T018 (Agent*ResourceIT)

# US1 et US2 peuvent avancer en parallèle (fichiers disjoints) ; seul README et CLAUDE.md sont communs, en Polish.
```

## Implementation Strategy

1. **MVP** : Phase 3 (US1). La vue Jira montre la hiérarchie, livrable et utile seule.
2. **Incrément 2** : Phase 1, puis US2. L'agent connaît le parent et le type.
3. **Incrément 3** : US3. La consigne de branche s'appuie sur `parentKey`.
4. **Polish** : documentation, visite guidée, quickstart, gates.

Chaque incrément passe les gates et fait l'objet d'un commit.
