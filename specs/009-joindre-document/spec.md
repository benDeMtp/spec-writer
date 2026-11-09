# Feature Specification: Joindre un document à une spécification

**Feature Branch**: `feature/013-joindre-document`

**Created**: 2026-11-09

**Status**: Draft

**Input**: User description: "Joindre un document à une spécification : morceau 4 de la conception docs/superpowers/specs/2026-11-06-documents-designes-design.md (section « Morceau 4 — Joindre un fichier »). Un bouton à côté du champ du besoin permet de joindre un fichier pris n'importe où sur le poste, hors du dossier synchronisé (.docx, .xlsx, .pdf, .html, mêmes lecteurs et mêmes bornes que la 006) ; il est joint à cette spécification seulement et n'entre pas dans l'index du projet. Il est transmis à l'IA comme un document désigné avec @ (007), dans le budget du modèle choisi (008), et son état (transmis, en partie, plus de place) est affiché comme celui d'une mention. L'outil garde le texte extrait dans la session, pas le fichier ni son chemin, pour que les régénérations restent possibles si le fichier est déplacé. Cas réel : des documents externes au référentiel scanné (reçus par mail, téléchargés) portent des informations utiles. Le morceau 3 (classeur en base de la spec) est abandonné : il spécialisait trop ; il n'y a donc qu'un rôle, « Référence ». Hors périmètre : OCR des documents scannés, rôle « Base de la spec »."

Prolonge `specs/008-budget-selon-modele/` (budget selon le modèle), `specs/007-mention-documents/`
(documents désignés, état des mentions) et `specs/006-documents-sharepoint/` (lecture des formats,
bornes), dont les décisions restent valables. Le morceau 3 de la conception (classeur en base de la
spécification) a été abandonné par le mainteneur le 2026-11-09.

## Clarifications

### Session 2026-11-09

- Q: L'outil garde-t-il le texte extrait d'un document joint dans les données de la session ? → A: Oui : le texte extrait est gardé avec la session, sur le poste, et supprimé avec elle ; ni le fichier ni son chemin ne sont gardés.
- Q: Depuis quels écrans peut-on joindre ou retirer un document ? → A: Depuis l'écran du besoin avant le démarrage, puis depuis le questionnaire et l'écran de la spécification pendant toute la vie de la session ; refusé sur une feature implémentée.
- Q: Le texte d'un document joint sort-il de l'outil (export, Jira, passerelle des agents) ? → A: Jamais : seul le titre du document, cité comme source, apparaît dans la spécification, l'export, Jira et la passerelle des agents.
- Q: Dans quel ordre passent documents joints et documents mentionnés quand la place manque ? → A: Mentions du besoin, puis documents joints dans l'ordre d'ajout, puis mentions des réponses, puis de l'instruction ; les 4 premiers documents désignés sont transmis, comptés ensemble.
- Q: Combien de documents peut-on joindre au plus à une session ? → A: 5.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Joindre un document au besoin (Priority: P1) 🎯 MVP

L'utilisateur a reçu par mail un compte rendu de réunion en PDF, rangé dans ses téléchargements, hors
du dossier synchronisé du projet. En écrivant son besoin, il clique sur « Joindre un document »,
choisit le fichier, et le voit apparaître sous le champ avec son état (« transmis à l'IA », « transmis
en partie », « non transmis : plus de place »). Les questions puis la spécification tiennent compte de
son contenu, comme pour un document mentionné avec @.

**Why this priority**: c'est le besoin exprimé : une information utile vit dans un document que la
connaissance du projet ne voit pas.

**Independent Test**: joindre un PDF dont le contenu fixe une règle absente du besoin (« le délai de
rétractation est de 14 jours ») : la règle apparaît dans la spécification, et le document figure dans
la connaissance transmise de la version.

**Acceptance Scenarios**:

1. **Given** le champ du besoin, **When** l'utilisateur joint un fichier Word, Excel, PDF ou HTML
   pris n'importe où sur le poste, **Then** le document apparaît sous le champ avec son titre, sa
   taille en caractères et son état de transmission.
2. **Given** un document joint, **When** l'utilisateur démarre le questionnaire puis génère la
   spécification, **Then** le contenu du document est transmis à l'IA à chaque opération (questions,
   spécification, modification), dans le budget du modèle choisi, comme un document désigné avec @.
3. **Given** un document joint trop long pour la place restante, **When** l'utilisateur le joint,
   **Then** l'état indique « transmis en partie (environ N %) » ou « non transmis : plus de place »
   avant toute génération, et, comme pour une mention, le modèle qui le transmettrait en entier s'il
   y en a un.
4. **Given** un document joint par erreur, **When** l'utilisateur le retire avant de démarrer, **Then**
   il n'est ni gardé ni transmis.
5. **Given** un fichier joint, **When** l'outil l'a lu, **Then** le dossier du projet, la connaissance
   indexée et les autres spécifications restent inchangés : le document n'appartient qu'à cette
   spécification.

---

### User Story 2 - Régénérer sans le fichier d'origine (Priority: P2)

Une semaine plus tard, le compte rendu a été supprimé des téléchargements. L'utilisateur régénère la
spécification ou demande une modification : le document joint est toujours transmis, parce que
l'outil a gardé son texte, pas le fichier.

**Why this priority**: sans cela, une spécification deviendrait impossible à reproduire dès que le
fichier bouge ; l'US1 seule couvre le premier usage.

**Independent Test**: joindre un document, démarrer, supprimer le fichier du disque, régénérer : le
document est transmis comme la première fois, et deux générations aux entrées identiques réutilisent
la même version.

**Acceptance Scenarios**:

1. **Given** une session avec un document joint, **When** le fichier d'origine est déplacé ou
   supprimé puis l'utilisateur régénère, **Then** le document est transmis à l'identique.
2. **Given** une session avec un document joint, **When** l'utilisateur consulte la session, **Then**
   il voit la liste des documents joints (titre, nom du fichier, date d'ajout, taille), sans chemin.
3. **Given** une session supprimée, **When** la suppression aboutit, **Then** le texte des documents
   joints est supprimé avec elle.

---

### User Story 3 - Gérer les documents joints d'une session en cours (Priority: P3)

Pendant le questionnaire ou après une première version, l'utilisateur reçoit un nouveau document, ou
s'aperçoit qu'un document joint est périmé. Il peut joindre un document à la session en cours ou en
retirer un ; la génération suivante en tient compte.

**Why this priority**: les documents arrivent rarement tous avant de commencer ; mais joindre au
départ suffit au premier usage.

**Independent Test**: sur une session commencée, joindre un second document puis régénérer : il est
transmis ; le retirer puis régénérer : il ne l'est plus, et la version précédente reste consultable.

**Acceptance Scenarios**:

1. **Given** une session commencée, **When** l'utilisateur joint un document depuis le questionnaire
   ou la spécification, **Then** les questions suivantes et la prochaine génération le reçoivent.
2. **Given** une session avec un document joint, **When** l'utilisateur le retire, **Then** il n'est
   plus transmis, et les versions déjà produites ne changent pas.
3. **Given** une spécification d'une feature implémentée (verrouillée), **When** l'utilisateur tente
   de joindre ou de retirer un document, **Then** l'outil le refuse comme toute modification.

---

### Edge Cases

- Format non pris en charge (`.doc`, `.xls`, `.pptx`, image…) : refusé avec la liste des formats
  acceptés ; rien n'est gardé.
- Fichier protégé par mot de passe, chiffré ou corrompu : refusé avec la raison de la 006
  (« protégé », « illisible »).
- PDF scanné sans texte : refusé « sans texte » (pas de reconnaissance de caractères).
- Fichier au-delà des bornes de la 006 (taille du fichier, taille décompressée, nombre de cellules,
  texte extrait) : refusé avec la limite dépassée.
- Même fichier joint deux fois (même contenu) : le second est refusé comme doublon.
- Plus de documents joints que le plafond par session : refusé avec le plafond.
- Document joint dont le contenu contient des consignes adressées à l'IA : traité comme une donnée,
  jamais comme une consigne.
- Document joint et document mentionné avec @ en même temps : les deux sont des documents désignés,
  dans le même budget ; l'état de chacun est affiché.
- Projet sans dossier synchronisé : joindre reste possible (le document ne dépend d'aucune source).
- Lecture lente ou bloquée d'un fichier : abandonnée au délai de lecture de la 006, avec un message.

## Requirements *(mandatory)*

### Functional Requirements

**Joindre**

- **FR-001**: L'utilisateur MUST pouvoir joindre un document depuis le champ du besoin, en choisissant
  un fichier n'importe où sur le poste ; formats acceptés : `.docx`, `.xlsx`, `.pdf`, `.html`,
  `.htm`.
- **FR-002**: L'outil MUST lire le document avec les lecteurs et les bornes de la 006 (taille du
  fichier, taille décompressée, nombre de cellules, texte extrait, délai de lecture) ; un refus donne
  sa raison et ne garde rien.
- **FR-003**: Une session MUST accepter au plus 5 documents joints ; un doublon (même contenu) est
  refusé.
- **FR-004**: L'utilisateur MUST pouvoir joindre et retirer un document depuis l'écran du besoin
  avant le démarrage, puis depuis le questionnaire et l'écran de la spécification pendant toute la
  vie de la session (US3) ; joindre ou retirer est refusé sur une spécification verrouillée (feature
  implémentée).

**Garder**

- **FR-005**: L'outil MUST garder, pour chaque document joint, son titre, le nom du fichier (sans
  chemin), la date d'ajout et le texte extrait, attachés à la session ; le fichier lui-même et son
  chemin ne sont jamais gardés.
- **FR-006**: Le texte d'un document joint MUST être supprimé avec la session (suppression locale de
  la 002) et MUST ne jamais entrer dans l'index du projet, la recherche, la passerelle des agents ni
  une autre session.
- **FR-007**: Le texte d'un document joint MUST ne jamais apparaître dans les journaux, les messages
  d'erreur, l'export Markdown, l'envoi vers Jira ni la passerelle des agents ; seul son titre y
  figure, quand la spécification le cite comme source.

**Transmettre**

- **FR-008**: Un document joint MUST être transmis à l'IA comme un document désigné avec @ (007) :
  après la fiche générale, avant les documents trouvés par la recherche, dans la part des documents
  désignés du budget du modèle choisi (008), coupé à une section s'il dépasse sa part ; documents
  désignés comptés ensemble (4 au plus, comme en 007), dans l'ordre : mentions du besoin, documents
  joints (ordre d'ajout), mentions des réponses, puis de l'instruction ; un document au-delà est
  affiché « non transmis : plus de place ».
- **FR-009**: Un document joint MUST être transmis à toutes les opérations qui reçoivent la
  connaissance du projet (questions, spécification, modification), et entrer dans l'empreinte de la
  génération : à documents joints identiques, une régénération réutilise la version existante.
- **FR-010**: L'état de transmission de chaque document joint MUST être affiché sous le champ, comme
  celui d'une mention (transmis, en partie avec le pourcentage, plus de place), avec la suggestion
  d'un modèle qui le transmettrait en entier (008).
- **FR-011**: Le contenu d'un document joint MUST être traité comme une donnée : consignes des prompts
  et séparation des instructions inchangées (constitution V).

**Accessibilité**

- **FR-012**: Le bouton, la liste des documents joints, leur retrait et leurs états MUST être
  utilisables au clavier et annoncés au lecteur d'écran (WCAG 2.1 AA, axe sans violation).

### Key Entities *(include if feature involves data)*

- **Document joint** : appartient à une session ; titre, nom du fichier (sans chemin), format, date
  d'ajout, taille en caractères, empreinte du contenu (doublons, empreinte de génération), texte
  extrait ; ordre d'ajout ; supprimé avec la session.
- **Document désigné** (007, étendu) : un document mentionné avec @ ou un document joint ; transmis
  d'office dans la même part du budget.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Joindre un document de 50 pages et voir son état prend moins de 10 secondes.
- **SC-002**: 100 % des régénérations d'une session aux documents joints inchangés transmettent le
  même texte, que le fichier d'origine existe encore ou non.
- **SC-003**: Après la suppression d'une session, aucune trace du texte de ses documents joints ne
  reste dans les données de l'outil (vérifié de bout en bout).
- **SC-004**: Une session sans document joint est traitée exactement comme avant (golden set et
  évaluation inchangés ou supérieurs à `baseline.json`).
- **SC-005**: Aucun document joint n'apparaît dans la recherche d'essai, l'index du projet ni la
  passerelle des agents.

## Assumptions

- Un seul rôle : « Référence ». Le rôle « Base de la spec » et le morceau 3 de la conception sont
  abandonnés.
- Le plafond de 5 documents joints par session (confirmé, Clarifications) laisse un document de
  réserve au-delà des 4 documents désignés transmis au plus (007).
- Le texte d'un document joint est gardé comme le besoin et les réponses : localement, dans les
  données de l'outil, pour la vie de la session ; ce n'est pas une copie du fichier (confirmé par le
  mainteneur, Clarifications).
- Le consentement à l'envoi vers GitHub Copilot (001) couvre les documents joints, comme le besoin.
- Hors périmètre : reconnaissance de caractères (OCR), formats anciens (`.doc`, `.xls`), PowerPoint,
  joindre un document à une seule réponse ou à une seule instruction (le document appartient à la
  session), rôle « Base de la spec ».
