---
description: "Tâches de la feature 019 : cycle de vie de l'outil installé (quitter, instance unique, réouverture)"
---

# Tasks: Cycle de vie de l'outil installé (lancer, rouvrir, quitter)

**Input**: `specs/019-cycle-de-vie-outil/`. Les documents de conception :

- plan.md ;
- spec.md ;
- research.md, R1 à R8 ;
- data-model.md ;
- contracts/rest-api.md, contracts/instance-socket.md et contracts/ui.md ;
- quickstart.md, Q1 à Q7.

**Tests**: obligatoires (constitution III, TDD). Chaque test est écrit et vu échouer avant le code.

- JaCoCo ne compte pas les `@QuarkusTest`. `application/lifecycle` et la logique de
  `infrastructure/platform` sont donc couverts par des tests unitaires, avec des doubles de
  `ClockPort`, `ExitPort`, des registres et de `Desktop`.
- Les règles ArchUnit se lancent sous Maven.
- Tout nouveau `@QuarkusTest` porte `@Isolated`.

**Évaluation LLM**: non concernée. Aucun prompt ni code de `infrastructure/llm` n'est touché, et
`ci/check-eval.sh` doit garder son empreinte.

**Organisation**:
- La phase 2 pose ce qui sert à toutes les US : point d'entrée, inventaire des travaux, ouverture
  d'un onglet.
- US1 (Quitter) et US2 (instance unique) sont toutes deux P1 et indépendantes une fois la phase 2
  faite.
- US3 (Dock) réutilise l'ouverture d'onglet de la phase 2 et l'arrêt de l'US1.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: parallélisable (fichiers différents, aucune dépendance sur une tâche non finie).
- Abréviations des chemins :
  - `B/` = `backend/src/main/java/fr/kawamind/` ;
  - `BT/` = `backend/src/test/java/fr/kawamind/` ;
  - `F/` = `frontend/src/app/`.

---

## Phase 1: Setup

- [X] T001 Spike R3 : ajouter une classe `B/infrastructure/platform/SpecWriterMain.java` annotée
  `@QuarkusMain`, dont le `main(String[])` statique écrit une ligne dans `System.err` puis appelle
  `Quarkus.run(args)`. Vérifier les trois cas suivants et consigner le résultat dans research.md
  (R3, « Résultat du spike ») :
  1. le jar fast-jar (`java -jar backend/target/quarkus-app/quarkus-run.jar`) passe par ce `main` ;
  2. l'uber-jar du profil `dist` (`./mvnw -Pdist package -DskipTests`, sans JReleaser si besoin)
     passe aussi par ce `main` ;
  3. `quarkus:dev` ne passe pas par ce `main`.

  Si l'un des trois n'est pas conforme, s'arrêter et revoir R3 avant la suite.
- [X] T002 Ajouter `quarkus.shutdown.timeout=4s` dans
  `backend/src/main/resources/application.properties`, avec un commentaire qui renvoie à R4.

---

## Phase 2: Foundational (bloque toutes les US)

### Inventaire des travaux en cours

- [X] T003 [P] Test `BT/application/knowledge/IndexationsRunningTest.java` :
  - `running()` liste les instantanés au statut `RUNNING` avec leur projet ;
  - `cancelAll()` demande l'annulation de chacun.
- [X] T004 Implémenter `running()` et `cancelAll()` dans `B/application/knowledge/Indexations.java`.
- [X] T005 [P] Test `BT/application/conversation/TurnEventsOpenTurnsTest.java` :
  - `open(turnId, kind, conversationId)` enregistre la nature du tour (`AGENT_REPLY` ou
    `REVISION`) ;
  - `openTurns()` liste les tours non finis ;
  - `cancelAll()` les annule tous ;
  - un tour fini n'est plus listé.
- [X] T006 Implémenter la nature du tour, `openTurns()` et `cancelAll()` dans
  `B/application/conversation/TurnEvents.java`. Passer la nature à `open` depuis
  `B/application/conversation/Converse.java` (`AGENT_REPLY`) et
  `B/application/conversation/ProposeRevision.java` (`REVISION`). Le comportement de l'existant est
  inchangé.
- [X] T007 [P] Test `BT/application/lifecycle/LongWorkTrackerTest.java` : `begin(kind, projectId)`
  rend un jeton, `end(jeton)` le retire, `current()` liste ceux en cours, et deux générations
  simultanées sont comptées deux fois.
- [X] T008 Implémenter `B/application/lifecycle/LongWorkTracker.java`.
- [X] T009 [P] Test `BT/application/lifecycle/RunningWorkTest.java` :
  - l'inventaire agrège les trois sources ;
  - `kind` vaut `INDEXATION`, `AGENT_REPLY`, `REVISION` ou `GENERATION` ;
  - `projectName` et `conversationTitle` sont résolus par `ProjectRepository` et le dépôt des
    conversations, sans aucun texte saisi ;
  - l'inventaire est vide quand rien ne tourne.
- [X] T010 Implémenter `B/application/lifecycle/RunningWork.java` (record `Item(kind, projectName,
  conversationTitle)`) et le câbler dans `B/infrastructure/config/UseCaseProducer.java`.

### Ouverture d'un onglet (partagée par le canal et le Dock)

- [X] T011 [P] Test `BT/infrastructure/web/TabOpenerTest.java` :
  - un nouveau jeton à usage unique est émis à chaque ouverture (`LaunchTokenService.issue()`) ;
  - le navigateur configuré est ouvert (`ManageBrowser` simulé) ;
  - si `specwriter.open-browser=false` ou si l'ouverture échoue, le résultat est `PRINT` avec
    l'adresse, sinon `OPENED` ;
  - l'adresse n'est jamais journalisée.
- [X] T012 Implémenter `B/infrastructure/web/TabOpener.java` (bean, à côté de `BrowserOpener` dont il reprend
  `launchUrl`).

**Checkpoint** : `./mvnw -B test -DskipDependencyCheck -Dtest='Indexations*,TurnEvents*,LongWork*,RunningWork*,TabOpener*' -Dsurefire.failIfNoSpecifiedTests=false`
au vert.

---

## Phase 3: User Story 1 - Quitter spec-writer depuis l'outil (Priority: P1) 🎯 MVP

**Goal** : « Quitter spec-writer » arrête l'outil proprement, avec une confirmation qui nomme les
travaux en cours, et l'onglet affiche l'écran d'arrêt.

**Independent Test** : quickstart Q1 et Q2.

### Tests (écrits d'abord, vus échouer)

- [X] T013 [P] [US1] Test `BT/application/lifecycle/GracefulStopTest.java` (horloge simulée) :
  - `stop()` annule les indexations et les tours, attend que l'inventaire soit vide **au plus
    5 s**, puis appelle `ExitPort.exit(0)` ;
  - un second `stop()` est sans effet (état `ARRÊT DEMANDÉ` atteint une fois) ;
  - `onShutdown()` (crochet de la JVM) annule et attend sans rappeler `ExitPort` ;
  - le journal ne contient que `Arrêt demandé depuis l'interface (n travaux interrompus)`.
- [X] T014 [P] [US1] Test `BT/infrastructure/web/LongWorkFilterTest.java` : une requête sur une
  route `@LongWork(GENERATION)` est comptée pendant son traitement, puis retirée, même en cas
  d'exception.
- [X] T015 [P] [US1] Test `@QuarkusTest @Isolated`
  `BT/infrastructure/web/ApplicationResourceIT.java`, contrat de contracts/rest-api.md :
  - `GET /api/application` → `os` vaut `MACOS`, `WINDOWS` ou `LINUX`, `installation` vaut
    `SOURCES`, `work` est vide ;
  - `POST /api/application/quit` avec `{}` → `202` avec `{"stopping":true}` et `ExitPort` simulé
    appelé ; un second appel → `202` ;
  - sans cookie → `403` (refus existant de `LocalSecurityFilter`) ; sans `Content-Type` JSON → `415`.
- [X] T016 [P] [US1] Test `F/core/lifecycle/lifecycle.store.spec.ts` :
  - `load()` garde `os` et `installation` ;
  - `probe()` : une erreur réseau → `stopped`, toute réponse HTTP (même `403`) → inchangé,
    `200` → `running` ;
  - `stopped` est définitif.
- [X] T017 [P] [US1] Test `F/features/shell/app-stopped.component.spec.ts` :
  - le `h1` « spec-writer est arrêté » reçoit le focus et est annoncé ;
  - la consigne suit les cinq cas de contracts/ui.md ;
  - le texte se termine par « Vous pouvez fermer cet onglet. ».
- [X] T018 [P] [US1] Test dans `F/features/shell/main-nav.component.spec.ts` :
  - « Quitter spec-writer » est dans le bloc Application, après « Visite guidée » ;
  - sans travail en cours, la confirmation porte « Quitter » et « Annuler » ;
  - avec des travaux, elle les liste avec leur devenir et porte « Quitter quand même » ;
  - « Annuler » n'appelle pas `quit` ;
  - la confirmation appelle `quit` puis passe le store à `stopped`.
- [X] T019 [P] [US1] Tests dans `F/core/events/app-events.service.spec.ts` et
  `F/core/http/problem.interceptor.spec.ts` : une coupure du flux, ou une erreur de statut 0,
  déclenche `LifecycleStore.probe()`.

### Implémentation

- [X] T020 [US1] Port `B/application/lifecycle/ExitPort.java`, puis
  `B/application/lifecycle/GracefulStop.java` (attente par `ClockPort`), câblés dans
  `UseCaseProducer`.
- [X] T021 [US1] `B/infrastructure/platform/QuarkusExit.java` : implémente `ExitPort` par
  `Quarkus.asyncExit(code)`. `B/infrastructure/platform/GracefulStopOnShutdown.java` :
  `@Observes ShutdownEvent` → `GracefulStop.onShutdown()`.
- [X] T022 [US1] `B/infrastructure/web/LongWork.java` (`@NameBinding`, valeur `kind`) et
  `B/infrastructure/web/LongWorkFilter.java`. Annoter les routes de génération :
  - `POST` de `B/infrastructure/web/VersionResource.java` ;
  - `POST /specifications` de `B/infrastructure/web/ConversationResource.java` ;
  - toute autre route qui appelle `GenerateSpecification`, à relever par `grep`.
- [X] T023 [US1] `B/infrastructure/web/ApplicationResource.java` : `GET /api/application` et
  `POST /api/application/quit`, qui répond `202` puis appelle `GracefulStop.stop()` hors de la
  requête (exécuteur géré).
- [X] T024 [P] [US1] `F/core/lifecycle/application.api.ts` (types `ApplicationState`,
  `WorkItem`) et `F/core/lifecycle/lifecycle.store.ts`.
- [X] T025 [P] [US1] `F/features/shell/app-stopped.component.ts`, textes de contracts/ui.md.
  L'afficher dans `F/app.ts` à la place du shell quand le store est `stopped`, avant
  `sw-session-lost`.
- [X] T026 [US1] Dans `F/features/shell/main-nav.component.ts`, ajouter « Quitter spec-writer »
  avec `ConfirmDialogComponent` (`F/shared/ui/confirm-dialog.component.ts`). Le focus initial va
  sur « Annuler », et Échap annule. Libellés des travaux : « Indexation du projet <p> »,
  « Réponse de l'agent dans <conversation> », « Proposition de version dans <conversation> »,
  « Génération d'une spécification du projet <p> ».
- [X] T027 [US1] Brancher `LifecycleStore.probe()` dans `F/core/events/app-events.service.ts`
  (erreur du flux) et `F/core/http/problem.interceptor.ts` (statut 0).
- [X] T028 [US1] E2E `e2e/tests/us19-cycle-de-vie.spec.ts`, sur une seconde instance
  (`e2e/tests/support/fresh-instance.ts`, port 47697) :
  - **Q1** : Quitter → écran d'arrêt, processus terminé avec le code 0 en moins de 10 s, un
    second onglet ouvert passe à l'écran d'arrêt ;
  - **Q2** : Quitter pendant une indexation et une réponse rejouée lentement → la confirmation
    les nomme ; après relance, la réponse est marquée incomplète ;
  - `axe` sur la confirmation et sur l'écran d'arrêt.

  Ajouter au support le code de sortie du processus et la relance sur le même dossier de données.

**Checkpoint** : US1 utilisable seule. Le MVP est livrable.

---

## Phase 4: User Story 2 - Une seule instance, que chaque lancement rouvre (Priority: P1)

**Goal** : un second lancement sur le même dossier de données ouvre un onglet de l'instance en
marche et se termine. Un arrêt brutal ne bloque jamais le démarrage suivant.

**Independent Test** : quickstart Q3, Q4 et Q7.

### Tests (écrits d'abord, vus échouer)

- [X] T029 [P] [US2] Test `BT/infrastructure/platform/InstanceLockTest.java`, avec un processus
  enfant lancé par `ProcessBuilder` sur `java -cp <classpath de test>` :
  - si l'enfant tient le verrou, `tryAcquire()` → occupé ;
  - si l'enfant est tué (`destroyForcibly`), `tryAcquire()` → obtenu (FR-012) ;
  - un dossier en lecture seule → `IOException` distincte de « occupé » (cas « dossier illisible »
    de R1).
- [X] T030 [P] [US2] Test `BT/infrastructure/platform/InstanceChannelTest.java`, socket dans un
  dossier temporaire, contrat de contracts/instance-socket.md :
  - `open` → la réponse de `TabOpener` (`OPENED` ou `PRINT` avec l'adresse) ;
  - une `version` autre que 1, ou une commande inconnue → `UNSUPPORTED` ;
  - une ligne de plus de **4 Kio** → connexion fermée ;
  - une seule demande par connexion ;
  - pendant l'arrêt → `STOPPING` ;
  - un `instance.sock` orphelin est remplacé au démarrage ;
  - à l'arrêt, le fichier est supprimé.
- [X] T031 [P] [US2] Test `BT/infrastructure/platform/SecondLaunchTest.java` (horloge et canal
  simulés) :
  - `OPENED` → message `spec-writer est déjà lancé : un nouvel onglet a été ouvert.` et code 0 ;
  - `PRINT` → adresse affichée et code 0 ;
  - pas de canal, puis verrou libéré avant 15 s → « démarrer » ;
  - pas de canal pendant **15 s** → message « ne répond pas » et code 3 ;
  - pas de réponse en **5 s** → code 3 ;
  - `STOPPING` → nouvel essai ;
  - `UNSUPPORTED` → code 3.
- [X] T032 [P] [US2] Test `BT/infrastructure/platform/LauncherMessagesTest.java` (exécuteur de
  commandes simulé) :
  - toujours la console ;
  - une boîte sous macOS et Windows ;
  - sous Linux, dans l'ordre `zenity`, `kdialog`, `notify-send`, sinon la console seule ;
  - aucune boîte pour `OPENED` ou `PRINT`.

### Implémentation

- [X] T033 [US2] `B/infrastructure/platform/InstanceLock.java` (`FileChannel.tryLock` sur
  `instance.lock`, référence statique gardée toute la vie du processus) et `held()` statique.
- [X] T034 [US2] `B/infrastructure/platform/InstanceChannel.java` : bean `@Startup`, ouvert
  seulement si `InstanceLock.held()`. Il écoute sur `instance.sock` (droits du dossier), avec un
  thread virtuel par connexion. Il est fermé, et son fichier supprimé, sur `ShutdownEvent` avant
  la fin du processus. Un chemin trop long est journalisé et l'instance démarre sans canal (R2).
- [X] T035 [US2] `B/infrastructure/platform/SecondLaunch.java` et
  `B/infrastructure/platform/LauncherMessages.java` (R5). Les messages sont en français et
  disent comment arrêter l'outil bloqué, selon l'OS : Moniteur d'activité, Gestionnaire des
  tâches, ou `pkill -f spec-writer`.
- [X] T036 [US2] Compléter `B/infrastructure/platform/SpecWriterMain.java` :
  1. résoudre le dossier de données par `UserDirectories.fromSystem()` ;
  2. `InstanceLock.tryAcquire()` ;
  3. si le verrou est occupé, `SecondLaunch.run()` puis `System.exit(code)`, ou démarrer si
     `SecondLaunch` rend « démarrer » ;
  4. sinon `Quarkus.run(args)`.

  Retirer la ligne de diagnostic du spike T001.
- [X] T037 [US2] Vérifier que `fr.kawamind.mcp.McpBridge` et `dist/launchers/spec-writer-mcp.properties`
  n'utilisent pas `SpecWriterMain` (FR-016). Ajouter un test ArchUnit dans
  `BT/architecture/HexagonalArchitectureTest.java` : aucune classe de `fr.kawamind.mcp` ne dépend
  de `SpecWriterMain`, `InstanceLock` ni `SecondLaunch`.
- [X] T038 [US2] Vérifier que `dist/scripts/spec-writer.sh` et `dist/scripts/spec-writer.cmd`
  propagent le code de sortie : `exec` pour le `.sh`, `exit /b %ERRORLEVEL%` pour le `.cmd`.
  Corriger si besoin.
- [X] T039 [US2] E2E dans `e2e/tests/us19-cycle-de-vie.spec.ts`, sur la seconde instance :
  - **Q3** : second lancement sur le même dossier, avec `-Dspecwriter.open-browser=false
    -Dspecwriter.print-launch-url=true` → code 0 en moins de 5 s, adresse affichée qui ouvre une
    session dans un nouvel onglet Playwright, toujours une seule instance (le port n'a pas changé) ;
  - lancements simultanés sur un dossier neuf → une seule instance ;
  - **Q4** : `SIGKILL`, puis relance → démarrage normal.

**Checkpoint** : la suite E2E complète passe, dont `us18-premiers-pas` (FR-018).

---

## Phase 5: User Story 3 - Rouvrir l'outil depuis le Dock de macOS (Priority: P2)

**Goal** : sous macOS, un clic sur le Dock ou une réouverture depuis le Finder ouvre un onglet
connecté. ⌘Q arrête proprement, avec confirmation si un travail est en cours.

**Independent Test** : quickstart Q5 (manuel, sur le `.dmg` construit en local).

- [X] T040 [P] [US3] Test `BT/infrastructure/platform/MacDesktopIntegrationTest.java`, avec une
  abstraction de `Desktop` (interface `DesktopEvents` : `onQuit`, `onReopen`) :
  - `onReopen` appelle `TabOpener.open()` ;
  - `onQuit` sans travail → `performQuit` ;
  - avec des travaux → une confirmation (abstraction de boîte) qui les nomme, puis `performQuit`
    ou `cancelQuit` selon la réponse ;
  - rien n'est enregistré hors macOS, en mode headless ou sans `InstanceLock.held()`.
- [X] T041 [US3] Implémenter `B/infrastructure/platform/MacDesktopIntegration.java` (bean
  `@Startup`, `Desktop.setQuitHandler`, `Desktop.addAppEventListener(AppReopenedListener)`), et
  l'adaptateur réel de `DesktopEvents` sur `java.awt.Desktop`.
- [ ] T042 [US3] Vérification manuelle Q5 sur un `.dmg` construit par `./mvnw -Pdist package` :
  clic sur le Dock, Launchpad, Quitter du Dock pendant une indexation, ⌘Q. Consigner le résultat
  dans quickstart.md (Q5, date). Faire aussi Q6 (instance suspendue) sous macOS.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T043 [P] Mettre à jour `F/features/shell/session-lost.component.ts` et sa spec : consigne de
  relance par le raccourci, plus le Dock sous macOS (FR-019).
- [X] T044 [P] README.md :
  - **Lancement** : le second lancement rouvre un onglet ; sous macOS, le Dock aussi.
  - **Arrêt** : Menu › Quitter spec-writer, ⌘Q, Ctrl+C depuis les sources. Fermer le terminal
    arrête toujours l'outil.
  - **Outil bloqué** : comment l'arrêter.
  - **Sections touchées** : « Installer depuis les sources », « Installation », « Premier
    lancement » (paragraphe sur l'outil déjà lancé) et « Le menu » (FR-020).
- [X] T045 [P] CLAUDE.md :
  - feature en cours : 019 ;
  - pièges : `SpecWriterMain` n'est pas utilisé en dev mode ni en `@QuarkusTest` ;
    `instance.lock` et `instance.sock` sont dans le dossier de données ; une seconde instance E2E
    a besoin d'un dossier à elle.
- [X] T046 Gates avant commit :
  1. supprimer `backend/target/jacoco.exec` et `backend/target/test-data` ;
  2. `./mvnw -B verify -DskipDependencyCheck` (JaCoCo, ArchUnit, SpotBugs, PMD, front) ;
  3. `npm --prefix e2e test` sur le jar construit ;
  4. `ci/check-eval.sh`, avec une empreinte inchangée.
- [X] T047 Dérouler quickstart Q1, Q3, Q4 et Q7 à la main sur le jar, avec `dist/scripts/spec-writer.sh`.
  Cocher les tâches dans ce fichier.

---

## Dependencies & Execution Order

- **Phase 1** (T001 avant tout) : le spike conditionne R3, donc US2.
- **Phase 2** : T003 à T012. Elle bloque US1 (inventaire) et US2 et US3 (`TabOpener`).
- **US1** (T013 à T028) et **US2** (T029 à T039) sont indépendantes l'une de l'autre après la
  phase 2.
- **US3** (T040 à T042) dépend de T012 (`TabOpener`) et de T020 et T021 (arrêt par le crochet de
  la JVM). Elle utilise `InstanceLock.held()` de T033.
- **Phase 6** après les US. T046 avant chaque commit.

Au sein d'une US : tests, puis application, puis infrastructure, puis front, puis E2E.

## Parallel Opportunities

- **Phase 2** : T003, T005, T007, T009 et T011 en parallèle, puis leurs implémentations.
- **US1** : T013 à T019 en parallèle. T024 et T025 (front) en parallèle de T020 à T023
  (backend).
- **US2** : T029 à T032 en parallèle.
- **US1 et US2** : leurs phases peuvent avancer en parallèle, car leurs fichiers sont distincts.
  Seul `e2e/tests/us19-cycle-de-vie.spec.ts` est commun : y ajouter les scénarios l'un après
  l'autre.
- **Phase 6** : T043, T044 et T045 en parallèle.

## Implementation Strategy

1. **MVP** : phases 1 et 2, puis US1. Quitter fonctionne partout, avec l'écran d'arrêt. Commit
   après les gates.
2. **US2** : instance unique et réouverture par un second lancement (Windows, Linux, sources).
   Commit.
3. **US3** : Dock de macOS, vérifié à la main sur le `.dmg`. Commit.
4. **Polish** : README, CLAUDE.md, quickstart. Commit `docs`.

Commits en Conventional Commits, en français, avec les fichiers stagés explicitement.
