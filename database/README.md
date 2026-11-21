# LMS PostgreSQL 17 schema — v1 review draft (no Flyway)

**Status:** Implementation-oriented draft based on the existing 25-table LMS Database Draft v0.4, NOT a team-approved final database contract. PostgreSQL 17, Spring Boot 4.1.2, Java 21. Does not use Flyway.

## Files

- `01_create_tables.sql`: 25 related PostgreSQL tables, PKs, FKs, constraints, indexes. All 25 tables are in the `public` schema. Meant for a **new/empty** PostgreSQL database.
- `02_sample_data.sql`: optional fictional product/stock data for local testing; **never auto-applied**.
- `03_admin_changes.sql` and `04_client_order_idempotency.sql` extend the baseline.
- `../compose.yaml` mounts scripts `01`, `03`, and `04` for automatic initialization of a **new Docker PostgreSQL volume**.

## Table mapping from the original ERD

| ERD name | PostgreSQL SQL name |
|---|---|
| User | `app_user` |
| ClientProfile | `client_profile` |
| DeliveryWorkerProfile | `delivery_worker_profile` |
| Product | `product` |
| Inventory | `inventory` |
| StockReservation | `stock_reservation` |
| StockMovement | `stock_movement` |
| Order | `customer_order` |
| OrderItem | `order_item` |
| OrderDeliveryDetail | `order_delivery_detail` |
| Payment | `payment` |
| Refund | `refund` |
| PickingTask | `picking_task` |
| PickingAssignmentHistory | `picking_assignment_history` |
| PickingScanHistory | `picking_scan_history` |
| Package | `delivery_package` |
| PickupList | `pickup_list` |
| PickupListItem | `pickup_list_item` |
| PackageCustodyEvent | `package_custody_event` |
| DeliveryAttempt | `delivery_attempt` |
| DeliveryProof | `delivery_proof` |
| ClientInstruction | `client_instruction` |
| Notification | `notification` |
| AuditLog | `audit_log` |
| SecurityLog | `security_log` |

Why `app_user`, `customer_order`, `delivery_package`? Avoid collision/ambiguity with SQL terms and use lowercase snake_case for JPA mapping. **Tell the entire team these are the authoritative table names before updating Java code.**

## Apply the baseline to a running, empty database

Use these baseline commands only if you have verified that the running database contains no tables. From the repository root in PowerShell, with the database container running:

```powershell
docker compose cp .\database\01_create_tables.sql db:/tmp/01_create_tables.sql
docker compose exec db psql -v ON_ERROR_STOP=1 -U lms -d lms -f /tmp/01_create_tables.sql
docker compose exec db psql -U lms -d lms -c "\dt"
docker compose exec db psql -U lms -d lms -tAc "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE';"
```

Expected table count after a successful first application: **25** (the script doesn't add migration bookkeeping tables).

To insert *optional fictional* product/stock examples:

```powershell
docker compose cp .\database\02_sample_data.sql db:/tmp/02_sample_data.sql
docker compose exec db psql -v ON_ERROR_STOP=1 -U lms -d lms -f /tmp/02_sample_data.sql
docker compose exec db psql -U lms -d lms -c "SELECT p.sku,p.name,i.physical_stock,i.reserved_stock FROM product p JOIN inventory i USING(product_id);"
```

**Do not run `docker compose down -v` merely to rerun SQL**; that destroys the local database volume and records. Do not rerun `01_create_tables.sql` on a populated database; use a versioned `ALTER TABLE` script for changes.

## Teammate's first-run Docker setup

Use the repository's `compose.yaml`. On a **fresh** local Docker volume, `docker compose up --build` runs the mounted initialization scripts in order. The Postgres container runs `/docker-entrypoint-initdb.d` only when the data directory is empty. For a populated volume, apply only the pending versioned scripts manually and retain the volume.

## SQL change policy — manual, no Flyway

1. Keep the originally applied `01_create_tables.sql` unchanged once teammates are using it.
2. For each change, add a new ordered file such as `03_add_client_column.sql` or `04_adjust_delivery_attempt.sql` using `ALTER TABLE`; document execution order and who applied it to which environment in this README or an operations changelog.
3. Coordinate changes to the shared Supabase database; do not apply temporary H2 `schema.sql` from Delivery to PostgreSQL.
4. Never commit Supabase credentials, real passwords, payment tokens, personal addresses or proof-file URLs.
5. A successful SQL run creates only schema. It **does not** implement application business logic or grant database-backed role authorization.

## Deliberate design corrections compared with v0.4

- `PickingScanHistory.orderItemID` becomes nullable for wrong/unknown QR; save `scanned_identifier` and rejection `reason`.
- `OrderItem.picked_quantity` stores durable progress (not only expiring scan history).
- `Payment` includes `checkout_expires_at` and `accepted_at` to distinguish retries/late accepted payments; still requires service logic.
- `StockMovement` includes `reserved_delta` and optional `custody_event_id`, allowing reservation/pickup/return reconciliation.
- `ClientInstruction` optionally links an address version and a return custody event, allowing the service to bind approval to the right return cycle.
- `PickingAssignmentHistory` allows only one active assignment per task; one scan may be reversed only once.
- Timestamps use `TIMESTAMPTZ`, amounts use `NUMERIC(12,2)`, primary keys use `BIGINT IDENTITY`.
- Role values and several basic fields are constrained; **most workflow status strings are deliberately not locked into a CHECK list**, because the group still has unapproved names/transitions.

## Important unresolved rules before calling this final

- **Return receipt/lines:** The original 25-table draft lacks typed per-item receipt records for damaged/short returns. `package_custody_event.details` is JSONB as a *provisional* place to retain detail. Do not claim it fully models warehouse receipt validation; review whether dedicated receipt tables are needed.
- **Concurrency:** Reservation, simultaneous checkout, pick/cancel races, repeated pickup, refund retries, and physical/reserved stock reconciliation require transactions, row locking, idempotency, and service tests. Ordinary FK and CHECK constraints are insufficient.
- **Cross-table ownership:** a delivery address, package and attempt can individually reference valid rows but still reference **different orders**. Validate matching order IDs in a transaction, or add composite FK constraints after review.
- **Authentication:** A real Spring Security login/session mechanism is still required. This schema stores hashed passwords only; `must_change_password` and `temp_password_expires_at` do not implement secure reset by themselves.
- **Delivery worker profile:** optional vehicle/employee fields were a proposal, not a fully approved scope.
- **Retention, privacy, and proofs:** do not place real PII/password hashes/secret tokens in audit details. Configure private storage, auth checks, and retention separately.
- **Payment status mapping, cancellation rules, pickup cycle IDs, exception/retry policy:** require team agreement.

This is a complete **25-table structural baseline**, not a claim of complete executable LMS business behaviour.

## Admin changes

On a fresh local volume, `01_create_tables.sql` runs before `03_admin_changes.sql`. The Admin script applies all seven changes in one transaction and brings the `public` schema to **27 tables** before the Client migration below. `02_sample_data.sql` remains optional.

| Change | Database update |
| --- | --- |
| A1 | Allows `BANNED` in `app_user.account_status` alongside `ACTIVE`, `INACTIVE` and `LOCKED`. |
| A2 | Adds required integer `app_user.session_version` with default `0`. |
| A3 | Creates `shortage_case` with decision history and allows one `OPEN` or `WAITING_RESTOCK` case per order and product. |
| A4 | Creates `delivery_assignment_history` and indexes assignment times by package. |
| A5 | Adds `client_instruction.source` with default `TRACKING_PAGE` and an optional `recorded_by_user_id` link to `app_user`. |
| A6 | Adds optional `client_instruction.resolution_note`. |
| A7 | Adds a unique index on `LOWER(app_user.username)`. |

The initialization scripts run only when PostgreSQL creates a fresh volume. On an existing **local Admin** database that has only the 25-table baseline, apply `03_admin_changes.sql` once using `psql` with `ON_ERROR_STOP=1`. Existing usernames that differ only by case will cause the transaction to roll back. Record manual applications in the stage report or operation log with the operator, environment and result.

## Client Order Idempotency

`04_client_order_idempotency.sql` adds one table for persistent Create Order responses. Fresh Compose volumes run scripts `01`, `03`, and `04` in that order and have **28 tables**. For a fresh integration test database, apply the same scripts in order with `psql -v ON_ERROR_STOP=1`.

On an existing database with `01` and `03` already applied, verify the intended database and check whether `04` is present. For an existing local Compose volume, run from the repository root.

```powershell
docker compose exec db psql -v ON_ERROR_STOP=1 -U lms -d lms -tAc "SELECT to_regclass('public.client_order_idempotency')"
```

If the check returns the table name, skip `04`. If it returns a blank result, run `04` once before deploying the updated backend.

```powershell
docker compose cp .\database\04_client_order_idempotency.sql db:/tmp/04_client_order_idempotency.sql
docker compose exec db psql -v ON_ERROR_STOP=1 -U lms -d lms -f /tmp/04_client_order_idempotency.sql
docker compose exec db psql -U lms -d lms -c "\d public.client_order_idempotency"
```

Record the migration in the team’s operations log. The script creates the new table in one transaction. A repeat execution stops with an already-exists error and leaves the existing table and data intact. Existing orders and volumes are retained, and old orders have no replay record to backfill.

## Stripe Sandbox foundation

`05_client_stripe_sandbox.sql` adds a checkout-to-Session mapping and a durable webhook inbox keyed by event ID. It preserves existing order, payment, reservation and refund rows. The foundation requires `05` only when Stripe is enabled. For an isolated test database, apply `01`, `03`, `04`, then `05` with `psql -v ON_ERROR_STOP=1`. The resulting schema contains 30 tables. The normal Compose initialization still stops at `04`. Existing databases and volumes need no changes for Stripe-disabled operation.

For a new isolated Stripe database, apply all four scripts in order. For an already-initialized isolated database, verify the server, confirm `04` is present, and check both `public.client_stripe_checkout` and `public.client_stripe_event` before applying `05`. If both are present, skip `05`. If only one is present, inspect the schema before proceeding. The migration adds no backfill and runs in one transaction. If any statement fails, PostgreSQL rolls back the entire migration when the `psql` session exits.

For application rollback, disable Stripe and retain both tables. If any sessions have already been issued, stop new checkout issuance and arrange closure and reconciliation of those sessions and any refund obligations before retiring the adapter. If an urgent rollback takes the webhook offline, retain the stored identifiers and reconcile against the Sandbox before restoring checkout. Dropping the tables would erase the evidence needed to avoid duplicate Session creation and process missed events. This milestone includes no down migration.
