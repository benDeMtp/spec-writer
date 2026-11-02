-- Réglages des services (une seule ligne chacun) ; aucun jeton ici (FR-033a).
CREATE TABLE copilot_settings (
    id                 INTEGER PRIMARY KEY CHECK (id = 1),
    connected_login    TEXT,
    subscription_state TEXT    NOT NULL CHECK (subscription_state IN ('UNKNOWN', 'ACTIVE', 'NO_COPILOT')),
    auth_mode          TEXT    NOT NULL CHECK (auth_mode IN ('COPILOT_CLI', 'GITHUB_APP')),
    model              TEXT,
    available_models   TEXT    NOT NULL DEFAULT '[]',
    model_preference   TEXT    NOT NULL DEFAULT '[]',
    token_expires_at   TEXT,
    secret_persistence TEXT    NOT NULL CHECK (secret_persistence IN ('KEYCHAIN', 'MEMORY_ONLY'))
);

CREATE TABLE jira_settings (
    id                  INTEGER PRIMARY KEY CHECK (id = 1),
    enabled             INTEGER NOT NULL,
    server_url          TEXT    NOT NULL,
    default_project_key TEXT    NOT NULL,
    ca_certificate_path TEXT,
    server_version      TEXT,
    last_test_at        TEXT,
    last_test_result    TEXT,
    secret_persistence  TEXT    NOT NULL CHECK (secret_persistence IN ('KEYCHAIN', 'MEMORY_ONLY')),
    insecure_http       INTEGER NOT NULL
);

-- Historique des consentements (FR-024)
CREATE TABLE consent_record (
    id      TEXT PRIMARY KEY,
    service TEXT    NOT NULL CHECK (service IN ('COPILOT', 'JIRA')),
    granted INTEGER NOT NULL,
    at      TEXT    NOT NULL
);

CREATE INDEX consent_record_service_at ON consent_record (service, at);
