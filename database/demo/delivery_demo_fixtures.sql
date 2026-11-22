BEGIN;

-- Only these local fixtures may use simulated handover actions.
CREATE TABLE IF NOT EXISTS delivery_demo_order (
    order_id BIGINT PRIMARY KEY REFERENCES customer_order(order_id),
    warehouse_user_id BIGINT NOT NULL REFERENCES app_user(user_id),
    admin_user_id BIGINT NOT NULL REFERENCES app_user(user_id)
);

COMMIT;
