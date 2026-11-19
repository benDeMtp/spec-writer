---
description: "Tâches de la feature 018 : assistant de premier lancement, visite guidée, guide d'installation depuis les sources"
---

# Tasks: Assistant de premier lancement et visite guidée

**Input**: `specs/018-premier-lancement-visite/`. Les documents de conception :

- plan.md ;
- spec.md ;
- research.md, R1 à R8 ;
- data-model.md ;
- contracts/rest-api.md et contracts/tour.md ;
- quickstart.md, Q1 à Q6.

**Tests**: obligatoires (constitution III, TDD). Chaque test est écrit et vu échouer avant le code.

- JaCoCo ne compte pas les `@QuarkusTest`. `domain/onboarding` et `application/onboarding` sont
  donc couverts par des tests unitaires, avec des doubles de `OnboardingRepository`,
  `SettingsRepository`, `ConsentRepository` et `ClockPort`.
- Les règles ArchUnit se lancent sous Maven.
- Tout nouveau `@QuarkusTest` porte `@Isolated` (`QuarkusTestIsolationTest`).

**Évaluation LLM**: non concernée. Aucun prompt ni code de `infrastructure/llm` n'est touché, et
`ci/check-eval.sh` doit garder son empreinte.

**Cadre**: on reste dans le framework. La visite utilise le CDK d'Angular déjà présent (`Overlay`,
`A11yModule`), sans bibliothèque de visite (R6).

**Organisation**:
- La phase 2 extrait trois composants sans changer le rendu. Elle bloque US1.
- US1 (assistant) est le MVP.
- US2 (visite) ne dépend que de l'entrée « Visite guidée » et de la proposition en fin
  d'assistant (T030).
- US3 (guide d'installation) est indépendante.

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

- [ ] T001 Configuration de l'ouverture automatique dans `BR/application.properties` :
  - `specwriter.onboarding.auto-open=true` par défaut ;
  - `%e2e.specwriter.onboarding.auto-open=false` (R7) ;
  - `%test` garde le défaut. Les IT qui en dépendent fixent la valeur par un `TestProfile`.
- [ ] T002 Écrire `BR/db/migration/V29__onboarding.sql` (data-model.md) :
  - table `onboarding` : `id INTEGER PRIMARY KEY CHECK (id = 1)` ;
  - `current_step TEXT NOT NULL CHECK (current_step IN ('WELCOME','COPILOT','JIRA','CONFLUENCE','PROJECT','SOURCES','INDEXATION','DONE'))` ;
  - `completed_at TEXT` (nullable) et `updated_at TEXT NOT NULL` ;
  - table `onboarding_step` : `step TEXT PRIMARY KEY CHECK (step IN ('COPILOT','JIRA','CONFLUENCE','PROJECT','SOURCES','INDEXATION'))` et `state TEXT NOT NULL CHECK (state IN ('DONE','SKIPPED'))` ;
  - insérer la ligne `(1, 'WELCOME', NULL, now)` ;
  - puis la passer à `DONE` avec `completed_at = now` si l'installation est déjà utilisée (R1) :
    `copilot_settings.connected_login IS NOT NULL`, une ligne dans `jira_settings` ou
    `confluence_settings`, une ligne dans `session`, `conversation` ou `project_doc_source`, ou
    `(SELECT count(*) FROM project) > 1`.

---

## Phase 2: Foundational (bloque US1)

Trois composants sortent de leurs pages pour être partagés avec l'assistant (R3, R4). Les pages
gardent exactement le même rendu : mêmes libellés, mêmes `data-testid`, mêmes classes CSS.

- [X] T003 [P] Extraire les sources de documentation de `FE/features/project-settings/project-settings.page.ts`
  vers `FE/features/project-settings/doc-sources.component.ts` (`sw-doc-sources`) :
  - entrée `projectId` et `sources` ; sortie `changed` ;
  - liste, ajout (Antora en lien ou en dossier, SharePoint, racine Confluence si Confluence est
    configuré), retrait, aperçu, exclusions, niveau d'une racine Confluence ;
  - spec Vitest `doc-sources.component.spec.ts`, avec les cas repris de
    `project-settings.page.spec.ts`.
- [X] T004 [P] Extraire le modèle d'architecture de la même page vers
  `FE/features/project-settings/architecture-model.component.ts` (`sw-architecture-model`) :
  - entrée `projectId` ; sortie `changed` ;
  - spec Vitest `architecture-model.component.spec.ts`.
- [X] T005 [P] Extraire la progression de l'indexation de `FE/features/project-knowledge/knowledge-panel.component.ts`
  vers `FE/features/project-knowledge/indexation-progress.component.ts` (`sw-indexation-progress`) :
  - entrée `projectId` ; sortie `finished` ;
  - l'interrogation périodique, les libellés d'étape, `<progress>` et la durée sont repris tels
    quels ;
  - spec Vitest `indexation-progress.component.spec.ts`.
- [X] T006 Brancher les trois composants dans `project-settings.page.ts` et `knowledge-panel.component.ts`,
  puis retirer le code devenu inutile.
  - Les specs existantes des deux pages restent vertes sans retouche de leurs attentes.
  - Les E2E restent verts : `us15-navigation`, `us16-*`, `us17-synchro` et ceux de la
    connaissance.
- [ ] T007 Gates complètes, puis commit `refactor(front): sources, modèle d'architecture et progression de l'indexation en composants`.

**Checkpoint** : comportement inchangé, trois composants réutilisables.

---

## Phase 3: User Story 1 — Être mené jusqu'à un projet indexé (Priority: P1) 🎯 MVP

**Goal**: une installation vierge s'ouvre sur l'assistant, qui mène de Copilot à l'indexation et
se reprend après une interruption.

**Independent Test**: quickstart Q1 à Q4.

### Tests US1 (écrits d'abord)

- [ ] T008 [P] [US1] `BT/domain/onboarding/OnboardingTest.java` :
  - ordre de `next()` et `previous()` ;
  - `skip(COPILOT)` lève `ONBOARDING_COPILOT_REQUIRED` ;
  - `goTo` au-delà de `COPILOT` non fait lève la même erreur ;
  - `goTo` en arrière est toujours permis et garde les états ;
  - `complete` exige `COPILOT = DONE`, et un second `complete` garde la première date ;
  - `markDone` d'une étape passée la remet à `DONE`.
- [ ] T009 [P] [US1] `BT/application/onboarding/ManageOnboardingTest.java`, avec des dépôts en mémoire :
  - `state()` donne `autoOpen` vrai seulement si l'assistant n'est pas terminé et que
    l'ouverture automatique est active ;
  - `markDone(COPILOT)` refusé si Copilot n'est pas connecté, sans modèle, ou sans accord
    Copilot (`ConsentRepository.latest(COPILOT)`), et accepté quand les trois sont réunis ;
  - avancement de `currentStep` après `markDone` et après `skip` ;
  - `complete()` horodaté par `ClockPort`.
- [ ] T010 [P] [US1] `BT/infrastructure/persistence/MigrationV29IT.java`, sur le modèle de `MigrationV27IT` :
  - base vierge → `WELCOME`, non terminée ;
  - une session → terminée ;
  - Copilot connecté → terminée ;
  - Jira configuré → terminée ;
  - deux projets → terminée.
- [ ] T011 [P] [US1] `BT/infrastructure/persistence/OnboardingSqliteRepositoryIT.java` : aller-retour de
  la progression (étape courante, états, `completed_at`). Annoter `@Isolated`, et remettre la
  ligne dans son état d'origine en fin de test, car la base est partagée.
- [ ] T012 [P] [US1] `BT/infrastructure/web/OnboardingResourceIT.java` (contracts/rest-api.md) :
  - forme de `GET /api/onboarding` ;
  - `PUT steps/COPILOT {SKIPPED}` → 409 `ONBOARDING_COPILOT_REQUIRED` ;
  - `PUT steps/WELCOME` → 400 `ONBOARDING_UNKNOWN_STEP` ;
  - `PUT steps/JIRA {SKIPPED}` → 200 et étape suivante ;
  - `PUT current` ;
  - `POST complete` idempotent ;
  - requête sans cookie → refusée ;
  - le journal ne contient que des noms d'étapes.
- [ ] T013 [P] [US1] Specs Vitest du front :
  - `FE/core/onboarding/onboarding.service.spec.ts` : navigation vers `/premiers-pas/<étape>`
    si `autoOpen`, rien sinon, rien si l'URL est déjà dans l'assistant ;
  - `FE/features/onboarding/onboarding.page.spec.ts` : progression affichée, Suivant inactif à
    l'étape Copilot non faite, Passer, Précédent, annonce du changement d'étape ;
  - une spec par étape dans `FE/features/onboarding/steps/` : clé Jira proposée seulement si
    Jira est connecté, racine Confluence seulement si Confluence est connecté, indexation
    courante suivie ou lancée, explication quand il n'y a rien à indexer (US1-8).
- [ ] T014 [US1] `e2e/tests/support/fresh-instance.ts` : démarre le jar sur le port 47697 avec un
  dossier de données neuf, `-Dquarkus.profile=e2e` et `-Dspecwriter.onboarding.auto-open=true`,
  et donne l'URL de lancement. L'arrête à la fin du fichier de test.
- [ ] T015 [US1] `e2e/tests/us18-premiers-pas.spec.ts` :
  - P1 : installation vierge, parcours complet (Q1). Le projet par défaut est renommé, aucun
    second projet.
  - P2 : tout passer (Q2).
  - P3 : reprise après arrêt et relance de l'instance (Q3).
  - P4 : instance partagée, pas d'assistant ; relance depuis Configuration à l'accueil, avec
    l'état réel des étapes (Q4).
  - Contrôle `axe` à chaque étape.

### Implémentation US1

- [ ] T016 [P] [US1] Domaine `BM/domain/onboarding/` : `OnboardingStep.java` (ordre, `required`),
  `StepState.java` (`TODO`, `DONE`, `SKIPPED`) et `Onboarding.java`, un record immuable avec les
  règles de data-model.md.
- [ ] T017 [P] [US1] Ajouter dans `BM/domain/error/ErrorCode.java` :
  - `ONBOARDING_COPILOT_REQUIRED` (409, « Connectez GitHub Copilot pour continuer : c'est la seule étape obligatoire. ») ;
  - `ONBOARDING_UNKNOWN_STEP` (400, « Étape de l'assistant inconnue. »).
- [ ] T018 [US1] Port `BM/domain/port/OnboardingRepository.java` (`load`, `save`) et adaptateur
  `BM/infrastructure/persistence/onboarding/OnboardingSqliteRepository.java`. Les nulls sont
  écrits comme littéraux dans les requêtes natives, comme `SourceSyncSqliteRepository`.
- [ ] T019 [US1] Cas d'usage `BM/application/onboarding/ManageOnboarding.java`
  (`OnboardingRepository`, `SettingsRepository`, `ConsentRepository`, `ClockPort`,
  `boolean autoOpen`), câblé dans `BM/infrastructure/config/UseCaseProducer.java` depuis
  `specwriter.onboarding.auto-open`.
- [ ] T020 [US1] Ressource `BM/infrastructure/web/OnboardingResource.java` :
  - `GET /api/onboarding` ;
  - `PUT /api/onboarding/steps/{step}` ;
  - `PUT /api/onboarding/current` ;
  - `POST /api/onboarding/complete` ;
  - consignation au journal sans valeur saisie.
- [ ] T021 [P] [US1] `FE/core/onboarding/onboarding.api.ts` et ses modèles (contracts/rest-api.md).
- [ ] T022 [US1] `FE/core/onboarding/onboarding.service.ts` :
  - lecture au démarrage, après l'échange du jeton de lancement, appelée depuis `FE/app.ts` ;
  - navigation vers l'étape courante si `autoOpen` ;
  - relecture sur `visibilitychange` (R5).
- [ ] T023 [US1] Route `/premiers-pas/:etape` dans `FE/app.routes.ts` (chargée à la demande, titre
  « Premiers pas »). Une étape inconnue mène à l'accueil.
- [ ] T024 [US1] Cadre `FE/features/onboarding/onboarding.page.ts` :
  - liste des étapes avec leur état (à faire, faite, passée) ;
  - focus sur le titre et annonce `LiveAnnouncer` à chaque changement d'étape (FR-011) ;
  - Précédent ; Suivant, inactif tant que Copilot n'est pas fait ; Passer, absent à l'étape
    Copilot.
- [ ] T025 [P] [US1] `FE/features/onboarding/steps/welcome.step.ts` : les étapes, celles qui sont
  facultatives, et une durée indicative de 10 minutes.
- [ ] T026 [P] [US1] Étapes de connexion dans `FE/features/onboarding/steps/` :
  - `copilot.step.ts` place `sw-copilot-card` et marque l'étape faite quand le compte est
    connecté, le modèle choisi et l'accord donné ;
  - `jira.step.ts` et `confluence.step.ts` placent les cartes et marquent l'étape faite après un
    test réussi.
- [ ] T027 [P] [US1] `FE/features/onboarding/steps/project.step.ts` : `sw-project-identity-form` sur
  le projet courant (R2), clé Jira seulement si Jira est connecté.
- [ ] T028 [P] [US1] `FE/features/onboarding/steps/sources.step.ts` : `sw-doc-sources` (T003) et
  `sw-architecture-model` (T004). L'étape est faite si au moins une source ou un modèle est
  enregistré.
- [ ] T029 [P] [US1] `FE/features/onboarding/steps/indexation.step.ts` (R4) :
  - suit l'indexation courante avec `sw-indexation-progress` (T005) ;
  - sinon la lance en incrémental si le projet a une clé Jira ou une source ;
  - sinon explique qu'il n'y a rien à indexer ;
  - « Ouvrir la conversation » est actif pendant l'indexation.
- [ ] T030 [US1] `FE/features/onboarding/steps/done.step.ts` :
  - `POST complete` ;
  - « Ouvrir la conversation » mène à `/` ;
  - « Faire la visite guidée » et « Plus tard » ; le premier bouton est branché en T042.
- [ ] T031 [US1] Bouton « Relancer l'assistant de premier lancement » dans
  `FE/features/settings/settings.page.ts`, qui mène à `/premiers-pas/accueil`. Mettre à jour
  `settings.page.spec.ts`.
- [ ] T032 [US1] Gates complètes. Cocher T008-T032, puis commit
  `feat(premiers-pas): assistant de premier lancement jusqu'à l'indexation du projet`.

**Checkpoint** : Q1 à Q4 passent ; MVP livrable.

---

## Phase 4: User Story 2 — Visiter l'outil dans l'ordre d'usage (Priority: P2)

**Goal**: une visite de 11 arrêts (contracts/tour.md), lancée depuis le menu ou en fin
d'assistant, sans aucune écriture.

**Independent Test**: quickstart Q5.

### Tests US2 (écrits d'abord)

- [ ] T033 [P] [US2] `FE/core/tour/tour.service.spec.ts` :
  - ordre des 11 arrêts ;
  - Précédent, Suivant, Terminer ;
  - Échap et Quitter ramènent à l'URL de départ ;
  - repli quand la cible est absente (FR-015) ;
  - spécification la plus récente choisie par lecture seule ;
  - aucune requête autre que `GET` (`HttpTestingController`) ;
  - arrêt maintenu si `LeaveGuard` refuse la navigation.
- [ ] T034 [P] [US2] `FE/core/tour/tour-bubble.component.spec.ts` :
  - titre, texte et « Arrêt n sur 11 » ;
  - focus piégé dans la bulle ;
  - annonce `LiveAnnouncer` ;
  - boutons accessibles.
- [ ] T035 [P] [US2] `FE/core/tour/tour-stops.spec.ts` : chaque cible `data-tour` déclarée se
  retrouve dans un gabarit du front (lecture des sources par `import.meta.glob` ou par une liste
  tenue à jour).
- [ ] T036 [US2] `e2e/tests/us18-visite.spec.ts` :
  - V1 : visite complète au clavier depuis le menu ; chaque arrêt désigne un élément visible ;
    contrôle `axe` à chaque arrêt ;
  - V2 : projet sans spécification, les arrêts 4 à 6 utilisent le texte de remplacement ;
  - V3 : Échap à l'arrêt 7, retour à l'écran de départ ;
  - V4 : aucune requête `POST`, `PUT`, `PATCH` ou `DELETE` pendant la visite
    (`page.on('request')`) ;
  - V5 : visite proposée en fin d'assistant, sur l'instance neuve de T014.

### Implémentation US2

- [ ] T037 [US2] Poser les attributs `data-tour` des 11 arrêts (contracts/tour.md) dans les gabarits
  existants : conversation, Spécifications, spécification, discussion, tâches, Tickets Jira,
  connaissance du projet, réglages du projet, Configuration (Avancé, Accès des agents).
- [ ] T038 [US2] `FE/core/tour/tour-stops.ts` : les 11 arrêts, avec leur route ou la fonction qui la
  calcule, leur cible, leurs textes et leur repli. Deux phrases au plus par bulle, sans donnée de
  l'utilisateur.
- [ ] T039 [US2] `FE/core/tour/tour-bubble.component.ts` :
  - `Overlay` avec `FlexibleConnectedPositionStrategy`, repli au-dessus, en dessous puis centré ;
  - `cdkTrapFocus` ;
  - contour de la cible et fond assombri sans clic (R6).
- [ ] T040 [US2] `FE/core/tour/tour.service.ts` :
  - démarrage en mémorisant l'URL de départ ;
  - navigation par le `Router`, attente de la cible (`MutationObserver` avec délai borné), puis
    `scrollIntoView` ;
  - affichage de la bulle ;
  - Échap et Quitter ;
  - fin avec retour à l'URL de départ.
- [ ] T041 [US2] Entrée « Visite guidée » dans le bloc « Application » de
  `FE/features/shell/main-nav.component.ts`. C'est un bouton, pas un lien. Mettre à jour
  `main-nav.component.spec.ts` et `nav.spec.ts`.
- [ ] T042 [US2] Brancher « Faire la visite guidée » de `done.step.ts` (T030) sur `TourService`.
- [ ] T043 [US2] Gates complètes. Cocher T033-T043, puis commit
  `feat(visite): visite guidée de l'outil dans l'ordre d'usage`.

**Checkpoint** : Q5 passe.

---

## Phase 5: User Story 3 — Installer l'outil depuis les sources sans aide (Priority: P3)

**Goal**: un nouvel utilisateur installe et lance l'outil avec le seul README (R8).

**Independent Test**: quickstart Q6.

- [ ] T044 [US3] Écrire la section « Installer depuis les sources » de `README.md`, avant
  « Installation » :
  - prérequis avec leur version minimale et la commande qui la vérifie : JDK 25, Node 22,
    Copilot CLI 1.0.105 ;
  - clonage, construction et lancement ;
  - adresse où l'outil s'ouvre ;
  - renvoi vers l'assistant de premier lancement ;
  - mise à jour (`git pull`, reconstruction, relance) sans perte de données (FR-019, FR-021).
  Reprendre les commandes de `CONTRIBUTING.md` et les vérifier.
- [ ] T045 [US3] Ajouter à la même section les difficultés connues, décrites par leur symptôme
  visible (FR-020) :
  - jar qui s'arrête au démarrage avec un JDK 21 ;
  - proxy d'entreprise (`HTTPS_PROXY`, certificat) ;
  - Copilot CLI introuvable ou trop ancien, avec un renvoi vers « Où spec-writer cherche le
    Copilot CLI ».
- [ ] T046 [US3] Réécrire la section « Premier lancement » de `README.md` pour l'assistant et la
  visite (FR-022). La section « Installation » renvoie désormais à « Installer depuis les
  sources » tant qu'il n'y a pas d'installeur.
- [ ] T047 [US3] Vérifier le guide sur un clone neuf, dans un dossier temporaire, avec un dossier de
  données neuf (Q6), et corriger chaque écart. Laisser au mainteneur la vérification par un
  collègue (SC-005), notée dans quickstart.md.
- [ ] T048 [US3] Commit `docs(readme): installer depuis les sources, premier lancement guidé`.

---

## Phase 6: Polish & Cross-Cutting

- [ ] T049 [P] Ajouter un test de sécurité à `OnboardingResourceIT` et à `e2e/tests/us18-premiers-pas.spec.ts` :
  après le parcours P1, le journal (`/api/journal`, `app.log`) ne contient ni l'adresse Jira,
  ni le jeton, ni le nom du projet saisi dans l'assistant.
- [ ] T050 [P] Documentation :
  - `CLAUDE.md` : la ligne « feature en cours » passe à la 018 ; ajouter dans les pièges
    l'ouverture automatique coupée en `%e2e` et la seconde instance sur le port 47697 ;
  - `CONTRIBUTING.md` : comment ajouter un arrêt à la visite (`tour-stops.ts`, `data-tour`).
- [ ] T051 Parcourir `quickstart.md` (Q1 à Q5) sur le jar et noter les écarts dans le quickstart.
- [ ] T052 Gates complètes, `ci/check-eval.sh` inchangé, puis commit
  `docs: guide et contexte de la feature 018`.

---

## Dependencies & Execution Order

- Setup (T001-T002), puis Foundational (T003-T007).
- US1 (T008-T032) dépend de T002 et de la phase 2.
- US2 (T033-T043) dépend de T030 pour la proposition en fin d'assistant (T042) et de T014 pour
  V5. Le reste peut commencer dès la phase 2.
- US3 (T044-T048) est indépendante. Rédiger T046 après US1 et US2 pour décrire ce qui existe.
- Polish après les stories.

### Dans chaque story

Tests rouges, puis domaine, port, adaptateur, cas d'usage, ressource, front, E2E, gates, commit.

### Parallel Opportunities

- Phase 2 : T003, T004 et T005 ensemble.
- US1 : T008 à T013 ensemble, puis T016 et T017 ensemble. Les étapes T025 à T029 sont toutes en
  parallèle une fois T024 fait.
- US2 : T033, T034 et T035 ensemble. T037 en parallèle de T038.
- US3 : T044 et T045 en parallèle de US1 et de US2.

```text
# US1, tests en parallèle :
T008 OnboardingTest · T009 ManageOnboardingTest · T010 MigrationV29IT · T011 OnboardingSqliteRepositoryIT · T012 OnboardingResourceIT · T013 specs front
# US1, étapes en parallèle après le cadre T024 :
T025 accueil · T026 connexions · T027 projet · T028 sources · T029 indexation
```

## Implementation Strategy

1. **Fondations** : extraction des composants (T003-T007), commit isolé. Le rendu ne change pas.
2. **MVP** : Setup puis US1 (T001, T002, T008-T032), avec un arrêt pour valider l'assistant
   (Q1 à Q4).
3. US2 : la visite (Q5).
4. US3 : le README, vérifié sur un clone neuf (Q6). Le mainteneur le fait ensuite relire par son
   collègue.
5. Polish.

Chaque phase se termine par ses gates et son commit.
