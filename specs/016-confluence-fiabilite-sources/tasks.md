---
description: "Tâches de la feature 016 : Confluence, source et date des extraits, fiabilité des sources"
---

# Tasks: Confluence, source et date des extraits, fiabilité des sources

**Input**: `specs/016-confluence-fiabilite-sources/` (plan.md, spec.md, research.md R1 à R10,
data-model.md, contracts/rest-api.md, contracts/llm-tags.md, contracts/confluence-client.md,
quickstart.md)

**Tests** : obligatoires (constitution III, TDD). Chaque test est écrit et vu échouer avant le code.
JaCoCo ne compte pas les `@QuarkusTest` : `domain` et `application` sont couverts par des tests
unitaires avec des doubles de `ConfluenceClient`, `ReliabilityRepository`, `KnowledgeIndex` et
`ClockPort`. Les IT WireMock (`Confluence*IT`) et les règles ArchUnit se lancent sous Maven.

**Évaluation réelle** : elle appelle le vrai Copilot (environ 50 minutes) ; elle se lance après
T076, puis `ci/check-eval.sh`. Les consignes et le code LLM modifiés changent l'empreinte :
`last-run.json` est commité avec le passage.

**Organisation** : l'ordre de réalisation suit le plan. US2 (source et date) vient d'abord : elle
vaut pour les sources existantes et prépare US3. US1 (Confluence) est indépendante d'US2 et reste
le MVP fonctionnel de la demande. US3 (fiabilité) s'appuie sur US2 ; elle prend tout son sens avec
US1, mais se teste avec SharePoint et Antora seuls.

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

---

## Phase 1: Setup

- [X] T001 Vérifier la base : branche `feature/020-confluence-fiabilite` à jour de `feature/019-taches-avancement`, gates vertes avant tout changement (`/tmp/gates.sh base016`).
- [X] T002 [P] Arbre Confluence de démonstration pour WireMock dans `e2e/fixtures/confluence/` : serveur sous `/confluence` ; racine `pageId=100` (« Remboursements »), 12 pages sur trois niveaux, une page restreinte (`403`), une page hors de l'arbre (`pageId=900`) ; réponses `user/current`, `content/{id}?expand=version,space`, `content/search?cql=ancestor…` paginée par 5, `content/{id}?expand=body.view,version` (HTML avec deux sections `h2`) ; README des mappings.

---

## Phase 2: Foundational (bloque toutes les histoires)

- [X] T003 Test `BT/infrastructure/persistence/MigrationV27IT.java` : tables `confluence_settings` (une seule ligne, `id = 1`), `confluence_page` (`version >= 1`, clé `(source_id, page_id)`, cascade à la suppression de la source), `project_source_reliability` (`origin IN ('JIRA','ANTORA','DOCUMENTS','CONFLUENCE','ARCHITECTURE','SPECIFICATIONS')`, `level IN ('REFERENCE','INTERPRETATION','TO_CONFIRM')`, clé `(project_id, origin)`) ; `project_doc_source` reconstruite avec `kind` dont `CONFLUENCE`, `reliability` (nul ou CONFLUENCE seulement, trois valeurs), `page_id` et `title` (`length(title) <= 500`) réservés à CONFLUENCE ; sources existantes conservées à l'identique.
- [X] T004 Migration `BR/db/migration/V27__confluence_reliability.sql` (data-model.md, reconstruction de `project_doc_source` comme V15 : table `_v27`, copie, suppression, renommage, index) ; T003 vert.
- [X] T005 [P] Test `BT/domain/knowledge/SourceOriginTest.java` et `ProvenanceTest.java` : huit origines, `adjustable()` vrai pour JIRA, ANTORA, DOCUMENTS, CONFLUENCE, ARCHITECTURE, SPECIFICATIONS ; `Provenance(origin, updatedAt, level)` avec `updatedAt` nul (« date inconnue ») et `level` nul seulement pour SHEET et ATTACHMENT.
- [X] T006 [P] `BM/domain/knowledge/SourceOrigin.java`, `Reliability.java` (`REFERENCE` > `INTERPRETATION` > `TO_CONFIRM`, `max`), `Provenance.java` ; T005 vert.

**Checkpoint** : migration appliquée, types de provenance prêts.

---

## Phase 3: User Story 2 - L'IA connaît la source et la date de chaque extrait (P1)

**Goal** : chaque extrait transmis à l'IA (recherches de l'agent, génération, modification,
discussion) porte sa source et sa date de mise à jour ; l'écran des étapes de recherche les montre.

**Independent Test** : projet avec des extraits Jira, Antora et SharePoint aux dates connues ;
une question à l'agent montre source et date de chaque résultat, et le rejeu reçoit `source` et
`maj` sur chaque `<source>`.

### Tests for User Story 2

- [X] T007 [P] [US2] Test `BT/application/knowledge/ProvenanceResolverTest.java` (table de data-model.md) : `TICKET` → JIRA ; `ARCH_ELEMENT` → ARCHITECTURE ; préfixe `docs:<source>/` → DOCUMENTS ; `source_id` d'une `DocSource` URL ou FOLDER → ANTORA ; date = `source_updated_at` ou nulle, jamais `indexed_at` (FR-012).
- [X] T008 [P] [US2] Étendre `BT/application/knowledge/SynchronizeKnowledgeTest.java` (ou le test des dossiers de documents) : un document de dossier garde la date de modification du fichier dans `source_updated_at` ; une page Antora lue en HTTP garde `Last-Modified` quand le serveur l'envoie, la date du fichier pour un dossier local.
- [X] T009 [P] [US2] Étendre `BT/application/conversation/ConversationSearchTest.java` : chaque `Found` porte `origin` et `updatedAt` ; une spécification du projet a l'origine SPECIFICATIONS et la date de sa version.
- [X] T010 [P] [US2] Étendre les tests de `UserMessages` (`BT/infrastructure/llm/UserMessagesTest.java` ou `AgentToolsTest.java`) : attributs `source` (`jira`, `antora`, `sharepoint`, `confluence`, `architecture`, `specification`, `fiche-generale`, `document-joint`) et `maj` (`AAAA-MM-JJ` ou `inconnue`) sur `<source>`, `<document>`, `<adr>`, `<ticket>`, `<section>` ; valeurs échappées ; message antérieur sans ces attributs inchangé ailleurs (contracts/llm-tags.md).
- [X] T011 [P] [US2] Test front `FE/features/conversation/conversation-thread.component.spec.ts` : chaque résultat d'une étape de recherche affiche sa source et sa date (« Confluence · 3 mars 2026 », « date inconnue ») ; un message enregistré avant 016 (champs nuls) s'affiche sans erreur.

### Implementation for User Story 2

- [X] T012 [US2] `BM/application/knowledge/ProvenanceResolver.java` (lit les `DocSource` du projet une fois par recherche) ; T007 vert.
- [X] T013 [US2] Dates à l'indexation dans `BM/application/knowledge/SynchronizeKnowledge.java` (documents : `FileEntry.modifiedAt`) et `BM/infrastructure/docs/AntoraReader.java` (`Last-Modified`, date du fichier en dossier local), portées par `DocumentationReader.Page` ; T008 vert.
- [X] T014 [US2] `ConversationSearch.Found` et `BM/domain/conversation/TurnSource.java` gagnent `origin` et `updatedAt` (et `level`, nul tant qu'US3 n'est pas faite), facultatifs à la lecture des messages persistés ; constructeurs compatibles ; T009 vert.
- [X] T015 [US2] `BM/infrastructure/llm/UserMessages.java` : attributs `source` et `maj` dans `sources(...)` et `knowledge(...)` ; `ProjectKnowledge.Entry` porte la `Provenance` (renseignée par `SelectKnowledge`) ; T010 vert.
- [X] T016 [US2] Consigne de l'agent `BR/prompts/converse-agent.txt` et des rédactions : une phrase sur la lecture de `source` et `maj` (même source : le plus récent prime, écart signalé ; `inconnue` n'est ni ancien ni récent), FR-014 ; `PromptRulesTest` vert.
- [X] T017 [US2] Vues REST : `origin` et `updatedAt` dans les résultats de la recherche d'essai et des étapes (SSE) (`BM/infrastructure/web/…`) ; front `FE/core/conversation/*.model.ts` et `FE/features/conversation/conversation-thread.component.ts` ; T011 vert.
- [X] T018 [US2] Fixtures de rejeu existantes : vérifier que `e2e/fixtures/llm` apparie toujours (les fragments `userContains` ne dépendent pas des nouveaux attributs) ; ajouter dans `e2e/tests/us13-conversation.spec.ts` (ou un nouveau test) la vérification de la source et de la date affichées d'un résultat.

**Checkpoint** : US2 livrable seule. Commit `feat(knowledge): source et date de chaque extrait transmis à l'IA`.

---

## Phase 4: User Story 1 - Indexer des pages Confluence et toute leur descendance (P1) 🎯 MVP

**Goal** : un serveur Confluence déclaré une fois ; des racines par projet dont toute la
descendance est indexée, réindexée de façon incrémentale.

**Independent Test** : avec l'arbre de T002, déclarer le serveur et la racine `pageId=100`,
indexer, retrouver une page de troisième niveau par la recherche d'essai, aucune page hors arbre.

### Tests for User Story 1

- [X] T019 [P] [US1] Test `BT/domain/settings/ConfluenceSettingsTest.java` : adresse normalisée (1 à 2 000 caractères), `http://` refusé sans confirmation avec le message de Jira, certificat PEM lisible ou refus explicite.
- [X] T020 [P] [US1] Test `BT/infrastructure/confluence/ConfluencePageAddressTest.java` : `…/pages/viewpage.action?pageId=123`, `…/spaces/KEY/pages/123/Titre`, `…/display/KEY/Titre+de+la+page` (à résoudre par titre) ; chemin de contexte ; autre hôte refusé (`OTHER_HOST`) ; adresse canonique d'une page `<serveur>/pages/viewpage.action?pageId=<id>`.
- [X] T021 [P] [US1] IT `BT/infrastructure/confluence/ConfluenceRestClientIT.java` (WireMock, contracts/confluence-client.md) : `Authorization: Bearer`, `user/current` (compte, `401` → `UNAUTHORIZED`), racine par identifiant et par titre, descendance paginée par `ancestor=` (`totalSize`), contenu `body.view`, `403` sur une page, `429` avec `Retry-After`, débit de 8 requêtes par seconde, proxy appliqué, certificat interne.
- [X] T022 [P] [US1] Test `BT/application/context/ManageDocSourcesTest.java` (étendu) : aperçu CONFLUENCE (titre, nombre de pages) ; refus `CONFLUENCE_NOT_CONFIGURED`, `NOT_A_PAGE`, `OTHER_HOST`, `ACCESS_DENIED`, `UNREACHABLE`, `TOO_LARGE` (au-delà de 2 000 pages Confluence pour le projet, toutes racines) ; ajout qui garde `pageId` et `title` ; limite de 10 sources inchangée.
- [X] T023 [P] [US1] Test `BT/application/knowledge/ConfluenceIndexationTest.java` (doubles de `ConfluenceClient` et `ConfluencePageStates`) : arbre complet indexé, rien hors arbre (SC-001) ; page commune à deux racines indexée une fois ; réindexation sans changement : aucune lecture de contenu (SC-002) ; page de version + 1 seule relue ; page supprimée ou sortie retirée ; page `403` ignorée et comptée ; racine disparue (`404`) en erreur, ses pages retirées ; Confluence injoignable : pages gardées, erreur et heure de la dernière lecture réussie ; `LIMIT_REACHED` à 2 000 pages ; dates `version.when` dans `source_updated_at`.
- [X] T024 [P] [US1] IT `BT/infrastructure/web/ConfluenceSettingsResourceIT.java` : GET, PUT (jeton gardé si absent), POST `/test` (résultats `OK`, `UNREACHABLE`, `UNAUTHORIZED`, `TLS_ERROR`, `NOT_CONFLUENCE`), DELETE (jeton retiré du trousseau) ; aucun jeton dans une réponse ni dans le journal.
- [X] T025 [P] [US1] Tests front `FE/features/settings/confluence-card.component.spec.ts` (saisie, test de connexion, avertissement HTTP, retrait) et `FE/features/project-settings/project-settings.page.spec.ts` (sorte « Confluence » : aperçu avec titre et nombre de pages, refus expliqués, lien vers Configuration sans serveur).

### Implementation for User Story 1

- [X] T026 [US1] `BM/domain/settings/ConfluenceSettings.java` ; T019 vert.
- [X] T027 [US1] Ports `BM/domain/port/ConfluenceClient.java` (`currentUser`, `page`, `pageByTitle`, `descendants(id, start, limit)`, `content(id)`), `ConfluenceSettingsRepository.java`, `ConfluencePageStates.java`.
- [X] T028 [US1] `BM/infrastructure/platform/HttpTlsConfigurer.java` extrait de `BM/infrastructure/jira/JiraTlsConfigurer.java` (Jira inchangé, ses IT vertes).
- [X] T029 [US1] `BM/infrastructure/confluence/ConfluencePageAddress.java` ; T020 vert.
- [X] T030 [US1] `BM/infrastructure/confluence/ConfluenceApi.java` (client REST), `ConfluenceRestClient.java` (proxy `ProxySettings`, TLS, jeton du `SecretStore`, erreurs → `UnreadReason`), `ConfluenceThrottle.java` (8 requêtes par seconde, `Retry-After`) ; T021 vert.
- [X] T031 [US1] Persistance `BM/infrastructure/persistence/settings/…` (réglages Confluence), `BM/infrastructure/persistence/project/ConfluencePageSqliteStates.java`, `DocSource` (sorte CONFLUENCE, `pageId`, `title`) dans le dépôt des sources.
- [X] T032 [US1] `BM/application/settings/SaveConfluenceSettings.java`, `DeleteConfluenceSettings.java`, test de connexion ; `BM/infrastructure/web/ConfluenceSettingsResource.java` (`/api/settings/confluence`) ; câblage dans `BM/infrastructure/config/UseCaseProducer.java` ; T024 vert.
- [X] T033 [US1] `BM/domain/project/DocSource.java` (`Kind.CONFLUENCE`, `pageId`, `title`) et `BM/application/context/ManageDocSources.java` (aperçu et ajout CONFLUENCE, limite de 2 000 pages) ; `ContextResource` accepte `kind: "CONFLUENCE"` ; T022 vert.
- [X] T034 [US1] Lecture des racines dans `BM/application/knowledge/SynchronizeKnowledge.java` (R3 : liste CQL, comparaison des versions, contenu `body.view` découpé par `Sections` comme Antora, ADR reconnus, rattachement `source_id`, retrait des pages sorties) ; Confluence dans la réindexation par source (`IndexableSource`) ; T023 vert.
- [X] T035 [US1] Front : `FE/features/settings/confluence-card.component.ts` sur la page Configuration ; sorte « Confluence » dans `FE/features/project-settings/project-settings.page.ts` (adresse de la page, aperçu, titre affiché) ; libellés de `FE/features/knowledge/…` (compteurs, réindexation) ; T025 vert.
- [X] T036 [US1] E2E `e2e/tests/us16-confluence.spec.ts`, parcours US1 : WireMock de T002 démarré par le `globalSetup` (même instance que Jira, préfixe `/confluence`) ; serveur déclaré, connexion testée, racine ajoutée (aperçu « 12 pages »), indexation, recherche d'essai d'une page de troisième niveau, page `pageId=900` absente, page restreinte comptée ; accessibilité (axe) des écrans touchés.

**Checkpoint** : US1 livrable. Commit `feat(confluence): racines Confluence indexées avec leur descendance`.

---

## Phase 5: User Story 3 - Une source de référence l'emporte et les écarts sont signalés (P2)

**Goal** : niveaux par source et par racine Confluence, transmis à l'IA ; places réservées ;
consigne d'arbitrage ; rubrique « Écarts entre sources » ; évaluation réelle.

**Independent Test** : demande SharePoint (500 €) et page Antora ou Confluence (1 000 €) ; la
réponse de l'agent suit 500 €, signale l'écart en citant les deux ; une génération porte la rubrique
« Écarts entre sources ».

### Tests for User Story 3

- [X] T037 [P] [US3] Test `BT/domain/knowledge/ReliabilitySettingsTest.java` : défaut DOCUMENTS → REFERENCE, autres → INTERPRETATION ; réglage qui égale le défaut effacé ; `uniform()` ; niveau d'une page atteinte par deux racines = le plus haut (FR-016a).
- [X] T038 [P] [US3] Test `BT/domain/knowledge/ReservedPlacesTest.java` (FR-019, FR-020) : aucune référence candidate → liste inchangée ; niveaux uniformes → inchangée ; références candidates hors des 8 → les deux mieux classées prennent les dernières places non-référence, ordre des autres gardé ; une seule candidate → une place ; trois références déjà dans les 8 → inchangée ; jamais plus de deux remplacements ; jamais de doublon.
- [X] T039 [P] [US3] Étendre `ConversationSearchTest` et le test de `SelectKnowledge` : places réservées appliquées après la fusion RRF ; scores intacts ; `level` sur chaque `Found` et chaque `Entry`.
- [X] T040 [P] [US3] Test `BT/application/context/ManageReliabilityTest.java` : lecture des niveaux et défauts, changement d'une sorte, niveau d'une racine Confluence (`null` : celui de Confluence), refus du niveau propre sur une source qui n'est pas Confluence ; changement pris en compte sans réindexation (FR-022).
- [X] T041 [P] [US3] Tests du domaine de la spécification : `BT/domain/specification/SourceDeviationTest.java` (`difference` de 1 à 1 000 caractères, 10 écarts au plus par version) ; `MarkdownRendererTest` étendu (rubrique « Écarts entre sources » après « Écarts aux ADR », sources et dates des deux extraits, omise sans écart, ancre `source-deviations`) ; `DraftValidation` : un écart dont une référence n'est pas citable est écarté (comme les écarts aux ADR).
- [X] T042 [P] [US3] Tests LLM : `LlmOutputsTest` (JSON sans `sourceDeviations` lisible ; écart hors bornes redemandé) ; `LangChainLlmClientTest` (schémas `specification` et `revision` contiennent `sourceDeviations`) ; `UserMessages` (attribut `fiabilite` : `reference`, `interpretation`, `a-confirmer`, absent pour la fiche et les documents joints) ; `SkillLibraryTest` (consigne `fiabilite-sources` déclarée dans `converse-agent`, `revise-specification`, `generate-specification`, `edit-specification`, `merge-conflict`, `structure-imported-text`, `propose-questions`).
- [X] T043 [P] [US3] IT `BT/infrastructure/web/ReliabilityResourceIT.java` : `GET/PUT /api/projects/{id}/reliability`, `PUT …/doc-sources/{sourceId}/reliability` (`422` hors Confluence) ; `SpecificationView.sourceDeviations`.
- [X] T044 [P] [US3] Tests front `FE/features/project-settings/reliability.component.spec.ts` (un sélecteur par sorte de source déclarée, défauts indiqués, niveau propre d'une racine) et `FE/features/specification/specification.page.spec.ts` (rubrique « Écarts entre sources », lien vers chaque extrait, dates).

### Implementation for User Story 3

- [X] T045 [US3] `BM/domain/knowledge/ReliabilitySettings.java`, `ReservedPlaces.java` ; T037 et T038 verts.
- [X] T046 [US3] Port `BM/domain/port/ReliabilityRepository.java`, persistance `project_source_reliability` et `project_doc_source.reliability` ; `BM/application/context/ManageReliability.java` ; `BM/infrastructure/web/ReliabilityResource.java` et route `…/doc-sources/{sourceId}/reliability` ; câblage `UseCaseProducer` ; T040 et T043 verts (partie niveaux).
- [X] T047 [US3] `ProvenanceResolver` renseigne `level` (niveau de la sorte, niveau propre de la racine, plus haut de deux racines) ; places réservées dans `BM/application/conversation/ConversationSearch.java` et `BM/application/knowledge/SelectKnowledge.java` ; T039 vert.
- [X] T048 [US3] `BM/infrastructure/llm/UserMessages.java` : attribut `fiabilite` ; T042 vert (partie messages).
- [X] T049 [P] [US3] Consigne réutilisable `BR/prompts/skills/fiabilite-sources.md` (contracts/llm-tags.md : même source → le plus récent ; interprétation ou à confirmer contre référence → la référence l'emporte, écart signalé avec les deux extraits ; deux références → aucune ne l'emporte, écart signalé ; source à confirmer → le dire ; `inconnue` ni ancien ni récent ; les écarts de la conversation sont des incohérences au sens de la 010) ; aucune accolade.
- [X] T050 [US3] Déclarer `[skill:fiabilite-sources]` dans les sept consignes de R8 (`BR/prompts/*.txt`) ; pour les cinq rédactions, le format `sourceDeviations` (`referenceRef`, `interpretationRef`, `difference`, liste vide sans écart) ; `PromptRulesTest` et `SkillLibraryTest` verts.
- [X] T051 [US3] `SpecificationContent.sourceDeviations` (`SourceDeviation`, `Cited`) et `DraftSpecification.sourceDeviations` (facultatifs, versions et fixtures antérieures lisibles) ; `IdentifierAssigner`, `DraftValidation` (références citables, sources et dates complétées d'après l'index), `PerimeterSnapshot`, `LlmOutputs` et `LlmSchemas` (`specification`, `revision`) ; T041 et T042 verts.
- [X] T052 [US3] `BM/domain/specification/MarkdownRenderer.java` : rubrique « Écarts entre sources » (export et description Jira la reprennent) ; `SessionViews.SpecificationView.sourceDeviations` ; T041 vert.
- [X] T053 [US3] Front : `FE/features/project-settings/reliability.component.ts` dans les réglages du projet ; niveau propre sur chaque racine Confluence ; rubrique « Écarts entre sources » dans `FE/features/specification/specification.page.ts` ; niveau affiché dans les étapes de recherche ; T044 vert.
- [X] T054 [P] [US3] Fixtures `e2e/fixtures/llm/` : recherche de l'agent dont les sources portent `fiabilite` et réponse qui signale l'écart (500 € contre 1 000 €) ; génération avec un écart entre sources ; un document SharePoint et une page de l'arbre Confluence porteurs des deux règles.
- [X] T055 [US3] E2E `e2e/tests/us16-confluence.spec.ts`, parcours US3 : niveaux par défaut affichés, racine passée « À confirmer » ; question à l'agent : la demande SharePoint figure dans les résultats même mal classée, réponse rejouée qui cite les deux ; génération : rubrique « Écarts entre sources » à l'écran, à l'export Markdown et dans l'aperçu Jira ; accessibilité.

**Checkpoint** : US3 livrable. Commit `feat(knowledge): niveaux de fiabilité, places réservées et écarts entre sources`.

---

## Phase 6: Évaluation réelle (FR-023, SC-005, SC-006, SC-007)

- [X] T056 [P] Cas `BTR/eval/sources/cases.json` (R9) : corpus avec une demande SharePoint (référence) et une page Confluence plus récente (interprétation) qui fixe une autre règle ; questions qui portent sur la règle ; un cas sans contradiction (même règle, dates différentes) ; un cas avec deux références contradictoires ; une page Confluence au texte adressé à une IA.
- [X] T057 `BT/eval/SourceRuns.java` et `BT/eval/SourceEval.java` (métriques `sourceConflictFlagged` : réponse qui suit la référence et cite les deux sources dans une incohérence, aucun écart inventé dans le cas sans contradiction ; `referenceRetained` : calcul déterministe sur les recherches, une référence pertinente retenue) ; branchés dans `BT/eval/EvalRuns.java` ; constantes dans `BT/eval/EvalBaseline.java` ; test unitaire du calcul sur des réponses figées (`BT/eval/SourceRunsTest.java`).
- [X] T058 `BTR/eval/baseline.json` : minimums `sourceConflictFlagged` 0.9, `referenceRetained` 1.0 ; `ci/check-eval.sh` les contrôle (boucle sur les minimums existante).

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T059 [P] Test d'injection : une page Confluence et un titre de page adressés à une IA restent des données dans `<source>` et dans la connaissance d'une génération (`BT/infrastructure/llm/…`) ; aucun contenu de page, aucun jeton dans les journaux (indexation, client Confluence).
- [X] T060 [P] ArchUnit (`BT/architecture/HexagonalArchitectureTest.java`) : le client REST Confluence seulement dans `infrastructure.confluence` ; `ReservedPlaces` et `ReliabilitySettings` sans dépendance d'infrastructure.
- [X] T061 [P] Mesure de SC-003 dans `ConfluenceIndexationTest` ou un IT : 500 pages simulées, nombre de requêtes et durée au débit de 8 par seconde sous 5 minutes ; réindexation sans changement : seules les requêtes de liste.
- [X] T062 [P] Documentation utilisateur `README.md` (Configuration › Confluence, racines, niveaux de fiabilité, rubrique « Écarts entre sources », date et source dans les étapes de recherche) et `CONTRIBUTING.md` (client Confluence, WireMock, consigne `fiabilite-sources`).
- [X] T063 [P] `CLAUDE.md` : feature en cours `specs/016-confluence-fiabilite-sources/`.
- [X] T064 [P] `e2e/fixtures/llm/README.md` et `e2e/fixtures/confluence/README.md` : nouvelles fixtures.
- [X] T065 Dérouler `quickstart.md` §1 à §4 (E2E sur le jar si le dev mode piloté par MCP n'est pas joignable).
- [X] T066 Gates complètes (verify, package, E2E) sur la branche.
- [X] T067 Évaluation réelle `./mvnw -pl backend verify -Peval -DskipDependencyCheck` puis `ci/check-eval.sh` : nouvelles métriques au-dessus de leur minimum, aucun indicateur existant sous sa base ; commit de `BTR/eval/last-run.json`.
- [X] T068 Vérification manuelle du mainteneur (quickstart §6) : Confluence de l'entreprise, jeton personnel, proxy, une racine réelle.

---

## Dependencies & Execution Order

### Phases

- **Setup (T001–T002)** : aucune dépendance.
- **Foundational (T003–T006)** : après Setup ; bloque toutes les histoires.
- **US2 (T007–T018)** : après Foundational.
- **US1 (T019–T036)** : après Foundational ; indépendante d'US2 (sans US2, ses pages sont transmises sans source ni date).
- **US3 (T037–T055)** : après US2 (la provenance porte le niveau) ; le niveau propre d'une racine (T046) suppose US1.
- **Évaluation (T056–T058)** : après US3.
- **Polish (T059–T068)** : après tout le reste ; T067 après T066.

### À l'intérieur d'une histoire

Tests d'abord (vus échouer), puis domaine, ports, adaptateurs, cas d'usage, REST, front, E2E.

## Parallel Example

```text
# Foundational
T005 SourceOriginTest / ProvenanceTest      ‖ T003 MigrationV27IT

# US2, tests
T007 ProvenanceResolverTest ‖ T008 dates à l'indexation ‖ T009 ConversationSearchTest
‖ T010 UserMessages ‖ T011 conversation-thread.spec

# US1, tests
T019 ConfluenceSettingsTest ‖ T020 ConfluencePageAddressTest ‖ T021 ConfluenceRestClientIT
‖ T022 ManageDocSourcesTest ‖ T023 ConfluenceIndexationTest ‖ T024 ConfluenceSettingsResourceIT
‖ T025 specs front

# US3, tests
T037 ReliabilitySettingsTest ‖ T038 ReservedPlacesTest ‖ T040 ManageReliabilityTest
‖ T041 SourceDeviation / MarkdownRenderer ‖ T043 ReliabilityResourceIT ‖ T044 specs front
```

## Implementation Strategy

1. **US2** : la source et la date jusqu'au modèle, pour les sources existantes. Commit.
2. **US1** : Confluence, MVP de la demande ; l'utilisateur peut déjà indexer son Confluence. Commit.
3. **US3** : fiabilité, places réservées, écarts entre sources. Commit.
4. **Évaluation**, puis **Polish**, gates, évaluation réelle, commit de `last-run.json`.

Chaque commit : gates complètes, Conventional Commits en français, fichiers stagés explicitement.
