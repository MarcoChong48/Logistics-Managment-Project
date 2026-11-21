-- Add durable notification identity without changing historical notification rows.
BEGIN;

ALTER TABLE notification
    ADD COLUMN source_event_id BIGINT,
    ADD CONSTRAINT chk_notification_event_source CHECK (
        source_event_id IS NULL OR (order_id IS NOT NULL AND source_event_id > 0)
    );

CREATE UNIQUE INDEX uq_notification_order_event
    ON notification (order_id, type, source_event_id) WHERE source_event_id IS NOT NULL;

COMMIT;
