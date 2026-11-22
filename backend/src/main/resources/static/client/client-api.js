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
