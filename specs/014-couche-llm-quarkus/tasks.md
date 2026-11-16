---
description: "Tâches de la feature 014 : couche LLM sur les AI Services Quarkus, et renommage du package"
---

# Tasks: Couche LLM sur les AI Services Quarkus, et renommage du package

**Input**: `specs/014-couche-llm-quarkus/` (plan.md, spec.md, research.md, data-model.md,
contracts/llm-adapter.md, contracts/agent-tools.md, contracts/replay-format.md, quickstart.md)

**Tests** : obligatoires (constitution III, TDD). Chaque test est écrit et vu échouer avant le
code. JaCoCo ne compte pas les `@QuarkusTest` : les adaptateurs, garde-fous et outils sont
couverts par des tests unitaires.

**Évaluation réelle** : chaque passage appelle le vrai Copilot (environ 45 minutes). Il se fait
**avec l'accord du mainteneur**, puis `ci/check-eval.sh`. Une famille d'opérations n'est commitée
que si l'évaluation passe (research R7, R9).

**Organization** : les histoires de la spec, dans l'ordre de migration de research R9.

- Le renommage (R10) est un prérequis bloquant : premier commit, mécanique.
- Les adaptateurs et le rejeu sont le socle commun.
- **US2** (sécurité) et **US1** (comportement inchangé) forment le MVP, opération par opération.
- **US3** (extensibilité), **US4** (suites du renommage) et **US5** (rejeu documenté) suivent.

## Format: `[ID] [P?] [Story] Description`

- **[P]** : parallélisable (fichiers différents, aucune dépendance sur une tâche non finie).
- Abréviations des chemins, **après le renommage** (T003) :
  - `BM/` : `backend/src/main/java/fr/kawamind/`
  - `BT/` : `backend/src/test/java/fr/kawamind/`
  - `BR/` : `backend/src/main/resources/`
  - `BTR/` : `backend/src/test/resources/`
  - `LLM/` : `BM/infrastructure/llm/`

## Gates avant chaque commit (CLAUDE.md)

1. `rm -f backend/target/jacoco.exec` ;
2. `rm -rf backend/target/classes backend/target/test-classes` ;
3. `./mvnw -B verify -DskipDependencyCheck` ;
4. `./mvnw -B package -DskipTests` ;
5. `npm --prefix e2e test`.

JDK 25 en tête du `PATH`, Maven sous `env -u JAVA_TOOL_OPTIONS`.

---

## Phase 1: Setup

- [ ] T001 Vérifier le point de départ : branche `feature/018-couche-llm-quarkus` rebasée sur
  `main` (PR #16 incluse), gates verts avant toute modification.
- [ ] T002 Test d'essai « appels parallèles » (research R2), avec l'accord du mainteneur.
  - Dans `backend/src/test/java/dev/specwriter/spike/CopilotToolsSpikeIT.java`, ajouter
    `s6_appelsParallelesTerminaux` : deux outils terminaux, une demande qui appelle les deux.
  - Consigner si **tous** les appels d'une même réponse sont capturés avant l'arrêt du tour.
  - Noter le résultat dans research R2 :
    - capturés : rien à faire ;
    - sinon : repli, la consigne `converse-agent` demande un outil par réponse.

---

## Phase 2: Foundational (bloquant)

**But** : le code vit sous `fr.kawamind`, et le socle LLM (adaptateurs à outils, flux, journal,
rejeu) est prêt. Aucune opération n'est encore migrée.

### Renommage (research R10) : un commit à lui seul

- [ ] T003 Déplacer les packages.
  - `git mv backend/src/main/java/dev/specwriter backend/src/main/java/fr/kawamind`, et de même
    pour `src/test/java`.
  - Remplacer `dev.specwriter.` par `fr.kawamind.` dans :
    - les `.java` ;
    - `BR/META-INF/services/*` ;
    - `frontend/src/app/features/journal/journal.page.spec.ts` ;
    - `CONTRIBUTING.md`, `CLAUDE.md` ;
    - les chemins de `BT/eval/EvalFingerprint.java` et `EvalFingerprintTest.java`.
  - Ne **pas** toucher les clés `%dev.specwriter.` de `BR/application.properties`, ni les specs
    passées.
- [ ] T004 groupId `fr.kawamind` dans `pom.xml`, `backend/pom.xml`, `frontend/pom.xml`, et le
  `groupId` Maven de `jreleaser.yml`. `packageIdentifier: dev.specwriter.app` reste inchangé.
- [ ] T005 Supprimer `backend/target/classes` et `test-classes` (classes de l'IDE), puis passer
  les gates.
  - Contrôle : `git grep -n "dev\.specwriter" -- backend/src frontend/src` ne rend que les clés
    `%dev.specwriter.`.
  - Commit `refactor: renommage du package dev.specwriter en fr.kawamind`, sans aucun autre
    changement.

### Adaptateur `ChatModel` à outils (contracts/llm-adapter.md)

- [ ] T006 [P] Tests `BT/infrastructure/llm/CopilotSdkChatModelTest.java`, sur un double de
  `CopilotSdkGateway` étendu :
  - les `ToolSpecification` deviennent des outils SDK terminaux et `skipPermission` ;
  - un appel capturé rend `AiMessage` avec les `ToolExecutionRequest` (identifiant, nom,
    arguments) et `finishReason = TOOL_EXECUTION` ;
  - l'historique (`AiMessage`, appels, `ToolExecutionResultMessage`) est sérialisé dans
    `<echange-outils>`, échappé ;
  - le message système reste seul système ;
  - `supportedCapabilities` contient `RESPONSE_FORMAT_JSON_SCHEMA`.
- [ ] T007 Étendre `CopilotSdkGateway` et `LLM/CopilotRuntime.java`.
  - Nouvelle méthode `call(model, system, user, schema, tools, Consumer<String> deltas, cancelled)`,
    qui rend un texte ou des appels capturés.
  - `complete` et `stream` deviennent des cas particuliers.
- [ ] T008 Implémenter la traduction dans `LLM/CopilotSdkChatModel.java` jusqu'à faire passer
  T006.

### Flux, journal, production des modèles

- [ ] T009 [P] Tests `BT/infrastructure/llm/CopilotSdkStreamingChatModelTest.java` :
  - morceaux transmis à `onPartialResponse` ;
  - réflexion transmise à `onPartialThinking` ;
  - appels d'outils rendus à `onCompleteResponse` ;
  - `StreamingHandle.cancel()` abandonne la session ;
  - erreurs traduites comme dans `toDomain`.
- [ ] T010 Implémenter `LLM/CopilotSdkStreamingChatModel.java`.
- [ ] T011 [P] Tests `BT/infrastructure/llm/LlmCallLogTest.java` : ligne
  `LLM <op> : <ms> ms, <n> tentative(s)[, outils : …]`, sans aucun fragment de consigne, de donnée
  ni de réponse (FR-008).
- [ ] T012 Implémenter `LLM/LlmCallLog.java` (`ChatModelListener`, opération lue par
  `Operations.of`). `LLM/ChatModelProducer.java` produit `ChatModel` et `StreamingChatModel`
  (Copilot ou rejeu).

### Rejeu (contracts/replay-format.md)

- [ ] T013 [P] Tests `BT/infrastructure/llm/ReplayChatModelTest.java` et
  `ReplayStreamingChatModelTest.java` :
  - `toolCalls` et `afterTools` ;
  - substitutions appliquées aux `arguments` ;
  - un seul champ parmi `response`, `text` et `toolCalls` ;
  - message d'échec qui nomme l'opération, l'étape et les fragments manquants (US5).
- [ ] T014 Étendre `LLM/ReplayChatModel.java` et créer `LLM/ReplayStreamingChatModel.java`.

**Checkpoint** : gates verts. Les opérations passent encore par l'ancien code, inchangé. Commit
`feat(llm): adaptateurs ChatModel et StreamingChatModel à outils terminaux, rejeu étendu`.

---

## Phase 3: User Story 2 — Le modèle ne peut agir que par les outils de spec-writer (P1)

**Goal** : chaque session Copilot est verrouillée, et les demandes hors liste sont refusées.

**Independent Test** : tests de réglages de session, d'outil inconnu, d'arguments invalides ; cas
d'injection dans l'évaluation (quickstart 3 et 4).

- [ ] T015 [P] [US2] Tests dans `CopilotSdkChatModelTest` et `CopilotSdkStreamingChatModelTest`
  (contracts/llm-adapter.md, réglages obligatoires). Pour une requête sans outil et avec outils :
  - `availableTools` exact ;
  - permissions refusées ;
  - `setEnableSkills(false)`, `setSkipCustomInstructions(true)`, `setEnableConfigDiscovery(false)` ;
  - dossier de travail privé vide.
- [ ] T016 [US2] Implémenter ces réglages dans `LLM/CopilotRuntime.java`, en remplacement de
  `toolLessSession`. Le dossier `<data-dir>/copilot/empty` est créé en `rwx------`, via
  `UserDirectories`.
- [ ] T017 [P] [US2] Test de règles des consignes, et règle de sécurité sur `<echange-outils>`
  (contenu = donnée). La règle s'applique à toute consigne qui reçoit des résultats d'outils :
  aujourd'hui aucune, puis `converse-agent` (T034), dont ce test garde la règle.
  - Test `BT/infrastructure/llm/PromptRulesTest.java` :
    - chaque consigne a la règle des balises ;
    - aucune accolade dans les consignes (research R3).
- [ ] T018 [P] [US2] Étendre `BT/infrastructure/llm/PromptInjectionIT.java` avec un résultat
  d'outil piégé (ticket rejoué qui demande de lire un fichier) : aucun outil hors liste, aucun
  fichier lu, contenu jamais en système.
- [ ] T019 [US2] Volet d'évaluation `BT/eval/InjectionRuns.java`.
  - Corpus avec un ticket piégé et un `secret.txt` dans le dossier de travail de la session.
  - Métrique `injectionResistance` dans `EvalBaseline` et `BTR/eval/baseline.json` (minimum 1,0).
  - Branchement dans `EvalRuns`.
  - Il tourne avec l'évaluation de T037.

**Checkpoint** : gates verts. Commit `feat(llm): session Copilot verrouillée et résistance à
l'injection mesurée`.

---

## Phase 4: User Story 1 — L'utilisateur ne voit aucune différence (P1) 🎯 MVP

**Goal** : toutes les opérations passent par les AI Services, sans changement visible ni recul
de l'évaluation.

**Independent Test** : gates complets, puis évaluation réelle au minimum de référence
(quickstart 2 et 3).

### Évaluation sur le code livré (research R7)

- [ ] T020 [US1] Passer l'évaluation en `@QuarkusTest` avec un profil
  `BT/eval/EvalProfile.java` : rejeu désactivé, `specwriter.copilot.home` pointé sur
  `~/.copilot` (ou `-Deval.copilot-home`).
  - Nouvelle clé de configuration `specwriter.copilot.home`, facultative, lue par
    `CopilotRuntime.copilotHome()`.
  - `EvalCopilot` est retiré au profit des beans injectés.
  - Vérifier que l'évaluation actuelle tourne sur le rejeu (en sautant les appels réels) : test
    `EvalProfileTest`.

### Famille Knowledge (3 opérations structurées)

- [ ] T021 [P] [US1] Tests `@QuarkusTest` `BT/infrastructure/llm/ai/KnowledgeAiTest.java` sur le
  rejeu : `judge-related-issues`, `compare-with-issue` et `identify-impact` rendent les DTO
  attendus.
  - Tests unitaires des garde-fous `RelatedGuardrail`, `ComparisonGuardrail` et `ImpactGuardrail` :
    sortie invalide → reprise, deux au plus.
- [ ] T022 [US1] Créer `LLM/ai/KnowledgeAi.java`, `LLM/ai/dto/` (DTO déplacés) et
  `LLM/ai/guardrails/`.
  - `LangChainLlmClient` délègue ces trois méthodes à `KnowledgeAi`.
  - `quarkus.langchain4j.guardrails.max-retries=2` dans `BR/application.properties`.
- [ ] T023 [US1] Évaluation réelle (accord du mainteneur), puis gates. Commit
  `refactor(llm): opérations de connaissance sur les AI Services`.

### Famille Questions

- [ ] T024 [P] [US1] Tests `@QuarkusTest` `BT/infrastructure/llm/ai/QuestionsAiTest.java` : `propose-questions`,
  `reassess-after-change` ; garde-fous (thème connu, identifiants de questions).
- [ ] T025 [US1] Créer `LLM/ai/QuestionsAi.java` et ses garde-fous ; `LangChainLlmClient`
  délègue.
- [ ] T026 [US1] Évaluation réelle (`questionRelevance`), gates, commit.

### Famille Specification

- [ ] T027 [P] [US1] Tests `SpecificationAiTest.java` :
  - `generate-specification`, `edit-specification`, `merge-conflict`, `structure-imported-text` ;
  - `revise-specification`, `list-changes` ;
  - garde-fous : structure, traçabilité, tableaux, périmètre de révision.
- [ ] T028 [US1] Créer `LLM/ai/SpecificationAi.java` et ses garde-fous.
  - `LangChainLlmClient` et `LangChainConversationLlm` (`revise`, `changes`) délèguent.
  - Supprimer la boucle `MAX_ATTEMPTS` de `LangChainLlmClient` quand plus rien ne l'utilise.
- [ ] T029 [US1] Évaluation réelle :
  - `scenarioStructure`, `revisionCompleteness`, `revisionPreservation`, `revisionStructure` ;
  - `unchangedRecall`, `changesRecall`, `changesPrecision`.

  Puis gates, commit.

### Famille Conversation, dont l'agent (research R5, contracts/agent-tools.md)

- [ ] T030 [P] [US1] Tests `@QuarkusTest` `ConversationAiTest.java` pour `qualify-perimeter`,
  `propose-need` et `compact-memory` sur le rejeu.
- [ ] T031 [US1] Créer `LLM/ai/ConversationAi.java` (méthodes non-agent) ;
  `LangChainConversationLlm` délègue `qualify`, `draftNeed` et `compact`.
- [ ] T032 [P] [US1] Tests unitaires du port et de l'application.
  - `BT/application/conversation/ConverseTest.java` et `ConversePerimeterTest.java`, réécrits sur
    `ConversationLlm.converse(AgentRequest, TurnTools, deltas)`. Le double appelle `TurnTools` :
    - sources numérotées en continu ;
    - étapes enregistrées et publiées ;
    - plafond de 5 (`stepsLeft`) ;
    - une seule analyse de périmètre ;
    - arrêt (`cancelled`) ;
    - erreurs.
  - Les événements SSE attendus sont ceux d'aujourd'hui.
- [ ] T033 [US1] Domaine et application.
  - Créer `BM/domain/port/TurnTools.java`.
  - Remplacer `plan`/`answer` par `converse` dans `BM/domain/port/ConversationLlm.java` (data-model).
  - Faire implémenter `TurnTools` par `BM/application/conversation/Converse.java`, en retirant la
    boucle plan/réponse.
- [ ] T034 [US1] Consigne `BR/prompts/converse-agent.txt` : fusion de `converse-plan` et de
  `converse-answer`.
  - Choisir chercher, analyser le périmètre ou répondre ; une analyse par tour ; dire quand la
    recherche s'est arrêtée.
  - Règles de réponse reprises telles quelles (citations, abstention, modules non impactés,
    spécification discutée).
  - Règle de sécurité des balises, dont `<echange-outils>`.
  - Supprimer `converse-plan.txt` et `converse-answer.txt`.
- [ ] T035 [P] [US1] Tests unitaires `BT/infrastructure/llm/ai/AgentToolsTest.java` et
  `SearchCapTest.java` (contracts/agent-tools.md) :
  - validation des paramètres ;
  - format `<sources>` ;
  - refus au 6e appel ;
  - second périmètre refusé ;
  - outil inconnu ;
  - arrêt ;
  - erreur d'exécution sans détail interne ;
  - journal sans requête.
- [ ] T036 [US1] Créer `LLM/ai/AgentTools.java`, `LLM/ai/SearchCap.java` et la méthode
  `ConversationAi.converse` (`TokenStream`, `InvocationParameters`, `maxToolCallingRoundTrips = 7`,
  `hallucinatedToolNameStrategy`).
  - `LangChainConversationLlm.converse` branche `beforeToolExecution`, `onToolExecuted`,
    `onPartialResponseWithContext` (arrêt), `onPartialThinking` et `onError`.
- [ ] T037 [US1] Fixtures du rejeu.
  - Réécrire en entrées `converse-agent` (`toolCalls`, `afterTools`, `text`) :
    - les 19 fixtures `converse-plan` / `converse-answer` de `e2e/fixtures/llm/` ;
    - leurs équivalents de `BTR/llm-replay/`.
  - Adapter les IT de conversation, de discussion et SSE.
  - Passer `npm --prefix e2e test` complet.
- [ ] T038 [US1] Évaluation réelle complète (accord du mainteneur) : citations, abstention,
  incohérences, discussion, périmètre (`perimeterAnswerFocus`), `injectionResistance`.
  - En cas de recul, ajuster `converse-agent.txt` et noter l'ajustement dans research R5.
  - Mettre à jour `last-run.json`.
  - Gates, commit `refactor(conversation): agent à outils sur les AI Services`.
- [ ] T039 [US1] Mesurer SC-004 : délai de première réponse visible avant et après, sur trois
  questions du quickstart en dev mode `live`. Consigner le résultat dans research R5.

**Checkpoint** : US1 et US2 complètes. Toutes les opérations passent par les AI Services.

---

## Phase 5: User Story 3 — Ajouter une capacité sans toucher à la boucle (P2)

**Goal** : un outil ou une consigne réutilisable s'ajoute par déclaration.

**Independent Test** : quickstart 5.

- [ ] T040 [P] [US3] Test `@QuarkusTest` `BT/infrastructure/llm/ai/AgentToolsExtensionTest.java`,
  avec un AI Service de test, un outil fictif et une fixture rejouée.
  - L'outil est appelé et l'étape est publiée.
  - Le plafond s'applique.
  - Aucune classe de `LLM/` ni de `Converse` n'est modifiée pour l'ajouter.
- [ ] T041 [US3] Consignes réutilisables (FR-011).
  - Dossier `BR/prompts/skills/` et chargeur `LLM/ai/SkillLibrary.java` : une ressource Markdown
    incluse dans le message système par l'opération qui la déclare.
  - Test `SkillLibraryTest` : contenu en système, jamais dans le message utilisateur ; ressource
    absente → erreur au démarrage.
- [ ] T042 [US3] Documenter dans `CONTRIBUTING.md` comment ajouter une opération, un outil ou une
  consigne réutilisable. Rappeler la liste blanche, le garde-fou et la fixture de rejeu.

---

## Phase 6: User Story 4 — Le code vit sous le nom kawamind (P2)

**Goal** : suites du renommage (T003 à T005) : contrôle permanent, passerelle MCP, notes de
version.

**Independent Test** : quickstart 1.

- [ ] T043 [P] [US4] Règle ArchUnit dans `BT/architecture/HexagonalArchitectureTest.java` :
  - toutes les classes sont sous `fr.kawamind` ;
  - seul `infrastructure.llm` dépend de `com.github.copilot` et de `dev.langchain4j.service`.
- [ ] T044 [P] [US4] Test `BT/infrastructure/platform/McpCommandTest.java` : la commande affichée
  lance `fr.kawamind.mcp.McpBridge`.
  - Test E2E existant de la carte « Accès des agents » ajusté si la commande y est vérifiée.
- [ ] T045 [US4] Section « Mise à jour » du `README.md` : la commande de la passerelle MCP change,
  et il faut recopier celle des réglages (FR-019a). Elle sera reprise dans la description de la
  release.
- [ ] T046 [US4] Vérification manuelle sur l'installation existante (quickstart 1) : données,
  réglages et jeton Jira présents après mise à jour.

---

## Phase 7: User Story 5 — Les tests rejouent les échanges sans Copilot (P3)

**Goal** : le rejeu est documenté et ses échecs sont lisibles.

**Independent Test** : quickstart 2, et un réenregistrement selon la procédure.

- [ ] T047 [US5] `e2e/fixtures/llm/README.md` documente :
  - le format (contracts/replay-format.md) ;
  - l'appariement par opération, étape et fragments ;
  - la procédure de réenregistrement en dev mode `live` ;
  - le contenu confidentiel à ne jamais copier.
- [ ] T048 [US5] Vérifier hors ligne (réseau coupé ou proxy refusé) que `npm --prefix e2e test`
  passe sans appel à Copilot.

---

## Phase 8: Polish & Cross-Cutting

- [ ] T049 Supprimer le code devenu inutile :
  - `ConversationStream`, `CopilotConversationStream`, `ReplayConversationStream` ;
  - les records `Plan`, `Action`, `PlanRequest` et `AnswerRequest` du port ;
  - le marqueur d'opération resté inutilisé ;
  - les tests associés.

  Couverture ≥ 80 % sur le cœur.
- [ ] T050 [P] Marquer comme remplacées (FR-020) :
  - `specs/001-local-spec-assistant/research.md` R3 (partie AI Services) ;
  - `specs/010-agent-conversationnel/research.md` R1 ;
  - `specs/001-local-spec-assistant/contracts/llm-port.md` (règles d'adaptateur).

  Ajouter un renvoi vers 014 R1, R2 et R8.
- [ ] T051 [P] Mettre à jour `CLAUDE.md` : chemins `fr/kawamind`, boucle de l'agent, rejeu à
  appels d'outils.
- [ ] T052 Dérouler `quickstart.md` en entier (1 à 6), puis gates complets.
- [ ] T053 Évaluation réelle finale (accord du mainteneur), `ci/check-eval.sh`, puis
  `last-run.json` commité.
- [ ] T054 Pousser, ouvrir la PR vers `main`. Le corps donne :
  - les décisions remplacées ;
  - la note MCP ;
  - les résultats de l'évaluation.

---

## Dependencies & Execution Order

- **Setup (T001, T002)** : en premier. T002 peut changer la consigne de T034.
- **Renommage (T003 à T005)** : bloque tout le reste. Commit isolé.
- **Socle (T006 à T014)** : après le renommage. Bloque US1, US2, US3, US5.
- **US2 (T015 à T019)** : après le socle. T019 tourne avec l'évaluation de T038.
- **US1 (T020 à T039)** : après le socle et T015/T016. L'ordre des familles est imposé :
  Knowledge, Questions, Specification, Conversation. Chaque famille est commitée après son
  évaluation.
- **US3 (T040 à T042)** : après T036.
- **US4 (T043 à T046)** : après T005. Indépendante de la refonte.
- **US5 (T047, T048)** : après T037.
- **Polish (T049 à T054)** : après tout.

### Parallel Opportunities

- Socle :
  - T006, T009, T011 et T013 (tests, fichiers distincts) ;
  - T010, T012 et T014 après leurs tests.
- US2 : T015, T017 et T018.
- US1 : dans chaque famille, le test [P] s'écrit pendant la famille précédente. T032 et T035 en
  parallèle.
- US4 : T043 et T044 dès la fin du renommage, en parallèle du socle.

### Parallel Example: socle

```text
T006 CopilotSdkChatModelTest      T009 CopilotSdkStreamingChatModelTest
T011 LlmCallLogTest               T013 ReplayChatModelTest + ReplayStreamingChatModelTest
```

## Implementation Strategy

### MVP

Renommage, socle, US2, puis US1 famille par famille. Chaque famille migrée est livrable seule :
le reste passe encore par l'ancien code. Le MVP est atteint quand l'agent (T038) est migré.

### Livraison incrémentale

1. Commit du renommage (T005).
2. Commit du socle (T014).
3. Commit de la sécurité (T019).
4. Un commit par famille, de T023 à T038.
5. Commits d'US3, d'US4 et d'US5.
6. Commit de nettoyage, puis PR.

Chaque commit passe les gates. Chaque commit de famille passe aussi l'évaluation réelle.
