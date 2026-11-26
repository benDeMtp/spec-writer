# Contrat MCP : fin d'une tâche et story qui suit (023)

Complète `specs/015-taches-avancement/contracts/mcp-tools.md` et
`specs/022-hierarchie-sous-taches/contracts/mcp-tools.md`. Aucun champ existant n'est retiré.

## `complete_task`

- **Projet réglé** : la sous-tâche passe au statut d'arrivée (« Réalisée »).
  `task.status` vaut `DONE` et `task.jiraStatus` le vrai nom du statut Jira.
- **Aucune transition directe** : erreur `NO_TRANSITION`, avec le message « Le workflow Jira de
  AURORA-182 n'offre aucune transition directe de « En Cours » vers « Réalisée ». Faites-la dans
  Jira. » Rien ne change.
- **Champ `story` ajouté** (nul si la story n'est pas concernée) :

```json
{
  "task": { "key": "AURORA-185", "status": "DONE", "…": "…" },
  "jiraSync": "OK",
  "story": { "key": "AURORA-24", "status": "MOVED", "to": "Réalisée", "message": null }
}
```

Valeurs de `story.status` :

| Valeur | Sens |
|---|---|
| `MOVED` | la story a été déplacée |
| `ALREADY` | la story était déjà dans un statut fini |
| `SKIPPED` | projet sans réglage : la story n'est pas déplacée à la fin |
| `FAILED` | pas de transition, refus ou Jira injoignable ; `message` dit quoi faire dans Jira |

## `start_task`

Même champ `story`. Il vaut `MOVED` quand la story est passée de « à faire » à « en cours », et il
est nul si la story était déjà en cours ou plus loin.

## `set_feature_status` (feature sans tâches)

Vers `IMPLEMENTED`, sur un projet réglé et avec un ticket lié, la réponse gagne le même champ
`story`.

## Descriptions des outils

- `complete_task` : la sous-tâche passe au statut d'arrivée réglé dans le projet (sinon à
  « terminé ») ; `story` dit ce qui est arrivé à la story.
- `start_task` : la story « à faire » passe en cours.

## Affectation au propriétaire du jeton (US5)

Avant chaque transition, la sous-tâche ou la story est affectée au propriétaire du jeton Jira.
Aucun champ n'est ajouté. Une affectation refusée ne bloque rien :
- pour la sous-tâche, `message` de `start_task` ou `complete_task` dit « AURORA-185 n'a pas pu
  être affectée à jdupont : … » ;
- pour la story, `story.message` le dit, avec `story.status` à `MOVED`.

