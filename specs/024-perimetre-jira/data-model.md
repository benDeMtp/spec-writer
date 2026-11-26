# Data Model: Périmètre Jira de spec-writer (024)

## Base (V36)

### `project_jira_label`

| Colonne | Type | Règle |
|---|---|---|
| `project_id` | `text`, clé | `references project (id) on delete cascade` |
| `label` | `text`, non nul | 1 à 255 caractères, sans espace |
| `updated_at` | `text`, non nul | instant ISO |

Pas de ligne : pas de label.

### `ticket_label`

| Colonne | Type | Règle |
|---|---|---|
| `project_id` | `text` | `references project (id) on delete cascade` |
| `ticket_key` | `text` | clé Jira |
| `label` | `text` | 1 à 255 caractères |

Clé `(project_id, ticket_key, label)` ; index `(project_id, label)`.

### `lost_jira_link`

| Colonne | Type | Règle |
|---|---|---|
| `session_id` | `text`, clé | `references session (id) on delete cascade` |
| `issue_key` | `text`, non nul | clé du ticket supprimé |
| `lost_at` | `text`, non nul | instant ISO |

Effacée quand la spécification est de nouveau liée.

## Domaine

### `JiraLabel` (`domain/jira`)

- `JiraLabel.of(String)` : `strip`, 1 à 255 caractères, aucun blanc ; sinon `VALIDATION_FAILED`
  (« Un label Jira ne contient pas d'espace et compte au plus 255 caractères. »).
- `value()` ; égalité exacte.

### `TrackedIssue` (`domain/jira`)

| Champ ajouté | Type | Règle |
|---|---|---|
| `labels` | `List<String>` | labels du ticket, vide si aucun (champ `labels` de Jira) |

### Ports

- `JiraLabelRepository` : `find(projectId)`, `save(projectId, JiraLabel, Instant)`, `delete`.
- `TicketLabels` : `replace(projectId, key, labels)`, `add(projectId, key, label)`,
  `has(projectId, key, label)`, `retain(projectId, keys)`, `remove(projectId, keys)`.
- `LostJiraLinks` : `record(sessionId, key, Instant)`, `find(sessionId)`, `clear(sessionId)`.
- `IssueTracker.addLabel(endpoint, key, label)` ; `createIssue` et `createSubtask` reçoivent les
  labels à poser.

## Application

- `JiraScope` : `feature(Session)`, `task(UUID projectId, SpecTask)`, `label(UUID projectId)`.
- `ForgetDeletedTickets` : `run(UUID projectId, Set<String> removedKeys)` → nombre de tâches et de
  liens retirés.
- `ListProjectIssues.IssueFilters(boolean labelled, boolean done)` ; `IssuePage` gagne `label`
  (nul sans réglage), `labelled` et `done` appliqués.

## Règles d'état

```text
Label (projet réglé) :
  création d'une story ou d'une sous-tâche ─► label dans le corps ; refus ─► recréation sans label
    puis ajout ; refus de l'ajout ─► message, le ticket existe
  reprise (story liée, sous-tâche reprise) sans le label ─► ajout ; déjà là ─► rien

Périmètre du MCP (projet réglé) :
  feature sans ticket lié ─► servie
  story avec le label ─► servie ; sans ─► NOT_IN_JIRA_SCOPE
  tâche dont la sous-tâche a le label ─► proposée, prise, terminée ; sans ─► inScope false, refusée

Synchronisation de Jira :
  listKeys ─► clés disparues retirées de l'index, des liens et des labels
    ─► tâches de ces sous-tâches supprimées, avancement recalculé
    ─► liens de spécification supprimés, lost_jira_link écrit
  listKeys en échec, projet invisible, liste vide et index non vide ─► rien retiré, erreur JIRA
```

### `AgentTasks.TaskInfo` (MCP)

| Champ ajouté | Type | Règle |
|---|---|---|
| `inScope` | `boolean` | faux si le projet a un label et que la sous-tâche ne le porte pas |
