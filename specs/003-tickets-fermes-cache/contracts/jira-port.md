# Contrat : requêtes Jira de l'indexation (port IssueTracker)

Les requêtes sont construites par `JqlBuilder` ; aucune donnée saisie n'y entre. Clé de projet et
clés de ticket sont validées avant (`[A-Z][A-Z0-9_]+` et `[A-Z][A-Z0-9_]+-\d+`).

| Usage | Méthode | Requête `GET /rest/api/2/search` | Champs |
|---|---|---|---|
| Complète, sans repère (relecture unique) | `searchUpdatedSince(…, null, …)` (inchangé) | `project = X ORDER BY updated ASC` | `TRACKED_FIELDS` |
| Complète, avec repère | `searchOpenOrUpdatedSince` (nouveau) | `project = X AND (statusCategory != Done OR updated >= "yyyy/MM/dd HH:mm") ORDER BY updated ASC` | `TRACKED_FIELDS` |
| Incrémentale | `searchUpdatedSince(…, repère, …)` (inchangé) | `project = X AND updated >= "…" ORDER BY updated ASC` | `TRACKED_FIELDS` |
| Clés du projet | `listKeys` (inchangé) | `project = X ORDER BY key ASC` | `key` |
| Lecture par clé | `issuesByKeys` (nouveau) | `project = X AND key in (K1, …, K50) ORDER BY key ASC`, `validateQuery=warn`, `maxResults=50` | `TRACKED_FIELDS` |
| Statut d'un ticket montré | `issueStatus` (inchangé) | `GET issue/{key}?fields=status,updated` : **plus appelé pour un ticket indexé fermé** | |

`TRACKED_FIELDS` = `summary,description,status,issuetype,updated,parent,subtasks,issuelinks` (002).
La marge du repère (`SINCE_MARGIN`) est celle de la 002.

## Erreurs

- `issuesByKeys` : avertissements de validation ignorés (clé supprimée entre la liste et la
  lecture) ; toute autre erreur suit `IssueTrackerException` comme les autres lectures.
- Une erreur Jira pendant l'étape Jira : comme aujourd'hui, erreur `JIRA` dans l'état de
  l'indexation, repère avancé jusqu'au dernier ticket lu à l'étape 1, aucun retrait.

## Écritures (FR-010)

Inchangées : création de ticket, lien, mise à jour de description, import et détection de conflit
lisent ou écrivent Jira sans tenir compte du cache.
