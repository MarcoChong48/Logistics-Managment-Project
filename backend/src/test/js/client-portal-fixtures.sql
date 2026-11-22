-- Run only on a newly initialized disposable database named lms_m10e_acceptance.
\set ON_ERROR_STOP on
DO $$
BEGIN
    IF current_database() <> 'lms_m10e_acceptance' THEN
        RAISE EXCEPTION 'M10E fixtures require a dedicated acceptance database';
    END IF;
END $$;

\if :{?verify}
-- Run after the live Chromium journey. Any false or missing assertion fails psql.
BEGIN;
CREATE TEMP TABLE acceptance_check (name text, passed boolean NOT NULL CHECK (passed));
INSERT INTO acceptance_check
WITH own AS (SELECT user_id FROM app_user WHERE username = :'fixture_a'),
     other AS (SELECT user_id FROM app_user WHERE username = :'fixture_b'),
     replay AS (SELECT * FROM client_order_idempotency WHERE client_user_id = (SELECT user_id FROM own)),
     placed AS (SELECT * FROM customer_order WHERE order_id = (SELECT order_id FROM replay)),
     paid AS (SELECT * FROM customer_order WHERE order_reference = 'ORD-M10E-PAID'),
     failed AS (SELECT * FROM customer_order WHERE order_reference = 'ORD-M10E-FAILED')
SELECT 'one order across both submissions', count(*) = 1 FROM replay
UNION ALL SELECT 'three owned orders, including two seeded orders', count(*) = 3
    FROM customer_order WHERE client_user_id = (SELECT user_id FROM own)
UNION ALL SELECT 'order status, amount and deadline', (SELECT order_status = 'CANCELLED' AND total_amount = 39.60
    AND payment_deadline = created_at + interval '24 hours' FROM placed)
UNION ALL SELECT 'stored original response', (SELECT response_body->>'orderStatus' = 'AWAITING_PAYMENT'
    AND response_body->>'orderReference' = (SELECT order_reference FROM placed) FROM replay)
UNION ALL SELECT 'two exact order item snapshots', count(*) = 2 AND count(*) FILTER (WHERE
    (product_id, quantity, unit_price) IN ((9007199254740993, 2, 18.25), (123, 1, 3.10))) = 2
    FROM order_item WHERE order_id = (SELECT order_id FROM placed)
UNION ALL SELECT 'saved address snapshot', (SELECT version_no = 1 AND recipient_name = 'Updated Recipient'
    AND phone = '61234567' AND address_text = 'Unit 8, 12 Test Street, Kowloon, Hong Kong'
    FROM order_delivery_detail WHERE order_id = (SELECT order_id FROM placed))
UNION ALL SELECT 'no payment attempt created', NOT EXISTS (SELECT FROM payment WHERE order_id = (SELECT order_id FROM placed))
UNION ALL SELECT 'no reservation created', NOT EXISTS (SELECT FROM stock_reservation s JOIN order_item i USING (order_item_id)
    WHERE i.order_id = (SELECT order_id FROM placed))
UNION ALL SELECT 'stock unchanged for both ordered products', count(*) = 2 AND count(*) FILTER (WHERE
    (product_id, physical_stock, reserved_stock) IN ((9007199254740993, 12, 0), (123, 5, 0))) = 2
    FROM inventory WHERE product_id IN (9007199254740993, 123)
UNION ALL SELECT 'paid fixture cancelled before picking', (SELECT order_status = 'CANCELLED' FROM paid)
    AND (SELECT start_time IS NULL FROM picking_task WHERE order_id = (SELECT order_id FROM paid))
UNION ALL SELECT 'accepted payment preserved and one full refund requested', count(*) = 1 AND count(*) FILTER (WHERE
    p.payment_status = 'ACCEPTED' AND p.accepted_at IS NOT NULL AND r.amount = p.amount AND r.amount = 40.00
    AND r.refund_status = 'REQUESTED' AND r.completed_at IS NULL) = 1
    FROM payment p JOIN refund r USING (payment_id) WHERE p.order_id = (SELECT order_id FROM paid)
UNION ALL SELECT 'one release for the paid fixture', (SELECT count(*) = 1 AND count(*) FILTER (WHERE
    s.status = 'RELEASED' AND s.quantity = 2 AND s.released_at >= s.reserved_at) = 1
    FROM stock_reservation s JOIN order_item i USING (order_item_id) WHERE i.order_id = (SELECT order_id FROM paid))
    AND (SELECT physical_stock = 8 AND reserved_stock = 0 FROM inventory WHERE product_id = 124)
    AND (SELECT count(*) = 1 AND count(*) FILTER (WHERE product_id = 124 AND quantity_delta = 0 AND reserved_delta = -2) = 1
         FROM stock_movement)
UNION ALL SELECT 'no M9 instruction, custody event or additional delivery cycle', (SELECT order_status = 'DELIVERY_FAILED' FROM failed)
    AND NOT EXISTS (SELECT FROM client_instruction) AND NOT EXISTS (SELECT FROM package_custody_event)
    AND (SELECT count(*) = 2 AND count(DISTINCT delivery_detail_id) = 2 FROM delivery_attempt)
UNION ALL SELECT 'two read notifications, each linked to the owned failed attempt and address', count(*) = 2
    AND count(*) FILTER (WHERE n.is_read AND n.type = 'DELIVERY_FAILED' AND d.order_id = n.order_id
        AND pkg.order_id = n.order_id AND a.outcome = 'FAILED' AND n.created_at = a.end_time) = 2
    FROM notification n JOIN delivery_attempt a ON a.delivery_attempt_id = n.source_event_id
    JOIN order_delivery_detail d USING (delivery_detail_id) JOIN delivery_package pkg USING (package_id)
    WHERE n.recipient_user_id = (SELECT user_id FROM own) AND n.order_id = (SELECT order_id FROM failed)
UNION ALL SELECT 'other client notification still unread', count(*) = 1 AND count(*) FILTER (WHERE NOT is_read) = 1
    FROM notification WHERE recipient_user_id = (SELECT user_id FROM other)
UNION ALL SELECT 'other client defaults unchanged', (SELECT default_recipient_name = 'Other Recipient'
    AND default_address = 'Unit 2, 1 Other Street, Hong Kong' FROM client_profile WHERE user_id = (SELECT user_id FROM other));
SELECT name FROM acceptance_check ORDER BY name;
ROLLBACK;
\else
BEGIN;
DO $$
BEGIN
    IF EXISTS (SELECT FROM app_user) OR EXISTS (SELECT FROM product) OR EXISTS (SELECT FROM customer_order) THEN
        RAISE EXCEPTION 'M10E fixtures require an empty application database';
    END IF;
END $$;
CREATE EXTENSION pgcrypto;
INSERT INTO app_user (username, password_hash, role, must_change_password, temp_password_expires_at) VALUES
    (:'fixture_a', crypt(:'pass_a', gen_salt('bf', 10)), 'CLIENT', false, NULL),
    (:'fixture_b', crypt(:'pass_b', gen_salt('bf', 10)), 'CLIENT', false, NULL),
    (:'fixture_role', crypt(:'pass_role', gen_salt('bf', 10)), 'DELIVERY_WORKER', false, NULL),
    (:'fixture_forced', crypt(:'pass_forced', gen_salt('bf', 10)), 'CLIENT', true, NOW() + interval '1 hour');
INSERT INTO client_profile (user_id, default_recipient_name, default_phone, default_address)
SELECT user_id, 'Original Recipient', '62345678', 'Unit 1, 3 Test Street, Kowloon, Hong Kong' FROM app_user WHERE username = :'fixture_a'
UNION ALL SELECT user_id, 'Other Recipient', '69876543', 'Unit 2, 1 Other Street, Hong Kong' FROM app_user WHERE username = :'fixture_b';
INSERT INTO product (product_id, sku, name, current_price) VALUES
    (9007199254740993, 'M10E-WIDE', 'M10E Wide Carton', 18.25),
    (123, 'M10E-SMALL', 'M10E Small Mailer', 3.10),
    (124, 'M10E-PAID', 'M10E Paid Fixture Item', 20.00);
INSERT INTO inventory (product_id, physical_stock, reserved_stock) VALUES (9007199254740993, 12, 0), (123, 5, 0), (124, 8, 2);

-- Historical paid and failed orders are persisted fixtures. No payment or Delivery event is sent.
INSERT INTO customer_order (order_reference, client_user_id, order_status, created_at, payment_deadline, total_amount)
SELECT 'ORD-M10E-PAID', user_id, 'READY_FOR_PICKING', NOW() - interval '2 hours', NOW() + interval '22 hours', 40.00
    FROM app_user WHERE username = :'fixture_a'
UNION ALL SELECT 'ORD-M10E-FAILED', user_id, 'DELIVERY_FAILED', NOW() - interval '3 hours', NOW() + interval '21 hours', 20.00
    FROM app_user WHERE username = :'fixture_a'
UNION ALL SELECT 'ORD-M10E-OTHER', user_id, 'AWAITING_PAYMENT', NOW() - interval '1 hour', NOW() + interval '23 hours', 3.10
    FROM app_user WHERE username = :'fixture_b';
INSERT INTO order_item (order_id, product_id, quantity, unit_price)
SELECT order_id, 124, 2, 20.00 FROM customer_order WHERE order_reference = 'ORD-M10E-PAID'
UNION ALL SELECT order_id, 124, 1, 20.00 FROM customer_order WHERE order_reference = 'ORD-M10E-FAILED'
UNION ALL SELECT order_id, 123, 1, 3.10 FROM customer_order WHERE order_reference = 'ORD-M10E-OTHER';
INSERT INTO order_delivery_detail (order_id, version_no, recipient_name, phone, address_text, created_at)
SELECT order_id, 1, 'Paid fixture recipient', '62345678', 'Unit 4, 3 Test Street, Kowloon, Hong Kong', created_at
    FROM customer_order WHERE order_reference = 'ORD-M10E-PAID'
UNION ALL SELECT order_id, 1, 'Historical recipient', '62345678', 'Unit 5, 3 Test Street, Kowloon, Hong Kong', created_at
    FROM customer_order WHERE order_reference = 'ORD-M10E-FAILED'
UNION ALL SELECT order_id, 2, 'Current recipient', '62345678', 'Unit 6, 3 Test Street, Kowloon, Hong Kong', NOW() - interval '30 minutes'
    FROM customer_order WHERE order_reference = 'ORD-M10E-FAILED';
INSERT INTO payment (order_id, amount, payment_status, created_at, checkout_expires_at, ended_at, accepted_at)
SELECT order_id, total_amount, 'ACCEPTED', created_at + interval '2 minutes', created_at + interval '32 minutes',
    created_at + interval '4 minutes', created_at + interval '4 minutes'
FROM customer_order WHERE order_reference IN ('ORD-M10E-PAID', 'ORD-M10E-FAILED');
INSERT INTO stock_reservation (order_item_id, payment_id, quantity, reserved_at)
SELECT i.order_item_id, p.payment_id, i.quantity, p.created_at
FROM customer_order o JOIN order_item i USING (order_id) JOIN payment p USING (order_id) WHERE o.order_reference = 'ORD-M10E-PAID';
INSERT INTO picking_task (order_id) SELECT order_id FROM customer_order WHERE order_reference = 'ORD-M10E-PAID';
INSERT INTO delivery_package (order_id, package_qr_code, created_at, custody_status, current_handler_user_id)
SELECT order_id, 'M10E-PACKAGE', NOW() - interval '90 minutes', 'WITH_DELIVERY_WORKER',
    (SELECT user_id FROM app_user WHERE username = :'fixture_role') FROM customer_order WHERE order_reference = 'ORD-M10E-FAILED';
INSERT INTO delivery_attempt (package_id, delivery_detail_id, delivery_worker_user_id, start_time, end_time, outcome, failure_reason, internal_note)
SELECT p.package_id, d.delivery_detail_id, p.current_handler_user_id,
    NOW() - make_interval(mins => CASE d.version_no WHEN 1 THEN 65 ELSE 15 END),
    NOW() - make_interval(mins => CASE d.version_no WHEN 1 THEN 55 ELSE 5 END),
    'FAILED', 'RECIPIENT_UNAVAILABLE', 'M10E worker-only note'
FROM delivery_package p JOIN order_delivery_detail d USING (order_id) ORDER BY d.version_no;
INSERT INTO notification (recipient_user_id, order_id, type, message, created_at, source_event_id)
SELECT o.client_user_id, o.order_id, 'DELIVERY_FAILED', 'The recipient was unavailable.', a.end_time, a.delivery_attempt_id
FROM delivery_attempt a JOIN delivery_package p USING (package_id) JOIN customer_order o USING (order_id) ORDER BY a.end_time;
INSERT INTO notification (recipient_user_id, type, message)
SELECT user_id, 'GENERAL', 'M10E legacy fixture body' FROM app_user WHERE username = :'fixture_b';
COMMIT;
\endif
