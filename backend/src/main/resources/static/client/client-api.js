const defaultsUrl = "/api/client/account/delivery-defaults";

function invalidResponse() {
  return { code: "CLIENT_RESPONSE_INVALID", message: "Could not read the server’s answer.", status: 0 };
}

function isHkdAmount(value) {
  return typeof value === "number" && value >= 0 && value <= 9999999999.99
    && Math.round(value * 100) / 100 === value;
}

function readDefaults(value) {
  const fields = ["recipientName", "contactPhone", "deliveryAddress"];
  if (!value || !fields.every((field) => value[field] === null || typeof value[field] === "string")) {
    throw invalidResponse();
  }
  return value;
}

export async function readClientSession() {
  const session = await window.sharedSendRequest("GET", "/api/shared/session");
  if (!session || typeof session.sharedViewSessionIsLoggedIn !== "boolean") {
    throw invalidResponse();
  }
  if (session.sharedViewSessionIsLoggedIn && (typeof session.username !== "string"
      || typeof session.role !== "string" || typeof session.mustChangePassword !== "boolean"
      || !Number.isInteger(session.userID) || !Number.isInteger(session.sharedViewSessionIdleTimeoutSeconds)
      || session.sharedViewSessionIdleTimeoutSeconds < 1
      || !session.sharedViewSessionSecurityToken || !session.sharedViewSessionSecurityTokenHeaderName)) {
    throw invalidResponse();
  }
  return session;
}

export async function readClientAccount(session) {
  const account = await window.sharedSendRequest("GET", "/api/client/account");
  if (!account || typeof account.userId !== "string" || !/^[0-9]+$/.test(account.userId)
      || typeof account.username !== "string" || typeof account.createdAt !== "string"
      || Number.isNaN(Date.parse(account.createdAt))) {
    throw invalidResponse();
  }
  if (account.username !== session.username
      || (Number.isSafeInteger(session.userID) && account.userId !== String(session.userID))) {
    throw { code: "SHARED_SESSION_ENDED", message: "Please sign in again.", status: 401 };
  }
  readDefaults(account.deliveryDefaults);
  return account;
}

export async function saveDeliveryDefaults(defaults) {
  return readDefaults(await window.sharedSendRequest("PUT", defaultsUrl, defaults));
}

export async function readClientProducts(name) {
  // Reading the JSON text preserves bigint IDs before the browser rounds JSON numbers.
  let response;
  try {
    response = await fetch("/api/client/products?name=" + encodeURIComponent(name), {
      headers: { Accept: "application/json" }, cache: "no-store"
    });
  } catch {
    throw { code: "CLIENT_NETWORK_ERROR", status: 0 };
  }
  if (!response.ok) {
    const error = await response.json().catch(() => null);
    throw { status: response.status, code: error?.code, message: error?.message };
  }
  let catalogue;
  try {
    catalogue = JSON.parse(await response.text(), (key, value, context) => {
      if (key !== "productId") return value;
      if (typeof value !== "number" || !Number.isInteger(value) || value < 1) throw invalidResponse();
      if (Number.isSafeInteger(value)) return context?.source ?? String(value);
      // Older browsers can still order safe IDs without risking a rounded ID on the wire.
      return typeof JSON.rawJSON === "function" ? context?.source ?? null : null;
    });
  } catch {
    throw invalidResponse();
  }
  if (!Array.isArray(catalogue) || !catalogue.every((product) => product
      && (product.productId === null || (typeof product.productId === "string"
        && /^[1-9]\d{0,18}$/.test(product.productId) && BigInt(product.productId) <= 9223372036854775807n))
      && typeof product.sku === "string" && typeof product.name === "string"
      && Number.isInteger(product.availableStock) && product.availableStock >= 0 && product.availableStock <= 2147483647
      && isHkdAmount(product.currentPriceHkd))) {
    throw invalidResponse();
  }
  const ids = catalogue.map(({ productId }) => productId).filter((id) => id !== null);
  if (new Set(ids).size !== ids.length) throw invalidResponse();
  return catalogue;
}

export async function createClientOrder(payload, key) {
  const order = await window.sharedSendRequest("POST", "/api/client/orders", payload, null, key);
  if (!order || typeof order.orderReference !== "string" || !order.orderReference.trim()
      || order.orderReference.length > 60 || order.orderStatus !== "AWAITING_PAYMENT"
      || !isHkdAmount(order.totalAmount)
      || typeof order.paymentDeadline !== "string" || Number.isNaN(Date.parse(order.paymentDeadline))) {
    throw invalidResponse();
  }
  return order;
}

const ordersUrl = "/api/client/orders";
const isText = (value) => typeof value === "string";
const isCount = (value) => Number.isSafeInteger(value) && value >= 0;
const nullable = (value, check) => value === null || check(value);
const arrayOf = (value, check) => Array.isArray(value) && value.every(check);
const instantFormat = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z$/;

function isTime(value) {
  if (!isText(value) || !instantFormat.test(value)) return false;
  const time = Date.parse(value);
  // Reject dates silently normalized by the browser, such as 30 February.
  return Number.isFinite(time) && new Date(time).toISOString().slice(0, 19) === value.slice(0, 19);
}

export function isOrderReference(value) {
  return isText(value) && value.trim().length > 0 && value.length <= 60
    && value !== "." && value !== ".." && !/[/\\%\u0000-\u001f\u007f\p{Cs}]/u.test(value);
}

function orderUrl(reference) {
  if (!isOrderReference(reference)) throw { status: 404 };
  return ordersUrl + "/" + encodeURIComponent(reference);
}

function isOrderSummary(order) {
  return order && isOrderReference(order.orderReference) && isText(order.orderStatus)
    && isTime(order.createdAt) && nullable(order.paymentDeadline, isTime) && isHkdAmount(order.totalAmountHkd);
}

function isPaymentAttempt(attempt) {
  return attempt && isText(attempt.status) && isTime(attempt.createdAt) && nullable(attempt.acceptedAt, isTime)
    && arrayOf(attempt.refunds, (refund) => refund && isText(refund.status) && isHkdAmount(refund.amountHkd)
      && isTime(refund.requestedAt) && nullable(refund.completedAt, isTime));
}

export async function readClientOrders(cursor = null) {
  const query = new URLSearchParams({ limit: "20" });
  if (cursor !== null) query.set("cursor", cursor);
  const page = await window.sharedSendRequest("GET", ordersUrl + "?" + query);
  if (!page || !nullable(page.nextCursor, (cursor) => isText(cursor) && cursor.length > 0)
      || !arrayOf(page.orders, (row) => isOrderSummary(row) && row.itemSummary
        && isCount(row.itemSummary.productCount) && isCount(row.itemSummary.totalQuantity))) {
    throw invalidResponse();
  }
  return page;
}

export async function readClientOrder(reference) {
  const order = await window.sharedSendRequest("GET", orderUrl(reference));
  if (!isOrderSummary(order) || order.orderReference !== reference || typeof order.canCancel !== "boolean"
      || !arrayOf(order.items, (item) => item && isText(item.labelSource) && isCount(item.quantity)
        && nullable(item.sku, isText) && nullable(item.name, isText) && isHkdAmount(item.unitPriceHkd))
      || !nullable(order.deliveryDetails, (detail) => detail && isCount(detail.version)
        && isText(detail.recipientName) && isText(detail.phone) && isText(detail.address))
      || !arrayOf(order.paymentAttempts, isPaymentAttempt)) throw invalidResponse();
  return order;
}

export async function readClientOrderTracking(reference) {
  const tracking = await window.sharedSendRequest("GET", orderUrl(reference) + "/tracking");
  if (!tracking || tracking.orderReference !== reference || !isText(tracking.orderStatus) || !isText(tracking.statusLabel)
      || !nullable(tracking.currentStatusSince, isTime) || typeof tracking.historyComplete !== "boolean"
      || !arrayOf(tracking.progression, (event) => event && isText(event.type) && isTime(event.occurredAt)
        && nullable(event.attemptNumber, isCount) && nullable(event.cycleNumber, isCount))
      || !arrayOf(tracking.deliveryAttempts, (attempt) => attempt && isCount(attempt.attemptNumber)
        && nullable(attempt.cycleNumber, isCount) && isCount(attempt.deliveryAddressVersion)
        && isTime(attempt.startedAt) && nullable(attempt.endedAt, isTime)
        && isText(attempt.outcome) && nullable(attempt.failureReason, isText))
      || !arrayOf(tracking.paymentAttempts, isPaymentAttempt)) throw invalidResponse();
  return tracking;
}

export async function cancelClientOrder(reference) {
  const result = await window.sharedSendRequest("POST", orderUrl(reference) + "/cancel");
  if (result?.orderReference !== reference || result.orderStatus !== "CANCELLED") throw invalidResponse();
}

const notificationsUrl = "/api/client/notifications";

function isNotificationId(value) {
  return isText(value) && /^[1-9]\d{0,18}$/.test(value) && BigInt(value) <= 9223372036854775807n;
}

export async function readClientNotifications(cursor = null) {
  const query = new URLSearchParams({ limit: "20" });
  if (cursor !== null) query.set("cursor", cursor);
  const page = await window.sharedSendRequest("GET", notificationsUrl + "?" + query);
  if (!page || !nullable(page.nextCursor, isNotificationId)
      || !arrayOf(page.notifications, (item) => item && isNotificationId(item.notificationId)
        && isText(item.type) && isText(item.message) && nullable(item.orderReference, isText)
        && isTime(item.createdAt) && typeof item.isRead === "boolean")) throw invalidResponse();
  return page;
}

export async function readClientUnreadCount() {
  const result = await window.sharedSendRequest("GET", notificationsUrl + "/unread-count");
  if (!result || !isCount(result.unreadCount)) throw invalidResponse();
  return result.unreadCount;
}

export async function markClientNotificationRead(id) {
  if (!isNotificationId(id)) throw { status: 404 };
  const result = await window.sharedSendRequest("POST", notificationsUrl + "/" + encodeURIComponent(id) + "/read");
  if (result?.notificationId !== id || result.isRead !== true) throw invalidResponse();
}

export async function markAllClientNotificationsRead() {
  const result = await window.sharedSendRequest("POST", notificationsUrl + "/read-all");
  if (!result || !isCount(result.markedRead)) throw invalidResponse();
  return result.markedRead;
}

export function clientErrorMessage(error) {
  switch (error?.status) {
    case 400:
      return typeof error.message === "string" ? error.message : "Check the entered details and try again.";
    case 403:
      return error.code === "SHARED_SECURITY_TOKEN_INVALID"
        ? "Your security token has expired. Reload the page before trying again."
        : "You do not have access to this action.";
    case 404:
      return "These account details are unavailable.";
    case 409:
      return "The saved details may have changed. Reload them before trying again.";
    case 0:
      return error.code === "CLIENT_RESPONSE_INVALID" ? error.message : "Could not reach the server. Try again.";
    default:
      return "The server could not complete the request. Try again.";
  }
}
