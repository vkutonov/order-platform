ALTER TABLE reservations
    ADD COLUMN failure_code VARCHAR(50);

UPDATE reservations
SET failure_code = 'INSUFFICIENT_STOCK'
WHERE status = 'FAILED';

ALTER TABLE reservations
    DROP COLUMN failure_reason;

ALTER TABLE reservations
    ADD CONSTRAINT chk_reservations_failure_code
        CHECK (
            failure_code IS NULL
            OR failure_code IN ('INSUFFICIENT_STOCK', 'PRODUCT_INACTIVE')
        ),
    ADD CONSTRAINT chk_reservations_failure_consistency
        CHECK (
            (status = 'FAILED' AND failure_code IS NOT NULL)
            OR (status <> 'FAILED' AND failure_code IS NULL)
        );
