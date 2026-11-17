# Contrat : API de l'écran des tâches (015)

Ressource `infrastructure/web/TaskResource`, `@Path("/api/sessions/{id}/tasks")`, même protection
que les autres ressources de session (cookie de session, Origin local). Erreurs au format habituel
`{code, message}`.

| Méthode et chemin | Corps | Réponse | Notes |
|---|---|---|---|
| `GET /` | — | `TasksView` | rejoue les transitions en attente puis relit les statuts (R5, R6) |
| `POST /proposal` | — | `ProposalView` | demande au modèle (`propose-tasks`) ; remplace la proposition en cours ; 409 `NO_LINKED_TICKET`, `PARENT_IS_SUBTASK` ; 503 `TASKS_UNAVAILABLE` |
| `PUT /proposal` | `ProposalView` corrigée | `ProposalView` | enregistre les corrections (renommer, fusionner, supprimer, réordonner, ajouter) ; bornes vérifiées à la validation seulement, sauf titre ≤ 120 et résultat ≤ 300 |
| `DELETE /proposal` | — | 204 | abandon |
| `POST /proposal/validate` | — | `TasksView` | crée les sous-tâches (R10) ; 409 `NO_SUBTASK_TYPE` ; création partielle → 200 avec tâches `NOT_CREATED` et `creationProblem` |
| `POST /create-remaining` | — | `TasksView` | relance la création des tâches `NOT_CREATED` |
| `POST /` | `{title, outcome, dependsOn}` | `TasksView` | ajout manuel après validation (`origin = MANUAL`), créé aussitôt dans Jira |
| `PATCH /{taskId}` | `{title?, position?, dependsOn?}` | `TasksView` | renommer met aussi à jour le résumé Jira ; une tâche `NOT_CREATED` peut être supprimée par `DELETE /{taskId}`, une tâche `CREATED` non (409 `CANNOT_DELETE_CREATED`) |
| `POST /{taskId}/release` | — | `TasksView` | libère une tâche tenue par un agent (FR-022) ; transition Jira catégorie `new` |
| `POST /sync` | — | `TasksView` | bouton « Synchroniser » (FR-013a) |
| `POST /resume` | — | `TasksView` | reprend le suivi suspendu et recalcule le statut (FR-020a) |

`PUT /api/sessions/{id}/status` existant : 409 `STATUS_FOLLOWS_TASKS` pour `IN_PROGRESS` ou
`IMPLEMENTED` quand la session a des tâches ; `DRAFT` ou `READY` passe `task_tracking` à
`SUSPENDED`.

## `TasksView`

```json
{
  "linkedIssueKey": "FACT-120",
  "canPropose": true,
  "proposeBlocked": "NO_LINKED_TICKET | PARENT_IS_SUBTASK | null",
  "tracking": "ACTIVE | SUSPENDED",
  "progress": {"done": 3, "total": 5},
  "lastReadAt": "2026-11-17T10:12:00Z",
  "readProblem": "JIRA_UNREACHABLE | null",
  "pendingSync": 1,
  "creationProblem": "message | null",
  "hasProposal": false,
  "tasks": [
    {
      "id": "…", "position": 0, "title": "…", "outcome": "…",
      "basis": {"kind": "MODULE", "ref": "facturation-api"},
      "origin": "PROPOSED", "creation": "CREATED",
      "key": "FACT-124", "url": "https://jira…/browse/FACT-124",
      "status": "IN_PROGRESS", "jiraStatusName": "En cours",
      "heldBy": "claude-code", "heldSince": "…", "holderClosed": false,
      "pending": "DONE | null", "report": "…", "dependsOn": ["…"]
    }
  ]
}
```

## `ProposalView`

```json
{
  "basis": "MODULES | USE_CASES",
  "existing": [{"key": "FACT-121", "title": "…"}],
  "tasks": [{"title": "…", "outcome": "…", "basis": {"kind": "USE_CASE", "ref": "UC-02"}, "dependsOn": [0]}]
}
```
