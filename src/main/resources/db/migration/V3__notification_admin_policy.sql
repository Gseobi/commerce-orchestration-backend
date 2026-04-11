alter table notification_events
    add column handling_policy varchar(50) not null default 'NONE';

alter table notification_events
    add column retry_count integer not null default 0;

alter table notification_events
    add column next_attempt_at timestamp null;

alter table notification_events
    add column last_attempt_at timestamp null;

alter table notification_events
    add column failure_code varchar(100) null;

alter table notification_events
    add column failure_reason varchar(255) null;

create index idx_notification_events_status_action
    on notification_events (status, next_attempt_at, id desc);
