# Quickstart : statut d'une tâche implémentée (023)

Prérequis : le jar des gates, ou le dev mode, avec Jira simulé par WireMock. Contrats :
[contracts/rest-api.md](contracts/rest-api.md) et [contracts/mcp-tools.md](contracts/mcp-tools.md).

Le workflow simulé reprend celui d'AURORA :
- la story est « Prête » ;
- les sous-tâches sont dans un statut « Créée » qui mène à « En Cours » ;
- depuis « En Cours », le workflow offre « Réaliser » vers « Réalisée » (catégorie
  indeterminate), « Fermer » vers « Fermée » (done) et « Annuler » vers « Annulée » (done).

## Q1 — Régler le projet (US3)

1. Synchroniser Jira, puis ouvrir les réglages du projet, rubrique « Suivi des tâches dans Jira ».
2. Attendu : les statuts connus, classés par catégorie, avec leur catégorie.
3. Choisir « Réalisée », cocher « En cours de test », puis enregistrer et recharger : le réglage est
   gardé. Choisir « Prête » : l'enregistrement est refusé, avec le message.

## Q2 — Fin d'une tâche (US1)

`start_task`, puis `complete_task` par la passerelle. Attendu :
- la transition envoyée est « Réaliser », pas « Fermer » ;
- la tâche est `DONE` et `jiraStatus` vaut « Réalisée » ;
- le compte rendu est en commentaire.

Avec un workflow sans transition vers « Réalisée » : `NO_TRANSITION`, et rien ne change.

## Q3 — Tâche finie à la relecture (US2)

Rappeler `get_feature` : la tâche « Réalisée » reste `DONE`. La passer à « En cours de test » dans
WireMock : elle reste `DONE`. La renvoyer à « En Cours » : elle redevient `IN_PROGRESS`.

## Q4 — La story suit (US4)

- À la première prise d'une tâche, la story « Prête » passe « En Cours » (`story.status` vaut
  `MOVED`).
- À la fin de la dernière tâche, la story passe à « Réalisée ».
- Story sans transition possible : la tâche est terminée quand même, avec `story.status` à `FAILED`
  et une entrée `WARN` au journal.

## Q5 — Sans réglage (FR-005, FR-018)

Retirer le réglage : `complete_task` vise la catégorie « terminé » (comportement de la 015), et la
story n'est pas déplacée à la fin.

## Q6 — Sur AURORA, à la main (mainteneur)

Régler « Réalisée » et les statuts qui suivent, puis faire terminer une sous-tâche par l'agent.
Attendu : la sous-tâche est « Réalisée » dans Jira.

## Gates

`./mvnw -B verify -DskipDependencyCheck`, puis `npm --prefix e2e test` sur le jar construit, puis
`bash ci/check-eval.sh` (aucun prompt ni code LLM touché).
