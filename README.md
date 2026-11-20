# Logistics Management System · Admin

`feature/admin-system` contains the Admin module and shared sign-in for the team's Logistics Management System. It builds on `feature/database-setup` using Java 21, Spring Boot 4.1.2, PostgreSQL 17, and Docker Compose.

**Stage 3 (Logs and shared login updates) passed on 20 November 2026.** Four of ten stages are complete. Stage 3 passed all 27 acceptance cases and seven repeats.

Start with the [team integration guide](TEAM_GUIDE.md) to add a role to the shared application.

## What's included

- **Setup.** Local database with 27 tables, a health endpoint, the Admin page frame, and test tools for creating sample accounts and work assignments.
- **Shared login.** Sign-in and sign-out, an idle timeout, temporary login lockouts, and separate password-change flows for temporary and permanent credentials. Each role's pages and APIs are protected by its role check.
- **User accounts.** Search, filter, and create accounts for all four LMS roles. Admins can edit Delivery Worker profiles, preview work affected by account changes, change eligible staff roles, ban or deactivate accounts and reverse those actions, unlock accounts, and reset passwords.
- **Logs and CSV.** Search the read-only audit log, security log and picking scan history, and export their filtered rows or the Users list as CSV. The Logs page shows the latest daily retention run.

## Ten-stage roadmap

| Stage | Area | Status |
| --- | --- | --- |
| 0 | Setup | Passed |
| 1 | Shared login | Passed |
| 2 | User accounts | Passed |
| 3 | Logs and shared login updates | Passed |
| 4 | Products | Planned |
| 5 | Orders | Planned |
| 6 | Warehouse cases | Planned |
| 7 | Delivery cases | Planned |
| 8 | Refund problems | Planned |
| 9 | Dashboard and final checks | Planned |

## Set up and test locally

1. Download the [Admin branch ZIP](https://github.com/MarcoChong48/Logistics-Managment-Project/archive/refs/heads/feature/admin-system.zip) and extract it.
2. Give your AI coding assistant the **entire unzipped project folder** by opening it as the assistant's workspace. Select the folder containing `compose.yaml`, `backend/`, and `database/`. Do this before creating `.env`. Ask the assistant to read `ADMIN_README.md` and help you start and test the Admin system.
3. Start Docker Desktop in Linux containers mode. In the extracted folder, copy `.env.example` to `.env` and set `LMS_BOOTSTRAP_ADMIN_TEMPORARY_PASSWORD` to a password for this local test.
4. Run these commands from the folder containing `compose.yaml`. Wait for `db` to show as healthy and `backend` to show as running.

   ```powershell
   docker compose up --build -d
   docker compose ps
   ```

5. Open `http://127.0.0.1:8081/`. Sign in as `admin_local` with the temporary password you set and change it when prompted. Open **Test tools** for sample users and log fixtures. Use **Users** and **Logs** to test filters and CSV downloads.

For password requirements, database checks, and automated tests, see the [Admin setup guide](ADMIN_README.md).
