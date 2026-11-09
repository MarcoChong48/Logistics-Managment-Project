# Local delivery walkthrough

This script describes the intended seeded workflow. Check the running interface for exact list/package IDs. Use fictional data only. The app must save real local Delivery changes through Java; a **Place holder** sandbox button simulates a different role and must be announced as such during a demonstration.

## Preparation

1. Build and launch the app from `features/delivery`, then open `http://localhost:8080`. You can also use the development interface on 5173 with the backend on 8080. [Build and launch instructions](../README.md)
2. Sign in as `ken` with `Delivery123!`.
3. Open the pickup workspace. The contract uses example list `PL-1001` and package `PKG-1001`; select an actual open seeded list if the build uses different IDs.
4. Explain that the list and packed orders are **Place holder** Warehouse data. They do not demonstrate payment or packing by another real module.

The local store persists. Use a fresh separate demo data directory for an independent run if supported by the package; do not reset another person's saved work.

## Normal delivery

1. Scan or manually enter a pickup-list QR and claim it. Show that claiming assigns one collector but does not mean the packages were collected.
2. Scan one package QR from that list. Wait for backend acceptance and the workspace refresh. The package becomes SHIPPED; its actual holder and pickup event are saved.
3. Try the same pickup again. It must not create another stock/custody effect.
4. Select Start Delivery for that package. Only this order becomes OUT_FOR_DELIVERY and a new attempt begins.
5. Capture a signature or choose a real JPEG/PNG proof image. Wait for a successful private upload.
6. Confirm Delivered. The backend requires valid proof belonging to this attempt. Show the completed outcome; contacts/private proof should no longer be available through active-handler access after completion.

## Failure, return and approved redelivery

Use another eligible package so the successful order remains complete.

1. Claim its list, collect the individual package and Start Delivery.
2. Record a failure such as Client not home. Other requires an explanation. Show a separate failed attempt; failure leaves the package with its actual holder and does not restore stock.
3. Request return. Explain that this records a handoff pending Warehouse confirmation, not a receipt.
4. Open the integration sandbox and identify the next control aloud: **Place holder — simulated Warehouse receipt**. Confirm an intact return. Show that actual Warehouse receipt is the point at which local physical/reserved quantities and custody are restored.
5. Identify **Place holder — simulated Client instruction**. Submit a new REDELIVER instruction for this return. This substitutes for the future authenticated Client workflow.
6. Identify **Place holder — simulated Admin approval**. Approve redelivery. This substitutes for the future Admin authorization and freezes the instruction/delivery-detail version. Approval creates eligibility; it does not give a worker physical possession.
7. Find the newly created eligible list/cycle. Claim it and scan the same package again. This second legitimate pickup must succeed; the earlier cycle remains in history.
8. Start Delivery again and record another outcome. Confirm that attempt one was preserved and attempt two is separate.

## Useful negative demonstrations

- Before step 4 above, try approval: a missing confirmed return/instruction must block it.
- Submit a simulated CANCEL instruction instead of REDELIVER, then try approval: pending cancellation must block redelivery. Approved cancellation is final; a local event does not prove a provider refund.
- Report a Vehicle full issue before collection: no failed customer visit should appear.
- Return a SHIPPED package before Start Delivery: preserve the pickup/return history without inventing an attempt.
- Log in as `mei` in a separate browser session: another worker cannot collect Ken's claimed list or view his private proof.
- Use the damaged-return sandbox option: the package stays held; it cannot silently become sellable or eligible for redelivery.

## What to say about external parts

"This is a local Delivery module. The Java backend saves the Delivery workflow. Warehouse receipt, Client instruction and Admin approval controls are Place holder simulations. The relevant owners must replace them with authenticated shared-role actions and the final database. Cloud deployment, notification delivery and provider refunds are not demonstrated here."

The walkthrough is a script, not a test report. Record what actually ran and any missing controls in [TESTING.md](TESTING.md).
