# Implementation Plan: Budget de contexte selon le modèle

**Branch**: `feature/011-budget-selon-modele` (depuis `main`) | **Date**: 2026-11-07 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/008-budget-selon-modele/spec.md`

## Summary

La limite de prompt et le coefficient de chaque modèle, fournis par le SDK Copilot, sont lus à la
connexion et rangés avec la liste des modèles (migration V16). Un `KnowledgeBudget` du domaine
calcule, à partir de la limite du modèle choisi, la place de la connaissance transmise : les
plafonds d'aujourd'hui sont le minimum (`STANDARD`, aussi pour un modèle inconnu ou le défaut du
compte), tous grandissent dans la même proportion jusqu'à 200 000 caractères. `SelectKnowledge` et
`MentionQueries` l'utilisent ; l'empreinte des entrées suit le budget hors `STANDARD`. La
configuration affiche capacité et coût de chaque modèle ; l'état d'une mention coupée propose le
modèle le moins coûteux qui la transmettrait en entier.

## Technical Context

**Language/Version**: Java 25 (backend), TypeScript 5 / Angular 22 (front)

**Primary Dependencies**: Quarkus 4, Copilot SDK Java 1.0.28-preview.0 (existant) ; aucune nouvelle

**Storage**: SQLite ; migration `V16__model_capacities.sql` (colonne JSON sur `copilot_settings`)

**Testing**: JUnit 5 (`KnowledgeBudgetTest`, `SelectKnowledgeTest`, `MentionQueriesTest`), IT
(`SettingsPersistenceIT`, `SettingsResourceIT`), Vitest, Playwright E2E (configuration, axe) ;
`ci/check-eval.sh` doit rester vert sans relancer l'évaluation (research.md R3)

**Target Platform**: poste local (macOS, Windows, Linux)

**Project Type**: application web locale (backend + front)

**Performance Goals**: aucun appel à Copilot de plus par génération (capacités rangées)

**Constraints**: comportement identique à aujourd'hui pour un modèle inconnu (SC-002) ; prompts et
`infrastructure/llm` inchangés ; jamais de changement de modèle automatique

**Scale/Scope**: une vingtaine de modèles par compte ; total de connaissance de 35 000 à 200 000
caractères

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principe | Vérification | État |
|---|---|---|
| I. Expérience guidée | Capacité et coût lisibles avant de choisir ; « inconnu » plutôt qu'une erreur ; suggestion d'un modèle quand un document est coupé ; accessible (options lues, axe) | OK |
| II. Scénarios structurés, reproductibilité | Budget déterministe pour un modèle ; empreinte suivant le budget hors `STANDARD` ; `STANDARD` identique à aujourd'hui | OK |
| III. Test-First | `KnowledgeBudget` (domaine) et sélection (application) couverts par des tests unitaires d'abord | OK |
| IV. E2E et qualité des sorties | E2E de la configuration ; prompts inchangés, empreinte de l'évaluation inchangée, évaluation par défaut en `STANDARD` | OK |
| V. Sécurité et confidentialité | Aucune donnée nouvelle envoyée ; capacités lues du compte de l'utilisateur seulement ; valeurs aberrantes neutralisées | OK |
| Complexité | Une migration, un port et son adaptateur, un record du domaine | OK |

Re-vérification après la phase 1 : inchangée, aucune violation.

## Project Structure

### Documentation (this feature)

```text
specs/008-budget-selon-modele/
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
├── domain/knowledge/KnowledgeBudget.java                 # nouveau
├── domain/knowledge/ProjectKnowledge.java                # budget, empreinte
├── domain/settings/ModelCapacity.java                    # nouveau
├── domain/settings/CopilotSettings.java                  # capacities
├── domain/port/ModelCapacities.java                      # nouveau
├── domain/port/GitHubAuthenticator.java                  # ModelOption enrichi
├── application/settings/PollCopilotConnection.java       # range les capacités
├── application/knowledge/SelectKnowledge.java            # budget au lieu des constantes
├── application/knowledge/MentionQueries.java             # budget, betterModel
├── infrastructure/github/SdkCopilotAccountGateway.java   # lit limites et coefficient
├── infrastructure/github/FakeGitHubAuthenticator.java    # capacités simulées
├── infrastructure/settings/SettingsModelCapacities.java  # nouveau adaptateur
├── infrastructure/persistence/settings/…                 # colonne model_capacities
├── infrastructure/web/SettingsResource.java              # models, knowledgeChars
└── resources/db/migration/V16__model_capacities.sql

frontend/src/app/
├── core/settings/settings.model.ts
├── features/settings/copilot-card.component.ts
└── shared/ui/mention-status.component.ts
```

**Structure Decision**: structure web existante. Le calcul du budget est du domaine (pur) ; la
lecture des capacités reste dans `infrastructure/github`, leur exposition dans
`infrastructure/settings`, hors de `infrastructure/llm` pour ne pas changer l'empreinte de
l'évaluation.

## Complexity Tracking

Aucune violation à justifier.
