create table matching_outbox (
    retry_count integer not null,
    requested_at_epoch_milli bigint not null,
    created_at datetime(6),
    modified_at datetime(6),
    id binary(16) not null,
    match_request_id binary(16) not null,
    industry varchar(255) not null,
    situation varchar(255) not null,
    status enum ('INIT','SUCCESS','FAIL') not null,
    primary key (id)
) engine=InnoDB;

create index idx_matching_outbox_status
    on matching_outbox (status);
