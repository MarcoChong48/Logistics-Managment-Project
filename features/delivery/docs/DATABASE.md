# Delivery database

| Look up | File |
|---|---|
| Exact SQL used by the app | [schema.sql](../backend/src/main/resources/schema.sql) |
| H2 connection and schema initialization | [application.properties](../backend/src/main/resources/application.properties) |
| Demo mode | [application-demo.properties](../backend/src/main/resources/application-demo.properties) |
| Demo accounts, orders and pickup lists | [DemoSeed.java](../backend/src/main/java/hk/edu/hkmu/lms/delivery/demo/DemoSeed.java) |
| Transactions and request replay | [DeliveryTransactions.java](../backend/src/main/java/hk/edu/hkmu/lms/delivery/operations/DeliveryTransactions.java) |
| Eligibility, stock and custody events | [FulfilmentState.java](../backend/src/main/java/hk/edu/hkmu/lms/delivery/fulfilment/FulfilmentState.java) |
| Shared database handoff | [Integration guide](INTEGRATION.md), [v0.4 draft and review](database/LMS_Database_Draft_v0_4_Review.md) |

The build copies the source SQL into the JAR. Spring Boot runs it on startup to create the 25 H2 tables and initialize the mutation lock. In demo mode, Java adds sample records to an empty store. Start the app to create the local store in `data/` or in the folder set through `LMS_DATA_DIR`. Proof files are stored separately from their database metadata.

The SQL is a **Place holder** for the shared LMS schema. Use the integration guide to map pickup cycles, custody, stock and return receipts to the Database owner's design. The v0.4 draft and ERDs are design references.
