-- Logical baseline from LMS Database Draft v0.4. Status and role labels stay open.
-- A database maintainer applies migrations to the approved database.
CREATE SCHEMA lms_delivery;
REVOKE ALL ON SCHEMA lms_delivery FROM PUBLIC;

-- Prefix the account table and qualify customer orders to avoid SQL keywords.
CREATE TABLE lms_delivery.lms_user (
    user_id varchar(60) PRIMARY KEY,
    username varchar(120) NOT NULL UNIQUE,
    password_hash varchar(100) NOT NULL,
    role text NOT NULL,
    account_status text NOT NULL,
    failed_login_count integer NOT NULL DEFAULT 0 CHECK (failed_login_count >= 0),
    lock_until timestamptz
);

CREATE TABLE lms_delivery.client_profile (
    user_id varchar(60) PRIMARY KEY REFERENCES lms_delivery.lms_user (user_id),
    default_recipient_name varchar(120),
    default_phone varchar(80),
    default_address varchar(600)
);

CREATE TABLE lms_delivery.delivery_worker_profile (
    user_id varchar(60) PRIMARY KEY REFERENCES lms_delivery.lms_user (user_id),
    employee_number varchar(60) NOT NULL UNIQUE,
    vehicle_type varchar(60),
    vehicle_plate varchar(60),
    equipment_note varchar(600)
);

CREATE TABLE lms_delivery.product (
    product_id varchar(60) PRIMARY KEY,
    sku varchar(60) NOT NULL UNIQUE,
    name varchar(150) NOT NULL,
    current_price numeric(12, 2) NOT NULL CHECK (current_price >= 0),
    low_stock_threshold integer NOT NULL CHECK (low_stock_threshold >= 0),
    listing_status text NOT NULL
);

CREATE TABLE lms_delivery.inventory (
    product_id varchar(60) PRIMARY KEY REFERENCES lms_delivery.product (product_id),
    physical_stock integer NOT NULL CHECK (physical_stock >= 0),
    reserved_stock integer NOT NULL CHECK (reserved_stock >= 0)
);

-- Actual counts may fall below reservations during a shortage.
CREATE TABLE lms_delivery.customer_order (
    order_id varchar(60) PRIMARY KEY,
    order_reference varchar(60) NOT NULL UNIQUE,
    client_user_id varchar(60) NOT NULL REFERENCES lms_delivery.lms_user (user_id),
    order_status text NOT NULL,
    created_time timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    payment_deadline timestamptz NOT NULL,
    total_amount numeric(12, 2) NOT NULL CHECK (total_amount >= 0),
    CHECK (payment_deadline >= created_time)
);

CREATE TABLE lms_delivery.order_item (
    order_item_id varchar(60) PRIMARY KEY,
    order_id varchar(60) NOT NULL REFERENCES lms_delivery.customer_order (order_id),
    product_id varchar(60) NOT NULL REFERENCES lms_delivery.product (product_id),
    quantity integer NOT NULL CHECK (quantity > 0),
    unit_price numeric(12, 2) NOT NULL CHECK (unit_price >= 0)
);

CREATE TABLE lms_delivery.order_delivery_detail (
    delivery_detail_id varchar(60) PRIMARY KEY,
    order_id varchar(60) NOT NULL REFERENCES lms_delivery.customer_order (order_id),
    version_no integer NOT NULL CHECK (version_no > 0),
    recipient_name varchar(120) NOT NULL,
    phone varchar(80) NOT NULL,
    address varchar(600) NOT NULL,
    created_time timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (order_id, version_no)
);

CREATE TABLE lms_delivery.payment (
    payment_id varchar(60) PRIMARY KEY,
    order_id varchar(60) NOT NULL REFERENCES lms_delivery.customer_order (order_id),
    amount numeric(12, 2) NOT NULL CHECK (amount >= 0),
    payment_status text NOT NULL,
    provider_reference text,
    created_time timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_time timestamptz,
    CHECK (ended_time IS NULL OR ended_time >= created_time)
);

CREATE TABLE lms_delivery.refund (
    refund_id varchar(60) PRIMARY KEY,
    payment_id varchar(60) NOT NULL REFERENCES lms_delivery.payment (payment_id),
    amount numeric(12, 2) NOT NULL CHECK (amount > 0),
    refund_status text NOT NULL,
    provider_reference text,
    created_time timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE lms_delivery.stock_reservation (
    reservation_id varchar(60) PRIMARY KEY,
    order_item_id varchar(60) NOT NULL REFERENCES lms_delivery.order_item (order_item_id),
    payment_id varchar(60) REFERENCES lms_delivery.payment (payment_id),
    quantity integer NOT NULL CHECK (quantity > 0),
    status text NOT NULL,
    reserved_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    released_at timestamptz,
    CHECK (released_at IS NULL OR released_at >= reserved_at)
);

CREATE TABLE lms_delivery.picking_task (
    picking_task_id varchar(60) PRIMARY KEY,
    order_id varchar(60) NOT NULL UNIQUE REFERENCES lms_delivery.customer_order (order_id),
    task_status text NOT NULL,
    start_time timestamptz,
    confirm_packing_time timestamptz,
    CHECK (confirm_packing_time IS NULL OR (start_time IS NOT NULL AND confirm_packing_time >= start_time))
);

CREATE TABLE lms_delivery.picking_assignment_history (
    assignment_id varchar(60) PRIMARY KEY,
    picking_task_id varchar(60) NOT NULL REFERENCES lms_delivery.picking_task (picking_task_id),
    worker_user_id varchar(60) NOT NULL REFERENCES lms_delivery.lms_user (user_id),
    assigned_time timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_time timestamptz,
    CHECK (ended_time IS NULL OR ended_time >= assigned_time)
);

-- The draft references an order item for every scan. Unknown-code audit needs a later Warehouse decision.
CREATE TABLE lms_delivery.picking_scan_history (
    scan_id varchar(60) PRIMARY KEY,
    picking_task_id varchar(60) NOT NULL REFERENCES lms_delivery.picking_task (picking_task_id),
    order_item_id varchar(60) NOT NULL REFERENCES lms_delivery.order_item (order_item_id),
    worker_user_id varchar(60) NOT NULL REFERENCES lms_delivery.lms_user (user_id),
    scan_time timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    result text NOT NULL,
    original_scan_id varchar(60) REFERENCES lms_delivery.picking_scan_history (scan_id),
    CHECK (original_scan_id IS NULL OR original_scan_id <> scan_id)
);

CREATE TABLE lms_delivery.package (
    package_id varchar(60) PRIMARY KEY,
    order_id varchar(60) NOT NULL UNIQUE REFERENCES lms_delivery.customer_order (order_id),
    package_qr_code varchar(80) NOT NULL UNIQUE,
    created_time timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    current_handler_user_id varchar(60) REFERENCES lms_delivery.lms_user (user_id)
);

CREATE TABLE lms_delivery.stock_movement (
    movement_id varchar(60) PRIMARY KEY,
    product_id varchar(60) NOT NULL REFERENCES lms_delivery.product (product_id),
    package_id varchar(60) REFERENCES lms_delivery.package (package_id),
    actor_user_id varchar(60) REFERENCES lms_delivery.lms_user (user_id),
    movement_type text NOT NULL,
    quantity_delta integer NOT NULL,
    event_time timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE lms_delivery.pickup_list (
    pickup_list_id varchar(60) PRIMARY KEY,
    list_qr_code varchar(80) NOT NULL UNIQUE,
    created_by_worker_id varchar(60) NOT NULL REFERENCES lms_delivery.lms_user (user_id),
    created_time timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status text NOT NULL,
    claimed_by_delivery_worker_id varchar(60) REFERENCES lms_delivery.lms_user (user_id),
    claimed_time timestamptz,
    CHECK (claimed_time IS NULL OR claimed_time >= created_time)
);

CREATE TABLE lms_delivery.pickup_list_item (
    pickup_list_id varchar(60) NOT NULL REFERENCES lms_delivery.pickup_list (pickup_list_id),
    package_id varchar(60) NOT NULL REFERENCES lms_delivery.package (package_id),
    collection_outcome text NOT NULL,
    collected_time timestamptz,
    cannot_collect_reason text,
    PRIMARY KEY (pickup_list_id, package_id)
);

CREATE TABLE lms_delivery.package_custody_event (
    custody_event_id varchar(60) PRIMARY KEY,
    package_id varchar(60) NOT NULL REFERENCES lms_delivery.package (package_id),
    event_type text NOT NULL,
    actor_user_id varchar(60) REFERENCES lms_delivery.lms_user (user_id),
    pickup_list_id varchar(60) REFERENCES lms_delivery.pickup_list (pickup_list_id),
    event_time timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    details text
);

CREATE TABLE lms_delivery.delivery_attempt (
    delivery_attempt_id varchar(60) PRIMARY KEY,
    package_id varchar(60) NOT NULL REFERENCES lms_delivery.package (package_id),
    delivery_detail_id varchar(60) NOT NULL REFERENCES lms_delivery.order_delivery_detail (delivery_detail_id),
    delivery_worker_id varchar(60) NOT NULL REFERENCES lms_delivery.lms_user (user_id),
    start_time timestamptz NOT NULL,
    end_time timestamptz,
    outcome text NOT NULL,
    failure_reason text,
    internal_note varchar(600),
    CHECK (end_time IS NULL OR end_time >= start_time)
);

CREATE TABLE lms_delivery.delivery_proof (
    proof_id varchar(60) PRIMARY KEY,
    delivery_attempt_id varchar(60) NOT NULL REFERENCES lms_delivery.delivery_attempt (delivery_attempt_id),
    proof_type text NOT NULL,
    proof_reference text NOT NULL,
    created_time timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE lms_delivery.client_instruction (
    instruction_id varchar(60) PRIMARY KEY,
    order_id varchar(60) NOT NULL REFERENCES lms_delivery.customer_order (order_id),
    client_user_id varchar(60) NOT NULL REFERENCES lms_delivery.lms_user (user_id),
    instruction_type text NOT NULL,
    status text NOT NULL,
    submitted_time timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_by_admin_id varchar(60) REFERENCES lms_delivery.lms_user (user_id),
    resolved_time timestamptz,
    CHECK (resolved_time IS NULL OR resolved_time >= submitted_time)
);

CREATE TABLE lms_delivery.notification (
    notification_id varchar(60) PRIMARY KEY,
    recipient_user_id varchar(60) NOT NULL REFERENCES lms_delivery.lms_user (user_id),
    order_id varchar(60) REFERENCES lms_delivery.customer_order (order_id),
    type text NOT NULL,
    message varchar(600) NOT NULL,
    created_time timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_status boolean NOT NULL DEFAULT FALSE
);

CREATE TABLE lms_delivery.audit_log (
    audit_id varchar(60) PRIMARY KEY,
    actor_user_id varchar(60) REFERENCES lms_delivery.lms_user (user_id),
    action text NOT NULL,
    target_type text NOT NULL,
    target_id varchar(80) NOT NULL,
    old_value jsonb,
    new_value jsonb,
    event_time timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    result text NOT NULL
);

CREATE TABLE lms_delivery.security_log (
    security_log_id varchar(60) PRIMARY KEY,
    user_id varchar(60) REFERENCES lms_delivery.lms_user (user_id),
    event_type text NOT NULL,
    event_time timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    result text NOT NULL,
    ip_address varchar(100),
    device_info varchar(300),
    details text
);

CREATE INDEX order_item_order_idx ON lms_delivery.order_item (order_id);
CREATE INDEX payment_order_idx ON lms_delivery.payment (order_id);
CREATE INDEX refund_payment_idx ON lms_delivery.refund (payment_id);
CREATE INDEX stock_reservation_item_idx ON lms_delivery.stock_reservation (order_item_id);
CREATE INDEX pickup_list_item_package_idx ON lms_delivery.pickup_list_item (package_id);
CREATE INDEX package_custody_event_package_idx ON lms_delivery.package_custody_event (package_id);
CREATE INDEX delivery_attempt_package_idx ON lms_delivery.delivery_attempt (package_id);
CREATE INDEX delivery_proof_attempt_idx ON lms_delivery.delivery_proof (delivery_attempt_id);
CREATE INDEX client_instruction_order_idx ON lms_delivery.client_instruction (order_id);
