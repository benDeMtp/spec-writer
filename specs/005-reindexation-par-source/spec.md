# Feature Specification: Réindexation par source

**Feature Branch**: `feature/008-reindexation-par-source`

**Created**: 2026-11-06

**Status**: Draft

**Input**: User description: "Réindexation par source (suite de la 003, US4 proposée) : (1) l'utilisateur choisit la ou les sources à réindexer (Jira, chacune des sources de documentation, le modèle d'architecture C4) au lieu de tout réindexer ; « Resynchroniser » et « Tout réindexer » restent disponibles pour l'ensemble ; (2) une erreur inattendue dans une étape de l'indexation (Jira, documentation, architecture) n'arrête pas les étapes suivantes : l'étape en échec est signalée dans le bilan avec son erreur, les autres sources sont indexées normalement, et l'indexation se termine « avec des erreurs ». Hors périmètre : lecture des !docs/!adrs du C4."

Prolonge la connaissance du projet de `specs/002-projets-contexte-mcp/` et
`specs/003-tickets-fermes-cache/` (US4 proposée à l'essai de la 003), dont les décisions restent
valables.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Une étape en échec n'arrête pas les autres (Priority: P1)

L'utilisateur lance une indexation. Une erreur inattendue survient pendant la lecture de Jira : à
l'essai de la 003, une bibliothèque manquante. Aujourd'hui, l'indexation s'arrête en échec : la
documentation et le modèle d'architecture ne sont pas lus, et l'utilisateur croit que ces sources
posent problème. Avec cette feature, l'étape Jira est signalée en échec avec son erreur, puis la
documentation et l'architecture sont indexées normalement. L'indexation se termine « avec des
erreurs ».

**Why this priority**: une source cassée ne doit pas priver le projet des autres. C'est aussi la
condition d'une réindexation par source fiable (US2) : chaque source vit sa vie.

**Independent Test**: provoquer une erreur inattendue à la lecture de Jira, lancer une
indexation complète. La documentation et l'architecture sont indexées, le bilan signale l'échec
de l'étape Jira avec son message, et l'indexation est « terminée, avec des erreurs ».

**Acceptance Scenarios**:

1. **Given** une erreur inattendue pendant l'étape Jira, **When** l'indexation s'exécute,
   **Then** les étapes documentation et architecture s'exécutent normalement, et l'indexation se
   termine « avec des erreurs », l'étape Jira étant signalée dans le détail avec son message.
2. **Given** une erreur inattendue pendant la lecture d'une source de documentation parmi
   plusieurs, **When** l'indexation s'exécute, **Then** les autres sources de documentation et
   l'architecture sont indexées, et la source en échec est signalée.
3. **Given** une erreur inattendue pendant l'étape architecture, **When** l'indexation
   s'exécute, **Then** Jira et la documentation sont indexés, et l'étape architecture est
   signalée.
4. **Given** une étape en échec, **When** l'indexation se termine, **Then** ce que l'index
   contenait pour cette source reste en place : rien n'est retiré, et la synchronisation suivante
   de cette source reprend là où la dernière réussie s'était arrêtée.
5. **Given** l'utilisateur annule l'indexation, **When** une étape est en cours, **Then**
   l'indexation s'arrête comme aujourd'hui : l'annulation n'est pas une erreur d'étape.

---

### User Story 2 - Réindexer seulement les sources choisies (Priority: P1)

L'utilisateur vient de corriger une page de la documentation, ou de modifier le modèle
d'architecture C4. Il veut forcer la réindexation de cette source seule, sans relire Jira ni les
autres sources. Depuis le panneau « Connaissance du projet », il choisit la ou les sources à
réindexer parmi celles du projet : Jira, chacune des sources de documentation, le modèle
d'architecture. Seules ces sources sont relues, entièrement. « Resynchroniser » et « Tout
réindexer » restent disponibles pour l'ensemble des sources.

**Why this priority**: demandé à l'essai de la 003 (« il faudrait pouvoir contrôler pour quelle
source nous voulons forcer une réindexation »). Tout réindexer relit Jira et toutes les sources
pour un changement local.

**Independent Test**: sur un projet avec Jira, deux sources de documentation et un modèle
d'architecture, choisir la seule deuxième source de documentation et lancer la réindexation.
Seule cette source est relue entièrement ; Jira, la première source et l'architecture ne sont
pas lus, et leur contenu dans l'index ne change pas.

**Acceptance Scenarios**:

1. **Given** un projet avec Jira, deux sources de documentation et un modèle d'architecture,
   **When** l'utilisateur ouvre le choix des sources, **Then** il voit ces quatre sources, chacune
   désignée de façon reconnaissable (Jira et la clé du projet, l'emplacement de chaque source de
   documentation, le modèle d'architecture).
2. **Given** l'utilisateur a choisi une seule source de documentation, **When** il lance la
   réindexation, **Then** seule cette source est relue entièrement, les pages inchangées
   comprises ; les autres sources ne sont pas lues.
3. **Given** l'utilisateur a choisi le modèle d'architecture, **When** il lance la réindexation,
   **Then** le modèle est relu et réanalysé même s'il n'a pas changé depuis la dernière lecture.
4. **Given** l'utilisateur a choisi Jira, **When** il lance la réindexation, **Then** Jira est
   relu comme par « Tout réindexer » : les tickets fermés déjà indexés restent gardés sans
   relecture (feature 003).
5. **Given** une réindexation de sources choisies terminée, **When** l'utilisateur lit le bilan,
   **Then** le bilan dit quelles sources ont été réindexées, avec leurs erreurs éventuelles.
6. **Given** aucune source choisie, **When** l'utilisateur regarde le choix, **Then** la
   réindexation ne peut pas être lancée.
7. **Given** un projet sans Jira, sans documentation ou sans modèle d'architecture, **When**
   l'utilisateur ouvre le choix, **Then** seules les sources déclarées sont proposées.
8. **Given** une indexation en cours, **When** l'utilisateur veut réindexer des sources
   choisies, **Then** il ne le peut pas avant la fin, comme pour les autres indexations.

---

### Edge Cases

- **Source retirée du projet pendant l'indexation** : la source n'est plus lue ; les autres le
  sont.
- **Erreur prévue d'une source** (Jira injoignable, page de documentation introuvable, script du
  modèle ignoré) : elle reste signalée comme aujourd'hui ; la feature ne change que les erreurs
  inattendues, qui arrêtaient tout.
- **Erreur inattendue à la préparation du modèle de recherche** : déjà sans effet sur les
  sources (recherche par les termes seule) ; inchangé.
- **Erreur inattendue au calcul des vecteurs, après les sources** : signalée, les sources restent
  indexées ; les éléments sans vecteur restent retrouvables par les termes, et la synchronisation
  suivante calcule les vecteurs manquants.
- **Erreur qui empêche tout** (base locale inaccessible, projet supprimé) : l'indexation reste en
  échec, comme aujourd'hui.
- **Toutes les étapes en échec** : l'indexation est « terminée, avec des erreurs », chaque étape
  signalée ; l'index précédent reste en place.
- **Réindexation de sources choisies puis « Resynchroniser »** : la resynchronisation suivante ne
  relit pas en double ce que la réindexation choisie a déjà lu à jour.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Une erreur inattendue pendant l'étape d'une source (Jira, une source de
  documentation, le modèle d'architecture) DOIT être signalée dans le détail de l'indexation,
  avec la source et le message de l'erreur, sans arrêter les étapes suivantes.
- **FR-002**: Une indexation dont au moins une étape a échoué DOIT se terminer « avec des
  erreurs », et non en échec, tant qu'une erreur n'empêche pas toute l'indexation.
- **FR-003**: Une source dont l'étape a échoué NE DOIT perdre aucun élément de l'index, et son
  repère de synchronisation (date de Jira, empreinte du modèle d'architecture) NE DOIT PAS
  avancer.
- **FR-004**: Le calcul des vecteurs DOIT s'exécuter après les sources, même si l'une d'elles a
  échoué, pour les éléments indexés.
- **FR-005**: L'annulation par l'utilisateur DOIT arrêter l'indexation comme aujourd'hui.
- **FR-006**: Le panneau « Connaissance du projet » DOIT proposer, à côté de « Resynchroniser »
  et « Tout réindexer », de réindexer des sources choisies.
- **FR-007**: Le choix DOIT lister les sources déclarées du projet : Jira (avec la clé du projet)
  s'il est configuré, chaque source de documentation (avec son emplacement), le modèle
  d'architecture s'il est déclaré. Rien n'est coché par défaut.
- **FR-008**: La réindexation de sources choisies DOIT relire entièrement chacune d'elles, comme
  « Tout réindexer » le ferait pour elle (règles de la 003 comprises pour Jira), et NE DOIT
  lire aucune autre source.
- **FR-009**: La réindexation DOIT être impossible sans source choisie, et pendant une
  indexation en cours.
- **FR-010**: Le bilan d'une réindexation de sources choisies DOIT nommer les sources
  réindexées ; le bilan des tickets (feature 003) n'apparaît que si Jira en fait partie.
- **FR-011**: Le choix DOIT être utilisable au clavier et annoncé par les lecteurs d'écran
  (WCAG 2.1 AA), comme le reste du panneau.

### Key Entities

- **Source de connaissance du projet** : Jira (clé du projet), une source de documentation
  (dossier ou site Antora), ou le modèle d'architecture. Chacune a son repère de synchronisation.
- **Indexation** : une exécution, de mode resynchronisation, réindexation complète ou
  réindexation de sources choisies ; elle porte la liste des sources concernées, ses étapes, son
  détail d'erreurs et son bilan.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Une erreur inattendue dans une étape laisse indexer 100 % des autres sources
  disponibles, et l'utilisateur voit quelle source a échoué sans ouvrir le journal.
- **SC-002**: La réindexation d'une seule source de documentation ne fait aucune requête à Jira
  et ne relit aucune autre source.
- **SC-003**: Réindexer une source choisie se lance en 3 actions au plus depuis le panneau.
- **SC-004**: Aucune régression des indexations « Resynchroniser » et « Tout réindexer »
  (scénarios de bout en bout existants inchangés).

## Assumptions

- La date « Synchronisée le » du panneau reste celle de la dernière indexation de toutes les
  sources : une réindexation de sources choisies ne la change pas ; son bilan dit ce qui a été
  réindexé et quand.
- Une réindexation de sources choisies relit chaque source entièrement : c'est ce que demande
  l'utilisateur quand il force la relecture d'une source. La resynchronisation incrémentale par
  source n'est pas demandée.
- La préparation du modèle de recherche et le calcul des vecteurs ne sont pas des sources : ils
  s'exécutent à chaque indexation, quelles que soient les sources choisies.
- Le choix des sources n'est pas mémorisé d'une fois sur l'autre.
- Hors périmètre : la lecture des `!docs` et `!adrs` du modèle d'architecture C4.
