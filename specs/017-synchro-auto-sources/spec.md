# Feature Specification: Tri de la vue Jira, synchronisation automatique des sources, notification des sources mises à jour

**Feature Branch**: `feature/021-synchro-auto`

**Created**: 2026-11-18

**Status**: Draft

**Input**: User description: "Feature 017 : tri de la vue Jira, synchronisation automatique des sources, notification des sources mises à jour. Décisions prises le 18/11 : la vue « Tickets Jira » se trie par date de modification, statut Jira et état de synchronisation avec la spécification liée, et lit les tickets de l'index local du projet (fraîcheur = dernière synchronisation de Jira, affichée) ; chaque source d'un projet (chaque source de documentation, Jira, le modèle d'architecture) peut recevoir un délai de synchronisation automatique, désactivé par défaut, avec la date de sa dernière synchronisation affichée ; une synchronisation qui constate des changements produit une notification du navigateur, désactivable, qui dit quelle source a été mise à jour et ce qui a changé en nombre. Hors périmètre : notification par le système d'exploitation, synchronisation quand l'outil est arrêté, contenu des changements dans la notification."

## Contexte

La connaissance d'un projet rassemble ses tickets Jira, ses sources de documentation (sites Antora
en ligne ou en dossier, dossier SharePoint synchronisé par OneDrive, racines Confluence) et son
modèle d'architecture. Elle n'est relue aujourd'hui qu'à l'ajout d'une source, à l'enregistrement
du modèle d'architecture, à la recherche des tickets proches d'un besoin, ou à la main
(« Resynchroniser », « Tout réindexer »). Entre deux relectures, la connaissance vieillit sans que
l'utilisateur sache depuis quand, ni ce qui a changé à la source.

La vue « Tickets Jira » lit Jira à chaque page, du plus récemment modifié au plus ancien, sans autre
tri, et n'affiche ni le statut des tickets ni l'état de leur synchronisation avec la spécification
liée. Trouver les tickets en conflit ou ceux d'un statut donné demande de parcourir toutes les
pages.

Cette feature rend la vue Jira triable, fait relire chaque source à son rythme pendant que l'outil
tourne, et prévient l'utilisateur quand une source a changé.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Trier les tickets Jira du projet (Priority: P1) 🎯 MVP

Dans la vue « Tickets Jira », l'utilisateur voit pour chaque ticket sa clé, son titre, son statut
Jira, sa date de modification et l'état de sa synchronisation avec la spécification liée (non liée,
à jour, spécification plus récente que Jira, conflit). Il trie la liste entière sur l'une de ces
trois colonnes, dans un sens ou dans l'autre, et parcourt le résultat page par page sans attendre
Jira. La vue dit de quand date la liste et permet de la resynchroniser.

**Why this priority**: c'est le besoin le plus direct (retrouver les tickets en conflit, ceux d'un
statut, les derniers modifiés) et il ne dépend pas des deux autres stories.

**Independent Test**: avec un projet dont les tickets sont indexés, dont certains sont liés à des
spécifications dans des états différents, trier par état de synchro puis par statut puis par date :
l'ordre porte sur toute la liste, et aucune requête ne part vers Jira en changeant de page ou de tri.

**Acceptance Scenarios**:

1. **Given** un projet dont Jira a été synchronisé, **When** l'utilisateur ouvre la vue, **Then** les tickets indexés s'affichent du plus récemment modifié au plus ancien, avec leur statut et leur état de synchro, et la vue indique « Liste à jour au <date de la dernière synchronisation de Jira> ».
2. **Given** la vue ouverte, **When** l'utilisateur trie par état de synchronisation, **Then** les tickets en conflit, puis ceux dont la spécification est plus récente, puis ceux à jour, puis les non liés sont regroupés sur toute la liste (dans l'ordre inverse au second clic), et la page revient à la première.
3. **Given** la vue ouverte, **When** l'utilisateur trie par statut, **Then** les tickets sont regroupés par catégorie de statut (à faire, en cours, terminé) puis par nom de statut, sur toute la liste.
4. **Given** un tri choisi, **When** l'utilisateur change de page puis revient à la vue plus tard, **Then** le tri est conservé et aucune requête n'est envoyée à Jira.
5. **Given** un projet dont Jira n'a jamais été synchronisé, **When** l'utilisateur ouvre la vue, **Then** elle invite à synchroniser Jira et propose de le faire.
6. **Given** la vue ouverte, **When** l'utilisateur lance la resynchronisation de Jira depuis la vue, **Then** seul Jira est relu, la liste et sa date de fraîcheur se mettent à jour à la fin.

---

### User Story 2 - Synchroniser chaque source à son rythme (Priority: P1)

Dans les réglages du projet, chaque source (chaque source de documentation, Jira, le modèle
d'architecture) a un délai de synchronisation automatique, désactivé par défaut, et affiche la date
de sa dernière synchronisation et son résultat. Tant que l'outil tourne, une source dont le délai
est écoulé est relue seule, de façon incrémentale.

**Why this priority**: c'est ce qui garde la connaissance fraîche sans y penser ; la notification
(US3) et la fraîcheur de la vue Jira (US1) en profitent.

**Independent Test**: régler un délai court sur une source de documentation et sur Jira, modifier
la source simulée, attendre le délai : la source est relue seule, sa date de dernière
synchronisation avance, les autres sources ne sont pas relues.

**Acceptance Scenarios**:

1. **Given** les réglages d'un projet, **When** l'utilisateur regarde ses sources, **Then** chacune affiche sa dernière synchronisation (date et heure, résultat : changements constatés, aucun changement, ou erreur) et son délai automatique (« Désactivée » par défaut).
2. **Given** une source avec un délai d'une heure, **When** une heure s'est écoulée depuis sa dernière synchronisation et que l'outil tourne, **Then** la source est relue seule, de façon incrémentale, et sa date de dernière synchronisation se met à jour.
3. **Given** une indexation en cours pour le projet, **When** le délai d'une source arrive à échéance, **Then** sa synchronisation attend la fin de celle en cours puis part, sans être perdue ni doublée.
4. **Given** l'outil arrêté pendant que des délais arrivaient à échéance, **When** il redémarre, **Then** les sources en retard sont synchronisées peu après le démarrage, l'une après l'autre.
5. **Given** une synchronisation automatique en échec (source injoignable, jeton refusé), **When** l'utilisateur regarde la source, **Then** il voit l'erreur et la date de la dernière synchronisation réussie, et la prochaine tentative a lieu au délai suivant.
6. **Given** une source au délai désactivé, **When** le temps passe, **Then** elle n'est jamais relue automatiquement.

---

### User Story 3 - Être prévenu qu'une source a changé (Priority: P2)

Quand une synchronisation, automatique ou manuelle, constate qu'une source a changé, l'utilisateur
reçoit une notification de bureau, par l'onglet de spec-writer, qui nomme la source et dit ce qui a
changé en nombre (« Confluence · MVP Aurora : 3 pages modifiées, 1 nouvelle »). Il active ou
désactive ces notifications dans Configuration.

**Why this priority**: elle n'a d'intérêt qu'une fois les synchronisations automatiques en place.

**Independent Test**: notifications activées et permission accordée, modifier deux pages d'une
source simulée et synchroniser : une notification nomme la source et compte deux pages modifiées ;
notifications désactivées, la même synchronisation n'en produit aucune.

**Acceptance Scenarios**:

1. **Given** les notifications activées, la permission accordée et un onglet de spec-writer ouvert, **When** une synchronisation constate des changements dans une source, **Then** une notification par source nomme la source et compte les éléments nouveaux, modifiés et retirés.
2. **Given** une synchronisation sans changement ou en échec, **When** elle se termine, **Then** aucune notification n'est émise.
3. **Given** l'utilisateur active les notifications dans Configuration, **When** le navigateur n'a pas encore donné sa permission, **Then** l'outil la demande ; refusée, il explique comment l'accorder et l'option reste inactive.
4. **Given** plusieurs onglets de spec-writer ouverts, **When** une source change, **Then** une seule notification est émise.
5. **Given** l'utilisateur clique la notification, **When** l'onglet s'active, **Then** il arrive sur la connaissance du projet de la source concernée.
6. **Given** les notifications désactivées, **When** une source change, **Then** aucune notification n'est émise ; le résultat reste visible dans la dernière synchronisation de la source.

---

### Edge Cases

- Ticket lié à une spécification mais absent de l'index (ticket supprimé dans Jira, ou d'un autre projet Jira) : il n'apparaît pas dans la vue ; la spécification garde son lien.
- Projet sans clé Jira : la vue Jira l'explique comme aujourd'hui ; Jira n'a pas de délai réglable.
- Plusieurs sources à échéance au même moment : elles passent l'une après l'autre, jamais en parallèle pour un même projet.
- Délai modifié pendant qu'une synchronisation tourne : il s'applique à partir de la prochaine échéance.
- Source retirée alors que sa synchronisation attend son tour : la synchronisation n'a pas lieu.
- Projet supprimé : ses délais disparaissent avec lui.
- Synchronisations automatiques de plusieurs projets : un projet à la fois, dans l'ordre des échéances.
- Notification pendant que l'utilisateur est sur la page de connaissance du projet : elle est tout de même émise si l'onglet n'est pas au premier plan, et la page se met à jour.
- Navigateur qui ne prend pas en charge les notifications : l'option est indisponible et le dit.
- Statut Jira inconnu de l'index (ticket indexé avant que le statut soit gardé) : trié en dernier, affiché « Statut inconnu ».

## Requirements *(mandatory)*

### Functional Requirements

**Vue Jira (US1)**

- **FR-001**: La vue « Tickets Jira » DOIT lister les tickets du projet Jira courant présents dans l'index local du projet, sans requête vers Jira pour afficher, trier ou changer de page.
- **FR-002**: Chaque ligne DOIT afficher la clé, le titre, le statut Jira, la date de modification et l'état de synchronisation avec la spécification liée : « Non liée », « À jour », « Spécification plus récente », « Conflit ».
- **FR-003**: La liste DOIT se trier sur toute son étendue par date de modification, par statut (catégorie puis nom) ou par état de synchronisation (conflit, spécification plus récente, à jour, non liée), dans les deux sens ; le tri par défaut est la date de modification, du plus récent au plus ancien.
- **FR-004**: Le tri choisi DOIT être conservé quand l'utilisateur change de page ou revient à la vue, et un changement de tri DOIT ramener à la première page.
- **FR-005**: La vue DOIT afficher la date de la dernière synchronisation réussie de Jira et permettre de resynchroniser Jira seul ; sans synchronisation, elle DOIT inviter à le faire.
- **FR-006**: Les en-têtes de tri DOIVENT être utilisables au clavier et annoncer le tri courant aux technologies d'assistance (WCAG 2.1 AA).

**Synchronisation automatique (US2)**

- **FR-007**: Chaque source d'un projet (chaque source de documentation, Jira, le modèle d'architecture) DOIT avoir un délai de synchronisation automatique réglable parmi : désactivé (par défaut), 15 minutes, 30 minutes, 1 heure, 4 heures, 1 jour.
- **FR-008**: Chaque source DOIT afficher la date et l'heure de sa dernière synchronisation, son résultat (nombre d'éléments nouveaux, modifiés et retirés, aucun changement, ou erreur) et, en cas d'erreur, la date de la dernière synchronisation réussie.
- **FR-009**: Tant que l'outil tourne, une source dont le délai est écoulé depuis sa dernière synchronisation DOIT être relue seule et de façon incrémentale, sans relire les autres sources du projet.
- **FR-010**: Les synchronisations automatiques NE DOIVENT jamais tourner en parallèle d'une autre indexation du même projet : une échéance attend la fin de l'indexation en cours, et une échéance déjà en attente n'est pas doublée.
- **FR-011**: Au démarrage de l'outil, les sources dont l'échéance est dépassée DOIVENT être synchronisées l'une après l'autre, sans bloquer le démarrage.
- **FR-012**: Une synchronisation automatique en échec DOIT être consignée au journal et visible sur la source ; la tentative suivante a lieu au délai suivant, sans relance immédiate.
- **FR-013**: Une synchronisation manuelle (Resynchroniser, Tout réindexer, réindexation par source) DOIT mettre à jour la date de dernière synchronisation des sources relues et repartir leur délai de là.

**Notifications (US3)**

- **FR-014**: Quand une synchronisation, automatique ou manuelle, constate des changements dans une source, l'outil DOIT émettre une notification du navigateur par source changée, qui nomme la source (sorte et nom) et donne le nombre d'éléments nouveaux, modifiés et retirés ; jamais le contenu des éléments.
- **FR-015**: Aucune notification NE DOIT être émise pour une synchronisation sans changement ou en échec.
- **FR-016**: Les notifications DOIVENT être désactivables dans Configuration (désactivées par défaut) ; l'activation DOIT demander la permission du navigateur, et un refus DOIT être expliqué avec la façon de l'accorder.
- **FR-017**: Une même mise à jour de source NE DOIT produire qu'une notification, quel que soit le nombre d'onglets de spec-writer ouverts.
- **FR-018**: Un clic sur la notification DOIT amener l'onglet sur la connaissance du projet de la source concernée.

### Key Entities

- **Ticket listé** : ticket du projet Jira courant tel que l'index le garde (clé, titre, statut et catégorie de statut, date de modification), avec, s'il est lié, la spécification et l'état de synchronisation du lien.
- **Planification d'une source** : délai de synchronisation automatique d'une source d'un projet (documentation, Jira ou modèle d'architecture), désactivé par défaut.
- **Dernière synchronisation d'une source** : date, résultat (nombres d'éléments nouveaux, modifiés, retirés, ou erreur) et date de la dernière réussite.
- **Mise à jour de source** : constat, à la fin d'une synchronisation, qu'une source a changé ; c'est ce qui est notifié.
- **Préférence de notification** : activée ou non, pour l'outil.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Sur un projet de 2 000 tickets indexés, l'utilisateur obtient la liste triée selon n'importe laquelle des trois colonnes, et chaque changement de page, en moins d'une seconde.
- **SC-002**: Afficher, trier et paginer la vue Jira n'envoie aucune requête à Jira.
- **SC-003**: Une source dont le délai est écoulé est synchronisée au plus une minute après son échéance quand aucune indexation n'est en cours.
- **SC-004**: Une synchronisation automatique ne relit que la source concernée : les autres sources du projet ne reçoivent aucune requête.
- **SC-005**: Avec les notifications activées, chaque source changée produit exactement une notification dans les 10 secondes qui suivent la fin de sa synchronisation ; désactivées, aucune.
- **SC-006**: L'utilisateur voit pour chaque source, en un coup d'œil dans les réglages du projet, de quand date sa dernière synchronisation et si elle a réussi.

## Assumptions

- Les tickets indexés portent déjà clé, titre, statut, catégorie de statut et date de modification ; la synchronisation de Jira existante (incrémentale, tickets fermés gardés) alimente la vue.
- L'état de synchronisation est celui que l'outil tient déjà pour un ticket lié à une spécification (à jour, spécification plus récente que Jira, conflit) ; « Non liée » s'y ajoute pour l'affichage.
- Les délais proposés (15 minutes à 1 jour) suffisent ; un délai plus court chargerait Jira et Confluence sans bénéfice.
- Une synchronisation automatique est incrémentale ; « Tout réindexer » reste manuel.
- Les notifications passent par le navigateur : rien n'est émis si aucun onglet de spec-writer n'est ouvert, et le résultat reste visible dans la dernière synchronisation de la source.
- Les synchronisations automatiques ne demandent pas d'accord de plus : elles lisent les mêmes sources, avec les mêmes accords, que les synchronisations manuelles.

## Hors périmètre

- Notification par le système d'exploitation (hors navigateur).
- Synchronisation quand l'outil est arrêté.
- Contenu des changements dans la notification (titres, extraits).
- Délai de synchronisation par dossier ou par page à l'intérieur d'une source.
- Écriture vers Jira depuis la vue triée (inchangé : par la spécification liée).
