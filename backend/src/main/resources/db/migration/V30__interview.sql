-- Interview d'une spécification avec les sachants (021, data-model.md, research.md R5, R8).

-- Séance : préparée, en cours, terminée ; participants en JSON (≤ 20 noms de 1 à 120 caractères,
-- vérifiés par le domaine) ; curseur pour reprendre au dernier élément affiché.
CREATE TABLE interview (
    id                 TEXT PRIMARY KEY,
    project_id         TEXT NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    state              TEXT NOT NULL CHECK (state IN ('PREPARED', 'IN_PROGRESS', 'FINISHED')),
    participants       TEXT NOT NULL DEFAULT '[]',
    created_at         TEXT NOT NULL,
    started_at         TEXT,
    finished_at        TEXT,
    cursor_session_id  TEXT,
    cursor_element     TEXT,
    cursor_step        INTEGER,
    cursor_alternative INTEGER NOT NULL DEFAULT 0 CHECK (cursor_alternative IN (0, 1))
);

CREATE INDEX interview_project_state ON interview (project_id, state);

-- Spécifications d'une séance, dans l'ordre ; version relue fixée au démarrage ; une seule séance
-- en cours par spécification (FR-007), par l'index unique partiel.
CREATE TABLE interview_spec (
    interview_id    TEXT    NOT NULL REFERENCES interview (id) ON DELETE CASCADE,
    session_id      TEXT    NOT NULL REFERENCES session (id) ON DELETE CASCADE,
    rank            INTEGER NOT NULL CHECK (rank >= 1),
    version         INTEGER,
    excluded        TEXT    NOT NULL DEFAULT '[]',
    in_progress     INTEGER NOT NULL DEFAULT 0 CHECK (in_progress IN (0, 1)),
    applied_version INTEGER,
    PRIMARY KEY (interview_id, session_id)
);

CREATE UNIQUE INDEX interview_spec_one_in_progress ON interview_spec (session_id) WHERE in_progress = 1;
CREATE INDEX interview_spec_session ON interview_spec (session_id);

-- Points notés en séance, rattachés à un élément de la version relue (élément nul : « Non rattachés »).
CREATE TABLE interview_point (
    id                 TEXT PRIMARY KEY,
    interview_id       TEXT    NOT NULL REFERENCES interview (id) ON DELETE CASCADE,
    session_id         TEXT    NOT NULL REFERENCES session (id) ON DELETE CASCADE,
    anchor_element     TEXT CHECK (anchor_element IS NULL OR length(anchor_element) <= 20),
    anchor_step        INTEGER CHECK (anchor_step IS NULL OR anchor_step >= 1),
    anchor_alternative INTEGER NOT NULL DEFAULT 0 CHECK (anchor_alternative IN (0, 1)),
    origin             TEXT    NOT NULL CHECK (origin IN ('ASSUMPTION', 'SOURCE_DEVIATION', 'OPEN_QUESTION', 'REMARK',
                                                          'QUESTION')),
    origin_code        TEXT CHECK (origin_code IS NULL OR length(origin_code) <= 20),
    text               TEXT    NOT NULL DEFAULT '' CHECK (length(text) <= 5000),
    outcome            TEXT CHECK (outcome IS NULL OR outcome IN ('CONFIRMED', 'CHANGE', 'REMOVE', 'DEFERRED')),
    owner              TEXT CHECK (owner IS NULL OR length(owner) <= 120),
    due                TEXT,
    source_kind        TEXT,
    source_ref         TEXT,
    created_at         TEXT    NOT NULL,
    updated_at         TEXT    NOT NULL
);

CREATE INDEX interview_point_interview ON interview_point (interview_id, session_id);

-- Sources de vérité à mettre à jour après une décision de séance (« à reporter », R8) ; jamais
-- closes automatiquement.
CREATE TABLE source_update (
    id           TEXT PRIMARY KEY,
    session_id   TEXT NOT NULL REFERENCES session (id) ON DELETE CASCADE,
    interview_id TEXT REFERENCES interview (id) ON DELETE SET NULL,
    point_id     TEXT REFERENCES interview_point (id) ON DELETE SET NULL,
    source_kind  TEXT NOT NULL CHECK (source_kind IN ('DOC_PAGE', 'JIRA_ISSUE', 'ARCH_ELEMENT')),
    source_ref   TEXT NOT NULL,
    source_title TEXT,
    fingerprint  TEXT,
    text         TEXT NOT NULL CHECK (length(text) <= 5000),
    state        TEXT NOT NULL CHECK (state IN ('OPEN', 'CLOSED')),
    created_at   TEXT NOT NULL,
    closed_at    TEXT
);

CREATE INDEX source_update_session_state ON source_update (session_id, state);

-- Message de séance dans la discussion d'une spécification (R4) : son titre de source.
ALTER TABLE conversation_message ADD COLUMN interview_id TEXT REFERENCES interview (id) ON DELETE SET NULL;
