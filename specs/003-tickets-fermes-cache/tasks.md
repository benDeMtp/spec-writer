---
description: "Tâches de la feature 003 : tickets fermés gardés en cache"
---

# Tasks: Tickets fermés gardés en cache

**Input**: `specs/003-tickets-fermes-cache/` (plan.md, spec.md, research.md, data-model.md,
contracts/, quickstart.md)

**Tests**: obligatoires (constitution III, TDD) : chaque test est écrit et vu échouer avant le
code qu'il couvre. Les règles de `domain` et `application` sont couvertes par des tests
unitaires (JaCoCo ne compte pas les `@QuarkusTest`).

**Organization**: une phase par user story ; US1 et US2 sont P1, US3 P2.

## Format: `[ID] [P?] [Story] Description`

- **[P]** : parallélisable (fichiers différents, aucune dépendance sur une tâche non finie)
- Chemins : `backend/src/main/java/dev/specwriter/` noté `BM/`, `backend/src/test/java/dev/specwriter/`
  noté `BT/`, `frontend/src/app/` noté `FA/`

---

## Phase 1: Setup

Aucune : pas de dépendance, de table ni de migration nouvelles (plan.md, data-model.md).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: ports et doublures partagés par US1 et US3.

- [ ] T001 [P] Ajouter `Set<String> closedTickets(UUID projectId)` à `BM/domain/port/KnowledgeIndex.java` (clés des tickets indexés dont `status_category = 'done'`), l'implémenter dans `BT/support/InMemoryKnowledgeIndex.java`, puis, test d'abord dans `BT/infrastructure/knowledge/SqliteKnowledgeIndexIT.java` (deux tickets fermés, un ouvert, une section de doc ; un autre projet ignoré), dans `BM/infrastructure/knowledge/SqliteKnowledgeIndex.java` (SQL natif)
- [ ] T002 [P] Ajouter à `BM/domain/port/IssueTracker.java` `TrackedIssuePage searchOpenOrUpdatedSince(JiraEndpoint, String projectKey, Instant since, int startAt, int maxResults)` (« `since` non nul, tickets ouverts ou modifiés depuis `since`, ordre `updated` croissant ») et `List<TrackedIssue> issuesByKeys(JiraEndpoint, String projectKey, List<String> keys)` (« 50 clés au plus, clés au format `[A-Z][A-Z0-9_]+-\d+` sinon `IllegalArgumentException` ; une clé inexistante est ignorée »), avec leur Javadoc d'après `contracts/jira-port.md`, et les simuler dans `BT/support/FakeIssueTracker.java` avec un compteur des tickets renvoyés et des clés demandées
- [ ] T003 [P] Créer `BM/application/knowledge/TicketSummary.java` : `record TicketSummary(int read, int keptClosed, int removed)`, « chaque nombre ≥ 0 » vérifié dans le constructeur compact ; ajouter `default void tickets(TicketSummary summary)` sans effet à `BM/application/knowledge/IndexationProgress.java` ; test unitaire `BT/application/knowledge/TicketSummaryTest.java` (nombre négatif refusé)

**Checkpoint** : le projet compile, `HexagonalArchitectureTest` passe (sous Maven).

---

## Phase 3: User Story 1 - Une réindexation ne relit pas les tickets fermés (Priority: P1) 🎯 MVP

**Goal**: l'indexation complète ne relit dans Jira que les tickets ouverts, modifiés, ou absents
de l'index ; les fermés indexés restent tels quels ; le bilan est exposé (FR-002 à FR-004,
FR-008, FR-009).

**Independent Test**: 100 tickets dont 80 fermés indexés ; une indexation complète demande au plus
20 tickets à Jira, bilan `read=20, keptClosed=80, removed=0`, les 80 restent retrouvables
(quickstart.md, US1-1).

### Tests for User Story 1 ⚠️ (écrits d'abord, vus échouer)

- [ ] T004 [P] [US1] Dans `BT/infrastructure/jira/JqlBuilderTest.java`, tester `openOrUpdatedSince` (`project = X AND (statusCategory != Done OR updated >= "yyyy/MM/dd HH:mm") ORDER BY updated ASC`, marge `SINCE_MARGIN` appliquée) et `keys` (`project = X AND key in (A-1, A-2) ORDER BY key ASC` ; clé invalide refusée, liste vide refusée, plus de 50 clés refusées)
- [ ] T005 [P] [US1] Dans `BT/infrastructure/jira/JiraKnowledgeSyncIT.java` (WireMock), tester `searchOpenOrUpdatedSince` (JQL et champs `TRACKED_FIELDS` envoyés, liens et sous-tâches lus comme pour `searchUpdatedSince`) et `issuesByKeys` (paramètre `validateQuery=warn`, `maxResults=50`, réponse avec `warningMessages` pour une clé supprimée : les autres tickets sont rendus, pas d'erreur)
- [ ] T006 [US1] Dans `BT/application/knowledge/SynchronizeKnowledgeTest.java`, scénarios de l'indexation complète avec repère (`FakeIssueTracker`, `InMemoryKnowledgeIndex`) : (a) US1-1, 10 tickets indexés dont 8 fermés, seuls les 2 ouverts relus, les 8 fermés intacts (titre, corps, vecteur) ; (b) US1-2, ticket fermé absent de l'index lu par `issuesByKeys`, puis non relu à l'indexation complète suivante ; (c) cas limite, ticket indexé ouvert non ramené par la requête (fermé dans Jira sans date plus récente) lu par clé et gardé fermé ; (d) US1-5, ticket fermé absent de `listKeys` retiré, ainsi que ses liens ; (e) FR-008, repère nul : `searchUpdatedSince(null)` lit tout, aucun `searchOpenOrUpdatedSince` ; (f) erreur Jira à l'étape des clés : aucun retrait, repère avancé jusqu'au dernier ticket de la requête ; (g) les clés lues par `issuesByKeys` n'avancent pas le repère ; (h) lots de 50 clés au plus ; (i) bilan transmis par `IndexationProgress.tickets` : `read`, `keptClosed`, `removed` attendus, et en incrémental `keptClosed = 0`
- [ ] T007 [P] [US1] Dans `BT/application/knowledge/SynchronizeKnowledgeTest.java`, scénarios incrémentaux inchangés (FR-003) : US1-3, ticket fermé rouvert (date modifiée) relu et devenu ouvert ; US1-4, ticket ouvert fermé relu une dernière fois et indexé fermé ; ticket fermé corrigé sans réouverture relu une fois
- [ ] T008 [P] [US1] Dans `BT/application/knowledge/IndexationsTest.java` (le créer s'il n'existe pas), le `Snapshot` garde le `TicketSummary` reçu, et `null` si l'étape Jira n'a pas tourné ; dans `BT/infrastructure/web/KnowledgeResourceIT.java`, `GET /api/projects/{id}/indexations/current` rend `tickets.read`, `tickets.keptClosed`, `tickets.removed` (contracts/openapi-delta.md)
- [ ] T009 [P] [US1] Dans `FA/features/project-context/knowledge-panel.component.spec.ts`, la ligne de bilan « {read} ticket(s) relu(s), {keptClosed} ticket(s) fermé(s) gardé(s) sans relecture, {removed} retiré(s) » s'affiche à la fin (SUCCEEDED, FAILED, CANCELLED), segment `keptClosed` omis s'il vaut 0 en incrémental, rien sans `tickets`

### Implementation for User Story 1

- [ ] T010 [P] [US1] Ajouter `openOrUpdatedSince(projectKey, since)` et `keys(projectKey, keys)` à `BM/infrastructure/jira/JqlBuilder.java` (validation des clés `[A-Z][A-Z0-9_]+-\d+`, 1 à 50 clés)
- [ ] T011 [US1] Implémenter `searchOpenOrUpdatedSince` et `issuesByKeys` dans `BM/infrastructure/jira/JiraRestClient.java` (réutiliser la lecture des `TrackedIssue` de `searchUpdatedSince` ; `validateQuery=warn` ajouté à l'appel de recherche dans `BM/infrastructure/jira/JiraApi.java`, par un paramètre optionnel ou une seconde méthode ; avertissements ignorés, jamais journalisés avec les clés)
- [ ] T012 [US1] Réécrire l'étape Jira de `BM/application/knowledge/SynchronizeKnowledge.java` selon research.md R1 : mode complet avec repère, requête `searchOpenOrUpdatedSince` paginée par 50 (repère avancé comme aujourd'hui), puis `listKeys`, puis `issuesByKeys` par lots de 50 sur (clés absentes de `index.hashes(TICKET)`) ∪ (indexés ouverts non lus), puis `index.retain` et `links.retain` ; mode complet sans repère et mode incrémental inchangés ; journal « Connaissance : relecture de tous les tickets (pas de repère de synchronisation) » quand le repère est nul ; extraire la lecture d'un ticket (index + liens + avancement) dans une méthode commune
- [ ] T013 [US1] Calculer le `TicketSummary` dans `BM/application/knowledge/SynchronizeKnowledge.java` (`keptClosed` = `index.closedTickets` ∩ clés du projet − tickets lus ; 0 en incrémental), l'envoyer par `progress.tickets(...)`, y compris après une erreur Jira (nombres déjà atteints), et l'écrire au journal en nombres seuls : « Connaissance : %d ticket(s) relu(s), %d fermé(s) gardé(s), %d retiré(s) »
- [ ] T014 [US1] Garder le `TicketSummary` dans `BM/application/knowledge/Indexations.java` (`Snapshot` gagne `TicketSummary tickets`, nullable) et l'exposer dans `IndexationView` de `BM/infrastructure/web/KnowledgeResource.java` (`tickets: {read, keptClosed, removed}`, absent si nul)
- [ ] T015 [P] [US1] Front : type `IndexationTickets` dans `FA/core/projects/projects.model.ts` et ligne de bilan dans `FA/features/project-context/knowledge-panel.component.ts` (texte du contrat, pluriels, `aria-live` déjà présent sur l'état réutilisé)
- [ ] T016 [US1] Mesure SC-001 dans `BT/infrastructure/jira/JiraKnowledgeSyncIT.java` ou un nouvel `BT/knowledge/ClosedTicketsCacheIT.java` : 100 tickets WireMock dont 80 fermés, première indexation complète puis seconde ; la seconde demande au plus 20 tickets (stubs comptés), bilan `read=20, keptClosed=80, removed=0`, et, avec un délai fixe par page simulé, durée de l'étape Jira au moins deux fois plus courte que la première

**Checkpoint** : US1 testable seule (quickstart.md, lignes US1 et FR-008).

---

## Phase 4: User Story 2 - Les tickets fermés sont montrés sans interroger Jira (Priority: P1)

**Goal**: tickets proches et sous-tâches fermés montrés avec leur statut indexé, sans
`issueStatus`, comme à jour (FR-006, FR-010, SC-002).

**Independent Test**: tickets proches deux fermés et un ouvert, Jira en erreur : seul l'ouvert est
demandé et porte la mention « Jira n'a pas pu être relu ».

### Tests for User Story 2 ⚠️

- [ ] T017 [P] [US2] Dans `BT/application/knowledge/FindRelatedIssuesTest.java` : US2-1, deux tickets proches fermés et un ouvert, `issueStatus` appelé une seule fois (l'ouvert), les fermés rendus avec `fresh = true`, statut et date indexés ; US2-2, `issueStatus` en erreur : seuls les ouverts ont `fresh = false` ; un fermé n'est jamais retiré de l'index par cette lecture
- [ ] T018 [P] [US2] Dans `BT/application/knowledge/SubtaskReportTest.java` (ou le test de `ReportSubtasks`) : US2-3, sous-tâches fermées dans l'index non demandées à Jira, `fresh = true` ; sous-tâche absente de l'index toujours relue
- [ ] T019 [P] [US2] Non-régression FR-010 / US2-4 : dans le test existant de la création de story liée (`relates to`) à un ticket fermé, vérifier que le lien est créé comme avant (aucun changement attendu, le test doit passer avant et après)
- [ ] T020 [US2] E2E dans `e2e/tests/us8-project-context.spec.ts` : projet indexé, stub `issue/{key}` des tickets fermés en erreur 500 ; deux tickets proches fermés sans la mention « Jira n'a pas pu être relu », l'ouvert (stub en erreur) la porte ; stubs WireMock temporaires retirés à la fin, comme P8a

### Implementation for User Story 2

- [ ] T021 [P] [US2] Dans `BM/application/knowledge/FindRelatedIssues.java`, `fresh(...)` : pour `item.closed()`, rendre le statut indexé avec `fresh = true` sans appeler `tracker.issueStatus`
- [ ] T022 [P] [US2] Dans `BM/application/knowledge/ReportSubtasks.java`, `status(...)` : sous-tâche indexée fermée rendue depuis l'index avec `fresh = true`, sans `issueStatus`

**Checkpoint** : US2 testable seule ; US1 et US2 forment le MVP.

---

## Phase 5: User Story 3 - Le cache d'un ticket fermé n'expire pas (Priority: P2)

**Goal**: aucune durée de vie ; revectorisation depuis le texte indexé (FR-005, FR-007). Déjà
vrai (research.md R3) : tâches de tests de non-régression seulement.

**Independent Test**: horloge avancée d'un an, synchronisation et tickets proches : aucun fermé
relu ni retiré ; changement de modèle : fermés revectorisés sans Jira.

- [ ] T023 [P] [US3] Dans `BT/application/knowledge/SynchronizeKnowledgeTest.java` : US3-1, `FixedClock` avancée d'un an après l'indexation, synchronisation incrémentale puis complète : aucun ticket fermé relu (`FakeIssueTracker`) ni retiré, `indexedAt` inchangé
- [ ] T024 [P] [US3] Dans `BT/application/knowledge/SynchronizeKnowledgeTest.java` : US3-2, `FakeEmbedding` d'un autre `modelId` : les tickets fermés sont revectorisés (vecteurs du nouveau modèle) depuis le texte indexé, sans aucune lecture Jira de ces tickets
- [ ] T025 [US3] SC-004 dans `BT/knowledge/KnowledgeReferenceSetIT.java` (ou `KnowledgeFictiveSetIT.java` si le jeu de référence réel est absent en CI) : recherches par les termes et par le sens sur les tickets fermés, avant puis après une indexation complète, résultats identiques (clés et ordre)

---

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T026 [P] Mettre à jour le résumé de `POST /api/projects/{id}/indexations` et ajouter `Indexation.tickets` dans `specs/002-projets-contexte-mcp/contracts/openapi.yaml`, d'après `contracts/openapi-delta.md`
- [ ] T027 [P] README : paragraphe « Tickets fermés » de la section connaissance du projet (fermés gardés sans relecture, réouverture prise en compte par la synchronisation, bilan affiché) dans `README.md`
- [ ] T028 Gates avant commit (CLAUDE.md) : `rm -f backend/target/jacoco.exec`, `./mvnw -B verify -DskipDependencyCheck`, `./mvnw -B package -DskipTests`, `npm --prefix e2e test` ; puis cocher les tâches et commiter par groupe logique
- [ ] T029 Essai manuel par l'utilisateur sur un projet réel (quickstart.md, « Essai manuel ») : durée de l'étape Jira avant et après, bilan affiché, réouverture d'un ticket ; noter le résultat dans `specs/003-tickets-fermes-cache/checklists/validation.md`

---

## Dependencies & Execution Order

- **Phase 2** (T001–T003) bloque tout.
- **US1** (T004–T016) : T004/T005 avant T010/T011 ; T006–T008 avant T012–T014 ; T009 avant T015 ;
  T016 après T012–T013.
- **US2** (T017–T022) : ne dépend que de la phase 2 (et pas de US1) ; T017/T018 avant T021/T022 ;
  T020 après T021.
- **US3** (T023–T025) : après T012 (les tests passent par l'indexation complète réécrite).
- **Polish** : après les stories retenues.

### Parallel Opportunities

- T001, T002, T003 en parallèle.
- US1 : T004, T005, T007, T008, T009 en parallèle ; puis T010 et T015 en parallèle.
- US2 entièrement en parallèle de US1 (fichiers distincts) ; T021 et T022 en parallèle.
- US3 : T023 et T024 en parallèle.

```text
# US2 pendant US1 :
T017 FindRelatedIssuesTest   | T006 SynchronizeKnowledgeTest
T018 SubtaskReportTest       | T004 JqlBuilderTest
T021 FindRelatedIssues       | T010 JqlBuilder
```

## Implementation Strategy

1. **MVP** : phase 2, US1 (moins de lectures Jira et bilan), US2 (affichage sans Jira). Valider
   par le quickstart, puis commiter.
2. **US3** : tests de non-régression, sans code, puis SC-004.
3. **Polish** : contrat, README, gates, essai manuel par l'utilisateur.
