---
description: "Tâches de la feature 012 : navigation par projet et réglages de l'application"
---

# Tasks: Navigation par projet et réglages de l'application

**Input**: `specs/012-navigation-projet/` (plan.md, spec.md, research.md, data-model.md,
contracts/api.md, contracts/ui.md, quickstart.md)

**Tests**: obligatoires (constitution III, TDD) : chaque test est écrit et vu échouer avant le
code. `domain` et `application` sont couverts par des tests unitaires, car JaCoCo ne compte pas les
`@QuarkusTest`.

**Organization**: les cinq histoires de la spec.

- US1 : savoir sur quel projet on travaille, avec le menu en deux blocs et les titres (MVP)
- US2 : régler un projet en un seul endroit, avec les pages Réglages et Connaissance
- US3 : une page d'un autre projet bascule le projet courant
- US4 : une configuration de l'application plus claire, avec la clé par défaut facultative et le
  test de connexion sur le projet courant
- US5 : une session perdue est dite clairement

**Prérequis** : la branche part de `feature/015-discussion-specification` (011, PR #13). La rebaser
sur `main` dès la fusion.

## Format: `[ID] [P?] [Story] Description`

- **[P]** : parallélisable (fichiers différents, aucune dépendance sur une tâche non finie).
- Abréviations des chemins :
  - `BM/` : `backend/src/main/java/dev/specwriter/`
  - `BT/` : `backend/src/test/java/dev/specwriter/`
  - `BR/` : `backend/src/main/resources/`
  - `FA/` : `frontend/src/app/`

---

## Phase 1: Setup

- [X] T001 Créer les dossiers et fichiers vides de la feature, pour que les imports se résolvent :
  - `FA/features/shell/`, `FA/core/navigation/` ;
  - `FA/features/project-settings/`, `FA/features/project-knowledge/`.

  Vérifier que `npm --prefix frontend run lint` passe sur la branche avant toute modification
  (point de départ propre).

---

## Phase 2: Foundational (bloque toutes les histoires)

- [X] T002 [P] Test puis service `LeaveGuard` (`FA/core/navigation/leave-guard.ts`, `.spec.ts`),
  modèle dans data-model.md :
  - `register(check: () => boolean | Promise<boolean>): () => void` enregistre la vérification de
    la page et renvoie la fonction qui la retire ;
  - `confirm(): Promise<boolean>` appelle la vérification enregistrée, ou renvoie `true` s'il n'y
    en a aucune ;
  - une seule vérification active à la fois : la dernière enregistrée.

  Tests : sans vérification ; vérification qui accepte ; qui refuse ; retirée.
- [X] T003 Brancher `LeaveGuard` sur la spécification, sans changer son comportement :
  - `FA/features/specification/specification.page.ts` enregistre `canLeave()` dans `ngOnInit` et
    le retire dans `ngOnDestroy` ;
  - dans `FA/app.routes.ts`, la garde `canDeactivate` de `sessions/:id/versions/:n` devient
    `() => inject(LeaveGuard).confirm()`, et l'import `type SpecificationPage` disparaît.

  Les tests existants de `specification.page.spec.ts` et la D5 de `us14-discussion` doivent
  rester verts.
- [X] T004 [P] Test puis service `ProjectFollower` (`FA/core/projects/project-follower.ts`,
  `.spec.ts`), research.md R3 :
  - `notice: Signal<string | null>` ;
  - `follow(projectId)` :
    - ne fait rien si `projectId === ProjectsStore.currentId()` ;
    - sinon appelle `ProjectsStore.select(projectId)` et pose `notice` à
      `Projet courant : <nom>` ;
  - `clear()` ;
  - effacement automatique après 6 s (`setTimeout`, testé avec les faux minuteurs de Vitest).

  Tests : même projet ; autre projet ; erreur de `select` (aucune annonce, l'erreur remonte au
  bandeau).

**Checkpoint** : les services partagés existent, et la 011 fonctionne comme avant.

---

## Phase 3: User Story 1 - Savoir sur quel projet on travaille (Priority: P1) 🎯 MVP

**Goal**: le menu présente deux blocs, « Projet » et « Application », et les pages du projet le
nomment.

**Independent Test**: avec deux projets, parcourir les entrées du menu (quickstart 1 à 4).

- [X] T005 [P] [US1] Test puis fonction pure `activeEntry(path)` (`FA/features/shell/nav.ts`,
  `nav.spec.ts`) :
  - type `NavEntry` (data-model.md) ;
  - les préfixes du tableau de research.md R2, la requête et l'ancre ignorées ;
  - `/` exact pour Conversation ;
  - `/projets/...` est Projets, mais `/projet/...` ne l'est pas ;
  - adresse inconnue : `null`.
- [X] T006 [US1] Test puis composant `sw-main-nav` (`FA/features/shell/main-nav.component.ts`,
  `.spec.ts`), selon contracts/ui.md, section Menu :
  - **Structure** :
    - un `<nav aria-label="Navigation principale">` ;
    - deux `div role="group"`, avec `aria-labelledby` vers un titre visible « Projet » et
      « Application » ;
    - le sélecteur (`sw-project-switcher`) dans le groupe Projet.
  - **Entrées** :
    - Conversation `/`, Spécifications `/sessions`, Tickets Jira `/jira`, Connaissance du
      projet `/projet/connaissance`, Réglages du projet `/projet/reglages` ;
    - Projets, Configuration, Journal.
  - **Entrée active** : `aria-current="page"` et la classe `active` sur l'entrée renvoyée par
    `activeEntry`, recalculée sur `NavigationEnd`.
  - **Tickets Jira** : la clé `effectiveJiraProjectKey` du projet courant s'affiche dans un
    `<span class="key">`. Le nom accessible est « Tickets Jira, projet FACT », et il n'y a pas de
    clé sans clé effective.
  - **Petit écran** (moins de 48 rem) :
    - le bouton « Menu » (`aria-expanded`, `aria-controls="menu-panel"`) ouvre et ferme le
      panneau ;
    - le panneau se ferme sur `NavigationEnd` et sur Échap, avec le focus rendu au bouton ;
    - le bouton est masqué en CSS au-dessus de 48 rem.

  Tests : groupes, clé (présente ou absente), entrée active sur `/jira/FACT-1` et
  `/sessions/x/versions/1`, ouverture et fermeture du menu.
- [X] T007 [US1] Shell `FA/app.ts` :
  - remplacer la liste de liens et `conversation()` par `<sw-main-nav />` ;
  - garder le lien d'évitement, la gestion du focus après navigation, le bandeau et
    `router-outlet` ;
  - styles : la règle `ul { grid-auto-flow: column; overflow-x: auto }` disparaît (remplacée par
    le panneau de T006).

  Mettre à jour `FA/app.spec.ts`.
- [X] T008 [US1] Sélecteur `FA/features/projects/project-switcher.component.ts` :
  - libellé « Projet » au lieu de « Projet courant » ;
  - le style de bloc du composant passe en titre de groupe, avec la marge revue ;
  - une dernière option « Gérer les projets » mène à `/projets` (cas limite d'un seul projet).

  Tests mis à jour.
- [X] T009 [P] [US1] Test puis `ProjectTitleStrategy`
  (`FA/core/navigation/project-title.strategy.ts`, `.spec.ts`) :
  - une route qui porte `data.scope === 'project'` reçoit le titre
    `<titre> · <projet> · spec-writer` ;
  - les autres gardent `<titre> · spec-writer` ;
  - le titre est recalculé quand `ProjectsStore.current` change (un `effect` mémorise le dernier
    snapshot).

  Fournie dans `FA/app.config.ts` (`{ provide: TitleStrategy, useClass: ProjectTitleStrategy }`).
- [X] T010 [US1] Routes `FA/app.routes.ts` :
  - les titres passent sans « · spec-writer » (la stratégie l'ajoute) ;
  - `data: { scope: 'project' }` sur `''`, `conversations/:cid`, `sessions`, `nouveau`,
    `sessions/:id`, `sessions/:id/versions/:n`, `jira`, `jira/:key`.
- [X] T011 [P] [US1] Test puis composant `sw-project-heading`
  (`FA/shared/ui/project-heading.component.ts`, `.spec.ts`) :
  - entrée `label` ;
  - rend `<h1>{{ label }} · {{ nom du projet courant }}</h1>` ;
  - sans projet chargé, `label` seul ;
  - styles de `h1` repris des pages existantes (`--sw-font-doc`, `--sw-step-3`).
- [X] T012 [US1] Utiliser `sw-project-heading` :
  - `FA/features/sessions/sessions.page.ts` (« Spécifications ») ;
  - `FA/features/jira-issues/jira-issues.page.ts` (« Tickets Jira ») ;
  - `FA/features/conversation/conversation.page.ts` : accueil, sans conversation ouverte
    (« Conversation »), à la place du titre actuel.

  Mettre à jour les tests de ces pages.
- [X] T013 [US1] E2E `e2e/tests/us15-navigation.spec.ts`, scénarios 1 à 4 du quickstart :
  - deux groupes, clé « FACT » ;
  - `h1` et titre de l'onglet après changement de projet ;
  - `aria-current` sur `/jira/FACT-1` et une spécification ;
  - menu replié en 600 px.

  axe sur le menu déplié et replié. Le projet « Recette » est créé dans `beforeAll` et supprimé
  dans `afterAll` (DELETE avec `sessions=DELETE`).
- [X] T014 [US1] Mettre à jour les E2E qui cliquent dans le menu ou lisent « Projet courant » :
  - `e2e/tests/us7-projects.spec.ts` ;
  - `e2e/tests/us9-deletion.spec.ts` ;
  - `e2e/tests/us13-conversation.spec.ts` ;
  - `e2e/tests/us14-discussion.spec.ts` (D5 : le lien « Conversation » de la navigation
    principale).

**Checkpoint** : US1 est démontrable seule. Les gates passent, puis commit
`feat(navigation): menu en deux blocs et projet nommé dans les titres`.

---

## Phase 4: User Story 2 - Régler un projet en un seul endroit (Priority: P1)

**Goal**: une page « Réglages du projet » et une page « Connaissance du projet », sur le projet
courant. Les messages Jira mènent aux réglages, et l'import annonce son projet.

**Independent Test**: depuis le menu, modifier la clé Jira et une source sur une même page, puis
voir les compteurs dans Connaissance (quickstart 5 à 10).

- [ ] T015 [P] [US2] Extraire le formulaire d'identité de `FA/features/projects/projects.page.ts`
  dans `FA/features/projects/project-identity-form.component.ts` (`.spec.ts`) :
  - champs Nom, Description et Clé Jira (`id` configurable, `jira` sur la page de réglages) ;
  - avertissement `jiraKeyWarning` en `role="alert"` ;
  - sorties `saved(ProjectInput)` et `dirty(boolean)`.

  La création de projet dans `projects.page` utilise le composant, sans changement visible.
- [ ] T016 [US2] Test puis page `FA/features/project-settings/project-settings.page.ts`
  (`.spec.ts`), contracts/ui.md, section Réglages du projet :
  - `sw-project-heading label="Réglages du projet"` ;
  - quatre sections avec `h2`, dans cet ordre :
    1. Identité (`project-identity-form`, enregistrement par `ProjectsApi.update`, puis
       `ProjectsStore.load()`) ;
    2. Fiche générale ;
    3. Sources de documentation ;
    4. Modèle d'architecture ;

    les trois dernières sont déplacées telles quelles de `project-context.page.ts`, avec leurs
    méthodes et leur `announce` ;
  - le projet affiché est `ProjectsStore.current()`, relu quand il change ;
  - lien « Voir la connaissance du projet » vers `/projet/connaissance` ;
  - ancre `#jira` : au chargement, si le fragment est `jira`, le champ Clé Jira reçoit le focus
    (`ActivatedRoute.fragment`) ;
  - modifications non enregistrées :
    - c'est le cas d'une identité modifiée, d'une saisie de source ou d'architecture non vide, ou
      de l'éditeur de fiche ouvert ;
    - la page enregistre auprès de `LeaveGuard` une vérification qui ouvre `ConfirmDialog` :
      titre « Quitter sans enregistrer ? », boutons « Rester » et « Quitter sans enregistrer ».

  Déplacer `sheet-editor.component.ts` dans `FA/features/project-settings/`. Tests : sections,
  enregistrement de l'identité, focus `#jira`, dialogue.
- [ ] T017 [US2] Test puis page `FA/features/project-knowledge/project-knowledge.page.ts`
  (`.spec.ts`) :
  - `sw-project-heading label="Connaissance du projet"` ;
  - `sw-knowledge-panel` déplacé avec son test dans `FA/features/project-knowledge/`, sur le projet
    courant ;
  - liens « ADR du projet » (`/projet/connaissance/adr`) et « Réglages du projet ».

  Déplacer `adrs.page.ts` et son test dans le même dossier. La page lit le projet courant au lieu
  de `:id`, son `h1` passe par `sw-project-heading label="ADR"`, et le lien de retour mène à
  `/projet/connaissance`.
- [ ] T018 [US2] Test puis garde `followProjectGuard` (`FA/core/navigation/follow-project.guard.ts`,
  `.spec.ts`), research.md R1 :
  - `canActivate` sur `projets/:id/contexte` et `projets/:id/contexte/adr` ;
  - `ProjectsStore.load()` si la liste est vide ;
  - `:id` inconnu : `UrlTree` vers `/projets`, et le bandeau affiche « Ce projet n'existe plus. »
    (`ProblemStore.show`, code `NOT_FOUND`) ;
  - sinon `ProjectFollower.follow(id)`, puis `UrlTree` vers `/projet/connaissance` ou
    `/projet/connaissance/adr`.
- [ ] T019 [US2] Routes `FA/app.routes.ts` :
  - `projet/reglages` (`canDeactivate: () => inject(LeaveGuard).confirm()`) ;
  - `projet/connaissance` ;
  - `projet/connaissance/adr` ;
  - les trois avec `data.scope = 'project'` ;
  - les deux anciennes adresses passent par `followProjectGuard` (composant factice jamais
    affiché).

  Supprimer `FA/features/project-context/project-context.page.ts` et son test, une fois leurs cas
  repris en T016 et T017.
- [ ] T020 [US2] Page Projets `FA/features/projects/projects.page.ts` :
  - « Modifier » rend le projet courant (`ProjectFollower.follow`), puis navigue vers
    `/projet/reglages` ;
  - le lien « Contexte » devient « Connaissance » (même comportement : courant, puis
    `/projet/connaissance`) ;
  - l'édition en ligne est retirée, la création reste.

  Tests mis à jour.
- [ ] T021 [US2] Tickets Jira `FA/features/jira-issues/jira-issues.page.ts`, les trois états de
  contracts/ui.md :
  - clé du projet ;
  - clé par défaut : « (clé par défaut de l'application) », si `jiraProjectKey` est null et
    `effectiveJiraProjectKey` ne l'est pas ;
  - aucune clé avec Jira configuré (`SettingsStore.jira()?.enabled`) : « Aucun projet Jira n'est
    associé à <projet>. » et un lien « Renseigner la clé Jira du projet » vers `/projet/reglages`,
    fragment `jira` ;
  - Jira non configuré : « Jira n'est pas configuré. » et « Ouvrir la configuration ».

  Tests des quatre cas.
- [ ] T022 [P] [US2] Bandeau `FA/shared/ui/problem-banner.component.ts` (`.spec.ts`) :
  - l'action `OPEN_PROJECT_SETTINGS` affiche « Ouvrir les réglages du projet » et navigue vers
    `/projet/reglages#jira` ;
  - ajouter la valeur au type `ProblemAction` de `FA/core/http/problem.ts`.
- [ ] T023 [P] [US2] Ticket Jira `FA/features/search/item-detail.page.ts` (`.spec.ts`) : le texte
  « La spécification sera ajoutée au projet <nom courant>. » s'affiche avant le bouton « Discuter
  avec l'IA ».
- [ ] T024 [US2] Dialogue de création de ticket
  (`FA/features/jira/create-ticket-dialog.component.ts`) et `FA/features/specification/specification.page.ts` :
  - le message d'indisponibilité lié à la clé du projet propose « Ouvrir les réglages du projet » ;
  - le serveur, le jeton ou l'accord gardent la configuration (FR-010).

  Tests mis à jour.
- [ ] T025 [US2] E2E `e2e/tests/us15-navigation.spec.ts`, scénarios 5 à 10 du quickstart, avec axe
  sur les réglages et la connaissance. Mettre à jour `e2e/tests/us8-project-context.spec.ts` (le
  renommer en `us8-project-settings.spec.ts`) et `e2e/tests/us7-projects.spec.ts` : nouvelles
  adresses, « Connaissance », « Modifier ».

**Checkpoint** : US1 et US2 sont livrées. Les gates passent, puis commit
`feat(projet): réglages et connaissance du projet courant`.

---

## Phase 5: User Story 3 - Une page d'un autre projet bascule le projet courant (Priority: P2)

**Goal**: le menu et la page montrent toujours le même projet.

**Independent Test**: avec Recette courant, ouvrir une spécification du projet par défaut
(quickstart 11, 12).

- [ ] T026 [P] [US3] Test puis composant `sw-project-notice`
  (`FA/features/shell/project-notice.component.ts`, `.spec.ts`) :
  - `<p role="status" aria-live="polite">` qui affiche `ProjectFollower.notice()` ;
  - effacé par `ProjectFollower.clear()` au `NavigationStart` suivant.

  À placer dans `FA/app.ts`, en tête de `main`.
- [ ] T027 [US3] Appeler `ProjectFollower.follow(projectId)` après le chargement de la ressource :
  - `FA/features/specification/specification.page.ts` et
    `FA/features/questionnaire/questionnaire.page.ts`, avec `session.projectId` ;
  - `FA/features/conversation/conversation.page.ts`, avec `detail.projectId` : le retour à
    l'accueil quand `detail.projectId !== projectId` est remplacé par la bascule ;
  - sur 404, aucune bascule.

  Tests : autre projet (bascule), même projet (aucun appel), 404.
- [ ] T028 [US3] Sélecteur `FA/features/projects/project-switcher.component.ts` (research.md R4) :
  - **avant `select`** : `await LeaveGuard.confirm()`. Sur un refus, la valeur du `mat-select`
    revient à `currentId` (signal local réinitialisé) ;
  - **après `select`** : si `activeEntry` est sur une page adressée par identifiant
    (`/sessions/…`, `/conversations/…`, `/jira/…`), navigation vers la liste de la même entrée
    (`/sessions`, `/`, `/jira`).

  Tests : refus, acceptation sur une spécification (navigation vers `/sessions`), acceptation sur
  `/sessions` (aucune navigation).
- [ ] T029 [US3] E2E `e2e/tests/us15-navigation.spec.ts`, scénarios 11 et 12 du quickstart. Le
  scénario 12 réutilise `proposeThroughDiscussion` (`e2e/tests/support/discussion.ts`).

**Checkpoint** : les gates passent, puis commit
`feat(navigation): bascule du projet courant à l'ouverture d'une page d'un autre projet`.

---

## Phase 6: User Story 4 - Une configuration de l'application plus claire (Priority: P2)

**Goal**: les connexions sont séparées des réglages avancés, la portée est écrite, l'état est
visible dans le menu, la clé par défaut est facultative et le test porte sur le projet courant.

**Independent Test**: Copilot connecté et Jira non configuré, vérifier les pastilles. Enregistrer
Jira sans clé par défaut, puis tester (quickstart 13, 14).

- [ ] T030 [US4] Migration `BR/db/migration/V23__jira_default_key_optional.sql` (data-model.md) :
  - reconstruire `jira_settings` à l'identique de V2, sauf `default_project_key TEXT`, nullable
    (« `TEXT NOT NULL` → `TEXT` ») ;
  - `INSERT INTO jira_settings_new SELECT * FROM jira_settings`, `DROP TABLE`, `ALTER TABLE …
    RENAME TO jira_settings`.

  IT `BT/infrastructure/persistence/settings/JiraSettingsMigrationIT.java` : ligne conservée,
  `NULL` accepté.
- [ ] T031 [P] [US4] Test puis domaine `BM/domain/settings/JiraSettings.java`
  (`BT/domain/settings/JiraSettingsTest.java`) :
  - `create` accepte une clé `null`, vide ou blanche, enregistrée à `null` ;
  - une clé non vide garde la règle `[A-Z][A-Z0-9_]+` et son message.

  `BM/infrastructure/persistence/settings/JiraSettingsEntity.java` : colonne nullable.
- [ ] T032 [P] [US4] Test puis codes d'erreur :
  - `BM/domain/error/ErrorAction.java` gagne `OPEN_PROJECT_SETTINGS` ;
  - `BM/domain/error/ErrorCode.java` gagne `JIRA_PROJECT_MISSING(409, "Aucun projet Jira n'est
    associé à ce projet. Renseignez sa clé Jira dans les réglages du projet.",
    ErrorAction.OPEN_PROJECT_SETTINGS)`.

  Vérifier que `ProblemMapper` sérialise l'action, et mettre à jour le test d'énumération s'il
  existe.
- [ ] T033 [US4] Test puis `BM/application/jira/JiraAccess.java`
  (`BT/application/jira/JiraAccessTest.java`) :
  - `Target.projectKey` peut être `null` ;
  - `Target.requireProject()` lève `JIRA_PROJECT_MISSING` ;
  - l'appeler dans `BM/application/jira/TicketForm.java`, `PreviewJiraTicket.java` et
    `CreateJiraTicket.java` avant tout appel à Jira ;
  - `BM/application/search/UnifiedSearch.java` omet la part Jira quand `projectKey` est `null`.

  Tests unitaires pour chacun : aucun appel à `IssueTracker` sans clé.
- [ ] T034 [US4] Test puis `BM/application/settings/TestJiraConnection.java`
  (`BT/application/settings/TestJiraConnectionTest.java`), research.md R8 :
  - le constructeur reçoit `ProjectRepository` (câblage dans
    `BM/infrastructure/config/UseCaseProducer.java`) ;
  - la clé est `JiraAccess`-équivalente : `CurrentProject.resolve(projects).effectiveJiraProject(jira.defaultProjectKey())` ;
  - `Outcome` gagne `projectKey` ;
  - sans clé : version et utilisateur seulement, puis `JiraTestResult.OK_NO_PROJECT`, nouveau, avec
    le message de data-model.md.

  Tests : clé du projet, clé par défaut, aucune clé, projet introuvable sur la clé du projet.
- [ ] T035 [US4] Web `BM/infrastructure/web/SettingsResource.java` :
  - `TestView` gagne `projectKey` ;
  - `JiraRequest.defaultProjectKey` est facultatif.

  IT `BT/infrastructure/web/SettingsResourceIT.java` : enregistrement sans clé, test sur la clé du
  projet courant (WireMock). IT de création de ticket sans clé effective : `409
  JIRA_PROJECT_MISSING`, action `OPEN_PROJECT_SETTINGS`. Les lancer sous Maven, car WireMock
  échoue en dev mode. Mettre à jour `specs/002-projets-contexte-mcp/contracts/openapi.yaml`
  (contracts/api.md).
- [ ] T036 [US4] Carte Jira `FA/features/settings/jira-card.component.ts` (`.spec.ts`) :
  - libellé « Clé Jira par défaut (facultatif) », avec l'aide « Utilisée pour les projets sans clé
    Jira » ;
  - validation front : vide accepté ;
  - après un test, le résultat « Projet Jira vérifié : <clé> », ou le message `OK_NO_PROJECT` ;
  - `FA/core/settings/settings.model.ts` : `defaultProjectKey: string | null`, `projectKey` dans le
    résultat du test.
- [ ] T037 [US4] Page `FA/features/settings/settings.page.ts` (`.spec.ts`), research.md R10 :
  - section `h2` « Connexions » (Copilot, Jira), puis `h2` « Avancé » (modèle de recherche, accès
    des agents) ;
  - phrases de portée sous le titre de `embedding-card.component.ts` et de
    `agent-access-card.component.ts`, mot pour mot comme dans contracts/ui.md.
- [ ] T038 [US4] Pastilles d'état dans `FA/features/shell/main-nav.component.ts` (`.spec.ts`),
  research.md R9 :
  - le shell charge `SettingsStore` au démarrage (`load()` avec erreur ignorée) ;
  - sous Configuration : « Copilot : connecté » ou « Copilot : non connecté », et « Jira :
    configuré » ou « Jira : non configuré » (`enabled` et jeton présent) ;
  - pastille colorée en `aria-hidden="true"`, contraste AA du texte ;
  - relecture du store quand `ProblemStore` reçoit un code `COPILOT_*` ou `JIRA_*`.
- [ ] T039 [US4] E2E `e2e/tests/us15-navigation.spec.ts`, scénarios 13 et 14 du quickstart, avec axe
  sur la configuration. Mettre à jour :
  - `e2e/tests/us1-settings.spec.ts` (sections, clé facultative) ;
  - `e2e/tests/us5-create-ticket.spec.ts`, `e2e/tests/us6-search-edit.spec.ts`,
    `e2e/tests/us10-jira-paging.spec.ts`, `e2e/tests/us11-agents.spec.ts` et
    `e2e/tests/network-egress.spec.ts`, là où ils lisent la clé par défaut ou « Ouvrir la
    configuration ».

**Checkpoint** : les gates passent, puis commit
`feat(configuration): connexions et réglages avancés, clé Jira par défaut facultative`.

---

## Phase 7: User Story 5 - Une session perdue est dite clairement (Priority: P3)

**Goal**: sans session, un écran dédié remplace le cadre vide.

**Independent Test**: ouvrir l'outil sans cookie (quickstart 15).

- [ ] T040 [US5] Test puis serveur, research.md R11 :
  - `BM/domain/error/ErrorCode.java` gagne `SESSION_REQUIRED(403, "Cette fenêtre n'est plus reliée
    à spec-writer. Relancez-la depuis son raccourci.", ErrorAction.NONE)` ;
  - `BM/infrastructure/web/LocalSecurityFilter.java` le renvoie quand le cookie manque ou n'est
    pas valide ;
  - `BM/infrastructure/web/SessionAuthResource.java` le renvoie quand le jeton est refusé ;
  - les refus d'hôte et d'origine gardent `FORBIDDEN`.

  Mettre à jour `BT/infrastructure/web/LocalSecurityFilterTest.java` : sans cookie,
  `SESSION_REQUIRED` ; origine étrangère, `FORBIDDEN`.
- [ ] T041 [US5] Test puis `FA/core/http/problem.interceptor.ts` (`.spec.ts`) : sur une réponse dont
  le code est `SESSION_REQUIRED`, même silencieuse, `LaunchTokenService.state.set('rejected')`, et
  le bandeau ne s'affiche pas.
- [ ] T042 [US5] Test puis composant `sw-session-lost` (`FA/features/shell/session-lost.component.ts`,
  `.spec.ts`), selon contracts/ui.md :
  - `h1` « Relancez spec-writer depuis son raccourci » et l'explication ;
  - dans `FA/app.ts`, quand `launch.state() === 'rejected'`, seul ce composant est rendu : ni
    menu, ni bandeau, ni `router-outlet` ;
  - le paragraphe `.session-lost` actuel est retiré.
- [ ] T043 [US5] E2E `e2e/tests/us15-navigation.spec.ts`, scénario 15 : nouveau contexte sans
  `storageState`, `/` ouvert, titre visible, aucune `navigation` « Navigation principale », axe.
  Vérifier `e2e/tests/security-local.spec.ts` et `e2e/tests/startup.spec.ts`, qui contrôlent les
  statuts 403, sans changement attendu.

**Checkpoint** : les gates passent, puis commit `feat(session): écran de session perdue`.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [ ] T044 [P] Documentation `README.md` :
  - menu en deux blocs ;
  - Réglages et Connaissance du projet, avec les nouvelles adresses ;
  - bascule du projet courant ;
  - clé Jira par défaut facultative ;
  - test de connexion sur le projet courant ;
  - écran de session perdue.
- [ ] T045 Revue d'ergonomie de la feature : skill `web-design-guidelines` sur les fichiers de
  `FA/features/shell/`, `project-settings/`, `project-knowledge/` et `settings/`. Constats et
  suites dans `specs/012-navigation-projet/checklists/ux-review.md`, avec les corrections
  testées.
- [ ] T046 Gates complètes :
  - `rm -f backend/target/jacoco.exec` ;
  - `./mvnw -B verify -DskipDependencyCheck` (JaCoCo, ArchUnit, SpotBugs, PMD, front) ;
  - `./mvnw -B package -DskipTests` ;
  - `npm --prefix e2e test`.

  Tout est vert, et aucun appel au vrai Copilot.
- [ ] T047 Essai manuel par le mainteneur (quickstart, section « Essai manuel »), consigné dans
  `specs/012-navigation-projet/checklists/validation.md`.

---

## Dependencies & Execution Order

- Setup (T001), puis Foundational (T002 à T004), qui bloque les histoires.
- **US1** (T005 à T014) : MVP.
  - T005 précède T006, puis T007 et T038.
  - T009 précède T010.
  - T011 précède T012, T016 et T017.
- **US2** (T015 à T025) dépend de US1 (menu, titres) et de T002 et T004.
  - T015 précède T016 et T020.
  - T016 et T017 précèdent T019.
  - T018 précède T019.
- **US3** (T026 à T029) dépend de T002 à T004 et de T008. Elle peut suivre US1 en parallèle de
  US2, sauf T028 si T008 n'est pas fini.
- **US4** (T030 à T039) :
  - le backend (T030 à T035) est indépendant du front et peut commencer dès la Foundational ;
  - T036 dépend de T035 ;
  - T038 dépend de T006 ;
  - T022 (bandeau) sert aussi T033 et T035.
- **US5** (T040 à T043) est indépendante. T042 touche `FA/app.ts` après T007.
- **Polish** : T044 dès US4, T045 après US5, T046 avant chaque commit, T047 en dernier.

## Parallel Example

```text
Foundational : T002 (LeaveGuard) | T004 (ProjectFollower)
US1          : T005 (activeEntry) | T009 (TitleStrategy) | T011 (project-heading)
US2          : T015 (formulaire d'identité) | T022 (bandeau) | T023 (ticket Jira)
US4 backend  : T030 (V23) | T031 (JiraSettings) | T032 (codes)   pendant US2 côté front
US5          : T040 (serveur) pendant US3
```

## Implementation Strategy

1. **MVP** : Setup, Foundational, puis US1. Le menu en deux blocs et les titres règlent seuls la
   confusion relevée ; c'est démontrable avec deux projets.
2. **US2** : la page de contexte disparaît au profit des Réglages et de la Connaissance ; les
   anciennes adresses restent utilisables.
3. **US3** : la bascule garantit que le menu et la page montrent le même projet.
4. **US4** : le backend d'abord (migration, clé facultative, test sur le projet courant), puis la
   configuration et les pastilles.
5. **US5** : l'écran de session perdue.
6. **Polish** : documentation, revue d'ergonomie, gates, essai manuel.

Commit par histoire, après les gates. Aucune évaluation du modèle : aucun prompt ne change.
