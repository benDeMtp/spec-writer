# Feature Specification: Confluence, source et date des extraits, fiabilité des sources

**Feature Branch**: `feature/020-confluence-fiabilite`

**Created**: 2026-11-17

**Status**: Draft

**Input**: User description: "Feature 016 : source Confluence on-prem, métadonnées de source et de date jusqu'au modèle, niveaux de fiabilité des sources. Décisions prises le 17/11 : Confluence Server / Data Center comme nouvelle source de connaissance d'un projet, une ou plusieurs pages racines dont toute la descendance est indexée, jeton personnel au trousseau, proxy respecté ; chaque extrait transmis au modèle porte sa source et sa date de mise à jour ; chaque source a un niveau de fiabilité réglable (référence, interprétation, à confirmer ; SharePoint référence, Confluence interprétation par défaut), transmis au modèle avec une règle d'arbitrage, et des places réservées aux extraits de référence dans les résultats d'une recherche (approche B, sans poids caché dans le classement). Motif : Confluence est l'interprétation par l'équipe des demandes métier qui vivent dans SharePoint ; le risque est qu'une interprétation dérive de la demande d'origine sans que personne ne le voie. Hors périmètre : Confluence Cloud, écriture dans Confluence, pièces jointes Confluence."

## Contexte

La connaissance d'un projet rassemble aujourd'hui les tickets Jira, la documentation Antora, un
dossier SharePoint synchronisé, le modèle d'architecture et la fiche générale. L'équipe tient aussi
dans Confluence (installé dans l'entreprise) sa compréhension des demandes métier, qui, elles,
vivent dans SharePoint. Confluence est donc une lecture de SharePoint : utile, plus détaillée,
mais susceptible de s'en écarter.

Aujourd'hui, un extrait remis à l'IA ne dit ni de quelle source il vient au-delà de sa sorte
(ticket, section de documentation, ADR, élément d'architecture), ni de quand il date, et toutes les
sources pèsent pareil. Une page Confluence qui contredit la demande SharePoint passe donc pour
aussi sûre qu'elle, sans que personne ne le voie.

Cette feature ajoute Confluence comme source, fait connaître à l'IA la source et la date de chaque
extrait, et donne à chaque source un niveau de fiabilité qui tranche les écarts et les fait
signaler.

## Clarifications

### Session 2026-11-17

- Q: Le niveau de fiabilité se règle-t-il par source entière ou aussi par racine Confluence et par dossier ? → A: Par source, et en plus par racine Confluence ; chaque racine prend par défaut le niveau de Confluence et peut en changer. Pas de niveau par sous-dossier SharePoint.
- Q: Où l'écart entre une interprétation et une référence apparaît-il dans une spécification générée ? → A: Dans une rubrique « Écarts entre sources » (référence, interprétation, leurs dates, ce qui diffère), reprise à l'export et vers Jira ; pas dans « Hypothèses ».
- Q: Le serveur Confluence est-il déclaré une fois pour l'outil ou par projet ? → A: Une seule fois, dans Configuration (adresse et jeton), comme Jira ; chaque projet ne déclare que ses racines.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Indexer des pages Confluence et toute leur descendance (Priority: P1) 🎯 MVP

Dans les réglages du projet, l'utilisateur déclare son serveur Confluence (adresse et jeton
personnel), puis une ou plusieurs pages racines, par leur adresse. Chaque racine se comporte comme
un dossier : la page et toutes ses pages descendantes, à toute profondeur, sont indexées dans la
connaissance du projet. L'agent les trouve dans ses recherches et la génération les utilise comme
le reste de la documentation.

**Why this priority**: c'est la nouvelle source demandée ; sans elle, les deux autres parties
n'ont rien à arbitrer entre Confluence et SharePoint.

**Independent Test**: sur un Confluence simulé portant un arbre de pages, déclarer une racine, lancer
l'indexation, et vérifier que chaque page de l'arbre (et seulement celles-là) est trouvée par la
recherche d'essai de la page « Connaissance du projet ».

**Acceptance Scenarios**:

1. **Given** un serveur Confluence déclaré avec un jeton valide, **When** l'utilisateur ajoute une
   page racine par son adresse, **Then** l'outil affiche le titre de la page reconnue et le nombre de
   pages qu'elle contient avec sa descendance, avant toute indexation.
2. **Given** une racine déclarée, **When** l'indexation se termine, **Then** la page racine et toutes
   ses descendantes sont dans la connaissance du projet, les pages hors de cette arborescence n'y
   sont pas, et les compteurs de la page « Connaissance du projet » donnent le nombre de pages
   Confluence indexées.
3. **Given** deux racines dont l'une est descendante de l'autre, **When** l'indexation se termine,
   **Then** chaque page n'est indexée qu'une fois.
4. **Given** des pages indexées, **When** une page est modifiée, ajoutée, supprimée ou déplacée hors
   de l'arborescence dans Confluence, puis que l'utilisateur réindexe la source Confluence, **Then**
   l'index reflète l'arborescence actuelle : page modifiée relue, page nouvelle ajoutée, page
   supprimée ou sortie retirée ; les pages inchangées ne sont pas relues.
5. **Given** une adresse qui ne désigne pas une page lisible avec le jeton (page inexistante,
   droits insuffisants, autre serveur), **When** l'utilisateur l'ajoute, **Then** elle est refusée
   avec un message qui dit pourquoi.
6. **Given** Confluence injoignable ou le jeton refusé pendant une réindexation, **When** elle
   s'arrête, **Then** les pages déjà indexées restent disponibles et la page « Connaissance du
   projet » montre l'erreur et l'heure de la dernière indexation réussie.

---

### User Story 2 - L'IA connaît la source et la date de chaque extrait (Priority: P1)

Chaque extrait de la connaissance remis à l'IA, dans les recherches de l'agent comme dans la
génération d'une spécification, dit de quelle source il vient (Jira, Antora, SharePoint, Confluence,
modèle d'architecture, fiche générale, document joint, spécification) et de quand date sa dernière
mise à jour à la source. L'agent peut ainsi dire « selon la page Confluence mise à jour le 3 mars »
et préférer l'information la plus récente d'une même source.

**Why this priority**: c'est la condition de l'arbitrage (US3), et elle profite à toutes les
sources existantes, Confluence ou non.

**Independent Test**: sur un projet avec des extraits de plusieurs sources aux dates connues, poser
une question à l'agent et vérifier, dans les résultats de recherche affichés et dans ce que reçoit
l'IA (rejeu), que chaque extrait porte sa source et sa date.

**Acceptance Scenarios**:

1. **Given** des extraits de Jira, d'Antora, de SharePoint et de Confluence dans l'index, **When**
   l'agent fait une recherche, **Then** chaque extrait transmis à l'IA porte le nom de sa source et
   sa date de mise à jour, et les étapes de recherche affichées dans la conversation montrent la
   source de chaque résultat.
2. **Given** une génération de spécification qui utilise la connaissance du projet, **When** elle
   est envoyée à l'IA, **Then** chaque document, ticket ou ADR transmis porte sa source et sa date.
3. **Given** un extrait dont la source ne fournit pas de date, **When** il est transmis, **Then** il
   porte « date inconnue », jamais la date de l'indexation présentée comme date de mise à jour.
4. **Given** deux extraits de la même source qui se contredisent, **When** l'agent répond, **Then**
   il retient le plus récent et signale l'écart en citant les deux.

---

### User Story 3 - Une source de référence l'emporte et les écarts sont signalés (Priority: P2)

Dans les réglages du projet, chaque source a un niveau de fiabilité : **référence**,
**interprétation** ou **à confirmer**. Par défaut, le dossier SharePoint est une référence (il porte
les demandes métier), Confluence une interprétation (la compréhension de ces demandes par
l'équipe), et les autres sources sont des interprétations. L'utilisateur peut changer chaque
niveau. L'IA reçoit le niveau de chaque extrait avec une règle : quand une interprétation s'écarte
d'une référence, la référence l'emporte et l'écart est signalé, en citant les deux extraits. Pour
qu'une référence ne soit pas écartée par le nombre, une recherche réserve des places à ses extraits
de référence pertinents.

**Why this priority**: c'est la précaution demandée ; elle suppose que les sources et les dates
soient transmises (US2), et prend tout son sens avec Confluence (US1).

**Independent Test**: sur un projet où une demande SharePoint fixe une règle et une page
Confluence en dit une autre, poser la question à l'agent : la réponse suit la demande SharePoint,
signale l'écart avec la page Confluence et cite les deux.

**Acceptance Scenarios**:

1. **Given** un projet nouveau ou existant, **When** l'utilisateur ouvre ses réglages, **Then**
   chaque source déclarée affiche son niveau, aux valeurs par défaut tant qu'il n'a rien changé,
   et chaque racine Confluence affiche le sien (celui de Confluence tant qu'il n'est pas précisé).
2. **Given** une demande SharePoint (référence) et une page Confluence (interprétation) qui se
   contredisent sur une règle, **When** l'agent répond à une question sur cette règle, **Then** il
   suit la demande SharePoint, signale l'incohérence comme les autres incohérences de la
   conversation, et cite les deux extraits.
3. **Given** une recherche dont les meilleurs résultats sont tous des pages Confluence alors qu'un
   extrait SharePoint pertinent est classé plus loin, **When** les résultats sont retenus, **Then**
   au moins un extrait de référence figure parmi eux, et l'ordre des autres résultats n'est pas
   modifié.
4. **Given** une recherche sans aucun extrait de référence pertinent, **When** les résultats sont
   retenus, **Then** ils sont identiques à ce qu'ils seraient sans niveaux de fiabilité.
5. **Given** une source passée « à confirmer », **When** l'agent s'appuie sur elle, **Then** il le
   dit dans sa réponse (« selon une source à confirmer »).
6. **Given** la génération d'une spécification, **When** une interprétation contredit une
   référence, **Then** la spécification suit la référence et l'écart apparaît dans sa rubrique
   « Écarts entre sources », avec les deux extraits, leurs sources et leurs dates.

---

### Edge Cases

- **Racine très large** : une racine dont la descendance dépasse la limite de pages d'une source
  est refusée à l'ajout, avec le nombre de pages trouvé ; l'utilisateur choisit des racines plus
  étroites.
- **Pages restreintes** : une page descendante que le jeton ne peut pas lire est ignorée et comptée
  (« 3 pages non lisibles avec ce jeton »), sans arrêter l'indexation.
- **Page racine supprimée ou déplacée** dans Confluence : la réindexation la signale, retire ses
  pages de l'index, et garde la racine déclarée en erreur jusqu'à ce que l'utilisateur la retire ou
  la corrige.
- **Même page via deux racines** : indexée une seule fois.
- **Contenu dynamique** (macros, inclusions d'autres pages, tableaux Jira) : le texte indexé est
  celui que Confluence affiche à la lecture de la page ; une macro que le serveur ne rend pas en
  texte est ignorée.
- **Pièces jointes** des pages : ignorées (hors périmètre).
- **Contenu adressé à une IA** dans une page Confluence : c'est une donnée, jamais une consigne,
  comme pour les autres sources.
- **Serveur avec une autorité de certification interne** ou en HTTP sur un réseau de confiance :
  mêmes options que pour Jira (certificat PEM, HTTP accepté explicitement).
- **Proxy d'entreprise** : appliqué à Confluence comme à Jira, avec les mêmes exclusions.
- **Toutes les sources au même niveau** : aucune place réservée, résultats inchangés.
- **Plusieurs extraits de référence pertinents** : les places réservées vont aux mieux classés
  d'entre eux.
- **Deux références qui se contredisent** : aucune ne l'emporte d'office ; l'écart est signalé avec
  les deux extraits et leurs dates.
- **Fiche générale, documents joints et documents désignés** : ils gardent leur place actuelle (la
  fiche l'emporte sur les sources ; un document désigné est exploité en priorité) ; ils ne prennent
  pas de niveau réglable.
- **Changement de niveau** : pris en compte à la recherche ou à la génération suivante, sans
  réindexation.

## Requirements *(mandatory)*

### Functional Requirements

**Confluence (US1)**

- **FR-001**: L'utilisateur DOIT pouvoir déclarer, une seule fois pour l'outil, dans
  Configuration (comme Jira), un serveur Confluence installé dans l'entreprise (adresse et jeton
  personnel), et tester la connexion, qui nomme le compte reconnu. Les projets n'y déclarent que
  leurs racines ; sans serveur déclaré, les réglages du projet mènent à Configuration.
- **FR-002**: Le jeton Confluence DOIT être gardé dans le trousseau du système, jamais en clair dans
  les données de l'outil, les journaux ou les réponses de l'outil.
- **FR-003**: L'utilisateur DOIT pouvoir déclarer, dans les réglages d'un projet, une ou plusieurs
  pages racines par leur adresse, et en retirer.
- **FR-004**: À l'ajout d'une racine, l'outil DOIT vérifier qu'elle est lisible et afficher son titre
  et le nombre de pages de sa descendance ; une racine illisible, d'un autre serveur, ou dont la
  descendance dépasse la limite DOIT être refusée avec la raison.
- **FR-005**: L'indexation DOIT couvrir chaque racine et toutes ses pages descendantes à toute
  profondeur, chaque page une seule fois, et rien d'autre.
- **FR-006**: La réindexation de la source Confluence DOIT relire les pages modifiées depuis la
  dernière indexation, ajouter les nouvelles, et retirer celles qui ont été supprimées ou sorties
  de l'arborescence ; les pages inchangées ne DOIVENT pas être relues.
- **FR-007**: Confluence DOIT être une source de la réindexation par source (005), avec ses
  compteurs et ses erreurs sur la page « Connaissance du projet ».
- **FR-008**: Une erreur de Confluence (serveur injoignable, jeton refusé, page racine disparue) NE
  DOIT pas retirer de l'index les pages déjà indexées des autres racines, et DOIT être affichée avec
  l'heure de la dernière indexation réussie.
- **FR-009**: Les appels à Confluence DOIVENT respecter le proxy d'entreprise et ses exclusions, les
  options de certificat et d'HTTP de Jira, et un débit borné comme pour Jira.
- **FR-010**: L'outil NE DOIT jamais écrire dans Confluence ni lui envoyer le contenu d'une
  spécification.

**Source et date (US2)**

- **FR-011**: Chaque extrait de la connaissance transmis à l'IA (recherches de l'agent, génération,
  modification, discussion d'une spécification) DOIT porter le nom de sa source et sa date de
  dernière mise à jour à la source.
- **FR-012**: Une source qui ne fournit pas de date DOIT être transmise comme « date inconnue » ;
  la date d'indexation ne DOIT jamais tenir lieu de date de mise à jour.
- **FR-013**: Les résultats de recherche affichés dans la conversation DOIVENT montrer la source de
  chaque résultat, et sa date quand elle est connue.
- **FR-014**: La consigne de l'IA DOIT lui dire qu'entre deux extraits de même source qui se
  contredisent, le plus récent prime, et que l'écart est signalé.

**Fiabilité (US3)**

- **FR-015**: Chaque source de connaissance d'un projet (Jira, Antora, dossier SharePoint, Confluence,
  modèle d'architecture, spécifications du projet) DOIT avoir un niveau de fiabilité parmi
  « référence », « interprétation » et « à confirmer », réglable dans les réglages du projet.
- **FR-016**: Par défaut, le dossier SharePoint DOIT être « référence » et toutes les autres
  sources « interprétation » ; un projet existant prend ces valeurs sans action de l'utilisateur.
- **FR-016a**: Chaque racine Confluence DOIT pouvoir avoir son propre niveau ; sans réglage, elle
  prend celui de la source Confluence. Une page atteinte par deux racines prend le plus haut de
  leurs niveaux. Il n'y a pas de niveau par sous-dossier du dossier SharePoint.
- **FR-017**: Le niveau de la source DOIT accompagner chaque extrait transmis à l'IA.
- **FR-018**: La consigne de l'IA DOIT dire : quand une interprétation ou une source à confirmer
  s'écarte d'une référence, la référence l'emporte, et l'écart est signalé en citant les deux
  extraits ; entre deux références, aucune ne l'emporte d'office et l'écart est signalé ; une
  réponse qui s'appuie sur une source à confirmer le dit.
- **FR-019**: Quand une recherche trouve des extraits pertinents de niveau référence, les
  résultats retenus DOIVENT en contenir au moins deux (ou tous s'il y en a moins), les mieux
  classés d'entre eux : ceux qui n'y figurent pas déjà prennent les dernières places, au plus deux
  places ainsi réservées ; les autres résultats gardent leur ordre. Sans extrait de référence
  pertinent, ou si toutes les sources ont le même niveau, les résultats DOIVENT être ceux
  d'aujourd'hui.
- **FR-020**: Le niveau de fiabilité NE DOIT pas modifier le score des résultats autrement que par
  ces places réservées.
- **FR-021**: À la génération (et à la modification) d'une spécification, la spécification DOIT
  suivre la référence, et chaque écart entre une interprétation et une référence DOIT figurer dans
  une rubrique « Écarts entre sources » : référence et interprétation citées, leurs sources, leurs
  dates et ce qui diffère. La rubrique est omise sans écart, et reprise à l'écran, à l'export
  Markdown et dans la description envoyée vers Jira.
- **FR-022**: Un changement de niveau DOIT valoir dès la recherche ou la génération suivante, sans
  réindexation.

**Qualité**

- **FR-023**: L'évaluation réelle DOIT mesurer qu'une page Confluence qui contredit une demande
  SharePoint est signalée par l'agent, avec les deux extraits cités, et que la réponse suit la
  demande SharePoint.
- **FR-024**: Les nouveaux écrans et réglages DOIVENT respecter les exigences d'interface de 001
  (accessibilité WCAG 2.1 AA, messages d'erreur qui disent quoi faire).

### Key Entities

- **Serveur Confluence** : adresse, jeton (au trousseau), options de connexion (certificat,
  HTTP accepté) ; un seul par installation, comme Jira.
- **Racine Confluence** : page désignée par un projet ; titre, état (valide, en erreur), nombre de
  pages de sa descendance à la dernière indexation, niveau de fiabilité propre (facultatif, celui
  de Confluence sinon).
- **Page Confluence indexée** : élément de la connaissance du projet, de la sorte « section de
  documentation » ; titre, texte, adresse, date de dernière modification dans Confluence, racine(s)
  d'appartenance.
- **Niveau de fiabilité** : référence, interprétation ou à confirmer ; un par source et par projet,
  et un par racine Confluence qui le précise.
- **Écart entre sources** : dans une version de spécification, une référence et une interprétation
  qui divergent ; les deux références citées, et ce qui diffère.
- **Extrait transmis** : texte d'un élément de la connaissance, avec sa référence, sa source, sa
  date de mise à jour (ou « date inconnue ») et le niveau de sa source.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Sur un arbre de démonstration, 100 % des pages descendantes d'une racine sont
  indexées, et aucune page hors de l'arbre.
- **SC-002**: Une réindexation sans changement dans Confluence ne relit aucune page ; une
  réindexation après la modification d'une page n'en relit qu'une.
- **SC-003**: Un arbre de 500 pages s'indexe en moins de 5 minutes avec un serveur répondant
  normalement.
- **SC-004**: 100 % des extraits transmis à l'IA portent une source et une date (ou « date
  inconnue »), vérifié sur les rejeux des tests.
- **SC-005**: Dans l'évaluation réelle, au moins 90 % des cas où une page Confluence contredit une
  demande SharePoint sont signalés avec les deux extraits cités, et la réponse suit la demande
  SharePoint.
- **SC-006**: Aucun indicateur existant de l'évaluation (citation, abstention, incohérences,
  qualité des questions et des spécifications) ne recule sous sa base.
- **SC-007**: Quand un extrait de référence pertinent existe, il figure dans les résultats retenus
  dans 100 % des recherches de l'évaluation.

## Assumptions

- Confluence Server ou Data Center, version qui accepte les jetons d'accès personnels ; Confluence
  Cloud est hors périmètre.
- Une page racine se désigne par son adresse (lien de la page ou adresse avec son identifiant) ;
  l'outil en déduit l'identifiant.
- La limite de pages d'une source est de 2 000 pages par projet pour Confluence, du même ordre que
  les autres sources ; au-delà, l'utilisateur choisit des racines plus étroites.
- Le texte indexé d'une page est sa forme affichée, découpée en sections comme les pages Antora ;
  les pièces jointes, commentaires et historiques ne sont pas lus.
- Lecture seule : aucune donnée de spécification n'est envoyée à Confluence ; aucun accord
  supplémentaire n'est demandé au-delà de la déclaration du serveur. L'envoi des extraits à
  Copilot reste couvert par l'accord existant.
- Les dates de mise à jour connues aujourd'hui (tickets Jira, documents du dossier, pages Antora
  quand le site les fournit) sont celles que l'index garde déjà ; aucune source existante n'est
  relue pour cette feature, mais une date manquante devient « date inconnue ».
- « Pertinent » pour les places réservées veut dire : trouvé par la recherche parmi les candidats
  examinés pour cette requête, pas seulement présent dans l'index.
- La fiche générale, les documents joints et les documents désignés gardent leurs règles actuelles
  et ne prennent pas de niveau réglable.
- Le nombre de résultats d'une recherche reste celui d'aujourd'hui (8) ; les places réservées sont
  prises sur ce nombre.
