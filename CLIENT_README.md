# Client orders and checkout

`POST /api/client/orders` requires a signed-in Client, the shared CSRF token, and an `Idempotency-Key` header. The entire key must match `[A-Za-z0-9._~-]{1,128}`. The request accepts product IDs and whole-number quantities, the recipient name, a contact phone, and the full Hong Kong delivery address. The schema stores the address as supplied and has no delivery-instructions field on the order's address record.

## Try it locally

Start the [local stack](ADMIN_README.md) and sign in as a Client. Run the following in the browser console on the shared welcome page. Replace `123` with an ID from the product listing that has available stock.

```javascript
await sharedSendRequest("GET", "/api/client/products")

const orderKey = crypto.randomUUID()
const orderRequest = {
  items: [{ productId: 123, quantity: 1 }],
  recipientName: "Test Recipient",
  contactPhone: "+852 5555 0100",
  deliveryAddress: "Flat A, 12 Nathan Road, Kowloon, Hong Kong"
}
await sharedSendRequest("POST", "/api/client/orders", orderRequest, null, orderKey)
```

Expect HTTP 201 with `orderReference`, `orderStatus` set to `AWAITING_PAYMENT`, `totalAmount` in HKD, and `paymentDeadline` 24 hours after creation. The total uses current product prices and includes delivery. Invalid items or delivery fields return 400. Recipient names, phones, and addresses are limited to 120, 40, and 1,000 characters respectively. Missing products return 404. Inactive products or unavailable stock return 409. The Create Order stock check is a snapshot. Creating or replaying an order leaves inventory and reservation history untouched.

## Starting checkout

`POST /api/client/orders/{orderReference}/checkout` uses the Client session and shared CSRF header. Copy `orderReference` from the Create Order response.

```javascript
await sharedSendRequest("POST", "/api/client/orders/" + encodeURIComponent(orderReference) + "/checkout")
```

Expect HTTP 200 with `checkoutId`, `orderReference`, `amountHkd`, `checkoutExpiresAt`, and `providerCheckoutAvailable`. A usable Stripe session also returns `checkoutUrl`; otherwise the flag is false and the URL is omitted. The server uses the saved order total. It locks the order, then products and inventory in ascending product ID order, rechecks stock, and writes the `PENDING` payment attempt, its `HELD` reservations, and `RESERVE` movements in one transaction. Physical stock is unchanged. A repeat during the same entry window returns the existing attempt. Unknown or unowned orders return 404. An ineligible order, insufficient stock, or an unresolved attempt past its entry window returns 409.

The entry window ends after 30 minutes or at the original order deadline, whichever comes first. A card decline or an elapsed entry window leaves the hold in place. When a trusted integration verifies that the attempt ended unpaid, the service records `ENDED_UNPAID` and releases only that attempt's holds. A new checkout can then recheck stock before the original deadline. The adapter must verify a terminal unpaid outcome with the provider before reporting it.

Only trusted provider evidence can trigger acceptance. The service checks the captured HKD amount against both the saved order and the payment attempt, checks the current holds and order status under the order lock, and compares database time with the strict 24-hour deadline. An accepted payment moves the order to `READY_FOR_PICKING`. The Warehouse Worker starts picking separately. An ineligible verified charge, including one discovered after cancellation or the deadline, records `REFUND_REQUIRED` and a durable `REQUESTED` refund for the full captured amount. The order remains closed if already cancelled. Browser return URLs do not report or change payment status.

With the default expiry job enabled, a hold on an unresolved unpaid order ends at explicit Client cancellation or the first successful expiry sweep after the 24-hour deadline. Use reservation-only checkout on an isolated development or test stack. With Stripe enabled, the reconciliation job retrieves provider state for linked attempts with closed entry, expired windows or final orders. It requests early expiry of open Stripe sessions. A local timeout, cancellation or missing callback alone is insufficient evidence of an unpaid checkout.

## Stripe Sandbox foundation

Stripe is disabled by default. For an approved isolated sandbox run, apply [migration 05](database/README.md#stripe-sandbox-foundation) to that database and use environment configuration. Use a dedicated Stripe Sandbox with cards enabled and HKD settlement. The adapter uses one-time card Checkout in HKD with Adaptive Pricing disabled. It requires the saved total to fit exactly in minor units between HK$4.00 and HK$999,999.99. The upper bound uses the conservative eight-digit PaymentIntent API limit. The checkout returns HTTP 409 for an unsupported total before reserving stock. A free-order path remains a separate policy decision. [Stripe currency limits](https://docs.stripe.com/currencies#minimum-and-maximum-charge-amounts) · [PaymentIntent amount](https://docs.stripe.com/api/payment_intents/create#create_payment_intent-amount)

| Environment variable | Sandbox setting |
|---|---|
| `LMS_CLIENT_STRIPE_ENABLED` | `true` for an isolated sandbox run. Defaults to `false`. |
| `LMS_STRIPE_TEST_API_KEY` | Sandbox secret or restricted test key with access to create, retrieve, and expire Checkout Sessions and retrieve PaymentIntent data. Live keys are rejected. |
| `LMS_STRIPE_WEBHOOK_SECRET` | Signing secret from the local Stripe CLI listener or the specific Sandbox webhook endpoint. |
| `LMS_STRIPE_RETURN_URL` | Fixed app URL for both Stripe returns. For example, `http://localhost:8081/shared/shared-welcome.html`. Use HTTPS outside loopback. |
| `LMS_STRIPE_CHECKOUT_SCOPE` | Stable, non-secret identifier unique to this LMS database within the Stripe Sandbox. Use 8–48 letters, digits, underscores or hyphens. Keep it stable while any attempt remains. |

The backend claims creation once in PostgreSQL before calling Stripe. The provider create key includes the scope and LMS checkout ID. A lost response or an interruption between claiming and calling Stripe can leave an unmatched claim. It survives a restart and blocks further Session creation for that attempt, even after Stripe's idempotency key retention period. A signed Session event can supply the missing ID, which LMS verifies through a server retrieval before binding it. If no event arrives, an operator should inspect the dedicated Sandbox for the scope and attempt metadata, resend any matching event from Stripe, and keep the claim intact. If Stripe stays unreachable or no Session can be established, keep provider state unresolved. Normal order cancellation or expiry still releases stock. Preserve the claim and continue reconciliation for any charge reported later. [Stripe API idempotency](https://docs.stripe.com/api/idempotent_requests)

The Stripe webhook accepts only `POST /api/webhooks/stripe`. It verifies the original body, deduplicates events in PostgreSQL, and retrieves current Session and PaymentIntent state with a sandbox key. Test locally via Stripe CLI for the same Sandbox. Register a public HTTPS endpoint only if a future isolated demo deployment is approved. Keep login and CSRF on Client requests, and keep provider credentials out of Git. [Stripe webhook setup](https://docs.stripe.com/webhooks#local-listener)

```powershell
stripe listen --events checkout.session.completed,checkout.session.expired --forward-to localhost:8081/api/webhooks/stripe
```

### The three deadlines

1. **LMS entry and retry.** Stops 30 minutes after LMS reserves stock, capped by the original order deadline. LMS stops returning hosted URLs at that cutoff.
2. **Stripe hosted Session.** Native expiry needs at least 30 minutes from provider creation. The adapter requests a backstop 31 minutes from preparing the Stripe request and tries to expire the session early after the LMS cutoff or final cancellation. An already-open page may remain usable until Stripe confirms closure. Check this with an abandoned session, a declined card retry, and an order with under 30 minutes left. [Checkout Session creation](https://docs.stripe.com/api/checkout/sessions/create) · [Session expiry](https://docs.stripe.com/api/checkout/sessions/expire)
3. **LMS payment acceptance.** The order must still be eligible and the verification and locked acceptance must occur strictly before the original 24-hour deadline. In the disabled-by-default foundation, if an already-open page produces a capture after the LMS entry cutoff, the existing 5B-1 logic accepts it when verified before the order deadline with an eligible order and intact holds. It queues a full refund if cancellation, order expiry or verified unpaid closure has already won. This behavior is observable in the sandbox foundation and does not satisfy the confirmed entry and card retry rule. Keep Stripe disabled for any requirement-compliant demo until the gap is resolved.

Stripe's public Checkout API does not provide a documented timestamp for each hosted Pay or retry submission. Session, PaymentIntent, Charge and Event `created` record when Stripe created those objects. An authenticated success record dated clearly before the cutoff can establish an early successful attempt. A timestamp after cutoff cannot generally distinguish a late click from an earlier submission followed by 3-D Secure, processing, or delayed reporting. Do not infer a new retry from webhook delivery time. [Checkout Session](https://docs.stripe.com/api/checkout/sessions/object) · [PaymentIntent](https://docs.stripe.com/api/payment_intents/object) · [Charge](https://docs.stripe.com/api/charges/object) · [Stripe event ordering](https://docs.stripe.com/webhooks#event-ordering) · [3-D Secure flow](https://docs.stripe.com/payments/3d-secure/authentication-flow)

To preserve the entry rule, evaluate a provider or payment flow that lets the server gate each confirmation attempt against the LMS cutoff. If the team explicitly approves retaining hosted Checkout with ambiguity, a conservative alternative is to withhold fulfillment and queue a refund for a captured payment whose timely submission cannot be proved. That can refund legitimate attempts that completed authentication late and still cannot prevent late input on an open hosted page. The foundation implements neither option. Sandbox verification should compare attempts on either side of the cutoff, including an in-flight 3-D Secure challenge and a retry after a decline.

## Cancelling or expiring an order

`POST /api/client/orders/{orderReference}/cancel` uses the same Client session and CSRF protection. An owner can cancel `AWAITING_PAYMENT` or `READY_FOR_PICKING` until picking actually starts. A successful cancellation or a repeat for an already cancelled order returns 200 with `orderReference` and `orderStatus`. Unknown or unowned orders return 404. An ineligible order returns 409.

```javascript
await sharedSendRequest("POST", "/api/client/orders/" + encodeURIComponent(orderReference) + "/cancel")
```

At the deadline, unpaid orders are eligible for the expiry job. The job starts with the application and runs a database sweep every minute by default. It reads persisted deadlines in batches, skips a busy order row, and processes each order in a separate transaction. An overdue order that was skipped or failed remains eligible on the next sweep, including after a restart. Set `lms.client.orders.expiry.interval` to change the delay between sweeps or `lms.client.orders.expiry.enabled=false` to disable the job.

Both expiry and Client cancellation set `CANCELLED`, end checkout entry, and release any held stock in one transaction. An unpaid order may have no holds. Each released reservation gets a timestamp, an inventory adjustment and a `RELEASE` movement. The movement reason identifies expiry, cancellation or a verified unpaid checkout. Payment attempts remain available for reconciliation. Cancelling a paid order queues a full refund in the same transaction. Cancellation and expiry are final even if a charge is reported later. Future payment and warehouse writers must lock the parent order before changing its status, payment state or reservations.

Warehouse Start Picking must acquire that lock, recheck `READY_FOR_PICKING`, and record the task start and transition to `PICKING` in one transaction. If cancellation wins the lock, Start Picking must reject the cancelled order. If Start Picking wins, cancellation must reject the started order. A pending task alone leaves the order cancellable. The Warehouse endpoint is pending, so this contract is covered by cancellation checks against persisted task state and still needs an end-to-end race test when the endpoint exists.

A refund starts as `REQUESTED`, with blank provider reference and completion timestamp. The refund writer uses the locked parent order to deduplicate by payment ID and preserves any matching refund row, including its status. Conflicting historical refund rows require review. A future executor must reuse the LMS refund ID when retrying provider submission and record completion only after authoritative provider confirmation. Order cancellation does not wait for provider completion.

## Legacy holds and rollout

Milestones 2 through 4 wrote `HELD` reservations at order creation with `payment_id IS NULL`. Run this read-only inventory on the intended deployment database before rollout.

```sql
BEGIN READ ONLY;
SELECT co.order_reference, co.order_status, co.payment_deadline,
       oi.product_id, sr.reservation_id, sr.quantity, sr.reserved_at
FROM stock_reservation sr
JOIN order_item oi ON oi.order_item_id = sr.order_item_id
JOIN customer_order co ON co.order_id = oi.order_id
WHERE sr.status = 'HELD' AND sr.payment_id IS NULL
  AND sr.released_at IS NULL AND sr.consumed_at IS NULL
ORDER BY co.order_id, oi.product_id, sr.reservation_id;
ROLLBACK;
```

For each row, record the order, reserved quantity, and any payment or fulfillment evidence. Review the result with the team and approve adjustments case by case. A null payment link alone does not establish that the order is unpaid. Have an operator reconcile only a confirmed unnecessary hold in an audited transaction. Lock the order, products, inventory and reservations in that order and recheck the evidence and quantities after locking. Preserve the original IDs and record the decision, affected rows, before and after stock balances, and release movement. Verify the stock totals after the transaction. Checkout and scheduled expiry leave active legacy unlinked holds for this review. An explicit owner cancellation still follows the normal cancellation rules. No migration, data rewrite, or automatic legacy release is included.

## Create Order retry protection

Install [migration 04](database/04_client_order_idempotency.sql) before starting the updated backend against an existing database. Fresh Compose volumes initialize it automatically. See the [installation steps](database/README.md#client-order-idempotency).

Create Order hashes the validated request with SHA-256 using a versioned encoding, sorting items by product ID and trimming outer whitespace from delivery fields. The Client-scoped key lock is taken before product locks. A matching request returns the stored 201 response, even after the order expires or is cancelled. The replay does not read prices, listings, or inventory. A different valid request with the same Client and key returns 409. Missing or invalid keys return 400. The response record, order, items, and delivery details commit together. Failed transactions leave the key available for retry.

The optional fifth argument to `sharedSendRequest()` adds the key alongside the existing session credentials and CSRF header. Generate one key for each intended order and preserve it with that order's payload, including across a page reload if retry is offered. After an ambiguous failure such as a timeout, use the same signed-in Client and resend the same payload with the same key. A new intended order needs a new key.

## My Orders, Order Details and Order Tracking

These GET APIs use the signed-in Client session, return `Cache-Control: no-store`, and make no workflow changes. Unknown and unowned orders return the same 404. `paymentDeadline` is returned only while the order is `AWAITING_PAYMENT` and is null for other statuses.

```javascript
const firstPage = await sharedSendRequest("GET", "/api/client/orders?limit=20")
if (firstPage.nextCursor) {
  await sharedSendRequest("GET", "/api/client/orders?limit=20&cursor=" + encodeURIComponent(firstPage.nextCursor))
}
if (firstPage.orders.length) {
  const orderUrl = "/api/client/orders/" + encodeURIComponent(firstPage.orders[0].orderReference)
  const details = await sharedSendRequest("GET", orderUrl)
  const tracking = await sharedSendRequest("GET", orderUrl + "/tracking")
}
```

`GET /api/client/orders` returns `orders` and `nextCursor`. Each row has `orderReference`, `createdAt`, `orderStatus`, `totalAmountHkd`, `paymentDeadline` and `itemSummary` with `productCount` and `totalQuantity`. The default page size is 20 and the allowed range is 1–50. An out-of-range or invalid limit returns 400. Results are newest first with a stable tie-breaker. Pass `nextCursor` unmodified to fetch the next page. A null cursor means there is no next page. A Client without orders receives `{"orders":[],"nextCursor":null}`. Payment and refund badges are available from the detail and tracking endpoints. A future list-level badge feature would need a bulk summary in this response.

`GET /api/client/orders/{orderReference}` includes `items`, `deliveryDetails`, `paymentAttempts` and `canCancel` alongside the order reference, creation time, status, total and deadline. Items use saved quantities and `unitPriceHkd`. The schema has no order-time SKU or name snapshots, so `sku` and `name` come from the current catalogue with `labelSource: "CURRENT_CATALOGUE"`. Renamed or inactive items remain visible. The schema prevents deleting a product referenced by an order item. If imported legacy data lacks a catalogue row, the item still exposes its saved quantity and price with null labels and `labelSource: "UNAVAILABLE"`. `deliveryDetails` contains `version`, `recipientName`, `phone` and `address` from the highest persisted address version on this order, or is null when none was recorded. `canCancel` uses the same status and Start Picking rules as the cancel API. A pending picking task leaves cancellation available. This is a read snapshot and the cancel API rechecks eligibility under the order lock when called.

In both details and tracking, `paymentAttempts` is an array ordered oldest first. Attempts include `status`, `createdAt`, `acceptedAt` and a `refunds` array. Each refund has `status`, `amountHkd`, `requestedAt` and `completedAt`. An order without an attempt returns `[]`. A refund is shown as `COMPLETED` only when both the persisted completion status and a valid completion timestamp are present. A `REQUESTED` refund remains separate from the main order status. Provider references are excluded.

`GET /api/client/orders/{orderReference}/tracking` includes `orderStatus`, `statusLabel`, `currentStatusSince`, `historyComplete`, `progression`, `deliveryAttempts` and `paymentAttempts`. `progression` contains persisted activities in chronological order, including order placement, picking start, packing confirmation, package creation, known custody events, and delivery starts and outcomes. Each event has `type`, `occurredAt`, and optional `attemptNumber` and `cycleNumber`. A delivery attempt has its own start, end, outcome, client-safe `failureReason` and linked `deliveryAddressVersion`. Tracking does not repeat the address and contact fields. Driver notes, identity, package QR codes, proofs, and raw failure text are excluded. `attemptNumber` and `cycleNumber` are display ordinals. A cycle is only assigned when the attempt links to an observed pickup for the same parcel. Repeated pickup records for one known list share the first recorded pickup's cycle. Pickups without a list remain separate because the schema has no key to distinguish duplicates from separate collection events. Unknown or missing links produce a null cycle. `currentStatusSince` is null and `historyComplete` is false because the schema lacks exact transition history. A current state alone never adds a historical event.

### Proposal for complete order history

The current APIs require no schema migration or writer changes. For a later team milestone, agree an append-only status history table keyed by order and transition ID. Include previous and next status, database timestamp, source and an idempotency reference. The actor can be retained for internal audit. Keep order ownership joins and expose only approved fields to Clients. Warehouse, Delivery and Client/payment writers would append a transition while holding the same order-row lock and commit it with the status update. Record repeat delivery cycles separately. Leave older unknown transitions without fabricated dates. This proposal has no SQL migration or backfill in Milestone 6.

Warehouse must persist picking start, packing confirmation and custody events. Delivery must persist each attempt with the address version actually used and link it to the same parcel's pickup event. For address approval, agree how writers distinguish a proposed version from the effective version before storing drafts as newer delivery details. The current schema has no effective-version flag. Client instructions record requests and approvals, which alone do not establish delivery events. Agree public reason codes before adding new mappings. The current read model recognizes delivery outcomes `IN_PROGRESS`, `FAILED` and `DELIVERED` and custody types `PICKED_UP` and `RETURNED_TO_WAREHOUSE`. Unknown outcomes display `UNKNOWN`, and unknown failed-delivery reasons use generic wording. Admin inspection found no shared status history or item name snapshots. Recheck the shared Product/OrderItem and security changes when the branches are integrated. [Admin Stage 4](https://github.com/MarcoChong48/Logistics-Managment-Project/commit/d3759e685496969f0c53b7237ee1e3169c178438)

## In-app notifications

All four endpoints require a signed-in Client. List and count are read-only. Responses use `Cache-Control: no-store`, and both POSTs use the shared CSRF header.

| Request | Response |
|---|---|
| `GET /api/client/notifications?limit=20` | `200` with `notifications` and `nextCursor`. Each item has `notificationId`, `type`, `message`, `orderReference`, `createdAt`, and `isRead`. |
| `GET /api/client/notifications/unread-count` | `200` with `{ "unreadCount": 2 }`. |
| `POST /api/client/notifications/{notificationId}/read` | `200` with `{ "notificationId": "42", "isRead": true }`, including when it was already read. Unknown and unowned IDs return the same `404`. |
| `POST /api/client/notifications/read-all` | `200` with `{ "markedRead": 2 }`, the number of the Client's notifications changed by the request. Repeating it returns zero if no new unread notifications arrived. |

The list is newest first by `(createdAt, notificationId)`. `notificationId` and `nextCursor` use decimal strings so JavaScript can preserve the full identifier. Use a page size from 1 to 50 and pass `nextCursor` as `cursor` on the next request. Invalid limits return `400`. A numeric cursor must identify an owned notification or the API returns the same `404` as mark-one. An empty inbox returns `{"notifications":[],"nextCursor":null}`. `orderReference` is null for a notification without a link to an owned order. Unrecognized legacy types and invalid cross-owner links display as `GENERAL` with a generic message. Text is limited to approved public templates, including when a legacy row contains free text.

```javascript
const inbox = await sharedSendRequest("GET", "/api/client/notifications?limit=20")
const count = await sharedSendRequest("GET", "/api/client/notifications/unread-count")
if (inbox.nextCursor !== null) {
  await sharedSendRequest("GET", "/api/client/notifications?cursor=" + encodeURIComponent(inbox.nextCursor))
}
if (inbox.notifications.length) {
  const id = inbox.notifications[0].notificationId
  await sharedSendRequest("POST", "/api/client/notifications/" + encodeURIComponent(id) + "/read")
}
await sharedSendRequest("POST", "/api/client/notifications/read-all")
```

### Business event writer

Inject `ClientNotificationWriter` in the service responsible for the business change. Hold the existing order-row lock, persist the new order status and its event evidence, flush any JPA entities, then call the writer before committing. It joins the existing transaction, derives the recipient from the order, and verifies that the evidence belongs to that order. It returns `INSERTED` for a new notification, `ALREADY_RECORDED` for a previously committed or inserted event, and `EVIDENCE_MISSING` if no matching notification exists and the supplied evidence or status does not qualify. A direct publisher must roll back its transition if the expected evidence is missing. Keep the same persisted source IDs on retry. [Migration 06 and rollback notes](database/README.md#client-notification-event-identity)

| Event | When the responsible service calls the writer | Durable source ID |
|---|---|---|
| `PAYMENT_CONFIRMED` | Already wired to `paymentConfirmed(orderId, paymentId)` after trusted capture verification sets the payment to `ACCEPTED` with `accepted_at` and moves the order to `READY_FOR_PICKING`. Browser returns, pending, declined, or refund-required payments do not qualify. Stripe remains disabled by default. | Order ID. One payment confirmation per order. |
| `SHIPPED` | Delivery calls `shipped(orderId, pickupListId)` when physical pickup moves the order to `SHIPPED`. The same transaction must set the parcel's pickup-list item to `COLLECTED` with `collected_at` and record `PICKED_UP` custody for that parcel and list. Claiming a list or scanning an internal QR code does not qualify. | Pickup list ID. A genuine redelivery pickup uses a new list. |
| `DELIVERY_FAILED` | Delivery finalizes the existing attempt as `FAILED` with `end_time` and a recognized reason code, sets `DELIVERY_FAILED`, and calls `deliveryFailed(orderId, attemptId)`. The attempt's parcel and address version must belong to the same order. | Delivery attempt ID. Separate failed visits may each notify. |
| `DELIVERED` | Delivery finalizes the attempt as `DELIVERED` with `end_time`, persists any required delivery proof under its own workflow contract, sets `DELIVERED`, and calls `delivered(orderId, attemptId)`. The writer does not expose proof or contact fields. | Order ID. One final delivery notice per order. |

`SHIPPED`, `DELIVERY_FAILED`, and `DELIVERED` have writer support and isolated tests. They await integration with Delivery's transition handlers. In addition to the existing shared codes, Tracking and Notifications map Delivery's `CLIENT_NOT_HOME` and `UNABLE_TO_CONTACT` to recipient unavailability, `WRONG_ADDRESS` to address not found, `CLIENT_REFUSED` to delivery refused, and `OTHER` to a generic failure. Unknown codes also use the generic message. Keep worker notes, audit data, and provider references out of the notification payload. This milestone adds no historical backfill, external messaging, or frontend. [Delivery v3.1 API contract](https://github.com/MarcoChong48/Logistics-Managment-Project/blob/e1e36a9810bf127af6fc5c4806ae676465ec39ec/features/delivery/API_CONTRACT.md)

Delivery v3.1 writes a placeholder row to `notification_outbox` in its own database. It records local IDs, a display message and transport status. Delivery and LMS need to agree how local records map to authoritative LMS order, parcel, pickup-list and attempt IDs. Map Delivery's `PICKUP` custody event and `ended_at` to shared `PICKED_UP` and `end_time`. [Published outbox schema](https://github.com/MarcoChong48/Logistics-Managment-Project/blob/e1e36a9810bf127af6fc5c4806ae676465ec39ec/features/delivery/backend/src/main/resources/schema.sql) [Delivery integration handoffs](https://github.com/MarcoChong48/Logistics-Managment-Project/blob/e1e36a9810bf127af6fc5c4806ae676465ec39ec/features/delivery/docs/INTEGRATION.md)

1. Choose one publication path for each transition. Use the direct writer when Delivery can persist the transition and notification in the same shared database transaction. With separate databases, use the Delivery outbox as transport and display only shared LMS `notification` rows in the Client inbox.
2. For the outbox path, Delivery must persist a typed event with the business transition. Include a stable transport event ID, type, mapped LMS order ID, mapped pickup-list or attempt ID, and the published reason code for a failed attempt. Reuse the same IDs after command retries. The transport ID tracks delivery of the message. The business identity in the table above deduplicates the notification. Omit recipient overrides and display text.
3. The consumer must authenticate the Delivery source, verify event ownership and mappings, and process transitions in order for each order. In one shared LMS transaction, acquire the order-row lock, check whether the canonical event is already recorded, and return the existing acknowledgement for an already-applied event. For a new event, persist the valid transition and its matching evidence, then invoke the writer. Conflicting or stale transitions need reconciliation before any new write.
4. Acknowledge `INSERTED` or `ALREADY_RECORDED` after the LMS transaction commits. Keep `EVIDENCE_MISSING` pending for investigation or retry after reconciliation. A crash after commit and before acknowledgement replays the same key. During cutover, both paths must preserve that key and shared LMS must remain the only Client-facing publisher. Retain keyed rows, or their identities if notification retention is introduced.

The typed payload, consumer, ID mappings and cross-module integration tests are pending. This milestone has no Delivery outbox consumer.

## Verification and deployment limits

- Automated verification uses PostgreSQL 18.1. PostgreSQL 17 and manual browser testing are pending.
- Existing databases need migration `04` before using the updated Create Order API. Earlier orders have no replay records.
- Stripe's hosted cutoff, terminal expiry evidence after a decline, and live webhook forwarding with Sandbox credentials remain unverified. Refund submission and verification of provider refund outcomes are pending. The adapter records `REQUESTED` obligations only.
- The local Compose stack activates the existing `admintest` profile, which includes an Admin-only fixture generator that creates already-paid sample orders. Keep that profile out of normal deployments.
