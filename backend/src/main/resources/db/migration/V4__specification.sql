-- Versions de spécification (FR-018), jamais modifiées. Le contenu structuré (use cases, scénarios,
-- hypothèses, exigences) est un document JSON lu en entier ; le Markdown sert à l'export et à la
-- recherche plein texte.
CREATE TABLE specification_version (
    id                TEXT PRIMARY KEY,
    session_id        TEXT    NOT NULL REFERENCES session (id) ON DELETE CASCADE,
    number            INTEGER NOT NULL CHECK (number >= 1),
    title             TEXT    NOT NULL CHECK (length(title) <= 120),
    input_fingerprint TEXT    NOT NULL,
    origin            TEXT    NOT NULL CHECK (origin IN ('GENERATED', 'AI_EDIT', 'JIRA_IMPORT', 'CONFLICT_MERGE')),
    content           TEXT    NOT NULL,
    markdown          TEXT    NOT NULL,
    created_at        TEXT    NOT NULL,
    UNIQUE (session_id, number)
);

CREATE INDEX specification_version_fingerprint ON specification_version (session_id, input_fingerprint);
