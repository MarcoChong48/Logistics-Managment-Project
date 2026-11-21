const defaultsUrl = "/api/client/account/delivery-defaults";

function invalidResponse() {
  return { code: "CLIENT_RESPONSE_INVALID", message: "Could not read the server’s answer.", status: 0 };
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
