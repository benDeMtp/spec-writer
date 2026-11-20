-- Assistant de premier lancement (018, data-model.md, research.md R1) : progression, une seule ligne,
-- et état des étapes faites ou passées (absence de ligne : à faire).
CREATE TABLE onboarding (
    id           INTEGER PRIMARY KEY CHECK (id = 1),
    current_step TEXT    NOT NULL CHECK (current_step IN ('WELCOME', 'COPILOT', 'JIRA', 'CONFLUENCE', 'PROJECT',
                                                          'SOURCES', 'INDEXATION', 'DONE')),
    completed_at TEXT,
    updated_at   TEXT    NOT NULL
);

CREATE TABLE onboarding_step (
    step  TEXT PRIMARY KEY CHECK (step IN ('COPILOT', 'JIRA', 'CONFLUENCE', 'PROJECT', 'SOURCES', 'INDEXATION')),
    state TEXT NOT NULL CHECK (state IN ('DONE', 'SKIPPED'))
);

INSERT INTO onboarding (id, current_step, completed_at, updated_at)
VALUES (1, 'WELCOME', NULL, strftime('%Y-%m-%dT%H:%M:%fZ', 'now'));

-- Une installation déjà utilisée est réputée configurée (FR-001, SC-006) : l'assistant ne s'y ouvre
-- pas de lui-même. Une base neuve passe ici avec des tables vides et reste « à faire ».
UPDATE onboarding
SET current_step = 'DONE',
    completed_at = strftime('%Y-%m-%dT%H:%M:%fZ', 'now')
WHERE EXISTS (SELECT 1 FROM copilot_settings WHERE connected_login IS NOT NULL)
   OR EXISTS (SELECT 1 FROM jira_settings)
   OR EXISTS (SELECT 1 FROM confluence_settings)
   OR EXISTS (SELECT 1 FROM session)
   OR EXISTS (SELECT 1 FROM conversation)
   OR EXISTS (SELECT 1 FROM project_doc_source)
   OR (SELECT count(*) FROM project) > 1;
