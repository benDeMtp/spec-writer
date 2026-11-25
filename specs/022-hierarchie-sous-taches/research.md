# Research: Hiérarchie des tâches et sous-tâches Jira (022)

## R1 — Source de la hiérarchie dans la vue Jira

- **Decision** : le lien `PARENT` de `ticket_link` (côté sous-tâche : `source_key` = sous-tâche,
  `target_key` = parent), écrit par l'indexation de Jira (research.md R29 de la 002).
- **Rationale** : sur l'index réel, 325 liens `PARENT` contre 253 `SUBTASK`. Le côté parent n'est
  pas toujours écrit, alors que chaque sous-tâche connaît son parent. Toutes les sous-tâches de
  l'index réel ont leur parent indexé, sur deux niveaux (story ou story technique, puis
  sous-tâche).
- **Alternatives considered** : le libellé du type (`issue_type` = « Sous-Tâche (AGILE) ») varie
  d'une instance Jira à l'autre (écarté). Les liens `SUBTASK` sont incomplets (écarté). Les liens
  `LINK` (Lier, Gantt, Cloners, Livraison…) ne sont pas de la hiérarchie (hors périmètre).

## R2 — Tri et pagination sur le premier niveau

- **Decision** : la requête de la page filtre les tickets qui ont un parent **indexé**
  (`not exists` sur `ticket_link` `PARENT` joint à `knowledge_item`). Le compte total suit le même
  filtre. Les sous-tâches des tickets de la page sont lues par une seconde requête, sur les clés de
  la page, classées par clé, puis rattachées à leur parent. Chaque ligne du premier niveau porte
  `subtaskCount` et ses `subtasks`, et une sous-tâche orpheline porte `parentKey`.
- **Rationale** : une story et ses sous-tâches restent sur la même page quel que soit le tri
  (SC-002). Deux requêtes indexées (`ticket_link` a sa clé primaire sur `(project_id, source_key,
  …)` et l'index `ticket_link_target`) restent bien sous la seconde sur 3 000 tickets (SC-003).
  Les sous-tâches arrivent avec la page : il n'y a pas d'appel au dépliage, ni d'état de
  chargement (325 sous-tâches au total sur l'index réel, une cinquantaine par page au plus).
- **Alternatives considered** : regrouper côté front les tickets de la page plate sépare un parent
  de ses sous-tâches sur deux pages (écarté). Un appel par dépliage ajoute un état de chargement et
  une route pour quelques lignes (écarté). Une vue arborescente à part ferait doublon avec le
  tableau (écarté).

## R3 — Ordre du tri appliqué au parent

- **Decision** : le tri (date, statut, synchro) s'applique aux seuls tickets du premier niveau,
  sur leurs propres valeurs. Les sous-tâches sont classées par clé, dans l'ordre numérique
  (AURORA-9 avant AURORA-10).
- **Rationale** : c'est le comportement attendu de la spec (FR-004). Remonter une story parce
  qu'une de ses sous-tâches a changé mélangerait deux sens du tri. L'ordre numérique des clés
  évite AURORA-182 avant AURORA-25.
- **Alternatives considered** : trier par la date la plus récente du groupe (écarté : écart à la
  spec, surprend sur le tri par statut).

## R4 — Parent et type d'une tâche de feature (MCP)

- **Decision** : le parent d'une tâche est la clé du ticket lié à la feature
  (`linked_jira_ticket.issue_key`), sous lequel `TaskDesk` crée les sous-tâches et dont
  `ProposeTasks` reprend les sous-tâches existantes. Le type de la sous-tâche (libellé Jira) est
  relu avec le statut et la description à chaque appel (`TaskSync.reread`, `TrackedIssue.issueType`)
  et gardé dans une colonne `spec_task.jira_issue_type` (V34). Jira injoignable, le dernier type lu
  reste. Une tâche pas encore relue n'a pas de type.
- **Rationale** : le parent est connu sans Jira ni index (FR-011, cas d'une sous-tâche créée et
  pas encore indexée). Le type suit la même voie que la description (V33), déjà relue au même
  moment.
- **Alternatives considered** : lire le parent et le type dans l'index (absent pour une sous-tâche
  tout juste créée, écarté). Fixer le type à « sous-tâche » (faux pour le libellé de l'instance,
  écarté).

## R5 — Type et parent du ticket d'une feature

- **Decision** : `get_feature` ajoute `jiraTicket` : `{key, issueType, parentKey}`, lus dans
  l'index (`knowledge_item` chunk 0 et lien `PARENT`). Hors de l'index, `issueType` et `parentKey`
  sont nuls. Le champ `jiraKey` existant reste (FR-014).
- **Rationale** : une feature importée depuis une sous-tâche a son parent dans l'index, puisque
  l'import part de la vue Jira, donc d'un ticket indexé. Aucun appel à Jira de plus dans
  `get_feature`.
- **Alternatives considered** : relire le ticket dans Jira à chaque `get_feature` (un appel de
  plus, et Jira injoignable rendrait le type inconnu, écarté).

## R6 — Consigne de branche

- **Decision** : un texte fixe dans trois endroits.
  1. Les instructions du serveur MCP (`initialize`) : « Une tâche qui est une sous-tâche (parentKey)
     se fait sur la branche de son ticket parent : si une branche est nécessaire, utilisez ou
     créez celle du parent, jamais une branche par sous-tâche. »
  2. Un champ `branch` dans les réponses de `next_task` (outcome TASK) et de `start_task`, pour une
     sous-tâche seulement : « AURORA-182 est une sous-tâche de AURORA-24 : si une branche est
     nécessaire, travaillez sur celle de AURORA-24 (reprenez-la si elle existe) ; toutes les
     sous-tâches de AURORA-24 s'y font. »
  3. La description des outils `next_task` et `start_task` mentionne `parentKey` et `branch`.
- **Rationale** : les instructions du serveur ne sont lues qu'à la connexion. Le rappel dans la
  réponse arrive au moment où l'agent décide de créer une branche, avec la clé du parent. Le nom
  de branche n'est pas imposé (spec).
- **Alternatives considered** : un nom de branche calculé (`feature/AURORA-24`), écarté par la
  spec. Une consigne seulement dans les instructions, trop loin du moment de la décision (écarté).

## R7 — Accessibilité du dépliage

- **Decision** : un bouton dans la cellule « Ticket » du parent, `aria-expanded`, `aria-controls`
  vers les lignes des sous-tâches, libellé « Afficher les 3 sous-tâches de AURORA-24 » ou
  « Masquer… ». Les lignes des sous-tâches sont de vraies lignes du tableau (`tr`), en retrait, et
  leur clé est précédée d'un texte masqué « Sous-tâche de AURORA-24 ». L'état ouvert est gardé en
  mémoire de la page (signal), pas dans l'adresse.
- **Rationale** : WCAG 2.1 AA (constitution I). Le motif « disclosure » est plus simple et mieux
  rendu par les lecteurs d'écran qu'une `treegrid` pour un seul niveau.
- **Alternatives considered** : `role="treegrid"` (navigation fléchée à écrire, écarté pour un
  seul niveau). `details`/`summary` dans une cellule (les sous-tâches ne seraient pas des lignes
  du tableau, écarté).
