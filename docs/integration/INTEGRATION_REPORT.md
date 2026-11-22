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
