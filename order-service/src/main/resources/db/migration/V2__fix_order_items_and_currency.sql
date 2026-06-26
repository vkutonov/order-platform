-- Fix order_items primary key.
-- In V1 id was NOT NULL, but PRIMARY KEY was missed.
ALTER TABLE order_items
    ADD CONSTRAINT pk_order_items PRIMARY KEY (id);

-- Add product name snapshot.
-- We add it as nullable first, backfill existing rows, then make it NOT NULL.
-- This is safer than adding NOT NULL immediately.
ALTER TABLE order_items
    ADD COLUMN product_name VARCHAR(255);


UPDATE order_items
SET product_name = 'UNKNOWN_PRODUCT'
WHERE product_name IS NULL;

ALTER TABLE order_items
    ALTER COLUMN product_name SET NOT NULL;

-- Change currency from CHAR(3) to VARCHAR(3).
-- trim(currency) removes possible padding spaces from CHAR(3).
ALTER TABLE orders
    ALTER COLUMN currency TYPE VARCHAR(3)
    USING trim(currency);

ALTER TABLE orders
    ALTER COLUMN currency SET DEFAULT 'RUB';

ALTER TABLE orders
    ADD CONSTRAINT chk_orders_currency
    CHECK ( currency ~ '^[A-Z]{3}$' );