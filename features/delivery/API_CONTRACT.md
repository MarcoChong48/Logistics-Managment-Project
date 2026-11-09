# Delivery app contract

Implementation choice U-05 B: React browser app + Spring Boot JSON API. Java 21, Spring Boot 3.5.16, Maven. Local H2 file storage is a clearly marked Place holder for the Database owner's shared relational schema. Final package serves the built React files from Spring Boot.

The user authorized a working local demo with simple English comments. External auth, Warehouse, Client, Admin, shared inventory/order database, notification transport, cloud storage and hosting must be named `Place holder`, with the future owner's work described. Real Delivery actions still commit through the Java backend, not front-end-only flags.

## Session and errors

- `GET /api/session` -> `{authenticated, user: {id,displayName,role}|null, csrfToken, csrfHeader, mode:"demo"}`. A session cookie identifies the worker. Never accept a worker ID as authentication.
- `POST /api/login` uses URL-encoded `username,password` plus CSRF header. Demo users `ken` / `mei` are separate Delivery workers, password `Delivery123!`. Success JSON, 401 on bad credentials.
- `POST /api/logout` uses CSRF header.
- Error JSON: `{code,message}` with an appropriate 400/401/403/404/409 status. UI shows the safe message.
- All mutation requests have `requestId` (UUID). Repeating the same actor/action/key/payload returns the earlier successful result without repeating effects; key reuse with a different payload is rejected.
- Mutations return `{message}`; frontend reloads workspace after success. Never update a status locally before backend confirmation.

## Workspace

`GET /api/delivery/workspace` returns:

```json
{
  "user":{"id":"ken","displayName":"Ken Tanaka","role":"DELIVERY"},
  "mode":"demo",
  "stats":{"readyLists":2,"inTransit":0,"deliveredToday":0,"pendingReturns":0,"failedAttempts":0},
  "pickupLists":[{"id":"PL-1001","qrCode":"PL-1001","status":"OPEN","createdAt":"ISO timestamp","claimedBy":null,"claimedAt":null,"stale":false,"packages":[{"id":"PKG-1001","qrCode":"PKG-1001","orderReference":"LMS-1001","status":"READY_FOR_PICKUP","itemSummary":"2 Mugs, 1 Notebook","collectionOutcome":"PENDING","cannotCollectReason":null}]}],
  "tasks":[{"id":"PKG-1001","qrCode":"PKG-1001","orderReference":"LMS-1001","status":"SHIPPED","custody":"DELIVERY","recipientName":"Chan Mei","phone":"+852 6123 4567","address":"Hong Kong demo address","products":[{"sku":"MUG-01","name":"Travel mug","quantity":2}],"attemptCount":0,"activeAttemptId":null,"canStart":true,"canDeliver":false,"canFail":false,"canReturn":true}],
  "attempts":[{"id":"ATT-1","packageId":"PKG-1001","orderReference":"LMS-1001","workerName":"Ken Tanaka","startedAt":"ISO timestamp","endedAt":null,"outcome":"IN_PROGRESS","reason":null,"note":null,"proofs":[{"id":"PROOF-1","type":"SIGNATURE","createdAt":"ISO timestamp","url":"/api/proofs/PROOF-1"}]}],
  "returns":[{"id":"RET-1","packageId":"PKG-1001","orderReference":"LMS-1001","reason":"Vehicle problem","status":"AWAITING_WAREHOUSE","createdAt":"ISO timestamp"}],
  "notifications":[{"id":"N-1","type":"DELIVERY_FAILED","message":"Local event saved; Place holder notification transport.","createdAt":"ISO timestamp","read":false}],
  "integrations":[{"id":"warehouse","name":"Warehouse","status":"PLACE_HOLDER","detail":"Place holder: Warehouse owner must provide packed lists and actual return receipts."}],
  "decisions":[{"id":"U-10","plan":"B","choice":"Stale claim review queue","reason":"Flag inactivity without moving custody.","status":"LOCAL_IMPLEMENTATION"}],
  "settings":{"photoLimitBytes":5242880,"staleClaimMinutes":30,"reviewFailureCount":2,"reminderDays":3,"finalReviewDays":7},
  "demoPackages":[{"id":"PKG-1001","orderReference":"LMS-1001","status":"READY_FOR_PICKUP","custody":"WAREHOUSE","returnStatus":null,"instruction":null,"approved":false,"pendingCancellation":false}]
}
```

`claimedBy` is a worker ID string or null. `tasks` only includes this worker's collected/active/return-pending packages. Contact/address is returned only while the worker actually handles an active delivery; Warehouse receipt and completed delivery revoke access. Pickup list rows never expose contact/address. History must not disclose other workers' private proof. Dashboard counts use authorized data. A `demoPackages` entry contains no recipient contact.

## Delivery actions

- `POST /api/pickup-lists/claim` `{qrCode,requestId}`: atomically claim one collector. Claim does not change physical custody/order stock.
- `POST /api/pickup-lists/{listId}/collect` `{packageQrCode,requestId}`: require current collector, open list membership, packed/approved eligibility, not already collected this cycle. Atomically update order/custody/list item, reduce physical and reserved stock, save a custody event/movement.
- `POST /api/pickup-lists/{listId}/cannot-collect` `{packageId,reason,note,requestId}`: before pickup only; reason `VEHICLE_FULL`, `PACKAGING_DAMAGED`, `GOODS_DAMAGED`, `OTHER`; Other needs note. Damage creates a Warehouse hold, vehicle-full can be released on close. No customer attempt is invented.
- `POST /api/pickup-lists/{listId}/close` `{requestId}`: preserve collected/uncollected history; release eligible uncollected packages for new lists.
- `POST /api/packages/{packageId}/start` `{requestId}`: only actual holder with SHIPPED package can enter OUT_FOR_DELIVERY; create a new attempt tied to the current pickup and frozen delivery details.
- `POST /api/packages/{packageId}/proofs` multipart `type=PHOTO|SIGNATURE`, `file`: only actual holder during its active attempt; validate decoded JPEG/PNG, type, size and actual content. Save privately; return `{id}`. Files must never be public static resources.
- `POST /api/packages/{packageId}/outcome` `{outcome:"DELIVERED"|"FAILED",reason,note,proofIds:[],requestId}`. Success needs at least one existing valid proof bound to this attempt. Failure reason is one of `CLIENT_NOT_HOME`, `WRONG_ADDRESS`, `CLIENT_REFUSED`, `UNABLE_TO_CONTACT`, `OTHER`; Other needs note. Separate attempts are appended. Failed outcome keeps actual holder until Warehouse receipt. Stock does not change on failure.
- `POST /api/packages/{packageId}/return` `{reason,requestId}`: holder reports return handoff pending Warehouse; supports SHIPPED before any attempt, or DELIVERY_FAILED. Does not restore stock or fake a failed attempt.
- `GET /api/proofs/{proofId}`: enforce active assigned Delivery access; no access after completion/return, unless a future authorized Client owner adapter grants own-order access. Return private no-store image content.

## Integration sandbox (demo profile only)

These are explicit `Place holder` test controls, isolated from real Delivery work and disabled outside demo mode. Their backend events include simulated Warehouse/Client/Admin actors. They must not be confused with actual staff confirmation.

- `POST /api/demo/packages/{packageId}/confirm-return` `{condition:"INTACT"|"DAMAGED",requestId}`. Only after a pending return. Warehouse simulated receipt restores actual physical quantities; intact allocated goods restore reserved quantities as well. Damaged goods remain unavailable/held. No automatic redelivery approval. For a pre-attempt intact return, re-enter READY_FOR_PICKUP or the already-approved READY_FOR_REDELIVERY, preserving cycle eligibility.
- `POST /api/demo/packages/{packageId}/client-instruction` `{instruction:"REDELIVER"|"CANCEL",requestId}`. Only after receipt; version and bind to the latest return/address. A cancel instruction blocks redelivery.
- `POST /api/demo/packages/{packageId}/admin-decision` `{decision:"APPROVE_REDELIVERY"|"APPROVE_CANCELLATION",requestId}`. Check latest receipt/instruction, damaged hold and no pending cancellation; freeze address version. Redelivery creates a new eligible pickup list/cycle. Cancellation final; refund integration is Place holder, not refunded.
- `POST /api/demo/pickup-lists/{listId}/release` `{requestId}`: Warehouse-presence confirmation + Admin simulated release of only uncollected entries; reject later scans on old list. Never transfer collected custody.

## Implementation boundaries

- Use real durable local delivery data. On startup seed only when empty. Repeated startup must not reset user work.
- Local H2 is a Database `Place holder`, not adoption of the member's draft schema. Keep event/cycle IDs and API contracts easy to replace by the Database owner.
- A consistent local transaction handles pickup stock/order/custody/list changes. A shared row lock or equivalent is acceptable for a small module. Async notifications must not replace atomic stock updates.
- Private local proof storage is the local fallback; U-09 B object storage connector and cloud credentials are a marked `Place holder`. Provide cleanup/orphan monitoring and minimal retained summary after one-year detailed proof/attempt expiry. Do not claim cloud configuration is complete.
- U-12 B adds review/reminder queues (two failures, three/seven days configurable), not automatic cancellation, charges or redelivery.
- U-01 B cloud deployment/rehearsal/managed backups remain configuration Place holders. Supply health, repeatable build and manual backup/restore guidance. Do not deploy a separate unrelated website.
- All code comments are simple English. Put `Decision U-xx, B:` near relevant code and `Place holder:` near replacement points. Explain the future owner/data needed.
