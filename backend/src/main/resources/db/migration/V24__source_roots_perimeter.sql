-- 013 : racines de sources du projet, lues à chaque analyse, jamais copiées ni indexées (research.md R1).
CREATE TABLE project_source_root (
    id         TEXT PRIMARY KEY,
    project_id TEXT NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    location   TEXT NOT NULL CHECK (length(location) BETWEEN 1 AND 2000),
    exclusions TEXT,
    created_at TEXT NOT NULL,
    UNIQUE (project_id, location)
);
CREATE INDEX project_source_root_project ON project_source_root (project_id);

-- Analyses de périmètre d'une réponse de l'agent (R9) : ordinal partagé avec les recherches.
-- root_id n'est pas une clé étrangère : une analyse reste lisible après la suppression de sa racine.
CREATE TABLE perimeter_analysis (
    id                TEXT PRIMARY KEY,
    message_id        TEXT    NOT NULL REFERENCES conversation_message (id) ON DELETE CASCADE,
    ordinal           INTEGER NOT NULL CHECK (ordinal BETWEEN 1 AND 5),
    terms             TEXT    NOT NULL,
    rejected_terms    TEXT    NOT NULL,
    roots             TEXT    NOT NULL,
    state             TEXT    NOT NULL CHECK (state IN ('COMPLETE', 'INCOMPLETE')),
    incomplete_reason TEXT,
    files_scanned     INTEGER NOT NULL,
    files_skipped     INTEGER NOT NULL,
    skipped_examples  TEXT    NOT NULL,
    duration_ms       INTEGER NOT NULL,
    architecture_only TEXT    NOT NULL,
    unavailable       TEXT,
    UNIQUE (message_id, ordinal)
);

CREATE TABLE perimeter_module (
    id            TEXT PRIMARY KEY,
    analysis_id   TEXT    NOT NULL REFERENCES perimeter_analysis (id) ON DELETE CASCADE,
    position      INTEGER NOT NULL,
    root_id       TEXT    NOT NULL,
    root_label    TEXT    NOT NULL,
    path          TEXT    NOT NULL,
    name          TEXT    NOT NULL CHECK (length(name) BETWEEN 1 AND 200),
    kind          TEXT    NOT NULL CHECK (kind IN ('MAVEN', 'NPM', 'HELM', 'ROOT')),
    occurrences   INTEGER NOT NULL,
    reasons       TEXT    NOT NULL,
    qualification TEXT    NOT NULL CHECK (qualification IN ('IMPACTED', 'TO_CHECK', 'NOT_IMPACTED', 'UNQUALIFIED')),
    justification TEXT CHECK (justification IS NULL OR length(justification) <= 300),
    is_new        INTEGER NOT NULL DEFAULT 0 CHECK (is_new IN (0, 1)),
    arch_element  TEXT,
    arch_status   TEXT,
    UNIQUE (analysis_id, position)
);

-- Échantillon d'occurrences : extraits bornés, seul contenu des sources conservé (FR-007).
CREATE TABLE perimeter_occurrence (
    module_id TEXT    NOT NULL REFERENCES perimeter_module (id) ON DELETE CASCADE,
    position  INTEGER NOT NULL,
    path      TEXT    NOT NULL,
    line      INTEGER NOT NULL CHECK (line >= 1),
    term      TEXT    NOT NULL,
    excerpt   TEXT    NOT NULL CHECK (length(excerpt) <= 200),
    PRIMARY KEY (module_id, position)
);

-- Dernière analyse de la conversation, copiée dans la session qu'elle a lancée (R11).
CREATE TABLE session_perimeter (
    session_id TEXT PRIMARY KEY REFERENCES session (id) ON DELETE CASCADE,
    snapshot   TEXT NOT NULL,
    taken_at   TEXT NOT NULL
);

-- Propriété Structurizr « spec-writer.module » d'un élément d'architecture (R8).
ALTER TABLE knowledge_item ADD COLUMN module_hint TEXT;
