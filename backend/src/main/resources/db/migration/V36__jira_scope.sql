-- 024 (research R1, R2, R8) : périmètre Jira de spec-writer.

-- Label de reconnaissance des tickets de spec-writer, par projet. Pas de ligne : pas de label.
create table project_jira_label (
    project_id text primary key references project (id) on delete cascade,
    label      text not null check (length(label) between 1 and 255),
    updated_at text not null
);

-- Labels de chaque ticket : remplacés à la synchronisation, ajoutés quand spec-writer pose le label.
create table ticket_label (
    project_id text not null references project (id) on delete cascade,
    ticket_key text not null,
    label      text not null check (length(label) between 1 and 255),
    primary key (project_id, ticket_key, label)
);

create index ticket_label_by_label on ticket_label (project_id, label);

-- Ticket lié à une spécification, supprimé dans Jira : gardé pour le dire sur la page.
create table lost_jira_link (
    session_id text primary key references session (id) on delete cascade,
    issue_key  text not null,
    lost_at    text not null
);
