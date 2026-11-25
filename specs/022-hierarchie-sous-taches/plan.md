# Implementation Plan: Hiérarchie des tâches et sous-tâches Jira

**Branch**: `feature/026-hierarchie-sous-taches` | **Date**: 2026-11-25 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/022-hierarchie-sous-taches/spec.md`

## Summary

**Vue « Tickets Jira » (017)**
- La requête de la page ne garde que les tickets sans parent indexé, d'après le lien `PARENT` de
  `ticket_link`. Le tri, la pagination et le total portent sur eux seuls.
- Une seconde requête lit les sous-tâches des tickets de la page ; elles arrivent avec la page.
- Le front ajoute un bouton de dépliage par parent et des lignes de sous-tâches en retrait.

**Passerelle MCP (015)**
- Chaque tâche porte `parentKey` (le ticket lié à la feature) et `issueType`, relu avec le statut
  et gardé en V34.
- `get_feature` ajoute `jiraTicket` (`{key, issueType, parentKey}`), lu dans l'index.
- Les instructions du serveur et un champ `branch` dans `next_task` et `start_task` donnent la
  consigne de branche avec la clé du parent.

## Technical Context

**Language/Version**: Java 25 (Quarkus 4), TypeScript (Angular 22).

**Primary Dependencies**: aucune nouvelle. Hibernate en requêtes natives sur SQLite, Angular
Material (`mat-icon-button` déjà utilisé).

**Storage**: SQLite. Migration V34 : `spec_task.jira_issue_type`.

**Testing**:
- JUnit 5 et AssertJ : `AgentTasksTest`, `TaskSync`, `GetFeature`, `ListProjectIssues` ;
- `@QuarkusTest` : `SqliteListedIssuesIT`, `ProjectIssuesResourceIT`, `TaskPersistenceIT`,
  `MigrationV34IT` ;
- test de la passerelle (`Tools` et `McpBridge`) ;
- Vitest : `jira-issues.page.spec.ts` ;
- Playwright : `us10-jira-paging` et `us17-synchro` (vue Jira), `us15-taches` et `us11-agents` (MCP).

**Target Platform**: outil local (jar Quarkus, navigateur), macOS, Linux, Windows.

**Project Type**: web application (backend + frontend).

**Performance Goals**: ouverture de la vue et changement de page sous la seconde sur 3 000
tickets (SC-003).

**Constraints**:
- aucun appel à Jira pour afficher la vue (FR-008) ;
- aucun champ MCP retiré (FR-014) ;
- WCAG 2.1 AA ;
- empreinte d'évaluation inchangée : aucun fichier de `infrastructure/llm` ni de `prompts`.

**Scale/Scope**: ~2 600 tickets, 325 sous-tâches sur l'index réel ; deux niveaux.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principe | Vérification | État |
|---|---|---|
| I. Expérience guidée | Dépliage au clavier, `aria-expanded`, libellé avec le nombre et la clé (R7). Le message d'une sous-tâche orpheline est en vocabulaire métier. | OK |
| II. Scénarios traçables | Non concerné : aucune génération de spécification. | N/A |
| III. Test-First | Chaque changement commence par un test : requête SQLite (IT), cas d'usage (unitaires), passerelle, page (Vitest), E2E. Couverture de `domain` et `application` par des tests unitaires (piège JaCoCo du CLAUDE.md). | OK |
| IV. E2E et qualité | `us10-jira-paging` couvre le dépliage et la pagination, `us15-taches` le parent et la consigne MCP. Aucun prompt touché : golden set et évaluation inchangés, empreinte identique. | OK |
| V. Sécurité | Aucune donnée nouvelle envoyée à un tiers. Le libellé du type, lu dans Jira, est borné à 100 caractères (V34). Le tri reste construit depuis un enum. Les clés de la seconde requête sont des paramètres liés. | OK |
| Complexité | Aucune dépendance ni couche nouvelle. Une colonne, deux champs de réponse, une requête de plus. L'alternative plus simple (regroupement côté front de la page plate) est écartée en R2. | OK |

Re-check après la phase 1 (data-model, contrats) : inchangé, OK.

## Project Structure

### Documentation (this feature)

```text
specs/022-hierarchie-sous-taches/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── rest-api.md
│   └── mcp-tools.md
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

```text
backend/src/main/java/fr/kawamind/
├── domain/jira/ListedIssue.java                      # parentKey, subtasks
├── domain/port/ListedIssues.java                     # page() rend les sous-tâches
├── domain/port/TaskRepository.java                   # issueTypes(), type(...)
├── application/jira/ListProjectIssues.java           # inchangé ou presque (total du premier niveau)
├── application/task/TaskSync.java                    # relit et garde le type
├── application/task/AgentTasks.java                  # parentKey, issueType, branch
├── application/agent/GetFeature.java                 # jiraTicket
├── infrastructure/knowledge/SqliteListedIssues.java  # filtre premier niveau, seconde requête
├── infrastructure/persistence/task/SqliteTaskRepository.java
├── infrastructure/web/ProjectIssuesResource.java     # sérialisation (si DTO dédié)
└── mcp/{McpBridge,Tools}.java                        # instructions, descriptions d'outils
backend/src/main/resources/db/migration/V34__task_issue_type.sql

frontend/src/app/
├── core/jira/jira.model.ts                           # parentKey, subtasks
└── features/jira-issues/jira-issues.page.ts          # dépliage, lignes de sous-tâches

e2e/fixtures/jira/…                                   # story, sous-tâches, sous-tâche orpheline
e2e/tests/us10-jira-paging.spec.ts, us17-synchro.spec.ts, us15-taches.spec.ts, us11-agents.spec.ts
README.md                                             # vue Jira, consigne de branche de l'agent
```

**Structure Decision**: application web existante (backend hexagonal + front Angular). Aucun
module nouveau. Le parent d'un ticket indexé est lu par l'adaptateur SQLite, déjà propriétaire
de la requête de la vue. Le parent d'une tâche vient du ticket lié, déjà connu de `AgentTasks`
par `TaskDesk`.

## Complexity Tracking

Aucune violation de la constitution à justifier.
