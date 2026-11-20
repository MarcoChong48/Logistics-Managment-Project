# Admin system setup

This branch builds on the team's PostgreSQL baseline. It includes shared login and role checks, Admin user management, read-only logs, CSV exports, retention, and local test tools through Stage 3.

## Start the local stack

1. Open Docker Desktop in Linux containers mode and open a terminal in this checkout. If another LMS stack is using port 8081, run `docker compose down` from that stack's own checkout to release the port and keep its data.
2. Copy `.env.example` to `.env`. Open `.env` locally and set a temporary bootstrap password. Use 8–72 characters and at most 72 bytes in UTF-8. Use a password dedicated to this local test stack. Git ignores `.env`.
3. Build and start the stack.

   ```powershell
   docker compose up --build -d
   docker compose ps
   ```

4. When the database is healthy, open the [sign-in page](http://127.0.0.1:8081/). Use the bootstrap username and temporary password you set in `.env`. Change the temporary password when prompted. The temporary password expires after 24 hours. Once a usable Admin account exists, the saved password is used on subsequent starts.

The Compose stack binds the app to `127.0.0.1:8081`. Use synthetic data with the `admintest` profile. Open **Users** to manage accounts and **Logs** to search the audit log, security log and picking scans. Use **Test tools** to create fixtures. The [health endpoint](http://127.0.0.1:8081/api/shared/health) reports the database connection and table count without requiring sign-in.

The first log retention run is scheduled for five minutes after startup. In **Test tools → Stage 3**, you can create tagged synthetic log rows and run cleanup immediately. **Logs** shows the latest run and deletion counts. Use the [team integration guide](TEAM_GUIDE.md) to build a role behind shared sign-in.

## Verify the setup

```powershell
docker build --no-cache --target rule-check --progress plain ./backend
docker compose exec db psql -U lms -d lms -c "\dt"
```

On a fresh volume, Compose runs `01_create_tables.sql` and `03_admin_changes.sql` in order. The result is 27 tables. For the migration details, see the [database README](database/README.md). Run `docker compose down` from this checkout to stop the stack and keep its data.
