-- Feature 002, liens entre tickets (FR-044, data-model.md TicketLink, research.md R29) : parent,
-- sous-tâches et liens explicites de chaque ticket indexé, éventuellement vers un autre projet.
CREATE TABLE ticket_link (
    project_id TEXT NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    source_key TEXT NOT NULL,
    kind       TEXT NOT NULL CHECK (kind IN ('PARENT', 'SUBTASK', 'LINK')),
    target_key TEXT NOT NULL,
    link_type  TEXT NOT NULL DEFAULT '',
    direction  TEXT CHECK ((kind = 'LINK' AND direction IN ('OUTWARD', 'INWARD'))
                           OR (kind <> 'LINK' AND direction IS NULL)),
    label      TEXT CHECK (label IS NULL OR length(label) <= 100),
    PRIMARY KEY (project_id, source_key, kind, target_key, link_type)
);

CREATE INDEX ticket_link_target ON ticket_link (project_id, target_key);

-- Les tickets déjà indexés n'ont pas de liens : la synchronisation suivante les relit tous une fois
UPDATE project SET jira_sync_mark = NULL;
