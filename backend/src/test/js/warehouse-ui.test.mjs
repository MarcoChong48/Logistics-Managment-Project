import assert from "node:assert/strict";
import { readdir } from "node:fs/promises";
import { after, before, test } from "node:test";
import { browser, startBrowser, until } from "./client-browser-harness.mjs";

const prefix = "/api/warehouse";
const home = "/warehouse/warehouse-available-orders.html";
const reference = "ORD-WH-37";
const sku = "SKU-WH-37";
const base = "/orders/" + reference;
const taskPath = base + "/picking-task";
const taskPage = "/warehouse/warehouse-picking-task.html?order=" + reference;
const time = "2026-11-23T01:00:00Z";
const token = "warehouse-fixture-csrf";
const message = '<img src=x onerror="window.warehouseInjected=true">';
const order = { orderReference: reference, orderStatus: "READY_FOR_PICKING", createdTime: time, unitsOrdered: 2, unitsPicked: 0 };
const notice = { notificationID: 18, isRead: false, message, orderReference: reference, createdTime: time };
let state, reads, writes, harness;
const evaluate = expression => harness.evaluate(expression);
const wait = (expression, label) => until(() => evaluate(expression), label);
const open = path => harness.command("Page.navigate", { url: harness.origin + path });

function fixtureState(overrides = {}) {
  reads = [];
  writes = [];
  state = { loggedIn: true, role: "WAREHOUSE_WORKER", forced: false, picked: 0,
    packed: false, deliveryEnabled: true, notices: [{ ...notice }, { ...notice, notificationID: 19 }],
    commandResults: new Map(), ...overrides };
}

function task() {
  return { orderReference: reference, orderStatus: state.packed ? "READY_FOR_PICKUP" : state.picked
    ? "PICKING" : "READY_FOR_PICKING", taskStatus: state.packed ? "PACKED" : state.picked ? "IN_PROGRESS" : "PENDING",
  assignedUsername: state.picked ? "warehouse-fixture" : null, isMine: state.picked > 0 && !state.packed,
  canScan: !state.packed, canConfirmPacking: state.picked === 2 && !state.packed,
  confirmPackingTime: state.packed ? time : null, hasOpenShortage: false,
  lineList: [{ orderItemID: 42, sku, productName: "Fixture product", orderedQuantity: 2, pickedQuantity: state.picked }] };
}

async function fixture(request, response) {
  const path = new URL(request.url, "http://localhost").pathname;
  const json = (status, data) => response.writeHead(status, { "Content-Type": "application/json" }).end(JSON.stringify(data));
  if (path === "/api/shared/session") {
    if (state.revoked) return json(401, { code: "SHARED_SESSION_ENDED" });
    return json(200, { sharedViewSessionIsLoggedIn: state.loggedIn, username: "warehouse-fixture",
      role: state.role, mustChangePassword: state.forced, sharedViewSessionIdleTimeoutSeconds: 900,
      sharedViewSessionSecurityToken: token, sharedViewSessionSecurityTokenHeaderName: "X-CSRF-TOKEN" });
  }
  if (!path.startsWith(prefix)) return;
  const endpoint = path.slice(prefix.length);
  if (request.method === "GET") {
    reads.push(endpoint);
    const orders = state.empty ? [] : [order];
    if (endpoint === "/summary") return json(200, { availableOrderCount: orders.length, myTaskCount: 0,
      unreadAdminNotificationCount: state.notices.filter(n => !n.isRead).length });
    if (endpoint === "/orders/available" && state.queueFailed) return json(503, { message: "Queue unavailable." });
    if (endpoint === "/orders/available" || endpoint === "/orders") return json(200, { orderRowList: orders, isLimited: false });
    if (endpoint === "/orders/mine") return json(200, { orderRowList: state.assigned
      ? [{ ...order, isMine: true, unitsPicked: 1, orderStatus: "PICKING", remainingItemText: sku }] : [] });
    if (endpoint === "/notifications") return json(200, { adminRowList: state.notices,
      newOrderRowList: orders.map(item => ({ ...item, readyTime: time })) });
    if (endpoint === "/activity") return json(200, { activityRowList: [], scanErrorRowList: [
      { time, orderReference: reference, kind: "REJECTED", text: "Wrong product." }
    ] });
    if (endpoint === taskPath) return json(200, task());
    if (endpoint === base) return json(200, { ...order, lineList: task().lineList,
      timelineRowList: [{ time, kind: "CREATED", text: "Order created." }] });
    if (endpoint === base + "/package") return json(200, { packageQrCode: reference + "-PACKAGE" });
    if (endpoint === base + "/pickup-list") return state.deliveryEnabled
      ? json(200, state.pickup || {}) : json(404, { code: "SHARED_NOT_FOUND" });
    return json(404, { code: "NOT_FOUND" });
  }

  let text = "";
  for await (const chunk of request) text += chunk;
  const body = text ? JSON.parse(text) : null;
  const csrf = request.headers["x-csrf-token"];
  writes.push({ endpoint, body, csrf });
  if (csrf !== token) return json(403, { code: "SHARED_SECURITY_TOKEN_INVALID" });
  if (endpoint === "/notifications/read-all") {
    for (const item of state.notices) item.isRead = true;
    return json(200, { markedRead: state.notices.length });
  }
  if (endpoint === "/notifications/18/read") {
    state.notices[0].isRead = true;
    return json(200, { isRead: true });
  }
  if (body?.requestId && state.commandResults.has(body.requestId)) {
    return json(200, state.commandResults.get(body.requestId));
  }
  let result;
  if (endpoint === taskPath + "/scans") {
    if (body.scannedIdentifier === sku && state.picked < 2) {
      state.picked++;
      result = { result: "ACCEPTED", message: "Picked one unit.", task: task() };
    } else {
      result = { result: "REJECTED", message: "Check the product SKU.", task: task() };
    }
  } else if (endpoint === taskPath + "/items/42/picked-quantity") {
    state.picked = body.pickedQuantity;
    result = { result: "REVERSED", message: "Pick reversed.", task: task() };
  } else if (endpoint === taskPath + "/confirm-packing") {
    state.packed = true;
    result = { result: "PACKED", message: "Packing confirmed.", task: task() };
  } else if (endpoint === base + "/pickup-list") {
    state.pickup = { pickupListId: "51", listQrCode: "PL-WH-37" };
    result = state.pickup;
  } else {
    return json(404, { code: "NOT_FOUND" });
  }
  if (body?.requestId) state.commandResults.set(body.requestId, result);
  if (endpoint.endsWith("/scans") && state.dropScanResponse) {
    state.dropScanResponse = false;
    response.writeHead(200, { "Content-Type": "application/json", "Content-Length": "150" });
    return response.write("{", () => response.destroy());
  }
  return json(200, result);
}

async function view(path) {
  await open(path);
  await wait(`location.pathname === ${JSON.stringify(path.split("?")[0])}
    && document.getElementById("warehouse-page-content")?.hidden === false
    && !document.getElementById("warehouse-refresh").disabled`, "the authorized Warehouse view");
}

function submitScan(value) {
  return evaluate(`document.getElementById("scan-item-id").value = ${JSON.stringify(value)};
    document.getElementById("item-scan-form").requestSubmit()`);
}

const press = id => evaluate("document.getElementById(" + JSON.stringify(id) + ").click()");
const includes = (id, text) => wait(`document.getElementById(${JSON.stringify(id)})?.textContent.includes(${JSON.stringify(text)})`, text);

before(async () => {
  if (browser) {
    fixtureState();
    harness = await startBrowser(fixture);
  }
});
after(async () => { await harness?.close(); });

test("only the six operational Warehouse HTML pages are deployed", async () => {
  const files = await readdir(new URL("../../main/resources/static/warehouse/", import.meta.url));
  assert.deepEqual(files.filter(name => name.endsWith(".html")).sort(), [
    "warehouse-available-orders.html", "warehouse-my-orders.html", "warehouse-notifications.html",
    "warehouse-order-history.html", "warehouse-picking-task.html", "warehouse-scan-log.html"
  ]);
});

test("Warehouse session gates block data requests for guests, other roles and forced or revoked sessions", { skip: !browser }, async () => {
  for (const [settings, expected] of [
    [{ loggedIn: false }, "/shared/shared-login.html"],
    [{ role: "CLIENT" }, "/shared/shared-welcome.html"],
    [{ forced: true }, "/shared/shared-change-password.html"],
    [{ revoked: true }, "/shared/shared-login.html"]
  ]) {
    fixtureState(settings);
    await open(home);
    await wait("location.pathname === " + JSON.stringify(expected), expected);
    assert.deepEqual(reads, []);
  }
});

test("queues recover from errors and render empty, assigned, history and scan activity states", { skip: !browser }, async () => {
  fixtureState({ queueFailed: true, empty: true });
  await open(home);
  await includes("warehouse-page-status", "Queue unavailable.");
  state.queueFailed = false;
  await press("warehouse-refresh");
  await includes("order-rows", "No orders are ready for picking.");
  state.empty = false;
  await press("warehouse-refresh");
  await includes("order-rows", reference);
  assert.equal(await evaluate('document.querySelector("#order-rows a").getAttribute("href")'),
    "warehouse-picking-task.html?order=" + reference);

  state.assigned = true;
  await view("/warehouse/warehouse-my-orders.html");
  await includes("current-task", "1 of 2 picked");
  await view("/warehouse/warehouse-order-history.html");
  await evaluate('document.getElementById("order-query").value = "missing"; document.getElementById("order-query").dispatchEvent(new Event("input"))');
  await includes("order-rows", "No orders match the filters.");
  await evaluate('document.getElementById("order-query").value = ""; document.getElementById("order-query").dispatchEvent(new Event("input")); document.querySelector("#order-rows button").click()');
  await wait('document.getElementById("order-details").open', "order details");
  await includes("order-details-body", "Order created.");
  await view("/warehouse/warehouse-scan-log.html");
  await includes("scan-errors", "Wrong product.");
  await includes("activity-rows", "No picking activity today.");
});

test("notification rendering keeps message text inert and both read actions send shared CSRF", { skip: !browser }, async () => {
  fixtureState();
  await view("/warehouse/warehouse-notifications.html");
  await includes("admin-notifications", message);
  assert.equal(await evaluate('document.querySelector("#admin-notifications img") === null && !window.warehouseInjected'), true);
  await evaluate('document.querySelector("#admin-notifications button").click()');
  await wait('document.querySelectorAll("#admin-notifications button").length === 1', "one unread notification");
  await press("mark-all");
  await wait('document.getElementById("mark-all").disabled && !document.querySelector("#admin-notifications button")', "all read");
  assert.deepEqual(writes.map(({ endpoint, csrf }) => [endpoint, csrf]),
    [["/notifications/18/read", token], ["/notifications/read-all", token]]);
});

test("scan retry survives reload, a reversal needs a reason and packing is separate from list creation", { skip: !browser }, async () => {
  fixtureState({ dropScanResponse: true });
  await view(taskPage);
  assert.equal(await evaluate('document.getElementById("pack-task").disabled'), true);
  await submitScan(sku);
  await wait('!document.getElementById("retry-action").hidden && !document.getElementById("retry-action").disabled', "the pending scan");
  const first = writes[0].body;
  assert.match(first.requestId, /^[0-9a-f-]{36}$/);
  await view(taskPage);
  assert.equal(await evaluate('document.getElementById("scan-item-id").disabled'), true);
  await press("retry-action");
  await includes("scan-feedback", "Picked one unit.");
  await wait('!document.getElementById("scan-item-id").disabled', "the completed retry");
  assert.deepEqual(writes[1].body, first);
  assert.equal(state.picked, 1);
  await submitScan("WRONG-SKU");
  await includes("scan-feedback", "Check the product SKU.");
  await wait('!document.getElementById("record-scan").disabled', "the rejected scan to settle");
  await evaluate('document.querySelector("#task-items button").click()');
  await evaluate('document.getElementById("reverse-reason").value = "   "; document.getElementById("reverse-form").requestSubmit()');
  assert.equal(await evaluate('document.getElementById("reverse-dialog").open'), true);
  assert.equal(writes.length, 3);
  await evaluate('document.getElementById("reverse-reason").value = "Wrong shelf"; document.getElementById("reverse-reason").dispatchEvent(new Event("input")); document.getElementById("reverse-form").requestSubmit()');
  await includes("task-progress", "0 of 2 picked");
  assert.deepEqual({ ...writes[3].body, requestId: "" }, { pickedQuantity: 0, reason: "Wrong shelf", requestId: "" });
  for (let index = 0; index < 2; index++) {
    await wait('!document.getElementById("record-scan").disabled', "the next deliberate scan");
    await submitScan(sku);
    await includes("task-progress", (index + 1) + " of 2 picked");
  }
  assert.notEqual(writes[4].body.requestId, writes[5].body.requestId);
  await wait('!document.getElementById("pack-task").disabled', "packing to be enabled");
  await press("pack-task");
  await press("confirm-pack");
  await wait('!document.getElementById("create-pickup-list").disabled && !document.getElementById("create-pickup-list").hidden', "the separate Pickup List action");
  assert.equal(writes.some(entry => entry.endpoint === base + "/pickup-list"), false);
  await includes("package-code", reference + "-PACKAGE");
  await press("create-pickup-list");
  await includes("pickup-list-status", "PL-WH-37");
  assert.equal(await evaluate('document.getElementById("pickup-list-qr").getAttribute("href")'),
    prefix + base + "/pickup-list/51/qr-code");
  assert.equal(writes.every(entry => entry.csrf === token), true);
});

test("packing and package labels work without Delivery, including responsive navigation", { skip: !browser }, async () => {
  fixtureState({ packed: true, picked: 2, deliveryEnabled: false });
  await harness.command("Emulation.setDeviceMetricsOverride", { width: 390, height: 820, deviceScaleFactor: 1, mobile: true });
  try {
    await view(taskPage);
    await includes("pickup-list-status", "unavailable");
    assert.equal(await evaluate('document.getElementById("create-pickup-list").hidden'), true);
    assert.equal(await evaluate('document.getElementById("package-qr").getAttribute("href")'), prefix + base + "/package/qr-code");
    assert.equal(await evaluate('document.documentElement.scrollWidth <= innerWidth + 1'), true);
    await press("warehouse-open-menu");
    assert.equal(await evaluate('document.getElementById("warehouse-open-menu").getAttribute("aria-expanded")'), "true");
    await evaluate('document.getElementById("warehouse-close-menu").dispatchEvent(new KeyboardEvent("keydown", { key: "Escape", bubbles: true }))');
    assert.equal(await evaluate('document.activeElement.id'), "warehouse-open-menu");
  } finally {
    await harness.command("Emulation.clearDeviceMetricsOverride");
  }
});
