# LMS integration handoff

Use the local integration checkout with Docker Desktop in Linux-container mode. Java 21 runs the Maven checks. Node.js 22 with Chrome or Edge runs the browser checks.

## Start the local stack

Create `.env` from `.env.example` if needed. Set `LMS_BOOTSTRAP_ADMIN_TEMPORARY_PASSWORD` to a strong local temporary password and keep `LMS_BOOTSTRAP_ADMIN_RESET_ON_START=false`.

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
```

Run the commands from the repository root on a fresh volume. For an existing local volume, define $stack, run docker @stack up -d db, then complete the database steps before starting the backend.

```powershell
$stack = @('compose','-p','lms-integration-local','-f','compose.yaml','-f','compose.delivery-integration.yaml')
docker @stack up --build -d
docker @stack ps -a
Invoke-RestMethod http://127.0.0.1:8081/api/shared/health
```

Open `http://127.0.0.1:8081/`. The overlay enables `delivery-full`, waits for migration 07, and mounts the private `delivery_proofs` volume at `/app/delivery-proofs`. Stripe is disabled by default. `docker @stack stop` retains the database and proofs.

## Databases

For a clean PostgreSQL 17 database, the manual sequence is **01, 03, 04, 06, 07, 08**. Clean Compose volumes apply 08 during initialization, and the Delivery overlay applies 07 before backend startup. Migration 08 depends on the base order and user tables. The integrated schema has 33 public tables. Optional 05 adds two tables for isolated Stripe sandbox testing. [Database reference](../../database/README.md)

**Migration 08 is required before using the upgraded backend on an existing database.** First rehearse the pending migrations on a backup restored to a disposable database. If this local `lms` volume is current through 07, the following commands apply and verify 08 on that volume.

```powershell
docker @stack exec -T db psql -X -U lms -d lms -v ON_ERROR_STOP=1 -f /docker-entrypoint-initdb.d/08_warehouse_picking_commands.sql
docker @stack exec -T db psql -X -U lms -d lms -Atc "SELECT to_regclass('public.warehouse_picking_command');"
```

## Local test accounts and demonstration

Sign in as `admin_local` with the configured temporary password and change it on first sign-in. In Admin **Users**, create the three test accounts below and complete each account’s first-login password change. Use separate browser profiles for simultaneous role sessions.

| Suggested account | Role | Entry point |
| --- | --- | --- |
| `admin_local` | Admin | Admin dashboard and Orders |
| `demo-client` | Client | Shared Welcome → Open Client Portal |
| `demo-picker` | Warehouse Worker | Available orders |
| `demo-driver` | Delivery Worker | Delivery workspace |

1. In Client, create an order with a delivery address. To continue in a manual demo, start with an eligible paid fixture or complete a test sandbox checkout. The Warehouse order needs a positive, timestamped accepted payment and exact payment-linked held reservations.
2. In Warehouse, open **Available orders**, scan each exact product SKU, and confirm packing. Open the saved Package QR. Choose **Create Pickup List** as a separate action and open its QR.
3. In Delivery, confirm the assigned list with its QR. Collect the package with its Package QR. Start the attempt, upload a synthetic photo or signature, and mark it **Delivered**.
4. Compare Client tracking and Admin order details. Delivery collection should record one pickup stock movement. Packing and list creation leave the reservations held.

For a reproducible demonstration from checkout through Delivered, run `FullFulfillmentFlowCheckTest` with the disposable PostgreSQL setup in the [Step 6 evidence](INTEGRATION_REPORT.md#step-6-full-end-to-end-verification). The suite signs a synthetic webhook and uses a mocked payment transport. The Client UI currently displays online payment as unavailable.

## Remaining limits

- Physical return receipt, condition-based stock adjustments, and redelivery need separate approval and integration. Delivery can report returns.
- Admin cancellation keeps the legacy inventory clamp. Inconsistent reserved stock can make the ledger release larger than the actual decrement.
- Active legacy payment reconciliation uses a [manual runbook](STEP_2_PAYMENT_RECONCILIATION.md). There is no Admin reconciliation API. A status-only acceptance cannot authorize fulfillment or refund.
- A `REQUESTED` refund records an obligation. The provider refund placeholder does not transfer money.
- Before rollout, confirm the intended environment’s migration history, proof storage, and provider configuration. [Final handoff checklist](INTEGRATION_CHECKLIST.md#final-validation-and-handoff)
