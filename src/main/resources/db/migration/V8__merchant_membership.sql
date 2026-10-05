create table merchant_memberships (
    id bigserial primary key,
    merchant_id bigint not null,
    actor_id varchar(120) not null,
    role varchar(30) not null,
    active boolean not null,
    version bigint not null default 0,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint fk_memberships_merchant foreign key (merchant_id) references merchants (id),
    constraint uk_memberships_merchant_actor unique (merchant_id, actor_id),
    constraint ck_memberships_role check (role in ('VIEWER', 'OPERATOR'))
);
