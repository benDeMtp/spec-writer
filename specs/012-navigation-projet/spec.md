# Feature Specification: Navigation par projet et réglages de l'application

**Feature Branch**: `feature/016-navigation-projet`

**Created**: 2026-11-11

**Status**: Draft

**Input**: User description: "Revue globale de l'UX : rendre explicite ce qui dépend du projet courant et ce qui relève de l'application. Décisions prises : menu en deux blocs, « Projet » (le sélecteur de projet titre le bloc ; Conversation, Spécifications, Tickets Jira avec la clé, Connaissance du projet, Réglages du projet) et « Application » (Projets, Configuration avec pastilles d'état Copilot et Jira, Journal) ; les pages du projet nomment le projet dans leur titre ; une seule page « Réglages du projet » (nom, description, clé Jira modifiable, fiche générale, sources de documentation, architecture), la connaissance (compteurs, indexation, recherche d'essai, ADR) a sa propre page atteinte depuis le menu et porte sur le projet courant ; ouvrir une spécification, un questionnaire, la connaissance ou les ADR d'un autre projet bascule le projet courant, avec une annonce ; la Configuration se sépare en « Connexions » (Copilot ; Jira : serveur, jeton, certificat) et « Avancé » (modèle de recherche, accès des agents, avec leur portée écrite : tous les projets, réindexation de chaque projet) ; la clé Jira par défaut devient facultative (repli pour un projet sans clé) ; le test de connexion Jira vérifie la clé du projet courant ; les messages Jira (Tickets Jira vide, Créer un ticket) mènent aux réglages du projet ; l'import d'un ticket annonce le projet de destination ; une session perdue (accès refusé) affiche un écran dédié « Relancez spec-writer depuis son raccourci » au lieu du cadre vide ; l'entrée active du menu suit aussi les pages filles (ticket Jira, réglages) ; menu repliable sur petit écran. Feature 012, à la suite de la 011 (PR #13)."

## Contexte

L'outil sert plusieurs projets. Certaines pages montrent les données du projet courant
(conversations, spécifications, tickets Jira), d'autres valent pour toute l'application
(projets, configuration, journal). Aujourd'hui, rien ne les distingue :

- le menu est une liste plate de six entrées, sous un sélecteur « Projet courant » ;
- les pages du projet ne nomment pas le projet ;
- une spécification ou le contexte d'un autre projet peuvent s'afficher alors que le menu indique
  un autre projet courant ;
- les réglages d'un projet sont répartis entre la liste des projets (nom, clé Jira), la page
  « Contexte » (fiche, sources, architecture, indexation) et la configuration de l'application
  (clé Jira par défaut, obligatoire) ;
- plusieurs messages renvoient à la configuration pour un réglage qui se fait dans le projet.

Cette feature rend la portée de chaque écran explicite et regroupe les réglages d'un projet.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Savoir sur quel projet on travaille (Priority: P1) 🎯 MVP

Le menu se présente en deux blocs. Le bloc « Projet » est titré par le sélecteur du projet
courant et regroupe ce qui porte sur ce projet : Conversation, Spécifications, Tickets Jira (avec
la clé Jira du projet), Connaissance du projet, Réglages du projet. Le bloc « Application »
regroupe ce qui vaut pour tous les projets : Projets, Configuration, Journal. Chaque page du
projet nomme le projet dans son titre. L'entrée du menu reste marquée sur les pages filles (un
ticket Jira, une spécification, les ADR). Sur petit écran, le menu se replie derrière un bouton.

**Why this priority**: c'est la cause de la confusion relevée ; seule, elle rend la portée de
chaque écran lisible.

**Independent Test**: avec deux projets, parcourir les entrées du menu ; chaque page du bloc
« Projet » nomme le projet courant, et changer de projet met à jour les titres.

**Acceptance Scenarios**:

1. **Given** deux projets, Aurora (courant, clé FACT) et Recette, **When** l'utilisateur ouvre
   l'application, **Then** le menu montre le bloc « Projet » titré « Aurora », avec Conversation,
   Spécifications, « Tickets Jira · FACT », Connaissance du projet et Réglages du projet, puis le
   bloc « Application » avec Projets, Configuration et Journal.
2. **Given** le projet Aurora courant, **When** l'utilisateur ouvre Spécifications, **Then** le
   titre de la page est « Spécifications · Aurora » ; de même pour Conversation, Tickets Jira,
   Connaissance du projet et Réglages du projet.
3. **Given** la page Spécifications d'Aurora, **When** l'utilisateur choisit Recette dans le
   sélecteur, **Then** le titre devient « Spécifications · Recette » et la liste celle de Recette.
4. **Given** un ticket Jira ouvert depuis Tickets Jira, **When** la page du ticket s'affiche,
   **Then** l'entrée « Tickets Jira » du menu reste marquée comme page courante.
5. **Given** un écran de moins de 48 rem de large, **When** l'utilisateur ouvre le menu par son
   bouton, **Then** les deux blocs s'affichent, et le menu se referme après le choix d'une page.
6. **Given** un projet sans clé Jira et sans clé par défaut, **When** le menu s'affiche, **Then**
   l'entrée « Tickets Jira » ne montre aucune clé.

---

### User Story 2 - Régler un projet en un seul endroit (Priority: P1)

« Réglages du projet » regroupe tout ce qui se règle pour le projet courant : nom, description,
clé Jira (modifiable), fiche générale, sources de documentation, modèle d'architecture.
« Connaissance du projet » montre ce que l'outil en a tiré : compteurs, indexation, recherche
d'essai, ADR. Les messages qui parlent du projet Jira (Tickets Jira sans clé, Créer un ticket)
mènent aux réglages du projet. L'import d'un ticket Jira annonce le projet de destination.

**Why this priority**: les réglages d'un projet sont aujourd'hui sur trois écrans, et la
connaissance, qui rend l'agent utile, n'est pas dans le menu.

**Independent Test**: depuis le menu, modifier la clé Jira et une source de documentation du
projet courant sur une même page, puis voir les compteurs dans Connaissance du projet.

**Acceptance Scenarios**:

1. **Given** le projet Aurora courant, **When** l'utilisateur ouvre Réglages du projet, **Then**
   il y trouve, sur une même page, le nom, la description, la clé Jira, la fiche générale, les
   sources de documentation et le modèle d'architecture d'Aurora, chacun modifiable.
2. **Given** la clé Jira modifiée dans les réglages, **When** l'utilisateur enregistre, **Then**
   la clé est vérifiée comme aujourd'hui (avertissement si Jira ne la connaît pas) et le menu
   affiche la nouvelle clé.
3. **Given** le projet Aurora courant, **When** l'utilisateur ouvre Connaissance du projet,
   **Then** il voit les compteurs, l'état de l'indexation, les actions de réindexation, la
   recherche d'essai et le lien vers les ADR d'Aurora, et un lien vers les réglages du projet.
4. **Given** un projet sans clé Jira et sans clé par défaut, **When** l'utilisateur ouvre Tickets
   Jira, **Then** la page explique qu'aucun projet Jira n'est associé et propose « Renseigner la
   clé Jira du projet », qui ouvre les réglages du projet sur ce champ.
5. **Given** Jira configuré mais sans clé pour le projet, **When** l'utilisateur veut créer un
   ticket depuis une spécification, **Then** le message d'indisponibilité mène aux réglages du
   projet pour la clé, et à la configuration seulement pour le serveur ou le jeton.
6. **Given** le projet Aurora courant, **When** l'utilisateur affiche un ticket Jira, **Then**
   l'écran annonce « La spécification sera ajoutée au projet Aurora » avant « Discuter avec
   l'IA ».
7. **Given** un ancien lien vers la page de contexte d'un projet, **When** l'utilisateur l'ouvre,
   **Then** il arrive sur la page équivalente (réglages ou connaissance) de ce projet.

---

### User Story 3 - Une page d'un autre projet bascule le projet courant (Priority: P2)

Ouvrir une spécification, un questionnaire, la connaissance ou les ADR d'un autre projet que le
projet courant (depuis un lien, une recherche, l'historique du navigateur) fait de ce projet le
projet courant. Le menu et la page montrent donc toujours le même projet. Une annonce le dit.

**Why this priority**: sans elle, le menu peut indiquer un projet et la page en montrer un autre ;
moins fréquent que les parcours de US1 et US2, mais source d'erreurs (import, nouvelle
spécification dans le mauvais projet).

**Independent Test**: avec Aurora courant, ouvrir l'adresse d'une spécification de Recette ;
le menu passe à Recette et une annonce le dit.

**Acceptance Scenarios**:

1. **Given** Aurora courant, **When** l'utilisateur ouvre une spécification de Recette, **Then**
   Recette devient le projet courant, le menu le montre, et l'annonce « Projet courant : Recette »
   s'affiche et est lue par les lecteurs d'écran.
2. **Given** la spécification de Recette affichée après bascule, **When** l'utilisateur ouvre
   Spécifications, **Then** il voit la liste de Recette.
3. **Given** Aurora courant, **When** l'utilisateur ouvre une spécification d'Aurora, **Then**
   rien ne bascule et aucune annonce ne s'affiche.
4. **Given** une proposition de nouvelle version en attente sur une spécification d'Aurora,
   **When** l'utilisateur change de projet dans le sélecteur, **Then** la confirmation de la
   feature 011 (« Quitter sans appliquer la proposition ? ») s'applique avant tout changement.

---

### User Story 4 - Une configuration de l'application plus claire (Priority: P2)

La Configuration se sépare en « Connexions » (GitHub Copilot ; Jira : serveur, jeton,
certificat) et « Avancé » (modèle de recherche, accès des agents), chaque réglage avancé disant
sa portée : l'accès des agents couvre tous les projets ; changer de modèle de recherche demande
de réindexer chaque projet. Dans le menu, l'entrée Configuration porte deux pastilles d'état,
Copilot et Jira. La clé Jira par défaut devient facultative : elle sert de repli aux projets sans
clé. Le test de connexion Jira vérifie la clé du projet courant.

**Why this priority**: la configuration est moins visitée que les pages du projet ; ces
changements lèvent des erreurs de compréhension sans changer les parcours principaux.

**Independent Test**: Copilot connecté et Jira non configuré, vérifier les pastilles du menu ;
enregistrer la configuration Jira sans clé par défaut ; tester la connexion avec un projet qui a
sa propre clé.

**Acceptance Scenarios**:

1. **Given** Copilot connecté et Jira non configuré, **When** le menu s'affiche, **Then**
   l'entrée Configuration porte « Copilot : connecté » et « Jira : non configuré », sous forme de
   pastilles dont le sens est donné par le texte, pas seulement par la couleur.
2. **Given** la page Configuration, **When** elle s'affiche, **Then** elle présente d'abord
   « Connexions » (Copilot, Jira), puis « Avancé » (modèle de recherche, accès des agents), avec
   la portée de chaque réglage avancé écrite sous son titre.
3. **Given** le formulaire Jira, **When** l'utilisateur enregistre un serveur et un jeton sans
   clé par défaut, **Then** l'enregistrement est accepté.
4. **Given** le projet courant avec la clé FACT et une clé par défaut PROJ, **When**
   l'utilisateur teste la connexion Jira, **Then** le test vérifie l'accès au projet Jira FACT et
   le dit.
5. **Given** un projet sans clé et une clé par défaut PROJ, **When** l'utilisateur ouvre Tickets
   Jira, **Then** la page liste les tickets de PROJ et indique « clé par défaut de
   l'application ».

---

### User Story 5 - Une session perdue est dite clairement (Priority: P3)

Quand la fenêtre n'est plus reliée à l'outil (accès refusé), l'application affiche un écran
dédié : « Relancez spec-writer depuis son raccourci », sans le menu ni des pages vides
trompeuses (« Aucune conversation dans ce projet »).

**Why this priority**: situation rare, mais aujourd'hui elle fait croire à un projet vide.

**Independent Test**: ouvrir l'outil sans jeton de lancement ; seul l'écran dédié s'affiche.

**Acceptance Scenarios**:

1. **Given** une fenêtre sans session valide, **When** elle s'ouvre, **Then** seul l'écran
   « Relancez spec-writer depuis son raccourci » s'affiche, avec son explication.
2. **Given** une session qui expire pendant l'usage, **When** une action est refusée pour cette
   raison, **Then** l'écran dédié remplace la page.

---

### Edge Cases

- Le projet courant est supprimé (depuis Projets) : le projet courant passe au premier projet par
  ordre alphabétique, comme aujourd'hui, et les titres du bloc « Projet » suivent.
- Un seul projet : le bloc « Projet » garde son titre ; le sélecteur reste (il mène aussi à
  « Gérer les projets »).
- Changer de projet pendant qu'une réponse de l'agent est en cours : la réponse continue dans sa
  conversation ; la page affichée passe au nouveau projet.
- Changer de projet sur Réglages du projet avec des modifications non enregistrées : l'outil
  demande de les enregistrer ou de les abandonner avant de changer.
- L'adresse d'une spécification supprimée : message « Cette spécification n'existe plus », sans
  bascule.
- Une conversation d'un autre projet ouverte par son adresse : la règle actuelle (retour à
  l'accueil du projet courant) devient une bascule vers son projet, comme pour une spécification.
- Les pastilles d'état ne doivent pas multiplier les appels : l'état est relu au chargement, à
  chaque changement dans la Configuration et après une erreur de connexion.
- Les agents de code (MCP) ne sont pas touchés : ils ne dépendent pas du projet courant.

## Requirements *(mandatory)*

### Functional Requirements

**Menu et titres**

- **FR-001**: Le menu DOIT présenter deux blocs nommés : « Projet », titré par le sélecteur du
  projet courant (Conversation, Spécifications, Tickets Jira, Connaissance du projet, Réglages du
  projet), et « Application » (Projets, Configuration, Journal).
- **FR-002**: L'entrée Tickets Jira DOIT afficher la clé Jira utilisée pour le projet courant,
  s'il y en a une.
- **FR-003**: Les pages du bloc « Projet » DOIVENT nommer le projet courant dans leur titre, et
  le titre de l'onglet du navigateur DOIT le contenir aussi.
- **FR-004**: L'entrée du menu DOIT rester marquée comme page courante sur ses pages filles
  (ticket Jira, spécification, questionnaire, nouvelle spécification, ADR).
- **FR-005**: Sous 48 rem de large, le menu DOIT se replier derrière un bouton accessible au
  clavier, et se refermer après le choix d'une page.

**Réglages et connaissance du projet**

- **FR-006**: « Réglages du projet » DOIT regrouper, pour le projet courant, le nom, la
  description, la clé Jira, la fiche générale, les sources de documentation et le modèle
  d'architecture, avec les règles de validation actuelles.
- **FR-007**: « Connaissance du projet » DOIT présenter, pour le projet courant, les compteurs,
  l'état et les actions d'indexation, la recherche d'essai, les ADR, et un lien vers les réglages.
- **FR-008**: La page Projets DOIT rester l'endroit où l'on crée, renomme, supprime et choisit un
  projet ; ses liens mènent aux réglages et à la connaissance du projet choisi.
- **FR-009**: Les anciennes adresses de la page de contexte et des ADR d'un projet DOIVENT mener à
  la page équivalente de ce projet.
- **FR-010**: Les messages d'indisponibilité liés au projet Jira (Tickets Jira sans clé, Créer un
  ticket) DOIVENT mener aux réglages du projet ; ceux liés au serveur, au jeton ou à l'accord DOIVENT
  mener à la configuration.
- **FR-011**: L'écran d'un ticket Jira DOIT annoncer le projet spec-writer de destination avant
  « Discuter avec l'IA ».

**Bascule du projet courant**

- **FR-012**: Ouvrir une spécification, un questionnaire, une conversation, la connaissance ou les
  ADR d'un autre projet DOIT faire de ce projet le projet courant, avec une annonce visible et lue
  par les lecteurs d'écran.
- **FR-013**: Un changement de projet DOIT respecter les confirmations existantes (proposition en
  attente, modifications non enregistrées des réglages).

**Configuration**

- **FR-014**: La Configuration DOIT présenter « Connexions » (GitHub Copilot ; Jira : serveur,
  jeton, certificat, accord) avant « Avancé » (modèle de recherche, accès des agents), et écrire la
  portée de chaque réglage avancé.
- **FR-015**: L'entrée Configuration du menu DOIT porter l'état de Copilot (connecté ou non) et de
  Jira (configuré ou non), en texte et non par la couleur seule.
- **FR-016**: La clé Jira par défaut DOIT être facultative ; elle sert de repli aux projets sans
  clé, et la page Tickets Jira DOIT dire quand elle l'utilise.
- **FR-017**: Le test de connexion Jira DOIT vérifier la clé effective du projet courant (sa clé,
  sinon la clé par défaut), et nommer la clé vérifiée.

**Session perdue**

- **FR-018**: Sans session valide, l'application DOIT afficher seulement un écran dédié qui
  explique de relancer spec-writer depuis son raccourci.

**Accessibilité**

- **FR-019**: Les blocs du menu DOIVENT être des groupes de navigation nommés ; l'ordre de
  tabulation suit l'ordre visuel ; les nouvelles pages passent axe sans violation.

### Key Entities

- **Projet courant** : le projet dont le menu, les titres et les pages du bloc « Projet » montrent
  les données ; il change par le sélecteur, par la page Projets, ou en ouvrant une page d'un autre
  projet.
- **Clé Jira effective** : la clé du projet, sinon la clé par défaut de l'application ; aucune si
  aucune des deux n'est renseignée.
- **État des connexions** : Copilot (connecté ou non) et Jira (configuré ou non, serveur et jeton
  renseignés), affichés dans le menu.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100 % des pages du bloc « Projet » nomment le projet courant dans leur titre.
- **SC-002**: Le menu et la page affichée montrent le même projet dans 100 % des parcours testés
  (sélecteur, page Projets, lien vers une page d'un autre projet).
- **SC-003**: Tous les réglages d'un projet sont modifiables depuis une seule page, atteinte en
  1 clic depuis le menu (aujourd'hui : 2 pages, dont une à 2 clics).
- **SC-004**: Aucun message de l'outil ne renvoie à la Configuration pour un réglage propre au
  projet.
- **SC-005**: L'état de Copilot et de Jira est lisible depuis n'importe quelle page, sans l'ouvrir.
- **SC-006**: axe ne signale aucune violation sur le menu (déplié et replié), les réglages, la
  connaissance, la configuration et l'écran de session perdue.

## Assumptions

- Les pages restent celles d'aujourd'hui dans leur contenu (conversation, spécifications,
  connaissance, configuration) ; seules leur place, leurs titres et leur regroupement changent,
  sauf la fusion des réglages du projet.
- Le projet courant reste enregistré par l'outil (il survit au redémarrage), sans apparaître dans
  l'adresse des pages du bloc « Projet ».
- La clé Jira par défaut existante est conservée telle quelle ; elle devient seulement facultative.
- Les agents de code (MCP) et le journal ne changent pas de comportement.
- La feature 011 (PR #13) est fusionnée ou sert de base : la confirmation « Quitter sans appliquer
  la proposition ? » existe.
- Pas de nouveau thème ni de nouvelle identité visuelle : les jetons visuels actuels sont repris.
