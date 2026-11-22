# LMS integration report

## Worktree and source revisions

Integration branch `integration/lms-20261122` is checked out in `C:\Users\eeapp\LMS-integration`. The clean worktree was moved from inside the original checkout and its upstream was cleared.

| Source | Local revision verified on 22 November 2026 |
| --- | --- |
| Admin Stage 9 and starting integration HEAD | `0cc608d16b76314962c4755040c0e6339300cfdc` |
| Delivery v4 | `bd63a1617aeec14ea4dcaa9b282051dd60a816b8` |
| Client M10 | `fac82e63bf986e9534bfe162e68a4944bc410e99` |
| Delivery integration checkpoint | `b324b614c8082b512a83d9a0dde989bd42112a68` |
| Reviewed Client M10 merge and Step 2 base | `d2f4a89ed9f26d0f03e292e2f6f807fee086e7bd` |

The three source refs were verified again after the Client conflict resolution. The original checkout is clean on `feature/client-backend`. The Warehouse handoff ZIPs were read in full, with 18 Java files and 11 frontend files. The attached `schema.sql` is the standalone Delivery placeholder. All database tests used Git-tracked LMS migrations.

## Delivery merge checkpoint

Delivery v4 merged without conflicts in `b324b614c8082b512a83d9a0dde989bd42112a68`. The merge retained the Stage 9 Admin modules and brought in Delivery's application code, frontend, migration 07 and Compose overlay. The effective overlay enables `delivery-full`, disables Delivery demo mode, mounts the private proof volume and waits for `delivery-migrate` to succeed. A fresh Compose project named `lms-integration-20261122-check` ran 01, 03 and 07 and contained 31 public tables. The focused Maven checkpoint passed 175 rules with zero failures, errors or skips. Physical pickup and Delivery workspace capability smoke tests remain pending.

## Client merge step 1

The reviewed two-parent Client merge was recorded at the Step 1 checkpoint. The 12 conflicts were resolved. The first run of the revised resolver stopped at a preflight check before any writes. The next run verified all 18 resolution files, checked each replacement count, read back the written content and passed `git diff --cached --check`. A separate verified one-line edit aligned a legacy Admin test with the Delivery v4 login destination. Git reported its configured LF-to-CRLF working-copy normalization, and the staged diff has no line-ending-only changes.

Paths in the following table are relative to the repository root or to `backend/src/main/java/com/lms/backend`, `backend/src/main/resources/static/shared`, or `backend/src/test/java/com/lms/backend` as indicated by their prefix or file type.

| File | Step 1 resolution |
| --- | --- |
| `ADMIN_README.md` | Conflict resolved to the Stage 9 guide. The separate Client guide retains the Client-specific material. |
| `README.md` | Retained the Stage 9 guide text and linked the Client and Delivery guides. |
| `backend/pom.xml` | Retained Admin's ZXing dependency and added Client's Stripe sandbox SDK dependency. Runtime Stripe activation is still opt-in. |
| `shared/database/CustomerOrderRepository.java` | Retained Admin queries and added Client's existing ownership-scoped row lock, payment row lock and expiry lock with `SKIP LOCKED`. |
| `shared/database/Inventory.java` | Resolved to the Stage 9 canonical entity, including `productID` and `updatedTime`. |
| `shared/database/InventoryRepository.java` | Kept Admin's release SQL and added Client's ordered pessimistic lock using canonical `productID`. |
| `shared/database/Product.java` | Resolved to the Stage 9 canonical entity, including `productID`, `SKU` and `SharedListingStatus`. |
| `shared/database/ProductRepository.java` | Kept Admin's catalogue queries and adapted Client's product lock and catalogue query to canonical fields and enum. Client JSON field names are preserved. |
| `shared/login/SharedSecurityConfiguration.java` | Resolved to the Stage 9 role restrictions for HTML and APIs, session filters and CSRF handling. |
| `shared-common.js` | Used the Stage 9 helper as the base and manually added Client's optional fifth argument and `Idempotency-Key` header. CSV responses retain the original response, content and filename. Multipart upload retains the browser-generated boundary. |
| `shared-welcome.html` | Added Client's portal link to the Stage 9 page frame. |
| `shared-welcome.js` | Shows the portal link after an eligible Client session check and keeps the Stage 9 role-specific return link and denial feedback. |
| `shared/value/SharedPlaceholderWord.java` | Removed the duplicate `RELEASED` constant declaration introduced by the merge. The stored value is identical. |
| `client/order/ClientOrderService.java` | Adapted product and inventory field names and the active listing enum. |
| `client/order/ClientOrderReservations.java` | Adapted canonical lock calls and the inventory key. Writes `updatedTime` explicitly to preserve Client's previous timestamp behavior with the Admin mapping. Reservation amounts, release predicates and transaction boundaries are identical to M10. |
| `client/product/ClientProductService.java` | Passes the canonical active listing enum. |
| `client/order/ClientOrderApiTest.java` | Updated fixture properties, enums and required timestamps. Cancellation, checkout and reservation assertions are retained. |
| `client/product/ClientProductApiTest.java` | Updated fixture properties and timestamps. Retained assertions on the Client API's `productId` and `sku` JSON fields. |
| `AdminUserFlowCheckTest.java` | Updated the expected start page after changing a Warehouse account to a Delivery role. The assertion that the previous session was revoked still runs. |

The ordinary Git merge separately brings in Client M10's portal, backend, tests and database changes from its parent. The following comparisons specifically verify the financial files.

| File | Baseline verified against the staged result |
| --- | --- |
| `admin/placeholder/AdminOrderCancellationTemporary.java` | Byte-identical to the integration first parent. |
| `admin/placeholder/AdminRefundTemporary.java` | Byte-identical to the integration first parent. |
| `shared/database/PaymentRepository.java` | Byte-identical to the integration first parent. The Admin lookup still requires positive amount and a non-null acceptance timestamp and locks the newest result. |
| `client/order/ClientOrderReleaseService.java` | Byte-identical to Client M10. Its existing ownership, expiry, cancellation and reservation behavior is imported as delivered. |
| `client/order/ClientCheckoutService.java` | Byte-identical to Client M10. Its existing settlement and late-capture refund handling is imported as delivered. |
| `shared/database/CheckoutPaymentRepository.java` | Byte-identical to Client M10. The existing Client-specific query and refund recording path still coexist with Admin's. |

## Regression evidence on 22 November 2026

Maven test compilation ran first on Windows with Java 21. It compiled 282 production files and 37 test files successfully.

| Command or check | Environment and result |
| --- | --- |
| `mvnw.cmd -B -ntp test-compile` | Java 21.0.13.0.2, successful. |
| `mvnw.cmd -B -ntp "-Dtest=*RuleCheckTest,Client*Test,BackendApplicationTests" test` | Loopback-only disposable PostgreSQL 17.11. **320 passed**, including 175 Admin/Shared rules, 144 Client application tests and one application context test. Zero failures, errors or skips. |
| `mvnw.cmd -B -ntp "-Dtest=StripeSandbox*Test" test` | Applied optional 05 on the disposable database and used mock provider responses. **18 passed**, zero failures, errors or skips. |
| Disposable schema startup | Applied 01, 03, 04, 06 and 07 with `ON_ERROR_STOP`. Counted **32 public tables** before applying optional 05. |
| Node shared HTTP-helper harness | Passed CSV download metadata and bytes, multipart body, JSON idempotency header and CSRF assertions against the merged `shared-common.js`. |
| `node --test --test-reporter=tap <file>` for each existing Client suite | Node 22.14.0 and headless Chromium. **64 passed**, zero failures, six live cases skipped. Browser fixtures use local mock responses. The initial restricted browser run was stopped after headless Chrome failed to start. |
| `docker compose -p lms-20261122-admin-flow-731c9add -f compose.yaml run --rm --build flow-check` | Before the test-only correction, two full runs each produced **198 passed, one failed, six skipped** in the HTTP flows. The independently counted 175 rule checks all passed. S2-T09 expected `/shared/shared-welcome.html` after the user became a Delivery Worker and received the intended `/delivery/index.html`. The detailed replay log is `backend/target/step1-admin-flow.log`. |
| Focused Admin HTTP rerun after test compilation | Fresh project `lms-20261122-admin-redirect-d7a88844`. Selected `SharedSetupFlowCheckTest#checkS0T02`, `SharedLoginFlowCheckTest#checkS1T01+checkS1T02+checkS1T03+checkS1T05`, then `AdminUserFlowCheckTest,SharedRoleProtectionFlowCheckTest`. **37 passed**, zero failures, errors or skips. S2-T09 passed alongside all 26 Admin user cases and six shared role cases. Log at `backend/target/step1-admin-redirect.log`. |

## Client merge step 2

The integration worktree was at the reviewed Step 1 commit `d2f4a89ed9f26d0f03e292e2f6f807fee086e7bd` with a clean tracked tree and only the two existing integration documents untracked. Applied the approved 17-file patch with SHA-256 `3765c48e3ac615e886169f4bff1923e54e01b372f922fd50a5359f66c183211c`. The patch passed whitespace and applicability checks, and a reverse applicability check verified the applied contents before the regression fix. Step 2 received local commit approval after the regression review and final source comparison.

### Payment, refund and stock invariants

- Client owner cancellation and checkout acquire the same PostgreSQL order row lock as provider settlement. Expiry uses `SKIP LOCKED` and preserves deadline, fulfilment and legacy unlinked reservation checks. Admin case cancellation uses its existing role and lifecycle rules. Cancellation closes pending entry while retaining payment attempts for later provider checks.
- Client cancellation and expiry release each eligible held reservation once in the same transaction as the order state and movement. The strict reserved-balance decrement requires an existing inventory row with enough reserved units. A `HELD` reservation with a release or consumption timestamp causes rollback if it reaches the shared Client mutation. The Client lifecycle guard rejects consumption evidence before the mutation. Movements use `RESERVATION_RELEASED` with zero physical delta. The expiry actor remains nullable. The paid-cancellation transaction also rolls back if an existing refund amount conflicts with the requested amount.
- Admin case actions retain the Stage 9 newest-positive-timestamped-payment rule. Client cancellation records an initial `REQUESTED` obligation for each distinct positive timestamped payment, holding the order lock and acquiring payment row locks in payment ID order. Matching refund history suppresses another initial request, and Admin's existing provider status and confirmed-failure retry paths still control advancing attempts.
- A status-only `ACCEPTED` claim cannot authorize a refund. It blocks new checkout and unsafe expiry and is placed into `RECONCILIATION_REQUIRED` during cancellation, eligible expiry or provider reconciliation. The Client tracking view labels these claims as under review. The Stripe sandbox sweep checks linked claims without expiring their sessions as pending checkout entry. An active order stays in its existing fulfilment state even when a capture is verified until the order and payment history are reconciled.
- Late, duplicate or misapplied capture obligations require verified provider evidence, including the bound sandbox session, succeeded PaymentIntent, currency, captured amount and unique provider payment reference. Settlement re-reads the attempt under the order lock. Initial refund recording locks its payment row and reuses matching refund history. Late capture leaves a cancelled order cancelled and does not add a fulfilment acceptance timestamp. `REQUESTED` rows carry no completion timestamp or provider refund reference. All provider regression tests use mocks.
- Warehouse and Delivery implementations, custody transitions and physical stock ownership are outside the Step 2 diff. Physical stock consumption remains at Delivery pickup.

### Legacy risks and operational scope

**Admin inventory clamping.** The Stage 9 Admin case path still applies `GREATEST(0, reserved_stock - quantity)`. If the stored balance is smaller than the reservation, the released reservation and nominal ledger delta can exceed the stock actually decremented. This can conceal a historical data inconsistency. The strict Client path rolls back that case. Changes to the Admin policy require separate approval.

**Active-order reconciliation is a runbook.** [Payment reconciliation](STEP_2_PAYMENT_RECONCILIATION.md) documents the authorized Admin review, payment evidence review and separately approved corrective transaction. Step 2 does not implement an Admin API or execute a manual correction. Active ambiguous payments keep their reconciliation marker and order state.

### File-by-file source changes

| File | Change |
| --- | --- |
| `AdminOrderCancellationPlaceholder.java` | Adds the shared Client/expiry entry point with a nullable actor. |
| `AdminOrderCancellationTemporary.java` | Shares the Stage 9 cancellation routine, closes pending checkout entry, flags timestamp-less claims and preserves the Admin clamp alongside the strict Client guard. Flushes the Client order and reservation mutations before JDBC reads in the same transaction. |
| `AdminRefundPlaceholder.java` | Adds internal entry points for timestamped Client payments and verified captures. |
| `AdminRefundTemporary.java` | Reuses the Stage 9 request writer, locks each payment, verifies capture state/reference and prevents duplicate initial obligations. |
| `ClientCheckoutService.java` | Keeps provider verification outside the database lock, checks for unresolved claims, preserves the active legacy marker and routes verified late captures to the shared refund writer. |
| `ClientOrderReadService.java` | Exposes the review status for timestamp-less acceptance in tracking. |
| `ClientOrderReleaseService.java` | Delegates owned pre-picking cancellation and automatic expiry to the shared service and preserves repeat cancellation behavior. |
| `StripeSandboxCheckout.java` | Passes the reconciliation mode selected for each linked session. |
| `StripeSandboxRepository.java` | Includes flagged linked payments in the sweep and scopes expiring open sessions to pending checkout. |
| `CheckoutPaymentRepository.java` | Splits acceptance claims from refund evidence and removes the separate Client refund insert. |
| `InventoryRepository.java` | Adds a guarded exact decrement for Client cancellation and retains Admin's existing decrement. |
| `PaymentRepository.java` | Adds the locked per-order query for all positive, timestamped payments. Preserves the existing newest-payment Admin query. |
| `SharedPlaceholderWord.java` | Adds `RECONCILIATION_REQUIRED`. |
| `order-details.js` | Displays the reconciliation state as ?Payment under review.? |
| `ClientOrderApiTest.java` | Exercises inconsistent reservation rollback, active legacy capture, multiple eligible payments, history conflicts and concurrent late capture replay. Keeps the repeated-cancellation assertion and aligns the existing cancellation movement checks with the shared movement name. |
| `StripeSandboxCheckoutTest.java` | Verifies that a signed paid event without a succeeded capture cannot create a refund, and that a verified sweep and later replay record one requested obligation. |
| `STEP_2_PAYMENT_RECONCILIATION.md` | Records the manual workflow and the separate Admin and Client refund selection policies. |

### Step 2 regression evidence

The full runs were executed on 22?23 November 2026 (Asia/Shanghai).

| Suite or command | Passed | Failed | Errors | Skipped | Evidence |
| --- | ---: | ---: | ---: | ---: | --- |
| `mvnw.cmd -B -ntp test-compile` | ? | ? | ? | ? | Java 21.0.13.0.2 compiled 282 main and 37 test sources. Exit 0. `backend/target/step2-final-compile.log`. |
| `mvnw.cmd -B -ntp "-Dtest=*Test,*Tests,!*FlowCheckTest" test` | 347 | 0 | 0 | 0 | All non-flow Maven/JUnit tests, including 19 Stripe mock tests. Fresh loopback PostgreSQL 17.11. Migrations 01, 03, 04, 06 and 07 yielded 32 public tables. Optional 05 yielded 34. Exit 0. `backend/target/step2-final-junit.log`. |
| Full Admin and Shared HTTP flows | 199 | 0 | 0 | 6 | `docker compose -p lms-20261122-step2-final-admin-71792bc0 -f compose.yaml run --rm --build flow-check`. The stateful suite used its own disposable `lms_flow_check` database, and the six existing suite skips remained in place. Exit 0. `backend/target/step2-final-admin-http.log`. |
| Admin and Shared rule rerun within the Compose command | 175 | 0 | 0 | 0 | These rules also ran in the 347 non-flow tests. |
| Client Chromium suites | 64 | 0 | 0 | 6 | Node 22.14.0 and Chrome 157.0.8103.79. Ran `node --test --test-reporter=tap <file>` once per existing suite. The six cases requiring a live backend were skipped during the fixture run. Exit 0. `backend/target/step2-chromium.log`. |

The full Admin/Shared HTTP suite also completed before the flush fix with the same 199 passes and six skips. The table records the rerun on the final Java source. The initial non-flow failure is recorded in `backend/target/step2-junit.log`.

The initial non-flow run had 346 passes, one failure and no skips. `ClientOrderApiTest.ownerCanCancelTwiceWithOnlyOneRelease` observed the balance decrement and ledger insert, then read the reservation through JDBC before Hibernate had written its in-memory `RELEASED` state. The fix flushes the shared Client cancellation mutations in the caller's transaction. The test assertions stayed intact. The Java-only fix does not affect the Chromium fixture inputs.

Surefire XML confirmed **19 passes** across the following named regression groups.

| Test group | Cases passed |
| --- | ---: |
| `ownerCanCancelTwiceWithOnlyOneRelease`, `expiresDueOrdersAndBarsPaymentAtTheDeadlineBeforeTheSweep`, `concurrentCancellationAndExpiryReleaseAllProductsOnce` | 3 |
| `rollsBackEarlierReleasesWhenAnotherProductHasInconsistentStock`, `inconsistentHeldReservationLeavesOrderInventoryAndHistoryUnchanged`, `inconsistentRefundHistoryRollsBackCancellationAndReservationRelease` | 4 |
| `lateCaptureCreatesOneFullRefundObligationWithoutRestoringStock`, `concurrentReplayedLateCaptureRecordsOneRequestedRefund`, `concurrentCaptureAndCancellationLeaveOneRefundAndRelease`, `captureAfterStrictOrderDeadlineOrCancellationCreatesOnlyARequestedRefund` | 7 |
| `statusOnlyAcceptanceEntersReconciliationUntilACaptureIsVerified`, `verifiedLegacyCaptureOnActiveOrderStaysInReconciliation`, `signedPaidEventWithNoCaptureEvidenceCannotRefundAStatusOnlyPayment` | 4 |
| `clientCancellationRecordsEachTimestampedPaymentExactlyOnce` | 1 |

Full migration versioning, Warehouse import and wiring, live Delivery pickup, physical returns and integrated cross-role acceptance remain open.

## Warehouse backend and Pickup List handoff

The Warehouse picking and packing checkpoint is `98a35e1d5818d3a8e61a2f3f4294ff5de2fe5b10`, built on the Step 2 commit `41dc8c26d5bf99a170af235be18640a8141f2252`. Fresh PostgreSQL installations use migrations 01, 03, 04, 06, 07 and 08 for 33 public tables. Optional Stripe fixture migration 05 brings the total to 35. Existing databases need migration 08 applied explicitly.

Confirm Packing commits the package and the `READY_FOR_PICKUP` order state with their reservations still `HELD`. A Warehouse worker can then use these authenticated operations.

| Operation | Route under `/api/warehouse/orders/{orderReference}` |
| --- | --- |
| Read the package QR payload or PNG | `GET /package` or `GET /package/qr-code` |
| Create a Pickup List | `POST /pickup-list` with a UUID `requestId` and the shared CSRF token |
| Read the active Pickup List or its QR PNG | `GET /pickup-list` or `GET /pickup-list/{listId}/qr-code` |

Picking, packing and package labels are available with the default profile. Pickup List operations require `delivery-full`. The handoff takes Delivery's transaction advisory lock before the package and order row locks. Before creating a list, it verifies committed packing, Warehouse custody, an address, no return history and no active list for the package. It also reuses the picking and collection payment gate, which requires one positive, timestamped accepted payment, exact payment-linked held quantities and clear payment and refund history. Delivery's command store saves the creation response in the same transaction as the list and audit event. The same request ID returns the saved result, and a second request for a package with an active list is rejected. Shipping and stock consumption occur at actual Delivery collection.

| File | Handoff change |
| --- | --- |
| `WarehousePickupListController.java` and `WarehousePickupListService.java` | Add profile-gated creation, active-list lookup and order-scoped QR PNG. Reuse shared identity, Delivery assignment and replay handling, and the shared fulfilment eligibility check. |
| `WarehouseController.java` and `WarehouseOrderService.java` | Expose the packed package QR payload and PNG and share the existing QR encoder. |
| `WarehouseApiTest.java` and `WarehouseDeliveryApiTest.java` | Exercise both profile configurations and committed packing, Warehouse role protection, list creation and replay, payment rejection, collector claim, one stock movement after replay, and Client tracking and Admin read access. |

Warehouse browser pages, physical return receipt and redelivery remain open. The Step 2 Admin inventory-clamping risk and manual payment reconciliation runbook still apply.

### Handoff regression evidence

| Suite | Passed | Failed | Skipped | Log |
| --- | ---: | ---: | ---: | --- |
| Maven/JUnit on isolated PostgreSQL 17.11 | 383 | 0 | 0 | `backend/target/warehouse-handoff-junit.log` |
| Full Admin/Shared HTTP flow suite | 199 | 0 | 6 | `backend/target/warehouse-handoff-admin-http.log` |
| Admin/Shared rule rerun within the Compose suite | 175 | 0 | 0 | Included in the Maven/JUnit total above |
| Client Chromium fixtures | 64 | 0 | 6 | `backend/target/warehouse-handoff-retry-chromium.log` |

Maven test compilation passed first. The 36 Warehouse JUnit executions include both Delivery profile configurations. Chromium fixture testing completed in Edge after Chrome's temporary DevTools startup file was locked. Six Chromium cases require a live Client backend. The six Admin/Shared skips are the existing flow-suite skips.

The isolated loopback-only Delivery Compose overlay passed the live handoff using a synthetic accepted payment with demo mode disabled. The real Warehouse session packed the order and created the list in separate HTTP requests. Delivery claimed and collected the package, and a replay returned the saved response across HTTP transactions. Client tracking reached SHIPPED and the authenticated Admin order read succeeded. PostgreSQL recorded physical stock 8, reserved stock 0, one consumed reservation, one list item, one PICKUP movement and one custody event. The log is `backend/target/warehouse-handoff-live.log`.

## Warehouse frontend milestone

The frontend builds on Pickup List checkpoint `5183d8a14cd4af4f5f8605bb6303cb29b1162bd6`. It ports Available orders, My orders, Notifications, All orders history, Picking task, and Scan log. The six pages use the integrated shared CSS, login, session, logout, and CSRF helpers. Available orders is the Warehouse Worker start page for login, session, and forced-password completion. The supplied offline pages, sample script, and replacement shared styles stay outside the deployed Portal.

Warehouse queue, summary, notification, order detail, and scan activity views render API data. Picking submits the exact product SKU. A scan, reversal, or list creation with an uncertain response can be retried with its original request ID after a same-tab refresh. Intentional new scans get their own IDs. The browser requires a reversal reason and displays the server's task state after writes. Confirm Packing and Create Pickup List are separate operations. Saved order, package, and Pickup List labels link to their distinct authenticated PNG routes. The package label is available even when `delivery-full` is disabled.

**Existing databases must apply migration 08 before deploying this integration.** The default disposable schema used migrations 01, 03, 04, 06, 07, and 08 and contained 33 public tables. Adding optional mock Stripe migration 05 produced 35. Warehouse frontend changes preserve the approved payment and refund rules, and Delivery collection continues to own stock consumption. Physical return receipt and redelivery remain separate work.

| Regression suite | Passed | Failed | Skipped | Evidence |
| --- | ---: | ---: | ---: | --- |
| Focused Warehouse and start page JUnit | 40 | 0 | 0 | `backend/target/warehouse-frontend-focused-junit.log` |
| Full Maven/JUnit in disposable PostgreSQL | 383 | 0 | 0 | `backend/target/warehouse-frontend-junit.log` |
| Full Admin/Shared HTTP flows | 199 | 0 | 6 | `backend/target/warehouse-frontend-admin-http.log`. Also reran 175 rule checks with no failures or skips. |
| Chromium browser fixtures | 70 | 0 | 6 | `backend/target/warehouse-frontend-chromium.log`. Six new Warehouse cases passed. The six existing Client live-backend cases were skipped in fixture mode. |
| Live Warehouse Chromium journeys | 2 | 0 | 0 | `backend/target/warehouse-frontend-live.log` and `backend/target/warehouse-frontend-live-default.log` |

Maven test compilation passed. The live loopback Compose test used the actual Client order and checkout APIs, then recorded a synthetic acceptance in the disposable database with Stripe disabled. Authenticated Warehouse and Delivery browser sessions exercised the six views and the post-packing handoff. The PostgreSQL stock balance stayed 10 physical and 2 reserved through packing and list creation, then reached 8 physical and 0 reserved after collection with one pickup movement and custody event. The Client tracking and Admin order reads passed. The second live run used the default profile and verified the saved package label and unavailable Pickup List action after packing, with stock still held. The Admin inventory clamp and manual reconciliation runbook limitations remain in the checklist.

## Step 6 full end-to-end verification

The accepted frontend baseline was `0230d5f9de35ca5abec7fcb588b7f0ad4676740e`. The test started a real loopback Spring Boot HTTP server under `delivery-full` with a disposable proof directory and PostgreSQL 17.11. The default schema had 33 public tables after migrations 01, 03, 04, 06, 07, and 08. Optional sandbox migration 05 brought the count to 35.

The new `FullFulfillmentFlowCheckTest` uses the real Client checkout, webhook signature verification, binding checks, and settlement. The Stripe SDK transport is replaced with a mock. A signed event without a succeeded PaymentIntent left the order awaiting payment and created no refund. A verified synthetic HKD capture accepted one positive timestamped payment. Replaying the capture webhook retained one processed event and one Client payment notification.

| Check | Verified result |
| --- | --- |
| Lifecycle and tracking | PostgreSQL, Client tracking, and Admin order details agreed on `AWAITING_PAYMENT → READY_FOR_PICKING → PICKING → READY_FOR_PICKUP → SHIPPED → OUT_FOR_DELIVERY → DELIVERED`. Client progression and Admin attempt and proof counts reflected the completed delivery. |
| Picking and handoff | An unpaid order was hidden from Warehouse and had no picking task. A wrong SKU left the order ready for picking. Partial packing was rejected. Complete packing closed the assignment and left the package in Warehouse custody. A separate Pickup List replay returned the same list. A fresh request was refused while that list was active. |
| Inventory and duplicate pickup | The Client checkout held two units against its payment. Physical and reserved stock were 10 and 2 through packing, list creation, and claim. The two concurrent collect requests with the same ID returned the same result. A fresh request was rejected. One `PICKUP` ledger entry at −2 physical and −2 reserved took the balance to 8 and 0 and consumed the reservation once. Delivery completion left those balances intact. |
| Custody and proof | One `PICKED_UP` event placed the package with the assigned driver. The real upload accepted a synthetic PNG. The assigned driver could retrieve its exact bytes privately during the attempt. Other drivers, Clients, and Admin were refused. Completion required saved proof, emitted one `DELIVERED` custody event, and cleared the handler. The proof became inaccessible through the Delivery API once the attempt closed. |
| Notifications | The owning Client received one payment confirmation, one shipped notification, and one delivered notification. The assigned driver received one delivered notification. Replayed outcomes left the counts intact. |
| Authorization | Anonymous access, cross-role calls, missing CSRF, another Client’s tracking request, and an unassigned driver’s pickup and attempt actions were refused. |
| Cancellation versus first scan | The HTTP race returned one 200 and one 409 in each run. Cancellation won on the initial run and released once with one `REQUESTED` refund record. The scan won on the final run and retained the held reservation with zero refunds. |

| Run | Passed | Failed | Skipped | Evidence |
| --- | ---: | ---: | ---: | --- |
| Initial Step 6 | 1 | 1 | 0 | `backend/target/step6-fulfillment-junit.log` |
| Final Step 6 | 2 | 0 | 0 | `backend/target/step6-fulfillment-final-junit.log` |
| Focused Warehouse, Delivery, and mocked Stripe regressions | 55 | 0 | 0 | `backend/target/step6-eligibility-junit.log` |

The initial failure was the new test’s expectation that an unpaid scan would return 409. The Warehouse lookup deliberately omits awaiting-payment orders. The final test asserts 404 with `WAREHOUSE_ORDER_NOT_FOUND`, no picking task, and the unchanged order and inventory. No production implementation change was needed.

To replay the authenticated suite, provision a fresh loopback-only PostgreSQL database named `lms_step6_<eight hex characters>`, apply the six default migrations and sandbox migration 05, and set Spring’s datasource variables for that database. From `backend`, run `mvnw.cmd -B -ntp test-compile`, then `mvnw.cmd -B -ntp "-Dtest=FullFulfillmentFlowCheckTest" test`. Use a separate fresh database for the other regression suites.

## Step 7 final validation and handoff

Step 6 was committed locally as `cdbe0a44cfc6bbb263d1bfe8eff41f3b39ae36ad`. Final runs used disposable PostgreSQL 17.11 and loopback HTTP listeners. The signed fulfillment suite used a mocked Stripe transport. Browser journeys used synthetic paid fixtures.

| Final suite | Passed | Failed | Skipped | Log under `backend/target` |
| --- | ---: | ---: | ---: | --- |
| Maven/JUnit | 383 | 0 | 0 | `step7-final-junit.log` |
| Admin/Shared HTTP | 199 | 0 | 6 | `step7-final-admin-http.log` |
| Chromium fixture mode | 70 | 0 | 6 | `step7-final-fixture-mode.log` |
| Chromium with all Client live cases enabled | 76 | 0 | 0 | `step7-client-live-final.log` |
| Signed mock-payment fulfillment E2E | 2 | 0 | 0 | `step7-final-fulfillment-junit.log` |
| Live Warehouse browser E2E with both Delivery profiles | 2 | 0 | 0 | `step7-warehouse-live-final.log` and `step7-warehouse-default.log` |

Maven test compilation passed first. Admin/Shared HTTP also reran the same 175 rules included in Maven/JUnit, with zero failures or skips. The six planned HTTP skips were S0-T01, S0-T06, S0-T08, S0-T09, S3-T13, and S9-T07. All six live Client cases skipped in Chromium fixture mode passed in the live-enabled run. The live Client database verifier passed 16 persisted-data checks.

Clean migration counts after 01, 03, 04, 06, 07, and 08 were 25, 27, 28, 28, 32, and 33. Migration 06 added its event column and unique index. Optional 05 brought the count to 35. A populated synthetic pre-08 database used the unchanged Step 2 migrations. A PostgreSQL custom-format backup restored with its rows and sequence values intact. Applying migration 08 twice preserved all existing rows and sequence values. The upgraded catalog matched the clean reference after both schemas were restored to normalize PostgreSQL’s implicit cast formatting. Inserts verified the replay primary key and both foreign keys. Evidence is in `backend/target/step7-migration-rehearsal.log`.

The startup check verified `delivery-full`, the migration dependency, disabled demo mode and Stripe, loopback binding, and a dedicated proof volume. The full-profile browser journey completed delivery, retrieved proof as the assigned driver during the attempt, and found the proof file on the private Compose volume after delivery. The default-profile journey packed and opened its Package QR with stock held and Pickup List creation unavailable.

Two test-only adjustments were made. The live Client role test now checks that a Delivery account starts in its workspace and that refused Client pages lead to Shared Welcome with the access-denied notice. The Chromium harness retries a transient Windows lock on its DevTools startup file within its existing deadline. The final tests passed with their business assertions intact.

[Local startup and demonstration README](README.md) · [Final handoff checklist](INTEGRATION_CHECKLIST.md#final-validation-and-handoff)
