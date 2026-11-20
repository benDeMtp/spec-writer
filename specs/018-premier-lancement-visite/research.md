# Research: Assistant de premier lancement et visite guidée

Décisions prises en lisant le code au 19/11 (branche `feature/022-premier-lancement`, après la 017).

## R1. Quand une installation est-elle « vierge » ?

**Constat** : une installation n'est jamais sans projet. V7 crée « Projet par défaut »
(`00000000-…-0001`) et `DeleteProject` refuse de supprimer le dernier projet (`LAST_PROJECT`). Le
critère « aucun projet » de la première version de la spec ne pouvait donc jamais être vrai.

**Decision** : la progression de l'assistant est une donnée de l'outil (V29). La migration la
marque terminée quand l'installation est déjà utilisée : `copilot_settings.connected_login` non
nul, une ligne dans `jira_settings` ou `confluence_settings`, au moins une ligne dans `session`,
`conversation` ou `project_doc_source`, ou plus d'un projet. Sur une base neuve, Flyway exécute
toutes les migrations sur des tables vides : la progression part « à faire ».

**Rationale** : aucun réglage à poser à la main pour l'installation du mainteneur (SC-006). Le
jugement est fait une fois, à la migration, et ne change plus ensuite.

**Alternatives considered** :
- Recalculer « vierge » à chaque lancement : l'assistant réapparaîtrait chez un utilisateur qui a
  tout passé sans rien configurer.
- Un drapeau dans le stockage du navigateur : perdu d'un navigateur à l'autre, et invisible du
  serveur (FR-008 exige une reprise après arrêt de l'outil).

## R2. L'étape du projet

**Decision** : l'étape configure le projet courant (le « Projet par défaut » d'une installation
vierge) : nom et clé Jira, par le formulaire d'identité existant (`sw-project-identity-form`) et
la mise à jour de projet existante. Aucun projet n'est créé.

**Rationale** : créer un second projet laisserait un « Projet par défaut » vide et un sélecteur de
projet à deux entrées dès le premier jour.

**Alternatives considered** : créer un projet puis supprimer le défaut s'il est vide (deux
opérations, cas limites sur les sessions déjà rattachées) ; renommer d'office (l'utilisateur ne
choisirait pas le nom).

## R3. Réutiliser les formulaires existants (FR-005)

**Constat** :
- Copilot, Jira et Confluence sont déjà des composants autonomes (`sw-copilot-card`,
  `sw-jira-card`, `sw-confluence-card`) : l'assistant les place tels quels.
- L'identité du projet est déjà un composant (`sw-project-identity-form`).
- Les sources de documentation et le modèle d'architecture sont écrits en ligne dans
  `project-settings.page.ts` (751 lignes).

**Decision** : extraire deux composants de la page des réglages, `sw-doc-sources` (liste, ajout,
retrait, niveau d'une racine Confluence) et `sw-architecture-model` (chemin du `workspace.dsl`),
utilisés par la page et par l'assistant. La page garde le même rendu ; ses tests et les E2E
existants vérifient qu'elle n'a pas changé.

**Alternatives considered** : copier les formulaires dans l'assistant (deux comportements qui
divergeront, refusé par FR-005) ; envoyer l'utilisateur sur la page des réglages au milieu de
l'assistant (il perd le fil, contraire à SC-001).

## R4. L'étape d'indexation

**Constat** : ajouter une source ou enregistrer le modèle d'architecture lance déjà une indexation
de la source (005, 017). Quand l'utilisateur arrive à l'étape d'indexation, une indexation tourne
souvent déjà.

**Decision** : l'étape lit l'indexation courante (`GET …/indexations/current`). Si elle tourne,
elle en montre la progression. Sinon, et si le projet a une clé Jira ou au moins une source, elle
lance une synchronisation incrémentale (`POST …/indexations`, mode `INCREMENTAL`). Sinon, elle
explique que l'agent ne connaîtra pas le projet (US1-8). La progression vient d'un composant
extrait de `sw-knowledge-panel` (`sw-indexation-progress`), partagé par les deux écrans. Le
bouton « Ouvrir la conversation » est actif pendant l'indexation (FR-007).

**Alternatives considered** : toujours lancer « Tout réindexer » (relit des sources déjà lues à
l'instant) ; ne pas montrer la progression (contraire à la décision de l'utilisateur).

## R5. Ouverture automatique et routes

**Decision** :
- Route `/premiers-pas/:etape`, chargée à la demande, hors des blocs « Projet » et « Application »
  du menu (le menu reste visible pour quitter).
- Au démarrage du front, après l'échange du jeton de lancement, `OnboardingService` lit
  `GET /api/onboarding`. Si `autoOpen` est vrai et que l'URL n'est pas déjà l'assistant, il y
  navigue, à l'étape courante.
- `autoOpen` = progression non terminée **et** `specwriter.onboarding.auto-open` (vrai par défaut,
  faux en `%e2e`, voir R7).
- Relance depuis Configuration : bouton « Relancer l'assistant de premier lancement » en tête de la
  page, qui navigue vers `/premiers-pas/accueil` sans toucher la progression enregistrée.
- Plusieurs onglets : chaque onglet relit la progression quand il redevient visible
  (`visibilitychange`). Pas d'événement serveur de plus (le flux SSE de la 017 reste dédié aux
  sources).

**Alternatives considered** : un garde sur chaque route (s'exécute à chaque navigation, pour un
cas qui n'arrive qu'une fois) ; un dialogue modal (l'assistant contient des cartes de connexion
qui ouvrent GitHub dans un autre onglet et attendent la fin de l'autorisation : un dialogue gêne).

## R6. Visite guidée

**Decision** : écrite avec le CDK d'Angular, déjà présent (`@angular/cdk` 22), sans bibliothèque
de visite.
- **Arrêts** : liste déclarée dans le front (`tour-stops.ts`) : route, cible, texte, texte de
  remplacement. Les cibles portent un attribut `data-tour="…"` posé dans les gabarits existants.
- **Bulle** : `Overlay` avec `FlexibleConnectedPositionStrategy` accrochée à la cible (repli
  au-dessus, en dessous, puis centré quand la place manque), `cdkTrapFocus` dans la bulle,
  `LiveAnnouncer` pour annoncer l'arrêt (« Arrêt 3 sur 9 : … »), Échap pour quitter.
- **Mise en valeur** : un contour autour de la cible (`outline` sur la cible, plus un fond
  assombri par un `backdrop` sans clic), `scrollIntoView` avant l'affichage.
- **Navigation** : `Router.navigateByUrl` vers la route de l'arrêt. Le garde de sortie existant
  (`LeaveGuard`) s'applique : si l'utilisateur garde un brouillon non enregistré, la visite reste
  sur l'arrêt courant et le dit.
- **Arrêts qui demandent un élément** (spécification, discussion, tâches) : la visite ouvre la
  spécification la plus récente du projet courant si elle existe (lecture seule de la liste des
  spécifications), sinon l'écran « Spécifications », avec le texte de remplacement (FR-015).
- **Aucune écriture** (FR-017) : la visite n'appelle que des lectures. Un test unitaire vérifie
  qu'aucune requête autre que `GET` ne part pendant la visite ; l'E2E le vérifie sur le réseau.
- **Fin** : retour à l'URL d'où la visite a été lancée.

**Alternatives considered** :
- driver.js ou Shepherd.js : une dépendance de plus pour ce que le CDK fait déjà (positionnement,
  piège du focus, annonces), et l'utilisateur a demandé de rester dans le framework.
- Bulles par écran : écartées par l'utilisateur (spec).

## R7. Tests de bout en bout

**Constat** : les E2E partagent une instance de l'outil et une base. Si l'assistant s'ouvrait
dans cette instance, tous les parcours existants seraient redirigés.

**Decision** :
- `%e2e` : `specwriter.onboarding.auto-open=false`. Les parcours existants ne voient pas
  l'assistant ; ceux de la 018 l'ouvrent par la relance depuis Configuration.
- Le parcours « installation vierge » (US1-1, US1-9, SC-004) démarre une seconde instance du jar
  (port 47697, dossier de données neuf, `auto-open=true`), arrêtée à la fin du fichier de test.
  Le démarrage coûte environ 3 s.
- La visite : parcours au clavier, contrôle `axe` à chaque arrêt, aucune requête autre que `GET`
  pendant la visite.

**Alternatives considered** : marquer l'assistant terminé dans `global-setup` (les parcours de la
018 devraient le remettre « à faire », donc une opération d'écriture réservée aux tests) ; ordre
des fichiers de test (non garanti en parallèle).

## R8. Guide d'installation depuis les sources

**Decision** : nouvelle section « Installer depuis les sources » du README, placée avant
« Installation » (qui parle des installeurs à venir), écrite à partir de `CONTRIBUTING.md` et des
pièges connus de `CLAUDE.md` :
- prérequis : JDK 25 (Mandrel ou Temurin) et `java -version`, Node 22 et `node -v`, Copilot CLI
  ≥ 1.0.105 et `copilot --version` ;
- construction (`./mvnw -B package -DskipTests -DskipDependencyCheck`, qui construit aussi le
  front) et lancement (`java -jar backend/target/quarkus-app/quarkus-run.jar`) ;
- symptômes et remèdes : jar qui s'arrête au démarrage avec un JDK 21, proxy d'entreprise
  (`HTTPS_PROXY`, certificat), Copilot CLI introuvable ou trop ancien ;
- mise à jour : `git pull`, reconstruction, relance ; les données restent dans le dossier de
  données de l'outil.

Les commandes exactes sont vérifiées sur un clone neuf pendant l'implémentation (quickstart Q6) ;
toute différence corrige le guide, pas l'inverse.
