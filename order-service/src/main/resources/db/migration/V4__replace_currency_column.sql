ALTER TABLE order_items
    ADD COLUMN currency VARCHAR(3);

UPDATE order_items oi
SET currency = o.currency
FROM orders o
WHERE o.id = oi.order_id;

ALTER TABLE order_items
    ALTER COLUMN currency SET NOT NULL;

ALTER TABLE order_items
    ADD CONSTRAINT chk_order_items_currency
        CHECK (currency ~ '^[A-Z]{3}$');