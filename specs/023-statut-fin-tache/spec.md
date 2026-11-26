# Feature Specification: Statut Jira d'une tâche implémentée, réglé par projet

**Feature Branch**: `feature/027-statut-fin-tache`

**Created**: 2026-11-26

**Status**: Draft

**Input**: User description: "Statut Jira d'arrivée d'une tâche implémentée, réglé par projet (option A retenue). Le workflow Jira des équipes est plus riche que les trois catégories de spec-writer (projet AURORA : Créée, Prête, En Cours, Réalisée, En cours de test, Testée, A accepter, A valider, Déployable, Fermée / Annulée). Aujourd'hui, complete_task passe la sous-tâche à la catégorie « terminé » : dans ce workflow il la ferme (AURORA-182, 183 et 184 sont passées à « Fermée »), alors qu'une tâche implémentée par un agent doit passer à l'état suivant du workflow (« Réalisée »). 1. Dans les réglages du projet, choisir le statut d'arrivée d'une tâche implémentée et les statuts où une tâche compte comme finie. 2. complete_task passe la tâche à ce statut, par une transition directe, jamais une annulation ni une chaîne ; sinon l'agent et l'utilisateur sont prévenus et rien ne change ; même règle au rejeu « à synchroniser ». 3. Une tâche dans un statut fini reste terminée dans spec-writer. 4. Sans réglage, comportement actuel inchangé."

## Contexte

Une feature découpée en tâches (015) est suivie dans Jira par des sous-tâches de sa story. Quand un
agent de code termine une tâche, spec-writer la passe à la catégorie Jira « terminé », celle des
tickets fermés. Il ne connaît que trois catégories : à faire, en cours, terminé.

Le workflow des équipes est plus riche. Sur le projet AURORA, il va de « Créée » à « Fermée » en
passant par Prête, En Cours, Réalisée, En cours de test, Testée, A accepter, A valider et
Déployable. Jira range tous les statuts de « En Cours » à « Déployable » dans la catégorie « en
cours » : seuls « Fermée » et « Annulée » sont « terminé ». Terminer une tâche la **ferme** donc,
alors que l'implémentation n'est qu'une étape. AURORA-182, AURORA-183 et AURORA-184 ont été fermées
ainsi, sans passer par les tests ni l'acceptation.

Jira n'ordonne pas les statuts d'un workflow. spec-writer ne peut pas deviner « l'état suivant » :
c'est l'utilisateur qui le désigne, une fois par projet.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Une tâche terminée par un agent passe au statut choisi, pas à « Fermée » (Priority: P1)

Dans les réglages du projet, le mainteneur choisit le statut Jira où arrive une tâche implémentée,
par exemple « Réalisée ». Quand un agent de code termine une tâche, spec-writer passe la sous-tâche
à ce statut, par une transition directe que Jira propose depuis son statut courant. Le compte rendu
de l'agent est toujours ajouté en commentaire.

**Why this priority**: c'est le défaut qui ferme aujourd'hui des sous-tâches avant les tests et
l'acceptation.

**Independent Test**: projet réglé sur « Réalisée », sous-tâche « En Cours » dont le workflow offre
« Réaliser » vers « Réalisée » et « Fermer » vers « Fermée » ; l'agent termine la tâche ; la
sous-tâche est « Réalisée » dans Jira, pas « Fermée ».

**Acceptance Scenarios**:

1. **Given** un projet réglé sur « Réalisée » et une sous-tâche « En Cours » dont le workflow
   propose une transition vers « Réalisée » et une vers « Fermée », **When** l'agent termine la
   tâche, **Then** spec-writer prend la transition vers « Réalisée », et la sous-tâche est
   « Réalisée » dans Jira.
2. **Given** le même projet et une sous-tâche dont le statut courant n'offre aucune transition
   directe vers « Réalisée », **When** l'agent termine la tâche, **Then** rien ne change dans Jira
   ni dans spec-writer, et l'agent reçoit un message qui nomme le statut courant, le statut visé et
   invite à faire la transition dans Jira.
3. **Given** une transition vers « Réalisée » qui est aussi une annulation (son nom dit
   « annuler »), **When** l'agent termine la tâche, **Then** spec-writer ne la prend pas.
4. **Given** Jira injoignable au moment où l'agent termine la tâche, **When** Jira redevient
   joignable, **Then** la transition rejouée vise « Réalisée », pas la catégorie « terminé ».
5. **Given** une sous-tâche déjà « Réalisée » (passée à la main dans Jira), **When** l'agent la
   termine, **Then** aucune transition n'est envoyée, et la tâche est terminée.
6. **Given** un projet sans réglage, **When** l'agent termine une tâche, **Then** le comportement
   est celui d'aujourd'hui : la catégorie « terminé ».

---

### User Story 2 - Une tâche « Réalisée » ou plus loin reste terminée dans spec-writer (Priority: P1)

Le mainteneur indique aussi les statuts où une tâche compte comme finie pour spec-writer : le statut
d'arrivée et ceux qui le suivent dans le workflow (En cours de test, Testée, A accepter, A valider,
Déployable). Les statuts de la catégorie « terminé » comptent toujours comme finis. Une tâche dans
l'un de ces statuts est terminée dans spec-writer : l'avancement la compte, `next_task` ne la
redonne pas à un agent, et le statut de la spécification suit.

**Why this priority**: sans elle, la relecture des statuts Jira, qui s'impose, verrait une tâche
« Réalisée » comme « en cours » (sa catégorie Jira), la sortirait de « terminé » et la redonnerait à
un agent.

**Independent Test**: tâche terminée par l'agent et passée à « Réalisée » ; à la relecture suivante,
elle reste terminée ; l'équipe la passe à « En cours de test » : elle reste terminée ; toutes les
tâches finies, la spécification passe à « implémentée ».

**Acceptance Scenarios**:

1. **Given** une tâche passée à « Réalisée » par spec-writer, **When** spec-writer relit les
   statuts dans Jira, **Then** la tâche reste terminée, et le statut Jira affiché est « Réalisée ».
2. **Given** une tâche « Réalisée » que l'équipe passe à « En cours de test », **When** spec-writer
   relit les statuts, **Then** elle reste terminée.
3. **Given** une tâche « Réalisée » que l'équipe renvoie à « En Cours » (statut non fini), **When**
   spec-writer relit les statuts, **Then** la tâche redevient « en cours », comme aujourd'hui quand
   Jira s'impose.
4. **Given** une feature dont toutes les tâches sont dans des statuts finis, **When** l'avancement
   est calculé, **Then** toutes comptent comme terminées et la spécification passe à
   « implémentée », comme aujourd'hui quand toutes sont fermées.
5. **Given** un agent qui demande la prochaine tâche, **When** les tâches restantes sont toutes
   dans des statuts finis, **Then** il n'y a plus de tâche à faire.

---

### User Story 3 - Régler le statut d'arrivée et les statuts finis du projet (Priority: P1)

Dans les réglages du projet, une rubrique « Suivi des tâches dans Jira » propose les statuts connus
du projet, lus dans l'index (ceux de ses tickets). Le mainteneur choisit le statut d'arrivée d'une
tâche implémentée, puis coche les statuts qui comptent comme finis. Le statut d'arrivée est
toujours compté comme fini, et les statuts de la catégorie « terminé » aussi, sans qu'on les coche.
Le réglage se retire pour revenir au comportement d'origine.

**Why this priority**: les deux stories précédentes en dépendent.

**Independent Test**: projet dont l'index compte les statuts d'AURORA ; la rubrique les propose
avec leur catégorie ; choisir « Réalisée » et cocher « En cours de test » et « Déployable »,
enregistrer, recharger : le réglage est gardé ; le retirer : l'agent revient au comportement
d'origine.

**Acceptance Scenarios**:

1. **Given** un projet dont l'index compte des tickets, **When** le mainteneur ouvre la rubrique,
   **Then** elle propose chaque statut connu une fois, avec sa catégorie (à faire, en cours,
   terminé), dans un ordre stable (catégorie, puis nom).
2. **Given** « Réalisée » choisi comme statut d'arrivée, **When** le mainteneur enregistre,
   **Then** « Réalisée » est compté comme fini, coché ou non, et les statuts « terminé » aussi.
3. **Given** un statut d'arrivée de la catégorie « à faire », **When** le mainteneur l'enregistre,
   **Then** le réglage est refusé, avec un message qui dit pourquoi.
4. **Given** un projet sans ticket indexé, **When** le mainteneur ouvre la rubrique, **Then** elle
   dit que les statuts s'afficheront après la synchronisation de Jira.
5. **Given** un réglage enregistré, **When** le mainteneur le retire, **Then** l'agent revient à la
   catégorie « terminé ».

---

### User Story 4 - La story de la feature suit ses tâches (Priority: P2)

Quand un agent prend la première tâche d'une feature et que la story liée est encore « à faire »
(par exemple « Créée » ou « Prête »), spec-writer passe la story en cours. Quand la dernière tâche
est implémentée, toutes les tâches de la feature étant alors dans un statut fini, spec-writer passe
la story au statut d'arrivée du projet. Il en va de même quand un agent déclare implémentée une
feature sans tâches. C'est la pratique courante des règles d'automatisation de Jira (la story suit
ses sous-tâches) : une story déjà au bon statut, ou plus loin, n'est pas touchée.

**Why this priority**: le tableau Jira de l'équipe montre ainsi l'avancement réel de la story, sans
geste manuel. Elle dépend des stories 1 et 3 (statut d'arrivée, statuts finis).

**Independent Test**: story « Prête » et trois sous-tâches ; l'agent prend la première : la story
passe « En Cours » ; il termine les trois : à la dernière, la story passe « Réalisée ».

**Acceptance Scenarios**:

1. **Given** une story « Prête » (catégorie « à faire »), **When** un agent prend la première tâche
   de la feature, **Then** spec-writer passe la story en cours, par une transition directe vers la
   catégorie « en cours », jamais une annulation.
2. **Given** une story déjà « En Cours » ou plus loin, **When** un agent prend une tâche, **Then**
   la story n'est pas touchée.
3. **Given** un projet réglé sur « Réalisée » et une feature dont il reste une tâche, **When**
   l'agent la termine, **Then** la story passe à « Réalisée », après la sous-tâche.
4. **Given** une story déjà dans un statut fini, **When** la dernière tâche est terminée, **Then**
   la story n'est pas touchée.
5. **Given** une feature sans tâches, liée à une story, dans un projet réglé, **When** un agent la
   déclare implémentée, **Then** la story passe à « Réalisée ».
6. **Given** une story sans transition directe vers le statut visé, ou Jira injoignable, **When**
   la story devrait bouger, **Then** la tâche ou la feature avance quand même dans spec-writer, la
   story reste où elle est, et l'agent et l'utilisateur en sont prévenus par un message. Le
   déplacement de la story n'est pas rejoué : c'est à faire dans Jira.
7. **Given** un projet sans réglage, **When** la dernière tâche est terminée, **Then** la story
   n'est pas déplacée, pour ne jamais la fermer ; le démarrage, lui, s'applique.

---

### Edge Cases

- Un statut choisi qui n'existe plus dans le workflow (renommé dans Jira) : la tâche n'est pas
  déplacée, et le message dit qu'aucune transition ne mène au statut visé.
- Deux statuts de même nom dans des catégories différentes : le nom désigne le statut ; le choix
  porte sur le nom affiché par Jira, sans tenir compte de la casse ni des accents différents.
- Une tâche libérée par l'utilisateur (`released`) ou reprise après la fermeture d'une conversation
  n'est pas concernée : seule la fin d'une tâche vise le statut d'arrivée.
- La prise d'une tâche (`start_task`) garde son comportement : la catégorie « en cours ». Si la
  transition vers « en cours » mène à un statut fini (par exemple directement « Réalisée »), elle
  n'est pas prise pour démarrer.
- Le statut de la spécification (« implémentée ») suit les tâches finies ; la story liée suit
  aussi (story 4), sauf sur un projet sans réglage.
- Une feature liée à une sous-tâche Jira (importée depuis une sous-tâche) n'a pas de tâches : c'est
  ce ticket qui passe au statut d'arrivée quand l'agent la déclare implémentée.
- Si l'équipe a déjà des règles d'automatisation Jira qui font suivre la story, spec-writer ne fait
  rien de plus : la story est déjà au bon statut.
- Le réglage s'applique aux tâches de toutes les features du projet, y compris celles déjà en
  cours.

## Requirements *(mandatory)*

### Functional Requirements

**Réglage du projet**

- **FR-001**: Chaque projet DOIT pouvoir porter un statut d'arrivée d'une tâche implémentée, et une
  liste de statuts finis ; les deux sont facultatifs.
- **FR-002**: Les réglages du projet DOIVENT proposer les statuts connus du projet, lus dans
  l'index (statuts de ses tickets), chacun une fois, avec sa catégorie, dans l'ordre catégorie
  puis nom.
- **FR-003**: Le statut d'arrivée DOIT être de la catégorie « en cours » ou « terminé » ; un statut
  « à faire » est refusé avec un message qui dit pourquoi.
- **FR-004**: Le statut d'arrivée DOIT toujours compter comme fini, et les statuts de la catégorie
  « terminé » aussi, sans qu'on les coche.
- **FR-005**: Le réglage DOIT pouvoir être retiré ; sans réglage, la fin d'une tâche vise la
  catégorie « terminé », comme aujourd'hui.

**Fin d'une tâche**

- **FR-006**: Quand un agent termine une tâche d'un projet réglé, spec-writer DOIT la passer au
  statut d'arrivée par une transition directe proposée par Jira depuis son statut courant ; jamais
  une annulation, jamais une chaîne de transitions.
- **FR-007**: Sans transition directe vers le statut d'arrivée, rien NE DOIT changer, ni dans Jira
  ni dans spec-writer ; l'agent DOIT recevoir un message qui nomme le statut courant et le statut
  visé, et invite à faire la transition dans Jira.
- **FR-008**: Une sous-tâche déjà dans un statut fini NE DOIT recevoir aucune transition ; la tâche
  est terminée.
- **FR-009**: La transition rejouée après une coupure de Jira (« à synchroniser ») DOIT viser le
  même statut d'arrivée que la fin qui l'a créée.
- **FR-010**: Le compte rendu de l'agent DOIT toujours être ajouté en commentaire, comme
  aujourd'hui.

**Statuts finis**

- **FR-011**: À la relecture des statuts Jira, une tâche dans un statut fini DOIT être terminée
  dans spec-writer, quelle que soit sa catégorie Jira ; une tâche dans un autre statut suit sa
  catégorie, comme aujourd'hui.
- **FR-012**: L'avancement d'une feature, le choix de la prochaine tâche et le statut de la
  spécification DOIVENT compter une tâche dans un statut fini comme terminée.
- **FR-013**: Le statut Jira affiché d'une tâche (vue des tâches, réponses MCP) DOIT rester son
  vrai statut (« Réalisée »), pas la catégorie.

**Story de la feature**

- **FR-014**: Quand un agent prend une tâche et que la story liée à la feature est dans la
  catégorie « à faire », spec-writer DOIT la passer en cours par une transition directe vers la
  catégorie « en cours », jamais une annulation ; une story en cours ou plus loin n'est pas
  touchée.
- **FR-015**: Quand toutes les tâches d'une feature d'un projet réglé sont dans un statut fini
  après la fin d'une tâche, spec-writer DOIT passer la story au statut d'arrivée, par une
  transition directe, sauf si elle est déjà dans un statut fini.
- **FR-016**: Quand un agent déclare implémentée une feature sans tâches d'un projet réglé,
  spec-writer DOIT passer son ticket lié au statut d'arrivée, selon la même règle.
- **FR-017**: Un déplacement de la story impossible (pas de transition directe, refus ou Jira
  injoignable) NE DOIT PAS empêcher la tâche ou la feature d'avancer dans spec-writer ; l'agent et
  l'utilisateur DOIVENT en être prévenus ; le déplacement n'est pas rejoué.
- **FR-018**: Sur un projet sans réglage, la story NE DOIT PAS être déplacée à la fin (jamais
  fermée par l'outil) ; le passage en cours au démarrage s'applique.

### Key Entities

- **Réglage du suivi des tâches** (par projet) : statut d'arrivée d'une tâche implémentée, et
  statuts finis. Facultatif.
- **Statut connu** : un statut Jira vu dans l'index du projet, avec sa catégorie (à faire, en
  cours, terminé).
- **Tâche de feature** (015) : sous-tâche Jira suivie par spec-writer, avec son statut dans
  spec-writer (à faire, en cours, terminée, introuvable) et son statut Jira.
- **Story de la feature** : le ticket Jira lié à la feature, parent de ses tâches.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Sur un projet réglé, 0 sous-tâche fermée par la fin d'une tâche d'agent : 100 % des
  tâches terminées arrivent au statut choisi, ou restent inchangées avec un message.
- **SC-002**: Une tâche arrivée dans un statut fini n'est jamais redonnée à un agent, sur toute la
  suite du workflow (tests, acceptation, déploiement).
- **SC-003**: Régler le projet prend moins d'une minute : choisir un statut et cocher les suivants,
  sans saisie libre.
- **SC-004**: Sur un projet sans réglage, aucun changement observable à la fin d'une tâche ; seule
  la story « à faire » passe en cours au démarrage.
- **SC-005**: Sur le tableau Jira, une story dont un agent a commencé les tâches n'est jamais
  « à faire », et une story dont toutes les tâches sont finies arrive au statut d'arrivée sans
  geste manuel.

## Assumptions

- Option A retenue : le statut d'arrivée est réglé par projet, parmi les statuts vus dans l'index.
  spec-writer ne lit ni l'ordre des colonnes d'un board, ni le workflow complet dans Jira.
- Un même réglage vaut pour toutes les sous-tâches du projet : un projet Jira a un seul workflow de
  sous-tâches pour le besoin décrit.
- Un statut est désigné par son nom tel que Jira l'affiche ; la comparaison ignore la casse et les
  accents.
- La prise d'une tâche (`start_task`) et la libération par l'utilisateur gardent leur comportement
  (catégories « en cours » et « à faire »).
- Les sous-tâches déjà fermées à tort (AURORA-182 à 184) ne sont pas rouvertes par l'outil : c'est
  à faire dans Jira si l'équipe le souhaite.
- La story suit ses tâches (réponse du 26/11, option A, et ajout du passage en cours au
  démarrage) : c'est la pratique courante des règles d'automatisation de Jira. Une story déjà au
  bon statut n'est pas touchée, ce qui évite tout conflit avec des règles Jira existantes.
