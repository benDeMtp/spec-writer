# Implementation Plan: Discuter d'une spécification avec l'agent

**Branch**: `feature/015-discussion-specification` (depuis `feature/014-agent-conversationnel`, à rebaser sur `main` après la fusion de la PR #12) | **Date**: 2026-11-10 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/011-discussion-specification/spec.md`

## Summary

« Faire évoluer avec l'IA », une consigne en un coup, devient « Discuter avec l'IA » : une
conversation (010) rattachée à la spécification, affichée à côté du document.

- **Rattachement** : la conversation est rattachée à la spécification par
  `specification_discussion` (V22). Il y a une discussion suivie par spécification, et « Nouvelle
  discussion » en crée une autre.
- **Contexte** : à chaque tour, la dernière version est découpée en sections et transmise en tête
  des sources, sur une part du budget prise sur la réserve. Les citations mènent à la section du
  document.
- **Proposition** : la nouvelle opération `revise-specification` réécrit la spécification d'après
  la discussion. Elle passe par la validation existante (structure, traçabilité, identifiants
  préservés), le diff et l'acceptation. Les messages de l'utilisateur deviennent des sources
  citables. La rédaction se suit par le flux SSE de la 010 (réflexion, chronomètre).
- **Version** : l'origine `DISCUSSION` est enregistrée par `version_discussion`, avec un lien vers
  la conversation.
- **Suppression** : la case « Supprimer aussi ses discussions » est cochée par défaut ; décochée,
  les discussions deviennent des conversations ordinaires marquées « spécification supprimée ».
- **Jira** : l'import ouvre la spécification existante quand le ticket est déjà lié.
- **Front** : le fil de la conversation devient un composant partagé `sw-conversation-thread`.
- **Retrait** : la consigne en un coup (`ProposeAiEdit`, `edit-specification`, `ai-edit.panel`) est
  retirée.
- **Évaluation** : nouveau volet « discussion » ; évaluation réelle avec l'accord du mainteneur.

## Technical Context

**Language/Version**: Java 25 (backend), TypeScript 5 / Angular 22 (front)

**Primary Dependencies**:
- Quarkus 4 REST (SSE) ;
- LangChain4j ;
- SDK Copilot `1.0.28-preview.0` ;
- java-diff-utils (diff existant) ;
- ngx-markdown ;
- aucune nouvelle dépendance.

**Storage**: SQLite. Migration `V22__specification_discussion.sql` : `specification_discussion`,
`version_discussion`, colonne `conversation_message.specification_version`.

**Testing**:
- JUnit 5 :
  - découpage en sections et ancres ;
  - sources de la discussion et budget ;
  - verrou ;
  - rattachement et nouvelle discussion ;
  - proposition (changement, rien à changer, identifiants perdus, traçabilité des messages) ;
  - suppression avec ou sans discussions ;
- IT : routes de la discussion, SSE de la proposition, acceptation liée, cascade, import Jira
  déjà lié, injection par la spécification ;
- Vitest : `sw-conversation-thread`, panneau de discussion, relecture, confirmation de
  suppression ;
- Playwright E2E : `us14-discussion` (quickstart 1 à 13), axe ;
- évaluation : volet discussion et `ci/check-eval.sh`.

**Target Platform**: poste local (macOS, Windows, Linux), navigateur récent

**Project Type**: application web locale (backend + front)

**Performance Goals**:
- premier signe d'activité en moins d'1 s (010) ;
- proposition d'une spécification moyenne en moins de 60 s avec un modèle courant ;
- discussion de 30 échanges rouverte sans perte (SC-006).

**Constraints**:
- spécification, messages et proposition jamais journalisés ;
- verrou des spécifications implémentées ;
- proposition en mémoire 30 min ;
- WCAG 2.1 AA ;
- aucune donnée d'un autre projet.

**Scale/Scope**:
- un utilisateur ;
- spécifications de quelques dizaines de milliers de caractères ;
- quelques discussions par spécification.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principe | Vérification | État |
|---|---|---|
| I. Expérience guidée | La discussion remplace une consigne à rédiger d'un coup : l'utilisateur précise par l'échange. Contexte et version visibles. Erreurs en vocabulaire métier. Chronomètre et réflexion. WCAG AA (FR-022). « Nouveau besoin » et le questionnaire sont inchangés. | OK |
| II. Scénarios traçables | La proposition passe le contrôle de structure et de traçabilité existant. Chaque ajout est rattaché à un message de la discussion (source `DISCUSSION`), à une source citée ou à une hypothèse marquée. Les use cases non concernés gardent leur identifiant. Diff relu avant création. | OK |
| III. Test-First | Découpage, sources, proposition, suppression et rattachement testés d'abord avec les doubles existants (scripts, rejeu, dépôts en mémoire, horloge). | OK |
| IV. E2E et qualité des sorties | Parcours E2E complet. Nouvelles métriques (citation, incohérences, complétude, préservation, structure de la proposition). Évaluation réelle avant fusion, avec accord. | OK (évaluation à lancer) |
| V. Sécurité | Spécification et messages confidentiels, non journalisés. Données entre balises échappées. Injection par la spécification testée. Verrou contrôlé à l'envoi, à la proposition et à l'acceptation. Accord Copilot. Isolation par projet. | OK |
| Complexité | Pas de reconstruction de table (lien `version_discussion`). Réutilisation de la boucle, du flux, de la validation, du diff et de l'acceptation. Un composant de fil partagé. | OK |

Re-vérification après la phase 1 : inchangée, aucune violation.

## Project Structure

### Documentation (this feature)

```text
specs/011-discussion-specification/
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
├── domain/conversation/        # SpecificationDiscussion, SpecificationSections (découpage, ancres), Thinking.Stage.REVISE, SourceKind.DISCUSSION
├── domain/specification/       # VersionOrigin.DISCUSSION
├── domain/port/                # ConversationLlm.revise, SpecificationDiscussionRepository, VersionRepository (lien discussion)
├── domain/knowledge/KnowledgeBudget.java              # part discussedSpecification
├── application/conversation/   # Converse (contexte de spécification), DiscussSpecification, ProposeRevision, DiscussionSources
├── application/specification/  # ProposalStore (conversationId), AcceptAiEdit (lien) ; ProposeAiEdit retiré
├── application/session/ManageSessions.java            # suppression avec ou sans discussions
├── application/jira/ImportJiraIssue.java              # ticket déjà lié → session existante
├── infrastructure/llm/         # LangChainConversationLlm.revise, LlmSchemas.REVISION, UserMessages (discussion, spécification discutée)
├── infrastructure/persistence/ # dépôts discussion et lien de version
├── infrastructure/web/         # SpecificationDiscussionResource, ProposalResource ; AiEditResource réduit à accept ; SessionResource (?discussions)
└── resources/prompts/revise-specification.txt ; converse-plan.txt, converse-answer.txt (bloc specification-discutee) ; edit-specification.txt retiré ; db/migration/V22__specification_discussion.sql

frontend/src/app/
├── features/conversation/conversation-thread.component.ts   # fil partagé, extrait de conversation.page
├── features/specification/discussion-panel.component.ts     # contexte, nouvelle discussion, proposer
├── features/specification/proposal-review.component.ts      # diff, appliquer, abandonner (discussion et conflit Jira)
├── features/specification/specification.page.ts             # disposition en deux colonnes, ancres des sections, suppression
├── features/conversation/conversation-list.component.ts     # mention « Spécification : <nom> »
├── features/search/item-detail.page.ts                      # « Discuter avec l'IA »
└── features/ai-edit/                                        # retiré

backend/src/test/resources/eval/discussion/ ; e2e/tests/us14-discussion.spec.ts ; e2e/fixtures/llm/4x-*.json
```

**Structure Decision**: structure web existante, architecture hexagonale.
- Le découpage de la spécification, la préservation des identifiants et les règles de discussion
  sont dans le domaine et l'application.
- Copilot, la base et le flux SSE restent derrière des ports en infrastructure.
- `DiscussSpecification` et `ProposeRevision` rejoignent la liste ArchUnit des cas d'usage
  d'écriture, qui vérifient le verrou.

## Complexity Tracking

Aucune violation à justifier.
