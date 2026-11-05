-- Numérotation des versions par compteur (feature 002, research.md R8, FR-014) : un numéro supprimé
-- n'est jamais réattribué. Le compteur n'est jamais décrémenté.
ALTER TABLE session ADD COLUMN next_version_number INTEGER NOT NULL DEFAULT 1 CHECK (next_version_number >= 1);

UPDATE session
SET next_version_number = coalesce(
    (SELECT max(v.number) FROM specification_version v WHERE v.session_id = session.id), 0) + 1;

-- Une version supprimée disparaît de la recherche (FR-018) : la dernière version restante, ou rien,
-- y prend sa place. V6 ne tenait à jour que l'ajout d'une version.
CREATE TRIGGER spec_search_version_delete AFTER DELETE ON specification_version
BEGIN
    UPDATE spec_search
    SET markdown = coalesce((SELECT v.markdown FROM specification_version v WHERE v.session_id = old.session_id
                             ORDER BY v.number DESC LIMIT 1), '')
    WHERE session_id = old.session_id;
END;
