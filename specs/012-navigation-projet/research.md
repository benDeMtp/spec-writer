# Research: Navigation par projet et réglages de l'application (012)

Inventaire de l'existant, puis décisions. Chaque décision donne son motif et les alternatives
écartées.

## Existant

- **Menu** (`app.ts`) :
  - une liste plate de six liens, sous `sw-project-switcher` (libellé « Projet courant ») ;
  - l'entrée active vient de `routerLinkActive`, sauf « Conversation », calculée à la main ;
  - `/jira/:key` n'active rien, car l'option `exact` est posée sur `/jira` ;
  - sous 48 rem, la liste devient un défilement horizontal.
- **Projet courant** :
  - `ProjectsStore` (`projects`, `current`, `select`) ;
  - côté serveur, `PUT /api/projects/current`, enregistré en base, résolu par `CurrentProject`.
- **Pages adressées par identifiant** :
  - `sessions/:id`, `sessions/:id/versions/:n`, `projets/:id/contexte` et `projets/:id/contexte/adr`
    affichent le projet de l'adresse, sans toucher au projet courant ;
  - `conversations/:cid` d'un autre projet ramène à l'accueil (`conversation.page.ts`,
    `detail.projectId !== projectId`).
- **Réglages d'un projet**, sur trois écrans :
  - `projects.page.ts` : nom, description, clé Jira, avec l'avertissement `jiraKeyWarning` ;
  - `project-context.page.ts` (455 lignes) : fiche, sources, modèle d'architecture, et le
    composant `knowledge-panel` (compteurs, indexation, recherche d'essai, lien vers les ADR) ;
  - la configuration : clé Jira par défaut.
- **Clé Jira par défaut** :
  - obligatoire à trois niveaux : la base (`jira_settings.default_project_key TEXT NOT NULL`, V2),
    le domaine (`JiraSettings.create` refuse une clé vide) et le formulaire `jira-card` ;
  - `JiraAccess.effectiveProject` : clé du projet, sinon clé par défaut ;
  - `JiraAccess.target()` part de la clé par défaut ;
  - `TestJiraConnection.run()` vérifie toujours la clé par défaut.
- **Session perdue** :
  - `LaunchTokenService.state = 'rejected'` seulement si l'échange du jeton de lancement échoue ;
  - sans jeton dans l'URL et sans cookie valide, chaque appel reçoit `403 FORBIDDEN`. Le bandeau
    commun l'affiche, mais le cadre reste : pages vides, « Aucune conversation dans ce projet » ;
  - le serveur renvoie `FORBIDDEN` dans quatre cas : hôte refusé, origine refusée, cookie absent
    ou invalide, et le 403 générique de `ProblemMapper`.
- **Erreurs Jira** :
  - `JIRA_NOT_CONFIGURED` porte l'action `OPEN_SETTINGS`, que `problem-banner` envoie vers
    `/configuration` ;
  - la page Tickets Jira sans clé propose « Ouvrir la configuration ».

## R1. Adresses des pages du projet

**Décision** : quatre nouvelles adresses, sans identifiant, qui portent sur le projet courant :

| Adresse | Page |
|---|---|
| `/projet/reglages` | Réglages du projet |
| `/projet/connaissance` | Connaissance du projet |
| `/projet/connaissance/adr` | ADR du projet |
| `/projet/reglages#jira` | ancre du champ clé Jira, cible de « Renseigner la clé Jira du projet » |

Les anciennes adresses `projets/:id/contexte` et `projets/:id/contexte/adr` restent comme
redirections, par une garde fonctionnelle `followProjectGuard` :
1. elle rend `:id` courant (R3) ;
2. elle renvoie l'`UrlTree` de la nouvelle page (`/projet/connaissance` ou
   `/projet/connaissance/adr`) ;
3. un identifiant inconnu mène à `/projets`, avec le message « Ce projet n'existe plus ».

**Motif** :
- le menu ne mène qu'à des pages du projet courant (FR-001), et l'adresse n'a donc pas besoin de
  l'identifiant ;
- c'est l'hypothèse de la spec (le projet courant n'apparaît pas dans l'adresse) ;
- ainsi, un changement de projet par le sélecteur ne laisse jamais une adresse qui désigne un
  autre projet.

**Alternatives écartées** :
- `/projets/:id/reglages` : chaque changement de projet devrait réécrire l'adresse, et un lien
  partagé ouvrirait un projet sans bascule ;
- garder `contexte` : le mot désigne aujourd'hui un mélange de réglages et de connaissance.

## R2. Menu en deux blocs et entrée active

**Décision** : le menu sort de `app.ts` dans un nouveau composant, `sw-main-nav`
(`features/shell/main-nav.component.ts`).

- **Structure** :
  - un seul `<nav aria-label="Navigation principale">` ;
  - deux blocs `<div role="group" aria-labelledby>`, chacun avec son titre :
    - « Projet », complété par le sélecteur : le `mat-select` est libellé « Projet » et sa valeur
      est le nom du projet, ce qui « titre le bloc » ;
    - « Application ».
- **Entrée active** : calculée par la fonction pure `activeEntry(path)` (`features/shell/nav.ts`),
  testée seule. Le tableau des préfixes :

| Entrée | Préfixes |
|---|---|
| Conversation | `/`, `/conversations/` |
| Spécifications | `/sessions`, `/nouveau` |
| Tickets Jira | `/jira` |
| Connaissance | `/projet/connaissance` |
| Réglages | `/projet/reglages` |
| Projets | `/projets` |
| Configuration | `/configuration` |
| Journal | `/journal` |

  L'entrée active reçoit `aria-current="page"`. `routerLinkActive` est retiré.
- **Clé Jira** : l'entrée « Tickets Jira » affiche `effectiveJiraProjectKey` (champ déjà présent
  dans `Project`), dans un `<span>` secondaire. Le nom accessible devient
  « Tickets Jira, projet FACT ».
- **Petit écran** (moins de 48 rem) :
  - un bouton « Menu » (`aria-expanded`, `aria-controls`) ouvre et ferme le panneau ;
  - le panneau se ferme sur `NavigationEnd` et sur Échap, et le focus revient au bouton ;
  - au-dessus de 48 rem, le bouton est masqué et le panneau toujours visible.

**Motif** : le calcul de l'entrée active est concentré dans une seule fonction testable, au lieu
d'un mélange de `routerLinkActive` et de cas particuliers. Les pages filles sont couvertes par
leurs préfixes.

**Alternatives écartées** :
- `routerLinkActive` sur des liens cachés : fragile ;
- `MatSidenav` : nouvelle dépendance de module, et un comportement modal inutile ici.

## R3. Bascule du projet courant

**Décision** : un nouveau service racine, `ProjectFollower` (`core/projects/project-follower.ts`).

- `follow(projectId)` : si `projectId` diffère du projet courant, il appelle
  `ProjectsStore.select(projectId)`, puis annonce « Projet courant : <nom> » :
  - en visible, par un `role="status"` du shell (`sw-project-notice`), qui s'efface après 6 s ou au
    changement de page suivant ;
  - aux lecteurs d'écran, par le même `role="status"` (pas de `LiveAnnouncer` en double).
- **Appelants**, après chargement de leur ressource :
  - `specification.page` et `questionnaire.page`, avec `session.projectId` ;
  - `conversation.page` : le retour à l'accueil est remplacé par `follow(detail.projectId)` ;
  - `followProjectGuard` (R1).
- **Ce qui ne bascule pas** :
  - `/jira/:key` : le ticket appartient au projet Jira, pas à un projet spec-writer ;
  - une ressource introuvable (404) : rien ne bascule, et le message existant s'affiche.
- **Rechargement des listes** : les listes qui dépendent du projet courant (sessions, conversations)
  se rechargent déjà quand `ProjectsStore.current` change. La page affichée ne recharge pas sa
  ressource : elle appartient déjà au nouveau projet courant.

**Motif** :
- une seule source de vérité (le projet courant enregistré côté serveur) ;
- le menu, l'import Jira et « Nouveau besoin » visent toujours le projet de la page affichée.

**Alternatives écartées** :
- un bandeau « ce document appartient à Recette » sans bascule : écarté par l'utilisateur ;
- un projet porté dans chaque adresse : voir R1.

## R4. Changement de projet et confirmations

**Décision** : un nouveau service racine, `LeaveGuard` (`core/navigation/leave-guard.ts`).

- Une page enregistre une fonction `() => boolean | Promise<boolean>` et la retire à sa
  destruction. Deux pages l'utilisent :
  - `specification.page`, avec l'actuel `canLeave()` (proposition en attente, rédaction en cours) ;
  - `project-settings.page`, pour les modifications non enregistrées.
- **La garde de route** `canDeactivate` de ces deux routes appelle `LeaveGuard.confirm()`.
- **Le sélecteur** appelle `LeaveGuard.confirm()` avant `ProjectsStore.select`. En cas de refus :
  - la sélection est annulée ;
  - la valeur du `mat-select` revient au projet courant.
- **Changement accepté**, sur une page adressée par identifiant (spécification, questionnaire,
  conversation ouverte, ticket Jira) :
  - le sélecteur navigue vers la page liste de la même entrée du menu (`/sessions`, `/`, `/jira`) ;
  - sur les autres pages, il reste sur place, et le contenu suit `current`.

**Motif** :
- le sélecteur ne déclenche pas de navigation quand l'adresse reste la même (réglages) : une garde
  de route ne suffit pas ;
- un registre unique sert les deux cas ;
- `canLeave()` de la 011 est réutilisé tel quel.

**Alternative écartée** : forcer une navigation vers une adresse « tampon » pour déclencher les
gardes. Elle produirait un historique bruité et un double chargement.

## R5. Titres de page

**Décision** :
- **Onglet** : une `TitleStrategy` personnalisée (`core/navigation/project-title.strategy.ts`).
  - Les routes du bloc « Projet » portent `data: { scope: 'project' }`.
  - Leur titre devient `<titre> · <projet> · spec-writer`.
  - La stratégie se recalcule quand `ProjectsStore.current` change.
- **Titre visible** : un composant partagé `sw-project-heading` (`shared/ui/project-heading.component.ts`),
  avec l'entrée `label`.
  - Il rend `<h1>{{ label }} · {{ nom }}</h1>`, et le nom vient du store.
  - Il sert à Conversation (accueil), Spécifications, Tickets Jira, Connaissance et Réglages.
  - Le titre d'une spécification ouverte reste son nom : le projet est dans le menu, et la page a
    basculé (R3).

**Motif** : le titre de l'onglet et le titre visible suivent le projet sans code dans chaque page.

## R6. Réglages du projet et Connaissance du projet

**Décision** : `project-context.page.ts` est découpé en deux pages.

- **`features/project-settings/project-settings.page.ts`**, Réglages du projet, en quatre sections :
  1. « Identité » : nom, description, clé Jira (`id="jira"`), avec l'avertissement
     `jiraKeyWarning` et un bouton « Enregistrer » propre à la section ;
  2. « Fiche générale » : `sw-sheet-editor`, existant ;
  3. « Sources de documentation » : le formulaire actuel, sans changement ;
  4. « Modèle d'architecture » : le formulaire actuel.

  Les sections sont déplacées telles quelles. La section Identité reprend le formulaire de
  `projects.page`, extrait en `project-identity-form.component.ts`, partagé avec la création de
  projet. Sont « non enregistrées » : la saisie de la section Identité, celle d'une source ou de
  l'architecture, et l'éditeur de fiche ouvert.
- **`features/project-knowledge/project-knowledge.page.ts`**, Connaissance du projet :
  - `sw-knowledge-panel` (compteurs, indexation, réindexation par source, recherche d'essai) ;
  - le lien « ADR du projet » ;
  - un lien « Réglages du projet ».

  `adrs.page` déménage à `/projet/connaissance/adr`.
- **Page Projets** :
  - elle garde la création, le choix du projet courant et la suppression ;
  - « Modifier » ouvre les réglages du projet (après avoir rendu le projet courant) ;
  - le lien « Contexte » devient « Connaissance ».

**Motif** : une page par intention, régler ou consulter (FR-006, FR-007, SC-003), sans
réécrire les formulaires.

## R7. Clé Jira par défaut facultative

**Décision** :
- **Migration** `V23__jira_default_key_optional.sql` : reconstruction de `jira_settings` (SQLite ne
  sait pas retirer un `NOT NULL`). Elle crée `jira_settings_new` avec la colonne nullable, copie,
  supprime l'ancienne table et renomme. La ligne existante est conservée telle quelle (hypothèse
  de la spec).
- **Domaine** : `JiraSettings.create` accepte une clé vide, enregistrée à `null`. Une clé saisie
  garde la règle `[A-Z][A-Z0-9_]+`.
- **Accès Jira** :
  - `JiraAccess.Target.projectKey` peut être `null` ;
  - la nouvelle méthode `Target.requireProject()` lève `JIRA_PROJECT_MISSING` (409, action
    `OPEN_PROJECT_SETTINGS`, message « Aucun projet Jira n'est associé à ce projet. Renseignez sa
    clé Jira dans les réglages du projet. ») ;
  - elle est appelée par `TicketForm`, `PreviewJiraTicket` et `CreateJiraTicket`. La liste des
    tickets (`/api/jira/projects/{key}/issues`) reçoit déjà la clé du front, qui ne l'appelle pas
    sans clé effective ;
  - `UnifiedSearch` saute la part Jira sans erreur quand la clé manque : la recherche locale
    suffit.
- **Front** :
  - `ErrorAction.OPEN_PROJECT_SETTINGS` est ajoutée ;
  - `problem-banner` affiche « Ouvrir les réglages du projet » et navigue vers
    `/projet/reglages#jira` ;
  - le libellé de `jira-card` devient « Clé Jira par défaut (facultatif) », avec l'aide « Utilisée
    pour les projets sans clé Jira ».

**Motif** :
- FR-016 ;
- un message précis remplace l'actuel « Jira n'est pas configuré », qui serait faux une fois
  serveur et jeton renseignés ;
- l'action renvoie vers l'endroit où se règle la clé (FR-010, SC-004).

## R8. Test de connexion Jira sur la clé du projet courant

**Décision** :
- `TestJiraConnection.run()` résout la clé effective du projet courant, par
  `JiraAccess.effectiveProject(CurrentProject.resolve(projects))`.
- **Clé présente** : les quatre étapes actuelles, sur cette clé.
- **Clé absente** : les deux premières étapes (version, utilisateur), et le nouveau résultat
  `OK_NO_PROJECT` : « Connexion à Jira réussie. Aucun projet Jira n'est associé au projet courant :
  renseignez sa clé dans les réglages du projet. »
- `Outcome` et `TestView` gagnent `projectKey` (la clé vérifiée, ou `null`). `jira-card` affiche
  « Projet Jira vérifié : FACT ».
- `last_test_result` n'a pas de contrainte `CHECK` : la nouvelle valeur s'enregistre sans
  migration.

**Motif** : FR-017. Le test montre alors ce que l'utilisateur va réellement utiliser.

## R9. Pastilles d'état dans le menu

**Décision** :
- `sw-main-nav` lit `SettingsStore`, chargé au démarrage du shell : un appel `GET /api/settings`,
  déjà fait aujourd'hui par plusieurs pages.
- **Les deux pastilles**, sous l'entrée Configuration :
  - « Copilot : connecté » ou « Copilot : non connecté » (`copilotConnected`) ;
  - « Jira : configuré » ou « Jira : non configuré » (`jira()?.enabled` et jeton présent).
- **Forme** : texte visible, plus une pastille colorée décorative (`aria-hidden`).
- **Rafraîchissement** : le store est déjà relu après chaque action de la Configuration. Le shell
  relit aussi l'état après une erreur `COPILOT_*` ou `JIRA_*` remontée au bandeau, via
  `ProblemStore`.

**Motif** : SC-005 sans scrutation (cas limite de la spec). Pas de nouvel appel.

## R10. Configuration en deux parties

**Décision** :
- `settings.page` devient deux `<section>` avec un `h2` chacune :
  - « Connexions » : `sw-copilot-card`, `sw-jira-card` ;
  - « Avancé » : `sw-embedding-card`, `sw-agent-access-card`.
- Chaque carte avancée reçoit, sous son titre, une phrase de portée :
  - « S'applique à tous les projets. Changer de modèle demande de réindexer chaque projet. » ;
  - « Les agents de code accèdent à tous les projets. »
- Pas d'onglets : la page reste courte.

**Motif** : FR-014. Des onglets cacheraient le contenu de la recherche dans la page.

## R11. Écran de session perdue

**Décision** :
- **Serveur** : un code dédié, `SESSION_REQUIRED` (403, action `NONE`, message « Cette fenêtre
  n'est plus reliée à spec-writer. »).
  - Il est renvoyé par `LocalSecurityFilter` quand le cookie manque ou n'est plus valide, et par
    `SessionAuthResource` quand le jeton de lancement est refusé.
  - Les refus d'hôte et d'origine gardent `FORBIDDEN`.
- **Front** :
  - `problemInterceptor` passe `LaunchTokenService.state` à `'rejected'` sur `SESSION_REQUIRED`,
    même pour une requête silencieuse, sans passer par le bandeau ;
  - `App` rend alors seulement `sw-session-lost` (`features/shell/session-lost.component.ts`) : un
    titre « Relancez spec-writer depuis son raccourci », une explication, et aucun menu.

**Motif** :
- FR-018 ;
- un code distinct évite de confondre la session perdue avec un refus d'origine (attaque, mauvais
  port), qui doit rester une erreur ordinaire.

## R12. Annonce du projet de destination à l'import

**Décision** :
- `item-detail.page` affiche « La spécification sera ajoutée au projet <nom courant>. », avant le
  bouton « Discuter avec l'IA ».
- Un ticket déjà lié ouvre la spécification existante (011). Si elle est dans un autre projet, la
  bascule de R3 s'applique et l'annonce le dit. Aucun champ n'est ajouté à `JiraIssueView`.

**Motif** : FR-011. L'import se fait dans le projet courant ; l'annonce le rend explicite.

## R13. Tests

- **Vitest** :
  - `activeEntry` : toutes les adresses de R2 ;
  - `sw-main-nav` : blocs, clé Jira, pastilles, menu replié ;
  - `ProjectFollower` : bascule, absence de bascule, annonce ;
  - `LeaveGuard` et le sélecteur : refus et retour de la valeur ;
  - `ProjectTitleStrategy` ;
  - `project-settings.page` (sections, ancre `#jira`, modifications non enregistrées) ;
  - `project-knowledge.page` ;
  - `jira-issues.page` : les trois états (clé du projet, clé par défaut, aucune clé) ;
  - `problem-banner` : `OPEN_PROJECT_SETTINGS` ;
  - interceptor : `SESSION_REQUIRED`.
- **JUnit** :
  - `JiraSettings.create` sans clé ;
  - `JiraAccess` : clé manquante ;
  - `TestJiraConnection` : clé du projet courant, clé par défaut, aucune clé ;
  - `UnifiedSearch` sans clé ;
  - `LocalSecurityFilter` : `SESSION_REQUIRED` d'un côté, `FORBIDDEN` de l'autre ;
  - IT de migration V23 (ligne conservée, `NULL` accepté) ;
  - IT `SettingsResource` (clé facultative, test sur le projet courant) et IT de création de ticket
    sans clé (409 `JIRA_PROJECT_MISSING`).
- **E2E** : `us15-navigation.spec.ts` (quickstart), avec axe sur le menu déplié et replié, les
  réglages, la connaissance, la configuration et l'écran de session perdue. Les specs existantes
  qui visent `projets/:id/contexte`, le libellé « Projet courant » ou « Ouvrir la configuration »
  sont mises à jour.
- **Évaluation** : aucune. Aucun prompt ni appel au modèle ne change.
