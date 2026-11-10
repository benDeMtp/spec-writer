-- Discussion d'une spécification (011) : une conversation (010) rattachée à une spécification.
-- Au plus une discussion suivie par spécification ; à la suppression de la spécification, les
-- discussions gardées perdent leur rattachement mais gardent le nom (« spécification supprimée »).
CREATE TABLE specification_discussion (
    conversation_id    TEXT PRIMARY KEY REFERENCES conversation (id) ON DELETE CASCADE,
    session_id         TEXT NULL REFERENCES session (id) ON DELETE SET NULL,
    specification_name TEXT    NOT NULL CHECK (length(specification_name) BETWEEN 1 AND 200),
    current            INTEGER NOT NULL DEFAULT 1,
    created_at         TEXT    NOT NULL
);

CREATE UNIQUE INDEX specification_discussion_current
    ON specification_discussion (session_id) WHERE current = 1 AND session_id IS NOT NULL;
CREATE INDEX specification_discussion_session ON specification_discussion (session_id);

-- Version créée depuis une proposition de discussion : son origine est « Issue de la discussion ».
-- En base, la version garde l'origine AI_EDIT (le CHECK de V4 n'est pas reconstruit).
CREATE TABLE version_discussion (
    version_id      TEXT PRIMARY KEY REFERENCES specification_version (id) ON DELETE CASCADE,
    conversation_id TEXT NULL REFERENCES conversation (id) ON DELETE SET NULL
);

-- Version de la spécification transmise à l'agent pour un tour de discussion.
ALTER TABLE conversation_message ADD COLUMN specification_version INTEGER NULL;
