# LMS PostgreSQL 17 schema — v1 review draft (no Flyway)

**Status:** Implementation-oriented draft based on the existing 25-table LMS Database Draft v0.4, NOT a team-approved final database contract. PostgreSQL 17, Spring Boot 4.1.2, Java 21. Does not use Flyway.

## Files

- `01_create_tables.sql`: 25 related PostgreSQL tables, PKs, FKs, constraints, indexes. All 25 tables are in the `public` schema. Meant for a **new/empty** PostgreSQL database.
- `02_sample_data.sql`: optional fictional product/stock data for local testing; **never auto-applied**.
- `../compose.yaml`: optional replacement for the repository's `compose.yaml`. Adds a bind mount so a **new Docker PostgreSQL volume** creates the tables automatically on first initialization.

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

## Apply NOW on your existing running Docker database

You already have an empty persistent `postgres_data` volume (as shown by `\\dt` returning no relations). From the **Git repository root** in PowerShell, with `docker compose up` already running:

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

Copy `compose.yaml` from this package to the repository root (or simply add the mount shown below under `db.volumes` in your existing Compose file). Commit `database/01_create_tables.sql` and `compose.yaml` together.

```yaml
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./database/01_create_tables.sql:/docker-entrypoint-initdb.d/01_create_tables.sql:ro
```

For a **fresh** local Docker volume, `docker compose up --build` initializes the database using this SQL automatically. The official Postgres container runs files in `/docker-entrypoint-initdb.d` only when the data directory is empty. If a teammate already has a populated volume, the mount alone **does not** update their existing schema. They should apply a documented change script manually, or intentionally recreate their disposable local data after backup.

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
