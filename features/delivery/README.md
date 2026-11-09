# LMS Delivery App

A local Delivery Worker demo for the Logistics Management System. The local design uses the enhanced B direction requested for this module: a React interface and a Java 21 / Spring Boot 3.5.16 JSON API. This choice does not record approval by the full project team.

Delivery actions must be accepted by the Java backend before the screen changes. Warehouse, Client and Admin actions in the integration sandbox are clearly marked **Place holder**. They are simulated actors used to demonstrate the handoffs; they are not authorization for a Delivery Worker to perform those actions in the final LMS.

## Find the right file

| Task | Start here |
|---|---|
| Build and demo the app | [Build script](Build-Delivery.ps1), [Windows launcher](Start%20Delivery%20App.cmd) and [demo walkthrough](docs/DEMO.md) |
| Change a screen | [React screens and styles](frontend/src/), [Vite configuration](frontend/vite.config.js) and [original Delivery prototype](docs/prototypes/delivery_field_app_pickup_proof_ken/screen.png) |
| Change an API or business rule | [Java backend](backend/src/main/java/hk/edu/hkmu/lms/delivery/), [API contract](API_CONTRACT.md) and [U-01 to U-16 decisions](docs/DECISIONS.md) |
| Inspect SQL or seed data | [Database guide](docs/DATABASE.md) and [current H2 SQL](backend/src/main/resources/schema.sql) |
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
|-- frontend/      React source, configuration and locked dependencies
|-- docs/          Demo, decisions, integration, testing and database guides
|   |-- database/  Database draft, ERDs and review
|   |-- project/   Requirements, workflow baseline, questionnaire and references
|   `-- prototypes/  HTML designs, screenshots and shared design notes
|-- qa/            Test scripts and recorded evidence
`-- Dockerfile    Container build
```

The first demo launch seeds an empty local database. Build output goes to `frontend/dist/`, `backend/target/` and `delivery-app.jar`. Keep the launch scripts and the generated JAR in this feature folder. GitHub Actions configuration lives under `.github/workflows/` at the repository root and runs against the source checkout.

## Build and start from source

This checkout supplies the source and build scripts. Build the JAR and React assets before using the launcher. Use a Java 21 JDK, Node.js 24 and npm. The first build downloads tools and dependencies.

On Windows, set `JAVA_HOME` to the Java 21 JDK installation folder and run these commands from the repository root.

```powershell
Set-Location features/delivery
.\Build-Delivery.ps1
& '.\Start Delivery App.cmd'
```

The build obtains Maven 3.9.11 if needed, installs the locked frontend dependencies, builds React, runs the backend tests and creates `delivery-app.jar`. The launcher calls `Run-Delivery.ps1`. If your local PowerShell execution policy blocks the build script, run `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\Build-Delivery.ps1` from this feature folder.

Open `http://localhost:8080` when the application is ready. Sign in with either demo account.

| Username | Password | Role |
|---|---|---|
| `ken` | `Delivery123!` | Delivery Worker |
| `mei` | `Delivery123!` | Delivery Worker |

On macOS or Linux, install Maven 3.9.11 as well as the JDK and Node.js. From the repository root, run the equivalent build and launch commands.

```sh
cd features/delivery
(cd frontend && npm ci && npm run build)
mvn -f backend/pom.xml clean package
java -jar backend/target/delivery-app.jar --spring.profiles.active=demo --server.address=127.0.0.1
```

If another app uses port 8080, stop it or start this Windows copy with `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\Run-Delivery.ps1 -Port 8081` and open `http://localhost:8081`. On macOS or Linux, add `--server.port=8081`.

These are two separate demo Delivery identities. The name Mei here does not grant Warehouse access. Use a separate browser profile or private window to compare ownership between workers. Stop the server with Ctrl+C in its window before making an offline backup.

## Source development

For separate development servers, use Maven 3.9.11 or the copy downloaded by the Windows build. Start the backend from `features/delivery/backend`.

```powershell
mvn spring-boot:run
```

Start the frontend from `features/delivery/frontend` in another terminal.

```powershell
npm ci
npm run dev
```

The backend uses port **8080** and the development interface uses **5173**. The frontend must forward `/api` to the backend so session cookies and CSRF protection use the same browser origin. The packaged application serves the built interface from port 8080.

## Saved local data

Launch from the app folder. The default database is `data/database` (H2 adds its file extension), and private proofs use `data/proofs`. Set `LMS_DATA_DIR` before starting to use a different data directory. Do not point two running app copies at the same local store.

The default demo listens on this computer only. Confirmed package, attempt and return records were verified across a real server restart. When running Maven from `backend`, the default relative data folder is instead under `backend`; use an absolute `LMS_DATA_DIR` if you need the same store, with only one server running against it.

For an offline backup, stop the app and copy the whole `data` folder as one database/proof checkpoint. For the default folder, `Backup-Delivery.ps1` creates a ZIP and `Restore-Delivery.ps1 -BackupFile <zip-path> -TargetFolder <new-folder>` restores into a new folder without replacing working data. An offline database/proof round trip passed a two-file hash comparison. Starting the restored copy and checking business records is still a separate recovery check. [INTEGRATION.md](docs/INTEGRATION.md) explains the remaining hosting/storage work.

## What is real and what is a Place holder

Implemented local Delivery work includes authenticated worker sessions, list claims, individual pickup, separate attempts, failure/return reporting, private signature/photo proof handling, ownership checks and safe request retries. Backend tests check direct inventory deductions/restoration, transaction rollback, duplicate effects, damaged-stock holds and expiry summaries. The sandbox demonstrates external handoffs using local records.

The 9 November source reorganization passed the production build, all 11 backend tests, 19 HTTP API checks, desktop and mobile viewport checks, and the UI signature workflow. The 5 November 2026 runnable package recorded **10 passing backend JUnit tests**, **19 passing HTTP API checks**, desktop/mobile Chrome checks and a complete UI signature delivery flow. The same release also has restart persistence and offline backup evidence. Those reports travel with this checkout for comparison. Camera hardware, the full U-02 workload, cloud services and live team integrations remain open verification items. [Testing guide](docs/TESTING.md)

| Boundary | Status and future owner |
|---|---|
| Shared authentication and staff accounts | **Place holder**: local demo accounts; the shared authentication owner must supply verified users, roles, current authorization and session lifecycle. |
| Warehouse lists, packing and return receipts | **Place holder**: seeded data and sandbox controls; the Warehouse owner must supply eligible packages, real physical receipt and condition data. |
| Client instructions and delivery-detail changes | **Place holder**: sandbox instructions; the Client owner must supply authenticated, versioned requests tied to the latest return and delivery details. |
| Admin approval and exceptional release | **Place holder**: sandbox decisions; the Admin owner must supply authorized approvals and preserved actor history. |
| Shared orders, inventory and database | **Place holder**: local H2 data; the Database and shared-service owners must map the contract to the agreed relational schema and transaction boundaries. |
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
