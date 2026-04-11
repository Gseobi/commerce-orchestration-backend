create table orders (
    id bigserial primary key,
    customer_id varchar(100) not null,
    total_amount numeric(18, 2) not null,
    currency varchar(10) not null,
    description varchar(255),
    status varchar(50) not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create table payments (
    id bigserial primary key,
    order_id bigint not null,
    status varchar(50) not null,
    amount numeric(18, 2) not null,
    provider_reference varchar(100),
    created_at timestamp not null,
    updated_at timestamp not null
);

create index idx_payments_order_id on payments (order_id, id desc);

create table settlements (
    id bigserial primary key,
    order_id bigint not null,
    status varchar(50) not null,
    memo varchar(255),
    created_at timestamp not null,
    updated_at timestamp not null
);

create index idx_settlements_order_id on settlements (order_id, id desc);

create table notification_events (
    id bigserial primary key,
    order_id bigint not null,
    status varchar(50) not null,
    channel varchar(100),
    payload varchar(255),
    created_at timestamp not null,
    updated_at timestamp not null
);

create index idx_notification_events_order_id on notification_events (order_id, id desc);

create table audit_logs (
    id bigserial primary key,
    order_id bigint,
    action varchar(100) not null,
    detail varchar(255) not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create index idx_audit_logs_order_id on audit_logs (order_id, id desc);

create table orchestration_steps (
    id bigserial primary key,
    order_id bigint not null,
    step_type varchar(50) not null,
    status varchar(50) not null,
    detail varchar(255),
    created_at timestamp not null,
    updated_at timestamp not null
);

create index idx_orchestration_steps_order_id on orchestration_steps (order_id, id asc);

create table outbox_events (
    id bigserial primary key,
    order_id bigint not null,
    topic varchar(100) not null,
    event_type varchar(100) not null,
    payload varchar(2000) not null,
    status varchar(50) not null,
    published_at timestamp,
    failure_reason varchar(500),
    created_at timestamp not null,
    updated_at timestamp not null
);

create index idx_outbox_events_order_id on outbox_events (order_id, id asc);
