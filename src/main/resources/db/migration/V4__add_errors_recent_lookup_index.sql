create index idx_errors_project_occurred_at_desc
    on errors (project_id, occurred_at desc);
