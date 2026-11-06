# Implementation Plan: Réindexation par source

**Branch**: `feature/008-reindexation-par-source` (depuis `feature/007-adr-du-projet`) | **Date**: 2026-11-06 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/005-reindexation-par-source/spec.md`

## Summary

Deux changements de l'indexation de la connaissance d'un projet :

1. **Étapes indépendantes** : une garde par source dans `SynchronizeKnowledge` (Jira, chaque
   source de documentation, architecture, vecteurs) transforme une erreur inattendue en erreur
   d'étape `UNEXPECTED` ; les étapes suivantes s'exécutent, les repères de la source en échec
   n'avancent pas, et l'indexation se termine « avec des erreurs » (research.md R1).
2. **Sources choisies** : une valeur `IndexationScope` portée de l'API (`sources` en mode `FULL`)
   jusqu'aux étapes ; seules les sources choisies sont relues, sans retrait de documentation
   partiel ni changement de la date « Synchronisée le » (R2 à R4). Le statut de la connaissance
   liste les sources réindexables ; le panneau propose « Réindexer des sources… » (R5, R6).

## Technical Context

**Language/Version**: Java 25 (backend), TypeScript 5 / Angular 22 (front)

**Primary Dependencies**: Quarkus 4, Angular Material ; aucune nouvelle dépendance

**Storage**: SQLite ; aucune migration

**Testing**: JUnit 5 (`SynchronizeKnowledgeTest`, `IndexationScopeTest`), IT
Quarkus (`KnowledgeResourceIT`), Vitest (panneau), Playwright (E2E sur le jeu fictif, axe-core)

**Target Platform**: poste local (macOS, Windows, Linux), navigateur récent

**Project Type**: application web locale (backend + front)

**Performance Goals**: réindexer une source de documentation ne coûte que sa lecture (aucune
requête Jira, SC-002)

**Constraints**: erreurs journalisées sans contenu ; une erreur hors étape (base, projet) reste
un échec ; annulation inchangée

**Scale/Scope**: quelques sources par projet

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principe | Vérification | État |
|---|---|---|
| I. Expérience guidée | Bilan qui nomme la source en échec et les sources réindexées ; choix au clavier, `fieldset`/`legend`, vérifié par axe en E2E | OK |
| II. Scénarios structurés, reproductibilité | Ordre des étapes inchangé ; sources choisies validées côté serveur | OK |
| III. Test-First | Tests d'abord pour chaque garde et pour la portée ; `domain` et `application` couverts par des tests unitaires | OK |
| IV. E2E et qualité des sorties | E2E de la réindexation d'une source ; aucun texte transmis à l'IA ne change : pas d'évaluation réelle | OK |
| V. Sécurité et confidentialité | Identifiants de sources validés contre le projet ; message d'erreur sans contenu (nom de la classe seul) | OK |
| Complexité | Une valeur, une garde, un champ d'API ; aucune dépendance ni table | OK |

Re-vérification après la phase 1 : inchangée, aucune violation.

## Project Structure

### Documentation (this feature)

```text
specs/005-reindexation-par-source/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── api.md
└── tasks.md            # /speckit-tasks
```

### Source Code (repository root)

```text
backend/src/main/java/dev/specwriter/
├── domain/knowledge/KnowledgeSource.java            # withJiraMark
├── application/knowledge/
│   ├── IndexationScope.java                         # nouveau : sources choisies
│   ├── SynchronizeKnowledge.java                    # gardes par source, portée
│   ├── Indexations.java                             # start(projectId, mode, scope), Snapshot.sources
│   └── KnowledgeQueries.java                        # Status.sources
├── infrastructure/web/KnowledgeResource.java        # IndexationInput.sources, IndexationView.sources
backend/src/test/java/dev/specwriter/...              # tests unitaires et IT
frontend/src/app/
├── core/projects/knowledge.api.ts, projects.model.ts
└── features/project-context/knowledge-panel.component.ts (+ spec)
e2e/tests/us8-project-context.spec.ts
```

**Structure Decision**: structure web existante (backend hexagonal + front Angular).

## Complexity Tracking

Aucune violation à justifier.
