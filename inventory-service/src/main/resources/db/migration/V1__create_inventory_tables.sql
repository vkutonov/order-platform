CREATE TABLE products (
		id UUID PRIMARY KEY,

		name VARCHAR(255) NOT NULL,
		description TEXT,

		unit_price NUMERIC(19, 2) NOT NULL,
		currency VARCHAR(3) NOT NULL,

		status VARCHAR(30) NOT NULL,

		created_at TIMESTAMPTZ NOT NULL,
		updated_at TIMESTAMPTZ NOT NULL,

		CONSTRAINT chk_products_name_not_blank
			CHECK (length(trim(name)) > 0),

		CONSTRAINT chk_products_unit_price_positive
			CHECK (unit_price > 0),

		CONSTRAINT chk_products_currency_format
			CHECK (currency ~ '^[A-Z]{3}$'),

		CONSTRAINT chk_products_status
			CHECK (status IN ('ACTIVE', 'INACTIVE', 'DELETED'))
);

CREATE TABLE inventory_items (
		id UUID PRIMARY KEY,

		product_id UUID NOT NULL UNIQUE,

		quantity_on_hand INTEGER NOT NULL,
		reserved_quantity INTEGER NOT NULL,

		created_at TIMESTAMPTZ NOT NULL,
		updated_at TIMESTAMPTZ NOT NULL,

		-- Optimistic locking защищает остатки от lost update.
		version BIGINT NOT NULL DEFAULT 0,

		CONSTRAINT fk_inventory_items_product
			FOREIGN KEY (product_id)
			REFERENCES products (id),

		CONSTRAINT chk_inventory_items_quantity_on_hand_non_negative
			CHECK (quantity_on_hand >= 0),

		CONSTRAINT chk_inventory_items_reserved_quantity_non_negative
			CHECK (reserved_quantity >= 0),

		CONSTRAINT chk_inventory_items_reserved_not_greater_than_on_hand
			CHECK (reserved_quantity <= quantity_on_hand)
);

CREATE TABLE reservations (
		id UUID PRIMARY KEY,

		order_id UUID NOT NULL UNIQUE,
		user_id UUID NOT NULL,

		status VARCHAR(30) NOT NULL,
		failure_reason TEXT,

		expires_at TIMESTAMPTZ,

		created_at TIMESTAMPTZ NOT NULL,
		updated_at TIMESTAMPTZ NOT NULL,

		CONSTRAINT chk_reservations_status
			CHECK (status IN ('PENDING', 'RESERVED', 'FAILED', 'COMMITTED', 'RELEASED', 'EXPIRED'))
);

-- Snapshot фиксирует данные товара на момент создания reservation.
CREATE TABLE reservation_items (
		id UUID PRIMARY KEY,

		reservation_id UUID NOT NULL,
		product_id UUID NOT NULL,

		product_name VARCHAR(255) NOT NULL,

		unit_price NUMERIC(19, 2) NOT NULL,
		currency VARCHAR(3) NOT NULL,

		quantity INTEGER NOT NULL,
		total_price NUMERIC(19, 2) NOT NULL,

		CONSTRAINT fk_reservation_items_reservation
			FOREIGN KEY (reservation_id)
			REFERENCES reservations (id)
			ON DELETE CASCADE,

		CONSTRAINT fk_reservation_items_product
			FOREIGN KEY (product_id)
			REFERENCES products (id),

		CONSTRAINT uq_reservation_items_reservation_product
			UNIQUE (reservation_id, product_id),

		CONSTRAINT chk_reservation_items_product_name_not_blank
			CHECK (length(trim(product_name)) > 0),

		CONSTRAINT chk_reservation_items_unit_price_positive
			CHECK (unit_price > 0),

		CONSTRAINT chk_reservation_items_currency_format
			CHECK (currency ~ '^[A-Z]{3}$'),

		CONSTRAINT chk_reservation_items_quantity_positive
			CHECK (quantity > 0),

		CONSTRAINT chk_reservation_items_total_price_positive
			CHECK (total_price > 0)
);

-- Inbox для идемпотентной обработки Kafka events.
CREATE TABLE processed_events (
		event_id UUID PRIMARY KEY,

		event_type VARCHAR(150) NOT NULL,
		aggregate_id UUID NOT NULL,

		processed_at TIMESTAMPTZ NOT NULL,

		CONSTRAINT chk_processed_events_event_type_not_blank
			CHECK (length(trim(event_type)) > 0)
);

-- Индексы под основные query и cleanup jobs.
CREATE INDEX idx_products_status
		ON products (status);

CREATE INDEX idx_reservations_status_expires_at
		ON reservations (status, expires_at);

CREATE INDEX idx_reservation_items_product_id
		ON reservation_items (product_id);

CREATE INDEX idx_processed_events_aggregate_id
		ON processed_events (aggregate_id);

CREATE INDEX idx_processed_events_processed_at
		ON processed_events (processed_at);
