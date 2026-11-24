-- Sous-tâche enrichie dans Jira : sa description, relue avec son statut et transmise à l'agent de code
-- par next_task, start_task et get_feature (Markdown, coupée à 32 000 caractères).
alter table spec_task add column jira_description text
    check (jira_description is null or length(jira_description) <= 32000);
