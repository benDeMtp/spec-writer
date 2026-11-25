# Contrat : hiérarchie et consigne de branche dans la passerelle MCP (022)

Complète `specs/015-taches-avancement/contracts/mcp-tools.md`. Aucun champ existant n'est retiré
ni renommé (FR-014).

## Instructions du serveur (`initialize`)

Le texte `instructions` gagne une phrase :

> Une tâche qui est une sous-tâche (champ parentKey) se fait sur la branche de son ticket parent :
> si une branche est nécessaire, reprenez ou créez celle du parent, jamais une branche par
> sous-tâche.

## Tâche (`get_feature`, `next_task`, `start_task`, `complete_task`)

Champs ajoutés à chaque tâche :

```json
{
  "key": "AURORA-182",
  "title": "Obtenir l'accès en lecture aux composants Halo sur le poste",
  "outcome": "Un développeur peut installer et importer les composants Halo…",
  "description": "## Accès…",
  "issueType": "Sous-Tâche (AGILE)",
  "parentKey": "AURORA-24",
  "position": 0,
  "dependsOn": [],
  "status": "TODO",
  "heldByYou": false,
  "jiraSync": "OK"
}
```

- `parentKey` : clé du ticket lié à la feature, sous lequel la tâche est créée. Toujours présent
  pour une tâche listée.
- `issueType` : libellé du type dans Jira, relu avec le statut ; `null` tant que la tâche n'a pas
  été relue.

## Consigne de branche (`next_task`, `start_task`)

Champ ajouté à la réponse quand la tâche a un `parentKey` :

```json
{
  "outcome": "TASK",
  "task": { "key": "AURORA-182", "parentKey": "AURORA-24", "…": "…" },
  "resume": false,
  "branch": "AURORA-182 est une sous-tâche de AURORA-24 : si une branche est nécessaire, travaillez sur celle de AURORA-24 (reprenez-la si elle existe). Toutes les sous-tâches de AURORA-24 s'y font."
}
```

`start_task` : même champ `branch` à côté de `task`, `jiraSync` et `message`. Absent (`null`) dans
`complete_task`, et pour `NONE_LEFT` ou `ALL_HELD`.

## Ticket d'une feature (`get_feature`)

Champ ajouté, à côté de `jiraKey` (inchangé) :

```json
{
  "jiraKey": "AURORA-182",
  "jiraTicket": { "key": "AURORA-182", "issueType": "Sous-Tâche (AGILE)", "parentKey": "AURORA-24" }
}
```

- `null` sans ticket lié.
- Lu dans l'index : `issueType` et `parentKey` sont `null` si le ticket n'est pas indexé ;
  `parentKey` est `null` si le ticket n'est pas une sous-tâche.

## Descriptions des outils

- `next_task` : la tâche porte `parentKey` ; pour une sous-tâche, `branch` dit sur quelle branche
  travailler.
- `start_task` : idem.
- `get_feature` : `jiraTicket` donne le type et le parent du ticket lié.
