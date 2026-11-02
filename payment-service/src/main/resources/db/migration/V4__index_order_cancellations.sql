CREATE INDEX ix_processed_events_aggregate_type ON processed_events (aggregate_id, event_type);
CREATE INDEX ix_payments_pending_expiration ON payments (expires_at, id) WHERE status = 'PENDING';
