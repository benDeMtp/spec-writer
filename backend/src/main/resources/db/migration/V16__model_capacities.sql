-- 008 : capacité et coefficient de chaque modèle Copilot, relus à la connexion (research.md R1)
ALTER TABLE copilot_settings ADD COLUMN model_capacities TEXT NOT NULL DEFAULT '[]';
