# Contrat MCP : périmètre Jira (024)

Complète `specs/015-taches-avancement/contracts/mcp-tools.md`,
`specs/022-hierarchie-sous-taches/contracts/mcp-tools.md` et
`specs/023-statut-fin-tache/contracts/mcp-tools.md`. Aucun champ n'est retiré. Sans label réglé
sur le projet, rien ne change.

## `list_features`

Seules les features sans ticket lié, ou dont la story porte le label du projet, sont listées.

## `get_feature`, `set_feature_status`, `next_task`, `start_task`, `complete_task`

Feature hors périmètre : erreur `NOT_IN_JIRA_SCOPE`, avec le message « La feature X est liée à
PROJ-12, qui ne porte pas le label spec-writer : elle n'est pas suivie par spec-writer dans
Jira. »

## Tâches

- `TaskInfo` gagne `inScope` (booléen).
- Une tâche `inScope: false` n'est pas proposée par `next_task` ; `start_task` et `complete_task`
  la refusent avec `NOT_IN_JIRA_SCOPE` : « La sous-tâche PROJ-302 ne porte pas le label
  spec-writer : elle n'est pas suivie par spec-writer. »
- Une tâche supprimée dans Jira n'existe plus après la synchronisation : `NOT_FOUND`, avec « La
  tâche PROJ-302 n'est plus une tâche de cette feature : elle a peut-être été supprimée dans
  Jira. »

## `search_knowledge`

Les sous-tâches ne sont plus retournées.

## Descriptions des outils

- `list_features` : « seules les features suivies par spec-writer dans Jira (label du projet) ».
- `next_task` : « une tâche dont la sous-tâche ne porte pas le label n'est jamais proposée ».
