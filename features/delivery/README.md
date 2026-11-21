# LMS Delivery App v3.2

A Delivery Worker app for the Logistics Management System, with a local demo profile. The app uses the enhanced B direction, with an HTML, CSS and native JavaScript interface and a Java 21 / Spring Boot 4.1.2 JSON API. Supabase PostgreSQL uses the team v0.4 draft and Delivery extensions.

Delivery actions update the screen after the Java backend accepts them. Demo-only **Test tools** uses clearly labelled **Place holder** controls to simulate Warehouse, Client and Admin handoffs. These controls run against demo data under simulated staff identities.

[繁體中文組員使用指南](docs/QUICKSTART_ZH_HK.md)

## Run the local demo with Docker Compose

Start Docker Desktop in Linux containers mode. From this repository's `features/delivery/` folder, run:

```powershell
docker compose up --build -d
docker compose ps
```

Wait for `db` to be healthy and `backend` to be running. Open [http://127.0.0.1:8080](http://127.0.0.1:8080) and sign in with a demo account.

| Username | Password | Role |
|---|---|---|
| `ken` | `Delivery123!` | Delivery Worker |
| `mei` | `Delivery123!` | Delivery Worker |

Compose builds the Java 21 app and starts a private PostgreSQL 17 database with fictional demo data. The first build downloads images and build dependencies. PostgreSQL data and private proof files each have their own named volume. [Docker Compose settings](compose.yaml)

To see startup logs, run `docker compose logs -f backend`. To stop Delivery and keep the demo data, run `docker compose down` from the same folder. The Admin reference uses port 8081. If port 8080 is busy, run `$env:DELIVERY_PORT = '8082'` in PowerShell before starting Compose and open [http://127.0.0.1:8082](http://127.0.0.1:8082).

The Java/H2 launch is described below. The hosted database setup uses the Supabase profile with team connection settings. [Database guide](docs/DATABASE.md)

## Delivery workspace

| Page | Use |
|---|---|
| Overview | Check Delivery work and open the relevant page. |
| Pickup | View your assigned lists, confirm a list at the warehouse, scan each package, report issues and close the list. |
| My deliveries | Open an assigned package, start an attempt and record the outcome and proof. |
| Return handoffs | Report a handoff and follow Warehouse receipt, client instructions, Admin approval and redelivery using saved backend status. |
| Attempt history | Find earlier attempts and review outcome and proof metadata. An authorized worker on a current active attempt can preview its proof. |
| Notifications | Review saved notifications and filter unread items. |
| Decisions & integrations | Look up local decisions and integration boundaries. The developer handoff is in the [integration guide](docs/INTEGRATION.md). |
| Test tools | In the demo profile, simulate external steps using labelled **Place holder** actions. |

Delivery assigns each ready list to the active worker with the fewest current lists. In **Pickup**, scan or enter your assigned list QR code to confirm pickup. Scan each package separately to record collection. [Assignment and integration rules](docs/INTEGRATION.md)

The workspace follows the shared design notes for menus, forms, dialogs and tables. The top bar shows the live session countdown. Use **Change password** to update your password and sign in again. The change also expires other sessions for the same account on their next API request.

## Find the right file

| Task | Start here |
|---|---|
| Run the app in Docker | [Compose settings](compose.yaml) and [demo walkthrough](docs/DEMO.md) |
| Build and run with local Java | [Build script](Build-Delivery.ps1), [Windows launcher](Start%20Delivery%20App.cmd) and [demo walkthrough](docs/DEMO.md) |
| Change a screen | [Browser page modules](frontend/src/features/), [workspace frame](frontend/src/app/shell.js), [shared styles](frontend/src/shared/shared-common.css), [HTML entry page](frontend/index.html) and [original Delivery prototype](docs/prototypes/delivery_field_app_pickup_proof_ken/screen.png) |
| Change an API or business rule | [Java backend](backend/src/main/java/hk/edu/hkmu/lms/delivery/), [API contract](API_CONTRACT.md) and [U-01 to U-16 decisions](docs/DECISIONS.md) |
| Inspect SQL or seed data | [Supabase database guide](docs/DATABASE.md) and [local H2 demo SQL](backend/src/main/resources/schema.sql) |
| Connect another team module | [Integration handoff](docs/INTEGRATION.md) and [API contract](API_CONTRACT.md) |
| Locate code for a specific workflow | [Source structure and import rules](docs/ARCHITECTURE.md) |
| Run tests or review evidence | [Testing guide](docs/TESTING.md), [backend tests](backend/src/test/) and [QA reports](qa/evidence/) |
| Check project rules or earlier designs | [Project reference index](docs/project/README.md) and [database review](docs/database/LMS_Database_Draft_v0_4_Review.md) |

```text
features/delivery/
|-- README.md, Start Delivery App.cmd
|-- Run-Delivery.ps1, Build-Delivery.ps1, Backup-Delivery.ps1, Restore-Delivery.ps1
|-- API_CONTRACT.md, THIRD_PARTY_NOTICES.md
|-- backend/       Java, SQL, configuration and JUnit tests
|-- frontend/      HTML, CSS, browser modules and static assets
|-- docs/          Demo, decisions, integration, testing and database guides
|   |-- database/  Database draft, ERDs and review
|   |-- project/   Requirements, workflow baseline, questionnaire and references
|   `-- prototypes/  HTML designs, screenshots and shared design notes
|-- supabase/      PostgreSQL migrations based on database draft v0.4
|-- qa/            Test scripts and recorded evidence
`-- compose.yaml and Dockerfile    Local PostgreSQL demo and app image
```

Each demo setup seeds its database on first launch. Maven bundles the frontend files directly into the JAR. Build output goes to `backend/target/` and `delivery-app.jar`. Keep the launch scripts and the generated JAR in this feature folder. GitHub Actions configuration lives under `.github/workflows/` at the repository root and runs against the source checkout.

## Build and start with local Java and H2

This checkout supplies the source and build scripts. Build the JAR before using the launcher. Use a Java 21 JDK and Maven. The first build downloads tools and dependencies. Node.js is used only for optional browser and HTTP QA scripts.

On Windows, set `JAVA_HOME` to the Java 21 JDK installation folder and run these commands from the repository root.

```powershell
Set-Location features/delivery
.\Build-Delivery.ps1
& '.\Start Delivery App.cmd'
```

The build obtains Maven 3.9.11 if needed, packages the frontend files, runs the application and architecture tests and creates `delivery-app.jar`. The launcher calls `Run-Delivery.ps1`. If your local PowerShell execution policy blocks the build script, run `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\Build-Delivery.ps1` from this feature folder.

Open `http://localhost:8080` when the application is ready. Sign in with either demo account.

On macOS or Linux, install Maven 3.9.11 and the Java 21 JDK. From the repository root, run the equivalent build and launch commands.

```sh
cd features/delivery
mvn -f backend/pom.xml clean package
java -jar backend/target/delivery-app.jar --spring.profiles.active=demo --server.address=127.0.0.1
```

If another app uses port 8080, stop it or start this Windows copy with `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\Run-Delivery.ps1 -Port 8082` and open `http://localhost:8082`. On macOS or Linux, add `--server.port=8082`.

These are two separate demo Delivery identities. The name Mei here does not grant Warehouse access. Use a separate browser profile or private window to compare ownership between workers. Stop the server with Ctrl+C in its window before making an offline backup.

## Source development

Edit `frontend/index.html`, `frontend/src/` or the Java sources. The Java server serves the static page and API on the same origin. Start the app from `features/delivery/backend` with Maven.

```sh
mvn -Dspring-boot.run.profiles=demo spring-boot:run
```

Run it again to copy changed frontend files into the static resource directory. For Supabase setup and the schema review, follow the [database guide](docs/DATABASE.md).

## Saved data from the Java and H2 launcher

Launch from the app folder. The local demo database is `data/database` (H2 adds its file extension), and private proofs use `data/proofs`. Set `LMS_DATA_DIR` before starting to use a different data directory. Do not point two running app copies at the same local store.

The default demo listens on this computer only. Confirmed package, attempt and return records were verified across a real server restart. When running Maven from `backend`, the default relative data folder is instead under `backend`; use an absolute `LMS_DATA_DIR` if you need the same store, with only one server running against it.

For an offline H2 backup, stop the app and copy the whole `data` folder as one database/proof checkpoint. For the default folder, `Backup-Delivery.ps1` creates a ZIP and `Restore-Delivery.ps1 -BackupFile <zip-path> -TargetFolder <new-folder>` restores into a new folder without replacing working data. An offline database/proof round trip passed a two-file hash comparison. Starting the restored copy and checking business records is still a separate recovery check. [INTEGRATION.md](docs/INTEGRATION.md) explains the remaining hosting/storage work.

## Delivery features and integration status

Implemented local Delivery work includes authenticated worker sessions, self-service password change, automatic list assignment, list confirmation, individual pickup, separate attempts, failure/return reporting, private signature/photo proof handling, ownership checks and safe request retries. Backend tests cover inventory deductions and restoration, transaction rollback, duplicate effects, damaged-stock holds and expiry summaries. Test tools demonstrates external handoffs using local records.

The 9 November source reorganization passed the production build, all 11 backend tests, 19 HTTP API checks, desktop and mobile viewport checks, and the UI signature workflow. The 5 November 2026 runnable package recorded **10 passing backend JUnit tests**, **19 passing HTTP API checks**, desktop/mobile Chrome checks and a complete UI signature delivery flow. The same release also has restart persistence and offline backup evidence. Those reports travel with this checkout for comparison. Camera hardware, the full U-02 workload, cloud services and live team integrations remain open verification items. [Testing guide](docs/TESTING.md)

| Boundary | Status and future owner |
|---|---|
| Shared authentication and staff accounts | Delivery uses Spring Security for username login, server sessions and changing your own password. Shared account provisioning, role lifecycle and Admin password resets remain **Place holder** work. |
| Warehouse lists, packing and return receipts | **Place holder** demo data and test controls. The Warehouse owner supplies eligible packages, real physical receipt and condition data. |
| Client instructions and delivery-detail changes | **Place holder**: sandbox instructions; the Client owner must supply authenticated, versioned requests tied to the latest return and delivery details. |
| Admin approval and exceptional release | **Place holder** demo decisions. The Admin owner supplies authorized approvals and preserved actor history. This release changes the Delivery workspace only. |
| Shared orders and inventory | The Supabase profile uses PostgreSQL migrations based on v0.4. Cross-role service calls and final ownership of shared transactions are **Place holder** work. |
| Notifications and refunds | **Place holder**: local events do not prove remote notification or provider refund; the notification and payment owners must deliver and reconcile their actual results. |
| Cloud proof storage and deployment | **Place holder**: private local files and local launch; hosting/storage owners must configure durable protected services, credentials, backups and recovery. |

Do not enter real customer details or real payment data in the demo. No external charge or refund is performed.

## More detail

- [Demo walkthrough](docs/DEMO.md)
- [Local decisions for U-01 to U-16](docs/DECISIONS.md)
- [Integration boundaries and data rules](docs/INTEGRATION.md)
- [Test scenarios and verification status](docs/TESTING.md)
- [API contract](API_CONTRACT.md)

The app interface and code comments use English. Comments should explain one short rule or reason in simple English. Use `Decision U-xx, B:` near a local B choice, and `Place holder:` near an external replacement point, naming the future owner and required data.
