# Contrat REST : progression de l'assistant

Mêmes règles que le reste de l'API : cookie de session, contrôle de l'origine
(`LocalSecurityFilter`), erreurs au format Problem existant (`code`, `title`, `detail`).

Les étapes appellent par ailleurs les API existantes, inchangées : connexions
(`/api/settings/copilot/*`, `/api/settings/jira`, `/api/settings/confluence`), accords
(`/api/consents/{service}`), projet (`PUT /api/projects/{id}`), sources
(`/api/projects/{id}/doc-sources`, `/api/projects/{id}/architecture-source`), indexation
(`/api/projects/{id}/indexations`, `…/indexations/current`).

## `GET /api/onboarding`

```json
{
  "currentStep": "JIRA",
  "steps": [
    { "step": "COPILOT",    "required": true,  "state": "DONE" },
    { "step": "JIRA",       "required": false, "state": "TODO" },
    { "step": "CONFLUENCE", "required": false, "state": "TODO" },
    { "step": "PROJECT",    "required": false, "state": "TODO" },
    { "step": "SOURCES",    "required": false, "state": "TODO" },
    { "step": "INDEXATION", "required": false, "state": "TODO" }
  ],
  "completed": false,
  "completedAt": null,
  "autoOpen": true
}
```

`autoOpen` = non terminé **et** `specwriter.onboarding.auto-open` (R5, R7).

## `PUT /api/onboarding/steps/{step}`

Corps : `{ "state": "DONE" }` ou `{ "state": "SKIPPED" }`. Réponse : la progression (même forme
que `GET`), `currentStep` avancé à l'étape suivante.

| Cas | Réponse |
|---|---|
| `COPILOT` + `DONE`, Copilot connecté, modèle choisi, accord donné | 200 |
| `COPILOT` + `DONE`, une condition manque | 409 `ONBOARDING_COPILOT_REQUIRED` |
| `COPILOT` + `SKIPPED` | 409 `ONBOARDING_COPILOT_REQUIRED` |
| étape inconnue, ou `WELCOME` / `DONE` | 400 `ONBOARDING_UNKNOWN_STEP` |

## `PUT /api/onboarding/current`

Corps : `{ "step": "PROJECT" }`. Déplace l'étape courante (retour en arrière, reprise). Une étape
après `COPILOT` alors que `COPILOT` n'est pas fait → 409 `ONBOARDING_COPILOT_REQUIRED`.

## `POST /api/onboarding/complete`

Termine l'assistant (`currentStep = DONE`, `completedAt`). 409 `ONBOARDING_COPILOT_REQUIRED` si
Copilot n'est pas fait. Idempotent : un second appel garde la première date.

## Journal

Chaque changement est consigné au journal (« Assistant de premier lancement : étape Jira
passée »), sans aucune valeur saisie (adresse, jeton, nom de projet).
