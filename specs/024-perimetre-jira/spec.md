# Feature Specification: Périmètre Jira de spec-writer

**Feature Branch**: `feature/027-statut-fin-tache`

**Created**: 2026-11-26

**Status**: Draft

**Input**: User description: "Périmètre Jira de spec-writer : label de reconnaissance, filtres de la vue Jira, sous-tâches en référentiel, tickets supprimés. 1. Label de reconnaissance réglé par projet, sans valeur par défaut ; posé sur les stories et sous-tâches créées ou reprises par spec-writer ; filtre par défaut de la vue Jira, désactivable ; le MCP n'expose que les tickets labellisés. 2. Vue Jira : les tickets de premier niveau terminés sont masqués par défaut, un filtre les affiche. 3. Sous-tâches gardées dans l'index comme référentiel, exclues de la recherche, des tickets proches et du RAG. 4. Ticket supprimé dans Jira : retiré à chaque synchronisation, sa tâche sort du suivi, la spécification liée perd son lien."

## Contexte

Le projet Jira d'une équipe contient bien plus que ce que spec-writer produit : des tickets saisis
à la main, des anomalies, d'anciennes stories. Rien ne distingue aujourd'hui une story ou une
sous-tâche créée par spec-writer d'un autre ticket. La vue « Tickets Jira » montre tout, y compris
les stories terminées depuis longtemps, et les agents de code peuvent se voir servir n'importe
quelle feature reliée à un ticket.

L'index local garde aussi les sous-tâches comme de la connaissance. Leur texte est un découpage
d'implémentation : il bruite la recherche, les tickets proches et la rédaction, alors que leur
utilité réelle est de référence (hiérarchie, statuts, suivi des tâches).

Enfin, un ticket supprimé dans Jira ne disparaît de spec-writer qu'avec « Tout réindexer » : la
synchronisation habituelle le garde, et les tâches ou spécifications qui y sont rattachées pointent
vers un ticket qui n'existe plus.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Reconnaître dans Jira les tickets de spec-writer par un label (Priority: P1)

Dans les réglages du projet, l'utilisateur saisit un label Jira, par exemple `spec-writer`. Dès
lors, chaque story et chaque sous-tâche que spec-writer crée porte ce label. Les tickets que
spec-writer reprend sans les créer le reçoivent aussi au moment de la reprise : une story existante
liée à une spécification, et les sous-tâches déjà présentes sous la story, reprises quand les
tâches sont validées. Dans Jira, l'équipe filtre ainsi d'un clic ce qui vient de spec-writer.

**Why this priority**: c'est la base des deux usages suivants (filtre de la vue, périmètre du MCP),
et elle sert déjà seule à l'équipe dans Jira.

**Independent Test**: régler le label `spec-writer`, envoyer une spécification vers Jira puis
valider ses tâches : la story et les sous-tâches créées portent le label ; lier une autre
spécification à une story existante : elle reçoit le label.

**Acceptance Scenarios**:

1. **Given** un projet sans label réglé, **When** spec-writer crée une story ou des sous-tâches,
   **Then** aucun label n'est posé, comme aujourd'hui.
2. **Given** le label `spec-writer` réglé, **When** une spécification est envoyée vers Jira,
   **Then** la story créée porte le label, en plus des labels éventuellement saisis.
3. **Given** le label réglé, **When** les tâches d'une spécification sont validées, **Then** chaque
   sous-tâche créée porte le label, et chaque sous-tâche déjà présente sous la story et reprise le
   reçoit.
4. **Given** le label réglé, **When** une spécification est liée à une story existante (import
   depuis un ticket ou lien à un ticket existant), **Then** la story reçoit le label ; ses autres
   labels sont gardés.
5. **Given** un ticket qui porte déjà le label, **When** spec-writer le reprend, **Then** rien
   n'est envoyé.
6. **Given** une pose du label refusée par Jira (droit de modifier le ticket manquant, champ absent
   de l'écran), **When** spec-writer crée ou reprend le ticket, **Then** la création ou la reprise
   réussit quand même, et l'utilisateur est prévenu que le label n'a pas été posé.
7. **Given** une saisie invalide (espace, plus de 255 caractères), **When** l'utilisateur
   enregistre, **Then** le réglage est refusé avec un message qui dit pourquoi.

---

### User Story 2 - La vue « Tickets Jira » montre d'abord les tickets utiles (Priority: P1)

La vue « Tickets Jira » s'ouvre avec deux filtres actifs : seulement les tickets qui portent le
label du projet, et sans les tickets de premier niveau terminés (stories, tâches mères ou tickets
sans enfant). Chaque filtre se désactive dans la vue, pour retrouver tous les tickets ou les
tickets terminés.

**Why this priority**: sur un projet de plusieurs centaines de tickets, la vue n'est utile que si
elle montre le travail en cours de spec-writer ; c'est la demande la plus visible au quotidien.

**Independent Test**: projet avec label réglé, une story labellisée ouverte, une story labellisée
terminée et une story non labellisée ouverte : à l'ouverture, seule la première est listée ;
afficher les terminés ajoute la deuxième ; retirer le filtre du label ajoute la troisième.

**Acceptance Scenarios**:

1. **Given** un projet avec label réglé, **When** l'utilisateur ouvre la vue, **Then** seuls les
   tickets de premier niveau qui portent le label sont listés, et le filtre « label » est affiché
   actif, avec le nom du label.
2. **Given** ce filtre actif, **When** l'utilisateur le désactive, **Then** tous les tickets sont
   listés, avec les autres filtres et le tri gardés.
3. **Given** un projet sans label réglé, **When** l'utilisateur ouvre la vue, **Then** le filtre
   « label » n'est pas proposé.
4. **Given** des tickets de premier niveau terminés, **When** l'utilisateur ouvre la vue, **Then**
   ils ne sont pas listés, et un filtre « Afficher les tickets terminés », inactif, le dit.
5. **Given** ce filtre activé, **When** la vue se met à jour, **Then** les tickets terminés sont
   listés avec leur statut.
6. **Given** une story ouverte dont certaines sous-tâches sont terminées, **When** elle est dépliée,
   **Then** toutes ses sous-tâches sont montrées : les filtres ne s'appliquent qu'au premier niveau.
7. **Given** des filtres qui ne laissent aucun ticket, **When** la vue s'affiche, **Then** un
   message dit qu'aucun ticket ne correspond et propose de retirer les filtres.
8. **Given** des filtres modifiés, **When** l'utilisateur quitte la vue et y revient pendant la
   même séance, **Then** ses choix sont gardés ; le pager et les compteurs suivent les filtres.

---

### User Story 3 - Les agents de code ne voient que les tickets de spec-writer (Priority: P2)

Sur un projet avec label réglé, la passerelle des agents de code (MCP) ne sert que les features dont
la story porte le label, et parmi leurs tâches, que celles dont la sous-tâche porte le label. Un
agent ne peut donc pas prendre une tâche d'un ticket que spec-writer ne gère pas.

**Why this priority**: elle protège le travail des équipes qui partagent un projet Jira ; elle
dépend de la story 1.

**Independent Test**: deux features prêtes, l'une liée à une story labellisée, l'autre à une story
non labellisée : la liste des features de l'agent ne montre que la première ; une sous-tâche non
labellisée de la première n'est jamais proposée par la prochaine tâche.

**Acceptance Scenarios**:

1. **Given** un projet avec label réglé, **When** un agent liste les features, **Then** seules
   celles dont la story porte le label sont listées.
2. **Given** une feature dont la story n'a pas le label, **When** un agent la lit, la change de
   statut ou demande une tâche, **Then** il reçoit une erreur qui dit que la feature n'est pas
   gérée par spec-writer dans Jira (label absent).
3. **Given** une feature labellisée dont une sous-tâche n'a pas le label, **When** l'agent demande
   la prochaine tâche ou la prend, **Then** cette tâche n'est ni proposée ni prise, et la réponse
   dit pourquoi.
4. **Given** une feature sans ticket lié, **When** un agent la lit, **Then** elle reste servie
   comme aujourd'hui : il n'y a pas de ticket à reconnaître.
5. **Given** un projet sans label réglé, **When** un agent travaille, **Then** rien ne change.
6. **Given** un label posé à la main dans Jira sur un ticket, **When** l'index est synchronisé,
   **Then** le ticket est reconnu comme les autres.

---

### User Story 4 - Les sous-tâches ne sont plus de la connaissance, seulement une référence (Priority: P2)

Les sous-tâches restent dans l'index local avec leur clé, leur titre, leur statut et leur parent :
la vue Jira hiérarchique, les statuts proposés dans le réglage « Suivi des tâches dans Jira », la
rubrique « Sous-tâches » d'une version et le suivi des tâches continuent de fonctionner. Mais elles
ne sont plus cherchées : ni par la recherche de connaissance (de l'utilisateur et de l'agent), ni
parmi les tickets proches d'un besoin, ni dans les extraits transmis au modèle pour rédiger.

**Why this priority**: améliore la pertinence de la recherche et de la rédaction sans rien retirer
aux usages de référence ; elle est indépendante des autres stories.

**Independent Test**: un besoin dont les mots figurent surtout dans le titre d'une sous-tâche : la
sous-tâche n'apparaît ni dans les tickets proches, ni dans les sources de la réponse de l'agent ;
elle reste visible sous sa story dans la vue Jira.

**Acceptance Scenarios**:

1. **Given** une sous-tâche indexée, **When** l'utilisateur ou l'agent cherche dans la
   connaissance du projet, **Then** elle n'est pas retournée ; sa story peut l'être.
2. **Given** un nouveau besoin, **When** spec-writer cherche les tickets proches, **Then** aucune
   sous-tâche n'est proposée.
3. **Given** une rédaction ou une conversation, **When** le modèle reçoit des extraits, **Then**
   aucun ne vient d'une sous-tâche.
4. **Given** la vue Jira, une version liée à une story, le réglage du suivi des tâches et le suivi
   par les agents, **When** ils s'appuient sur les sous-tâches, **Then** ils fonctionnent comme
   avant.
5. **Given** un index qui contient déjà des sous-tâches cherchables, **When** la première
   synchronisation après la mise à jour a lieu, **Then** elles deviennent de simples références,
   sans réindexation complète demandée à l'utilisateur.

---

### User Story 5 - Un ticket supprimé dans Jira disparaît de spec-writer (Priority: P2)

À chaque synchronisation de Jira (automatique, « Resynchroniser Jira » ou « Tout réindexer »), un
ticket supprimé dans Jira, ou déplacé hors du projet, est retiré de l'index et de la vue. Une tâche
dont la sous-tâche a été supprimée sort du suivi, et l'avancement est recalculé. Une spécification
liée à une story supprimée perd son lien, mais reste avec toutes ses versions. Un ticket annulé
n'est pas supprimé : il reste, avec son statut.

**Why this priority**: évite de travailler sur des tickets fantômes ; elle est indépendante des
autres stories.

**Independent Test**: supprimer dans Jira une sous-tâche suivie et une story liée, puis
resynchroniser : elles ne sont plus dans la vue ; la tâche n'est plus dans le panneau Tâches
(« 2 tâches sur 2 » au lieu de « 2 sur 3 ») ; la spécification n'est plus liée et le dit.

**Acceptance Scenarios**:

1. **Given** un ticket supprimé dans Jira, **When** une synchronisation incrémentale a lieu,
   **Then** il est retiré de l'index et de la vue ; le bilan de la synchronisation compte les
   tickets retirés.
2. **Given** une tâche suivie dont la sous-tâche est supprimée, **When** la synchronisation a lieu
   ou que la tâche est relue, **Then** la tâche sort du suivi, l'avancement et le statut de la
   spécification sont recalculés, et l'agent qui la tenait reçoit, à son prochain appel, un message
   qui dit qu'elle a été supprimée dans Jira.
3. **Given** une spécification liée à une story supprimée, **When** la synchronisation a lieu,
   **Then** le lien est retiré, la spécification et ses versions restent, et sa page dit que la
   story a été supprimée dans Jira et qu'elle peut être renvoyée ou liée à un autre ticket.
4. **Given** un ticket annulé ou fermé, **When** la synchronisation a lieu, **Then** il reste,
   avec son statut.
5. **Given** Jira injoignable, un projet devenu invisible pour le jeton, ou une liste de tickets
   vide alors que l'index en contient, **When** la synchronisation a lieu, **Then** rien n'est
   retiré, et une erreur de synchronisation est signalée.

---

### Edge Cases

- Changement de label : les tickets portant l'ancien label ne sont plus reconnus ; spec-writer ne
  réétiquette pas l'existant. Le message de confirmation du changement le dit.
- Retrait du réglage : plus rien n'est étiqueté ni filtré ; les labels déjà posés dans Jira
  restent.
- Un label posé hors de spec-writer sur un ticket quelconque fait entrer ce ticket dans le
  périmètre (vue, MCP) : c'est voulu, le label est la règle.
- La comparaison du label suit Jira : un label est sensible à la casse dans Jira ; spec-writer
  compare les labels tels quels.
- Une story de premier niveau terminée dont des sous-tâches restent ouvertes est masquée par le
  filtre des terminés : le filtre porte sur le ticket de premier niveau.
- « Terminé » désigne la catégorie Jira « terminé » (Fermée, Annulée…) ; un statut fini au sens du
  réglage de la 023 (par exemple « Réalisée ») n'est pas masqué.
- Une sous-tâche orpheline (parent non indexé) est un ticket de premier niveau pour les filtres de
  la vue.
- Une sous-tâche supprimée pendant qu'un agent la tient : la fin de la tâche par l'agent est
  refusée avec le motif, rien n'est envoyé à Jira.
- Suppression de la story d'une feature découpée : Jira supprime aussi ses sous-tâches ; toutes
  les tâches sortent du suivi et la feature n'a plus de tâches.
- Une story supprimée est retirée de l'index même si une spécification y était liée.

## Requirements *(mandatory)*

### Functional Requirements

**Label de reconnaissance**

- **FR-001**: Le système DOIT permettre de régler, par projet, un label Jira de reconnaissance,
  sans valeur par défaut, et de le retirer.
- **FR-002**: Le label DOIT être un label Jira valide : de 1 à 255 caractères, sans espace ; une
  saisie invalide est refusée avec son motif.
- **FR-003**: Sur un projet réglé, toute story et toute sous-tâche créée par spec-writer DOIT
  porter le label, en plus des labels déjà demandés.
- **FR-004**: Sur un projet réglé, un ticket repris par spec-writer (story liée à une
  spécification, sous-tâche existante reprise à la validation des tâches) DOIT recevoir le label
  s'il ne l'a pas ; ses autres labels DOIVENT être gardés.
- **FR-005**: Une pose du label refusée par Jira NE DOIT PAS empêcher la création ou la reprise ;
  l'utilisateur DOIT en être prévenu, avec le motif.
- **FR-006**: Sans réglage, aucun label NE DOIT être posé (comportement actuel).

**Vue « Tickets Jira »**

- **FR-007**: Sur un projet réglé, la vue DOIT s'ouvrir filtrée sur les tickets de premier niveau
  qui portent le label ; le filtre DOIT pouvoir être désactivé dans la vue. Sans réglage, il n'est
  pas proposé.
- **FR-008**: La vue DOIT s'ouvrir sans les tickets de premier niveau de catégorie « terminé » ;
  un filtre DOIT permettre de les afficher.
- **FR-009**: Les filtres NE DOIVENT s'appliquer qu'aux tickets de premier niveau ; les sous-tâches
  d'un ticket affiché sont toutes montrées.
- **FR-010**: Le pager, les compteurs et le tri DOIVENT suivre les filtres ; les choix DOIVENT être
  gardés pendant la séance de travail ; une liste vide DOIT le dire et proposer de retirer les
  filtres.

**Agents de code (MCP)**

- **FR-011**: Sur un projet réglé, la passerelle NE DOIT servir que les features sans ticket lié ou
  dont la story porte le label ; une feature hors périmètre est refusée avec un motif qui nomme le
  label manquant.
- **FR-012**: Sur un projet réglé, une tâche dont la sous-tâche n'a pas le label NE DOIT être ni
  proposée par la prochaine tâche, ni prise, ni terminée par un agent ; la réponse dit pourquoi.
- **FR-013**: La reconnaissance DOIT s'appuyer sur les labels lus dans Jira à la synchronisation et
  à la relecture des tâches, y compris un label posé à la main.

**Sous-tâches en référentiel**

- **FR-014**: Les sous-tâches DOIVENT rester indexées avec leur clé, leur titre, leur statut, leur
  catégorie, leur type et leur parent.
- **FR-015**: Les sous-tâches NE DOIVENT plus être retournées par la recherche de connaissance (de
  l'utilisateur et de l'agent), ni proposées parmi les tickets proches, ni transmises au modèle
  comme extraits.
- **FR-016**: La vue Jira hiérarchique, les statuts connus du réglage de suivi, la rubrique
  « Sous-tâches » d'une version et le suivi des tâches DOIVENT continuer de s'appuyer sur elles.
- **FR-017**: Un index existant DOIT être mis en conformité à la première synchronisation après la
  mise à jour, sans action de l'utilisateur.

**Tickets supprimés**

- **FR-018**: Chaque synchronisation de Jira (automatique, manuelle ou complète) DOIT retirer de
  l'index les tickets du projet qui n'existent plus dans Jira ou n'y sont plus visibles, et compter
  les retraits dans son bilan.
- **FR-019**: Une tâche dont la sous-tâche est retirée DOIT sortir du suivi ; l'avancement et le
  statut de la spécification DOIVENT être recalculés ; un agent qui la tenait DOIT en être prévenu
  à son appel suivant.
- **FR-020**: Une spécification liée à une story retirée DOIT perdre son lien et garder ses
  versions ; sa page DOIT dire que la story a été supprimée dans Jira.
- **FR-021**: Rien NE DOIT être retiré quand Jira est injoignable, que le projet n'est plus visible,
  ou que la liste des tickets est vide alors que l'index en contient ; une erreur de
  synchronisation est alors signalée.
- **FR-022**: Un ticket annulé ou fermé NE DOIT PAS être retiré.

### Key Entities

- **Label de reconnaissance** (par projet) : le label Jira qui marque les tickets de spec-writer.
  Facultatif.
- **Ticket de premier niveau** : story, tâche ou tout ticket sans parent indexé ; porte des
  sous-tâches ou non.
- **Sous-tâche de référence** : sous-tâche indexée pour sa clé, son titre, son statut et son parent,
  sans être cherchée.
- **Tâche de feature** (015) : sous-tâche suivie par spec-writer ; sort du suivi si elle est
  supprimée.
- **Lien d'une spécification** : le ticket Jira lié à une spécification ; retiré si le ticket est
  supprimé.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Sur un projet réglé, 100 % des stories et sous-tâches créées ou reprises par
  spec-writer portent le label, sauf refus de Jira, chaque refus étant signalé.
- **SC-002**: À l'ouverture de la vue Jira d'un projet réglé, 0 ticket sans label et 0 ticket de
  premier niveau terminé n'est listé ; un clic suffit pour lever chaque filtre.
- **SC-003**: Un agent de code ne prend jamais une tâche dont le ticket ne porte pas le label, sur
  un projet réglé.
- **SC-004**: 0 sous-tâche parmi les tickets proches, les résultats de recherche et les extraits
  transmis au modèle ; la vue hiérarchique et le suivi des tâches restent identiques.
- **SC-005**: Après une synchronisation, 0 ticket supprimé dans Jira ne reste dans la vue, le
  panneau Tâches ou le lien d'une spécification ; une coupure de Jira ne retire jamais rien.

## Assumptions

- Réponses du 26/11 : les sous-tâches deviennent un référentiel (option recommandée) ; le label est
  réglé par projet, sans valeur par défaut ; un ticket repris reçoit le label ; une suppression
  retire aussi la tâche du suivi et le lien de la spécification.
- Le label est posé à la création dans le même envoi que le ticket, et à la reprise par une
  modification du ticket ; le droit de modifier les tickets du projet suffit.
- spec-writer vise Jira Server / Data Center, où un label est une chaîne sans espace, sensible à
  la casse.
- Un ticket déplacé dans un autre projet est traité comme supprimé, comme aujourd'hui avec « Tout
  réindexer ».
- La séance de travail est la durée d'ouverture de l'outil ; les filtres reviennent à leurs
  valeurs par défaut au lancement suivant.
- Les tickets de la vue sont lus dans l'index (017) : le label et la catégorie de statut y sont
  disponibles après synchronisation.
- La recherche de documentation, d'ADR et du modèle d'architecture n'est pas concernée.
