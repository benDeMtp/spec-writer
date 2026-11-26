# Implementation Plan: Statut Jira d'une tâche implémentée, réglé par projet

**Branch**: `feature/027-statut-fin-tache` | **Date**: 2026-11-26 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/023-statut-fin-tache/spec.md`

## Summary

**Réglage par projet**
- Une table `project_task_workflow` (V35) garde, par projet, le statut d'arrivée d'une tâche
  implémentée et les statuts finis.
- Les statuts proposés sont lus dans l'index (R2), sans appel à Jira.
- Le réglage se fait dans une rubrique « Suivi des tâches dans Jira » des réglages du projet.

**Domaine**
- `TaskWorkflow` dit si un statut est fini et donne la cible d'une fin.
- `Target` désigne une catégorie ou un statut nommé.
- `StatusName` compare les noms de statuts.

**Application**
- `TaskJira.moveTo` vise un statut nommé : transition directe, sans annulation.
- `TaskSync` compte un statut fini comme terminé, et rejoue « à synchroniser » vers le statut
  d'arrivée.
- `StoryFollow` fait suivre la story :
  - au démarrage, une story « à faire » passe en cours ;
  - à la dernière tâche, ou pour une feature sans tâches déclarée implémentée, la story passe au
    statut d'arrivée.
- Les réponses MCP gagnent `jiraStatus` et `story`.

**Sans réglage**, le comportement de la 015 est gardé pour la fin d'une tâche, et la story n'est
jamais fermée.

## Technical Context

**Language/Version**: Java 25 (Quarkus 4), TypeScript (Angular 22).

**Primary Dependencies**: aucune nouvelle.

**Storage**: SQLite, migration V35 (`project_task_workflow`).

**Testing**:
- unitaires :
  - `TaskWorkflowTest`, `StatusNameTest` ;
  - `TaskJiraTest` (statut nommé, annulation écartée, `ALREADY`, `NO_TRANSITION`) ;
  - `TaskSync` (statut fini, rejeu) ;
  - `AgentTasksTest` (fin, story) ;
  - `StoryFollowTest`, `AgentFeaturesTest` (feature sans tâches) ;
  - `ManageTaskWorkflowTest` ;
- IT : `MigrationV35IT`, `SqliteTaskWorkflowRepositoryIT` (dépôt et statuts connus),
  `TaskWorkflowResourceIT`, `AgentTaskResourceIT` (WireMock, sous Maven) ;
- Vitest : composant de la rubrique ;
- Playwright : `us15-taches` (workflow à la AURORA) et `us8-project-settings` (rubrique).

**Target Platform**: outil local, macOS, Linux, Windows.

**Project Type**: web application (backend + frontend).

**Performance Goals**: `complete_task` reste sous 3 s, relecture Jira comprise. Il y a au plus
deux appels Jira de plus pour la story : un statut et une liste de transitions.

**Constraints**:
- jamais une annulation, jamais une chaîne de transitions ;
- aucun champ MCP retiré ;
- WCAG 2.1 AA ;
- empreinte d'évaluation inchangée.

**Scale/Scope**: une dizaine de statuts par projet ; quelques tâches par feature.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principe | Vérification | État |
|---|---|---|
| I. Expérience guidée | La rubrique propose des statuts connus, sans saisie libre. Le refus d'un statut « à faire » est expliqué. `NO_TRANSITION` et l'échec de la story disent quoi faire dans Jira, en vocabulaire métier. Le formulaire est accessible au clavier et étiqueté. | OK |
| II. Scénarios traçables | Non concerné : aucune génération. | N/A |
| III. Test-First | Domaine (`TaskWorkflow`, `StatusName`, `Target`) et application (`TaskJira`, `TaskSync`, `StoryFollow`, `ManageTaskWorkflow`) couverts par des tests unitaires avant le code ; le reste par des IT et des E2E. | OK |
| IV. E2E et qualité | `us15-taches` couvre la fin vers « Réalisée », la relecture et la story ; `us8-project-settings` la rubrique. Aucun prompt touché. | OK |
| V. Sécurité | Les noms de statuts sont bornés (100 caractères, 50 statuts) ; le JSON est désérialisé en liste typée ; `Origin` est vérifié sur PUT et DELETE ; le journal ne contient que des clés et des motifs. | OK |
| Complexité | Une table, un port, un cas d'usage, une ressource, un service `StoryFollow`. L'alternative plus simple (colonnes sur `project`) est écartée en R1. Le rejeu par tâche vers un statut stocké est écarté en R5. | OK |

Re-check après la phase 1 : inchangé, OK.

## Project Structure

### Documentation (this feature)

```text
specs/023-statut-fin-tache/
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
backend/src/main/resources/db/migration/V35__task_workflow.sql
backend/src/main/java/fr/kawamind/
├── domain/task/{TaskWorkflow,StatusName,Target,KnownStatus}.java
├── domain/port/TaskWorkflowRepository.java
├── application/task/ManageTaskWorkflow.java
├── application/task/TaskJira.java          # moveTo(Target)
├── application/task/TaskSync.java          # statuts finis, rejeu vers l'arrivée
├── application/task/StoryFollow.java       # la story suit ses tâches
├── application/task/AgentTasks.java        # jiraStatus, story ; fin et prise
├── application/agent/AgentChangeStatus.java  # feature sans tâches → story
├── infrastructure/persistence/task/SqliteTaskWorkflowRepository.java
├── infrastructure/web/TaskWorkflowResource.java
├── infrastructure/config/UseCaseProducer.java
└── mcp/Tools.java                          # descriptions de complete_task et start_task
frontend/src/app/
├── core/projects/task-workflow.api.ts
└── features/project-settings/task-workflow.component.ts (+ spec), intégré à project-settings.page.ts
e2e/tests/us15-taches.spec.ts, us8-project-settings.spec.ts
README.md
```

**Structure Decision**: application web existante, architecture hexagonale. Le réglage suit le
modèle des réglages par projet (fiabilité, synchro). La décision « fini » est dans le domaine
(`TaskWorkflow`), et les transitions restent dans `TaskJira`.

## Complexity Tracking

Aucune violation de la constitution à justifier.
