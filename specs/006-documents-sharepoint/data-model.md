# Data model : documents SharePoint par dossier synchronisé

## Migration `V15__documents_folder.sql`

- `project_doc_source` reconstruite (SQLite ne modifie pas un CHECK) :
  - `kind CHECK (kind IN ('URL', 'FOLDER', 'DOCUMENTS'))` ;
  - nouvelle colonne `exclusions TEXT` : tableau JSON de motifs, `NULL` hors `DOCUMENTS`.
- Nouvelle table `document_file` :

| Colonne | Type | Règle |
|---|---|---|
| `source_id` | TEXT | `REFERENCES project_doc_source (id) ON DELETE CASCADE` |
| `path` | TEXT | chemin relatif, `/`, 1–2 000 caractères |
| `size` | INTEGER | octets |
| `modified_at` | TEXT | instant ISO |
| | | `PRIMARY KEY (source_id, path)` |

Aucun contenu de fichier n'est stocké hors de `knowledge_item` (passages découpés).

## DocSource (domaine, existant)

- `Kind.DOCUMENTS` ajouté ; validation de l'emplacement identique à `FOLDER`.
- Nouveau champ `ExclusionPatterns exclusions` (vide hors `DOCUMENTS`).

## ExclusionPatterns (domaine, nouvelle valeur)

| Règle | Valeur |
|---|---|
| Nombre de motifs | 50 au plus |
| Longueur d'un motif | 1 à 200 caractères |
| Refusés | chemin absolu (`/…`, `C:…`), `..`, crochet non fermé |
| `excludes(String relativePath)` | vrai si un motif correspond (sans casse) |
| Toujours ignorés | `~$*`, `.~lock*`, noms commençant par `.` |

## DocumentFolder (port, nouveau)

- `Scan scan(DocSource source, Limits limits)` : documents retenus (chemin, taille, date),
  exclus, ignorés pour leur format ; sans ouvrir de fichier.
- `Optional<ExtractedDocument> read(DocSource source, String path)` : titre, format, sections
  (titre, texte) ; motif d'échec : `PROTECTED`, `NO_TEXT`, `TOO_LARGE`, `UNAVAILABLE`,
  `UNREADABLE`.

## DocumentationReader.UnreadReason (existant)

Ajouts : `PROTECTED`, `NO_TEXT`, `UNAVAILABLE`. Les formats ignorés donnent un avertissement
d'indexation (`WARNING`, raison `IGNORED_FORMAT`, nombre de fichiers dans le message).

## Références des éléments

- Section d'un document : `docs:<sourceId>/<chemin>#<n>` ; document entier : `docs:<sourceId>/<chemin>`.
- Un ADR d'un dossier de documents : `KnowledgeKind.ADR`, même préfixe.

## KnowledgeIndex (port, existant)

`int retainUnder(UUID projectId, String refPrefix, Set<Key> keep)` : retire les éléments dont la
référence commence par le préfixe et qui ne sont pas dans `keep`.

## SelectKnowledge (existant)

`MAX_DOCUMENTS = 2`, `MAX_DOCUMENT_TEXT = 8_000`, `MAX_DOCUMENT_PART = 6_000` ; entrée
`ProjectKnowledge.Entry` de type `DOC_SECTION` avec référence `docs:` → balise `<document>`.
