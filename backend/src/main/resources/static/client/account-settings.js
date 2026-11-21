import { clientErrorMessage, readClientAccount, saveDeliveryDefaults } from "./client-api.js";
import { startClientShell } from "./client-shell.js";

const content = document.getElementById("accountContent");
const accountLoad = document.getElementById("accountLoad");
const loadStatus = document.getElementById("accountLoadStatus");
const retry = document.getElementById("accountRetry");
const form = document.getElementById("deliveryForm");
const fieldset = document.getElementById("deliveryFields");
const feedback = document.getElementById("deliveryFeedback");
const empty = document.getElementById("deliveryEmpty");
const reload = document.getElementById("reloadDefaults");
const accountUsername = document.getElementById("accountUsername");
const accountRole = document.getElementById("accountRole");
const createdAt = document.getElementById("accountCreatedAt");
const fields = [
  { id: "recipientName", label: "Recipient name", max: 120 },
  { id: "contactPhone", label: "Contact phone", max: 40 },
  { id: "deliveryAddress", label: "Hong Kong address", max: 1000 }
].map((field) => ({
  ...field,
  input: document.getElementById(field.id),
  error: document.getElementById(field.id + "Error")
}));

const date = new Intl.DateTimeFormat("en-HK", { dateStyle: "medium", timeZone: "Asia/Hong_Kong" });
const phoneFormat = /^\+?[0-9]+(?:[ -][0-9]+)*$/;
// Match String.strip() for the whitespace the server allows after rejecting control characters.
const outerSpace = /^[\n \u1680\u2000-\u2006\u2008-\u200A\u205F\u3000]+|[\n \u1680\u2000-\u2006\u2008-\u200A\u205F\u3000]+$/gu;
const invalidText = /[\p{Cc}\p{Cs}\u2028\u2029]/u;
let loaded = false;
let loading = false;
let updating = false;

function fieldError(field, message = "") {
  field.error.textContent = message;
  field.error.hidden = !message;
  if (message) {
    field.input.setAttribute("aria-invalid", "true");
  } else {
    field.input.removeAttribute("aria-invalid");
  }
}

function showFeedback(state, message = "") {
  feedback.textContent = message;
  feedback.dataset.state = state;
  feedback.hidden = !message;
}

function setBusy(busy) {
  updating = busy;
  fieldset.disabled = busy;
  form.setAttribute("aria-busy", String(busy));
}

function renderDefaults(defaults) {
  for (const field of fields) {
    field.input.value = defaults[field.id] ?? "";
    fieldError(field);
  }
  empty.hidden = fields.some(({ id }) => defaults[id] !== null);
}

function clearAccount() {
  loaded = false;
  loading = false;
  content.hidden = true;
  accountUsername.textContent = "";
  accountRole.textContent = "";
  createdAt.textContent = "";
  createdAt.removeAttribute("datetime");
  renderDefaults({ recipientName: null, contactPhone: null, deliveryAddress: null });
  showFeedback("");
  setBusy(false);
  accountLoad.hidden = false;
  loadStatus.textContent = "Loading your account…";
  retry.hidden = true;
}

async function loadAccount(identity) {
  if (loaded || loading) {
    return;
  }
  loading = true;
  loadStatus.textContent = "Loading your account…";
  retry.hidden = true;
  try {
    const account = await readClientAccount(identity);
    if (!shell.isCurrent(identity)) {
      return;
    }
    accountUsername.textContent = account.username;
    accountRole.textContent = identity.role === "CLIENT" ? "Client" : identity.role;
    createdAt.dateTime = account.createdAt;
    createdAt.textContent = date.format(new Date(account.createdAt));
    renderDefaults(account.deliveryDefaults);
    loaded = true;
    accountLoad.hidden = true;
    content.hidden = false;
  } catch (error) {
    if (shell.isCurrent(identity) && !shell.handleAccessError(error)) {
      loadStatus.textContent = clientErrorMessage(error);
      retry.hidden = false;
    }
  } finally {
    if (shell.isCurrent(identity)) {
      loading = false;
    }
  }
}

function fieldValue(field, raw) {
  const multiline = field.id === "deliveryAddress";
  const normalized = multiline ? raw.replaceAll("\r\n", "\n") : raw;
  if (invalidText.test(multiline ? normalized.replaceAll("\n", "") : normalized)) {
    throw new Error(field.label + " contains unsupported characters.");
  }
  const value = normalized.replace(outerSpace, "");
  if (!value) {
    return null;
  }
  if (Array.from(value).length > field.max) {
    throw new Error(field.label + " must be " + field.max.toLocaleString("en") + " characters or fewer.");
  }
  if (field.id === "recipientName" && !/\p{L}/u.test(value)) {
    throw new Error("Recipient name must include a letter.");
  }
  if (field.id === "contactPhone") {
    const digits = value.replace(/[^0-9]/g, "").length;
    if (digits < 7 || digits > 15 || !phoneFormat.test(value)) {
      throw new Error("Enter 7 to 15 digits. You can use a leading + and spaces or hyphens between groups.");
    }
  }
  if (multiline && !/[\p{L}\p{Nd}]/u.test(value)) {
    throw new Error("Hong Kong address must include a letter or digit.");
  }
  return value;
}

async function updateDefaults(identity, action, pendingMessage, successMessage, saving) {
  setBusy(true);
  showFeedback("pending", pendingMessage);
  try {
    const defaults = await action();
    if (shell.isCurrent(identity)) {
      renderDefaults(defaults);
      showFeedback("success", successMessage);
    }
  } catch (error) {
    if (shell.isCurrent(identity) && !shell.handleAccessError(error)) {
      const message = saving && (error?.status === 0 || error?.status >= 500)
        ? "The save could not be confirmed. Reload your saved details before trying again."
        : clientErrorMessage(error);
      showFeedback("error", message);
    }
  } finally {
    if (shell.isCurrent(identity)) {
      setBusy(false);
    }
  }
}

const shell = startClientShell({ activePage: "account-settings", onReady: loadAccount, onClear: clearAccount });

retry.addEventListener("click", () => {
  const identity = shell.currentIdentity();
  if (identity) {
    loadAccount(identity);
  }
});
for (const field of fields) {
  field.input.addEventListener("input", () => {
    fieldError(field);
    showFeedback("");
  });
}
form.addEventListener("submit", (event) => {
  event.preventDefault();
  const identity = shell.currentIdentity();
  if (!identity || !loaded || updating) {
    return;
  }
  const payload = {};
  let firstInvalid;
  for (const field of fields) {
    fieldError(field);
    try {
      payload[field.id] = fieldValue(field, field.input.value);
    } catch (error) {
      fieldError(field, error.message);
      firstInvalid ??= field.input;
    }
  }
  if (firstInvalid) {
    showFeedback("error", "Check the highlighted fields.");
    firstInvalid.focus();
    return;
  }
  updateDefaults(identity, () => saveDeliveryDefaults(payload), "Saving delivery details…", "Delivery details saved.", true);
});
reload.addEventListener("click", () => {
  const identity = shell.currentIdentity();
  if (identity && loaded && !updating) {
    // Use the account response so the saved defaults are bound to the verified owner.
    updateDefaults(identity, async () => (await readClientAccount(identity)).deliveryDefaults,
      "Loading saved details…", "Saved delivery details reloaded.", false);
  }
});
