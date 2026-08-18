create table alert_log (
    id bigint not null auto_increment,
    project_id bigint not null,
    error_code varchar(100) not null,
    window_started_at datetime(6) not null,
    observed_count int not null,
    threshold int not null,
    status varchar(20) not null,
    retry_count int not null,
    sent_at datetime(6),
    created_at datetime(6) not null,
    primary key (id),
    constraint uk_alert_log_project_error_window
        unique (project_id, error_code, window_started_at),
    constraint fk_alert_log_project foreign key (project_id) references project (id)
) engine=InnoDB default charset=utf8mb4;
