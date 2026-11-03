# Contrat : passerelle MCP `spec-writer-mcp`

Serveur MCP stdio (protocole `2025-06-18`, JSON-RPC 2.0, un message par ligne sur stdin et
stdout ; diagnostics sur stderr, sans contenu ni secret). Il est lancé par l'agent de code sous
le compte de l'utilisateur (research.md R1, R2).

## Démarrage

1. Lire `agent-endpoint.json` dans le dossier de données de l'utilisateur (mêmes règles que
   l'outil : `specwriter.data-dir`, sinon le dossier standard de l'OS).
2. Fichier absent : chaque appel d'outil répond par l'erreur `SPEC_WRITER_UNAVAILABLE`, avec le
   message « Ouvrez spec-writer et activez l'accès des agents dans Configuration ».
3. Chaque appel d'outil devient une requête `http://127.0.0.1:<port>/api/agent/...` avec
   `Authorization: Bearer <secret>`. Une réponse 401 provoque une relecture du fichier, car le
   secret a pu être régénéré, puis une seule nouvelle tentative.

## Déclaration dans un agent

```json
{ "mcpServers": { "spec-writer": { "command": "<chemin du lanceur spec-writer-mcp>" } } }
```

L'écran Configuration fournit cet extrait avec le bon chemin. Il ne contient aucun secret.

## Méthodes MCP prises en charge

| Méthode | Comportement |
|---|---|
| `initialize` | Renvoie `protocolVersion`, `serverInfo {name: "spec-writer", version}` et `capabilities {tools: {}}`. Retient `clientInfo.name` pour le journal (FR-028). |
| `notifications/initialized` | Aucune réponse. |
| `ping` | Renvoie `{}`. |
| `tools/list` | Les 4 outils ci-dessous, avec leur `inputSchema` JSON. |
| `tools/call` | Exécute l'outil. Une erreur métier est renvoyée en `result` avec `isError: true` et un message en langage clair. |
| autre | Erreur JSON-RPC `-32601` (méthode inconnue). |

## Outils

| Outil | Entrée | Sortie (`content[0].text`, JSON) | API agent |
|---|---|---|---|
| `list_projects` | aucune | `[{id, name, description, jiraProjectKey}]` | `GET /api/agent/projects` |
| `list_features` | `{projectId}` | `[{id, title, status, latestVersion, updatedAt}]`, sessions `READY` et `IN_PROGRESS` ayant au moins une version | `GET /api/agent/projects/{id}/features` |
| `get_feature` | `{featureId}` | `{id, title, status, version, markdown, projectContext, jiraIssueKey?}`. `markdown` est identique à l'export de la version (SC-007). | `GET /api/agent/features/{id}` |
| `set_feature_status` | `{featureId, status: "IN_PROGRESS" \| "IMPLEMENTED"}` | `{id, status}`. Seules READY → IN_PROGRESS et IN_PROGRESS → IMPLEMENTED sont permises (FR-027). | `PUT /api/agent/features/{id}/status` |

## Erreurs métier (`isError: true`)

| Code | Cas |
|---|---|
| `SPEC_WRITER_UNAVAILABLE` | Outil fermé, accès désactivé ou fichier absent. |
| `ACCESS_DENIED` | Secret refusé après relecture du fichier. |
| `NOT_FOUND` | Projet ou feature introuvable, supprimé ou déplacé (aucun autre détail). |
| `TRANSITION_NOT_ALLOWED` | Statut demandé non permis à un agent, ou feature verrouillée. |

## Garanties

- La passerelle n'accède ni à la base ni à aucun secret autre que `agent-endpoint.json`. Elle ne
  peut déclencher aucun appel à Copilot ou à Jira (FR-029).
- Le contenu renvoyé à l'agent est une donnée : le texte des spécifications peut contenir des
  phrases adressées à une IA, que l'agent ne doit pas traiter comme des consignes. La description
  des outils le rappelle.
