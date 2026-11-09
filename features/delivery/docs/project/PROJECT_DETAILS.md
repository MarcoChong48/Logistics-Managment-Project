# Logistics Management System — detailed project record

Last updated: 5 November 2026 (Asia/Hong_Kong).

Project reference for the Delivery source checkout. Relative links point to the feature documentation and its bundled references.

## 1. Purpose, scope of this record and status

This is the comprehensive internal context for the COMP 3500SEF grand group project. It consolidates the supplied logistics drafts, prototype review, course requirements and the user's confirmations in the current discussion. The concise starting point is [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md).

The group has nine computer science students. On 5 November the user authorized a standalone Delivery application, now implemented and locally verified with AI assistance. The overall LMS is not integrated, other role implementations are not established, and there is no deployed shared service. Payment-account setup and integration have not started; the provider/interface choice remains undecided for the shared system (U-16).

This file is not the final assessed report, meeting minutes or evidence that every member approved every refinement. Its length is not constrained by the assessed report's 1,000-word limit.

### 1.1 Confirmation labels and source authority

| Label | Meaning |
|---|---|
| Official course requirement | A requirement in the course introduction, project-topic brief or subsequent verified course instruction |
| Reported team-approved baseline | The user confirmed that the Word workflow baseline was approved by the team |
| User-confirmed refinement | A choice confirmed with the user in this chat on 28–29 October or 4–5 November 2026; a later explicit revision supersedes the earlier choice |
| Source proposal | A suggestion or example in a draft; approval is not established merely by its presence |
| Observed prototype finding | A fact from source/screenshot inspection; it is not a passed or failed runtime test |
| Undecided | An unresolved topic, identified by a U-number in the final section |

Official course sources govern assessment. For business behaviour, apply the reported baseline and the user's later refinements, preserving their separate provenance. Suggestions inside the Word baseline retain their suggested status. Its filename, working-draft wording and approval report do not make an expressly proposed architecture or sprint plan a settled implementation choice.

The initial Markdown is a pre-meeting draft, prepared on 4 October 2026 and referring to a proposed 5 October meeting. Its Group 54/Matthew metadata does not establish this chat user's identity, current roster or actual contributions. The Word file describes itself as a discussion baseline, has a Version 0.2 footer, and omits some explicitly undecided material. Omission must not be treated as agreement.

### 1.2 Strict separation from Assignment 1

AssignmentFiles, output and tmp, and the earlier chats in this Codex project, belong to Assignment 1. Use the separate Assignment 1 context for that work. Grand-project artifacts belong under GrandProject.

Do not import Assignment 1's systems, client/developer team split, business policies, specification/reflection limits, script dialogue or meeting outcomes into the logistics requirements. Shared course lectures remain relevant educational sources.

Use only the current Lectures 1–8 as course grounding. The unrelated EJS/Middleware/Session lecture and its cached extracts are excluded. Existing source files have not been rewritten as part of creating these records.

## 2. Business purpose and first-version boundary

### 2.1 Confirmed setting

The system serves one retailer selling its own products online and using its own warehouse and delivery staff. It is not a carrier booking transport for other merchants' goods.

User-confirmed setting:

- Hong Kong delivery and HKD.
- One warehouse; product-level stock totals. Shelf/bin labels can describe location without separate location quantities.
- Four application roles: Client, Warehouse Worker, Delivery Worker and Admin.
- One physical package per order, containing the complete order.
- A simple form for one or more products and quantities.
- Product total only; delivery is included.
- A client can order for a different recipient.

Hong Kong was proposed from the HKMU course context and the suitability of one local delivery area for the chosen warehouse model. The logistics drafts did not specify it. It became agreed scope through the user's confirmation, independently of Assignment 1.

An order total is the sum of its quantities multiplied by its recorded unit prices. No separate delivery charge is part of the confirmed first-version calculation. This is an application pricing decision, not a claim about legal or tax obligations.

### 2.2 First-version exclusions

The reported baseline excludes a full shopping cart/product-detail experience, wishlist, reviews, coupons, loyalty points and advanced shopping filters. It also excludes supplier/manufacturer accounts, supplier purchasing, separate customer-service and payment-operator roles, route optimization, GPS/live maps and warehouse automation.

Purchasing from suppliers remains outside the application. Recording received restock remains a Warehouse function.

In-system notifications are required. Email notifications are future work. Admin password reset uses manual handover, not email recovery.

No real payment collection is intended: use an external provider's test environment and retain the agreed test-card scope. Provider/interface selection is undecided (U-16).

## 3. Roles, permissions and information boundaries

| Role | Confirmed responsibilities | Information/action limits |
|---|---|---|
| Client | Account access; delivery defaults; create/pay/track own orders; eligible ordinary cancellation; post-return detail correction and requests; notifications | Own orders only; no staff scan/audit/security history, raw errors or internal delivery notes |
| Warehouse Worker | Picking queue and active work; product verification/corrections; packing; restock/count corrections; pickup-list preparation; collection-problem resolution; return receipt | No catalogue administration, client address/phone access or Admin/security logs; no editing/deleting recorded history |
| Delivery Worker | Claim a list; scan actual package collection; record inability to collect; start delivery per order; record outcomes/proof; return packages | Contact/address access only for delivery work being handled; no warehouse/Admin/security logs or ordinary worker-to-worker transfer |
| Admin | Catalogue/users/roles; bans/unlocks/resets; monitoring; shortage and client-request resolution; picking/delivery reassignment; logs | No original passwords, direct physical-stock editing, audit deletion or client delivery address/phone access |

Permissions must be enforced by protected backend operations as well as UI controls. Public account registration must not permit a requester to grant themselves staff/Admin powers.

Changing assignment does not prove physical possession. List ownership, task ownership, package custody and historical authorship must remain distinct.

Proof-view permissions and detailed media access: [UNDECIDED — see U-09 in Undecided ideas].

## 4. Client workflow and experience

### 4.1 Account and delivery defaults

Registration, login, logout and password change are required. Clients can save/edit default recipient, phone and address details for future orders.

Each placed order keeps its own delivery information. Editing defaults must not change existing orders. A different recipient is a delivery contact and does not require a fifth application role or a separate account.

Registration field/validation details: [UNDECIDED — see U-13 in Undecided ideas].

### 4.2 Create and review an order

1. Select one or more products and quantities.
2. Show enough product information for ordering: name/SKU, price and availability. Retain useful name search from the initial draft without expanding into full e-commerce.
3. Enter/review the recipient's name, phone and Hong Kong address, using saved defaults if desired.
4. Show the actual items, quantities, unit prices and HKD total before payment.
5. Create an AWAITING_PAYMENT order and show its reference and 24-hour deadline.

After an unpaid order has been created, changes to products, quantities or delivery details use cancellation and creation of a replacement unpaid order. This does not grant a general modification path for paid orders or remove the separately approved correction window for returned failed-delivery orders.

Order summaries and payment amounts must use server-side validated data. Repeated rows for a product must not evade aggregate stock validation.

The baseline proposes order address and item-price snapshots; these support preserving the placed order despite later defaults/catalogue edits. Exact data-model implementation is in U-05.

### 4.3 Payment and ordinary cancellation

Payment provider/interface decision: [UNDECIDED — see U-16 in Undecided ideas]. Use a provider test environment for one-time order payments. Section 7 defines the confirmed timing and reservation semantics.

Ordinary client cancellation is allowed in AWAITING_PAYMENT and READY_FOR_PICKING, before Start Picking. After PICKING begins, that direct cancellation path is unavailable.

Cancellation remains final. Payment and refund resolution can continue separately; the UI must not confuse an order cancellation with successful prevention of an in-flight charge.

### 4.4 Own orders, tracking and notifications

Clients need access to their own orders, actual ordered contents, recipient details, creation/reference information, payment deadline where applicable, full order progression and separate payment/refund progress.

Retain the draft's review/invoice before payment and receipt after verified success. A receipt shows the relevant order/payment information; a submitted refund request is not proof that money was refunded.

The client sees all main internal logistics stages, with friendly labels if useful. Important baseline notifications are payment/order confirmation, SHIPPED, DELIVERY_FAILED and DELIVERED. Do not issue a notification for every internal scan.

Failures show a standard reason, not the delivery worker's full internal note. Errors should give understandable feedback and a reference/error code; sensitive technical details belong in appropriate protected logs.

Additional UI/notification/receipt presentation choices: [UNDECIDED — see U-14 in Undecided ideas].

### 4.5 After a failed delivery

The package returns to Warehouse. After confirmed return and before Admin redelivery approval, the client may correct that order's delivery details and choose:

- Request redelivery, confirming or correcting the details.
- Request cancellation, with progress visible while Admin reviews it.

Tracking-page controls and verified manual customer-service fallback feed the same recorded instruction process. Customer service remains outside the application; there is no new customer-service role.

Admin needs both confirmed return and a recorded client instruction before approving redelivery. Merely receiving a package does not authorize another attempt.

Post-return cancellation requires Admin approval and is a controlled exception to the normal Start Picking cutoff. A pending cancellation request blocks redelivery approval. Cancellation requested, CANCELLED, refund processing and REFUNDED must remain distinct.

Details lock for the attempt once redelivery is approved. Updating this order does not change saved defaults automatically.

Manual verification and amendments to pending requests: [UNDECIDED — see U-08 in Undecided ideas].

## 5. Order states and independent status concepts

### 5.1 Main order progression

    AWAITING_PAYMENT
      -> READY_FOR_PICKING
      -> PICKING
      -> READY_FOR_PICKUP
      -> SHIPPED
      -> OUT_FOR_DELIVERY
      -> DELIVERED

| State | Meaning / ordinary entry |
|---|---|
| AWAITING_PAYMENT | Order created; no accepted verified payment yet |
| READY_FOR_PICKING | Payment accepted; waiting for picking |
| PICKING | Warehouse worker has started the task |
| READY_FOR_PICKUP | Complete quantities verified and packing explicitly confirmed |
| SHIPPED | The individual package has been accepted at collection; current handler recorded |
| OUT_FOR_DELIVERY | Worker starts this order's delivery |
| DELIVERY_FAILED | Failed delivery attempt recorded |
| READY_FOR_REDELIVERY | Return confirmed and client-requested redelivery approved |
| DELIVERED | Successful attempt with signature or photo proof |
| CANCELLED | Final cancellation; fulfilment must not restart |

The return-based retry sequence refines the original Word shortcut:

    OUT_FOR_DELIVERY -> DELIVERY_FAILED
      -> Warehouse receipt + client instruction + Admin approval
      -> READY_FOR_REDELIVERY
      -> individual pickup -> SHIPPED
      -> Start Delivery -> OUT_FOR_DELIVERY

Warehouse receipt and client instruction are required events/conditions; this notation does not invent additional main order statuses.

### 5.2 Cancellation paths

- Ordinary direct client cancellation: AWAITING_PAYMENT or READY_FOR_PICKING, before Start Picking.
- Strict unpaid/payment-confirmation deadline: final cancellation at 24 hours from creation for an order still lacking accepted payment.
- Shortage exception: Warehouse reports; Admin selects waiting or full cancellation/refund.
- Returned failed-delivery exception: client requests; Admin approves cancellation.

Cancellation and Start Picking require consistent state/ownership checks so both cannot succeed for the same order. No later payment message revives a CANCELLED order.

### 5.3 Separate concepts

Order status describes logistics. Payment status describes a checkout/payment result. Refund status describes refund progress. A list's collected/skipped outcome and a return receipt are not automatically new order statuses.

Payment/refund labels must reflect actual provider evidence. REFUND_PENDING and REFUNDED are not main order states.

Detailed mapping for pre-attempt returns, repair holds and exceptional reopenings: [UNDECIDED — see U-07 and U-10 in Undecided ideas].

## 6. Inventory and reservations

### 6.1 Confirmed normal accounting model

- Physical stock means recorded stock inside the warehouse, including picked/packed goods awaiting collection.
- Reserved stock is allocated to active checkout/order commitments through pickup.
- In the normal accounting case, available stock = physical stock minus reserved stock.
- Product scans and reversals alter picked progress, not those stock totals.
- Accepted package pickup reduces physical and reserved quantities together.
- A confirmed intact return still allocated to the order restores physical and reserved quantities together; availability does not increase.
- Restock increases recorded physical stock.

Example for three units:

| Event | Physical | Reserved | Available |
|---|---:|---:|---:|
| Initial | 100 | 0 | 100 |
| Reserve before checkout | 100 | 3 | 97 |
| Pick and pack | 100 | 3 | 97 |
| Package collected | 97 | 0 | 97 |
| Intact package received back, still allocated | 100 | 3 | 97 |
| Collected for retry | 97 | 0 | 97 |

A failed attempt alone does not restore warehouse quantities. The package must physically return and be received. Do not use ordinary supplier-restock recording to erase return/custody history.

### 6.2 Availability and warnings

Low-stock warnings use available stock, beginning with reservation, and trigger when it is below the Admin-set product threshold. At equality, no warning is triggered by that rule.

Released reservations and restocks can change availability. Packing alone does not. Do not carry over the old Markdown's scan-time stock deduction or the prototype's reservation-on-success wording.

### 6.3 Counts, shortages and damage

Warehouse counts include all relevant goods still inside, including staged packages. A genuine zero count must be recordable. Confirmed count changes are logged.

Warehouse reports a verified shortage and pauses affected work; Admin chooses restock waiting or full cancellation/refund. Before cancellation releases goods for new orders, their condition must be assessed and necessary stock corrections recorded. Damaged or missing units must not become available merely because a reservation is released.

The normal formula/table does not settle incomplete returns, unusable units or a count below existing reservations. Detailed damaged-stock/shortfall accounting and return inspection: [UNDECIDED — see U-06 in Undecided ideas].

## 7. Payment interface, deadlines, cancellation and refunds

### 7.1 Provider/interface placeholder and confirmed payment scope

Payment provider/interface decision: [UNDECIDED — see U-16 in Undecided ideas]

The confirmed scope remains an external provider's test environment, test cards only, and one-time full-order payments in HKD. The LMS never stores raw card numbers. Payment-account setup and integration have not started; no real payment collection is intended. Candidate interfaces and their compatibility checks appear exclusively in U-16. No provider or hosted/embedded checkout interface is currently selected.

The application rechecks stock and reserves atomically immediately before starting checkout. An unpaid order does not reserve goods merely by existing.

One active checkout per order. Repeated clicks retrieve/resume its progress rather than create another active charge opportunity. Provider messages must be verified and processed without duplicate business effects.

### 7.2 Individual card decline versus checkout ending

The required LMS behaviour distinguishes an individual card decline from an ended checkout. The user confirmed:

- Retain the same reservation while the client retries cards in the same active checkout.
- Give each checkout a 30-minute entry/retry window.
- Treat an unsuccessful checkout as releasable only after confirming it has ended unpaid.
- Before a fresh checkout, resolve/end the previous one, recheck stock and reserve again.
- An uncertain provider response is not a confirmed failure.

Do not release stock simply because a card-decline event arrived. Checkout progress and the actual payment result are distinct; a page return or completion message is not by itself the LMS fulfilment decision. Validate that the selected provider/interface can implement these rules; provider-specific status names and session expiry behaviour are not yet chosen.

The 30-minute window is not a promise that every uncertain payment resolves within 30 minutes. Reconcile processing/unknown outcomes; the final order-cancellation rules below still apply.

### 7.3 Strict 24-hour LMS confirmation deadline

The clock starts at order creation and is not reset by another checkout.

Payment must be verified by the LMS within 24 hours. The user explicitly selected this rather than accepting provider payment time alone. A payment made earlier but only confirmed to the LMS after final expiry is refunded.

The order deadline overrides a later checkout expiry. A session opened near the end does not extend it. Enforce deadline checks when starting checkout and accepting results, as well as through an expiry task.

This concerns the first accepted payment confirmation. An order whose payment was already accepted on time does not expire merely because it becomes 24 hours old. A duplicate result arriving later must not cancel that order, repeat fulfilment or initiate another refund. Preserve the accepted result and apply duplicate-effect protection.

### 7.4 Final cancellation while payment is unresolved

The user confirmed immediate final order cancellation for an eligible client cancellation, even while payment is unresolved:

1. Set CANCELLED and release its reservation consistently.
2. Block picking and new payment attempts for that order.
3. Try to end the provider checkout and continue reconciling its payment result.
4. If a successful charge is subsequently confirmed, initiate its full refund once.
5. Keep CANCELLED permanently; do not re-reserve or fulfil because of the late charge.

The UI can say that the order is cancelled while payment is still being checked. It must not assert that the provider charge was prevented or that a refund completed before confirmation.

The same final-cancellation principle applies to the strict 24-hour expiry.

### 7.5 Refund representation

Refund progress is separate from logistics. A pending or failed refund does not restore an order or its reservation. Do not show Refund Issued/Refunded solely because an application request was created.

Full refunds are explicit for late successful charges and the agreed shortage full-cancellation path. Detailed approval/exception/retry handling and any unqualified post-return refund conditions: [UNDECIDED — see U-15 in Undecided ideas].

## 8. Warehouse workflow

1. See the available READY_FOR_PICKING queue and assigned active work.
2. Start Picking, recording task ownership and entering PICKING.
3. Open the appropriate task and verify product QR identifiers, with manual entry as a fallback.
4. Reject wrong products and excess quantities without increasing picked counts.
5. Retain accepted/rejected scans and any appended reversals. Before Confirm Packing, an accepted scan can be reversed; counts cannot become negative. The baseline does not require a reason for ordinary prepacking scan reversal.
6. Confirm Packing only when every line exactly matches. The last scan alone does not finish packing. No second-person checker is required.
7. Lock ordered contents and make the complete single package ready for pickup.
8. Record restocks/count corrections, see availability-based low-stock warnings, create pickup lists and handle reported collection problems.
9. Confirm returned packages and report discrepancies.

Start Picking exclusively claims an eligible available order. Only the current picking-task owner may scan, reverse scans or Confirm Packing; backend checks must prevent simultaneous claims and unauthorized changes.

Warehouse return receipt includes checking the actual received quantities and condition. Exact discrepancy controls/accounting remain open in U-06; the need to check the returned contents is required behaviour.

Accepted progress must be persisted as it is confirmed. Logout or inactivity expiry must not discard previously accepted scans as though they were unsaved form data.

### 8.1 Picking-task reassignment

User-confirmed: Admin authorizes reassignment to another Warehouse worker.

Preserve the task's progress, scan/reversal history and original actors. Record the transfer. Remove the old owner's modification permission. The new worker checks the physical order label/items/counts against saved progress before resuming. Reassignment itself changes no inventory.

This is not a new compulsory second-person packing check.

### 8.2 Packed-package problems

For damaged outer packaging with intact goods, Warehouse can repair/repack without changing the ordered products/quantities or erasing completed history.

Damaged/missing goods follow the shortage process. Ordinary postpacking scan reversals must not conceal an issue or bypass the packing lock.

Controlled replacement verification after Admin chooses waiting: [UNDECIDED — see U-07 in Undecided ideas].

## 9. Pickup-list preparation and collection

### 9.1 Ownership and actual pickup

Warehouse selects ready packages and creates a pickup list and its QR.

No normal delivery pre-assignment is required. The first Delivery worker to claim the list is its one collector. Claiming the list alone changes neither order status nor inventory nor actual package custody.

Each accepted package scan separately verifies list membership/eligibility, records worker/time/current handler, sets SHIPPED and applies the agreed stock movement. Reject duplicate or wrong-list scans.

Finishing pickup retains collected and uncollected history. Uncollected packages remain in Warehouse and become eligible for another list when released. Reject duplicate collection within the same pickup/custody cycle. An authorized pickup after confirmed return creates a new cycle and must remain possible, with prior collection history preserved.

Abandoned-list release, detailed edit/closure rules and recovery cases: [UNDECIDED — see U-10 in Undecided ideas].

### 9.2 Cannot collect

The worker can record Cannot collect, with a reason, before accepted pickup. Preserve the list entry and its outcome rather than deleting evidence.

| Cause | Agreed/refined handling |
|---|---|
| Vehicle full / insufficient capacity | Remains in Warehouse with reservation retained; release for another list |
| Packaging damaged, contents intact | Block collection until Warehouse resolves packaging |
| Goods damaged/missing | Warehouse verifies; use the Admin-controlled shortage process |
| Other | Explain the issue for review; do not silently discard the package |

This is not a failed customer delivery attempt.

### 9.3 Problem after recorded pickup but before delivery

Use a separate recorded return. Warehouse confirmation is required before restoring warehouse quantities/custody. Retain the original pickup. Do not fabricate an unsuccessful customer visit merely to fit a return into the system.

Exact queue/state mapping and exceptional physical reassignment: [UNDECIDED — see U-10 in Undecided ideas].

## 10. Delivery, failures and redelivery

Start Delivery changes only the selected order from SHIPPED to OUT_FOR_DELIVERY.

Success requires at least one proof method: client signature or photo. Attach proof to the particular DeliveryAttempt.

The baseline failure reasons are Client not home, Wrong address, Client refused, Unable to contact client, and Other. A note is optional for normal reasons and required for Other. A failure photo is not required.

Store the worker, time, outcome and reason/note. Notify the client using the standard failure reason without exposing the internal note. Repeated attempts are separate records, not overwrites.

### 10.1 Return and client choice

Failed packages return to Warehouse before redelivery. Recording failure alone does not restore stock.

Admin approval follows confirmed Warehouse receipt and the client's recorded instruction. The client can correct details in the return-to-approval window. Redelivery approval locks those details for the attempt and leads to a new recorded pickup before delivery starts.

Admin may reassign delivery responsibility, retaining old/new history. No ordinary delivery-worker-to-worker handover feature is agreed. A changed username must not falsely establish custody.

Attempt limits, no-response outcomes and final closure: [UNDECIDED — see U-12 in Undecided ideas].

## 11. Admin, catalogue and account lifecycle

### 11.1 Confirmed functions

- Add/view/edit products, prices, product identifiers and low-stock thresholds; control listing status.
- View physical stock read-only. Warehouse remains responsible for stock corrections/restocks.
- Add/manage accounts and roles; ban/unban; reset passwords; unlock early.
- Monitor order statuses, separate financial states and operational exceptions.
- Choose shortage waiting or full cancellation/refund.
- Review post-return redelivery/cancellation requests.
- Reassign Warehouse picking and delivery responsibilities with history.
- View protected operational/security/picking records without editing/deleting them.

### 11.2 Removal means deactivation

The user selected deactivation for removed products and users, preserving order and audit history. Do not implement ordinary destructive record deletion as Remove.

Precise effects on already-created orders, active sessions and assigned work: [UNDECIDED — see U-11 in Undecided ideas]. Protected actions must still enforce current authorization.

### 11.3 Temporary-password reset

Retain the reported baseline:

- Admin generates a single-use temporary password.
- It expires after 24 hours and requires password change at first login.
- An unused expired credential needs another Admin reset.

User-confirmed delivery method: show the newly generated value once to Admin, then use verified manual handover. Do not provide retrieval of original passwords or later redisplay from plaintext storage. Never put temporary or ordinary passwords into logs.

Manual identity-check procedure: [UNDECIDED — see U-08 in Undecided ideas].

## 12. Security, logging and retention

### 12.1 Reported baseline rules

- Passwords are hashed, not reversibly encrypted.
- Minimum eight characters, with uppercase, lowercase and a number.
- Normal password change uses current password, new password and confirmation.
- Successful password change invalidates active sessions and requires login again.
- Five consecutive failed logins lock the account for 15 minutes; automatic unlock follows, with Admin able to unlock earlier.
- Fifteen minutes of inactivity expires the session. Unsaved forms may be discarded; previously confirmed business records must remain.
- Record relevant login, lock/unlock, reset/change and security events with timestamp, result, IP address and device/browser information, without passwords.
- Enforce backend roles and ownership. Hiding a button is insufficient.
- Do not expose client/recipient addresses or phones through Admin tables, raw log values or free-text audit copies.
- Do not store raw card numbers.

These are the team's reported policy choices, not a claim of certification or that every value is a current industry standard.

### 12.2 Retention in the baseline

| Record | Baseline period | Access/handling |
|---|---|---|
| Operational audit | 3 years | Admin-readable; no ordinary application edit/delete |
| Security log | 90 days | Admin-readable; no passwords |
| PickingScanHistory | 90 days | Detailed history Admin-only; worker receives current-task progress/error feedback |
| DeliveryAttempt and its proof | 1 year | Baseline says delete proof with the attempt at expiry |

Important successful operational changes are audited. Sensitive rejected Admin actions and failed logins enter relevant security logs. Rejected product scans belong in scan history.

Retention cleanup versus retained client timeline/audit summaries and media access: [UNDECIDED — see U-09 in Undecided ideas]. Append-only through the user interface does not mean records are retained forever.

## 13. Technology and system data

### 13.1 Confirmed direction and planned integrations

Java/Spring Boot backend, role-based browser interfaces, relational storage, an external payment-provider test integration and camera QR scanning with manual entry.

Payment provider/interface decision: [UNDECIDED — see U-16 in Undecided ideas].

Product QR verifies goods; pickup-list QR identifies the batch; package QR identifies custody/return events. These purposes must not be confused.

A local Delivery backend is now established; no shared integrated backend, payment account or deployed environment is established. The original prototypes' backend-connected and security assurances remain unsupported by those prototype files.

Architecture/technology decision: [UNDECIDED — see U-05 in Undecided ideas].

### 13.2 Data that the agreed behaviour needs to preserve

Preserve order ownership/reference/time/deadline, actual ordered products and quantities, recorded prices, order-specific recipient/contact/address, payment/provider references/results, reservations and stock movements, picked quantities, scan/reversal history, package pickups/returns/custody, separate delivery attempts/proof, client instructions, assignments, notifications and audit/security events.

These are behavioural information needs, not a final schema or public API contract. Preserve original actors and prior events instead of overwriting history. Sensitive data visibility remains constrained.

Detailed data-model/interface decisions: [UNDECIDED — see U-05 in Undecided ideas].

### 13.3 Database Draft v0.4 review

On 4 November 2026 the user supplied a member's `LMS_Database_Draft_v0_4.md` and Overview/Detailed ERD PNGs from Downloads. The draft explicitly states it is not final/team-approved. Review does not adopt its tables, optional vehicle/equipment fields or remaining proposals. No SQL schema, migration or database implementation was supplied, and the three attachments do not include a separate workflow diagram.

The [review record](../database/LMS_Database_Draft_v0_4_Review.md) distinguishes definite diagram errors from implementation-specification gaps and unresolved policy. Findings include reversed/inconsistent relationship endpoints, rejected-scan representation, durable picking progress, return inspection/custody records, approval/address/return linkage, payment timing and duplicate-effect safeguards. Suggested changes in that review remain proposals until confirmed; U-01/U-02 and U-05 through U-16 are not closed by the draft.

The existing comparison Word document and illustrated PDFs are earlier snapshots and have not been regenerated for the 4 November process update or this database review.

### 13.4 Delivery implementation — 5 November 2026

The user explicitly selected PROJECT_DETAILS enhanced B plans as the implementation direction, rather than every literal questionnaire B answer. English UI and simple English comments are confirmed. A working local demo is authorized; unfinished team connections must carry the exact label Place holder and explain the actual content/owner needed later. This authority applies to Delivery implementation, not evidence that all nine members approved all global policies.

The source checkout keeps Delivery under `features/delivery`. It builds the Java 21/Spring Boot 3.5.16 JSON backend and React 19.3.1/Vite 8.3.6 frontend into a runnable JAR. The private H2 file schema, demo accounts, packed Warehouse data, simulated Warehouse/Client/Admin handoffs, shared notification/refund transport, private cloud storage and hosting connections are clearly documented Place holders. Original HTML prototypes are preserved. [Delivery source guide](../../README.md)

Actual local operations include exclusive list claim, atomic per-package pickup/stock/custody updates, current-holder checks, separate attempts, private photo/signature upload, return reporting, version-bound retry gates, stale/review queues, safe history, retention and repeat-request checks. [Decisions](../DECISIONS.md), [integration boundaries](../INTEGRATION.md) and [verification](../TESTING.md)

## 14. Deployment

Deployment decision: [UNDECIDED — see U-01 in Undecided ideas]

The user will discuss deployment with teammates. No hosting service or deployment option has been selected. The full three-option comparison is exclusively in U-01.

## 15. Quality, validation and evidence

### 15.1 Confirmed measurement approach

The user chose to redefine the original numerical goals as measurable criteria tied to agreed conditions. Do not retain the old figures as mandatory or verified commitments.

Quality targets and test conditions: [UNDECIDED — see U-02 in Undecided ideas].

Already agreed behavioural requirements support tests for authorization, ownership, no overselling, exact picking, no duplicate collection within a pickup cycle, authorized re-pickup after return, preserved records, final cancellation, no duplicate effects and honest status reporting.

### 15.2 Planned verification inventory

These are proposed checks derived from the agreed behaviour, not executed tests:

| Area | Meaningful cases |
|---|---|
| Order/payment | Actual order data and total; same-SKU aggregate quantity; one active checkout; declined-card retry retains reservation; expired unpaid checkout releases safely; payment verified at/beyond the deadline |
| Cancellation/refunds | Client cancel before picking; race with Start Picking; unresolved payment at cancel; late success refunds without revival; separate pending/failed refund presentation |
| Inventory | Two clients compete for the last units; no scan-time double deduction; restock; zero/short physical counts; intact allocated return preserves availability |
| Picking | Wrong product, excess, reversal underflow, missing line blocks packing, exact quantities require explicit confirmation, interrupted task resume and Admin reassignment |
| Pickup | Claim is not custody; wrong-list/duplicate scans; capacity defer; damage hold; partial list closure; pre-attempt return without false delivery failure |
| Delivery | Per-order start; proof required on success; Other requires note; attempt history retained; actual return before approval; explicit client instruction; new pickup for retry |
| Privacy/security | Own-order access; role-protected operations; Admin address/phone restriction including logs; original passwords unavailable; lockout, inactivity and temporary credentials |

Use unit/component/integration/system and user-acceptance evidence where appropriate. Static inspection of a draft is not the same as running an integrated application. Shared-role testing must exercise correct data and timing between components.

### 15.3 Local Delivery verification — 5 November 2026

Recorded evidence: 10 Java backend tests passed with no failures/errors/skips; 19 actual HTTP API checks passed; headless Chrome desktop/mobile login and layout checks passed without page errors or horizontal overflow; a real UI claim/collection/start/signature-upload/delivered/history flow passed; confirmed package/attempt/return records survived a real restart; and an offline database/proof backup restored into a new folder with matching hashes. These are local development checks, not invented student contributions or full-system acceptance.

The recorded reports cover the 5 November runnable package. Use the testing guide to rebuild and check the current checkout after source changes. Physical QR/camera hardware, the full U-02 workload and 24-hour observation, real multi-role integration, cloud services, external notifications and provider payments/refunds remain open verification items. [Delivery verification](../TESTING.md)

## 16. Team process and current work

User-confirmed on 4 November 2026:

- Nine members: five Coding and four Testing. The user belongs to Coding.
- Four Coding work areas correspond to Client, Warehouse Worker, Delivery Worker and Admin. One programmer handles that role's frontend and Java backend, using the HTML prototypes as the visual reference. Database is the fifth work area, with a separate person.
- Individual assignments remain undecided. The Database owner's scope is also undecided.
- For each new feature, create a Git branch; send every completed small function to Testing classmates for testing; finally integrate into one program.
- No agreed owners for shared functionality or overall integration were reported. Java/Spring Boot remains the backend direction; other technology choices and previously open policies remain undecided. No implementation progress since the previous record was reported.

The user subsequently clarified that "Batch" meant Git branch. A particular branch naming/merge strategy or sprint is not established. The final-integration plan does not establish earlier integration checks, a passing shared build, test criteria, defect handling or developer-side testing responsibilities.

Remaining division/process details and sprint length: [UNDECIDED — see U-03 in Undecided ideas]. Do not turn the historical nine-module split, three cross-functional groups, two-week sprints, backlog tooling or product-owner approval proposal into actual team practice.

Coding will be assisted by ChatGPT. Generated work still needs inspection, integration and verification. No individual responsibilities, real contributions, meetings or successful tests should be invented for the logbook.

## 17. Course deliverables and deadlines

The course introduction is the primary assessment source. Its project components total 50 percentage points of the course.

| Component | Requirement | Weight |
|---|---|---:|
| In-class progress review | User-confirmed slot: 23 November 2026; 10 minutes | 10% |
| Recorded project presentation | 15–25 minutes describing the project and engineering process | 10% |
| Group report/code | PDF, at most 1,000 words excluding source code; accessible GitHub repository with install/run guide; GitHub link first line; leader/representative submits through OLE; due 8 December 2026 | 15% |
| Individual project logbook | Individual records and questions about participation; final due 8 December 2026 | 15% |

Recorded-video submission deadline: [UNDECIDED — see U-04 in Undecided ideas].

Logbook interim dates are 30 September, 15 October, 30 October and 15 November. Meeting agenda/plans/participation/activity summary has a 200-word maximum per meeting; personal contribution at each meeting has a 100-word maximum. Also record project/report/presentation contributions. Do not copy another member's individual logbook.

Both presentation rubrics allocate 5 percentage points to structure and 5 to clear presentation. The report allocates 12 to correct application of software engineering concepts and 3 to report quality. Logbook weighting is 10 for participation and 5 for quality.

The PDF does not prescribe a separate mandatory document for every lecture technique or establish a required completed-system demo at the progress review. It gives no AI-specific usage policy. Any later lecturer instructions need separate verification; the PDF asks students to check OLE for updates.

Actual logbook submission/completion status and individual contributions are not established here.

## 18. Lecture-to-project traceability

These are appropriate applications/evidence to develop, not claims that every method is adopted or every artifact already exists.

| Current lecture | Relevant points/pages | Application to this project |
|---|---|---|
| 1. Introduction | Quality attributes, process activities, fundamentals and responsibilities; pp12–13, 16, 20 | Explain business purpose, scope, quality priorities and honest engineering evidence |
| 2. Software Processes | Process descriptions/models, incremental work, reuse/integration, validation and prototypes; pp4–7, 11–18, 27–31, 37–41 | Choose and justify a feasible process; evaluate prototypes/reuse; show increments and validation |
| 3. Agile Development | Incremental planning, stories, collective testing/integration, backlog and review; pp15–19, 36–42, 51 | Propose prioritized work and reviewable increments; do not claim Scrum/TDD/pair programming without actual practice |
| 4. Requirements Engineering | Functional/NFR distinctions and metrics, negotiation, scenarios, specification, validation and traceability/change; pp14–26, 35, 46, 50–58, 71–75, 80–83 | Number requirements, define states/permissions, preserve rationale/open decisions, derive tests and assess changes |
| 5. System Modeling | Perspectives, UML, boundaries, interactions, classes and behaviour; pp5–6, 9–20, 24–25, 36–41 | Context/use cases, activity/sequence/state models and domain relationships |
| 6. Architectural Design | Decisions/views/patterns, MVC/layers/client-server and transaction-based information systems; pp3–4, 13–19, 23–27, 33, 44–51 | Explain components, interfaces, shared data, security and deployment trade-offs |
| 7. Design and Implementation | Interfaces for parallel work, reuse, configuration/version/integration/problem management, deployment and licensing; pp7, 27, 40–46, 48–52, 56–60 | Agree shared interfaces and manage versions/dependencies; provide a reproducible run guide |
| 8. Software Testing | Requirement tests, V&V, inspections, development/component/interface/system testing, regression and acceptance; pp4, 8, 12, 16–25, 27, 32–42, 48, 52, 56–61 | Test normal/invalid/boundary/concurrent flows and interactions across roles; keep evidence separate from plans |

Useful integration boundaries include Order + Inventory + Payment, Picking + Inventory, and DeliveryAttempt + Status + Notification. They do not prescribe a final service/API schema.

## 19. Prototype review and selected references

### 19.1 Selected starting points

- Client: variant _1.
- Admin: variant _1 as the starting reference, with _2's visible failure reason/attempt time and reduced exception-table density.
- Keep separate order/payment/refund concepts, with financial detail accessible without widening every exception row.
- Keep the role name Delivery Worker and precise Order status wording.
- Retain Admin catalogue ownership and Warehouse-only physical stock changes.
- Remove unsupported GDPR/HIPAA, zero-knowledge, encrypted-hash, geocoded-token and cluster/security assurances from both variants.
- Correct low-stock fixtures, the Client Read-Only label and missing required product/user workflows.

The names Mei, Ken and Alex are prototype personas, not assignments to real students.

### 19.2 Findings from static source/screenshot inspection

The prototype folder contains six HTML files, six screenshots and DESIGN.md. Both Admin variants have identical interactive application logic; their changes are presentation/copy/fields, not additional implemented services.

The files use local fixture/simulation behaviour, with no established shared backend/database/authentication integration.

| Area | Observed issues that must not become requirements or implementation claims |
|---|---|
| Client | Reservation wording conflicts across variants; per-row quantity checks miss repeated-SKU totals; order/payment action targets hardcoded fixtures; delivery data is not properly validated; account settings are largely static; refund messaging implies completion too early |
| Warehouse | Start Picking opens a simulated task without the intended state change; missing progress DOM IDs would break a task update; list-verification flag is not enforced; only one hardcoded product is handled; restock entries do not update real stock; zero count is disallowed; queue/stock/threshold fixtures disagree |
| Delivery | Detail-drawer functions are referenced but undefined; delivery can start before pickup in local logic; photo/signature/hash labels do not prove capture/persistence; attempts are not actually appended; return/redelivery custody workflow is missing |
| Admin | Low-stock alerts contradict displayed thresholds; required catalogue/account workflows are missing; role/ban/reset/lock actions are simulations; countdown does not enforce expiry; credentials/log rows do not implement backend security or immutability |
| Across screens | Same order IDs have inconsistent products/recipients/addresses; QR formats and role/persona names differ; US addresses/ZIP/$ and company-account fields are fixtures; density/clipping/contrast and phone suitability need review |

These are inspection findings, not reports of runtime testing. Prototype buttons such as Simulate next status are demonstration controls, not Client permissions.

DESIGN.md is a visual reference with inconsistent values/roles across sections; it is not a source of additional business roles or infrastructure.

Remaining presentation/experience choices: [UNDECIDED — see U-14 in Undecided ideas].

## 20. Decision history and superseded draft concepts

User confirmations in this discussion established/refined:

- Word business baseline reported team-approved; one retailer confirmed.
- Client _1 and the combined Admin reference selected.
- One warehouse/product totals and one package per order.
- Reservation-based availability/warnings; no scan-time physical deduction.
- Cancellation stops at Start Picking; controlled Admin exceptions for shortages/returned failures.
- Warehouse-created lists; one collector; individual accepted pickup is custody.
- Reasoned skipped packages and separate returns before a delivery attempt.
- Warehouse return before client-requested/Admin-approved redelivery; pickup repeated for retry.
- Client correction/request window; tracking buttons plus manual support fallback; Admin-reviewed post-return cancellation.
- External-provider test payments; strict 24-hour LMS confirmation; final cancellation with late-charge refund. The provider/interface selection is now undecided (U-16).
- Same-checkout card retry with reservation retained; 30-minute checkout window.
- Hong Kong/HKD, different recipients, included delivery, saved defaults and unpaid cancel/recreate.
- Admin picking reassignment, deactivation/history preservation and one-time reset display/manual handover.
- As of 29 October: deployment deferred; numerical quality goals to be redefined; no team process/allocation/sprint agreement. The partial process/allocation confirmation on 4 November below supersedes that earlier status.

On 4 November 2026 the user confirmed five Coding/four Testing members, full-stack ownership for each of the four role areas using the HTML visual style, plus a Database owner whose scope is undecided. Names are not assigned. New features begin with a Git branch (the user clarified the earlier word "Batch"), each completed small function is sent to Testing, and final integration into one program is planned. Shared-function/integration ownership, merge details, other technical choices, operational policies and additional lecturer instructions remain undecided. No new implementation progress was reported. The earlier three-cross-functional-group recommendation was not adopted and is superseded as the current division proposal; it is not a team decision.

The same message supplied a member's unapproved Database Draft v0.4 and two ERDs for review. Supplying/reviewing them does not approve the schema, change business requirements or close undecided policies.

Payment decision history: Stripe-hosted Checkout with test cards was selected with the user on 28 October 2026. On 29 October 2026, the user explicitly reopened the payment provider/interface choice and requested a placeholder plus examples in the final Undecided ideas section. The earlier Stripe selection is superseded, not an active commitment. The test-only scope and agreed payment, stock, deadline, cancellation and refund rules remain in force; this revision does not establish approval by all nine members.

Consequently, do not carry forward the old cart/full product pages, second-person packing check, seven ambiguous logistics stages, stock reserved merely on order creation, stock deduction per scan, reservation only after success, automatic direct retry after failed delivery, or refund-as-order-status wording.

This is a record of this chat's confirmations, not invented group-meeting minutes.

On 5 November 2026 the user requested the Delivery frontend/Java backend with prototype styling, enhanced B plans, explicit Place holder connections and simple comments. The user clarified that B means the enhanced plans in PROJECT_DETAILS, authorized a runnable local demo with marked sample data/adapters, and selected English UI/simple English comments. The resulting Delivery stack and local evidence are recorded in Sections 13.4/15.3. External dependencies remain Place holders; no real hosting/payment setup or complete team-system integration is implied.

## 21. Source register and review limits

### Local primary sources

- `Course_Introduction_Software_Engineering.pdf` covers assessment on pp14, 18, 22 and 24–30. The original developer ZIP contains the course brief.
- `Project Topics.pdf` gives the logistics option on p2 and project process/report coverage on p4. The original developer ZIP contains the project brief.
- [Team design and workflow baseline](baseline/LMS_Team_Design_Workflow_Baseline_v1_Clean_Draft.docx): working business baseline; preserve suggested modality.
- [Initial requirements draft](baseline/logistics_requirements_team_v1.md): initial draft and historical proposals.
- Member's unapproved [Database Draft v0.4](../database/v0.4/LMS_Database_Draft_v0_4.md), [overview ERD](../database/v0.4/LMS_ERD_v0_4_Overview.png) and [detailed ERD](../database/v0.4/LMS_ERD_v0_4_Detailed.png), supplied on 4 November. See the [review record](../database/LMS_Database_Draft_v0_4_Review.md).
- Current root lectures:
  - Lecture 1_Introduction to Software Engineering.pdf
  - Lecture 2_Software Processes.pdf
  - Lecture 3_ Agile_Software_Development.pdf
  - Lecture 4_Requirement Engineering.pdf
  - Lecture 5_System_Modeling.pdf
  - Lecture 6_Architectural_Design.pdf
  - Lecture 7_Design_and_Implementation.pdf
  - Lecture 8_Software_Testing.pdf

Prototype sources are under [docs/prototypes](../prototypes/).

- client_portal_order_creation_payment_tracking_1/code.html and screen.png
- client_portal_order_creation_payment_tracking_2/code.html and screen.png
- warehouse_operations_picking_restock_mei/code.html and screen.png
- delivery_field_app_pickup_proof_ken/code.html and screen.png
- system_administration_audit_center_alex_1/code.html and screen.png
- system_administration_audit_center_alex_2/code.html and screen.png
- logistics_operations_system/DESIGN.md

All six prototype screens and HTMLs were inspected. Course assessment pages were also visually checked. Source review is not implementation validation. Assignment 1 coverage and its derivative/media limitations belong in its separate context.

### Official external references checked during this discussion

- Payment-interface candidate references and earlier provider-specific research are recorded under U-16; those references do not select a provider.
- [Browser camera contexts](https://developer.mozilla.org/en-US/docs/Web/API/MediaDevices/getUserMedia)
- [Temporary tunnel behaviour](https://developers.cloudflare.com/tunnel/concepts/)

External documentation supports feasibility/behaviour; it does not select an unagreed hosting product or create fictional business rules. Credentials, installations and cloud configuration have not been performed.

### Maintenance rule

For a later confirmed choice, fill the relevant main-section placeholder, identify its authority/date, and move the closed comparison into decision history before this final section. Keep the active Undecided ideas section exclusively for unresolved topics. Do not duplicate its full comparisons in the overview or main topical sections. Maintain an impact summary, at least two reasonable plans with examples, and an explicit recommendation for each unresolved entry.

## Undecided ideas

All entries contain unresolved matters; U-03 is partly confirmed on 4 November, with its remaining questions listed there. **Recommended** identifies the assistant's proposal, not a user or nine-member team decision. On 29 October 2026, the user requested reliable recommendations that generally reduce development time, with enhanced alternatives whose added functionality or other benefits can justify more effort. AI assistance is expected; compare implementation, integration, verification and maintenance work without assuming a particular team skill level.

Each entry states its impact and offers concrete examples. Plan A is the recommended reliable first-version proposal; Plan B adds capability or stronger evidence with more work. Existing fallback/provider alternatives are retained where useful. The user permits proposed numerical values with explicit assumptions. Such values are discussion inputs, not requirements, measured results or guarantees. A shared safeguard under an entry applies to all its plans. Related choices must be reconciled together, especially deployment/storage (U-01/U-09), account setup/recovery (U-08/U-13), stock/repair (U-06/U-07) and provider/refund operations (U-16/U-15).

### U-01 Deployment

Status: UNDECIDED. The user will discuss the alternatives with teammates. No deployment option or hosting provider is selected.

**Impact:** Affects access by all four roles, shared data, phone camera contexts, payment callbacks, proof-file persistence and whether a demonstration depends on one laptop remaining online.

| Option | Benefits | Disadvantages | Example |
|---|---|---|---|
| A. Shared hosted HTTPS site — **Recommended** | Everyone accesses the same application/database from different devices. It runs independently of a student's laptop and supports simultaneous role activity. Use a documented release and a manually checked backup/restore procedure. | Requires hosting, database and persistent proof-storage configuration. The deployed version/settings need management. Performance/availability depend on selected services and must be tested. | A client orders on a phone, Warehouse packs on a tablet, Delivery scans on another phone, and Admin monitors from a laptop. |
| B. Shared hosted site with deployment and recovery automation | Add a separate rehearsal environment, automated release checks, scheduled protected backups and service-health alerts. This improves release confidence and recovery visibility. | Additional environment cost/configuration, monitoring and restore exercises; backup retention must respect U-09. Security and durable storage are necessary in either hosted plan. | A release is rehearsed against test data before the shared demonstration site is updated. A failed health check alerts the team, and a backup is restored into an isolated test environment. |
| Fallback 1. Local server with a secure temporary link | Shares the locally running system with teammates' devices while retaining local debugging. Useful when full hosting setup is deferred. | The computer, application, database, connection and tunnel must remain running. Disposable URLs may change, requiring payment-service settings to be updated. | Spring Boot and the database run on one member's laptop; teammates open its temporary HTTPS address. Access stops when the laptop or tunnel stops. |
| Fallback 2. Local demonstration on one laptop | Smallest deployment setup, convenient debugging and a controlled demonstration environment. Members can install their own development copies. | No shared multi-device demonstration by itself; separate installations have separate data unless connected. External-provider testing needs internet; local payment-event forwarding or a public callback arrangement depends on U-16. | One laptop runs the application/database; separate browser profiles represent four roles; its webcam scans printed QR codes. |

**Recommendation:** Plan A best supports the agreed shared role workflows with a compact operating setup, assuming a suitable service and budget can be agreed. Plan B adds useful operational capability after the core system is stable. The two original local configurations remain development/demonstration fallbacks; they are not presented as feature enhancements. No deployment or hosting vendor is selected.

Comparison references:

- [Browser camera access in HTTPS/localhost contexts](https://developer.mozilla.org/en-US/docs/Web/API/MediaDevices/getUserMedia).
- [Temporary tunnel addresses and running-process dependency](https://developers.cloudflare.com/tunnel/concepts/).
- Provider-specific local payment-event examples are under U-16; selecting a deployment option does not select a payment provider.

The examples illustrate the alternatives; they do not choose a hosting/tunnel vendor or constitute deployment.

### U-02 Measurable quality criteria

Status: exact targets and conditions UNDECIDED; the approach of redefining measurable criteria is confirmed.

Historical draft inputs were one-second page loads, first order within five minutes without help, and 99% availability. These are not retained as active numerical commitments or measured results.

Define the following before treating them as acceptance thresholds:

| Area | Decisions needed |
|---|---|
| Response/performance | Device/browser, connection, local/hosted environment, data volume, concurrent workload, measured operation/completion point, treatment of external payment/media time, numerical threshold |
| Usability | Exact first-order task, inclusion of registration/payment, participants' familiarity, device, assistance, timing and success measure |
| Availability | Expected operating periods, observation window, monitoring and maintenance treatment |
| Recovery/persistence | Required restart/restore behaviour, backup/restore scope and any recovery-time/data-loss targets |
| Compatibility | Supported phone/laptop browsers and camera/manual-entry acceptance conditions |

Proposed evidence categories are response measurements, realistic task trials, authorization/concurrency tests and recovery checks. Do not present proposed numbers as approved thresholds or invent benchmark results.

**Impact:** Affects supported devices, response times, ordering usability, recovery and the evidence needed before claiming the system meets its requirements.

| Plan | Proposal and tradeoff | Example |
|---|---|---|
| A. Controlled acceptance package — **Recommended** | Use one available phone and one laptop, recording browser versions, network and deployment. Proposed test data: 100 products and 500 orders; five simultaneous authenticated role sessions. For named LMS operations, propose a 95th-percentile response of at most two seconds, measured from request submission to usable acknowledgement over at least 100 samples per operation. Measure external payment waits and media uploads separately. Add an unassisted ordering trial and restart/backup checks. | Five sessions perform ordering, picking, pickup and monitoring. A usability trial with five first-time participants who already have accounts aims for at least four to create a correct unpaid order without help within six minutes. Payment is tested separately. Restarting preserves committed orders/scans; a manual backup is restored and checked. |
| B. Broader acceptance, monitoring and recovery package | Include A, then add more phone/browser combinations, a proposed 20-session workload with 500 products and 2,000 orders, and a 24-hour hosted observation. Keep the same two-second target for the named LMS operations as a candidate under the heavier load; revise it only through explicit review. Add repeatable load runs, upload/camera measurements and timed recovery into a separate environment. This gives broader evidence but takes more preparation and analysis. | Teammates run simultaneous workflows while the team records percentiles, failed operations and observed downtime. Restore a backup into an isolated environment and compare orders, reservations, audit records and protected proof references with the backup checkpoint. |

**Recommendation and assumptions:** Start with A because it is reproducible and limited enough for a course project. Its candidate numbers assume a small catalogue, modest class-demonstration workload and available phone/laptop hardware; they are not promises about production demand. Propose two 60-minute rehearsals without an unexpected application outage and a 15-minute manual restore objective for this small test dataset, subject to storage/environment trials. B investigates wider use; a 24-hour observation cannot establish long-term 99% availability. Both plans must test authorization, stock concurrency, cancellation and duplicate payment effects regardless of speed. None of these trials has been run.

Proposed measurement detail: use repeatable seeded scenarios and an action mix of opening lists, viewing orders, submitting eligible scans and refreshing status, with each simulated role session issuing approximately one action every five seconds. Record the exact mix/pacing and at least 100 samples for each operation before calculating its percentile. Repeated load runs exclude external-provider calls; test actual provider integration separately in its sandbox. For the proposed 15-minute restore objective, start the timer when the documented restore begins and stop only when the application is usable and selected order/reservation/audit/proof-reference checks pass. Restore a consistent database/media backup checkpoint and state what later work would be lost; no production recovery-point guarantee or zero-loss backup claim has been agreed. A normal application restart must still preserve committed records.

### U-03 Team work division and development process

Status: PARTLY CONFIRMED on 4 November 2026. Five Coding/four Testing, four role full-stack owners plus one Database owner, per-feature Git branches, testing after each completed small function, and final integration are confirmed. Names, Database scope, shared-function/integration ownership, merge rules, detailed test/change workflow and sprint length remain UNDECIDED. Section 16 records the confirmed arrangement.

Historical proposals, not assigned work:

- Nine modules: four role frontends, backend API, database, login/security, testing, documentation/UI.
- A testing coordinator, JUnit and tests developed alongside/before code.
- Two-week sprints, prioritized P1/P2/P3 backlog, GitHub Projects/issues and product-owner approval.

The Word recommends small increments and shared interfaces/testing. Within the newly confirmed five/four split and feature-branch practice, decide real owners, Database scope, shared responsibilities, merge/review/change authority, scheduling and backlog practice. Do not claim formal Scrum, TDD or any member's contributions without actual evidence.

**Impact:** Affects how nine students share work, agree interfaces, integrate changes, discover defects and record their actual individual contributions.

| Plan | Proposal and tradeoff | Example |
|---|---|---|
| A. Shared contracts and early connected checks within the five/four split — **Recommended** | Keep the confirmed division. Define Database/shared-function scope, a common application skeleton, state/data contracts and a named integration coordinator. Alongside the agreed small-function handoffs, propose connected role checks before final integration, with recorded expected results and defects. This adds limited coordination without replacing the confirmed team structure. | Client payment confirmation feeds the real Warehouse queue; Warehouse packing feeds Delivery pickup. Testing checks shared data as well as each small function, with defects returned to the relevant Coding owner. |
| B. Add an automated integration and review pipeline | Include A, plus automated build/test checks on proposed changes, shared-code review, API-contract checks and a repeatable integration environment with seeded data. This gives faster feedback and wider ownership, but the team must configure and maintain useful checks. | A Warehouse change triggers the shared build and reservation/state tests. A reviewer checks its effect on cancellation; the connected scenario then runs against prepared test data. |

**Recommendation:** A addresses the remaining ownership and integration gaps within the user's confirmed arrangement; B adds useful automation as the shared build stabilizes. Early connected checks are a proposal, not an already-agreed replacement for final integration. No named member, sprint length or developer-testing split is assigned. The historical nine-module/two-week and three-group suggestions remain unadopted history; neither plan establishes formal Scrum or TDD. AI assistance does not replace review or evidence of actual contributions.

Feasibility reference: [GitHub's Java/Maven continuous-integration guide](https://docs.github.com/en/actions/tutorials/build-and-test-code/java-with-maven). Maven and GitHub Actions are examples, not selected tools.

### U-04 Recorded presentation details

Status: submission deadline UNCONFIRMED.

The 15–25-minute recorded project/process presentation is separate from the confirmed 23 November ten-minute in-class review. Determine its deadline, submission route, file requirements, participation/speaking expectations and any later demonstration instructions. Do not infer that the video is due on 8 December.

**Impact:** Affects recording preparation, demonstration stability, presentation clarity and which evidence the group must preserve. Neither plan can determine an official course deadline.

| Plan | Proposal and tradeoff | Example |
|---|---|---|
| A. Coordinated recording with a maintained evidence folder — **Recommended** | Maintain an outline, current diagrams, genuine test evidence and demonstration data. Verify official instructions, rehearse and record a coordinated presentation using a stable build. This limits editing work but requires scheduling and a recoverable demonstration setup. | Rehearse a proposed 20-minute presentation: two minutes on the problem, five on requirements/design, seven on the connected demonstration, four on integration/testing and two on reflection. This is a proposed structure within the known duration. |
| B. Edited chapters with additional visual explanation | If course instructions permit editing, record short chapters, add explanatory diagrams/captions and assemble a checked final video. This can improve clarity and let the team replace a defective segment, but adds editing and consistency checks. | Record requirements, architecture, workflow and testing chapters separately. Explain reservations with a diagram, then show matching footage from the same final build; verify terminology, captions, audio and total duration. |

**Recommendation:** A minimizes production work; B offers a more refined presentation when the instructions allow it. Check the official deadline, upload route, format, participation expectations and demonstration requirements before final production. Do not invent a submission date, completed test evidence or a requirement that all nine members speak.

### U-05 Architecture and technology details

Status: UNDECIDED beyond confirmed Java/Spring Boot, browser UI, relational storage, external-provider test integration and camera/manual QR direction. Payment provider/interface selection is separately undecided under U-16.

The Word's proposed starting design is a layered application: role UI, controllers, business services, repositories/ORM and relational database. Suggested responsibilities include authentication, order/state, inventory/reservation, payment adapter, picking, delivery/attempts, notifications and logging.

Suggested domain objects include User, Product, Order, OrderItem, PaymentRecord, stock reservation/movement, PickingScanHistory, DeliveryAttempt, assignment history, Notification and audit/security records. This is a candidate model, not an agreed schema or set of independently deployed services.

Choose concrete architecture, frontend framework, database product, shared component/API contracts, dependency versions, QR implementation, proof storage, configuration and installation approach. Payment-account access and integration setup remain to be completed after the provider/interface decision under U-16. No provider is selected here.

**Impact:** Affects how role screens reach business rules/data, how modules are integrated and tested, and how many builds/configurations the team must maintain.

| Plan | Proposal and tradeoff | Example |
|---|---|---|
| A. One Spring Boot application with server-rendered pages and focused JavaScript — **Recommended** | Propose one deployable application and one relational database, with internal modules for authentication, orders, inventory, payments, Warehouse, Delivery and audit. Use controllers, services and repositories; a template engine such as Thymeleaf renders pages, with JavaScript for scanning and selected interactions. This limits build/deployment setup while keeping business rules in the backend. | Delivery scans a QR in the browser and submits a protected action. The service validates assignment/state and commits custody/stock changes. Manual entry invokes the same behaviour. |
| B. Richer browser application with a Spring Boot JSON API | Keep one backend and one relational database, but add a separately built browser frontend, for example React or Vue, shared UI components and richer task navigation/state management. This supports more elaborate interactive screens and independent frontend work, but adds tooling, API contracts and coordinated authentication/error handling. | Warehouse filters tasks, switches between task views and sees confirmed progress without losing screen context. Pending/error states prevent an unconfirmed scan from appearing successful. A can also use focused asynchronous updates; a frontend framework is not required for that basic interaction. |

**Recommendation:** A reuses the HTML design direction with less build/configuration work. B offers a richer interface when its benefit justifies the integration and verification effort, including for AI-generated code. Either plan still needs an agreed database product, migration process, shared contracts, dependency versions, QR library, protected proof storage and installation guide. Provider/hosting choices remain under U-16/U-01. These plans are not an agreed schema or a microservices decision.

Official feasibility references: [Spring MVC with server-rendered HTML](https://spring.io/guides/gs/serving-web-content/), [Spring JSON REST services](https://spring.io/guides/gs/rest-service/) and [logical modules within Spring Boot](https://docs.spring.io/spring-modulith/reference/fundamentals.html). These support the examples; effort comparisons are project design judgments. Logical modules do not require adopting Spring Modulith.

### U-06 Inventory discrepancies and return inspection

Status: detailed accounting/inspection workflow UNDECIDED.

The normal intact-package return restores its allocated quantities without increasing availability. Define actual received quantities/condition, damaged/quarantined goods treatment, and how a count below reservations is represented/resolved.

Do not blindly add the full order quantity as sellable stock for an incomplete/damaged return. Do not release unsuitable goods for new orders after cancellation. Preserve the already agreed Warehouse report/Admin wait-or-full-cancel authority.

Quantity/condition checking at receipt is required, as stated in the Warehouse workflow. Its exact confirmation controls, discrepancy handling, adjustments and data model still need agreement.

**Impact:** Affects Warehouse receipt, saleable availability, shortage resolution and whether cancellation accidentally releases damaged or missing goods for new orders.

| Plan | Proposal and tradeoff | Example |
|---|---|---|
| A. Explicit unavailable-stock accounting — **Recommended** | Record actual quantities/condition and reasoned movements. Track damaged units as physically present but unavailable, and show any reservation shortfall against usable stock. Block affected fulfilment and refer the verified shortage to Admin's existing wait-or-full-cancel decision. This adds essential exception accounting without a larger investigation module. | Three units return: two usable and one damaged. All three are physically accounted for, but the damaged unit cannot satisfy the order or become available simply because Admin cancels. The order stays blocked until the shortage is resolved. |
| B. Add reconciliation cases and impact reporting | Include A, plus a discrepancy case with condition evidence, proposed corrections, affected reservations/orders and a resolution checklist. Warehouse records findings; Admin resolves business consequences. This helps coordinate several affected orders but adds screens, case states and links. | A count discovers four missing units. One case identifies which reservations/orders are affected and records each resolution, instead of staff investigating them separately. |

**Recommendation:** A supplies the essential reliable controls; B improves coordination for wider discrepancies. A candidate exception calculation is new-order availability = max(0, physical − unavailable − reserved), with any uncovered reservation deficit recorded separately. Do not hide the actual physical count or erase existing order demand to make the arithmetic look valid. This is a proposed extension for exceptions; the confirmed physical-minus-reserved formula remains the normal intact-stock case. Neither plan restores expected quantities that did not arrive or permits partial fulfilment/substitutions.

### U-07 Controlled replacement after packing

Status: replacement/reverification procedure UNDECIDED.

Intact-goods outer-packaging repair is already allowed. Goods damage/missing content uses the shortage process. If Admin chooses waiting after packing has locked contents, define who authorizes reopening/replacement and how the same ordered SKU/quantity is verified and packing reconfirmed.

A proposed approach is appended same-SKU replacement/verification records, retaining original history and collection blocks. Do not assume substitutions, partial delivery, deletion of original scans or unrestricted postpacking edits.

**Impact:** Affects the packing lock, replacement verification, collection eligibility and preservation of original packing/scan history.

| Plan | Proposal and tradeoff | Example |
|---|---|---|
| A. Authorized repair of affected lines — **Recommended** | Warehouse reports the issue; Admin's recorded wait decision authorizes a controlled repair task. The assigned Warehouse worker records defective-unit disposition/replacement scans, checks other contents and explicitly reconfirms packing. Admin uses the agreed reassignment procedure if a different worker takes over. Preserve original scans and block collection throughout. | One mug in a packed two-mug-and-notebook order is damaged. The assigned worker records its removal, scans a replacement mug, checks the unchanged items and reconfirms the complete package. |
| B. Complete re-verification with a guided checklist | Use the same Admin authority and assigned-worker ownership, but add a packing-verification revision for the same order/package. Rescan every line and complete a checklist covering contents, quantities, label and condition. This gives stronger assurance for uncertain contents but repeats work and adds UI/history. | A package has an unclear contents discrepancy. The assigned worker verifies both mugs and the notebook from the beginning and completes the checklist before making the package collection-ready. |

**Recommendation:** A limits repeated work for a clearly identified defect; B gives greater assurance when the contents cannot be trusted. If A is chosen, uncertain/multiple discrepancies should remain blocked for explicit review rather than being forced through its narrow repair path. Both preserve one complete package, ordered SKUs/quantities and original authorship. Scans do not invent stock movements: actual corrections use U-06. Neither adds a compulsory second-person check. Intact-goods outer-packaging repair remains the already-agreed simpler action.

### U-08 Manual verification and client-request lifecycle

Status: verification and pending-request amendment rules UNDECIDED.

Define how manual support verifies a client's authority for redelivery/cancellation or temporary-password handover while respecting Admin contact-data limits. Record instruction/source/time without copying private address/phone into Admin logs.

Decide whether/how a client can amend or withdraw a pending instruction, switch between redelivery/cancellation, or resolve a race with Admin approval. The already agreed requirements remain: explicit client instruction, return confirmation, Admin approval, correction window and pending-cancellation block.

**Impact:** Affects manual support, temporary-password handover, changes to pending client instructions and stale Admin approvals.

| Plan | Proposal and tradeoff | Example |
|---|---|---|
| A. Withdraw/resubmit and a simple verified fallback — **Recommended** | Use authenticated tracking controls and one active instruction per order. A changed instruction supersedes the earlier one through a withdraw-and-replace action; approval checks the current active record atomically. For manual fallback/reset handover, propose a single-use recovery code issued to the owner of every Client or staff/Admin account at setup, shown once and stored as a protected one-way verifier. The backend checks it and records verification success, never the code, address or phone. Code issuance/replacement is new proposed functionality. | A client replaces redelivery with cancellation. The earlier redelivery can no longer be approved. A client or staff member unable to sign in presents their previously saved recovery code for verified manual handover of the one-time temporary password. |
| B. Add versioned editing and remote support authorization | Include the same ownership/final-action checks, then allow direct editing of pending instructions with a versioned change history. Add short-lived, single-use support authorizations generated after client re-authentication and bound to a specific order/action; recovery codes remain for forgotten-password cases. This improves remote support and editing convenience but adds credential lifecycle and conflict handling. | The client edits a redelivery instruction while Admin reviews it. Approval against the old version fails. A signed-in client gives support an action-specific authorization instead of disclosing private delivery details. |

**Recommendation and assumptions:** A offers a small, explicit verification mechanism without adding an email service. It assumes clients can securely retain a code; lost-code cases need a separately agreed trusted manual verification procedure and must not bypass authentication. For B, propose a five-minute support-code lifetime as a discussion value, assuming a short support interaction; test expiry/reuse and accessibility before accepting it. Neither an order number nor knowledge of an address proves authority. Replacement instructions and approval must be checked in the same protected transaction; pending cancellation continues to block redelivery. These are proposals guided by [OWASP authentication](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html) and [transaction authorization](https://cheatsheetseries.owasp.org/cheatsheets/Transaction_Authorization_Cheat_Sheet.html), not existing capabilities.

### U-09 Proof access, storage and retention history

Status: media controls and retained-summary policy UNDECIDED.

Define photo/signature capture/upload types and limits, storage protection, exact proof-view permissions, and which non-sensitive order/attempt summaries remain after the baseline one-year attempt/proof deletion.

Reconcile that deletion with the client timeline and three-year operational audit history. Do not assume Admin may see proof merely because Admin can see outcome logs, or that deleted proof remains available forever.

**Impact:** Affects signature/photo capture, proof-view permissions, persistent media storage, expired-history messages and deletion from retained copies.

| Plan | Proposal and tradeoff | Example |
|---|---|---|
| A. Private storage with simple expiry handling — **Recommended where durable storage is available** | Store validated images outside the public web directory on protected persistent storage. Propose access for the owning Client and assigned Delivery worker while handling that attempt; Warehouse/Admin see outcome metadata only. Delete detailed attempts/proofs at one year; retain only permitted operational audit and the order's final state, with an expired-detail message. | An old order still shows Delivered, but its expired signature is unavailable. Admin retains a minimal audit event without the image, address or phone. |
| B. Add private object storage and retention monitoring | Keep A's restricted viewers and use private object storage, authorized retrieval, deletion-job monitoring and expired-file reconciliation. Add a separately defined minimal client event-summary record, proposed to expire with the three-year audit schedule. This improves cleanup visibility and historical usability but adds service/lifecycle configuration; it cannot reproduce deleted attempt details or proof. | Staff can check whether scheduled deletion completed. The client retains a limited “Delivered on…” summary after the detailed attempt expires, without being able to retrieve the old photo or signature. |

**Recommendation and assumptions:** A minimizes services if U-01 provides durable protected storage; disposable server files are unsuitable for either plan. Propose JPEG/PNG photos, a generated PNG signature and a five-MB per-file limit for initial device trials, assuming one compressed image or signature supplies the proof. The limit/format choice must be tested on intended phones; a failed upload must not mark delivery complete or erase captured work prematurely. Both need content/type/size checks, generated storage names, restricted retrieval, a cleanup schedule and backup expiry. Basic daily expiry cleanup is a proposal; B adds monitoring and reconciliation. See [OWASP file-upload guidance](https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html). Access and retention choices remain unapproved project proposals.

### U-10 Pickup and reassignment recovery details

Status: exceptional recovery details UNDECIDED.

Define abandoned-list claims, uncollected-package release, permitted list changes after claim, pre-attempt return queue/state mapping and physical custody confirmation for exceptional delivery reassignment.

A proposed safeguard is one open list per eligible package and fixed contents after claim; it has not been separately agreed. Preserve the confirmed one-collector rule, per-package actual pickup, Admin assignment authority, no ordinary driver-to-driver handover and no false failed attempt.

**Impact:** Affects abandoned claims, uncollected packages, stale scans, pre-attempt return queues and the distinction between assignment and physical custody.

| Plan | Proposal and tradeoff | Example |
|---|---|---|
| A. Explicit closure and supervised release — **Recommended** | Allow one open list per eligible package and freeze membership after claim. Cannot collect retains a reasoned outcome. The collector closes the list; Admin handles abandoned claims after Warehouse confirms which uncollected packages remain present. Release only uncollected entries and preserve collected custody records. | A worker collects four packages and marks a fifth “Vehicle full.” Closing the list releases only the fifth for another list; the other four remain with their actual collector. |
| B. Add a supervised timed-recovery queue | Include A, plus inactivity detection and a queue of stale claims for Warehouse confirmation/Admin release. After confirmation, atomically release only uncollected entries and reject scans against the closed list. This reduces overlooked packages but adds monitoring and timing/concurrency tests. | An abandoned claim is flagged; Warehouse checks the packages remain present and Admin releases them. The old collector's later scan is rejected. A package already collected is never released by a timeout. |

**Recommendation and assumptions:** A is simpler to verify. For B, propose flagging a claim after 30 minutes without pickup activity, assuming collection normally happens during one Warehouse visit; this triggers review, not automatic release or custody transfer. Exact timing must be adjusted after rehearsal. In either plan, propose inspected pre-attempt returns re-enter READY_FOR_PICKUP, or READY_FOR_REDELIVERY for an already-approved retry, preserving the earlier pickup and any required eligibility gates. Exceptional reassignment after collection still requires Warehouse-confirmed return and another pickup. These queue mappings remain proposals; no false failed attempt or ordinary driver handover is introduced.

### U-11 Deactivation and role-change effects

Status: detailed consequences UNDECIDED; deactivation/history preservation is confirmed.

Define how deactivated products affect existing unpaid orders/checkouts, how user deactivation/bans/role changes affect active sessions and assigned tasks, and recovery/protection for the last usable Admin account.

Do not silently cancel paid orders, destroy history or treat catalogue deactivation as a stock adjustment. Authorization must reflect current account/role state.

**Impact:** Affects access revocation, existing orders, task reassignment and continuity when products or staff accounts change.

| Plan | Proposal and tradeoff | Example |
|---|---|---|
| A. Immediate access controls with manual handover — **Recommended** | Product deactivation stops new orders while existing orders retain snapshots/eligibility; actual shortages use the exception process. Account deactivation/ban invalidates sessions and blocks protected actions immediately. Role changes require fresh authentication; Admin manually reassigns unfinished tasks. Prevent removal of the last usable Admin. | A deactivated picker cannot scan again. Admin transfers the saved task to another worker, preserving earlier scans/authorship. A discontinued product can still fulfil an existing paid order when stock is sound. |
| B. Add impact preview and planned handover | Include A, plus a preview of affected unpaid orders, active tasks and assignments before routine changes. Permit scheduled routine role changes after recorded handover/acceptance. Bans/deactivation still act immediately. This reduces operational surprises but adds queries, scheduling and handover records. | Before changing a worker's role, Admin sees two active tasks and assigns replacements. The scheduled change takes effect after handover; an emergency ban does not wait. |

**Recommendation:** A gives predictable catalogue behaviour and immediate access enforcement with less process setup; B improves planned transitions. Both preserve paid orders/history, original payment deadlines and actual custody. A product deactivated after order creation remains payable under this proposed snapshot policy unless a separately authorized shortage/exception blocks it. Define an installation-owner recovery procedure for lost Admin access without a public bypass. Current authorization must be enforced even for old sessions; see [OWASP session management](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html).

### U-12 Retry limits and terminal delivery outcomes

Status: business end conditions UNDECIDED.

No maximum attempt count, customer-response deadline, return fee, abandonment rule or automatic post-failure cancellation has been agreed. The baseline permits repeated attempts, now gated by return, client instruction and Admin approval.

Determine how long-pending/no-response/refusal cases close, and how any resulting cancellation, refund or stock disposition is authorized. Do not invent a numeric retry cap or reuse another assignment's deadlines.

**Impact:** Affects repeated attempts, Warehouse holding space, reserved stock, manual follow-up and how unanswered cases can eventually close.

| Plan | Proposal and tradeoff | Example |
|---|---|---|
| A. Manual case resolution — **Recommended** | Keep repeated attempts gated by confirmed return, client instruction, Admin approval and another pickup. Show unresolved cases and age for manual follow-up. Use the agreed client-requested cancellation/refund path for closure; no-response cases remain visibly unresolved until additional closure authority is agreed. This adds the least policy/automation. | Two attempts fail and return. The client corrects the address and requests another delivery; Admin reviews and approves another pickup. A no-response case remains in the queue, with its package/stock accounted for. |
| B. Add configurable review limits and reminders | Include A, plus attempt-review thresholds, response reminders and a final-review queue. Proposed starting values: review after two failed attempts, an in-system reminder after three days from confirmed return, and final review after seven days without client instruction. Limits trigger review, not silent cancellation. This improves follow-up but requires policy, scheduling and boundary tests. | A returned package has no instruction for seven days. Admin sees the reminder history and return condition before considering the separately agreed closure policy; goods are not automatically released. |

**Recommendation and assumptions:** A fits the existing authority model with less development. B gives more complete case management, assuming a local retail operation can review held packages weekly; the example values are not course or legal deadlines. To close a no-response case under B, propose explicit Admin full-cancellation/refund authority after documented follow-up and Warehouse inspection, subject to separate confirmation. Neither plan currently grants that authority, introduces fees/disposal/deductions, or waives client instruction for redelivery. The current baseline has no automatic cap or expiry.

### U-13 Account and address validation details

Status: exact field/validation rules UNDECIDED.

The initial draft mentions username/password/address registration. The confirmed order requires recipient name, phone and Hong Kong address, with editable saved defaults. Email/company-account fields in the prototype are not adopted requirements.

Define mandatory registration/profile fields, username rules, Hong Kong address/contact validation and any supported account-role model details. Do not copy mandatory US ZIP fields or grant privileged roles through public registration.

**Impact:** Affects registration effort, delivery-entry errors, saved details and ordering for different recipients.

| Plan | Proposal and tradeoff | Example |
|---|---|---|
| A. Minimal registration and one saved default — **Recommended** | Use a unique username and agreed password policy. Collect required recipient/contact/Hong Kong address details when ordering, with optional saving as the default. Use flexible address text, server-side validation and normalized common phone formatting. This keeps registration short and limits extra data management. | A client registers without an address, orders for a relative and saves those details. Later default edits do not change the placed order. |
| B. Add an optional address book and structured preview | Include A, plus named saved recipients/addresses, default selection and a combined address preview. Retain flexible text for locations that do not fit street/building/unit fields. This reduces repeated entry for several recipients but adds records, ownership checks and editing/deactivation. Multiple saved addresses are proposed extra scope. | A client selects “Home” or “Parents,” reviews the combined delivery address and places an order that retains its own snapshot. |

**Recommendation and assumptions:** A matches the agreed scope with less implementation. For discussion, propose usernames of 4–30 ASCII letters/digits/underscores, case-insensitive uniqueness and rejection of surrounding whitespace; names/address text may use Chinese/English and are not subject to that username rule. Propose storing Hong Kong contacts as +852 plus eight digits, accepting spaces/hyphens during input and providing a clear correction message. This assumes a Hong Kong contact-number requirement, which must be confirmed before enforcing it; foreign contact numbers would need a broader rule. Neither formatting nor a successful save proves ownership/deliverability. Do not add mandatory email/company/US ZIP fields. Both plans need explicit length limits and Client-only public registration. [OWASP input-validation guidance](https://cheatsheetseries.owasp.org/cheatsheets/Input_Validation_Cheat_Sheet.html) supports application-specific, server-side checks; the proposed field rules remain project choices.

The local-number format has [OFCA number-format context](https://www.info.gov.hk/gia/general/202304/11/P2023041100271p.htm); that source does not require this application to reject overseas contacts. If U-08's recovery-code plan is chosen, both registration/profile plans must also support secure code issuance/replacement; minimal registration is not an exemption from the agreed recovery mechanism.

### U-14 Optional interface and experience refinements

Status: presentation/enhancement details UNDECIDED.

Potential refinements include readable mobile delivery screens, current-task focus/feedback, useful queue/order/log filtering, receipt download, notification read-state presentation and consistent operational terminology. Assess benefits against the agreed scope and time before adopting them.

The visual DESIGN.md and selected screen layouts are references. Their infrastructure labels, company/persona fixtures and unsupported security assurances are not capabilities.

Do not reopen the explicitly excluded full-shopping/GPS/loyalty scope merely because a prototype or historical backlog mentions it.

**Impact:** Affects how easily each role finds its next action and how much optional frontend work is added after required workflows.

| Plan | Proposal and tradeoff | Example |
|---|---|---|
| A. Focused operational improvements — **Recommended** | Add current-task panels, consistent status words, basic queue/order filters, unread notification indicators and prominent scan/error feedback. Keep receipts as the normal order/payment summary. Usable mobile layouts remain necessary in both plans. This concentrates work on frequent actions. | A Delivery worker sees the current package and permitted next action on a phone. A rejected scan explains the problem while preserving accepted progress. |
| B. Add a convenience package | Include A, then add downloadable receipts, saved filters and a small role-specific dashboard using authorized existing data. This adds convenience and demonstration polish but needs formatting, persistence, permission checks and more regression testing. | A Client downloads a receipt after verified payment. Warehouse saves a ready-for-pickup filter; Admin sees exception counts without recipient contact details. |

**Recommendation:** A directly supports reliable use for less development time; B adds refinement after integration and required checks are stable. These are optional usability proposals, not permission to omit necessary error handling or a usable mobile scan flow. Receipts/dashboards must reflect actual payment/refund/order outcomes. Prototype infrastructure/security labels are not capabilities, and excluded business features stay excluded.

### U-15 Refund exception operations

Status: detailed operational handling UNDECIDED.

Define Admin visibility/action for pending or failed refunds, safe retry/escalation, and any business qualification of post-return cancellation/refund approval. No partial refund, alternative refund destination or manual Refunded checkbox is agreed.

Preserve the explicit full-refund paths for late successful charges and shortage cancellation. Every refund needs its real provider outcome; refund problems never revive cancelled logistics or re-reserve stock.

**Impact:** Affects Admin workload, client refund visibility and safe recovery from pending, failed or uncertain provider responses.

| Plan | Proposal and tradeoff | Example |
|---|---|---|
| A. Controlled Admin exception reconciliation — **Recommended** | Keep required initial full-refund initiation and verified provider-event handling. Show unresolved cases with a safe Check provider status action. Admin reviews pending/failed cases before retry, retrieving the earlier outcome and confirming retry eligibility. For approved returned-order cancellation, propose a full refund after receipt/condition exceptions are resolved under U-06. This limits background scheduling work. | A refund request times out. Admin retrieves the provider record and discovers success; no second refund is sent. The client sees the actual refund result. |
| B. Add automated rechecks and queue alerts | Include A, plus scheduled rechecks, persistent-exception alerts and controlled retries for explicitly eligible failures. Retain duplicate-effect protection and Admin oversight; an unknown result never authorizes another refund. This reduces monitoring effort but adds job lifecycle/race/failure tests. | A pending outcome is periodically retrieved. Verified success closes the queue item. Persistent uncertainty creates an Admin alert; it does not create a second refund or revive fulfilment. |

**Recommendation and assumptions:** A is a reliable first-version path when staff review exceptions; propose checking the queue at each operating session and before finishing the day. For B, propose rechecking unresolved outcomes every five minutes during the first hour, then hourly for a day before highlighting the case for manual follow-up. These are discussion intervals for a small test workload, subject to provider limits and actual pending semantics; elapsed time alone is not a failed refund. Both preserve full-refund-only scope, final cancellation and separate stock handling. No alternative destination, manual Refunded checkbox or automatic damage deduction is introduced. Mandatory late-success/shortage refunds remain unchanged. [Stripe's refund lifecycle](https://docs.stripe.com/refunds) is a conditional technical example, not a provider selection.

### U-16 Payment provider and checkout interface

Status: UNDECIDED. On 29 October 2026, the user reopened the earlier Stripe-hosted Checkout choice. No payment provider or checkout interface is selected. Account setup and integration have not started.

**Impact:** Affects the client's payment screen, provider account/configuration, backend payment verification, checkout expiry/retry handling, refunds and local/hosted integration testing.

Decide both who provides payment processing and what the client sees when paying. A provider-hosted page, a provider checkout approval window and an embedded provider component are different interface choices. The following are candidate examples for discussion, not approved requirements. Their integration-effort comparisons are project design assessments based on the documented interface types, not measured implementation results.

| Candidate interface | Benefits for this project | Disadvantages / points to resolve | Example client experience |
|---|---|---|---|
| A. Stripe-hosted Checkout in a test environment — **Recommended proposal** | A prebuilt provider payment page reduces the payment-form UI the team must implement. Official integration examples include Java, and test cards support success/decline scenarios. | The client leaves the LMS page for checkout. Requires provider setup, verified payment results, expiry/cancellation handling and refunds; using a hosted page does not implement the LMS stock/deadline rules automatically. | The client reviews an HKD order, selects Pay, enters a test card on the provider page, and returns to LMS tracking while the backend verifies the result. |
| B. An embedded provider payment component, for example Stripe Payment Element in a test environment | Provider-rendered payment fields appear inside a more integrated LMS payment page alongside its summary and progress. This gives greater control of the surrounding experience while the provider handles card fields. | More UI, confirmation/retry lifecycle and interaction testing; the component/API must fit the eventual frontend and deadlines. This still requires choosing its provider. | The client remains on the LMS payment page, enters a test card in the provider component, and sees confirmed progress alongside the order summary. |
| Additional provider alternative: PayPal Checkout in its sandbox | Provider checkout buttons and an approval flow demonstrate another external integration; official examples include Java and sandbox transactions. | Requires sandbox app credentials and test accounts. Approval and captured payment must be distinguished. A sandbox-wallet flow changes the earlier test-card-only scope and needs explicit agreement; card availability for the intended account/region must be checked. | The client approves using a fictitious sandbox buyer account, then returns to LMS tracking; the backend confirms captured payment before permitting picking. This wallet example is conditional, not added scope. |

**Recommendation:** A is the development-time proposal because the provider supplies the payment page; B offers a more refined in-page experience for additional UI/integration work. Both require verified backend results and the same stock/deadline/refund safeguards. The recommendation does not restore the superseded Stripe decision: the provider/interface remains undecided until explicitly confirmed.

Official sources for these examples, checked on 29 October 2026:

- [Stripe-hosted Checkout with Java integration examples](https://docs.stripe.com/checkout/quickstart).
- [Stripe test environment and test cards](https://docs.stripe.com/testing).
- [Stripe Payment Element: an embedded provider UI](https://docs.stripe.com/payments/payment-element).
- [PayPal standard Checkout integration examples, including Java](https://developer.paypal.com/checkout/put-it-all-together/).
- [PayPal sandbox testing and fictitious accounts](https://developer.paypal.com/sandbox-testing/overview).

Selection must preserve the confirmed one-time HKD full-order payment, test-only operation, stock reservation, one active checkout, 30-minute entry/retry window, strict 24-hour LMS confirmation, final cancellation and late-success refund rules. The test-card-only baseline remains active unless explicitly revised; a candidate requiring different test instruments does not silently change it. An internal payment simulation is not substituted for the agreed external-provider test integration.

Before confirming a candidate, check account/test access, intended HKD/card support, Java/backend integration, checkout retry/expiry semantics, verified server-side payment results, duplicate-message handling, payment-result retrieval, cancellation and full refunds, and compatibility with the eventual deployment. In particular, verify how to enforce the LMS's 30-minute entry window and earlier order deadline without treating an unresolved payment as a confirmed failure. No compatibility checks or payment tests have passed yet.

Possible integration responsibilities for later design discussion are create/resume checkout, obtain and verify a payment result, end an unpaid checkout where supported, request a full refund and retrieve its actual outcome. These are proposed interface responsibilities, not settled API signatures or a selected library. Provider-specific object names and statuses must be mapped to the separate LMS payment/refund states rather than copied into logistics statuses.

Earlier Stripe-specific research is retained here only as conditional reference material: [payment lifecycle/retry](https://docs.stripe.com/payments/paymentintents/lifecycle), [Checkout Session and payment status](https://docs.stripe.com/api/checkout/sessions/object), [session expiry configuration](https://docs.stripe.com/api/checkout/sessions/create), [expiring an open session](https://docs.stripe.com/api/checkout/sessions/expire), [webhooks and local event forwarding](https://docs.stripe.com/webhooks), and [refund outcomes](https://docs.stripe.com/refunds). None of these references restores the superseded provider decision.
