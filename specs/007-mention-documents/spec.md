# Feature Specification: Mention d'un document avec @

**Feature Branch**: `feature/010-mention-documents`

**Created**: 2026-11-07

**Status**: Draft

**Input**: User description: "Mention @ d'un document dans le besoin, les réponses et l'instruction d'une modification, rôle « Référence » : morceau 1 de la conception docs/superpowers/specs/2026-11-06-documents-designes-design.md (section « Morceau 1 »). Taper @ propose, par autocomplétion dans l'index du projet, les documents des dossiers synchronisés, les pages Antora (page entière) et les ADR ; le document mentionné est transmis d'office à l'IA, avant ceux de la recherche, dans le budget de la connaissance et après la fiche générale. Pas de @ pour Structurizr ni Jira. Cas réel : le mainteneur a écrit « @Aurora_Matrice_Regles_Gestion_Activites_1 » dans une réponse et la matrice n'a pas été transmise (checklists/validation.md de la 006). Le rôle « Base de la spec », le budget selon le modèle et « Joindre un fichier » sont hors périmètre (features suivantes)."

Prolonge `specs/006-documents-sharepoint/` (dossier de documents, document relu entier pour la
génération), `specs/004-adr-du-projet/` (ADR cité par son numéro transmis d'office) et
`specs/002-projets-contexte-mcp/` (connaissance du projet), dont les décisions restent valables.
Conception d'ensemble : `docs/superpowers/specs/2026-11-06-documents-designes-design.md`.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Un document mentionné est transmis à l'IA (Priority: P1) 🎯 MVP

En rédigeant une spécification, l'utilisateur sait qu'un document précis contient l'information :
une matrice de règles de gestion rédigée par le métier, une page de la documentation, un ADR. Il
écrit `@` suivi du nom du document dans le besoin, dans une réponse à une question ou dans
l'instruction d'une modification. L'outil reconnaît le document dans la connaissance du projet et
le transmet à l'IA d'office, en premier, au lieu de laisser la recherche décider. C'est le cas
réel de la 006 : « @Aurora_Matrice_Regles_Gestion_Activites_1 » écrit dans une réponse n'a pas
fait transmettre la matrice, et la spécification a dû en faire une hypothèse.

**Why this priority**: c'est la valeur de la feature, et elle sert même sans l'autocomplétion :
une mention écrite à la main, comme dans le cas réel, doit suffire.

**Independent Test**: dans un projet dont le dossier de documents contient un classeur que la
recherche ne retient pas, écrire « @<nom du classeur> » dans une réponse puis générer : le
classeur fait partie de la connaissance transmise, avant les documents trouvés par la recherche,
et la spécification n'en fait plus une hypothèse.

**Acceptance Scenarios**:

1. **Given** un document indexé nommé `Aurora_Matrice_Regles_Gestion_Activites_1.xlsx`, **When**
   une réponse contient « @Aurora_Matrice_Regles_Gestion_Activites_1 » et l'utilisateur génère la
   spécification, **Then** ce document est transmis à l'IA, avant les documents trouvés par la
   recherche, et marqué comme désigné par l'utilisateur.
2. **Given** la même mention écrite avec l'extension ou dans une autre casse
   (« @aurora_matrice_regles_gestion_activites_1.XLSX »), **When** la spécification est générée,
   **Then** le même document est transmis.
3. **Given** une mention dans le besoin, **When** l'outil prépare les questions, **Then** le
   document mentionné fait partie de la connaissance transmise pour les questions aussi.
4. **Given** une mention dans l'instruction d'une modification, **When** la modification est
   proposée, **Then** le document est transmis pour cette modification.
5. **Given** une mention d'une page Antora, **When** la spécification est générée, **Then** la page
   entière (ses sections dans l'ordre) est transmise ; **Given** une mention d'un ADR, **Then**
   l'ADR suit la voie des ADR cités (004).
6. **Given** des réponses déjà enregistrées avant cette feature, qui contiennent une mention,
   **When** la spécification est régénérée, **Then** la mention est reconnue.

---

### User Story 2 - Choisir le document dans une liste en tapant @ (Priority: P2)

L'utilisateur ne connaît pas le nom exact du fichier. Il tape `@` puis quelques lettres dans le
besoin, une réponse ou l'instruction d'une modification : une liste de suggestions apparaît, tirée
de la connaissance du projet, avec pour chacune sa sorte (document, page de documentation, ADR) et
son chemin. Il en choisit une au clavier ou à la souris ; la mention s'insère dans le texte.

**Why this priority**: elle évite les fautes de frappe et les noms ambigus, mais la mention écrite
à la main (US1) fonctionne déjà sans elle.

**Independent Test**: taper « @matr » dans une réponse : la matrice figure parmi les suggestions ;
la choisir au clavier insère « @Aurora_Matrice_Regles_Gestion_Activites_1 » ; l'écran ne présente
aucune violation d'accessibilité.

**Acceptance Scenarios**:

1. **Given** un projet indexé, **When** l'utilisateur tape « @matr », **Then** 8 suggestions au plus
   s'affichent, celles dont le nom ou le chemin contient ces lettres en premier, chacune avec sa
   sorte et son chemin.
2. **Given** la liste ouverte, **When** l'utilisateur choisit une suggestion au clavier (flèches,
   Entrée) ou la ferme (Échap), **Then** la mention est insérée, ou le texte reste tel quel.
3. **Given** aucune connaissance correspondante, **When** l'utilisateur tape « @zzz », **Then** la
   liste indique qu'aucun document ne correspond, et le texte reste libre.
4. **Given** une adresse électronique tapée dans le texte (« jean@laposte.fr »), **When**
   l'utilisateur tape le « @ », **Then** aucune liste ne s'ouvre.

---

### User Story 3 - Voir ce que deviendront les mentions (Priority: P3)

Avant de générer, l'utilisateur voit sous le champ les documents qu'il a mentionnés et ce qu'il en
adviendra : reconnu et transmis entier, reconnu mais coupé (trop long), ambigu (plusieurs
documents portent ce nom), introuvable. Il peut lever une ambiguïté en choisissant le bon document.

**Why this priority**: sans elle, une mention mal écrite échoue en silence, comme dans le cas réel ;
mais US1 et US2 apportent déjà l'essentiel.

**Independent Test**: écrire une mention exacte, une mention d'un nom porté par deux documents et
une mention d'un nom inconnu : l'écran montre respectivement « transmis », « ambigu, à préciser »
et « introuvable » ; choisir le bon document pour la mention ambiguë la fait passer à « transmis ».

**Acceptance Scenarios**:

1. **Given** une mention d'un document qui tient dans la place disponible, **When** l'utilisateur
   regarde sous le champ, **Then** le document est indiqué « transmis à l'IA ».
2. **Given** une mention d'un document plus long que la place disponible, **Then** il est indiqué
   « transmis en partie », avec la part transmise.
3. **Given** un nom porté par plusieurs documents, **Then** la mention est indiquée « ambiguë » avec
   les documents possibles ; en choisir un fixe la mention.
4. **Given** un nom qu'aucun document ne porte (fichier supprimé, faute de frappe), **Then** la
   mention est indiquée « introuvable » ; la génération reste possible et la mention est ignorée.

---

### Edge Cases

- **Mention dans un texte où « @ » a un autre sens** (adresse électronique, « @ 10 h ») : seule une
  mention qui commence après un espace, une ponctuation ou en début de ligne, et qui correspond à
  un document, est retenue ; le reste du texte n'est pas modifié.
- **Nom avec espaces** (« Schéma directeur Outils Commerciaux_CoStrat du 26.09_V1.pdf ») : la liste
  insère une mention qui garde le nom entier ; une mention écrite à la main avec des espaces est
  reconnue si elle correspond au nom entier d'un document.
- **Document mentionné puis supprimé du dossier, ou source retirée** : la mention devient
  introuvable et est ignorée à la génération suivante, sans erreur.
- **Document mentionné illisible au moment de la génération** (hors ligne, fichier à la demande) :
  ses passages indexés sont transmis, comme pour un document trouvé par la recherche (006).
- **Même document mentionné plusieurs fois**, dans le besoin et dans des réponses : transmis une
  seule fois.
- **Trop de documents mentionnés** pour la place disponible : les premiers mentionnés passent, les
  suivants sont indiqués non transmis (US3), et la génération n'est pas bloquée.
- **Mention d'un ADR remplacé ou obsolète** : la règle des ADR s'applique (son successeur, ou rien),
  et l'écran le dit.
- **Document d'un autre projet** : jamais proposé ni reconnu.
- **Mention d'un élément d'architecture ou d'un ticket Jira** : non proposé ; une clé Jira écrite
  dans le texte garde son comportement actuel.
- **Contenu d'un document mentionné qui contient des instructions** : il reste une donnée, comme
  toute la connaissance du projet ; être désigné par l'utilisateur ne lui donne aucune autorité.

## Requirements *(mandatory)*

### Functional Requirements

**Reconnaissance des mentions**

- **FR-001**: L'outil MUST reconnaître une mention `@<nom>` dans le besoin, dans les réponses aux
  questions et dans l'instruction d'une modification.
- **FR-002**: Une mention MUST correspondre au nom d'un document du projet sans tenir compte de la
  casse ni de l'extension : nom de fichier d'un dossier de documents, nom de page d'une
  documentation Antora (page entière), titre ou numéro d'un ADR. Les éléments d'architecture et
  les tickets Jira ne sont pas mentionnables.
- **FR-003**: Une mention qui correspond à plusieurs documents MUST être signalée ambiguë ; tant
  qu'elle n'est pas précisée, aucun des documents n'est transmis à son titre.
- **FR-004**: Une mention qui ne correspond à aucun document MUST être signalée introuvable et
  ignorée, sans bloquer la génération.
- **FR-005**: Les mentions déjà présentes dans des réponses ou des besoins enregistrés avant cette
  feature MUST être reconnues à la génération suivante.
- **FR-006**: Le texte écrit par l'utilisateur MUST rester tel qu'il l'a écrit : la mention reste
  lisible et transmise telle quelle à l'IA, en plus du document.

**Transmission**

- **FR-007**: Un document mentionné MUST être transmis à l'IA d'office, avant les documents et les
  ADR trouvés par la recherche, pour les questions, la rédaction et la modification ; la fiche
  générale garde la priorité (FR-017 de la 004).
- **FR-008**: Un document mentionné MUST être transmis comme le serait un document retenu : document
  d'un dossier relu entier (006) ou, à défaut, ses passages indexés ; page Antora recomposée de
  ses sections dans l'ordre ; ADR par la voie des ADR cités (004).
- **FR-009**: Les documents mentionnés MUST tenir dans le plafond de la connaissance transmise :
  4 documents mentionnés au plus, 12 000 caractères au plus pour l'ensemble, chacun coupé à une
  section au-delà de 8 000 caractères ; les documents trouvés par la recherche prennent la place
  qui reste.
- **FR-010**: L'IA MUST savoir qu'un document a été désigné par l'utilisateur, et le traiter comme
  une source à exploiter en priorité ; son contenu reste une donnée, jamais une consigne.
- **FR-011**: Un document mentionné plusieurs fois MUST être transmis une seule fois ; l'ordre de
  priorité est celui de la première mention (besoin, puis réponses dans l'ordre, puis instruction).
- **FR-012**: La spécification produite MUST pouvoir citer un document mentionné comme source, comme
  les autres éléments de la connaissance du projet.

**Autocomplétion**

- **FR-013**: Taper `@` après un espace, une ponctuation ou en début de ligne, puis au moins une
  lettre, MUST proposer 8 documents du projet au plus, correspondant aux lettres tapées dans le nom
  ou le chemin, les correspondances au début du nom en premier.
- **FR-014**: Chaque suggestion MUST montrer sa sorte (document, page de documentation, ADR) et son
  chemin ou sa référence, pour distinguer deux documents de même nom.
- **FR-015**: La liste MUST se piloter entièrement au clavier, être annoncée aux lecteurs d'écran et
  ne présenter aucune violation d'accessibilité ; Échap la ferme sans modifier le texte.
- **FR-016**: Les suggestions MUST venir de l'index local du projet : taper ne déclenche aucun appel
  à Jira, à Copilot ni à SharePoint, et ne lit aucun fichier.

**Retour à l'utilisateur**

- **FR-017**: Sous chaque champ qui contient des mentions, l'écran MUST indiquer pour chacune :
  transmise, transmise en partie, non transmise faute de place, ambiguë (avec les choix possibles)
  ou introuvable.
- **FR-018**: L'utilisateur MUST pouvoir lever une ambiguïté en choisissant le document voulu ; ce
  choix est gardé avec la session ou la modification.

### Key Entities *(include if feature involves data)*

- **Mention** : un nom précédé de `@` dans un texte de l'utilisateur (besoin, réponse, instruction).
  Attributs : texte de la mention, champ où elle se trouve, document reconnu (ou ambiguïté,
  introuvable), choix de l'utilisateur pour une ambiguïté.
- **Document mentionnable** : un document du dossier synchronisé, une page de documentation entière
  ou un ADR, déjà dans la connaissance du projet ; il a un nom, une sorte et un chemin ou une
  référence.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Dans le cas réel de la 006, la matrice mentionnée dans une réponse est transmise à
  l'IA et la spécification régénérée n'a plus d'hypothèse « contenu non disponible » à son sujet.
- **SC-002**: 100 % des mentions exactes (nom de fichier, avec ou sans extension, toute casse) d'un
  document du projet sont reconnues.
- **SC-003**: Les suggestions apparaissent en moins d'une demi-seconde après la frappe, sur un
  projet de 2 000 documents.
- **SC-004**: Aucune mention n'échoue en silence : chaque mention non transmise est signalée à
  l'écran avant la génération.
- **SC-005**: La pertinence des questions et la structure des scénarios de l'évaluation réelle ne
  reculent pas (seuils de `baseline.json`).

## Assumptions

- Un seul rôle dans cette feature : « Référence ». Le rôle « Base de la spec » (classeur traité en
  entier), le budget calculé selon le modèle et « Joindre un fichier » viendront dans les features
  suivantes de la conception d'ensemble.
- Le budget des documents mentionnés (FR-009) est fixe, comme celui de la 006 ; la matrice du cas
  réel (6 feuilles) sera donc transmise en partie, ce qui est signalé (US3). Son traitement complet
  relève du rôle « Base de la spec ».
- Le nom mentionnable d'un document de dossier est son nom de fichier ; celui d'une page Antora est
  son titre et son nom de fichier ; celui d'un ADR, son titre ou « ADR-<n> ».
- L'indication à l'IA qu'un document est désigné modifie les prompts : l'évaluation réelle est
  relancée, avec l'accord du mainteneur (constitution IV).
- Les mentions vivent dans les textes existants (besoin, réponses, instruction) ; aucune nouvelle
  saisie n'est demandée à l'utilisateur hors des choix d'ambiguïté.
