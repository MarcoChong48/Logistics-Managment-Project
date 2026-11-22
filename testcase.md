# LMS Admin manual test cases

Follow the [preview setup guide](ADMIN_UI_PREVIEW.md) and open the [React preview](http://127.0.0.1:18092/). Run these checks on the preview’s test database with **Test tools** enabled. Use your local Admin account, generate fresh test cases for each run, and record the order references or refund numbers the tool returns.

### TC-01 · Admin sign-in and sign-out

1. In a private browser window, open `/admin/admin-dashboard.html` on the preview. Try the local Admin username with an incorrect password once.
2. Sign in with the correct password. On first sign-in, complete **Choose your own password** and sign in again with the new password.
3. Select **Log out**, then reopen the dashboard URL.

**Expected result.** The dashboard URL redirects signed-out users to **Log in**. An incorrect password shows an error. Valid credentials open the Dashboard after any required password change. Reopening the dashboard after logout returns to **Log in**.

### TC-02 · Warehouse shortage on a narrow screen

1. In **Test tools → Stage 6**, use **Create shortage report** with **Picking**, a missing quantity of `1`, and an empty order reference. Record the generated reference and the Dashboard's open shortage count.
2. Set the browser viewport to about 390 × 844 px. Use **Menu → Warehouse cases**, find the order, and select **Wait for restock**. Enter a reason and confirm.
3. Reload the page and select **Waiting restock**. Find the same order and check the Dashboard count again.

**Expected result.** The menu drawer and case card work without horizontal page scrolling. The case remains in **Waiting restock** after reload. The open shortage count decreases by one if no other cases changed.

### TC-03 · Approve a redelivery request

1. In **Test tools → Stage 7**, use **Create failed delivery case**. Set **Failures** to `1`, enable **Returned to Warehouse**, set **Days since return** to `4`, and choose **Redeliver** under **Client request**.
2. Record the order reference and the Dashboard's **Client requests** count. Open **Delivery cases → Needs decision**, find the order, and select **Approve redelivery**. Enter a reason and confirm.
3. Refresh the needs-decision list and check the Dashboard count again.

**Expected result.** The page confirms **Redelivery approved**. The order leaves **Needs decision**, and the Dashboard count decreases by one if no other requests changed.

### TC-04 · Retry a failed refund

1. In **Test tools → Stage 8**, use **Create refund case** with status **Failed** and **Age in hours** set to `30`. Record the refund number and order reference.
2. In **Refunds**, find that row, select **Retry refund**, enter a reason, and confirm.
3. In **Test tools → Stage 8 → Set provider answer**, enter the same refund number and set **Outcome** to **Failed**. Return to **Refunds** and retry again. Clear **Unresolved only** to inspect the original refund.

**Expected result.** The first retry is refused while the simulated provider has no confirmed failure. The second retry creates a replacement refund with a new number. The original row shows **Replaced by refund** followed by that number.

### TC-05 · Create, deactivate, and reactivate a client

1. In **Users**, select **Create user**. Choose the **Client** role and use a unique username such as `test_client_qa_01`. Submit the form, then close the temporary password panel.
2. Search for the exact username. In its **Actions** menu, choose **Deactivate**, enter a reason, and confirm. Keep the status filter set to show all statuses.
3. For the same row, choose **Actions → Reactivate**, enter a reason, and confirm.

**Expected result.** Creating the client issues a temporary password. The account status changes from **Active** to **Deactivated**, then returns to **Active**.

### TC-06 · Preview a product QR label

1. In **Products**, select **Add product**. Enter a unique SKU such as `QA-QR-001`, name `QA QR test`, price `35.50`, and **Low below** `4`. Save.
2. Search for the SKU. Choose **Actions → Show QR code**, then **Print label**. Cancel the browser's print dialog.

**Expected result.** The saved product can be found by SKU. The QR dialog and print preview show the selected SKU and its QR image. Cancelling returns to the Products page with the QR dialog still open.

### TC-07 · Open an order and export the filtered list

1. In **Test tools → Stage 5**, select **Create sample order set**.
2. In **Orders**, set **Order status** to **Delivered**. Open a result by its order reference and check its details.
3. Return to the filtered Orders list and select **Download CSV**. Open the file and check the `orderStatus` column and the order reference you opened.

**Expected result.** The order detail shows the reference from the list and a **Delivered** status. The CSV contains the selected order and every exported order has status `DELIVERED`.
