ALTER TABLE reservation_items
    DROP CONSTRAINT IF EXISTS chk_reservation_items_product_name_not_blank,
    DROP CONSTRAINT IF EXISTS chk_reservation_items_unit_price_positive,
    DROP CONSTRAINT IF EXISTS chk_reservation_items_currency_format,
    DROP CONSTRAINT IF EXISTS chk_reservation_items_total_price_positive;

ALTER TABLE reservation_items
    DROP COLUMN product_name,
    DROP COLUMN unit_price,
    DROP COLUMN currency,
    DROP COLUMN total_price;