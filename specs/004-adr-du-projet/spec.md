# Feature Specification: ADR du projet

**Feature Branch**: `004-adr-du-projet`

**Created**: 2026-11-05

**Status**: Draft

**Input**: User description: "ADR du projet : (1) le compteur de la page Contexte compte les ADR et les sections de documentation, non les morceaux de découpage (21 ADR découpés en 57 morceaux étaient affichés « 57 ADR ») ; (2) une page dédiée aux ADR du projet, accessible seulement depuis le contexte du projet et seulement si des ADR ont été trouvés (le compteur « N ADR » devient un lien) : une ligne par ADR triée par numéro avec numéro, titre (lien vers la page de documentation, ouvert dans un nouvel onglet), statut, successeur ; résumé du nombre par statut ; filtre par statut ; ADR remplacés et obsolètes grisés avec la mention « non transmis à l'IA » ; statut non lu affiché « inconnu » ; numéros en double signalés ; pas de texte complet dans la page ; (3) quand la recherche retient un morceau d'ADR, la génération reçoit l'ADR entier (borné, quitte à transmettre moins d'ADR), et non le seul morceau : aujourd'hui l'IA répond que « le texte complet de l'ADR 0017 n'est pas fourni »."

Prolonge la connaissance du projet de `specs/002-projets-contexte-mcp/` (FR-013 à FR-013g, FR-036,
R20, R24) et la reconnaissance des ADR de la convention adr-tools (003, T030).

## Clarifications

### Session 2026-11-05

- Q : La page montre-t-elle le texte des ADR ? → R : Non : la liste, avec un lien vers la page de
  documentation de chaque ADR, ouvert dans un nouvel onglet.
- Q : Le contenu de la page (numéro, titre, statut, successeur, résumé par statut, filtre, grisé
  des ADR non transmis, statut inconnu, doublons signalés) convient-il ? → R : Oui.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Les compteurs disent combien d'ADR et de sections le projet a (Priority: P1)

L'utilisateur ouvre la page **Contexte** d'un projet dont la documentation contient 21 ADR. Le
panneau de connaissance affiche aujourd'hui « 57 ADR », parce que les ADR longs sont découpés en
morceaux pour la recherche et que le compteur compte les morceaux. Il doit afficher « 21 ADR ». Le
compteur des sections de documentation suit la même règle.

**Why this priority**: un compteur faux fait douter de toute la connaissance du projet ; c'est la
première chose que l'utilisateur a vue, et la correction est courte.

**Independent Test**: indexer une documentation de 21 ADR dont plusieurs dépassent la taille d'un
morceau ; le panneau affiche « 21 ADR ».

**Acceptance Scenarios**:

1. **Given** une documentation de 21 ADR découpés en 57 morceaux, **When** l'utilisateur ouvre la
   page Contexte, **Then** le panneau affiche « 21 ADR ».
2. **Given** une page de documentation dont une section longue est découpée en 3 morceaux,
   **When** le panneau est affiché, **Then** cette section compte pour une seule section.
3. **Given** un projet sans ADR, **When** le panneau est affiché, **Then** il affiche « 0 ADR »,
   sans lien.

---

### User Story 2 - Une page liste les ADR du projet (Priority: P1)

Depuis la page Contexte, le compteur « 21 ADR » est un lien vers une page propre au projet qui liste
ses ADR : un par ligne, triés par numéro, avec leur titre, leur statut et, s'il est remplacé, son
successeur. L'utilisateur voit d'un coup d'œil ce que l'outil a reconnu, ce qu'il transmet à l'IA
et ce qui pose problème (statut non lu, numéro en double).

**Why this priority**: c'est la demande ; sans elle, l'utilisateur ne peut pas vérifier ce que
l'outil a compris de ses ADR, ni pourquoi l'IA en cite ou non.

**Independent Test**: sur un projet indexé de 21 ADR (6 acceptés, 15 proposés, deux numéros 0005,
deux numéros 0017), ouvrir la page : 21 lignes triées, résumé « 6 acceptés, 15 proposés », les
deux doublons signalés ; un clic sur un titre ouvre la page de l'ADR dans un nouvel onglet.

**Acceptance Scenarios**:

1. **Given** un projet dont des ADR ont été reconnus, **When** l'utilisateur clique sur « 21 ADR »
   dans la page Contexte, **Then** la page des ADR du projet s'ouvre, avec 21 lignes triées par
   numéro.
2. **Given** la page des ADR, **When** elle s'affiche, **Then** chaque ligne montre le numéro, le
   titre, le statut et, pour un ADR remplacé, son successeur ; un résumé en tête compte les ADR par
   statut.
3. **Given** un titre d'ADR, **When** l'utilisateur clique dessus, **Then** la page de documentation
   de l'ADR s'ouvre dans un nouvel onglet, et la page des ADR reste ouverte.
4. **Given** un ADR remplacé ou obsolète, **When** la page s'affiche, **Then** sa ligne est grisée
   et porte la mention « non transmis à l'IA ».
5. **Given** un ADR dont le statut n'a pas pu être lu, **When** la page s'affiche, **Then** son
   statut est « inconnu ».
6. **Given** deux ADR qui portent le même numéro, **When** la page s'affiche, **Then** les deux
   lignes sont signalées « numéro en double ».
7. **Given** le filtre par statut, **When** l'utilisateur choisit « Proposé », **Then** seules les
   lignes des ADR proposés restent ; le résumé reste celui de tous les ADR.
8. **Given** un projet sans ADR, **When** l'utilisateur ouvre la page Contexte, **Then** aucun lien
   ne mène à la page des ADR ; une adresse saisie à la main affiche « Aucun ADR n'a été trouvé dans
   la documentation de ce projet » et un retour vers le contexte.

---

### User Story 3 - La génération reçoit l'ADR entier (Priority: P1)

Quand la recherche retient un ADR pour une génération, l'IA reçoit l'ADR entier (contexte, décision,
conséquences), et non le seul morceau trouvé. Aujourd'hui, elle reçoit un morceau, au plus 1 500
caractères, et répond que « le texte complet de l'ADR 0017 n'est pas fourni ». Le volume transmis
reste borné : quand les ADR retenus sont longs, l'outil en transmet moins, entiers, plutôt que
davantage de fragments.

**Why this priority**: c'est ce qui rend les ADR utiles à la génération ; une décision coupée de son
contexte ou de ses conséquences pousse l'IA à refuser de conclure ou à deviner.

**Independent Test**: un besoin dont l'ADR le plus proche est découpé en trois morceaux, la
recherche ne retenant que le troisième : la génération reçoit les trois, dans l'ordre, et cite la
décision.

**Acceptance Scenarios**:

1. **Given** un ADR découpé en 3 morceaux dont seul le troisième est retenu par la recherche,
   **When** la génération est préparée, **Then** l'ADR est transmis en entier, morceaux dans
   l'ordre, une seule fois.
2. **Given** deux morceaux du même ADR retenus, **When** la génération est préparée, **Then** l'ADR
   est transmis une seule fois.
3. **Given** des ADR retenus dont le total dépasse le budget des ADR, **When** la génération est
   préparée, **Then** les ADR les plus proches sont transmis entiers jusqu'au budget, les suivants
   ne le sont pas ; un ADR seul plus long que le budget d'un ADR est coupé à la fin de sa dernière
   section complète, avec la mention « ADR tronqué ».
4. **Given** un ADR remplacé retenu, **When** la génération est préparée, **Then** c'est son
   successeur qui est transmis, entier (règle de la 002).
5. **Given** la même génération rejouée deux fois avec les mêmes entrées, **When** elle est
   préparée, **Then** les ADR transmis et leur texte sont identiques (reproductibilité,
   constitution II).

---

### Edge Cases

- Un ADR sans numéro reconnaissable (reconnu par son titre « Architecture Decision Record : … ») :
  sa ligne apparaît en fin de liste, numéro vide, triée par titre.
- Le même numéro écrit différemment (« ADR-5 », « 0005 », « 5. … ») : c'est le même numéro, la
  comparaison se fait sur sa valeur.
- Un successeur introuvable dans les ADR du projet : le successeur cité est affiché tel quel, avec
  « introuvable ».
- Une page de documentation non joignable depuis le poste (site interne coupé) : le lien est
  montré quand même ; c'est le navigateur qui échoue.
- Une source de documentation retirée du projet : ses ADR disparaissent de la page à la
  synchronisation suivante, comme de l'index.
- Une indexation en cours : la page montre l'état actuel de l'index et le dit (« indexation en
  cours »).
- Un ADR plus long que tout le budget des ADR : transmis tronqué à une limite de section, seul.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Les compteurs de la connaissance du projet DOIVENT compter les ADR et les sections de
  documentation, et non les morceaux produits par le découpage pour la recherche.
- **FR-002**: Le compteur des ADR de la page Contexte DOIT être un lien vers la page des ADR du
  projet quand il vaut au moins 1, et un simple texte sinon.
- **FR-003**: La page des ADR DOIT appartenir à un projet et n'être accessible que depuis son
  contexte ; elle liste les ADR de ce seul projet.
- **FR-004**: Chaque ligne DOIT montrer le numéro, le titre, le statut (accepté, proposé, remplacé,
  obsolète, inconnu) et, pour un ADR remplacé, le successeur cité.
- **FR-005**: Les lignes DOIVENT être triées par valeur de numéro croissante, puis par titre ; les
  ADR sans numéro viennent en dernier.
- **FR-006**: Le titre DOIT être un lien vers la page de documentation de l'ADR, ouvert dans un
  nouvel onglet ; la page NE DOIT PAS montrer le texte des ADR.
- **FR-007**: La page DOIT afficher le nombre d'ADR par statut et un filtre par statut ; le filtre
  ne change pas le résumé.
- **FR-008**: Les ADR remplacés et obsolètes DOIVENT être grisés et porter la mention « non transmis
  à l'IA ».
- **FR-009**: Deux ADR ou plus portant le même numéro DOIVENT être signalés « numéro en double » ;
  un successeur cité mais absent des ADR du projet DOIT être signalé « introuvable ».
- **FR-010**: Quand la recherche retient un morceau d'ADR, la génération DOIT recevoir l'ADR entier,
  morceaux dans l'ordre, une seule fois par ADR.
- **FR-011**: Le volume des ADR transmis DOIT rester borné : les ADR les plus proches passent entiers
  jusqu'au budget, et les suivants ne passent pas ; un ADR seul plus long que la part d'un ADR est
  coupé à la fin d'une section complète et marqué « ADR tronqué ».
- **FR-012**: Les règles de la 002 sur les ADR transmis restent valables : un ADR remplacé cède la
  place à son successeur, un obsolète n'est pas transmis.
- **FR-013**: La page et les compteurs NE DOIVENT rien envoyer à un service tiers ; aucun contenu
  d'ADR n'est écrit au journal.
- **FR-014**: La page DOIT respecter le niveau WCAG 2.1 AA (tableau navigable au clavier, statut non
  porté par la seule couleur, liens annonçant l'ouverture d'un nouvel onglet).

### Key Entities

- **ADR du projet** : une décision reconnue dans la documentation d'un projet ; numéro (valeur
  entière, peut manquer), titre, statut, successeur cité, adresse de sa page ; formé d'un ou de
  plusieurs morceaux dans l'index.
- **Résumé des ADR** : nombre d'ADR par statut et signalements (doublons, successeurs introuvables)
  d'un projet.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Sur un projet de 21 ADR découpés en 57 morceaux, le panneau affiche « 21 ADR » et la
  page en liste 21, dans 100 % des cas.
- **SC-002**: L'utilisateur trouve un ADR donné et ouvre sa page de documentation en moins de
  15 secondes depuis la page Contexte.
- **SC-003**: Pour chaque ADR transmis à une génération, l'IA reçoit 100 % de son texte indexé
  (ou jusqu'à sa dernière section complète s'il est marqué tronqué), jamais un morceau isolé.
- **SC-004**: Sur le jeu de référence et le golden set, la qualité des générations ne baisse pas
  (scores de l'évaluation au moins égaux à la référence) et l'IA ne signale plus un ADR transmis
  comme « non fourni ».

## Assumptions

- Les ADR sont ceux que la 002 et le correctif adr-tools de la 003 reconnaissent ; la page ne change
  pas la reconnaissance.
- Le budget global des ADR transmis à une génération reste celui de la 002 (6 000 caractères) ; seule
  sa répartition change : des ADR entiers, moins nombreux s'il le faut. Le budget exact est fixé au
  plan, au vu de l'évaluation.
- Le découpage en morceaux reste utilisé pour la recherche : il n'est pas modifié.
- La page est en lecture seule ; elle ne modifie ni la documentation ni l'index.
- Le choix de la source à réindexer, la reprise des étapes après une erreur inattendue et la lecture
  des `!docs` du workspace Structurizr restent hors du périmètre (proposés séparément).
