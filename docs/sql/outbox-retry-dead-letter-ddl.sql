-- Reference DDL for the outbox retry/dead-letter change set.
-- Actual source of truth: src/main/resources/db/migration/V2__outbox_retry_dead_letter.sql

alter table outbox_events
    add column if not exists retry_count integer not null default 0;

alter table outbox_events
    add column if not exists next_attempt_at timestamp not null default current_timestamp;

alter table outbox_events
    alter column next_attempt_at drop not null;

alter table outbox_events
    add column if not exists last_attempt_at timestamp null;

alter table outbox_events
    add column if not exists dead_lettered_at timestamp null;

alter table outbox_events
    add column if not exists failure_code varchar(100) null;

alter table outbox_events
    alter column failure_reason type varchar(1000);

create index if not exists idx_outbox_events_publishable
    on outbox_events (status, next_attempt_at, id);

create index if not exists idx_outbox_events_dead_letter
    on outbox_events (status, dead_lettered_at desc, id desc);
