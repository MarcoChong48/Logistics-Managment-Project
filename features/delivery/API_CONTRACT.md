# Delivery app contract

The Java 21 / Spring Boot 4.1.2 service hosts the HTML, CSS and native JavaScript files on the same origin as the JSON API. The demo uses local H2. The Supabase profile uses the PostgreSQL migrations described in the [database guide](docs/DATABASE.md).

The user authorized a working local demo with simple English comments. External auth, Warehouse, Client, Admin, shared inventory/order database, notification transport, cloud storage and hosting must be named `Place holder`, with the future owner's work described. Real Delivery actions still commit through the Java backend, not front-end-only flags.

## Session and errors

- `GET /api/session` returns `{authenticated, user, csrfToken, csrfHeader, mode, sessionExpiresAt, serverTime}`. A session cookie identifies the worker. The `user` object includes `id`, `displayName` and `role`. For an anonymous session, `user`, `sessionExpiresAt` and `serverTime` are null. `mode` is `demo` or `integration`.
- `sessionExpiresAt` and `serverTime` use Unix epoch milliseconds. Qualifying authenticated API activity returns the same values as `X-Session-Expires-At` and `X-Server-Time` response headers. The browser uses their difference for the live inactivity countdown. It checks session status at zero. Sessions use a 15-minute inactivity limit by default.
- `POST /api/login` uses URL-encoded `username,password` plus CSRF header. Demo users `ken` / `mei` are separate Delivery workers, password `Delivery123!`. Success JSON, 401 on bad credentials.
- `POST /api/logout` uses CSRF header.
- `POST /api/change-password` requires an authenticated Delivery session, the current CSRF header and JSON `{currentPassword,newPassword,confirmPassword}`. The new password must differ from the current password, match the confirmation and contain 8 to 72 characters, including an uppercase A–Z, a lowercase a–z and a digit 0–9, within 72 UTF-8 bytes. On success the response is `{message:"Password changed. Sign in again."}` and the current session is invalidated. Other sessions for that account are denied on their next authenticated API request. A rejected change leaves the password in place.
- Error JSON: `{code,message}` with an appropriate 400/401/403/404/409 status. UI shows the safe message.
- Workflow mutation requests use a `requestId` UUID. Repeating the same actor, action, key and payload returns the earlier successful result without repeating effects. Key reuse with a different payload is rejected.
- Mutations return `{message}`; frontend reloads workspace after success. Never update a status locally before backend confirmation.

## Workspace

`GET /api/delivery/workspace` returns:

```json
{
  "user":{"id":"ken","displayName":"Ken Tanaka","role":"DELIVERY"},
  "mode":"demo",
  "stats":{"readyLists":1,"inTransit":0,"deliveredToday":0,"pendingReturns":0,"failedAttempts":0},
  "pickupLists":[{"id":"PL-1001","qrCode":"PL-1001","status":"OPEN","createdAt":"ISO timestamp","assignedTo":"ken","claimedBy":null,"claimedAt":null,"stale":false,"packages":[{"id":"PKG-1001","qrCode":"PKG-1001","orderReference":"LMS-1001","status":"READY_FOR_PICKUP","itemSummary":"2 Mugs, 1 Notebook","collectionOutcome":"PENDING","cannotCollectReason":null}]}],
  "tasks":[{"id":"PKG-1001","qrCode":"PKG-1001","orderReference":"LMS-1001","status":"SHIPPED","custody":"DELIVERY","recipientName":"Chan Mei","phone":"+852 6123 4567","address":"Hong Kong demo address","products":[{"sku":"MUG-01","name":"Travel mug","quantity":2}],"attemptCount":0,"activeAttemptId":null,"canStart":true,"canDeliver":false,"canFail":false,"canReturn":true}],
  "attempts":[{"id":"ATT-1","packageId":"PKG-1001","orderReference":"LMS-1001","workerName":"Ken Tanaka","startedAt":"ISO timestamp","endedAt":null,"outcome":"IN_PROGRESS","reason":null,"note":null,"proofs":[{"id":"PROOF-1","type":"SIGNATURE","createdAt":"ISO timestamp","url":"/api/proofs/PROOF-1"}]}],
  "returns":[{"id":"RET-1","packageId":"PKG-1001","orderReference":"LMS-1001","reason":"Vehicle problem","status":"AWAITING_WAREHOUSE","createdAt":"ISO timestamp","receivedAt":null,"condition":null,"awaitingInstruction":false,"followUpStatus":null}],
  "notifications":[{"id":"N-1","type":"DELIVERY_FAILED","message":"Local event saved; Place holder notification transport.","createdAt":"ISO timestamp","read":false}],
  "integrations":[{"id":"warehouse","name":"Warehouse","status":"PLACE_HOLDER","detail":"Place holder: Warehouse owner must provide packed lists and actual return receipts."}],
  "decisions":[{"id":"U-10","plan":"B","choice":"Stale claim review queue","reason":"Flag inactivity without moving custody.","status":"LOCAL_IMPLEMENTATION"}],
  "settings":{"photoLimitBytes":5242880,"staleClaimMinutes":30,"reviewFailureCount":2,"reminderDays":3,"finalReviewDays":7},
  "demoPackages":[{"id":"PKG-1001","orderReference":"LMS-1001","status":"READY_FOR_PICKUP","custody":"WAREHOUSE","returnStatus":null,"instruction":null,"approved":false,"pendingCancellation":false}]
}
```

`assignedTo` is the saved worker ID or null for older records. `claimedBy` identifies the worker who confirmed the list at the warehouse. An open list is visible to its assigned worker. `stats.readyLists` counts the current worker's assigned open lists awaiting confirmation. `tasks` only includes this worker's collected/active/return-pending packages. Contact/address is returned only while the worker actually handles an active delivery; Warehouse receipt and completed delivery revoke access. Pickup list rows never expose contact/address. History must not disclose other workers' private proof. Dashboard counts use authorized data. A `demoPackages` entry contains no recipient contact.

Each return record includes the Warehouse receipt `status` and any receipt time and condition. `followUpStatus` is a read-only Delivery view of the latest received handoff. It is `AWAITING_CLIENT_INSTRUCTION`, `AWAITING_ADMIN_APPROVAL`, `READY_FOR_REDELIVERY`, `ON_HOLD`, `CANCELLED` or null. Older handoffs and pending Warehouse receipts keep their own receipt status and have a null projection. The client can use `awaitingInstruction` for older backend responses that omit the projection. Approval and pickup remain separate authorized operations.

## Delivery actions

- `POST /api/pickup-lists/claim` `{qrCode,requestId}`: confirm pickup of the list assigned to the authenticated worker. A different worker receives 403. Confirmation updates the list and preserves stock and custody until each package is collected.
- `POST /api/pickup-lists/{listId}/collect` `{packageQrCode,requestId}`: require the worker who confirmed the list, an active list entry, packed or approved eligibility and a pending collection in this cycle. Atomically update order/custody/list item, reduce physical and reserved stock, save a custody event/movement.
- `POST /api/pickup-lists/{listId}/cannot-collect` `{packageId,reason,note,requestId}`: before pickup only; reason `VEHICLE_FULL`, `PACKAGING_DAMAGED`, `GOODS_DAMAGED`, `OTHER`; Other needs note. Damage creates a Warehouse hold, vehicle-full can be released on close. No customer attempt is invented.
- `POST /api/pickup-lists/{listId}/close` `{requestId}`: preserve collected/uncollected history; release eligible uncollected packages for new lists.
- `POST /api/packages/{packageId}/start` `{requestId}`: only actual holder with SHIPPED package can enter OUT_FOR_DELIVERY; create a new attempt tied to the current pickup and frozen delivery details.
- `POST /api/packages/{packageId}/proofs` multipart `type=PHOTO|SIGNATURE`, `file`: only actual holder during its active attempt; validate decoded JPEG/PNG, type, size and actual content. Save privately; return `{id}`. Files must never be public static resources.
- `POST /api/packages/{packageId}/outcome` `{outcome:"DELIVERED"|"FAILED",reason,note,proofIds:[],requestId}`. Success needs at least one existing valid proof bound to this attempt. Failure reason is one of `CLIENT_NOT_HOME`, `WRONG_ADDRESS`, `CLIENT_REFUSED`, `UNABLE_TO_CONTACT`, `OTHER`; Other needs note. Separate attempts are appended. Failed outcome keeps actual holder until Warehouse receipt. Stock does not change on failure.
- `POST /api/packages/{packageId}/return` `{reason,requestId}`: holder reports return handoff pending Warehouse; supports SHIPPED before any attempt, or DELIVERY_FAILED. Does not restore stock or fake a failed attempt.
- `GET /api/proofs/{proofId}`: enforce active assigned Delivery access; no access after completion/return, unless a future authorized Client owner adapter grants own-order access. Return private no-store image content.

## Test tools (demo profile only)

**Test tools** exposes these labelled `Place holder` controls in the demo profile. The routes are restricted to demo mode and record simulated Warehouse, Client or Admin actors. Real role integrations use the contracts in the [integration guide](docs/INTEGRATION.md).

- `POST /api/demo/packages/{packageId}/confirm-return` `{condition:"INTACT"|"DAMAGED",requestId}`. Only after a pending return. Warehouse simulated receipt restores actual physical quantities; intact allocated goods restore reserved quantities as well. Damaged goods remain unavailable/held. No automatic redelivery approval. For a pre-attempt intact return, re-enter READY_FOR_PICKUP or the already-approved READY_FOR_REDELIVERY, preserving cycle eligibility.
- `POST /api/demo/packages/{packageId}/client-instruction` `{instruction:"REDELIVER"|"CANCEL",requestId}`. Only after receipt; version and bind to the latest return/address. A cancel instruction blocks redelivery.
- `POST /api/demo/packages/{packageId}/admin-decision` `{decision:"APPROVE_REDELIVERY"|"APPROVE_CANCELLATION",requestId}`. Check latest receipt/instruction, damaged hold and no pending cancellation; freeze address version. Redelivery creates a new eligible pickup list/cycle. Cancellation final; refund integration is Place holder, not refunded.
- `POST /api/demo/pickup-lists/{listId}/release` `{requestId}`: Warehouse-presence confirmation + Admin simulated release of only uncollected entries; reject later scans on old list. Never transfer collected custody.

## Implementation boundaries

- Use real durable local delivery data. On startup seed only when empty. Repeated startup must not reset user work.
- The local demo uses H2. The Supabase profile uses the team v0.4 PostgreSQL migrations and Delivery extensions. Keep event and cycle IDs stable when the Database owner connects shared workflows.
- A consistent local transaction handles pickup stock/order/custody/list changes. A shared row lock or equivalent is acceptable for a small module. Async notifications must not replace atomic stock updates.
- Private local proof storage is the local fallback; U-09 B object storage connector and cloud credentials are a marked `Place holder`. Provide cleanup/orphan monitoring and minimal retained summary after one-year detailed proof/attempt expiry. Do not claim cloud configuration is complete.
- U-12 B adds review/reminder queues (two failures, three/seven days configurable), not automatic cancellation, charges or redelivery.
- U-01 B cloud deployment/rehearsal/managed backups remain configuration Place holders. Supply health, repeatable build and manual backup/restore guidance. Do not deploy a separate unrelated website.
- All code comments are simple English. Put `Decision U-xx, B:` near relevant code and `Place holder:` near replacement points. Explain the future owner/data needed.
