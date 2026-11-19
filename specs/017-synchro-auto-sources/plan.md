# Implementation Plan: Tri de la vue Jira, synchronisation automatique des sources, notification des sources mises à jour

**Branch**: `feature/021-synchro-auto` | **Date**: 2026-11-18 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/017-synchro-auto-sources/spec.md`

## Summary

La vue « Tickets Jira » ne lit plus Jira à chaque page. Elle lit les tickets de l'index local du
projet, joints à l'état de synchronisation des liens avec les spécifications. Le tri (date, statut,
état de synchro) et la pagination se font sur la base, et la date de la dernière synchronisation de
Jira est affichée.

Chaque source indexable d'un projet reçoit un délai de synchronisation automatique, désactivé par
défaut, et garde son dernier bilan : date, nombres d'éléments nouveaux, modifiés et retirés, ou
erreur. C'est la notion de source de la réindexation sélective de la 005 (`IndexableSource` :
`jira`, `architecture`, identifiant d'une source de documentation). Une tâche planifiée de
l'extension `quarkus-scheduler` interroge les échéances toutes les 30 secondes et lance la synchronisation incrémentale de la
source échue par la file d'indexation existante (`Indexations`). Il n'y a jamais deux indexations
en même temps pour un projet.

Chaque fin de synchronisation, automatique ou manuelle, publie pour les sources changées un
événement « source mise à jour ». Le front le reçoit par un flux SSE de l'application, et un seul
onglet l'affiche en notification du navigateur, si l'utilisateur l'a activée dans Configuration.

## Technical Context

**Language/Version**: Java 25 (backend Quarkus 4), TypeScript (Angular 22)

**Primary Dependencies**: on reste dans le framework.
- Nouvelle extension `quarkus-scheduler` (BOM Quarkus) : `@Scheduled` configurable,
  `concurrentExecution = SKIP`, `@RunOnVirtualThread`, désactivée en `%test`.
- Événements CDI (`Event<SourceUpdated>`, observateur après validation de la transaction).
- Flux SSE de Quarkus REST (`Multi<OutboundSseEvent>`, `BroadcastProcessor` de Mutiny).
- Côté navigateur : API Notification et Web Locks.

Aucune autre dépendance.

**Storage**: SQLite, migration V28 :
- table `source_sync` : délai et dernier bilan de chaque source d'un projet ;
- réglage `notifications_enabled` dans les réglages de l'application ;
- index sur les tickets de `knowledge_item` pour le tri.

**Testing**: JUnit 5, `@QuarkusTest` (`@Isolated`), ArchUnit, Vitest, Playwright (E2E sur le jar,
avec une horloge et des délais raccourcis pour les tests), sans évaluation LLM (aucun prompt
touché).

**Target Platform**: poste de l'utilisateur, outil local sur 127.0.0.1.

**Project Type**: application web locale (backend + frontend).

**Performance Goals**: tri et pagination de 2 000 tickets en moins d'une seconde (SC-001) ;
une échéance démarrée moins d'une minute après son heure (SC-003) ; une notification moins de
10 s après la fin d'une synchronisation (SC-005).

**Constraints**:
- aucune requête vers Jira pour afficher ou trier la vue (SC-002) ;
- une seule indexation à la fois par projet, et les projets un par un ;
- notification sans contenu d'élément (FR-014) ;
- aucun accord de plus : les synchronisations automatiques lisent les mêmes sources, avec les mêmes
  accords (Jira, Copilot non concerné) ;
- l'horloge passe par `ClockPort` (ArchUnit).

**Scale/Scope**: quelques projets, une dizaine de sources par projet, 2 000 tickets indexés.

## Constitution Check

| Principe | Vérification | Statut |
|---|---|---|
| I. Expérience guidée | Délais en liste fermée et désactivés par défaut. Chaque source montre sa dernière synchronisation et son erreur éventuelle. Un refus de permission des notifications est expliqué avec la façon de l'accorder. Les en-têtes de tri sont accessibles (WCAG 2.1 AA, FR-006). | OK |
| II. Scénarios traçables | Aucun changement de génération ni de traçabilité. La vue Jira montre l'état de synchro tenu par l'outil, sans en inventer. | OK |
| III. Test-first | Domaine pur testé d'abord : `SyncSchedule`, `SourceSyncResult`, ordre de tri, échéances. Ordonnanceur testé avec une horloge simulée. Domaine et application couverts par des tests unitaires (piège JaCoCo). | OK |
| IV. Bout en bout et qualité | Nouvel E2E `us17-synchro` sur le jar : tri, délai court, notification avec permission accordée par Playwright. Golden set et évaluation inchangés (aucun prompt ni code LLM touché). | OK |
| V. Sécurité | Aucun jeton ni contenu dans les notifications ni au journal. Le flux SSE est protégé comme le reste de l'API (cookie de session, origine). Les synchronisations automatiques respectent les accords (Jira) et le proxy existants. | OK |
| Complexité | Une extension du framework (`quarkus-scheduler`) plutôt que du code d'ordonnancement maison. L'horloge métier reste dans `ClockPort` : le tick ne fait que réveiller `AutoSync`. Les événements CDI et le `Multi` SSE de Quarkus REST remplacent une diffusion écrite à la main. | OK |

Re-vérification après conception : inchangée.

## Project Structure

### Documentation (this feature)

```text
specs/017-synchro-auto-sources/
├── plan.md
├── research.md          # R1 à R8
├── data-model.md        # V28, domaine, bilan d'une synchronisation
├── quickstart.md
├── contracts/
│   ├── rest-api.md      # tickets triés, délais et bilans, notifications, flux d'événements
│   └── events.md        # événement source-updated
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

```text
backend/src/main/java/fr/kawamind/
├── domain/
│   ├── sync/SyncInterval.java, SyncSchedule.java, SourceSyncResult.java, SourceChange.java
│   ├── jira/ListedIssue.java, IssueSort.java           # ticket listé, tri
│   └── port/SourceSyncRepository.java, ListedIssues.java, AppEvents.java
├── application/
│   ├── knowledge/SynchronizeKnowledge.java  # décompte nouveaux / modifiés / retirés par source (R3)
│   ├── knowledge/Indexations.java           # bilan par source à la fin, file des échéances (R4)
│   ├── knowledge/AutoSync.java              # échéances : quelles sources, dans quel ordre
│   ├── knowledge/ManageSourceSync.java      # délais et bilans d'un projet
│   ├── jira/ListProjectIssues.java          # vue Jira depuis l'index (R1)
│   └── settings/ManageNotifications.java
├── infrastructure/
│   ├── scheduling/SourceSyncJob.java        # @Scheduled toutes les 30 s → AutoSync (R4)
│   ├── events/CdiAppEvents.java             # port AppEvents → événement CDI (R5)
│   ├── persistence/…                        # V28, SourceSyncSqliteRepository, ListedIssues en SQL
│   └── web/ProjectIssuesResource.java, SourceSyncResource.java, EventsResource.java (Multi<OutboundSseEvent>, R5)
└── resources/db/migration/V28__source_sync.sql

frontend/src/app/
├── core/events/app-events.service.ts        # flux SSE, onglet élu, Notification (R6, R7)
├── features/jira-issues/jira-issues.page.ts # colonnes Statut et Synchro, tri, fraîcheur
├── features/project-settings/…              # délai et dernière synchro par source
└── features/settings/notifications-card.component.ts

e2e/tests/us17-synchro.spec.ts
```

**Structure Decision**: même découpage hexagonal. La tâche `@Scheduled` est un adaptateur
d'infrastructure qui appelle le cas d'usage `AutoSync` ; le domaine et l'application ignorent le
framework. Les échéances, le tri et les bilans sont du domaine pur.

## Ordre de réalisation

1. **US1** (vue Jira depuis l'index) : indépendante, MVP.
2. **US2** (délais, bilans, ordonnanceur) : le bilan par source sert ensuite à US3.
3. **US3** (événements et notifications).
