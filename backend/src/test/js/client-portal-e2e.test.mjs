import assert from "node:assert/strict";
import { after, before, test } from "node:test";
import { browser, startBrowser, until } from "./client-browser-harness.mjs";

// Optional live acceptance tests. Use only the disposable m10e-fixture accounts on a loopback test stack.
const liveUrl = process.env.LMS_M10E_TEST_URL;
const enabled = Boolean(liveUrl && browser);
const loginPage = "/shared/shared-login.html";
const welcomePage = "/shared/shared-welcome.html";
const passwordPage = "/shared/shared-change-password.html";
const accountPage = "/client/account-settings.html";
const createPage = "/client/create-order.html";
const listPage = "/client/my-orders-tracking.html";
const detailsPage = "/client/order-details.html";
const notificationsPage = "/client/notifications.html";
const ordersApi = "/api/client/orders";
const notificationsApi = "/api/client/notifications";
const otherOrder = "ORD-M10E-OTHER";
const failedOrder = "ORD-M10E-FAILED";
const paidOrder = "ORD-M10E-PAID";
const wideId = "9007199254740993";
const updatedAddress = "Unit 8, 12 Test Street, Kowloon, Hong Kong";
const detailsPath = (reference) => detailsPage + "?" + new URLSearchParams({ reference });
const calls = [];
let origin, harness;
const command = (...args) => harness.command(...args);
const evaluate = (...args) => harness.evaluate(...args);
const waitFor = (expression, description) => until(() => evaluate(expression), description);

async function open(path) {
  const navigation = await command("Page.navigate", { url: origin + path });
  assert.equal(navigation.errorText, undefined);
}

function at(path) {
  return waitFor("location.pathname === " + JSON.stringify(path)
    + " && document.readyState === 'complete'", path);
}

async function portal(path, activePage, ready) {
  await at(path);
  await waitFor('document.getElementById("clientPortal")?.hidden === false && (' + ready + ')', "the " + activePage + " view");
  assert.equal(await evaluate('document.querySelector("[aria-current=page]").dataset.clientPage'), activePage);
}

async function signIn(fixture, nextPage = welcomePage) {
  const username = process.env["LMS_M10E_TEST_" + fixture + "_USERNAME"];
  const password = process.env["LMS_M10E_TEST_" + fixture + "_PASSWORD"];
  assert(username?.startsWith("m10e-fixture-") && password, "Use a disposable test account.");
  await open(loginPage);
  await waitFor("document.readyState === 'complete' && typeof sharedLoginUser === 'function' && !sharedLoginUserSubmitButton.disabled",
    "the initialized Shared Login form");
  await evaluate("sharedLoginUserUsernameInput.value=" + JSON.stringify(username)
    + "; sharedLoginUserPasswordInput.value=" + JSON.stringify(password) + "; sharedLoginUserForm.requestSubmit()");
  await at(nextPage);
  if (nextPage === welcomePage) await waitFor('document.querySelector("main")?.hidden === false', "the verified Shared Welcome");
  return username;
}

async function navigateSidebar(page) {
  await evaluate('document.querySelector("[data-client-page=' + page + ']").click()');
}

function statusOf(path, method = "GET") {
  return evaluate("fetch(" + JSON.stringify(path) + ",{method:" + JSON.stringify(method) + "}).then((response) => response.status)");
}

async function signOut() {
  await evaluate("clientLogout.click()");
  await at(loginPage);
  await waitFor("!sharedLoginUserSubmitButton.disabled", "Shared Login after logout");
}

async function chooseProduct(index, id, quantity) {
  await evaluate(`(() => {
    const row = orderLines.querySelectorAll("fieldset")[${index}];
    const select = row.querySelector("select");
    const quantityInput = row.querySelector("input");
    select.value = ${JSON.stringify(id)};
    select.dispatchEvent(new Event("change", { bubbles: true }));
    quantityInput.value = ${JSON.stringify(quantity)};
    quantityInput.dispatchEvent(new Event("input", { bubbles: true }));
  })()`);
}

async function cancelVisibleOrder(reference) {
  assert.equal(await evaluate("!openCancelOrder.hidden && !openCancelOrder.disabled"), true);
  assert.equal(await statusOf(ordersApi + "/" + reference + "/cancel", "POST"), 403);
  const mutations = calls.filter(({ path, method }) => path.endsWith("/" + reference + "/cancel") && method === "POST").length;
  await evaluate("openCancelOrder.click()");
  assert.equal(await evaluate("cancelOrderDialog.open && document.activeElement === keepOrder"), true);
  await evaluate("keepOrder.click(); openCancelOrder.click(); confirmCancelOrder.click(); confirmCancelOrder.click()");
  await waitFor('!orderDetailsContent.hidden && cancellationNotice.dataset.state === "success"', "the confirmed cancellation");
  assert.equal(await evaluate("openCancelOrder.hidden"), true);
  assert.match(await evaluate("orderDetailFacts.textContent"), /Cancelled/);
  assert.equal(calls.filter(({ path, method }) => path.endsWith("/" + reference + "/cancel") && method === "POST").length, mutations + 1);
}

before(async () => {
  if (!enabled) return;
  const stack = new URL(liveUrl);
  assert.equal(stack.hostname, "127.0.0.1");
  origin = stack.origin;
  harness = await startBrowser(() => {}, ({ method, params }) => {
    if (method !== "Network.requestWillBeSent") return;
    const url = new URL(params.request.url);
    if (url.origin === origin && url.pathname.startsWith("/api/")) {
      calls.push({ path: url.pathname, method: params.request.method });
    }
  });
  await command("Network.enable");
});

after(async () => {
  await harness?.close();
});

test("the live Client journey connects defaults, stock-neutral order creation, tracking, notifications and cancellation", { skip: !enabled }, async () => {
  const usernameA = await signIn("A");
  const hasEntry = await evaluate(`Boolean(document.querySelector('main a[href^="/client/"]'))`);
  console.log("Shared Welcome exposes a Client Portal link: " + hasEntry);
  await open(accountPage);
  await portal(accountPage, "account-settings", "accountUsername.textContent === " + JSON.stringify(usernameA));
  assert.equal(await evaluate("recipientName.value"), "Original Recipient");
  assert.equal(await evaluate(`document.querySelector('a[href="/shared/shared-change-password.html"]') !== null`), true);
  await evaluate('recipientName.value = "Updated Recipient"; contactPhone.value = "61234567"; deliveryAddress.value = '
    + JSON.stringify(updatedAddress) + "; deliveryForm.requestSubmit()");
  await waitFor('deliveryFeedback.dataset.state === "success"', "saved delivery defaults");
  await evaluate('deliveryAddress.value = "Unsaved form value"; reloadDefaults.click()');
  await waitFor("deliveryAddress.value === " + JSON.stringify(updatedAddress), "reloaded delivery defaults");

  await navigateSidebar("create-order");
  await portal(createPage, "create-order", "!submitOrder.disabled");
  assert.deepEqual(await evaluate("[orderRecipient.value,orderPhone.value,orderAddress.value]"),
    ["Updated Recipient", "61234567", updatedAddress]);
  const catalogue = () => evaluate('import("/client/client-api.js").then((module) => module.readClientProducts(""))');
  const before = await catalogue();
  assert.equal(before.find((product) => product.productId === wideId).availableStock, 12);
  await chooseProduct(0, wideId, "2");
  await evaluate("addOrderLine.click()");
  await chooseProduct(1, "123", "1");
  assert.equal(await evaluate("estimatedTotal.textContent"), "HK$39.60");

  // Drop the first successful response at the browser boundary after the real server has committed.
  await evaluate(`(() => {
    const send = window.fetch.bind(window);
    window.acceptancePosts = [];
    window.fetch = async (resource, options) => {
      if (resource !== "/api/client/orders" || options?.method !== "POST") return send(resource, options);
      const first = acceptancePosts.length === 0;
      acceptancePosts.push({ key: options.headers["Idempotency-Key"], raw: options.body });
      const response = await send(resource, options);
      if (first && response.status === 201) throw new TypeError("Acceptance fixture dropped the response");
      return response;
    };
  })()`);
  await evaluate("orderForm.requestSubmit(); orderForm.requestSubmit()");
  await waitFor("!orderRetry.hidden", "the unknown outcome after the committed POST");
  assert.equal(await evaluate("acceptancePosts.length === 1 && orderDeliveryFields.disabled"), true);
  await evaluate('document.querySelector("[data-client-page=my-orders]").click()');
  assert.equal(await evaluate("leaveOrderDialog.open && document.activeElement === stayOnOrder"), true);
  await evaluate("stayOnOrder.click(); retryOrder.click(); retryOrder.click()");
  const reference = await waitFor("!createdOrder.hidden && createdOrderReference.textContent", "the replayed order receipt");
  const posts = await evaluate("acceptancePosts");
  assert.equal(posts.length, 2);
  assert.deepEqual(posts[0], posts[1]);
  assert.match(posts[0].key, /^[A-Za-z0-9._~-]{1,128}$/);
  assert.match(posts[0].raw, /"productId":9007199254740993,/);
  assert.deepEqual(await evaluate("[createdOrderStatus.textContent,createdOrderTotal.textContent]"), ["AWAITING_PAYMENT", "HK$39.60"]);
  assert.equal(await evaluate("createdOrder.textContent.includes('Online payment is currently unavailable')"), true);
  assert.deepEqual(await catalogue(), before);

  await evaluate("viewCreatedOrder.click()");
  await portal(detailsPage, "my-orders", "!orderDetailsContent.hidden");
  assert.equal(await evaluate("detailReference.textContent"), reference);
  assert.match(await evaluate("orderDetailFacts.textContent"), /Awaiting payment.*HKD\s+39\.60/s);
  const itemText = await evaluate("orderDetailItems.textContent");
  assert.match(itemText, /M10E Wide Carton.*Historical unit price.*18\.25/s);
  assert.match(itemText, /M10E Small Mailer.*Historical unit price.*3\.10/s);
  assert.match(await evaluate("orderDetailDelivery.textContent"), /Updated Recipient.*Unit 8/s);
  assert.match(await evaluate("orderDetailPayments.textContent"), /No payment attempts recorded/);
  assert.deepEqual(await evaluate('Array.from(trackingContent.querySelectorAll("ol li strong"), (title) => title.textContent)'), ["Order placed"]);
  assert.equal(await evaluate('trackingContent.querySelector(".client-order-tracking-status time") === null'), true);
  assert.match(await evaluate("trackingContent.textContent"), /Earlier tracking events may be unavailable/);

  await evaluate('document.querySelector(".client-order-back").click()');
  await portal(listPage, "my-orders", "orderHistory.children.length > 0");
  assert.equal(await evaluate("orderHistory.firstElementChild.querySelector('.client-order-reference').textContent"), reference);
  await evaluate('orderHistory.firstElementChild.querySelector("a").click()');
  await portal(detailsPage, "my-orders", "!orderDetailsContent.hidden");
  assert.equal(await evaluate("detailReference.textContent"), reference);
  await evaluate("history.back()");
  await portal(listPage, "my-orders", "orderHistory.children.length > 0");
  await evaluate("history.forward()");
  await portal(detailsPage, "my-orders", "!orderDetailsContent.hidden");
  assert.equal(await evaluate("detailReference.textContent"), reference);
  await navigateSidebar("notifications");
  await portal(notificationsPage, "notifications", "!notificationList.hidden && !refreshNotifications.disabled");
  assert.equal(await evaluate("notificationUnreadCount.textContent"), "2 unread");
  assert.equal(await evaluate("notificationList.children.length"), 2);
  assert.match(await evaluate("notificationList.textContent"), /Delivery failed/);
  await evaluate('notificationList.querySelector("[data-mark-notification]").click()');
  await waitFor('notificationUnreadCount.textContent === "1 unread" && !refreshNotifications.disabled', "one remaining unread notification");
  await evaluate("markAllNotificationsRead.click()");
  await waitFor('notificationUnreadCount.textContent === "0 unread" && !refreshNotifications.disabled', "read-all persistence");
  assert.equal(await evaluate('notificationList.querySelectorAll("[data-read=false]").length'), 0);
  await evaluate('notificationList.querySelector("a").click()');
  await portal(detailsPage, "my-orders", "!orderDetailsContent.hidden");
  assert.equal(await evaluate("detailReference.textContent"), failedOrder);
  assert.equal(await evaluate('trackingContent.querySelectorAll(".client-order-records > li").length'), 2);
  assert.match(await evaluate("trackingContent.textContent"), /Attempt 1.*Attempt 2/s);
  assert.match(await evaluate("orderDetailDelivery.textContent"), /Current recipient.*Unit 6/s);
  assert.match(await evaluate("trackingContent.textContent"), /Address version1.*Address version2/s);
  assert.doesNotMatch(await evaluate("trackingContent.textContent"), /M10E worker-only note/);
  assert.equal(await evaluate('!postDeliveryActions.hidden && Array.from(postDeliveryActions.querySelectorAll("button")).every((button) => button.disabled)'), true);
  await evaluate('postDeliveryActions.querySelectorAll("button").forEach((button) => button.click())');
  assert.equal(await evaluate("openCancelOrder.hidden"), true);

  await open(detailsPath(reference));
  await portal(detailsPage, "my-orders", "!orderDetailsContent.hidden");
  await cancelVisibleOrder(reference);
  await open(detailsPath(paidOrder));
  await portal(detailsPage, "my-orders", "!orderDetailsContent.hidden");
  await cancelVisibleOrder(paidOrder);
  assert.match(await evaluate("orderDetailPayments.textContent"), /Payment accepted.*Refund requested/s);
  assert.doesNotMatch(await evaluate("orderDetailPayments.textContent"), /Refund completed/);
  assert.equal(calls.some(({ path }) => /\/checkout$|\/instructions$|\/delivery-options$/.test(path)), false);

  assert.equal(await statusOf(ordersApi + "/" + otherOrder), 404);
  assert.equal(await statusOf(ordersApi + "/" + otherOrder + "/tracking"), 404);
  await signOut();
  assert.equal(await statusOf("/api/client/account"), 401);
  assert.equal(await statusOf(notificationsApi), 401);
  assert.equal(await statusOf(ordersApi + "/" + reference), 401);
  await signIn("B");
  await open(accountPage);
  await portal(accountPage, "account-settings", "recipientName.value === 'Other Recipient'");
  assert.doesNotMatch(await evaluate("deliveryAddress.value"), /Unit 8/);
  await navigateSidebar("notifications");
  await portal(notificationsPage, "notifications", "!notificationList.hidden && !refreshNotifications.disabled");
  assert.equal(await evaluate("notificationUnreadCount.textContent"), "1 unread");
  assert.equal(await evaluate("notificationList.children.length"), 1);
  await open(detailsPath(reference));
  await portal(detailsPage, "my-orders", 'orderDetailsNotice.textContent === "This order is unavailable."');
  assert.equal(await evaluate("orderDetailsContent.hidden && openCancelOrder.hidden && !detailReference.textContent"), true);
  await signOut();
});

test("all five live routes fit desktop and mobile, and guest, wrong-role and forced sessions are gated", { skip: !enabled }, async () => {
  const pages = [
    [accountPage, "account-settings", "!accountContent.hidden"],
    [createPage, "create-order", "!submitOrder.disabled"],
    [listPage, "my-orders", "orderHistory.children.length > 0"],
    [detailsPath(failedOrder), "my-orders", "!orderDetailsContent.hidden"],
    [notificationsPage, "notifications", "!notificationList.hidden && !refreshNotifications.disabled"]
  ];
  await signIn("A");
  try {
    for (const width of [1440, 390, 320]) {
      await command("Emulation.setDeviceMetricsOverride", { width, height: 900, deviceScaleFactor: 1, mobile: width < 500 });
      for (const [route, selected, ready] of pages) {
        await open(route);
        await portal(route.split("?")[0], selected, ready);
        assert.equal(await evaluate("document.documentElement.scrollWidth <= innerWidth"), true, route + " at " + width);
        assert.equal(await evaluate('document.querySelectorAll("main#clientMain").length === 1 && getComputedStyle(document.body).fontFamily.includes("Hanken Grotesk")'), true);
        await evaluate("document.fonts.ready.then(() => true)");
        assert.equal(await evaluate('document.fonts.check("16px Hanken Grotesk") && document.fonts.check("16px JetBrains Mono")'), true);
        if (width < 500) {
          await evaluate("clientMenuToggle.focus()");
          await command("Input.dispatchKeyEvent", { type: "keyDown", key: " ", code: "Space", windowsVirtualKeyCode: 32 });
          await command("Input.dispatchKeyEvent", { type: "keyUp", key: " ", code: "Space", windowsVirtualKeyCode: 32 });
          assert.equal(await evaluate('clientMenuToggle.getAttribute("aria-expanded")'), "true");
          await command("Input.dispatchKeyEvent", { type: "keyDown", key: "Escape", code: "Escape", windowsVirtualKeyCode: 27 });
          await command("Input.dispatchKeyEvent", { type: "keyUp", key: "Escape", code: "Escape", windowsVirtualKeyCode: 27 });
          assert.equal(await evaluate('clientMenuToggle.getAttribute("aria-expanded") === "false" && document.activeElement === clientMenuToggle'), true);
        }
      }
    }
    assert.equal(await evaluate("localStorage.length + sessionStorage.length"), 0);
  } finally {
    await command("Emulation.clearDeviceMetricsOverride");
  }
  await signOut();
  for (const [route] of pages) {
    await open(route);
    await at(loginPage);
  }
  for (const [fixture, destination, rejection] of [["ROLE", welcomePage, 403], ["FORCED", passwordPage, 403]]) {
    await signIn(fixture, destination);
    for (const [route] of pages) {
      await open(route);
      await at(destination);
    }
    assert.equal(await statusOf("/api/client/account"), rejection);
    assert.equal(await statusOf(ordersApi), rejection);
    assert.equal(await statusOf(notificationsApi), rejection);
    await evaluate('sharedSendRequest("POST", "/api/shared/logout")');
  }
});
