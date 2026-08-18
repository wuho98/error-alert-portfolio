alter table project_api_key
    add column active_project_id bigint
        generated always as (case when revoked_at is null then project_id end) stored,
    add constraint uk_project_api_key_active_project unique (active_project_id);
