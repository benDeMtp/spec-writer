-- Ticket Jira lié (data-model.md, LinkedJiraTicket) : un ticket par session, une session par ticket.
CREATE TABLE linked_jira_ticket (
    session_id                  TEXT PRIMARY KEY REFERENCES session (id) ON DELETE CASCADE,
    issue_key                   TEXT    NOT NULL UNIQUE,
    issue_url                   TEXT    NOT NULL,
    issue_type                  TEXT,
    linked_version_number       INTEGER NOT NULL,
    last_known_updated          TEXT,
    last_known_description_hash TEXT,
    created_or_imported_at      TEXT    NOT NULL,
    last_pushed_at              TEXT,
    sync_state                  TEXT    NOT NULL CHECK (sync_state IN ('IN_SYNC', 'LOCAL_AHEAD', 'CONFLICT'))
);
