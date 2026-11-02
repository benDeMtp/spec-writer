-- Sessions, questions et réponses (data-model.md) ; suppression en cascade depuis session (FR-017).
CREATE TABLE session (
    id             TEXT PRIMARY KEY,
    name           TEXT NOT NULL CHECK (length(name) BETWEEN 1 AND 120),
    need           TEXT NOT NULL CHECK (length(need) <= 10000),
    language       TEXT NOT NULL CHECK (language IN ('FR', 'EN')),
    origin         TEXT NOT NULL CHECK (origin IN ('LOCAL', 'JIRA_IMPORT')),
    state          TEXT NOT NULL CHECK (state IN ('QUESTIONING', 'READY_TO_GENERATE', 'GENERATED')),
    covered_themes TEXT NOT NULL DEFAULT '[]',
    created_at     TEXT NOT NULL,
    updated_at     TEXT NOT NULL
);

CREATE INDEX session_updated_at ON session (updated_at);

CREATE TABLE question (
    id            TEXT PRIMARY KEY,
    session_id    TEXT    NOT NULL REFERENCES session (id) ON DELETE CASCADE,
    ordinal       INTEGER NOT NULL,
    theme         TEXT    NOT NULL,
    batch         INTEGER NOT NULL,
    label         TEXT    NOT NULL CHECK (length(label) <= 500),
    rationale     TEXT    NOT NULL,
    suggestions   TEXT    NOT NULL DEFAULT '[]',
    default_value TEXT,
    depends_on    TEXT    NOT NULL DEFAULT '[]',
    state         TEXT    NOT NULL CHECK (state IN ('PENDING', 'ANSWERED', 'SKIPPED', 'OBSOLETE')),
    UNIQUE (session_id, ordinal)
);

CREATE TABLE answer (
    question_id TEXT PRIMARY KEY REFERENCES question (id) ON DELETE CASCADE,
    value       TEXT    NOT NULL CHECK (length(value) <= 5000),
    assumption  INTEGER NOT NULL,
    answered_at TEXT    NOT NULL
);
