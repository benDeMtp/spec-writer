-- 017 : délai de synchronisation automatique et dernier bilan de chaque source d'un projet
-- (data-model.md). source_key : JIRA, ARCHITECTURE ou identifiant d'une source de documentation.
CREATE TABLE source_sync (
    project_id       TEXT    NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    source_key       TEXT    NOT NULL CHECK (length(source_key) BETWEEN 1 AND 64),
    interval_minutes INTEGER CHECK (interval_minutes IS NULL OR interval_minutes IN (15, 30, 60, 240, 1440)),
    last_run_at      TEXT,
    last_result      TEXT    CHECK (last_result IS NULL OR last_result IN ('CHANGED', 'UNCHANGED', 'ERROR')),
    added            INTEGER NOT NULL DEFAULT 0 CHECK (added >= 0),
    modified         INTEGER NOT NULL DEFAULT 0 CHECK (modified >= 0),
    removed          INTEGER NOT NULL DEFAULT 0 CHECK (removed >= 0),
    error_code       TEXT,
    last_success_at  TEXT,
    PRIMARY KEY (project_id, source_key)
);

CREATE INDEX source_sync_due ON source_sync (interval_minutes, last_run_at);

-- une source de documentation retirée emporte son délai et son bilan
CREATE TRIGGER source_sync_doc_source_deleted AFTER DELETE ON project_doc_source
BEGIN
    DELETE FROM source_sync WHERE project_id = OLD.project_id AND source_key = OLD.id;
END;

-- notifications du navigateur des sources mises à jour, désactivées par défaut (FR-016)
ALTER TABLE app_settings ADD COLUMN notify_source_updates INTEGER NOT NULL DEFAULT 0
    CHECK (notify_source_updates IN (0, 1));

-- tri de la vue Jira par date de modification (SC-001)
CREATE INDEX knowledge_item_updated ON knowledge_item (project_id, kind, source_updated_at);
