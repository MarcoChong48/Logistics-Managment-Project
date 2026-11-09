# Delivery source structure

The repository groups the Delivery feature under `features/delivery/`. It has its own React and Spring Boot build entry points. Within the module, code is grouped by the workflow it supports.

| Workflow | React | Spring Boot |
|---|---|---|
| Worker access | [access](../frontend/src/features/access/) | [access](../backend/src/main/java/hk/edu/hkmu/lms/delivery/access/) |
| Claim and collect at the warehouse | [pickup](../frontend/src/features/pickup/) | [pickup](../backend/src/main/java/hk/edu/hkmu/lms/delivery/pickup/) |
| Attempts, outcomes and proof | [deliveries](../frontend/src/features/deliveries/) | [attempts](../backend/src/main/java/hk/edu/hkmu/lms/delivery/attempts/) |
| Return handoff and retry approvals | [returns](../frontend/src/features/returns/) and [integrations](../frontend/src/features/integrations/) | [returns](../backend/src/main/java/hk/edu/hkmu/lms/delivery/returns/) |
| Overview, attempt history and notifications | [overview](../frontend/src/features/overview/), [history](../frontend/src/features/history/) and [notifications](../frontend/src/features/notifications/) | [workspace](../backend/src/main/java/hk/edu/hkmu/lms/delivery/workspace/) |

The React [app layer](../frontend/src/app/) composes screens and owns session and workspace state. Each feature uses its own components and [shared UI and formatting](../frontend/src/shared/). The [Vite boundary check](../frontend/import-boundaries.js) rejects peer feature imports, imports from features into the app layer, and imports from shared code into features.

Backend workflows call the shared [transaction entry point](../backend/src/main/java/hk/edu/hkmu/lms/delivery/operations/DeliveryTransactions.java) and [fulfilment state](../backend/src/main/java/hk/edu/hkmu/lms/delivery/fulfilment/FulfilmentState.java). Keeping the lock, request replay and stock changes inside the existing transaction boundary lets pickup and returns reuse the same rules. [DeliveryArchitectureTest](../backend/src/test/java/hk/edu/hkmu/lms/delivery/DeliveryArchitectureTest.java) checks the allowed Java package imports when Maven runs the tests.

For dependencies on other LMS roles, use the [integration guide](INTEGRATION.md) and the [API contract](../API_CONTRACT.md). Demo data, H2 and simulated external actors are Delivery Place holders.
