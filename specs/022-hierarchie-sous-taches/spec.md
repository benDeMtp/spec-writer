# Feature Specification: Hiérarchie des tâches et sous-tâches Jira

**Feature Branch**: `feature/026-hierarchie-sous-taches`

**Created**: 2026-11-25

**Status**: Draft

**Input**: User description: "Hiérarchie tâches / sous-tâches Jira dans la vue Tickets Jira et dans la passerelle MCP. 1. Vue « Tickets Jira » : les sous-tâches ne sont plus des lignes à part ; elles sont repliées sous leur ticket parent (fermées par défaut, ouvrables), le tableau garde ses colonnes, son tri (Modifié le, Statut, Synchro) et ses pages de 50 ; tri et pagination portent sur les tickets de premier niveau, les sous-tâches suivent leur parent. Une sous-tâche dont le parent n'est pas indexé reste au premier niveau avec la clé de son parent. La hiérarchie vient du lien parent déjà indexé ; les autres liens (Lier, Gantt, Cloners…) n'en font pas partie. Aujourd'hui l'index compte deux niveaux : story (ou story technique) et ses sous-tâches. 2. MCP : l'agent de code ne travaille que sur ce qui est passé par spec-writer (features validées ou importées, et leurs tâches). Chaque tâche (next_task, start_task, get_feature) et le ticket Jira d'une feature exposent leur type de ticket et la clé de leur parent quand ce sont des sous-tâches. Les tâches d'une feature sont des sous-tâches de la story liée ; une feature importée depuis une sous-tâche Jira a elle-même un parent. 3. MCP : le serveur instruit l'agent que si une branche doit être créée en travaillant sur une sous-tâche, tout le travail des sous-tâches se fait sur la branche du ticket parent (consigne dans les instructions du serveur et dans les réponses de next_task / start_task, nom de branche non imposé)."

## Contexte

La vue « Tickets Jira » liste les tickets du projet dans un tableau plat, les plus récemment
modifiés d'abord, par pages de 50. Les sous-tâches y sont des lignes comme les autres, mêlées aux
stories : rien ne dit à quelle story appartient une sous-tâche, et une story et ses sous-tâches
tombent souvent sur des pages différentes. Sur un projet réel, l'index compte 325 sous-tâches pour
environ 2 600 tickets, toutes rattachées à une story ou une story technique dont le lien parent est
déjà connu de l'outil.

L'agent de code reçoit les tâches d'une feature par la passerelle MCP. Ces tâches sont des
sous-tâches Jira de la story liée à la feature, mais rien ne le lui dit. Faute de savoir qu'il
travaille sur une sous-tâche, et de quel ticket, il peut créer une branche par sous-tâche, alors que
l'équipe veut tout le travail d'une story sur une seule branche.

Cette feature rend la hiérarchie visible dans la vue Jira, la transmet à l'agent de code, et lui
donne la consigne de branche.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Voir les sous-tâches sous leur ticket parent (Priority: P1)

Le mainteneur ouvre la vue « Tickets Jira ». Chaque ticket qui a des sous-tâches indexées affiche
leur nombre et se déplie pour les montrer, sous lui, en retrait. Les sous-tâches ne sont plus des
lignes du premier niveau. Le tableau garde ses colonnes, son tri et ses pages ; le tri et la
pagination portent sur les tickets du premier niveau.

**Why this priority**: c'est la demande de départ. Sans elle, une story et ses sous-tâches restent
éparpillées dans la liste, souvent sur plusieurs pages.

**Independent Test**: sur un projet dont l'index compte une story avec trois sous-tâches, la vue
montre la story avec « 3 sous-tâches » repliées ; un clic les montre sous elle ; aucune des trois
n'apparaît ailleurs dans la liste.

**Acceptance Scenarios**:

1. **Given** une story AURORA-24 avec trois sous-tâches indexées, **When** le mainteneur ouvre la
   vue, **Then** AURORA-24 est une ligne du premier niveau qui indique 3 sous-tâches, repliées, et
   aucune des trois sous-tâches n'est une ligne du premier niveau.
2. **Given** AURORA-24 repliée, **When** le mainteneur la déplie, **Then** ses sous-tâches
   s'affichent juste sous elle, en retrait, avec leurs propres colonnes (titre, date, statut,
   synchro, spécification) et un lien vers chacune.
3. **Given** la vue triée par statut, **When** le mainteneur change de page, **Then** la pagination
   compte les seuls tickets du premier niveau, et une story et ses sous-tâches sont toujours sur la
   même page.
4. **Given** un ticket du premier niveau sans sous-tâche, **When** la vue s'affiche, **Then** il
   n'a ni compteur ni commande pour déplier.
5. **Given** une sous-tâche dont le ticket parent n'est pas dans l'index, **When** la vue
   s'affiche, **Then** cette sous-tâche est une ligne du premier niveau qui indique la clé de son
   parent.

---

### User Story 2 - L'agent de code sait qu'il travaille sur une sous-tâche, et de quel ticket (Priority: P1)

Quand l'agent de code demande la prochaine tâche, prend une tâche ou lit une feature, chaque tâche
indique son type de ticket et, pour une sous-tâche, la clé de son ticket parent. Le ticket Jira lié
à la feature les indique aussi : une feature importée depuis une sous-tâche Jira a elle-même un
parent.

**Why this priority**: c'est la condition de la consigne de branche : l'agent ne peut travailler
sur la branche du parent que s'il connaît ce parent.

**Independent Test**: une feature liée à la story AURORA-24, dont les tâches sont des sous-tâches
créées sous elle ; `next_task` renvoie une tâche qui se déclare sous-tâche de AURORA-24.

**Acceptance Scenarios**:

1. **Given** une feature liée à la story AURORA-24 et sa tâche AURORA-182, **When** l'agent appelle
   la prochaine tâche, la prise d'une tâche ou la lecture de la feature, **Then** chaque tâche
   indique qu'elle est une sous-tâche et que son parent est AURORA-24.
2. **Given** une feature importée depuis la sous-tâche AURORA-182 (parent AURORA-24), **When**
   l'agent lit la feature, **Then** le ticket de la feature indique son type (sous-tâche) et son
   parent AURORA-24.
3. **Given** une feature liée à une story qui n'est la sous-tâche d'aucun ticket, **When** l'agent
   lit la feature, **Then** le ticket indique son type et n'a pas de parent.
4. **Given** Jira injoignable, **When** l'agent appelle la prochaine tâche, **Then** le type et le
   parent connus restent donnés.

---

### User Story 3 - Une seule branche pour une story et ses sous-tâches (Priority: P1)

La passerelle MCP dit à l'agent de code que, s'il doit créer une branche en travaillant sur une
sous-tâche, tout le travail des sous-tâches d'un même parent se fait sur la branche de ce parent.
La consigne figure dans les instructions générales du serveur, et dans la réponse quand l'agent
reçoit ou prend une sous-tâche, avec la clé du parent. Le nom de la branche n'est pas imposé.

**Why this priority**: c'est la règle de travail de l'équipe ; sans elle, l'agent peut ouvrir une
branche par sous-tâche et éparpiller le travail d'une story.

**Independent Test**: l'agent reçoit AURORA-182 par la prochaine tâche ; la réponse lui dit de
travailler sur la branche de AURORA-24 si une branche est nécessaire.

**Acceptance Scenarios**:

1. **Given** un agent qui se connecte à la passerelle, **When** il lit les instructions du serveur,
   **Then** elles disent que le travail des sous-tâches se fait sur la branche de leur ticket
   parent, et qu'aucune branche n'est créée par sous-tâche.
2. **Given** la tâche AURORA-182, sous-tâche de AURORA-24, **When** l'agent la reçoit par la
   prochaine tâche ou la prend, **Then** la réponse lui dit, avec la clé AURORA-24, de travailler
   sur la branche de ce ticket si une branche est nécessaire, et de la reprendre si elle existe.
3. **Given** une tâche qui n'est pas une sous-tâche, **When** l'agent la reçoit, **Then** la
   réponse ne porte pas de consigne de branche.

---

### Edge Cases

- Une story dont toutes les sous-tâches sont retirées de l'index n'a plus de compteur à la
  synchronisation suivante.
- Une sous-tâche déplacée sous un autre parent dans Jira apparaît sous son nouveau parent après la
  synchronisation suivante de l'index.
- Une sous-tâche créée par l'outil pour une feature (tâches de la 015) n'est peut-être pas encore
  dans l'index : son parent reste connu, c'est la story liée à la feature.
- Un ticket qui a beaucoup de sous-tâches (plus de 20) : toutes s'affichent en le dépliant, sans
  pagination propre.
- Le tri par statut ou par synchro porte sur l'état du ticket parent, pas sur celui de ses
  sous-tâches.
- Les liens Jira qui ne sont pas de la hiérarchie (Lier, Gantt, Cloners, Livraison…) ne changent
  rien à l'affichage.
- Un ticket dont le parent n'est ni une story ni une story technique (un niveau de plus, comme un
  ticket « Feature ») : la vue suit le lien parent quel que soit le type, sur un seul niveau de
  repli.

## Requirements *(mandatory)*

### Functional Requirements

**Vue « Tickets Jira »**

- **FR-001**: La vue DOIT afficher au premier niveau les tickets qui ne sont pas des sous-tâches
  d'un ticket indexé, et seulement eux.
- **FR-002**: Un ticket du premier niveau qui a des sous-tâches indexées DOIT indiquer leur nombre
  et DOIT pouvoir être déplié et replié ; il est replié à l'ouverture de la vue.
- **FR-003**: Déplié, un ticket DOIT montrer toutes ses sous-tâches indexées juste sous lui, en
  retrait, avec les mêmes colonnes qu'un ticket et un lien vers chacune ; elles sont classées par
  clé.
- **FR-004**: Le tri (Modifié le, Statut, Synchro) et la pagination (pages de 50) DOIVENT porter sur
  les seuls tickets du premier niveau ; les sous-tâches suivent leur parent.
- **FR-005**: Une sous-tâche dont le ticket parent n'est pas dans l'index DOIT rester au premier
  niveau et indiquer la clé de son parent.
- **FR-006**: La hiérarchie DOIT venir du seul lien parent / sous-tâche de Jira ; les autres types
  de liens n'en font pas partie.
- **FR-007**: La vue DOIT rester utilisable au clavier et par un lecteur d'écran : la commande
  pour déplier annonce son état (replié ou déplié) et le nombre de sous-tâches.
- **FR-008**: L'affichage de la vue NE DOIT PAS appeler Jira : la hiérarchie est lue dans l'index
  local, comme aujourd'hui.

**Passerelle MCP**

- **FR-009**: Chaque tâche rendue à l'agent (prochaine tâche, prise, fin, lecture de la feature)
  DOIT indiquer son type de ticket et, si c'est une sous-tâche, la clé de son ticket parent.
- **FR-010**: Le ticket Jira lié à une feature, quand il y en a un, DOIT indiquer son type et, si
  c'est une sous-tâche, la clé de son parent, dans la lecture de la feature.
- **FR-011**: Le parent d'une tâche de feature DOIT être connu même si la sous-tâche n'est pas
  encore dans l'index, et même si Jira est injoignable : c'est le dernier parent connu.
- **FR-012**: Les instructions du serveur MCP DOIVENT dire que, si une branche doit être créée en
  travaillant sur une sous-tâche, tout le travail des sous-tâches d'un même parent se fait sur la
  branche de ce parent ; le nom de la branche n'est pas imposé.
- **FR-013**: Quand l'agent reçoit ou prend une sous-tâche, la réponse DOIT rappeler la consigne
  de branche avec la clé du parent ; une tâche qui n'est pas une sous-tâche n'en porte pas.
- **FR-014**: Les ajouts DOIVENT rester compatibles avec les agents existants : aucun champ
  existant n'est retiré ni renommé.

### Key Entities

- **Ticket indexé** : un ticket Jira du projet dans l'index local, avec son type (story, story
  technique, sous-tâche, anomalie…), son statut et sa date de modification.
- **Lien de hiérarchie** : le lien entre une sous-tâche et son ticket parent, tel que Jira le donne.
  Un ticket a au plus un parent.
- **Tâche de feature** : une tâche suivie par spec-writer pour une feature, créée en sous-tâche
  Jira de la story liée à la feature (015).
- **Consigne de branche** : la règle donnée à l'agent de code, avec la clé du ticket parent dont la
  branche porte le travail.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Sur un projet réel, aucune sous-tâche dont le parent est indexé n'apparaît comme
  ligne du premier niveau, et chacune est retrouvée sous son parent en un clic.
- **SC-002**: Une story et ses sous-tâches sont toujours sur la même page de la vue, quel que soit
  le tri.
- **SC-003**: L'ouverture de la vue et le changement de page restent aussi rapides qu'aujourd'hui
  (moins d'une seconde sur un index de 3 000 tickets).
- **SC-004**: Pour 100 % des tâches de feature rendues à l'agent, le parent est indiqué ; pour 100 %
  des tickets qui ne sont pas des sous-tâches, aucun parent n'est indiqué.
- **SC-005**: Lors d'un travail sur plusieurs sous-tâches d'une même story, l'agent de code reste
  sur une seule branche, celle de la story, sans qu'on le lui demande dans la conversation.

## Assumptions

- La hiérarchie a un seul niveau de repli : un ticket et ses sous-tâches directes. L'index actuel
  ne compte que deux niveaux (story ou story technique, et sous-tâches).
- Le type « sous-tâche » se reconnaît au lien parent donné par Jira, pas au libellé du type, qui
  varie d'une instance Jira à l'autre (« Sous-Tâche (AGILE) »).
- Le nombre de sous-tâches affiché compte les seules sous-tâches indexées.
- La fiche d'un ticket (`/jira/:clé`) et la recherche dans la connaissance (`search_knowledge`) ne
  changent pas : l'agent ne travaille que sur les features et leurs tâches, qui passent par
  spec-writer.
- Le nom de la branche reste au choix de l'agent ou de l'équipe ; spec-writer ne crée ni ne vérifie
  de branche.
- Le sens d'un tri par statut ou par synchro reste celui d'aujourd'hui, appliqué aux tickets du
  premier niveau.
