# Delivery integration guide

Use the [API contract](../API_CONTRACT.md) for fields and routes. **Decisions & integrations** gives Delivery users a summary of local decisions and handoffs. This guide gives developers the details. A **Place holder** supplies demo data or simulates an external actor during local testing.

## Runtime and identity

Spring Boot serves the HTML, CSS, JavaScript and JSON API on port 8080 by default. Keep browser API calls on the frontend's origin. Session cookies identify the worker; accepting `workerId` from a request as authentication is forbidden. Mutations require the current CSRF token/header supplied by `/api/session`.

Spring Security manages Delivery login and account sessions. The session API and authenticated response headers expose the server time and idle deadline in epoch milliseconds. The workspace updates the visible countdown locally and checks the session with the server when the countdown reaches zero. Keep the server as the authority for session expiry.

An authenticated worker can change their own password using the current password and CSRF token. The request expires the current session after a successful change. Other sessions for the same account are denied on their next API request. The authentication owner supplies shared account provisioning, role lifecycle, and account recovery. Preserve the password-change and session revocation behavior when connecting shared identity.

Demo `ken` and `mei` are both Delivery Workers. **Test tools** and its routes are available only with demo mode enabled. Warehouse receipt, Client request and Admin approval in that page use simulated identities. The Delivery redesign introduces no Admin UI or API changes. Connect each role at the owner handoffs below.

## Owner handoffs

| Owner | Must provide | Delivery must preserve |
|---|---|---|
| Warehouse | Packed packages grouped into ready lists, package and list IDs and QR codes, item quantities, return receipts, holds and supervised release of uncollected packages. | Assignment, warehouse confirmation, physical pickup, packing history, actual receipt and each custody cycle. |
| Client | Own-order authorization; current delivery-detail version; authenticated post-return instruction, supersession/cancellation status and request identity. | The exact request/version approved for the current return, not an arbitrary latest address. |
| Admin | Current-role authorization; redelivery/cancellation approval tied to a valid receipt/instruction; reassignment actor/time/reason and supervised release. | Decision provenance and final cancellation. Reassignment alone does not prove possession. |
| Database/shared services | Final schema, migrations, identifiers, locking/transaction rules, inventory allocation/movement model, outbox or equivalent durable event delivery. | Order/custody/list/stock agreement and duplicate-effect protection across modules. |
| Payment/refund | Verified accepted payment eligibility; final cancellation reconciliation and actual refund progress. | Separate financial/logistics states; local events are not proof of a charge/refund. |
| Notification | Durable delivery of important Client events and read-state mapping. | A local event must survive delivery retry without repeating business effects. |
| Storage/hosting | Protected durable proof storage, authorized retrieval, backup lifecycle, deletion jobs, HTTPS and operational monitoring. | Proof privacy and expiry, database/file consistency and safe failure recovery. |

## Database profiles

The `demo` profile uses local H2. The `supabase` profile uses PostgreSQL with the [v0.4 migrations and Delivery extensions](DATABASE.md). It disables demo seeding and automatic retention by default. Shared transaction coordination with the Warehouse, Client and Admin modules remains a **Place holder**.

The [local Docker Compose stack](../compose.yaml) enables demo seeding on its private PostgreSQL 17 service. PostgreSQL records and proof files use separate named volumes. The [demo guide](DEMO.md) explains startup.

The local proof directory is `${app.data-dir}/proofs`. Set `LMS_DATA_DIR` to change it. The default listener is `127.0.0.1:8080`. Use `LMS_BIND_ADDRESS` and `PORT` to configure the Java service, or use the Windows launcher with a local port.

Contract settings use `app.photo-limit-bytes=5242880`, `app.stale-claim-minutes=30`, `app.review-failure-count=2`, `app.reminder-days=3` and `app.final-review-days=7`. Delivery demo sessions expire after 15 minutes of inactivity.

## State and cycle rules

Normal Delivery progression is `READY_FOR_PICKUP → SHIPPED → OUT_FOR_DELIVERY → DELIVERED`.

Failure creates a separate attempt and leaves `DELIVERY_FAILED` with the actual Delivery holder. A return request records pending Warehouse handoff; it restores no warehouse quantity. Warehouse confirmation records receipt/condition. Redelivery requires a current Client instruction and Admin approval, then `READY_FOR_REDELIVERY → new individual pickup → SHIPPED → OUT_FOR_DELIVERY`.

The reporting worker sees receipt status and `followUpStatus` on **Return handoffs**. The projection reads current backend records and applies only when the package's latest handoff has been received. After a failed attempt and receipt, the absence of a current valid instruction gives `AWAITING_CLIENT_INSTRUCTION`. A pending Client instruction gives `AWAITING_ADMIN_APPROVAL`. Saved redelivery eligibility, holds and cancellation surface as `READY_FOR_REDELIVERY`, `ON_HOLD` and `CANCELLED`. A hold takes precedence over pending instructions. Receipt condition and time come from the saved receipt. Keep earlier receipts as history when the package enters a new handoff.

A pre-attempt return is a custody event, not a failed customer visit. The local contract requeues an intact receipt to `READY_FOR_PICKUP`, or `READY_FOR_REDELIVERY` for a still-eligible approved retry. Preserve the prior pickup. Damaged/missing goods stay held for Warehouse/Admin exception handling.

Use a stable pickup/return cycle or event identity. Duplicate rejection applies within that cycle; using `packageId` as a once-for-life pickup key would incorrectly block legitimate redelivery. Attempts bind to the actual pickup, approved instruction and frozen delivery details. Pending cancellation blocks approval. Cancelled packages cannot return to fulfilment.

## Atomic effects and external messages

Warehouse prepares and groups packages into ready lists. Delivery assigns each open list to the active Delivery worker with the fewest assigned or confirmed lists in progress. Ties use the worker account ID. Delivery saves the assignment when it prepares a replacement list or processes an unassigned open list. The assigned worker confirms the list at the warehouse, and the worker’s scan of each package records physical collection. An accepted individual pickup consistently commits list outcome, order status, holder/custody event, inventory movement and physical/reserved deductions. An intact allocated Warehouse receipt restores actual physical and allocated reserved quantities together; failure alone does neither.

Mutations use a UUID `requestId`. Repeating the same actor/action/key/payload returns the previous success. Reusing the key with different data is rejected. Apply state/owner checks inside the transaction. Only the worker assigned to a list may confirm it, and a stale scan must not collect a released entry.

External notification, payment and cloud calls are not part of a local database transaction. Persist the event/result needed to recover, then dispatch/reconcile with duplicate protection. A timeout is an unknown result until checked, not permission to repeat a charge/refund.

## Proof and private data

Only the actual holder during an active attempt may capture/upload proof. Validate decoded JPEG/PNG content, size and type. Bind the proof to that attempt; an arbitrary proof ID or another attempt's image cannot complete delivery. At least one valid signature or photo is required for success.

Local proof files live outside public static assets and are retrieved only through an authorized backend operation with private/no-store responses. Contact details are absent from pickup-list rows and revoked after delivery completion or Warehouse receipt. Do not leak them through audit copies or other workers' history.

Cloud object storage is a **Place holder**. Its owner must provide private upload/read/delete operations, protected credentials, orphan/deletion monitoring and backup expiry. Replacing the storage backend must preserve proof IDs and permissions. One-year detailed attempt/proof cleanup and permitted minimal summaries must agree with the three-year audit policy; no deleted image may remain available through a public link.

## Backup and restore contract

For a manual H2 backup, stop the app and copy the complete local database, private proof directory and non-secret configuration as one checkpoint. For Supabase, coordinate a database backup with the Database owner and take a matching proof-directory backup. Record the checkpoint time. Restore both together into an isolated copy, start it, then verify selected orders, custody, reservations, attempts and proof references before using it. Work after the checkpoint is not present in that backup.

Copy the complete `data` directory by default, or the directory chosen with `LMS_DATA_DIR`. Maintenance endpoints/commands, when present, must be confirmed against the delivered build. Do not copy an open database file and claim a verified consistent backup. Managed scheduled backups, hosted rehearsal and recovery alerts remain hosting **Place holder** work until configured and tested.
