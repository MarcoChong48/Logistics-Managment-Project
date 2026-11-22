import { cancelClientOrder, isOrderReference, readClientAccount, readClientOrder, readClientOrderTracking } from "./client-api.js";
import { startClientShell } from "./client-shell.js";
import { badge, byId, hkd, make, orderLoadMessage, setNotice, timestamp } from "./client-order-view.js";

const reference = new URLSearchParams(location.search).get("reference");
const content = byId("orderDetailsContent");
const notice = byId("orderDetailsNotice");
const cancellationNotice = byId("cancellationNotice");
const trackingNotice = byId("trackingNotice");
const refresh = byId("refreshOrderDetails");
const cancel = byId("openCancelOrder");
const dialog = byId("cancelOrderDialog");
const confirmCancel = byId("confirmCancelOrder");
const timeline = byId("trackingContent");
const cancellationWaitMs = 30000;
const eventLabels = new Map([
  ["ORDER_PLACED", "Order placed"],
  ["PICKING_STARTED", "Picking started"],
  ["PACKING_CONFIRMED", "Packing confirmed"],
  ["PACKAGE_CREATED", "Package created"],
  ["PACKAGE_PICKED_UP", "Package picked up"],
  ["PACKAGE_RETURNED_TO_WAREHOUSE", "Package returned to warehouse"],
  ["DELIVERY_ATTEMPT_STARTED", "Delivery attempt started"],
  ["DELIVERY_COMPLETED", "Delivery completed"],
  ["DELIVERY_FAILED", "Delivery failed"],
  ["DELIVERY_ATTEMPT_ENDED", "Delivery attempt ended"]
]);
const paymentLabels = new Map([
  ["PENDING", "Payment pending"], ["ACCEPTED", "Payment accepted"],
  ["ENDED_UNPAID", "Checkout ended unpaid"], ["REFUND_REQUIRED", "Payment requires a refund"]
]);
const refundLabels = new Map([
  ["REQUESTED", "Refund requested"], ["PENDING", "Refund pending"],
  ["FAILED", "Refund failed"], ["CANCELLED", "Refund cancelled"]
]);
const deliveryLabels = new Map([
  ["IN_PROGRESS", "In progress"], ["DELIVERED", "Delivered"], ["FAILED", "Failed"]
]);
let order = null;
let loading = false;
let cancelling = false;

function fact(list, title, value) {
  const group = make("div");
  const data = make("dd");
  data.append(value);
  group.append(make("dt", title), data);
  list.append(group);
}

function updateActions() {
  refresh.hidden = !isOrderReference(reference);
  refresh.disabled = loading || cancelling;
  cancel.hidden = order?.canCancel !== true;
  cancel.disabled = loading || cancelling;
  confirmCancel.disabled = loading || cancelling;
  content.setAttribute("aria-busy", String(loading));
}

function clearOrder() {
  order = null;
  content.hidden = true;
  byId("detailReference").hidden = true;
  byId("detailReference").textContent = "";
  for (const id of ["orderDetailFacts", "orderDetailItems", "orderDetailDelivery", "orderDetailPayments", "trackingContent"]) {
    byId(id).replaceChildren();
  }
  byId("orderItemsEmpty").hidden = true;
  byId("postDeliveryActions").hidden = true;
  setNotice(trackingNotice, "");
  if (dialog.open) window.sharedCloseDialog(dialog);
  updateActions();
}

function renderItems(items) {
  const list = byId("orderDetailItems");
  for (const item of items) {
    const row = make("li", undefined, "client-order-record");
    const catalogue = item.labelSource === "CURRENT_CATALOGUE";
    row.append(make("strong", catalogue && item.name ? item.name : "Product label unavailable"));
    if (catalogue && (item.name || item.sku)) {
      row.append(make("p", item.sku ? "Current catalogue · SKU " + item.sku : "Current catalogue", "client-order-note"));
    }
    const facts = make("dl", undefined, "client-order-facts");
    fact(facts, "Quantity", String(item.quantity));
    fact(facts, "Historical unit price", hkd(item.unitPriceHkd));
    row.append(facts);
    list.append(row);
  }
  byId("orderItemsEmpty").hidden = items.length !== 0;
}

function renderDelivery(detail) {
  const panel = byId("orderDetailDelivery");
  if (detail === null) {
    panel.append(make("p", "Delivery details are unavailable.", "client-empty"));
    return;
  }
  const facts = make("dl", undefined, "client-order-facts");
  fact(facts, "Recipient", detail.recipientName);
  fact(facts, "Phone", detail.phone);
  fact(facts, "Address", make("span", detail.address, "client-order-address"));
  fact(facts, "Address version", String(detail.version));
  panel.append(facts);
}

function renderPayments(attempts) {
  const panel = byId("orderDetailPayments");
  if (!attempts.length) {
    panel.append(make("p", "No payment attempts recorded.", "client-empty"));
    return;
  }
  const list = make("ul", undefined, "client-order-records");
  list.setAttribute("role", "list");
  for (const attempt of attempts) {
    const row = make("li", undefined, "client-order-record");
    row.append(make("strong", paymentLabels.get(attempt.status) ?? "Payment status unavailable"));
    const facts = make("dl", undefined, "client-order-facts");
    fact(facts, "Attempt created", timestamp(attempt.createdAt));
    if (attempt.acceptedAt !== null) fact(facts, "Payment accepted at", timestamp(attempt.acceptedAt));
    row.append(facts);
    if (attempt.refunds.length) {
      const refunds = make("ul", undefined, "client-order-refunds");
      refunds.setAttribute("role", "list");
      for (const refund of attempt.refunds) {
        const completed = refund.status === "COMPLETED" && refund.completedAt !== null
          && Date.parse(refund.completedAt) >= Date.parse(refund.requestedAt);
        const label = completed ? "Refund completed" : refundLabels.get(refund.status) ?? "Refund status unavailable";
        const refundRow = make("li", undefined, "client-order-record");
        refundRow.append(make("strong", label));
        const refundFacts = make("dl", undefined, "client-order-facts");
        fact(refundFacts, "Amount", hkd(refund.amountHkd));
        fact(refundFacts, "Requested at", timestamp(refund.requestedAt));
        if (completed) fact(refundFacts, "Completed at", timestamp(refund.completedAt));
        refundRow.append(refundFacts);
        refunds.append(refundRow);
      }
      row.append(refunds);
    }
    list.append(row);
  }
  panel.append(list);
}

function renderOrder(data) {
  order = data;
  const title = byId("detailReference");
  title.textContent = data.orderReference;
  title.hidden = false;
  const facts = byId("orderDetailFacts");
  fact(facts, "Order status", badge(data.orderStatus));
  fact(facts, "Created", timestamp(data.createdAt));
  fact(facts, "Confirmed total", make("span", hkd(data.totalAmountHkd), "client-order-money"));
  if (data.paymentDeadline !== null) fact(facts, "Payment deadline", timestamp(data.paymentDeadline));
  renderItems(data.items);
  renderDelivery(data.deliveryDetails);
  renderPayments(data.paymentAttempts);
  byId("postDeliveryActions").hidden = data.orderStatus !== "DELIVERY_FAILED";
  content.hidden = false;
}

function renderTracking(data) {
  const status = make("p", undefined, "client-order-tracking-status");
  status.append(make("strong", data.statusLabel));
  if (data.currentStatusSince !== null) status.append(" since ", timestamp(data.currentStatusSince));
  timeline.append(status);
  if (!data.historyComplete) {
    timeline.append(make("p", "Earlier tracking events may be unavailable.", "client-empty"));
  }
  if (data.progression.length) {
    const events = make("ol", undefined, "client-order-timeline");
    events.setAttribute("role", "list");
    for (const event of data.progression) {
      const row = make("li", undefined, "client-order-record");
      row.append(make("strong", eventLabels.get(event.type) ?? "Recorded tracking event"), timestamp(event.occurredAt));
      const context = [];
      if (event.attemptNumber !== null) context.push("Attempt " + event.attemptNumber);
      if (event.cycleNumber !== null) context.push("Cycle " + event.cycleNumber);
      if (context.length) row.append(make("span", context.join(" · "), "client-order-note"));
      events.append(row);
    }
    timeline.append(events);
  } else {
    timeline.append(make("p", "No tracking events are available.", "client-empty"));
  }

  timeline.append(make("h3", "Delivery attempts", "client-order-subheading"));
  if (!data.deliveryAttempts.length) {
    timeline.append(make("p", "No delivery attempts recorded.", "client-empty"));
    return;
  }
  const attempts = make("ul", undefined, "client-order-records");
  attempts.setAttribute("role", "list");
  for (const attempt of data.deliveryAttempts) {
    const row = make("li", undefined, "client-order-record");
    row.append(make("strong", "Attempt " + attempt.attemptNumber + " · " + (deliveryLabels.get(attempt.outcome) ?? "Outcome unavailable")));
    const facts = make("dl", undefined, "client-order-facts");
    if (attempt.cycleNumber !== null) fact(facts, "Cycle", String(attempt.cycleNumber));
    fact(facts, "Address version", String(attempt.deliveryAddressVersion));
    fact(facts, "Started", timestamp(attempt.startedAt));
    if (attempt.endedAt !== null) fact(facts, "Ended", timestamp(attempt.endedAt));
    if (attempt.outcome === "FAILED" && attempt.failureReason) fact(facts, "Reason", attempt.failureReason);
    row.append(facts);
    attempts.append(row);
  }
  timeline.append(attempts);
}

function unavailable() {
  clearOrder();
  setNotice(notice, "This order is unavailable.", "error");
}

async function loadOrder(identity) {
  if (loading || cancelling) return;
  if (!isOrderReference(reference)) {
    unavailable();
    return;
  }
  loading = true;
  clearOrder();
  setNotice(notice, "Loading order…");
  try {
    const [details, tracking] = await Promise.allSettled([readClientOrder(reference), readClientOrderTracking(reference)]);
    if (!shell.isCurrent(identity)) return;
    // Both endpoints enforce ownership. Bind the responses to the Client shown by this tab as well.
    for (const result of [details, tracking]) {
      if (result.status === "rejected" && shell.handleAccessError(result.reason)) return;
    }
    if ([details, tracking].some(({ status, reason }) => status === "rejected" && reason?.status === 404)) {
      unavailable();
      return;
    }
    if (details.status === "rejected") throw details.reason;
    await readClientAccount(identity);
    if (!shell.isCurrent(identity)) return;
    renderOrder(details.value);
    if (tracking.status === "fulfilled") renderTracking(tracking.value);
    else setNotice(trackingNotice, orderLoadMessage(tracking.reason, "tracking"), "error");
    setNotice(notice, "");
  } catch (error) {
    if (!shell.isCurrent(identity) || shell.handleAccessError(error)) return;
    clearOrder();
    setNotice(notice, orderLoadMessage(error, "the order"), "error");
  } finally {
    if (shell.isCurrent(identity)) {
      loading = false;
      updateActions();
    }
  }
}

async function submitCancellation(identity) {
  cancelling = true;
  updateActions();
  window.sharedCloseDialog(dialog);
  setNotice(cancellationNotice, "Cancelling the order…");
  cancellationNotice.focus();
  let reload = true;
  let waiting = true;
  let timer;
  try {
    const response = (async () => {
      await readClientAccount(identity);
      if (!waiting || shell.currentIdentity() !== identity) return false;
      await cancelClientOrder(reference);
      if (!waiting || !shell.isCurrent(identity)) return false;
      // The cookie may have changed while the cancellation was in flight.
      await readClientAccount(identity);
      return true;
    })();
    const timeout = new Promise((_, reject) => {
      timer = window.setTimeout(() => reject({ code: "CLIENT_ORDER_CANCELLATION_TIMEOUT" }), cancellationWaitMs);
    });
    const confirmed = await Promise.race([response, timeout]);
    if (!shell.isCurrent(identity)) return;
    if (confirmed) setNotice(cancellationNotice, "Order cancelled.", "success");
    else setNotice(cancellationNotice, "Cancellation was paused. Review the order again.", "error");
  } catch (error) {
    if (!shell.isCurrent(identity) || shell.handleAccessError(error)) return;
    if (error?.status === 404) {
      reload = false;
      setNotice(cancellationNotice, "");
      unavailable();
    } else if (error?.status === 409) {
      setNotice(cancellationNotice, "Cancellation eligibility may have changed. Review the refreshed order status.", "error");
    } else if (error?.status === 403 && error.code === "SHARED_SECURITY_TOKEN_INVALID") {
      setNotice(cancellationNotice, "The security token was rejected. Review the order after the session check.", "error");
      await shell.recheckSession();
    } else if (error?.code === "CLIENT_ORDER_CANCELLATION_TIMEOUT") {
      setNotice(cancellationNotice, "The cancellation request timed out. Review the refreshed order status before trying again.", "error");
    } else {
      setNotice(cancellationNotice, "Cancellation could not be confirmed. Review the refreshed order status before trying again.", "error");
    }
  } finally {
    waiting = false;
    window.clearTimeout(timer);
    if (shell.isCurrent(identity)) {
      cancelling = false;
      if (reload) loadOrder(identity);
      else updateActions();
    }
  }
}

const shell = startClientShell({
  activePage: "my-orders",
  onReady: loadOrder,
  onClear() {
    loading = false;
    cancelling = false;
    clearOrder();
    setNotice(cancellationNotice, "");
    setNotice(notice, "Loading order…");
  }
});

refresh.addEventListener("click", () => {
  const identity = shell.currentIdentity();
  if (identity) {
    setNotice(cancellationNotice, "");
    loadOrder(identity);
  }
});

cancel.addEventListener("click", () => {
  if (shell.currentIdentity() && order?.canCancel && !cancelling && !loading) window.sharedOpenDialog(dialog);
});

byId("keepOrder").addEventListener("click", () => window.sharedCloseDialog(dialog));
confirmCancel.addEventListener("click", () => {
  const identity = shell.currentIdentity();
  if (identity && dialog.open && order?.canCancel && !cancelling && !loading) submitCancellation(identity);
});
