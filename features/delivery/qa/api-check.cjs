const { request, chromium } = require('playwright');
const assert = require('node:assert/strict');
const { randomUUID } = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');

// Run only against a fresh demo database. This suite changes its three seeded orders.
const baseURL = process.env.DELIVERY_TEST_URL || 'http://127.0.0.1:8080';
const evidenceFile = path.join(__dirname, 'evidence', 'api-result.json');
const report = {
  suite: 'Delivery HTTP contract checks',
  baseURL,
  startedAt: new Date().toISOString(),
  status: 'running',
  freshSeedRequired: true,
  simulatedActors: ['Warehouse Place holder', 'Client Place holder', 'Admin Place holder'],
  steps: [],
  requests: [],
  notCovered: ['Camera hardware', 'Real shared-role authentication', 'Cloud storage/deployment', 'External payment/refund', 'Restart/backup/retention', 'Direct stock ledger assertions (not exposed by this API)', 'Performance targets'],
};
const contexts = [];

async function step(name, action) {
  const started = Date.now();
  try {
    const detail = await action();
    report.steps.push({ name, status: 'passed', milliseconds: Date.now() - started, ...(detail ? { detail } : {}) });
    console.log(`PASS ${name}`);
  } catch (error) {
    report.steps.push({ name, status: 'failed', milliseconds: Date.now() - started, error: error.message });
    throw error;
  }
}

async function expectStatus(response, expected, label) {
  const statuses = Array.isArray(expected) ? expected : [expected];
  if (!statuses.includes(response.status())) {
    let safeMessage = '';
    try { safeMessage = (await response.json()).message || ''; } catch { /* The status remains useful if the response is not JSON. */ }
    assert.fail(`${label}: expected HTTP ${statuses.join('/')}, received ${response.status()}${safeMessage ? ` (${safeMessage})` : ''}`);
  }
  return response;
}

async function get(context, url, expected = 200) {
  const response = await context.get(url);
  report.requests.push({ method: 'GET', path: url, status: response.status() });
  return expectStatus(response, expected, `GET ${url}`);
}

async function newWorker(name) {
  const api = await request.newContext({ baseURL, timeout: 20000 });
  contexts.push(api);
  const worker = { name, api, session: null };
  worker.refresh = async () => {
    worker.session = await (await get(api, '/api/session')).json();
    return worker.session;
  };
  worker.login = async (password = 'Delivery123!', expected = 200) => {
    await worker.refresh();
    const response = await api.post('/api/login', {
      headers: { [worker.session.csrfHeader]: worker.session.csrfToken },
      form: { username: name, password },
      maxRedirects: 0,
    });
    report.requests.push({ method: 'POST', path: '/api/login', actor: name, status: response.status() });
    await expectStatus(response, expected, `login ${name}`);
    if (expected === 200) {
      await worker.refresh();
      assert.equal(worker.session.authenticated, true);
      assert.equal(worker.session.user.id, name);
      assert.equal(worker.session.user.role, 'DELIVERY');
    }
    return response;
  };
  worker.post = async (url, fields = {}, expected = 200) => {
    const body = { requestId: randomUUID(), ...fields };
    const response = await api.post(url, {
      headers: { [worker.session.csrfHeader]: worker.session.csrfToken },
      data: body,
    });
    report.requests.push({ method: 'POST', path: url, actor: name, status: response.status() });
    await expectStatus(response, expected, `POST ${url} as ${name}`);
    return response;
  };
  worker.workspace = async () => (await get(api, '/api/delivery/workspace')).json();
  worker.upload = async (packageId, buffer, options = {}, expected = 200) => {
    const response = await api.post(`/api/packages/${packageId}/proofs`, {
      headers: { [worker.session.csrfHeader]: worker.session.csrfToken },
      multipart: {
        type: options.type || 'PHOTO',
        requestId: options.requestId || randomUUID(),
        file: { name: options.name || 'qa-proof.png', mimeType: options.mimeType || 'image/png', buffer },
      },
    });
    report.requests.push({ method: 'POST', path: `/api/packages/${packageId}/proofs`, actor: name, status: response.status() });
    return expectStatus(response, expected, `proof upload as ${name}`);
  };
  return worker;
}

function packageState(workspace, id) {
  const row = workspace.demoPackages.find(item => item.id === id);
  assert.ok(row, `Sandbox package ${id} must exist.`);
  return row;
}

function task(workspace, id) {
  const row = workspace.tasks.find(item => item.id === id);
  assert.ok(row, `An authorized task for ${id} must exist.`);
  return row;
}

function attempts(workspace, id) {
  return workspace.attempts.filter(item => item.packageId === id);
}

function newList(workspace, packageId, oldListId) {
  const row = workspace.pickupLists.find(list => list.id !== oldListId && list.status !== 'CLOSED'
    && list.packages.some(item => item.id === packageId && item.collectionOutcome === 'PENDING'));
  assert.ok(row, `A new eligible pickup list for ${packageId} must exist.`);
  return row;
}

async function makePng() {
  // Chrome creates a real PNG. No external image package or remote fixture is used.
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  try {
    const page = await browser.newPage();
    const encoded = await page.evaluate(() => {
      const canvas = document.createElement('canvas');
      canvas.width = 160;
      canvas.height = 100;
      const pen = canvas.getContext('2d');
      pen.fillStyle = '#ffffff';
      pen.fillRect(0, 0, canvas.width, canvas.height);
      pen.strokeStyle = '#102c45';
      pen.lineWidth = 4;
      pen.beginPath();
      pen.moveTo(18, 66);
      pen.bezierCurveTo(35, 16, 53, 90, 78, 36);
      pen.bezierCurveTo(90, 90, 120, 32, 142, 58);
      pen.stroke();
      return canvas.toDataURL('image/png').split(',')[1];
    });
    return Buffer.from(encoded, 'base64');
  } finally {
    await browser.close();
  }
}

(async () => {
  const anonymous = await newWorker('ken');
  const ken = await newWorker('ken');
  const mei = await newWorker('mei');
  let png, proofId, retryListId;

  await step('Anonymous API access and login CSRF are enforced', async () => {
    const session = await anonymous.refresh();
    assert.equal(session.authenticated, false);
    assert.ok(session.csrfToken && session.csrfHeader);
    await get(anonymous.api, '/api/delivery/workspace', 401);
    const response = await anonymous.api.post('/api/login', { form: { username: 'ken', password: 'Delivery123!' }, maxRedirects: 0 });
    report.requests.push({ method: 'POST', path: '/api/login', status: response.status(), note: 'missing CSRF' });
    await expectStatus(response, 403, 'Login without CSRF');
    await anonymous.login('wrong-password', 401);
  });

  await step('Ken and Mei have independent authenticated Delivery sessions', async () => {
    await ken.login();
    await mei.login();
    assert.notDeepEqual(await ken.api.storageState(), await mei.api.storageState());
    const response = await ken.api.post('/api/pickup-lists/claim', { data: { qrCode: 'PL-1001', requestId: randomUUID() } });
    report.requests.push({ method: 'POST', path: '/api/pickup-lists/claim', status: response.status(), note: 'missing CSRF' });
    await expectStatus(response, 403, 'Mutation without CSRF');
  });

  await step('Fresh seeded data has no private contacts in pickup-list rows', async () => {
    const workspace = await ken.workspace();
    assert.equal(workspace.mode, 'demo', 'Run this suite against the demo profile.');
    assert.equal(workspace.tasks.length, 0, 'Use a fresh database; the suite never resets existing work.');
    for (const id of ['PKG-1001', 'PKG-1002', 'PKG-1003']) {
      assert.equal(packageState(workspace, id).status, 'READY_FOR_PICKUP');
      assert.equal(packageState(workspace, id).custody, 'WAREHOUSE');
    }
    for (const list of workspace.pickupLists) for (const item of list.packages) {
      for (const key of ['recipientName', 'phone', 'address']) assert.equal(Object.hasOwn(item, key), false, `${key} must not be exposed before actual pickup.`);
    }
  });

  await step('Start Delivery is blocked before actual pickup', async () => {
    await ken.post('/api/packages/PKG-1001/start', {}, [403, 409]);
    assert.equal(attempts(await ken.workspace(), 'PKG-1001').length, 0);
  });

  await step('Claim is exclusive and safe to retry; claim is not custody', async () => {
    const key = randomUUID();
    const fields = { qrCode: 'PL-1001', requestId: key };
    const first = await (await ken.post('/api/pickup-lists/claim', fields)).json();
    assert.deepEqual(await (await ken.post('/api/pickup-lists/claim', fields)).json(), first);
    await ken.post('/api/pickup-lists/claim', { qrCode: 'PL-1002', requestId: key }, 409);
    await mei.post('/api/pickup-lists/claim', { qrCode: 'PL-1001' }, 409);
    const workspace = await ken.workspace();
    assert.equal(workspace.pickupLists.find(item => item.id === 'PL-1001').claimedBy, 'ken');
    assert.equal(packageState(workspace, 'PKG-1001').custody, 'WAREHOUSE');
    assert.equal(packageState(workspace, 'PKG-1001').status, 'READY_FOR_PICKUP');
    assert.equal(workspace.tasks.length, 0);
  });

  await step('Unknown and wrong-list QR codes leave custody unchanged', async () => {
    await ken.post('/api/pickup-lists/claim', { qrCode: 'UNKNOWN-LIST' }, 404);
    await ken.post('/api/pickup-lists/PL-1001/collect', { packageQrCode: 'UNKNOWN-PACKAGE' }, 404);
    await ken.post('/api/pickup-lists/PL-1001/collect', { packageQrCode: 'PKG-1003' }, 409);
    assert.equal(packageState(await ken.workspace(), 'PKG-1003').custody, 'WAREHOUSE');
  });

  await step('Actual collection is once per cycle and belongs to its collector', async () => {
    const fields = { packageQrCode: 'PKG-1001', requestId: randomUUID() };
    const first = await (await ken.post('/api/pickup-lists/PL-1001/collect', fields)).json();
    assert.deepEqual(await (await ken.post('/api/pickup-lists/PL-1001/collect', fields)).json(), first);
    await ken.post('/api/pickup-lists/PL-1001/collect', { packageQrCode: 'PKG-1001' }, 409);
    await mei.post('/api/pickup-lists/PL-1001/collect', { packageQrCode: 'PKG-1002' }, 403);
    await mei.post('/api/packages/PKG-1001/start', {}, 403);
    const workspace = await ken.workspace();
    assert.equal(packageState(workspace, 'PKG-1001').custody, 'DELIVERY');
    assert.equal(task(workspace, 'PKG-1001').status, 'SHIPPED');
    assert.equal(packageState(workspace, 'PKG-1002').status, 'READY_FOR_PICKUP');
    assert.ok(task(workspace, 'PKG-1001').address);
    assert.equal((await mei.workspace()).tasks.some(item => item.id === 'PKG-1001'), false);
  });

  await step('Start creates one attempt and success requires saved proof', async () => {
    const fields = { requestId: randomUUID() };
    await ken.post('/api/packages/PKG-1001/start', fields);
    await ken.post('/api/packages/PKG-1001/start', fields);
    await ken.post('/api/packages/PKG-1001/start', {}, 409);
    assert.equal(attempts(await ken.workspace(), 'PKG-1001').length, 1);
    await ken.post('/api/packages/PKG-1001/outcome', { outcome: 'DELIVERED', proofIds: [] }, 400);
    assert.equal(task(await ken.workspace(), 'PKG-1001').status, 'OUT_FOR_DELIVERY');
  });

  await step('Proof content is validated and another worker cannot read it', async () => {
    await ken.upload('PKG-1001', Buffer.from('not an image'), {}, 400);
    png = await makePng();
    const saved = await (await ken.upload('PKG-1001', png)).json();
    proofId = saved.id;
    assert.ok(proofId);
    const response = await get(ken.api, `/api/proofs/${proofId}`);
    assert.match(response.headers()['content-type'], /^image\/png/);
    assert.match(response.headers()['cache-control'], /no-store/);
    assert.ok((await response.body()).length > 30);
    await get(mei.api, `/api/proofs/${proofId}`, 403);
    await mei.upload('PKG-1001', png, {}, 403);
  });

  await step('Successful delivery is final, repeat-safe and revokes handler access', async () => {
    const fields = { outcome: 'DELIVERED', proofIds: [proofId], requestId: randomUUID() };
    const first = await (await ken.post('/api/packages/PKG-1001/outcome', fields)).json();
    assert.deepEqual(await (await ken.post('/api/packages/PKG-1001/outcome', fields)).json(), first);
    await ken.post('/api/packages/PKG-1001/outcome', { outcome: 'DELIVERED', proofIds: [proofId] }, [403, 409]);
    const workspace = await ken.workspace();
    assert.equal(packageState(workspace, 'PKG-1001').status, 'DELIVERED');
    assert.equal(packageState(workspace, 'PKG-1001').custody, 'RECIPIENT');
    const completed = workspace.tasks.find(item => item.id === 'PKG-1001');
    if (completed) assert.equal(completed.address, undefined);
    await get(ken.api, `/api/proofs/${proofId}`, 403);
  });

  await step('Other failure needs a note and preserves the actual holder', async () => {
    await ken.post('/api/pickup-lists/PL-1001/collect', { packageQrCode: 'PKG-1002' });
    await ken.post('/api/packages/PKG-1002/start');
    await ken.post('/api/packages/PKG-1002/outcome', { outcome: 'FAILED', reason: 'OTHER', note: '' }, 400);
    await ken.post('/api/packages/PKG-1002/outcome', { outcome: 'FAILED', reason: 'OTHER', note: 'Fictional access problem.' });
    const workspace = await ken.workspace();
    assert.equal(packageState(workspace, 'PKG-1002').status, 'DELIVERY_FAILED');
    assert.equal(packageState(workspace, 'PKG-1002').custody, 'DELIVERY');
    assert.equal(attempts(workspace, 'PKG-1002').length, 1);
  });

  await step('Return report alone cannot authorize redelivery or Client instruction', async () => {
    await ken.post('/api/demo/packages/PKG-1002/admin-decision', { decision: 'APPROVE_REDELIVERY' }, 409);
    await ken.post('/api/demo/packages/PKG-1002/client-instruction', { instruction: 'REDELIVER' }, 409);
    await ken.post('/api/packages/PKG-1002/return', { reason: 'Returning failed delivery.' });
    await ken.post('/api/demo/packages/PKG-1002/admin-decision', { decision: 'APPROVE_REDELIVERY' }, 409);
    await ken.post('/api/demo/packages/PKG-1002/client-instruction', { instruction: 'REDELIVER' }, 409);
    assert.equal(packageState(await ken.workspace(), 'PKG-1002').custody, 'DELIVERY');
  });

  await step('Warehouse Place holder receipt is repeat-safe; instruction is still required', async () => {
    const fields = { condition: 'INTACT', requestId: randomUUID() };
    const response = await (await ken.post('/api/demo/packages/PKG-1002/confirm-return', fields)).json();
    assert.match(response.message, /Place holder/);
    assert.deepEqual(await (await ken.post('/api/demo/packages/PKG-1002/confirm-return', fields)).json(), response);
    await ken.post('/api/demo/packages/PKG-1002/admin-decision', { decision: 'APPROVE_REDELIVERY' }, 409);
    const workspace = await ken.workspace();
    assert.equal(packageState(workspace, 'PKG-1002').custody, 'WAREHOUSE');
    assert.equal(packageState(workspace, 'PKG-1002').status, 'DELIVERY_FAILED');
    assert.equal(workspace.tasks.some(item => item.id === 'PKG-1002' && item.address), false);
  });

  await step('Client/Admin Place holders approve the current return; approval is not pickup', async () => {
    await ken.post('/api/demo/packages/PKG-1002/client-instruction', { instruction: 'REDELIVER' });
    await ken.post('/api/demo/packages/PKG-1002/admin-decision', { decision: 'APPROVE_REDELIVERY' });
    const workspace = await ken.workspace();
    assert.equal(packageState(workspace, 'PKG-1002').status, 'READY_FOR_REDELIVERY');
    assert.equal(packageState(workspace, 'PKG-1002').custody, 'WAREHOUSE');
    retryListId = newList(workspace, 'PKG-1002', 'PL-1001').id;
    await ken.post('/api/packages/PKG-1002/start', {}, [403, 409]);
  });

  await step('A new legitimate pickup succeeds and preserves the prior failed attempt', async () => {
    await ken.post('/api/pickup-lists/claim', { qrCode: retryListId });
    await ken.post(`/api/pickup-lists/${retryListId}/collect`, { packageQrCode: 'PKG-1002' });
    await ken.post('/api/packages/PKG-1002/start');
    const workspace = await ken.workspace();
    const history = attempts(workspace, 'PKG-1002');
    assert.equal(history.length, 2);
    assert.equal(history.filter(item => item.outcome === 'FAILED').length, 1);
    assert.equal(history.filter(item => item.outcome === 'IN_PROGRESS').length, 1);
    await ken.post('/api/packages/PKG-1002/outcome', { outcome: 'DELIVERED', proofIds: [proofId] }, 403);
  });

  await step('Current cancellation blocks redelivery and cancellation remains final', async () => {
    await ken.post('/api/packages/PKG-1002/outcome', { outcome: 'FAILED', reason: 'CLIENT_NOT_HOME' });
    await ken.post('/api/packages/PKG-1002/return', { reason: 'Second failed delivery returned.' });
    await ken.post('/api/demo/packages/PKG-1002/confirm-return', { condition: 'INTACT' });
    await ken.post('/api/demo/packages/PKG-1002/client-instruction', { instruction: 'CANCEL' });
    await ken.post('/api/demo/packages/PKG-1002/admin-decision', { decision: 'APPROVE_REDELIVERY' }, 409);
    const response = await (await ken.post('/api/demo/packages/PKG-1002/admin-decision', { decision: 'APPROVE_CANCELLATION' })).json();
    assert.match(response.message, /Place holder/);
    assert.match(response.message, /not Refunded/i);
    assert.equal(packageState(await ken.workspace(), 'PKG-1002').status, 'CANCELLED');
    await ken.post('/api/packages/PKG-1002/start', {}, [403, 409]);
    await ken.post('/api/demo/packages/PKG-1002/admin-decision', { decision: 'APPROVE_REDELIVERY' }, 409);
  });

  await step('A pre-attempt return records no fake customer failure', async () => {
    await mei.post('/api/pickup-lists/claim', { qrCode: 'PL-1002' });
    await mei.post('/api/pickup-lists/PL-1002/collect', { packageQrCode: 'PKG-1003' });
    assert.equal(attempts(await mei.workspace(), 'PKG-1003').length, 0);
    await mei.post('/api/packages/PKG-1003/return', { reason: 'Fictional vehicle issue before visiting customer.' });
    assert.equal(packageState(await mei.workspace(), 'PKG-1003').custody, 'DELIVERY');
    await mei.post('/api/demo/packages/PKG-1003/confirm-return', { condition: 'INTACT' });
    const workspace = await mei.workspace();
    assert.equal(attempts(workspace, 'PKG-1003').length, 0);
    assert.equal(packageState(workspace, 'PKG-1003').status, 'READY_FOR_PICKUP');
    assert.equal(packageState(workspace, 'PKG-1003').custody, 'WAREHOUSE');
  });

  await step('Concurrent claims of a replacement list select only one collector', async () => {
    const replacement = newList(await ken.workspace(), 'PKG-1003', 'PL-1002');
    const results = await Promise.all([
      ken.post('/api/pickup-lists/claim', { qrCode: replacement.qrCode }, [200, 409]),
      mei.post('/api/pickup-lists/claim', { qrCode: replacement.qrCode }, [200, 409]),
    ]);
    assert.deepEqual(results.map(item => item.status()).sort(), [200, 409]);
    assert.equal(packageState(await ken.workspace(), 'PKG-1003').custody, 'WAREHOUSE');
  });

  await step('Logout invalidates authenticated access', async () => {
    await ken.post('/api/logout');
    await get(ken.api, '/api/delivery/workspace', 401);
    assert.equal((await ken.refresh()).authenticated, false);
  });

  report.status = 'passed';
})().catch(error => {
  report.status = 'failed';
  report.error = error.message;
  console.error(error);
  process.exitCode = 1;
}).finally(async () => {
  await Promise.allSettled(contexts.map(context => context.dispose()));
  report.finishedAt = new Date().toISOString();
  report.passedSteps = report.steps.filter(item => item.status === 'passed').length;
  report.failedSteps = report.steps.filter(item => item.status === 'failed').length;
  fs.mkdirSync(path.dirname(evidenceFile), { recursive: true });
  fs.writeFileSync(evidenceFile, JSON.stringify(report, null, 2));
  console.log(JSON.stringify({ status: report.status, passedSteps: report.passedSteps, failedSteps: report.failedSteps, evidenceFile }));
});
