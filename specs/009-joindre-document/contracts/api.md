# Contrat : joindre un document

Ajouts aux contrats `specs/001-local-spec-assistant/contracts/openapi.yaml` (sessions) et
`specs/002-projets-contexte-mcp/contracts/openapi.yaml` (mentions). Le texte d'un document joint
n'est jamais renvoyé.

## POST /api/attachment-drafts?name=<nom du fichier>

Corps : contenu brut du fichier (`application/octet-stream`, 20 Mo au plus). `201` :

```json
{ "id": "…", "title": "Compte rendu COPIL", "fileName": "CR COPIL.pdf", "format": "PDF", "chars": 18234 }
```

Refus `422` avec `code` : `UNSUPPORTED_FORMAT`, `DOCUMENT_UNREADABLE` (raison : `TOO_LARGE`,
`PROTECTED`, `NO_TEXT`, `UNREADABLE`), `VALIDATION_FAILED` (nom vide ou > 255) ; `413` au-delà de la
taille.

## DELETE /api/attachment-drafts/{id}

`204` ; brouillon oublié.

## POST /api/sessions (enrichi)

`{ "need", "name", "attachmentIds": ["…"] }` (5 au plus) ; brouillon inconnu ou expiré :
`422 VALIDATION_FAILED`.

## GET /api/sessions/{id}/attachments

`200` `[{ "id", "title", "fileName", "format", "chars", "addedAt" }]` dans l'ordre d'ajout.

## POST /api/sessions/{id}/attachments?name=<nom>

Corps brut comme pour un brouillon ; `201` et la vue. `422 ATTACHMENT_LIMIT` (5),
`422 ATTACHMENT_DUPLICATE`, `409 SPECIFICATION_LOCKED`.

## DELETE /api/sessions/{id}/attachments/{attachmentId}

`204` ; `409 SPECIFICATION_LOCKED`.

## POST /api/projects/{id}/mentions/resolve (007, enrichi)

Corps : `{ sessionId?, fields[], attachmentIds?[] }`. Les documents joints (brouillons ou ceux de la
session) donnent chacun un état `{ field: "ATTACHMENT", state, document: { kind: "ATTACHMENT", title,
path: <nom du fichier> }, transmittedShare, betterModel }`, placés dans l'ordre de transmission.

## Front

`sw-attachments` : « Joindre un document », liste (titre, nom, taille, état), « Retirer » ; sur
l'écran du besoin, le questionnaire et la spécification.
