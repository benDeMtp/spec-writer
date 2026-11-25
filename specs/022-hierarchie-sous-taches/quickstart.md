# Quickstart : vérifier la hiérarchie des sous-tâches (022)

Prérequis : dev mode (`quarkus_start`, port 47625) ou jar des gates ; front sur 4200 en dev mode.
Données : projet E2E « Jira simulé » (WireMock, `e2e/fixtures/jira`), qui compte une story et ses
sous-tâches. Les contrats sont dans [contracts/rest-api.md](contracts/rest-api.md) et
[contracts/mcp-tools.md](contracts/mcp-tools.md).

## Q1 — Sous-tâches repliées sous leur parent (US1)

1. Synchroniser Jira, puis ouvrir « Tickets Jira ».
2. Attendu :
   - la story affiche « N sous-tâches », repliée ;
   - aucune de ses sous-tâches n'est une ligne du premier niveau ;
   - un clic (ou Entrée sur le bouton) déplie les sous-tâches sous la story, en retrait, avec un
     lien chacune ; le lecteur d'écran annonce « déplié ».
3. Trier par statut, puis changer de page : la story et ses sous-tâches restent ensemble ; `total`
   compte les tickets du premier niveau.

## Q2 — Sous-tâche orpheline (US1, FR-005)

Avec une sous-tâche dont le parent n'est pas indexé (fixture dédiée) : elle est au premier niveau
et indique « Sous-tâche de <clé> ».

## Q3 — Parent et type dans les outils MCP (US2)

1. Feature liée à une story, tâches créées (015).
2. `next_task`, puis `start_task` et `get_feature` par la passerelle :
   - chaque tâche porte `parentKey` = clé de la story et `issueType` ;
   - `get_feature.jiraTicket` = `{key, issueType, parentKey: null}` pour une story.
3. Couper Jira (WireMock arrêté) et rappeler `next_task` : `parentKey` et `issueType` restent.

## Q4 — Consigne de branche (US3)

- `initialize` : les instructions mentionnent la branche du parent.
- `next_task` (TASK) et `start_task` : `branch` cite la clé du parent.
- `complete_task` : pas de `branch`.

## Q5 — Essai avec un agent réel (SC-005, à la main)

Sur l'installation du mainteneur : demander à l'agent de code d'enchaîner deux sous-tâches d'une
même story. Attendu : une seule branche, celle de la story, sans consigne dans la conversation.

## Gates

`./mvnw -B verify -DskipDependencyCheck` puis `npm --prefix e2e test` sur le jar construit
(CLAUDE.md). `ci/check-eval.sh` reste vert : aucun prompt ni code LLM touché.
