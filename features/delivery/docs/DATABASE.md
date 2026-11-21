# Delivery database

The `supabase` profile uses PostgreSQL. The `demo` profile keeps the existing local H2 store for offline demos. Spring Boot owns the JDBC connection in both profiles. Browser requests go to the Delivery API.

| Look up | File |
|---|---|
| 25 core tables from Database Draft v0.4 | [Core migration](../supabase/migrations/20261110000100_lms_database_draft_v0_4.sql) |
| Delivery workflow extensions and SQL mapping | [Delivery migration](../supabase/migrations/20261110000200_delivery_runtime_compatibility.sql) |
| Pickup list assignment for Delivery v3.2 | [Assignment migration](../supabase/migrations/20261120000300_delivery_pickup_assignment.sql) |
| Supabase connection settings | [application-supabase.properties](../backend/src/main/resources/application-supabase.properties) |
| Local H2 schema and demo fixtures | [schema.sql](../backend/src/main/resources/schema.sql) and [DemoSeed.java](../backend/src/main/java/hk/edu/hkmu/lms/delivery/demo/DemoSeed.java) |
| Design input and open issues | [Database Draft v0.4](database/v0.4/LMS_Database_Draft_v0_4.md) and [review](database/LMS_Database_Draft_v0_4_Review.md) |

## Schema boundaries

`lms_delivery` holds the core draft tables. SQL names use lowercase snake case. `lms_user` and `customer_order` avoid keyword conflicts. Draft tables retain separate Product and Inventory records, order item prices, payment/refund records and versioned delivery details.

`lms_delivery_runtime` supplies the Delivery query names and extra records for pickup cycles, Warehouse receipts and quantities, request replay and retention. See the second migration for each extension and its link to a core record. The assignment migration adds a worker reference while preserving existing list confirmations. The demo profile adds the same field to existing H2 files. The migrations also establish a fresh PostgreSQL schema.

Warehouse, Client, Admin and payment handoffs stay marked **Place holder** in the demo. The team needs a common contract for which service writes orders, stock and custody, including its lock and retry rules.

## Local PostgreSQL in Docker

The [Delivery Compose file](../compose.yaml) starts an isolated PostgreSQL 17 database and the Delivery backend. On the first start of an empty `postgres_data` volume, PostgreSQL runs the core migration followed by the Delivery runtime migration. The `migrate` service applies the assignment migration on new and existing volumes. The backend waits until this succeeds, then uses the `supabase` profile with local settings and seeds fictional data if the store is empty.

The database volume holds the records and the `delivery_data` volume holds private proof files. `docker compose down` keeps both. To upgrade a v3.1 Compose installation, use the same project name and run `docker compose up --build -d` from v3.2. [Local startup instructions](../README.md#run-the-local-demo-with-docker-compose)

## Start against Supabase

1. Ask the Database owner to review and apply the pending migrations in filename order. The v3.2 assignment migration must be applied before starting the updated backend. Use the [Supabase migration workflow](https://supabase.com/docs/guides/local-development/database-migrations) or a PostgreSQL client. The application does not apply migrations at startup.
2. Give the app a dedicated PostgreSQL login with usage and the required table and view privileges in the Delivery schemas. For a dedicated development database, the grants below match the locally tested role. Keep both schemas out of the Supabase Data API exposure list.
3. Copy the Direct or Session pooler values from the Supabase Connect dialog. Use Session pooler for an IPv4-only client when Direct is unavailable. Set the JDBC URL, user and password on the server as environment variables. Configure the Supabase CA using the pgJDBC `sslrootcert` URL parameter or the driver's local certificate path. The profile defaults to `sslmode=verify-full`. [Supabase connection guide](https://supabase.com/docs/guides/database/connecting-to-postgres)
4. After building, start the JAR with the profile below. Leave demo seeding and retention jobs disabled on the shared database. Use a separate proof directory for each environment.

| Environment variable | Value |
|---|---|
| `SUPABASE_DB_URL` | JDBC URL such as `jdbc:postgresql://<host>:5432/postgres` |
| `SUPABASE_DB_USER` | Database login for the chosen connection mode |
| `SUPABASE_DB_PASSWORD` | Database login password |
| `LMS_DATA_DIR` | Directory for private proof files |

```sh
java -jar backend/target/delivery-app.jar --spring.profiles.active=supabase --server.address=127.0.0.1
```

On Windows, the launcher script also accepts `-Profile supabase`. The temporary demo usernames and passwords are available only when demo data is explicitly seeded. The team must provision real users through its agreed account workflow. Delivery uses Spring Security for username login and sessions. The backend looks up `lms_user.username` and uses the stable `user_id` for permissions and audit records.

## Development role grants

Run these grants as the migration owner after creating the dedicated PostgreSQL login. Replace `lms_delivery_app` with the chosen role. These grants cover the two isolated schemas and are intended for the development environment.

```sql
GRANT USAGE ON SCHEMA lms_delivery, lms_delivery_runtime TO lms_delivery_app;
GRANT SELECT, INSERT, UPDATE, DELETE
    ON ALL TABLES IN SCHEMA lms_delivery, lms_delivery_runtime TO lms_delivery_app;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA lms_delivery_runtime TO lms_delivery_app;
```

Review narrower role grants with the Database owner before integrating shared role data.

## Proof files

PostgreSQL stores the proof reference and metadata. The Java app keeps the file in the private `proofs` subdirectory under `LMS_DATA_DIR` and checks access before serving it. For PostgreSQL recovery, plan the database and proof-directory backups as one checkpoint.
