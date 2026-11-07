---
description: "Tâches de la feature 008 : budget de contexte selon le modèle"
---

# Tasks: Budget de contexte selon le modèle

**Input**: `specs/008-budget-selon-modele/` (plan.md, spec.md, research.md, data-model.md,
contracts/api.md, quickstart.md)

**Tests**: obligatoires (constitution III, TDD) : chaque test est écrit et vu échouer avant le
code. `domain` et `application` sont couverts par des tests unitaires (JaCoCo ne compte pas les
`@QuarkusTest`).

**Organization**: US1 (la connaissance suit la capacité du modèle, MVP), US2 (capacité et coût
dans la configuration), US3 (suggestion d'un modèle pour un document coupé).

## Format: `[ID] [P?] [Story] Description`

- **[P]** : parallélisable (fichiers différents, aucune dépendance sur une tâche non finie)
- Chemins : `backend/src/main/java/dev/specwriter/` noté `BM/`, `backend/src/test/java/dev/specwriter/`
  noté `BT/`, `frontend/src/app/` noté `FA/`

---

## Phase 1: Setup

- [X] T001 Migration `backend/src/main/resources/db/migration/V16__model_capacities.sql` : colonne `model_capacities TEXT NOT NULL DEFAULT '[]'` sur `copilot_settings` (JSON, liste de capacités)

---

## Phase 2: Foundational (bloque toutes les user stories)

- [X] T002 [P] Tests d'abord `BT/domain/settings/ModelCapacityTest.java` puis `BM/domain/settings/ModelCapacity.java` (record `id` non vide, `name` à défaut l'identifiant, `promptTokens` et `contextTokens` « nul si absent, ≤ 0 ou > 10 000 000 », `multiplier` « nul si absent ou < 0 »)
- [X] T003 [P] Tests d'abord `BT/domain/knowledge/KnowledgeBudgetTest.java` puis `BM/domain/knowledge/KnowledgeBudget.java` (research.md R2) : `STANDARD` = total 35 000, rôles 3 000, ADR 12 000 / 8 000, documents trouvés 8 000 / 6 000, désignés 12 000 / 8 000, tickets 3 000, architecture 3 000, sous-tâches 1 500 ; `forPromptTokens(OptionalInt)` = `(tokens × 3 − 60 000) / 2` borné entre 35 000 et 200 000, chaque plafond × `total / 35 000` arrondi à l'entier inférieur ; inconnu → `STANDARD` ; cas 32 000, 64 000 (total 66 000), 128 000 (total 162 000, document désigné 37 028), 2 000 000 (200 000) ; `isStandard()`
- [X] T004 `CopilotSettings.capacities` et `connected(login, models, capacities)` dans `BM/domain/settings/CopilotSettings.java` ; persistance dans `BM/infrastructure/persistence/settings/CopilotSettingsEntity.java` (convertisseur JSON) et `SettingsJpaRepository.java` ; test `BT/infrastructure/persistence/SettingsPersistenceIT.java` (capacités relues, colonne vide → liste vide)
- [X] T005 `GitHubAuthenticator.ModelOption` gagne `promptTokens`, `contextTokens` (`Integer`) et `multiplier` (`Double`) dans `BM/domain/port/GitHubAuthenticator.java` ; lecture dans `BM/infrastructure/github/SdkCopilotAccountGateway.java` (`getCapabilities().getLimits()`, `getBilling().getMultiplierOpt()`, sans planter si nuls) ; capacités simulées dans `BM/infrastructure/github/FakeGitHubAuthenticator.java` (un modèle de 128 000 tokens ×1, un de 32 000 ×0,33, un sans limite) ; `BM/application/settings/PollCopilotConnection.java` range les capacités avec les identifiants ; test `BT/application/settings/PollCopilotConnectionTest.java` (ou le test existant de la connexion)
- [X] T006 Port `BM/domain/port/ModelCapacities.java` (`OptionalInt promptTokens()` du modèle choisi, `List<ModelCapacity> all()` des modèles autorisés) et adaptateur `BM/infrastructure/github/SettingsModelCapacities.java` (modèle nul ou absent des capacités → inconnu) ; test `BT/infrastructure/github/SettingsModelCapacitiesTest.java`

**Checkpoint** : capacités lues, rangées et exposées ; budget calculable.

---

## Phase 3: User Story 1 - La connaissance transmise suit la capacité du modèle (Priority: P1) 🎯 MVP

**Goal** : avec un grand modèle, la connaissance grandit (matrice de 30 000 transmise en entier) ;
sinon, comportement identique à aujourd'hui.

**Independent Test** : quickstart.md scénarios 1 et 2.

- [X] T007 [US1] Tests d'abord dans `BT/application/knowledge/SelectKnowledgeTest.java` : (a) modèle de 128 000 tokens → document désigné de 30 000 transmis en entier, sans « [document tronqué] » ; (b) proportions gardées (ADR, documents, tickets ≤ leurs plafonds × facteur), nombres d'éléments inchangés, fiche générale prioritaire, total ≤ budget ; (c) modèle inconnu → tous les tests existants passent sans changement ; (d) empreinte : ligne `budget=` seulement hors `STANDARD`
- [X] T008 [US1] `BM/application/knowledge/SelectKnowledge.java` : constructeur avec `ModelCapacities` (les constructeurs existants gardent `STANDARD`), budget calculé au début de chaque sélection, toutes les constantes de taille remplacées par celles du budget (rôles, ADR, documents, désignés, tickets, architecture, sous-tâches), `mentionBudget` et `mentionPart` avec le budget ; câblage `BM/infrastructure/config/UseCaseProducer.java`
- [X] T009 [US1] `BM/domain/knowledge/ProjectKnowledge.java` : `budget` (total), `withBudget`, ligne `budget=<total>` dans `fingerprint()` hors `STANDARD` ; `ProjectKnowledge.MAX_TOTAL` reste le total `STANDARD` ; test `BT/domain/knowledge/ProjectKnowledgeEntryTest.java` (ou un test de l'empreinte)
- [X] T010 [US1] `BM/application/knowledge/MentionQueries.java` : place calculée avec le budget du modèle choisi (FR-010) ; test dans `BT/application/knowledge/MentionQueriesTest.java` (la matrice de 30 000 indiquée « transmise » avec 128 000 tokens, « en partie » sans limite connue)

**Checkpoint** : le cas réel passe avec un grand modèle.

---

## Phase 4: User Story 2 - Voir la capacité et le coût de chaque modèle (Priority: P2)

**Goal** : la configuration montre capacité, coût et place de la connaissance.

**Independent Test** : quickstart.md scénario 5 (configuration) et 6 (E2E).

- [X] T011 [US2] `BM/infrastructure/web/SettingsResource.java` : `CopilotView.models` (`id`, `name`, `promptTokens`, `contextTokens`, `multiplier`, `knowledgeChars`) et `knowledgeChars` du modèle choisi (contracts/api.md), `availableModels` gardé ; test `BT/infrastructure/web/SettingsResourceIT.java` (ou l'IT existant de la configuration Copilot)
- [X] T012 [US2] Tests d'abord dans la spec de `FA/features/settings/copilot-card.component.ts` puis le composant et `FA/core/settings/settings.model.ts` : chaque option montre « environ N tokens (≈ M caractères) · ×coefficient », ou « capacité inconnue » / « coût inconnu » ; ligne « Connaissance transmise : jusqu'à N caractères » sous le choix ; le texte de l'option est lu au lecteur d'écran

**Checkpoint** : on choisit un modèle en connaissance de cause.

---

## Phase 5: User Story 3 - Être averti qu'un autre modèle transmettrait le document en entier (Priority: P3)

**Goal** : une mention coupée propose le modèle le moins coûteux qui suffit.

**Independent Test** : quickstart.md scénario 3.

- [X] T013 [US3] Tests d'abord dans `BT/application/knowledge/MentionQueriesTest.java` : `betterModel` pour une mention `PARTIAL` ou `NO_ROOM` = le modèle autorisé le moins coûteux (coefficient inconnu compté comme le plus cher) qui transmet le document en entier, puis la plus petite limite ; nul si aucun ne suffit ou si la mention est transmise ; aucun changement du modèle choisi
- [X] T014 [US3] `MentionQueries.MentionState.betterModel` (`id`, `name`, `multiplier`) dans `BM/application/knowledge/MentionQueries.java` (research.md R5)
- [X] T015 [US3] Tests d'abord dans `FA/shared/ui/mention-status.component.spec.ts` puis `FA/shared/ui/mention-status.component.ts` et `FA/core/projects/projects.model.ts` : « transmis en entier avec <nom> (×coefficient) » quand `betterModel` est présent

**Checkpoint** : aucun document coupé sans dire comment l'éviter.

---

## Phase 6: Polish & Cross-Cutting

- [X] T016 E2E `e2e/tests/` (configuration Copilot, modèles simulés de T005) : capacité et coût affichés, « capacité inconnue », ligne du budget après choix du grand modèle ; axe sans violation
- [X] T017 [P] Contrat `specs/001-local-spec-assistant/contracts/` ou `specs/002-projets-contexte-mcp/contracts/openapi.yaml` (vue Copilot enrichie, `betterModel`) et README (choix du modèle, place de la connaissance)
- [X] T018 Gates avant commit (CLAUDE.md) : `rm -f backend/target/jacoco.exec`, `./mvnw -B verify -DskipDependencyCheck`, `./mvnw -B package -DskipTests`, `npm --prefix e2e test`, et `bash ci/check-eval.sh` vert sans relancer l'évaluation (prompts et `infrastructure/llm` inchangés, research.md R3) ; sinon, évaluation réelle avec l'accord du mainteneur
- [X] T020 Correctif de l'essai manuel : une connexion faite avant la 008 garde des capacités vides (« capacité inconnue ») ; `BM/application/settings/RefreshModelCapacities.java` relit modèles et capacités d'un compte connecté, lancé au démarrage en tâche de fond (`BM/infrastructure/platform/ModelCapacitiesStartup.java`), sans bloquer ni échouer si Copilot ne répond pas ; tests `BT/application/settings/SettingsUseCasesTest.java` et `BT/infrastructure/platform/ModelCapacitiesStartupTest.java`
- [X] T021 Correctif de l'essai manuel : la déconnexion refusée par le trousseau macOS (jeton rangé par un autre programme) affiche « GitHub Copilot ne répond pas » ; code `COPILOT_KEYCHAIN_DENIED` avec la marche à suivre, levé par `BM/infrastructure/github/SdkCopilotAccountGateway.java` ; test `BT/infrastructure/github/SdkCopilotAccountGatewayTest.java`
- [X] T019 Essai manuel par l'utilisateur (quickstart.md, « Essai manuel ») ; résultat dans `specs/008-budget-selon-modele/checklists/validation.md`

---

## Dependencies & Execution Order

- **Setup (T001)** → **Foundational (T002–T006)** → user stories ; T002 et T003 en parallèle ; T004
  après T002 ; T005 après T004 ; T006 après T004.
- **US1** : T007 → T008 → T009 → T010.
- **US2** : après Foundational ; T011 → T012.
- **US3** : après T010 ; T013 → T014 → T015.
- **Polish** : T016 après US2 ; T017 à tout moment ; T018 avant chaque commit ; T019 en dernier.

## Parallel Example

```text
Foundational : T002 (ModelCapacity) + T003 (KnowledgeBudget)
US2 : T011 (vue) en parallèle de US1, la carte (T012) après T011
```

## Implementation Strategy

1. **MVP = US1** : le budget suit le modèle ; commit après les gates.
2. US2 (affichage), puis US3 (suggestion), chacune commitée après les gates.
3. Essai manuel, PR.
