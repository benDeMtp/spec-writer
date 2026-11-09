# Contrat : agent conversationnel

Ajouts à `specs/002-projets-contexte-mcp/contracts/openapi.yaml`. Aucun texte de message ni
d'extrait n'apparaît dans les erreurs.

## Conversations

- `GET /api/projects/{id}/conversations` → `200 [{ id, name, createdAt, updatedAt }]`, la plus
  récente d'abord.
- `POST /api/projects/{id}/conversations` `{ name? }` → `201` la conversation.
- `PATCH /api/conversations/{cid}` `{ name }` → `200`.
- `DELETE /api/conversations/{cid}` → `204` ; `409 OPERATION_IN_PROGRESS` si un tour tourne.
- `GET /api/conversations/{cid}` → `200 { id, projectId, name, messages: [Message], sessions:
  [{ sessionId, name }] }`.

`Message` : `{ id, role, text, state, historyTruncated, createdAt, steps: [{ query, kinds, hits }],
sources: [{ n, kind, ref, title, url }] }`. `url` est l'adresse Jira, l'adresse de la page, ou
l'adresse de la version dans l'outil (`/sessions/{id}/versions/{n}`) pour une spécification.

## Tour

- `POST /api/conversations/{cid}/messages` `{ text, mentions? }` → `202 { userMessageId,
  turnId }`. Refus :
  - `422` : texte vide ou de plus de 8 000 caractères ;
  - `428` : accord Copilot manquant ;
  - `409 OPERATION_IN_PROGRESS` : un tour tourne déjà ;
  - `503` : Copilot non connecté.
- `GET /api/conversations/{cid}/turns/{turnId}/events` → `text/event-stream`. Les événements
  déjà produits sont d'abord rejoués, puis :
  - `step` `{ ordinal, query, kinds, hits }` ;
  - `delta` `{ text }` ;
  - `sources` `{ sources: [...] }` ;
  - `done` `{ state, historyTruncated }` ;
  - `error` `{ code, userMessage }`.
- `DELETE /api/conversations/{cid}/turns/{turnId}` → `204` : arrêt, texte gardé, message
  `INCOMPLETE`.
- `POST /api/conversations/{cid}/messages/{mid}/retry` → `202 { turnId }`, sur le dernier message
  utilisateur dont la réponse est incomplète.

## Documents joints

Comme 009, en mode conversation :

- `GET`, `POST ?name=` (corps brut) et `DELETE /api/conversations/{cid}/attachments[/{aid}]` ;
- états via `POST /api/projects/{id}/mentions/resolve` avec `conversationId`.

## Rédiger une spécification

- `POST /api/conversations/{cid}/need-draft` → `200 { need, points: [{ theme, question, answer,
  basis, sourceRef }] }` (appel à l'IA, aucun enregistrement).
- `POST /api/conversations/{cid}/specifications` `{ need, points }` → `201 SessionDetail`. Le
  questionnaire contient les points comme questions répondues ; les documents joints sont copiés.
  Refus `422` : besoin invalide, plus de 10 points, point invalide.

## Mentions (007, enrichi)

`POST /api/projects/{id}/mentions/resolve` accepte `conversationId`. Les documents joints de la
conversation donnent des états `ATTACHMENT`.

## Front

- Route `''` : conversation. `/recherche` redirige vers `''`. `/nouveau` : écran du besoin.
- `/sessions` s'intitule « Spécifications » et porte « Nouvelle spécification » et la recherche.
