CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,

    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,

    payload JSON NOT NULL,

    status VARCHAR(30) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ,
    published_at TIMESTAMPTZ,

    error_message TEXT,

    CONSTRAINT chk_outbox_events_status
            CHECK ( status IN ('NEW', 'PROCESSING', 'PUBLISHED' , 'FAILED') )
);

CREATE INDEX idx_outbox_events_status_created_at
    ON outbox_events(status, created_at);