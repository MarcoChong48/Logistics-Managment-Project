# Delivery integration guide

The public module contract is [API_CONTRACT.md](../API_CONTRACT.md). This guide explains replacement boundaries. A **Place holder** uses local demo data or a simulated external actor; it is not a completed remote integration.

## Runtime and identity

The packaged React build is served by Spring Boot on port 8080. During development React uses 5173 and forwards `/api` to 8080. Keep browser API calls on the frontend's origin. Session cookies identify the worker; accepting `workerId` from a request as authentication is forbidden. Mutations require the current CSRF token/header supplied by `/api/session`.

The authentication owner must replace local users with shared identities, roles, current account authorization, session expiry, password-change revocation and recovery. Demo `ken` and `mei` are both Delivery Workers. Demo sandbox access is permitted only in the demo profile and must be disabled in an integrated deployment.

## Owner handoffs

| Owner | Must provide | Delivery must preserve |
|---|---|---|
| Warehouse | Packed eligible package/list IDs and QR values; exact item contents; real return receipt actor/time/quantities/condition; holds and supervised uncollected-package release. | List claim versus physical pickup, original packing history, actual receipt and each custody cycle. |
| Client | Own-order authorization; current delivery-detail version; authenticated post-return instruction, supersession/cancellation status and request identity. | The exact request/version approved for the current return, not an arbitrary latest address. |
| Admin | Current-role authorization; redelivery/cancellation approval tied to a valid receipt/instruction; reassignment actor/time/reason and supervised release. | Decision provenance and final cancellation. Reassignment alone does not prove possession. |
| Database/shared services | Final schema, migrations, identifiers, locking/transaction rules, inventory allocation/movement model, outbox or equivalent durable event delivery. | Order/custody/list/stock agreement and duplicate-effect protection across modules. |
| Payment/refund | Verified accepted payment eligibility; final cancellation reconciliation and actual refund progress. | Separate financial/logistics states; local events are not proof of a charge/refund. |
| Notification | Durable delivery of important Client events and read-state mapping. | A local event must survive delivery retry without repeating business effects. |
| Storage/hosting | Protected durable proof storage, authorized retrieval, backup lifecycle, deletion jobs, HTTPS and operational monitoring. | Proof privacy and expiry, database/file consistency and safe failure recovery. |

## Provisional database

File-backed H2 is the Database **Place holder**. It allows a local demonstration to persist work between restarts. It neither adopts Database Draft v0.4 nor selects the final database product. Startup seeds only an empty store; a normal restart must not overwrite accepted work.

`app.data-dir` defaults to `./data` and can be set with `LMS_DATA_DIR`. The H2 path is `${app.data-dir}/database`; the local proof directory is `${app.data-dir}/proofs`. The default listener is `127.0.0.1:8080`; `LMS_BIND_ADDRESS` and `PORT` configure the application properties, while the Windows launcher explicitly binds localhost and accepts its own `-Port` argument. Do not expose demo sandbox controls by changing the listener and assume production authorization is complete.

Contract settings use `app.photo-limit-bytes=5242880`, `app.stale-claim-minutes=30`, `app.review-failure-count=2`, `app.reminder-days=3`, `app.final-review-days=7` and an hourly `app.maintenance-delay-ms=3600000`. An inactivity session expires after 15 minutes. These are local configuration choices, not measured performance or a global team decision.

Before replacement, map local package/order/list/cycle/attempt/proof/event IDs to shared IDs, migrate durable records and preserve authorship. Give every changed table an owner. Do not connect two independently maintained stock totals and treat both as authoritative. The Database and shared-service owners must decide where the single inventory transaction runs.

## State and cycle rules

Normal Delivery progression is `READY_FOR_PICKUP → SHIPPED → OUT_FOR_DELIVERY → DELIVERED`.

Failure creates a separate attempt and leaves `DELIVERY_FAILED` with the actual Delivery holder. A return request records pending Warehouse handoff; it restores no warehouse quantity. Warehouse confirmation records receipt/condition. Redelivery requires a current Client instruction and Admin approval, then `READY_FOR_REDELIVERY → new individual pickup → SHIPPED → OUT_FOR_DELIVERY`.

A pre-attempt return is a custody event, not a failed customer visit. The local contract requeues an intact receipt to `READY_FOR_PICKUP`, or `READY_FOR_REDELIVERY` for a still-eligible approved retry. Preserve the prior pickup. Damaged/missing goods stay held for Warehouse/Admin exception handling.

Use a stable pickup/return cycle or event identity. Duplicate rejection applies within that cycle; using `packageId` as a once-for-life pickup key would incorrectly block legitimate redelivery. Attempts bind to the actual pickup, approved instruction and frozen delivery details. Pending cancellation blocks approval. Cancelled packages cannot return to fulfilment.

## Atomic effects and external messages

Claiming a list assigns one collector without changing custody or stock. An accepted individual pickup consistently commits list outcome, order status, holder/custody event, inventory movement and physical/reserved deductions. An intact allocated Warehouse receipt restores actual physical and allocated reserved quantities together; failure alone does neither.

Mutations use a UUID `requestId`. Repeating the same actor/action/key/payload returns the previous success. Reusing the key with different data is rejected. Apply state/owner checks inside the transaction. Two workers must not both claim a list, and a stale scan must not collect a released entry.

External notification, payment and cloud calls are not part of a local database transaction. Persist the event/result needed to recover, then dispatch/reconcile with duplicate protection. A timeout is an unknown result until checked, not permission to repeat a charge/refund.

## Proof and private data

Only the actual holder during an active attempt may capture/upload proof. Validate decoded JPEG/PNG content, size and type. Bind the proof to that attempt; an arbitrary proof ID or another attempt's image cannot complete delivery. At least one valid signature or photo is required for success.

Local proof files live outside public static assets and are retrieved only through an authorized backend operation with private/no-store responses. Contact details are absent from pickup-list rows and revoked after delivery completion or Warehouse receipt. Do not leak them through audit copies or other workers' history.

Cloud object storage is a **Place holder**. Its owner must provide private upload/read/delete operations, protected credentials, orphan/deletion monitoring and backup expiry. Replacing the storage backend must preserve proof IDs and permissions. One-year detailed attempt/proof cleanup and permitted minimal summaries must agree with the three-year audit policy; no deleted image may remain available through a public link.

## Backup and restore contract

For a manual local backup, stop the app and copy the complete local database plus private proof directory and non-secret configuration as one checkpoint. Record the checkpoint time. Restore both together into an isolated copy, start it, then verify selected orders, custody, reservations, attempts and proof references before using it. Work after the checkpoint is not present in that backup.

Copy the complete `data` directory by default, or the directory chosen with `LMS_DATA_DIR`. Maintenance endpoints/commands, when present, must be confirmed against the delivered build. Do not copy an open database file and claim a verified consistent backup. Managed scheduled backups, hosted rehearsal and recovery alerts remain hosting **Place holder** work until configured and tested.
