alter table outbox_events
    add column retry_count integer not null default 0;

alter table outbox_events
    add column next_attempt_at timestamp not null default current_timestamp;

alter table outbox_events
    add column last_attempt_at timestamp null;

alter table outbox_events
    add column dead_lettered_at timestamp null;

alter table outbox_events
    add column failure_code varchar(100) null;

alter table outbox_events
    alter column failure_reason type varchar(1000);

create index idx_outbox_events_publishable
    on outbox_events (status, next_attempt_at, id);

create index idx_outbox_events_dead_letter
    on outbox_events (status, dead_lettered_at desc, id desc);
