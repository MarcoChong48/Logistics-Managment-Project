# LMS integration checklist

## Integration lead

- [x] Verify local sources and both Warehouse ZIPs and relocate the clean integration worktree.
- [x] Merge Delivery v4 at `b324b614c8082b512a83d9a0dde989bd42112a68` and validate the Stage 9 rule suite and disposable database.
- [x] Resolve the Client merge's shared mapping, security and frontend conflicts. Run test compilation first, followed by regression tests.
- [x] Review the Step 2 changes and [full regression results](INTEGRATION_REPORT.md#step-2-regression-evidence) and approve the local checkpoint.

## Client and Admin

- [x] Confirm that the merge preserves existing Client order ownership, idempotency, checkout, order tracking and notification tests.
- [x] Preserve Admin Stage 9's role policies, shared mappings and cancellation and refund policy.
- [x] Share Client and Admin cancellation mutation under the order lock. Verify duplicate cancellation, expiry, late payment and rollback.
- [x] Verify that Client cancellation and the first Warehouse scan cannot both advance an order.
- [x] Verify late and duplicate captures, separate paid attempts and Admin refund selection and retry behavior on disposable data.
- [ ] Track the legacy Admin inventory clamp. A reserved balance below the reservation quantity can produce a ledger delta larger than the stock decremented. A change to this policy requires separate approval.
- [ ] Follow the [manual reconciliation runbook](STEP_2_PAYMENT_RECONCILIATION.md) for active legacy claims. Step 2 provides a runbook, with no implemented Admin API for clearing the marker or advancing the order.
- [ ] Define the authorized return-request path, address version and active instruction status. Test rejection and approval with a recorded physical receipt.

## Database and Payment

- [x] Verify default migrations 01, 03, 04, 06, 07 and 08 in a disposable database with 33 public tables. Existing databases must apply migration 08 before Warehouse picking.
- [x] Run optional migration 05 and module tests on disposable data with mocked provider responses. Keep Stripe disabled by default.
- [ ] Rehearse the ordered migration plan and version checks on a disposable copy of any intended existing schema. Verify the FK, unique and CHECK constraints before rollout.
- [ ] Confirm which provider record proves capture and settlement for each supported source. Reconcile timestamp-less `ACCEPTED` records before authorizing refund attempts.

## Warehouse and Delivery

- [x] Import Warehouse Java and wire the six operational pages to authenticated `/api/warehouse` endpoints with shared CSRF.
- [x] Verify Product SKU scanning, exact order-item quantities and the first-scan lock. Keep Order Reference, Package QR and Pickup List QR distinct.
- [x] Check accepted payment and all required held quantities before picking and packing. Verify one stock consumption at Delivery collection when a pickup is replayed.
- [x] Connect eligible packed packages to `DeliveryPickupService.createList` through an authenticated post-packing action. Verify assignment, claim and collection under `delivery-full`.
- [ ] Implement physical return receipt and inventory condition handling after the team approves the rules for quantity discrepancies and damaged goods.

## Integration testing

- [x] Run and record the complete Maven/JUnit, Admin/Shared HTTP and Chromium fixture suites for Step 2. The report records six Admin/Shared skips and six live-backend Chromium skips.
- [x] Run a live Client checkout to Warehouse to Delivery journey in PostgreSQL with authenticated sessions and a synthetic accepted payment. Verify marking a Warehouse notification read, package and pickup labels, collection and stock movement, Client tracking, and the Admin order read.
- [x] Verify assignment-scoped delivery proof access during an attempt and denial after delivery completes.
- [x] Run all six eligible live Client cases and both Warehouse browser journeys. Record recovery, cancellation, and role-denial results.

## Final validation and handoff

- [x] Verify the accepted integration baseline and run the signed mock-payment journey through Delivered on a disposable database. [Step 6 evidence](INTEGRATION_REPORT.md#step-6-full-end-to-end-verification)
- [x] Verify the Client and Admin views against stored statuses, payment evidence, stock and reservation movements, custody, notification recipients, and the completed delivery attempt.
- [x] Verify one winner in the cancellation-versus-first-scan race, replay safety for pickup and delivery outcome, and role and proof-access restrictions.
- [x] Restore a populated synthetic pre-08 backup, rehearse migration 08, and verify schema, existing rows, sequences, and replay constraints.
- [ ] Before rollout, repeat the rehearsal on a disposable copy of the intended deployment database. Apply migration 08 to every existing database that will run this integration.
- [x] Verify the local `delivery-full` overlay, private proof volume, and disabled demo and Stripe settings.
- [ ] Verify deployment-specific profile, proof storage, and provider configuration before rollout.
- [ ] Review the legacy Admin inventory-clamping risk and the manual payment reconciliation runbook with the handoff team.
- [ ] Keep physical return receipt and redelivery as separately approved workflows.
- [x] Prepare the [startup and demonstration README](README.md) and record the [final test results](INTEGRATION_REPORT.md#step-7-final-validation-and-handoff).
