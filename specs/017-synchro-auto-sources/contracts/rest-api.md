# Contrats REST : 017

Tous les points d'accès exigent le cookie de session. Les écritures exigent aussi l'en-tête `Origin`
de l'outil, comme le reste de l'API. Les erreurs suivent le format de problème existant
(`code`, `message`).

## Vue Jira depuis l'index (US1)

### `GET /api/projects/{projectId}/jira-issues`

Paramètres :
- `sort` : `updated` (par défaut), `status` ou `sync` ;
- `dir` : `desc` (par défaut) ou `asc` ;
- `startAt` : entier ≥ 0, par défaut 0 ;
- `size` : 1 à 100, par défaut 50.

200 :

```json
{
  "jiraProjectKey": "SW",
  "lastSync": { "at": "2026-11-18T16:40:00Z", "result": "CHANGED" },
  "total": 2000,
  "startAt": 0,
  "issues": [
    {
      "key": "SW-412",
      "title": "Plafond des notes de frais",
      "status": "En cours",
      "statusCategory": "indeterminate",
      "updatedAt": "2026-11-17T09:12:00Z",
      "syncState": "CONFLICT",
      "sessionId": "1b6a4894-…"
    }
  ]
}
```

Cas particuliers :
- `lastSync` est `null` si Jira n'a jamais été synchronisé ; la vue invite alors à le faire.
- `syncState` vaut `UNLINKED` quand le ticket n'est lié à aucune spécification, et `sessionId` est
  alors absent.

Erreurs :
- 400 `INVALID_SORT` pour un tri, une direction ou une taille hors liste ;
- 404 si le projet est inconnu ;
- 409 `JIRA_NOT_CONFIGURED` si le projet n'a pas de clé Jira.

Aucun appel à Jira n'est fait (SC-002).

La resynchronisation de Jira seul reprend l'existant
`POST /api/projects/{projectId}/indexations` avec `{ "mode": "INCREMENTAL", "sources": ["jira"] }`.

### Retiré

`GET /api/jira/projects/{key}/issues`, dont le seul appelant était la vue.

## Délais et bilans par source (US2)

### `GET /api/projects/{projectId}/source-sync`

200 : une entrée par source indexable du projet, dans l'ordre de `GET …/indexations/sources`.

```json
[
  {
    "sourceKey": "jira",
    "kind": "JIRA",
    "label": "Jira SW",
    "intervalMinutes": 60,
    "last": { "at": "2026-11-18T16:40:00Z", "result": "CHANGED", "added": 2, "modified": 5, "removed": 0, "errorCode": null },
    "lastSuccessAt": "2026-11-18T16:40:00Z",
    "nextRunAt": "2026-11-18T17:40:00Z"
  },
  {
    "sourceKey": "7f0c…",
    "kind": "DOCUMENTATION",
    "label": "Remboursements",
    "intervalMinutes": null,
    "last": null,
    "lastSuccessAt": null,
    "nextRunAt": null
  }
]
```

Les champs à `null` ont un sens précis :
- `intervalMinutes` à `null` : délai désactivé ;
- `last` à `null` : la source n'a jamais été synchronisée ;
- `nextRunAt` à `null` : délai désactivé.

### `PUT /api/projects/{projectId}/source-sync/{sourceKey}`

Corps : `{ "intervalMinutes": 15 | 30 | 60 | 240 | 1440 | null }`.

200 : l'entrée mise à jour, avec `nextRunAt` recalculé.

Erreurs :
- 400 `INVALID_SYNC_INTERVAL` ;
- 404 si la source ou le projet est inconnu.

### Point d'accès existant prolongé

`GET /api/projects/{projectId}/indexations/current` garde sa forme. Les bilans par source se lisent
sur `source-sync` une fois l'indexation terminée.

## Notifications (US3)

### `GET /api/settings/notifications`

200 : `{ "sourceUpdates": false }`.

### `PUT /api/settings/notifications`

Corps : `{ "sourceUpdates": true }`.

204 en cas de succès, 400 si le corps est invalide.

### `GET /api/events`

`text/event-stream`. Voir [events.md](events.md).
