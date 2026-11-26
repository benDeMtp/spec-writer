# Contrat REST : suivi des tâches dans Jira (023)

## `GET /api/projects/{id}/task-workflow`

200 :

```json
{
  "arrivalStatus": "Réalisée",
  "finishedStatuses": ["Réalisée", "En cours de test", "A valider"],
  "known": [
    { "name": "Créée", "category": "new", "seen": true },
    { "name": "Prête", "category": "new", "seen": true },
    { "name": "En Cours", "category": "indeterminate", "seen": true },
    { "name": "Réalisée", "category": "indeterminate", "seen": true },
    { "name": "Fermée", "category": "done", "seen": true },
    { "name": "Déployable", "category": null, "seen": false }
  ]
}
```

- Sans réglage : `arrivalStatus` vaut `null` et `finishedStatuses` vaut `[]`.
- `known` donne les statuts lus dans l'index, classés par catégorie puis par nom. Un statut réglé
  mais absent de l'index y figure avec `seen: false` et `category: null`.
- Projet inconnu : 404 `PROJECT_NOT_FOUND`.

## `PUT /api/projects/{id}/task-workflow`

Corps :

```json
{ "arrivalStatus": "Réalisée", "finishedStatuses": ["En cours de test", "A valider"] }
```

- 200 : la même forme que `GET`. Le statut d'arrivée est ajouté aux statuts finis.
- 422 `VALIDATION_FAILED` :
  - statut d'arrivée vide ou de plus de 100 caractères ;
  - plus de 50 statuts finis ;
  - statut d'arrivée connu dans la catégorie `new`, avec le message « Le statut d'arrivée d'une
    tâche implémentée ne peut pas être un statut « à faire » (Prête). »
- En-tête `Origin` obligatoire, comme les autres écritures.

## `DELETE /api/projects/{id}/task-workflow`

204 : le réglage est retiré, et le projet revient à la catégorie « terminé » (FR-005).
