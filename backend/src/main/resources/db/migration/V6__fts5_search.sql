-- Recherche plein texte (FR-040) : nom et besoin de la session, Markdown de sa dernière version.
-- remove_diacritics 2 : « regle » trouve « règle ».
CREATE VIRTUAL TABLE spec_search USING fts5(
    session_id UNINDEXED,
    title,
    need,
    markdown,
    tokenize = 'unicode61 remove_diacritics 2'
);

INSERT INTO spec_search (session_id, title, need, markdown)
SELECT s.id, s.name, s.need,
       coalesce((SELECT v.markdown FROM specification_version v WHERE v.session_id = s.id
                 ORDER BY v.number DESC LIMIT 1), '')
FROM session s;

CREATE TRIGGER spec_search_session_insert AFTER INSERT ON session
BEGIN
    INSERT INTO spec_search (session_id, title, need, markdown) VALUES (new.id, new.name, new.need, '');
END;

CREATE TRIGGER spec_search_session_update AFTER UPDATE OF name, need ON session
BEGIN
    UPDATE spec_search SET title = new.name, need = new.need WHERE session_id = new.id;
END;

CREATE TRIGGER spec_search_session_delete AFTER DELETE ON session
BEGIN
    DELETE FROM spec_search WHERE session_id = old.id;
END;

CREATE TRIGGER spec_search_version_insert AFTER INSERT ON specification_version
BEGIN
    UPDATE spec_search SET markdown = new.markdown WHERE session_id = new.session_id;
END;
