# Data model : 017

## Migration V28 `V28__source_sync.sql`

### Table `source_sync`

| Colonne | Type | Contrainte | Sens |
|---|---|---|---|
| `project_id` | TEXT | NOT NULL, FK `project(id)` ON DELETE CASCADE | projet |
| `source_key` | TEXT | NOT NULL | `jira`, `architecture` ou identifiant de source de documentation |
| `interval_minutes` | INTEGER | NULL, ∈ {15, 30, 60, 240, 1440} | délai ; NULL = désactivé |
| `last_run_at` | TEXT (ISO-8601) | NULL | fin de la dernière tentative |
| `last_result` | TEXT | NULL, ∈ {CHANGED, UNCHANGED, ERROR} | résultat de la dernière tentative |
| `added` | INTEGER | NOT NULL DEFAULT 0 | éléments nouveaux (dernière tentative) |
| `modified` | INTEGER | NOT NULL DEFAULT 0 | éléments modifiés |
| `removed` | INTEGER | NOT NULL DEFAULT 0 | éléments retirés |
| `error_code` | TEXT | NULL | code d'erreur (ex. `JIRA_UNAUTHORIZED`), jamais de message brut |
| `last_success_at` | TEXT (ISO-8601) | NULL | fin de la dernière tentative réussie |

Clé primaire `(project_id, source_key)`. Index `(interval_minutes, last_run_at)` pour le calcul des
échéances.

La suppression d'une source de documentation supprime sa ligne dans le même cas d'usage. Aucune
clé étrangère ne le garantit, car `source_key` prend aussi les valeurs `jira` et `architecture`.

### Réglages de l'application

Colonne `notify_source_updates INTEGER NOT NULL DEFAULT 0 CHECK (notify_source_updates IN (0, 1))`
ajoutée à `app_settings`, qui a une colonne par réglage.

### Index de tri des tickets

Index sur `knowledge_item(project_id, kind, source_updated_at)` s'il n'existe pas déjà. Le tri par
statut et par synchro se fait sur au plus 2 000 lignes filtrées et n'a pas besoin d'index propre.

## Domaine

### `SyncInterval` (enum)

`OFF(null)`, `M15(15)`, `M30(30)`, `H1(60)`, `H4(240)`, `D1(1440)`. Méthodes :

- `fromMinutes(Integer)` : rejette toute autre valeur (400 `INVALID_SYNC_INTERVAL`) ;
- `duration(Duration minute)` : nombre de minutes × durée d'une minute. La durée vaut une minute
  réelle, sauf en E2E (`%e2e.specwriter.knowledge.auto-sync.minute=1s`, R8).

### `SyncSchedule`

`(SyncInterval interval, Optional<Instant> lastRunAt)`. Méthodes :

- `dueAt()` : vide si `OFF` ; sinon `lastRunAt + duration`, ou `Instant.MIN` si la source n'a
  jamais été synchronisée ;
- `isDue(now)`.

### `SourceChange`

`(int added, int modified, int removed)`, valeurs ≥ 0. Méthodes :

- `changed()` : vrai si l'une des valeurs est non nulle ;
- `NONE`.

### `SourceSyncResult`

Union de deux cas :

- `Success(Instant at, SourceChange change)` : donne `CHANGED` ou `UNCHANGED` ;
- `Failure(Instant at, String errorCode)`.

### `SourceSyncState`

Ce que voit l'utilisateur pour une source : `sourceKey`, `kind`, `label`, `SyncInterval`, dernier
`SourceSyncResult` (optionnel), `lastSuccessAt` (optionnel), `nextRunAt` (optionnel).

### `SourceUpdated` (événement)

Champs : `id` (UUID), `projectId`, `projectName`, `sourceKey`, `sourceKind`
(`JIRA | DOCUMENTATION | ARCHITECTURE`, avec la sous-sorte de documentation pour le libellé),
`sourceLabel`, `SourceChange`, `at`. Il ne contient aucun contenu d'élément.

### `ListedIssue`

Champs :

- `key`, `title`, `status`, `statusCategory`, `updatedAt` ;
- `syncState` : `UNLINKED | IN_SYNC | LOCAL_AHEAD | CONFLICT` ;
- `sessionId` (optionnel).

### `IssueSort`

`(Field {UPDATED, STATUS, SYNC}, Direction {ASC, DESC})`. Défaut : `UPDATED DESC`.

Rangs :
- statut : `new` 0, `indeterminate` 1, `done` 2, autre 3 ;
- synchro : `CONFLICT` 0, `LOCAL_AHEAD` 1, `IN_SYNC` 2, `UNLINKED` 3.

`DESC` inverse le rang principal ; les critères secondaires (nom du statut, clé) restent
croissants pour une lecture stable.

## Ports

| Port | Rôle | Adaptateur |
|---|---|---|
| `SourceSyncRepository` | `schedules(projectId)`, `due(now)`, `setInterval(projectId, key, interval)`, `record(projectId, key, result)`, `forget(projectId, key)` | SQLite (JPA ou JDBC) |
| `ListedIssues` | `page(projectId, jiraKey, IssueSort, offset, limit)`, `count(...)` | requête native SQLite |
| `AppEvents` | `publish(SourceUpdated)` | `CdiAppEvents` (événement CDI), diffusé par `EventsResource` |
| `SettingsRepository` | `+ notificationsEnabled()`, `+ setNotificationsEnabled(boolean)` | existant |

## Transitions

```text
source sans ligne ──réglage d'un délai──▶ échue (jamais synchronisée)
échue ──AutoSync (projet libre)──▶ indexation en cours ──fin──▶ bilan CHANGED / UNCHANGED / ERROR
bilan ──(dueAt = last_run_at + délai)──▶ en attente ──heure passée──▶ échue
toute indexation manuelle relisant la source ──fin──▶ bilan (repart le délai, FR-013)
délai OFF ──▶ jamais échue (le dernier bilan reste affiché)
```
