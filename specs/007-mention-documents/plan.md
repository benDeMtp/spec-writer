# Implementation Plan: Mention d'un document avec @

**Branch**: `feature/010-mention-documents` (depuis `main`) | **Date**: 2026-11-07 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/007-mention-documents/spec.md`

## Summary

Un `@nom` écrit dans le besoin, une réponse ou l'instruction d'une modification désigne un
document de la connaissance du projet (document d'un dossier synchronisé, page Antora entière,
ADR) ; il est transmis à l'IA d'office, avant la recherche, marqué `designe="oui"`. La
reconnaissance est une fonction pure du domaine (plus long nom connu, research.md R1), appliquée
aux textes déjà enregistrés : la mention écrite à la main du cas réel fonctionne sans autre
saisie. Une directive Angular ajoute l'autocomplétion (Overlay CDK, combobox ARIA) ; un composant
montre l'état de chaque mention, estimé sur l'index, et lève une ambiguïté en réécrivant la
mention avec le chemin du document (aucune table, aucune migration).

## Technical Context

**Language/Version**: Java 25 (backend), TypeScript 5 / Angular 22 (front)

**Primary Dependencies**: Quarkus 4, Angular Material et CDK 22 (existants) ; aucune nouvelle

**Storage**: SQLite existant ; aucune migration (les mentions vivent dans les textes)

**Testing**: JUnit 5 (`MentionsTest`, `MentionQueriesTest`, `SelectKnowledgeTest`), IT
(`SqliteKnowledgeIndexIT`, `PromptInjectionIT`, `KnowledgeResourceIT`), Vitest (directive, état),
Playwright E2E (US8, axe), évaluation réelle (prompts modifiés)

**Target Platform**: poste local (macOS, Windows, Linux)

**Project Type**: application web locale (backend + front)

**Performance Goals**: suggestions en moins de 0,5 s pour 2 000 documents (SC-003)

**Constraints**: aucun appel externe ni lecture de fichier pendant la frappe (FR-016) ; texte de
l'utilisateur inchangé (FR-006) ; contenu désigné toujours traité comme donnée (FR-010)

**Scale/Scope**: catalogue de l'ordre de 2 000 documents par projet ; 4 documents désignés au plus
par génération

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principe | Vérification | État |
|---|---|---|
| I. Expérience guidée | État de chaque mention avant la génération (aucun échec silencieux, SC-004) ; levée d'ambiguïté en un clic ; combobox ARIA 1.2, clavier complet, résultats annoncés ; axe en E2E | OK |
| II. Scénarios structurés, reproductibilité | Reconnaissance déterministe (plus long nom, ordre besoin → réponses → instruction) ; deux préparations identiques ; un document désigné citable comme source | OK |
| III. Test-First | `Mentions` (domaine) et `MentionQueries` (application) couverts par des tests unitaires d'abord ; directive testée en Vitest | OK |
| IV. E2E et qualité des sorties | E2E P11 ; prompts modifiés → évaluation réelle avec l'accord du mainteneur, scores ≥ `baseline.json` | OK |
| V. Sécurité et confidentialité | Catalogue limité au projet ; aucun fichier lu pendant la frappe ; `designe` n'ouvre aucune autorité au contenu (consigne des prompts, test d'injection) ; entrées bornées (422) | OK |
| Complexité | Aucune dépendance, aucune migration ; un port étendu, un cas d'usage, une directive | OK |

Re-vérification après la phase 1 : inchangée, aucune violation.

## Project Structure

### Documentation (this feature)

```text
specs/007-mention-documents/
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
├── domain/knowledge/Mentionable.java, Mention.java, Mentions.java      # nouveaux
├── domain/knowledge/ProjectKnowledge.java                              # Entry.designated
├── domain/port/KnowledgeIndex.java                                     # mentionables()
├── application/knowledge/MentionQueries.java                           # nouveau
├── application/knowledge/SelectKnowledge.java                          # étape « mentionnés »
├── infrastructure/knowledge/SqliteKnowledgeIndex.java                  # mentionables()
├── infrastructure/llm/UserMessages.java                                # designe="oui"
├── infrastructure/web/KnowledgeResource.java                           # mentions, resolve
├── infrastructure/config/UseCaseProducer.java
└── resources/prompts/*.txt                                             # 6 prompts
backend/src/test/java/dev/specwriter/…                                  # tests ci-dessus

frontend/src/app/
├── shared/ui/mention.directive.ts, mention-status.component.ts         # nouveaux
├── core/projects/knowledge.api.ts                                      # suggest, resolve
├── features/need/need.page.ts
├── features/questionnaire/question-card.component.ts
└── features/ai-edit/ai-edit.panel.ts

e2e/tests/us8-project-context.spec.ts                                   # P11
```

**Structure Decision**: structure web existante. La grammaire des mentions est du domaine (pure,
testable) ; la sélection reste dans `SelectKnowledge` ; l'autocomplétion est une directive
partagée posée sur les trois zones de texte existantes.

## Complexity Tracking

Aucune violation à justifier.
