-- Keep the system assignment separate from the confirmed worker claim.
BEGIN;

ALTER TABLE lms_delivery.pickup_list
    ADD COLUMN IF NOT EXISTS assigned_to_delivery_worker_id varchar(60);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'lms_delivery.pickup_list'::regclass
          AND conname = 'pickup_list_assigned_to_fk'
    ) THEN
        ALTER TABLE lms_delivery.pickup_list
            ADD CONSTRAINT pickup_list_assigned_to_fk
            FOREIGN KEY (assigned_to_delivery_worker_id)
            REFERENCES lms_delivery.lms_user (user_id);
    END IF;
END;
$$;

-- Append the new field so the original view keeps its columns and grants.
CREATE OR REPLACE VIEW lms_delivery_runtime.pickup_list WITH (security_invoker = true) AS
SELECT pickup_list_id AS id, list_qr_code AS qr_code, status AS list_status,
       created_time AS created_at, created_by_worker_id AS created_by,
       claimed_by_delivery_worker_id AS claimed_by, claimed_time AS claimed_at, last_activity_at,
       assigned_to_delivery_worker_id AS assigned_to
FROM lms_delivery.pickup_list;

COMMIT;
