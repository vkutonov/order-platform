CREATE TABLE order_payment_results (
    payment_id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id),
    status VARCHAR(20) NOT NULL,
    failure_code VARCHAR(100),
    occurred_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT chk_order_payment_results_status CHECK (status IN ('SUCCEEDED', 'FAILED')),
    CONSTRAINT chk_order_payment_results_failure CHECK (
        (status = 'SUCCEEDED' AND failure_code IS NULL)
        OR (status = 'FAILED' AND failure_code IS NOT NULL AND length(trim(failure_code)) > 0)
    )
);

CREATE INDEX idx_order_payment_results_order_id ON order_payment_results(order_id);
