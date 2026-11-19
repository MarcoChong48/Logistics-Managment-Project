-- Delivery extensions preserve the v0.4 core tables and their data.
-- Java uses the private lms_delivery_runtime schema for its current JDBC names.
CREATE SCHEMA lms_delivery_runtime;
REVOKE ALL ON SCHEMA lms_delivery_runtime FROM PUBLIC;

ALTER TABLE lms_delivery.lms_user
    ADD COLUMN display_name varchar(120);

-- Delivery sign-in is case-insensitive and must identify one account.
CREATE UNIQUE INDEX lms_user_username_lower_idx ON lms_delivery.lms_user (lower(username));

ALTER TABLE lms_delivery.inventory
    ADD COLUMN unavailable_stock integer NOT NULL DEFAULT 0 CHECK (unavailable_stock >= 0);

-- Only verified checkout acceptance makes a real order eligible for pickup.
ALTER TABLE lms_delivery.payment
    ADD COLUMN accepted_at timestamptz,
    ADD COLUMN is_demo_fixture boolean NOT NULL DEFAULT FALSE,
    ADD CHECK (accepted_at IS NULL OR accepted_at >= created_time);

CREATE UNIQUE INDEX payment_one_accepted_order_idx
    ON lms_delivery.payment (order_id) WHERE accepted_at IS NOT NULL;

ALTER TABLE lms_delivery.order_delivery_detail
    ADD UNIQUE (order_id, delivery_detail_id);

ALTER TABLE lms_delivery.pickup_list
    ADD COLUMN last_activity_at timestamptz;

ALTER TABLE lms_delivery.pickup_list_item
    ADD COLUMN internal_note varchar(600);

ALTER TABLE lms_delivery.stock_movement
    ADD COLUMN source_id varchar(60),
    ADD COLUMN reserved_delta integer NOT NULL DEFAULT 0,
    ADD COLUMN unavailable_delta integer NOT NULL DEFAULT 0,
    ADD UNIQUE (source_id, product_id);

ALTER TABLE lms_delivery.package_custody_event
    ADD COLUMN source_id varchar(60),
    ADD COLUMN from_holder varchar(60),
    ADD COLUMN to_holder varchar(60),
    ADD UNIQUE (source_id, event_type);

ALTER TABLE lms_delivery.delivery_proof
    ADD COLUMN content_type varchar(50),
    ADD COLUMN byte_count bigint CHECK (byte_count > 0),
    ADD COLUMN sha256 varchar(64),
    ADD COLUMN used_for_outcome boolean NOT NULL DEFAULT FALSE;

ALTER TABLE lms_delivery.notification
    ADD COLUMN actor_user_id varchar(60) REFERENCES lms_delivery.lms_user (user_id),
    ADD COLUMN transport_status text;

ALTER TABLE lms_delivery.audit_log
    ADD COLUMN source_module text,
    ADD COLUMN safe_detail varchar(600);

ALTER TABLE lms_delivery.security_log
    ADD COLUMN source_module text;

-- The lock row serializes the current Delivery app's local transactions.
CREATE TABLE lms_delivery_runtime.mutation_lock (id integer PRIMARY KEY);
INSERT INTO lms_delivery_runtime.mutation_lock (id) VALUES (1);

CREATE TABLE lms_delivery_runtime.pickup_cycle (
    id varchar(60) PRIMARY KEY,
    package_id varchar(60) NOT NULL REFERENCES lms_delivery.package (package_id),
    list_id varchar(60) NOT NULL REFERENCES lms_delivery.pickup_list (pickup_list_id),
    worker_id varchar(60) NOT NULL REFERENCES lms_delivery.lms_user (user_id),
    collected_at timestamptz NOT NULL,
    approval_id varchar(60) REFERENCES lms_delivery.client_instruction (instruction_id),
    UNIQUE (package_id, id),
    FOREIGN KEY (list_id, package_id)
        REFERENCES lms_delivery.pickup_list_item (pickup_list_id, package_id)
);

ALTER TABLE lms_delivery.delivery_attempt
    ADD COLUMN pickup_cycle_id varchar(60),
    ADD FOREIGN KEY (package_id, pickup_cycle_id)
        REFERENCES lms_delivery_runtime.pickup_cycle (package_id, id);

CREATE TABLE lms_delivery_runtime.return_receipt (
    id varchar(60) PRIMARY KEY,
    package_id varchar(60) NOT NULL REFERENCES lms_delivery.package (package_id),
    pickup_id varchar(60) NOT NULL,
    attempt_id varchar(60) REFERENCES lms_delivery.delivery_attempt (delivery_attempt_id),
    reported_by varchar(60) NOT NULL REFERENCES lms_delivery.lms_user (user_id),
    reason varchar(600) NOT NULL,
    receipt_status text NOT NULL,
    created_at timestamptz NOT NULL,
    received_at timestamptz,
    received_by varchar(60) REFERENCES lms_delivery.lms_user (user_id),
    receipt_condition text,
    UNIQUE (package_id, id),
    FOREIGN KEY (package_id, pickup_id)
        REFERENCES lms_delivery_runtime.pickup_cycle (package_id, id),
    CHECK (received_at IS NULL OR received_at >= created_at)
);

-- Receipt lines use the catalog's unique SKU as their product reference.
CREATE TABLE lms_delivery_runtime.return_line (
    return_id varchar(60) NOT NULL REFERENCES lms_delivery_runtime.return_receipt (id),
    sku varchar(60) NOT NULL REFERENCES lms_delivery.product (sku),
    actual_quantity integer NOT NULL CHECK (actual_quantity >= 0),
    unusable_quantity integer NOT NULL CHECK (unusable_quantity >= 0),
    PRIMARY KEY (return_id, sku),
    CHECK (unusable_quantity <= actual_quantity)
);

ALTER TABLE lms_delivery.package
    ADD COLUMN custody text NOT NULL DEFAULT 'WAREHOUSE',
    ADD COLUMN responsible_user_id varchar(60) REFERENCES lms_delivery.lms_user (user_id),
    ADD COLUMN current_delivery_detail_id varchar(60),
    ADD COLUMN hold_reason varchar(300),
    ADD COLUMN last_pickup_id varchar(60),
    ADD COLUMN last_return_id varchar(60),
    ADD COLUMN approved_instruction_id varchar(60) REFERENCES lms_delivery.client_instruction (instruction_id),
    ADD COLUMN retry_approved boolean NOT NULL DEFAULT FALSE,
    ADD COLUMN failed_return boolean NOT NULL DEFAULT FALSE,
    ADD COLUMN version_no bigint NOT NULL DEFAULT 0 CHECK (version_no >= 0),
    ADD FOREIGN KEY (order_id, current_delivery_detail_id)
        REFERENCES lms_delivery.order_delivery_detail (order_id, delivery_detail_id),
    ADD FOREIGN KEY (package_id, last_pickup_id)
        REFERENCES lms_delivery_runtime.pickup_cycle (package_id, id),
    ADD FOREIGN KEY (package_id, last_return_id)
        REFERENCES lms_delivery_runtime.return_receipt (package_id, id);

ALTER TABLE lms_delivery.client_instruction
    ADD COLUMN return_id varchar(60) REFERENCES lms_delivery_runtime.return_receipt (id),
    ADD COLUMN delivery_detail_id varchar(60),
    ADD COLUMN version_no integer CHECK (version_no > 0),
    ADD UNIQUE (order_id, version_no),
    ADD FOREIGN KEY (order_id, delivery_detail_id)
        REFERENCES lms_delivery.order_delivery_detail (order_id, delivery_detail_id);

CREATE TABLE lms_delivery_runtime.discrepancy_case (
    id varchar(60) PRIMARY KEY,
    package_id varchar(60) NOT NULL REFERENCES lms_delivery.package (package_id),
    source_id varchar(60) NOT NULL,
    case_status text NOT NULL,
    condition_code text NOT NULL,
    reported_by varchar(60) NOT NULL REFERENCES lms_delivery.lms_user (user_id),
    created_at timestamptz NOT NULL,
    safe_detail varchar(600) NOT NULL
);

CREATE TABLE lms_delivery_runtime.operation_result (
    actor_id varchar(60) NOT NULL REFERENCES lms_delivery.lms_user (user_id),
    action_name varchar(120) NOT NULL,
    request_id varchar(40) NOT NULL,
    payload_hash varchar(64) NOT NULL,
    result_json text NOT NULL,
    created_at timestamptz NOT NULL,
    PRIMARY KEY (actor_id, action_name, request_id)
);

-- A summary survives expiry of the detailed attempt and proof.
CREATE TABLE lms_delivery_runtime.retained_summary (
    id varchar(60) PRIMARY KEY,
    package_id varchar(60) NOT NULL REFERENCES lms_delivery.package (package_id),
    worker_id varchar(60) NOT NULL REFERENCES lms_delivery.lms_user (user_id),
    outcome text NOT NULL,
    event_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    CHECK (expires_at >= event_at)
);

CREATE TABLE lms_delivery_runtime.maintenance_status (
    id varchar(60) PRIMARY KEY,
    last_run_at timestamptz,
    removed_proofs integer NOT NULL DEFAULT 0 CHECK (removed_proofs >= 0),
    removed_attempts integer NOT NULL DEFAULT 0 CHECK (removed_attempts >= 0),
    orphan_count integer NOT NULL DEFAULT 0 CHECK (orphan_count >= 0),
    last_error varchar(300)
);

CREATE INDEX return_receipt_package_idx ON lms_delivery_runtime.return_receipt (package_id);

-- Keep JDBC column order where current calls use INSERT VALUES.
-- Views run with the caller's permissions. Apply runtime grants separately.
CREATE VIEW lms_delivery_runtime.app_account WITH (security_invoker = true) AS
SELECT user_id AS id, display_name, password_hash, role, account_status,
       failed_login_count AS failed_count, lock_until, username
FROM lms_delivery.lms_user;

CREATE VIEW lms_delivery_runtime.delivery_detail WITH (security_invoker = true) AS
SELECT delivery_detail_id AS id, order_id, version_no, recipient_name, phone,
       address AS address_text
FROM lms_delivery.order_delivery_detail;

CREATE VIEW lms_delivery_runtime.delivery_package WITH (security_invoker = true) AS
SELECT package_id AS id, order_id, package_qr_code AS qr_code, custody,
       current_handler_user_id AS holder_id, responsible_user_id AS responsible_id,
       current_delivery_detail_id AS detail_id, hold_reason, last_pickup_id,
       last_return_id, approved_instruction_id, retry_approved, failed_return, version_no
FROM lms_delivery.package;

CREATE VIEW lms_delivery_runtime.pickup_list WITH (security_invoker = true) AS
SELECT pickup_list_id AS id, list_qr_code AS qr_code, status AS list_status,
       created_time AS created_at, created_by_worker_id AS created_by,
       claimed_by_delivery_worker_id AS claimed_by, claimed_time AS claimed_at, last_activity_at
FROM lms_delivery.pickup_list;

CREATE VIEW lms_delivery_runtime.pickup_item WITH (security_invoker = true) AS
SELECT pickup_list_id AS list_id, package_id, collection_outcome,
       cannot_collect_reason AS reason, internal_note, collected_time AS collected_at
FROM lms_delivery.pickup_list_item;

CREATE VIEW lms_delivery_runtime.delivery_attempt WITH (security_invoker = true) AS
SELECT delivery_attempt_id AS id, package_id, delivery_worker_id AS worker_id,
       pickup_cycle_id AS pickup_id, delivery_detail_id AS detail_id,
       start_time AS started_at, end_time AS ended_at, outcome,
       failure_reason AS reason, internal_note
FROM lms_delivery.delivery_attempt;

CREATE VIEW lms_delivery_runtime.delivery_order WITH (security_invoker = true) AS
SELECT o.order_id AS id, o.order_reference, o.client_user_id AS client_id, o.order_status,
       EXISTS (
           SELECT 1 FROM lms_delivery.payment AS p
           WHERE p.order_id = o.order_id AND p.accepted_at IS NOT NULL
       ) AS payment_accepted,
       o.created_time AS created_at
FROM lms_delivery.customer_order AS o;

CREATE VIEW lms_delivery_runtime.product_stock WITH (security_invoker = true) AS
SELECT p.sku, p.name AS product_name, i.physical_stock, i.reserved_stock,
       i.unavailable_stock, p.low_stock_threshold AS low_threshold
FROM lms_delivery.product AS p
JOIN lms_delivery.inventory AS i USING (product_id);

CREATE VIEW lms_delivery_runtime.order_item WITH (security_invoker = true) AS
SELECT i.order_item_id AS id, i.order_id, p.sku, i.quantity
FROM lms_delivery.order_item AS i
JOIN lms_delivery.product AS p USING (product_id);

-- The compatibility quantity is the sum of current reservations for one SKU.
CREATE VIEW lms_delivery_runtime.stock_allocation WITH (security_invoker = true) AS
SELECT pkg.package_id, p.sku,
       COALESCE(SUM(r.quantity) FILTER (WHERE r.released_at IS NULL), 0)::integer AS quantity
FROM lms_delivery.package AS pkg
JOIN lms_delivery.order_item AS i USING (order_id)
JOIN lms_delivery.product AS p USING (product_id)
LEFT JOIN lms_delivery.stock_reservation AS r USING (order_item_id)
GROUP BY pkg.package_id, p.sku;

CREATE VIEW lms_delivery_runtime.stock_movement WITH (security_invoker = true) AS
SELECT m.movement_id AS id, p.sku, m.package_id, m.source_id, m.movement_type,
       m.quantity_delta AS physical_delta, m.reserved_delta, m.unavailable_delta,
       m.actor_user_id AS actor_id, m.event_time AS event_at
FROM lms_delivery.stock_movement AS m
JOIN lms_delivery.product AS p USING (product_id);

CREATE VIEW lms_delivery_runtime.custody_event WITH (security_invoker = true) AS
SELECT custody_event_id AS id, package_id, source_id, event_type, from_holder,
       to_holder, actor_user_id AS actor_id, event_time AS event_at
FROM lms_delivery.package_custody_event;

-- Proof ownership comes from the recorded attempt.
CREATE VIEW lms_delivery_runtime.delivery_proof WITH (security_invoker = true) AS
SELECT p.proof_id AS id, p.delivery_attempt_id AS attempt_id,
       (SELECT a.delivery_worker_id FROM lms_delivery.delivery_attempt AS a
        WHERE a.delivery_attempt_id = p.delivery_attempt_id) AS worker_id,
       p.proof_type, p.proof_reference AS file_name, p.content_type,
       p.byte_count, p.sha256, p.created_time AS created_at, p.used_for_outcome
FROM lms_delivery.delivery_proof AS p;

CREATE VIEW lms_delivery_runtime.client_instruction WITH (security_invoker = true) AS
SELECT i.instruction_id AS id,
       (SELECT p.package_id FROM lms_delivery.package AS p WHERE p.order_id = i.order_id) AS package_id,
       i.return_id, i.delivery_detail_id AS detail_id, i.version_no,
       i.instruction_type, i.status AS instruction_status, i.submitted_time AS created_at,
       i.resolved_time AS resolved_at, i.resolved_by_admin_id AS resolved_by
FROM lms_delivery.client_instruction AS i;

CREATE VIEW lms_delivery_runtime.notification_outbox WITH (security_invoker = true) AS
SELECT n.notification_id AS id, n.actor_user_id AS actor_id,
       n.recipient_user_id AS client_id,
       (SELECT p.package_id FROM lms_delivery.package AS p WHERE p.order_id = n.order_id) AS package_id,
       n.type AS event_type, n.message AS safe_message, n.created_time AS created_at,
       n.read_status AS is_read, n.transport_status
FROM lms_delivery.notification AS n
WHERE n.transport_status IS NOT NULL;

CREATE VIEW lms_delivery_runtime.audit_event WITH (security_invoker = true) AS
SELECT audit_id AS id, actor_user_id AS actor_id, action AS action_name, target_id,
       safe_detail, event_time AS created_at
FROM lms_delivery.audit_log
WHERE source_module = 'DELIVERY';

CREATE VIEW lms_delivery_runtime.security_event WITH (security_invoker = true) AS
SELECT security_log_id AS id, user_id AS account_id, event_type, result AS event_result,
       event_time AS occurred_at, ip_address, device_info
FROM lms_delivery.security_log
WHERE source_module = 'DELIVERY';

CREATE FUNCTION lms_delivery_runtime.insert_account() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog AS $$
BEGIN
    INSERT INTO lms_delivery.lms_user
        (user_id, username, display_name, password_hash, role, account_status, failed_login_count, lock_until)
    VALUES
        (NEW.id, COALESCE(NEW.username, NEW.id), COALESCE(NEW.display_name, NEW.username, NEW.id),
         NEW.password_hash, NEW.role, NEW.account_status, COALESCE(NEW.failed_count, 0), NEW.lock_until);
    RETURN NEW;
END;
$$;

CREATE TRIGGER app_account_insert INSTEAD OF INSERT ON lms_delivery_runtime.app_account
FOR EACH ROW EXECUTE FUNCTION lms_delivery_runtime.insert_account();

-- The existing DemoSeed uses this insert shape. Live checkout writes core payment data.
CREATE FUNCTION lms_delivery_runtime.insert_demo_order() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog AS $$
DECLARE
    order_time timestamptz := COALESCE(NEW.created_at, CURRENT_TIMESTAMP);
BEGIN
    INSERT INTO lms_delivery.customer_order
        (order_id, order_reference, client_user_id, order_status, created_time, payment_deadline, total_amount)
    VALUES
        (NEW.id, NEW.order_reference, NEW.client_id, NEW.order_status, order_time,
         order_time + interval '24 hours', 0);

    IF NEW.payment_accepted THEN
        INSERT INTO lms_delivery.payment
            (payment_id, order_id, amount, payment_status, created_time, ended_time, accepted_at, is_demo_fixture)
        VALUES
            (gen_random_uuid()::text, NEW.id, 0, 'DEMO_ACCEPTED', order_time, order_time, order_time, TRUE);
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER delivery_order_insert INSTEAD OF INSERT ON lms_delivery_runtime.delivery_order
FOR EACH ROW EXECUTE FUNCTION lms_delivery_runtime.insert_demo_order();

-- The demo insert supplies stock and a name. The core catalog owns live prices.
CREATE FUNCTION lms_delivery_runtime.write_product_stock() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog AS $$
DECLARE
    product_key varchar(60);
BEGIN
    IF TG_OP = 'INSERT' THEN
        INSERT INTO lms_delivery.product
            (product_id, sku, name, current_price, low_stock_threshold, listing_status)
        VALUES
            (gen_random_uuid()::text, NEW.sku, NEW.product_name, 0, NEW.low_threshold, 'DEMO_FIXTURE')
        RETURNING product_id INTO product_key;

        INSERT INTO lms_delivery.inventory
            (product_id, physical_stock, reserved_stock, unavailable_stock)
        VALUES
            (product_key, NEW.physical_stock, NEW.reserved_stock, NEW.unavailable_stock);
    ELSE
        IF NEW.sku IS DISTINCT FROM OLD.sku THEN
            RAISE EXCEPTION 'A Delivery stock update cannot change the SKU' USING ERRCODE = '23514';
        END IF;
        SELECT product_id INTO STRICT product_key FROM lms_delivery.product WHERE sku = OLD.sku;
        UPDATE lms_delivery.inventory
        SET physical_stock = NEW.physical_stock,
            reserved_stock = NEW.reserved_stock,
            unavailable_stock = NEW.unavailable_stock
        WHERE product_id = product_key
          AND physical_stock = OLD.physical_stock
          AND reserved_stock = OLD.reserved_stock
          AND unavailable_stock = OLD.unavailable_stock;

        -- Roll back if a concurrent stock writer changed this row.
        IF NOT FOUND THEN
            RAISE EXCEPTION 'Stock changed while Delivery was saving' USING ERRCODE = '40001';
        END IF;

        IF NEW.product_name IS DISTINCT FROM OLD.product_name
            OR NEW.low_threshold IS DISTINCT FROM OLD.low_threshold THEN
            UPDATE lms_delivery.product
            SET name = NEW.product_name, low_stock_threshold = NEW.low_threshold
            WHERE product_id = product_key;
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER product_stock_write INSTEAD OF INSERT OR UPDATE ON lms_delivery_runtime.product_stock
FOR EACH ROW EXECUTE FUNCTION lms_delivery_runtime.write_product_stock();

CREATE FUNCTION lms_delivery_runtime.insert_order_item() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog AS $$
DECLARE
    product_key varchar(60);
    snapshot_price numeric(12, 2);
BEGIN
    SELECT product_id, current_price INTO STRICT product_key, snapshot_price
    FROM lms_delivery.product WHERE sku = NEW.sku;

    INSERT INTO lms_delivery.order_item (order_item_id, order_id, product_id, quantity, unit_price)
    VALUES (NEW.id, NEW.order_id, product_key, NEW.quantity, snapshot_price);
    RETURN NEW;
END;
$$;

CREATE TRIGGER order_item_insert INSTEAD OF INSERT ON lms_delivery_runtime.order_item
FOR EACH ROW EXECUTE FUNCTION lms_delivery_runtime.insert_order_item();

-- A pickup releases current reservations. An intact return creates a new hold.
CREATE FUNCTION lms_delivery_runtime.write_stock_allocation() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog AS $$
DECLARE
    order_key varchar(60);
    product_key varchar(60);
    payment_key varchar(60);
    ordered_quantity integer;
    current_quantity integer;
BEGIN
    IF TG_OP = 'UPDATE' THEN
        IF NEW.package_id IS DISTINCT FROM OLD.package_id OR NEW.sku IS DISTINCT FROM OLD.sku THEN
            RAISE EXCEPTION 'A reservation cannot move to another package or SKU' USING ERRCODE = '23514';
        END IF;
    END IF;
    SELECT order_id INTO STRICT order_key FROM lms_delivery.package WHERE package_id = NEW.package_id;
    SELECT product_id INTO STRICT product_key FROM lms_delivery.product WHERE sku = NEW.sku;
    SELECT COALESCE(SUM(quantity), 0)::integer INTO ordered_quantity
    FROM lms_delivery.order_item WHERE order_id = order_key AND product_id = product_key;

    IF ordered_quantity = 0 OR NEW.quantity IS NULL OR NEW.quantity < 0
        OR (NEW.quantity <> 0 AND NEW.quantity <> ordered_quantity) THEN
        RAISE EXCEPTION 'Delivery reservations must match the order SKU quantity' USING ERRCODE = '23514';
    END IF;

    SELECT COALESCE(SUM(r.quantity), 0)::integer INTO current_quantity
    FROM lms_delivery.stock_reservation AS r
    JOIN lms_delivery.order_item AS i USING (order_item_id)
    WHERE i.order_id = order_key AND i.product_id = product_key AND r.released_at IS NULL;
    IF current_quantity = NEW.quantity THEN
        RETURN NEW;
    END IF;

    UPDATE lms_delivery.stock_reservation AS r
    SET status = 'RELEASED', released_at = CURRENT_TIMESTAMP
    FROM lms_delivery.order_item AS i
    WHERE r.order_item_id = i.order_item_id AND i.order_id = order_key
      AND i.product_id = product_key AND r.released_at IS NULL;

    IF NEW.quantity > 0 THEN
        SELECT payment_id INTO payment_key FROM lms_delivery.payment
        WHERE order_id = order_key AND accepted_at IS NOT NULL;

        INSERT INTO lms_delivery.stock_reservation
            (reservation_id, order_item_id, payment_id, quantity, status)
        SELECT gen_random_uuid()::text, i.order_item_id, payment_key, i.quantity, 'ACTIVE'
        FROM lms_delivery.order_item AS i
        WHERE i.order_id = order_key AND i.product_id = product_key;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER stock_allocation_write INSTEAD OF INSERT OR UPDATE ON lms_delivery_runtime.stock_allocation
FOR EACH ROW EXECUTE FUNCTION lms_delivery_runtime.write_stock_allocation();

CREATE FUNCTION lms_delivery_runtime.insert_stock_movement() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog AS $$
DECLARE
    product_key varchar(60);
BEGIN
    SELECT product_id INTO STRICT product_key FROM lms_delivery.product WHERE sku = NEW.sku;
    INSERT INTO lms_delivery.stock_movement
        (movement_id, product_id, package_id, source_id, movement_type, quantity_delta,
         reserved_delta, unavailable_delta, actor_user_id, event_time)
    VALUES
        (NEW.id, product_key, NEW.package_id, NEW.source_id, NEW.movement_type, NEW.physical_delta,
         NEW.reserved_delta, NEW.unavailable_delta, NEW.actor_id, NEW.event_at);
    RETURN NEW;
END;
$$;

CREATE TRIGGER stock_movement_insert INSTEAD OF INSERT ON lms_delivery_runtime.stock_movement
FOR EACH ROW EXECUTE FUNCTION lms_delivery_runtime.insert_stock_movement();

CREATE FUNCTION lms_delivery_runtime.insert_custody_event() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog AS $$
DECLARE
    list_key varchar(60);
BEGIN
    SELECT list_id INTO list_key FROM lms_delivery_runtime.pickup_cycle
    WHERE id = NEW.source_id AND package_id = NEW.package_id;
    INSERT INTO lms_delivery.package_custody_event
        (custody_event_id, package_id, source_id, event_type, from_holder, to_holder,
         actor_user_id, pickup_list_id, event_time)
    VALUES
        (NEW.id, NEW.package_id, NEW.source_id, NEW.event_type, NEW.from_holder, NEW.to_holder,
         NEW.actor_id, list_key, NEW.event_at);
    RETURN NEW;
END;
$$;

CREATE TRIGGER custody_event_insert INSTEAD OF INSERT ON lms_delivery_runtime.custody_event
FOR EACH ROW EXECUTE FUNCTION lms_delivery_runtime.insert_custody_event();

CREATE FUNCTION lms_delivery_runtime.insert_proof() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog AS $$
DECLARE
    attempt_worker varchar(60);
BEGIN
    SELECT delivery_worker_id INTO STRICT attempt_worker
    FROM lms_delivery.delivery_attempt WHERE delivery_attempt_id = NEW.attempt_id;
    IF NEW.worker_id IS DISTINCT FROM attempt_worker THEN
        RAISE EXCEPTION 'Proof worker must own the attempt' USING ERRCODE = '23503';
    END IF;
    INSERT INTO lms_delivery.delivery_proof
        (proof_id, delivery_attempt_id, proof_type, proof_reference, content_type,
         byte_count, sha256, created_time, used_for_outcome)
    VALUES
        (NEW.id, NEW.attempt_id, NEW.proof_type, NEW.file_name, NEW.content_type,
         NEW.byte_count, NEW.sha256, NEW.created_at, COALESCE(NEW.used_for_outcome, FALSE));
    RETURN NEW;
END;
$$;

CREATE TRIGGER delivery_proof_insert INSTEAD OF INSERT ON lms_delivery_runtime.delivery_proof
FOR EACH ROW EXECUTE FUNCTION lms_delivery_runtime.insert_proof();

CREATE FUNCTION lms_delivery_runtime.insert_instruction() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog AS $$
DECLARE
    order_key varchar(60);
    client_key varchar(60);
BEGIN
    SELECT p.order_id, o.client_user_id INTO STRICT order_key, client_key
    FROM lms_delivery.package AS p
    JOIN lms_delivery.customer_order AS o USING (order_id)
    JOIN lms_delivery_runtime.return_receipt AS r USING (package_id)
    WHERE p.package_id = NEW.package_id AND r.id = NEW.return_id;

    INSERT INTO lms_delivery.client_instruction
        (instruction_id, order_id, client_user_id, return_id, delivery_detail_id, version_no,
         instruction_type, status, submitted_time, resolved_time, resolved_by_admin_id)
    VALUES
        (NEW.id, order_key, client_key, NEW.return_id, NEW.detail_id, NEW.version_no,
         NEW.instruction_type, NEW.instruction_status, NEW.created_at, NEW.resolved_at, NEW.resolved_by);
    RETURN NEW;
END;
$$;

CREATE TRIGGER client_instruction_insert INSTEAD OF INSERT ON lms_delivery_runtime.client_instruction
FOR EACH ROW EXECUTE FUNCTION lms_delivery_runtime.insert_instruction();

CREATE FUNCTION lms_delivery_runtime.insert_notification() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog AS $$
DECLARE
    order_key varchar(60);
BEGIN
    SELECT p.order_id INTO STRICT order_key
    FROM lms_delivery.package AS p JOIN lms_delivery.customer_order AS o USING (order_id)
    WHERE p.package_id = NEW.package_id AND o.client_user_id = NEW.client_id;

    INSERT INTO lms_delivery.notification
        (notification_id, actor_user_id, recipient_user_id, order_id,
         type, message, created_time, read_status, transport_status)
    VALUES
        (NEW.id, NEW.actor_id, NEW.client_id, order_key, NEW.event_type, NEW.safe_message,
         NEW.created_at, COALESCE(NEW.is_read, FALSE), COALESCE(NEW.transport_status, 'PLACE_HOLDER'));
    RETURN NEW;
END;
$$;

CREATE TRIGGER notification_outbox_insert INSTEAD OF INSERT ON lms_delivery_runtime.notification_outbox
FOR EACH ROW EXECUTE FUNCTION lms_delivery_runtime.insert_notification();

CREATE FUNCTION lms_delivery_runtime.insert_audit_event() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog AS $$
BEGIN
    INSERT INTO lms_delivery.audit_log
        (audit_id, actor_user_id, action, target_type, target_id, safe_detail, event_time, result, source_module)
    VALUES
        (NEW.id, NEW.actor_id, NEW.action_name, 'DELIVERY_RECORD', NEW.target_id,
         NEW.safe_detail, NEW.created_at, 'SUCCESS', 'DELIVERY');
    RETURN NEW;
END;
$$;

CREATE TRIGGER audit_event_insert INSTEAD OF INSERT ON lms_delivery_runtime.audit_event
FOR EACH ROW EXECUTE FUNCTION lms_delivery_runtime.insert_audit_event();

CREATE FUNCTION lms_delivery_runtime.insert_security_event() RETURNS trigger
LANGUAGE plpgsql SET search_path = pg_catalog AS $$
BEGIN
    INSERT INTO lms_delivery.security_log
        (security_log_id, user_id, event_type, result, event_time, ip_address, device_info, source_module)
    VALUES
        (NEW.id, NEW.account_id, NEW.event_type, NEW.event_result, NEW.occurred_at,
         NEW.ip_address, NEW.device_info, 'DELIVERY');
    RETURN NEW;
END;
$$;

CREATE TRIGGER security_event_insert INSTEAD OF INSERT ON lms_delivery_runtime.security_event
FOR EACH ROW EXECUTE FUNCTION lms_delivery_runtime.insert_security_event();

-- Keep both schemas private. The maintainer grants the backend role access separately.
REVOKE ALL ON ALL TABLES IN SCHEMA lms_delivery, lms_delivery_runtime FROM PUBLIC;
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA lms_delivery_runtime FROM PUBLIC;

DO $$
DECLARE
    api_role text;
BEGIN
    FOR api_role IN SELECT rolname FROM pg_roles
        WHERE rolname IN ('anon', 'authenticated', 'service_role', 'authenticator')
    LOOP
        EXECUTE format('REVOKE ALL ON SCHEMA lms_delivery, lms_delivery_runtime FROM %I', api_role);
        EXECUTE format('REVOKE ALL ON ALL TABLES IN SCHEMA lms_delivery, lms_delivery_runtime FROM %I', api_role);
        EXECUTE format('REVOKE ALL ON ALL FUNCTIONS IN SCHEMA lms_delivery_runtime FROM %I', api_role);
    END LOOP;
END;
$$;
