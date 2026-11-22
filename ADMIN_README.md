# Admin system setup

LMS Admin runs on a local Spring Boot application with a PostgreSQL database. It provides account and product management, operational work lists, orders, and activity logs. The Dashboard shows work counts and links to their filters. The shared sign-in protects each role's own pages and APIs.

## Install and start

1. Install and start Docker Desktop in Linux containers mode. The first build downloads the Java 21 and PostgreSQL 17 images and Maven dependencies. [Docker Desktop setup](https://docs.docker.com/desktop/)
2. In File Explorer, open the folder containing this README and `compose.yaml`. Type `cmd` in the Explorer address bar, press Enter, and run `powershell.exe -NoProfile` in the Command Prompt that opens. Use that PowerShell session for the commands below. Run `Get-Location` to check that you are in the Compose folder.
3. Create `.env` from the example if needed. In that file, set `LMS_BOOTSTRAP_ADMIN_TEMPORARY_PASSWORD` to a temporary password for your local stack. Use at least eight characters, including an uppercase letter, a lowercase letter, and a digit. The password can use at most 72 UTF-8 bytes. Git ignores `.env`.

   ```powershell
   if (-not (Test-Path .env)) { Copy-Item .env.example .env }
   notepad .env
   ```

4. If another LMS stack is running on port 8081, run this command from that stack's own Compose folder to release the port and preserve its volume. Return to this checkout for the next step.

   ```powershell
   docker compose down
   ```

5. From the Admin Compose folder, build and start the services. Wait a minute and check that `db` is healthy and `backend` is running with `127.0.0.1:8081` in the Ports column.

   ```powershell
   docker compose up --build -d
   Start-Sleep -Seconds 60
   docker compose ps
   ```

If startup fails, run `docker compose logs --tail 100 db backend`. A Whitelabel 404 after a Windows restart can mean the original stack is listening on 8081. If the intended Admin backend exits with `UnknownHostException: db` while its database is healthy, recreate the Admin backend from the Admin Compose folder.

```powershell
docker compose up -d --no-deps --force-recreate backend
```

## First sign-in and super admin

Open the sign-in page. Use the configured `LMS_BOOTSTRAP_ADMIN_USERNAME` and `LMS_BOOTSTRAP_ADMIN_TEMPORARY_PASSWORD` from `.env`. The default username is `admin_local`. Choose a new password when prompted, then sign in with it. A temporary password expires after 24 hours. Once a usable Admin exists, a normal restart keeps that account's password. [Sign in](http://127.0.0.1:8081/)

The configured bootstrap account is the **super admin**. It alone creates and changes other Admin accounts. It appears with the **Super admin** flag on Users. Other Admins can manage Client, Warehouse Worker and Delivery Worker accounts.

### Recover the super admin password

1. In `.env`, set `LMS_BOOTSTRAP_ADMIN_RESET_ON_START` to true and set a new valid temporary password in `LMS_BOOTSTRAP_ADMIN_TEMPORARY_PASSWORD`.
2. From the Compose folder, run the following and wait for the backend. Open Sign in, use the temporary password and choose a new permanent password.

   ```powershell
   docker compose up -d
   ```

3. Restore `LMS_BOOTSTRAP_ADMIN_RESET_ON_START` to false and restore the normal password configuration. Run `docker compose up -d` again and sign in with the new permanent password. Restore the reset switch even if sign-in fails. A true switch resets the password on every backend startup.

A session ends after 15 minutes without a request by default. The top bar warns at two minutes remaining and offers **Stay logged in**. The Dashboard has a **Refresh** button so that an unattended tab can time out.

## Admin pages

| Page | Use |
| --- | --- |
| [Dashboard](http://127.0.0.1:8081/admin/admin-dashboard.html) | Open filtered lists from the open-case, order-status, and low-stock counts. See the database connection, table count and server time in the browser's local time. |
| [Warehouse cases](http://127.0.0.1:8081/admin/admin-warehouse-cases.html) | Review shortages and active picking tasks, decide how to handle cases and reassign picking tasks. |
| [Delivery cases](http://127.0.0.1:8081/admin/admin-delivery-cases.html) | Review failed deliveries, client instructions and held packages. |
| [Refunds](http://127.0.0.1:8081/admin/admin-refunds.html) | Check an unresolved refund or retry a confirmed failure. Clear **Unresolved only** to see completed and replaced attempts. |
| [Users](http://127.0.0.1:8081/admin/admin-users.html) | Manage accounts, roles and temporary passwords. |
| [Products](http://127.0.0.1:8081/admin/admin-products.html) | Manage catalogue entries, import CSV, view stock and print product QR labels. |
| [Orders](http://127.0.0.1:8081/admin/admin-orders.html) | Search orders or open an order's logistics, payment, and refund records. |
| [Logs](http://127.0.0.1:8081/admin/admin-logs.html) | Review audit, security and picking scan history and the latest retention run. |
| [Test tools](http://127.0.0.1:8081/admin/admin-temporary-test-tools.html) | Create synthetic data and run test-only actions while test mode is active. |

Users, Products, Orders, Refunds, Shortage cases, Failed deliveries and the three Logs tabs export their matching rows as CSV. If a table is wider than its card, drag the horizontal scrollbar below the rows or swipe across the table on a touch screen.

## Test mode

`SPRING_PROFILES_ACTIVE: admintest` under `backend.environment` in `compose.yaml` enables the **Test mode** flag, **Testing** menu and tool endpoints. Use test mode on a local database with synthetic data. The cards create fixtures, set refund stand-in answers and run the Stage 9 privacy check.

To switch test mode off, comment out that line in `compose.yaml` and run `docker compose up -d` from the Compose folder. Sign in again if prompted. The menu and flag disappear, and [Test mode JSON](http://127.0.0.1:8081/api/admin/temporary-test/mode) returns 404 for a signed-in Admin. Uncomment the line and run `docker compose up -d` again to restore it.

The refund stand-in runs in both modes. It records refund requests in PostgreSQL without moving money. Its default provider result is unknown. Test overrides reset when the backend restarts.

## Rule and flow checks

Run the database-free rule checks from the Compose folder.

```powershell
docker build --no-cache --target rule-check --progress plain ./backend
```

Run the combined rule and HTTP flow checks for Stages 0–9 with Docker Desktop running.

```powershell
docker compose run --rm --build flow-check
```

The flow service recreates `lms_flow_check` with the same initialization scripts and uses real sessions, HTTP requests and SQL assertions. It leaves the `lms` database used by the Admin browser session intact. Read the case lines, omitted steps and the two totals. Any rule or flow failure produces a nonzero exit. The first run downloads Maven dependencies.

## Check the database

On a fresh volume, PostgreSQL runs `01_create_tables.sql` followed by `03_admin_changes.sql`. Check [Health JSON](http://127.0.0.1:8081/api/shared/health) or list the 27 tables from PowerShell. The database instructions explain the seven Admin changes and how to apply the migration to a baseline-only volume. [Database setup](database/README.md)

```powershell
docker compose exec db psql -U lms -d lms -c "\dt"
docker compose exec db psql -U lms -d lms -c "\d app_user"
docker compose exec db psql -U lms -d lms -c "\d client_instruction"
```

## Stop or reset

To stop the services and keep their data, run `docker compose down` from the Compose folder. Use `docker compose up -d` to start them again.

For a fresh database, run the following only against a disposable Compose project. `docker compose down -v` deletes that project's PostgreSQL volume, including its accounts and passwords.

```powershell
docker compose down -v
docker compose up --build -d
```

For a test-data cleanup that keeps accounts, open **Test tools → Stage 9 → Remove all test data**, type `REMOVE`, and submit. It clears operational data and test accounts, keeping the signed-in account and the other accounts whose usernames do not start with `test_`.

## Team integration

Use the shared login and role folders in the team guide. The placeholder register identifies the interfaces and shared rows each owner needs to connect. The temporary code checklist lists the files and settings to remove at final hand-in. Keep test tools available while the team uses them to create local accounts. [Team guide](TEAM_GUIDE.md) · [Placeholder register](PLACEHOLDER_LIST.md) · [Temporary code checklist](TEMPORARY_CODE_LIST.md) · [Third-party notices](THIRD_PARTY_NOTICES.md)
