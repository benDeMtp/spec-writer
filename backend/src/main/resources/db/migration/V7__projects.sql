-- Projets (feature 002, data-model.md) : chaque session appartient à un projet ; les sessions
-- existantes vont dans le « Projet par défaut » (SC-001).
CREATE TABLE project (
    id               TEXT PRIMARY KEY,
    name             TEXT NOT NULL CHECK (length(name) BETWEEN 1 AND 80),
    name_key         TEXT NOT NULL UNIQUE,
    description      TEXT CHECK (description IS NULL OR length(description) <= 1000),
    jira_project_key TEXT CHECK (jira_project_key IS NULL OR length(jira_project_key) BETWEEN 2 AND 10),
    created_at       TEXT NOT NULL,
    updated_at       TEXT NOT NULL
);

INSERT INTO project (id, name, name_key, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000001', 'Projet par défaut', 'projet par défaut',
        strftime('%Y-%m-%dT%H:%M:%fZ', 'now'), strftime('%Y-%m-%dT%H:%M:%fZ', 'now'));

-- Pas de reconstruction de la table session : sous foreign_keys=ON, dans la transaction de
-- Flyway, son DROP déclencherait les ON DELETE CASCADE vers les questions, les versions et le
-- ticket lié. SQLite refuse d'ajouter une colonne REFERENCES avec une valeur par défaut non nulle :
-- la colonne est donc ajoutée nullable, remplie, puis rendue obligatoire par deux triggers.
ALTER TABLE session ADD COLUMN project_id TEXT REFERENCES project (id);

UPDATE session SET project_id = '00000000-0000-0000-0000-000000000001';

CREATE INDEX session_project_updated_at ON session (project_id, updated_at);

CREATE TRIGGER session_project_required_insert BEFORE INSERT ON session
WHEN new.project_id IS NULL
BEGIN
    SELECT RAISE(ABORT, 'session.project_id est obligatoire');
END;

CREATE TRIGGER session_project_required_update BEFORE UPDATE OF project_id ON session
WHEN new.project_id IS NULL
BEGIN
    SELECT RAISE(ABORT, 'session.project_id est obligatoire');
END;

-- Réglages de l'application, une seule ligne : le projet courant, conservé d'un lancement à
-- l'autre (FR-005). Vide si le projet est supprimé : c'est alors le premier projet par nom.
CREATE TABLE app_settings (
    id                 INTEGER PRIMARY KEY CHECK (id = 1),
    current_project_id TEXT REFERENCES project (id) ON DELETE SET NULL
);

INSERT INTO app_settings (id, current_project_id) VALUES (1, '00000000-0000-0000-0000-000000000001');
