# Client order creation

`POST /api/client/orders` requires a signed-in Client and the shared CSRF token. It accepts product IDs and whole-number quantities, the recipient name, a contact phone, and the full Hong Kong delivery address. The schema stores the address as supplied and has no delivery-instructions field on the order's address record.

## Try it locally

Start the [local stack](ADMIN_README.md) and sign in as a Client. Run the following in the browser console on the shared welcome page. Replace `123` with an ID from the product listing that has available stock.

```javascript
await sharedSendRequest("GET", "/api/client/products")

await sharedSendRequest("POST", "/api/client/orders", {
  items: [{ productId: 123, quantity: 1 }],
  recipientName: "Test Recipient",
  contactPhone: "+852 5555 0100",
  deliveryAddress: "Flat A, 12 Nathan Road, Kowloon, Hong Kong"
})
```

Expect HTTP 201 with `orderReference`, `orderStatus` set to `AWAITING_PAYMENT`, `totalAmount` in HKD, and `paymentDeadline` 24 hours after creation. The total uses current product prices and includes delivery. Invalid items or delivery fields return 400. Recipient names, phones, and addresses are limited to 120, 40, and 1,000 characters respectively. Missing products return 404. Inactive products or unavailable stock return 409.

## Reservations and payment

An order holds stock immediately. The service locks products and then inventory in ascending product ID order, saves the order and item price snapshots, and records a `HELD` reservation and a `RESERVE` movement per item. Physical stock stays unchanged until fulfillment. The reservation's nullable payment link stays empty because a payment row represents a checkout attempt.

## Cancelling an unpaid order

`POST /api/client/orders/{orderReference}/cancel` uses the same Client session and CSRF protection. An owner can cancel an unpaid `AWAITING_PAYMENT` order. A successful cancellation or a repeat for an already cancelled order returns 200 with `orderReference` and `orderStatus`. Unknown orders and orders owned by another Client return 404. Paid or otherwise ineligible orders return 409.

```javascript
await sharedSendRequest("POST", "/api/client/orders/" + encodeURIComponent(orderReference) + "/cancel")
```

At the deadline, unpaid orders are eligible for the expiry job. The job starts with the application and runs a database sweep every minute by default. It reads persisted deadlines in batches, skips a busy order row, and processes each order in a separate transaction. An overdue order that was skipped or failed remains eligible on the next sweep, including after a restart. Set `lms.client.orders.expiry.interval` to change the delay between sweeps or `lms.client.orders.expiry.enabled=false` to disable the job.

Both expiry and a Client cancellation use the existing `CANCELLED` order status. Each affected `HELD` reservation becomes `RELEASED`, with a release timestamp, a decrease in reserved inventory, and a `RELEASE` stock movement in the same transaction. Movement reasons begin with `PAYMENT_DEADLINE_EXPIRED` or `CLIENT_CANCELLED`; physical stock is unchanged. Locks are taken on the existing order first, then products and inventory in product ID order, then reservations. Future payment writers must use the same order-row lock before checking `CustomerOrder.canAcceptPaymentAt` with current database time and validating the existing holds. The deadline bars payment eligibility even if the sweep has yet to run.

## Proposed Create Order retry protection

The [proposed migration](database/proposals/client_order_idempotency.sql) adds a table keyed by authenticated Client ID and `Idempotency-Key`. It is outside the Compose initialization scripts and requires approval and separate application.

The proposed implementation normalizes the validated request, sorts items by product ID, and hashes the versioned canonical payload with SHA-256. The hash covers items and delivery details, while prices and inventory remain server-controlled. Within the order transaction, a PostgreSQL transaction advisory lock scoped to the Client and key serializes retries before stock is locked. A matching fingerprint returns the stored 201 response, even after the order has changed status. A different fingerprint returns 409. The original response, fingerprint, and order ID are recorded in the same transaction as a new order and its reservations. Failed transactions leave the key available for retry. The table's primary key enforces uniqueness across app instances and restarts.

Create Order retry protection is pending approval. Repeated successful POST requests can still create separate orders. Payment confirmation is pending. Automated verification uses PostgreSQL 18.1. PostgreSQL 17 and manual browser testing remain unverified.
