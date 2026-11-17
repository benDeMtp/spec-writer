# Implementation Plan: Tâches d'une spécification, avancement suivi avec Jira et par les agents

**Branch**: `feature/019-taches-avancement` | **Date**: 2026-11-17 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/015-taches-avancement/spec.md`

## Summary

Une spécification liée à une story Jira se découpe sur demande en 3 à 8 tâches. Une nouvelle
opération LLM, `propose-tasks`, fait la proposition à partir des modules touchés (013) ou des cas
d'usage. L'utilisateur la corrige puis la valide, et les tâches deviennent des sous-tâches Jira.
Les agents de code suivent ces tâches par trois nouveaux outils de la passerelle MCP (`next_task`,
`start_task`, `complete_task`) et par `get_feature`, qui est étendu. Le choix de la tâche est
déterministe ; une tâche est réservée à la connexion qui l'a prise, identifiée par le processus de
la passerelle. Chaque transition est envoyée à Jira, avec un choix par catégorie de statut. Si Jira
est injoignable, la transition attend d'être envoyée. Le statut des sous-tâches est relu dans Jira
à la demande, et le statut de la spécification se calcule d'après les tâches. La rédaction gagne
un énoncé de story (rôle, capacité, bénéfice). Deux consignes réutilisables portent les méthodes
de rédaction et de découpage, et l'évaluation réelle les mesure.

## Technical Context

**Language/Version**: Java 25 (backend Quarkus 4), TypeScript (Angular 22)

**Primary Dependencies**: quarkus-langchain4j (AI Services, garde-fous, 014), client REST Quarkus
(Jira REST v2), Flyway, Hibernate ORM / JDBC natif (SQLite), Angular Material

**Storage**: SQLite, migration V26 (`spec_task`, `spec_task_dependency`, `spec_task_proposal`,
`session.task_tracking`, reconstruction de `agent_access_log`)

**Testing**: JUnit 5, `@QuarkusTest`, WireMock (IT Jira), ArchUnit, Vitest (front), Playwright
(E2E sur le jar avec Jira simulé et LLM rejoué), évaluation réelle `-Peval`

**Target Platform**: poste de l'utilisateur (macOS, Windows, Linux), outil local sur 127.0.0.1

**Project Type**: application web locale (backend + frontend) et passerelle MCP stdio

**Performance Goals**: la relecture des statuts (au plus 50 clés par appel Jira) ne retarde pas
l'ouverture du panneau de plus de 2 s avec un Jira qui répond ; `next_task` répond en moins de 3 s

**Constraints**: aucun contenu de spécification, de tâche ni de compte rendu dans les journaux ;
consignes en système seulement, données balisées ; jamais de suppression dans Jira ; passerelle
sans accès à la base ni à Jira

**Scale/Scope**: 8 tâches au plus par proposition, quelques connexions d'agent en même temps

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principe | Vérification | État |
|---|---|---|
| I. UX guidée | Proposition relue et corrigée avant tout envoi. Messages d'erreur métier (`NO_LINKED_TICKET`, `NO_TRANSITION` nommant le statut, Jira injoignable). Panneau accessible : réordonner par boutons et non par glisser seul, libellés, contrastes WCAG AA. | OK |
| II. Scénarios traçables | Chaque tâche cite sa base (module ou cas d'usage), vérifiée par le garde-fou. Un élément supposé de l'énoncé de story est marqué hypothèse. Le rendu Markdown reste déterministe. | OK |
| III. Test-first | TDD : `TaskProgress`, `TaskProposal` et `UserStory` sont des fonctions pures du domaine couvertes par des tests unitaires. Les cas d'usage sont testés avec des doubles de `IssueTracker`, `TaskLlm` et `AgentConnections`. L'horloge et les processus passent par des ports. | OK |
| IV. E2E et qualité | Parcours E2E US1 à US3 sur le jar. Golden set et évaluation réelle étendus (5 métriques, R13), sans recul des indicateurs existants. | OK |
| V. Sécurité | Les titres, résultats et comptes rendus sont des données, balisées pour le modèle et signalées comme telles dans la description des outils MCP. Bornes : titre 120, résultat 300, compte rendu 2 000. Aucun contenu n'est journalisé. Le jeton Jira ne sort pas du trousseau. Un test d'injection couvre un titre de tâche adressé à une IA. L'envoi vers Jira reste une action de l'utilisateur (validation) ou d'un agent qu'il a autorisé (accès des agents). | OK |
| Gates | verify, package, E2E, évaluation réelle, documentation utilisateur (README, panneau Tâches, outils MCP). | OK |

Aucune dérogation. Complexité ajoutée : trois tables, un AI Service, quatre appels Jira, trois
outils MCP. Chacun répond à une exigence ; la plus simple des alternatives est écartée dans
research.md (R1, R4, R5).

**Re-check après conception** : data-model.md, contracts/ et quickstart.md respectent ces points.
La règle FR-029 de la 002 (« la passerelle n'appelle pas Jira ») reste vraie et sera annotée
(R12).

## Project Structure

### Documentation (this feature)

```text
specs/015-taches-avancement/
├── plan.md
├── research.md          # R1–R13
├── data-model.md        # V26, domaine, transitions
├── quickstart.md
├── contracts/
│   ├── mcp-tools.md     # get_feature étendu, next_task, start_task, complete_task
│   ├── rest-api.md      # /api/sessions/{id}/tasks
│   └── llm-propose-tasks.md
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

```text
backend/src/main/java/fr/kawamind/
├── domain/
│   ├── task/                    # SpecTask, TaskStatus, TaskProgress, TaskProposal, StatusCategory
│   ├── specification/           # UserStory ; SpecificationContent.story, splitProposal ; MarkdownRenderer
│   ├── session/                 # ImplementationStatus.Actor.TASKS
│   ├── agent/                   # AgentOperation + NEXT_TASK, START_TASK, COMPLETE_TASK, RELEASE_TASK
│   └── port/                    # TaskRepository, TaskLlm, AgentConnections ; IssueTracker étendu
├── application/
│   ├── task/                    # ProposeTasks, EditProposal, ValidateTasks, ReadTasks (relecture + rejeu),
│   │                            # ReleaseTask, SyncTasks, ResumeTracking, TaskJira (transition par catégorie)
│   ├── agent/                   # NextTask, StartTask, CompleteTask ; GetFeature et AgentChangeStatus étendus
│   └── session/ChangeStatus     # STATUS_FOLLOWS_TASKS, suspension
├── infrastructure/
│   ├── jira/                    # JiraApi + transitions, comment, createmeta subtask ; JiraRestClient
│   ├── llm/ai/                  # TasksAi, TasksGuardrail ; LangChainTaskLlm
│   ├── persistence/task/        # SqliteTaskRepository
│   ├── platform/                # ProcessAgentConnections (ProcessHandle)
│   ├── web/                     # TaskResource, AgentResource étendu
│   └── config/UseCaseProducer   # câblage
└── mcp/                         # McpBridge (connexion), Tools (3 outils, codes d'erreur)

backend/src/main/resources/
├── db/migration/V26__spec_tasks.sql
└── prompts/
    ├── propose-tasks.txt
    ├── generate-specification.txt, edit-specification.txt, merge-conflict.txt, structure-imported-text.txt
    └── skills/decoupage-taches.md, skills/redaction-story.md

backend/src/test/
├── java/fr/kawamind/{domain/task, application/task, application/agent, infrastructure/jira, infrastructure/web, mcp, eval}
└── resources/eval/{tasks/cases.json, baseline.json, last-run.json}, eval/prompts/judge-task.txt

frontend/src/app/features/specification/
├── tasks-panel.component.ts     # avancement, liste, libérer, synchroniser, reprendre le suivi
├── task-proposal.component.ts   # relecture : renommer, fusionner, supprimer, réordonner, ajouter
└── specification.page.ts        # statut d'implémentation guidé par les tâches
frontend/src/app/core/tasks/     # tasks.api.ts, tasks.model.ts
frontend/src/app/features/settings/agent-access-card.component.ts  # nouvelles opérations du journal

e2e/
├── fixtures/jira/mappings/tasks.json        # createmeta sous-tâche, issue, transitions, comment
├── fixtures/llm/NN-propose-tasks-*.json, NN-generate-story.json
└── tests/us15-taches.spec.ts
```

**Structure Decision** : on garde la structure hexagonale existante. Les tâches forment un nouveau
paquet `task` dans le domaine et l'application. Les nouveaux cas d'usage d'écriture qui touchent
la session (statut) sont ajoutés à `CAS_D_USAGE_D_ECRITURE` (ArchUnit
`ecrituresGardeesParLeVerrou`) quand ils modifient la spécification. Les transitions de tâche ne
modifient pas les versions, et le statut se change par `Actor.TASKS` (R7).

## Complexity Tracking

Aucune violation de la constitution à justifier.
