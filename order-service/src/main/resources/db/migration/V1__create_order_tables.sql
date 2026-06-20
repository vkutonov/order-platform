CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Main order table.
-- One row - one order
CREATE TABLE orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id UUID NOT NULL,
    status VARCHAR(40) NOT NULL,
    total_price NUMERIC(19,2) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'RUB',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- JPA @Version for optimistic locking.
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT chk_orders_status CHECK (
        status IN (
            'WAITING_FOR_INVENTORY',
            'WAITING_FOR_PAYMENT',
            'PAID',
            'PAYMENT_FAILED',
            'CANCELLED'
        )
    ),

    CONSTRAINT chk_orders_total_price CHECK ( total_price >= 0 )

);


-- Order item table
-- One row - one product inside one order.
-- This table stores snapshot at the moment of order creation
CREATE TABLE order_items (
    id UUID NOT NULL DEFAULT gen_random_uuid(),

    order_id UUID NOT NULL,
    product_id UUID NOT NULL,
    unit_price NUMERIC(19, 2) NOT NULL,
    quantity INTEGER NOT NULL,
    total_price NUMERIC(19, 2) NOT NULL,

    CONSTRAINT fk_order_items_order
        FOREIGN KEY (order_id)
        REFERENCES orders(id)
        ON DELETE CASCADE,

    CONSTRAINT chk_order_items_unit_price CHECK ( unit_price >= 0 ),
    CONSTRAINT chk_order_items_quantity CHECK ( quantity > 0 ),
    CONSTRAINT chk_order_items_total_price CHECK ( total_price >= 0 )
);


-- Order lifecycle history.
-- Stores lifecycle changes of an order.
CREATE TABLE order_status_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    order_id UUID NOT NULL,

    -- For the first status change old_status can be null.
    old_status VARCHAR(40),

    new_status VARCHAR(40) NOT NULL,

    -- Human-readable or technical reason.
    reason VARCHAR(255),

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_order_status_history_order
        FOREIGN KEY (order_id)
        REFERENCES orders(id)
        ON DELETE CASCADE,

    CONSTRAINT chk_order_status_history_old_status CHECK (
        old_status IS NULL OR old_status IN (
                                             'WAITING_FOR_INVENTORY',
                                             'WAITING_FOR_PAYMENT',
                                             'PAID',
                                             'PAYMENT_FAILED',
                                             'CANCELLED'
            )
        ),

    CONSTRAINT chk_order_status_history_new_status CHECK (
        new_status IN (
                       'WAITING_FOR_INVENTORY',
                       'WAITING_FOR_PAYMENT',
                       'PAID',
                       'PAYMENT_FAILED',
                       'CANCELLED'
            )
        )

);

-- Needed for: GET /api/orders/user/{userId}
CREATE INDEX idx_orders_user_id ON orders(user_id);

-- Needed for loading items of one order.
CREATE INDEX idx_order_items_order_id ON order_items(order_id);

-- Needed for loading status history of one order.
CREATE INDEX idx_order_status_history_order_id ON order_status_history(order_id);