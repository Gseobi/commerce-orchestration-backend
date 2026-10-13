-- Expand phase: preserve legacy rows; tenant writers are not enabled yet.
DO $migration$
DECLARE
    target_table text;
BEGIN
    FOREACH target_table IN ARRAY ARRAY['orders', 'payments', 'settlements', 'notification_events',
            'outbox_events', 'orchestration_steps', 'audit_logs']
    LOOP
        EXECUTE format('ALTER TABLE %I
            ADD COLUMN ownership_mode varchar(10) NOT NULL DEFAULT ''LEGACY'',
            ADD COLUMN merchant_id bigint,
            ADD CONSTRAINT %I CHECK (
                (ownership_mode = ''LEGACY'' AND merchant_id IS NULL) OR
                (ownership_mode = ''TENANT'' AND merchant_id IS NOT NULL AND merchant_id > 0)),
            ADD CONSTRAINT %I FOREIGN KEY (merchant_id) REFERENCES merchants(id)',
            target_table, 'ck_' || target_table || '_ownership', 'fk_' || target_table || '_owner');
        EXECUTE format('CREATE INDEX %I ON %I (merchant_id, id) WHERE ownership_mode = ''TENANT''',
            'idx_' || target_table || '_tenant', target_table);
    END LOOP;
END
$migration$;

ALTER TABLE orders
    ADD CONSTRAINT uk_orders_owner_id UNIQUE (merchant_id, id),
    ADD CONSTRAINT uk_orders_scope_id UNIQUE (ownership_mode, id);

DO $migration$
DECLARE
    target_table text;
BEGIN
    FOREACH target_table IN ARRAY ARRAY['payments', 'settlements', 'notification_events',
            'outbox_events', 'orchestration_steps', 'audit_logs']
    LOOP
        -- The scope FK preserves historical orphan rows but checks all new references.
        EXECUTE format('ALTER TABLE %I
            ADD CONSTRAINT %I CHECK (ownership_mode <> ''TENANT'' OR order_id IS NOT NULL),
            ADD CONSTRAINT %I FOREIGN KEY (merchant_id, order_id) REFERENCES orders(merchant_id, id),
            ADD CONSTRAINT %I FOREIGN KEY (ownership_mode, order_id)
                REFERENCES orders(ownership_mode, id) NOT VALID',
            target_table, 'ck_' || target_table || '_tenant_order',
            'fk_' || target_table || '_owner_order', 'fk_' || target_table || '_scope_order');
    END LOOP;
END
$migration$;

CREATE FUNCTION prevent_commerce_owner_change() RETURNS trigger
LANGUAGE plpgsql AS $function$
BEGIN
    IF NEW.ownership_mode IS DISTINCT FROM OLD.ownership_mode
            OR NEW.merchant_id IS DISTINCT FROM OLD.merchant_id THEN
        RAISE EXCEPTION 'Commerce ownership is immutable' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END
$function$;

DO $migration$
DECLARE
    target_table text;
BEGIN
    FOREACH target_table IN ARRAY ARRAY['orders', 'payments', 'settlements', 'notification_events',
            'outbox_events', 'orchestration_steps', 'audit_logs']
    LOOP
        EXECUTE format('CREATE TRIGGER %I BEFORE UPDATE ON %I
            FOR EACH ROW EXECUTE FUNCTION prevent_commerce_owner_change()',
            'trg_' || target_table || '_immutable_owner', target_table);
    END LOOP;
END
$migration$;
