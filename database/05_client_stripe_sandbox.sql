-- Additive migration. Run only on an explicitly approved sandbox database after 04.
BEGIN;

CREATE TABLE client_stripe_checkout (
    payment_id BIGINT PRIMARY KEY REFERENCES payment(payment_id),
    session_id VARCHAR(160) UNIQUE,
    claimed_at TIMESTAMPTZ NOT NULL DEFAULT statement_timestamp(),
    last_checked_at TIMESTAMPTZ
);

CREATE TABLE client_stripe_event (
    event_id VARCHAR(160) PRIMARY KEY,
    payment_id BIGINT NOT NULL REFERENCES client_stripe_checkout(payment_id),
    session_id VARCHAR(160) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT statement_timestamp(),
    last_checked_at TIMESTAMPTZ,
    processed_at TIMESTAMPTZ
);

CREATE INDEX idx_stripe_event_pending ON client_stripe_event (last_checked_at NULLS FIRST, received_at)
    WHERE processed_at IS NULL;

COMMIT;
