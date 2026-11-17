-- Feature 016 (data-model.md) : serveur Confluence, racines Confluence (sorte CONFLUENCE des sources
-- de documentation), pages lues pour la réindexation incrémentale, niveaux de fiabilité, provenance
-- des sources citées par l'agent. Aucune donnée existante n'est modifiée ni réindexée.

-- Serveur Confluence de l'outil (R4) ; le jeton est au trousseau, jamais ici.
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

-- project_doc_source reconstruite (comme V15). document_file la référence avec ON DELETE CASCADE :
-- son contenu est mis de côté pendant la reconstruction, sinon la suppression de l'ancienne table
-- l'effacerait (clés étrangères actives).
CREATE TEMPORARY TABLE document_file_v27 AS SELECT source_id, path, size, modified_at FROM document_file;

CREATE TABLE project_doc_source_v27 (
    id           TEXT PRIMARY KEY,
    project_id   TEXT NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    kind         TEXT NOT NULL CHECK (kind IN ('URL', 'FOLDER', 'DOCUMENTS', 'CONFLUENCE')),
    location     TEXT NOT NULL CHECK (length(location) BETWEEN 1 AND 2000),
    last_read_at TEXT,
    last_result  TEXT,
    -- tableau JSON de motifs, NULL hors DOCUMENTS
    exclusions   TEXT CHECK (exclusions IS NULL OR kind = 'DOCUMENTS'),
    -- niveau propre d'une racine Confluence ; NULL : celui de Confluence (R6)
    reliability  TEXT CHECK (reliability IS NULL OR (kind = 'CONFLUENCE'
                     AND reliability IN ('REFERENCE', 'INTERPRETATION', 'TO_CONFIRM'))),
    -- identifiant et titre de la page racine, résolus à l'ajout
    page_id      TEXT CHECK (page_id IS NULL OR kind = 'CONFLUENCE'),
    title        TEXT CHECK (title IS NULL OR (kind = 'CONFLUENCE' AND length(title) <= 500))
);

INSERT INTO project_doc_source_v27 (id, project_id, kind, location, last_read_at, last_result, exclusions)
SELECT id, project_id, kind, location, last_read_at, last_result, exclusions FROM project_doc_source;

DROP TABLE project_doc_source;

ALTER TABLE project_doc_source_v27 RENAME TO project_doc_source;

CREATE INDEX project_doc_source_project ON project_doc_source (project_id);

INSERT INTO document_file (source_id, path, size, modified_at)
SELECT source_id, path, size, modified_at FROM document_file_v27
WHERE source_id IN (SELECT id FROM project_doc_source)
  AND NOT EXISTS (SELECT 1 FROM document_file f WHERE f.source_id = document_file_v27.source_id
                  AND f.path = document_file_v27.path);

DROP TABLE document_file_v27;

-- Pages lues d'une racine Confluence, pour ne relire que celles dont la version change (R3).
CREATE TABLE confluence_page (
    source_id  TEXT NOT NULL REFERENCES project_doc_source (id) ON DELETE CASCADE,
    page_id    TEXT NOT NULL,
    version    INTEGER NOT NULL CHECK (version >= 1),
    updated_at TEXT,
    PRIMARY KEY (source_id, page_id)
);

-- Niveau d'une sorte de source, seulement s'il diffère du défaut (R6).
CREATE TABLE project_source_reliability (
    project_id TEXT NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    origin     TEXT NOT NULL CHECK (origin IN ('JIRA', 'ANTORA', 'DOCUMENTS', 'CONFLUENCE',
                                               'ARCHITECTURE', 'SPECIFICATIONS')),
    level      TEXT NOT NULL CHECK (level IN ('REFERENCE', 'INTERPRETATION', 'TO_CONFIRM')),
    PRIMARY KEY (project_id, origin)
);

-- Provenance d'une source citée par l'agent (R5) ; NULL pour les messages antérieurs.
ALTER TABLE conversation_source ADD COLUMN origin TEXT;
ALTER TABLE conversation_source ADD COLUMN updated_at TEXT;
ALTER TABLE conversation_source ADD COLUMN level TEXT;
