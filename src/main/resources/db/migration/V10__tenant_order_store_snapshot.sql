ALTER TABLE stores ADD CONSTRAINT uk_stores_owner_id UNIQUE (merchant_id, id);

ALTER TABLE orders
    ADD COLUMN store_id bigint,
    ADD COLUMN store_timezone varchar(64),
    ADD COLUMN store_recovery_policy varchar(40),
    ADD CONSTRAINT fk_orders_owner_store FOREIGN KEY (merchant_id, store_id)
        REFERENCES stores (merchant_id, id),
    -- V9 fixture/import rows may lack a snapshot. Preserve them; enforce on new writes.
    ADD CONSTRAINT ck_orders_store_snapshot CHECK (
        (ownership_mode = 'LEGACY' AND store_id IS NULL
            AND store_timezone IS NULL AND store_recovery_policy IS NULL)
        OR (ownership_mode = 'TENANT' AND store_id IS NOT NULL AND store_id > 0
            AND store_timezone IS NOT NULL AND btrim(store_timezone) <> ''
            AND store_recovery_policy IS NOT NULL AND btrim(store_recovery_policy) <> '')
    ) NOT VALID;

CREATE INDEX idx_orders_owner_store ON orders (merchant_id, store_id) WHERE store_id IS NOT NULL;

CREATE FUNCTION prevent_order_store_snapshot_change() RETURNS trigger
LANGUAGE plpgsql AS $function$
BEGIN
    IF ROW(NEW.store_id, NEW.store_timezone, NEW.store_recovery_policy)
        IS DISTINCT FROM ROW(OLD.store_id, OLD.store_timezone, OLD.store_recovery_policy) THEN
        RAISE EXCEPTION 'Order store snapshot is immutable' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END
$function$;

CREATE TRIGGER trg_orders_immutable_store_snapshot BEFORE UPDATE ON orders
    FOR EACH ROW EXECUTE FUNCTION prevent_order_store_snapshot_change();
