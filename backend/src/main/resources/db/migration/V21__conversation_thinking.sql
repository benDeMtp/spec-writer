-- Réflexion de l'agent pendant un tour (010) : étapes résumées par le modèle (JSON, 20 000 caractères
-- de texte au plus) et durée du tour. Supprimée avec le message.
CREATE TABLE conversation_thinking (
    message_id  TEXT PRIMARY KEY REFERENCES conversation_message (id) ON DELETE CASCADE,
    parts       TEXT    NOT NULL CHECK (length(parts) <= 200000),
    duration_ms INTEGER NOT NULL CHECK (duration_ms >= 0)
);
