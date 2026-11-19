---
description: "Tâches de la feature 017 : tri de la vue Jira, synchronisation automatique des sources, notifications"
---

# Tasks: Tri de la vue Jira, synchronisation automatique des sources, notification des sources mises à jour

**Input**: `specs/017-synchro-auto-sources/`. Les documents de conception :

- plan.md ;
- spec.md ;
- research.md, R1 à R8 ;
- data-model.md ;
- contracts/rest-api.md et contracts/events.md ;
- quickstart.md.

**Tests**: obligatoires (constitution III, TDD). Chaque test est écrit et vu échouer avant le code.

- JaCoCo ne compte pas les `@QuarkusTest`. `domain` et `application` sont donc couverts par des
  tests unitaires, avec des doubles de `SourceSyncRepository`, `ListedIssues`, `AppEvents`,
  `ClockPort` et `Indexations`.
- Les IT WireMock et les règles ArchUnit se lancent sous Maven.
- Tout nouveau `@QuarkusTest` porte `@Isolated` (`QuarkusTestIsolationTest`).

**Évaluation LLM**: non concernée. Aucun prompt ni code de `infrastructure/llm` n'est touché, et
`ci/check-eval.sh` doit garder son empreinte.

**Cadre**: on reste dans le framework.

- `quarkus-scheduler` porte la tâche planifiée.
- Les événements CDI et le `Multi<OutboundSseEvent>` de Quarkus REST portent les notifications.
- Avant d'écrire le code d'une extension, consulter `quarkus_searchDocs` (quarkus-agent).

**Organisation**: US1 (vue Jira) est indépendante : c'est le MVP. US2 (délais et bilans) produit
les bilans dont US3 (notifications) a besoin.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: parallélisable (fichiers différents, aucune dépendance sur une tâche non finie).
- Abréviations des chemins :
  - `BM/` : `backend/src/main/java/fr/kawamind/`
  - `BT/` : `backend/src/test/java/fr/kawamind/`
  - `BR/` : `backend/src/main/resources/`
  - `FE/` : `frontend/src/app/`

## Gates avant chaque commit (CLAUDE.md)

1. Arrêter le dev mode et supprimer `backend/target/jacoco.exec`. Si une gate a été interrompue,
   supprimer aussi `backend/target/test-data`.
2. Lancer `./mvnw -B verify -DskipDependencyCheck`.
3. Lancer `npm --prefix e2e test` sur le jar que `verify` vient de construire.
4. Lancer `ci/check-eval.sh` : l'empreinte doit être inchangée.

Commits en Conventional Commits, en français, fichiers stagés explicitement. Ne jamais commiter
`.specify/feature.json`.

---

## Phase 1: Setup

- [ ] T001 Ajouter `io.quarkus:quarkus-scheduler` sans version (BOM) dans `backend/pom.xml`.
  - Fixer `%test.quarkus.scheduler.enabled=false` dans `BR/application.properties`.
  - Fixer la configuration du profil `%e2e` : `specwriter.knowledge.auto-sync.every=2s`,
    `specwriter.knowledge.auto-sync.delayed=1s` et `specwriter.knowledge.auto-sync.minute=1s`.
  - Défauts : `every=30s`, `delayed=10s`, `minute=1m`.
  - `quarkus_restart` si le dev mode tourne.
- [ ] T002 Écrire la migration `BR/db/migration/V28__source_sync.sql` (data-model.md) :
  - table `source_sync`, clé primaire `(project_id, source_key)` ;
  - `project_id` : FK `project(id)` ON DELETE CASCADE ;
  - `interval_minutes` : NULL ou ∈ {15, 30, 60, 240, 1440} par `CHECK` ;
  - `last_result` : NULL ou ∈ {CHANGED, UNCHANGED, ERROR} ;
  - `added`, `modified`, `removed` : `INTEGER NOT NULL DEFAULT 0` ;
  - `last_run_at`, `last_success_at` et `error_code`, nullables ;
  - index `(interval_minutes, last_run_at)` ;
  - colonne `app_settings.notify_source_updates INTEGER NOT NULL DEFAULT 0 CHECK (notify_source_updates IN (0, 1))` ;
  - index `knowledge_item(project_id, kind, source_updated_at)` s'il n'existe pas. Le vérifier
    dans les migrations V10 et suivantes.

---

## Phase 2: Foundational (bloque US2 et US3)

**But** : connaître, à la fin de toute indexation, le bilan exact de chaque source relue.

- [ ] T003 [P] Tests unitaires de `SourceChange` dans `BT/domain/sync/SourceChangeTest.java` :
  - refuse un nombre négatif ;
  - `changed()` est faux seulement si les trois nombres valent 0 ;
  - `NONE`.
- [ ] T004 [P] Créer `BM/domain/sync/SourceChange.java` : `record (int added, int modified, int removed)`.
- [ ] T005 Tests de décompte dans `BT/application/knowledge/SynchronizeKnowledgeChangesTest.java`.
  Ils passent par les doubles de connecteurs et d'index déjà utilisés par les tests de
  `SynchronizeKnowledge` :
  - documentation : 1 page nouvelle, 1 page dont l'empreinte change, 1 page disparue donnent
    `(1, 1, 1)` ; une relecture sans changement donne `(0, 0, 0)` ;
  - Confluence : une page dont seul le numéro de version change compte en `modified` ;
  - Jira : un ticket nouveau, un ticket dont `source_updated_at` change, un ticket retiré ; un
    ticket relu identique n'est pas compté ;
  - architecture : un élément ajouté, un élément modifié par empreinte, un élément retiré ;
  - une source en erreur ne rapporte aucun bilan de succès.
- [ ] T006 Étendre `BM/application/knowledge/TicketSummary.java` avec `added` et `modified`,
  valeurs ≥ 0. Mettre à jour tous les appelants et leurs tests (`grep -rn "new TicketSummary"`).
- [ ] T007 Ajouter `default void sourceDone(String sourceKey, SourceChange change)` et
  `default void sourceFailed(String sourceKey, String errorCode)` à
  `BM/application/knowledge/IndexationProgress.java`.
  - `SynchronizeKnowledge` appelle l'un ou l'autre une fois par source relue (`jira`,
    `architecture`, identifiant de source de documentation), à la fin de sa lecture.
  - Les décomptes viennent des écritures dans l'index : `retain` et `replaceSource` pour la
    documentation, `TicketSummary` pour Jira, empreintes pour l'architecture.
  - Fichier : `BM/application/knowledge/SynchronizeKnowledge.java` (T005 au vert).
- [ ] T008 Accepter une liste de sources en mode INCREMENTAL dans `Indexations.start(projectId, mode, sources)` :
  - retirer le refus `mode != FULL` ;
  - l'incrémental se limite à la portée choisie ;
  - test dans `BT/application/knowledge/IndexationsTest.java` : une liste en INCREMENTAL ne relit
    que la source choisie (SC-004) ;
  - fichier : `BM/application/knowledge/Indexations.java`.

**Checkpoint** : le bilan par source est connu à la fin de chaque indexation, et le relire en
incrémental source par source est possible.

---

## Phase 3: User Story 1 — Trier la vue Jira (Priority: P1) 🎯 MVP

**Goal** : la vue « Tickets Jira » lit l'index local et trie toute la liste par date, statut ou
état de synchro.

**Independent Test** :
1. Synchroniser Jira (WireMock), ouvrir la vue et trier par chaque colonne.
2. La liste entière est triée, le tri survit à un changement de page et à un retour.
3. Aucune requête n'atteint Jira (quickstart §1).

### Tests US1 (écrits d'abord)

- [ ] T009 [P] [US1] Tests unitaires de `IssueSort` dans `BT/domain/jira/IssueSortTest.java` :
  - défaut `UPDATED DESC` ;
  - analyse de `sort` ∈ {updated, status, sync} et de `dir` ∈ {asc, desc}, toute autre valeur
    rejetée (`INVALID_SORT`) ;
  - rangs de statut : `new` 0, `indeterminate` 1, `done` 2, autre 3 ;
  - rangs de synchro : `CONFLICT` 0, `LOCAL_AHEAD` 1, `IN_SYNC` 2, `UNLINKED` 3.
- [ ] T010 [P] [US1] Tests unitaires de `ListProjectIssues` (nouvelle version) dans
  `BT/application/jira/ListProjectIssuesTest.java`, avec des doubles de `ListedIssues` et de
  `SourceSyncRepository` :
  - 409 `JIRA_NOT_CONFIGURED` si le projet n'a pas de clé Jira ;
  - `lastSync` vide tant que Jira n'a jamais été synchronisé ;
  - `size` hors de 1 à 100 rejetée.
- [ ] T011 [P] [US1] IT de persistance `BT/infrastructure/knowledge/SqliteListedIssuesIT.java`
  (`@QuarkusTest @Isolated`) :
  - 2 000 tickets générés, chaque tri dans les deux sens ;
  - critères secondaires stables : nom du statut, puis clé ;
  - tickets non liés en `UNLINKED` ;
  - tickets d'un autre projet exclus ;
  - durée de chaque page sous 1 s (SC-001) ;
  - nettoyer le projet créé.
- [ ] T012 [P] [US1] IT web `BT/infrastructure/web/ProjectIssuesResourceIT.java` :
  - forme de la réponse (contracts/rest-api.md) ;
  - 400 sur tri invalide ;
  - 404 sur projet inconnu ;
  - aucune requête WireMock Jira pendant les appels (SC-002) ;
  - lancé sous Maven.

### Implémentation US1

- [ ] T013 [P] [US1] Créer `BM/domain/jira/IssueSort.java` et `BM/domain/jira/ListedIssue.java` (data-model.md).
- [ ] T014 [P] [US1] Créer le port `BM/domain/port/ListedIssues.java` : `page(projectId, jiraKey, IssueSort, offset, limit)` et `count(projectId, jiraKey)`.
- [ ] T015 [US1] Écrire l'adaptateur `BM/infrastructure/knowledge/SqliteListedIssues.java`, une
  requête native SQLite (research.md R1) :
  - tickets `kind = TICKET`, morceau 0, joints à gauche à `linked_jira_ticket` ;
  - `ORDER BY` construit à partir des seuls rangs de `IssueSort`, sans texte venu de l'URL ;
  - lecture `@Transactional`.
- [ ] T016 [US1] Créer le port `BM/domain/port/SourceSyncRepository.java` (data-model.md) et son
  adaptateur `BM/infrastructure/persistence/SourceSyncSqliteRepository.java`. US1 n'en lit que le
  dernier bilan de `jira` ; US2 le complète.
- [ ] T017 [US1] Réécrire `BM/application/jira/ListProjectIssues.java` sur `ListedIssues` et
  `SourceSyncRepository` (T010 au vert).
  - Le câbler dans `BM/infrastructure/config/UseCaseProducer.java`.
  - Retirer la lecture de Jira en direct et le code devenu mort du client Jira, après vérification
    par `grep` qu'aucun outil MCP ne l'appelle.
- [ ] T018 [US1] Remplacer le point d'accès :
  - créer `BM/infrastructure/web/ProjectIssuesResource.java`, `GET /api/projects/{projectId}/jira-issues`
    (T012 au vert) ;
  - supprimer `BM/infrastructure/web/JiraProjectIssuesResource.java` et ses tests.
- [ ] T019 [P] [US1] Tests Vitest dans `FE/features/jira-issues/jira-issues.page.spec.ts` :
  - colonnes Ticket, Titre, Statut, Modifié le, Synchro, Spécification ;
  - un clic sur un en-tête met `tri`, `ordre` et `page=1` dans l'URL ;
  - `aria-sort` sur la colonne active seulement ;
  - annonce du tri dans une région `aria-live="polite"` ;
  - « Dernière synchronisation de Jira : … » ;
  - invitation à synchroniser si `lastSync` est `null` ;
  - le bouton « Resynchroniser Jira » appelle `POST indexations` avec
    `{ mode: 'INCREMENTAL', sources: ['jira'] }`.
- [ ] T020 [US1] Front :
  - `FE/core/jira/jira.api.ts` : remplacer `projectIssues(key, startAt)` par
    `issues(projectId, sort, dir, startAt)`.
  - `FE/features/jira-issues/jira-issues.page.ts` :
    - en-têtes triables : boutons dans les `th` ;
    - libellés de synchro : « Non liée », « À jour », « Spécification plus récente », « Conflit » ;
    - tri lu et écrit dans les paramètres de route ;
    - après la resynchronisation, suivi de `indexations/current` puis rechargement.

  (T019 au vert)
- [ ] T021 [US1] E2E `e2e/tests/us17-synchro.spec.ts`, tests J1 et J2 :
  - J1 : tri par Statut puis Synchro sur les tickets WireMock ; le tri est conservé après un
    changement de page et un retour ; `aria-sort` ; `expectNoA11yViolations`.
  - J2 : compteur WireMock de `/rest/api/2/search.*` inchangé pendant les tris.
  - Adapter les E2E existants qui visitent la vue (`grep -rn "Tickets Jira" e2e/tests`).
- [ ] T022 [US1] Lancer les gates, puis commiter `feat(jira): vue des tickets triée depuis l'index local`.

**Checkpoint** : US1 livrable seule.

---

## Phase 4: User Story 2 — Synchroniser chaque source automatiquement (Priority: P1)

**Goal** : chaque source a un délai réglable et un bilan visible ; une source échue est relue
seule, en incrémental, sans chevaucher une autre indexation.

**Independent Test** :
1. Donner 15 minutes à une source. En E2E, une minute vaut une seconde.
2. Constater qu'elle seule est relue au tick suivant l'échéance, avec son bilan affiché.
3. Une indexation manuelle repart le délai (quickstart §2).

### Tests US2 (écrits d'abord)

- [ ] T023 [P] [US2] Tests unitaires `BT/domain/sync/SyncIntervalTest.java` et
  `BT/domain/sync/SyncScheduleTest.java` :
  - `fromMinutes` n'accepte que `null`, 15, 30, 60, 240 et 1440 (sinon `INVALID_SYNC_INTERVAL`) ;
  - `duration(minute)` multiplie le nombre de minutes par la durée d'une minute ;
  - `dueAt()` est vide si `OFF`, et vaut `Instant.MIN` si la source n'a jamais tourné ;
  - l'échéance part de la dernière tentative, même en échec.
- [ ] T024 [P] [US2] Tests unitaires `BT/application/knowledge/AutoSyncTest.java` (horloge simulée,
  doubles de `Indexations` et `SourceSyncRepository`) :
  - la source échue la plus ancienne part en INCREMENTAL avec sa seule clé ;
  - si le projet est occupé, rien ne part et aucun double n'est mis en file (FR-010) ;
  - un seul lancement par tick et par projet ;
  - un prérequis manquant (Jira non configuré, jeton absent, accord refusé) est enregistré en
    `ERROR` avec son code, sans appel ;
  - aucune relance avant le délai suivant (FR-012) ;
  - une exception d'une source n'arrête pas le tick pour les autres projets.
- [ ] T025 [P] [US2] Tests unitaires `BT/application/knowledge/ManageSourceSyncTest.java` :
  - une entrée par source indexable, dans l'ordre de `Indexations.sources` ;
  - `setInterval` sur une source inconnue lève `SOURCE_NOT_FOUND` ;
  - `nextRunAt` est recalculé ;
  - `forget` est appelé au retrait d'une source de documentation.
- [ ] T026 [P] [US2] Test unitaire de l'enregistrement des bilans par `Indexations` dans
  `BT/application/knowledge/IndexationsTest.java` (FR-013) :
  - `sourceDone` donne `record(… Success …)` ;
  - `sourceFailed` donne `record(… Failure …)` ;
  - cela vaut aussi pour les indexations manuelles et « Tout réindexer ».
- [ ] T027 [P] [US2] IT `BT/infrastructure/persistence/SourceSyncSqliteRepositoryIT.java`
  (`@QuarkusTest @Isolated`) :
  - aller-retour des colonnes ;
  - `due(now)` ;
  - cascade à la suppression du projet ;
  - `forget`.
- [ ] T028 [P] [US2] IT de câblage `BT/infrastructure/scheduling/SourceSyncJobIT.java` :
  - `@QuarkusTest @Isolated` avec un `QuarkusTestProfile` qui réactive le scheduler
    (`quarkus.scheduler.enabled=true`, `auto-sync.every=1s`) ;
  - une source échue reçoit un bilan ;
  - le `Scheduler` injecté liste la tâche `source-sync`.
- [ ] T029 [P] [US2] IT web `BT/infrastructure/web/SourceSyncResourceIT.java` : `GET` et `PUT`
  (contracts/rest-api.md), 400 et 404, `Origin` exigé sur `PUT`.

### Implémentation US2

- [ ] T030 [P] [US2] Créer le domaine dans `BM/domain/sync/` : `SyncInterval.java`,
  `SyncSchedule.java`, `SourceSyncResult.java` et `SourceSyncState.java` (data-model.md).
  - `SyncInterval` : `OFF(null)`, `M15(15)`, `M30(30)`, `H1(60)`, `H4(240)`, `D1(1440)`.
  - Ajouter les codes `INVALID_SYNC_INTERVAL` et `SOURCE_NOT_FOUND` à `ErrorCode` s'ils manquent.

  (T023 au vert)
- [ ] T031 [US2] Compléter `SourceSyncSqliteRepository` : `schedules`, `due`, `setInterval`,
  `record`, `forget`, chacun `@Transactional` (T027 au vert).
- [ ] T032 [US2] Enregistrer le bilan de chaque source dans `Indexations` : implémenter
  `sourceDone` et `sourceFailed` dans `Indexation`, avec horodatage par `ClockPort`, puis
  `SourceSyncRepository.record`. Fichier : `BM/application/knowledge/Indexations.java` (T026 au
  vert).
- [ ] T033 [US2] Créer `BM/application/knowledge/AutoSync.java`, qui reçoit en constructeur
  `Duration minute` (T024 au vert). Il lit `IndexableSource` pour les prérequis.
- [ ] T034 [US2] Créer `BM/application/knowledge/ManageSourceSync.java` et appeler
  `SourceSyncRepository.forget` dans `ManageDocSources.delete`
  (`BM/application/context/ManageDocSources.java`). (T025 au vert)
- [ ] T035 [US2] Câbler `AutoSync` et `ManageSourceSync` dans `BM/infrastructure/config/UseCaseProducer.java` (`specwriter.knowledge.auto-sync.minute`).
- [ ] T036 [US2] Créer `BM/infrastructure/scheduling/SourceSyncJob.java` :
  - `@Scheduled(identity = "source-sync", every = "${specwriter.knowledge.auto-sync.every}", delayed = "${specwriter.knowledge.auto-sync.delayed}", concurrentExecution = SKIP)`
    plus `@RunOnVirtualThread`, qui appelle `autoSync.tick()` ;
  - consigner au journal chaque lancement et chaque échec, avec la clé de la source, sans contenu ;
  - vérifier la règle ArchUnit `HexagonalArchitectureTest` (paquet `infrastructure.scheduling`).

  (T028 au vert)
- [ ] T037 [US2] Créer `BM/infrastructure/web/SourceSyncResource.java` :
  `GET /api/projects/{projectId}/source-sync` et `PUT …/source-sync/{sourceKey}` (T029 au vert).
- [ ] T038 [P] [US2] Tests Vitest de `FE/features/project-settings/source-sync.component.spec.ts` :
  - une ligne par source : sorte et nom, liste de délais (Désactivée, 15 min, 30 min, 1 h, 4 h,
    1 jour) ;
  - dernière synchronisation : date, puis « n nouveaux, n modifiés, n retirés » ou « Aucun
    changement » ;
  - en échec : « Échec » avec la dernière réussite ;
  - prochaine synchronisation ;
  - annonce `role="status"` après un changement de délai.
- [ ] T039 [US2] Écrire `FE/features/project-settings/source-sync.component.ts`, région
  « Synchronisation des sources ». L'intégrer dans `project-settings.page.ts`, après les sources de
  documentation. Utiliser `subscriptSizing="dynamic"` sur les `mat-form-field`, comme
  `reliability.component.ts`. Ajouter les appels d'API dans `FE/core/`, au même endroit que les
  appels de connaissance existants. (T038 au vert)
- [ ] T040 [US2] E2E `e2e/tests/us17-synchro.spec.ts`, tests S1 à S3 (une minute vaut une seconde
  en `%e2e`) :
  - S1 : régler 15 min sur un dossier SharePoint ; ajouter un fichier ; dans les 30 s, la source
    affiche « 1 nouveau » et l'autre source n'a reçu aucune lecture (compteur WireMock Confluence
    ou Jira). `expectNoA11yViolations`.
  - S2 : « Resynchroniser » met à jour la date de la source.
  - S3 : WireMock Jira en 500, la source Jira affiche « Échec » avec sa dernière réussite, et une
    entrée au journal.
- [ ] T041 [US2] Lancer les gates, puis commiter
  `feat(connaissance): synchronisation automatique par source et dernier bilan`.

**Checkpoint** : US1 et US2 fonctionnent indépendamment.

---

## Phase 5: User Story 3 — Être prévenu des sources mises à jour (Priority: P2)

**Goal** : une notification du navigateur par source changée, une seule quel que soit le nombre
d'onglets, désactivable, qui mène à la connaissance du projet.

**Independent Test** :
1. Activer les notifications et accorder la permission.
2. Changer une source, puis resynchroniser.
3. Constater une seule notification en moins de 10 s, avec deux onglets ouverts.
4. Le clic mène à « Connaissance » (quickstart §3).

### Tests US3 (écrits d'abord)

- [ ] T042 [P] [US3] Test unitaire dans `BT/application/knowledge/IndexationsTest.java` :
  - un bilan `CHANGED` appelle `AppEvents.publish` avec la sorte, le nom, le projet et les
    décomptes ;
  - `UNCHANGED` et `ERROR` ne publient rien (FR-015) ;
  - l'événement ne contient ni titre ni contenu.
- [ ] T043 [P] [US3] IT `BT/infrastructure/web/EventsResourceIT.java` (`@QuarkusTest @Isolated`) :
  - un événement publié arrive sur `GET /api/events` avec `id`, `event: source-updated` et le JSON
    de contracts/events.md ;
  - `Last-Event-ID` renvoie les événements suivants ;
  - 401 sans session ;
  - pas d'événement si la transaction échoue.
- [ ] T044 [P] [US3] IT `BT/infrastructure/web/NotificationSettingsResourceIT.java` : `GET` vaut
  `false` par défaut ; `PUT true` puis `GET` ; `Origin` exigé.
- [ ] T045 [P] [US3] Tests Vitest `FE/core/events/app-events.service.spec.ts`, avec `EventSource`,
  `navigator.locks` et `Notification` simulés :
  - seul le détenteur du verrou `spec-writer-notifications` affiche ;
  - `tag` = identifiant de l'événement ;
  - titre « {Sorte} « {nom} » mise à jour » ;
  - corps sans les zéros ;
  - rien si le réglage est faux ou la permission n'est pas `granted` ;
  - le clic fait `focus`, change le projet courant s'il le faut, puis navigue vers
    `/projet/connaissance`.
- [ ] T046 [P] [US3] Tests Vitest `FE/features/settings/notifications-card.component.spec.ts` :
  - l'activation appelle `requestPermission` ;
  - `granted` enregistre le réglage ;
  - `denied` ne l'enregistre pas et affiche comment accorder la permission ;
  - une permission déjà refusée affiche ce message sans appel ;
  - la désactivation enregistre `false`.

### Implémentation US3

- [ ] T047 [P] [US3] Créer `BM/domain/sync/SourceUpdated.java` et le port `BM/domain/port/AppEvents.java`.
  Publier depuis `Indexations` sur un bilan `CHANGED` (T042 au vert).
- [ ] T048 [US3] Créer `BM/infrastructure/events/CdiAppEvents.java` :
  - `Event<SourceUpdated>.fire` ;
  - les observateurs utilisent `@Observes(during = TransactionPhase.AFTER_SUCCESS)` ;
  - consulter `quarkus_searchDocs` « CDI events transactional observers ».
- [ ] T049 [US3] Créer `BM/infrastructure/web/EventsResource.java` (T043 au vert) :
  - observateur qui garde les 50 derniers événements ;
  - `BroadcastProcessor<SourceUpdated>` ;
  - `GET /api/events` en `Multi<OutboundSseEvent>`, construit avec le `Sse` injecté (`id`,
    `name`, `data` JSON), en `@Produces(SERVER_SENT_EVENTS)` ;
  - reprise par `@HeaderParam("Last-Event-ID")` ;
  - commentaire de maintien toutes les 25 s ;
  - consulter `quarkus_searchDocs` « SSE Multi OutboundSseEvent ».
- [ ] T050 [US3] Ajouter `notificationsEnabled()` et `setNotificationsEnabled(boolean)` à
  `BM/domain/port/SettingsRepository.java` et à son adaptateur (colonne `notify_source_updates`).
  - Créer `BM/application/settings/ManageNotifications.java`.
  - Créer `BM/infrastructure/web/NotificationSettingsResource.java`, `GET` et `PUT`
    `/api/settings/notifications`.
  - Câbler dans `UseCaseProducer`.

  (T044 au vert)
- [ ] T051 [US3] Créer `FE/core/events/app-events.service.ts`, démarré par le shell à l'ouverture
  de session (T045 au vert) :
  - `EventSource('/api/events')` ;
  - verrou Web Locks ;
  - `new Notification(title, { body, tag })`, puis `onclick`.
- [ ] T052 [US3] Créer `FE/features/settings/notifications-card.component.ts` (carte
  « Notifications », `data-testid="notifications-card"`). L'intégrer dans
  `FE/features/settings/settings.page.ts` (T046 au vert).
- [ ] T053 [US3] E2E `e2e/tests/us17-synchro.spec.ts`, tests N1 à N3 :
  - contexte avec `permissions: ['notifications']` ;
  - `addInitScript` qui remplace `window.Notification` par un espion qui compte titre, corps et
    `tag` ;
  - N1 : activer, modifier le fichier SharePoint, resynchroniser ; une notification nommant la
    source arrive dans les 10 s.
  - N2 : deux pages ouvertes, une seule notification au total ; resynchronisation sans
    changement, aucune notification.
  - N3 : désactiver, aucune notification. `expectNoA11yViolations` sur Configuration.
- [ ] T054 [US3] Lancer les gates, puis commiter
  `feat(notifications): notification du navigateur des sources mises à jour`.

**Checkpoint** : les trois stories fonctionnent.

---

## Phase 6: Polish & Cross-Cutting

- [ ] T055 [P] Test de sécurité dans `e2e/tests/us17-synchro.spec.ts` (test X1). Le journal
  (`/api/journal`, `app.log`) et le flux `/api/events` ne contiennent :
  - ni titre de ticket ;
  - ni contenu de page ;
  - ni jeton.
- [ ] T056 [P] Documentation :
  - `CLAUDE.md` : ligne « feature en cours » sur la 017 ;
  - `CONTRIBUTING.md` : tâche planifiée et flux d'événements, si une section les attend ;
  - `README.md` : réglage du délai et notifications.
- [ ] T057 Parcourir `quickstart.md` en dev mode, puis noter les écarts dans le quickstart.
- [ ] T058 Gates complètes, `ci/check-eval.sh` inchangé, puis commit
  `docs: guide et contexte de la feature 017`.

---

## Dependencies & Execution Order

- Setup (T001-T002), puis Foundational (T003-T008).
- US1 (T009-T022) ne dépend que de T002, et de T016 qu'elle crée elle-même. Elle peut commencer
  en parallèle de la phase 2.
- US2 (T023-T041) dépend de la phase 2 et de T016.
- US3 (T042-T054) dépend de T032 : les bilans enregistrés par `Indexations`.
- Polish après les stories voulues.

### Dans chaque story

Tests rouges, puis domaine, ports, adaptateurs, cas d'usage, ressources web, front, E2E, gates,
commit.

### Parallel Opportunities

- Phase 2 : T003 et T004 en parallèle de T005.
- US1 : T009, T010, T011, T012 et T019 ensemble, puis T013 et T014 ensemble.
- US2 : T023 à T029 et T038 ensemble, puis T030.
- US3 : T042 à T046 ensemble, puis T047. T051 et T052 (front) en parallèle de T048 à T050
  (backend).

```text
# US1, tests en parallèle :
T009 IssueSortTest · T010 ListProjectIssuesTest · T011 SqliteListedIssuesIT · T012 ProjectIssuesResourceIT · T019 jira-issues.page.spec
```

## Implementation Strategy

1. **MVP** : Setup, puis US1 (T001, T002, T009-T022), avec un arrêt pour valider la vue Jira
   triée.
2. Phase 2 puis US2 : délais, bilans et tâche planifiée. Arrêt pour validation (quickstart §2).
3. US3 : notifications, puis le polish.

Chaque phase se termine par ses gates et son commit.
