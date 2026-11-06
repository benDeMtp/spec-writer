# Contrat : réindexation par source

Complète `specs/002-projets-contexte-mcp/contracts/openapi.yaml`.

## POST /api/projects/{id}/indexations

Corps :

```json
{ "mode": "FULL", "sources": ["JIRA", "ARCHITECTURE", "0b6f…-uuid-source-doc"] }
```

- `sources` absent : toutes les sources (inchangé).
- `sources` présent : `mode` doit être `FULL` ; liste non vide ; chaque valeur est `JIRA`,
  `ARCHITECTURE` ou l'identifiant d'une source de documentation du projet.
- 202 : `IndexationView`, avec `sources` repris.
- 400 `VALIDATION_FAILED` : liste vide, valeur inconnue, `INCREMENTAL` avec une liste.
- 409 `OPERATION_IN_PROGRESS` : indexation en cours (inchangé).

## GET /api/projects/{id}/indexations/current

`IndexationView` gagne `sources` (`string[]` ou `null`). Une erreur inattendue d'étape apparaît
dans `errors` : `{ "kind": "JIRA" | "SOURCE" | "ARCHITECTURE" | "MODEL", "location": …,
"reason": "UNEXPECTED", "message": "Erreur inattendue (…)" }`, et `status` vaut `SUCCEEDED`.

## GET /api/projects/{id}/knowledge

Le statut gagne `sources` :

```json
"sources": [
  { "id": "JIRA", "kind": "JIRA", "label": "Jira AURORA" },
  { "id": "0b6f…", "kind": "DOCUMENTATION", "label": "/Users/…/docs/build/site" },
  { "id": "ARCHITECTURE", "kind": "ARCHITECTURE", "label": "/Users/…/C4/07-aurora/workspace.dsl" }
]
```

## Front

- `knowledge.api.ts` : `start(projectId, mode, sources?)`.
- Panneau : « Réindexer des sources… », groupe de cases « Sources à réindexer », « Réindexer la
  sélection » (désactivé sans case cochée), « Annuler ».
- Bilan : « Sources réindexées : Jira AURORA, … » quand `sources` n'est pas `null`.
