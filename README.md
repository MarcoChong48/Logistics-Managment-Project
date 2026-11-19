# Logistics Management System · Admin

`feature/admin-system` contains the Admin module and shared sign-in for the team's Logistics Management System. It builds on `feature/database-setup` using Java 21, Spring Boot 4.1.2, PostgreSQL 17, and Docker Compose.

**Stage 2 (User accounts) passed on 19 November 2026.** Three of ten stages are complete. Stage 2 passed all 27 acceptance cases and three regression checks. The latest automated run passed 81 checks. Stage 3 (Logs) is next.

## What's included

- **Setup.** Local database with 27 tables, a health endpoint, the Admin page frame, and test tools for creating sample accounts and work assignments.
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

## Run locally

Use Docker Desktop in Linux containers mode. Copy `.env.example` to `.env`, set a local bootstrap password, and run `docker compose up --build -d` from the Admin branch checkout. The app opens at `http://127.0.0.1:8081/`. Use sample data with the local test tools. See the [Admin setup guide](ADMIN_README.md) for first sign-in, database checks, and automated tests.
