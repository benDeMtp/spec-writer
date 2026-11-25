# Feature Specification: Cycle de vie de l'outil installé (lancer, rouvrir, quitter)

**Feature Branch**: `feature/023-cycle-de-vie`

**Created**: 2026-11-20

**Status**: Draft

**Input**: User description: "Feature 019 : cycle de vie de l'outil installé (lancer, rouvrir, quitter). Constats du 20/11 : rien n'arrête l'outil depuis l'interface et fermer l'onglet ne l'arrête pas ; sur macOS, relancer une application déjà ouverte ne fait que l'activer, sans nouvel onglet ; sous Windows et Linux, un second lancement démarre une seconde instance sur un autre port avec les mêmes données. Décision de l'utilisateur : (1) commande « Quitter spec-writer » dans le bloc Application du menu, qui arrête proprement l'outil, avertit si un travail est en cours et laisse un écran « spec-writer est arrêté » ; Quitter depuis le Dock de macOS arrête aussi proprement ; (2) sur macOS, un clic sur l'icône du Dock ou une relance rouvre un onglet avec un nouveau jeton ; (3) une seule instance par dossier de données : un second lancement demande à l'instance en marche d'ouvrir un onglet, puis s'arrête ; un verrou abandonné après un arrêt brutal ne bloque pas le démarrage. Vaut aussi pour le lancement depuis les sources ; ne gêne ni le dev mode ni les E2E ; README mis à jour. Hors périmètre : icône de barre des menus ou de zone de notification, arrêt automatique après inactivité, démarrage à l'ouverture de session, signature des installeurs."

## Contexte

spec-writer n'a pas de fenêtre à lui. Au lancement, il démarre un serveur sur le poste, ouvre le
navigateur sur une adresse qui porte un jeton à usage unique, puis tourne en tâche de fond.
L'interface est l'onglet du navigateur.

Ce fonctionnement laisse trois trous, relevés le 20/11 avant la publication des premiers
installeurs (`.dmg`, `.msi`, `.deb`) :

- **On ne peut pas l'arrêter.** Aucune commande ne le fait : il n'y a ni bouton ni icône dans la
  barre des menus. Fermer l'onglet le laisse tourner. Il ne reste que le Moniteur d'activité,
  le Gestionnaire des tâches ou la commande `kill`.
- **On ne peut pas y revenir sur macOS.** Le README dit de rouvrir l'outil depuis son lanceur
  pour obtenir une nouvelle adresse. Mais macOS ne relance pas une application déjà ouverte : il
  la met seulement au premier plan, et aucun onglet ne s'ouvre. Un utilisateur qui a fermé son
  onglet doit tuer le processus pour revenir.
- **Deux instances peuvent tourner ensemble.** Sous Windows et Linux, un second lancement en
  démarre une seconde, sur un autre port, avec la même base et le même dossier de données. Les
  deux écrivent dans la même base, et les synchronisations automatiques sont lancées deux fois.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Quitter spec-writer depuis l'outil (Priority: P1)

L'utilisateur a fini sa journée. Dans le menu, bloc Application, il choisit « Quitter
spec-writer ». Si rien n'est en cours, l'outil s'arrête. Si une indexation, une génération de
spécification ou une réponse de l'agent est en cours, l'outil le dit et lui demande de confirmer.
Une fois l'outil arrêté, l'onglet affiche « spec-writer est arrêté » et indique comment le
relancer. Sur macOS, Quitter depuis le Dock (clic droit › Quitter, ou ⌘Q) arrête l'outil de la
même façon.

**Why this priority**: sans commande d'arrêt, un utilisateur non technicien ne peut pas arrêter
un outil qu'il vient d'installer. C'est le trou le plus visible, et il concerne les trois
systèmes.

**Independent Test**: lancer l'outil, choisir « Quitter spec-writer », vérifier que le processus
est arrêté et que l'onglet affiche l'écran d'arrêt. Recommencer pendant une indexation et vérifier
l'avertissement.

**Acceptance Scenarios**:

1. **Given** l'outil lancé sans travail en cours, **When** l'utilisateur choisit « Quitter
   spec-writer », **Then** une confirmation courte est demandée. Après confirmation, l'outil
   s'arrête en moins de 10 secondes et l'onglet affiche « spec-writer est arrêté » avec la
   manière de le relancer.
2. **Given** une indexation en cours, **When** l'utilisateur choisit « Quitter spec-writer »,
   **Then** la confirmation nomme le travail en cours et dit ce qu'il deviendra. « Annuler »
   laisse l'outil tourner, l'indexation continue. « Quitter quand même » arrête l'outil.
3. **Given** une réponse de l'agent ou une génération en cours, **When** l'utilisateur confirme
   « Quitter quand même », **Then** le texte déjà reçu est gardé et marqué incomplet, comme avec
   « Arrêter ».
4. **Given** plusieurs onglets ouverts sur l'outil, **When** l'outil s'arrête depuis l'un d'eux,
   **Then** chaque autre onglet affiche l'écran d'arrêt à sa prochaine action, ou dans les
   15 secondes s'il est au premier plan.
5. **Given** l'outil installé sur macOS, **When** l'utilisateur choisit Quitter dans le menu de
   l'icône du Dock (ou ⌘Q avec l'application au premier plan), **Then** l'outil s'arrête comme au
   scénario 1. Si un travail est en cours, une boîte de dialogue du système demande la même
   confirmation.
6. **Given** l'outil lancé depuis les sources dans un terminal, **When** l'utilisateur choisit
   « Quitter spec-writer », **Then** l'outil s'arrête et le terminal rend la main.
7. **Given** l'outil arrêté, **When** l'utilisateur le relance par son raccourci, **Then** il
   retrouve ses données, ses réglages et ses conversations, et un nouvel onglet s'ouvre.

---

### User Story 2 - Une seule instance, que chaque lancement rouvre (Priority: P1)

L'utilisateur a fermé son onglet sans quitter l'outil. Il relance spec-writer depuis son
raccourci : menu Démarrer, lanceur Linux, Launchpad ou script depuis les sources. Aucune seconde
instance ne démarre. L'outil déjà lancé ouvre un nouvel onglet avec un nouveau jeton, et le second
lancement s'arrête aussitôt.

**Why this priority**: c'est le seul moyen de revenir dans l'outil sous Windows et Linux. C'est
aussi ce qui évite que deux instances écrivent dans la même base.

**Independent Test**: lancer l'outil, fermer l'onglet, le relancer. Vérifier qu'un seul processus
tourne, qu'un onglet s'est ouvert et que la session y est ouverte.

**Acceptance Scenarios**:

1. **Given** l'outil lancé, **When** l'utilisateur le relance avec le même dossier de données,
   **Then** aucune seconde instance ne démarre. Un nouvel onglet s'ouvre sur l'outil en marche,
   déjà connecté, en moins de 5 secondes, et le second lancement se termine.
2. **Given** l'outil lancé depuis les sources dans un premier terminal, **When** l'utilisateur
   lance le script dans un second terminal, **Then** le second terminal affiche que spec-writer
   est déjà lancé, qu'un onglet a été ouvert, et rend la main.
3. **Given** un navigateur choisi dans Configuration, **When** un second lancement rouvre
   l'outil, **Then** l'onglet s'ouvre dans ce navigateur, comme au démarrage.
4. **Given** un poste sans bureau (affichage de l'adresse au lieu de l'ouverture du navigateur),
   **When** l'outil est relancé, **Then** le second lancement affiche dans sa console l'adresse
   d'ouverture avec un nouveau jeton.
5. **Given** l'outil arrêté brutalement (coupure, processus tué), **When** l'utilisateur le
   relance, **Then** l'outil démarre normalement : la trace de l'instance disparue ne bloque pas
   le démarrage.
6. **Given** une instance qui tourne mais ne répond plus, **When** l'utilisateur relance l'outil,
   **Then** aucune seconde instance ne démarre. Un message visible, même sans terminal, dit que
   spec-writer est déjà lancé mais ne répond pas, et comment l'arrêter.
7. **Given** deux dossiers de données différents (outil installé et dev mode, ou instance de
   test sur une base neuve), **When** les deux sont lancés, **Then** ils tournent côte à côte.
   L'instance unique vaut par dossier de données.
8. **Given** l'outil lancé, **When** un agent de code démarre la passerelle MCP, **Then** la
   passerelle fonctionne comme avant : elle n'est pas un second lancement de l'outil.

---

### User Story 3 - Rouvrir l'outil depuis le Dock de macOS (Priority: P2)

Sur macOS, l'utilisateur a fermé son onglet. Il clique sur l'icône de spec-writer dans le Dock, ou
le relance depuis le Launchpad ou le Finder. Un nouvel onglet s'ouvre sur l'outil en marche, déjà
connecté.

**Why this priority**: sur macOS, relancer une application ouverte ne déclenche pas de second
lancement. L'US2 ne suffit donc pas, et le clic sur le Dock est le geste naturel. Elle vient après
l'US1 et l'US2 parce qu'elle ne concerne qu'un système.

**Independent Test**: sur macOS avec l'application installée, fermer l'onglet, cliquer sur
l'icône du Dock, vérifier qu'un onglet s'ouvre déjà connecté.

**Acceptance Scenarios**:

1. **Given** l'outil installé sur macOS et lancé, **When** l'utilisateur clique sur son icône dans
   le Dock, **Then** un nouvel onglet s'ouvre déjà connecté, en moins de 5 secondes.
2. **Given** l'outil lancé, **When** l'utilisateur ouvre de nouveau l'application depuis le
   Launchpad ou le Finder, **Then** le résultat est le même qu'au scénario 1.
3. **Given** un onglet de l'outil déjà ouvert, **When** l'utilisateur clique sur l'icône du Dock,
   **Then** un nouvel onglet s'ouvre aussi. L'outil ne cherche pas à retrouver l'onglet existant.

---

### Edge Cases

- **Double clic sur Quitter, ou Quitter depuis deux onglets.** L'arrêt n'est fait qu'une fois et
  aucune erreur n'est affichée.
- **Relance pendant l'arrêt.** L'utilisateur relance l'outil pendant qu'il s'arrête. Le nouveau
  lancement attend la fin de l'arrêt, au plus 15 secondes, puis démarre normalement. Il ne s'adresse
  pas à l'instance qui s'arrête.
- **Deux lancements simultanés** (double clic rapide sur le raccourci). Une seule instance démarre ;
  l'autre lancement se comporte comme un second lancement.
- **Dossier de données en lecture seule ou plein.** Le démarrage échoue avec un message qui nomme le
  dossier, comme aujourd'hui. Cet échec ne doit pas être pris pour une instance déjà lancée.
- **Synchronisation automatique en cours au moment de Quitter.** Elle compte comme un travail en
  cours (une indexation) : elle est nommée dans la confirmation.
- **Onglet resté ouvert après un arrêt brutal.** Il affiche l'écran d'arrêt au lieu d'erreurs
  répétées.
- **Adresse d'ouverture réutilisée.** Un utilisateur rouvre une ancienne adresse depuis
  l'historique du navigateur. Le jeton est déjà consommé : l'écran de session perdue s'affiche,
  et il indique maintenant de relancer l'outil depuis son raccourci pour ouvrir un onglet.
- **Lancement par un autre compte** du même poste. Chaque compte a son propre dossier de données :
  l'autre compte ne peut ni joindre l'instance en marche ni lui demander un jeton.

## Requirements *(mandatory)*

### Functional Requirements

**Quitter**

- **FR-001**: Le bloc Application du menu MUST proposer « Quitter spec-writer », utilisable au
  clavier.
- **FR-002**: Quitter MUST demander une confirmation. Si un travail est en cours, la confirmation
  MUST le nommer (indexation ou synchronisation, génération, réponse de l'agent, proposition d'une
  nouvelle version) et dire ce qu'il deviendra. Elle MUST être annonçable par un lecteur d'écran.
- **FR-003**: Un arrêt MUST être propre : les travaux en cours sont interrompus comme par leurs
  commandes d'arrêt existantes, le texte déjà reçu est gardé et marqué incomplet, et la base reste
  cohérente.
- **FR-004**: L'outil MUST être arrêté au plus 10 secondes après la confirmation, travaux en cours
  compris.
- **FR-005**: Après l'arrêt, l'onglet d'origine MUST afficher « spec-writer est arrêté », avec la
  manière de le relancer pour le système de l'utilisateur. Les autres onglets MUST afficher le même
  écran à leur prochaine action, ou dans les 15 secondes s'ils sont au premier plan.
- **FR-006**: Sur macOS, Quitter depuis le Dock ou par ⌘Q MUST produire le même arrêt propre. Si un
  travail est en cours, une boîte de dialogue du système MUST demander la même confirmation qu'en
  FR-002.
- **FR-007**: Lancé depuis les sources dans un terminal, l'outil arrêté par Quitter MUST rendre la
  main au terminal, avec un code de sortie de succès.

**Instance unique et réouverture**

- **FR-008**: Une seule instance de l'outil MUST tourner par dossier de données.
- **FR-009**: Un lancement qui trouve une instance en marche sur son dossier de données MUST lui
  demander d'ouvrir un nouvel onglet, avec un nouveau jeton de session à usage unique, dans le
  navigateur configuré. Ce lancement MUST ensuite se terminer sans démarrer de serveur.
- **FR-010**: Si le poste est configuré pour afficher l'adresse au lieu d'ouvrir le navigateur, le
  second lancement MUST afficher l'adresse avec le nouveau jeton dans sa propre console.
- **FR-011**: Seul le compte qui a lancé l'instance MUST pouvoir lui demander un nouveau jeton.
  Ni une page web, ni un autre compte du poste, ni un autre processus sans accès au dossier de
  données ne MUST pouvoir l'obtenir. Le jeton MUST n'être écrit ni dans les journaux ni sur le
  disque.
- **FR-012**: La trace d'une instance disparue sans s'arrêter (arrêt brutal) MUST être reconnue
  comme abandonnée, et le lancement suivant MUST démarrer normalement, sans intervention.
- **FR-013**: Si une instance tourne mais ne répond pas en 5 secondes, le lancement MUST s'arrêter
  sans démarrer de seconde instance. Il MUST afficher un message, même sans terminal (boîte de
  dialogue du système), qui dit que spec-writer est déjà lancé mais ne répond pas et comment
  l'arrêter.
- **FR-014**: Deux lancements simultanés sur le même dossier de données MUST aboutir à une seule
  instance.
- **FR-015**: Sur macOS, avec l'outil lancé, un clic sur l'icône du Dock ou une nouvelle ouverture
  de l'application (Launchpad, Finder) MUST ouvrir un nouvel onglet déjà connecté, comme en FR-009.
- **FR-016**: La passerelle MCP des agents de code MUST rester indépendante de l'instance unique.
  Elle continue de joindre l'instance en marche comme aujourd'hui.

**Environnements et documentation**

- **FR-017**: Le comportement MUST être le même pour les installeurs (macOS, Windows, Linux) et
  pour le lancement depuis les sources (scripts `.sh` et `.cmd`), sauf le Dock, propre à macOS.
- **FR-018**: Le dev mode et les tests de bout en bout MUST continuer de fonctionner : ils ont
  leurs propres dossiers de données. Le rechargement à chaud du dev mode ne MUST pas être pris
  pour un second lancement.
- **FR-019**: L'écran de session perdue MUST indiquer de relancer l'outil depuis son raccourci pour
  ouvrir un onglet, ce qui est désormais vrai sur les trois systèmes.
- **FR-020**: Le README MUST décrire le lancement, la réouverture d'un onglet fermé et l'arrêt,
  pour les installeurs et pour l'installation depuis les sources. La mention « fermer le terminal
  arrête l'outil » reste vraie et MUST être gardée.

### Key Entities

- **Instance en marche** : l'outil lancé sur un dossier de données. Il laisse dans ce dossier une
  trace lisible par le seul utilisateur : de quoi la joindre sur le poste, la preuve qu'elle est
  vivante, et le secret qui l'authentifie. Cette trace disparaît à l'arrêt propre et est reconnue
  comme abandonnée après un arrêt brutal.
- **Demande d'ouverture** : envoyée par un second lancement à l'instance en marche, authentifiée
  par le secret de la trace. Elle produit un nouveau jeton de session à usage unique et l'ouverture
  d'un onglet.
- **Travail en cours** : indexation ou synchronisation, génération, réponse de l'agent, proposition
  d'une nouvelle version. C'est ce qui rend Quitter interruptif, et c'est ce que la confirmation
  nomme.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Sur les trois systèmes, un utilisateur arrête l'outil depuis l'interface en
  2 actions au plus (Quitter, puis confirmer), sans passer par un terminal ni par le gestionnaire
  des tâches.
- **SC-002**: Après avoir fermé son onglet, un utilisateur retrouve l'outil connecté par son geste
  habituel en moins de 5 secondes, dans 100 % des essais : raccourci sur les trois systèmes, Dock
  sur macOS.
- **SC-003**: Sur 20 relances avec l'outil déjà lancé, simultanées comprises, une seule instance
  tourne à chaque fois.
- **SC-004**: Après un arrêt brutal (processus tué), le lancement suivant démarre l'outil sans
  intervention, dans 100 % des essais.
- **SC-005**: Après un Quitter pendant une réponse de l'agent, le texte déjà reçu est retrouvé au
  lancement suivant, marqué incomplet, et la base est intacte.
- **SC-006**: Le jeton d'un nouvel onglet ne peut être obtenu que par le compte qui a lancé l'outil.
  Une requête d'une page web ou sans le secret de la trace est refusée.

## Assumptions

- Sur macOS, l'application installée a une icône dans le Dock tant qu'elle tourne, car l'ouverture
  du navigateur charge la partie graphique de Java. C'est ce qui rend possibles FR-006 et FR-015.
  Le plan le vérifiera. Les menus du Dock et de l'application restent ceux que macOS fournit par
  défaut.
- Un lancement depuis les sources dans un terminal reste un processus de premier plan : fermer le
  terminal ou faire Ctrl+C l'arrête toujours. Ctrl+C donne le même arrêt propre que Quitter, sans
  confirmation.
- La confirmation de Quitter est demandée même sans travail en cours. Le menu Application est
  proche d'autres commandes, et un arrêt involontaire coûte un relancement.
- La réouverture ouvre toujours un nouvel onglet. Retrouver et mettre au premier plan un onglet
  existant n'est pas possible de façon fiable depuis le poste.
- Les travaux interrompus par Quitter reprennent comme après tout arrêt de l'outil. Une indexation
  interrompue est marquée comme telle et se relance depuis Connaissance du projet. Cette feature ne
  change pas la reprise des travaux.
- Les tests de bout en bout couvrent Quitter et l'instance unique avec le jar construit. Le Dock de
  macOS ne s'automatise pas en CI : il est vérifié à la main sur l'application installée, par le
  quickstart.
- Hors périmètre : icône dans la barre des menus ou la zone de notification, arrêt automatique
  après inactivité, démarrage à l'ouverture de session, signature des installeurs.
