# Implementation Plan: Agent conversationnel, point d'entrée principal

**Branch**: `feature/014-agent-conversationnel` (depuis `main`) | **Date**: 2026-11-09 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/010-agent-conversationnel/spec.md`

## Summary

L'outil s'ouvre sur une conversation avec un agent qui connaît le projet courant.

- **Recherche** : à chaque message, l'agent planifie jusqu'à 5 recherches dans la connaissance du
  projet. Elles portent sur l'index existant et sur la dernière version des spécifications du
  projet. Chaque recherche est une étape visible.
- **Réponse** : l'agent rédige ensuite une réponse en flux, qui cite ses sources par numéro. Les
  numéros inconnus sont retirés.
- **Usages** : la même boucle sert à la recherche documentaire et à l'analyse d'incohérences.
- **Vers la spécification** : sur demande, l'agent propose un besoin et des points établis, que
  l'utilisateur relit. Le questionnaire actuel démarre avec ces points comme réponses déjà données.
- **Conservation** : les conversations sont gardées par projet (V18).
- **Navigation** : le menu commence par « Conversation ». « Recherche » et « Nouveau besoin »
  passent dans l'écran des spécifications.
- **Évaluation** : nouvelles opérations IA, donc évaluation réelle avec accord du mainteneur, et
  nouveau volet d'évaluation conversationnelle.

## Technical Context

**Language/Version**: Java 25 (backend), TypeScript 5 / Angular 22 (front)

**Primary Dependencies**:
- Quarkus 4 REST (SSE natif) ;
- LangChain4j (`ChatModel`, `StreamingChatModel`) ;
- SDK Copilot `1.0.28-preview.0` (streaming par événements) ;
- ngx-markdown (rendu assaini) ;
- aucune nouvelle dépendance.

**Storage**: SQLite. Migration `V18__conversation.sql`. Recherche des spécifications sur la table
FTS5 `spec_search` existante.

**Testing**:
- JUnit 5 : boucle de l'agent avec `ScriptedChatModel`, citations, plafond, budget, brouillon,
  démarrage depuis les points ;
- IT : SSE, reprise, arrêt, cascades, isolation des projets, injection ;
- Vitest : page de conversation, menu, écran des spécifications ;
- Playwright E2E : parcours de la conversation à la spécification, axe ;
- évaluation conversationnelle et `ci/check-eval.sh`.

**Target Platform**: poste local (macOS, Windows, Linux), navigateur récent

**Project Type**: application web locale (backend + front)

**Performance Goals**:
- étape ou premier signe d'activité en moins d'1 s ;
- réponse à une seule recherche en moins de 20 s (SC-006) ;
- conversation de 50 échanges rouverte sans perte (SC-007).

**Constraints**:
- messages jamais journalisés ;
- 5 recherches au plus par réponse ;
- message de 8 000 caractères au plus ;
- aucune donnée d'un autre projet ;
- WCAG 2.1 AA.

**Scale/Scope**:
- un utilisateur ;
- quelques centaines de conversations par projet ;
- 10 points au plus par brouillon.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principe | Vérification | État |
|---|---|---|
| I. Expérience guidée | Le questionnaire reste la voie de rédaction. Les points établis sont des réponses déjà données : aucune question connue n'est reposée. L'avancement est visible (étapes, flux). Les erreurs sont en vocabulaire métier. WCAG AA et revue d'ergonomie (FR-022, FR-023). | OK |
| II. Scénarios traçables | Un point établi devient une réponse citable comme source `ANSWER`. La conversation brute n'entre pas dans la génération. Génération et golden set inchangés. | OK |
| III. Test-First | Boucle, citations, plafond, budget et brouillon testés d'abord avec des doubles déterministes (scripts, rejeu, horloge). | OK |
| IV. E2E et qualité des sorties | Parcours E2E conversation → spécification. Nouvelles métriques d'évaluation (citation, abstention, incohérences). Évaluation réelle avant fusion, avec l'accord du mainteneur. | OK (évaluation à lancer) |
| V. Sécurité | Messages confidentiels, non journalisés. Données entre balises, échappées. Markdown assaini. Citations limitées aux sources du tour. Isolation par projet. Tests d'injection étendus. Accord Copilot avant chaque tour. Entrées bornées. | OK |
| Complexité | Une boucle applicative au lieu des outils du SDK. SSE au lieu de WebSocket. Pas de nouvelle indexation. | OK |

Re-vérification après la phase 1 : inchangée, aucune violation.

## Project Structure

### Documentation (this feature)

```text
specs/010-agent-conversationnel/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/api.md
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

```text
backend/src/main/java/dev/specwriter/
├── domain/conversation/        # Conversation, ConversationMessage, SearchStep, CitedSource, NeedDraft, EstablishedPoint, Citations
├── domain/port/                # ConversationLlm, ConversationRepository ; LocalSearch.byRelevance
├── domain/knowledge/KnowledgeBudget.java              # parts conversationSources, conversationHistory
├── application/conversation/   # Converse (boucle), ConversationSearch, TurnEvents, ManageConversations, DraftNeed, StartFromConversation
├── application/session/StartSession.java              # questionnaire initial avec points répondus
├── infrastructure/llm/         # LangChainConversationLlm, CopilotSdkStreamingChatModel, ReplayStreamingChatModel, UserMessages (échange, sources)
├── infrastructure/persistence/conversation/           # dépôt SQLite
├── infrastructure/persistence/search/FtsLocalSearch.java  # byRelevance
├── infrastructure/web/ConversationResource.java, ConversationTurnResource.java (SSE)
└── resources/prompts/converse-plan.txt, converse-answer.txt, propose-need.txt ; db/migration/V18__conversation.sql

frontend/src/app/
├── features/conversation/      # conversation.page, conversation-list, message, need-draft dialog
├── core/conversations/         # conversations.api (+ EventSource), conversations.model
├── shared/ui/attachments.component.ts                 # mode conversation
├── app.ts, app.routes.ts       # menu, accueil, redirections
└── features/sessions/sessions.page.ts                 # « Spécifications », nouvelle spécification, recherche

backend/src/test/resources/eval/conversation/ ; e2e/tests/us13-conversation.spec.ts ; e2e/fixtures/llm/3x-converse-*.json
```

**Structure Decision**: structure web existante, architecture hexagonale. La boucle de l'agent et
ses règles (plafond, citations, budget) sont dans le domaine et l'application. Copilot (flux), la
base et SSE restent derrière des ports en infrastructure.

## Complexity Tracking

Aucune violation à justifier.
