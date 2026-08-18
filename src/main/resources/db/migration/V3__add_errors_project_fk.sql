-- Existing rows must have matching project ids before this migration is applied:
-- select e.project_id, count(*)
-- from errors e
-- left join project p on p.id = e.project_id
-- where p.id is null
-- group by e.project_id;

alter table errors
    add constraint fk_errors_project
        foreign key (project_id) references project (id);
