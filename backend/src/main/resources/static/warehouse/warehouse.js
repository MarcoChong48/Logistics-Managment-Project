import { byId } from "./warehouse-ui.js";
import { orderQueue, notifications, orderHistory, scanLog } from "./warehouse-views.js";
import { pickingTask } from "./warehouse-task.js";

const page = document.body.dataset.warehousePage;
const main = byId("warehousePageFrameMain");
const entry = byId("warehouse-entry");
const entryStatus = byId("warehouse-entry-status");
const retryEntry = byId("warehouse-entry-retry");
const pageStatus = byId("warehouse-page-status");
const content = byId("warehouse-page-content");
const refresh = byId("warehouse-refresh");
const login = "/shared/shared-login.html";
const changePassword = "/shared/shared-change-password.html";
const denied = "/shared/shared-welcome.html?sharedShowWelcomeNotice=ACCESS_DENIED";

// Fixed shell markup. Server values are assigned as text after the session is verified.
entry.insertAdjacentHTML("afterend", `
  <div class="shared-page-frame shared-page-frame-adaptive warehouse-frame" id="warehouse-frame" hidden>
    <svg class="shared-icon-sprite" aria-hidden="true">
      <symbol id="icon-clock" viewBox="0 0 20 20"><circle cx="10" cy="10" r="6.5"/><path d="M10 6.5v4l3 2"/></symbol>
      <symbol id="icon-check" viewBox="0 0 20 20"><circle cx="10" cy="10" r="6.5"/><path d="m7 10 2 2 4-4"/></symbol>
      <symbol id="icon-alert" viewBox="0 0 20 20"><path d="M10 3.6 17 16H3zM10 8v4m0 2v.5"/></symbol>
      <symbol id="icon-information" viewBox="0 0 20 20"><circle cx="10" cy="10" r="6.5"/><path d="M10 9v4m0-6v.5"/></symbol>
    </svg>
    <a class="shared-skip-link" href="#warehousePageFrameMain">Skip to main content</a>
    <nav class="shared-page-frame-side-menu" id="warehouse-navigation" aria-label="Warehouse pages">
      <div class="shared-page-frame-name">LMS Warehouse</div>
      <button class="shared-button shared-page-frame-menu-close" type="button" id="warehouse-close-menu">Close menu</button>
      <a class="shared-page-frame-menu-item" data-page="notifications" href="warehouse-notifications.html">
        <span class="shared-page-frame-menu-text">Notifications</span>
        <span id="warehouse-notification-count" class="shared-page-frame-menu-count" hidden></span>
      </a>
      <div class="shared-page-frame-menu-label">Picking</div>
      <a class="shared-page-frame-menu-item" data-page="my-orders" href="warehouse-my-orders.html">
        <span class="shared-page-frame-menu-text">My orders</span>
        <span id="warehouse-mine-count" class="shared-page-frame-menu-count" hidden></span>
      </a>
      <a class="shared-page-frame-menu-item" data-page="available-orders" href="warehouse-available-orders.html">
        <span class="shared-page-frame-menu-text">Available orders</span>
        <span id="warehouse-available-count" class="shared-page-frame-menu-count" hidden></span>
      </a>
      <a class="shared-page-frame-menu-item" data-page="scan-log" href="warehouse-scan-log.html">
        <span class="shared-page-frame-menu-text">Scan log</span>
      </a>
      <a class="shared-page-frame-menu-item" data-page="order-history" href="warehouse-order-history.html">
        <span class="shared-page-frame-menu-text">All orders history</span>
      </a>
      <div class="shared-page-frame-menu-footer">
        <a class="shared-button" href="/shared/shared-change-password.html">Change password</a>
      </div>
    </nav>
    <header class="shared-page-frame-top-bar">
      <button class="shared-button shared-page-frame-menu-button" type="button" id="warehouse-open-menu"
        aria-controls="warehouse-navigation" aria-expanded="false">Menu</button>
      <span class="shared-page-frame-spacer"></span>
      <span class="shared-page-frame-countdown" role="timer" aria-live="off">
        <svg class="shared-icon" aria-hidden="true"><use href="#icon-clock"/></svg>
        <span class="shared-page-frame-countdown-words">Session ends in</span>
        <span id="sharedStartSessionCountdownText"></span>
      </span>
      <span class="shared-page-frame-user" id="warehouse-username"></span>
      <a class="shared-page-frame-top-link" href="/shared/shared-change-password.html">Change password</a>
      <button class="shared-button shared-button-small" type="button" id="warehouse-logout">Log out</button>
    </header>
    <dialog class="shared-dialog" id="sharedStartSessionCountdownDialog" aria-labelledby="warehouse-session-title">
      <h2 class="shared-dialog-title" id="warehouse-session-title">Session ending soon</h2>
      <div class="shared-dialog-body">
        <p>Stay logged in to keep working.</p>
        <div class="shared-inline-message shared-inline-message-error" role="alert" hidden><p></p></div>
      </div>
      <div class="shared-dialog-footer">
        <button class="shared-button" type="button" id="warehouse-dialog-logout">Log out</button>
        <button class="shared-button shared-button-primary" type="button" id="warehouse-extend">Stay logged in</button>
      </div>
    </dialog>
  </div>`);
const frame = byId("warehouse-frame");
const menu = byId("warehouse-open-menu");
frame.append(main);
const current = frame.querySelector('[data-page="' + page + '"]');
current?.classList.add("shared-page-frame-menu-item-active");
current?.setAttribute("aria-current", "page");

function toggleMenu(open) {
  frame.classList.toggle("shared-page-frame-menu-open", open);
  menu.setAttribute("aria-expanded", String(open));
  (open ? byId("warehouse-close-menu") : menu).focus();
}

menu.addEventListener("click", () => toggleMenu(menu.getAttribute("aria-expanded") !== "true"));
byId("warehouse-close-menu").addEventListener("click", () => toggleMenu(false));
frame.addEventListener("keydown", event => {
  if (event.key === "Escape" && menu.getAttribute("aria-expanded") === "true") toggleMenu(false);
});
byId("warehouse-extend").addEventListener("click", sharedExtendSession);
for (const id of ["warehouse-logout", "warehouse-dialog-logout"]) {
  byId(id).addEventListener("click", event => sharedLogoutUser(event.currentTarget));
}
for (const control of main.querySelectorAll("[data-close]")) {
  control.addEventListener("click", () => sharedCloseDialog(byId(control.dataset.close)));
}

let username;
let view;
let leaving = false;
let sessionCheck;
let pageLoad;

function redirect(url) {
  leaving = true;
  frame.hidden = true;
  entry.hidden = false;
  entryStatus.textContent = "Opening the shared sign-in flow…";
  window.location.replace(url);
}

function handleAccess(error) {
  if (error?.status === 401) {
    redirect(error.code === "SHARED_SESSION_ENDED" ? login + "?sharedLoginUserNotice=SESSION_ENDED" : login);
  } else if (error?.code === "SHARED_PASSWORD_CHANGE_REQUIRED") {
    redirect(changePassword);
  } else if (error?.code === "SHARED_ACCESS_DENIED") {
    redirect(denied);
  } else {
    return false;
  }
  return true;
}

async function api(path, method = "GET", body = null) {
  try {
    return await sharedSendRequest(method, "/api/warehouse" + path, body);
  } catch (error) {
    handleAccess(error);
    throw error;
  }
}

async function updateSummary() {
  try {
    const summary = await api("/summary");
    const counts = {
      "warehouse-notification-count": summary.unreadAdminNotificationCount,
      "warehouse-mine-count": summary.myTaskCount,
      "warehouse-available-count": summary.availableOrderCount
    };
    for (const [id, count] of Object.entries(counts)) {
      byId(id).textContent = Number.isInteger(count) ? String(count) : "";
      byId(id).hidden = !Number.isInteger(count) || count <= 0;
    }
  } catch {
    for (const badge of frame.querySelectorAll(".shared-page-frame-menu-count")) badge.hidden = true;
  }
}

function loadPage() {
  if (pageLoad || leaving) return pageLoad;
  refresh.disabled = true;
  pageStatus.textContent = "Loading…";
  pageLoad = Promise.allSettled([view.load(), updateSummary()]).then(([result]) => {
    content.hidden = result.status !== "fulfilled";
    pageStatus.textContent = result.status === "fulfilled" ? ""
      : (result.reason?.message || "Could not load this page.") + " Use Refresh to try again.";
  }).finally(() => {
    refresh.disabled = false;
    pageLoad = null;
  });
  return pageLoad;
}

async function verifySession() {
  entryStatus.textContent = "Checking your session…";
  retryEntry.hidden = true;
  frame.hidden = true;
  entry.hidden = false;
  try {
    const session = await sharedSendRequest("GET", "/api/shared/session");
    if (session.sharedViewSessionIsLoggedIn !== true) return redirect(login);
    if (session.mustChangePassword) return redirect(changePassword);
    if (session.role !== "WAREHOUSE_WORKER") return redirect(denied);
    if (username && username !== session.username) {
      window.location.reload();
      return;
    }
    username = session.username;
    byId("warehouse-username").textContent = username;
    if (!view) {
      const views = {
        "available-orders": () => orderQueue(api, "/orders/available"),
        "my-orders": () => orderQueue(api, "/orders/mine"),
        notifications: () => notifications(api, updateSummary),
        "order-history": () => orderHistory(api),
        "scan-log": () => scanLog(api),
        "picking-task": () => pickingTask(api, updateSummary, username)
      };
      view = views[page]();
    }
    entry.hidden = true;
    frame.hidden = false;
    main.hidden = false;
    const seconds = session.sharedViewSessionIdleTimeoutSeconds;
    if (Number.isFinite(seconds) && seconds > 0) sharedStartSessionCountdown(seconds);
    await loadPage();
  } catch (error) {
    if (!handleAccess(error)) {
      entryStatus.textContent = error?.message || "Could not verify your session.";
      retryEntry.hidden = false;
    }
  }
}

function checkSession() {
  if (!sessionCheck && !leaving) sessionCheck = verifySession().finally(() => { sessionCheck = null; });
}

retryEntry.addEventListener("click", checkSession);
refresh.addEventListener("click", loadPage);
document.addEventListener("visibilitychange", () => {
  if (!document.hidden && !document.querySelector("#sharedStartSessionCountdownDialog:modal")) checkSession();
});
checkSession();
