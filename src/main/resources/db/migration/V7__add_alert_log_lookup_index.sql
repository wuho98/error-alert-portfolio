create index idx_alert_log_project_created_at_desc
    on alert_log (project_id, created_at desc);
