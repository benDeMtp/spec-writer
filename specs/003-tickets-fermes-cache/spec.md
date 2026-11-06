# Feature Specification: Tickets fermés gardés en cache

**Feature Branch**: `003-tickets-fermes-cache`

**Created**: 2026-11-04

**Status**: Draft

**Input**: User description: "pour la gestion des ticket jira et leur indexation locale ainsi que leur cache, il faut prendre en compte leur status. Un ticket fermé n'a pas besoin d'être rescanné pour le RAG, il ne sera jamais modifié. Sont cache peut être très long aussi pour la même raison."

Prolonge la connaissance du projet de `specs/002-projets-contexte-mcp/` (FR-032 à FR-035, FR-044
à FR-047), dont les autres décisions restent valables.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Une réindexation ne relit pas les tickets fermés (Priority: P1)

L'utilisateur relance l'indexation complète d'un projet dont la plupart des tickets Jira sont
fermés depuis longtemps. Aujourd'hui, l'outil relit tous les tickets, fermés compris, alors qu'un
ticket fermé ne change plus. Avec cette feature, l'indexation ne relit dans Jira que les tickets
ouverts et ceux qui ne sont pas encore dans l'index. Les tickets fermés déjà indexés restent tels
quels, recherche par le sens comprise.

**Why this priority**: c'est l'essentiel de la demande. Sur un projet ancien, les tickets fermés
sont la grande majorité : les relire coûte du temps et des requêtes Jira pour rien.

**Independent Test**: indexer un projet de 100 tickets dont 80 fermés, puis lancer une
indexation complète. Seuls les 20 tickets ouverts sont relus dans Jira ; les 80 fermés restent
dans l'index, retrouvables par les termes et par le sens.

**Acceptance Scenarios**:

1. **Given** un projet indexé de 100 tickets dont 80 fermés, **When** l'utilisateur lance une
   indexation complète, **Then** seuls les 20 tickets ouverts sont relus dans Jira, et les 80
   fermés restent dans l'index, inchangés.
2. **Given** un ticket fermé jamais indexé (nouveau projet, ou ticket fermé avant la première
   synchronisation), **When** l'indexation s'exécute, **Then** il est lu une fois, puis plus
   jamais relu tant qu'il reste fermé.
3. **Given** un ticket fermé indexé, **When** il est rouvert dans Jira, **Then** la
   synchronisation suivante le relit, il redevient un ticket ouvert et suit à nouveau les règles
   des tickets ouverts.
4. **Given** un ticket ouvert indexé, **When** il est fermé dans Jira, **Then** la synchronisation
   suivante le relit une dernière fois et l'index le garde comme fermé.
5. **Given** un ticket fermé supprimé dans Jira, **When** l'indexation complète s'exécute,
   **Then** il est retiré de l'index, comme aujourd'hui.

---

### User Story 2 - Les tickets fermés sont montrés sans interroger Jira (Priority: P1)

Avant les questions, l'outil montre les tickets proches du besoin, et la page d'une version montre
les sous-tâches de la story. Aujourd'hui, le statut de chacun est relu dans Jira avant d'être
montré. Un ticket fermé ne change plus : son statut est montré depuis l'index, sans requête vers
Jira, et l'écran dit qu'il est fermé.

**Why this priority**: c'est le même principe appliqué à ce que voit l'utilisateur. Les tickets
proches s'affichent plus vite, et sans Jira joignable pour les tickets fermés.

**Independent Test**: sur un projet indexé, saisir un besoin dont les tickets proches sont deux
tickets fermés et un ouvert. Seul le ticket ouvert est relu dans Jira ; les deux fermés
s'affichent comme fermés, sans requête, même Jira coupé.

**Acceptance Scenarios**:

1. **Given** des tickets proches dont deux fermés et un ouvert, **When** ils sont montrés,
   **Then** seul le ticket ouvert est relu dans Jira, et les deux fermés sont montrés comme
   fermés sans mention « statut indexé, Jira n'a pas pu être relu ».
2. **Given** Jira injoignable, **When** des tickets proches fermés sont montrés, **Then** ils
   s'affichent normalement ; seuls les tickets ouverts portent la mention « Jira n'a pas pu être
   relu ».
3. **Given** une story dont deux sous-tâches sont fermées, **When** la rubrique « Sous-tâches de
   <clé> » est affichée, **Then** ces deux sous-tâches ne sont pas relues dans Jira.
4. **Given** un ticket fermé retenu pour une nouvelle story liée (« relates to »), **When** la
   story est créée dans Jira, **Then** le lien est créé comme aujourd'hui : l'envoi vers Jira
   reste inchangé.

---

### User Story 3 - Le cache d'un ticket fermé n'expire pas (Priority: P2)

Un ticket fermé reste dans l'index aussi longtemps que le projet existe : aucune durée de vie ne
le fait relire ou disparaître. Seules sa suppression dans Jira ou sa réouverture le font sortir de
cet état.

**Why this priority**: c'est la conséquence de US1 et US2 pour la durée de conservation. Elle ne
change rien tant qu'aucune expiration n'existe, mais elle doit rester vraie quand d'autres caches
en auront une.

**Independent Test**: indexer un projet, avancer l'horloge d'un an sans modification dans Jira,
puis synchroniser et afficher des tickets proches : aucun ticket fermé n'est relu ni retiré.

**Acceptance Scenarios**:

1. **Given** un ticket fermé indexé il y a un an et jamais modifié depuis, **When** une
   synchronisation s'exécute ou qu'il est montré comme ticket proche, **Then** il n'est ni relu
   ni retiré de l'index.
2. **Given** l'utilisateur change de modèle de recherche par le sens, **When** les vecteurs sont
   recalculés, **Then** les tickets fermés sont revectorisés depuis le texte gardé dans l'index,
   sans requête vers Jira.

---

### Edge Cases

- Un ticket est rouvert puis refermé entre deux synchronisations : sa date de modification a
  changé, il est relu une fois et gardé comme fermé avec son nouveau contenu.
- Un ticket fermé est modifié dans Jira sans être rouvert (commentaire, correction de texte) : sa
  date de modification change, la synchronisation incrémentale le relit une fois. La règle porte
  sur la relecture systématique, pas sur une modification signalée par Jira.
- Une mise à jour de l'outil a besoin d'une information que l'index n'a pas encore pour les
  tickets fermés (par exemple les liens entre tickets de la 002) : elle peut demander une
  relecture unique de tous les tickets, annoncée dans le journal ; ensuite la règle reprend.
- Le statut d'un ticket change de nom dans le workflow Jira : seule compte sa catégorie (« done »
  pour fermé), comme dans la 002.
- Un ticket fermé déplacé vers un autre projet Jira : il n'est plus dans la liste des clés du
  projet, l'indexation complète le retire.
- Jira injoignable pendant l'indexation : les tickets fermés indexés restent disponibles ; le
  manque ne porte que sur les tickets ouverts et les nouveaux.
- Un ticket fermé dont le statut indexé est faux (indexé ouvert alors qu'il est fermé dans Jira) :
  il est traité comme ouvert, relu, et l'index se corrige.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Un ticket est **fermé** si la catégorie de son statut Jira est « terminé », comme
  dans la 002. Tout autre ticket est ouvert.
- **FR-002**: L'indexation complète NE DOIT PAS relire dans Jira le contenu des tickets fermés
  déjà présents dans l'index. Elle DOIT relire les tickets ouverts et lire les tickets absents de
  l'index, fermés compris.
- **FR-003**: La synchronisation incrémentale DOIT continuer à relire tout ticket dont la date de
  modification Jira a changé, fermé ou non : c'est ainsi qu'une réouverture, une fermeture ou une
  correction est prise en compte.
- **FR-004**: La détection des tickets supprimés ou déplacés DOIT continuer à porter sur tous les
  tickets du projet, fermés compris, sans relire leur contenu.
- **FR-005**: Les vecteurs de la recherche par le sens d'un ticket fermé NE DOIVENT être
  recalculés que si son texte indexé change ou si le modèle de recherche change ; dans ce cas, ils
  le sont depuis le texte gardé dans l'index, sans requête vers Jira.
- **FR-006**: Avant de montrer un ticket proche, ou une sous-tâche dans la rubrique « Sous-tâches
  de <clé> », l'outil NE DOIT relire dans Jira que le statut des tickets ouverts. Un ticket fermé
  est montré avec son statut indexé, comme un statut à jour, sans mention de Jira injoignable.
- **FR-007**: Un ticket fermé NE DOIT jamais être retiré ni relu du seul fait de son ancienneté
  dans l'index : il n'a pas de durée de vie.
- **FR-008**: Une mise à jour de l'outil PEUT demander une relecture unique de tous les tickets,
  fermés compris, quand l'index doit acquérir une information nouvelle ; la relecture est annoncée
  dans le journal, puis FR-002 s'applique de nouveau.
- **FR-009**: À la fin d'une indexation, l'outil DOIT indiquer combien de tickets ont été relus et
  combien de tickets fermés ont été gardés sans relecture.
- **FR-010**: Les écritures vers Jira (création d'une story, lien « relates to », envoi d'une
  version, import d'un ticket) NE DOIVENT pas changer : elles lisent ou écrivent Jira comme
  aujourd'hui, quel que soit le statut du ticket.

### Key Entities

- **Ticket indexé** (de la 002) : gagne son état face au cache : ouvert, donc relu ; ou fermé,
  donc gardé sans relecture, avec la date de sa dernière lecture.
- **Bilan d'indexation** : nombre de tickets relus, de tickets fermés gardés, de tickets retirés.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Sur un projet dont 80 % des tickets indexés sont fermés, une indexation complète
  relit au plus 20 % des tickets dans Jira (les ouverts et les nouveaux), et se termine au moins
  deux fois plus vite qu'avant la feature.
- **SC-002**: Les tickets proches fermés s'affichent sans aucune requête vers Jira, dans 100 % des
  cas, y compris Jira coupé.
- **SC-003**: 100 % des tickets rouverts dans Jira sont repris comme ouverts à la synchronisation
  qui suit leur réouverture.
- **SC-004**: Les résultats de la recherche (termes et sens) sur les tickets fermés sont
  identiques avant et après une indexation complète, sur le jeu de référence de la 002.

## Assumptions

- Un ticket fermé n'est normalement plus modifié ; quand il l'est quand même, Jira change sa date
  de modification, et la synchronisation incrémentale de la 002 le relit. Aucune relecture
  périodique de sécurité n'est prévue.
- La détection des tickets supprimés reste fondée sur la liste des clés du projet, une requête
  légère qui ne lit pas le contenu.
- Le cache concerné est l'index local de la connaissance du projet ; les lectures faites pour
  écrire dans Jira (import, envoi, conflit) ne sont pas mises en cache.
- Les autres sources (pages Antora, modèle Structurizr) gardent leur règle actuelle : relecture
  quand leur empreinte change.
