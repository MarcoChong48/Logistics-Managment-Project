-- PROPOSAL ONLY. Apply separately after approval and before enabling Idempotency-Key.
BEGIN;

CREATE TABLE client_order_idempotency (
    client_user_id      BIGINT NOT NULL REFERENCES app_user(user_id),
    idempotency_key     VARCHAR(128) NOT NULL CHECK (btrim(idempotency_key) <> ''),
    request_fingerprint BYTEA NOT NULL CHECK (octet_length(request_fingerprint) = 32),
    order_id           BIGINT NOT NULL UNIQUE REFERENCES customer_order(order_id),
    response_body      JSONB NOT NULL CHECK (jsonb_typeof(response_body) = 'object'),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (client_user_id, idempotency_key)
);

COMMIT;
