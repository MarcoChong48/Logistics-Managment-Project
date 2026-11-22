import { clientErrorMessage, createClientOrder, readClientAccount, readClientProducts } from "./client-api.js";
import { startClientShell } from "./client-shell.js";

const element = (id) => document.getElementById(id);
const form = element("orderForm");
const lineContainer = element("orderLines");
const productFields = element("productFields");
const deliveryFields = element("orderDeliveryFields");
const addButton = element("addOrderLine");
const search = element("catalogueSearch");
const searchButton = element("searchCatalogue");
const refreshButton = element("refreshCatalogue");
const accountRetry = element("retryOrderAccount");
const submit = element("submitOrder");
const retry = element("retryOrder");
const discard = element("discardOrderRetry");
const feedback = element("orderFeedback");
const result = element("createdOrder");
const dialog = element("discardOrderDialog");
const delivery = [
  { key: "recipientName", input: element("orderRecipient"), label: "Recipient name", limit: 120 },
  { key: "contactPhone", input: element("orderPhone"), label: "Contact phone", limit: 40 },
  { key: "deliveryAddress", input: element("orderAddress"), label: "Hong Kong address", limit: 1000 }
];
const number = new Intl.NumberFormat("en-HK", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const date = new Intl.DateTimeFormat("en-HK", { dateStyle: "medium", timeStyle: "short", timeZone: "Asia/Hong_Kong" });
const responseWaitMs = 30000;
const stockRejections = new Map([
  ["CLIENT_ORDER_PRODUCT_NOT_FOUND", 404],
  ["CLIENT_ORDER_PRODUCT_INACTIVE", 409],
  ["CLIENT_ORDER_STOCK_UNAVAILABLE", 409]
]);
const orderRejections = new Map([
  ["CLIENT_ORDER_INVALID", 400],
  ["CLIENT_ORDER_DUPLICATE_PRODUCT", 400],
  ["CLIENT_ORDER_IDEMPOTENCY_KEY_INVALID", 400],
  ["CLIENT_ORDER_TOTAL_TOO_LARGE", 409]
]);
// Match Java String.strip() and PostgreSQL character counts, including supplementary characters.
const outerSpace = /^[\u0009-\u000D\u001C-\u001F \u1680\u2000-\u2006\u2008-\u200A\u2028\u2029\u205F\u3000]+|[\u0009-\u000D\u001C-\u001F \u1680\u2000-\u2006\u2008-\u200A\u2028\u2029\u205F\u3000]+$/gu;
const formatMoney = (value) => "HK$" + number.format(value);

let lines = [];
let lineNumber = 0;
let products = new Map();
let matches = [];
let catalogueReady = false;
let loadingCatalogue = false;
let needsRefresh = false;
let accountReady = false;
let loadingAccount = false;
let pending = null;
let submitting = false;

function showStatus(node, message) {
  node.textContent = message;
  node.hidden = !message;
}

function showFeedback(state, message) {
  feedback.dataset.state = state;
  showStatus(feedback, message);
}

function fieldError(input, message = "") {
  const errorId = input.getAttribute("aria-describedby").split(" ")[0];
  showStatus(element(errorId), message);
  if (message) input.setAttribute("aria-invalid", "true");
  else input.removeAttribute("aria-invalid");
}

function updateControls() {
  const locked = pending !== null;
  productFields.disabled = locked;
  deliveryFields.disabled = locked || !accountReady;
  addButton.disabled = locked || loadingCatalogue || !matches.some(selectable);
  searchButton.disabled = loadingCatalogue;
  refreshButton.disabled = loadingCatalogue;
  accountRetry.disabled = locked || loadingAccount;
  submit.disabled = locked || !catalogueReady || loadingCatalogue || needsRefresh || !accountReady;
  retry.disabled = submitting || pending?.conflict === true;
  discard.disabled = submitting;
  form.setAttribute("aria-busy", String(submitting));
  element("orderRetry").hidden = !locked || submitting;
}

function selectable(product) {
  return product.productId !== null && product.availableStock > 0;
}

function productLabel(product) {
  return product.name + " · " + product.sku;
}

function updateSelections() {
  const chosen = new Set(lines.map((line) => line.productId).filter(Boolean));
  for (const line of lines) {
    const selected = products.get(line.productId);
    const options = matches.slice();
    // Keep a selected product visible while filtering the catalogue.
    if (line.productId && !options.some((record) => record.productId === line.productId)) {
      options.unshift(selected ?? { ...line.previousProduct, productId: line.productId, availableStock: 0 });
    }
    line.select.replaceChildren(new Option("Choose a product", ""));
    for (const item of options) {
      const label = productLabel(item) + (item.productId === null ? " · ID unsupported by this browser"
        : " · " + item.availableStock + " available");
      const option = new Option(label, item.productId ?? "");
      option.disabled = !selectable(item) || (item.productId !== line.productId && chosen.has(item.productId));
      line.select.add(option);
    }
    line.select.value = line.productId;
    line.stock.textContent = selected ? selected.availableStock + " available · " + formatMoney(selected.currentPriceHkd) + " each"
      : line.productId ? "This product is no longer available." : "";
    line.quantity.max = String(selected?.availableStock || 2147483647);
  }
  updateSummary();
  updateControls();
}

function quantityOf(value) {
  if (!/^\d+$/.test(value)) return null;
  const quantity = Number(value);
  return Number.isSafeInteger(quantity) && quantity >= 1 && quantity <= 2147483647 ? quantity : null;
}

function updateSummary() {
  const breakdown = element("orderBreakdown");
  const entries = [];
  let cents = 0n;
  for (const line of lines) {
    const product = products.get(line.productId);
    const quantity = quantityOf(line.quantity.value);
    if (!product || !quantity || quantity > product.availableStock) continue;
    const cost = BigInt(Math.round(product.currentPriceHkd * 100)) * BigInt(quantity);
    cents += cost;
    const row = document.createElement("li");
    const label = document.createElement("span");
    const amount = document.createElement("span");
    label.textContent = quantity + " × " + product.name;
    amount.textContent = cost <= 999999999999n ? formatMoney(Number(cost) / 100) : "Over order limit";
    row.append(label, amount);
    entries.push(row);
  }
  breakdown.replaceChildren(...entries);
  breakdown.hidden = !entries.length;
  element("orderSummaryEmpty").hidden = Boolean(entries.length);
  element("estimatedTotal").textContent = cents <= 999999999999n ? formatMoney(Number(cents) / 100) : "Over order limit";
}

function addLine() {
  const row = element("orderLineTemplate").content.firstElementChild.cloneNode(true);
  const instance = ++lineNumber;
  const select = row.querySelector("select");
  const quantity = row.querySelector("input");
  row.querySelector("legend").textContent = "Product line " + instance;
  for (const input of [select, quantity]) {
    const id = "orderLine" + instance + (input === select ? "Product" : "Quantity");
    input.id = id;
    input.parentElement.querySelector("label").htmlFor = id;
    input.parentElement.querySelector(".client-field-error").id = id + "Error";
    input.setAttribute("aria-describedby", id + "Error");
  }
  const stock = row.querySelector(".client-order-stock");
  stock.id = select.id + "Stock";
  select.setAttribute("aria-describedby", select.getAttribute("aria-describedby") + " " + stock.id);
  const line = { row, select, quantity, stock, productId: "", previousProduct: null };
  lineContainer.append(row);
  lines.push(line);
  select.addEventListener("change", () => {
    line.productId = select.value;
    line.previousProduct = products.get(line.productId);
    fieldError(select);
    fieldError(quantity);
    updateSelections();
  });
  quantity.addEventListener("input", () => {
    fieldError(quantity);
    updateSummary();
  });
  row.querySelector(".order-line-remove").addEventListener("click", () => {
    const index = lines.indexOf(line);
    lines.splice(index, 1);
    row.remove();
    updateSelections();
    (lines[index]?.select ?? lines.at(-1)?.select ?? addButton).focus();
  });
  updateSelections();
  return select;
}

async function loadCatalogue(identity, name) {
  if (loadingCatalogue) return;
  loadingCatalogue = true;
  showStatus(element("catalogueStatus"), "Loading products…");
  updateControls();
  try {
    const rows = await readClientProducts(name);
    if (!shell.isCurrent(identity)) return;
    if (!name) {
      products.clear();
      needsRefresh = false;
    }
    matches = rows;
    for (const product of rows) {
      if (product.productId !== null) products.set(product.productId, product);
    }
    catalogueReady = true;
    showStatus(element("catalogueStatus"), !rows.length ? (name ? "No products match your search." : "The catalogue is empty.")
      : rows.some(selectable) ? "" : "None of these products can be ordered right now.");
    if (!lines.length && rows.some(selectable)) addLine();
    updateSelections();
  } catch (error) {
    if (shell.isCurrent(identity) && !shell.handleAccessError(error)) {
      showStatus(element("catalogueStatus"), clientErrorMessage(error) + " Use Refresh catalogue to try again.");
    }
  } finally {
    if (shell.isCurrent(identity)) {
      loadingCatalogue = false;
      updateControls();
    }
  }
}

async function loadAccount(identity) {
  if (accountReady || loadingAccount) return;
  loadingAccount = true;
  showStatus(element("orderDeliveryStatus"), "Loading saved delivery details…");
  accountRetry.hidden = true;
  updateControls();
  try {
    const account = await readClientAccount(identity);
    if (!shell.isCurrent(identity)) return;
    for (const { key, input } of delivery) input.value = account.deliveryDefaults[key] ?? "";
    accountReady = true;
    showStatus(element("orderDeliveryStatus"), "");
  } catch (error) {
    if (shell.isCurrent(identity) && !shell.handleAccessError(error)) {
      showStatus(element("orderDeliveryStatus"), clientErrorMessage(error));
      accountRetry.hidden = false;
    }
  } finally {
    if (shell.isCurrent(identity)) {
      loadingAccount = false;
      updateControls();
    }
  }
}

function clear() {
  pending = null;
  submitting = loadingCatalogue = loadingAccount = false;
  catalogueReady = accountReady = needsRefresh = false;
  products.clear();
  matches = [];
  lines = [];
  lineContainer.replaceChildren();
  lineNumber = 0;
  search.value = "";
  for (const { input } of delivery) {
    input.value = "";
    fieldError(input);
  }
  for (const id of ["createdOrderReference", "createdOrderStatus", "createdOrderTotal", "createdOrderDeadline"]) {
    element(id).textContent = "";
  }
  element("createdOrderDeadline").removeAttribute("datetime");
  result.hidden = true;
  form.hidden = false;
  if (dialog.open) window.sharedCloseDialog(dialog);
  showStatus(element("catalogueStatus"), "Loading products…");
  showStatus(element("orderDeliveryStatus"), "Loading saved delivery details…");
  showStatus(element("orderLinesError"), "");
  showFeedback("", "");
  accountRetry.hidden = true;
  updateSummary();
  updateControls();
}

function orderPayload() {
  const items = [];
  const used = new Set();
  let firstInvalid;
  let cents = 0n;
  for (const line of lines) {
    const product = products.get(line.productId);
    const quantity = quantityOf(line.quantity.value);
    const productError = !line.productId ? "Choose a product." : used.has(line.productId)
      ? "Choose each product only once." : !product?.availableStock ? "Choose an available product." : "";
    const quantityError = !quantity ? "Enter a positive whole number."
      : product?.availableStock && quantity > product.availableStock ? "Enter a quantity within available stock." : "";
    fieldError(line.select, productError);
    fieldError(line.quantity, quantityError);
    if (productError || quantityError) {
      firstInvalid ??= productError ? line.select : line.quantity;
    } else {
      used.add(line.productId);
      cents += BigInt(Math.round(product.currentPriceHkd * 100)) * BigInt(quantity);
      const id = BigInt(line.productId);
      items.push(Object.freeze({ productId: id <= BigInt(Number.MAX_SAFE_INTEGER)
        ? Number(id) : JSON.rawJSON(line.productId), quantity }));
    }
  }
  const linesMessage = !lines.length ? "Add at least one product line."
    : cents > 999999999999n ? "The estimate exceeds the supported order total." : "";
  showStatus(element("orderLinesError"), linesMessage);
  if (linesMessage) firstInvalid ??= addButton;

  const payload = { items: Object.freeze(items) };
  for (const { key, input, label, limit } of delivery) {
    const value = input.value.replace(outerSpace, "");
    payload[key] = value;
    const message = !value ? label + " is required."
      : Array.from(value).length > limit ? label + " must be " + limit.toLocaleString("en") + " characters or fewer."
        : /[\u0000\p{Cs}]/u.test(value) ? label + " contains unsupported characters." : "";
    fieldError(input, message);
    if (message) firstInvalid ??= input;
  }
  if (firstInvalid) {
    showFeedback("error", "Check the order details before submitting.");
    firstInvalid.focus();
    return null;
  }
  return Object.freeze(payload);
}

function displayOrder(order) {
  element("createdOrderReference").textContent = order.orderReference;
  element("createdOrderStatus").textContent = order.orderStatus;
  element("createdOrderTotal").textContent = formatMoney(order.totalAmount);
  const deadline = element("createdOrderDeadline");
  deadline.dateTime = order.paymentDeadline;
  deadline.textContent = date.format(new Date(order.paymentDeadline));
  form.hidden = true;
  result.hidden = false;
  if (!document.hidden) result.focus();
}

async function sendOrder(identity) {
  const { key, payload } = pending;
  submitting = true;
  updateControls();
  showFeedback("pending", "Creating your order. Keep this page open to receive the result.");
  let timer;
  let returnedOrder = false;
  try {
    const verifiedOrder = createClientOrder(payload, key).then(async (order) => {
      returnedOrder = true;
      if (shell.isCurrent(identity)) await readClientAccount(identity);
      return order;
    });
    // The request can still complete after the browser stops waiting. Explicit retries use the same key.
    const timeout = new Promise((_, reject) => {
      timer = window.setTimeout(() => reject({ status: 0, code: "CLIENT_ORDER_RESPONSE_TIMEOUT" }), responseWaitMs);
    });
    const order = await Promise.race([verifiedOrder, timeout]);
    if (!shell.isCurrent(identity)) return;
    pending = null;
    displayOrder(order);
  } catch (error) {
    if (!shell.isCurrent(identity) || shell.handleAccessError(error)) return;
    const { status = 0, code } = error ?? {};
    if (!returnedOrder && status === 403 && code === "SHARED_SECURITY_TOKEN_INVALID") {
      showFeedback("error", "The session token was rejected. After the session check, retry the original submission.");
      shell.recheckSession();
    } else if (!returnedOrder && status === 409 && code === "CLIENT_ORDER_IDEMPOTENCY_KEY_CONFLICT") {
      pending.conflict = true;
      showFeedback("error", "The server matched this submission key to different order details. Avoid resubmitting the same order as a new one.");
    } else if (!returnedOrder && stockRejections.get(code) === status) {
      pending = null;
      needsRefresh = true;
      showFeedback("error", "Product availability changed. Refresh the catalogue and review your quantities before submitting again.");
      updateControls();
      refreshButton.focus();
    } else if (!returnedOrder && orderRejections.get(code) === status) {
      pending = null;
      showFeedback("error", typeof error.message === "string" ? error.message : clientErrorMessage(error));
    } else if (code === "CLIENT_ORDER_RESPONSE_TIMEOUT") {
      showFeedback("error", "We stopped waiting for a response. The order may already be saved. Retry the original submission.");
    } else {
      showFeedback("error", "We could not confirm the result. Retry with the original order details.");
    }
  } finally {
    window.clearTimeout(timer);
    if (shell.isCurrent(identity)) {
      submitting = false;
      updateControls();
    }
  }
}

function newOrder(identity) {
  clear();
  loadCatalogue(identity, "");
  loadAccount(identity);
  element("clientMain").focus();
}

const shell = startClientShell({ activePage: "create-order", onClear: clear, preservePendingRequest: () => pending !== null, onReady(identity) {
  if (!catalogueReady && !loadingCatalogue) loadCatalogue(identity, "");
  loadAccount(identity);
} });

addButton.addEventListener("click", () => addLine().focus());
searchButton.addEventListener("click", () => {
  const identity = shell.currentIdentity();
  if (identity && !pending) loadCatalogue(identity, search.value.trim());
});
search.addEventListener("keydown", (event) => {
  if (event.key === "Enter") {
    event.preventDefault();
    searchButton.click();
  }
});
refreshButton.addEventListener("click", () => {
  const identity = shell.currentIdentity();
  if (identity && !pending) {
    search.value = "";
    loadCatalogue(identity, "");
  }
});
accountRetry.addEventListener("click", () => {
  const identity = shell.currentIdentity();
  if (identity) loadAccount(identity);
});
for (const { input } of delivery) input.addEventListener("input", () => fieldError(input));
form.addEventListener("submit", (event) => {
  event.preventDefault();
  const identity = shell.currentIdentity();
  if (!identity || pending || submitting || submit.disabled) return;
  const payload = orderPayload();
  if (!payload) return;
  const key = Array.from(crypto.getRandomValues(new Uint8Array(16)), (byte) => byte.toString(16).padStart(2, "0")).join("");
  pending = { key, payload };
  sendOrder(identity);
});
retry.addEventListener("click", () => {
  const identity = shell.currentIdentity();
  if (identity && pending && !pending.conflict && !submitting) sendOrder(identity);
});
discard.addEventListener("click", () => window.sharedOpenDialog(dialog));
element("keepOrderRetry").addEventListener("click", () => window.sharedCloseDialog(dialog));
for (const id of ["confirmNewOrder", "startAnotherOrder"]) {
  element(id).addEventListener("click", () => {
    const identity = shell.currentIdentity();
    if (identity && !submitting) newOrder(identity);
  });
}
