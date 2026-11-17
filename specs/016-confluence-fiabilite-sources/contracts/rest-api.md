# Contrat REST (016)

Mêmes conventions que l'API existante : `Origin` vérifiée, cookie de session, erreurs au format
`Problem {type, title, status, code, userMessage, action}`. Aucun jeton n'apparaît jamais dans une
réponse.

## Serveur Confluence (Configuration)

| Méthode | Chemin | Corps | Réponse |
|---|---|---|---|
| GET | `/api/settings/confluence` | — | `200 {enabled, serverUrl, caCertificatePath, insecureHttp, serverVersion, lastTest {at, result}, tokenSet, secretPersistence}` ; `enabled=false` sans serveur |
| PUT | `/api/settings/confluence` | `{serverUrl, caCertificatePath?, acceptInsecureHttp, token?}` | `200` réglages ; `token` absent : jeton gardé |
| POST | `/api/settings/confluence/test` | — | `200 {result, account?, serverVersion?, message}` ; `result` ∈ `OK`, `UNREACHABLE`, `UNAUTHORIZED`, `TLS_ERROR`, `NOT_CONFLUENCE` |
| DELETE | `/api/settings/confluence` | — | `204` ; jeton retiré du trousseau ; les racines des projets restent, en erreur jusqu'à un nouveau serveur |

## Racines Confluence et niveaux (réglages du projet)

Les racines passent par les routes existantes des sources de documentation, avec `kind:
"CONFLUENCE"` :

| Méthode | Chemin | Corps | Réponse |
|---|---|---|---|
| POST | `/api/projects/{id}/doc-sources/preview` | `{kind: "CONFLUENCE", location}` | `200 {pages, title, problem?}` ; `problem` ∈ `CONFLUENCE_NOT_CONFIGURED`, `NOT_A_PAGE`, `OTHER_HOST`, `ACCESS_DENIED`, `UNREACHABLE`, `TOO_LARGE` |
| POST | `/api/projects/{id}/doc-sources` | `{kind: "CONFLUENCE", location, reliability?}` | `201` source (avec `title`, `pageId`, `reliability`) ; `422 VALIDATION_FAILED` avec la raison de l'aperçu |
| PUT | `/api/projects/{id}/doc-sources/{sourceId}/reliability` | `{reliability: "REFERENCE" \| "INTERPRETATION" \| "TO_CONFIRM" \| null}` | `200` source ; `null` : niveau de Confluence ; `422` pour une source qui n'est pas Confluence |
| GET | `/api/projects/{id}/reliability` | — | `200 {levels: {JIRA, ANTORA, DOCUMENTS, CONFLUENCE, ARCHITECTURE, SPECIFICATIONS}, defaults: {…}}` |
| PUT | `/api/projects/{id}/reliability` | `{origin, level}` | `200` même forme ; un niveau égal au défaut efface le réglage |

## Recherche d'essai et étapes de conversation

Les résultats (`/api/projects/{id}/knowledge/search` et les étapes de recherche d'un tour, SSE)
gagnent `origin`, `updatedAt` (ISO 8601 ou `null`) et `level` (ou `null` pour la fiche générale
et les documents joints). Les messages enregistrés avant 016 renvoient ces champs à `null`.

## Spécification

`SpecificationView` gagne `sourceDeviations: [{reference: {ref, origin, title, updatedAt},
interpretation: {…}, difference}]` (vide sans écart, et pour les versions antérieures).
