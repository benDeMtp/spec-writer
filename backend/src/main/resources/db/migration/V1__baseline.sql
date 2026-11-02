-- Table technique ; chaque user story ajoute sa propre migration (tasks.md).
CREATE TABLE app_meta (
    key   TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

INSERT INTO app_meta (key, value) VALUES ('schema', '1');
