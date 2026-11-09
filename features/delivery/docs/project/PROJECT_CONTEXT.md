# Logistics Management System — project context

Last updated: 5 November 2026 (Asia/Hong_Kong).

Project reference for the Delivery source checkout. Relative links point to the feature documentation and its bundled references.

## Read this first

This is the concise handoff for the COMP 3500SEF grand group project. Read [PROJECT_DETAILS.md](PROJECT_DETAILS.md) for the complete requirements, rationale, source references, prototype review and unresolved topics.

Keep the grand project separate from Assignment 1. The existing AssignmentFiles, output and tmp folders, and the earlier project chats, concern Assignment 1. Its separate handoff is `ASSIGNMENT_1_CONTEXT.md` in the separate Assignment 1 workspace. Do not import its business rules, two-team arrangement, marks or personal reflection into this system.

### Authority and status

1. Official course documents control assessment requirements.
2. The user reported that the team approved the Word workflow baseline.
3. Later refinements below were confirmed by the user in this chat. This is not evidence that all nine members have separately approved them.
4. Preserve the original wording of suggestions: suggested architecture, work allocation and process choices are not automatically agreements.
5. The initial Markdown and Stitch files are reviewed source proposals. Resolve conflicts using the subsequent confirmations; do not copy them wholesale.
6. Use the current Lectures 1–8, including Lecture 7: Design and Implementation. Exclude the unrelated EJS/Middleware/Session lecture and its cached extracts.

## Current state

- Nine university computer science students. A local Delivery frontend/backend is now implemented; the complete shared LMS and other role implementations have not been established.
- User-confirmed on 4 November: five Coding members and four Testing members. Coding has one full-stack owner for each of Client, Warehouse, Delivery and Admin, plus one Database owner; names and Database scope remain undecided. Role interfaces follow the HTML prototype style.
- For each new feature the team creates a Git branch, sends each completed small function to Testing, and plans final integration into one program. The user clarified that the earlier word "Batch" meant Git branch. Shared-function ownership, integration responsibility, merge rules and sprint length remain undecided.
- The Delivery module was implemented with ChatGPT assistance on 5 November. Local backend tests, HTTP checks, desktop/mobile browser checks, an actual UI signature flow, restart persistence and offline backup/restore checks passed; this is not evidence of full LMS integration or student Testing-team execution.
- The original HTML UI artifacts remain draft references. The separate Delivery app now saves its own local records through a Java backend.
- A member supplied the unapproved Database Draft v0.4 and two ERDs for review. They are discussion designs, not an implemented or approved schema; see [review findings](../database/LMS_Database_Draft_v0_4_Review.md).
- Payment provider/interface decision: [UNDECIDED — see U-16](PROJECT_DETAILS.md#u-16-payment-provider-and-checkout-interface). Payment-account setup and integration have not started.
- Backend direction: Java/Spring Boot. Frontend: role-based web interfaces. Database direction: relational.
- Delivery-specific local choices: React, Spring Boot and Java 21; H2/private local proof files are Place holder fallbacks for shared Database/private cloud integration. Overall team contracts, database product and deployment remain open.
- The user selected the enhanced B direction from PROJECT_DETAILS for Delivery, English UI/simple English comments, and a working local demo with exact Place holder labels and future-owner notes. Do not substitute literal questionnaire B answers or claim nine-member approval.
- Delivery app: [start and source guide](../../README.md), [U-01–U-16 decisions](../DECISIONS.md), [integration handoff](../INTEGRATION.md), [actual test evidence](../TESTING.md). No real cloud deployment, external payment/refund integration or hardware camera test is claimed.

## Confirmed scope

- One retailer sells its own products and uses its own warehouse and delivery staff.
- Hong Kong delivery, HKD, one warehouse with product totals; shelf labels are descriptive.
- Four roles: Client, Warehouse Worker, Delivery Worker and Admin.
- One physical package per order, containing all ordered products and quantities.
- Simple multi-product order form; product total only, with delivery included.
- A client may specify another recipient. Saved delivery defaults affect future orders; placed orders retain their own details.
- Changes to placed unpaid orders use cancellation and creation of a replacement order.
- Supplier purchasing, a full shopping cart, loyalty/coupons/reviews, separate customer-service/payment roles, route optimization, GPS/live maps and email notifications are outside the first-version baseline.

## Workflow and correctness

Normal order flow:

    AWAITING_PAYMENT -> READY_FOR_PICKING -> PICKING
    -> READY_FOR_PICKUP -> SHIPPED -> OUT_FOR_DELIVERY -> DELIVERED

- Ordinary client cancellation stops at Start Picking.
- Order, payment and refund statuses are separate.
- Reserve stock immediately before checkout. Scans update picking progress. Accepted package collection reduces physical and reserved stock together.
- Available stock is physical stock minus reservations in the normal accounting case. Low-stock warnings use availability and trigger below the product threshold.
- Warehouse reports shortages; Admin chooses waiting for restock or full cancellation/refund.
- Successful delivery needs signature or photo proof. Failures retain distinct attempt records.
- Failed-delivery packages return to Warehouse. Redelivery requires confirmed receipt, a recorded client instruction and Admin approval, followed by another pickup.
- Clients can correct that order's delivery details after return confirmation and before redelivery approval, then request redelivery or cancellation. Tracking-page controls and verified manual customer-service fallback lead to the same recorded process.
- Post-failure cancellation requires Admin approval. A pending cancellation request blocks redelivery approval.
- One collector claims each Warehouse-created pickup list. Each package scan records actual collection; list claim alone changes neither custody nor stock.
- Cannot collect before pickup retains a reasoned list record. After recorded pickup, use a Warehouse-confirmed return, without inventing a failed customer attempt.
- Admin can reassign picking tasks while preserving progress and original authorship. Removed products/users are deactivated, with history preserved.

### Payment timing

- Use an external provider's test environment for one-time HKD order payments; retain the agreed test-card scope. Provider and checkout interface: [UNDECIDED — see U-16](PROJECT_DETAILS.md#u-16-payment-provider-and-checkout-interface). No real charges are intended.
- Each checkout has a 30-minute entry/retry window. Declined cards can be retried in that checkout while its reservation stays held.
- Release an unsuccessful checkout's reservation after confirming it ended unpaid, not simply after an individual card decline.
- Payment must be verified by the LMS within 24 hours of order creation. This deadline overrides a later checkout expiry.
- Only orders still lacking accepted payment expire. A duplicate message for payment already accepted on time must not cause cancellation, refund or repeated fulfilment.
- Final cancellation releases stock and prevents fulfilment permanently. Continue resolving any uncertain payment; refund a later confirmed successful charge without reviving the order.
- One active checkout per order; duplicate messages or repeated clicks must not duplicate business effects.

## Assessment milestones

| Component | Confirmed requirement |
|---|---|
| In-class progress review | 23 November 2026; 10 minutes; 10 percentage points of the course |
| Recorded project presentation | 15–25 minutes; 10 percentage points; deadline: [UNDECIDED — see U-04](PROJECT_DETAILS.md#u-04-recorded-presentation-details) |
| Group report/code | 8 December 2026; report PDF at most 1,000 words; accessible GitHub link on first line, with installation/run guide; 15 percentage points |
| Individual project logbook | Final deadline 8 December 2026; 15 percentage points; interim dates 30 September, 15 October, 30 October and 15 November |

Logbooks are individual: meeting summary at most 200 words per meeting, personal meeting contribution at most 100 words, plus contributions to the project/report/presentation. Do not invent meetings, contributions or test results.

## Decision placeholders

Full alternatives and analysis appear only in the final Undecided ideas section of PROJECT_DETAILS.md.

Each undecided entry includes its system/workflow impact, a recommended reliable plan, a more capable plan requiring additional effort, and examples. Recommendations prioritize reliability and development time, including integration and verification of AI-assisted code. Numerical examples are proposals with stated assumptions; none is an approved target or passed result.

| Topic | Status |
|---|---|
| Deployment | [UNDECIDED — see U-01](PROJECT_DETAILS.md#u-01-deployment) |
| Payment provider and checkout interface | [UNDECIDED — see U-16](PROJECT_DETAILS.md#u-16-payment-provider-and-checkout-interface) |
| Numerical quality targets and test conditions | Redefine as measurable criteria; values [UNDECIDED — see U-02](PROJECT_DETAILS.md#u-02-measurable-quality-criteria) |
| Work division and process details | Five Coding/four Testing, role full-stack ownership, feature branches and small-function Testing handoffs confirmed; Database scope, shared owners, integration/merge details and sprint length [UNDECIDED — see U-03](PROJECT_DETAILS.md#u-03-team-work-division-and-development-process) |
| Architecture and technology details | [UNDECIDED — see U-05](PROJECT_DETAILS.md#u-05-architecture-and-technology-details) |
| Remaining operational/privacy details | See U-06 through U-15 in the detailed record |

## How to continue

- Explain and review the source before adopting its content. Ask about material ambiguities; do not turn an example into a requirement.
- Keep baseline, user-confirmed refinements, proposals, observed defects and completed work distinct.
- Preserve the user's ordinary Codex response tone: precise, professional and candid.
- Update both records when a decision changes. Replace its main-section placeholder, record its provenance in decision history, and close the corresponding undecided entry.
- Internal context files are not the assessed 1,000-word report and are not subject to that submission word limit.
