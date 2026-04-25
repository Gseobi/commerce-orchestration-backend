ALTER TABLE payments
    ADD COLUMN payment_request_id VARCHAR(100);

ALTER TABLE payments
    ADD COLUMN provider_transaction_id VARCHAR(100);

ALTER TABLE payments
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE UNIQUE INDEX uk_payments_payment_request_id
    ON payments(payment_request_id)
    WHERE payment_request_id IS NOT NULL;

CREATE UNIQUE INDEX uk_payments_provider_transaction_id
    ON payments(provider_transaction_id)
    WHERE provider_transaction_id IS NOT NULL;
