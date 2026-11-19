# Feature Specification: Assistant de premier lancement et visite guidée

**Feature Branch**: `feature/022-premier-lancement`

**Created**: 2026-11-19

**Status**: Draft

**Input**: User description: "Feature 018 : assistant de premier lancement et visite guidée. Un collègue va installer l'outil à partir des sources, sans le contexte du mainteneur. Décisions prises le 19/11 : un assistant de premier lancement mène jusqu'à la première indexation (Copilot obligatoire ; Jira, Confluence, premier projet, premières sources et modèle d'architecture ; lancement de l'indexation avec sa progression), s'ouvre tant que la configuration initiale n'est pas faite, se relance depuis Configuration, laisse passer chaque étape facultative, reprend où l'utilisateur s'était arrêté et réutilise les formulaires existants ; une visite guidée enchaînée, proposée à la fin de l'assistant et relançable depuis le menu, parcourt les écrans principaux dans l'ordre d'usage avec une bulle par arrêt ; la section d'installation du README est relue avec l'œil d'un débutant qui installe depuis les sources. Hors périmètre : paquet d'installation distribué, aide contextuelle par écran, réglages avancés dans l'assistant."

## Contexte

L'outil va être installé par un collègue du mainteneur, à partir des sources : aucun paquet n'est
encore distribué. Ce collègue n'a ni le contexte du mainteneur ni sa configuration.

Aujourd'hui, rien ne guide un nouvel utilisateur. Au premier lancement, l'outil ouvre la page
Configuration, mais ce qu'il faut régler est éparpillé sur trois écrans : Configuration (connexions
à Copilot, Jira et Confluence ; réglages avancés), Projets (création du projet, clé Jira) et
Réglages du projet (sources de documentation, modèle d'architecture, racines de sources, fiabilité,
synchronisation automatique). Seul Copilot est exigé par l'outil ; sans projet ni sources, l'agent
répond pourtant sans rien savoir du projet, ce qui donne une mauvaise première impression.

Une fois configuré, l'outil enchaîne plusieurs écrans (conversation, spécification et discussion,
tâches suivies par les agents, vue Jira, connaissance du projet) dont la logique d'ensemble ne se
devine pas.

Cette feature mène le nouvel utilisateur de l'outil vierge à une conversation qui connaît son
projet, puis lui fait visiter l'outil dans l'ordre où il s'en servira. Elle remet aussi à niveau
le guide d'installation depuis les sources, que l'assistant ne peut pas remplacer.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Être mené jusqu'à un projet indexé (Priority: P1) 🎯 MVP

Au premier lancement, l'outil ouvre un assistant qui enchaîne les étapes : connexion à Copilot,
connexions à Jira et à Confluence, création du premier projet, ses premières sources de
documentation et son modèle d'architecture, puis l'indexation du projet, dont il suit la
progression. À la fin, l'utilisateur arrive sur une conversation qui connaît son projet. Seule la
connexion à Copilot est obligatoire ; toute autre étape se passe d'un clic et se fait plus tard
depuis les écrans habituels.

**Why this priority**: sans configuration, l'outil ne sert à rien ; c'est la première chose que le
collègue rencontrera, et c'est elle qui décide s'il continue.

**Independent Test**: sur une installation vierge, suivre l'assistant avec Copilot, un Jira et une
source de documentation simulés : l'utilisateur arrive sur une conversation dont le projet est
indexé, sans avoir ouvert Configuration, Projets ni Réglages du projet. Recommencer en passant
toutes les étapes facultatives : l'utilisateur arrive sur une conversation avec un projet vide.

**Acceptance Scenarios**:

1. **Given** une installation vierge (aucun projet, Copilot non connecté), **When** l'utilisateur ouvre l'outil, **Then** l'assistant s'ouvre sur un écran d'accueil qui annonce les étapes, celles qui sont facultatives et une durée indicative.
2. **Given** l'étape Copilot, **When** l'utilisateur n'est pas connecté, **Then** il ne peut pas aller plus loin ; une fois connecté (modèle choisi, accord d'envoi des données donné), l'étape est marquée faite et l'assistant passe à la suivante.
3. **Given** l'étape Jira, **When** l'utilisateur saisit l'adresse et le jeton et teste la connexion avec succès, **Then** l'étape est marquée faite ; **When** il choisit « Passer », **Then** l'étape est marquée passée et l'assistant continue.
4. **Given** l'étape Confluence, **When** l'utilisateur la remplit ou la passe, **Then** l'assistant continue de la même façon qu'à l'étape Jira.
5. **Given** l'étape du premier projet, **When** l'utilisateur le nomme (clé Jira proposée seulement si Jira est connecté), **Then** le projet est créé et devient le projet courant.
6. **Given** l'étape des sources, **When** l'utilisateur ajoute une ou plusieurs sources de documentation (selon les connexions faites : site Antora, dossier SharePoint, racine Confluence) et le modèle d'architecture, ou passe l'étape, **Then** l'assistant continue.
7. **Given** l'étape d'indexation, **When** le projet a au moins une source ou une clé Jira, **Then** l'indexation part et sa progression s'affiche source par source ; à la fin, l'assistant indique ce qui a été indexé et propose d'ouvrir la conversation.
8. **Given** l'étape d'indexation, **When** le projet n'a ni source ni clé Jira, **Then** l'assistant explique que l'agent ne connaîtra pas le projet et où ajouter des sources plus tard, puis propose d'ouvrir la conversation.
9. **Given** l'assistant interrompu à une étape (onglet fermé ou outil arrêté), **When** l'utilisateur rouvre l'outil, **Then** l'assistant reprend à cette étape, avec les étapes précédentes marquées faites ou passées.
10. **Given** une installation qui a déjà au moins un projet, **When** l'utilisateur ouvre l'outil, **Then** l'assistant ne s'ouvre pas ; il reste relançable depuis Configuration.
11. **Given** l'assistant relancé depuis Configuration, **When** il s'ouvre, **Then** chaque étape montre l'état actuel (Copilot connecté, Jira connecté, projets existants) et l'utilisateur peut créer un nouveau projet ou reprendre les sources d'un projet existant.

---

### User Story 2 - Visiter l'outil dans l'ordre d'usage (Priority: P2)

À la fin de l'assistant, l'outil propose une visite guidée. Elle parcourt les écrans principaux
dans l'ordre où l'utilisateur s'en servira : conversation, spécification et discussion, tâches et
suivi par les agents, vue Jira, connaissance du projet, réglages avancés et passerelle MCP. À chaque
arrêt, une bulle désigne un élément de l'écran et explique son rôle. L'utilisateur avance, recule
ou quitte à tout moment, et relance la visite depuis le menu (« Visite guidée »).

**Why this priority**: elle donne la logique d'ensemble de l'outil, mais elle n'a de sens qu'une
fois l'outil configuré, et l'outil reste utilisable sans elle.

**Independent Test**: avec un projet configuré, lancer la visite depuis le menu, la suivre de bout
en bout au clavier : chaque arrêt affiche l'écran attendu et désigne un élément visible ; la
quitter au milieu ramène l'utilisateur où il était, sans rien avoir modifié.

**Acceptance Scenarios**:

1. **Given** l'assistant terminé, **When** l'utilisateur arrive à son dernier écran, **Then** l'outil propose « Faire la visite guidée » ou « Plus tard ».
2. **Given** la visite lancée, **When** l'utilisateur avance, **Then** l'outil ouvre l'écran de l'arrêt suivant, met en valeur l'élément désigné et affiche la bulle, avec la position dans la visite (« 3 / 9 ») et les commandes Précédent, Suivant et Quitter.
3. **Given** un arrêt dont l'élément n'existe pas encore (aucune spécification, aucune tâche, aucun ticket), **When** la visite y arrive, **Then** la bulle désigne l'écran ou son état vide et explique ce qui y apparaîtra.
4. **Given** la visite en cours, **When** l'utilisateur quitte (bouton ou touche Échap), **Then** la visite se ferme et l'utilisateur revient à l'écran où il l'avait lancée.
5. **Given** la visite, **When** l'utilisateur la suit de bout en bout, **Then** aucune donnée n'est créée, modifiée ni envoyée à un service (Copilot, Jira, Confluence) du fait de la visite.
6. **Given** le menu, **When** l'utilisateur choisit « Visite guidée », **Then** la visite démarre au premier arrêt, qu'il l'ait déjà faite ou non.
7. **Given** aucun projet configuré, **When** l'utilisateur lance la visite, **Then** les arrêts qui demandent un projet expliquent l'écran sans l'ouvrir et invitent à configurer un projet.

---

### User Story 3 - Installer l'outil depuis les sources sans aide (Priority: P3)

Un nouvel utilisateur qui n'a que le dépôt suit le guide d'installation, du clonage au premier
lancement, sans demander d'aide : prérequis à installer (JDK, Node, Copilot CLI) avec leurs
versions et la façon de les vérifier, construction, lancement, ouverture de l'outil, et ce qu'il
faut faire quand une étape échoue.

**Why this priority**: l'assistant n'aide qu'une fois l'outil lancé ; sans un guide fiable, le
collègue bute avant de le voir. C'est un travail de documentation, indépendant des deux autres.

**Independent Test**: une personne qui n'a jamais lancé l'outil suit le guide sur un poste sans
JDK 25 ni Copilot CLI ; elle arrive à l'assistant de premier lancement sans autre aide que le
guide, et chaque difficulté rencontrée est reportée dans le guide.

**Acceptance Scenarios**:

1. **Given** le README, **When** un nouvel utilisateur cherche comment installer l'outil, **Then** une section « Installer depuis les sources » donne, dans l'ordre, les prérequis avec leur version minimale et la commande qui la vérifie, le clonage, la construction, le lancement et l'adresse où l'outil s'ouvre.
2. **Given** les pièges connus du poste (JDK par défaut trop ancien, proxy d'entreprise, Copilot CLI introuvable ou trop ancien), **When** l'utilisateur en rencontre un, **Then** le guide nomme le symptôme tel qu'il apparaît et la marche à suivre.
3. **Given** une mise à jour des sources, **When** l'utilisateur veut passer à la nouvelle version, **Then** le guide dit comment reconstruire et relancer en gardant ses données.

---

### Edge Cases

- Copilot CLI absent ou trop ancien à l'étape Copilot : l'étape le dit avec la marche à suivre du guide d'installation, et l'utilisateur la retente sans quitter l'assistant.
- Compte GitHub sans accès à Copilot : l'étape l'explique (abonnement, politique de l'organisation) et reste bloquante.
- Test de connexion Jira ou Confluence en échec : l'utilisateur corrige, retente ou passe l'étape ; rien n'est enregistré tant que le test n'a pas réussi ou que l'utilisateur n'a pas choisi d'enregistrer quand même, comme dans les cartes de connexion existantes.
- Projet créé depuis l'écran Projets pendant que l'assistant était interrompu : l'assistant ne s'ouvre plus de lui-même ; relancé, il propose de poursuivre avec ce projet.
- Indexation en échec sur une source (site injoignable, jeton refusé) : l'assistant affiche l'erreur de la source, les autres sources continuent, et l'utilisateur peut quand même ouvrir la conversation.
- Indexation longue : l'utilisateur peut ouvrir la conversation sans attendre la fin ; l'indexation continue et sa progression reste visible dans la connaissance du projet.
- Plusieurs onglets ouverts pendant l'assistant : chaque onglet affiche l'étape courante et l'état des étapes ; une étape faite dans un onglet l'est dans l'autre.
- Fenêtre trop étroite pour placer une bulle à côté de l'élément : la bulle se place au-dessus ou en dessous et reste lisible en entier.
- Élément désigné hors de la zone visible : la visite le fait défiler à l'écran avant d'afficher la bulle.
- Visite lancée pendant une conversation en cours de réponse : la visite n'interrompt ni ne relance la réponse.
- Installation mise à jour depuis une version antérieure à la feature, avec des projets : ni l'assistant ni la visite ne s'ouvrent d'eux-mêmes.

## Requirements *(mandatory)*

### Functional Requirements

**Assistant de premier lancement (US1)**

- **FR-001**: L'outil DOIT ouvrir l'assistant de premier lancement à son ouverture tant que la configuration initiale n'est pas terminée, c'est-à-dire tant que l'assistant n'a pas été mené à son terme et qu'aucun projet n'existe.
- **FR-002**: L'assistant DOIT enchaîner, dans cet ordre : accueil, connexion à Copilot, connexion à Jira, connexion à Confluence, premier projet, sources de documentation et modèle d'architecture, indexation, fin.
- **FR-003**: La connexion à Copilot (compte connecté, modèle choisi, accord d'envoi des données donné) DOIT être la seule étape obligatoire ; toutes les autres DOIVENT pouvoir être passées d'une seule action.
- **FR-004**: Chaque étape DOIT afficher son état (à faire, faite, passée) et l'assistant DOIT permettre de revenir à une étape précédente pour la refaire.
- **FR-005**: Les étapes DOIVENT reprendre le comportement, les contrôles et les messages des écrans existants (connexions de Configuration, création de projet, sources du projet), sans en diverger : une connexion ou une source faite dans l'assistant est la même que celle faite depuis ces écrans.
- **FR-006**: L'étape du premier projet DOIT proposer la clé Jira seulement si Jira est connecté, et l'étape des sources DOIT proposer seulement les sortes de sources utilisables avec les connexions faites (racine Confluence seulement si Confluence est connecté).
- **FR-007**: L'étape d'indexation DOIT lancer l'indexation du projet quand il a au moins une source ou une clé Jira, afficher sa progression source par source et son résultat, et permettre d'ouvrir la conversation avant la fin.
- **FR-008**: La progression de l'assistant (étape courante, étapes faites ou passées, projet créé) DOIT être conservée par l'outil, de sorte qu'un onglet fermé ou un outil arrêté reprenne à la même étape.
- **FR-009**: L'assistant DOIT pouvoir être relancé depuis Configuration ; relancé, il DOIT montrer l'état actuel de chaque étape et permettre de créer un projet de plus ou de compléter les sources d'un projet existant.
- **FR-010**: L'assistant DOIT se terminer sur une conversation du projet créé (ou du projet courant si aucune création), et proposer la visite guidée.
- **FR-011**: L'assistant DOIT être utilisable entièrement au clavier, annoncer chaque changement d'étape aux technologies d'assistance et respecter WCAG 2.1 AA.

**Visite guidée (US2)**

- **FR-012**: La visite DOIT être proposée à la fin de l'assistant et pouvoir être lancée à tout moment depuis le menu (« Visite guidée »).
- **FR-013**: La visite DOIT parcourir, dans cet ordre, la conversation, la spécification et sa discussion, les tâches et leur suivi par les agents, la vue Jira, la connaissance du projet, les réglages avancés et la passerelle MCP pour les agents de code.
- **FR-014**: À chaque arrêt, la visite DOIT ouvrir l'écran concerné, mettre en valeur un élément de l'écran et afficher une bulle qui explique son rôle, avec la position dans la visite et les commandes Précédent, Suivant et Quitter.
- **FR-015**: Quand l'élément d'un arrêt n'existe pas encore, la bulle DOIT désigner l'écran ou son état vide et expliquer ce qui y apparaîtra ; sans projet, les arrêts qui en demandent un DOIVENT l'expliquer et inviter à en configurer un.
- **FR-016**: La visite DOIT pouvoir être quittée à tout moment (commande ou touche Échap) et ramener l'utilisateur à l'écran d'où il l'avait lancée.
- **FR-017**: La visite NE DOIT créer, modifier ni envoyer aucune donnée.
- **FR-018**: La visite DOIT être utilisable entièrement au clavier, garder le focus dans la bulle tant qu'elle est ouverte, annoncer chaque arrêt aux technologies d'assistance et respecter WCAG 2.1 AA.

**Guide d'installation (US3)**

- **FR-019**: Le README DOIT contenir une section « Installer depuis les sources » qui donne, dans l'ordre : prérequis avec version minimale et commande de vérification, clonage, construction, lancement, adresse d'ouverture de l'outil, puis renvoi vers l'assistant de premier lancement.
- **FR-020**: Cette section DOIT décrire les difficultés connues de l'installation depuis les sources (JDK par défaut trop ancien, proxy d'entreprise, Copilot CLI introuvable ou trop ancien) par leur symptôme visible et leur marche à suivre.
- **FR-021**: Cette section DOIT dire comment mettre à jour les sources, reconstruire et relancer l'outil sans perdre ses données.
- **FR-022**: La section « Premier lancement » du README DOIT décrire l'assistant et la visite guidée.

### Key Entities

- **Progression de l'assistant** : étape courante, état de chaque étape (à faire, faite, passée), projet créé par l'assistant, assistant terminé ou non. Une seule pour l'installation.
- **Étape de l'assistant** : nom, caractère obligatoire ou facultatif, état ; s'appuie sur les connexions, projets et sources existants plutôt que sur des données propres.
- **Arrêt de la visite** : écran à ouvrir, élément désigné, texte de la bulle, texte de remplacement quand l'élément n'existe pas encore ou que l'arrêt demande un projet.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un nouvel utilisateur qui a déjà ses accès (compte Copilot, jeton Jira, adresse d'une source de documentation) passe de l'outil vierge à une conversation dont le projet est en cours d'indexation en moins de 10 minutes, sans ouvrir Configuration, Projets ni Réglages du projet.
- **SC-002**: Un nouvel utilisateur qui passe toutes les étapes facultatives arrive à une conversation en moins de 3 minutes après la connexion à Copilot.
- **SC-003**: La visite guidée complète se suit en moins de 5 minutes et chaque arrêt désigne un élément visible à l'écran.
- **SC-004**: Après une interruption à n'importe quelle étape, l'assistant reprend à la même étape dans 100 % des cas, outil redémarré ou non.
- **SC-005**: Un collègue qui n'a jamais lancé l'outil l'installe depuis les sources en suivant le seul README et arrive à l'assistant sans aide extérieure.
- **SC-006**: Une installation existante (au moins un projet) ne voit jamais l'assistant ni la visite s'ouvrir d'eux-mêmes après la mise à jour.

## Assumptions

- L'outil reste local et mono-utilisateur : une seule progression d'assistant par installation.
- La configuration initiale est réputée faite dès qu'un projet existe, ce qui couvre les installations antérieures à la feature.
- Les réglages avancés (modèle de recherche, accès des agents, navigateur, notifications, synchronisation automatique, racines de sources, fiabilité) gardent leurs valeurs par défaut pendant l'assistant ; la visite les montre.
- Le modèle de recherche par défaut est disponible sans réglage, de sorte que l'indexation lancée par l'assistant fonctionne sans étape de plus.
- Les accords d'envoi de données à Copilot et à Jira restent demandés comme aujourd'hui ; l'assistant les présente au moment où ils sont nécessaires.
- Les textes de l'assistant et de la visite sont en français, comme le reste de l'outil.
- L'installation depuis les sources vise macOS et Linux, les postes du mainteneur et de son collègue ; Windows est mentionné sans être vérifié.

## Hors périmètre

- Paquet d'installation distribué (installeurs signés) : prévu avec la première version.
- Aide contextuelle par écran (bulles à la première visite d'un écran, bouton « ? ») : écartée au profit de la visite enchaînée.
- Réglages avancés dans l'assistant.
- Import de la configuration d'une autre installation (projets, sources, connexions).
- Visite guidée de la configuration elle-même (couverte par l'assistant).
