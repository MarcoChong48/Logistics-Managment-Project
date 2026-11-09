const { chromium } = require('playwright');
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');

// Run against a fresh local demo server. This does not use real customer data.
const baseURL = process.env.DELIVERY_TEST_URL || 'http://127.0.0.1:8080';
const evidence = path.join(__dirname, 'evidence');
fs.mkdirSync(evidence, { recursive: true });
let testBrowser;

(async () => {
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  testBrowser = browser;
  const errors = [];
  const desktop = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
  desktop.on('pageerror', error => errors.push(error.message));
  await desktop.goto(baseURL, { waitUntil: 'networkidle' });
  await desktop.getByLabel(/username/i).fill('ken');
  await desktop.getByLabel(/password/i).fill('Delivery123!');
  await desktop.getByRole('button', { name: /sign in/i }).click();
  await desktop.getByTestId('workspace-shell').waitFor({ state: 'visible' });
  await desktop.screenshot({ path: path.join(evidence, 'desktop.png'), fullPage: true });
  assert.match(await desktop.locator('body').innerText(), /Place holder/);
  assert.equal(await desktop.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), true, 'Desktop must not scroll sideways.');

  const mobile = await browser.newPage({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true });
  mobile.on('pageerror', error => errors.push(error.message));
  await mobile.goto(baseURL, { waitUntil: 'networkidle' });
  await mobile.getByLabel(/username/i).fill('ken');
  await mobile.getByLabel(/password/i).fill('Delivery123!');
  await mobile.getByRole('button', { name: /sign in/i }).click();
  await mobile.getByTestId('workspace-shell').waitFor({ state: 'visible' });
  await mobile.screenshot({ path: path.join(evidence, 'mobile.png'), fullPage: true });
  assert.equal(await mobile.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), true, 'Phone must not scroll sideways.');
  assert.equal(errors.length, 0, errors.join('\n'));
  await browser.close();
  const result = { browser: 'Chrome headless', desktop: '1440 x 1000', mobile: '390 x 844', login: 'passed', placeHolderNotice: 'passed', horizontalOverflow: 'none', pageErrors: errors, cameraHardware: 'not tested' };
  fs.writeFileSync(path.join(evidence, 'browser-result.json'), JSON.stringify(result, null, 2));
  console.log(JSON.stringify(result));
})().catch(error => { console.error(error); process.exitCode = 1; }).finally(async () => { if (testBrowser) await testBrowser.close(); });
