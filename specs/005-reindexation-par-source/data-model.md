# Data model : réindexation par source

Aucune table ni migration.

## IndexationScope (application/knowledge, nouvelle valeur)

| Champ | Type | Règle |
|---|---|---|
| `jira` | boolean | Jira à réindexer |
| `docSources` | Set<UUID> | sources de documentation à réindexer ; toutes si `ALL` |
| `architecture` | boolean | modèle d'architecture à réindexer |

- `ALL` : toutes les sources (« Resynchroniser », « Tout réindexer », déclenchements automatiques).
- `of(List<String>, List<DocSource>)` : `"JIRA"`, `"ARCHITECTURE"`, identifiants de sources de
  documentation du projet ; liste vide ou valeur inconnue → `VALIDATION_FAILED`.
- `all()` vrai pour `ALL` : la documentation peut alors retirer ce qui n'a pas été lu.

## IndexationError (existant)

Nouvelle raison `UNEXPECTED` (erreur inattendue d'une étape), avec les types existants :
`JIRA`, `SOURCE` (emplacement de la source), `ARCHITECTURE`, `MODEL`.

## Indexations.Snapshot (existant)

Nouveau champ `sources` : `List<String>` demandée, `null` pour toutes.

## KnowledgeQueries.Status (existant)

Nouveau champ `sources` : `List<IndexableSource>`.

- `IndexableSource(String id, String kind, String label)` : `id` = `"JIRA"`, `"ARCHITECTURE"` ou
  l'identifiant de la source de documentation ; `kind` = `JIRA`, `DOCUMENTATION`,
  `ARCHITECTURE` ; `label` = « Jira CLE », l'emplacement, ou le workspace.

## KnowledgeSource (existant)

Nouvelle méthode `withJiraMark(Instant)` : avance le repère Jira sans changer `syncedAt`.
