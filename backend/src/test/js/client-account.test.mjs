import assert from "node:assert/strict";
import { after, before, test } from "node:test";
import { setTimeout as delay } from "node:timers/promises";
import { browser, startBrowser, until } from "./client-browser-harness.mjs";

// Run with node --test backend/src/test/js/client-account.test.mjs.
// Optionally supply a disposable Spring Boot stack through LMS_M10A_TEST_URL and its fixture username and password.
const liveUrl = process.env.LMS_M10A_TEST_URL;
const accountPath = "/client/account-settings.html";
const api = "/api/client/account";
const defaultsPath = api + "/delivery-defaults";
const empty = { recipientName: null, contactPhone: null, deliveryAddress: null };
let state, requests, origin, harness;
const command = (...args) => harness.command(...args);
const evaluate = (...args) => harness.evaluate(...args);
const observedClears = [];

function prepare(overrides = {}) {
  requests = [];
  state = { signedIn: true, role: "CLIENT", forced: false, revoked: false, username: "fixture-client",
    token: "fixture-token", defaults: { ...empty }, ...overrides };
}

async function fixture(request, response) {
  const path = new URL(request.url, "http://localhost").pathname;
  const method = request.method;
  const json = (status, body) => {
    response.writeHead(status, { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" });
    response.end(JSON.stringify(body));
  };
  if (path.startsWith("/api/")) {
    const entry = { path, method, token: request.headers["x-csrf-token"] };
    requests.push(entry);
    if (path === "/api/shared/session") {
      if (state.revoked) {
        return json(401, { code: "SHARED_SESSION_ENDED" });
      }
      const session = {
        sharedViewSessionIsLoggedIn: state.signedIn, role: state.role, username: state.username, userID: 10,
        mustChangePassword: state.forced, sharedViewSessionIdleTimeoutSeconds: state.timeout ?? 900,
        sharedViewSessionSecurityToken: state.token, sharedViewSessionSecurityTokenHeaderName: "X-CSRF-TOKEN"
      };
      const hold = state.holdSession;
      state.holdSession = null;
      if (hold) await hold;
      return json(200, session);
    }
    if (method !== "GET" && entry.token !== state.token) {
      return json(403, { code: "SHARED_SECURITY_TOKEN_INVALID" });
    }
    if (path === "/api/shared/logout") {
      if (state.dropLogout) {
        return response.destroy();
      }
      state.signedIn = false;
      return json(200, { message: "Logged out." });
    }
    if (path === "/api/shared/session/extend") {
      return json(200, { message: "Extended." });
    }
    if (path === api) {
      return json(200, state.badAccount ? { username: state.username } : {
        userId: "10", username: state.username, createdAt: "2026-11-01T10:00:00Z", deliveryDefaults: state.defaults
      });
    }
    if (path === defaultsPath) {
      if (method === "PUT") {
        const chunks = [];
        for await (const chunk of request) {
          chunks.push(chunk);
        }
        entry.body = JSON.parse(Buffer.concat(chunks));
        if (state.holdSave) {
          await state.holdSave;
        }
        if (state.error) {
          return json(state.error, { code: "SHARED_SECURITY_TOKEN_INVALID", message: "<b>Check the fields.</b>" });
        }
        if (state.dropSave) {
          return response.destroy();
        }
        state.defaults = entry.body;
      }
      return json(200, state.defaults);
    }
    return json(404, {});
  }
}

async function openAccount() {
  await command("Page.navigate", { url: origin + accountPath });
}

function visibleAccount() {
  return until(() => evaluate('!document.getElementById("clientPortal")?.hidden && document.getElementById("accountUsername")?.textContent'),
    "the visible account page");
}

function expectPath(path) {
  return until(() => evaluate("location.pathname === " + JSON.stringify(path)), path);
}

async function expectClearedBeforeSignIn(trigger) {
  const start = observedClears.length;
  // Observe the actual field clear in the old document, even if navigation destroys its context.
  await evaluate('(() => {' +
    'const field = document.getElementById("recipientName");' +
    'const value = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value");' +
    'Object.defineProperty(field, "value", {' +
    'get() { return value.get.call(this); },' +
    'set(next) { value.set.call(this, next);' +
    'm10aObserveClear(String(next === "" && !accountUsername.textContent && clientPortal.hidden)); }' +
    '});' +
    '})()');
  await evaluate(trigger);
  await until(() => observedClears.length > start, "the private field clear");
  assert(observedClears.slice(start).every((cleared) => cleared === "true"));
  await expectPath("/shared/shared-login.html");
}

before(async () => {
  if (!browser) return;
  prepare();
  harness = await startBrowser(fixture, ({ method, params }) => {
    if (method === "Runtime.bindingCalled" && params.name === "m10aObserveClear") {
      observedClears.push(params.payload);
    }
  });
  origin = harness.origin;
  await command("Runtime.addBinding", { name: "m10aObserveClear" });
});

after(async () => {
  await harness?.close();
});

test("Client bootstrap verifies session and renders only API data", { skip: !browser }, async () => {
  prepare({ username: '<img src=x onerror="window.injected=true">' });
  await openAccount();
  assert.equal(await visibleAccount(), state.username);
  assert.deepEqual(requests.slice(0, 2).map(({ path }) => path), ["/api/shared/session", api]);
  assert.equal(await evaluate('document.querySelectorAll("#clientUsername img,#accountUsername img").length'), 0);
  assert.equal(await evaluate('!document.getElementById("deliveryEmpty").hidden && !window.injected'), true);
  assert.match(await evaluate('document.getElementById("sharedStartSessionCountdownText").textContent'), /^\d+:\d{2}$/);
  assert.equal(await evaluate('document.querySelectorAll(".client-nav-item[aria-disabled=true]:not([href])").length'), 1);
  assert.equal(await evaluate('getComputedStyle(clientNavigation).display !== "none" && getComputedStyle(clientMenuToggle).display === "none"'), true);
  const fonts = await evaluate("document.fonts.ready.then(() => Array.from(document.fonts, ({family, status}) => [family, status]))");
  assert.deepEqual(fonts.map(([family, status]) => [family.replaceAll('"', ""), status]).sort(), [
    ["Hanken Grotesk", "loaded"], ["JetBrains Mono", "loaded"]
  ]);
});

test("guest, other roles, forced password change and revoked sessions do not load the account", { skip: !browser }, async () => {
  for (const [options, target] of [
    [{ signedIn: false }, "/shared/shared-login.html"],
    [{ role: "ADMIN" }, "/shared/shared-welcome.html"],
    [{ role: "WAREHOUSE_WORKER" }, "/shared/shared-welcome.html"],
    [{ forced: true }, "/shared/shared-change-password.html"],
    [{ revoked: true }, "/shared/shared-login.html"]
  ]) {
    prepare(options);
    await openAccount();
    await expectPath(target);
    assert.equal(requests.some(({ path }) => path.startsWith(api)), false);
  }
});

test("defaults save once with CSRF and all fields, clear with null values, and reload from GET", { skip: !browser }, async () => {
  let release;
  prepare({ holdSave: new Promise((resolveSave) => { release = resolveSave; }) });
  await openAccount();
  await visibleAccount();
  await evaluate("recipientName.value='  Chan Tai Man  '; contactPhone.value='+852 2345 6789'; " +
    "deliveryAddress.value='Unit 4\\nKowloon'; deliveryForm.requestSubmit(); deliveryForm.requestSubmit()");
  await until(() => requests.find(({ method }) => method === "PUT")?.body, "the save payload");
  assert.equal(await evaluate('deliveryFeedback.dataset.state'), "pending");
  assert.deepEqual(requests.filter(({ method }) => method === "PUT"), [{
    path: defaultsPath, method: "PUT", token: "fixture-token",
    body: { recipientName: "Chan Tai Man", contactPhone: "+852 2345 6789", deliveryAddress: "Unit 4\nKowloon" }
  }]);
  release();
  await until(() => evaluate('deliveryFeedback.dataset.state === "success"'), "the saved confirmation");
  assert.equal(await evaluate("recipientName.value"), "Chan Tai Man");
  await evaluate("recipientName.value=''; contactPhone.value=''; deliveryAddress.value=''; deliveryForm.requestSubmit()");
  await until(() => evaluate('!deliveryEmpty.hidden && deliveryFeedback.dataset.state === "success"'), "cleared defaults");
  assert.deepEqual(requests.filter(({ method }) => method === "PUT").at(-1).body, empty);
  state.defaults = { ...empty, deliveryAddress: "Reloaded address" };
  await evaluate("reloadDefaults.click()");
  await until(() => evaluate('deliveryAddress.value === "Reloaded address"'), "the reloaded defaults");
  assert.equal(requests.filter(({ path, method }) => path === api && method === "GET").length, 2);
});

test("invalid inputs and server rejections do not show success", { skip: !browser }, async () => {
  prepare();
  await openAccount();
  await visibleAccount();
  await evaluate("recipientName.value='a'.repeat(121); contactPhone.value='invalid'; deliveryAddress.value='***'; deliveryForm.requestSubmit()");
  assert.equal(await evaluate('deliveryForm.querySelectorAll("[aria-invalid=true]").length'), 3);
  assert.equal(requests.some(({ method }) => method === "PUT"), false);
  await evaluate("recipientName.value='Valid Name'; contactPhone.value=''; deliveryAddress.value=''");
  for (const status of [400, 403, 404, 409, 500]) {
    state.error = status;
    await evaluate("deliveryForm.requestSubmit()");
    await until(() => evaluate('deliveryFeedback.dataset.state === "error"'), "the save error for " + status);
    assert.equal(await evaluate("deliveryFeedback.querySelectorAll('b').length"), 0);
  }
  state.error = null;
  state.dropSave = true;
  await evaluate("deliveryForm.requestSubmit()");
  await until(() => evaluate('deliveryFeedback.textContent.includes("save could not be confirmed")'), "the failed network save");
  assert.deepEqual(state.defaults, empty);
});

test("malformed responses keep private account content hidden and allow retry", { skip: !browser }, async () => {
  prepare({ badAccount: true });
  await openAccount();
  await until(() => evaluate("document.getElementById('accountRetry')?.offsetWidth > 0"), "the account retry");
  assert.equal(await evaluate("accountContent.hidden"), true);
  state.badAccount = false;
  await evaluate("accountRetry.click()");
  await visibleAccount();
});

test("mobile navigation opens and closes with keyboard without horizontal overflow", { skip: !browser }, async () => {
  prepare();
  await command("Emulation.setDeviceMetricsOverride", { width: 390, height: 850, deviceScaleFactor: 1, mobile: true });
  try {
    await openAccount();
    await visibleAccount();
    assert.equal(await evaluate('getComputedStyle(clientNavigation).display'), "none");
    await evaluate("clientMenuToggle.focus(); clientMenuToggle.click()");
    assert.equal(await evaluate('clientMenuToggle.getAttribute("aria-expanded")'), "true");
    assert.notEqual(await evaluate("getComputedStyle(clientNavigation).display"), "none");
    await command("Input.dispatchKeyEvent", { type: "keyDown", key: "Escape", code: "Escape", windowsVirtualKeyCode: 27 });
    await command("Input.dispatchKeyEvent", { type: "keyUp", key: "Escape", code: "Escape", windowsVirtualKeyCode: 27 });
    assert.equal(await evaluate('clientMenuToggle.getAttribute("aria-expanded")'), "false");
    assert.equal(await evaluate("document.activeElement.id"), "clientMenuToggle");
    assert.equal(await evaluate("document.documentElement.scrollWidth === innerWidth"), true);
    await command("Emulation.setDeviceMetricsOverride", { width: 320, height: 760, deviceScaleFactor: 1, mobile: true });
    assert.equal(await evaluate("document.documentElement.scrollWidth === innerWidth"), true);
  } finally {
    await command("Emulation.clearDeviceMetricsOverride");
  }
});

test("revocation on visibility recheck clears account data before navigation", { skip: !browser }, async () => {
  prepare({ defaults: { ...empty, recipientName: "Previously visible" } });
  await openAccount();
  await visibleAccount();
  state.revoked = true;
  await expectClearedBeforeSignIn("document.dispatchEvent(new Event('visibilitychange'))");
});

test("visibility checks cannot let an earlier response reset the current CSRF token", { skip: !browser }, async () => {
  prepare();
  await openAccount();
  await visibleAccount();
  const initialChecks = requests.filter(({ path }) => path === "/api/shared/session").length;
  const checks = () => requests.filter(({ path }) => path === "/api/shared/session").length - initialChecks;
  let release;
  state.holdSession = new Promise((resolveCheck) => { release = resolveCheck; });
  await evaluate("document.dispatchEvent(new Event('visibilitychange'))");
  await until(() => checks() === 1, "the delayed visibility check");
  state.token = "new-fixture-token";
  await evaluate("document.dispatchEvent(new Event('visibilitychange'))");
  // A concurrent request can reach the fixture while the first reply is still withheld.
  await delay(100);
  const checksBeforeRelease = checks();
  release();
  assert.equal(checksBeforeRelease, 1);
  await until(() => checks() === 2, "the second visibility check");
  await visibleAccount();
  await evaluate("recipientName.value='Valid Name'; deliveryForm.requestSubmit()");
  await until(() => evaluate('deliveryFeedback.dataset.state === "success"'), "a save using the current token");
  assert.equal(requests.find(({ method }) => method === "PUT").token, "new-fixture-token");
});

test("a delayed cross-tab logout signal rechecks the current session before deciding", { skip: !browser }, async () => {
  prepare({ defaults: { ...empty, recipientName: "Previous recipient" } });
  await openAccount();
  await visibleAccount();
  state.username = "new-fixture-client";
  state.defaults = { ...empty, recipientName: "Current recipient" };
  await evaluate("const sender = new BroadcastChannel('lms-client-session'); sender.postMessage('signed-out'); sender.close();");
  await until(() => evaluate("document.getElementById('accountUsername')?.textContent === 'new-fixture-client' && !clientPortal.hidden"),
    "the current session after a cross-tab signal");
  assert.equal(await evaluate("recipientName.value"), "Current recipient");
});

test("reloading defaults cannot show a different Client's values under the old session identity", { skip: !browser }, async () => {
  prepare({ defaults: { ...empty, recipientName: "Previous recipient" } });
  await openAccount();
  await visibleAccount();
  state.username = "different-fixture-client";
  state.defaults = { ...empty, recipientName: "Private recipient of a different account" };
  await expectClearedBeforeSignIn("reloadDefaults.click()");
});

test("shared logout sends CSRF and Back cannot restore private content", { skip: !browser }, async () => {
  prepare({ dropLogout: true, defaults: { ...empty, recipientName: "Previously visible" } });
  await openAccount();
  await visibleAccount();
  assert.equal(await evaluate("clientLogout.click(); clientPortal.hidden && !accountUsername.textContent && !recipientName.value"), true);
  await until(() => evaluate("!clientRetryLogout.hidden"), "the logout retry");
  assert.equal(await evaluate("clientPortal.hidden && !accountUsername.textContent && !recipientName.value"), true);
  state.dropLogout = false;
  await evaluate("clientRetryLogout.click()");
  await expectPath("/shared/shared-login.html");
  assert(requests.some(({ path, method, token }) => path === "/api/shared/logout" && method === "POST" && token === "fixture-token"));
  const accountReads = requests.filter(({ path }) => path === api).length;
  const checks = requests.filter(({ path }) => path === "/api/shared/session").length;
  await evaluate("history.back()");
  await until(() => requests.filter(({ path }) => path === "/api/shared/session").length > checks, "a session check after Back");
  await expectPath("/shared/shared-login.html");
  assert.equal(requests.filter(({ path }) => path === api).length, accountReads);
});

test("the shared warning can extend the session and the shared timer can end it", { skip: !browser }, async () => {
  prepare();
  await openAccount();
  await visibleAccount();
  await evaluate("sharedOpenDialog(sharedStartSessionCountdownDialog); clientExtendSession.click()");
  await until(() => evaluate("!sharedStartSessionCountdownDialog.open"), "the warning to close");
  assert(requests.some(({ path, token }) => path === "/api/shared/session/extend" && token === "fixture-token"));
  await evaluate("sharedStartSessionCountdown(1)");
  await expectPath("/shared/shared-login.html");
  assert.match(await evaluate("location.search"), /sharedLoginUserNotice=IDLE/);
});

test("a real Spring Boot session can save, read, clear and log out", { skip: !browser || !liveUrl }, async () => {
  const username = process.env.LMS_M10A_TEST_USERNAME;
  const password = process.env.LMS_M10A_TEST_PASSWORD;
  assert(["127.0.0.1", "localhost"].includes(new URL(liveUrl).hostname)
    && username?.startsWith("m10a-fixture-") && password, "Use a local disposable stack and Client fixture for the live check.");
  await command("Page.navigate", { url: liveUrl + "/shared/shared-login.html" });
  await until(() => evaluate("document.readyState === 'complete' && typeof sharedLoginUser === 'function' && " +
    "!document.getElementById('sharedLoginUserSubmitButton')?.disabled"), "the ready shared login form");
  await evaluate("sharedLoginUserUsernameInput.value=" + JSON.stringify(username) +
    "; sharedLoginUserPasswordInput.value=" + JSON.stringify(password) + "; sharedLoginUserForm.requestSubmit()");
  await expectPath("/shared/shared-welcome.html");
  await command("Page.navigate", { url: liveUrl + accountPath });
  assert.equal(await visibleAccount(), username);
  assert.equal(await evaluate("!deliveryEmpty.hidden"), true);
  await evaluate("recipientName.value=' Integration Client '; contactPhone.value='+852 2345 6789'; " +
    "deliveryAddress.value='Unit 4\\nKowloon, Hong Kong'; deliveryForm.requestSubmit()");
  await until(() => evaluate('deliveryFeedback.dataset.state === "success"'), "the real save");
  assert.deepEqual(await evaluate('sharedSendRequest("GET", "/api/client/account/delivery-defaults")'), {
    recipientName: "Integration Client", contactPhone: "+852 2345 6789", deliveryAddress: "Unit 4\nKowloon, Hong Kong"
  });
  await evaluate("reloadDefaults.click()");
  await until(() => evaluate('deliveryFeedback.textContent === "Saved delivery details reloaded."'), "the real read");
  assert.equal(await evaluate("recipientName.value"), "Integration Client");
  await evaluate("recipientName.value=''; contactPhone.value=''; deliveryAddress.value=''; deliveryForm.requestSubmit()");
  await until(() => evaluate('!deliveryEmpty.hidden && deliveryFeedback.textContent === "Delivery details saved."'), "the real clear");
  await evaluate("clientLogout.click()");
  await expectPath("/shared/shared-login.html");
  await command("Page.navigate", { url: liveUrl + accountPath });
  await expectPath("/shared/shared-login.html");
});
