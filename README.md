# Logistics Management System (LMS)

A web-based logistics system with four roles: **Client, Warehouse, Delivery, and Admin**.

## Tech Stack

- Java 21 · Spring Boot 4.1.2 · Maven
- PostgreSQL 17 · Docker Compose

## Setup Completed

- Spring Boot backend configured
- Docker environment for backend and PostgreSQL
- Backend connected to PostgreSQL
- Initial database schema with **25 tables**

> The database schema is a draft for team review. Application features are still in development.

## Getting Started

1. Install and open **Docker Desktop** (Linux containers).
2. Clone the database setup branch:

   ```powershell
   git clone -b feature/database-setup https://github.com/MarcoChong48/Logistics-Managment-Project.git
   cd Logistics-Managment-Project
   ```

3. Start the project:

   ```powershell
   docker compose up --build
   ```

4. Open another terminal to verify:

   ```powershell
   docker compose ps
   docker compose exec db psql -U lms -d lms -c "\dt"
   ```

   PostgreSQL should be healthy, and `\dt` should list **25 tables**.

To stop: press **Ctrl+C** or run `docker compose down`.

## Notes

- This setup is currently on `feature/database-setup`, not yet merged into `main`. After merging, clone without `-b feature/database-setup`.

- Each teammate has their **own local database** (not synced with Supabase).
- Tables initialize automatically only on the **first run with a new database volume**.
- Do not run `docker compose down -v` unless you want to erase local database data.
- SQL files and database details: [`database/`](database/). No Flyway is used.
