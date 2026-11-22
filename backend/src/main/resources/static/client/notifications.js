import {
  markAllClientNotificationsRead, markClientNotificationRead, readClientAccount, readClientNotifications, readClientUnreadCount
} from "./client-api.js";
import { startClientShell } from "./client-shell.js";
import { byId, clientOrderDetailsUrl, make, orderLoadMessage, setNotice, timestamp } from "./client-order-view.js";

const list = byId("notificationList");
const notice = byId("notificationNotice");
const actionNotice = byId("notificationActionNotice");
const countLabel = byId("notificationUnreadCount");
const refreshButton = byId("refreshNotifications");
const markAllButton = byId("markAllNotificationsRead");
const moreButton = byId("loadMoreNotifications");
const moreAction = byId("moreNotificationAction");
const typeLabels = new Map([
  ["PAYMENT_CONFIRMED", "Payment confirmed"],
  ["SHIPPED", "Shipped"],
  ["DELIVERY_FAILED", "Delivery failed"],
  ["DELIVERED", "Delivered"],
  ["GENERAL", "General"]
]);
const responseWaitMs = 30000;

function emptyInbox() {
  return { notifications: new Map(), seenCursors: new Set(), cursor: null, pages: 0 };
}

let inbox = emptyInbox();
let unreadCount = null;
let busy = false;
let syncRequired = false;

function updateControls() {
  const markingDisabled = busy || syncRequired || list.hidden;
  const hasUnread = unreadCount > 0 || [...inbox.notifications.values()].some((item) => !item.isRead);
  refreshButton.disabled = busy;
  markAllButton.disabled = markingDisabled || !hasUnread;
  moreButton.disabled = busy || syncRequired;
  moreAction.hidden = list.hidden || inbox.cursor === null;
  list.setAttribute("aria-busy", String(busy));
  for (const button of list.querySelectorAll("[data-mark-notification]")) button.disabled = markingDisabled;
}

function notificationRow(item) {
  const row = make("li", undefined, "client-notification-row");
  row.dataset.notificationId = item.notificationId;
  row.dataset.read = String(item.isRead);
  row.tabIndex = -1;
  const body = make("div", undefined, "client-notification-body");
  const heading = make("div", undefined, "client-notification-heading");
  heading.append(make("strong", typeLabels.get(item.type) ?? "Notification"),
    make("span", item.isRead ? "Read" : "Unread", "client-notification-state"));
  body.append(heading, make("p", item.message), timestamp(item.createdAt));
  row.append(body);

  const actions = make("div", undefined, "client-notification-actions");
  const url = clientOrderDetailsUrl(item.orderReference);
  if (url) {
    const link = make("a", "View order", "client-button");
    link.href = url;
    link.setAttribute("aria-label", "View order " + item.orderReference);
    actions.append(link);
  }
  if (!item.isRead) {
    const mark = make("button", "Mark as read", "client-button");
    mark.type = "button";
    mark.dataset.markNotification = item.notificationId;
    mark.setAttribute("aria-label", "Mark " + (typeLabels.get(item.type) ?? "notification").toLowerCase() + " notification as read");
    actions.append(mark);
  }
  if (actions.children.length) row.append(actions);
  return row;
}

function renderInbox() {
  const rows = document.createDocumentFragment();
  for (const item of inbox.notifications.values()) rows.append(notificationRow(item));
  list.replaceChildren(rows);
  list.hidden = false;
  setNotice(countLabel, unreadCount === null ? "Unread count unavailable" : unreadCount + " unread");
}

async function withinResponseTime(action) {
  let waiting = true;
  let timer;
  try {
    const timeout = new Promise((_, reject) => {
      timer = window.setTimeout(() => reject({ code: "CLIENT_NOTIFICATION_TIMEOUT" }), responseWaitMs);
    });
    return await Promise.race([action(() => waiting), timeout]);
  } finally {
    waiting = false;
    window.clearTimeout(timer);
  }
}

async function readPages(identity, append, waiting) {
  // Rebuild the same page depth after a refresh or mutation, using the new server cursors.
  const next = append
    ? { ...inbox, notifications: new Map(inbox.notifications), seenCursors: new Set(inbox.seenCursors) }
    : emptyInbox();
  const pageCount = append ? 1 : Math.max(1, inbox.pages);
  for (let pageNumber = 0; pageNumber < pageCount; pageNumber++) {
    const page = await readClientNotifications(next.cursor);
    if (!waiting() || !shell.isCurrent(identity)) return null;
    if (page.nextCursor !== null && next.seenCursors.has(page.nextCursor)) {
      throw { code: "CLIENT_NOTIFICATION_CURSOR_INVALID" };
    }
    const previousSize = next.notifications.size;
    for (const item of page.notifications) {
      if (!next.notifications.has(item.notificationId)) next.notifications.set(item.notificationId, item);
    }
    if (page.nextCursor !== null && next.notifications.size === previousSize) {
      throw { code: "CLIENT_NOTIFICATION_CURSOR_INVALID" };
    }
    next.pages++;
    next.cursor = page.nextCursor;
    if (next.cursor === null) break;
    next.seenCursors.add(next.cursor);
  }
  return next;
}

async function readInbox(identity, append = false) {
  setNotice(notice, append ? "Loading more notifications…" : "Loading notifications…");
  const [page, count] = await Promise.allSettled([
    withinResponseTime((waiting) => readPages(identity, append, waiting)),
    withinResponseTime(readClientUnreadCount)
  ]);
  if (!shell.isCurrent(identity)) return false;
  for (const result of [page, count]) {
    if (result.status === "rejected" && shell.handleAccessError(result.reason)) return false;
  }
  try {
    // Recheck the account after both reads in case another tab changed the shared session cookie.
    await withinResponseTime(() => readClientAccount(identity));
  } catch (error) {
    if (shell.isCurrent(identity) && !shell.handleAccessError(error)) {
      list.hidden = true;
      setNotice(countLabel, "");
      setNotice(notice, "Could not verify the Client account. Refresh the inbox to try again.", "error");
    }
    return false;
  }
  if (!shell.isCurrent(identity)) return false;

  unreadCount = count.status === "fulfilled" ? count.value : null;
  if (page.status === "rejected") {
    const staleCursor = page.reason?.status === 404 || page.reason?.code === "CLIENT_NOTIFICATION_CURSOR_INVALID";
    if (staleCursor) {
      inbox.cursor = null;
      inbox.pages = 1;
    }
    setNotice(notice, staleCursor ? "This page is unavailable. Refresh the inbox to reload your notifications."
      : orderLoadMessage(page.reason, "notifications"), "error");
    if (!list.hidden) setNotice(countLabel, unreadCount === null ? "Unread count unavailable" : unreadCount + " unread");
    return false;
  }

  const oldSize = inbox.notifications.size;
  inbox = page.value;
  if (count.status === "fulfilled") syncRequired = false;
  renderInbox();
  setNotice(notice, inbox.notifications.size ? "" : "You have no notifications yet.");
  if (append && inbox.notifications.size > oldSize && shell.currentIdentity() === identity) {
    list.children[oldSize].focus();
  }
  return count.status === "fulfilled";
}

async function loadInbox(identity, append = false) {
  if (busy || (append && (syncRequired || inbox.cursor === null))) return;
  busy = true;
  setNotice(actionNotice, "");
  updateControls();
  try {
    await readInbox(identity, append);
    if (shell.isCurrent(identity) && syncRequired) {
      setNotice(actionNotice, "Refresh the inbox to verify the read status before trying again.", "error");
    }
  } finally {
    if (shell.isCurrent(identity)) {
      busy = false;
      updateControls();
    }
  }
}

async function markRead(identity, id = null) {
  if (busy || syncRequired || list.hidden || (id !== null && inbox.notifications.get(id)?.isRead !== false)) return;
  busy = true;
  syncRequired = true;
  setNotice(actionNotice, "Updating read status…");
  actionNotice.focus();
  updateControls();
  let message = "The read action could not be confirmed.";
  let confirmed = false;
  try {
    confirmed = await withinResponseTime(async (waiting) => {
      await readClientAccount(identity);
      if (!waiting() || shell.currentIdentity() !== identity) return false;
      if (id === null) await markAllClientNotificationsRead();
      else await markClientNotificationRead(id);
      return true;
    });
    if (confirmed) message = id === null ? "Read status updated." : "Notification marked as read.";
  } catch (error) {
    if (!shell.isCurrent(identity) || shell.handleAccessError(error)) return;
    if (error?.status === 404) message = "This notification is unavailable.";
    if (error?.code === "SHARED_SECURITY_TOKEN_INVALID") {
      message = "The security token was rejected.";
      await shell.recheckSession();
    }
  }
  if (!shell.isCurrent(identity)) return;
  try {
    const synchronized = await readInbox(identity);
    if (!shell.isCurrent(identity)) return;
    setNotice(actionNotice, synchronized ? message : "Refresh the inbox to verify the read status before trying again.",
      confirmed && synchronized ? "success" : "error");
  } finally {
    if (shell.isCurrent(identity)) {
      busy = false;
      updateControls();
    }
  }
}

const shell = startClientShell({
  activePage: "notifications",
  onReady: (identity) => loadInbox(identity),
  onClear() {
    inbox = emptyInbox();
    unreadCount = null;
    busy = false;
    syncRequired = false;
    list.replaceChildren();
    list.hidden = true;
    setNotice(countLabel, "");
    setNotice(notice, "Loading notifications…");
    setNotice(actionNotice, "");
    updateControls();
  }
});

refreshButton.addEventListener("click", () => {
  const identity = shell.currentIdentity();
  if (identity) loadInbox(identity);
});

moreButton.addEventListener("click", () => {
  const identity = shell.currentIdentity();
  if (identity) loadInbox(identity, true);
});

markAllButton.addEventListener("click", () => {
  const identity = shell.currentIdentity();
  if (identity) markRead(identity);
});

list.addEventListener("click", (event) => {
  const button = event.target.closest("[data-mark-notification]");
  const identity = shell.currentIdentity();
  if (identity && button) markRead(identity, button.dataset.markNotification);
});
