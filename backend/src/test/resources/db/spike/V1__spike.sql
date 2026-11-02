CREATE TABLE spike_note (
    id    TEXT PRIMARY KEY,
    title TEXT,
    body  TEXT
);

CREATE VIRTUAL TABLE spike_search USING fts5(
    note_id UNINDEXED,
    title,
    body,
    tokenize = 'unicode61 remove_diacritics 2'
);
