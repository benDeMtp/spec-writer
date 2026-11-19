# Contrat : arrêts de la visite guidée

Ordre d'usage (FR-013). Chaque cible est un attribut `data-tour` posé dans le gabarit existant ;
un test unitaire vérifie que chaque cible déclarée existe dans un gabarit, et l'E2E que chaque
arrêt désigne un élément visible (SC-003).

| # | Arrêt | Route | Cible `data-tour` | Rôle expliqué | Repli (élément absent) |
|---|---|---|---|---|---|
| 1 | Conversation | `/` | `composer` | Poser une question à l'agent, qui cherche dans la connaissance du projet et cite ses sources | — |
| 2 | Rédiger depuis la discussion | `/` | `draft-spec` | Transformer la discussion en spécification | Bouton absent : bulle sur `composer`, « apparaît quand la discussion décrit un besoin » |
| 3 | Spécifications | `/sessions` | `sessions-list` | Retrouver, chercher et rouvrir les spécifications | Liste vide : bulle sur l'état vide |
| 4 | Une spécification | `/sessions/{plus récente}` | `spec-document` | Le document, ses versions, son ticket Jira lié | Aucune spécification : reste sur `/sessions`, texte de remplacement |
| 5 | Discussion | idem | `discuss` | « Discuter avec l'IA » : proposer une nouvelle version, relue par diff | idem |
| 6 | Tâches | idem | `tasks-panel` | Tâches proposées, créées en sous-tâches Jira, suivies par les agents de code | idem |
| 7 | Tickets Jira | `/jira` | `jira-table` | Tri par date, statut, état de synchro ; fraîcheur de la liste | Pas de clé Jira : bulle sur le message existant |
| 8 | Connaissance du projet | `/projet/connaissance` | `knowledge-state` | Ce que l'agent sait du projet, Resynchroniser, réindexer une source | — |
| 9 | Réglages du projet | `/projet/reglages` | `doc-sources` | Sources, fiabilité, synchronisation automatique, sources du code | — |
| 10 | Réglages avancés | `/configuration` | `advanced` | Modèle de recherche, navigateur, notifications | — |
| 11 | Agents de code (MCP) | `/configuration` | `agent-access` | La passerelle MCP : commande à recopier dans l'agent de code | — |

Commandes de la bulle : « Précédent » (absent au 1), « Suivant » (« Terminer » au 11),
« Quitter ». Échap quitte. Position : « Arrêt n sur 11 ».

Les textes définitifs sont écrits à l'implémentation, en français, deux phrases au plus par
bulle ; ils ne contiennent ni nom de projet ni donnée de l'utilisateur.
