import { readClientAccount, readClientOrders } from "./client-api.js";
import { startClientShell } from "./client-shell.js";
import { badge, byId, clientOrderDetailsUrl, hkd, make, orderLoadMessage, setNotice, timestamp } from "./client-order-view.js";

const history = byId("orderHistory");
const notice = byId("ordersNotice");
const refresh = byId("refreshOrders");
const more = byId("loadMoreOrders");
const moreAction = byId("ordersMoreAction");
const shownReferences = new Set();
const seenCursors = new Set();
let cursor = null;
let loading = false;

function updateControls() {
  refresh.disabled = loading;
  more.disabled = loading;
  moreAction.hidden = cursor === null;
  history.setAttribute("aria-busy", String(loading));
}

function orderRow(order) {
  const row = make("li", undefined, "client-order-history-row");
  const identity = make("div", undefined, "client-order-record");
  identity.append(make("strong", order.orderReference, "client-order-reference"), timestamp(order.createdAt));
  const status = make("div");
  status.append(badge(order.orderStatus));
  const items = make("p", order.itemSummary.productCount + " products · " + order.itemSummary.totalQuantity + " units");
  const total = make("strong", hkd(order.totalAmountHkd), "client-order-money");
  const details = make("a", "View details", "client-button");
  details.href = clientOrderDetailsUrl(order.orderReference);
  details.setAttribute("aria-label", "View details for " + order.orderReference);
  row.append(identity, status, items, total, details);
  return row;
}

async function loadPage(identity, pageCursor) {
  if (loading) return;
  loading = true;
  const firstPage = pageCursor === null;
  if (firstPage) {
    history.replaceChildren();
    shownReferences.clear();
    seenCursors.clear();
    cursor = null;
  }
  updateControls();
  setNotice(notice, firstPage ? "Loading orders…" : "Loading more orders…");
  try {
    const page = await readClientOrders(pageCursor);
    if (!shell.isCurrent(identity)) return;
    await readClientAccount(identity);
    if (!shell.isCurrent(identity)) return;
    if (page.nextCursor !== null && (seenCursors.has(page.nextCursor) || !page.orders.length)) {
      cursor = null;
      setNotice(notice, "The next page is unavailable. Refresh to reload your orders.", "error");
      return;
    }
    let firstAdded = null;
    for (const order of page.orders) {
      if (shownReferences.has(order.orderReference)) continue;
      shownReferences.add(order.orderReference);
      const row = orderRow(order);
      firstAdded ??= row;
      history.append(row);
    }
    cursor = page.nextCursor;
    if (cursor !== null) seenCursors.add(cursor);
    setNotice(notice, shownReferences.size ? "" : "You have no orders yet.");
    if (!firstPage && firstAdded && shell.currentIdentity() === identity) firstAdded.querySelector("a").focus();
  } catch (error) {
    if (!shell.isCurrent(identity) || shell.handleAccessError(error)) return;
    if (!firstPage && error?.status === 404) {
      cursor = null;
      setNotice(notice, "This page of orders is unavailable. Refresh to reload your orders.", "error");
    } else {
      setNotice(notice, orderLoadMessage(error, "orders"), "error");
    }
  } finally {
    if (shell.isCurrent(identity)) {
      loading = false;
      updateControls();
    }
  }
}

const shell = startClientShell({
  activePage: "my-orders",
  onReady: (identity) => loadPage(identity, null),
  onClear() {
    loading = false;
    cursor = null;
    shownReferences.clear();
    seenCursors.clear();
    history.replaceChildren();
    setNotice(notice, "Loading orders…");
    updateControls();
  }
});

refresh.addEventListener("click", () => {
  const identity = shell.currentIdentity();
  if (identity) loadPage(identity, null);
});

more.addEventListener("click", () => {
  const identity = shell.currentIdentity();
  if (identity && cursor !== null) loadPage(identity, cursor);
});
