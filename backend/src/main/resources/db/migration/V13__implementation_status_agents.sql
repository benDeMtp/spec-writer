-- Statut d'implémentation et accès des agents de code (feature 002, research.md R7, R11, FR-026 à FR-028).

-- Écrit seulement par le changement de statut : l'enregistrement d'une session ne le touche pas.
ALTER TABLE session ADD COLUMN implementation_status TEXT NOT NULL DEFAULT 'DRAFT'
    CHECK (implementation_status IN ('DRAFT', 'READY', 'IN_PROGRESS', 'IMPLEMENTED'));
ALTER TABLE session ADD COLUMN implemented_at TEXT;
ALTER TABLE session ADD COLUMN evolved_from_session_id TEXT REFERENCES session (id) ON DELETE SET NULL;

-- Une seule ligne : l'accès des agents est désactivé par défaut.
CREATE TABLE agent_access_settings (
    id      INTEGER PRIMARY KEY CHECK (id = 1),
    enabled INTEGER NOT NULL DEFAULT 0 CHECK (enabled IN (0, 1))
);
INSERT INTO agent_access_settings (id, enabled) VALUES (1, 0);

-- Journal des accès des agents, sans contenu ; purgé au-delà de 90 jours.
CREATE TABLE agent_access_log (
    id          TEXT PRIMARY KEY,
    at          TEXT NOT NULL,
    agent_name  TEXT NOT NULL CHECK (length(agent_name) BETWEEN 1 AND 80),
    operation   TEXT NOT NULL
        CHECK (operation IN ('LIST_PROJECTS', 'LIST_FEATURES', 'GET_FEATURE', 'SET_STATUS', 'SEARCH_KNOWLEDGE')),
    project_id  TEXT REFERENCES project (id) ON DELETE SET NULL,
    session_id  TEXT REFERENCES session (id) ON DELETE SET NULL,
    old_status  TEXT CHECK (old_status IS NULL OR old_status IN ('DRAFT', 'READY', 'IN_PROGRESS', 'IMPLEMENTED')),
    new_status  TEXT CHECK (new_status IS NULL OR new_status IN ('DRAFT', 'READY', 'IN_PROGRESS', 'IMPLEMENTED'))
);
CREATE INDEX agent_access_log_at ON agent_access_log (at DESC);
