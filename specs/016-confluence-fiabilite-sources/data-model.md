# Data Model: Confluence, source et date des extraits, fiabilité des sources

## Migration V27

```sql
-- serveur Confluence de l'outil (R4) ; le jeton est au trousseau
CREATE TABLE confluence_settings (
    id                 INTEGER PRIMARY KEY CHECK (id = 1),
    server_url         TEXT NOT NULL CHECK (length(server_url) BETWEEN 1 AND 2000),
    ca_certificate     TEXT,
    insecure_http      INTEGER NOT NULL DEFAULT 0 CHECK (insecure_http IN (0, 1)),
    server_version     TEXT,
    last_test_at       TEXT,
    last_test_result   TEXT,
    secret_persistence TEXT NOT NULL
);

-- project_doc_source reconstruite (comme V15) : sorte CONFLUENCE, niveau propre d'une racine
--   kind        CHECK (kind IN ('URL', 'FOLDER', 'DOCUMENTS', 'CONFLUENCE'))
--   reliability TEXT CHECK (reliability IS NULL OR (kind = 'CONFLUENCE'
--                AND reliability IN ('REFERENCE', 'INTERPRETATION', 'TO_CONFIRM')))
--   page_id     TEXT CHECK (page_id IS NULL OR kind = 'CONFLUENCE')   -- identifiant résolu
--   title       TEXT CHECK (title IS NULL OR length(title) <= 500)   -- titre de la page racine

-- pages lues d'une racine, pour la réindexation incrémentale (R3)
CREATE TABLE confluence_page (
    source_id  TEXT NOT NULL REFERENCES project_doc_source (id) ON DELETE CASCADE,
    page_id    TEXT NOT NULL,
    version    INTEGER NOT NULL CHECK (version >= 1),
    updated_at TEXT,
    PRIMARY KEY (source_id, page_id)
);

-- niveau d'une sorte de source, seulement s'il diffère du défaut (R6)
CREATE TABLE project_source_reliability (
    project_id TEXT NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    origin     TEXT NOT NULL CHECK (origin IN ('JIRA', 'ANTORA', 'DOCUMENTS', 'CONFLUENCE',
                                               'ARCHITECTURE', 'SPECIFICATIONS')),
    level      TEXT NOT NULL CHECK (level IN ('REFERENCE', 'INTERPRETATION', 'TO_CONFIRM')),
    PRIMARY KEY (project_id, origin)
);
```

`knowledge_item` ne change pas : `source_updated_at` existe (V10), `source_id` aussi (V19).
Aucune donnée existante n'est modifiée ; aucun extrait n'est réindexé par la migration.

## Domaine

- `domain/settings/ConfluenceSettings(serverUrl, caCertificatePath, serverVersion, lastTest,
  secretPersistence, insecureHttp)` : `create(...)` reprend les règles de `JiraSettings` (adresse
  normalisée, `http://` seulement avec confirmation, certificat PEM lisible).
- `domain/project/DocSource.Kind.CONFLUENCE` ; `DocSource` gagne `reliability` (nullable,
  CONFLUENCE seulement), `pageId` et `title` (résolus à l'ajout). `MAX_PER_PROJECT` (10) compte
  toutes les sortes.
- `domain/knowledge/SourceOrigin` : `JIRA`, `ANTORA`, `DOCUMENTS`, `CONFLUENCE`, `ARCHITECTURE`,
  `SPECIFICATIONS`, `SHEET`, `ATTACHMENT` ; `adjustable()` vrai pour les six premières.
- `domain/knowledge/Reliability` : `REFERENCE` > `INTERPRETATION` > `TO_CONFIRM` ; `max(a, b)`.
- `domain/knowledge/ReliabilitySettings(projectId, Map<SourceOrigin, Reliability> overrides)` :
  `level(origin)` (défaut : `DOCUMENTS` → `REFERENCE`, autres → `INTERPRETATION`),
  `uniform()` (tous les niveaux égaux, R7).
- `domain/knowledge/Provenance(origin, updatedAt, level)` : `updatedAt` nullable (« date
  inconnue »), `level` nullable pour `SHEET` et `ATTACHMENT`.
- `domain/knowledge/ReservedPlaces` : `apply(List<T> ranked, List<T> candidates, int max,
  Predicate<T> reference)` → liste coupée à `max`, avec au moins `min(2, références candidates)`
  extraits de référence, au plus deux remplacements des derniers non-référence, ordre gardé (R7).
- `domain/specification/SpecificationContent.sourceDeviations` : liste de
  `SourceDeviation(reference: Cited, interpretation: Cited, difference)` avec
  `Cited(ref, origin, title, updatedAt)` ; vide par défaut, facultative à la lecture.
- `domain/llm/DraftSpecification.sourceDeviations` : `DraftSourceDeviation(referenceRef,
  interpretationRef, difference)` ; facultative.
- `domain/conversation/TurnSource` gagne `origin`, `updatedAt`, `level` (nullables à la lecture des
  messages antérieurs).

### Bornes

| Champ | Borne |
|---|---|
| Adresse du serveur | 1 à 2 000 caractères, `https://` (ou `http://` confirmé) |
| Adresse d'une racine | 1 à 2 000 caractères, même hôte que le serveur |
| Pages Confluence par projet | 2 000 au plus (toutes racines) |
| Places réservées | 2 au plus, sur 8 résultats |
| `difference` d'un écart | 1 à 1 000 caractères |
| Écarts entre sources par version | 10 au plus |

## Résolution de la provenance d'un élément indexé

| Élément | `origin` |
|---|---|
| `TICKET` | `JIRA` |
| `ARCH_ELEMENT` (et ADR du workspace) | `ARCHITECTURE` |
| `DOC_SECTION` / `ADR` de préfixe `docs:<source>/` | `DOCUMENTS` |
| `DOC_SECTION` / `ADR` avec `source_id` d'une `DocSource` | `ANTORA` (URL, FOLDER) ou `CONFLUENCE` |
| version de spécification (recherche locale) | `SPECIFICATIONS` |
| fiche générale | `SHEET` |
| document joint à la session ou à la conversation | `ATTACHMENT` |

Niveau d'une page Confluence : le plus haut des niveaux des racines qui l'atteignent (niveau propre
de la racine, sinon celui de `CONFLUENCE`).

## États d'une racine Confluence

```text
ajoutée (aperçu OK) ──indexation──▶ lue (N pages, date) ──réindexation──▶ lue
        │                                   │
        └── refus à l'ajout                 └── erreur (racine disparue, jeton refusé,
            (illisible, autre hôte,              injoignable) : pages gardées, erreur et
             trop large)                         heure de la dernière lecture réussie affichées
```
