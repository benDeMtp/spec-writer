# Contrat REST : séances et « à reporter »

Mêmes règles que le reste de l'API : cookie de session (403 sinon), `Origin` vérifié, corps JSON
borné, erreurs en `application/problem+json` avec un message métier (`Problem`). Les identifiants
sont des UUID.

## Points à traiter (page « Spécifications »)

`GET /api/sessions` : chaque `SessionSummary` gagne

```json
"pending": { "assumptions": 3, "openQuestions": 2, "sourceDeviations": 1 }
```

`null` pour une spécification sans version ou illisible (FR-022, FR-025).

## Séances

| Méthode et chemin | Corps | Réponse |
|---|---|---|
| `GET /api/interviews?state=PREPARED` | | séances du projet courant dans cet état : `InterviewSummary[]` |
| `POST /api/interviews` | `{ "specs": [{ "sessionId", "excluded": ["H-02"] }], "participants": ["Julie M."] }` | 201, `InterviewDetail` en `PREPARED` |
| `PUT /api/interviews/{id}` | même corps | 200 ; 409 si la séance n'est plus préparée |
| `DELETE /api/interviews/{id}` | | 204 ; 409 si en cours ou terminée |
| `POST /api/interviews/{id}/start` | `{ "participants"?: [...] }` | 200, `InterviewDetail` en `IN_PROGRESS` ; 409 `INTERVIEW_BUSY` si une spécification a déjà une séance en cours (le message la nomme) |
| `POST /api/sessions/{sid}/interviews` | `{ "participants"?: [...] }` | 201 : « Faire le point », séance d'une spécification, directement en cours ; 409 comme ci-dessus, ou `OPERATION_IN_PROGRESS` pendant une génération ou une révision |
| `GET /api/sessions/{sid}/interviews` | | séances de cette spécification (en cours en tête, puis terminées), pour « Reprendre » et les comptes rendus passés |
| `GET /api/interviews/{id}` | | `InterviewDetail` |
| `PUT /api/interviews/{id}/cursor` | `{ "sessionId", "element", "step"?, "alternative"? }` | 204 |
| `POST /api/interviews/{id}/points` | `PointInput` | 201, `PointView` |
| `PUT /api/interviews/{id}/points/{pid}` | `PointInput` | 200, `PointView` |
| `DELETE /api/interviews/{id}/points/{pid}` | | 204 (remarques et questions seulement) |
| `POST /api/interviews/{id}/finish` | | 200, `InterviewDetail` en `FINISHED` |
| `GET /api/interviews/{id}/report` | | `{ "html": "…", "text": "…" }` (FR-010, FR-011) |
| `POST /api/interviews/{id}/specs/{sid}/revision` | | 202 `{ "turnId", "conversationId" }` : tour de révision de 011 ; le flux et la relecture sont ceux de la discussion |

Les points ne se modifient que pendant la séance (409 sinon). Après « Terminer », seule la révision
reste possible.

### InterviewDetail

```json
{
  "id": "…", "state": "IN_PROGRESS", "participants": ["Julie M.", "Paul D."],
  "startedAt": "…", "finishedAt": null,
  "cursor": { "sessionId": "…", "element": "UC-02", "step": 3, "alternative": false },
  "specs": [{
    "sessionId": "…", "name": "Émission des avoirs", "rank": 1,
    "version": 4, "latestVersion": 4,
    "excluded": ["H-02"], "appliedVersion": null,
    "specification": { "…": "SpecificationView de la version relue (anchors, openQuestions inclus)" },
    "points": [ "PointView…" ],
    "revisionAvailable": true, "revisionUnavailableReason": null
  }]
}
```

`latestVersion > version` : une version plus récente existe (R11). `revisionAvailable` est faux
sans consentement Copilot, avec la raison (R12).

### PointInput et PointView

```json
{
  "origin": "ASSUMPTION | SOURCE_DEVIATION | OPEN_QUESTION | REMARK | QUESTION",
  "originCode": "H-03",
  "sessionId": "…",
  "anchor": { "element": "UC-02", "step": 4, "alternative": false },
  "text": "Plafonné au montant TTC facturé.",
  "outcome": "CONFIRMED | CHANGE | REMOVE | DEFERRED | null",
  "owner": "Paul D.", "due": "2026-11-27",
  "source": { "kind": "DOC_PAGE", "ref": "https://wiki…/4512" }
}
```

`PointView` ajoute `id`, `createdAt`, `updatedAt`. Validation : 422 avec un message métier pour
une ancre inconnue de la version relue, un texte vide là où il est obligatoire, un texte trop
long, ou une source absente de la connaissance du projet.

## « À reporter »

| Méthode et chemin | Réponse |
|---|---|
| `GET /api/sessions/{sid}/source-updates` | `SourceUpdateView[]` ouverts (et « source modifiée ») de la spécification |
| `POST /api/sessions/{sid}/source-updates/{uid}/close` | 204 |

```json
{ "id": "…", "source": { "kind": "DOC_PAGE", "ref": "…", "title": "Règles de facturation", "link": "https://…" },
  "text": "Un avoir partiel est plafonné au montant TTC facturé.",
  "state": "OPEN | SOURCE_CHANGED", "interviewId": "…", "decidedAt": "…" }
```

Aucune route n'écrit dans Confluence, OneDrive, SharePoint ou Jira (FR-030).

## Révision (011, inchangé)

La relecture et l'application passent par les routes existantes de la discussion
(`DiscussionProposalResource`). À l'application d'une proposition issue d'une séance, le serveur
enregistre `applied_version` et crée les « à reporter » ; la réponse est inchangée.
