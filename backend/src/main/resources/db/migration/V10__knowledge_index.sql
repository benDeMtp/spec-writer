-- Feature 002, connaissance du projet (data-model.md, research.md R18 à R22) : index local des
-- tickets, sections, ADR et éléments d'architecture, avec leurs vecteurs ; relations et
-- déploiements du modèle Structurizr. Le contenu ne quitte pas le dossier de données.
CREATE TABLE knowledge_item (
    id                TEXT PRIMARY KEY,
    project_id        TEXT NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    kind              TEXT NOT NULL CHECK (kind IN ('TICKET', 'DOC_SECTION', 'ADR', 'ARCH_ELEMENT')),
    ref               TEXT NOT NULL,
    chunk             INTEGER NOT NULL DEFAULT 0 CHECK (chunk >= 0),
    title             TEXT NOT NULL CHECK (length(title) BETWEEN 1 AND 500),
    body              TEXT NOT NULL DEFAULT '' CHECK (length(body) <= 8000),
    status            TEXT,
    status_category   TEXT,
    superseded_by     TEXT,
    issue_type        TEXT,
    element_type      TEXT CHECK (element_type IS NULL
                                  OR element_type IN ('PERSON', 'SOFTWARE_SYSTEM', 'CONTAINER', 'COMPONENT')),
    scope             TEXT CHECK (scope IS NULL OR scope IN ('PROJECT', 'COMMON')),
    transport         INTEGER NOT NULL DEFAULT 0 CHECK (transport IN (0, 1)),
    role              TEXT CHECK (role IS NULL OR role IN ('VOCABULARY', 'ARCHITECTURE_OVERVIEW')),
    content_hash      TEXT NOT NULL,
    source_updated_at TEXT,
    indexed_at        TEXT NOT NULL,
    embedding         BLOB,
    embedding_model   TEXT,
    UNIQUE (project_id, kind, ref, chunk)
);

CREATE INDEX knowledge_item_project_kind ON knowledge_item (project_id, kind);

-- remove_diacritics 2 : « regle » trouve « règle » (comme spec_search, V6)
CREATE VIRTUAL TABLE knowledge_fts USING fts5(
    item_id UNINDEXED,
    title,
    body,
    tokenize = 'unicode61 remove_diacritics 2'
);

CREATE TRIGGER knowledge_fts_insert AFTER INSERT ON knowledge_item
BEGIN
    INSERT INTO knowledge_fts (item_id, title, body) VALUES (new.id, new.title, new.body);
END;

CREATE TRIGGER knowledge_fts_update AFTER UPDATE OF title, body ON knowledge_item
BEGIN
    UPDATE knowledge_fts SET title = new.title, body = new.body WHERE item_id = new.id;
END;

CREATE TRIGGER knowledge_fts_delete AFTER DELETE ON knowledge_item
BEGIN
    DELETE FROM knowledge_fts WHERE item_id = old.id;
END;

CREATE TABLE arch_relation (
    project_id  TEXT NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    position    INTEGER NOT NULL,
    source      TEXT NOT NULL,
    destination TEXT NOT NULL,
    description TEXT CHECK (description IS NULL OR length(description) <= 500),
    technology  TEXT CHECK (technology IS NULL OR length(technology) <= 500),
    PRIMARY KEY (project_id, position)
);

CREATE TABLE arch_deployment (
    project_id            TEXT NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    position              INTEGER NOT NULL,
    element               TEXT NOT NULL,
    environment           TEXT,
    root_node             TEXT NOT NULL,
    node_path             TEXT,
    instances             INTEGER NOT NULL DEFAULT 1 CHECK (instances >= 1),
    shared_infrastructure INTEGER NOT NULL DEFAULT 0 CHECK (shared_infrastructure IN (0, 1)),
    PRIMARY KEY (project_id, position)
);

-- Source d'architecture du projet et repères de synchronisation (FR-038, research.md R20)
ALTER TABLE project ADD COLUMN architecture_workspace TEXT;
ALTER TABLE project ADD COLUMN architecture_ownership TEXT;
ALTER TABLE project ADD COLUMN architecture_hash TEXT;
ALTER TABLE project ADD COLUMN knowledge_synced_at TEXT;
ALTER TABLE project ADD COLUMN jira_sync_mark TEXT;

-- Story liée « relates to » à un ticket fermé (FR-035) et connaissance transmise (research.md R24)
ALTER TABLE session ADD COLUMN relates_to TEXT;
ALTER TABLE specification_version ADD COLUMN knowledge TEXT;

-- Modèle d'embedding remplacé par un dossier local (research.md R19)
ALTER TABLE app_settings ADD COLUMN embedding_model_folder TEXT;
ALTER TABLE app_settings ADD COLUMN embedding_pooling TEXT CHECK (embedding_pooling IS NULL
                                                                 OR embedding_pooling IN ('MEAN', 'CLS'));
ALTER TABLE app_settings ADD COLUMN embedding_query_prefix TEXT;
ALTER TABLE app_settings ADD COLUMN embedding_passage_prefix TEXT;
