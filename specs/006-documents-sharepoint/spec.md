# Feature Specification: Documents SharePoint par dossier synchronisé

**Feature Branch**: `feature/009-documents-sharepoint`

**Created**: 2026-11-06

**Status**: Draft

**Input**: User description: "Nous allons ajouter un nouveau connecteur de source sharepoint pour allimenter le RAG. Pour la connection, nous utiliserons le même systeme que pour le compte github, a savoir une authentification OIDC si c'est possible. T commencera par voir si c'est possible sans avoir a declarer specialement une application au près de sharepoint. 9a allimentera la base de donnée vectorielle. Les documents n'ont pas besoin d'être stocké en local, ils seront récupéré à la vollé pour arrichir le context e si besoin. on peut y trouver du word, du excle du pdf et du html. il faudrait aussi prevoir une option de filtre de type Globbing pour exlure des extensions, des paths, etc."

Prolonge la connaissance du projet de `specs/002-projets-contexte-mcp/` (sources de
documentation), `specs/004-adr-du-projet/` (ADR entier transmis) et
`specs/005-reindexation-par-source/` (réindexation par source, étapes indépendantes), dont les
décisions restent valables.

## Clarifications

### Session 2026-11-06

- Q: Connexion à SharePoint sans application déclarée par l'IT ? → A: Pas possible proprement.
  Depuis juillet 2025, lire des fichiers SharePoint au nom d'un utilisateur exige l'accord d'un
  administrateur ; les applications partagées qui le contournaient ont été retirées en 2024 ;
  réutiliser l'identifiant d'une application Microsoft est contraire à ses conditions (décision
  déjà prise pour GitHub dans la 001). Retenu par l'utilisateur : lire le dossier SharePoint
  **synchronisé par OneDrive** sur le poste, sans connexion à gérer.
- Q: « Les documents n'ont pas besoin d'être stockés en local » ? → A: Les fichiers ne sont jamais
  copiés par l'outil ; le texte découpé et ses vecteurs restent dans l'index, pour la recherche par
  les termes et par le sens ; le document entier est relu à la volée quand la génération en a
  besoin.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Indexer un dossier de documents (Priority: P1) 🎯 MVP

La documentation du projet de l'utilisateur est dans un dossier SharePoint partagé : des documents
Word, des classeurs Excel, des PDF et des pages HTML. L'utilisateur synchronise ce dossier sur son
poste avec OneDrive, puis le déclare dans les sources de documentation du projet comme « dossier
de documents ». L'outil lit les documents, en indexe le texte (découpé, avec ses vecteurs) et les
rend disponibles pour la génération, sans en copier aucun.

**Why this priority**: c'est la demande : alimenter la connaissance du projet avec la
documentation qui vit dans SharePoint et non dans un site Antora.

**Independent Test**: déclarer un dossier contenant un document Word, un classeur Excel, un PDF et
une page HTML, lancer l'indexation : les quatre sont comptés et retrouvés par la recherche d'essai
sur un mot de leur contenu.

**Acceptance Scenarios**:

1. **Given** un dossier synchronisé contenant un .docx, un .xlsx, un .pdf et un .html, **When**
   l'utilisateur le déclare comme dossier de documents, **Then** l'indexation lit les quatre, et
   la recherche d'essai retrouve chacun par un mot de son contenu, avec son titre et son chemin.
2. **Given** le dossier contient d'autres fichiers (.doc, .xls, .pptx, images, archives), **When**
   l'indexation s'exécute, **Then** ils sont ignorés, et le bilan dit combien de fichiers ont été
   ignorés pour leur format.
3. **Given** un fichier illisible (protégé par mot de passe, chiffré, corrompu) ou un PDF sans
   texte (scanné), **When** l'indexation s'exécute, **Then** il est signalé dans le détail avec son
   chemin et le motif, et les autres fichiers sont indexés.
4. **Given** un dossier indexé, **When** un document est modifié dans SharePoint puis synchronisé,
   **Then** la synchronisation suivante relit ce document seul ; **When** un document est supprimé,
   **Then** la réindexation de la source le retire de l'index.
5. **Given** un ADR rédigé en Word ou en PDF (titre « ADR-17 … » ou « 17. … », ligne de statut),
   **When** l'indexation s'exécute, **Then** il est reconnu comme ADR et suit les règles des
   features 003 et 004 (statut, successeur, page des ADR, ADR entier transmis).
6. **Given** le dossier de documents est déclaré, **When** l'utilisateur ouvre le formulaire des
   sources, **Then** une aide explique comment synchroniser un dossier SharePoint avec OneDrive et
   où le trouver sur le poste.
7. **Given** un dossier Antora déjà déclaré comme dossier, **When** la feature est livrée,
   **Then** il est lu comme avant.

---

### User Story 2 - Exclure des fichiers par motifs (Priority: P1)

Le dossier SharePoint contient des archives, des brouillons et des classeurs de suivi qui
pollueraient la connaissance du projet. L'utilisateur saisit des motifs d'exclusion pour ce
dossier de documents, un par ligne : `*.xlsx`, `Archives/**`, `**/brouillon*`. Avant d'indexer, il
voit combien de fichiers sont retenus et combien sont exclus.

**Why this priority**: sans filtre, un dossier SharePoint réel amène du bruit dans les questions et
les spécifications. Demandé explicitement.

**Independent Test**: dossier de 10 fichiers dont 3 sous `Archives/` et 2 classeurs ; motifs
`Archives/**` et `*.xlsx` : l'aperçu dit « 5 retenus, 5 exclus », et l'index ne contient que les
5 retenus.

**Acceptance Scenarios**:

1. **Given** un dossier de documents, **When** l'utilisateur saisit des motifs d'exclusion,
   **Then** chaque motif s'applique au chemin du fichier relatif au dossier : `*` dans un nom,
   `**` sur plusieurs niveaux de dossiers, `?` pour un caractère, sans tenir compte de la casse.
2. **Given** des motifs saisis, **When** l'utilisateur demande l'aperçu, **Then** il voit le
   nombre de fichiers retenus et exclus, et quelques exemples de chacun.
3. **Given** un motif invalide (crochet non fermé, chemin absolu, `..`), **When** l'utilisateur
   l'enregistre, **Then** il est refusé avec le motif en cause et la raison.
4. **Given** des documents déjà indexés, **When** un nouveau motif les exclut et que la source est
   réindexée, **Then** ils sont retirés de l'index.
5. **Given** des motifs qui excluent tout, **When** l'indexation s'exécute, **Then** le bilan dit
   que le dossier ne contient aucun document retenu, sans erreur.

---

### User Story 3 - Le document entier, relu à la volée, pour la génération (Priority: P2)

Quand la recherche retient un passage d'un document du dossier, la génération reçoit le document
entier, relu dans le dossier synchronisé au moment de la génération, comme un ADR entier
(feature 004). L'outil n'a pas de copie du document : il le relit à la source.

**Why this priority**: un passage isolé d'un document Word ou d'un PDF manque souvent de contexte ;
c'est le sens de « récupérés à la volée pour enrichir le contexte ».

**Independent Test**: un document de 5 sections, dont une seule contient le terme recherché :
la génération reçoit les 5 sections, dans l'ordre, dans la limite du budget.

**Acceptance Scenarios**:

1. **Given** un passage d'un document retenu par la recherche, **When** la génération est
   préparée, **Then** elle reçoit le document entier relu dans le dossier, borné comme l'ADR
   entier (coupé à une section au-delà, avec la mention de la coupe).
2. **Given** le fichier n'est plus lisible au moment de la génération (déplacé, hors ligne et non
   téléchargé), **When** la génération est préparée, **Then** les passages indexés sont transmis à
   la place, sans échec.
3. **Given** deux préparations identiques, **When** le dossier n'a pas changé, **Then** le texte
   transmis est identique.

---

### Edge Cases

- **Fichier « à la demande » non téléchargé, poste hors ligne** : la lecture échoue pour ce
  fichier, signalé « non disponible hors ligne » ; les autres sont indexés ; l'indexation précédente
  du fichier est gardée.
- **Dossier non synchronisé, déplacé ou supprimé** : la source est signalée illisible ; l'index
  précédent est gardé (règle des sources de la 002).
- **Très gros classeur ou document** : lecture bornée par fichier (taille, nombre de cellules ou de
  pages) ; au-delà, le fichier est signalé « trop volumineux ».
- **Lien symbolique ou chemin qui sort du dossier** : non suivi.
- **Nombre de fichiers ou durée au-delà des limites de la source** : lecture arrêtée, signalée
  « limite atteinte », rien n'est retiré (règle de la 002).
- **Motifs modifiés pendant une indexation** : pris en compte à l'indexation suivante.
- **Fichier temporaire de bureautique** (`~$…`, `.~lock…`) : toujours ignoré.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Les sources de documentation d'un projet DOIVENT accepter un nouveau type, « dossier
  de documents » : un dossier local (absolu, existant, lisible), distinct du dossier Antora, dont
  le comportement ne change pas.
- **FR-002**: Un dossier de documents DOIT être lu récursivement, et ses fichiers Word (.docx),
  Excel (.xlsx), PDF (.pdf) et HTML (.html, .htm) indexés : titre, texte découpé en passages,
  vecteurs, chemin relatif comme référence.
- **FR-003**: Les autres fichiers DOIVENT être ignorés et comptés dans le bilan ; les fichiers
  temporaires de bureautique ne sont pas comptés.
- **FR-004**: Un fichier illisible, protégé, chiffré, sans texte ou trop volumineux DOIT être
  signalé avec son chemin relatif et le motif, sans arrêter la lecture des autres.
- **FR-005**: Les fichiers NE DOIVENT jamais être copiés ni conservés par l'outil ; seuls le texte
  découpé, ses vecteurs et ses métadonnées (titre, chemin, date de modification) sont gardés dans
  l'index. Le texte n'est jamais journalisé.
- **FR-006**: Un document inchangé (même date de modification et même taille) NE DOIT PAS être
  relu à la synchronisation ; un document modifié l'est ; un document supprimé ou exclu est retiré
  à la réindexation de la source.
- **FR-007**: Un ADR rédigé en Word, PDF ou HTML dans un dossier de documents DOIT être reconnu
  avec les règles existantes (numéro, statut, successeur) et suivre les features 003 et 004.
- **FR-008**: Un dossier de documents DOIT pouvoir porter une liste de motifs d'exclusion
  (globbing : `*`, `**`, `?`, classes `[…]`), appliqués au chemin relatif, sans tenir compte de la
  casse ; 50 motifs au plus, 200 caractères chacun.
- **FR-009**: Un motif invalide, absolu ou contenant `..` DOIT être refusé avec le motif et la
  raison.
- **FR-010**: L'utilisateur DOIT pouvoir voir, avant d'indexer, le nombre de fichiers retenus et
  exclus par les motifs, avec au plus 10 exemples de chacun.
- **FR-011**: Quand la sélection de la connaissance retient un passage d'un document de ce type, la
  génération DOIT recevoir le document entier relu dans le dossier, dans le budget et avec la coupe
  de l'ADR entier (feature 004) ; à défaut de lecture, les passages indexés.
- **FR-012**: La lecture d'un dossier de documents DOIT respecter des limites par fichier (taille,
  volume extrait) et par source (nombre de fichiers, durée), et signaler la limite atteinte.
- **FR-013**: La réindexation par source et l'indépendance des étapes (feature 005) DOIVENT
  s'appliquer au dossier de documents.
- **FR-014**: Le formulaire des sources DOIT proposer le type « dossier de documents », la saisie
  des motifs, l'aperçu, et une aide pour synchroniser un dossier SharePoint avec OneDrive ;
  accessible au clavier et aux lecteurs d'écran (WCAG 2.1 AA).

### Key Entities

- **Dossier de documents** : source de documentation d'un projet ; chemin du dossier, motifs
  d'exclusion, date et bilan de la dernière lecture (fichiers lus, ignorés, exclus, en erreur).
- **Document** : fichier retenu du dossier ; chemin relatif, titre, format, date de modification,
  taille ; ses passages et vecteurs sont dans l'index, jamais le fichier.
- **Motif d'exclusion** : expression de globbing appliquée au chemin relatif.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un dossier de 200 documents mixtes (Word, Excel, PDF, HTML) est indexé en moins de
  5 minutes sur un poste de développeur.
- **SC-002**: 100 % des fichiers exclus par un motif sont absents de l'index après réindexation.
- **SC-003**: Un document déclaré est retrouvé par la recherche d'essai sur un mot de son contenu,
  pour chacun des quatre formats.
- **SC-004**: Aucune copie d'un document n'apparaît dans les données de l'outil après indexation
  et génération (vérifié de bout en bout).
- **SC-005**: Une synchronisation sans changement du dossier ne relit aucun document.

## Assumptions

- OneDrive est installé et connecté au compte professionnel, et la synchronisation est autorisée
  par l'IT. Lire un fichier « à la demande » le fait télécharger par OneDrive : ce stockage est
  celui de OneDrive, pas de l'outil.
- Pour synchroniser un dossier partagé (macOS) : dans SharePoint, « Synchroniser » sur la
  bibliothèque, ou « Ajouter un raccourci à Mes fichiers » sur le dossier partagé ; le dossier
  apparaît sous `~/Library/CloudStorage/OneDrive-<Organisation>/` ou
  `~/Library/CloudStorage/OneDrive-SharedLibraries-<Organisation>/` ; « Toujours conserver sur
  cet appareil » le rend lisible hors ligne.
- Formats hors périmètre : PowerPoint, anciens formats binaires (.doc, .xls), images (pas de
  reconnaissance de texte).
- Hors périmètre : la connexion directe à SharePoint par une application déclarée par l'IT ;
  piste documentée pour une feature future.
- Les motifs d'exclusion ne s'appliquent qu'au dossier de documents ; les sources Antora gardent
  leur lecture actuelle.
