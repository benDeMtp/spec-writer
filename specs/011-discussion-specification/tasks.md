---
description: "Tâches de la feature 011 : discuter d'une spécification avec l'agent"
---

# Tasks: Discuter d'une spécification avec l'agent

**Input**: `specs/011-discussion-specification/` (plan.md, spec.md, research.md, data-model.md,
contracts/api.md, quickstart.md)

**Tests**: obligatoires (constitution III, TDD) : chaque test est écrit et vu échouer avant le
code. `domain` et `application` sont couverts par des tests unitaires (JaCoCo ne compte pas les
`@QuarkusTest`).

**Organization**: les quatre histoires de la spec.

- US1 : discuter d'une spécification à côté du document (MVP)
- US2 : proposer une nouvelle version d'après la discussion
- US3 : reprendre ou recommencer la discussion ; suppression
- US4 : discuter d'un ticket Jira importé

**Prérequis** : la branche part de `feature/014-agent-conversationnel` (010, PR #12). La rebaser sur
`main` dès la fusion.

## Format: `[ID] [P?] [Story] Description`

- **[P]** : parallélisable (fichiers différents, aucune dépendance sur une tâche non finie).
- Abréviations des chemins :
  - `BM/` : `backend/src/main/java/dev/specwriter/`
  - `BT/` : `backend/src/test/java/dev/specwriter/`
  - `BR/` : `backend/src/main/resources/`
  - `FA/` : `frontend/src/app/`

---

## Phase 1: Setup

- [X] T001 Migration `BR/db/migration/V22__specification_discussion.sql` (data-model.md) :
  - table `specification_discussion` :
    - `conversation_id TEXT PRIMARY KEY REFERENCES conversation(id) ON DELETE CASCADE` ;
    - `session_id TEXT NULL REFERENCES session(id) ON DELETE SET NULL` ;
    - `specification_name TEXT NOT NULL CHECK (length(specification_name) BETWEEN 1 AND 200)` ;
    - `current INTEGER NOT NULL DEFAULT 1` ;
    - `created_at TEXT NOT NULL` ;
  - index unique `ON specification_discussion(session_id) WHERE current = 1 AND session_id IS NOT NULL`,
    et index sur `session_id` ;
  - table `version_discussion` :
    - `version_id TEXT PRIMARY KEY REFERENCES specification_version(id) ON DELETE CASCADE` ;
    - `conversation_id TEXT NULL REFERENCES conversation(id) ON DELETE SET NULL` ;
  - `ALTER TABLE conversation_message ADD COLUMN specification_version INTEGER NULL`.
- [X] T002 [P] Valeurs ajoutées, avec leur libellé ou message métier :
  - `SourceKind.DISCUSSION` dans `BM/domain/conversation/SourceKind.java` ;
  - `Thinking.Stage.REVISE` dans `BM/domain/conversation/Thinking.java` ;
  - `VersionOrigin.DISCUSSION` dans `BM/domain/specification/VersionOrigin.java` ;
  - `ErrorCode.DISCUSSION_NOT_FOUND` (404) et `ErrorCode.NOT_A_DISCUSSION` (422, « Cette
    conversation n'est pas la discussion d'une spécification. ») dans
    `BM/domain/error/ErrorCode.java`.

---

## Phase 2: Foundational

- [X] T003 [P] Tests d'abord (`BT/domain/conversation/SpecificationSectionsTest.java`), puis
  `BM/domain/conversation/SpecificationSections.java`.
  - Découpe le Markdown d'une version en sections aux titres de niveau 2 et 3.
  - Ancre stable : le titre en minuscules, sans accents, mots joints par `-`, suffixe `-2`, `-3`
    en cas de doublon.
  - Référence `spec:<sessionId>@v<n>#<ancre>`, titre « <nom>, <titre de section> ».
  - Le texte d'avant le premier titre devient la section `introduction`.
  - `fit(sections, part)` : garde le texte des premières sections tant qu'elles tiennent dans
    `part` ; les suivantes sont réduites à leur titre et marquées `partial`.
  - Écart : la classe est dans `domain/specification` (elle lit les libellés de
    `MarkdownRenderer`), et les rubriques connues prennent les ancres fixes du document affiché
    (`summary`, `use-cases`, `UC-01`, `scenarios-UC-01`…) ; le calcul par le titre ne sert qu'aux
    rubriques inconnues.
- [X] T004 [P] Tests d'abord (`BT/domain/conversation/SpecificationDiscussionTest.java`), puis
  `BM/domain/conversation/SpecificationDiscussion.java` :
  - champs `(conversationId, sessionId?, specificationName, current, createdAt)` ;
  - nom de 1 à 200 caractères ;
  - `detached()` : `sessionId` absent et `current` faux ;
  - `retired()` : `current` faux.
- [X] T005 [P] Ports :
  - `BM/domain/port/SpecificationDiscussionRepository.java` : `current(sessionId)`,
    `ofConversation(conversationId)`, `bySession(sessionId)`, `save`, `retire(conversationId)`,
    `detachAll(sessionId)`, `countBySession(sessionId)`, `renameAll(sessionId, name)`,
    `byConversations(Set<UUID>)` ;
  - `BM/domain/port/VersionRepository` : `linkDiscussion(versionId, conversationId)` et lecture de
    l'origine `DISCUSSION` avec `conversationId` ;
  - `BM/domain/port/ConversationRepository` : `ConversationMessage.specificationVersion`
    enregistré et relu.
- [X] T006 Tests d'abord (`BT/infrastructure/persistence/SpecificationDiscussionPersistenceIT.java`),
  puis :
  - `BM/infrastructure/persistence/conversation/SqliteSpecificationDiscussionRepository.java` ;
  - le lien de version dans le dépôt des versions ;
  - la colonne `specification_version` dans `SqliteConversationRepository`.
  
  Le test couvre l'unicité de la discussion suivie, `SET NULL` à la suppression de la session,
  `CASCADE` à la suppression de la conversation, et l'origine `DISCUSSION` relue (`AI_EDIT` en
  base). Doubles en mémoire dans `BT/support/` (`InMemorySpecificationDiscussions`, versions).
- [X] T007 [P] Tests d'abord, puis la part `discussedSpecification` dans
  `BM/domain/knowledge/KnowledgeBudget.java` :
  - prise sur `RESERVE` : 40 000 caractères en standard ;
  - proportionnée par `forPromptTokens` comme les autres parts ;
  - plancher de 8 000.

**Checkpoint** : modèle, ports, persistance et budget prêts.

---

## Phase 3: User Story 1 - Discuter d'une spécification à côté du document (Priority: P1) 🎯 MVP

**Goal**: ouvrir une discussion sur la dernière version, avec la spécification en tête des
sources, citée par section ; incohérences avec le projet.

**Independent Test**: IA rejouée, ouvrir la discussion d'une spécification, poser une question,
vérifier la citation d'une section et d'une autre source, sans quitter l'écran (quickstart 1 à 5,
11).

- [X] T008 [US1] Tests d'abord (`BT/application/conversation/DiscussSpecificationTest.java`), puis
  `BM/application/conversation/DiscussSpecification.java` :
  - `open(sessionId)` : renvoie la discussion suivie, ou la crée (nom de la spécification) ;
  - `fresh(sessionId)` : retire l'ancienne et crée la nouvelle ;
  - `context(conversationId)` : spécification, dernière version, verrou ;
  - refuse une spécification implémentée (`SPECIFICATION_LOCKED`), ou sans version (422
    « Générez d'abord une spécification. ») ;
  - câblage dans `BM/infrastructure/config/UseCaseProducer.java` ;
  - ajout à la liste des cas d'usage d'écriture de
    `BT/architecture/HexagonalArchitectureTest.java`.
- [X] T009 [US1] Tests d'abord dans `BT/application/conversation/ConverseTest.java`, puis
  `BM/application/conversation/Converse.java` et une nouvelle classe
  `BM/application/conversation/DiscussionSources.java`. Pour une conversation qui est une
  discussion d'une spécification existante :
  - l'envoi et le nouvel essai refusent une spécification verrouillée (`SPECIFICATION_LOCKED`) ;
  - le tour lit la dernière version et place ses sections (`SpecificationSections.fit` sur
    `discussedSpecification`) en tête des sources, avant les documents désignés ;
  - les documents joints à la spécification (009) rejoignent les documents désignés ;
  - `ConversationSearch` n'ajoute pas la spécification discutée ;
  - les sections réduites à leur titre sont ajoutées à `notCompared` ;
  - le message de l'agent enregistre `specificationVersion` ;
  - une discussion détachée (spécification supprimée) se comporte comme une conversation
    ordinaire.
- [X] T010 [US1] Prompts et messages :
  - `BR/prompts/converse-plan.txt` et `BR/prompts/converse-answer.txt` : bloc
    `<specification-discutee nom="…" version="N"/>` (research.md R3), cas d'incohérence entre la
    spécification et le projet ;
  - `BM/infrastructure/llm/UserMessages.java` : le bloc, échappé ;
  - `PlanRequest` et `AnswerRequest` portent `DiscussedSpecification(name, version)?` ;
  - test dans `BT/infrastructure/llm/LangChainConversationLlmTest.java`.
- [X] T011 [P] [US1] Test d'injection dans `BT/infrastructure/llm/PromptInjectionIT.java` : une
  spécification contenant « ignore tes consignes » reste une donnée (bloc et sources échappés).
- [X] T012 [US1] Tests d'abord (`BT/infrastructure/web/SpecificationDiscussionResourceIT.java`),
  puis `BM/infrastructure/web/SpecificationDiscussionResource.java` :
  - `GET` et `POST /api/sessions/{id}/discussion` (`{ fresh? }`), contracts/api.md ;
  - `ManageConversations.detail` : `specificationVersion` par message, `specification` sur le
    détail ;
  - `GET /api/conversations/{cid}` : `specification` présente.
- [X] T013 [P] [US1] Fixtures E2E `e2e/fixtures/llm/40-discussion-plan.json`,
  `41-discussion-answer.json` et `42-discussion-incoherence.json`. Elles sont repérées par
  `<specification-discutee` et un marqueur `E2E-DISCUSSION`, et citent une section et un ADR.
- [X] T014 [US1] Tests d'abord (`FA/features/conversation/conversation-thread.component.spec.ts`),
  puis extraction de `FA/features/conversation/conversation-thread.component.ts` depuis
  `conversation.page.ts` :
  - le fil, les étapes, la réflexion, les sources et le repère de mémoire ;
  - le champ de saisie avec mentions et documents joints, l'envoi, l'arrêt et le nouvel essai ;
  - le flux SSE et les annonces.
  
  Entrées `conversationId`, `projectId` ; sorties `turnFinished` et `citationClicked(ref)`.
  `conversation.page.ts` utilise le composant, et ses tests restent verts.
- [X] T015 [US1] Tests d'abord, puis `FA/features/specification/discussion-panel.component.ts` :
  - « Contexte : <nom>, version N », et le repère dans le fil devant le premier message de chaque
    version ;
  - l'avertissement quand une version plus ancienne est affichée ;
  - le focus placé dans le champ à l'ouverture et rendu au bouton à la fermeture ;
  - « Fermer ».
  
  API ajoutée à `FA/core/conversations/conversations.api.ts` (`discussion`, `openDiscussion`) ;
  modèles à `conversations.model.ts` (data-model.md, Front).
- [X] T016 [US1] `FA/features/specification/specification.page.ts` :
  - le bouton « Discuter avec l'IA » remplace « Faire évoluer avec l'IA » ; il est indisponible et
    expliqué quand la spécification est verrouillée ;
  - disposition en deux colonnes, et le panneau sous le document au-dessous de 64 rem ;
  - ancres des sections du document, calculées comme `SpecificationSections` (fonction partagée
    `FA/features/specification/section-anchor.ts`, testée avec les mêmes cas) ;
  - une citation `spec:…#ancre` de la spécification discutée fait défiler le document jusqu'à la
    section et lui donne le focus ;
  - `?discussion=1` ouvre le panneau ;
  - test dans `specification.page.spec.ts`.
  - Écart : pas de `section-anchor.ts` ; l'écran rend le contenu structuré avec des `id` fixes,
    les mêmes que les ancres connues de `SpecificationSections`. Le panneau de proposition directe
    ne sert plus qu'à la fusion d'un conflit Jira.

**Checkpoint** : la discussion fonctionne seule, sans proposition.

---

## Phase 4: User Story 2 - Proposer une nouvelle version d'après la discussion (Priority: P1)

**Goal**: réécriture d'après la discussion, relecture par diff, version « Issue de la discussion »
liée à la conversation, contexte qui passe à la nouvelle version.

**Independent Test**: après deux échanges, proposer, relire le diff, appliquer ; vérifier la
version, son origine, son lien et le repère « Contexte : version N » (quickstart 6 à 8, 14).

- [X] T017 [US2] Port et schéma :
  - `ConversationLlm.revise(ReviseRequest) → Revision` dans `BM/domain/port/ConversationLlm.java`,
    avec les records de data-model.md ;
  - schéma strict `REVISION = { changed: boolean, reason: string, specification: SPECIFICATION }`
    dans `BM/infrastructure/llm/LlmSchemas.java` ;
  - `BM/infrastructure/llm/LangChainConversationLlm.revise` (3 tentatives, comme `call`) ;
  - blocs `<specification-courante>`, `<discussion>` (messages de l'utilisateur numérotés
    `discussion:<cid>#<ordinal>`), `<sources-citees>` et `<reponses>` dans `UserMessages.java` ;
  - prompt `BR/prompts/revise-specification.txt` : ne changer que ce que la discussion a établi,
    recopier le reste, rattacher chaque ajout à un message ou une source, sinon hypothèse ;
    `changed: false` avec la raison ;
  - tests dans `LangChainConversationLlmTest` et `ScriptedConversationLlm.revise`.
- [X] T018 [US2] Traçabilité des messages de la discussion :
  - `BM/application/specification/DraftValidation.java` et `TraceabilityValidator` acceptent les
    références `discussion:<cid>#<ordinal>` fournies ;
  - `MarkdownRenderer` les écrit « Discussion du <date>, message n » ;
  - contrôle de préservation : un identifiant de use case ou de scénario de la version courante
    absent de la proposition donne une correction (« UC-3 a disparu : recopiez-le s'il n'est pas
    concerné »), puis un échec après 3 tentatives ;
  - tests d'abord dans `BT/application/specification/DraftValidationTest.java` et
    `MarkdownRendererTest`.
  - Écart : les codes étant attribués par position, la préservation compare les titres des use
    cases ; un use case disparu est redemandé, mais la dernière tentative est gardée (le retrait
    peut être ce que la discussion a établi). Les sources DISCUSSION de la version courante restent
    citables (passées dans <sources-citees>).
- [X] T019 [US2] Tests d'abord (`BT/application/conversation/ProposeRevisionTest.java`), puis
  `BM/application/conversation/ProposeRevision.java`.
  - Refus : `NOT_A_DISCUSSION` ; `SPECIFICATION_LOCKED` ; `OPERATION_IN_PROGRESS` pendant une
    réponse ; accord Copilot manquant.
  - Déroulement : tour `TurnEvents` (`started`, `stage REVISE`, réflexion via
    `ConversationLlm.thinking`), mémoire et échanges par `ConversationHistory.prepare`, sources
    citées dans la discussion relues, `DraftValidation.produce`.
  - Résultat : `changed` donne `ProposalStore.propose(…, DISCUSSION, conversationId)`, puis
    `done { proposalId }` ; sinon `done { unchanged, reason }` ; une erreur donne `error`.
  - La réflexion n'est pas gardée.
  - Câblage dans `UseCaseProducer`, et ajout à la liste ArchUnit des cas d'écriture.
- [X] T020 [US2] Proposition et acceptation :
  - `BM/application/specification/ProposalStore.java` : champ `conversationId` ;
    `discard(sessionId, proposalId)` ;
  - `BM/application/specification/AcceptAiEdit.java` : pour `DISCUSSION`, version d'origine
    `DISCUSSION` liée par `linkDiscussion` ; verrou revérifié ;
  - tests d'abord dans `BT/application/specification/AcceptAiEditTest.java`.
- [X] T021 [US2] Tests d'abord (`BT/infrastructure/web/ProposalResourceIT.java`), puis :
  - `POST /api/conversations/{cid}/proposals` (202), avec les événements `done` de proposition
    dans `ConversationResource.name` et la sérialisation ;
  - `GET` et `DELETE /api/sessions/{id}/proposals/{proposalId}` ;
  - `VersionSummary` et `SpecificationView` avec `origin: DISCUSSION` et `conversationId` ;
  - fixtures `e2e/fixtures/llm/43-revise-specification.json` (ajout d'un cas d'erreur) et
    `44-revise-unchanged.json` (`changed: false`).
  - Écart : deux ressources (`ProposalResource` sous `/api/sessions/{id}/proposals`,
    `DiscussionProposalResource` sous `/api/conversations/{cid}/proposals`), JAX-RS ne revenant pas
    en arrière d'une classe à l'autre. Le rejeu accepte `__DISCUSSION_n__` (référence du n-ième
    message de l'utilisateur).
- [X] T022 [US2] Tests d'abord, puis `FA/features/specification/proposal-review.component.ts`,
  extrait de `ai-edit.panel.ts` : diff, « Appliquer », « Abandonner », version de départ et version
  créée, chronomètre pendant l'application.
- [X] T023 [US2] `discussion-panel.component.ts` :
  - « Proposer une nouvelle version », sans saisie (FR-008a), indisponible pendant une réponse ;
  - suivi du flux de proposition : titre « L'agent rédige une nouvelle version », chronomètre,
    réflexion ;
  - « aucun changement établi » avec la raison ;
  - relecture par `proposal-review`, puis rechargement de la spécification et repère
    « Contexte : version N ».
  
  `FA/features/specification/versions-panel.component.ts` : « Issue de la discussion », avec un
  lien vers la conversation. Tests Vitest.
  - Écart : la relecture s'affiche dans la colonne du document (le diff y a la place), le panneau
    la signale et attend.
- [X] T024 [US2] Conflit Jira : `specification.page.ts` affiche `mergeProposal` dans
  `proposal-review`, sans discussion (FR-018). Retrait de la consigne en un coup :
  - `FA/features/ai-edit/` ;
  - `BM/application/specification/ProposeAiEdit.java` ;
  - `POST /api/sessions/{id}/ai-edits` dans `AiEditResource` (`accept` reste) ;
  - `LlmClient.editSpecification`, `EditRequest` et `BR/prompts/edit-specification.txt` ;
  - `MentionField.INSTRUCTION` et ses usages ;
  - leurs tests.
  
  Les tests restants restent verts.
  - Écart : « C'est la même » (002, FR-035) propose encore le besoin comme modification du ticket
    importé : `ProposeAiEdit`, `LlmClient.editSpecification`, `EditRequest`, le prompt et
    `MentionField.INSTRUCTION` (qui désigne aussi les documents cités dans la discussion) restent ;
    seuls la route `POST …/ai-edits`, `JiraApi.proposeEdit` et `features/ai-edit/` sont retirés.

**Checkpoint** : US1 et US2 donnent la fonction complète qui remplace l'ancienne.

---

## Phase 5: User Story 3 - Reprendre ou recommencer la discussion (Priority: P2)

**Goal**: discussion suivie reprise, nouvelle discussion, mention dans la liste des conversations,
suppression avec ou sans discussions.

**Independent Test**: discuter, redémarrer, retrouver ; nouvelle discussion ; supprimer la
spécification avec la case cochée puis décochée (quickstart 9, 10, 12).

- [X] T025 [US3] Tests d'abord, puis la liste et le renommage :
  - `ManageConversations.list` porte `specification { sessionId, name }` (lecture groupée
    `byConversations`) ;
  - le renommage d'une spécification met à jour `specification_name` (`renameAll`, dans
    `ManageSessions.rename`) ;
  - `ProjectConversationResource` et son IT.
- [X] T026 [US3] Tests d'abord (`BT/application/session/ManageSessionsTest.java`), puis
  `ManageSessions.delete(id, keepDiscussions)` :
  - `false` : supprime les conversations rattachées, puis la spécification ;
  - `true` : `detachAll` ;
  - refus pendant une réponse ou une proposition sur une discussion rattachée.
  
  `DELETE /api/sessions/{id}?discussions=delete|keep` et
  `GET /api/sessions/{id}/discussions/count`, avec leur IT.
- [X] T027 [P] [US3] `FA/features/conversation/conversation-list.component.ts` : mention
  « Spécification : <nom> », ou « spécification supprimée » ; une discussion ouverte dans l'écran
  Conversation montre son contexte et le lien « Ouvrir la spécification » (FR-015). Tests Vitest.
- [X] T028 [US3] Confirmation de suppression :
  - `FA/shared/ui/confirm-dialog.component.ts` : case facultative (`option: { label, checked }`,
    résultat `{ confirmed, option }`) ;
  - `FA/features/sessions/sessions.page.ts` et `specification.page.ts` : case « Supprimer aussi
    ses discussions », cochée par défaut, montrée seulement s'il y en a (compte) ;
  - « Nouvelle discussion » dans `discussion-panel` ;
  - tests Vitest.

---

## Phase 6: User Story 4 - Discuter d'un ticket Jira importé (Priority: P3)

**Goal**: depuis un ticket, importer puis ouvrir la spécification avec la discussion ; un ticket
déjà importé ouvre la spécification existante.

**Independent Test**: depuis un ticket simulé, « Discuter avec l'IA », deux fois (quickstart 13).

- [X] T029 [US4] Tests d'abord, puis `BM/application/jira/ImportJiraIssue.java` et
  `JiraIssueResource` : un ticket déjà lié renvoie `200` avec la session existante, au lieu de
  `409 ALREADY_LINKED`. Mise à jour de `JiraIssueResourceIT` et des usages de `ALREADY_LINKED`.
- [X] T030 [US4] `FA/features/search/item-detail.page.ts` : bouton « Discuter avec l'IA » ; import,
  puis navigation vers `/sessions/{id}` avec `?discussion=1` ; mise à jour de
  `item-detail.page.spec.ts`.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T031 [P] Évaluation (research.md R10) :
  - `backend/src/test/resources/eval/discussion/cases.json` : spécification fictive Aurora,
    corpus, questions, discussions, changements attendus, use cases à préserver, cas sans
    changement ;
  - `BT/eval/DiscussionRuns.java` et `BT/eval/DiscussionEval.java` ;
  - métriques `discussionCitation` (0.9), `discussionInconsistency` (0.8), `revisionCompleteness`
    (0.9), `revisionPreservation` (0.9), `revisionStructure` (1.0) et `unchangedRecall` (0.9),
    dans `EvalBaseline` et `baseline.json`.
- [X] T032 [P] E2E `e2e/tests/us14-discussion.spec.ts` (quickstart 1 à 14), axe sur la
  spécification avec la discussion ouverte. Mise à jour des E2E qui utilisaient « Faire évoluer
  avec l'IA » et la consigne (us6, us9 et suivants).
- [X] T033 [P] Documentation :
  - `README.md` : discussion d'une spécification et suppression ;
  - `specs/002-projets-contexte-mcp/contracts/openapi.yaml` ou contrats équivalents ;
  - `CLAUDE.md` si besoin ;
  - revue d'ergonomie du panneau (web-design-guidelines), avec les écarts corrigés ou justifiés
    dans `specs/011-discussion-specification/checklists/ux-review.md`.
- [X] T034 Gates avant chaque commit (CLAUDE.md) : `rm -f backend/target/jacoco.exec`, `./mvnw -B
  verify -DskipDependencyCheck`, `./mvnw -B package -DskipTests`, `npm --prefix e2e test`.
- [X] T035 Évaluation réelle avec l'accord du mainteneur (`./mvnw -pl backend verify -Peval`),
  `last-run.json` commité, `bash ci/check-eval.sh` vert.
- [X] T036 Essai manuel par l'utilisateur (quickstart.md) ; résultat dans
  `specs/011-discussion-specification/checklists/validation.md`.

---

## Dependencies & Execution Order

- Setup (T001, T002), puis Foundational (T003 à T007), qui bloque toutes les histoires.
- **US1** (T008 à T016) : MVP.
  - T014 (fil partagé) précède T015 et T016.
- **US2** (T017 à T024) dépend de US1 (panneau, contexte).
  - T017 et T018 précèdent T019.
  - T020 précède T021.
  - T024 vient en dernier dans US2.
- **US3** (T025 à T028) dépend de la Foundational et du panneau (T015) ; elle peut suivre US1 en
  parallèle de US2, côté backend.
- **US4** (T029, T030) dépend de US1 (`?discussion=1`).
- **Polish** :
  - T031 dès T019 ;
  - T032 à la fin ;
  - T034 avant chaque commit ;
  - T035 avant la PR ;
  - T036 en dernier.

## Parallel Example

```text
Foundational : T003 (sections) | T004 (discussion) | T007 (budget)
US1          : T011 (injection) | T013 (fixtures) pendant T009
US2 / US3    : T025, T026 (backend US3) pendant T017 à T019
Polish       : T031 (évaluation) | T033 (documentation)
```

## Implementation Strategy

1. **MVP** : Setup, Foundational, puis US1. On discute d'une spécification avec l'agent, qui la
   cite par section ; c'est démontrable seul.
2. US2 : la proposition remplace la consigne en un coup, qui est retirée à la fin de l'histoire
   (T024).
3. US3 : reprise, nouvelle discussion, suppression.
4. US4 : parcours Jira.
5. Polish : évaluation (accord du mainteneur), E2E, revue d'ergonomie, documentation.

Commit par groupe logique après les gates. L'évaluation réelle est faite une seule fois, avant la
PR, une fois les prompts stabilisés.
