---
description: "Tâches de la feature 004 : ADR du projet"
---

# Tasks: ADR du projet

**Input**: `specs/004-adr-du-projet/` (plan.md, spec.md, research.md, data-model.md,
contracts/api.md, quickstart.md)

**Tests**: obligatoires (constitution III, TDD) : chaque test est écrit et vu échouer avant le
code. `domain` et `application` sont couverts par des tests unitaires (JaCoCo ne compte pas les
`@QuarkusTest`).

**Organization**: une phase par user story, toutes P1, dans l'ordre US1 (compteurs), US2 (page),
US3 (ADR entier).

## Format: `[ID] [P?] [Story] Description`

- **[P]** : parallélisable (fichiers différents, aucune dépendance sur une tâche non finie)
- Chemins : `backend/src/main/java/dev/specwriter/` noté `BM/`, `backend/src/test/java/dev/specwriter/`
  noté `BT/`, `frontend/src/app/` noté `FA/`

---

## Phase 1: Setup

Aucune : pas de dépendance, de table ni de migration (plan.md).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: le numéro d'ADR, partagé par la page (US2) et la génération (US3).

- [ ] T001 Test d'abord `BT/domain/knowledge/AdrNumberTest.java`, puis `BM/domain/knowledge/AdrNumber.java` : `static OptionalInt of(String ref, String title)` selon data-model.md (« `adr[-_ ]?0*(\d+)` (référence ou titre) », « fichier `0*(\d+)[-_]…` (dernier segment de la référence) », « titre `^0*(\d+)\.\s` », sinon vide) ; cas : `adr-005.html` → 5, « ADR-12 : … » → 12, `aurora/adr/0005-trois-domaines.html` → 5, « 5. Trois domaines » → 5, `guide/installation.html` + « Installation » → vide ; et `static OptionalInt ofCited(String supersededBy)` (« ADR-0005 » → 5)
- [ ] T002 Remplacer `number(...)`, `ADR_NUMBER` et `LEADING_NUMBER` de `BM/application/knowledge/SelectKnowledge.java` par `AdrNumber` (comparaison par valeur entière) ; `SelectKnowledgeTest` existant (dont `successeurRetrouveParLeNumeroDeSonFichier`) reste vert

**Checkpoint** : `SelectKnowledgeTest`, `AdrNumberTest` et `HexagonalArchitectureTest` (sous Maven) passent.

---

## Phase 3: User Story 1 - Les compteurs disent combien d'ADR et de sections le projet a (Priority: P1) 🎯 MVP

**Goal**: « 21 ADR » et non « 57 » (FR-001).

**Independent Test**: index de 3 ADR dont un en 3 morceaux et d'une section en 2 morceaux :
`count(ADR) = 3`, `count(DOC_SECTION) = 1`.

- [ ] T003 [US1] Test d'abord dans `BT/infrastructure/knowledge/SqliteKnowledgeIndexIT.java` (`compteursParElementEtNonParMorceau` : ADR en morceaux 0, 1, 2 + deux ADR d'un morceau ; section en morceaux 0, 1), puis `count` de `BM/infrastructure/knowledge/SqliteKnowledgeIndex.java` limité à `chunk = 0` (research.md R1) ; même règle dans `BT/support/InMemoryKnowledgeIndex.java`
- [ ] T004 [US1] Vérifier les tests qui comptent les ADR ou les sections (`KnowledgeResourceIT` : `docSections`, `adrs` ; `KnowledgeFictiveSetIT` ; `knowledge-panel.component.spec.ts`) et ajuster les valeurs attendues qui comptaient des morceaux, en notant chaque changement

**Checkpoint** : US1 livrable seule.

---

## Phase 4: User Story 2 - Une page liste les ADR du projet (Priority: P1)

**Goal**: page `projets/:id/contexte/adr` ouverte depuis « N ADR » (FR-002 à FR-009, FR-013, FR-014).

**Independent Test**: jeu fictif (3 ADR : 002 remplacé par 005, 004, 005) : le lien « 3 ADR » ouvre
la page, 3 lignes triées, 002 grisé « non transmis à l'IA », successeur ADR-005 trouvé ; un titre
ouvre un nouvel onglet.

### Tests for User Story 2 ⚠️ (écrits d'abord, vus échouer)

- [ ] T005 [P] [US2] `BT/application/knowledge/ListAdrsTest.java` (`InMemoryKnowledgeIndex`, `InMemoryDocSources`) : regroupement des morceaux par référence (titre, statut, successeur du morceau 0) ; tri « numéro croissant, puis titre ; sans numéro à la fin, par titre » ; statut absent → `UNKNOWN` ; `duplicate` vrai pour deux ADR de même valeur (« ADR-5 » et `0005-…`) ; `successorFound` vrai/faux ; `sentToAi` faux pour `SUPERSEDED` et `DEPRECATED` ; `byStatus` sur tous les ADR ; `url` = référence si elle commence par `http`, sinon `/api/projects/{id}/doc-files/{ref}` si un dossier déclaré du projet contient le fichier, sinon vide ; `indexing` reflète `Indexations.current` ; projet inconnu → `PROJECT_NOT_FOUND`
- [ ] T006 [P] [US2] `BT/infrastructure/web/AdrResourceIT.java` : `GET /api/projects/{id}/knowledge/adrs` rend le contrat `AdrList` (contracts/api.md) ; projet sans ADR → `adrs: []` ; projet inconnu → 404
- [ ] T007 [P] [US2] `BT/infrastructure/web/DocFileResourceIT.java` (dossier temporaire déclaré comme source FOLDER d'un projet) : page `adr/0001-x.html` servie en 200 `text/html` avec les en-têtes du contrat (`Content-Security-Policy: sandbox allow-same-origin; script-src 'none'; object-src 'none'; default-src 'self' 'unsafe-inline'`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`, `Cache-Control: no-store`) ; CSS et image servis avec leur type ; 404 pour : `../secret.html`, `%2e%2e/secret.html`, chemin absolu, lien symbolique vers l'extérieur du dossier, extension `.js` et `.txt`, fichier absent, dossier, fichier d'un dossier déclaré par un **autre** projet, source de type URL ; sans cookie de session → 401/403 comme le reste de l'API
- [ ] T008 [P] [US2] `FA/features/project-context/adrs.page.spec.ts` : lignes triées, résumé « 6 acceptés, 15 proposés » (pluriels), filtre par statut sans effet sur le résumé, ligne grisée + « non transmis à l'IA », « numéro en double », successeur « introuvable », statut « inconnu », lien `target="_blank"` `rel="noopener noreferrer"` dont le nom accessible finit par « (nouvel onglet) », titre sans lien si `url` vide, message « Aucun ADR n'a été trouvé dans la documentation de ce projet » et lien de retour, mention « indexation en cours »
- [ ] T009 [P] [US2] `FA/features/project-context/knowledge-panel.component.spec.ts` : « 3 ADR » est un lien vers `/projets/p1/contexte/adr` ; « 0 ADR » n'est pas un lien

### Implementation for User Story 2

- [ ] T010 [US2] `BM/application/knowledge/ListAdrs.java` et `AdrList.java` (records `AdrList(List<AdrSummary> adrs, Map<String, Integer> byStatus, boolean indexing)` et `AdrSummary` selon data-model.md : « `title` texte ≤ 500 », « `status` `ACCEPTED`, `PROPOSED`, `SUPERSEDED`, `DEPRECATED`, `UNKNOWN` ») ; câblage dans `BM/infrastructure/config/UseCaseProducer.java`
- [ ] T011 [US2] `GET /knowledge/adrs` dans `BM/infrastructure/web/KnowledgeResource.java` (vue JSON du contrat)
- [ ] T012 [US2] `BM/infrastructure/web/DocFileResource.java` : `GET /api/projects/{id}/doc-files/{path: .+}` selon research.md R3 (sources FOLDER du projet seules ; `toRealPath` du fichier et du dossier puis `startsWith` ; liste d'extensions ; 404 sans détail ; en-têtes du contrat ; refus journalisé sans chemin ; aucun contenu journalisé) ; la logique de confinement en dehors de la ressource si elle est réutilisable, testée par T007
- [ ] T013 [P] [US2] Front : types `AdrList`, `AdrSummary` dans `FA/core/projects/projects.model.ts`, `adrs(projectId)` dans `FA/core/projects/knowledge.api.ts`, page `FA/features/project-context/adrs.page.ts` (tableau, résumé, filtre, signalements, WCAG 2.1 AA), route `projets/:id/contexte/adr` (titre « ADR · spec-writer ») dans `FA/app.routes.ts`
- [ ] T014 [US2] `FA/features/project-context/knowledge-panel.component.ts` : « N ADR » en `routerLink` vers la page quand N ≥ 1 (le reste de la ligne des compteurs inchangé)
- [ ] T015 [US2] E2E dans `e2e/tests/us8-project-context.spec.ts` : depuis la page Contexte du jeu fictif, clic sur « 3 ADR », 3 lignes triées (002, 004, 005), 002 grisé « non transmis à l'IA » avec successeur ADR-005, lien du titre de 005 avec `target="_blank"` vers l'adresse WireMock de `adr-005.html`, aucune violation axe

**Checkpoint** : US2 testable seule (quickstart.md).

---

## Phase 5: User Story 3 - La génération reçoit l'ADR entier (Priority: P1)

**Goal**: ADR entier, une fois, dans 6 000 caractères au total et 4 000 par ADR (FR-010 à FR-012).

**Independent Test**: ADR en 3 morceaux dont seul le troisième est retenu : l'entrée transmise
contient les trois, dans l'ordre.

- [ ] T016 [US3] Tests d'abord dans `BT/application/knowledge/SelectKnowledgeTest.java` : (a) US3-1, ADR en 3 morceaux, seul le 3e correspond au besoin → une entrée, `chunk = 0`, texte = morceaux 0, 1, 2 joints dans l'ordre ; (b) US3-2, deux morceaux retenus → une seule entrée ; (c) US3-3, ADR de 3 000, 2 500 et 2 000 caractères → les deux premiers passent entiers (5 500 ≤ 6 000), le troisième non ; (d) ADR de 5 000 caractères → coupé à la dernière ligne `#` avant 4 000, suivi de « [ADR tronqué] », et sans ligne de titre au dernier paragraphe ; (e) US3-4, ADR remplacé retenu → successeur entier ; (f) US3-5, deux préparations identiques ; (g) 4 ADR au plus
- [ ] T017 [US3] `BM/application/knowledge/SelectKnowledge.java` : `adrs(...)` selon research.md R4 (rang des ADR = meilleur rang de leurs morceaux ; texte = morceaux de `index.all(ADR)` triés ; budget `MAX_ADR_TEXT = 6_000`, nouveau `MAX_ADR_PART = 4_000` ; arrêt au premier ADR qui ne tient pas ; coupe à une ligne de titre, sinon au paragraphe, « [ADR tronqué] »)
- [ ] T018 [US3] Vérifier les réponses rejouées (`e2e/fixtures/llm/*.json`, `backend/src/test/resources/golden/*/llm/*.json`) : aucun `userContains` ne dépend d'un fragment d'ADR coupé ; lancer `KnowledgeFictiveSetIT`, les tests golden et les E2E concernés
- [ ] T019 [US3] Évaluation réelle (SC-004, research.md R5), **avec l'accord de l'utilisateur** (environ 150 requêtes Copilot) : `env -u JAVA_TOOL_OPTIONS ./mvnw -B -pl backend verify -Peval -DskipDependencyCheck`, scores ≥ `baseline.json`, `bash ci/check-eval.sh`, commit de `backend/src/test/resources/eval/last-run.json`

---

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T020 [P] Contrat : ajouter `GET /knowledge/adrs`, `AdrList` et `GET /doc-files/{path}` à `specs/002-projets-contexte-mcp/contracts/openapi.yaml` d'après `contracts/api.md`
- [ ] T021 [P] README : page des ADR (lien depuis le contexte, signalements, pages d'un dossier local servies par l'outil sans script) et ADR transmis en entier, dans `README.md`
- [ ] T022 Gates avant commit (CLAUDE.md) : `rm -f backend/target/jacoco.exec`, `./mvnw -B verify -DskipDependencyCheck`, `./mvnw -B package -DskipTests`, `npm --prefix e2e test` ; prévenir l'utilisateur avant, son instance tournant depuis `backend/target/` ; puis cocher et commiter par groupe logique
- [ ] T023 Essai manuel par l'utilisateur (quickstart.md, « Essai manuel ») : « 21 ADR », page des ADR, doublons 0005 et 0017, lien en nouvel onglet, génération touchant l'ADR 0017

---

## Dependencies & Execution Order

- **Phase 2** (T001, T002) avant US2 et US3 ; US1 n'en dépend pas.
- **US1** (T003, T004) : indépendante.
- **US2** (T005–T015) : T005–T009 avant T010–T014 ; T015 après T013 et T014.
- **US3** (T016–T019) : après T002 ; T016 avant T017 ; T018 après T017 ; T019 en dernier, avant les
  gates.
- **Polish** : après les stories.

### Parallel Opportunities

- US1 en parallèle de la phase 2.
- US2 : T005, T006, T007, T008, T009 en parallèle ; T012 et T013 en parallèle de T010–T011.
- US2 et US3 en parallèle une fois T002 fait (fichiers distincts, sauf `SelectKnowledge.java`
  réservé à US3).

```text
T003 SqliteKnowledgeIndex (US1)  | T001 AdrNumber (fondation)
T005 ListAdrsTest                | T007 DocFileResourceIT     | T008 adrs.page.spec
T016 SelectKnowledgeTest (US3)   | T013 page Angular (US2)
```

## Implementation Strategy

1. **MVP** : US1 (compteur juste), puis US2 (page des ADR) : ce que l'utilisateur voit en premier.
2. **US3** : ADR entier dans la génération, puis évaluation réelle avec son accord.
3. **Polish** : contrat, README, gates, essai manuel.
