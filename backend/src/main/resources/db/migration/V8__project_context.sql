-- Contexte de projet (feature 002, data-model.md, FR-008 à FR-013) : la fiche Markdown du projet,
-- ses sources de documentation et, sur chaque version, le contexte utilisé (FR-011).
ALTER TABLE project ADD COLUMN context TEXT NOT NULL DEFAULT '' CHECK (length(context) <= 20000);
ALTER TABLE project ADD COLUMN context_hash TEXT NOT NULL DEFAULT '';
ALTER TABLE project ADD COLUMN context_origin TEXT NOT NULL DEFAULT 'NONE'
    CHECK (context_origin IN ('NONE', 'GENERATED', 'EDITED'));
ALTER TABLE project ADD COLUMN context_updated_at TEXT;

-- Le contenu lu n'est jamais stocké : last_result ne contient que des nombres (FR-013b).
CREATE TABLE project_doc_source (
    id           TEXT PRIMARY KEY,
    project_id   TEXT NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    kind         TEXT NOT NULL CHECK (kind IN ('URL', 'FOLDER')),
    location     TEXT NOT NULL CHECK (length(location) BETWEEN 1 AND 2000),
    last_read_at TEXT,
    last_result  TEXT
);

CREATE INDEX project_doc_source_project ON project_doc_source (project_id);

ALTER TABLE specification_version ADD COLUMN context_hash TEXT NOT NULL DEFAULT '';
ALTER TABLE specification_version ADD COLUMN context_updated_at TEXT;
