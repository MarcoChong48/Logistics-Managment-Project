# Local delivery decisions

Date: 5 November 2026, Hong Kong time.

The user requested the enhanced B direction for this Delivery implementation and allowed a standalone local demo with clearly identified external **Place holder** parts. This is a module implementation choice. It does not claim that all nine members approved B for the full LMS, replace official course instructions, or approve Database Draft v0.4.

The table maps all sixteen unresolved topics in `PROJECT_DETAILS.md`. Settings are the local contract and must be checked against the running implementation. **Place holder** work remains external even when a local event or sandbox button demonstrates the boundary.

| Topic | Local treatment of B | Concrete setting or boundary |
|---|---|---|
| U-01 Deployment | B operating direction; cloud work is a **Place holder**. | Local server on 8080; React development on 5173. Repeatable build, health and recovery guidance support integration. The hosting owner must provide HTTPS hosting, a rehearsal environment, managed backups and alerts. No cloud site is claimed. |
| U-02 Quality criteria | B checks are test objectives, not results. | Candidate larger workload: 20 sessions, 500 products and 2,000 orders; named-operation p95 at most 2 seconds with at least 100 samples per operation. Media/provider waits are separate. Cross-role ordering/usability, 24-hour observation and timed recovery require the integrated system and are not demonstrated by this Delivery demo. |
| U-03 Division and process | B integration support within the confirmed five Coding/four Testing split. | API contract, repeatable checks and test scenarios support review. One full-stack owner per role plus Database remains the team arrangement. Names, merge authority, shared owners and sprint length are not assigned here. Git feature branches and small-function Testing handoffs remain unchanged. |
| U-04 Recorded presentation | Owned by the presentation team; outside Delivery runtime. | B permits edited chapters only if course instructions allow it. Preserve honest demo/test evidence; do not invent the official deadline, speaking requirements or a passed demonstration. |
| U-05 Architecture | B is the local implementation choice. | React browser app, Spring Boot JSON API, Java 21. Spring Boot 3.5.16 and Maven are the API contract's build choices. File-backed H2 is a **Place holder**, not the final team database. Shared interface/schema owners must review the replacement. |
| U-06 Discrepancies and receipt | B exception detail belongs to Warehouse/inventory. | Delivery can report a collection problem or request return. The Warehouse sandbox is a **Place holder** for actual receipt, quantities, condition and discrepancy cases. Damaged returns must stay unavailable/held; no automatic full sellable-stock restoration is permitted. |
| U-07 Packed replacement | Owned by Warehouse/Admin; not a Delivery editing function. | B full re-verification/checklist must be implemented by those owners. Delivery cannot remove a packing lock or repair an order by changing its contents. Report damage and preserve collection holds. |
| U-08 Instructions and verification | B version/approval boundary; authentication support is external. | The sandbox demonstrates a new post-return instruction tied to current return/details. Actual Client authentication, versioned editing, short-lived support authorization and recovery codes are **Place holder** work for Client/authentication owners. No recovery code or secret is requested by this app. |
| U-09 Proof storage and history | B retained-summary/cleanup direction, with local storage fallback. | Private decoded JPEG/PNG images; contract limit 5,242,880 bytes per file. Detailed attempts/proofs expire after one year; minimal permitted summaries remain for the agreed longer audit period. Cloud object storage and backup-expiry integration are **Place holder** work for the storage owner. Do not claim completed remote deletion. |
| U-10 Pickup recovery | B supervised stale-claim direction. | Flag 30 minutes without pickup activity for review. A flag never transfers custody. Only a Warehouse-presence/Admin sandbox release may release uncollected entries. Preserve old lists/cycles, block stale scans and require a new accepted pickup after return. No ordinary worker-to-worker handover. |
| U-11 Deactivation and roles | B impact/handover support belongs to Admin/authentication. | Local demo identities are separate workers. Shared account revocation, role-change preview, planned handover and last-Admin recovery are **Place holder** work. Physical custody never changes merely because assignment/account metadata changes. |
| U-12 Repeat failure | B review/reminder direction. | Local contract settings: review after two failures; reminder at three days after confirmed return; final review at seven days without instruction. These flag follow-up only. No automatic cancellation, fees, disposal or redelivery approval. Notification transport and final case closure are **Place holder** work. |
| U-13 Accounts and addresses | B address-book scope belongs to Client/authentication. | Seeded fictional contacts and delivery snapshots support the demo. Real registration validation, multiple saved addresses and profile updates are **Place holder** work. Editing a profile cannot change an existing order or an approved attempt's locked details. |
| U-14 Interface | B convenience direction limited to authorized Delivery data. | Delivery task/history dashboards and filtering may use this worker's records only. Client receipts and other-role dashboards are **Place holder** or outside this module. Only features visible in the delivered build may be called implemented. |
| U-15 Refund exceptions | B automated recheck/alert integration belongs to payment/Admin. | The sandbox can demonstrate final cancellation and save an event. Provider status queries, eligible retries, scheduling and actual refund outcomes are **Place holder** work. Never label a local cancellation event as Refunded. |
| U-16 Payments | B embedded-provider direction is external to Delivery. | Client/payment owners must select and configure the provider and embedded test-card component. Keep HKD, test-only full-order payment, the 30-minute entry window, 24-hour LMS confirmation, one active checkout and duplicate-effect protection. No provider, credentials or successful payment integration is claimed by this app. |

## Rules that B does not replace

One warehouse, one complete package per order, separate logistics/payment/refund states, actual per-package pickup, proof on delivery success, a recorded reason on failure, confirmed return before redelivery and a current Client instruction/Admin decision remain necessary.

Cancellation stays final. Pending cancellation blocks redelivery approval. Scanning products while picking is not a second physical-stock deduction. Actual collection and an inspected return are separate custody/stock events. An expired or duplicated request cannot create another movement.

## English comments

Keep comments brief and factual. Explain the rule, not every line of syntax. Examples:

```java
// Decision U-10, B: Flag a stale claim for review. Do not transfer custody.
// Place holder: The Warehouse owner must provide the actual return receipt.
```

These examples describe the intended comment style; they are not evidence that a feature has passed testing.
