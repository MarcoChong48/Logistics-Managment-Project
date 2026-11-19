const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const baseURL = process.env.DELIVERY_TEST_URL || 'http://127.0.0.1:8080';
const evidence = path.join(__dirname, 'evidence');
const packageId = 'PKG-1002';
const orderReference = 'LMS-1002';
const failureNote = 'The demo building entrance was closed.';
const returnReason = 'Returning the package after a failed delivery.';

function openPage(page, name) {
  return page.getByRole('link', { name, exact: true }).click();
}

async function confirm(page, name) {
  const dialog = page.getByRole('dialog');
  await dialog.getByRole('button', { name, exact: true }).click();
  await dialog.waitFor({ state: 'hidden' });
}

async function checkHandoff(page, status) {
  const row = page.getByRole('table', { name: 'Return handoff records' })
    .getByRole('row').filter({ hasText: orderReference });
  await row.getByText(status, { exact: true }).waitFor();
  await row.getByText(`“${returnReason}”`, { exact: true }).waitFor();
}

async function simulate(page, action, expectedStatus) {
  await openPage(page, 'Test tools');
  await page.locator(`[data-test-package="${packageId}"]`).getByRole('button', { name: action, exact: true }).click();
  await confirm(page, action);
  await openPage(page, 'Return handoffs');
  await checkHandoff(page, expectedStatus);
}

// Use a demo database where PKG-1002 has not been collected. This can run after ui-flow.cjs.
async function run() {
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
    page.setDefaultTimeout(15000);
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.addInitScript(() => {
      // Force the same denial each run without requiring a physical camera.
      Object.defineProperty(navigator.mediaDevices, 'getUserMedia', {
        value: async () => {
          window.cameraPermissionDenied = true;
          throw new DOMException('Camera access denied for this test.', 'NotAllowedError');
        },
      });
    });
    await page.goto(baseURL, { waitUntil: 'networkidle' });
    await page.getByLabel('Username', { exact: true }).fill('ken');
    await page.getByLabel('Password', { exact: true }).fill('Delivery123!');
    await page.getByRole('button', { name: 'Log in', exact: true }).click();
    await page.getByTestId('workspace-shell').waitFor();

    await openPage(page, 'Pickup');
    const [decoder] = await Promise.all([
      page.waitForResponse(response => response.url().endsWith('/assets/shared/vendor/zxing-browser.min.js')),
      page.getByRole('button', { name: 'Scan pickup list with camera', exact: true }).click(),
    ]);
    const cameraDialog = page.getByRole('dialog', { name: 'Scan a QR code' });
    await cameraDialog.getByText('Camera unavailable. Enter the QR code manually.', { exact: true }).waitFor();
    assert.ok(decoder.ok(), 'The bundled QR decoder loaded.');
    assert.equal(await page.evaluate(() => window.cameraPermissionDenied), true);
    await cameraDialog.getByRole('button', { name: 'Use manual entry', exact: true }).click();

    const list = page.locator('[data-pickup-list="PL-1001"]');
    await list.waitFor();
    if (!(await list.getByText('Claimed by you', { exact: true }).isVisible())) {
      await page.getByLabel('Pickup list QR code', { exact: true }).fill('PL-1001');
      await page.getByRole('button', { name: 'Claim list', exact: true }).click();
    }
    await list.getByText('Claimed by you', { exact: true }).waitFor();
    await list.getByLabel('Package QR code', { exact: true }).fill(packageId);
    await list.getByRole('button', { name: 'Collect package for pickup list PL-1001', exact: true }).click();
    await list.getByRole('row').filter({ hasText: orderReference }).getByText('Collected', { exact: true }).waitFor();

    await openPage(page, 'My deliveries');
    await page.getByRole('button', { name: `Start delivery for order ${orderReference}`, exact: true }).click();
    await confirm(page, 'Start delivery');
    await page.getByRole('link', { name: `Record outcome for order ${orderReference}`, exact: true }).click();
    const delivered = page.getByRole('tab', { name: 'Delivered', exact: true });
    const failed = page.getByRole('tab', { name: 'Failed', exact: true });
    await delivered.press('ArrowRight');
    assert.equal(await failed.getAttribute('aria-selected'), 'true');
    const failure = page.getByRole('tabpanel', { name: 'Failed', exact: true });
    await failure.getByRole('combobox', { name: /^Failure reason/ }).selectOption('OTHER');
    const record = failure.getByRole('button', { name: 'Record failed attempt', exact: true });
    assert.equal(await record.isDisabled(), true, 'Other requires a note.');
    await failure.getByRole('textbox', { name: /^Internal note/ }).fill(failureNote);
    await record.click();
    await confirm(page, 'Record failed attempt');

    await openPage(page, 'Attempt history');
    const attempt = page.getByRole('row').filter({ hasText: orderReference });
    await attempt.getByText('Failed', { exact: true }).waitFor();
    await openPage(page, 'Return handoffs');
    await page.getByRole('button', { name: `Report return for order ${orderReference}`, exact: true }).click();
    await page.getByRole('dialog').getByLabel('Return reason', { exact: true }).fill(returnReason);
    await confirm(page, 'Report handoff');
    await checkHandoff(page, 'Waiting for Warehouse receipt');

    await simulate(page, 'Receive intact', 'Waiting for client');
    await simulate(page, 'Request redelivery', 'Waiting for Admin approval');
    await simulate(page, 'Approve redelivery', 'Ready for redelivery');

    await openPage(page, 'Pickup');
    const newList = page.locator('[data-pickup-list]:not([data-pickup-list="PL-1001"])').filter({
      has: page.getByRole('row').filter({ hasText: orderReference }),
    });
    const retry = newList.getByRole('row').filter({ hasText: orderReference });
    await retry.getByText('Ready for redelivery', { exact: true }).waitFor();

    await openPage(page, 'Notifications');
    await page.getByRole('checkbox', { name: 'Unread only', exact: true }).check();
    const notification = page.getByRole('article').filter({ hasText: 'Delivery failed: OTHER.' });
    await notification.getByText('Unread', { exact: true }).waitFor();
    assert.doesNotMatch(await notification.innerText(), /demo building entrance/i, 'Internal notes must stay out of notifications.');
    assert.equal(errors.length, 0, errors.join('\n'));
    return {
      status: 'passed',
      pickupFallback: 'Camera permission denied, decoder loaded, manual claim and collection worked.',
      handoffStates: ['Waiting for Warehouse receipt', 'Waiting for client', 'Waiting for Admin approval', 'Ready for redelivery'],
      redeliveryPickup: 'A new pickup list contains the approved package.',
      notification: 'Unread event shows the standard failure reason.',
      pageErrors: errors,
    };
  } finally {
    await browser.close();
  }
}

run().then(result => {
  fs.mkdirSync(evidence, { recursive: true });
  fs.writeFileSync(path.join(evidence, 'return-flow-result.json'), JSON.stringify(result, null, 2) + '\n');
  console.log(JSON.stringify(result));
}).catch(error => {
  console.error(error);
  process.exitCode = 1;
});
