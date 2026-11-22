# Logistics Management System · Admin

For the combined Client, Warehouse, Delivery, and Admin stack, see the [integration handoff](docs/integration/README.md).

`feature/admin-system` contains the Admin module and shared sign-in for the team's Logistics Management System. It builds on `feature/database-setup` using Java 21, Spring Boot 4.1.2, PostgreSQL 17, and Docker Compose.

**Stage 9 passed on 22 November 2026.** All ten Admin implementation stages are complete. The final stage passed all 13 hand tests. The combined suite passed 175 rule checks and 199 flow checks with zero failures and six documented skips.

Use these references to build another role in the shared application. [Team integration guide](TEAM_GUIDE.md) · [Placeholder register](PLACEHOLDER_LIST.md) · [Final hand-in checklist](TEMPORARY_CODE_LIST.md)

## What's included

- **Setup.** Local database with 27 tables, a health endpoint, the Admin page frame, and test tools for creating sample accounts and work assignments.
- **Shared login.** Sign-in and sign-out, an idle timeout, temporary login lockouts, and separate password-change flows for temporary and permanent credentials. Each role's pages and APIs are protected by its role check.
- **User accounts.** Search, filter, and create accounts for all four LMS roles. Admins can edit Delivery Worker profiles, preview work affected by account changes, change eligible staff roles, ban or deactivate accounts and reverse those actions, unlock accounts, and reset passwords.
- **Logs and CSV.** Search the read-only audit log, security log and picking scan history, and export their filtered rows or the Users list as CSV. The Logs page shows the latest daily retention run.
- **Products.** Search and edit the catalogue, activate or deactivate products, see stock and low-stock flags, print SKU QR labels, and import or export products by CSV.
- **Super admin.** The bootstrap Admin can create and manage other Admin accounts and recover access with the local reset setting. Other Admins manage non-Admin accounts.
- **Orders.** Search and export orders, open order details, and review status history, payment and refund state, and operational events.
- **Warehouse cases.** Review stock shortages, choose whether to wait or cancel, export case results, and reassign picking work. Paid cancellations record a refund request through a local payment stand-in.
- **Delivery cases.** Review failed deliveries and returned packages, record or decide client requests, send reminders, and reassign held packages.
- **Refunds.** Review unresolved refunds, check provider status, retry confirmed failures, and export matching rows. Test tools provide synthetic provider answers.
- **Dashboard and final checks.** Open filtered lists from order, stock, and case counts. The three work-list counters update after successful decisions. Test tools include a privacy check and local test-data cleanup.

## Ten-stage roadmap

| Stage | Area | Status |
| --- | --- | --- |
| 0 | Setup | Passed |
| 1 | Shared login | Passed |
| 2 | User accounts | Passed |
| 3 | Logs and shared login updates | Passed |
| 4 | Products | Passed |
| 5 | Orders | Passed |
| 6 | Warehouse cases | Passed |
| 7 | Delivery cases | Passed |
| 8 | Refund problems | Passed |
| 9 | Dashboard and final checks | Passed |

## Set up and test locally

1. Download the [Admin branch ZIP](https://github.com/MarcoChong48/Logistics-Managment-Project/archive/refs/heads/feature/admin-system.zip) and extract it.
2. Give your AI coding assistant the **entire unzipped project folder** by opening it as the assistant's workspace. Select the folder containing `compose.yaml`, `backend/`, and `database/`. Do this before creating `.env`. Ask the assistant to read `ADMIN_README.md` and help you start and test the Admin system.
3. Start Docker Desktop in Linux containers mode. In the extracted folder, copy `.env.example` to `.env` and set `LMS_BOOTSTRAP_ADMIN_TEMPORARY_PASSWORD` to a password for this local test.
4. Run these commands from the folder containing `compose.yaml`. Wait for `db` to show as healthy and `backend` to show as running.

   ```powershell
   docker compose up --build -d
   docker compose ps
   ```

5. Open `http://127.0.0.1:8081/`. Sign in as `admin_local` with the temporary password you set, change it when prompted, and sign in again with the new password. Use **Test tools** to create sample work, then use the Dashboard links to open the matching lists.

For password requirements, database checks, and automated tests, see the [Admin setup guide](ADMIN_README.md).

## Client and Delivery

- [Client portal guide](CLIENT_README.md)
- [Delivery v4 setup](DELIVERY_V4.md)
