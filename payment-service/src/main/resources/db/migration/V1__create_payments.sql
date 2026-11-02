CREATE TABLE payments (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    user_id UUID NOT NULL,
    attempt_number INTEGER NOT NULL,
    total_price NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(30) NOT NULL,
    provider_payment_id VARCHAR(200) UNIQUE,
    checkout_url VARCHAR(2048),
    expires_at TIMESTAMPTZ NOT NULL,
    failure_code VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uk_payments_order_attempt UNIQUE (order_id, attempt_number),
    CONSTRAINT chk_payments_attempt CHECK (attempt_number > 0),
    CONSTRAINT chk_payments_amount CHECK (total_price > 0),
    CONSTRAINT chk_payments_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_payments_status CHECK (status IN ('CREATING', 'PENDING', 'FAILED', 'SUCCEEDED')),
    CONSTRAINT chk_payments_session CHECK (
        (provider_payment_id IS NULL) = (checkout_url IS NULL)
    ),
    CONSTRAINT chk_payments_session_required CHECK (
        status NOT IN ('PENDING', 'SUCCEEDED') OR provider_payment_id IS NOT NULL
    ),
    CONSTRAINT chk_payments_failure CHECK (
        status <> 'FAILED' OR (failure_code IS NOT NULL AND length(trim(failure_code)) > 0)
    )
);

-- An active attempt or a successful charge prevents another attempt for this order.
CREATE UNIQUE INDEX uk_payments_order_active_or_paid ON payments(order_id)
    WHERE status IN ('CREATING', 'PENDING', 'SUCCEEDED');

CREATE TABLE refunds (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES payments(id),
    status VARCHAR(30) NOT NULL,
    reason VARCHAR(100) NOT NULL,
    provider_refund_id VARCHAR(200) UNIQUE,
    failure_code VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uk_refunds_payment UNIQUE (payment_id),
    CONSTRAINT chk_refunds_status CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT chk_refunds_reason CHECK (length(trim(reason)) > 0),
    CONSTRAINT chk_refunds_success CHECK (status <> 'SUCCEEDED' OR provider_refund_id IS NOT NULL),
    CONSTRAINT chk_refunds_failure CHECK (
        status <> 'FAILED' OR (failure_code IS NOT NULL AND length(trim(failure_code)) > 0)
    )
);
