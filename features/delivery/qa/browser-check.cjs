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
  await desktop.getByLabel('Username', { exact: true }).fill('ken');
  await desktop.getByLabel('Password', { exact: true }).fill('Delivery123!');
  await desktop.getByRole('button', { name: 'Log in', exact: true }).click();
  await desktop.getByTestId('workspace-shell').waitFor({ state: 'visible' });
  await desktop.screenshot({ path: path.join(evidence, 'desktop.png'), fullPage: true });
  assert.equal(await desktop.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), true, 'Desktop must not scroll sideways.');
  await desktop.getByText('Test mode', { exact: true }).waitFor();
  await desktop.getByRole('link', { name: 'Decisions & integrations', exact: true }).click();
  await desktop.getByText('Place holder', { exact: true }).first().waitFor();

  const mobile = await browser.newPage({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true });
  mobile.on('pageerror', error => errors.push(error.message));
  await mobile.goto(baseURL, { waitUntil: 'networkidle' });
  await mobile.getByLabel('Username', { exact: true }).fill('ken');
  await mobile.getByLabel('Password', { exact: true }).fill('Delivery123!');
  await mobile.getByRole('button', { name: 'Log in', exact: true }).click();
  await mobile.getByTestId('workspace-shell').waitFor({ state: 'visible' });
  await mobile.screenshot({ path: path.join(evidence, 'mobile.png'), fullPage: true });
  assert.equal(await mobile.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), true, 'Phone must not scroll sideways.');
  const testTools = mobile.getByRole('link', { name: 'Test tools', exact: true });
  await testTools.waitFor({ state: 'hidden' });
  await mobile.getByRole('button', { name: 'Menu', exact: true }).click();
  await testTools.click();
  await mobile.getByRole('heading', { name: 'Test tools', exact: true }).waitFor();
  await testTools.waitFor({ state: 'hidden' });
  assert.equal(await mobile.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), true, 'Test tools must not scroll sideways.');
  await mobile.setViewportSize({ width: 320, height: 740 });
  const pages = ['Overview', 'Pickup', 'My deliveries', 'Return handoffs', 'Attempt history', 'Notifications', 'Decisions & integrations', 'Test tools'];
  for (const name of pages) {
    const menu = mobile.getByRole('button', { name: 'Menu', exact: true });
    await menu.click();
    assert.equal(await mobile.locator('#delivery-main').getAttribute('inert'), '');
    await mobile.getByRole('link', { name, exact: true }).click();
    await mobile.getByRole('heading', { name, exact: true, level: 1 }).waitFor();
    assert.equal(await mobile.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), true, `320 px: ${name} must not scroll sideways.`);
  }
  assert.equal(errors.length, 0, errors.join('\n'));
  const result = { browser: 'Chrome headless', desktop: '1440 x 1000', mobile: '390 x 844', login: 'passed', placeHolderNotice: 'passed', mobileMenu: 'passed', horizontalOverflow: 'none', narrowMobile: 'all 8 pages at 320 x 740', pageErrors: errors, cameraHardware: 'not tested' };
  fs.writeFileSync(path.join(evidence, 'browser-result.json'), JSON.stringify(result, null, 2));
  console.log(JSON.stringify(result));
})().catch(error => { console.error(error); process.exitCode = 1; }).finally(async () => { if (testBrowser) await testBrowser.close(); });
