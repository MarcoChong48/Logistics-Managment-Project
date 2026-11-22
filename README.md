# Logistics Management System — Client Portal

This repository contains a team Logistics Management System (LMS) built with **Java 21, Spring Boot 4.1.2, PostgreSQL 17, and Docker Compose**. This README describes the **Client implementation on `feature/client-backend`**. The repository also contains shared authentication and the Admin module; see the module guides below.

**Status (22 November 2026):** Milestone 10 (Client Portal Frontend Integration) passed its reported automated checks and a local manual acceptance walkthrough. The five Client pages use backend APIs rather than static demo data. Shared Welcome now provides an **Open Client Portal** link for eligible Clients. Final repository sign-off and cross-team integration are separate from this functional acceptance. **Milestone 9 (post-delivery-failure Return / Redelivery Requests) remains deferred** until work resumes on it.

## Client Portal features

| Page | What Clients can do |
| --- | --- |
| **Account Settings** | View account details and save or reload default recipient name, phone, and delivery address. Saved defaults prefill new orders; they do not alter existing orders. |
| **Create Order** | Browse available products, select quantities, review an estimate, and create an order with saved or edited delivery details. Successful creation produces an order reference and `AWAITING_PAYMENT` status. |
| **My Orders & Tracking** | View the signed-in Client's order history, amounts, and statuses, and open an order for details. |
| **Order Details** | View purchased items, historical unit prices, delivery details, tracking information, payment/refund summaries, and delivery attempts where recorded. Cancel eligible orders before picking starts. |
| **Notifications** | View the Client's own inbox and unread count; mark individual notifications or all current notifications as read. Read state persists in PostgreSQL. |

**Access and security:** Shared Login manages sessions, password changes, and role checks. Only an authenticated `CLIENT` who is not required to change their password sees **Open Client Portal** on Shared Welcome. Client pages independently verify the session and role. Mutating requests use the shared CSRF protection; Create Order additionally uses an idempotency key to protect retries.

## Quick start — local development

> **Local test environment only.** The Compose configuration uses development credentials and binds the web app to loopback (`127.0.0.1`). Do not expose this setup publicly or use real customer or payment data.

1. Check out `feature/client-backend`, open the **repository root** (the folder containing `compose.yaml`), and start Docker Desktop in Linux containers mode.
2. Create a local `.env` file if one does not already exist, then set a dedicated bootstrap Admin password in it:

   ```powershell
   if (!(Test-Path .env)) { Copy-Item .env.example .env }
   notepad .env
   ```

   Set `LMS_BOOTSTRAP_ADMIN_TEMPORARY_PASSWORD` to a local-only password meeting the shared password rules. Keep `.env` out of Git.
3. On an **existing** PostgreSQL volume, check for pending schema migrations *before* starting the updated backend. PostgreSQL initialization scripts run automatically only for a fresh volume. Follow [database migrations](database/README.md) for scripts `03`, `04`, and `06` where required. Do not rerun migrations that are already applied, rerun the baseline on populated data, or delete the volume to make a migration run.
4. Start the stack from the repository root:

   ```powershell
   docker compose up --build -d
   docker compose ps
   ```

5. Confirm that `db` is healthy and `backend` remains running. Open [system health](http://127.0.0.1:8081/api/shared/health); the JSON should report `sharedCheckSystemHealthDatabaseIsConnected: true`. The default non-Stripe local schema has **28 tables**.
6. Open the [LMS sign-in page](http://127.0.0.1:8081/shared/shared-login.html). The default bootstrap Admin username is `admin_local` (changeable through `LMS_BOOTSTRAP_ADMIN_USERNAME` in `.env`). Sign in using its configured password and change the temporary password if prompted. From **Admin dashboard → Users → Create user**, create a test user and select role `CLIENT`. Sign out, sign in with the new Client account, and complete any required password change.
7. On **Shared Welcome**, select **Open Client Portal**, which opens [Account Settings](http://127.0.0.1:8081/client/account-settings.html). The Client sidebar links to Create Order, My Orders & Tracking, Notifications, and Account Settings. Order Details opens from an existing order.

If the catalogue is empty in your **isolated local test database**, optional fictional products and stock can be inserted using `database/02_sample_data.sql`:

```powershell
docker compose cp .\database\02_sample_data.sql db:/tmp/02_sample_data.sql
docker compose exec db psql -v ON_ERROR_STOP=1 -U lms -d lms -f /tmp/02_sample_data.sql
```

That script is **not** automatically applied. Do not apply it to shared or production databases. Stop the local stack without removing its data by running `docker compose down` (without `-v`).

**Important:** The Compose backend receives its JDBC credentials and active `admintest` profile from `compose.yaml`. Running `.\mvnw.cmd spring-boot:run` from the `backend` folder on Windows does not inherit those settings and will fail without additional local datasource configuration. Use Docker Compose for the documented quick start.

## Manual Client acceptance walkthrough

Run these actions using a disposable Client account on the local test stack:

1. **Account Settings:** Save sample delivery defaults, reload them, and refresh the browser. Verify they persist.
2. **Create Order:** Check the product catalogue and automatic delivery-detail prefill. Create a sample order and confirm its reference, amount, `AWAITING_PAYMENT` status, and payment deadline.
3. **Order Details / History:** Open the new order, verify the saved items and delivery information, and confirm that it appears in My Orders & Tracking. Cancel the unpaid order and confirm the status changes to `CANCELLED` both on the details page and in the list.
4. **Notifications:** Verify the empty state. For read-action testing, use approved synthetic notification fixtures on an isolated database; check **Mark as read**, **Mark all as read**, unread counts, and persistence after refresh.
5. **Access control:** Verify that Client login displays **Open Client Portal** and opens Account Settings. Admin login must not show that link, and an Admin navigating directly to a Client URL must be denied. Guest and forced-password-change sessions must not enter the portal.

**Automated verification (reported):** 244 Maven regression tests, 64 Chromium fixture tests, five isolated real-server browser tests for Shared Welcome integration, and seven focused Chromium integration checks passed in the final reports. A separate M10E real-server acceptance run passed, along with Firefox smoke testing.

**Manual browser acceptance (performed 22 November 2026):** The project owner also exercised Account Settings save/reload, sample order creation/history/tracking/cancellation, individual and bulk notification read actions (including refresh persistence), Shared Welcome → Client Portal navigation, and Admin access denial against the local Docker/PostgreSQL stack. This is a specific local walkthrough, not a claim of broader production or cross-team acceptance. These results do **not** establish production readiness or complete cross-team workflows.

## Scope, limitations, and next milestone

- **Online payment:** Stripe is disabled in the default local Compose stack. A Client can create an unpaid order, but the current UI reports online payment as unavailable. Stripe sandbox verification and payment-cutoff decisions remain outstanding; do not describe live checkout as complete.
- **Delivery and Warehouse:** Real end-to-end picking race handling, production Delivery notification publication, and some paid or failed-delivery scenarios still require cross-team integration. Isolated seeded fixtures are not equivalent to production workflows.
- **Milestone 9 — deferred:** After a failed delivery, implement Warehouse physical return check-in; Client return/redelivery instruction APIs; Admin approval/rejection; and Delivery redelivery-cycle integration. These controls are intentionally not available in the M10 Client UI.
- **Client registration:** Public Client self-registration is **not implemented**. An authorized Admin must currently provision Client accounts through **Users → Create user**. This requires a separate implementation and security review before being advertised as a public feature.
- **Other open checks:** Physical-device acceptance remains pending. Avoid using real personal information in local fixtures.

## Repository guides

- [CLIENT_README.md](CLIENT_README.md) — detailed Client API behavior, validation, order/checkout rules, notifications, account defaults, and integration limits.
- [ADMIN_README.md](ADMIN_README.md) — shared/Admin setup, user provisioning, local test tools, and Admin acceptance guidance.
- [database/README.md](database/README.md) — PostgreSQL schema, table mapping, migration order, and sample-data policy.
- [`compose.yaml`](compose.yaml) — local PostgreSQL and backend services.
- [`backend/src/main/resources/static/client/`](backend/src/main/resources/static/client/) — Client Portal pages and JavaScript.
- [`backend/src/test/js/`](backend/src/test/js/) — browser and Client integration fixtures/tests.

For local work, keep Client changes on `feature/client-backend`, review the diff and tests before committing, and coordinate Shared Welcome changes with the shared-authentication owner.
