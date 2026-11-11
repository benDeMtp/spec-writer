---
description: "Tâches de la feature 013 : périmètre des modules touchés"
---

# Tasks: Périmètre des modules touchés

**Input**: `specs/013-perimetre-modules/` (plan.md, spec.md, research.md, data-model.md,
contracts/api.md, contracts/ui.md, quickstart.md)

**Tests** : obligatoires (constitution III, TDD). Chaque test est écrit et vu échouer avant le
code. `domain` et `application` sont couverts par des tests unitaires, car JaCoCo ne compte pas
les `@QuarkusTest`.

**Organization** : les cinq histoires de la spec.

- **US1** : déclarer les sources du projet. C'est un prérequis technique de US2.
- **US2** : analyser le périmètre dans la conversation (MVP, avec US1).
- **US3** : suivre les dépendances Maven.
- **US4** : suivre les relations du modèle d'architecture.
- **US5** : reprendre le périmètre dans la spécification.

**Prérequis** : la branche `feature/017-perimetre-modules` part de
`feature/016-navigation-projet` (012, PR #14). La rebaser sur `main` dès la fusion.

## Format: `[ID] [P?] [Story] Description`

- **[P]** : parallélisable (fichiers différents, aucune dépendance sur une tâche non finie).
- Abréviations des chemins :
  - `BM/` : `backend/src/main/java/dev/specwriter/`
  - `BT/` : `backend/src/test/java/dev/specwriter/`
  - `BR/` : `backend/src/main/resources/`
  - `BTR/` : `backend/src/test/resources/`
  - `FA/` : `frontend/src/app/`

---

## Phase 1: Setup

- [X] T001 Créer les paquets et dossiers vides de la feature :
  - `BM/domain/sources/`, `BM/application/sources/`, `BM/infrastructure/sources/` ;
  - `BT/` (mêmes paquets) ;
  - `e2e/fixtures/sources/`, `BTR/eval/perimeter/`.

  Vérifier que `./mvnw -B verify -DskipDependencyCheck` passe sur la branche avant toute
  modification : c'est le point de départ propre.
- [X] T002 [P] Créer l'arbre de démonstration `e2e/fixtures/sources/demo-rv/` (research R13) :
  14 modules (13 poms, `front` en npm, `charts/facturation` en Helm). Le `README.md` de l'arbre
  donne les résultats attendus.

  **Écart au plan** : le dépôt ignore `.env`, `target/` et `node_modules/`, donc ces pièges ne
  sont pas versionnés. Les tests copient l'arbre dans un dossier temporaire et y ajoutent :
  - `.env` et `settings.xml` ;
  - `node_modules/` et `target/` ;
  - le binaire ;
  - le lien symbolique.

  Ce sont les helpers `DemoTree` (backend) et `e2e/support/demo-tree.ts` (E2E).

  Le commentaire d'injection est dans `modules/annuaire`, qui devient donc un module trouvé.

---

## Phase 2: Foundational (bloque toutes les histoires)

- [X] T003 Écrire la migration `BR/db/migration/V24__source_roots_perimeter.sql`, exactement
  selon data-model.md § Persistance :
  - `project_source_root`, avec `location` de 1 à 2000 caractères et
    `UNIQUE(project_id, location)` ;
  - `perimeter_analysis`, avec `ordinal` de 1 à 5, `state IN ('COMPLETE','INCOMPLETE')` et
    `UNIQUE(message_id, ordinal)` ;
  - `perimeter_module`, avec `kind IN ('MAVEN','NPM','HELM','ROOT')`,
    `qualification IN ('IMPACTED','TO_CHECK','NOT_IMPACTED','UNQUALIFIED')`,
    `justification` de 300 caractères au plus, `name` de 1 à 200 caractères et `is_new` 0 ou 1 ;
  - `perimeter_occurrence`, avec `excerpt` de 200 caractères au plus ;
  - `session_perimeter`, avec une clé étrangère vers `session` en cascade ;
  - `ALTER TABLE knowledge_item ADD COLUMN module_hint TEXT`.

  Écrire d'abord l'IT `BT/infrastructure/persistence/V24MigrationIT.java` sur le modèle des IT
  de migration existants. Elle vérifie :
  - les contraintes ;
  - les cascades : la suppression d'un projet supprime ses racines, celle d'une conversation
    supprime ses analyses ;
  - que les données existantes sont conservées.
- [X] T004 [P] Écrire les types du domaine `BM/domain/sources/` : `ModuleKind`, `Module` (record
  `rootId`, `path` avec séparateur `/` et vide pour la racine, `name` de 1 à 200 caractères,
  `kind`, `coordinates` facultatives) et `ModuleId` (forme `<rootId>/<path>`).

  Tests `BT/domain/sources/ModuleTest.java` : validation du nom, forme texte de l'identifiant,
  pseudo-module `ROOT`.

**Checkpoint** : le schéma est en place. Les types partagés existent.

---

## Phase 3: User Story 1 - Déclarer les sources du projet (Priority: P1)

**Goal** : déclarer une ou plusieurs racines dans les réglages du projet, avec exclusions et
aperçu des modules.

**Independent Test** : quickstart 1 à 3. Déclarer `demo-rv` donne « 14 modules détectés ».
`legacy/**` retire `legacy/ancien`. Un chemin absent est refusé.

### Tests et domaine

- [X] T005 [P] [US1] Test puis `BM/domain/sources/SourceExclusions.java` (research R3) :
  - `ignoredDirectory(String name)` : vrai pour un segment qui commence par `.`, ou pour
    `target`, `node_modules`, `build`, `dist`, `out`, `bin`, `coverage`, `__pycache__`,
    `vendor` ;
  - `sensitive(String fileName)` : vrai pour `settings.xml`, `settings-security.xml`, `*.pem`,
    `*.key`, `*.p12`, `*.pfx`, `*.jks`, `*.keystore`, `*.truststore`, `*.kdbx`, `id_rsa*`,
    `id_ed25519*`, `*.ppk`, `credentials*`, `*secret*`, `*.tfvars` et `kubeconfig*`, sans tenir
    compte de la casse.

  Tests `BT/domain/sources/SourceExclusionsTest.java` : un cas positif et un cas négatif par
  règle. `Settings.XML` est sensible. `secretariat.md` aussi : c'est un faux positif assumé.
  `keys.java` ne l'est pas.
- [X] T006 [P] [US1] Test puis `BM/domain/sources/GitIgnoreRules.java` et `GitIgnoreStack.java`
  (research R3, data-model.md).

  `GitIgnoreRules.parse(String baseDir, String content)` lit les motifs :
  - lignes vides et `#` ignorées, échappements `\#` et `\!` ;
  - `!` (négation), `/` en tête (motif ancré), `/` final (dossier seulement) ;
  - `*` (sans traverser de `/`), `**`, `?`, `[...]`.

  `Match match(String relativePath, boolean directory)` renvoie `IGNORED`, `INCLUDED` ou
  `NONE`. Dans `GitIgnoreStack`, le dernier motif qui correspond l'emporte, en partant du
  `.gitignore` le plus profond. Un dossier ignoré coupe la descente.

  Tests `GitIgnoreRulesTest`, `GitIgnoreStackTest` : chaque règle, `.gitignore` imbriqué,
  négation dans un dossier ignoré (sans effet), motif `generated/` et `!keep.txt` de T002.
- [X] T007 [P] [US1] Test puis `BM/domain/sources/SourceRoot.java`, un record avec `id`,
  `projectId`, `location`, `label`, `exclusions` (`ExclusionPatterns`) et `createdAt`
  (data-model.md) :
  - `MAX_PER_PROJECT = 10` ;
  - `location` absolue, de 1 à 2000 caractères ;
  - `label` égal au nom du dossier ;
  - `requireRoomFor(int existing)` ;
  - `requireNotNested(List<SourceRoot> others)`, qui refuse une racine imbriquée dans une autre
    ou qui en contient une, après normalisation.

  Les contrôles d'existence sur le disque restent dans l'adaptateur (T010). Tests
  `SourceRootTest`.

### Ports et adaptateurs

- [X] T008 [US1] Écrire les ports `BM/domain/port/SourceRootRepository.java` (`save`,
  `find(projectId, id)`, `list(projectId)`, `delete`) et `BM/domain/port/SourceTree.java` :
  - `record Limits(int maxFiles, Duration maxDuration, long maxFileBytes, int maxModules)`, avec
    `DEFAULT = 50_000, 120 s, 1 Mo, 2_000` ;
  - `record Preview(List<Module> modules, int moduleCount, int retainedFiles, int excludedFiles,
    int sensitiveFiles, int ignoredByGit, boolean limitReached, RootProblem problem)` ;
  - `enum RootProblem { NOT_FOUND, NOT_A_FOLDER, ACCESS_DENIED }` ;
  - `Preview preview(SourceRoot root, Limits limits)`.

  `search` viendra en US2.
- [X] T009 [P] [US1] Test puis `BM/infrastructure/sources/MavenPomReader.java`, partie
  coordonnées :
  - parseur DOM du JDK **protégé** : `disallow-doctype-decl` à vrai, entités externes à faux,
    `setXIncludeAware(false)`, `setExpandEntityReferences(false)` ;
  - `ModuleDescriptor read(InputStream)` renvoie `coordinates` (`groupId` hérité du `<parent>`
    s'il est absent), `parent` et `artifactId` ;
  - une valeur contenant `${` est comptée dans `unresolved` ;
  - un pom invalide renvoie le nom du dossier, sans exception qui remonte.

  Écrire aussi le record `BM/domain/sources/ModuleDescriptor.java` complet (data-model.md),
  dont les champs de dépendances restent vides en US1.

  Tests `MavenPomReaderTest` : héritage du `groupId`, pom avec DOCTYPE et entité externe
  refusé sans lecture du fichier visé, propriété non résolue.
- [X] T010 [US1] Test puis `BM/infrastructure/sources/LocalSourceTree.java#preview` (research R2
  à R4) :
  - **contrôles de la racine** : `NOT_FOUND`, `NOT_A_FOLDER`, `ACCESS_DENIED` ;
  - **parcours** : `Files.walkFileTree` sans `FOLLOW_LINKS`, fichiers réguliers seulement ;
  - **filtres**, dans cet ordre : `SourceExclusions`, puis `GitIgnoreStack` (lecture du
    `.gitignore` en entrant dans chaque dossier), puis `ExclusionPatterns` ;
  - **détection des modules** :
    - `pom.xml`, par `MavenPomReader` ;
    - `package.json` hors `node_modules`, `name` lu avec Jackson ;
    - `Chart.yaml`, première ligne `name:` de premier niveau ;
    - repli sur le nom du dossier ;
  - modules triés par chemin ;
  - aucun contenu lu hors des descripteurs ;
  - limites `Limits`.

  Tests `BT/infrastructure/sources/LocalSourceTreeTest.java` sur des arbres `@TempDir` :
  - modules imbriqués ;
  - lien symbolique vers l'extérieur, non suivi (test ignoré si le système refuse les liens) ;
  - `.env` et `settings.xml` comptés en `sensitiveFiles` ;
  - `.gitignore` ;
  - motif utilisateur ;
  - limite de fichiers.

  Puis un test sur `e2e/fixtures/sources/demo-rv`, qui attend exactement les 14 modules.
- [X] T011 [P] [US1] Écrire `BM/infrastructure/sources/SqliteSourceRootRepository.java` (entité
  ou requêtes natives, sur le modèle de `DocSourceJpaRepository`), avec les exclusions en
  JSON. Ajouter une IT de persistance.
- [X] T012 [US1] Test puis `BM/application/sources/ManageSourceRoots.java` :
  - `list(projectId)` ;
  - `add(projectId, location, exclusions)` : projet existant, `requireRoomFor`, unicité,
    `requireNotNested`, `SourceTree.preview` pour vérifier le dossier (un problème donne
    `VALIDATION_FAILED` avec la raison) ;
  - `setExclusions(projectId, rootId, exclusions)` ;
  - `preview(projectId, location, exclusions)` ;
  - `delete(projectId, rootId)`.

  Aucune indexation ni appel au LLM. Rien n'est journalisé, ni chemin ni motif. Câblage dans
  `BM/infrastructure/config/UseCaseProducer.java`. Tests `ManageSourceRootsTest`, avec des
  doubles en mémoire.
- [X] T013 [US1] Test puis `BM/infrastructure/web/SourceRootResource.java`, sous
  `/api/projects/{id}/source-roots` : `GET`, `POST`, `PUT /{rootId}/exclusions`,
  `DELETE /{rootId}` et `POST /preview`, exactement selon contracts/api.md (`SourceRootView` avec
  `state` évalué à la lecture, `SourceRootPreview` limité à 500 modules et `moduleCount` exact).

  Ajouter `sourceRoots` à `ProjectView` dans `ProjectResource.java`. IT
  `SourceRootResourceIT` : création, doublon, imbriquée, chemin absent, 11e racine refusée,
  aperçu de `demo-rv`.

### Front

- [X] T014 [P] [US1] Écrire `FA/core/projects/source-roots.api.ts` (`list`, `add`,
  `setExclusions`, `remove`, `preview`) et ajouter les types `SourceRoot` et
  `SourceRootPreview` à `FA/core/projects/projects.model.ts`.
- [X] T015 [US1] Test puis composant `FA/features/project-settings/source-roots.component.ts`
  (`sw-source-roots`), selon contracts/ui.md § Réglages :
  - texte d'information FR-026, toujours visible ;
  - liste des racines avec leur état en texte ;
  - exclusions modifiables ;
  - suppression avec confirmation nommée ;
  - formulaire d'ajout tant qu'il y a moins de 10 racines ;
  - aperçu en `role="status"` ;
  - erreurs sous le champ, liées par `aria-describedby`.

  Tests `.spec.ts` : aperçu affiché, erreur de chemin, confirmation, formulaire masqué à
  10 racines.
- [X] T016 [US1] Insérer `sw-source-roots` dans `FA/features/project-settings/project-settings.page.ts`,
  après « Sources de documentation », avec l'ancre `id="sources-code"` sur le `h2`. Le
  défilement vers l'ancre suit le mécanisme existant de `#jira`. Mettre à jour
  `project-settings.page.spec.ts`.
- [X] T017 [US1] E2E `e2e/tests/us16-perimetre.spec.ts`, bloc « US1 », quickstart 1 à 3, avec
  axe sur la section.

  Le chemin de `demo-rv` vient de `path.resolve` dans le test. Ajouter au `beforeAll` la
  création d'un lien symbolique `demo-rv/modules/lien-externe` vers un dossier temporaire
  contenant `ContractStatus`, et sa suppression au `afterAll`.

**Checkpoint** : les racines se déclarent et l'aperçu est juste. Aucun fichier sensible n'est
lu.

---

## Phase 4: User Story 2 - Analyser le périmètre dans la conversation (Priority: P1) 🎯 MVP

**Goal** : l'agent propose des termes, l'outil recense tous les modules et occurrences, le LLM
qualifie sans pouvoir masquer de module, et la réponse cite les modules.

**Independent Test** : quickstart 4, 7, 8, 9, 10, 11 et 14.

### Domaine

- [X] T018 [P] [US2] Test puis `BM/domain/sources/PerimeterTerms.java` :
  - de 1 à 10 termes, chacun de 2 à 100 caractères, débarrassés des espaces en tête et en fin ;
  - doublons retirés sans tenir compte de la casse ;
  - les termes rejetés sont gardés avec leur raison (`TOO_SHORT`, `TOO_LONG`, `DUPLICATE`,
    `OVER_LIMIT`) ;
  - aucun terme valide donne une `ValidationException`.

  Tests `PerimeterTermsTest`.
- [X] T019 [P] [US2] Test puis les types de l'analyse, dans `BM/domain/sources/` (data-model.md) :
  - `Occurrence` : `path`, `line` à partir de 1, `term`, `excerpt` de 200 caractères au plus,
    centré sur le terme, caractères de contrôle remplacés. Fabrique `Occurrence.of(path, line,
    term, rawLine)` ;
  - `Reason` : interface scellée `Term`, `Dependency`, `Aggregator`, `Architecture` ;
  - `Qualification` ;
  - `PerimeterModule` : au moins une raison, `samples` de 20 au plus par terme,
    `occurrences` exact ;
  - `PerimeterAnalysis`.

  Comportements :
  - `PerimeterAnalysis.sorted()` : raison directe, puis dépendance, puis architecture ; ensuite
    occurrences décroissantes, puis chemin ;
  - `withQualifications(List<ModuleQualification>)` :
    - un id inconnu est ignoré ;
    - un module absent reste `UNQUALIFIED` ;
    - `IMPACTED` sans raison `Term` est ramené à `TO_CHECK` ;
    - la justification est tronquée à 300 caractères ;
    - **aucun module n'est retiré** ;
  - `markNew(PerimeterAnalysis previous)` ;
  - `summary()`.

  Tests `PerimeterAnalysisTest` : chaque règle, et un test de propriété « la taille de
  `modules` est invariante par qualification ».

### Recensement

- [X] T020 [US2] Ajouter à `BM/domain/port/SourceTree.java` la méthode `Scan search(List<SourceRoot>
  roots, PerimeterTerms terms, Limits limits, BooleanSupplier cancelled)`, avec :
  - `record Scan(List<ModuleHit> hits, Map<ModuleId, ModuleDescriptor> descriptors,
    List<RootStatus> roots, int filesScanned, int filesSkipped, List<String> skippedExamples,
    IncompleteReason incomplete)` ;
  - `ModuleHit(Module module, Map<String, Integer> countByTerm, List<Occurrence> samples)`.

  Puis l'implémenter dans `LocalSourceTree` :
  - une seule passe ;
  - fichier binaire (octet nul dans les 8 premiers Ko) ou de plus de 1 Mo ignoré et compté,
    avec au plus 20 noms d'exemple ;
  - UTF-8, avec repli ISO-8859-1 ;
  - recherche littérale insensible à la casse (`toLowerCase(Locale.ROOT)` et `indexOf`) ;
  - rattachement au module de plus bas niveau, sinon au pseudo-module `ROOT` ;
  - lecture par threads virtuels, avec un sémaphore de 16 ;
  - tri final déterministe ;
  - racine absente : `RootStatus` `MISSING`, les autres racines sont tout de même parcourues ;
  - arrêt à `cancelled`, à `maxFiles`, à `maxDuration` ou à `maxModules`, avec la raison.

  Tests dans `LocalSourceTreeTest` :
  - casse ;
  - binaire ;
  - taille ;
  - échantillons limités à 20 et total exact ;
  - deux recherches donnent le même résultat (FR-017) ;
  - annulation ;
  - sur `demo-rv`, les modules attendus de quickstart 4, et **aucune** occurrence dans les pièges
    de T002.
- [X] T021 [P] [US2] Écrire le port `BM/domain/port/PerimeterRepository.java` (data-model.md :
  `save`, `update`, `list(messageId)`, `last(conversationId)`) et son adaptateur
  `BM/infrastructure/persistence/conversation/SqlitePerimeterRepository.java` sur les tables de
  V24.

  IT `SqlitePerimeterRepositoryIT` :
  - aller-retour complet, raisons en JSON comprises ;
  - `last` renvoie la dernière analyse de la conversation ;
  - suppression en cascade avec la conversation ;
  - l'analyse reste lisible après suppression de la racine.
- [X] T022 [US2] Test puis `BM/application/sources/AnalyzePerimeter.java`, partie termes :
  - `analyze(projectId, rawTerms, previous)` :
    - `PerimeterTerms` ;
    - `SourceRootRepository.list` ;
    - `SourceTree.search` ;
    - modules avec leurs raisons `Term` ;
    - `markNew(previous)` ;
    - analyse triée.

    Sans racine, il renvoie le résultat `NO_ROOTS` (contracts/api.md `unavailable`) ;
  - `qualify(analysis, question, exchanges)` : appelle `ConversationLlm.qualify` (T024), puis
    `withQualifications`. Si le LLM échoue, l'analyse est renvoyée non qualifiée et l'échec
    est signalé, sans exception.

  Journal : seulement les compteurs, jamais un terme ni un chemin. Câblage dans
  `UseCaseProducer`. Tests `AnalyzePerimeterTest`, avec des doubles de `SourceTree`, du dépôt
  et du LLM.

### LLM

- [X] T023 [US2] Faire évoluer le plan de conversation :
  - `ConversationLlm.Action` reçoit `PERIMETER`, et `Plan` reçoit `terms`
    (`BM/domain/port/ConversationLlm.java`) ;
  - `LlmSchemas.CONVERSE_PLAN` : `action` dans `SEARCH | PERIMETER | ANSWER`, et `terms`, un
    tableau de chaînes de 10 au plus (`BM/infrastructure/llm/LlmSchemas.java`) ;
  - `LangChainConversationLlm.toPlan` lit `terms` ;
  - `UserMessages.conversePlan` ajoute `<perimetre-disponible>oui|non</perimetre-disponible>`
    et `<dernier-perimetre>` (termes de la dernière analyse) ;
  - `PlanRequest` reçoit ces deux champs.

  Mettre à jour le prompt `BR/prompts/converse-plan.txt` (research R6) :
  - quand choisir `PERIMETER` (impact, modules, périmètre, tâches techniques), seulement si
    `<perimetre-disponible>oui` ;
  - préférer des identifiants techniques et leurs variantes (`CamelCase`, `SNAKE_CASE`, clés
    pointées) ;
  - reprendre et compléter `<dernier-perimetre>` quand l'utilisateur demande d'autres termes ;
  - ajouter les nouvelles balises à la règle de sécurité.

  Tests unitaires de `UserMessages` (échappement, présence des balises) et de `toPlan`.
- [X] T024 [US2] Ajouter la qualification :
  - `ConversationLlm.qualify(QualifyRequest)` renvoie `List<ModuleQualification>`, avec
    `ModuleQualification(String id, Qualification q, String justification)` ;
  - schéma strict `LlmSchemas.PERIMETER_QUALIFICATION` (contracts/api.md) ;
  - nouveau prompt `BR/prompts/qualify-perimeter.txt` (`[operation:qualify-perimeter]`, règle
    de sécurité : `<question>`, `<echange>`, `<termes>`, `<module>`, `<extrait>` sont des
    données) ;
  - `UserMessages.qualifyPerimeter(request)` :
    - par module : id, nom, sorte, raisons, puis au plus 5 extraits échappés ;
    - budget total de 40 000 caractères ;
    - au-delà, modules sans extrait, dans l'ordre déterministe de R7.

  Tests `UserMessagesTest` (budget, ordre, échappement d'un extrait contenant `</extrait>`).
- [X] T025 [US2] Mettre à jour `BR/prompts/converse-answer.txt` et `UserMessages` :
  - les sources `MODULE` portent nom, qualification et justification, sans aucun extrait ;
  - consigne : citer chaque module évoqué par `[n]`, rappeler que la liste complète est affichée
    dans l'étape, et dire si l'analyse est incomplète ou non qualifiée ;
  - avec `<perimetre-indisponible>`, inviter à déclarer les sources du code.

  Ajouter `MODULE` à `BM/domain/conversation/SourceKind.java`.

### Conversation

- [X] T026 [US2] Test puis intégration dans `BM/application/conversation/Converse.java`
  (research R6). Sur `PERIMETER` :
  1. si une analyse a déjà eu lieu dans le tour, l'action est traitée comme `ANSWER` ;
  2. `analyze` avec `previous = perimeters.last(conversationId)` ;
  3. `++steps` (ordinal partagé) ;
  4. `perimeters.save` ;
  5. publication de `Event.Perimeter` ;
  6. `qualify` ;
  7. `perimeters.update` ;
  8. nouvelle publication de `Event.Perimeter` ;
  9. ajout des sources `MODULE` (ref `module:<id>`, titre `nom (chemin)`) ;
  10. retour au plan.

  Avec `NO_ROOTS`, il publie `Perimeter` avec `unavailable = NO_ROOTS`, sans enregistrer de
  modules, et la réponse reçoit `<perimetre-indisponible>`. Un arrêt du tour pendant le
  recensement donne `INCOMPLETE (CANCELLED)`.

  Ajouter `Perimeter(PerimeterAnalysis)` à `BM/application/conversation/TurnEvents.java`.

  Tests `ConverseTest` :
  - `PERIMETER`, puis `ANSWER` ;
  - deux `PERIMETER` dans un tour ;
  - sans racine ;
  - qualification en échec : l'analyse reste et la réponse a lieu ;
  - les ordinaux suivent les recherches.
- [X] T027 [US2] Exposer l'analyse côté web :
  - `BM/infrastructure/web/ConversationResource.java` : nom d'événement `perimeter` dans
    `name(event)`, sérialisé en `PerimeterView` selon contracts/api.md (classe
    `PerimeterViews`) ;
  - `GET /api/conversations/{cid}` : `perimeters` par message `AGENT`
    (`ManageConversations.Detail`).

  IT avec rejeu `ConversationPerimeterIT` : flux SSE de deux événements `perimeter` de même
  ordinal, puis détail rechargé identique.
- [X] T028 [US2] Ajouter les réponses rejouées, en tête de liste pour que les entrées
  spécifiques passent avant les génériques :
  - `e2e/fixtures/llm/50-converse-plan-perimeter.json` : `userContains` `["SUSPENDU",
    "<perimetre-disponible>oui"]`, réponse `PERIMETER` avec `ContractStatus`, `CONTRACT_STATUS`
    et `contract.status` ;
  - `51-converse-plan-perimeter-relance.json` : `["contract_suspended"]`, avec les termes
    précédents plus `contract_suspended` ;
  - `52-qualify-perimeter.json` : qualifie tous les modules **sauf `demo-front`** ;
  - `53-converse-answer-perimeter.json` : réponse avec `[1]`, `[2]` ;
  - `54-converse-plan-no-roots.json` : `<perimetre-disponible>non`, réponse `ANSWER`.

  Copier les mêmes réponses dans `BTR/llm-replay/` pour les IT. Vérifier que les entrées
  `converse-plan` existantes (30 à 49) ne captent pas ces messages : leurs `userContains`
  doivent rester distincts.

### Front

- [X] T029 [P] [US2] Modèle et API de la conversation :
  - `FA/core/conversations/conversations.model.ts` : `PerimeterView` (contracts/api.md),
    événement `perimeter` dans `TurnEvent`, `perimeters` dans le message, sorte `MODULE` dans
    `CitedSource` ;
  - `FA/core/conversations/conversations.api.ts` : `perimeter` dans la liste `NAMES` de
    l'`EventSource`.
- [X] T030 [US2] Test puis composant `FA/features/conversation/perimeter-step.component.ts`
  (`sw-perimeter-step`), selon contracts/ui.md § Conversation :
  - en-tête (termes, racines, fichiers, durée, état, termes rejetés, racines absentes) ;
  - résumé en texte, avec `aria-busy` tant que `qualified` est faux ;
  - une `<section>` par qualification avec un `h3` qui donne le nombre, dans l'ordre Impactés,
    À vérifier, Non impactés, Non qualifiés ;
  - `<table>` avec `<caption>` masquée et des `th scope="col"` ;
  - badge texte « Nouveau » ;
  - raisons `TERM` (les autres sortes viennent en US3 et US4) ;
  - `<details>` des occurrences, avec la ligne « 20 premières sur N » ;
  - ancre `perimeter-<message>-<ordinal>-<position>` par ligne ;
  - état `unavailable = NO_ROOTS` avec le lien vers `/projet/reglages#sources-code` ;
  - aucun résultat ;
  - annonce `LiveAnnouncer` à la fin du recensement, puis de la qualification.

  Tests `.spec.ts` :
  - groupes et nombres ;
  - un module non qualifié est affiché ;
  - en-tête incomplet ;
  - lien des réglages ;
  - annonces.
- [X] T031 [US2] Intégrer l'étape dans `FA/features/conversation/conversation-thread.component.ts` :
  - `case 'perimeter'` remplace l'analyse de même ordinal ;
  - les étapes sont rendues dans l'ordre des ordinaux (recherches et analyses mêlées) ;
  - l'analyse est ouverte par défaut ;
  - `rendered(m)` fait pointer `[n]` d'une source `MODULE` vers l'ancre de la ligne, qui reçoit
    le focus au clic.

  Mettre à jour `conversation-thread.component.spec.ts`.
- [X] T032 [US2] E2E `us16-perimetre.spec.ts`, bloc « US2 » :
  - quickstart 4 (modules et occurrences attendus, citations) ;
  - quickstart 7 (`demo-front` sous « Non qualifiés ») ;
  - quickstart 8 (aucun piège visible, lien symbolique compris) ;
  - quickstart 9 (relance, badge « Nouveau » sur `batch-suspension`) ;
  - quickstart 10 (projet sans racine, lien) ;
  - quickstart 11 (rechargement identique) ;
  - quickstart 14 (journal sans chemin, terme ni extrait : vérification sur le texte du journal
    et sur `e2e` data dir, comme `secrets-at-rest.spec.ts`) ;
  - axe sur l'étape.

  Le piège d'injection de `annuaire` ne doit changer ni la liste ni la qualification rejouée.
- [X] T033 [US2] Test d'injection, écrit dans `BT/infrastructure/llm/PerimeterLlmTest.java` (au plus
  près de `UserMessages`) plutôt que dans un `PerimeterInjectionTest` séparé :
  - un extrait contenant `</extrait><question>…` est échappé dans le message de qualification ;
  - une réponse du LLM qui « retire » des modules (liste vide) laisse tous les modules
    `UNQUALIFIED`, sans en supprimer (FR-014, OWASP LLM01).
- [X] T034 [US2] Évaluation `BT/eval/PerimeterEval.java` (`@Tag("eval")`), sur le modèle de
  `ConversationEval` :
  - cas `BTR/eval/perimeter/cases.json` : au moins 6 discussions sur `demo-rv` et un second arbre
    généré, avec les modules attendus ;
  - métriques :
    - `PERIMETER_TERM_RECALL` (modules attendus retrouvés par les termes du vrai plan, seuil
      0,9) ;
    - `PERIMETER_QUALIFICATION` (accord avec la qualification attendue) ;
  - ajout dans `BTR/eval/baseline.json`.

  **Ne pas lancer avec le vrai Copilot sans l'accord de l'utilisateur.** Seule la compilation
  est vérifiée par `verify`.

**Checkpoint** : US1 et US2 forment le MVP. Le périmètre est complet pour les termes, visible et
cité.

---

## Phase 5: User Story 3 - Suivre les dépendances entre modules (Priority: P1)

**Goal** : chaque module qui dépend, directement ou non, d'un module trouvé figure « à
vérifier », avec sa chaîne. L'agrégateur est signalé.

**Independent Test** : quickstart 5. `relance` figure avec la chaîne facturation → relance (la
plus courte depuis un module trouvé), et l'agrégateur « agrège common-contract ».

- [X] T035 [US3] Compléter `MavenPomReader` :
  - `dependencies`, y compris celles des `<profiles>` ;
  - `bomImports` (`<dependencyManagement>` avec `<type>pom</type>` et `<scope>import</scope>`) ;
  - `aggregated` (`<modules>`, chemins relatifs normalisés) ;
  - les valeurs `${...}` sont ignorées et comptées.

  Tests dans `MavenPomReaderTest`.
- [X] T036 [P] [US3] Test puis `BM/domain/sources/ModuleGraph.java` (data-model.md) :
  - `of(Map<ModuleId, ModuleDescriptor>)` : arêtes « dépend de » (dépendance, parent, import de
    BOM), sans coordonnée hors de l'arbre. Deux modules aux mêmes coordonnées : le premier en
    ordre de chemin l'emporte, et le doublon est signalé ;
  - `dependents(Set<ModuleId> seeds)` : parcours en largeur inverse, chaîne la plus courte
    depuis une graine, ordre déterministe, cycles tolérés ;
  - `aggregators(Set<ModuleId>)`.

  Tests `ModuleGraphTest` : A ← B ← C, cycle A ↔ B, dépendance externe, BOM, parent, deux
  graines vers un même dépendant (chaîne la plus courte).
- [X] T037 [US3] Propagation dans `AnalyzePerimeter.analyze` :
  - les descripteurs viennent de `Scan.descriptors` ;
  - chaque dépendant reçoit `Reason.Dependency(chain)` et l'agrégateur `Reason.Aggregator(of)` ;
  - un module trouvé par terme et par dépendance n'apparaît qu'une fois, avec ses deux raisons
    (FR-020) ;
  - qualification initiale `TO_CHECK` pour les modules sans terme.

  Tests dans `AnalyzePerimeterTest` et sur `demo-rv` (`LocalSourceTreeTest` ou un test
  d'intégration du cas d'usage).
- [X] T038 [US3] Afficher les raisons dans `sw-perimeter-step` :
  - `DEPENDENCY` : « dépend de X », avec la chaîne en `<ol>` accessible ;
  - `AGGREGATOR` : « agrège X ».

  Envoyer ces raisons au LLM de qualification (`UserMessages.qualifyPerimeter`). Tests
  `.spec.ts`.
- [X] T039 [US3] E2E `us16-perimetre.spec.ts`, quickstart 5. Mettre à jour la réponse rejouée
  `52-qualify-perimeter.json` si de nouveaux modules apparaissent (relance, agrégateur), en
  gardant l'omission de `demo-front`.

**Checkpoint** : les dépendances indirectes ne sont plus oubliées.

---

## Phase 6: User Story 4 - Suivre les relations du modèle d'architecture (Priority: P2)

**Goal** : les conteneurs reliés à un module trouvé figurent « à vérifier », avec la relation.

**Independent Test** : quickstart 6. `notification` figure « à vérifier », relié à facturation
(REST).

- [X] T040 [US4] Ajouter `moduleHint` à `BM/domain/knowledge/ArchElement.java` (adapter tous les
  appels du constructeur) :
  - `StructurizrReader` lit la propriété `spec-writer.module` ;
  - `SqliteKnowledgeIndex` l'écrit dans `knowledge_item.module_hint` (élément `chunk = 0`) et la
    relit dans `architecture()`.

  Tests `StructurizrReaderTest` (propriété présente ou absente), et IT de l'index.
- [X] T041 [US4] Compléter le modèle de démonstration `e2e/fixtures/c4/`. Écart : un workspace à part,
  `e2e/fixtures/c4/perimetre/`, pour ne pas changer celui d'us8 ni les rubriques d'architecture :
  - conteneurs `facturation` (`spec-writer.module` = `modules/facturation`) et `notification`
    (sans propriété : correspondance par le nom) ;
  - relation facturation → notification, « envoie les relances », REST ;
  - un conteneur `archives`, relié à facturation, sans module.

  Vérifier que les E2E qui utilisent ce modèle (`us8`) restent verts, et adapter leurs attentes
  s'ils comptent les éléments.
- [X] T042 [US4] Test puis propagation par l'architecture dans `AnalyzePerimeter` (research R8) :
  - `KnowledgeIndex.architecture(projectId)` chargé une fois ;
  - correspondance par `moduleHint` (nom ou chemin), sinon par le nom normalisé (minuscules,
    sans `-`, `_`, `.` ni espaces) ;
  - relations directes `ArchRelation.links`, dans les deux sens ;
  - module relié : `Reason.Architecture` ;
  - élément sans module : `architectureOnly` ;
  - `archStatus` `MATCHED` ou `ABSENT` sur les modules `MAVEN`, `NPM` et `HELM`.

  Pas d'enchaînement au-delà d'un saut. Tests `AnalyzePerimeterTest`.
- [X] T043 [US4] Afficher dans `sw-perimeter-step` :
  - la raison `ARCHITECTURE` : « relié à Y (technologie, « description ») » ;
  - la mention « absent du modèle d'architecture » ;
  - la section « Éléments d'architecture sans module ».

  Envoyer ces raisons au LLM de qualification. Tests `.spec.ts`. E2E quickstart 6 dans
  `us16-perimetre.spec.ts`.

**Checkpoint** : les relations réseau complètent les dépendances de code.

---

## Phase 7: User Story 5 - Reprendre le périmètre dans la spécification (Priority: P2)

**Goal** : la spécification rédigée depuis la discussion contient « Modules touchés », et les
modules à vérifier sont repris en hypothèses.

**Independent Test** : quickstart 12.

- [X] T044 [P] [US5] Test puis `BM/domain/sources/PerimeterSnapshot.java` : modules,
  qualifications, justifications, raisons et au plus 3 occurrences par module, avec un état
  incomplet éventuel.
  - `PerimeterSnapshot.of(PerimeterAnalysis)` ;
  - `toAssumptions(Language)` produit une `Assumption` `TOOL_INFERENCE` par module `TO_CHECK` ou
    `UNQUALIFIED` : « Le module X (chemin) est à vérifier : <raison principale>. »

  Ajouter `snapshot(sessionId)` et `saveSnapshot` à `PerimeterRepository`, implémentés sur
  `session_perimeter`.
- [X] T045 [US5] Test puis rubrique dans `BM/domain/specification/MarkdownRenderer.java` :
  - surcharge `render(content, language, knowledge, PerimeterSnapshot perimeter)` ;
  - rubrique `## Modules touchés` (EN `Affected modules`) après « Architecture », avec
    l'ancre `perimeter` dans `anchors()` ;
  - sous-rubriques Impactés, À vérifier (y compris les non qualifiés), Non impactés ;
  - pour chaque module : nom, chemin, justification, et preuves en sous-liste (`chemin:ligne`
    ou chaîne) ;
  - si l'analyse est incomplète, une phrase le dit ;
  - textes échappés (`escape`, `inline`) ;
  - sans instantané : rendu inchangé.

  Tests `MarkdownRendererTest` : présence, ordre, échappement d'un chemin contenant `*` et `_`,
  absence de la rubrique sans instantané.
- [X] T046 [US5] `BM/application/conversation/StartFromConversation.java` copie
  `perimeters.last(conversationId)` dans l'instantané de la session créée.

  `DraftNeed` renvoie `perimeter: {moduleCount, ordinal} | null` (`NeedDraft`, vue web). Tests
  unitaires des deux cas d'usage.
- [X] T047 [US5] Rendu et hypothèses :
  - `GenerateSpecification` lit l'instantané de la session, ajoute `toAssumptions` **après** la
    réponse du LLM et **avant** `IdentifierAssigner`, puis passe l'instantané à `render` ;
  - même chose pour la révision (`ProposalStore`), `ImportJiraIssue` et `PushToJira`, pour que
    la rubrique ne disparaisse pas d'une version à l'autre de la session ;
  - les prompts de génération ne changent pas.

  Écart : `ImportJiraIssue` crée une session sans discussion, donc sans instantané ; il n'est pas
  modifié. Les hypothèses sont ajoutées après la validation de la réponse du LLM, numérotées à la
  suite, et une hypothèse au même énoncé n'est pas répétée (révision).

  Tests `GenerateSpecificationTest` : hypothèses ajoutées et numérotées, rubrique présente, et
  contrôle de traçabilité (`TraceabilityValidator`) toujours vert.
- [X] T048 [US5] Front :
  - `FA/features/conversation/need-draft.component.ts` affiche « La spécification reprendra le
    périmètre analysé (N modules). » quand `perimeter` est présent ;
  - le sommaire des rubriques de la spécification (011) reçoit « Modules touchés », s'il est
    construit côté front à partir des ancres.

  Écart : l'écran de la version affiche la rubrique en la prenant dans le Markdown de la version,
  rendu par l'outil, plutôt qu'en élargissant la vue `SpecificationView`. Le sommaire, construit
  côté serveur à partir des titres, reçoit l'ancre `perimeter`.

  Tests `.spec.ts`. E2E quickstart 12 dans `us16-perimetre.spec.ts`, avec les réponses rejouées
  `propose-need` et la génération existantes, complétées si besoin pour la discussion E2E.

**Checkpoint** : le périmètre va jusqu'à la spécification exportée.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [ ] T049 [P] Écrire le test de performance `BT/infrastructure/sources/LocalSourceTreePerfTest.java`
  (`@Tag("perf")`, exclu par défaut dans `pom.xml` comme `eval`). Il génère 60 modules et
  20 000 fichiers dans `@TempDir`, cherche 5 termes et vérifie une durée inférieure à 30 s
  (SC-003). Le lancer une fois et noter la durée dans research.md R2.
- [X] T050 [P] Documentation :
  - `README.md` : section « Sources du code et analyse de périmètre » (déclarer une racine,
    exclusions, fichiers jamais lus, ce qui est envoyé à Copilot, propriété
    `spec-writer.module` dans Structurizr) ;
  - `specs/002-projets-contexte-mcp/contracts/openapi.yaml` : routes `source-roots`.
- [ ] T051 Revue d'ergonomie et d'accessibilité dans
  `specs/013-perimetre-modules/checklists/ux-review.md` (skill web-design-guidelines sur
  `source-roots.component.ts` et `perimeter-step.component.ts`). Corriger les écarts bloquants.
- [ ] T052 Gates complètes, avec le dev mode arrêté :
  1. `rm -f backend/target/jacoco.exec` ;
  2. `./mvnw -B verify -DskipDependencyCheck` (JaCoCo ≥ 80 % sur le cœur, ArchUnit, SpotBugs,
     PMD, front) ;
  3. `./mvnw -B package -DskipTests` ;
  4. `npm --prefix e2e test`, sous `JAVA_HOME` 25 en tête du `PATH`.

  Vérifier que les E2E existants de la conversation (`us13`, `us14`) restent verts avec les
  nouvelles réponses rejouées.
- [ ] T053 **Avec l'accord de l'utilisateur** : lancer l'évaluation réelle
  `./mvnw -pl backend verify -Peval -Dtest=PerimeterEval`. Ajuster le prompt `converse-plan` si
  le rappel est inférieur à 0,9, puis fixer `baseline.json`.
- [ ] T054 Faire dérouler le quickstart complet (1 à 15) par l'utilisateur sur le jar, avec une
  vraie racine (rv) : test manuel du mainteneur.

---

## Dependencies & Execution Order

- **Setup (T001, T002)**, puis **Foundational (T003, T004)**, qui bloquent tout.
- **US1 (T005 à T017)** : prérequis de US2, car l'analyse lit les racines.
- **US2 (T018 à T034)** dépend de US1. Le MVP est livrable après US2.
- **US3 (T035 à T039)** dépend de US2 (`AnalyzePerimeter`, `sw-perimeter-step`).
- **US4 (T040 à T043)** dépend de US2. Elle est indépendante de US3 et parallélisable avec elle,
  sauf `AnalyzePerimeter` et `sw-perimeter-step`, communs : enchaîner T037 → T042 et
  T038 → T043.
- **US5 (T044 à T048)** dépend de US2. Elle est indépendante de US3 et US4 (l'instantané reprend
  les raisons qui existent).
- **Polish (T049 à T054)** vient après les histoires voulues.

Dans chaque histoire : tests, puis domaine, puis ports et adaptateurs, puis cas d'usage, puis
web, puis front, puis E2E.

## Parallel Example

```text
# US1, après T008 :
T009 MavenPomReader (coordonnées)   T011 SqliteSourceRootRepository   T014 API et modèle front
# US2, en tête :
T018 PerimeterTerms   T019 types de l'analyse   T021 PerimeterRepository   T029 modèle front
# Après le MVP :
US3 (T035, T036)  en parallèle de  US4 (T040, T041)  et de  US5 (T044, T045)
```

## Implementation Strategy

1. **MVP** : Setup, Foundational, US1 et US2. Les racines se déclarent, l'analyse est
   exhaustive pour les termes, et rien n'est masqué. Commits par groupe logique (racines, puis
   recensement, puis conversation, puis front).
2. **US3** : la propagation Maven, qui comble les oublis les plus fréquents dans rv.
3. **US4**, puis **US5**.
4. **Polish** : gates, puis évaluation réelle (avec accord), puis test manuel.

Chaque tâche est cochée `[X]` dès qu'elle est finie. Les commits sont en Conventional Commits,
en français, avec des fichiers stagés explicitement. On ne commite jamais
`.specify/feature.json`.
