import { byId, button, link, list, node, orderPath, rows, statusBadge, statusWord, taskLink } from "./warehouse-ui.js";

const pageSize = 20;
const canPick = order => order.orderStatus === "READY_FOR_PICKING" || order.isMine && order.orderStatus === "PICKING";

function historyLink(reference) {
  return link(reference, "warehouse-order-history.html?order=" + encodeURIComponent(reference));
}

function renderOrders(orders, isLimited, labels, empty, cells) {
  function showPage(number) {
    const first = (number - 1) * pageSize;
    rows("order-rows", orders.slice(first, first + pageSize), labels, empty, cells);
    const pages = Math.ceil(orders.length / pageSize);
    const pager = byId("orders-pager");
    pager.hidden = pages <= 1;
    if (pages > 1) sharedBuildPager(pager, number, pages, showPage);
  }
  byId("orders-limit").hidden = !isLimited;
  showPage(1);
}

function renderCurrentTask(order) {
  const target = byId("current-task");
  if (!order) {
    target.replaceChildren(node("p", "You have no active picking task. "),
      link("Available orders", "warehouse-available-orders.html"));
    return;
  }
  const progress = node("progress", null, "shared-progress-bar");
  progress.max = order.unitsOrdered || 1;
  progress.value = order.unitsPicked;
  const message = node("p", order.unitsPicked + " of " + order.unitsOrdered + " picked", "shared-progress-text");
  message.id = "current-progress";
  progress.setAttribute("aria-labelledby", message.id);
  const info = [node("h3", order.orderReference, "shared-card-part-title"), progress, message];
  if (order.remainingItemText) info.push(node("p", "Next item: " + order.remainingItemText));
  target.replaceChildren(...info, taskLink("Resume picking", order.orderReference));
}

export function orderQueue(api, path) {
  const mine = path === "/orders/mine";
  const quantities = mine ? ["Units ordered", "Units picked"] : ["Units ordered"];
  return {
    async load() {
      const data = await api(path);
      const orders = list(data, "orderRowList");
      const empty = mine ? "No orders are assigned to you." : "No orders are ready for picking.";
      renderOrders(orders, data.isLimited, ["Order", ...quantities, "Status", "Actions"], empty, order => [
        order.orderReference,
        String(order.unitsOrdered),
        ...(mine ? [String(order.unitsPicked)] : []),
        statusBadge(order.orderStatus),
        taskLink(mine ? "Resume picking" : "Start picking", order.orderReference)
      ]);
      if (mine) renderCurrentTask(orders[0]);
    }
  };
}

export function notifications(api, updateSummary) {
  const markAll = byId("mark-all");
  let marksPending = false;
  let revision = 0;
  let data;

  function render() {
    const adminRows = list(data, "adminRowList");
    const newOrders = list(data, "newOrderRowList");
    rows("new-orders", newOrders, ["Received", "Order", "Units", "Order status", "Actions"],
      "No recent new orders.", order => [
        sharedFormatTime(order.readyTime), order.orderReference, String(order.unitsOrdered),
        statusBadge(order.orderStatus),
        canPick(order) ? taskLink(order.isMine ? "Resume picking" : "Start picking", order.orderReference)
          : historyLink(order.orderReference)
      ]);
    rows("admin-notifications", adminRows, ["Received", "Notification", "Order", "Status", "Actions"],
      "No Admin notifications.", item => {
        const action = item.isRead ? null : button("Mark as read",
          () => mark("/notifications/" + encodeURIComponent(item.notificationID) + "/read"));
        if (action) action.disabled = marksPending;
        return [
          sharedFormatTime(item.createdTime), item.message, item.orderReference ? historyLink(item.orderReference) : null,
          item.isRead ? "Read" : "Unread", action
        ];
      });
    markAll.disabled = marksPending || adminRows.every(item => item.isRead);
  }

  async function load() {
    const current = ++revision;
    const response = await api("/notifications");
    if (current === revision) {
      data = response;
      render();
    }
  }

  async function mark(path) {
    if (marksPending) return;
    marksPending = true;
    render();
    try {
      await api(path, "POST");
      await Promise.all([load(), updateSummary()]);
    } catch (error) {
      sharedShowMessage("ERROR", error?.message || "Could not update the notifications.");
    } finally {
      marksPending = false;
      render();
    }
  }

  markAll.addEventListener("click", () => mark("/notifications/read-all"));
  return { load };
}

export function orderHistory(api) {
  const query = byId("order-query");
  const filter = byId("order-status-filter");
  const dialog = byId("order-details");
  let orders = [];
  let limited = false;
  query.value = sharedReadPageParameter("order") || "";

  function render() {
    const reference = query.value.trim().toLowerCase();
    const matches = orders.filter(order => (!filter.value || order.orderStatus === filter.value)
      && order.orderReference.toLowerCase().includes(reference));
    renderOrders(matches, limited, ["Order", "Created", "Units ordered", "Units picked", "Status", "Actions"],
      reference || filter.value ? "No orders match the filters." : "No orders in Warehouse history.",
      order => [order.orderReference, sharedFormatTime(order.createdTime), String(order.unitsOrdered),
        String(order.unitsPicked), statusBadge(order.orderStatus),
        button("View details", control => details(order, control), order.orderReference)]);
  }

  async function details(order, control) {
    control.disabled = true;
    try {
      const data = await api(orderPath(order.orderReference));
      const lines = list(data, "lineList");
      const timeline = list(data, "timelineRowList");
      byId("order-details-title").textContent = "Order " + data.orderReference;
      const items = node("ul");
      for (const line of lines) {
        items.append(node("li", line.sku + " · " + line.productName + " · "
          + line.pickedQuantity + " of " + line.orderedQuantity + " picked"));
      }
      const history = node("ol");
      for (const event of timeline) {
        history.append(node("li", sharedFormatTime(event.time) + " — " + event.text));
      }
      const actions = node("div", null, "warehouse-actions");
      const qr = link("Open order QR", "/api/warehouse" + orderPath(order.orderReference) + "/qr-code");
      qr.target = "_blank";
      qr.rel = "noopener";
      actions.append(qr);
      if (canPick(order)) actions.append(taskLink(order.isMine ? "Resume picking" : "Start picking", order.orderReference));
      if (data.orderStatus === "READY_FOR_PICKUP") actions.append(taskLink("View packing and pickup", order.orderReference));
      byId("order-details-body").replaceChildren(statusBadge(data.orderStatus),
        node("p", "Created " + sharedFormatTime(data.createdTime)),
        node("h3", "Items", "shared-card-part-title"), items,
        node("h3", "Order activity", "shared-card-part-title"), history, actions);
      sharedOpenDialog(dialog);
    } catch (error) {
      sharedShowMessage("ERROR", error?.message || "Could not load order details.");
    } finally {
      control.disabled = false;
    }
  }

  query.addEventListener("input", render);
  filter.addEventListener("change", render);
  return {
    async load() {
      const data = await api("/orders");
      orders = list(data, "orderRowList");
      limited = data.isLimited;
      const previous = filter.value;
      const statuses = [...new Set(orders.map(order => order.orderStatus))].sort();
      filter.replaceChildren(new Option("All statuses", ""), ...statuses.map(status => new Option(statusWord(status), status)));
      filter.value = statuses.includes(previous) ? previous : "";
      render();
    }
  };
}

export function scanLog(api) {
  return {
    async load() {
      const [activity, mine] = await Promise.all([api("/activity"), api("/orders/mine")]);
      renderCurrentTask(list(mine, "orderRowList")[0]);
      const labels = ["Time", "Order", "Event", "Details"];
      const cells = event => [sharedFormatTime(event.time), historyLink(event.orderReference), statusBadge(event.kind), event.text];
      rows("activity-rows", list(activity, "activityRowList").slice().reverse(), labels, "No picking activity today.", cells);
      rows("scan-errors", list(activity, "scanErrorRowList").slice().reverse(), labels, "No scan errors recorded.", cells);
    }
  };
}
