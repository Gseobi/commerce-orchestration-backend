create table merchants (
    id bigserial primary key,
    code varchar(50) not null,
    name varchar(120) not null,
    status varchar(30) not null,
    timezone varchar(64) not null,
    default_recovery_policy varchar(40) not null,
    version bigint not null default 0,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint uk_merchants_code unique (code)
);

create table stores (
    id bigserial primary key,
    merchant_id bigint not null,
    code varchar(50) not null,
    name varchar(120) not null,
    status varchar(30) not null,
    timezone varchar(64) not null,
    recovery_policy varchar(40) not null,
    version bigint not null default 0,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint fk_stores_merchant foreign key (merchant_id) references merchants (id),
    constraint uk_stores_merchant_code unique (merchant_id, code)
);

create index idx_stores_merchant_status on stores (merchant_id, status, id);
