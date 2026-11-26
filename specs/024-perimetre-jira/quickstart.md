# Quickstart : périmètre Jira (024)

Prérequis : le jar des gates, ou le dev mode, avec Jira simulé par WireMock. Contrats :
[contracts/rest-api.md](contracts/rest-api.md) et [contracts/mcp-tools.md](contracts/mcp-tools.md).

## Q1 — Régler le label (US1)

Dans **Projet › Réglages › Suivi des tâches dans Jira**, saisir `spec writer` : refusé, avec le
motif. Saisir `spec-writer` et enregistrer : le réglage est gardé au rechargement.

## Q2 — Label posé (US1)

- Envoyer une spécification vers Jira : WireMock reçoit `fields.labels` avec `spec-writer`.
- Valider ses tâches : chaque sous-tâche créée porte le label ; une sous-tâche reprise reçoit
  `PUT …/issue/{clé}` avec `update.labels.add`.
- Créer une spécification depuis un ticket existant : le ticket reçoit l'ajout du label.
- Création refusée à cause du label (400 `errors.labels`) : ticket recréé sans label, puis ajout ;
  ajout refusé : `labelWarning` affiché.

## Q3 — Filtres de la vue (US2)

Une story labellisée ouverte, une story labellisée fermée, une story sans label ouverte. À
l'ouverture : la première seule. « Afficher les tickets terminés » : la deuxième s'ajoute. Retirer
« Avec le label spec-writer » : la troisième s'ajoute. Le pager suit. Quitter la vue et revenir :
les choix sont gardés. Sans label réglé : le filtre du label n'est pas proposé.

## Q4 — Périmètre du MCP (US3)

Deux features prêtes, l'une liée à une story labellisée, l'autre non : `list_features` n'en rend
qu'une ; `get_feature` sur l'autre rend `NOT_IN_JIRA_SCOPE`. Une sous-tâche sans label : `inScope`
faux, jamais rendue par `next_task`, refusée par `start_task`.

## Q5 — Sous-tâches hors recherche (US4)

Un besoin dont les mots sont dans le titre d'une sous-tâche : pas de sous-tâche parmi les tickets
proches ni dans `search_knowledge` ; la sous-tâche reste sous sa story dans la vue et dans la
rubrique « Sous-tâches » de la version.

## Q6 — Suppression (US5)

Retirer de WireMock une sous-tâche suivie et la story d'une autre spécification, puis
« Resynchroniser Jira » : elles disparaissent de la vue ; le panneau Tâches compte une tâche de
moins ; la page de l'autre spécification dit que la story a été supprimée. Jira coupé, ou liste
des clés vide : rien n'est retiré, une erreur de synchronisation est affichée.

## Gates

`./mvnw -B verify -DskipDependencyCheck`, puis `npm --prefix e2e test` sur le jar construit, puis
`bash ci/check-eval.sh` (aucun prompt ni code LLM touché).
