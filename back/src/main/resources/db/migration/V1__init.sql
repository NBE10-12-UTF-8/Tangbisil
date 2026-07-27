create table member (
    is_suspended bit not null,
    created_at datetime(6),
    modified_at datetime(6),
    refresh_token_expires_at datetime(6),
    id binary(16) not null,
    refresh_token binary(16),
    email varchar(255),
    industry varchar(255),
    password varchar(255),
    provider_id varchar(255),
    role varchar(255),
    provider enum ('GOOGLE','KAKAO','LOCAL'),
    primary key (id)
) engine=InnoDB;

create table chat_room (
    max_participants integer,
    closed_at datetime(6),
    created_at datetime(6),
    modified_at datetime(6),
    id binary(16) not null,
    status enum ('ACTIVE','CLOSED'),
    primary key (id)
) engine=InnoDB;

create table chat_room_participant (
    created_at datetime(6),
    joined_at datetime(6),
    modified_at datetime(6),
    id binary(16) not null,
    member_id binary(16) not null,
    room_id binary(16) not null,
    nickname varchar(255),
    primary key (id)
) engine=InnoDB;

create table chat_message (
    created_at datetime(6),
    modified_at datetime(6),
    id binary(16) not null,
    participant_id binary(16) not null,
    room_id binary(16) not null,
    content TEXT not null,
    primary key (id)
) engine=InnoDB;

create table match_request (
    created_at datetime(6),
    modified_at datetime(6),
    requested_at datetime(6),
    version bigint not null,
    id binary(16) not null,
    member_id binary(16),
    room_id binary(16),
    industry varchar(255),
    situation varchar(255),
    status enum ('MATCHED','PENDING'),
    primary key (id)
) engine=InnoDB;

create table report (
    created_at datetime(6),
    modified_at datetime(6),
    id binary(16) not null,
    reported_id binary(16),
    reported_message_id binary(16),
    reporter_id binary(16),
    room_id binary(16),
    reason TEXT,
    status enum ('PENDING','PROCESSED'),
    primary key (id)
) engine=InnoDB;

create table reported_message (
    is_target bit not null,
    created_at datetime(6),
    modified_at datetime(6),
    sent_at datetime(6),
    id binary(16) not null,
    report_id binary(16),
    sender_member_id binary(16),
    content TEXT,
    sender_nickname varchar(255),
    primary key (id)
) engine=InnoDB;

create index idx_match_request_status_room_id
    on match_request (status, room_id);

alter table member
    add constraint uk_member_provider_provider_id unique (provider, provider_id);

alter table member
    add constraint uk_member_refresh_token unique (refresh_token);

alter table member
    add constraint uk_member_email unique (email);

alter table chat_room_participant
    add constraint fk_chat_room_participant_room
    foreign key (room_id) references chat_room (id);

alter table chat_room_participant
    add constraint fk_chat_room_participant_member
    foreign key (member_id) references member (id);

alter table chat_message
    add constraint fk_chat_message_room
    foreign key (room_id) references chat_room (id);

alter table chat_message
    add constraint fk_chat_message_participant
    foreign key (participant_id) references chat_room_participant (id);

alter table match_request
    add constraint fk_match_request_member
    foreign key (member_id) references member (id);

alter table match_request
    add constraint fk_match_request_room
    foreign key (room_id) references chat_room (id);

alter table report
    add constraint fk_report_reporter
    foreign key (reporter_id) references member (id) on delete set null;

alter table report
    add constraint fk_report_reported
    foreign key (reported_id) references member (id) on delete set null;

alter table report
    add constraint fk_report_room
    foreign key (room_id) references chat_room (id);

alter table reported_message
    add constraint fk_reported_message_report
    foreign key (report_id) references report (id);
