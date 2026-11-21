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

Expect HTTP 200 with `checkoutId`, `orderReference`, `amountHkd`, `checkoutExpiresAt`, and `providerCheckoutAvailable: false`. The server uses the saved order total. It locks the order, then products and inventory in ascending product ID order, rechecks stock, and writes the `PENDING` payment attempt, its `HELD` reservations, and `RESERVE` movements in one transaction. Physical stock is unchanged. A repeat during the same entry window returns the existing attempt. Unknown or unowned orders return 404. An ineligible order, insufficient stock, or an unresolved attempt past its entry window returns 409.

The entry window ends after 30 minutes or at the original order deadline, whichever comes first. A card decline or an elapsed entry window leaves the hold in place. When a trusted integration verifies that the attempt ended unpaid, the service records `ENDED_UNPAID` and releases only that attempt's holds. A new checkout can then recheck stock before the original deadline. The adapter must verify a terminal unpaid outcome with the provider before reporting it.

Only trusted provider evidence can trigger acceptance. The service checks the captured HKD amount against both the saved order and the payment attempt, checks the current holds and order status under the order lock, and compares database time with the strict 24-hour deadline. An accepted payment moves the order to `READY_FOR_PICKING`. The Warehouse Worker starts picking separately. An ineligible verified charge, including one discovered after cancellation or the deadline, records `REFUND_REQUIRED` and a durable `REQUESTED` refund for the full captured amount. The order remains closed if already cancelled. This milestone has no payment-result endpoint, configured provider, reconciliation worker, or refund executor.

With the default expiry job enabled, a hold on an unresolved unpaid order ends at explicit Client cancellation or the first successful expiry sweep after the 24-hour deadline. Use the reservation-only checkout endpoint on an isolated development or test stack while it has no provider. In a future sandbox integration, query pending attempts with an elapsed `checkout_expires_at` or set `ended_at` (including cancelled orders), then use the provider to close old entry and verify the outcome. Retain unresolved attempts for retry across restarts, with an operator review path if the provider never supplies a terminal result. Apply results through the internal reconciliation service. A local timeout, cancellation or missing callback alone is insufficient evidence of an unpaid checkout.

Before enabling hosted checkout, the adapter must reuse a stable provider idempotency key derived from the LMS checkout ID when creating or retrying a session. It must enforce the saved entry deadline, stop provider entry after final cancellation or expiry, and bind each attempt to at most one settled HKD charge. Keep the verification calls outside the database transaction. A late charge still needs reconciliation and a refund obligation after stock is released.

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

## Verification and deployment limits

- Automated verification uses PostgreSQL 18.1. PostgreSQL 17 and manual browser testing are pending.
- Existing databases need migration `04` before using the updated Create Order API. Earlier orders have no replay records.
- Sandbox provider selection, checkout sessions, authoritative verification, reconciliation scheduling, and refund execution are pending. The team still needs to decide how checkout handles a zero-HKD order, which the current schema permits.
- The local Compose stack activates the existing `admintest` profile, which includes an Admin-only fixture generator that creates already-paid sample orders. Keep that profile out of normal deployments.
