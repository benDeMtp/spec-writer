# Implementation Plan: Périmètre Jira de spec-writer

**Branch**: `feature/027-statut-fin-tache` | **Date**: 2026-11-26 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/024-perimetre-jira/spec.md`

## Summary

**Label de reconnaissance (US1, US3)**
- Une table `project_jira_label` (V36) garde le label de chaque projet ; pas de ligne, pas de label.
- Une table `ticket_label` (V36) garde les labels de chaque ticket : remplacés à la synchronisation,
  ajoutés quand spec-writer pose le label (R2).
- Le label est posé dans le corps de la création (story, sous-tâche) et ajouté à la reprise par
  `update.labels.add` (R3). Un refus ne bloque rien.
- `JiraScope` (application) dit si une feature et une tâche sont dans le périmètre ; la passerelle
  MCP s'en sert pour lister, lire et suivre (R5).

**Vue « Tickets Jira » (US2)**
- `GET /api/projects/{id}/jira-issues` gagne `labelled` et `done` ; le filtre est fait en SQL sur le
  premier niveau, le pager et le compte suivent (R6). Le front garde le choix en `sessionStorage`.

**Sous-tâches en référentiel (US4)**
- `SqliteKnowledgeIndex.search` écarte les tickets qui ont un lien `PARENT` (R7). Les sous-tâches
  gardent leur texte et leur vecteur, dont la rubrique « Sous-tâches » a besoin.

**Tickets supprimés (US5)**
- Chaque synchronisation de Jira liste les clés du projet et retire les tickets disparus, avec un
  garde-fou contre une liste vide (R8). Les tâches et le lien des spécifications concernées suivent
  (`ForgetDeletedTickets`) ; la spécification garde la trace du ticket perdu (`lost_jira_link`).

## Technical Context

**Language/Version**: Java 25 (Quarkus 4), TypeScript (Angular 22).

**Primary Dependencies**: aucune nouvelle.

**Storage**: SQLite, migration V36 (`project_jira_label`, `ticket_label`, `lost_jira_link`).

**Testing**:
- unitaires : `JiraLabel` (validation), `JiraScope`, `ForgetDeletedTickets`, `CreateJiraTicket`,
  `ImportJiraIssue`, `TaskDesk` (label à la création et à la reprise), `ListFeatures`,
  `GetFeature`, `AgentTasks`, `SynchronizeKnowledge` (retraits, garde-fou), `ManageJiraLabel` ;
- IT : `MigrationV36IT`, `SqliteTicketLabelsIT`, `SqliteListedIssuesIT` (filtres),
  `SqliteKnowledgeIndexIT` (sous-tâches hors recherche), `JiraTasksIT` / `JiraRestClientIT`
  (labels lus, posés, ajoutés ; WireMock, sous Maven), `ProjectIssuesResourceIT`,
  `JiraLabelResourceIT` ;
- Vitest : rubrique du label, filtres de la vue Jira, bandeau du lien perdu ;
- Playwright : `us22-hierarchie` (filtres), `us15-taches` (label et périmètre MCP), `us17-synchro`
  (suppression).

**Target Platform**: outil local, macOS, Linux, Windows.

**Project Type**: web application (backend + frontend).

**Performance Goals**: la vue Jira reste sous 300 ms pour 5 000 tickets (filtres en SQL, index
`ticket_label`). La synchronisation incrémentale gagne une liste paginée des clés (1 000 par page).

**Constraints**:
- rien n'est étiqueté ni filtré sans réglage ;
- aucun champ MCP retiré ;
- une coupure de Jira ou une liste vide ne retire jamais rien ;
- WCAG 2.1 AA ;
- empreinte d'évaluation inchangée (R7).

**Scale/Scope**: quelques milliers de tickets par projet, quelques labels par ticket.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principe | Vérification | État |
|---|---|---|
| I. Expérience guidée | Les filtres sont visibles et nommés (« Avec le label spec-writer », « Afficher les tickets terminés ») ; une liste vide propose de les retirer ; un label refusé ou un lien perdu est expliqué en vocabulaire métier ; saisie du label validée avec son motif. | OK |
| II. Scénarios traçables | Non concerné : aucune génération ne change de forme. | N/A |
| III. Test-First | Domaine (`JiraLabel`) et application (`JiraScope`, `ForgetDeletedTickets`, cas d'usage modifiés) couverts par des tests unitaires avant le code ; le reste par des IT et des E2E. | OK |
| IV. E2E et qualité | Filtres, périmètre MCP et suppression couverts par des E2E ; les sous-tâches hors recherche par des IT. Aucun prompt touché. | OK |
| V. Sécurité | Le label est borné (255, sans espace) ; il n'est jamais mis dans une JQL (filtre local) ; `Origin` vérifié sur PUT et DELETE ; le journal ne contient que des clés et des motifs. | OK |
| Complexité | Trois tables, deux ports, deux services d'application. L'alternative d'un champ `labels` sur `KnowledgeItem` est écartée en R2 (record utilisé partout, et l'index n'a pas les tickets tout juste créés). | OK |

Re-check après la phase 1 : inchangé, OK.

## Project Structure

### Documentation (this feature)

```text
specs/024-perimetre-jira/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── rest-api.md
│   └── mcp-tools.md
├── checklists/requirements.md
└── tasks.md            # /speckit-tasks
```

### Source Code (repository root)

```text
backend/src/main/resources/db/migration/V36__jira_scope.sql
backend/src/main/java/fr/kawamind/
├── domain/jira/JiraLabel.java                 # validation, labels d'un ticket
├── domain/jira/TrackedIssue.java              # labels
├── domain/port/{JiraLabelRepository,TicketLabels,LostJiraLinks}.java
├── domain/port/IssueTracker.java              # addLabel ; labels à la création
├── application/jira/{CreateJiraTicket,ImportJiraIssue}.java   # pose du label
├── application/jira/ListProjectIssues.java    # filtres
├── application/project/ManageJiraLabel.java
├── application/agent/JiraScope.java           # périmètre du MCP
├── application/agent/{ListFeatures,GetFeature,AgentChangeStatus}.java
├── application/task/{TaskDesk,AgentTasks,TaskSync}.java
├── application/knowledge/{SynchronizeKnowledge,ForgetDeletedTickets}.java
├── infrastructure/jira/{JiraApi,JiraRestClient}.java
├── infrastructure/knowledge/{SqliteListedIssues,SqliteKnowledgeIndex}.java
├── infrastructure/persistence/jira/{SqliteJiraLabelRepository,SqliteTicketLabels,SqliteLostJiraLinks}.java
├── infrastructure/web/{JiraLabelResource,ProjectIssuesResource,SessionResource}.java
└── infrastructure/config/UseCaseProducer.java
frontend/src/app/
├── core/projects/jira-label.api.ts
├── features/project-settings/jira-label.component.ts (+ spec)
├── features/jira-issues/ (filtres de la vue, + spec)
└── features/specification/ (bandeau « story supprimée dans Jira »)
e2e/tests/us22-hierarchie.spec.ts, us15-taches.spec.ts, us17-synchro.spec.ts
README.md
```

**Structure Decision**: application web existante, architecture hexagonale. Le label suit le modèle
des réglages par projet (023) ; les labels des tickets suivent celui des liens (`ticket_link`). La
décision « dans le périmètre » est un service d'application unique (`JiraScope`), appelé par les
cas d'usage des agents.

## Complexity Tracking

Aucune violation de la constitution à justifier.
