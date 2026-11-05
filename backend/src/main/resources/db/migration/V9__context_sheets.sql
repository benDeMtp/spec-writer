-- Feature 002 (research.md R16) : la fiche technique, à côté de la fiche fonctionnelle (colonnes
-- context* de V8), et les questions de clarification répondues ou passées (FR-008a, FR-013i).
ALTER TABLE project ADD COLUMN technical_context TEXT NOT NULL DEFAULT '' CHECK (length(technical_context) <= 20000);
ALTER TABLE project ADD COLUMN technical_context_hash TEXT NOT NULL DEFAULT '';
ALTER TABLE project ADD COLUMN technical_context_origin TEXT NOT NULL DEFAULT 'NONE'
    CHECK (technical_context_origin IN ('NONE', 'GENERATED', 'EDITED'));
ALTER TABLE project ADD COLUMN technical_context_updated_at TEXT;

CREATE TABLE project_context_question (
    id          TEXT PRIMARY KEY,
    project_id  TEXT NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    number      INTEGER NOT NULL CHECK (number >= 1),
    sheet       TEXT NOT NULL CHECK (sheet IN ('FUNCTIONAL', 'TECHNICAL')),
    label       TEXT NOT NULL CHECK (length(label) <= 500),
    rationale   TEXT NOT NULL DEFAULT '' CHECK (length(rationale) <= 500),
    answer      TEXT NOT NULL DEFAULT '' CHECK (length(answer) <= 2000),
    state       TEXT NOT NULL CHECK (state IN ('ANSWERED', 'SKIPPED')),
    asked_at    TEXT NOT NULL,
    answered_at TEXT NOT NULL,
    UNIQUE (project_id, number)
);
