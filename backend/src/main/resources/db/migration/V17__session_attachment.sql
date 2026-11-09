-- 009 : documents joints à une spécification (research.md R4). Seul le texte extrait est gardé, avec
-- la session : ni le fichier ni son chemin ; supprimé avec elle.
CREATE TABLE session_attachment (
    id           TEXT PRIMARY KEY,
    session_id   TEXT    NOT NULL REFERENCES session (id) ON DELETE CASCADE,
    ordinal      INTEGER NOT NULL,
    title        TEXT    NOT NULL,
    file_name    TEXT    NOT NULL CHECK (length(file_name) BETWEEN 1 AND 255),
    format       TEXT    NOT NULL CHECK (format IN ('DOCX', 'XLSX', 'PDF', 'HTML')),
    chars        INTEGER NOT NULL,
    content_hash TEXT    NOT NULL,
    text         TEXT    NOT NULL CHECK (length(text) <= 200000),
    added_at     TEXT    NOT NULL,
    UNIQUE (session_id, content_hash),
    UNIQUE (session_id, file_name)
);
CREATE INDEX session_attachment_session ON session_attachment (session_id, ordinal);
