const { request } = require('playwright');
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const baseURL = process.env.DELIVERY_TEST_URL || 'http://127.0.0.1:8080';
const evidence = path.join(__dirname, 'evidence');

(async () => {
  const client = await request.newContext({ baseURL });
  let session = await (await client.get('/api/session')).json();
  const login = await client.post('/api/login', { headers: { [session.csrfHeader]: session.csrfToken }, form: { username: 'ken', password: 'Delivery123!' } });
  assert.equal(login.status(), 200);
  const response = await client.get('/api/delivery/workspace');
  assert.equal(response.status(), 200);
  const workspace = await response.json();
  const state = {
    packages: workspace.demoPackages.map(p => ({ id: p.id, status: p.status, custody: p.custody, returnStatus: p.returnStatus, instruction: p.instruction, approved: p.approved, pendingCancellation: p.pendingCancellation })).sort((a,b) => a.id.localeCompare(b.id)),
    attempts: workspace.attempts.map(a => ({ id: a.id, packageId: a.packageId, outcome: a.outcome })).sort((a,b) => a.id.localeCompare(b.id)),
    returns: workspace.returns.map(r => ({ id: r.id, packageId: r.packageId, status: r.status })).sort((a,b) => a.id.localeCompare(b.id))
  };
  fs.mkdirSync(evidence, { recursive: true });
  const before = path.join(evidence, 'persistence-before.json');
  if (process.argv[2] === 'before') {
    fs.writeFileSync(before, JSON.stringify(state, null, 2));
    console.log('Saved the confirmed delivery state before restart.');
  } else {
    assert.deepEqual(state, JSON.parse(fs.readFileSync(before, 'utf8')));
    const result = { status: 'passed', packages: state.packages.length, attempts: state.attempts.length, returns: state.returns.length, check: 'Confirmed package, attempt and return records survive a real server restart.' };
    fs.writeFileSync(path.join(evidence, 'persistence-result.json'), JSON.stringify(result, null, 2));
    console.log(JSON.stringify(result));
  }
  session = await (await client.get('/api/session')).json();
  await client.post('/api/logout', { headers: { [session.csrfHeader]: session.csrfToken } });
  await client.dispose();
})().catch(error => { console.error(error); process.exitCode = 1; });
