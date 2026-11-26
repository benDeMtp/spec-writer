# Contrat REST (024)

Toutes les écritures exigent l'en-tête `Origin` de l'outil (403 sinon), comme les autres réglages.

## `GET /api/projects/{id}/jira-label`

```json
{ "label": "spec-writer" }
```

`label` vaut `null` sans réglage. 404 `PROJECT_NOT_FOUND`.

## `PUT /api/projects/{id}/jira-label`

Corps `{ "label": "spec-writer" }`. 200 avec le réglage. 400 `VALIDATION_FAILED` : « Un label Jira
ne contient pas d'espace et compte au plus 255 caractères. »

## `DELETE /api/projects/{id}/jira-label`

204. Plus rien n'est étiqueté ni filtré ; les labels déjà posés restent dans Jira.

## `GET /api/projects/{id}/jira-issues` (017, 022) — paramètres ajoutés

| Paramètre | Défaut | Sens |
|---|---|---|
| `labelled` | `true` | seulement les tickets de premier niveau qui portent le label du projet ; ignoré sans label |
| `done` | `false` | inclure les tickets de premier niveau de catégorie « terminé » |

Réponse : champs ajoutés à `IssuePage`.

```json
{
  "jiraProjectKey": "AURORA",
  "label": "spec-writer",
  "labelled": true,
  "done": false,
  "total": 12,
  "startAt": 0,
  "issues": [ "…" ]
}
```

`total` compte les tickets de premier niveau retenus par les filtres. Les sous-tâches d'un ticket
retenu sont toutes rendues (022).

## `GET /api/sessions/{id}` — champ ajouté

`lostJiraIssueKey` : clé du ticket lié supprimé dans Jira, `null` sinon.

## Création du ticket et validation des tâches — avertissement ajouté

`POST /api/sessions/{id}/jira` et la validation des tâches gagnent `labelWarning` (texte, `null`
sinon) quand le label n'a pas pu être posé : « Le label spec-writer n'a pas pu être posé sur
PROJ-12 : … ».
