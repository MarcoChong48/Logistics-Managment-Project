# Build a role in the LMS

## 1. Start from the Admin branch

`feature/admin-system` contains the shared Spring Boot application, the PostgreSQL schema and the Admin module. The Stage 2 update was published on 19 November 2026. Its Client, Warehouse and Delivery addresses required a login. Stage 3 adds the role checks below.

## 2. Run the local stack

Open Docker Desktop in Linux containers mode. In PowerShell, from the folder containing `compose.yaml`, run:

```powershell
Copy-Item .env.example .env
notepad .env
```

Set `LMS_BOOTSTRAP_ADMIN_TEMPORARY_PASSWORD` in `.env` to a password for this local stack. You can keep the example username. Start the application:

```powershell
docker compose up --build -d
docker compose ps
```

When the database is healthy, open [the local sign-in page](http://127.0.0.1:8081/). Sign in as the bootstrap Admin with the temporary password you set, choose a new password and sign in again. Keep `.env` local. See the [Admin setup guide](ADMIN_README.md) for startup and schema checks.

## 3. Get a test account for your role

In the Admin window, open **Test tools**. Under **Stage 1**, choose your role and click **Create test user**. Copy the username and the temporary password from the result. The tool shows that password once, and it is valid for 24 hours. In a separate browser profile or an Incognito window, sign in with it, choose a password and then sign in again. This tool requires the `admintest` profile.

## 4. Put code under your role

| Role | Java package | Page folder under `backend/src/main/resources/` | Page and API prefixes |
| --- | --- | --- | --- |
| Client | `com.lms.backend.client` | `static/client/` | `/client`, `/api/client` |
| Warehouse Worker | `com.lms.backend.warehouse` | `static/warehouse/` | `/warehouse`, `/api/warehouse` |
| Delivery Worker | `com.lms.backend.delivery` | `static/delivery/` | `/delivery`, `/api/delivery` |

Each prefix covers the bare address and paths underneath it.

| Address | Access |
| --- | --- |
| `/`, `/index.html`, `/shared/…` | Anyone. Individual shared pages can require a session to work. |
| `/api/shared/health`, `/api/shared/login`, `/api/shared/logout`, `/api/shared/session` | Anyone. Changing requests still require a security token. |
| `/api/shared/session/extend`, `/api/shared/change-password` | Any signed-in role, including users changing a temporary password. |
| `/admin`, `/api/admin` | Admin only. Test tools also require the test profile. |
| `/client`, `/api/client` | Client only. |
| `/warehouse`, `/api/warehouse` | Warehouse Worker only. |
| `/delivery`, `/api/delivery` | Delivery Worker only. |

The shared layer checks the session and any required password change, then the security token for changing requests and then the role. Your handlers still check access to the particular order, task or package.

## 5. Call the server from a page

Load the shared stylesheet and script:

```html
<link rel="stylesheet" href="/shared/shared-common.css">
<script defer src="/shared/shared-common.js"></script>
```

Start with `await sharedSendRequest("GET", "/api/shared/session")` to read the session and initialize the security token. Call your API with `sharedSendRequest(method, webAddress, body)`. It supplies the security token for changes and handles ended sessions. Use it for every changing request.

## 6. Read the signed-in person in Java

Inject `SharedCurrentUserReader` from `com.lms.backend.shared.login`. Call `sharedReadCurrentUser()` to obtain the signed-in `SharedCurrentUser`, whose accessors are `userID()`, `username()` and `role()`. Use its ID in ownership checks and saved records that need an actor.

## 7. Set your start page

When your first page is ready, change only your role's return value in `SharedStartPageRule.sharedFindStartPage` (PO-10). The shared login and session endpoints read this rule. Until then, your role opens the shared welcome page.

## 8. Use the shared design

Build pages with the parts in the [general design note](docs/DESIGN_NOTES_GENERAL.md) and `/shared/shared-common.css`. The note describes the layout, form controls, tables, messages and shared interactions.

## 9. Coordinate changes to shared files

Keep your work in your role's folders. Keep `shared/` under the Admin owner except for your start-page line. Leave `admin/`, `database/01_create_tables.sql`, `database/03_admin_changes.sql` and `compose.yaml` to their owners. Agree on new numbered scripts for new tables with the Database owner. Ask the Admin owner for changes to shared code.

## 10. Take an Admin update

After each passed Admin stage the Admin owner publishes the accepted update to `feature/admin-system`. Fetch it and merge it into your own feature branch:

```powershell
git fetch origin feature/admin-system
git merge origin/feature/admin-system
```

Keep role-branch changes on your branch for integration with the team.

## 11. Move Delivery in by steps

1. Build the Delivery React app and place its built files under `static/delivery/`. Move its API addresses under `/api/delivery/`.
2. Replace its file database with the shared PostgreSQL tables.
3. Replace its demo sign-in with the shared session and role checks.

All three steps target Spring Boot 4.1.2. Until the move is complete, the original Delivery program continues to run separately.

## 12. Resolve the remaining team decisions

Team adoption of the one-program setup, shared-code ownership and merge rules remain open under U-03 and U-05.
