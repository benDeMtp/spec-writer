# Implementation Plan: Couche LLM sur les AI Services Quarkus, et renommage du package

**Branch**: `feature/018-couche-llm-quarkus` (depuis `main` ; la PR #16 sera mergée avant ou rebasée) | **Date**: 2026-11-16 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/014-couche-llm-quarkus/spec.md`

## Summary

Les opérations confiées au modèle passent par des AI Services de quarkus-langchain4j au lieu du
code écrit à la main.

- **Déclaration** : `@RegisterAiService`, consignes `@SystemMessage(fromResource)`, données
  `@UserMessage` déjà échappées, sorties typées, garde-fous avec reprise.
- **Agent** : il devient un AI Service en flux avec deux outils `@Tool`, la recherche et
  l'analyse de périmètre.
  - La boucle, le plafond (garde-fou d'entrée d'outil) et les événements d'outils sont ceux du
    framework.
  - `Converse` garde la logique métier du tour par l'interface `TurnTools`.
- **Adaptateur `ChatModel`** sur le SDK Copilot : il expose les outils du framework comme outils
  SDK **terminaux**. Il capture l'appel et le rend au framework, qui l'exécute dans l'application.
- **Session SDK** : limitée à ces outils, toute permission refusée, skills et consignes de dépôt
  désactivés, dossier de travail vide.
- **Rejeu** : il reste au niveau du `ChatModel`, étendu aux appels d'outils.
- **Évaluation réelle** : un `@QuarkusTest` qui mesure le code livré, avec un cas d'injection en
  plus.
- **Renommage** : le package `dev.specwriter` devient `fr.kawamind` (groupId compris). C'est le
  premier commit, mécanique et isolé.

Décisions détaillées dans [research.md](research.md) (R1 à R10).

## Technical Context

**Language/Version**: Java 25 (backend), TypeScript 5 / Angular 22 (front, une ligne de test).

**Primary Dependencies**:
- existantes : Quarkus 4.0.0.Beta2, quarkus-langchain4j-core 2.0.0.Beta1 (langchain4j 1.21.0),
  copilot-sdk-java 1.0.28-preview.0 ;
- **aucune nouvelle dépendance**. Les AI Services, les garde-fous, `TokenStream`,
  `InvocationParameters` et les écouteurs sont déjà dans les jars présents (vérifié).

**Storage**: SQLite, **aucune migration**.

**Testing**:
- **JUnit 5, adaptateurs** :
  - `CopilotSdkChatModelTest`, `CopilotSdkStreamingChatModelTest` (outils, historique, réglages de
    session, erreurs) sur un double de `CopilotSdkGateway` ;
  - `ReplayChatModelTest`, `ReplayStreamingChatModelTest` (`toolCalls`, `afterTools`, erreurs
    d'appariement) ;
  - garde-fous de sortie (une classe par DTO), `SearchCap`, `AgentTools`.
- **JUnit 5, application** : `Converse` sur un double de `ConversationLlm` qui appelle
  `TurnTools`. Les étapes, les sources numérotées, le plafond, l'arrêt et le périmètre gardent
  leurs tests actuels, adaptés à la nouvelle méthode.
- **`@QuarkusTest`** :
  - AI Services sur le rejeu : chaque opération rend le DTO attendu ;
  - conversation complète en SSE ;
  - `AgentToolsExtensionTest` (US3).
- **ArchUnit** : couches inchangées, plus « toutes les classes sous `fr.kawamind` » et « seule
  `infrastructure.llm` dépend de `com.github.copilot` et de `dev.langchain4j.service` ».
- **Playwright** : suite actuelle sur les fixtures réécrites. Aucun parcours nouveau.
- **Évaluation** (tag `eval`, `@QuarkusTest`, profil `eval`) : toutes les métriques, plus
  `injectionResistance`.
- **Essai** (tag `spike`) : `CopilotToolsSpikeIT`, plus le cas « appels parallèles capturés »
  (R2).

**Target Platform**: application locale (macOS, Linux, Windows), jar Quarkus et installeurs
jreleaser.

**Project Type**: application web locale (backend Quarkus et front Angular).

**Performance Goals**: le délai de première réponse visible de l'agent ne dépasse pas de plus
de 20 % l'actuel (SC-004).
- Une session SDK par appel au modèle, comme aujourd'hui.
- Le nombre d'appels d'un tour est inchangé, à une étape près : avant, un plan par recherche
  puis une réponse ; après, un appel par recherche puis la réponse.

**Constraints**:
- comportement visible inchangé ;
- aucun contenu dans le journal ;
- consigne en système, données balisées ;
- tests déterministes (rejeu) ;
- WCAG AA inchangé, aucun écran nouveau.

**Scale/Scope**:
- 16 consignes, dont 2 fusionnées en une ;
- 61 fixtures, dont 19 réécrites ;
- environ 730 fichiers Java déplacés.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principe | Vérification | Statut |
|---|---|---|
| I. Expérience guidée | Aucun écran ni parcours modifié. Messages d'erreur et étapes identiques (FR-001). | ✅ |
| II. Scénarios traçables | Les sorties de génération gardent leurs contrôles de structure et de traçabilité ; les garde-fous les appliquent avant d'accepter une sortie. | ✅ |
| III. Test-First | TDD sur les adaptateurs, garde-fous et outils avant migration. Chaque opération migrée a ses tests sur rejeu. Couverture ≥ 80 % sur le cœur, `jacoco.exec` supprimé avant chaque `verify`. Dépendance au modèle isolée derrière `ChatModel` et rejouée. | ✅ |
| IV. E2E et qualité des sorties | Suite Playwright complète sur les fixtures réécrites. Évaluation réelle à chaque étape de migration, recul bloquant (`check-eval.sh`). Golden set inchangé. | ✅ |
| V. Sécurité | Consigne séparée des données, résultats d'outils traités comme données (FR-009). Liste blanche d'outils, permissions refusées, skills et consignes de dépôt désactivés, dossier vide (R8). Cas d'injection mesuré (`injectionResistance`). Rien de journalisé. | ✅ |
| Sécurité : dépendances | Aucune nouvelle dépendance. Dependency-Check inchangé. | ✅ |
| Complexité justifiée | Pas de nouvelle couche. Ajout de `infrastructure/llm/ai` (interfaces déclaratives). Retrait de la boucle de relance, de `ConversationStream` et de la boucle plan/réponse. | ✅ |
| Workflow | Spec → clarify (2 questions tranchées) → plan → tasks → implement. Documentation : README (mise à jour MCP), CONTRIBUTING, CLAUDE.md. | ✅ |

**Après conception (Phase 1)** : les contrats ([llm-adapter](contracts/llm-adapter.md),
[agent-tools](contracts/agent-tools.md), [replay-format](contracts/replay-format.md)) ne créent
aucun écart. Le seul point à surveiller est la capture des appels parallèles (R2). Un test d'essai
la vérifie en début d'implémentation ; un repli est prévu (un appel par réponse). ✅

## Project Structure

### Documentation (this feature)

```text
specs/014-couche-llm-quarkus/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── llm-adapter.md
│   ├── agent-tools.md
│   └── replay-format.md
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

```text
backend/src/main/java/fr/kawamind/            # ex dev/specwriter (R10)
├── domain/port/
│   ├── ConversationLlm.java                  # converse(AgentRequest, TurnTools, deltas) ; Plan/Action retirés
│   └── TurnTools.java                        # nouveau
├── application/conversation/
│   └── Converse.java                         # implémente TurnTools ; plus de boucle plan/réponse
└── infrastructure/llm/
    ├── CopilotSdkChatModel.java              # outils terminaux, historique balisé, réglages de session
    ├── CopilotSdkStreamingChatModel.java     # nouveau (flux, outils, arrêt)
    ├── CopilotRuntime.java                   # session sécurisée (R8), flux réutilisé
    ├── ReplayChatModel.java                  # toolCalls, afterTools
    ├── ReplayStreamingChatModel.java         # nouveau
    ├── ChatModelProducer.java                # produit ChatModel et StreamingChatModel
    ├── LlmCallLog.java                       # nouveau, ChatModelListener (journal sans contenu)
    ├── LangChainLlmClient.java               # adaptateur mince du port LlmClient
    ├── LangChainConversationLlm.java         # adaptateur mince du port ConversationLlm
    ├── UserMessages.java                     # inchangé (échappement, balises)
    └── ai/
        ├── QuestionsAi.java  SpecificationAi.java  KnowledgeAi.java  ConversationAi.java
        ├── AgentTools.java  SearchCap.java    # @Tool, garde-fou d'entrée
        ├── guardrails/*.java                  # un OutputGuardrail par DTO
        └── dto/*.java                         # DTO déplacés depuis les deux adaptateurs
    # retirés : ConversationStream, CopilotConversationStream, ReplayConversationStream, boucle MAX_ATTEMPTS
backend/src/main/resources/prompts/
├── converse-agent.txt                        # fusion de converse-plan et converse-answer
└── skills/                                   # consignes réutilisables (FR-011), vide au départ
backend/src/test/java/fr/kawamind/
├── infrastructure/llm/…Test.java             # adaptateurs, rejeu, garde-fous, outils
├── eval/                                     # @QuarkusTest, EvalProfile, InjectionRuns
└── spike/CopilotToolsSpikeIT.java
e2e/fixtures/llm/
├── converse-agent-*.json                     # remplacent les fixtures converse-plan/answer
└── README.md                                 # procédure de réenregistrement
```

**Structure Decision** : structure web existante (backend Quarkus hexagonal et front Angular).
La feature ajoute un sous-paquet `infrastructure/llm/ai` et ne crée ni module ni couche.

## Complexity Tracking

Aucune violation de la constitution à justifier.
