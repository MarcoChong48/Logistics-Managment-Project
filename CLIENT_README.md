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

## Milestone 2 limitations

- Expired unpaid orders keep their reservations. Before real use, expiry and cancellation must release each hold once, decrement reserved stock, and record its release in the same transaction.
- Repeated successful POST requests can create duplicate orders. Clients should avoid automatically retrying an ambiguous request.
- Payment confirmation and cancellation are pending. Future payment confirmation must coordinate with reservation release before an order can move out of `AWAITING_PAYMENT`.
- Automated verification uses PostgreSQL 18.1. PostgreSQL 17 and manual browser testing remain unverified.
