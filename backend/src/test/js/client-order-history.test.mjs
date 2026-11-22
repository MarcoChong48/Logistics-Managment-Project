import assert from "node:assert/strict";
import { after, before, test } from "node:test";
import { browser, startBrowser, until } from "./client-browser-harness.mjs";

const listPage = "/client/my-orders-tracking.html";
const detailPage = "/client/order-details.html";
const ordersApi = "/api/client/orders";
const knownReference = "ORD-FIXTURE-01";
const listCursor = "cursor +/&= 紙";
const placedAt = "2026-11-21T23:20:00Z";
const attemptAt = "2026-11-22T02:47:00Z";
const endedAt = "2026-11-22T03:12:00Z";
const account = { userId: "10", username: "client-fixture", createdAt: "2026-11-01T10:00:00Z",
  deliveryDefaults: { recipientName: null, contactPhone: null, deliveryAddress: null } };
const samples = [
  { orderReference: knownReference, createdAt: placedAt, orderStatus: "READY_FOR_PICKING",
    totalAmountHkd: 36.5, paymentDeadline: null, itemSummary: { productCount: 2, totalQuantity: 3 } },
  { orderReference: "ORD-FIXTURE-02", createdAt: placedAt, orderStatus: "AWAITING_PAYMENT",
    totalAmountHkd: 27.1, paymentDeadline: "2026-11-22T23:20:00Z", itemSummary: { productCount: 1, totalQuantity: 1 } },
  { orderReference: "ORD-FIXTURE-03", createdAt: "2026-11-21T20:00:00Z", orderStatus: "DELIVERED",
    totalAmountHkd: 18, paymentDeadline: null, itemSummary: { productCount: 1, totalQuantity: 5 } }
];
const pending = { status: "PENDING", createdAt: placedAt, acceptedAt: null, refunds: [] };
const accepted = { status: "ACCEPTED", createdAt: placedAt, acceptedAt: attemptAt, refunds: [
  { status: "REQUESTED", amountHkd: 36.5, requestedAt: endedAt, completedAt: null }
] };
const failedTracking = {
  orderReference: knownReference, orderStatus: "DELIVERY_FAILED", statusLabel: "Delivery failed",
  currentStatusSince: null, historyComplete: false,
  progression: [
    { type: "ORDER_PLACED", occurredAt: placedAt, attemptNumber: null, cycleNumber: null },
    { type: "DELIVERY_ATTEMPT_STARTED", occurredAt: attemptAt, attemptNumber: 1, cycleNumber: null },
    { type: "DELIVERY_FAILED", occurredAt: endedAt, attemptNumber: 1, cycleNumber: null }
  ],
  deliveryAttempts: [{ attemptNumber: 1, cycleNumber: null, deliveryAddressVersion: 1, startedAt: attemptAt,
    endedAt, outcome: "FAILED", failureReason: "The recipient could not be contacted." }],
  paymentAttempts: [accepted]
};
let state, requests, harness;
const cancellationMessages = [];
const command = (...args) => harness.command(...args);
const evaluate = (...args) => harness.evaluate(...args);
const calls = (path, method = "GET") => requests.filter((entry) => entry.path === path && entry.method === method);
const waitFor = (expression, label) => until(() => evaluate(expression), label);
const pathFor = (reference) => detailPage + "?" + new URLSearchParams({ reference });
const orderUrl = (reference = knownReference) => ordersApi + "/" + encodeURIComponent(reference);

function prepare(overrides = {}) {
  requests = [];
  state = {
    signedIn: true, token: "fixture-csrf", account: { ...account }, role: "CLIENT", forced: false,
    list: samples.slice(0, 2), nextCursor: listCursor, nextPage: [samples[2]],
    order: {
      ...samples[0], deliveryDetails: { version: 2, recipientName: "Saved recipient", phone: "61234567",
        address: "Unit 2\nKowloon, Hong Kong" },
      items: [
        { name: "<img src=x onerror=window.injected=true>", sku: "<script>", labelSource: "CURRENT_CATALOGUE",
          quantity: 2, unitPriceHkd: 10.05 },
        { name: null, sku: null, labelSource: "UNAVAILABLE", quantity: 1, unitPriceHkd: 16.4 }
      ],
      paymentAttempts: [pending, accepted], canCancel: true
    },
    tracking: { ...failedTracking }, ...overrides
  };
}

async function fixture(request, response) {
  const url = new URL(request.url, "http://localhost");
  if (!url.pathname.startsWith("/api/")) return;
  const entry = { path: url.pathname, method: request.method, params: Object.fromEntries(url.searchParams),
    token: request.headers["x-csrf-token"] };
  requests.push(entry);
  const json = (status, body) => response.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store"
  }).end(JSON.stringify(body));
  if (entry.path === "/api/shared/session") {
    if (state.revoked) return json(401, { code: "SHARED_SESSION_ENDED" });
    return json(200, {
      sharedViewSessionIsLoggedIn: state.signedIn, role: state.role, username: state.account.username,
      userID: Number(state.account.userId), mustChangePassword: state.forced,
      sharedViewSessionIdleTimeoutSeconds: 900, sharedViewSessionSecurityToken: state.token,
      sharedViewSessionSecurityTokenHeaderName: "X-CSRF-TOKEN"
    });
  }
  if (entry.path === "/api/shared/logout" && entry.method === "POST") {
    assert.equal(entry.token, state.token);
    state.signedIn = false;
    return json(200, { message: "Logged out." });
  }
  if (entry.path === "/api/client/account") return json(200, state.account);
  if (entry.path === ordersApi && entry.method === "GET") {
    const cursor = url.searchParams.get("cursor");
    const page = cursor === null ? { orders: state.list, nextCursor: state.nextCursor }
      : state.cursorPages?.get(cursor) ?? { orders: state.nextPage, nextCursor: null };
    if (state.holdList) await state.holdList;
    if (state.dropList) {
      response.writeHead(200, { "Content-Type": "application/json", "Content-Length": "128" });
      return response.write("{", () => response.destroy());
    }
    return json(200, page);
  }
  if (entry.path === orderUrl() && entry.method === "GET") {
    const order = state.order;
    if (state.holdDetails) await state.holdDetails;
    return json(200, order);
  }
  if (entry.path === orderUrl() + "/tracking" && entry.method === "GET") return json(200, state.tracking);
  if (entry.path === orderUrl() + "/cancel" && entry.method === "POST") {
    if (entry.token !== state.token) return json(403, { code: "SHARED_SECURITY_TOKEN_INVALID" });
    if (state.holdCancel) await state.holdCancel;
    if (state.cancelNotFound) return json(404, { code: "CLIENT_ORDER_NOT_FOUND", message: "Internal fixture details" });
    if (state.cancelConflict) {
      state.order = { ...state.order, orderStatus: "PICKING", canCancel: false };
      return json(409, { code: "CLIENT_ORDER_CANCELLATION_NOT_ALLOWED" });
    }
    state.order = { ...state.order, orderStatus: "CANCELLED", canCancel: false };
    state.tracking = { ...state.tracking, orderStatus: "CANCELLED", statusLabel: "Cancelled" };
    state.list = [state.order];
    if (state.holdCancelResponse) await state.holdCancelResponse;
    return json(200, { orderReference: knownReference, orderStatus: "CANCELLED" });
  }
  return json(404, { code: "CLIENT_ORDER_NOT_FOUND", message: "Internal fixture details must be ignored." });
}

async function open(path, origin = harness.origin) {
  await command("Page.navigate", { url: origin + path });
}

function listReady() {
  return waitFor('document.getElementById("orderHistory")?.children.length', "an order history row");
}

function detailReady() {
  return waitFor('document.getElementById("orderDetailsContent")?.hidden === false', "the order details");
}

before(async () => {
  if (!browser) return;
  prepare();
  harness = await startBrowser(fixture, ({ method, params }) => {
    if (method === "Runtime.bindingCalled" && params.name === "reportCancellationMessage") {
      cancellationMessages.push(params.payload);
    }
  });
  await command("Runtime.addBinding", { name: "reportCancellationMessage" });
});

after(async () => {
  await harness?.close();
});

test("order pages check guest, role and password restrictions before fetching private records", { skip: !browser }, async () => {
  for (const [override, page, target] of [
    [{ signedIn: false }, listPage, "/shared/shared-login.html"],
    [{ role: "ADMIN" }, pathFor(knownReference), "/shared/shared-welcome.html"],
    [{ forced: true }, pathFor(knownReference), "/shared/shared-change-password.html"]
  ]) {
    prepare(override);
    await open(page);
    await waitFor("location.pathname === " + JSON.stringify(target), target);
    assert.equal(requests.some(({ path }) => path.startsWith("/api/client/")), false);
  }
});

test("the list follows the exact opaque cursor, appends stable rows, and refreshes from the first page", { skip: !browser }, async () => {
  prepare();
  await open(listPage);
  await listReady();
  assert.equal(await evaluate('document.querySelector("[aria-current=page]").textContent.trim()'), "My Orders & Tracking");
  assert.deepEqual(calls(ordersApi)[0].params, { limit: "20" });
  assert.match(await evaluate("orderHistory.firstElementChild.textContent"), /ORD-FIXTURE-01.*Ready for picking.*2 products.*3 units.*36.50/s);
  assert.equal(await evaluate('orderHistory.querySelector("a").getAttribute("href")'), pathFor(knownReference));
  await evaluate("loadMoreOrders.click(); loadMoreOrders.click()");
  await waitFor("orderHistory.children.length === 3", "the next page");
  assert.deepEqual(calls(ordersApi).map(({ params }) => params), [{ limit: "20" }, { limit: "20", cursor: listCursor }]);
  assert.deepEqual(await evaluate('Array.from(orderHistory.querySelectorAll(".client-order-reference"), (row) => row.textContent)'),
    samples.map(({ orderReference }) => orderReference));
  assert.equal(await evaluate("ordersMoreAction.hidden && document.activeElement === orderHistory.lastElementChild.querySelector('a')"), true);
  await evaluate("refreshOrders.click()");
  await waitFor("orderHistory.children.length === 2", "the refreshed first page");
  assert.deepEqual(calls(ordersApi).at(-1).params, { limit: "20" });
});

test("list loading, request failure, refresh, and empty history are visible", { skip: !browser }, async () => {
  let release;
  prepare({ list: [], nextCursor: null, dropList: true, holdList: new Promise((resolve) => { release = resolve; }) });
  try {
    await open(listPage);
    await waitFor('document.getElementById("orderHistory")?.getAttribute("aria-busy") === "true"', "loading orders");
    assert.equal(await evaluate("refreshOrders.disabled && ordersMoreAction.hidden"), true);
    release();
    await waitFor('ordersNotice.dataset.state === "error" && !refreshOrders.disabled', "the error state");
    assert.equal(await evaluate("orderHistory.children.length"), 0);
    state.dropList = false;
    await evaluate("refreshOrders.click()");
    await waitFor('ordersNotice.textContent.includes("no orders yet")', "the empty state");
    assert.equal(await evaluate("ordersMoreAction.hidden"), true);
  } finally {
    release();
  }
});

test("Refresh and Load More cannot mix results while a request is in flight", { skip: !browser }, async () => {
  let release;
  prepare();
  await open(listPage);
  await listReady();
  state.holdList = new Promise((resolve) => { release = resolve; });
  try {
    await evaluate("loadMoreOrders.click(); refreshOrders.click(); loadMoreOrders.click()");
    await until(() => calls(ordersApi).length === 2, "one in-flight page request");
    assert.equal(await evaluate("orderHistory.children.length === 2 && refreshOrders.disabled && loadMoreOrders.disabled"), true);
    state.list = [{ ...samples[0], orderReference: "ORD-NEW-FIRST" }];
    state.nextCursor = null;
    state.holdList = null;
    release();
    await waitFor("orderHistory.children.length === 3 && !refreshOrders.disabled", "the appended page");
    assert.equal(calls(ordersApi).length, 2);
    await evaluate("refreshOrders.click()");
    await waitFor('orderHistory.textContent.includes("ORD-NEW-FIRST")', "a fresh first page");
    assert.equal(await evaluate("orderHistory.children.length"), 1);
    assert.deepEqual(calls(ordersApi).at(-1).params, { limit: "20" });
  } finally {
    release();
  }
});

test("a looping server cursor is rejected and a malformed first page never renders", { skip: !browser }, async () => {
  const middle = "next +/? middle";
  prepare({ cursorPages: new Map([
    [listCursor, { orders: [samples[2]], nextCursor: middle }],
    [middle, { orders: [samples[0]], nextCursor: listCursor }]
  ]) });
  await open(listPage);
  await listReady();
  await evaluate("loadMoreOrders.click()");
  await waitFor("orderHistory.children.length === 3", "the valid middle page");
  await evaluate("loadMoreOrders.click()");
  await waitFor('ordersNotice.dataset.state === "error" && ordersMoreAction.hidden', "the circular cursor rejection");
  assert.deepEqual(calls(ordersApi).slice(1).map(({ params }) => params.cursor), [listCursor, middle]);
  assert.equal(await evaluate("orderHistory.children.length"), 3);
  state.list = [{ ...samples[0], itemSummary: { productCount: null, totalQuantity: 3 } }];
  await evaluate("refreshOrders.click()");
  await waitFor('ordersNotice.dataset.state === "error" && !refreshOrders.disabled', "the malformed list rejection");
  assert.equal(await evaluate("orderHistory.children.length"), 0);
});

test("an old list response cannot append orders after a cross-tab account switch", { skip: !browser }, async () => {
  let release;
  prepare();
  await open(listPage);
  await listReady();
  state.holdList = new Promise((resolve) => { release = resolve; });
  try {
    await evaluate("loadMoreOrders.click()");
    await until(() => calls(ordersApi).length === 2, "the previous Client's delayed page");
    state.account = { ...account, userId: "20", username: "another-client" };
    state.list = [{ ...samples[0], orderReference: "ORD-ANOTHER-CLIENT" }];
    state.nextCursor = null;
    state.holdList = null;
    await evaluate('const sender = new BroadcastChannel("lms-client-session"); sender.postMessage("signed-out"); sender.close()');
    await waitFor('clientUsername.textContent === "another-client" && orderHistory.textContent.includes("ORD-ANOTHER-CLIENT")',
      "the new Client's first page");
    release();
    await evaluate("new Promise((resolve) => setTimeout(resolve, 100))");
    assert.equal(await evaluate("orderHistory.children.length === 1 && ordersMoreAction.hidden && !refreshOrders.disabled"), true);
    assert.doesNotMatch(await evaluate("orderHistory.textContent"), /ORD-FIXTURE/);
  } finally {
    release();
  }
});

test("missing and unowned orders show the same unavailable state without reflecting unsafe references", { skip: !browser }, async () => {
  prepare();
  for (const reference of ["ORD-MISSING", "ORD-UNOWNED", "../private"]) {
    await open(pathFor(reference));
    await waitFor('document.getElementById("orderDetailsNotice")?.textContent === "This order is unavailable."', "the neutral 404");
    assert.equal(await evaluate("orderDetailsContent.hidden && openCancelOrder.hidden && !detailReference.textContent"), true);
  }
  assert(calls(orderUrl("ORD-UNOWNED")).length > 0);
  assert.equal(requests.some(({ path }) => path.includes("private")), false);
});

test("details render historical prices, current catalogue labels, safe delivery text and separate payment/refund states", { skip: !browser }, async () => {
  prepare();
  state.order.deliveryDetails.address = "<svg onload=window.injected=true>\nKowloon";
  await open(pathFor(knownReference));
  await detailReady();
  const facts = await evaluate("orderDetailFacts.textContent");
  assert.match(facts, /Ready for picking.*HKT.*Confirmed total.*36.50/s);
  assert.doesNotMatch(facts, /Payment deadline/);
  const items = await evaluate("orderDetailItems.textContent");
  assert.match(items, /Current catalogue.*SKU <script>.*Historical unit price.*10.05.*Product label unavailable.*16.40/s);
  assert.match(await evaluate("orderDetailDelivery.textContent"), /<svg onload=window.injected=true>\nKowloon/);
  assert.equal(await evaluate("orderDetailsContent.querySelectorAll('img, svg, script').length"), 0);
  assert.equal(await evaluate("window.injected === undefined"), true);
  assert.match(await evaluate("orderDetailPayments.textContent"), /Payment pending.*Payment accepted.*Refund requested/s);
  assert.doesNotMatch(await evaluate("orderDetailPayments.textContent"), /Refund completed/);
  state.order = { ...state.order, orderStatus: "AWAITING_PAYMENT", paymentDeadline: "2026-11-22T23:20:00Z", deliveryDetails: null };
  await evaluate("refreshOrderDetails.click()");
  await detailReady();
  assert.match(await evaluate("orderDetailFacts.textContent"), /Payment deadline.*HKT/);
  assert.match(await evaluate("orderDetailDelivery.textContent"), /Delivery details are unavailable/);
});

test("refund completion requires an authoritative completed state with a valid completion time", { skip: !browser }, async () => {
  prepare();
  const refund = accepted.refunds[0];
  state.order.paymentAttempts = [{ ...accepted, refunds: [
    { ...refund, status: "FAILED" },
    { ...refund, status: "REQUESTED", completedAt: refund.requestedAt },
    { ...refund, status: "COMPLETED", completedAt: attemptAt },
    { ...refund, status: "COMPLETED", completedAt: "2026-11-22T03:12:08.123456Z" }
  ] }];
  await open(pathFor(knownReference));
  await detailReady();
  assert.deepEqual(await evaluate('Array.from(orderDetailPayments.querySelectorAll(".client-order-refunds > li > strong"), (row) => row.textContent)'),
    ["Refund failed", "Refund requested", "Refund status unavailable", "Refund completed"]);
  assert.equal(await evaluate('orderDetailPayments.querySelectorAll("time[datetime=\'2026-11-22T03:12:08.123456Z\']").length'), 1);
});

test("tracking uses recorded events and nullable timestamps and exposes no failed-delivery mutations", { skip: !browser }, async () => {
  prepare();
  state.order = { ...state.order, orderStatus: "DELIVERY_FAILED", canCancel: false };
  await open(pathFor(knownReference));
  await detailReady();
  assert.deepEqual(await evaluate('Array.from(trackingContent.querySelectorAll("ol time"), (item) => item.dateTime)'),
    [placedAt, attemptAt, endedAt]);
  assert.equal(await evaluate('trackingContent.querySelector(".client-order-tracking-status time") === null'), true);
  assert.match(await evaluate("trackingContent.textContent"), /Earlier tracking events may be unavailable/);
  assert.match(await evaluate("trackingContent.textContent"), /The recipient could not be contacted/);
  assert.doesNotMatch(await evaluate("trackingContent.textContent"), /Cycle|ETA/);
  assert.equal(await evaluate("!postDeliveryActions.hidden && openCancelOrder.hidden"), true);
  assert.equal(await evaluate('Array.from(postDeliveryActions.querySelectorAll("button")).every((button) => button.disabled)'), true);
  await evaluate('postDeliveryActions.querySelectorAll("button").forEach((button) => button.click())');
  assert.equal(requests.some(({ method }) => method !== "GET"), false);
});

test("tracking keeps delivery cycles distinct and rejects an invalid timestamp instead of normalizing it", { skip: !browser }, async () => {
  prepare();
  const retryStart = "2026-11-22T07:28:00Z";
  state.tracking = { ...failedTracking, progression: [...failedTracking.progression,
    { type: "DELIVERY_ATTEMPT_STARTED", occurredAt: retryStart, attemptNumber: 2, cycleNumber: 2 }],
  deliveryAttempts: [{ ...failedTracking.deliveryAttempts[0], cycleNumber: 1 },
    { attemptNumber: 2, cycleNumber: 2, deliveryAddressVersion: 2, startedAt: retryStart,
      endedAt: null, outcome: "IN_PROGRESS", failureReason: null }] };
  await command("Emulation.setTimezoneOverride", { timezoneId: "America/Los_Angeles" });
  try {
    await open(pathFor(knownReference));
    await detailReady();
    const attempts = await evaluate('Array.from(trackingContent.querySelectorAll(".client-order-records > li"), (row) => row.textContent)');
    assert.equal(attempts.length, 2);
    assert.match(attempts[0], /Attempt 1.*Cycle1.*Address version1/s);
    assert.match(attempts[1], /Attempt 2.*Cycle2.*Address version2/s);
    assert.doesNotMatch(attempts[1], /Ended/);
    assert.deepEqual(await evaluate('Array.from(trackingContent.querySelectorAll("ol time"), (row) => row.dateTime)'),
      [placedAt, attemptAt, endedAt, retryStart]);
    assert.match(await evaluate('trackingContent.querySelector("ol time").textContent'), /22 Nov 2026, 7:20 am HKT/i);
    state.tracking.currentStatusSince = "2026-02-30T04:00:00Z";
    await evaluate("refreshOrderDetails.click()");
    await waitFor('trackingNotice.dataset.state === "error"', "invalid tracking date rejection");
    assert.equal(await evaluate('trackingContent.querySelectorAll("time").length'), 0);
    assert.equal(await evaluate("orderDetailsContent.hidden"), false);
  } finally {
    await command("Emulation.setTimezoneOverride", { timezoneId: "" });
  }
});

test("cancellation is confirmed once with CSRF, refreshes details and tracking, and the list reflects it on return", { skip: !browser }, async () => {
  let release;
  prepare({ holdCancel: new Promise((resolve) => { release = resolve; }) });
  try {
    await open(pathFor(knownReference));
    await detailReady();
    await evaluate("openCancelOrder.click()");
    assert.equal(await evaluate("cancelOrderDialog.open && document.activeElement === keepOrder"), true);
    await evaluate("keepOrder.click()");
    assert.equal(calls(orderUrl() + "/cancel", "POST").length, 0);
    await evaluate("openCancelOrder.click(); confirmCancelOrder.click(); confirmCancelOrder.click()");
    const request = await until(() => calls(orderUrl() + "/cancel", "POST")[0], "the confirmed cancellation");
    assert.equal(request.token, "fixture-csrf");
    assert.equal(await evaluate("openCancelOrder.disabled && !cancelOrderDialog.open"), true);
    assert.doesNotMatch(await evaluate("cancellationNotice.textContent"), /Order cancelled/);
    release();
    await waitFor('orderDetailFacts.textContent.includes("Cancelled") && !orderDetailsContent.hidden', "the returned order status");
    assert.equal(calls(orderUrl() + "/cancel", "POST").length, 1);
    assert(calls(orderUrl()).length >= 2 && calls(orderUrl() + "/tracking").length >= 2);
    assert.equal(await evaluate("openCancelOrder.hidden"), true);
    assert.match(await evaluate("orderDetailPayments.textContent"), /Refund requested/);
    assert.doesNotMatch(await evaluate("orderDetailPayments.textContent"), /Refund completed/);
    await evaluate('document.querySelector(".client-order-back").click()');
    await listReady();
    assert.match(await evaluate("orderHistory.textContent"), /Cancelled/);
    assert.equal(requests.some(({ path }) => /checkout|instructions|delivery-options/.test(path)), false);
  } finally {
    release();
  }
});

test("a 409 refreshes eligibility and an invalid CSRF token cannot cancel an order", { skip: !browser }, async () => {
  prepare({ cancelConflict: true });
  await open(pathFor(knownReference));
  await detailReady();
  state.token = "rotated-token";
  await evaluate("openCancelOrder.click(); confirmCancelOrder.click()");
  await waitFor('cancellationNotice.textContent.includes("token was rejected") && !orderDetailsContent.hidden && !openCancelOrder.disabled',
    "the CSRF error and refreshed order");
  assert.equal(state.order.orderStatus, "READY_FOR_PICKING");
  await evaluate("openCancelOrder.click(); confirmCancelOrder.click()");
  await waitFor('orderDetailFacts.textContent.includes("Picking") && openCancelOrder.hidden', "the 409 refresh");
  assert.match(await evaluate("cancellationNotice.textContent"), /eligibility may have changed/);
  assert.equal(calls(orderUrl() + "/cancel", "POST")[1].token, "rotated-token");
});

test("a cancellation timeout keeps the outcome uncertain until a fresh read shows the server state", { skip: !browser }, async () => {
  let release;
  const { identifier } = await command("Page.addScriptToEvaluateOnNewDocument", {
    source: 'const actualTimeout = window.setTimeout.bind(window); window.setTimeout = (fn, delay, ...args) => actualTimeout(fn, delay === 30000 ? 700 : delay, ...args)'
  });
  try {
    prepare({ holdCancelResponse: new Promise((resolve) => { release = resolve; }) });
    await open(pathFor(knownReference));
    await detailReady();
    await evaluate("openCancelOrder.click(); confirmCancelOrder.click()");
    await until(() => state.order.orderStatus === "CANCELLED", "the committed server fixture without a response");
    await waitFor('orderDetailFacts.textContent.includes("Cancelled") && !orderDetailsContent.hidden', "the timeout recovery read");
    assert.match(await evaluate("cancellationNotice.textContent"), /timed out/);
    assert.equal(await evaluate("openCancelOrder.hidden"), true);
    assert.equal(calls(orderUrl() + "/cancel", "POST").length, 1);
    release();
    await evaluate("new Promise((resolve) => setTimeout(resolve, 100))");
    assert.match(await evaluate("cancellationNotice.textContent"), /timed out/);
  } finally {
    release?.();
    await command("Page.removeScriptToEvaluateOnNewDocument", { identifier });
  }
});

test("a missing order during cancellation returns the same unavailable state", { skip: !browser }, async () => {
  prepare({ cancelNotFound: true });
  await open(pathFor(knownReference));
  await detailReady();
  await evaluate("openCancelOrder.click(); confirmCancelOrder.click()");
  await waitFor('orderDetailsNotice.textContent === "This order is unavailable."', "the unavailable cancellation target");
  assert.equal(await evaluate("orderDetailsContent.hidden && cancellationNotice.hidden && openCancelOrder.hidden"), true);
});

test("a delayed cancellation receipt cannot show success after the session cookie switches clients", { skip: !browser }, async () => {
  let release;
  cancellationMessages.length = 0;
  prepare({ holdCancelResponse: new Promise((resolve) => { release = resolve; }) });
  try {
    await open(pathFor(knownReference));
    await detailReady();
    await evaluate('new MutationObserver(() => reportCancellationMessage(cancellationNotice.textContent))'
      + '.observe(cancellationNotice, { childList: true })');
    await evaluate("openCancelOrder.click(); confirmCancelOrder.click()");
    await until(() => state.order.orderStatus === "CANCELLED", "the accepted cancellation awaiting its receipt");
    await until(() => cancellationMessages.some((message) => /Cancelling/.test(message)), "the cancellation notice observer");
    state.account = { ...account, userId: "20", username: "another-client" };
    release();
    await waitFor('location.pathname === "/shared/shared-login.html"', "the mismatched session redirect");
    assert.equal(cancellationMessages.includes("Order cancelled."), false);
    assert.equal(calls(orderUrl() + "/cancel", "POST").length, 1);
  } finally {
    release();
  }
});

test("switching accounts during a read clears the old order, and logout and revocation clear private history", { skip: !browser }, async () => {
  let release;
  prepare({ holdDetails: new Promise((resolve) => { release = resolve; }) });
  try {
    await open(pathFor(knownReference));
    await until(() => calls(orderUrl()).length === 1, "the delayed order read");
    state.account = { ...account, userId: "20", username: "another-client" };
    // The second session cannot read this order. Keep the first response in flight.
    state.order = { ...state.order, orderReference: "ORD-ANOTHER-CLIENT" };
    state.holdDetails = null;
    await evaluate('const signOut = new BroadcastChannel("lms-client-session"); signOut.postMessage("signed-out"); signOut.close()');
    await waitFor('clientUsername.textContent === "another-client" && orderDetailsNotice.dataset.state === "error"', "the new session");
    release();
    assert.equal(await evaluate("orderDetailsContent.hidden && !detailReference.textContent"), true);
    await open(listPage);
    await listReady();
    const navigation = await command("Page.getNavigationHistory");
    await evaluate("clientLogout.click()");
    await waitFor('location.pathname === "/shared/shared-login.html"', "shared logout");
    state.signedIn = true;
    state.revoked = true;
    const reads = calls(ordersApi).length;
    const checks = calls("/api/shared/session").length;
    await command("Page.navigateToHistoryEntry", { entryId: navigation.entries[navigation.currentIndex].id });
    await until(() => calls("/api/shared/session").length > checks, "ownership recheck on browser history navigation");
    await waitFor('location.pathname === "/shared/shared-login.html"', "revoked session redirect");
    assert.equal(calls(ordersApi).length, reads);
  } finally {
    release();
  }
});

test("the history and details fit desktop, 390px and 320px with keyboard-accessible controls", { skip: !browser }, async () => {
  try {
    for (const width of [1440, 390, 320]) {
      prepare();
      await command("Emulation.setDeviceMetricsOverride", { width, height: 900, deviceScaleFactor: 1, mobile: width < 500 });
      await open(listPage);
      await listReady();
      assert.equal(await evaluate("document.documentElement.scrollWidth <= innerWidth"), true);
      await evaluate('orderHistory.querySelector("a").focus(); orderHistory.querySelector("a").click()');
      await detailReady();
      assert.equal(await evaluate("document.documentElement.scrollWidth <= innerWidth"), true);
      await evaluate("openCancelOrder.focus(); openCancelOrder.click()");
      assert.equal(await evaluate('cancelOrderDialog.matches(":modal") && document.activeElement === keepOrder'), true);
      await command("Input.dispatchKeyEvent", { type: "keyDown", key: "Escape", code: "Escape", windowsVirtualKeyCode: 27 });
      await command("Input.dispatchKeyEvent", { type: "keyUp", key: "Escape", code: "Escape", windowsVirtualKeyCode: 27 });
      assert.equal(await evaluate("!cancelOrderDialog.open && document.activeElement === openCancelOrder"), true);
    }
  } finally {
    await command("Emulation.clearDeviceMetricsOverride");
  }
});

const liveUrl = process.env.LMS_M10C_TEST_URL;

async function liveSignIn(stack, username, password) {
  assert(username?.startsWith("m10c-fixture-") && password, "Use a disposable M10C Client fixture.");
  await open("/shared/shared-login.html", stack);
  await waitFor("document.readyState === 'complete' && typeof sharedLoginUser === 'function' && "
    + "!document.getElementById('sharedLoginUserSubmitButton')?.disabled", "the ready shared login form");
  await evaluate("sharedLoginUserUsernameInput.value=" + JSON.stringify(username)
    + "; sharedLoginUserPasswordInput.value=" + JSON.stringify(password) + "; sharedLoginUserForm.requestSubmit()");
  await waitFor('location.pathname === "/shared/shared-welcome.html"', "the shared welcome page");
}

test("live orders isolate two Clients and cancel a paid pre-picking order with a requested refund", { skip: !browser || !liveUrl }, async () => {
  const stack = new URL(liveUrl);
  assert.equal(stack.hostname, "127.0.0.1");
  const owned = "ORD-M10C-PAID";
  const other = "ORD-M10C-OTHER";
  await liveSignIn(stack.origin, process.env.LMS_M10C_TEST_A_USERNAME, process.env.LMS_M10C_TEST_A_PASSWORD);
  await open(listPage, stack.origin);
  await listReady();
  assert.equal(await evaluate("orderHistory.children.length"), 20);
  await evaluate("loadMoreOrders.click()");
  await waitFor("ordersMoreAction.hidden && orderHistory.children.length === 21", "all owned orders across two server pages");
  assert.doesNotMatch(await evaluate("orderHistory.textContent"), new RegExp(other));
  await open(pathFor(other), stack.origin);
  await waitFor('document.getElementById("orderDetailsNotice")?.textContent === "This order is unavailable."', "the unowned order");
  const unownedPaths = [orderUrl(other), orderUrl(other) + "/tracking"];
  assert.deepEqual(await evaluate("Promise.all(" + JSON.stringify(unownedPaths) + ".map((url) => fetch(url).then((response) => response.status)))"), [404, 404]);
  assert.equal(await evaluate('sharedSendRequest("POST",' + JSON.stringify(orderUrl(other) + "/cancel")
    + ').then(() => 200, (error) => error.status)'), 404);
  await open(pathFor(owned), stack.origin);
  await detailReady();
  assert.match(await evaluate("orderDetailFacts.textContent"), /Ready for picking.*36.50/s);
  assert.match(await evaluate("orderDetailItems.textContent"), /Renamed Carton.*Current catalogue.*Historical unit price.*18.25/s);
  assert.match(await evaluate("orderDetailDelivery.textContent"), /Current A address/);
  assert.match(await evaluate("trackingContent.textContent"), /Earlier tracking events may be unavailable/);
  assert.equal(await evaluate('trackingContent.querySelector(".client-order-tracking-status time") === null'), true);
  await evaluate("openCancelOrder.click(); confirmCancelOrder.click()");
  await waitFor('orderDetailFacts.textContent.includes("Cancelled") && !orderDetailsContent.hidden', "the persisted cancellation");
  assert.match(await evaluate("orderDetailPayments.textContent"), /Payment accepted.*Refund requested.*36.50/s);
  assert.doesNotMatch(await evaluate("orderDetailPayments.textContent"), /Refund completed/);
  assert.equal(await evaluate("openCancelOrder.hidden"), true);
  await evaluate('document.querySelector(".client-order-back").click()');
  await listReady();
  assert.match(await evaluate("orderHistory.firstElementChild.textContent"), /ORD-M10C-PAID.*Cancelled/s);
  await evaluate("clientLogout.click()");
  await waitFor('location.pathname === "/shared/shared-login.html"', "logout from Client A");
  await liveSignIn(stack.origin, process.env.LMS_M10C_TEST_B_USERNAME, process.env.LMS_M10C_TEST_B_PASSWORD);
  await open(pathFor(other), stack.origin);
  await detailReady();
  assert.match(await evaluate("orderDetailDelivery.textContent"), /Private B address/);
  assert.match(await evaluate("trackingContent.textContent"), /The recipient was unavailable/);
  assert.doesNotMatch(await evaluate("trackingContent.textContent"), /Internal test note|Cycle/);
  assert.equal(await evaluate("!postDeliveryActions.hidden && openCancelOrder.hidden"), true);
  await open(pathFor(owned), stack.origin);
  await waitFor('document.getElementById("orderDetailsNotice")?.textContent === "This order is unavailable."', "Client B is denied Client A's order");
});
