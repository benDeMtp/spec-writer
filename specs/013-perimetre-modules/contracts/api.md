# Contrat : périmètre des modules touchés (API)

Ce document liste les ajouts par rapport à `specs/002-projets-contexte-mcp/contracts/openapi.yaml`
et aux contrats des features 010 à 012. Toutes les routes exigent le cookie de session. Les
erreurs suivent le format `{ code, userMessage, action? }`.

La passerelle MCP des agents de code ne change pas.

## Racines de sources

Toutes les routes sont sous `/api/projects/{id}/source-roots`.

| Méthode | Chemin | Corps | Réponse |
|---|---|---|---|
| `GET` | `` | | `200 SourceRootView[]` |
| `POST` | `` | `{ location, exclusions?: string[] }` | `201 SourceRootView` |
| `PUT` | `/{rootId}/exclusions` | `{ exclusions: string[] }` | `200 SourceRootView` |
| `DELETE` | `/{rootId}` | | `204` |
| `POST` | `/preview` | `{ location, exclusions?: string[] }` | `200 SourceRootPreview` |

```text
SourceRootView = { id, location, label, exclusions: string[], createdAt,
                   state: "OK" | "MISSING" | "UNREADABLE" }   // évalué à la lecture
SourceRootPreview = { modules: [{ name, path, kind }], moduleCount,
                      retainedFiles, excludedFiles, sensitiveFiles, ignoredByGit,
                      limitReached }
```

- `ProjectView` (`GET /api/projects/{id}`) reçoit un champ `sourceRoots: SourceRootView[]`.
- L'aperçu ne lit que les descripteurs : `pom.xml`, `package.json`, `Chart.yaml`. Les modules
  sont triés par chemin, et l'aperçu en renvoie 500 au plus. `moduleCount` donne le total exact.

**Erreurs** :
- `422 VALIDATION_FAILED` :
  - chemin non absolu, absent, pas un dossier, ou illisible (le message dit lequel) ;
  - motif invalide (le motif est cité) ;
  - racine déjà déclarée ;
  - racine imbriquée dans une autre racine du projet, ou qui en contient une ;
  - plus de 10 racines.
- `404 NOT_FOUND` : projet ou racine inconnus.

Les chemins et les motifs ne figurent jamais dans le journal.

## Conversation

### Plan (interne, schéma `CONVERSE_PLAN`)

```json
{ "action": "SEARCH | PERIMETER | ANSWER", "query": "string", "kinds": ["..."],
  "terms": ["string (2..100)", "… (≤10)"] }
```

### Qualification (interne, schéma `PERIMETER_QUALIFICATION`, prompt `qualify-perimeter`)

```json
{ "modules": [ { "id": "<rootId>/<path>",
                 "qualification": "IMPACTED | TO_CHECK | NOT_IMPACTED",
                 "justification": "string ≤300" } ] }
```

### Flux SSE : `GET /api/conversations/{cid}/turns/{turnId}/events`

Il reçoit un nouvel événement, `perimeter`. Il est publié une première fois après le recensement,
puis une seconde fois, avec le même `ordinal`, après la qualification.

```text
event: perimeter
data: PerimeterView
```

```text
PerimeterView = {
  ordinal, terms: string[], rejectedTerms: [{ term, reason }],
  roots: [{ rootId, label, state: "SCANNED" | "MISSING" | "UNREADABLE" }],
  state: "COMPLETE" | "INCOMPLETE",
  incompleteReason: null | "MAX_FILES" | "MAX_DURATION" | "ROOT_MISSING" | "MAX_MODULES" | "CANCELLED",
  filesScanned, filesSkipped, skippedExamples: string[], durationMs,
  qualified: boolean,
  summary: { impacted, toCheck, notImpacted, unqualified },
  modules: [{
    id, rootLabel, path, name, kind: "MAVEN" | "NPM" | "HELM" | "ROOT",
    occurrences, isNew,
    qualification: "IMPACTED" | "TO_CHECK" | "NOT_IMPACTED" | "UNQUALIFIED",
    justification: string | null,
    reasons: [
      { type: "TERM", term, count } |
      { type: "DEPENDENCY", chain: [{ id, name }] } |
      { type: "AGGREGATOR", of: { id, name } } |
      { type: "ARCHITECTURE", element, related, description, technology }
    ],
    architecture: { element: string } | null,      // null : absent du modèle
    samples: [{ path, line, term, excerpt }]         // ≤ 20 par terme
  }],
  architectureOnly: [{ element, related, description, technology }],
  unavailable: null | "NO_ROOTS"                     // US2 scénario 5
}
```

- L'événement `sources` peut contenir des sources `{ kind: "MODULE", ref: "module:<id>", title,
  n }`, citées `[n]` dans la réponse.
- `GET /api/conversations/{cid}` : chaque message `AGENT` reçoit `perimeters: PerimeterView[]`,
  dans l'ordre des ordinaux.
- L'arrêt d'un tour (`DELETE /api/conversations/{cid}/turns/{turnId}`, existant) arrête
  l'analyse en cours. Elle est alors enregistrée `INCOMPLETE (CANCELLED)`.

### Brouillon et création de la spécification

- `POST /api/conversations/{cid}/need-draft` est inchangé. Sa réponse reçoit `perimeter:
  { moduleCount, ordinal } | null`, qui annonce que la spécification reprendra la dernière
  analyse.
- `POST /api/conversations/{cid}/specifications` est inchangé. La session reçoit l'instantané
  de la dernière analyse.

## Spécification

- Le rendu Markdown (écran, export) reçoit la rubrique `## Modules touchés` (EN :
  `## Affected modules`, ancre `perimeter`) quand la session a un instantané.
- `GET /api/sessions/{id}/versions/{n}` est inchangé : la rubrique fait partie du Markdown
  rendu.
- Les hypothèses `TOOL_INFERENCE` ajoutées suivent le format existant.
