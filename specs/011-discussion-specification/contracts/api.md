# Contrat : discuter d'une spécification avec l'agent

Ajouts et changements par rapport à `specs/010-agent-conversationnel/contracts/api.md`. Aucun texte
de message, de spécification ni de proposition n'apparaît dans les erreurs.

## Discussion d'une spécification

- `GET /api/sessions/{id}/discussion` → `200 { conversationId, specificationName, version }` pour
  la discussion suivie, ou `204` s'il n'y en a pas encore.
- `POST /api/sessions/{id}/discussion` `{ fresh?: boolean }` → `201 { conversationId,
  specificationName, version }`.
  - Sans `fresh`, la discussion suivie est rendue, ou créée si elle manque.
  - Avec `fresh: true`, une nouvelle discussion devient la discussion suivie, et l'ancienne reste
    dans la liste.
  - `409 SPECIFICATION_LOCKED` : spécification implémentée.
  - `422` : spécification sans version (« Générez d'abord une spécification. »).
- Messages, nouvel essai, arrêt, flux SSE, documents joints et mentions passent par les routes de
  la 010 (`/api/conversations/{cid}/…`).
  - Pour une discussion, l'envoi renvoie `409 SPECIFICATION_LOCKED` si la spécification est
    verrouillée.
  - Chaque message de l'agent porte `specificationVersion`.
- `GET /api/sessions/{id}/discussions/count` → `200 { count }`, toutes les discussions rattachées,
  pour la confirmation de suppression.

## Proposition d'une nouvelle version

- `POST /api/conversations/{cid}/proposals/candidates` → `200 { changes: [{ text, origin }] }`
  (feature 013, T055) : les changements de la spécification que la discussion avance, à cocher
  avant la rédaction. `origin` vaut `USER` (dit ou confirmé par la personne) ou `AGENT` (proposé par
  l'agent seul). Liste vide si la discussion n'en avance aucun. Rien n'est gardé. Mêmes refus que
  ci-dessous.
- `POST /api/conversations/{cid}/proposals` `{ changes?: string[] }` → `202 { turnId, message }`.
  La conversation doit être la discussion d'une spécification existante. Les changements retenus
  (au plus 20, de 1 000 caractères au plus chacun) entrent d'abord dans la discussion, comme un
  message de la personne « Changements retenus pour la nouvelle version : - … », rendu dans
  `message` (nul sans changement). Sans corps, la discussion seule fait foi.
  - `422 VALIDATION_FAILED` : discussion vide, ou trop de changements, ou un changement trop long.
  - `409 OPERATION_IN_PROGRESS` : une réponse ou une proposition est en cours.
  - `409 SPECIFICATION_LOCKED`.
  - `428` : accord Copilot manquant.
  - `503` : Copilot non connecté.
- `GET /api/conversations/{cid}/turns/{turnId}/events` (flux de la 010) envoie, pour une
  proposition :
  - `started` `{ at }` ;
  - `stage` `{ stage: "REVISE" }` ;
  - `reasoning` `{ text }` ;
  - `done` `{ state, durationMs, proposalId }` quand une version est proposée ;
  - `done` `{ state, durationMs, unchanged: true, reason }` quand la discussion n'a établi aucun
    changement ;
  - `error` `{ code, userMessage }`, par exemple `LLM_INVALID_OUTPUT` après 3 tentatives.
- `GET /api/sessions/{id}/proposals/{proposalId}` → `200 ProposalView` (`baseVersion`, `proposed`,
  `unifiedDiff`, `origin: "DISCUSSION"`, `conversationId`). `404` si elle a expiré (30 min) ou a
  été prise.
- `POST /api/sessions/{id}/ai-edits/{proposalId}/accept` (inchangé) → `201` la nouvelle version.
  - Pour une proposition `DISCUSSION`, la version est liée à la conversation.
  - `409 SPECIFICATION_LOCKED` si la spécification a été verrouillée entre-temps.
- `DELETE /api/sessions/{id}/proposals/{proposalId}` → `204` : abandon.

## Versions

- `VersionSummary` et `SpecificationView` portent `origin` : `GENERATED`, `AI_EDIT`, `JIRA_IMPORT`,
  `CONFLICT_MERGE` ou `DISCUSSION`, et `conversationId` (`null` sauf pour `DISCUSSION` dont la
  conversation existe encore).

## Suppression d'une spécification

- `DELETE /api/sessions/{id}?discussions=delete|keep` (défaut `delete`) → `204`.
  - `delete` : les discussions rattachées sont supprimées.
  - `keep` : elles restent, sans spécification.
  - `409 OPERATION_IN_PROGRESS` : une réponse ou une proposition est en cours.

## Conversations (010, champ ajouté)

- `GET /api/projects/{id}/conversations` : chaque conversation porte `specification: { sessionId,
  name } | null`. `sessionId` vaut `null` si la spécification a été supprimée.
- `GET /api/conversations/{cid}` : mêmes champs, plus `specificationVersion` sur chaque message.

## Jira

- `POST /api/jira/issues/{key}/import` → `201` la session créée, ou **`200` la session existante**
  si le ticket est déjà lié (au lieu de `409 ALREADY_LINKED`).

## Retiré

- `POST /api/sessions/{id}/ai-edits` (consigne en un coup) et l'opération `edit-specification`.
  - La fusion d'un conflit Jira (`PUT /api/sessions/{id}/jira-link/push`, résolution `AI_MERGE`)
    est inchangée.
  - Sa proposition s'accepte toujours par `…/ai-edits/{proposalId}/accept`.
