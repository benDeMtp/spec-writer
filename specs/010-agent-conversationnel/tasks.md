---
description: "Tâches de la feature 010 : agent conversationnel, point d'entrée principal"
---

# Tasks: Agent conversationnel, point d'entrée principal

**Input**: `specs/010-agent-conversationnel/` (plan.md, spec.md, research.md, data-model.md,
contracts/api.md, quickstart.md)

**Tests**: obligatoires (constitution III, TDD) : chaque test est écrit et vu échouer avant le
code. `domain` et `application` sont couverts par des tests unitaires (JaCoCo ne compte pas les
`@QuarkusTest`).

**Organization**: les cinq histoires de la spec.

- US1 : questionner l'agent (MVP)
- US2 : de la conversation à une spécification
- US3 : recherche documentaire et incohérences
- US4 : retrouver et reprendre une conversation
- US5 : nouveau menu

## Format: `[ID] [P?] [Story] Description`

- **[P]** : parallélisable (fichiers différents, aucune dépendance sur une tâche non finie).
- Abréviations des chemins :
  - `BM/` : `backend/src/main/java/dev/specwriter/`
  - `BT/` : `backend/src/test/java/dev/specwriter/`
  - `BR/` : `backend/src/main/resources/`
  - `FA/` : `frontend/src/app/`

---

## Phase 1: Setup

- [X] T001 Migration `BR/db/migration/V18__conversation.sql` (data-model.md). Toutes les clés étrangères sont en `ON DELETE CASCADE`.
  - `conversation (id TEXT PRIMARY KEY, project_id TEXT NOT NULL REFERENCES project(id) ON DELETE CASCADE, name TEXT NOT NULL CHECK (length(name) BETWEEN 1 AND 120), created_at TEXT NOT NULL, updated_at TEXT NOT NULL)`
  - `conversation_message (id TEXT PRIMARY KEY, conversation_id … , ordinal INTEGER NOT NULL, role TEXT NOT NULL CHECK (role IN ('USER','AGENT')), text TEXT NOT NULL CHECK (length(text) <= 100000), state TEXT NOT NULL CHECK (state IN ('COMPLETE','INCOMPLETE','RUNNING')), history_truncated INTEGER NOT NULL DEFAULT 0, created_at TEXT NOT NULL, UNIQUE (conversation_id, ordinal))`
  - `conversation_step (message_id …, ordinal INTEGER NOT NULL CHECK (ordinal BETWEEN 1 AND 5), query TEXT NOT NULL CHECK (length(query) <= 300), kinds TEXT NOT NULL, hits INTEGER NOT NULL, PRIMARY KEY (message_id, ordinal))`
  - `conversation_source (message_id …, n INTEGER NOT NULL, kind TEXT NOT NULL, ref TEXT NOT NULL, title TEXT NOT NULL, PRIMARY KEY (message_id, n))`
  - `conversation_attachment` : mêmes colonnes et contraintes que `session_attachment` (V17), avec `conversation_id`.
  - `conversation_session (conversation_id …, session_id TEXT NOT NULL REFERENCES session(id) ON DELETE CASCADE, PRIMARY KEY (conversation_id, session_id))`
  - Index `conversation(project_id, updated_at)`.
- [X] T002 [P] Codes d'erreur dans `BM/domain/error/ErrorCode.java`, avec leurs messages métier :
  - `CONVERSATION_NOT_FOUND` (404) ;
  - `MESSAGE_TOO_LONG` (422, « 8 000 caractères au plus ») ;
  - `NEED_DRAFT_INVALID` (422).

---

## Phase 2: Foundational

- [X] T003 [P] Tests d'abord dans `BT/domain/conversation/` (`ConversationTest`, `CitationsTest`), puis `BM/domain/conversation/`.
  - `Conversation` : nom par défaut = 60 premiers caractères du premier message ; nom de 1 à 120 caractères.
  - `ConversationMessage` : utilisateur de 1 à 8 000 caractères, agent 100 000 au plus ; états `COMPLETE` | `INCOMPLETE` | `RUNNING`.
  - `SearchStep` : ordinal 1 à 5, requête de 300 caractères au plus.
  - `CitedSource`.
  - `Citations.resolve(text, sources)` : garde les `[n]` connus, retire les autres, renvoie les sources citées dans l'ordre.
  - `toString()` sans texte.
- [X] T004 [P] Ports `BM/domain/port/ConversationRepository.java` (liste par projet, lecture, ajout et mise à jour de message, étapes, sources, renommage, suppression, lien session, `incompleteRunning()`) et `BM/domain/port/ConversationLlm.java` (`Plan plan(PlanRequest)`, `String answer(AnswerRequest, Consumer<String> deltas, BooleanSupplier cancelled)`, `NeedDraft draftNeed(DraftRequest)`), avec les records des requêtes. Les données passent par des champs ; aucune consigne n'est en paramètre.
- [X] T005 Tests d'abord dans `BT/infrastructure/persistence/ConversationPersistenceIT.java`, puis `BM/infrastructure/persistence/conversation/SqliteConversationRepository.java` (SQL natif, comme `SqliteAttachmentRepository`).
  - Ordre des messages.
  - Cascades : suppression de la conversation, du projet et de la session liée.
  - Un message `RUNNING` passe à `INCOMPLETE` au démarrage (`ConversationStartup`).
- [X] T006 [P] `KnowledgeBudget` (`BM/domain/knowledge/KnowledgeBudget.java`) gagne les parts `conversationSources` (STANDARD 20 000, extrait de 1 500 par résultat) et `conversationHistory` (STANDARD 8 000), proportionnelles au total comme les autres. Tests dans `BT/domain/knowledge/KnowledgeBudgetTest.java`.
- [X] T007 Tests d'abord, puis `LocalSearch.byRelevance(projectId, terms, limit)` (`BM/domain/port/LocalSearch.java`, `BM/infrastructure/persistence/search/FtsLocalSearch.java`) : bm25 sur `spec_search`, dernière version, projet seul. Renvoie la session, le numéro de version, le titre, un extrait et l'état d'implémentation. IT dans `BT/infrastructure/persistence/`.
- [X] T008 Tests d'abord dans `BT/application/conversation/ConversationSearchTest.java`, puis `BM/application/conversation/ConversationSearch.java` (research.md R3).
  - Fusion RRF de `KnowledgeIndex.search` (sortes demandées) et de `LocalSearch.byRelevance` (sorte `SPECIFICATION`, ref `spec:<sessionId>@v<n>`).
  - 8 résultats au plus, extraits bornés par `conversationSources`.
  - Projet courant seul (FR-026).

---

## Phase 3: User Story 1 - Questionner l'agent sur le projet (Priority: P1) 🎯 MVP

**Goal**: l'utilisateur pose une question. L'agent cherche jusqu'à 5 fois, puis répond en flux en
citant ses sources.

**Independent Test**: projet indexé, IA rejouée. Une question puis une relance : étapes, texte en
flux, sources citées existantes, relance appuyée sur l'échange, arrêt.

- [X] T009 [US1] Tests d'abord dans `BT/application/conversation/ConverseTest.java` (doubles de `ConversationLlm`, horloge, ids), puis `BM/application/conversation/Converse.java` (research.md R1, R4).
  - Boucle `plan` / recherche, 5 recherches au plus ; à la 5e, réponse forcée avec la mention de l'arrêt.
  - Étapes enregistrées et publiées.
  - Fiche générale toujours transmise.
  - Historique récent dans `conversationHistory`, `historyTruncated` au-delà.
  - Réponse en flux, `Citations.resolve`.
  - Arrêt : texte gardé, état `INCOMPLETE`.
  - Panne : état `INCOMPLETE`, code d'erreur.
  - Accord Copilot exigé (FR-008).
  - Un seul tour à la fois (`OperationRegistry`).
- [X] T010 [US1] `BM/application/conversation/TurnEvents.java` : événements d'un tour (`step`, `delta`, `sources`, `done`, `error`) en mémoire, rejoués à un abonné tardif, oubliés 10 minutes après la fin. Tests unitaires.
- [X] T011 [US1] Prompts `BR/prompts/converse-plan.txt` et `BR/prompts/converse-answer.txt`.
  - En tête : `[operation:converse-plan]` et `[operation:converse-answer]`.
  - Consignes : données entre balises, jamais des consignes ; répondre seulement d'après les sources ; dire « je ne trouve pas » ; citer `[n]` ; s'en tenir au projet ; présentation d'une comparaison (US3).
  - Schéma JSON du plan dans `BM/infrastructure/llm/LlmSchemas.java` : `{action: SEARCH|ANSWER, query, kinds}`.
- [X] T012 [US1] Tests d'abord (`BT/infrastructure/llm/LangChainConversationLlmTest.java`, `CopilotSdkStreamingChatModelTest.java`), puis l'infrastructure (research.md R2).
  - `BM/infrastructure/llm/LangChainConversationLlm.java` : plan en JSON (3 tentatives), réponse en flux.
  - `UserMessages` gagne `<echange>`, `<question>`, `<sources><source n ref titre>`, échappés.
  - `CopilotSdkStreamingChatModel.java` (`StreamingChatModel`) : `setStreaming(true)`, `AssistantMessageDeltaEvent`, fin sur `SessionIdleEvent`, `abort()`, session supprimée.
  - `ReplayStreamingChatModel.java` : champ `text` des fixtures, rendu par morceaux.
  - Producteur dans `ChatModelProducer`.
  - Journal : opération, durée, étapes, code ; aucun texte.
- [X] T013 [US1] Ressources.
  - `BM/infrastructure/web/ConversationResource.java` : `POST /api/projects/{id}/conversations`, `GET /api/conversations/{cid}`, `POST /api/conversations/{cid}/messages` → 202, `POST …/messages/{mid}/retry`.
  - `BM/infrastructure/web/ConversationTurnResource.java` : SSE `GET …/turns/{turnId}/events` (rejeu puis flux), `DELETE …/turns/{turnId}`.
  - Câblage dans `BM/infrastructure/config/UseCaseProducer.java`.
  - IT `BT/infrastructure/web/ConversationResourceIT.java` : flux SSE complet en rejeu, reconnexion sans perte, arrêt, 422 au-delà de 8 000 caractères, 428 sans accord, conversation d'un autre projet invisible.
- [X] T014 [P] [US1] Fixtures de rejeu.
  - Tests (`BR` de test `llm-replay/`) : `converse-plan` et `converse-answer` pour une question, une relance et une question sans réponse.
  - E2E : `e2e/fixtures/llm/30-converse-*.json`.
- [X] T015 [US1] Front, specs Vitest écrites d'abord.
  - `FA/core/conversations/conversations.api.ts` (REST et `EventSource`) et `conversations.model.ts`.
  - `FA/features/conversation/conversation.page.ts` (+ `.spec.ts`), conçu avec la compétence `frontend-design` (research.md R7) :
    - fil `role="log"` ;
    - message de l'agent avec ses étapes (repliables après la fin), Markdown assaini, citations `[n]` en liens vers la source (ticket, page, ADR, `/sessions/{id}/versions/{n}`) et liste des sources ;
    - « Arrêter » (Échap), « Réessayer » sur un message incomplet, signal « échanges anciens non transmis » ;
    - champ avec `swMention` (Entrée envoie, Maj+Entrée va à la ligne, compteur de 8 000 caractères) ;
    - accord via `ConsentGate` ;
    - région `aria-live="polite"` séparée pour l'étape en cours et la réponse complète ;
    - projet courant affiché.
  - Route `''` vers la conversation dans `FA/app.routes.ts` (garde Copilot comme `/nouveau`).

**Checkpoint**: US1 utilisable seule, MVP.

---

## Phase 4: User Story 2 - De la conversation à une spécification (Priority: P1)

**Goal**: l'agent propose un besoin et des points établis ; une fois validés, le questionnaire
démarre avec ces points répondus.

**Independent Test**: après deux échanges, « Rédiger une spécification » ; corriger un point, en
retirer un ; valider. Le questionnaire montre les points répondus sans reposer leurs questions ; la
spécification cite un point ; les liens sont visibles des deux côtés.

- [X] T016 [US2] Tests d'abord dans `BT/application/conversation/DraftNeedTest.java`, puis `BM/application/conversation/DraftNeed.java`.
  - Prompt `BR/prompts/propose-need.txt` et schéma `NEED_DRAFT` (data-model.md : besoin, 10 points au plus, `theme` dans `QuestionTheme`, question de 500 caractères au plus, réponse de 5 000 au plus, `basis USER|SOURCE`, `sourceRef` parmi les sources citées de la conversation).
  - Un point dont la `sourceRef` est inconnue est retiré.
- [X] T017 [US2] Tests d'abord dans `BT/application/session/SessionUseCasesTest.java`, puis `StartSession.fromConversation(need, points, project, conversationId)` (`BM/application/session/StartSession.java`) et `BM/application/conversation/StartFromConversation.java`.
  - Points validés → questions `ANSWERED` du lot 0 (justification « Établi dans la conversation ») avant `NextBatch.ask`.
  - Documents joints de la conversation copiés dans `session_attachment` ; mentions @ ajoutées au besoin.
  - Lien `conversation_session`.
  - Échec : aucune session gardée.
  - Une question de même thème déjà couverte n'est pas reposée (rejeu de `propose-questions`).
- [X] T018 [US2] `POST /api/conversations/{cid}/need-draft` et `POST /api/conversations/{cid}/specifications` dans `ConversationResource`.
  - `GET /api/sessions/{id}` expose la conversation d'origine.
  - IT : brouillon, validation, 422 au-delà de 10 points, liens.
- [X] T019 [US2] Front, specs d'abord.
  - Bouton « Rédiger une spécification » et dialogue `FA/features/conversation/need-draft.dialog.ts` : besoin modifiable, points modifiables et retirables, « Démarrer ».
  - Le lien vers la spécification s'affiche dans la conversation.
  - Le questionnaire (`FA/features/questionnaire/questionnaire.page.ts`) montre « Depuis la conversation <nom> » avec un lien et les points répondus.

---

## Phase 5: User Story 3 - Recherche documentaire et incohérences (Priority: P2)

**Goal**: l'agent compare les sources d'un sujet et signale les écarts, en disant ce qu'il n'a pas
comparé.

**Independent Test**: deux sources contradictoires sur le délai de lettrage ; l'écart est signalé
avec les deux sources.

- [X] T020 [US3] Tests d'abord dans `ConverseTest`, puis `Converse`.
  - Documents désignés avec @ et documents joints de la conversation en tête des sources (règles 007/009 : ordre, 4 au plus, coupe à une section).
  - Sources non transmises faute de place listées dans la requête de réponse (`<non-comparees>`).
  - Spécifications implémentées marquées.
- [X] T021 [US3] Documents joints de conversation : `conversation_attachment`, `BM/application/conversation/ConversationAttachments.java` (réutilise `Attachments`, `AttachmentReader`), routes `GET/POST/DELETE /api/conversations/{cid}/attachments`, `MentionsInput.conversationId` (états `ATTACHMENT`). Tests et IT. Front : `sw-attachments` gagne l'entrée `conversationId` (`FA/shared/ui/attachments.component.ts` + spec).
- [X] T022 [P] [US3] Fixtures de rejeu d'une analyse d'incohérence (deux sources contradictoires, consigne de comparaison) pour les tests et l'E2E.

---

## Phase 6: User Story 4 - Retrouver et reprendre une conversation (Priority: P2)

**Goal**: les conversations du projet courant sont listées, reprises, renommées et supprimées.

**Independent Test**: deux conversations ; redémarrage ; reprise de l'une, suppression de l'autre ;
changement de projet.

- [X] T023 [US4] Tests d'abord dans `BT/application/conversation/ManageConversationsTest.java`, puis `ManageConversations.java` (liste par projet, la plus récente d'abord, renommer, supprimer, refusée pendant un tour) et ses routes `GET /api/projects/{id}/conversations`, `PATCH`/`DELETE /api/conversations/{cid}`. IT : suppression sans trace, les spécifications liées restent.
- [X] T024 [US4] Front, specs d'abord : `FA/features/conversation/conversation-list.component.ts`.
  - Panneau repliable : nouvelle, liste, renommer, supprimer avec confirmation.
  - Rechargement au changement de projet.
  - Adresse `/conversations/{cid}` pour rouvrir une conversation.

---

## Phase 7: User Story 5 - Nouveau menu (Priority: P2)

**Goal**: « Conversation » en premier ; « Recherche » et « Nouveau besoin » passent dans l'écran
des spécifications.

**Independent Test**: menu au clavier ; chaque écran est atteint ; les anciennes adresses mènent à
un écran utile.

- [X] T025 [US5] Specs d'abord.
  - Menu de `FA/app.ts` : Conversation, Spécifications, Tickets Jira, Projets, Journal, Configuration.
  - Redirection `/recherche` → `''` dans `FA/app.routes.ts`.
  - `FA/features/sessions/sessions.page.ts` s'intitule « Spécifications », avec le bouton « Nouvelle spécification » (`/nouveau`) et la recherche (composants de `features/search` réutilisés, `/api/search`).
  - Les liens internes vers l'ancienne recherche sont mis à jour.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [X] T026 [P] Test d'injection dans `BT/infrastructure/llm/PromptInjectionIT.java`.
  - Message, source et document joint hostiles : ils restent des données, les balises ne se referment pas.
  - `converse-plan` ne peut dépasser 5 recherches.
  - Une citation `[n]` inconnue est retirée.
  - Aucune donnée d'un autre projet.
- [X] T027 Évaluation conversationnelle `BT/eval/ConversationEval.java` et `backend/src/test/resources/eval/conversation/`.
  - Projet Aurora fictif construit par `TestDocuments`.
  - Questions à source attendue, questions hors connaissance, paires contradictoires.
  - Métriques `conversationCitation` (≥ 0,90), `conversationAbstention` (≥ 0,90), `inconsistencyRecall` (≥ 0,80) dans `baseline.json`.
  - Intégrées à `-Peval` et à `ci/check-eval.sh`.
- [X] T028 E2E `e2e/tests/us13-conversation.spec.ts` : accueil, question avec étapes et flux, citations, arrêt, réessai, incohérence, rédaction de spécification avec points corrigés, reprise après rechargement, menu, axe sans violation. Retirer l'accord Copilot à la fin.
- [X] T029 Revue d'ergonomie avec la compétence `web-design-guidelines` sur les écrans de conversation, de spécifications et le menu (FR-023, SC-008). Écarts corrigés ou justifiés dans `specs/010-agent-conversationnel/checklists/ux-review.md`.
- [X] T030 [P] Contrats `specs/002-projets-contexte-mcp/contracts/openapi.yaml` (conversations, tour, SSE, brouillon, `conversationId` des mentions), README (conversation, recherche, incohérences, rédaction, confidentialité), mise à jour de la feature en cours dans `CLAUDE.md`.
- [X] T031 Gates avant commit (CLAUDE.md) : `rm -f backend/target/jacoco.exec`, `./mvnw -B verify -DskipDependencyCheck`, `./mvnw -B package -DskipTests`, `npm --prefix e2e test`.
- [X] T032 Évaluation réelle `./mvnw -pl backend verify -Peval`, **avec l'accord du mainteneur** (prompts et `infrastructure/llm` modifiés), puis `bash ci/check-eval.sh` vert.
- [ ] T033 Essai manuel par l'utilisateur (quickstart.md, « Essai manuel ») ; résultat dans `specs/010-agent-conversationnel/checklists/validation.md`.

---

## Dependencies & Execution Order

- **Setup** (T001-T002), puis **Foundational** (T003-T008) : bloquent toutes les histoires.
- **US1** : T009, puis T010, T011, T012, T013 ; T014 en parallèle ; T015 après T013.
- **US2** : après US1 ; T016, puis T017, T018, T019.
- **US3** : après US1 ; T020 et T021 ; T022 en parallèle.
- **US4** : après T005 et T013 ; T023, puis T024. Indépendante de US2 et US3.
- **US5** : après T015 ; T025.
- **Polish** :
  - T026 après T012 ;
  - T027 après US1 et US3 ;
  - T028 après toutes les histoires ;
  - T029 après les écrans ;
  - T030 à tout moment ;
  - T031 avant chaque commit ;
  - T032 avant la PR ;
  - T033 en dernier.

## Parallel Example

```text
Foundational : T003 (domaine) | T004 (ports) | T006 (budget)
US1          : T014 (fixtures) pendant T012 (infrastructure LLM)
US3 / US4    : en parallèle une fois US1 finie
Polish       : T026 (injection) | T030 (contrats, README)
```

## Implementation Strategy

1. **MVP** : Setup, Foundational, puis US1. La conversation devient l'accueil et répond avec ses
   sources ; elle est démontrable seule.
2. US2 : la rédaction de spécification depuis la conversation.
3. US4 et US5 : reprise et menu, pour que le changement de point d'entrée soit complet.
4. US3 : documents de conversation et incohérences.
5. Polish : injection, évaluation (accord du mainteneur), E2E, revue d'ergonomie, documentation.

Commit par groupe logique après les gates. L'évaluation réelle est faite une seule fois, avant la
PR, une fois les prompts stabilisés.
