# Data Model: Tickets fermés gardés en cache

Aucune table ni migration : le statut et sa catégorie sont déjà dans `knowledge_item`
(`status`, `status_category`), le repère dans `project.jira_sync_mark` (002, V10).

## Ticket indexé (KnowledgeItem de sorte TICKET, 002)

| Champ | Rôle dans la 003 |
|---|---|
| `statusCategory` | `done` : ticket **fermé** (`closed()`), gardé sans relecture (FR-001, FR-002) |
| `sourceUpdatedAt` | date de modification Jira lue ; montrée avec le statut indexé |
| `indexedAt` | date de la dernière écriture ; jamais utilisée comme durée de vie (FR-007) |
| `contentHash` | inchangé : le vecteur n'est recalculé que s'il change ou si le modèle change (FR-005) |

### États face au cache

```text
            lu fermé                       modifié dans Jira (incrémental)
 absent ─────────────▶ FERMÉ (gardé) ─────────────────────────────▶ relu
   │                      ▲    │ rouvert : relu, devient OUVERT
   │ lu ouvert            │    ▼
   └────────────────▶ OUVERT (relu à chaque indexation complète)
                          │ fermé dans Jira : relu une dernière fois → FERMÉ
 supprimé ou déplacé dans Jira (absent de la liste des clés) : retiré, quel que soit l'état
```

## TicketSummary (nouveau, `application/knowledge`)

Bilan de l'étape Jira d'une indexation (FR-009).

| Champ | Type | Règle |
|---|---|---|
| `read` | int ≥ 0 | tickets lus dans Jira pendant l'indexation |
| `keptClosed` | int ≥ 0 | tickets indexés fermés, encore dans le projet, non relus ; 0 en incrémental |
| `removed` | int ≥ 0 | tickets retirés de l'index (supprimés ou déplacés) |

Absent du `Snapshot` si l'étape Jira n'a pas tourné (Jira non configuré, pas de consentement,
pas de clé de projet). En cas d'erreur Jira, les nombres comptent ce qui a été fait avant
l'erreur.

## Indexations.Snapshot (002)

Gagne `TicketSummary tickets` (nullable).

## Ports

- `IssueTracker.searchOpenOrUpdatedSince(endpoint, projectKey, since, startAt, maxResults)` :
  `TrackedIssuePage`, tickets ouverts ou modifiés depuis `since` (non nul), ordre `updated` croissant.
- `IssueTracker.issuesByKeys(endpoint, projectKey, keys)` : `List<TrackedIssue>`, 50 clés au plus,
  clés au format `[A-Z][A-Z0-9_]+-\d+` (sinon `IllegalArgumentException`) ; une clé inexistante
  est ignorée.
- `KnowledgeIndex.closedTickets(projectId)` : `Set<String>`, clés des tickets indexés fermés.
