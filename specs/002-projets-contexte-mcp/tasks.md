---

description: "Liste des tâches d'implémentation de la feature 002"
---

# Tasks: Projets, contexte de projet et accès des agents de code

**Input**: Design documents from `specs/002-projets-contexte-mcp/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Tests**: obligatoires. La constitution v2.0.0 impose le TDD (principe III) et un E2E par
parcours (principe IV). Dans chaque phase, les tâches de test passent **avant** l'implémentation :
elles doivent échouer avant qu'on écrive le code.

**Organization**: les tâches sont groupées par user story, dans l'ordre de priorité de la spec :
- P1 : US1 (projets) et US2 (contexte) ;
- P2 : US3 (suppression) et US4 (Jira paginé) ;
- P3 : US5 (agents de code, statut, verrou).

**Préalable de branche** : la feature part de `main`. Elle suppose que la PR #2 (évaluation LLM,
cache NVD) et la PR du texte riche (`feature/003-texte-riche`, champ `details`, FR-012b de 001)
sont fusionnées. Rebaser `feature/005-projets-mcp` sur `main` avant T001.

## Format: `[ID] [P?] [Story] Description`

- **[P]** : parallélisable (fichiers différents, aucune dépendance sur une tâche non terminée)
- **[Story]** : user story concernée (US1 à US5)

## Path Conventions

Les chemins sont relatifs à la racine du dépôt. Les abréviations sont celles de 001 :

- `B/` = `backend/src/main/java/dev/specwriter/`
- `BR/` = `backend/src/main/resources/`
- `BT/` = `backend/src/test/java/dev/specwriter/`
- `BTR/` = `backend/src/test/resources/`
- `F/` = `frontend/src/app/`
- `E/` = `e2e/tests/`

Les règles transverses de 001 s'appliquent : domaine sans framework (ArchUnit), aucun contenu ni
secret dans les journaux, erreurs au format `Problem`, axe sur chaque écran. S'y ajoutent :

- **Dépendances** : aucune dépendance Maven qui ne soit pas déjà dans `~/.m2` (proxy du poste de
  développement). Seul jsoup 1.23.2 est ajouté (T002).
- **Prompts** : toute modification de prompt ou de `B/infrastructure/llm/**` impose de relancer
  l'évaluation (`ci/check-eval.sh`, T058).
- **Boucle de développement** : dev mode Quarkus piloté par le serveur MCP Quarkus, comme dans
  `CLAUDE.md`. Avant chaque commit : `./mvnw -B verify -DskipDependencyCheck`, puis les E2E sur le
  jar.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose** : dépendance, règles d'architecture, jeux d'essai partagés

- [X] T001 Rebaser `feature/005-projets-mcp` sur `main` après la fusion de la PR #2 et de la PR du texte riche. Vérifier `./mvnw -B -o verify -DskipDependencyCheck` au vert avant toute modification.
- [X] T002 Ajouter `org.jsoup:jsoup` 1.23.2 (MIT) à `backend/pom.xml`, avec la propriété `jsoup.version`. Déclarer la licence dans `NOTICE`. Vérifier qu'elle se résout hors ligne (`./mvnw -o`).
- [X] T003 [P] Étendre `BT/architecture/HexagonalArchitectureTest.java` avec deux règles :
  - le paquet `dev.specwriter.mcp..` ne dépend d'aucune classe de `domain`, `application` ou `infrastructure` ;
  - tout cas d'usage d'écriture d'une session, dont la liste est tenue dans le test, appelle `SpecificationLock.requireEditable` (research.md R7).
  
  Les règles échouent tant que le paquet et le garde n'existent pas : les marquer `@Disabled("T047")` jusqu'à T047.
- [X] T004 [P] Créer le site Antora de test `e2e/fixtures/jira/__files/antora/` (WireMock ne sert que les fichiers de `__files`) : un `sitemap.xml` de type index renvoyant vers `sitemap-billing.xml`, 30 pages HTML avec `<article class="doc">` (vocabulaire, acteurs, règles de facturation), une page qui répond 500, un lien vers un autre hôte et une page sans `article.doc`. Le servir par WireMock dans `e2e/fixtures/jira/mappings/antora.json` et le copier en dossier dans `BTR/antora-site/`.
- [X] T005 [P] Créer le projet Jira simulé `FACT` dans `e2e/fixtures/jira/mappings/fact.json` : 120 tickets, `GET /rest/api/2/search` paginé (`startAt`, `maxResults=25`, `total=120`, tri `updated DESC`), le ticket `FACT-1` lié dans le scénario P9, et une réponse JQL avec mot-clé (60 tickets) pour P10.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose** : l'entité Projet et le rattachement des sessions, dont toutes les stories dépendent.

**⚠️ CRITICAL** : aucune story ne commence avant la fin de cette phase.

- [X] T006 [P] Tests du domaine dans `BT/domain/project/ProjectTest.java`, cas par cas :
  - nom « 1 à 80 caractères, sans espace en début ni en fin », unicité « sans tenir compte de la casse » via `nameKey` ;
  - description « 1 000 caractères maximum, facultative » ;
  - `jiraProjectKey` « facultative, format `[A-Z][A-Z0-9_]{1,9}` » ;
  - projet Jira effectif = clé du projet, sinon projet par défaut des réglages.
- [X] T007 Créer `B/domain/project/Project.java`, `ProjectName.java` et `JiraProjectKey.java` (records validés), ainsi que le port `B/domain/port/ProjectRepository.java` (`save`, `find`, `findByNameKey`, `list`, `delete`, `count`). Faire passer T006.
- [X] T008 Écrire la migration `BR/db/migration/V7__projects.sql` :
  - table `project` (`id`, `name`, `name_key` UNIQUE, `description`, `jira_project_key`, `created_at`, `updated_at`, avec des `CHECK` de longueur conformes à data-model.md) ;
  - création du « Projet par défaut » ;
  - `ALTER TABLE session` pour ajouter `project_id` `REFERENCES project`, valorisé par le projet par défaut pour toutes les sessions existantes. Pas de reconstruction de la table : sous `foreign_keys=ON`, dans la transaction de Flyway, son `DROP` supprimerait en cascade questions, versions et tickets liés. La colonne est ajoutée nullable (SQLite refuse une colonne `REFERENCES` avec une valeur par défaut non nulle), puis rendue obligatoire par deux triggers ;
  - table `app_settings` à une ligne, avec `current_project_id` (`ON DELETE SET NULL`) : il n'existe pas de table `settings` commune.
  
  Test `BT/infrastructure/persistence/MigrationV7IT.java` : une base V6 avec 3 sessions et leurs versions migre sans perte, et toutes les sessions sont dans le projet par défaut (SC-001).
- [X] T009 Ajouter `projectId` au record `B/domain/session/Session.java`, propagé par toutes ses méthodes de copie, à `SessionEntity` et `SessionJpaRepository`. Implémenter `ProjectJpaRepository` (`B/infrastructure/persistence/project/`). Adapter `StartSession` pour créer la session dans le projet courant, et les tests existants (`InMemorySessions`, `Specs`, `JiraWorld`, golden set) avec un projet par défaut.
- [X] T010 Résoudre le projet Jira effectif en un point unique, `JiraAccess.effectiveProject(Session)` (`B/application/jira/JiraAccess.java`), et l'utiliser dans `PreviewJiraTicket`, `CreateJiraTicket` (`require(sessionId)`) et `UnifiedSearch` (partie Jira, `targetForCurrentProject()`). `ImportJiraIssue` lit un ticket par sa clé : il n'a pas besoin du projet Jira, et sa session va dans le projet courant (T009). Tests dans `BT/application/jira/JiraAccessTest.java` : clé du projet, ou défaut en son absence (FR-006).

**Checkpoint** : toutes les sessions ont un projet ; le comportement de 001 est inchangé (suite complète au vert).

---

## Phase 3: User Story 1 - Ranger ses spécifications par projet (Priority: P1) 🎯 MVP

**Goal** : créer, modifier, supprimer des projets, choisir le projet courant, déplacer une
spécification ; les actions Jira utilisent la clé du projet.

**Independent Test** : quickstart P1 à P4.

### Tests for User Story 1

- [X] T011 [P] [US1] `BT/application/project/ManageProjectsTest.java` :
  - création, renommage, nom en double refusé (`PROJECT_NAME_TAKEN`) ;
  - suppression avec `MOVE` ou `DELETE` ;
  - « Le dernier projet restant ne peut pas être supprimé » (`LAST_PROJECT`) ;
  - suppression refusée pendant une opération IA en cours ;
  - projet courant conservé, et premier projet par nom si le projet courant est supprimé.
- [X] T012 [P] [US1] `BT/application/project/MoveSessionTest.java` : la session et toutes ses versions changent de projet ; la recherche locale du projet cible la trouve, celle du projet source ne la trouve plus.
- [X] T013 [P] [US1] `BT/infrastructure/web/ProjectResourceIT.java` :
  - CRUD de `contracts/openapi.yaml` (`/api/projects`, `/api/projects/current`, `/api/sessions/{id}/project`) ;
  - vérification de la clé Jira par WireMock : une clé introuvable donne `jiraKeyWarning`, sans bloquer l'enregistrement (FR-007) ;
  - liste et recherche filtrées sur le projet courant (FR-005).
- [X] T014 [P] [US1] E2E `E/us7-projects.spec.ts`, scénarios quickstart P1 à P4 avec axe ; l'aperçu de ticket indique `FACT` pour « Facturation » et `PROJ` sinon.

### Implementation for User Story 1

- [X] T015 [US1] Cas d'usage `B/application/project/` : `CreateProject`, `UpdateProject`, `DeleteProject`, `SelectCurrentProject` et `MoveSession`, câblés dans `B/infrastructure/config/UseCaseProducer.java`. Codes d'erreur `PROJECT_NAME_TAKEN`, `LAST_PROJECT` et `PROJECT_NOT_FOUND` dans `ErrorCode`.
- [X] T016 [US1] Filtrer `ManageSessions` (liste) et `FtsLocalSearch` sur `project_id` du projet courant (`B/infrastructure/persistence/search/FtsLocalSearch.java`).
- [X] T017 [US1] Créer `B/infrastructure/web/ProjectResource.java` (contrat `/api/projects*` et `/api/sessions/{id}/project`), avec la vérification de la clé Jira via `IssueTracker`.
- [X] T018 [P] [US1] Front :
  - `F/core/projects/projects.api.ts` et `projects.store.ts` (projet courant, signal) ;
  - `F/features/projects/projects.page.ts` (liste, création, édition, suppression avec choix déplacer ou supprimer, et confirmation nommée) ;
  - sélecteur de projet courant dans la barre d'en-tête (`F/app.ts`) ;
  - action « Déplacer vers… » sur la page de spécification.

**Checkpoint** : US1 utilisable seule.

---

## Phase 4: User Story 2 - Un contexte de projet qui améliore la rédaction (Priority: P1)

**Goal** : une fiche de contexte générée depuis la documentation Antora et les tickets du projet,
relue, modifiable, régénérable, utilisée et citée par toutes les opérations LLM.

**Independent Test** : quickstart P5 à P8.

### Tests for User Story 2

- [X] T019 [P] [US2] `BT/domain/project/ProjectContextTest.java` :
  - contexte « 20 000 caractères maximum » ;
  - `contextHash` recalculé ;
  - origine `NONE` → `GENERATED` → `EDITED` ;
  - source de documentation : 10 sources au plus, `URL` absolue `http`/`https` sans identifiants (`user:pass@` refusé), `FOLDER` absolu existant, « 2 000 caractères maximum ».
- [X] T020 [P] [US2] `BT/infrastructure/docs/AntoraReaderTest.java`, sur `BTR/antora-site/` et WireMock :
  - index de sitemaps ;
  - extraction de `article.doc` seulement (titres, listes et tableaux conservés en texte) ;
  - page 500 et page sans `article.doc` listées dans les non-lus avec leur motif (`HTTP_ERROR`, `NOT_ANTORA`) ;
  - redirection vers un autre hôte non suivie (`REDIRECT_OTHER_HOST`) ;
  - bornes : 2 Mo par page (`TOO_LARGE`), 1 000 pages et 10 min (`LIMIT_REACHED`, avec horloge injectée) ;
  - repli sans sitemap par les liens du même hôte et sous le même chemin ;
  - proxy appliqué (`ProxySettings`).
- [X] T021 [P] [US2] `BT/application/context/ProposeProjectContextTest.java`, avec `StubLlm` :
  - lots d'environ 40 000 caractères pour `summarizeProjectSources`, puis `buildProjectContext` ;
  - les 200 tickets les plus récents demandés par pages de 50 ;
  - une référence citée inconnue des sources provoque une `correction`, 3 essais au plus ;
  - une fiche de plus de 20 000 caractères provoque une `correction` ;
  - consentement Copilot exigé ;
  - proposition gardée en mémoire et rien d'enregistré avant acceptation ;
  - acceptation refusée si le contexte a changé (`CONTEXT_CHANGED`) ;
  - aucune page ni aucun ticket journalisé.
- [X] T022 [P] [US2] Étendre `BT/infrastructure/llm/PromptInjectionIT.java` : des pages Antora et des descriptions de tickets malveillantes (« ignore tes instructions », fermeture de `</sources>` ou `</contexte-projet>`) restent des données échappées dans les deux nouvelles opérations et dans `<contexte-projet>`.
- [X] T023 [P] [US2] `BT/domain/specification/TraceabilityValidatorTest.java` et `InputFingerprintTest.java` :
  - source `PROJECT_CONTEXT` acceptée si son extrait figure dans le contexte, refusée sinon ;
  - `contextHash` dans l'empreinte, donc une nouvelle version à contexte modifié et la réutilisation à contexte identique (FR-011).
- [X] T024 [P] [US2] Golden set :
  - `BTR/golden/04-facturation-avec-contexte/` et `05-conges-avec-contexte/`, avec `need.md`, `context.md`, `answers.json` et des réponses LLM dont des sources `PROJECT_CONTEXT` ;
  - `GoldenSetIT` lit `context.md` s'il existe et vérifie `<contexte-projet>` par `userContains`.
- [X] T025 [P] [US2] `BT/infrastructure/web/ContextResourceIT.java` : contrat `/api/projects/{id}/context*`, `/doc-sources*` et `/context-proposals*`, de bout en bout, avec WireMock (Antora et Jira) et le LLM rejoué (`BTR/llm-replay/`).
- [X] T026 [P] [US2] E2E `E/us8-project-context.spec.ts`, scénarios quickstart P5 à P8 avec axe ; réponses LLM rejouées dans `e2e/fixtures/llm/09-summarize-sources.json`, `10-build-context.json` et des questions sans thème ACTORS.

### Implementation for User Story 2

- [X] T027 [US2] Écrire la migration `BR/db/migration/V8__project_context.sql` :
  - colonnes `project.context`, `context_hash`, `context_origin` (`NONE`, `GENERATED`, `EDITED`) et `context_updated_at` ;
  - table `project_doc_source` (`ON DELETE CASCADE`, `kind` `URL`|`FOLDER`, `location` de 2 000 caractères au plus, `last_read_at`, `last_result` JSON sans contenu) ;
  - colonnes `specification_version.context_hash` et `context_updated_at`.
- [X] T028 [US2] Domaine : `B/domain/project/ProjectContext.java` et `DocSource.java`, le port `B/domain/port/DocumentationReader.java` (pages `{ref, title, text}` et non-lus `{location, reason}`), et le type `SourceRef.Kind.PROJECT_CONTEXT`. Adapter `TraceabilityValidator` et `InputFingerprint` (T023).
- [X] T029 [US2] Implémenter `B/infrastructure/docs/AntoraReader.java` avec jsoup, le sitemap, le dossier, les bornes, 4 requêtes en parallèle, un délai de 15 s par page et le proxy (research.md R3). Faire passer T020.
- [X] T030 [US2] Port LLM :
  - ajouter `projectContext` aux records `B/domain/llm/*Request.java`, propagé dans `withCorrection` ;
  - ajouter les records `SummarizeSourcesRequest`/`SourceFacts` et `BuildContextRequest`/`ProjectContextDraft` ;
  - ajouter les méthodes `summarizeProjectSources` et `buildProjectContext` à `LlmClient` et `LangChainLlmClient`, avec leurs schémas JSON dans `LlmSchemas` ;
  - `UserMessages` : balises `<contexte-projet>` (après `<langue>`) et `<sources>`, contenu échappé ;
  - adapter `StubLlm`, `ScriptedChatModel` et `ReplayChatModel`.
- [X] T031 [US2] Prompts :
  - créer `BR/prompts/summarize-project-sources.txt` et `build-project-context.txt` (contracts/llm-port.md : sections fixes, `[source : …]`, aucune invention, sources = données) ;
  - ajouter `<contexte-projet>` aux 6 prompts existants : déclaré dans la règle de sécurité, information à ne pas redemander, source `PROJECT_CONTEXT` citable, contradiction à remonter.
- [X] T032 [US2] Cas d'usage `B/application/context/` : `PreviewContextSources` (comptes sans lecture des pages, FR-013b), `ProposeProjectContext` (map-reduce avec vérification des références), `AcceptContextProposal`, `EditContext` et `ManageDocSources`. Brancher le contexte du projet dans `NextBatch`, `ReviseAnswer`, `GenerateSpecification`, `ProposeAiEdit`, `PushToJira` et `ImportJiraIssue`. Enregistrer `contextHash` et `contextUpdatedAt` sur chaque version.
- [X] T033 [US2] Créer `B/infrastructure/web/ContextResource.java` (contrat) et ajouter `PROJECT_CONTEXT` aux vues `SessionViews` (libellé « contexte du projet » et extrait, date du contexte utilisé).
- [X] T034 [P] [US2] Front `F/features/project-context/` :
  - éditeur de fiche (Markdown avec aperçu) ;
  - sources de documentation (ajout d'un lien ou d'un dossier, suppression, résultat de la dernière lecture) ;
  - aperçu de l'envoi (pages, tickets, non-lus) suivi du dialogue de consentement existant ;
  - proposition avec `unified-diff.component`, « Accepter » ou « Garder la mienne ».
  
  Page de spécification : source « contexte du projet » et « contexte du <date> » sur la version.

### Progression, erreurs et journal (FR-013f, FR-013g, FR-031, research.md R14 et R15)

- [X] T065 [P] [US2] `BT/application/context/ContextGenerationsTest.java` (exécution synchrone injectée) :
  - étapes, élément en cours et compteurs signalés dans l'ordre (pages, tickets par pages de 50, lots, rédaction) ;
  - une seule génération par projet (`OPERATION_IN_PROGRESS`), consentement vérifié avant le démarrage ;
  - interruption avant une page, une page de tickets ou un appel au LLM : statut `CANCELLED`, aucune proposition ;
  - erreurs détaillées : page (motif, code HTTP), source injoignable, Jira, correction demandée à l'IA, cause d'un échec ; aucun contenu ;
  - génération terminée consultable 30 minutes, puis oubliée.
- [X] T066 [P] [US2] Étendre `BT/infrastructure/docs/AntoraReaderTest.java` : chaque page signalée à l'écouteur, `httpStatus` 500 sur `tarifs-2025.html`, avertissement journalisé avec l'adresse et le motif, sans texte de page.
- [X] T067 [P] [US2] `BT/infrastructure/logging/JournalHandlerTest.java` et `LogJournalTest.java` : une entrée JSON par ligne, cause et pile expurgées (jeton `ghp_…`, en-tête `Authorization`), niveau WARN au moins, rotation à 2 Mo sur 5 fichiers, relecture des 500 dernières du plus récent au plus ancien, filtre par niveau, ligne illisible ignorée.
- [X] T068 [US2] Backend de la génération suivie :
  - port `GenerationProgress` et `DocumentationReader.read(source, listener)` ; `Unread.httpStatus` ;
  - `ProposeProjectContext` informe la progression et lit le drapeau d'interruption ;
  - `B/application/context/ContextGenerations.java` (une par projet, thread virtuel, 30 minutes) ;
  - `ContextResource` : `POST /context-generations` (202), `GET` et `DELETE /context-generations/current`, à la place de `POST /context-proposals` ;
  - erreurs journalisées en WARN sans contenu. Faire passer T065 et T066, adapter `ContextResourceIT`.
- [X] T069 [US2] Journal : `B/infrastructure/logging/JournalHandler.java` (enregistré au démarrage, `logs/journal.jsonl`), `LogJournal.java` et `B/infrastructure/web/JournalResource.java` (`GET /api/journal`). Faire passer T067 et `JournalResourceIT`.
- [X] T070 [P] [US2] Front :
  - page du contexte : progression (étape, élément en cours, barre `role="progressbar"`, annonces `aria-live` limitées aux changements d'étape), « Interrompre », reprise au retour sur la page, détail des erreurs avec un lien vers le journal ;
  - écran `F/features/journal/` (route `/journal`, lien dans la navigation) : filtre par niveau, cause et pile dépliables, « Copier », « Actualiser ».
- [X] T071 [US2] E2E `E/us8-project-context.spec.ts` : scénarios quickstart P5a et P5b avec axe ; délai WireMock sur les pages pour observer la progression.

### Deux fiches et questions de clarification (FR-008a, FR-013h, FR-013i, research.md R16)

- [X] T072 [P] [US2] Tests du domaine et de la persistance :
  - `ProjectContextTest` : deux fiches, hash combiné, date la plus récente ;
  - `BT/domain/project/ContextQuestionTest.java` : numérotation `Q-n`, états, longueurs ;
  - `ProjectPersistenceIT` : fiche technique et questions (V9), cascade à la suppression du projet.
- [X] T073 [P] [US2] Étendre `ProposeProjectContextTest` et `ContextGenerationsTest` :
  - deux fiches, références vérifiées dans chacune, `Q-n` connues ;
  - 10 questions au plus, une question sur un point couvert par une réponse ou passée est refusée par une `correction` ;
  - réponse et passage enregistrés aussitôt, avec un numéro ;
  - révision sans relire les sources ni Jira, réponses citées ; génération expirée → `NOT_FOUND` ;
  - acceptation par fiche, `CONTEXT_CHANGED` par fiche.
- [X] T074 [P] [US2] `PromptInjectionIT` et `TraceabilityValidatorTest` : `<contexte-fonctionnel>`, `<contexte-technique>`, `<reponses>` et `<questions-passees>` échappés ; source `PROJECT_CONTEXT` acceptée si l'extrait figure dans l'une des deux fiches.
- [X] T075 [P] [US2] Golden set `BTR/golden/06-api-avec-contexte-technique/` (`context.md`, `technical.md`, exigences techniques issues de la fiche technique) ; `GoldenSetIT` lit `technical.md`.
- [X] T076 [US2] Migration `BR/db/migration/V9__context_sheets.sql` (colonnes `technical_context*`, table `project_context_question`), domaine (`ContextSheet`, `Project.technicalContext`, `ContextQuestion`, port `ContextQuestionRepository`) et persistance JPA. Faire passer T072.
- [X] T077 [US2] Port LLM et prompts :
  - `ProjectContextInput(functional, technical)` dans les 6 requêtes, avec les deux balises ;
  - nouvelles catégories de faits ;
  - `BuildContextRequest` avec les deux fiches actuelles, les réponses et les questions passées ; `ProjectContextDraft(functional, technical, questions)` ;
  - `LlmSchemas`, `UserMessages` ;
  - les 8 prompts. Faire passer T074.
- [X] T078 [US2] Application :
  - `ProposeProjectContext` (deux fiches, questions, faits gardés pour la révision) ;
  - `ContextGenerations.revise` ;
  - `AnswerContextQuestion` et `ManageContextQuestions` ;
  - `AcceptContextProposal` par fiche, `EditContext` par fiche ;
  - les opérations sur les spécifications reçoivent les deux fiches ;
  - `InputFingerprint` et version avec le hash combiné. Faire passer T073 et T075.
- [X] T079 [US2] `ContextResource` : `PUT /context/{sheet}`, réponses aux questions, `revise`, `accept` par fiche, `GET`/`PUT /context-questions`. Adapter `ContextResourceIT`.
- [X] T080 [P] [US2] Front, page du contexte :
  - deux onglets « Fonctionnel » et « Technique », chacun avec son éditeur, son aperçu et son origine ;
  - proposition fiche par fiche, avec diff, « Accepter » ou « Garder la mienne » ;
  - questions de clarification (suggestions, réponse libre, « Passer »), puis « Réviser la proposition » ;
  - historique des réponses `Q-n`, modifiables ;
  - spécification : l'extrait « contexte du projet » nomme sa fiche.
- [X] T081 [US2] E2E `E/us8-project-context.spec.ts` : scénarios quickstart P5c et P5d avec axe ; réponses LLM rejouées (`10-build-context.json` avec questions, `09b-revise-context.json`, rangé avant `10-` pour être retenu en premier).

**Checkpoint** : US1 et US2 utilisables ; le golden set avec contexte passe.

---

## Phase 5: User Story 3 - Supprimer une version ou une spécification locale (Priority: P2)

**Goal** : suppression définitive d'une version ou d'une spécification, sans toucher à Jira.

**Independent Test** : quickstart P9.

### Tests for User Story 3

- [ ] T035 [P] [US3] `BT/application/specification/DeleteVersionTest.java` :
  - les numéros des autres versions sont inchangés, et un numéro supprimé n'est pas réattribué (`nextVersionNumber`) ;
  - le lien Jira est reporté sur la dernière version restante et l'état de synchronisation est recalculé ;
  - après suppression de la dernière version, la session repasse à l'état prêt à générer avec son besoin et ses réponses (FR-015) ;
  - la suppression est refusée pendant une opération IA en cours (`OPERATION_IN_PROGRESS`) ;
  - les propositions en mémoire de la session sont purgées.
- [ ] T036 [P] [US3] `BT/infrastructure/web/DeletionIT.java` :
  - `DELETE /api/sessions/{id}/versions/{n}` et `DELETE /api/sessions/{id}` ;
  - l'élément disparaît de la liste, de l'historique, de la comparaison, de la recherche FTS et de l'export ;
  - **aucune requête WireMock Jira** n'est faite (SC-005).
- [ ] T037 [P] [US3] E2E `E/us9-deletion.spec.ts`, scénario quickstart P9 : confirmation nommée, annulation sans effet, avertissement « Jira n'est pas modifié » pour une spec liée, axe.

### Implementation for User Story 3

- [ ] T038 [US3] Écrire la migration `BR/db/migration/V10__version_numbering.sql` : colonne `session.next_version_number`, valorisée à `max(number)+1`. Adapter `GenerateSpecification`, `AcceptAiEdit`, `ImportJiraIssue` et `PushToJira` pour numéroter par ce compteur.
- [ ] T039 [US3] Créer `B/application/specification/OperationRegistry.java` (opérations IA en cours par session, ouvertes et fermées par les cas d'usage IA) et le cas d'usage `DeleteVersion`. Ajouter à `ManageSessions.delete` le refus pendant une opération et la purge des propositions. Port `SpecificationRepository.delete(sessionId, number)`.
- [ ] T040 [US3] `B/infrastructure/web/VersionResource.java` : `DELETE /api/sessions/{id}/versions/{n}`. Codes `OPERATION_IN_PROGRESS`, puis `SPECIFICATION_LOCKED`, ajouté en T047.
- [ ] T041 [P] [US3] Front : action « Supprimer cette version » dans `F/features/specification/versions-panel.component.ts`, et « Supprimer la spécification » sur la page de spécification et dans la liste des sessions. Utiliser `F/shared/ui/confirm-dialog.component.ts` avec le nom, le numéro et l'avertissement Jira. Après suppression, rediriger vers la dernière version restante ou vers la liste.

**Checkpoint** : US3 utilisable seule.

---

## Phase 6: User Story 4 - Parcourir les tickets Jira page par page (Priority: P2)

**Goal** : la liste paginée des tickets du projet Jira et la liste unique de recherche paginée par
25.

**Independent Test** : quickstart P10.

### Tests for User Story 4

- [ ] T042 [P] [US4] `BT/application/search/PagedUnifiedSearchTest.java` :
  - fusion par date décroissante de deux sources simulées ;
  - curseur `{localOffset, jiraOffset}` stable : la page 2 continue l'ordre, sans doublon ni élément sauté ;
  - un élément « les deux » apparaît une seule fois ;
  - 25 tickets Jira demandés au plus par page ;
  - position « 26 à 50 sur N » et total approximatif ;
  - en cas d'erreur Jira, la page locale est rendue avec `jiraError` et le curseur Jira est inchangé (FR-022).
- [ ] T043 [P] [US4] `BT/infrastructure/jira/JiraIssuePagingIT.java` (WireMock `FACT`) : `startAt`, `maxResults=25`, JQL `project = FACT ORDER BY updated DESC` nettoyée, `total` lu ; mot-clé échappé dans la JQL.
- [ ] T063 [P] [US4] `BT/infrastructure/jira/JiraRateLimiterTest.java` (horloge et attente simulées, research.md R13) :
  - les 8 premières requêtes n'attendent pas, la 9ᵉ attend jusqu'à « plus ancienne + 1 s » ;
  - sur une rafale de 50 requêtes, aucune fenêtre d'une seconde n'en contient plus de 8 ;
  - une attente au-delà de 10 s échoue en `RATE_LIMITED`, une interruption en `UNREACHABLE` ;
  - dans `JiraRestClientIT` (WireMock), 20 recherches et une nouvelle tentative après 429 : au plus 8 requêtes reçues par seconde (FR-022a).
- [ ] T044 [P] [US4] E2E `E/us10-jira-paging.spec.ts`, scénario quickstart P10 : pages suivante et précédente, position affichée, recherche de 70 résultats triée par date, Jira coupé avec la page conservée et un message, axe.

### Implementation for User Story 4

- [ ] T045 [US4] Ajouter `IssueTracker.searchPage(endpoint, jql, startAt, max)` et `JiraSearchPage.total` (`B/domain/jira/`). Créer le cas d'usage `B/application/jira/ListProjectIssues.java` et la ressource `GET /api/jira/projects/{key}/issues`. Réécrire `UnifiedSearch` avec un curseur opaque, encodé en base64url d'un JSON signé par HMAC avec une clé de processus, pour qu'un curseur modifié soit refusé. `GET /api/search?cursor=` suit le contrat.
- [ ] T064 [US4] Créer `B/infrastructure/jira/JiraRateLimiter.java` (fenêtre glissante, file équitable, attente plafonnée à 10 s, seuil `specwriter.jira.max-requests-per-second=8`) et l'appeler au début de `JiraRestClient.once()`. Faire passer T063. Indépendante du reste d'US4.
- [ ] T046 [P] [US4] Front :
  - `F/features/jira-issues/jira-issues.page.ts` (liste paginée des tickets du projet courant, lien vers le détail et l'import) ;
  - pagination de `F/features/search/` avec une pile de curseurs pour la page précédente, la position et le message d'erreur Jira sans perdre la page ;
  - un composant partagé `F/shared/ui/pager.component.ts`, accessible au clavier, avec annonce `aria-live` de la position.

**Checkpoint** : US4 utilisable seule.

---

## Phase 7: User Story 5 - Un agent de code récupère les features à implémenter (Priority: P3)

**Goal** : le statut d'implémentation et le verrou, puis l'accès des agents par la passerelle MCP
stdio, avec le journal.

**Independent Test** : quickstart P11 et P12.

### Tests for User Story 5

- [ ] T047 [P] [US5] `BT/domain/session/ImplementationStatusTest.java` et `SpecificationLockTest.java` :
  - le tableau des transitions de data-model.md, par rôle (utilisateur ou agent) ;
  - `IMPLEMENTED` exige `confirmUnlock` pour l'utilisateur et est interdit à l'agent ;
  - `implementedAt` est renseigné puis effacé ;
  - `requireEditable` lève `SPECIFICATION_LOCKED`.
  
  Retirer le `@Disabled` des règles ArchUnit de T003.
- [ ] T048 [P] [US5] `BT/application/specification/LockedSpecificationTest.java` :
  - sur une session `IMPLEMENTED`, sont refusés avec `SPECIFICATION_LOCKED` : réponse, révision, conclusion, génération, proposition et acceptation IA, envoi, fusion, reprise et import Jira, suppression de version ;
  - la suppression de la session reste possible ;
  - une acceptation IA démarrée avant le passage à `IMPLEMENTED` n'est pas enregistrée ;
  - `EvolveSpecification` crée une session `DRAFT` dans le même projet, avec `evolvedFromSessionId` et le besoin = Markdown de la dernière version ;
  - une nouvelle version ne change jamais le statut (FR-026).
- [ ] T049 [P] [US5] `BT/infrastructure/platform/AgentEndpointFileTest.java` :
  - fichier `rw-------` (POSIX) avec `{port, secret}` et un secret de 256 bits ;
  - persistant d'un lancement à l'autre, réécrit à la régénération, supprimé à la désactivation ;
  - absent des journaux. Étendre `E/secrets-at-rest.spec.ts` pour qu'il ne le trouve ni dans la base ni dans les journaux.
- [ ] T050 [P] [US5] `BT/infrastructure/web/AgentResourceIT.java` :
  - contrat `/api/agent/**` : la liste des features ne contient que `READY` et `IN_PROGRESS` avec au moins une version ;
  - `get_feature` renvoie un Markdown identique à l'export (SC-007) et la fiche technique du projet (`technicalContext`, FR-008a) ;
  - transitions de l'agent, `TRANSITION_NOT_ALLOWED` sinon ;
  - journal `agent_access_log`, sans contenu ;
  - **refus** : sans secret, avec l'ancien secret, avec un en-tête `Origin`, avec le cookie `sw_launch` seul, et secret présenté à `/api/**` (SC-008) ;
  - comparaison à temps constant (`MessageDigest.isEqual`).
- [ ] T051 [P] [US5] `BT/mcp/McpBridgeTest.java` : échanges JSON-RPC enregistrés pour `initialize` (`protocolVersion` `2025-06-18`, `capabilities.tools`), `notifications/initialized` sans réponse, `ping`, `tools/list` (4 outils et leurs `inputSchema`), `tools/call` de chaque outil vers un serveur HTTP de test, et méthode inconnue (`-32601`). Il vérifie aussi :
  - le fichier absent donne `SPEC_WRITER_UNAVAILABLE` ;
  - après un 401, le fichier est relu et la requête retentée une seule fois ;
  - stdout ne contient que du JSON-RPC, et stderr ne contient ni secret ni contenu.
- [ ] T052 [P] [US5] E2E `E/us11-agents.spec.ts` et le client de test `e2e/mcp-client.ts`, qui lance `java -cp backend/target/spec-writer-runner.jar dev.specwriter.mcp.McpBridge` avec le dossier de données de l'E2E. Scénarios quickstart P11 et P12 avec axe : verrou et évolution, activation, appels MCP, journal, régénération, puis refus.

### Implementation for User Story 5

- [ ] T053 [US5] Écrire la migration `BR/db/migration/V11__implementation_status_agents.sql` :
  - colonnes `session.implementation_status` (`DRAFT` par défaut, `READY`, `IN_PROGRESS`, `IMPLEMENTED`), `implemented_at` et `evolved_from_session_id` (`ON DELETE SET NULL`) ;
  - `settings.agent_access_enabled` (`false` par défaut) ;
  - table `agent_access_log` (`agent_name` de 80 caractères au plus, `operation` `LIST_PROJECTS`|`LIST_FEATURES`|`GET_FEATURE`|`SET_STATUS`, `project_id` et `session_id` en `ON DELETE SET NULL`, `old_status`, `new_status`).
  
  Purge des entrées de plus de 90 jours au démarrage.
- [ ] T054 [US5] Domaine et application :
  - `B/domain/session/ImplementationStatus.java` et `SpecificationLock.java` ;
  - garde appelé par tous les cas d'usage d'écriture listés en research.md R7, avec revérification avant enregistrement dans `AcceptAiEdit` et `GenerateSpecification` ;
  - cas d'usage `ChangeStatus` (utilisateur) et `EvolveSpecification` ;
  - ressources `PUT /api/sessions/{id}/status` et `POST /api/sessions/{id}/evolutions`.
  
  Faire passer T047 et T048.
- [ ] T055 [US5] Accès des agents :
  - `B/infrastructure/platform/AgentEndpointFile.java` (écriture au démarrage, régénération, suppression) ;
  - `B/infrastructure/web/AgentSecretFilter.java` (Bearer, refus de tout `Origin`, temps constant, chemin `/api/agent/**` seulement), et exclusion de `/api/agent/**` dans `LocalSecurityFilter` pour le cookie, sans affaiblir le contrôle `Host` ;
  - cas d'usage `B/application/agent/` `ListProjectsForAgent`, `ListFeatures`, `GetFeature` et `AgentChangeStatus`, avec un port `AgentAccessLog` ;
  - ressources `AgentResource` et `/api/settings/agent-access*` (état, commande et extrait `mcpJson` sans secret, activation, régénération, journal paginé).
  
  Faire passer T049 et T050.
- [ ] T056 [US5] Passerelle `B/mcp/` :
  - `McpBridge.java` (`main`, sans Quarkus ni CDI) : lecture de `agent-endpoint.json` selon la résolution du dossier de données de `UserDirectories`, dupliquée sans dépendance pour respecter la règle ArchUnit de T003 ;
  - `JsonRpc.java` (lecture ligne à ligne de stdin, écriture sur stdout) ;
  - `Tools.java` (les 4 outils de `contracts/mcp-tools.md`, un client `java.net.http`) ;
  - le nom `clientInfo.name` est transmis dans l'en-tête `X-Agent-Name`, tronqué à 80 caractères.
  
  Faire passer T051. Ajouter le lanceur `spec-writer-mcp` dans `jreleaser.yml` (`jpackage` `--add-launcher`, main `dev.specwriter.mcp.McpBridge`).
- [ ] T057 [P] [US5] Front :
  - statut d'implémentation sur la page de spécification (sélecteur, confirmation pour quitter « implémentée », actions verrouillées avec leur raison, « Créer une évolution ») et dans la liste des sessions ;
  - carte « Accès des agents » dans `F/features/settings/` : activation, commande et extrait `mcpJson` à copier, « Régénérer le secret » avec confirmation, journal paginé avec `pager.component`.

**Checkpoint** : les 5 stories sont utilisables.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [ ] T058 Étendre la suite d'évaluation (`BT/eval/`, research.md R10) :
  - le juge reçoit le contexte et un verdict `DEJA_DANS_LE_CONTEXTE` (`BTR/eval/prompts/judge-questions.txt`) ;
  - chaque cas avec `context.md` (et `technical.md`) est joué avec et sans contexte ;
  - nouvelles métriques `contextQuestionRate` (minimum : 5 % au plus) et `questionReduction` (au moins 20 %) dans `EvalRuns`, `EvalBaseline`, `baseline.json` et `ci/check-eval.sh`, avec un test d'empreinte identique.
  
  Relancer `./mvnw -pl backend verify -Peval`, avec l'accord de l'utilisateur car cela consomme des requêtes Copilot, et committer `last-run.json` (SC-002, SC-003).
- [ ] T059 [P] Documentation : README (projets, contexte depuis Antora, statut, agents MCP avec l'extrait de configuration pour Claude Code, Copilot et Cursor) et CONTRIBUTING (passerelle et tests MCP). Dans 001, renvoyer vers les amendements 002 depuis FR-017 et FR-031.
- [ ] T060 [P] Performance : `BT/performance/PagingPerformanceIT.java` (page de recherche et page Jira en moins de 3 s au 95ᵉ centile avec WireMock et 2 000 tickets, SC-006) et `AgentLatencyIT` (liste puis contenu en moins de 5 s, SC-007).
- [ ] T061 Essai manuel SC-004a sur un vrai site Antora d'environ 300 pages : durée et références échantillonnées, résultat consigné dans `specs/002-projets-contexte-mcp/checklists/validation.md`. Essai de `spec-writer-mcp` depuis l'application installée avec Claude Code.
- [ ] T062 Exécuter les scénarios quickstart P1 à P12 et la suite complète (`./mvnw -B verify`, E2E, `ci/check-eval.sh`), puis cocher les tâches et ouvrir la PR.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup** (Phase 1) : T001 d'abord, qui suppose les PR #2 et texte riche fusionnées.
- **Foundational** (Phase 2) : dépend de Setup et bloque toutes les stories.
- **US1** (Phase 3) : dépend de Foundational.
- **US2** (Phase 4) : dépend de Foundational, et d'US1 pour l'écran de projet qui accueille le contexte (T034 après T018).
- **US3** (Phase 5) : dépend de Foundational seulement.
- **US4** (Phase 6) : dépend de Foundational (projet Jira effectif, T010).
- **US5** (Phase 7) : dépend de Foundational. Le verrou (T054) complète la suppression de version d'US3 (T040) ; l'accès des agents a besoin des projets (US1).
- **Polish** (Phase 8) : T058 après US2 ; le reste après toutes les stories.

### User Story Dependencies

```text
Setup ─► Foundational ─┬─► US1 ─┬─► US2 ─────────────► Polish (T058…)
                       │        └─► US5 (agents) ─────►
                       ├─► US3 ──► (verrou US5 T054) ─►
                       └─► US4 ──────────────────────►
```

### Within Each User Story

- Les tests d'abord : ils doivent échouer.
- Ensuite domaine, migration, application, infrastructure, web, puis front.
- Un commit par groupe logique, avec la tâche cochée `[X]` (`CLAUDE.md`).

### Parallel Opportunities

- Setup : T003, T004 et T005 en parallèle.
- Dans chaque story, toutes les tâches de test marquées [P] en parallèle, puis le front [P] en parallèle du backend dès que le contrat est fixé.
- Après Foundational, US3 et US4 peuvent avancer en parallèle d'US1 et US2.

---

## Parallel Example: User Story 2

```bash
# Tests d'abord, en parallèle :
Task: "T019 ProjectContextTest"
Task: "T020 AntoraReaderTest (BTR/antora-site, WireMock)"
Task: "T021 ProposeProjectContextTest"
Task: "T022 PromptInjectionIT (sources et contexte)"
Task: "T023 TraceabilityValidatorTest et InputFingerprintTest"
Task: "T024 golden set 04 et 05 avec contexte"

# Puis, en parallèle du backend une fois le contrat fixé :
Task: "T034 front project-context"
```

## Parallel Example: User Story 5

```bash
Task: "T047 ImplementationStatusTest et SpecificationLockTest"
Task: "T049 AgentEndpointFileTest"
Task: "T050 AgentResourceIT (refus SC-008)"
Task: "T051 McpBridgeTest (échanges JSON-RPC)"
```

---

## Implementation Strategy

### MVP First (User Story 1)

1. Setup, puis Foundational : migration V7 et projet Jira effectif.
2. US1 : projets, projet courant, déplacement, clé Jira.
3. **S'arrêter et valider** : quickstart P1 à P4, puis PR.

### Incremental Delivery

1. US1, puis la PR « projets ».
2. US2, puis la PR « contexte de projet ». L'évaluation est à relancer (T058), car les prompts changent.
3. US3 et US4, ensemble ou séparément : deux PR courtes.
4. US5, puis la PR « agents MCP », et T061 sur l'installeur.

### Notes

- Une PR par story garde la CI (job `nvd` puis `verify`) et la relecture courtes.
- L'évaluation LLM n'est obligatoire qu'aux PR qui touchent aux prompts ou au code LLM (US2, et T058).
