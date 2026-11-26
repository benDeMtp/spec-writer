-- 022 (research R4) : type de la sous-tâche dans Jira (« Sous-Tâche (AGILE) »), relu avec son statut et
-- sa description (V33) et transmis à l'agent de code avec la clé du ticket parent.
alter table spec_task add column jira_issue_type text
    check (jira_issue_type is null or length(jira_issue_type) <= 100);
