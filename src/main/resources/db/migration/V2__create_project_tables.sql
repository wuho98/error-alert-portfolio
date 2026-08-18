create table project (
    id bigint not null auto_increment,
    name varchar(255) not null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id)
) engine=InnoDB default charset=utf8mb4;

create table project_setting (
    id bigint not null auto_increment,
    project_id bigint not null,
    threshold int not null,
    window_seconds int not null,
    cooldown_seconds int not null,
    webhook_url varchar(255),
    webhook_enabled bit(1) not null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id),
    constraint uk_project_setting_project_id unique (project_id),
    constraint fk_project_setting_project foreign key (project_id) references project (id)
) engine=InnoDB default charset=utf8mb4;

create table project_api_key (
    id bigint not null auto_increment,
    project_id bigint not null,
    api_key_hash varchar(255) not null,
    created_at datetime(6) not null,
    revoked_at datetime(6),
    primary key (id),
    constraint uk_project_api_key_api_key_hash unique (api_key_hash),
    constraint fk_project_api_key_project foreign key (project_id) references project (id)
) engine=InnoDB default charset=utf8mb4;
