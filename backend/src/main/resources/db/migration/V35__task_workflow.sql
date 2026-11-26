-- 023 (research R1) : statut Jira d'arrivée d'une tâche implémentée et statuts où une tâche compte comme
-- finie, réglés par projet. Pas de ligne : comportement de la 015 (catégorie « terminé »).
create table project_task_workflow (
    project_id        text primary key references project (id) on delete cascade,
    arrival_status    text not null check (length(arrival_status) between 1 and 100),
    -- tableau JSON de noms de statuts, tels que Jira les affiche
    finished_statuses text not null,
    updated_at        text not null
);
