CREATE TABLE processed_events (
            event_id UUID PRIMARY KEY,

            event_type VARCHAR(150) NOT NULL,
            aggregate_id UUID NOT NULL,

            processed_at TIMESTAMPTZ NOT NULL,

            CONSTRAINT chk_processed_events_event_type_not_blank
                CHECK (length(trim(event_type)) > 0)
);