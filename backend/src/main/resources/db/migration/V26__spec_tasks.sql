-- 015 : tâches d'une spécification, suivies avec Jira et par les agents (data-model.md, research R1, R4, R5, R11).

CREATE TABLE spec_task (
    id                TEXT PRIMARY KEY,
    session_id        TEXT    NOT NULL REFERENCES session (id) ON DELETE CASCADE,
    position          INTEGER NOT NULL CHECK (position >= 0),
    title             TEXT    NOT NULL CHECK (length(title) BETWEEN 1 AND 120),
    outcome           TEXT    NOT NULL CHECK (length(outcome) <= 300
                                              AND (length(outcome) >= 1 OR origin = 'JIRA')),
    basis_kind        TEXT    NOT NULL CHECK (basis_kind IN ('MODULE', 'USE_CASE', 'NONE')),
    basis_ref         TEXT CHECK ((basis_kind = 'NONE') = (basis_ref IS NULL)),
    origin            TEXT    NOT NULL CHECK (origin IN ('PROPOSED', 'MANUAL', 'JIRA')),
    creation          TEXT    NOT NULL CHECK (creation IN ('NOT_CREATED', 'CREATED')),
    jira_key          TEXT UNIQUE CHECK (creation = 'NOT_CREATED' OR jira_key IS NOT NULL),
    status            TEXT    NOT NULL CHECK (status IN ('TODO', 'IN_PROGRESS', 'DONE', 'MISSING')),
    jira_status_name  TEXT,
    -- Connexion d'agent : « <pid>-<début du processus en ms> » de la passerelle (R4).
    holder_connection TEXT,
    holder_agent      TEXT CHECK (holder_agent IS NULL OR length(holder_agent) BETWEEN 1 AND 80),
    held_since        TEXT,
    -- Transition enregistrée mais pas encore envoyée à Jira (« à synchroniser », R5).
    pending_target    TEXT CHECK (pending_target IS NULL OR pending_target IN ('TODO', 'IN_PROGRESS', 'DONE')),
    pending_comment   INTEGER NOT NULL DEFAULT 0 CHECK (pending_comment IN (0, 1)),
    completion_report TEXT CHECK (completion_report IS NULL OR length(completion_report) <= 2000),
    last_read_at      TEXT,
    UNIQUE (session_id, position)
);
CREATE INDEX spec_task_session ON spec_task (session_id);

-- Préalables : seulement entre tâches de la même spécification, sans lien Jira.
CREATE TABLE spec_task_dependency (
    task_id       TEXT NOT NULL REFERENCES spec_task (id) ON DELETE CASCADE,
    depends_on_id TEXT NOT NULL REFERENCES spec_task (id) ON DELETE CASCADE,
    PRIMARY KEY (task_id, depends_on_id)
);

-- Proposition en cours de relecture, gardée pour survivre à un rechargement.
CREATE TABLE spec_task_proposal (
    session_id TEXT PRIMARY KEY REFERENCES session (id) ON DELETE CASCADE,
    content    TEXT NOT NULL,
    created_at TEXT NOT NULL
);

ALTER TABLE session ADD COLUMN task_tracking TEXT NOT NULL DEFAULT 'ACTIVE'
    CHECK (task_tracking IN ('ACTIVE', 'SUSPENDED'));

-- Journal des agents : contrainte d'opérations étendue et clé de tâche (reconstruction SQLite).
CREATE TABLE agent_access_log_new (
    id          TEXT PRIMARY KEY,
    at          TEXT NOT NULL,
    agent_name  TEXT NOT NULL CHECK (length(agent_name) BETWEEN 1 AND 80),
    operation   TEXT NOT NULL
        CHECK (operation IN ('LIST_PROJECTS', 'LIST_FEATURES', 'GET_FEATURE', 'SET_STATUS', 'SEARCH_KNOWLEDGE',
                             'NEXT_TASK', 'START_TASK', 'COMPLETE_TASK', 'RELEASE_TASK')),
    project_id  TEXT REFERENCES project (id) ON DELETE SET NULL,
    session_id  TEXT REFERENCES session (id) ON DELETE SET NULL,
    old_status  TEXT CHECK (old_status IS NULL OR old_status IN ('DRAFT', 'READY', 'IN_PROGRESS', 'IMPLEMENTED')),
    new_status  TEXT CHECK (new_status IS NULL OR new_status IN ('DRAFT', 'READY', 'IN_PROGRESS', 'IMPLEMENTED')),
    task_key    TEXT
);
INSERT INTO agent_access_log_new (id, at, agent_name, operation, project_id, session_id, old_status, new_status)
    SELECT id, at, agent_name, operation, project_id, session_id, old_status, new_status FROM agent_access_log;
DROP TABLE agent_access_log;
ALTER TABLE agent_access_log_new RENAME TO agent_access_log;
CREATE INDEX agent_access_log_at ON agent_access_log (at DESC);
