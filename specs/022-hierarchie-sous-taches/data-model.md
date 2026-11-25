# Data Model: Hiérarchie des tâches et sous-tâches Jira (022)

Aucune nouvelle table. Une colonne ajoutée (V34), et des champs ajoutés aux réponses existantes.

## Base

### `spec_task` (V34)

| Colonne | Type | Règle |
|---|---|---|
| `jira_issue_type` | `text`, nul | Libellé du type de la sous-tâche dans Jira (« Sous-Tâche (AGILE) »), relu avec le statut et la description (V33). 100 caractères au plus. Nul tant que la tâche n'a pas été relue. |

Écrit par `TaskSync.reread` quand il change. Jira injoignable : inchangé. Comme
`jira_description`, la colonne est hors du record `SpecTask` (méthodes dédiées du dépôt).

### Lectures sans changement de schéma

- `ticket_link` (kind `PARENT`) : `source_key` = sous-tâche, `target_key` = parent (R1).
- `knowledge_item` (kind `TICKET`, chunk 0) : `ref`, `title`, `issue_type`, statut, date.
- `linked_jira_ticket.issue_key` : ticket lié à une feature, parent de ses tâches (R4).

## Domaine

### `ListedIssue` (vue Jira, 017)

Champs ajoutés :

| Champ | Type | Règle |
|---|---|---|
| `parentKey` | `String`, nul | Clé du parent d'une sous-tâche du premier niveau dont le parent n'est **pas** indexé (FR-005). Nul sinon. |
| `subtasks` | `List<ListedIssue>` | Sous-tâches indexées d'un ticket du premier niveau, classées par clé dans l'ordre numérique. Vide pour une sous-tâche et pour un ticket sans sous-tâche. Une sous-tâche n'a jamais elle-même de `subtasks` (un seul niveau). |

`subtaskCount` se déduit de `subtasks.size()` côté REST.

Invariants :
- Un ticket du premier niveau n'a pas de parent indexé.
- Une sous-tâche listée dans `subtasks` a pour parent la ligne qui la contient, et n'apparaît
  nulle part ailleurs.
- `total` compte les seules lignes du premier niveau.

### `AgentTasks.TaskInfo` (MCP, 015)

Champs ajoutés :

| Champ | Type | Règle |
|---|---|---|
| `issueType` | `String`, nul | Libellé Jira de la sous-tâche (`jira_issue_type`), nul si pas encore relue. |
| `parentKey` | `String` | Clé du ticket lié à la feature (R4). Toujours présente pour une tâche créée. |

### `AgentTasks.NextAnswer` et `TaskAnswer`

| Champ | Type | Règle |
|---|---|---|
| `branch` | `String`, nul | Consigne de branche avec la clé du parent (R6), pour une tâche qui a un `parentKey`. Présent dans `next_task` (outcome TASK) et `start_task`. Nul dans `complete_task` et quand il n'y a pas de tâche. |

### `GetFeature.FeatureContent`

| Champ | Type | Règle |
|---|---|---|
| `jiraTicket` | objet, nul | `{key, issueType, parentKey}` du ticket lié à la feature, lus dans l'index (R5). Nul sans ticket lié. `issueType` et `parentKey` nuls si le ticket n'est pas indexé ; `parentKey` nul si ce n'est pas une sous-tâche. |

Le champ `jiraKey` existant reste, identique à `jiraTicket.key` (FR-014).
