---
description: "Tâches de la feature 005 : réindexation par source"
---

# Tasks: Réindexation par source

**Input**: `specs/005-reindexation-par-source/` (plan.md, spec.md, research.md, data-model.md,
contracts/api.md, quickstart.md)

**Tests**: obligatoires (constitution III, TDD) : chaque test est écrit et vu échouer avant le
code. `domain` et `application` sont couverts par des tests unitaires (JaCoCo ne compte pas les
`@QuarkusTest`).

**Organization**: une phase par user story, toutes P1 : US1 (étapes indépendantes) d'abord, car
US2 s'appuie sur des étapes qui vivent chacune leur vie.

## Format: `[ID] [P?] [Story] Description`

- **[P]** : parallélisable (fichiers différents, aucune dépendance sur une tâche non finie)
- Chemins : `backend/src/main/java/dev/specwriter/` noté `BM/`, `backend/src/test/java/dev/specwriter/`
  noté `BT/`, `frontend/src/app/` noté `FA/`

---

## Phase 1: Setup

- [X] T001 Vérifier la base : `../mvnw -B -o -q test -DskipDependencyCheck -Djacoco.skip=true -Dsurefire.failIfNoSpecifiedTests=false -Dtest='SynchronizeKnowledgeTest,KnowledgeResourceIT'` depuis `backend/` passe avant tout changement

---

## Phase 2: Foundational (Blocking Prerequisites)

Aucune : chaque story apporte ce dont elle a besoin.

---

## Phase 3: User Story 1 - Une étape en échec n'arrête pas les autres (Priority: P1) 🎯 MVP

**Goal**: FR-001 à FR-005 (research.md R1).

**Independent Test**: erreur inattendue à la lecture de Jira → documentation et architecture
indexées, erreur `JIRA/UNEXPECTED` dans le détail, indexation `SUCCEEDED` avec erreurs.

- [X] T002 [US1] Tests d'abord dans `BT/application/knowledge/SynchronizeKnowledgeTest.java` : (a) `RuntimeException` inattendue pendant la lecture de Jira (`FakeIssueTracker.failingCall` ou équivalent) → sections, ADR et éléments d'architecture indexés, erreur `IndexationError(JIRA, clé, "UNEXPECTED", null, "Erreur inattendue (…)")`, repère Jira inchangé ; (b) `LinkageError` (`NoSuchMethodError`) à Jira → même résultat ; (c) deux sources de documentation, la première lève une exception inattendue → la seconde indexée, erreur `SOURCE` avec l'emplacement, aucun élément de documentation retiré ; (d) exception inattendue à l'architecture → Jira et documentation indexés, erreur `ARCHITECTURE/UNEXPECTED`, empreinte inchangée ; (e) exception inattendue au calcul des vecteurs → erreur `MODEL/UNEXPECTED`, sources indexées ; (f) annulation demandée pendant une étape → `Cancelled` remonte, pas d'erreur d'étape ; (g) par `Indexations` : statut `SUCCEEDED` avec l'erreur d'étape
- [X] T003 [US1] `BM/application/knowledge/SynchronizeKnowledge.java` : garde par source (`guarded(kind, location, progress, action)`) autour de Jira, de chaque source de documentation, de l'architecture et des vecteurs ; attrape `RuntimeException` et `LinkageError` sauf `IndexationProgress.Cancelled` et annulation demandée ; `LOG.error` avec la pile ; Jira en échec renvoie le repère d'avant l'étape, architecture en échec garde la source, documentation en échec rend la lecture incomplète (aucun retrait)
- [X] T004 [P] [US1] Front `FA/features/project-context/knowledge-panel.component.ts` (+ spec) : libellé de la raison `UNEXPECTED` dans le détail (« erreur inattendue ») si `errorLabel` affiche la raison brute ; test Vitest du détail d'une erreur `JIRA/UNEXPECTED`

**Checkpoint** : une étape cassée laisse indexer les autres.

---

## Phase 4: User Story 2 - Réindexer seulement les sources choisies (Priority: P1)

**Goal**: FR-006 à FR-011 (research.md R2 à R6).

**Independent Test**: choisir la seule deuxième source de documentation → seule elle est lue ;
aucune requête Jira ; date « Synchronisée le » inchangée.

### Backend

- [X] T005 [P] [US2] Test d'abord `BT/application/knowledge/IndexationScopeTest.java`, puis `BM/application/knowledge/IndexationScope.java` : `record IndexationScope(boolean jira, Set<UUID> docSources, boolean architecture)`, `ALL`, `all()`, `of(List<String> ids, List<DocSource> declared)` (`"JIRA"`, `"ARCHITECTURE"`, identifiants de sources du projet ; liste vide, valeur inconnue, identifiant d'un autre projet → `DomainException(VALIDATION_FAILED)`), `includes(DocSource)`
- [X] T006 [P] [US2] Test d'abord (test du domaine existant de `KnowledgeSource`, ou nouveau `BT/domain/knowledge/KnowledgeSourceTest.java`), puis `BM/domain/knowledge/KnowledgeSource.java` : `withJiraMark(Instant)` avance le repère Jira sans changer `syncedAt`
- [X] T007 [US2] Tests d'abord dans `BT/application/knowledge/SynchronizeKnowledgeTest.java` : (a) portée = une source de documentation sur deux → seule elle est lue (lecteur factice qui compte les lectures), aucune requête Jira, architecture non lue ; (b) page retirée de la source choisie → l'élément reste (pas de retrait partiel) ; (c) portée = architecture → modèle réanalysé même empreinte identique ; (d) portée = Jira → règles de la 003 (fermés gardés), repère avancé, `syncedAt` inchangé ; (e) portée `ALL` → comportement actuel, `syncedAt` avancé
- [X] T008 [US2] `BM/application/knowledge/SynchronizeKnowledge.java` : `run(projectId, mode, scope, progress)` (l'ancienne signature délègue avec `ALL`) ; étapes sautées hors portée ; retrait de documentation seulement si `scope.all()` et lecture complète ; enregistrement des repères : `synced(mark, now)` si `ALL`, sinon `withJiraMark` (si Jira choisi) et empreinte d'architecture (si choisie)
- [X] T009 [US2] `BM/application/knowledge/Indexations.java` : `start(projectId, mode, List<String> sources)` (valide par `IndexationScope.of` avec les sources de documentation du projet ; liste non nulle seulement en `FULL`, sinon `VALIDATION_FAILED`) ; `Snapshot.sources` ; `trigger` et `followUp` inchangés (`ALL`) ; tests dans `SynchronizeKnowledgeTest` (validation, `sources` dans l'instantané)
- [X] T010 [US2] `BM/application/knowledge/KnowledgeQueries.java` : `Status.sources` (`IndexableSource(id, kind, label)` : Jira si configuré, consenti et clé de projet effective, « Jira CLE » ; chaque source de documentation, libellé = emplacement ; architecture si workspace déclaré) ; câblage dans `BM/infrastructure/config/UseCaseProducer.java` si le constructeur change ; test unitaire existant de `KnowledgeQueries` ou nouveau
- [X] T011 [US2] `BM/infrastructure/web/KnowledgeResource.java` : `IndexationInput(String mode, List<String> sources)`, `IndexationView.sources` ; IT dans `BT/infrastructure/web/KnowledgeResourceIT.java` : 202 avec `sources` repris ; 422 pour liste vide, valeur inconnue, `INCREMENTAL` avec liste ; `GET /knowledge` renvoie `sources` limitées aux sources déclarées

### Front

- [X] T012 [P] [US2] `FA/core/projects/projects.model.ts` et `FA/core/projects/knowledge.api.ts` : types `IndexableSource`, `KnowledgeStatus.sources`, `Indexation.sources` ; `start(projectId, mode, sources?)` (corps `{ mode, sources }` seulement si `sources`)
- [X] T013 [US2] Tests d'abord dans `FA/features/project-context/knowledge-panel.component.spec.ts`, puis `knowledge-panel.component.ts` : bouton « Réindexer des sources… » (absent pendant une indexation) ; `fieldset` « Sources à réindexer » avec une case par source du statut, rien de coché ; « Réindexer la sélection » désactivé sans case cochée, lance `start(id, 'FULL', ids)` ; « Annuler » referme et rend le focus au bouton ; bilan « Sources réindexées : … » quand `sources` n'est pas `null` ; bilan des tickets absent si Jira n'en fait pas partie
- [X] T014 [US2] E2E `e2e/tests/us8-project-context.spec.ts` (jeu fictif) : « Réindexer des sources… », cocher la seule documentation, lancer ; aucune requête de recherche Jira reçue par WireMock pendant cette indexation ; bilan « Sources réindexées » ; axe sans violation

**Checkpoint** : une source se réindexe seule, en trois actions.

---

## Phase 5: Polish & Cross-Cutting Concerns

- [X] T015 [P] Contrat `specs/002-projets-contexte-mcp/contracts/openapi.yaml` (`IndexationInput.sources`, `IndexationView.sources`, `KnowledgeStatus.sources`, raison `UNEXPECTED`) et README (réindexation par source)
- [X] T016 Gates avant commit (CLAUDE.md) : `rm -f backend/target/jacoco.exec`, `./mvnw -B verify -DskipDependencyCheck`, `./mvnw -B package -DskipTests`, `npm --prefix e2e test`
- [X] T017 Essai manuel par l'utilisateur (quickstart.md, « Essai manuel ») ; résultat dans `specs/005-reindexation-par-source/checklists/validation.md`

---

## Dependencies & Execution Order

- **US1** (T002–T004) : T002 avant T003 ; T004 indépendant.
- **US2** : T005 et T006 en parallèle ; T007 avant T008 (après T005, T006) ; T009 après T008 ;
  T010 indépendant de T008 ; T011 après T009 et T010 ; T012 en parallèle du backend ; T013 après
  T012 ; T014 après T011 et T013.
- **Polish** : T015 à tout moment ; T016 avant chaque commit ; T017 en dernier.

## Parallel Example

```text
T005 IndexationScope        | T006 KnowledgeSource.withJiraMark | T012 types et API du front
```

## Implementation Strategy

US1 seule est livrable : elle corrige l'arrêt complet constaté à l'essai de la 003. US2 ajoute le
choix des sources. Commits par groupe logique : US1, puis US2 backend, puis US2 front et E2E,
puis contrat et README.
