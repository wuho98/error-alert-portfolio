create table errors (
    id bigint not null auto_increment,
    project_id bigint not null,
    error_code varchar(100) not null,
    message varchar(1000) not null,
    occurred_at datetime(6) not null,
    received_at datetime(6) not null,
    primary key (id)
) engine=InnoDB default charset=utf8mb4;
