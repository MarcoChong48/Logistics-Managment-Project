# Reconciling legacy payment acceptance

A payment marked `ACCEPTED` with a null `accepted_at` enters `RECONCILIATION_REQUIRED`. For an active order, a verified provider capture leaves this marker in place. The capture proves funds were received. It cannot establish which payment already drove picking or dispatch. The application keeps the order, picking task, held stock and refund history at their current state.

## Responsibility and evidence

1. An Admin authorized for the relevant order or case owns the order review under the existing Admin role policy. Use the order and payment IDs to inspect the order timeline, all payment attempts, refund history, picking and custody events, and held, released or consumed reservations.
2. The team member authorized to access the payment provider verifies the captured amount, currency, capture time, and provider payment identity against the linked session and the LMS payment ID. Check for another LMS payment with the same provider reference, independent accepted payments and refunds already recorded for the capture. For unlinked claims, require independent evidence that ties the capture to the LMS order and attempt. Preserve the review marker when any evidence conflicts or is incomplete.
3. Record the deciding Admin, the payment reviewer, evidence references, the reason and the intended scope in the authorized operations record. Keep provider credentials and raw customer or provider payloads out of logs.

## Resolution paths

- **Cancellation is independently authorized.** Use the existing Admin case action or the eligible Client owner cancellation flow. That transaction releases applicable held reservations and closes pending entry. The next verified reconciliation of the cancelled payment can record one `REQUESTED` refund using the captured amount and unique provider reference. For linked Stripe sessions, the reconciliation sweep can obtain the evidence. Replaying the confirmation preserves the cancelled order and existing refund history.
- **The order should continue or has already progressed.** Step 2 provides no manual acceptance endpoint. An authorized, separately reviewed data correction is required to clear the marker on an active order. The designated database operator must lock the order row first and the affected payment rows second, then re-read payment, refund, picking, custody and reservation history and compare it with the approved evidence. Abort on a changed order, mismatched provider reference or amount, duplicate payment evidence, conflicting refund, or uncertain fulfilment. If the capture is the payment already used for a progressed order, any approved correction repairs the payment record and preserves the order status, stock and custody. For a waiting order, a correction may advance the order only if the review approves the transition and the locked data meets the existing checkout acceptance guards, including deadline, exact held quantities, no other accepted or unresolved payment and no prior fulfilment. Persist the verified payment identity, accepted timestamp, authorized order transition and any applicable idempotent notification in the same transaction. Leave the marker in place if the guards fail.
- **No capture is verified or the evidence is incomplete.** Keep the case in reconciliation until the authorized reviewer can establish the terminal payment outcome and an approved follow-up. Keep fulfilment and any refund request gated on the verified facts.

## Refund policy by entry point

- **Admin case action** preserves the Stage 9 policy. It selects the newest positive payment with a non-null acceptance timestamp and applies the existing refund history, status-check and retry rules.
- **Client cancellation** records an initial refund obligation for each distinct positive, timestamped accepted payment, under the same order transaction and each payment's lock. Matching refund history prevents duplicate obligations, and conflicting amounts abort the transaction for reconciliation. Admin's existing retry rules continue to govern recorded requests.
- **Verified late capture** uses the provider-confirmed amount and payment identity. The resulting `REQUESTED` row records a refund obligation. Completion requires separate provider confirmation.

This workflow preserves Delivery pickup as the physical stock-consumption point.
