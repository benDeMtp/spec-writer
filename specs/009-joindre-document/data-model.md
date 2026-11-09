# Modèle de données : joindre un document

## Domaine (`domain/attachment`, nouveau)

| Type | Champs | Règles |
|---|---|---|
| `Attachment` | `id` (UUID), `sessionId`, `ordinal` (1…5), `title`, `fileName` (sans chemin, ≤ 255), `format` (`DOCX`, `XLSX`, `PDF`, `HTML`), `chars`, `contentHash` (SHA-256 du texte), `text` (≤ 200 000), `addedAt` | Titre : titre du document, sinon nom du fichier sans extension |
| `AttachmentDraft` | `id`, `title`, `fileName`, `format`, `chars`, `contentHash`, `text`, `createdAt` | En mémoire seulement, 60 min |
| `Attachments` *(règles)* | `MAX_PER_SESSION = 5` | Refus : plafond (`ATTACHMENT_LIMIT`), même contenu ou même nom (`ATTACHMENT_DUPLICATE`), format (`UNSUPPORTED_FORMAT`), raisons de lecture de la 006 (`TOO_LARGE`, `PROTECTED`, `NO_TEXT`, `UNREADABLE`) |

Vue (jamais le texte) : `AttachmentView(id, title, fileName, format, chars, addedAt)`.

## Connaissance (étendu)

| Type | Changement |
|---|---|
| `ProjectKnowledge.Entry` | document joint : `kind DOC_SECTION`, `role null`, `ref "joint:<fileName>"`, `designated true` |
| `MentionField` | + `ATTACHMENT` |
| `MentionKind` | + `ATTACHMENT` |
| `MentionQueries.MentionState` | inchangé ; un document joint y figure avec `field ATTACHMENT` |

## Persistance

`V17__session_attachment.sql` :

```text
session_attachment
  id           TEXT PRIMARY KEY
  session_id   TEXT NOT NULL REFERENCES session(id) ON DELETE CASCADE
  ordinal      INTEGER NOT NULL
  title        TEXT NOT NULL
  file_name    TEXT NOT NULL CHECK (length(file_name) BETWEEN 1 AND 255)
  format       TEXT NOT NULL CHECK (format IN ('DOCX','XLSX','PDF','HTML'))
  chars        INTEGER NOT NULL
  content_hash TEXT NOT NULL
  text         TEXT NOT NULL CHECK (length(text) <= 200000)
  added_at     TEXT NOT NULL
  UNIQUE (session_id, content_hash), UNIQUE (session_id, file_name)
```

Port `AttachmentRepository` : `list(sessionId)` (ordre d'ajout, avec texte, pour la connaissance),
`views(sessionId)` (sans texte), `add(Attachment)`, `remove(sessionId, id)`, `count(sessionId)`.

## Cycle de vie

```text
Écran du besoin : fichier ──lecture──▶ brouillon (mémoire, 60 min) ──démarrage──▶ document de la session
                                       └─ retiré ou expiré : oublié
Session : joindre ──lecture──▶ document de la session ──retirer──▶ supprimé (versions inchangées)
Suppression de la session ──▶ documents supprimés en cascade
```
