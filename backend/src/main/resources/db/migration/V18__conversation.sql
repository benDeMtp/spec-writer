-- 010 : conversations avec l'agent, rattachées à un projet et supprimées avec lui (research.md R6).
CREATE TABLE conversation (
    id         TEXT PRIMARY KEY,
    project_id TEXT NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    name       TEXT NOT NULL CHECK (length(name) BETWEEN 1 AND 120),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);
CREATE INDEX conversation_project ON conversation (project_id, updated_at);

CREATE TABLE conversation_message (
    id                TEXT PRIMARY KEY,
    conversation_id   TEXT    NOT NULL REFERENCES conversation (id) ON DELETE CASCADE,
    ordinal           INTEGER NOT NULL,
    role              TEXT    NOT NULL CHECK (role IN ('USER', 'AGENT')),
    text              TEXT    NOT NULL CHECK (length(text) <= 100000),
    state             TEXT    NOT NULL CHECK (state IN ('COMPLETE', 'INCOMPLETE', 'RUNNING')),
    history_truncated INTEGER NOT NULL DEFAULT 0,
    created_at        TEXT    NOT NULL,
    UNIQUE (conversation_id, ordinal)
);

-- Recherches lancées par l'agent pour une réponse : 5 au plus.
CREATE TABLE conversation_step (
    message_id TEXT    NOT NULL REFERENCES conversation_message (id) ON DELETE CASCADE,
    ordinal    INTEGER NOT NULL CHECK (ordinal BETWEEN 1 AND 5),
    query      TEXT    NOT NULL CHECK (length(query) <= 300),
    kinds      TEXT    NOT NULL,
    hits       INTEGER NOT NULL,
    PRIMARY KEY (message_id, ordinal)
);

-- Sources citées par une réponse ; les autres résultats ne sont pas gardés.
CREATE TABLE conversation_source (
    message_id TEXT    NOT NULL REFERENCES conversation_message (id) ON DELETE CASCADE,
    n          INTEGER NOT NULL,
    kind       TEXT    NOT NULL,
    ref        TEXT    NOT NULL,
    title      TEXT    NOT NULL,
    PRIMARY KEY (message_id, n)
);

-- Documents joints à une conversation (comme V17) : texte extrait seul.
CREATE TABLE conversation_attachment (
    id              TEXT PRIMARY KEY,
    conversation_id TEXT    NOT NULL REFERENCES conversation (id) ON DELETE CASCADE,
    ordinal         INTEGER NOT NULL,
    title           TEXT    NOT NULL,
    file_name       TEXT    NOT NULL CHECK (length(file_name) BETWEEN 1 AND 255),
    format          TEXT    NOT NULL CHECK (format IN ('DOCX', 'XLSX', 'PDF', 'HTML')),
    chars           INTEGER NOT NULL,
    content_hash    TEXT    NOT NULL,
    text            TEXT    NOT NULL CHECK (length(text) <= 200000),
    added_at        TEXT    NOT NULL,
    UNIQUE (conversation_id, content_hash),
    UNIQUE (conversation_id, file_name)
);

-- Spécifications démarrées depuis une conversation.
CREATE TABLE conversation_session (
    conversation_id TEXT NOT NULL REFERENCES conversation (id) ON DELETE CASCADE,
    session_id      TEXT NOT NULL REFERENCES session (id) ON DELETE CASCADE,
    PRIMARY KEY (conversation_id, session_id)
);
CREATE INDEX conversation_session_session ON conversation_session (session_id);
