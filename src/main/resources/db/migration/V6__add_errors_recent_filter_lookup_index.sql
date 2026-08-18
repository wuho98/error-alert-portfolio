create index idx_errors_project_error_code_occurred_at_desc
    on errors (project_id, error_code, occurred_at desc);
