# LMS Database Draft v0.4

**Status:** Draft for group discussion — not final/team-approved.

## Tables

| Table | Simple purpose | Main fields |
|---|---|---|
| `User` | Common account/login data for all four roles. | `PK userID, username UNIQUE, passwordHash, role, accountStatus, failedLoginCount, lockUntil` |
| `ClientProfile` | Saved default delivery details for a client. | `PK/FK userID, defaultRecipientName, defaultPhone, defaultAddress` |
| `DeliveryWorkerProfile` | Delivery-worker-specific information such as employee and vehicle/equipment details. | `PK/FK userID, employeeNumber UNIQUE, vehicleType, vehiclePlate, equipmentNote` |
| `Product` | Current product catalogue information. | `PK productID, SKU UNIQUE, name, currentPrice, lowStockThreshold, listingStatus` |
| `Inventory` | Current stock totals for each product in the single warehouse. | `PK/FK productID, physicalStock, reservedStock` |
| `StockReservation` | Shows which order item owns reserved stock and when that reservation is released. | `PK reservationID, FK orderItemID, FK paymentID NULL, quantity, status, reservedAt, releasedAt NULL` |
| `StockMovement` | History of physical stock changes such as restock, pickup and confirmed return. | `PK movementID, FK productID, FK packageID NULL, FK actorUserID NULL, movementType, quantityDelta, eventTime` |
| `Order` | Main order record and its current logistics status. | `PK orderID, orderReference UNIQUE, FK clientUserID, orderStatus, createdTime, paymentDeadline, totalAmount` |
| `OrderItem` | Products, quantities and historical unit prices inside an order. | `PK orderItemID, FK orderID, FK productID, quantity, unitPrice` |
| `OrderDeliveryDetail` | Versioned recipient, phone and address details for an order. | `PK deliveryDetailID, FK orderID, versionNo, recipientName, phone, address, createdTime, UNIQUE(orderID, versionNo)` |
| `Payment` | One checkout/payment session for an order. | `PK paymentID, FK orderID, amount, paymentStatus, providerReference NULL, createdTime, endedTime NULL` |
| `Refund` | One refund request/attempt against a payment. | `PK refundID, FK paymentID, amount, refundStatus, providerReference NULL, createdTime` |
| `PickingTask` | The single warehouse picking job for an order. | `PK pickingTaskID, FK orderID UNIQUE, taskStatus, startTime NULL, confirmPackingTime NULL` |
| `PickingAssignmentHistory` | History of which warehouse worker owned a picking task. | `PK assignmentID, FK pickingTaskID, FK workerUserID, assignedTime, endedTime NULL` |
| `PickingScanHistory` | Accepted, rejected and reversed product scans for a picking task. | `PK scanID, FK pickingTaskID, FK orderItemID, FK workerUserID, scanTime, result, FK originalScanID NULL` |
| `Package` | The one physical package created for an order. | `PK packageID, FK orderID UNIQUE, packageQRCode UNIQUE, createdTime, FK currentHandlerUserID NULL` |
| `PickupList` | A warehouse-created group of packages for a delivery worker to collect. | `PK pickupListID, listQRCode UNIQUE, FK createdByWorkerID, createdTime, status, FK claimedByDeliveryWorkerID NULL, claimedTime NULL` |
| `PickupListItem` | Links a package to a pickup list and stores its collection result. | `PK/FK pickupListID, PK/FK packageID, collectionOutcome, collectedTime NULL, cannotCollectReason NULL` |
| `PackageCustodyEvent` | History of actual package pickup, return and custody changes. | `PK custodyEventID, FK packageID, eventType, FK actorUserID NULL, FK pickupListID NULL, eventTime, details NULL` |
| `DeliveryAttempt` | One separate customer delivery attempt. | `PK deliveryAttemptID, FK packageID, FK deliveryDetailID, FK deliveryWorkerID, startTime, endTime NULL, outcome, failureReason NULL, internalNote NULL` |
| `DeliveryProof` | Signature/photo proof attached to a delivery attempt. | `PK proofID, FK deliveryAttemptID, proofType, proofReference, createdTime` |
| `ClientInstruction` | A client's post-return request for redelivery or cancellation. | `PK instructionID, FK orderID, FK clientUserID, instructionType, status, submittedTime, FK resolvedByAdminID NULL, resolvedTime NULL` |
| `Notification` | An in-system notification for a user. | `PK notificationID, FK recipientUserID, FK orderID NULL, type, message, createdTime, readStatus` |
| `AuditLog` | Important operational/admin changes with only the relevant old/new values. | `PK auditID, FK actorUserID NULL, action, targetType, targetID, oldValue NULL, newValue NULL, timestamp, result` |
| `SecurityLog` | Login, lock, password-reset and authorization-related security events. | `PK securityLogID, FK userID NULL, eventType, timestamp, result, ipAddress NULL, deviceInfo NULL, details NULL` |

## Still open / not final

- Exact fields for `DeliveryWorkerProfile` (vehicle/equipment information still needs team agreement).
- Payment provider/interface and exact provider status mapping.
- Exact refund retry/exception handling.
- Proof-file storage and who can view delivery proof.
- Damaged/incomplete return inspection and stock accounting.
- Pickup-list abandonment/recovery and exceptional reassignment handling.
- Delivery retry limits and final no-response/refusal outcomes.
- Exact account-registration and Hong Kong address/contact validation.
- Exact status labels for some operational tables may change during implementation.
- `Package.currentHandlerUserID` may later be replaced or supplemented by a clearer current-custody design.

> **Team note:** This is a database draft for discussion. Please review it before treating it as the implementation schema.
