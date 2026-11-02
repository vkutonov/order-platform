ALTER TABLE payments
    DROP CONSTRAINT chk_payments_session,
    DROP CONSTRAINT chk_payments_session_required,
    DROP COLUMN checkout_url,
    DROP COLUMN provider_payment_id;

UPDATE payments SET status = 'PENDING' WHERE status = 'CREATING';

ALTER TABLE payments
    DROP CONSTRAINT chk_payments_status,
    ADD CONSTRAINT chk_payments_status CHECK (status IN ('PENDING', 'FAILED', 'SUCCEEDED'));

DROP INDEX uk_payments_order_active_or_paid;

CREATE UNIQUE INDEX uk_payments_order_active_or_paid ON payments(order_id)
    WHERE status IN ('PENDING', 'SUCCEEDED');
