# Data Model: Statut Jira d'une tâche implémentée (023)

## Base (V35)

### `project_task_workflow`

| Colonne | Type | Règle |
|---|---|---|
| `project_id` | `text`, clé | `references project (id) on delete cascade` |
| `arrival_status` | `text`, non nul | 1 à 100 caractères ; nom du statut tel que Jira l'affiche |
| `finished_statuses` | `text`, non nul | tableau JSON de noms (chacun 1 à 100 caractères), 50 au plus ; peut être `[]` |
| `updated_at` | `text`, non nul | instant ISO |

Pas de ligne : pas de réglage.

## Domaine

### `TaskWorkflow` (`domain/task`)

Record `TaskWorkflow(String arrivalStatus, Set<String> finishedStatuses)`, plus la constante `NONE`.

- **`finished(String statusName, String category)`** : vrai si la catégorie est `done`, ou si le
  nom est le statut d'arrivée ou l'un des statuts cochés (comparaison `StatusName.same`).
- **`statusOf(String statusName, String category)`** : `DONE` si `finished`, sinon le statut de la
  catégorie (`new` donne `TODO`, `indeterminate` donne `IN_PROGRESS`, `done` donne `DONE`).
- **`completion()`** : la cible d'une fin. `Target.Status(arrivalStatus)` si le projet est réglé,
  sinon `Target.Category(DONE)`.
- **Validation à la création** :
  - le statut d'arrivée a de 1 à 100 caractères ;
  - les statuts cochés sont au plus 50, sans doublon (au sens de `StatusName.same`) ;
  - le statut d'arrivée est ajouté aux finis s'il n'y est pas.

### `StatusName` (`domain/task`)

- `normalize(String)` : NFD, sans diacritiques, minuscules, `strip`.
- `same(a, b)` : égalité des formes normalisées.

### `Target` (`domain/task`), interface scellée

- `Category(StatusCategory category)` : le comportement de la 015.
- `Status(String name, TaskWorkflow workflow)` : un statut nommé ; `workflow` dit ce qui est déjà
  fini, pour `ALREADY`.

### `KnownStatus` (`domain/task`)

- `KnownStatus(String name, String category)`, lu dans l'index (R2).

### `StoryMove` (`application/task`)

- `StoryMove(String key, Outcome status, String to, String message)`.
- `Outcome` vaut `MOVED`, `ALREADY`, `SKIPPED` ou `FAILED`.

## Règles d'état

```text
Relecture d'une sous-tâche (TaskSync.reread) :
  statut Jira fini (arrivée, coché, ou catégorie done) ─► DONE
  sinon catégorie new ─► TODO ; indeterminate ─► IN_PROGRESS
  absente ─► MISSING (inchangé)

Fin d'une tâche (complete_task), projet réglé :
  déjà fini ─► ALREADY (aucune transition), tâche DONE
  transition directe vers l'arrivée, sans annulation ─► SENT, tâche DONE
  aucune ─► NO_TRANSITION, rien ne change
  Jira injoignable ─► tâche DONE « à synchroniser », rejouée vers l'arrivée (R5)

Story (StoryFollow) :
  prise d'une tâche, story en catégorie new ─► catégorie « en cours » (jamais vers un statut fini)
  toutes les tâches DONE, projet réglé, story pas finie ─► statut d'arrivée
  feature sans tâches IMPLEMENTED, projet réglé ─► statut d'arrivée
  échec ─► WARN au journal + champ story FAILED ; pas de rejeu
```

### `AgentTasks.TaskInfo` (MCP)

| Champ ajouté | Type | Règle |
|---|---|---|
| `jiraStatus` | `String`, nul | Vrai nom du statut Jira de la sous-tâche (`jira_status_name`), par exemple « Réalisée » (FR-013). |

### `AgentTasks.TaskAnswer` et la réponse de `set_feature_status`

| Champ ajouté | Type | Règle |
|---|---|---|
| `story` | `StoryMove`, nul | Ce qui est arrivé à la story de la feature (R8). |

### `IssueStatus` (`domain/jira`, US5)

| Champ ajouté | Type | Règle |
|---|---|---|
| `assignee` | `String`, nul | Login Jira de la personne affectée, nul si personne. |

### `TaskJira.Move` (`application/task`, US5)

- `Move(Result result, String status, String assignProblem)` : l'issue d'un envoi, le statut
  atteint au démarrage, et le motif d'une affectation refusée (nul si l'affectation a réussi ou
  n'était pas nécessaire).

Règle d'affectation : avant toute transition envoyée par spec-writer, sauf la remise « à faire »,
le ticket est affecté au propriétaire du jeton s'il ne l'est pas déjà.

