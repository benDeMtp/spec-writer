-- Correctif de la 010 : chaque page de documentation indexée est rattachée à sa source, pour que la
-- lecture complète d'une source remplace ses éléments (page retirée ou renommée, ADR renuméroté),
-- même en réindexation partielle ou quand une autre source est en panne. Les dossiers de documents
-- (« docs:<source>/… », 006) le sont déjà par leur préfixe.
ALTER TABLE knowledge_item ADD COLUMN source_id TEXT;

CREATE INDEX knowledge_item_source ON knowledge_item (project_id, source_id);

-- Pages indexées avant ce rattachement : leur source est inconnue et elles peuvent contenir des
-- restes (deux ADR de même numéro). Elles sont retirées ; la prochaine indexation les réimporte.
DELETE FROM knowledge_item WHERE kind IN ('DOC_SECTION', 'ADR') AND substr(ref, 1, 5) <> 'docs:';
