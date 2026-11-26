# Contrat REST : vue « Tickets Jira » (022)

Prolonge `specs/017-synchro-auto-sources/contracts/rest-api.md`. Mêmes paramètres, mêmes erreurs.

## `GET /api/projects/{projectId}/jira-issues`

Paramètres inchangés : `sort` (`updated`, `status`, `sync`), `dir`, `startAt`, `size` (1 à 100,
50 par défaut).

Changements :
- `issues` ne contient que les tickets du **premier niveau** : ceux qui n'ont pas de parent
  indexé. Le tri et la pagination portent sur eux seuls.
- `total` compte les tickets du premier niveau.
- Chaque ticket porte `subtasks` (sous-tâches indexées, classées par clé dans l'ordre numérique,
  même forme qu'un ticket, sans `subtasks` à leur tour) et `parentKey` (clé du parent d'une
  sous-tâche orpheline, `null` sinon).

200 :

```json
{
  "jiraProjectKey": "AURORA",
  "lastSync": { "at": "2026-11-24T17:33:00Z", "outcome": "CHANGED", "change": { "added": 0, "modified": 3, "removed": 0 }, "errorCode": null },
  "total": 1980,
  "startAt": 0,
  "issues": [
    {
      "key": "AURORA-24",
      "title": "Accès Halo pour aurora-ui",
      "status": "En cours",
      "statusCategory": "indeterminate",
      "updatedAt": "2026-11-24T16:22:45Z",
      "sync": "IN_SYNC",
      "sessionId": "1b6a4894-…",
      "parentKey": null,
      "subtasks": [
        {
          "key": "AURORA-182",
          "title": "Obtenir l'accès en lecture aux composants Halo sur le poste",
          "status": "Créée",
          "statusCategory": "new",
          "updatedAt": "2026-11-24T16:22:45Z",
          "sync": "UNLINKED",
          "sessionId": null,
          "parentKey": null,
          "subtasks": []
        }
      ]
    },
    {
      "key": "RIS-994",
      "title": "Sous-tâche dont la story n'est pas indexée",
      "status": "À faire",
      "statusCategory": "new",
      "updatedAt": "2026-11-20T10:00:00Z",
      "sync": "UNLINKED",
      "sessionId": null,
      "parentKey": "RIS-12",
      "subtasks": []
    }
  ]
}
```

Le `parentKey` d'une sous-tâche placée dans `subtasks` est nul : son parent est la ligne qui la
contient.
