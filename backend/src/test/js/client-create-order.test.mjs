import assert from "node:assert/strict";
import { after, before, test } from "node:test";
import { browser, startBrowser, until } from "./client-browser-harness.mjs";

// Run with node --test backend/src/test/js/client-create-order.test.mjs.
// The optional live test requires a freshly initialized loopback LMS stack and an m10b-fixture- account.
const liveUrl = process.env.LMS_M10B_TEST_URL;
const path = "/client/create-order.html";
const accountPath = "/api/client/account";
const cataloguePath = "/api/client/products";
const orderPath = "/api/client/orders";
const keyPattern = /^[A-Za-z0-9._~-]{1,128}$/;
const wideId = "9007199254740993";
const defaults = { recipientName: "Saved Recipient", contactPhone: "61234567", deliveryAddress: "Saved address\nHong Kong" };
const savedOrder = {
  orderReference: "ORDER-REAL-001", orderStatus: "AWAITING_PAYMENT", totalAmount: 32.7,
  paymentDeadline: "2026-11-22T05:30:00Z"
};
const products = [
  { productId: 123, sku: "S-123", name: "<b>Storage Box</b>", currentPriceHkd: 10.15, availableStock: 4 },
  { productId: JSON.rawJSON(wideId), sku: "S-WIDE", name: "紙箱", currentPriceHkd: 18.25, availableStock: 5 }
];
let state, requests, harness;
const calls = (url, method = "GET") => requests.filter((entry) => entry.path === url && entry.method === method);
const command = (...args) => harness.command(...args);
const evaluate = (...args) => harness.evaluate(...args);
const ready = () => until(() => evaluate('document.getElementById("clientPortal")?.hidden === false && !submitOrder.disabled'),
  "the ready order form");
const retryVisible = () => until(() => evaluate('!orderRetry.hidden'), "the explicit retry controls");
const created = () => until(() => evaluate('!createdOrder.hidden && createdOrderReference.textContent'), "a confirmed order");

function prepare(overrides = {}) {
  requests = [];
  state = { userId: 10, username: "fixture-client", signedIn: true, forced: false, role: "CLIENT",
    defaults: { ...defaults }, token: "fixture-csrf", products, orders: new Map(), ...overrides };
}

async function fixture(request, response) {
  const url = new URL(request.url, "http://localhost");
  if (!url.pathname.startsWith("/api/")) return;
  const entry = { path: url.pathname, method: request.method, name: url.searchParams.get("name"),
    key: request.headers["idempotency-key"], token: request.headers["x-csrf-token"] };
  requests.push(entry);
  const json = (status, body) => response.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store"
  }).end(JSON.stringify(body));
  if (entry.path === "/api/shared/session") {
    if (state.revoked) return json(401, { code: "SHARED_SESSION_ENDED" });
    if (state.sessionUnavailable) return json(503, { code: "SESSION_CHECK_UNAVAILABLE" });
    return json(200, {
      sharedViewSessionIsLoggedIn: state.signedIn, username: state.username, role: state.role,
      userID: state.userId, mustChangePassword: state.forced, sharedViewSessionIdleTimeoutSeconds: 900,
      sharedViewSessionSecurityToken: state.token, sharedViewSessionSecurityTokenHeaderName: "X-CSRF-TOKEN"
    });
  }
  if (entry.path === accountPath) {
    return json(200, { userId: String(state.userId), username: state.username,
      createdAt: "2026-11-01T10:00:00Z", deliveryDefaults: state.defaults });
  }
  if (entry.path === cataloguePath) {
    if (state.catalogueError) return json(503, { code: "CATALOGUE_UNAVAILABLE" });
    if (!Array.isArray(state.products)) return json(200, state.products);
    const filter = entry.name.toLowerCase();
    return json(200, state.products.filter(({ name }) => name.toLowerCase().includes(filter)));
  }
  if (entry.path === orderPath && entry.method === "POST") {
    const chunks = [];
    for await (const chunk of request) chunks.push(chunk);
    entry.raw = Buffer.concat(chunks).toString();
    entry.body = JSON.parse(entry.raw);
    if (entry.token !== state.token) return json(403, { code: "SHARED_SECURITY_TOKEN_INVALID" });
    if (!keyPattern.test(entry.key ?? "")) return json(400, { code: "CLIENT_ORDER_IDEMPOTENCY_KEY_INVALID" });
    const known = state.orders.get(entry.key);
    if (known) {
      return entry.raw === known.raw ? json(201, known.order) : json(409, { code: "CLIENT_ORDER_IDEMPOTENCY_KEY_CONFLICT" });
    }
    const prior = state.orders.size;
    if (state.orderError) return json(...state.orderError);
    const order = { ...savedOrder, orderReference: prior ? "ORDER-REAL-002" : savedOrder.orderReference };
    state.orders.set(entry.key, { raw: entry.raw, order });
    if (state.holdOrder) await state.holdOrder;
    if (state.loseFirst && prior === 0) {
      response.writeHead(201, { "Content-Type": "application/json", "Content-Length": "128" });
      return response.write("{", () => response.destroy());
    }
    return json(201, state.malformed ? { orderReference: order.orderReference } : order);
  }
  return json(404, { code: "ROUTE_UNEXPECTED" });
}

async function openOrder() {
  await command("Page.navigate", { url: harness.origin + path });
  await until(() => evaluate('location.pathname === "/client/create-order.html" && !!document.getElementById("orderForm")'), "the order page");
}

async function select(id, quantity, index = 0) {
  await evaluate(`(() => {
    const line = orderLines.querySelectorAll("fieldset")[${index}];
    const product = line.querySelector("select");
    product.value = ${JSON.stringify(id)};
    product.dispatchEvent(new Event("change", { bubbles: true }));
    const input = line.querySelector("input");
    input.value = ${JSON.stringify(quantity)};
    input.dispatchEvent(new Event("input", { bubbles: true }));
  })()`);
}

before(async () => {
  if (!browser) return;
  prepare();
  harness = await startBrowser(fixture);
});

after(async () => {
  await harness?.close();
});

test("session gates the page and prevents guest, Admin, forced-password and revoked-session data loads", { skip: !browser }, async () => {
  for (const [scenario, destination] of [
    [{ signedIn: false }, "/shared/shared-login.html"],
    [{ role: "ADMIN" }, "/shared/shared-welcome.html"],
    [{ forced: true }, "/shared/shared-change-password.html"],
    [{ revoked: true }, "/shared/shared-login.html"]
  ]) {
    prepare(scenario);
    await command("Page.navigate", { url: harness.origin + path });
    await until(() => evaluate("location.pathname === " + JSON.stringify(destination)), destination);
    assert.equal(calls(cataloguePath).length + calls(accountPath).length, 0);
  }
});

test("catalogue search, price estimate, safe text and owned delivery prefilling use the real contract", { skip: !browser }, async () => {
  prepare();
  await openOrder();
  await ready();
  assert.equal(requests[0].path, "/api/shared/session");
  assert.equal(await evaluate('document.querySelector("[aria-current=page]").textContent.trim()'), "Create Order");
  assert.equal(await evaluate('getComputedStyle(document.querySelector(".client-order-summary")).position'), "sticky");
  assert.deepEqual(await evaluate("[orderRecipient.value, orderPhone.value, orderAddress.value]"), Object.values(defaults));
  assert.equal(await evaluate('orderLines.querySelector("option[value=\'123\']").textContent.includes("<b>Storage Box</b>")'), true);
  assert.equal(await evaluate('orderLines.querySelectorAll("b").length'), 0);
  await select("123", "3");
  assert.equal(await evaluate("estimatedTotal.textContent"), "HK$30.45");
  assert.match(await evaluate('orderLines.querySelector(".client-order-stock").textContent'), /4 available/);
  await evaluate(`catalogueSearch.value = "紙箱"; searchCatalogue.click()`);
  await until(() => calls(cataloguePath).some((entry) => entry.name === "紙箱"), "the encoded product search");
  await ready();
  assert.equal(await evaluate('orderLines.querySelector("select").value'), "123");
  assert.equal(await evaluate(`orderLines.querySelector("option[value='${wideId}']").disabled`), false);
});

test("empty, unreachable and malformed catalogue responses are visible and recoverable", { skip: !browser }, async () => {
  prepare({ products: { products: [] } });
  await openOrder();
  await until(() => evaluate('catalogueStatus.textContent.includes("Could not read")'), "the malformed response message");
  assert.equal(await evaluate("submitOrder.disabled"), true);
  state.catalogueError = true;
  await evaluate("refreshCatalogue.click()");
  await until(() => evaluate('catalogueStatus.textContent.includes("could not complete")'), "the catalogue error");
  state.catalogueError = false;
  state.products = [];
  await evaluate("refreshCatalogue.click()");
  await until(() => evaluate('catalogueStatus.textContent.includes("catalogue is empty")'), "the empty state");
  await ready();
  assert.equal(await evaluate("addOrderLine.disabled"), true);
  await evaluate("orderForm.requestSubmit()");
  assert.match(await evaluate("orderLinesError.textContent"), /at least one/);
  state.products = products;
  await evaluate("refreshCatalogue.click()");
  await until(() => evaluate('orderLines.querySelectorAll("fieldset").length === 1 && !submitOrder.disabled'), "catalogue recovery");
});

test("whole-cent prices are exact and a fractional-cent catalogue entry blocks submission", { skip: !browser }, async () => {
  prepare({ products: [
    { ...products[0], currentPriceHkd: 0.29 },
    { ...products[1], currentPriceHkd: 0.1 }
  ] });
  await openOrder();
  await ready();
  await select("123", "3");
  await evaluate("addOrderLine.click()");
  await select(wideId, "1", 1);
  assert.equal(await evaluate("estimatedTotal.textContent"), "HK$0.97");
  state.products = [{ ...products[0], currentPriceHkd: 0.291 }];
  await openOrder();
  await until(() => evaluate('catalogueStatus.textContent.includes("Could not read")'), "the invalid price response");
  assert.equal(await evaluate("submitOrder.disabled"), true);
});

test("without lossless JSON support, wide product IDs cannot be selected or submitted", { skip: !browser }, async () => {
  const { identifier } = await command("Page.addScriptToEvaluateOnNewDocument", { source: "JSON.rawJSON = undefined" });
  try {
    prepare();
    await openOrder();
    await ready();
    assert.equal(await evaluate('Array.from(orderLines.querySelectorAll("option")).some((option) => option.disabled && option.text.includes("ID unsupported"))'), true);
    await select(wideId, "1");
    await evaluate("orderForm.requestSubmit()");
    assert.equal(calls(orderPath, "POST").length, 0);
    await select("123", "1");
    await evaluate("orderForm.requestSubmit()");
    assert.equal(await created(), savedOrder.orderReference);
    assert.equal(calls(orderPath, "POST")[0].body.items[0].productId, 123);
  } finally {
    await command("Page.removeScriptToEvaluateOnNewDocument", { identifier });
  }
});

test("invalid selections, quantities and delivery fields block submission; products are unique and removable", { skip: !browser }, async () => {
  prepare();
  await openOrder();
  await ready();
  await evaluate("orderForm.requestSubmit()");
  assert.equal(await evaluate('document.activeElement.matches("#orderLines select[aria-invalid=true]")'), true);
  for (const quantity of ["0", "-1", "1.5", "5"]) {
    await select("123", quantity);
    await evaluate("orderForm.requestSubmit()");
    assert.equal(await evaluate('document.activeElement.matches("#orderLines input[aria-invalid=true]")'), true);
  }
  await select("123", "2");
  await evaluate('orderRecipient.value = "x".repeat(121); orderPhone.value = ""; orderAddress.value = "a".repeat(1001); orderForm.requestSubmit()');
  assert.equal(await evaluate("orderRecipientError.hidden || orderPhoneError.hidden || orderAddressError.hidden"), false);
  assert.equal(await evaluate("document.activeElement === orderRecipient"), true);
  await evaluate("addOrderLine.click()");
  assert.equal(await evaluate('orderLines.lastElementChild.querySelector("option[value=\'123\']").disabled'), true);
  await select("123", "1", 1);
  await evaluate("orderForm.requestSubmit()");
  assert.match(await evaluate('orderLines.lastElementChild.querySelector(".client-field-error").textContent'), /only once/);
  await evaluate('orderLines.lastElementChild.querySelector("button").click()');
  assert.equal(await evaluate("orderLines.children.length"), 1);
  assert.equal(calls(orderPath, "POST").length, 0);
});

test("response-loss retry keeps the exact payload and key, blocks duplicate clicks and displays only the server result", { skip: !browser }, async () => {
  let release;
  prepare({ loseFirst: true, holdOrder: new Promise((resolve) => { release = resolve; }) });
  await openOrder();
  await ready();
  await select(wideId, "2");
  assert.equal(await evaluate("estimatedTotal.textContent"), "HK$36.50");
  const orderAddress = "Order-only address\nHong Kong";
  await evaluate(`orderAddress.value = ${JSON.stringify(orderAddress)}; orderForm.requestSubmit(); orderForm.requestSubmit(); submitOrder.click()`);
  await until(() => calls(orderPath, "POST")[0]?.raw, "the first order request");
  assert.equal(calls(orderPath, "POST").length, 1);
  assert.equal(await evaluate("submitOrder.disabled && productFields.disabled && orderDeliveryFields.disabled && createdOrder.hidden"), true);
  release();
  await retryVisible();
  assert.equal(await evaluate('window.dispatchEvent(new Event("beforeunload", { cancelable: true }))'), false);
  await evaluate('document.querySelector("[data-client-page=my-orders]").click()');
  assert.equal(await evaluate("leaveOrderDialog.open && document.activeElement === stayOnOrder"), true);
  await evaluate("stayOnOrder.click()");
  assert.equal(await evaluate('location.pathname === "/client/create-order.html" && !orderRetry.hidden'), true);
  const [original] = calls(orderPath, "POST");
  assert.match(original.key, keyPattern);
  assert.equal(original.token, state.token);
  assert.match(original.raw, new RegExp('"productId":' + wideId + '(?=,)'));
  assert.deepEqual({ ...original.body, items: undefined }, {
    items: undefined, recipientName: defaults.recipientName, contactPhone: defaults.contactPhone, deliveryAddress: orderAddress
  });
  assert.equal(original.body.items[0].quantity, 2);
  await evaluate('orderAddress.value = "Changed after response loss"; retryOrder.click(); retryOrder.click()');
  assert.equal(await created(), savedOrder.orderReference);
  assert.equal(await evaluate('viewCreatedOrder.getAttribute("href")'), "/client/order-details.html?reference=" + savedOrder.orderReference);
  assert.equal(await evaluate('window.dispatchEvent(new Event("beforeunload", { cancelable: true }))'), true);
  const replay = calls(orderPath, "POST")[1];
  assert.equal(calls(orderPath, "POST").length, 2);
  assert.equal(replay.key, original.key);
  assert.equal(replay.raw, original.raw);
  assert.equal(state.orders.size, 1);
  assert.deepEqual(await evaluate("[createdOrderStatus.textContent, createdOrderTotal.textContent, createdOrderDeadline.dateTime]"),
    [savedOrder.orderStatus, "HK$32.70", savedOrder.paymentDeadline]);
  assert.match(await evaluate("createdOrder.textContent"), /Online payment is currently unavailable/);
  assert.equal(await evaluate("localStorage.length + sessionStorage.length"), 0);
  assert.equal(requests.some(({ path, method }) => /checkout|payment/.test(path) || method === "PUT"), false);
  await evaluate("startAnotherOrder.click()");
  await ready();
  assert.equal(await evaluate("orderAddress.value"), defaults.deliveryAddress);
  await select("123", "1");
  await evaluate("orderForm.requestSubmit()");
  assert.equal(await created(), "ORDER-REAL-002");
  assert.notEqual(calls(orderPath, "POST")[2].key, original.key);
});

test("a malformed success can be retried, or explicitly discarded through an accessible dialog", { skip: !browser }, async () => {
  prepare({ malformed: true });
  await openOrder();
  await ready();
  await select("123", "1");
  await evaluate("orderForm.requestSubmit()");
  await retryVisible();
  assert.equal(await evaluate("createdOrder.hidden"), true);
  assert.match(await evaluate("orderRetry.textContent"), /reloading this page loses the retry/);
  await evaluate("discardOrderRetry.click()");
  assert.equal(await evaluate("discardOrderDialog.open"), true);
  await evaluate("keepOrderRetry.click()");
  assert.equal(await evaluate("!discardOrderDialog.open && !orderRetry.hidden"), true);
  const key = calls(orderPath, "POST")[0].key;
  await evaluate("discardOrderRetry.click(); confirmNewOrder.click()");
  await ready();
  assert.equal(await evaluate("orderRetry.hidden && orderLines.querySelector('select').value === ''"), true);
  state.malformed = false;
  await select("123", "1");
  await evaluate("orderForm.requestSubmit()");
  assert.equal(await created(), "ORDER-REAL-002");
  assert.notEqual(calls(orderPath, "POST")[1].key, key);
});

test("navigation to order history requires explicit confirmation while the submission result is unknown", { skip: !browser }, async () => {
  prepare({ loseFirst: true });
  await openOrder();
  await ready();
  await select("123", "1");
  await evaluate("orderForm.requestSubmit()");
  await retryVisible();
  await evaluate('document.querySelector("[data-client-page=my-orders]").click()');
  assert.equal(await evaluate("leaveOrderDialog.open && orderDeliveryFields.disabled"), true);
  await evaluate("confirmLeaveOrder.click()");
  await until(() => evaluate('location.pathname === "/client/my-orders-tracking.html"'), "the confirmed navigation");
  assert.equal(calls(orderPath, "POST").length, 1);
});

test("an unanswered order can be retried after the UI times out and the session check temporarily fails", { skip: !browser }, async () => {
  let release;
  const { identifier } = await command("Page.addScriptToEvaluateOnNewDocument", {
    source: 'const realSetTimeout = window.setTimeout.bind(window); window.setTimeout = (callback, ms, ...args) => realSetTimeout(callback, ms === 30000 ? 700 : ms, ...args)'
  });
  try {
    prepare({ holdOrder: new Promise((resolve) => { release = resolve; }) });
    await openOrder();
    await ready();
    await select("123", "1");
    await evaluate("orderForm.requestSubmit()");
    await until(() => state.orders.size === 1, "the committed fixture order without an HTTP reply");
    await retryVisible();
    assert.match(await evaluate("orderFeedback.textContent"), /stopped waiting/);
    state.sessionUnavailable = true;
    await evaluate('document.dispatchEvent(new Event("visibilitychange"))');
    await until(() => evaluate("clientPortal.hidden && !clientRetrySession.hidden"), "the unavailable session check");
    assert.equal(await evaluate("createdOrder.hidden && orderDeliveryFields.disabled"), true);
    state.sessionUnavailable = false;
    await evaluate("clientRetrySession.click()");
    await until(() => evaluate("!clientPortal.hidden && !orderRetry.hidden"), "the same Client's restored retry");
    await evaluate("retryOrder.click()");
    assert.equal(await created(), savedOrder.orderReference);
    const [first, second] = calls(orderPath, "POST");
    assert.equal(second.key, first.key);
    assert.equal(second.raw, first.raw);
    assert.equal(state.orders.size, 1);
  } finally {
    release?.();
    await command("Page.removeScriptToEvaluateOnNewDocument", { identifier });
  }
});

test("an idempotency conflict locks the old submission until a different order is explicitly confirmed", { skip: !browser }, async () => {
  prepare({ orderError: [409, { code: "CLIENT_ORDER_IDEMPOTENCY_KEY_CONFLICT" }] });
  await openOrder();
  await ready();
  await select("123", "1");
  await evaluate("orderForm.requestSubmit()");
  await retryVisible();
  assert.equal(await evaluate("retryOrder.disabled && submitOrder.disabled && orderDeliveryFields.disabled"), true);
  await evaluate("retryOrder.click(); orderForm.requestSubmit(); discardOrderRetry.click(); keepOrderRetry.click()");
  assert.equal(calls(orderPath, "POST").length, 1);
  state.orderError = null;
  await evaluate("discardOrderRetry.click(); confirmNewOrder.click()");
  await ready();
  await select("123", "1");
  await evaluate("orderForm.requestSubmit()");
  assert.equal(await created(), savedOrder.orderReference);
  assert.notEqual(calls(orderPath, "POST")[0].key, calls(orderPath, "POST")[1].key);
});

test("stock conflict requires refresh and API validation errors are rendered as plain text", { skip: !browser }, async () => {
  prepare({ orderError: [409, { code: "CLIENT_ORDER_STOCK_UNAVAILABLE" }] });
  await openOrder();
  await ready();
  await select("123", "4");
  await evaluate("orderForm.requestSubmit()");
  await until(() => evaluate('orderFeedback.textContent.includes("availability changed")'), "the stock conflict");
  assert.equal(await evaluate("document.activeElement === refreshCatalogue && submitOrder.disabled && orderRetry.hidden"), true);
  state.products = [{ ...products[0], availableStock: 2 }, products[1]];
  await evaluate("refreshCatalogue.click()");
  await ready();
  await evaluate("orderForm.requestSubmit()");
  assert.match(await evaluate('orderLines.querySelector("input + p").textContent'), /within available stock/);
  assert.equal(calls(orderPath, "POST").length, 1);
  state.orderError = [404, { code: "CLIENT_ORDER_PRODUCT_NOT_FOUND" }];
  await select("123", "2");
  await evaluate("orderForm.requestSubmit()");
  await until(() => calls(orderPath, "POST").length === 2, "the missing product response");
  await until(() => evaluate("document.activeElement === refreshCatalogue"), "the required catalogue refresh");
  assert.equal(await evaluate("submitOrder.disabled && orderRetry.hidden"), true);
  await evaluate("refreshCatalogue.click()");
  await ready();
  state.orderError = [400, { code: "CLIENT_ORDER_INVALID", message: "<b>Check delivery fields</b>" }];
  await select("123", "2");
  await evaluate("orderForm.requestSubmit()");
  await until(() => evaluate('orderFeedback.textContent === "<b>Check delivery fields</b>"'), "the validation response");
  assert.equal(await evaluate("orderFeedback.children.length === 0 && !orderDeliveryFields.disabled && createdOrder.hidden"), true);
});

test("a rejected CSRF token refreshes the session and preserves the order key and body", { skip: !browser }, async () => {
  prepare();
  await openOrder();
  await ready();
  await select("123", "1");
  state.token = "fresh-fixture-csrf";
  await evaluate("orderForm.requestSubmit()");
  await until(() => evaluate('!clientPortal.hidden && !orderRetry.hidden && orderFeedback.textContent.includes("session token")'),
    "the refreshed session and original retry");
  assert.equal(state.orders.size, 0);
  await evaluate("retryOrder.click()");
  assert.equal(await created(), savedOrder.orderReference);
  const [first, second] = calls(orderPath, "POST");
  assert.notEqual(first.token, second.token);
  assert.equal(second.token, state.token);
  assert.equal(second.key, first.key);
  assert.equal(second.raw, first.raw);
});

test("asynchronous order results are discarded when the session switches clients or is revoked", { skip: !browser }, async () => {
  let release;
  prepare({ holdOrder: new Promise((resolve) => { release = resolve; }) });
  await openOrder();
  await ready();
  await evaluate(`(() => {
    const send = window.sharedSendRequest;
    window.sharedSendRequest = async (...args) => {
      try {
        return await send(...args);
      } finally {
        if (args[0] === "POST" && args[1] === "/api/client/orders") {
          setTimeout(() => { window.oldOrderReplyReceived = true; }, 0);
        }
      }
    };
  })()`);
  await select("123", "1");
  await evaluate("orderForm.requestSubmit()");
  await until(() => state.orders.size === 1, "the original owner's in-flight order");
  Object.assign(state, { userId: 20, username: "different-client", defaults: { ...defaults, recipientName: "Second Client" } });
  await evaluate('document.dispatchEvent(new Event("visibilitychange"))');
  await until(() => evaluate('orderRecipient.value === "Second Client" && !clientPortal.hidden'), "the new account");
  release();
  await until(() => evaluate("window.oldOrderReplyReceived"), "the old order response reaching the browser");
  assert.equal(await evaluate('!submitOrder.disabled && orderRecipient.value === "Second Client"'), true);
  assert.equal(await evaluate("createdOrder.hidden && !createdOrderReference.textContent && orderRetry.hidden"), true);
  state.revoked = true;
  await evaluate('document.dispatchEvent(new Event("visibilitychange"))');
  await until(() => evaluate('location.pathname === "/shared/shared-login.html"'), "the sign-in page after revocation");
});

test("cross-tab logout drops the pending submission and history navigation cannot restore it", { skip: !browser }, async () => {
  prepare({ loseFirst: true });
  await openOrder();
  await ready();
  await select("123", "1");
  await evaluate("orderForm.requestSubmit()");
  await retryVisible();
  Object.assign(state, { userId: 20, username: "second-client",
    defaults: { ...defaults, recipientName: "New account" } });
  await evaluate('const sender = new BroadcastChannel("lms-client-session"); sender.postMessage("signed-out"); sender.close()');
  await until(() => evaluate('document.getElementById("orderRecipient")?.value === "New account"'), "the new session's form");
  assert.equal(await evaluate("orderRetry.hidden && createdOrder.hidden && !createdOrderReference.textContent"), true);
  const { currentIndex, entries } = await command("Page.getNavigationHistory");
  assert.equal(entries[currentIndex].url, harness.origin + path);
  await command("Page.navigate", { url: harness.origin + "/shared/shared-welcome.html" });
  await until(() => evaluate('location.pathname === "/shared/shared-welcome.html"'), "navigation away from Create Order");
  const accountReads = calls(accountPath).length;
  const sessionChecks = calls("/api/shared/session").length;
  state.revoked = true;
  await command("Page.navigateToHistoryEntry", { entryId: entries[currentIndex].id });
  await until(() => calls("/api/shared/session").length > sessionChecks, "a session check after Back");
  await until(() => evaluate('location.pathname === "/shared/shared-login.html"'), "revocation after Back");
  assert.equal(calls(accountPath).length, accountReads);
  assert.equal(calls(orderPath, "POST").length, 1);
});

test("mobile widths have no horizontal overflow and offer keyboard-accessible navigation and labelled controls", { skip: !browser }, async () => {
  try {
    for (const width of [320, 390]) {
      prepare();
      await command("Emulation.setDeviceMetricsOverride", { width, height: 840, deviceScaleFactor: 1, mobile: true });
      await openOrder();
      await ready();
      assert.equal(await evaluate("document.documentElement.scrollWidth === innerWidth"), true);
      await evaluate("clientMenuToggle.focus()");
      assert.equal(await evaluate("document.activeElement === clientMenuToggle"), true);
      await evaluate("clientMenuToggle.click()");
      assert.equal(await evaluate('clientMenuToggle.getAttribute("aria-expanded")'), "true");
      assert.ok(await evaluate('Array.from(orderForm.querySelectorAll("input, select, textarea")).every((input) => input.labels.length === 1)'));
      await select(wideId, "3");
      assert.equal(await evaluate("document.documentElement.scrollWidth === innerWidth"), true);
      await command("Input.dispatchKeyEvent", { type: "keyDown", key: "Escape", code: "Escape", windowsVirtualKeyCode: 27 });
      await command("Input.dispatchKeyEvent", { type: "keyUp", key: "Escape", code: "Escape", windowsVirtualKeyCode: 27 });
      assert.equal(await evaluate('clientMenuToggle.getAttribute("aria-expanded")'), "false");
    }
  } finally {
    await command("Emulation.clearDeviceMetricsOverride");
  }
});

test("live Spring Boot order submission persists a wide product ID and leaves stock and delivery defaults unchanged",
  { skip: !browser || !liveUrl }, async () => {
    const stack = new URL(liveUrl);
    const username = process.env.LMS_M10B_TEST_USERNAME;
    const password = process.env.LMS_M10B_TEST_PASSWORD;
    assert.equal(stack.hostname, "127.0.0.1");
    assert(username?.startsWith("m10b-fixture-") && password, "Use a disposable M10B fixture account.");
    await command("Page.navigate", { url: new URL("/shared/shared-login.html", stack).href });
    await until(() => evaluate("document.readyState === 'complete' && typeof sharedLoginUser === 'function' && " +
      "!document.getElementById('sharedLoginUserSubmitButton')?.disabled"), "the live login form");
    await evaluate(`sharedLoginUserUsernameInput.value = ${JSON.stringify(username)};
      sharedLoginUserPasswordInput.value = ${JSON.stringify(password)}; sharedLoginUserForm.requestSubmit();`);
    await until(() => evaluate('location.pathname === "/shared/shared-welcome.html"'), "the shared welcome page");
    await command("Page.navigate", { url: new URL(path, stack).href });
    await ready();
    const before = await evaluate("[orderRecipient.value, orderPhone.value, orderAddress.value]");
    assert(before.every((field) => field));
    await select(wideId, "2");
    await evaluate('orderAddress.value = "M10B order only address, Hong Kong"; orderForm.requestSubmit()');
    const reference = await created();
    assert.deepEqual(await evaluate("[createdOrderStatus.textContent, createdOrderTotal.textContent]"), ["AWAITING_PAYMENT", "HK$36.50"]);
    assert(Date.parse(await evaluate("createdOrderDeadline.dateTime")) > Date.now());
    const apiResult = await evaluate(`Promise.all([
      fetch(${JSON.stringify(orderPath + "/" + reference)}).then((response) => response.json()),
      fetch(${JSON.stringify(accountPath)}).then((response) => response.json()),
      import("/client/client-api.js").then((module) => module.readClientProducts(""))
    ])`);
    const [order, account, catalogue] = apiResult;
    assert.equal(order.deliveryDetails.address, "M10B order only address, Hong Kong");
    assert.equal(order.items.length, 1);
    assert.deepEqual(order.paymentAttempts, []);
    assert.deepEqual(Object.values(account.deliveryDefaults), before);
    assert.equal(catalogue.find(({ productId }) => productId === wideId).availableStock, 5);
  });
