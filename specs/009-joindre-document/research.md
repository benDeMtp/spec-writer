# Recherche : joindre un document à une spécification

État des lieux (code de `main`, 2026-11-09) :

- Lecture des formats (006) : `LocalDocumentFolder.extract(Path, format)` appelle `OoxmlText.docx`,
  `OoxmlText.xlsx`, `PdfText.read`, `HtmlText.read` ; bornes `DocumentFolder.Limits.DEFAULT`
  (20 Mo par fichier, 200 000 caractères extraits, 60 s par fichier ; 50 Mo décompressés et 50 000
  cellules dans `OoxmlText`) ; raisons de refus `UnreadReason` (TOO_LARGE, PROTECTED, NO_TEXT,
  UNREADABLE…). Les lecteurs prennent un `Path`.
- Documents désignés (007) : `SelectKnowledge.mentioned` transmet les documents mentionnés après la
  fiche générale, 4 au plus, dans la part `mentionedText`/`mentionedPart` du budget (008), coupés à
  une section ; `MentionQueries.resolve` calcule leur état avec les mêmes règles ; le front
  `sw-mention-status` l'affiche.
- `UserMessages.isDocument` rend en `<document ref titre designe="oui">` toute entrée `DOC_SECTION`
  sans rôle et désignée : un document joint passe par là **sans toucher `infrastructure/llm`**.
- `quarkus.http.limits.max-body-size=256K` (global) ; aucun envoi de fichier n'existe aujourd'hui ;
  pas d'extension multipart.
- La session est démarrée par `POST /api/sessions` (`CreateRequest(need, name)`), qui calcule le
  premier lot de questions : les documents joints sur l'écran du besoin doivent exister avant.
- Les tables liées à une session sont supprimées en cascade (`ON DELETE CASCADE`, `foreign_keys=true`).

## R1 — Envoi du fichier au backend local

**Decision** : le navigateur envoie le contenu du fichier choisi (`<input type="file">`) en corps
brut `application/octet-stream`, le nom du fichier en paramètre `name` ; aucun chemin n'est connu ni
transmis (le navigateur ne le donne pas). Limite de corps : `quarkus.http.limits.max-body-size` porté
à 21 Mo, et un filtre de route (`BodySizeFilter`, Vert.x) refuse au-delà de 256 Ko toute requête
qui n'est pas un envoi de document joint (en-tête `Content-Length`, corps sans longueur refusés hors
de ces routes) : la protection d'aujourd'hui reste entière pour le reste de l'API. `LocalSecurityFilter`
n'accepte que JSON pour les requêtes qui modifient (protection contre les requêtes d'un autre site) ;
`application/octet-stream` est accepté seulement sur ces deux routes : ce type n'est pas « simple »,
il impose donc la même vérification préalable du navigateur, et l'origine reste contrôlée.

**Rationale** : un seul fichier par requête, sans dépendance ; le filtre garde la borne de 256 Ko
partout ailleurs.

**Alternatives** : extension multipart (dépendance de plus, inutile pour un fichier) ; lire un chemin
saisi par l'utilisateur (le navigateur ne le fournit pas ; ouvrirait la lecture de tout le disque par
l'API) ; File System Access API (non portable).

## R2 — Lecture sans copie durable

**Decision** : port `domain/port/AttachmentReader` : `Reading read(String fileName, byte[] content)` ;
adaptateur `infrastructure/docs/TempFileAttachmentReader` : format d'après l'extension (`.docx`,
`.xlsx`, `.pdf`, `.html`, `.htm`, sinon refus `UNSUPPORTED_FORMAT`), écriture dans un fichier
temporaire du dossier de données (permissions du propriétaire seulement), extraction par les
lecteurs de la 006 avec les mêmes bornes et le même délai (fil virtuel), suppression du fichier
temporaire dans `finally`. L'extraction est déplacée dans une classe partagée `DocumentExtraction`
(utilisée aussi par `LocalDocumentFolder`, sans changement de comportement).

**Rationale** : les lecteurs prennent un `Path` (zip, PDFBox) ; le fichier temporaire ne vit que le
temps de la lecture ; seul le texte extrait est gardé (clarification 1).

## R3 — Documents joints avant le démarrage (brouillons)

**Decision** : l'écran du besoin envoie chaque fichier à `POST /api/attachment-drafts` ; le texte
extrait est gardé **en mémoire** (`application/attachment/AttachmentDrafts`, durée de vie 60 min,
200 brouillons au plus, purgés à la lecture), et l'écran garde les identifiants. `POST /api/sessions`
reçoit `attachmentIds` : `StartSession` les rattache à la session (enregistrés en base) avant le
premier lot de questions, puis les retire des brouillons. Un brouillon retiré ou expiré n'est jamais
enregistré.

**Alternatives** : créer la session avant d'envoyer les fichiers (le premier lot serait calculé sans
eux) ; enregistrer les brouillons en base (traces à purger si l'utilisateur abandonne l'écran).

## R4 — Stockage

**Decision** : migration `V17__session_attachment.sql`, table `session_attachment (id PK, session_id →
session ON DELETE CASCADE, ordinal, title, file_name, format, chars, content_hash, text, added_at)`,
unicité `(session_id, content_hash)` et `(session_id, file_name)`. Port `AttachmentRepository`.
Plafond de 5 par session (clarification 5), contrôlé par le domaine. Le texte n'est lu qu'à la
sélection de la connaissance ; les vues ne renvoient jamais le texte.

## R5 — Transmission : un document désigné de plus

**Decision** :
- Référence d'un document joint : `joint:<nom du fichier>` (unique dans la session, R4).
- `SelectKnowledge.mentioned` reçoit les documents joints de la session et les insère dans l'ordre
  de la clarification 4 : mentions du besoin, documents joints (ordre d'ajout), mentions des
  réponses, de l'instruction ; même compte (4), même part du budget, même coupe à une section ;
  `Entry(DOC_SECTION, role null, ref joint:…, titre, texte coupé, empreinte du texte transmis)
  .withDesignated()` : rendu en `<document … designe="oui">` par `UserMessages` existant ; prompts
  et `infrastructure/llm` inchangés, empreinte de l'évaluation inchangée.
- `SessionKnowledge` lit les documents joints par `AttachmentRepository` (questions, spécification,
  modification) ; l'empreinte d'entrée suit l'empreinte de chaque entrée (régénération réutilisée à
  documents identiques, FR-009).
- `MentionQueries.resolve` reçoit les brouillons (`attachmentIds`) ou ceux de la session, et rend
  leur état avec `field = ATTACHMENT` (nouvelle valeur de `MentionField`), `document.kind =
  ATTACHMENT` (nouvelle valeur de `MentionKind`), `transmittedShare` et `betterModel` (008).

## R6 — Ce qui sort de l'outil (clarification 3)

**Decision** : une source `DOC_PAGE` de référence `joint:…` est rendue « Document joint <nom> » par
`MarkdownRenderer`, **sans extrait** (l'extrait cité par l'IA est retiré à la réparation,
`DraftValidation.repaired`, pour ces références) ; `SourceLinks` ne donne aucun lien ; l'export, Jira
et la passerelle des agents reçoivent le Markdown rendu, donc le titre seul. Le texte n'apparaît dans
aucun journal ni message d'erreur (les refus ne citent que le nom du fichier et la raison).

## R7 — Écrans

**Decision** : composant partagé `sw-attachments` (bouton « Joindre un document » ouvrant le
sélecteur de fichiers, liste titre, nom, taille, état, « Retirer », annonce des refus et des états
en `aria-live`) sur l'écran du besoin (brouillons), le questionnaire et l'écran de la spécification
(documents de la session, désactivé sur une feature implémentée, verrou existant).

## R8 — Évaluation

Les prompts et `infrastructure/llm` ne changent pas : `ci/check-eval.sh` doit rester vert sans
évaluation réelle. Golden set inchangé (sessions sans document joint).
