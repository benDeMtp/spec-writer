# Implementation Plan: Tickets fermés gardés en cache

**Branch**: `feature/006-tickets-fermes-cache` | **Date**: 2026-11-05 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/003-tickets-fermes-cache/spec.md`

## Summary

Un ticket Jira fermé (catégorie de statut `done`) ne change plus : l'index local le garde sans
le relire. Trois changements, tous dans la connaissance du projet de la 002 :

1. **Indexation complète** : quand un repère de synchronisation existe, la requête Jira ne
   ramène plus que les tickets ouverts et ceux modifiés depuis le repère ; les tickets absents de
   l'index (nouveaux, ou fermés jamais lus) et les tickets indexés ouverts que la requête n'a pas
   ramenés sont lus ensuite par clé. La liste légère des clés continue de retirer les tickets
   supprimés ou déplacés. Sans repère, tout est relu, comme aujourd'hui : c'est la « relecture
   unique » de FR-008 (research.md R1, R5).
2. **Statuts montrés** : les tickets proches et les sous-tâches fermés dans l'index sont montrés
   avec leur statut indexé, sans appel `issueStatus`, comme un statut à jour (R2).
3. **Bilan** : l'indexation renvoie le nombre de tickets relus, de tickets fermés gardés et de
   tickets retirés, affiché dans le panneau de connaissance et écrit dans le journal (R4).

La revectorisation depuis le texte indexé (FR-005) et l'absence de durée de vie (FR-007) sont
déjà vraies : elles sont couvertes par des tests, sans changement de code (R3).

## Technical Context

**Language/Version**: Java 25 (backend), TypeScript 5 / Angular 22 (front)

**Primary Dependencies**: Quarkus 4, client REST Jira de la 001/002 (`JiraRestClient`), SQLite
(index de la 002), modèle d'embedding local e5

**Storage**: SQLite, table `knowledge_item` de la 002 ; aucune migration (le statut et sa
catégorie sont déjà indexés)

**Testing**: JUnit 5 (unitaires sur `SynchronizeKnowledge`, `FindRelatedIssues`,
`ReportSubtasks` avec `IssueTracker` simulé et compteur d'appels), IT WireMock
(`JiraKnowledgeSyncIT`, `JiraRestClient`), jeu de référence de la 002 (SC-004), Vitest
(panneau), Playwright (E2E)

**Target Platform**: poste local (macOS, Windows, Linux), Jira Server / Data Center

**Project Type**: application web locale (backend + front)

**Performance Goals**: SC-001, projet à 80 % de tickets fermés : au plus 20 % des tickets relus,
indexation complète au moins deux fois plus rapide (mesurée par le nombre de tickets lus et la
durée de l'étape Jira, R6)

**Constraints**: aucune requête Jira pour un ticket fermé montré (SC-002) ; aucune donnée de
ticket dans le journal, seulement des nombres ; écritures vers Jira inchangées (FR-010)

**Scale/Scope**: projets jusqu'à quelques milliers de tickets (volume cible SC-004a de la 002)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principe | Vérification | État |
|---|---|---|
| I. Expérience guidée | Le bilan dit ce qui a été relu et gardé ; un ticket fermé n'affiche plus d'avertissement « Jira n'a pas pu être relu » qui ne le concerne pas | OK |
| II. Scénarios structurés | Pas de changement du générateur ni des scénarios | Sans objet |
| III. Test-First | Chaque règle (requête, lecture par clé, statut montré, bilan) commence par un test qui échoue ; couverture ≥ 80 % sur `application` par tests unitaires (piège JaCoCo du CLAUDE.md) | OK |
| IV. E2E et qualité des sorties | Un E2E : tickets proches fermés montrés Jira coupé ; jeu de référence de la 002 rejoué (SC-004). Prompts et code LLM inchangés : pas de nouvelle évaluation | OK |
| V. Sécurité et confidentialité | Clés de ticket validées (`[A-Z][A-Z0-9_]+-\d+`) avant la requête `key in (...)` ; JQL construite par l'adaptateur ; journal limité aux nombres | OK |
| Complexité | Aucune dépendance, aucune table, aucune couche nouvelle ; deux méthodes de port | OK |

Re-vérification après la phase 1 : inchangée, aucune violation.

## Project Structure

### Documentation (this feature)

```text
specs/003-tickets-fermes-cache/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── jira-port.md       # requêtes Jira de l'indexation
│   └── openapi-delta.md   # bilan dans l'état d'indexation
└── tasks.md               # /speckit-tasks
```

### Source Code (repository root)

```text
backend/src/main/java/dev/specwriter/
├── domain/port/IssueTracker.java            # + searchOpenOrUpdatedSince, issuesByKeys
├── domain/port/KnowledgeIndex.java          # + closedTickets
├── application/knowledge/
│   ├── SynchronizeKnowledge.java            # étape Jira : requête réduite, lecture par clé, bilan
│   ├── TicketSummary.java                   # nouveau : relus, fermés gardés, retirés
│   ├── IndexationProgress.java              # + tickets(TicketSummary)
│   ├── Indexations.java                     # Snapshot.tickets
│   ├── FindRelatedIssues.java               # statut indexé pour un ticket fermé
│   └── ReportSubtasks.java                  # idem pour les sous-tâches
└── infrastructure/
    ├── jira/JqlBuilder.java                 # openOrUpdatedSince, keys
    ├── jira/JiraRestClient.java             # validateQuery=warn pour la lecture par clé
    ├── knowledge/SqliteKnowledgeIndex.java  # closedTickets
    └── web/KnowledgeResource.java (DTO)     # tickets dans Indexation

frontend/src/app/
├── core/projects/projects.model.ts          # IndexationTickets
└── features/project-context/knowledge-panel.component.ts   # ligne de bilan

backend/src/test/java/dev/specwriter/...     # tests unitaires, IT WireMock, jeu de référence
e2e/tests/us8-project-context.spec.ts        # tickets proches fermés, Jira coupé
```

**Structure Decision**: structure web existante (backend hexagonal + front Angular) ; la feature
ne touche que la connaissance du projet de la 002.

## Complexity Tracking

Aucune violation à justifier.
