# Logistics Management System · Admin

`feature/admin-system` contains the Admin module and shared sign-in for the team's Logistics Management System. It builds on `feature/database-setup` using Java 21, Spring Boot 4.1.2, PostgreSQL 17, and Docker Compose.

**Stage 2 (User accounts) passed on 19 November 2026.** Three of ten stages are complete. Stage 2 passed all 27 acceptance cases and three regression checks. The latest automated run passed 81 checks. Stage 3 (Logs) is next.

## What's included

- **Setup.** Local database with 28 tables, including Client order idempotency, a health endpoint, the Admin page frame, and test tools for creating sample accounts and work assignments.
- **Shared login.** Sign-in and sign-out, role-based access, an idle timeout, temporary login lockouts, password changes, and forced replacement of temporary passwords.
- **User accounts.** Search, filter, and create accounts for all four LMS roles. Admins can edit Delivery Worker profiles, preview work affected by account changes, change eligible staff roles, ban or deactivate accounts and reverse those actions, unlock accounts, and reset passwords.
- **Audit records.** Sign-in security events and Admin account changes are recorded. The Admin log viewer is planned for Stage 3.

## Ten-stage roadmap

| Stage | Area | Status |
| --- | --- | --- |
| 0 | Setup | Passed |
| 1 | Shared login | Passed |
| 2 | User accounts | Passed |
| 3 | Logs | Planned next |
| 4 | Products | Planned |
| 5 | Orders | Planned |
| 6 | Warehouse cases | Planned |
| 7 | Delivery cases | Planned |
| 8 | Refund problems | Planned |
| 9 | Dashboard and final checks | Planned |

## Set up and test locally

1. Use this checkout or extract the [Client branch ZIP](https://github.com/MarcoChong48/Logistics-Managment-Project/archive/refs/heads/feature/client-backend.zip).
2. Give your AI coding assistant the **entire unzipped project folder** by opening it as the assistant's workspace. Select the folder containing `compose.yaml`, `backend/`, and `database/`. Do this before creating `.env`. Ask the assistant to read `ADMIN_README.md` and help you start and test the Stage 2 Admin system.
3. Start Docker Desktop in Linux containers mode. In the extracted folder, copy `.env.example` to `.env` and set `LMS_BOOTSTRAP_ADMIN_TEMPORARY_PASSWORD` to a password for this local test.
4. If you are reusing an initialized volume, follow the [database migration steps](database/README.md#client-order-idempotency) before rebuilding the backend. Then run these commands from the folder containing `compose.yaml`. Wait for `db` to show as healthy and `backend` to show as running.

   ```powershell
   docker compose up --build -d
   docker compose ps
   ```

5. Open `http://127.0.0.1:8081/`. Sign in as `admin_local` with the temporary password you set and change it when prompted. Open **Test tools** to create sample accounts, then use **Users** to test search, filters, and account actions.

For password requirements, database checks, and automated tests, see the [Admin setup guide](ADMIN_README.md).
