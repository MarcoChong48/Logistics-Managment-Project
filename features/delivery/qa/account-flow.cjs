const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

// Run this check on a disposable demo server after the other browser checks.
const baseURL = process.env.DELIVERY_TEST_URL || 'http://127.0.0.1:8080';
const initialPassword = 'Delivery123!';
const temporaryPassword = 'DeliveryQa2026!';
const evidence = path.join(__dirname, 'evidence');

function apiResponse(page, pathname, method) {
  return page.waitForResponse(response =>
    new URL(response.url()).pathname === pathname && response.request().method() === method);
}

async function logIn(page, password, expectedStatus = 200) {
  await page.getByTestId('login-page').waitFor();
  await page.getByLabel('Username', { exact: true }).fill('ken');
  await page.getByLabel('Password', { exact: true }).fill(password);
  const [response] = await Promise.all([
    apiResponse(page, '/api/login', 'POST'),
    page.getByRole('button', { name: 'Log in', exact: true }).click(),
  ]);
  assert.equal(response.status(), expectedStatus);
  if (expectedStatus === 200) {
    await page.getByTestId('workspace-shell').waitFor();
    await page.getByText('Test mode', { exact: true }).waitFor();
  } else {
    await page.getByText(/^Sign-in failed\./).waitFor();
  }
}

async function submitPassword(page, currentPassword, newPassword) {
  await page.getByRole('heading', { name: 'Change your password', exact: true }).waitFor();
  await page.getByLabel('Current password', { exact: true }).fill(currentPassword);
  await page.getByLabel('New password', { exact: true }).fill(newPassword);
  await page.getByLabel('Repeat new password', { exact: true }).fill(newPassword);
  const [response] = await Promise.all([
    apiResponse(page, '/api/change-password', 'POST'),
    page.getByRole('button', { name: 'Change password', exact: true }).click(),
  ]);
  return response;
}

function passwordLink(page) {
  return page.getByRole('link', { name: 'Change password', exact: true });
}

function countdownSeconds(value) {
  assert.match(value, /^\d{2,}:[0-5]\d$/);
  const [minutes, seconds] = value.split(':').map(Number);
  return minutes * 60 + seconds;
}

async function verifyCountdown(page, workspaceResponse) {
  const timer = page.getByRole('timer', { name: 'Time until session expires' });
  const headers = workspaceResponse.headers();
  const serverTime = Number(headers['x-server-time']);
  const deadline = Number(headers['x-session-expires-at']);
  const expectedSeconds = Math.ceil((deadline - serverTime) / 1000);
  assert.ok(serverTime > 0 && expectedSeconds > 0, 'The server must supply a future session deadline.');

  const displayedSeconds = countdownSeconds(await timer.innerText());
  assert.ok(displayedSeconds <= expectedSeconds && displayedSeconds >= expectedSeconds - 2,
    'The displayed countdown must match the server deadline.');
  await page.waitForFunction(before => {
    const value = document.querySelector('[data-countdown]')?.textContent;
    if (!value || !/^\d{2,}:[0-5]\d$/.test(value)) return false;
    const [minutes, seconds] = value.split(':').map(Number);
    return minutes * 60 + seconds < before;
  }, displayedSeconds, { timeout: 5_000 });
}

async function verifyIdleExpiry(page) {
  if (process.env.DELIVERY_TEST_IDLE_EXPIRY !== '1') return 'not run';
  const remaining = countdownSeconds(await page.getByRole('timer').innerText());
  assert.ok(remaining > 0 && remaining <= 60, 'Use a one-minute demo session timeout to test idle expiry.');
  await page.getByText('Your session ended. Log in again.', { exact: true })
    .waitFor({ timeout: (remaining + 5) * 1000 });
  await page.getByTestId('login-page').waitFor();
  return 'passed';
}

(async () => {
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  const errors = [];
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
    const otherSession = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
    for (const tab of [page, otherSession]) tab.on('pageerror', error => errors.push(error.message));
    await page.goto(baseURL);
    const [workspaceResponse] = await Promise.all([
      apiResponse(page, '/api/delivery/workspace', 'GET'),
      logIn(page, initialPassword),
    ]);
    await verifyCountdown(page, workspaceResponse);
    await otherSession.goto(baseURL);
    await logIn(otherSession, initialPassword);

    let passwordChanged = false;
    try {
      await passwordLink(page).click();
      const wrongPassword = await submitPassword(page, 'IncorrectDemoPassword1!', temporaryPassword);
      assert.equal(wrongPassword.status(), 400);
      await page.getByText('Your current password is incorrect.', { exact: true }).waitFor();

      const changed = await submitPassword(page, initialPassword, temporaryPassword);
      passwordChanged = changed.ok();
      assert.equal(changed.status(), 200);
      await page.getByTestId('login-page').waitFor();
      await page.getByText('Password changed. Sign in again.', { exact: true }).waitFor();

      await otherSession.reload();
      await otherSession.getByTestId('login-page').waitFor();
      await logIn(page, initialPassword, 401);
      await logIn(page, temporaryPassword);
    } finally {
      if (passwordChanged) {
        // Use a new session so cleanup still works if an earlier assertion failed.
        const recovery = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
        await recovery.goto(baseURL);
        await logIn(recovery, temporaryPassword);
        await passwordLink(recovery).click();
        const restored = await submitPassword(recovery, temporaryPassword, initialPassword);
        assert.equal(restored.status(), 200, 'Restore the original demo password.');
        await recovery.getByTestId('login-page').waitFor();
        await logIn(recovery, initialPassword);
        await recovery.close();
      }
    }

    await page.reload();
    await logIn(page, initialPassword);
    const idleExpiry = await verifyIdleExpiry(page);
    assert.equal(errors.length, 0, errors.join('\n'));
    const result = {
      status: 'passed',
      serverCountdown: 'passed',
      idleExpiry,
      passwordChange: 'passed',
      otherSessionRevoked: 'passed',
      originalPasswordRestored: 'passed',
      pageErrors: errors,
    };
    fs.mkdirSync(evidence, { recursive: true });
    fs.writeFileSync(path.join(evidence, 'account-flow-result.json'), JSON.stringify(result, null, 2));
    console.log(JSON.stringify(result));
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
