# Logistics Management System - Requirements Draft v1

Group 54 | COMP 3500SEF Software Engineering Project
Prepared by Matthew | 4 Oct 2026

Please read this before the meeting on 5 Oct.
This is a draft. We will review and change it together in the meeting.

---

## 1. About the Project

A web-based system for a retail shop. The shop sells products online and delivers by itself (like a small Taobao).

- Backend: Java (Spring Boot)
- Frontend: web pages, one interface for each role
- Database: relational database

## 2. Roles

| Role | What they do |
|---|---|
| Client | Browse products, order, pay, track the order |
| Warehouse | Pack orders, update stock, record restock |
| Delivery | Pick up packed orders, deliver, upload proof of delivery |
| Admin | Manage products, users, and monitor the whole system |

Out of scope: manufacturer / supplier role. Buying from suppliers is done offline by the shop staff. Restock is recorded in the system by scanning, but purchasing is not in the system.

## 3. Order Status Flow

Every order goes through these statuses in this order:

1. awaiting payment - order created, stock reserved, client has 24 hours to pay
2. pending - paid, waiting for warehouse to pack
3. packed - all items scanned and packed, checked by a second staff
4. awaiting shipment - packed and waiting for the delivery person to pick up
5. shipped - the delivery person has the order and is going to the client
6. awaiting delivery - out for delivery
7. delivered - finished, with proof (signature or photo)

Rules:
- The client can cancel only while the status is "pending".
- An "awaiting payment" order is cancelled automatically after 24 hours. The reserved stock goes back.
- The client can see all statuses.
- Warehouse sees only "pending". Delivery sees "packed" (to pick up) and "shipped" (to deliver).

## 4. Functional Requirements (Draft)

### 4.1 Client

- C1: The system shall return all products whose name contains the search text entered by the user. Search is not case-sensitive (uppercase and lowercase are the same).
- C2: When something fails, the system shall show the user a friendly message with an error code. The technical details are written to the system log, not shown to the user.
- C3: Before payment, the system shall show an invoice with the items, quantities, unit prices, and total.
- C4: After successful payment, the system shall show a receipt with the items, prices, payment time, payment method, and the last 4 digits of the card.
- C5: The system shall allow a logged-in client to view the status of their own orders.
- C6: The system shall allow a user to register (username, password, address), log in, and log out. To change the password, the user must enter the current password and the new password. Passwords can never be viewed, only changed.
- C7: The system shall show the product's name, price, description, and stock availability on the product page.
- C8: The system shall allow the client to add a product to the cart from the product page. In the cart, the client can change the quantity, remove an item, and see the total.
- C9: At checkout, the system shall use the client's stored address by default. The client can change it before paying.
- C10: The system shall process payment through an external payment provider (like Stripe or PayPal). Our system shall never store the card number. Our system only records the result: success or fail, method, and time.
- C11: The system shall check stock when an item is added to the cart. Stock is reserved when the order is created. If payment is not done within 24 hours, the reservation is cancelled and the stock goes back. If an item is out of stock, the system shall show an out-of-stock message.
- C12: The system shall allow the client to cancel an order only while the status is "pending".

### 4.2 Warehouse

- W1: The system shall show the warehouse a packing list with only the orders that are "pending" (paid). Each order shows the items and quantities to pack.
- W2: The system shall reduce the stock in the database when the warehouse worker scans the product QR code during packing.
- W3: The system shall show a low-stock warning on the warehouse main page when a product's stock is below its threshold. Each product has its own threshold number, set by the admin.
- W4: After all items are scanned, the system shall require a second warehouse staff member to check the order before it can be marked as "packed".

### 4.3 Restock (done by warehouse, decided 4 Oct)

- R1: The system shall allow the warehouse to record a restock by scanning the product's QR code and entering the received quantity. The stock in the database increases. Buying from suppliers is still done offline by the shop staff.

### 4.4 Delivery

- D1: The system shall show the delivery person a list of orders that are "packed" (ready to pick up), with the delivery address, client phone number, and items.
- D2: To mark an order as "delivered", the delivery person must attach proof: a client signature or a photo of the delivered item. If the client is not home, the system shall record a failed delivery and the order stays waiting for the next delivery attempt.
- D3: The client's address and phone number can only be seen by the delivery role and the client. No other role can see them.

### 4.5 Admin

- A1: The system shall give the admin a dashboard to see all orders and their statuses, and to see system logs and error logs.
- A2: The system shall allow the admin to add, edit, remove, and view products.
- A3: The system shall allow the admin to add and remove user accounts, ban and unban accounts, reset passwords, and change a user's role. (Reason: part-time staff change departments, so roles must be changeable.)
- A4: The system shall restrict the admin from seeing client passwords (passwords are stored encrypted and cannot be viewed by anyone).

## 5. Non-Functional Requirements (Draft)

- N1 Performance: Pages shall load within 1 second.
- N2 Security: Passwords are stored encrypted (hashed). Card numbers are never stored. Client address and phone are visible only to the client and delivery. Users who are not logged in cannot open any role page.
- N3 Usability: A first-time user shall be able to complete an order within 5 minutes without help.
- N4 Reliability: The system shall be available 99% of the time.

## 6. Things We Still Need to Decide in the Meeting

1. Confirm this requirements draft (try to find anything unclear - this is the point of the review)
2. Module division: 9 people, one module each. Proposal: 4 frontends (client / warehouse / delivery / admin), 1 backend API, 1 database, 1 login & security, 1 testing, 1 documentation + UI design
3. Who is the testing lead? Testing plan: JUnit tests, written before or with the code
4. How do we handle requirement changes? Proposal: every change goes through a GitHub issue, approved by the product owner
5. Sprint plan: 2-week sprints until 8 Dec. Sprint 1 = client can browse and order + database schema
6. Confirm the product backlog in GitHub Projects, with priorities (P1 must have, P2 should have, P3 nice to have)

## 7. Backlog Items (P2 / P3, not in the first sprints)

- Member level / loyalty points
- Order history page for clients
- Search filters (price range, category)
- Coupons / discount codes

---

This draft follows the method from Lecture 4 (Requirements Engineering): every requirement uses "shall", every requirement can be tested, and vague words like "correct" or "fast" are not used.
