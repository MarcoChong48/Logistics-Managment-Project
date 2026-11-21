# Local delivery walkthrough

This walkthrough uses local demo data. Check the running interface for the seeded list and package IDs. Use fictional data only. Delivery work is saved through the Java backend. **Test tools** appears in demo mode and identifies actions that simulate other roles as **Place holder**.

## Preparation

1. Start Docker Desktop in Linux containers mode. From `features/delivery/`, run `docker compose up --build -d` followed by `docker compose ps`. When `db` is healthy and `backend` is running, open `http://127.0.0.1:8080`. [Launch instructions](../README.md#run-the-local-demo-with-docker-compose)
2. Sign in as `ken` with `Delivery123!`.
3. Open **Pickup** to see the lists assigned to you. In a fresh demo, Ken receives `PL-1001` and Mei receives `PL-1002`. This walkthrough uses `PKG-1001` on Ken’s list. Use the actual IDs shown in your demo, and select **Refresh** to load new assignments.
4. Explain that the list and packed orders use **Place holder** Warehouse data. Open **Decisions & integrations** for the team decisions and external handoffs.

The local store persists. Use a fresh separate demo data directory for an independent run if supported by the package; do not reset another person's saved work.

Run `docker compose down` from the same folder after testing. PostgreSQL records and proof files are saved in separate volumes for the next session.

## Normal delivery

1. At the warehouse, scan your assigned list QR code in **Pickup**. For manual entry, enter the code and select **Confirm pickup list**. Once pickup is confirmed, collect each package separately.
2. Scan or enter one package QR from that list. Wait for backend acceptance and the workspace refresh. The package becomes Shipped. The backend saves its actual holder and the pickup event.
3. Try the same pickup again. It must not create another stock/custody effect.
4. Open **My deliveries** and select **Start delivery** for that package. Confirm the action. That order becomes Out for delivery and a new attempt begins.
5. Open **Record outcome**, capture a signature or choose a JPEG/PNG proof image, then save it. Wait for the private upload to succeed.
6. Confirm delivery with proof from this attempt. Open **Attempt history** to find the completed outcome and proof metadata. Contact and image access end when the active attempt closes.

## Failure, return and approved redelivery

Use another eligible package so the successful order remains complete.

1. Confirm the assigned list at the warehouse, collect the individual package and select **Start delivery**. If the list is already confirmed, scan the remaining package on that list.
2. Open **Record outcome** and record a failure such as **Client not home**. Add an explanation for **Other**. The failed attempt stays in history, custody stays with the holder and stock quantities stay the same.
3. In **Return handoffs**, report the return and check for **Waiting for Warehouse receipt**. Select **Refresh status** to load changes saved by another session.
4. In **Test tools**, use the **Place holder** Warehouse action **Receive intact** for the package. This simulates the receipt that restores custody and physical and reserved stock. In **Return handoffs**, check for **Waiting for client**.
5. In **Test tools**, use the **Place holder** Client action **Request redelivery**. In **Return handoffs**, check for **Waiting for Admin approval**.
6. In **Test tools**, use the **Place holder** Admin action **Approve redelivery**. Approval freezes the instruction and delivery-detail version. In **Return handoffs**, check for **Ready for redelivery**. Warehouse retains custody until collection.
7. The system assigns the new list. Sign in as the worker who received it, then confirm the list and collect the same package again. The earlier pickup remains in history.
8. Start delivery again and record another outcome. In **Attempt history**, check for two separate attempts. Review any new local events in **Notifications**.

## Session and password

1. Watch **Session ends in** in the top bar. It counts down in real time. Refreshing workspace data updates it using the server's current deadline.
2. On a separate demo store, select **Change password**. Enter the current password and the new password twice. Use a different password with at least eight characters, including an uppercase letter, a lowercase letter and a digit.
3. Submit the change. Sign in again with the new password. Any other browser session for this account must reauthenticate on its next API request. The demo database keeps the changed password.

## Useful negative demonstrations

- Before step 4 above, try approval: a missing confirmed return/instruction must block it.
- Submit a simulated CANCEL instruction instead of REDELIVER, then try approval: pending cancellation must block redelivery. Approved cancellation is final; a local event does not prove a provider refund.
- Report a Vehicle full issue before collection: no failed customer visit should appear.
- Return a SHIPPED package before Start Delivery: preserve the pickup/return history without inventing an attempt.
- Sign in as `mei` in a separate browser session. Check that Mei sees her own lists. Try to confirm Ken’s list and verify that the request is denied.
- Use **Receive damaged** in **Test tools**: the package stays held; it cannot silently become sellable or eligible for redelivery.

## What to say about external parts

"This is the Delivery module running in demo mode. The Java backend saves the Delivery workflow. Test tools uses Place holder simulations of Warehouse receipt, Client instruction and Admin approval. Those teams provide the real authenticated actions when we integrate."

Record the results of the walkthrough in the [testing guide](TESTING.md).
