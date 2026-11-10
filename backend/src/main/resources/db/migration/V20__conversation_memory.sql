-- Mémoire d'une conversation (010) : résumé des échanges anciens, jusqu'au message covered_until ;
-- transmis à l'agent avant les échanges récents. Supprimée avec la conversation.
CREATE TABLE conversation_memory (
    conversation_id TEXT PRIMARY KEY REFERENCES conversation (id) ON DELETE CASCADE,
    summary         TEXT    NOT NULL CHECK (length(summary) <= 4000),
    covered_until   INTEGER NOT NULL CHECK (covered_until >= 0),
    updated_at      TEXT    NOT NULL
);
