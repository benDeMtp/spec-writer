-- 012 (FR-016) : la clé Jira par défaut devient facultative, repli des projets sans clé.
-- SQLite ne sait pas retirer un NOT NULL : la table est reconstruite à l'identique, sauf cette colonne.
CREATE TABLE jira_settings_new (
    id                  INTEGER PRIMARY KEY CHECK (id = 1),
    enabled             INTEGER NOT NULL,
    server_url          TEXT    NOT NULL,
    default_project_key TEXT,
    ca_certificate_path TEXT,
    server_version      TEXT,
    last_test_at        TEXT,
    last_test_result    TEXT,
    secret_persistence  TEXT    NOT NULL CHECK (secret_persistence IN ('KEYCHAIN', 'MEMORY_ONLY')),
    insecure_http       INTEGER NOT NULL
);

INSERT INTO jira_settings_new (id, enabled, server_url, default_project_key, ca_certificate_path, server_version,
                               last_test_at, last_test_result, secret_persistence, insecure_http)
SELECT id, enabled, server_url, default_project_key, ca_certificate_path, server_version,
       last_test_at, last_test_result, secret_persistence, insecure_http
FROM jira_settings;

DROP TABLE jira_settings;

ALTER TABLE jira_settings_new RENAME TO jira_settings;
