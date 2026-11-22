-- Warehouse scan and reversal replay protection. Apply after the base schema.
BEGIN;

CREATE TABLE IF NOT EXISTS warehouse_picking_command (
    order_id BIGINT NOT NULL REFERENCES customer_order(order_id),
    actor_user_id BIGINT NOT NULL REFERENCES app_user(user_id),
    request_id UUID NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    result_json JSONB NOT NULL,
    PRIMARY KEY (order_id, actor_user_id, request_id)
);

COMMIT;
