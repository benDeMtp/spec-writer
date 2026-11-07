-- Dossier de documents (feature 006, data-model.md) : un nouveau type de source, ses motifs
-- d'exclusion, et l'état des fichiers lus (taille, date) pour ne relire que ce qui a changé.
-- Aucune table ne référence project_doc_source : la reconstruire ne déclenche aucune cascade.
CREATE TABLE project_doc_source_v15 (
    id           TEXT PRIMARY KEY,
    project_id   TEXT NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    kind         TEXT NOT NULL CHECK (kind IN ('URL', 'FOLDER', 'DOCUMENTS')),
    location     TEXT NOT NULL CHECK (length(location) BETWEEN 1 AND 2000),
    last_read_at TEXT,
    last_result  TEXT,
    -- tableau JSON de motifs, NULL hors DOCUMENTS
    exclusions   TEXT CHECK (exclusions IS NULL OR kind = 'DOCUMENTS')
);

INSERT INTO project_doc_source_v15 (id, project_id, kind, location, last_read_at, last_result)
SELECT id, project_id, kind, location, last_read_at, last_result FROM project_doc_source;

DROP TABLE project_doc_source;

ALTER TABLE project_doc_source_v15 RENAME TO project_doc_source;

CREATE INDEX project_doc_source_project ON project_doc_source (project_id);

-- Le contenu des fichiers n'est jamais stocké ici : chemin relatif, taille et date seulement.
CREATE TABLE document_file (
    source_id   TEXT NOT NULL REFERENCES project_doc_source (id) ON DELETE CASCADE,
    path        TEXT NOT NULL CHECK (length(path) BETWEEN 1 AND 2000),
    size        INTEGER NOT NULL CHECK (size >= 0),
    modified_at TEXT NOT NULL,
    PRIMARY KEY (source_id, path)
);
