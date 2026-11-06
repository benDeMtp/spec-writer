# Contrat : documents SharePoint par dossier synchronisé

Complète `specs/002-projets-contexte-mcp/contracts/openapi.yaml`.

## POST /api/projects/{id}/doc-sources

```json
{ "kind": "DOCUMENTS", "location": "/Users/…/OneDrive-LaPoste/Aurora", "exclusions": ["Archives/**", "*.xlsx"] }
```

- `kind` : `URL`, `FOLDER` ou `DOCUMENTS` ; `exclusions` accepté pour `DOCUMENTS` seulement.
- 201 : la source, avec `exclusions` ; l'indexation de la source est lancée.
- 422 `VALIDATION_FAILED` : dossier invalide, motif invalide (le message cite le motif et la
  raison), plus de 50 motifs.

## PUT /api/projects/{id}/doc-sources/{sourceId}/exclusions

```json
{ "exclusions": ["Archives/**"] }
```

- 200 : la source ; pris en compte à la prochaine indexation de la source.
- 422 : motif invalide ; 404 : source inconnue ou d'un autre projet.

## POST /api/projects/{id}/doc-sources/preview

```json
{ "location": "/Users/…/Aurora", "exclusions": ["Archives/**"] }
```

200 :

```json
{ "retained": 42, "excluded": 7, "ignored": 3,
  "retainedExamples": ["Specs/Aurora.docx"], "excludedExamples": ["Archives/2023.pdf"],
  "ignoredExamples": ["Specs/vieux.doc"], "limitReached": false }
```

Aucun fichier n'est ouvert : noms et attributs seulement.

## Vues existantes

- `DocSourceView` gagne `exclusions` (`string[]`).
- `IndexationView.errors[]` : nouvelles raisons `PROTECTED`, `NO_TEXT`, `UNAVAILABLE` (type
  `PAGE`, emplacement = chemin relatif) et `IGNORED_FORMAT` (type `WARNING`).
- `KnowledgeQueries.Hit.url` : nul pour une référence `docs:` (fichiers jamais servis).

## Front

- `projects.model.ts` : `DocSourceKind = 'URL' | 'FOLDER' | 'DOCUMENTS'`, `DocSource.exclusions`.
- `context.api.ts` : `addSource(kind, location, exclusions?)`, `setExclusions`, `preview`.
- Page « Contexte du projet » : type « Dossier de documents (SharePoint synchronisé) », champ
  « Motifs d'exclusion » (zone de texte, un motif par ligne), bouton « Aperçu du dossier » (résultat annoncé
  par `role="status"`), aide « Synchroniser un dossier SharePoint » (étapes OneDrive).
