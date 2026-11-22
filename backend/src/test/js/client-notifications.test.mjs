import assert from "node:assert/strict";
import { after, before, test } from "node:test";
import { browser, startBrowser, until } from "./client-browser-harness.mjs";

const pagePath = "/client/notifications.html";
const api = "/api/client/notifications";
const countApi = api + "/unread-count";
const allApi = api + "/read-all";
const wideId = "9007199254740993";
const createdAt = "2026-11-22T02:00:00Z";
const account = { userId: "10", username: "notification-fixture", createdAt,
  deliveryDefaults: { recipientName: null, contactPhone: null, deliveryAddress: null } };
const readPath = (id) => api + "/" + id + "/read";
const item = (notificationId, options = {}) => ({
  notificationId, type: "GENERAL", message: "Fixture notification " + notificationId,
  orderReference: null, createdAt, isRead: false, ...options
});
const samples = [
  item(wideId, { type: "PAYMENT_CONFIRMED", message: "Payment received for your order.", orderReference: "ORD-FIXTURE-01" }),
  item("9007199254740992", { type: "SHIPPED", isRead: true }),
  item("47", { type: "DELIVERY_FAILED" }), item("46", { type: "DELIVERED", isRead: true }),
  item("45", { type: "FUTURE_TYPE", message: '<img src=x onerror="window.injected=true">', orderReference: "../unsafe" })
];
let state, requests, harness;
const command = (...args) => harness.command(...args);
const evaluate = (...args) => harness.evaluate(...args);
const waitFor = (expression, description) => until(() => evaluate(expression), description);
const calls = (path, method = "GET") => requests.filter((entry) => entry.path === path && entry.method === method);
const listedIds = () => evaluate("Array.from(notificationList.children, (row) => row.dataset.notificationId)");
const countText = () => evaluate("notificationUnreadCount.textContent");
const waitForInbox = () => waitFor('document.getElementById("clientPortal")?.hidden === false && !notificationList.hidden'
  + ' && !notificationUnreadCount.hidden && !refreshNotifications.disabled',
  "the verified inbox");
const markButton = (id) => 'notificationList.querySelector("[data-mark-notification=\'' + id + '\']")';

function prepare(overrides = {}) {
  requests = [];
  state = { account: { ...account }, signedIn: true, role: "CLIENT", forced: false, token: "fixture-csrf",
    items: samples.map((notification) => ({ ...notification })), pageSize: 20, ...overrides };
}

async function fixture(request, response) {
  const url = new URL(request.url, "http://localhost");
  if (!url.pathname.startsWith("/api/")) return;
  const entry = { path: url.pathname, method: request.method, params: Object.fromEntries(url.searchParams),
    token: request.headers["x-csrf-token"] };
  requests.push(entry);
  const json = (status, body) => response.writeHead(status, {
    "Content-Type": "application/json", "Cache-Control": "no-store"
  }).end(JSON.stringify(body));
  if (entry.path === "/api/shared/session") {
    if (state.revoked) return json(401, { code: "SHARED_SESSION_ENDED" });
    return json(200, {
      sharedViewSessionIsLoggedIn: state.signedIn, role: state.role, username: state.account.username,
      userID: Number(state.account.userId), mustChangePassword: state.forced, sharedViewSessionIdleTimeoutSeconds: 900,
      sharedViewSessionSecurityToken: state.token, sharedViewSessionSecurityTokenHeaderName: "X-CSRF-TOKEN"
    });
  }
  if (entry.path === "/api/shared/logout" && entry.method === "POST") {
    state.signedIn = false;
    return json(200, { message: "Logged out." });
  }
  if (entry.path === "/api/client/account") return json(200, state.account);
  if (entry.path === countApi && entry.method === "GET") {
    if (state.holdCount) await state.holdCount;
    if (state.countError) return json(503, { code: "COUNT_ERROR" });
    return json(200, { unreadCount: state.items.filter((notification) => !notification.isRead).length });
  }
  if (entry.path === api && entry.method === "GET") {
    const cursor = url.searchParams.get("cursor");
    if (cursor !== null && state.overrideNextPage) return json(200, state.overrideNextPage);
    const offset = cursor === null ? 0 : state.items.findIndex((notification) => notification.notificationId === cursor) + 1;
    if (cursor !== null && (offset === 0 || state.staleCursor)) return json(404, { code: "CLIENT_NOTIFICATION_NOT_FOUND" });
    const notifications = state.items.slice(offset, offset + state.pageSize).map((notification) => ({ ...notification }));
    if (cursor !== null && state.duplicateBoundary) notifications.unshift({ ...state.items[offset - 1] });
    const nextCursor = cursor !== null && state.loopCursor ? state.loopCursor
      : offset + state.pageSize < state.items.length ? notifications.at(-1).notificationId : null;
    if (state.holdList) await state.holdList;
    if (state.listError) return json(503, { code: "LIST_ERROR" });
    return json(200, { notifications, nextCursor });
  }
  if (entry.method === "POST" && entry.path.startsWith(api + "/")) {
    if (entry.token !== state.token) return json(403, { code: "SHARED_SECURITY_TOKEN_INVALID" });
    const target = state;
    if (entry.path === allApi) {
      entry.marked = target.items.filter((notification) => !notification.isRead).length;
      for (const notification of target.items) notification.isRead = true;
    } else {
      const selected = target.items.find((notification) => readPath(notification.notificationId) === entry.path);
      if (!selected || target.missing) return json(404, { code: "CLIENT_NOTIFICATION_NOT_FOUND", message: "Internal fixture reason" });
      entry.marked = selected.isRead ? 0 : 1;
      selected.isRead = true;
      entry.id = selected.notificationId;
    }
    if (target.holdMutation) await target.holdMutation;
    if (target.dropMutation) {
      response.writeHead(200, { "Content-Type": "application/json", "Content-Length": "128" });
      return response.write("{", () => response.destroy());
    }
    return json(200, entry.id ? { notificationId: entry.id, isRead: true } : { markedRead: entry.marked });
  }
  return json(404, { code: "ROUTE_UNEXPECTED" });
}

async function open(path = pagePath, origin = harness.origin) {
  await command("Page.navigate", { url: origin + path });
}

before(async () => {
  if (!browser) return;
  prepare();
  harness = await startBrowser(fixture);
});

after(async () => {
  await harness?.close();
});

test("the inbox is gated by the Client session and forced password changes", { skip: !browser }, async () => {
  for (const [overrides, destination] of [
    [{ signedIn: false }, "/shared/shared-login.html"],
    [{ role: "ADMIN" }, "/shared/shared-welcome.html"],
    [{ forced: true }, "/shared/shared-change-password.html"],
    [{ revoked: true }, "/shared/shared-login.html"]
  ]) {
    prepare(overrides);
    await open();
    await waitFor("location.pathname === " + JSON.stringify(destination), destination);
    assert.equal(calls(api).length + calls(countApi).length, 0);
  }
});

test("the inbox renders server text, read states, safe order links and HKT dates without writes", { skip: !browser }, async () => {
  prepare();
  await command("Emulation.setTimezoneOverride", { timezoneId: "America/Los_Angeles" });
  try {
    await open();
    await waitForInbox();
    assert.deepEqual(calls(api)[0].params, { limit: "20" });
    assert.equal(await evaluate('document.querySelector("[aria-current=page]").textContent.trim()'), "Notifications");
    assert.equal(await countText(), "3 unread");
    assert.deepEqual(await evaluate("Array.from(notificationList.children, (row) => row.dataset.read)"),
      ["false", "true", "false", "true", "false"]);
    assert.match(await evaluate("notificationList.firstElementChild.textContent"),
      /Payment confirmed.*Payment received for your order.*22 Nov 2026, 10:00 am HKT/i);
    assert.match(await evaluate("notificationList.lastElementChild.textContent"), /Notification.*<img src=x/);
    assert.equal(await evaluate("notificationList.querySelectorAll('img,script').length"), 0);
    assert.equal(await evaluate("window.injected === undefined && localStorage.length + sessionStorage.length === 0"), true);
    assert.deepEqual(await evaluate('Array.from(notificationList.querySelectorAll("a"), (link) => link.getAttribute("href"))'),
      ["/client/order-details.html?reference=ORD-FIXTURE-01"]);
    assert.equal(requests.some(({ method }) => method === "POST"), false);
  } finally {
    await command("Emulation.setTimezoneOverride", { timezoneId: "" });
  }
});

test("loading, empty and failed count states remain usable", { skip: !browser }, async () => {
  let release;
  prepare({ holdList: new Promise((resolve) => { release = resolve; }), items: [], listError: true });
  try {
    await open();
    await waitFor('document.getElementById("notificationList")?.getAttribute("aria-busy") === "true"', "loading state");
    assert.equal(await evaluate("markAllNotificationsRead.disabled && refreshNotifications.disabled"), true);
    release();
    await waitFor('notificationNotice.dataset.state === "error" && !refreshNotifications.disabled', "list error state");
    state.listError = false;
    state.countError = true;
    await evaluate("refreshNotifications.click()");
    await waitForInbox();
    assert.match(await evaluate("notificationNotice.textContent"), /no notifications yet/);
    assert.equal(await countText(), "Unread count unavailable");
    assert.equal(await evaluate("moreNotificationAction.hidden && markAllNotificationsRead.disabled"), true);
  } finally {
    release();
  }
});

test("pagination uses exact wide cursors and serializes Refresh with Load More", { skip: !browser }, async () => {
  let release;
  prepare({ pageSize: 2, duplicateBoundary: true });
  await open();
  await waitForInbox();
  state.holdList = new Promise((resolve) => { release = resolve; });
  try {
    await evaluate("loadMoreNotifications.click(); loadMoreNotifications.click(); refreshNotifications.click()");
    await until(() => calls(api).length === 2, "the pending next page");
    assert.deepEqual(calls(api)[1].params, { limit: "20", cursor: "9007199254740992" });
    assert.deepEqual(await listedIds(), [wideId, "9007199254740992"]);
    assert.equal(await evaluate("refreshNotifications.disabled && loadMoreNotifications.disabled"), true);
    state.holdList = null;
    release();
    await waitForInbox();
    assert.equal(calls(api).length, 2);
    assert.deepEqual(await listedIds(), [wideId, "9007199254740992", "47", "46"]);
    assert.equal(await evaluate('document.activeElement.dataset.notificationId'), "47");
    await evaluate("loadMoreNotifications.click()");
    await waitForInbox();
    assert.deepEqual(await listedIds(), samples.map((notification) => notification.notificationId));
    assert.equal(await evaluate("moreNotificationAction.hidden"), true);
  } finally {
    release();
  }
});

test("cursor loops and unavailable cursor pages stop safely and Refresh recovers", { skip: !browser }, async () => {
  prepare({ pageSize: 2 });
  await open();
  await waitForInbox();
  await evaluate("loadMoreNotifications.click()");
  await waitForInbox();
  assert.deepEqual(await listedIds(), [wideId, "9007199254740992", "47", "46"]);
  state.loopCursor = "9007199254740992";
  await evaluate("loadMoreNotifications.click()");
  await waitFor('notificationNotice.dataset.state === "error" && moreNotificationAction.hidden', "the looping cursor rejection");
  assert.deepEqual(await listedIds(), [wideId, "9007199254740992", "47", "46"]);
  await evaluate("refreshNotifications.click()");
  await waitFor('notificationList.children.length === 2 && !refreshNotifications.disabled', "recovery with the first page");
  assert.equal(calls(api).at(-1).params.cursor, undefined);
  state.loopCursor = null;
  state.staleCursor = true;
  await evaluate("loadMoreNotifications.click()");
  await waitFor('notificationNotice.dataset.state === "error" && moreNotificationAction.hidden', "the stale cursor rejection");
  assert.deepEqual(await listedIds(), [wideId, "9007199254740992"]);
  state.staleCursor = false;
  await evaluate("refreshNotifications.click()");
  await waitForInbox();
  assert.equal(await evaluate("moreNotificationAction.hidden"), false);
});

test("a repeated-only page with a new cursor stops and malformed IDs are rejected", { skip: !browser }, async () => {
  prepare({ pageSize: 2, overrideNextPage: { notifications: [samples[0]], nextCursor: "47" } });
  await open();
  await waitForInbox();
  await evaluate("loadMoreNotifications.click()");
  await waitFor('notificationNotice.dataset.state === "error" && moreNotificationAction.hidden', "the page with no new rows");
  assert.deepEqual(await listedIds(), [wideId, "9007199254740992"]);
  state.items = [item(Number(wideId))];
  await evaluate("refreshNotifications.click()");
  await waitFor('notificationNotice.textContent.includes("incomplete notifications data") && !refreshNotifications.disabled',
    "the malformed numeric ID rejection");
  assert.deepEqual(await listedIds(), [wideId, "9007199254740992"]);
});

test("mark one preserves wide IDs and loaded depth, sends CSRF, and reads the real count", { skip: !browser }, async () => {
  let release;
  prepare({ pageSize: 2, holdMutation: new Promise((resolve) => { release = resolve; }) });
  try {
    await open();
    await waitForInbox();
    await evaluate("loadMoreNotifications.click()");
    await waitForInbox();
    await evaluate(markButton(wideId) + ".click(); " + markButton(wideId) + ".click(); markAllNotificationsRead.click()");
    const mutation = await until(() => calls(readPath(wideId), "POST")[0], "the authorized wide ID mutation");
    assert.equal(mutation.token, state.token);
    assert.equal(await evaluate("notificationList.firstElementChild.dataset.read"), "false");
    assert.equal(await countText(), "3 unread");
    assert.equal(await evaluate("markAllNotificationsRead.disabled"), true);
    state.items.unshift(item("9007199254740994"));
    state.holdMutation = null;
    release();
    await waitForInbox();
    assert.equal(await countText(), "3 unread");
    assert.deepEqual(await listedIds(), ["9007199254740994", wideId, "9007199254740992", "47"]);
    assert.equal(await evaluate(markButton(wideId) + " === null"), true);
    assert.equal(calls(readPath(wideId), "POST").length, 1);
    assert.equal(calls(allApi, "POST").length, 0);
    const repeated = await evaluate('sharedSendRequest("POST", ' + JSON.stringify(readPath(wideId)) + ')');
    assert.deepEqual(repeated, { notificationId: wideId, isRead: true });
    assert.equal(calls(readPath(wideId), "POST").at(-1).marked, 0);
  } finally {
    release();
  }
});

test("mark all is explicit and repeated requests are safe", { skip: !browser }, async () => {
  prepare();
  await open();
  await waitForInbox();
  assert.equal(calls(allApi, "POST").length, 0);
  await evaluate("markAllNotificationsRead.click(); markAllNotificationsRead.click()");
  await waitFor('notificationUnreadCount.textContent === "0 unread" && !refreshNotifications.disabled', "the updated count");
  assert.equal(await evaluate('notificationList.querySelectorAll("[data-read=false]").length'), 0);
  assert.equal(calls(allApi, "POST").length, 1);
  assert.equal(calls(allApi, "POST")[0].token, state.token);
  assert.equal(await evaluate("markAllNotificationsRead.disabled"), true);
  assert.deepEqual(await evaluate('sharedSendRequest("POST", ' + JSON.stringify(allApi) + ')'), { markedRead: 0 });
});

test("a notification arriving between list and count reads is reconciled on the next refresh", { skip: !browser }, async () => {
  prepare();
  await open();
  await waitForInbox();
  const before = await listedIds();
  assert.equal(await countText(), "3 unread");
  let release;
  const pending = new Promise((resolve) => { release = resolve; });
  Object.assign(state, { holdList: pending, holdCount: pending });
  try {
    await evaluate("refreshNotifications.click()");
    await until(() => calls(api).length === 2 && calls(countApi).length === 2, "both reads for refresh");
    const newId = "9007199254740994";
    state.items.unshift(item(newId, { message: "Arrived during refresh" }));
    Object.assign(state, { holdList: null, holdCount: null });
    release();
    await waitForInbox();
    assert.deepEqual(await listedIds(), before);
    assert.equal(await countText(), "4 unread");
    await evaluate("refreshNotifications.click()");
    await waitForInbox();
    assert.deepEqual(await listedIds(), [newId, ...before]);
    assert.equal(await countText(), "4 unread");
    assert.equal(await evaluate('notificationList.firstElementChild.dataset.read'), "false");
    assert.equal(requests.some(({ method }) => method === "POST"), false);
  } finally {
    release();
  }
});

test("a notification arriving after Mark All stays unread and refresh cannot race the mutation", { skip: !browser }, async () => {
  let release;
  prepare({ pageSize: 2, holdMutation: new Promise((resolve) => { release = resolve; }) });
  try {
    await open();
    await waitForInbox();
    await evaluate("loadMoreNotifications.click()");
    await waitForInbox();
    const listReads = calls(api).length;
    const countReads = calls(countApi).length;
    await evaluate("markAllNotificationsRead.click()");
    const mutation = await until(() => calls(allApi, "POST")[0], "the committed mark-all with a delayed response");
    assert.equal(mutation.marked, 3);
    await evaluate("refreshNotifications.click(); loadMoreNotifications.click(); markAllNotificationsRead.click()");
    assert.equal(calls(api).length, listReads);
    assert.equal(calls(countApi).length, countReads);
    assert.equal(calls(allApi, "POST").length, 1);
    assert.equal(await countText(), "3 unread");
    state.items.unshift(item("9007199254740994", { message: "Just arrived after mark-all" }));
    release();
    await waitForInbox();
    assert.equal(await countText(), "1 unread");
    assert.equal(await evaluate('notificationList.firstElementChild.dataset.read'), "false");
    assert.equal(await evaluate('notificationList.querySelectorAll("[data-read=false]").length'), 1);
    assert.equal((await listedIds()).length, 4);
  } finally {
    release();
  }
});

test("stale CSRF is recovered and a missing or unowned notification uses a neutral 404", { skip: !browser }, async () => {
  prepare();
  await open();
  await waitForInbox();
  state.token = "rotated-fixture-csrf";
  await evaluate(markButton(wideId) + ".click()");
  await waitFor('notificationActionNotice.textContent.includes("security token was rejected") && !refreshNotifications.disabled',
    "the refreshed CSRF token");
  assert.equal(state.items[0].isRead, false);
  state.missing = true;
  await evaluate(markButton(wideId) + ".click()");
  await waitFor('notificationActionNotice.textContent === "This notification is unavailable." && !refreshNotifications.disabled',
    "the neutral unavailable message");
  assert.equal(calls(readPath(wideId), "POST").at(-1).token, state.token);
  assert.equal(await countText(), "3 unread");
  assert.equal(await evaluate('notificationActionNotice.textContent.includes("Internal fixture reason")'), false);
});

test("an ambiguous mutation stays locked until notifications and count are both verified", { skip: !browser }, async () => {
  prepare({ dropMutation: true });
  await open();
  await waitForInbox();
  state.countError = true;
  await evaluate(markButton(wideId) + ".click()");
  await waitFor('notificationActionNotice.textContent.includes("Refresh the inbox") && !refreshNotifications.disabled',
    "the blocked mutation after a failed count refresh");
  assert.equal(await countText(), "Unread count unavailable");
  assert.equal(await evaluate("markAllNotificationsRead.disabled && " + markButton("47") + ".disabled"), true);
  assert.equal(calls(readPath(wideId), "POST").length, 1);
  state.countError = false;
  state.dropMutation = false;
  await evaluate("refreshNotifications.click()");
  await waitForInbox();
  assert.equal(await countText(), "2 unread");
  assert.equal(await evaluate(markButton(wideId) + " === null && !" + markButton("47") + ".disabled"), true);
  assert.equal(calls(readPath(wideId), "POST").length, 1);
});

test("a confirmed POST with a failed list refresh does not show success or unlock writes", { skip: !browser }, async () => {
  prepare();
  await open();
  await waitForInbox();
  state.listError = true;
  await evaluate(markButton(wideId) + ".click()");
  await waitFor('notificationNotice.dataset.state === "error" && !refreshNotifications.disabled', "the failed list refresh");
  assert.equal(state.items[0].isRead, true);
  assert.equal(await evaluate('notificationActionNotice.dataset.state === "error" && markAllNotificationsRead.disabled'), true);
  assert.equal(await evaluate(markButton("47") + ".disabled"), true);
  await evaluate(markButton("47") + ".click()");
  assert.equal(calls(readPath("47"), "POST").length, 0);
  state.listError = false;
  await evaluate("refreshNotifications.click()");
  await waitForInbox();
  assert.equal(await countText(), "2 unread");
  assert.equal(await evaluate(markButton(wideId) + " === null && !" + markButton("47") + ".disabled"), true);
});

test("an unanswered POST times out and refreshes authoritative read state", { skip: !browser }, async () => {
  let release;
  const { identifier } = await command("Page.addScriptToEvaluateOnNewDocument", {
    source: 'const realDelay = window.setTimeout.bind(window); window.setTimeout = (fn, ms, ...args) => realDelay(fn, ms === 30000 ? 700 : ms, ...args)'
  });
  try {
    prepare({ holdMutation: new Promise((resolve) => { release = resolve; }) });
    await open();
    await waitForInbox();
    await evaluate("markAllNotificationsRead.click()");
    await waitFor('notificationActionNotice.textContent.includes("could not be confirmed") && !refreshNotifications.disabled',
      "recovery from an unanswered request");
    assert.equal(await countText(), "0 unread");
    assert.equal(await evaluate("markAllNotificationsRead.disabled"), true);
    assert.equal(calls(allApi, "POST").length, 1);
  } finally {
    release?.();
    await command("Page.removeScriptToEvaluateOnNewDocument", { identifier });
  }
});

test("delayed reads and mutation receipts cannot update a different Client's inbox", { skip: !browser }, async () => {
  let release;
  prepare({ holdList: new Promise((resolve) => { release = resolve; }) });
  try {
    await open();
    await until(() => calls(api).length === 1, "the previous Client's pending list");
    state.account = { ...account, userId: "20", username: "second-client" };
    state.items = [item("56", { message: "Second Client inbox" })];
    state.holdList = null;
    await evaluate('const sender = new BroadcastChannel("lms-client-session"); sender.postMessage("signed-out"); sender.close()');
    await waitFor('notificationList.textContent.includes("Second Client inbox")', "the second Client's verified inbox");
    release();
    await waitForInbox();
    assert.equal(await countText(), "1 unread");
    assert.deepEqual(await listedIds(), ["56"]);
    state.holdMutation = new Promise((resolve) => { release = resolve; });
    await evaluate(markButton("56") + ".click()");
    await until(() => calls(readPath("56"), "POST").length === 1, "the second Client's pending POST");
    state.account = { ...account, userId: "30", username: "third-client" };
    state.items = [item("60", { message: "Third Client inbox" })];
    await evaluate('const nextSender = new BroadcastChannel("lms-client-session"); nextSender.postMessage("signed-out"); nextSender.close()');
    await waitFor('notificationList.textContent.includes("Third Client inbox")', "the third Client inbox");
    release();
    await evaluate("new Promise((resolve) => setTimeout(resolve, 100))");
    assert.deepEqual(await listedIds(), ["60"]);
    assert.equal(await evaluate("notificationActionNotice.hidden"), true);
  } finally {
    release();
  }
});

test("a silently replaced session cannot render another Client's data while resolving Mark One", { skip: !browser }, async () => {
  let release;
  prepare({ holdMutation: new Promise((resolve) => { release = resolve; }) });
  try {
    await open();
    await waitForInbox();
    await evaluate(markButton(wideId) + ".click()");
    await until(() => calls(readPath(wideId), "POST").length === 1, "the first Client's approved mutation");
    state.account = { ...account, userId: "20", username: "second-client" };
    state.items = [item("41", { message: "Second Client private message" })];
    release();
    await waitFor('location.pathname === "/shared/shared-login.html"', "the ownership mismatch redirect");
    assert.equal(calls(readPath(wideId), "POST").length, 1);
    await open();
    await waitForInbox();
    assert.deepEqual(await listedIds(), ["41"]);
    assert.equal(await countText(), "1 unread");
    assert.equal(await evaluate("notificationActionNotice.hidden"), true);
  } finally {
    release();
  }
});

test("logout and browser Back clear private inbox data and recheck revocation", { skip: !browser }, async () => {
  prepare();
  await open();
  await waitForInbox();
  const nav = await command("Page.getNavigationHistory");
  assert.equal(await evaluate("clientLogout.click(); notificationList.children.length === 0 && notificationUnreadCount.hidden"), true);
  await waitFor('location.pathname === "/shared/shared-login.html"', "the logout redirect");
  state.revoked = true;
  const checks = calls("/api/shared/session").length;
  const reads = calls(api).length;
  await command("Page.navigateToHistoryEntry", { entryId: nav.entries[nav.currentIndex].id });
  await until(() => calls("/api/shared/session").length > checks, "a new session check on Back");
  await waitFor('location.pathname === "/shared/shared-login.html"', "the revoked session redirect");
  assert.equal(calls(api).length, reads);
});

test("the inbox fits desktop, 390px and 320px and its actions have keyboard focus", { skip: !browser }, async () => {
  try {
    for (const width of [1440, 390, 320]) {
      prepare();
      await command("Emulation.setDeviceMetricsOverride", { width, height: 900, deviceScaleFactor: 1, mobile: width < 500 });
      await open();
      await waitForInbox();
      assert.equal(await evaluate("document.documentElement.scrollWidth <= innerWidth"), true);
      assert.equal(await evaluate('getComputedStyle(document.querySelector(".client-notification-row")).display'), "grid");
      await evaluate("refreshNotifications.focus()");
      await command("Input.dispatchKeyEvent", { type: "keyDown", key: "Tab", code: "Tab", windowsVirtualKeyCode: 9 });
      await command("Input.dispatchKeyEvent", { type: "keyUp", key: "Tab", code: "Tab", windowsVirtualKeyCode: 9 });
      assert.equal(await evaluate("document.activeElement === markAllNotificationsRead"), true);
      assert.equal(await evaluate('notificationUnreadCount.getAttribute("role")'), "status");
    }
  } finally {
    await command("Emulation.clearDeviceMetricsOverride");
  }
});

const liveUrl = process.env.LMS_M10D_TEST_URL;

async function liveSignIn(origin, username, password) {
  assert(username?.startsWith("m10d-fixture-") && password, "Use a disposable M10D Client fixture.");
  await open("/shared/shared-login.html", origin);
  await waitFor("document.readyState === 'complete' && typeof sharedLoginUser === 'function' && "
    + "!document.getElementById('sharedLoginUserSubmitButton')?.disabled", "the live sign-in page");
  await evaluate("sharedLoginUserUsernameInput.value=" + JSON.stringify(username)
    + "; sharedLoginUserPasswordInput.value=" + JSON.stringify(password) + "; sharedLoginUserForm.requestSubmit()");
  await waitFor('location.pathname === "/shared/shared-welcome.html"', "the shared welcome page");
}

test("live Spring Boot isolates notification records and persists explicit read actions", { skip: !browser || !liveUrl }, async () => {
  const stack = new URL(liveUrl);
  assert.equal(stack.hostname, "127.0.0.1");
  const otherId = "9007199254740994";
  const userA = process.env.LMS_M10D_TEST_A_USERNAME;
  const userB = process.env.LMS_M10D_TEST_B_USERNAME;
  await liveSignIn(stack.origin, userA, process.env.LMS_M10D_TEST_A_PASSWORD);
  await open(pagePath, stack.origin);
  await waitForInbox();
  assert.equal(await countText(), "21 unread");
  assert.equal((await listedIds()).length, 20);
  const first = await evaluate('sharedSendRequest("GET", "/api/client/notifications?limit=1")');
  assert.equal(first.notifications[0].notificationId, wideId);
  assert.equal(first.nextCursor, wideId);
  const next = await evaluate('sharedSendRequest("GET", "/api/client/notifications?limit=1&cursor=' + wideId + '")');
  assert.equal(next.notifications[0].notificationId, "9007199254740992");
  await evaluate("loadMoreNotifications.click()");
  await waitForInbox();
  assert.equal((await listedIds()).length, 22);
  assert.equal((await listedIds()).includes(otherId), false);
  assert.equal(await evaluate('notificationList.firstElementChild.querySelector("a").getAttribute("href")'),
    "/client/order-details.html?reference=ORD-M10D-OWNED");

  const missing = "9223372036854775807";
  const unauthorized = await evaluate(`Promise.all(${JSON.stringify([readPath(otherId), readPath(missing)])}
    .map((path) => sharedSendRequest("POST", path).then(() => ({}), ({ status, code }) => ({ status, code }))))`);
  assert.deepEqual(unauthorized, Array.from({ length: 2 }, () => ({ status: 404, code: "CLIENT_NOTIFICATION_NOT_FOUND" })));
  assert.equal(await evaluate('fetch("/api/client/notifications?limit=20&cursor=' + otherId + '").then((response) => response.status)'), 404);
  assert.equal(await evaluate('fetch(' + JSON.stringify(readPath(wideId)) + ', { method: "POST" }).then((response) => response.status)'), 403);

  await evaluate(markButton(wideId) + ".click()");
  await waitFor('notificationUnreadCount.textContent === "20 unread" && !refreshNotifications.disabled', "a persisted mark-one");
  assert.equal((await listedIds()).length, 22);
  assert.equal(await evaluate("notificationList.firstElementChild.dataset.read"), "true");
  await evaluate("markAllNotificationsRead.click()");
  await waitFor('notificationUnreadCount.textContent === "0 unread" && !refreshNotifications.disabled', "a persisted mark-all");
  assert.equal(await evaluate('notificationList.querySelectorAll("[data-read=false]").length'), 0);
  assert.deepEqual(await evaluate('sharedSendRequest("POST", "/api/client/notifications/read-all")'), { markedRead: 0 });

  await evaluate("clientLogout.click()");
  await waitFor('location.pathname === "/shared/shared-login.html"', "logout from Client A");
  await liveSignIn(stack.origin, userB, process.env.LMS_M10D_TEST_B_PASSWORD);
  await open(pagePath, stack.origin);
  await waitForInbox();
  assert.deepEqual(await listedIds(), [otherId, "9007199254740991"]);
  assert.equal(await countText(), "1 unread");
  assert.equal(await evaluate("notificationList.firstElementChild.dataset.read"), "false");
  assert.equal(await evaluate('fetch("/api/client/notifications?limit=20&cursor=' + wideId + '").then((response) => response.status)'), 404);
});
