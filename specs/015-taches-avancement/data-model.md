# Data model : tâches d'une spécification (015)

Migration **V26__spec_tasks.sql** (V11 n'existe pas, V25 est la dernière). Aucune migration
appliquée n'est modifiée.

## Table `spec_task`

| Colonne | Type | Règle |
|---|---|---|
| `id` | TEXT PK | identifiant de la tâche |
| `session_id` | TEXT NOT NULL | FK `session(id)` ON DELETE CASCADE |
| `position` | INTEGER NOT NULL | ordre, à partir de 0 ; UNIQUE (`session_id`, `position`) |
| `title` | TEXT NOT NULL | 1 à 120 caractères |
| `outcome` | TEXT NOT NULL | résultat vérifiable, 1 à 300 caractères (vide permis seulement pour `origin = JIRA`) |
| `basis_kind` | TEXT NOT NULL | CHECK IN (`MODULE`, `USE_CASE`, `NONE`) |
| `basis_ref` | TEXT | nom du module ou code du cas d'usage ; NULL si `NONE` |
| `origin` | TEXT NOT NULL | CHECK IN (`PROPOSED`, `MANUAL`, `JIRA`) |
| `creation` | TEXT NOT NULL | CHECK IN (`NOT_CREATED`, `CREATED`) |
| `jira_key` | TEXT | UNIQUE ; NOT NULL si `creation = CREATED` |
| `status` | TEXT NOT NULL | CHECK IN (`TODO`, `IN_PROGRESS`, `DONE`, `MISSING`) |
| `jira_status_name` | TEXT | dernier nom de statut lu dans Jira (affichage) |
| `holder_connection` | TEXT | connexion d'agent qui tient la tâche (`<pid>-<début ms>`), NULL sinon |
| `holder_agent` | TEXT | nom déclaré de l'agent (80 caractères, sans caractère de contrôle) |
| `held_since` | TEXT | instant de la prise |
| `pending_target` | TEXT | CHECK IN (`TODO`, `IN_PROGRESS`, `DONE`) ; transition pas encore envoyée à Jira |
| `pending_comment` | INTEGER NOT NULL DEFAULT 0 | 1 si le compte rendu reste à envoyer en commentaire |
| `completion_report` | TEXT | compte rendu de fin, 2 000 caractères au plus |
| `last_read_at` | TEXT | dernière relecture réussie depuis Jira |

## Table `spec_task_dependency`

| Colonne | Type | Règle |
|---|---|---|
| `task_id` | TEXT NOT NULL | FK `spec_task(id)` ON DELETE CASCADE |
| `depends_on_id` | TEXT NOT NULL | FK `spec_task(id)` ON DELETE CASCADE ; même session, position inférieure |

PK (`task_id`, `depends_on_id`).

## Table `spec_task_proposal`

Proposition en cours de relecture (une par session au plus), gardée pour survivre à un rechargement.

| Colonne | Type | Règle |
|---|---|---|
| `session_id` | TEXT PK | FK `session(id)` ON DELETE CASCADE |
| `content` | TEXT NOT NULL | JSON : `[{title, outcome, basisKind, basisRef, dependsOn: [index]}]` |
| `created_at` | TEXT NOT NULL | |

Supprimée à la validation ou à l'abandon.

## Colonne ajoutée à `session`

`task_tracking TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (task_tracking IN ('ACTIVE','SUSPENDED'))`.

## Table `agent_access_log` (reconstruite)

Reconstruction SQLite (créer `agent_access_log_new`, copier, supprimer, renommer, recréer l'index
`agent_access_log_at`) :
- `operation` CHECK étendu : `LIST_PROJECTS`, `LIST_FEATURES`, `GET_FEATURE`, `SET_STATUS`,
  `SEARCH_KNOWLEDGE`, `NEXT_TASK`, `START_TASK`, `COMPLETE_TASK`, `RELEASE_TASK` ;
- colonne `task_key TEXT` (clé Jira de la tâche, NULL pour les autres opérations).

`RELEASE_TASK` n'est inscrite que lorsqu'une tâche tenue par un agent est libérée par
l'utilisateur (`agent_name` = l'agent qui la tenait).

## Domaine

- `domain/task/SpecTask` (record) : champs ci-dessus ; `TaskStatus { TODO, IN_PROGRESS, DONE,
  MISSING }` ; `Basis(kind, ref)` ; `Holder(connection, agentName, since)` ; `Pending(target,
  comment)`.
- `domain/task/TaskProgress` : `done()`, `total()` (hors `MISSING`), `status(tasks)` (R7),
  `next(tasks, connection, alive)` (R4, FR-010) :
  1. une tâche `IN_PROGRESS` tenue par `connection` ;
  2. sinon la première (par `position`) tâche `IN_PROGRESS` dont la connexion est fermée ;
  3. sinon la première tâche `TODO` (créée) dont tous les préalables sont `DONE` ;
  4. sinon `NONE_LEFT` si toutes sont `DONE`, ou `ALL_HELD` si les restantes sont tenues par des
     connexions ouvertes ou bloquées par leurs préalables.
- `domain/task/TaskProposal` (record) : liste de `ProposedTask(title, outcome, basis, dependsOn)`,
  validation des bornes (R8).
- `domain/task/StatusCategory` : `NEW`, `IN_PROGRESS`, `DONE` ↔ clés Jira `new`, `indeterminate`,
  `done`.
- `domain/specification/UserStory(role, capability, benefit, Set<StoryPart> assumed)` et
  `SpecificationContent.story` (nullable), `SpecificationContent.splitProposal` (liste, vide par
  défaut) — R9.
- `domain/session/ImplementationStatus.Actor.TASKS` — R7.
- `domain/agent/AgentOperation` : + `NEXT_TASK`, `START_TASK`, `COMPLETE_TASK`, `RELEASE_TASK`.

## Transitions d'une tâche

```text
            start_task (agent)               complete_task (agent)
  TODO ───────────────────────────▶ IN_PROGRESS ─────────────────────────▶ DONE
   ▲                                   │  ▲                                  │
   └──── libérer (utilisateur) ────────┘  └──── relecture Jira (rouverte) ───┘
  toute catégorie lue dans Jira s'impose à la relecture ; clé absente → MISSING
```

- `start_task` : depuis `TODO` (créée, préalables `DONE`), ou depuis `IN_PROGRESS` tenue par une
  connexion fermée (reprise) ou par la même connexion (sans effet).
- `complete_task` : depuis `IN_PROGRESS` tenue par la connexion appelante ; depuis `DONE`, réponse
  « déjà terminée » sans erreur.
- `libérer` : depuis `IN_PROGRESS` ; vers `TODO`, connexion effacée, transition Jira catégorie `new`.
- Chaque changement pose la connexion (`start`) ou l'efface (`DONE`, `TODO`), puis recalcule le
  statut de la spécification si `task_tracking = ACTIVE` (R7).
