import { clientErrorMessage, readClientSession } from "./client-api.js";

const signIn = "/shared/shared-login.html";
const passwordPage = "/shared/shared-change-password.html";
const accessDenied = "/shared/shared-welcome.html?sharedShowWelcomeNotice=ACCESS_DENIED";

export function startClientShell({ activePage, onReady, onClear, preservePendingRequest = () => false }) {
  const entry = document.getElementById("clientEntry");
  const status = document.getElementById("clientEntryStatus");
  const retrySession = document.getElementById("clientRetrySession");
  const retryLogout = document.getElementById("clientRetryLogout");
  const logoutError = document.getElementById("clientLogoutError");
  const main = document.getElementById("clientMain");

  // This template is fixed markup. Set identity and API text using DOM properties.
  entry.insertAdjacentHTML("afterend", `
    <div class="client-portal" id="clientPortal" hidden>
      <a class="client-skip" href="#clientMain">Skip to main content</a>
      <aside class="client-sidebar" id="clientNavigation" aria-label="Client navigation">
        <div class="client-brand">
          <span class="client-brand-icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"><path d="m12 3 8 4.5v9L12 21l-8-4.5v-9L12 3Z"/><path d="m4 7.5 8 4.5 8-4.5M12 12v9M8 5.25l8 4.5"/></svg></span>
          <span>LMS Portal<small>Client workspace</small></span>
        </div>
        <nav aria-label="Client pages">
          <ul class="client-navigation-list">
            <li><a class="client-nav-item" href="/client/create-order.html" data-client-page="create-order"><svg aria-hidden="true" viewBox="0 0 24 24"><path d="M12 5v14M5 12h14"/></svg>Create Order</a></li>
            <li><a class="client-nav-item" href="/client/my-orders-tracking.html" data-client-page="my-orders"><svg aria-hidden="true" viewBox="0 0 24 24"><rect x="5" y="4" width="14" height="16" rx="2"/><path d="M8 9h8m-8 4h8m-8 4h5"/></svg>My Orders &amp; Tracking</a></li>
            <li><a class="client-nav-item" href="/client/notifications.html" data-client-page="notifications"><svg aria-hidden="true" viewBox="0 0 24 24"><path d="M9 19a3 3 0 0 0 6 0M6 9a6 6 0 0 1 12 0c0 4 1 5 2 7H4c1-2 2-3 2-7Z"/></svg>Notifications</a></li>
            <li><a class="client-nav-item" href="/client/account-settings.html" data-client-page="account-settings"><svg aria-hidden="true" viewBox="0 0 24 24"><path d="M4 7h16M4 17h16"/><circle cx="9" cy="7" r="2.5"/><circle cx="15" cy="17" r="2.5"/></svg>Account Settings</a></li>
          </ul>
        </nav>
      </aside>
      <header class="client-header">
        <div class="client-header-title">
          <button class="client-button client-menu-toggle" id="clientMenuToggle" type="button"
            aria-controls="clientNavigation" aria-expanded="false">Menu</button>
          <span>Client Portal</span>
        </div>
        <div class="client-header-actions">
          <span class="client-timer" role="timer" aria-live="off">Session ends in <span id="sharedStartSessionCountdownText"></span></span>
          <span class="client-user" id="clientUsername"></span>
          <button class="client-button" id="clientLogout" type="button">Log out</button>
        </div>
      </header>
      <dialog class="shared-dialog client-session-dialog" id="sharedStartSessionCountdownDialog" aria-labelledby="clientCountdownTitle">
        <h2 class="shared-dialog-title" id="clientCountdownTitle">Session ending soon</h2>
        <div class="shared-dialog-body">
          <p>Stay logged in to keep working.</p>
          <div class="shared-inline-message shared-inline-message-error" role="alert" hidden><p></p></div>
        </div>
        <div class="shared-dialog-footer">
          <button class="client-button" id="clientDialogLogout" type="button">Log out</button>
          <button class="client-button client-button--primary" id="clientExtendSession" type="button">Stay logged in</button>
        </div>
      </dialog>
    </div>`);

  const portal = document.getElementById("clientPortal");
  const username = document.getElementById("clientUsername");
  const menu = document.getElementById("clientMenuToggle");
  const dialog = document.getElementById("sharedStartSessionCountdownDialog");
  const dialogError = dialog.querySelector(".shared-inline-message");
  const channel = "BroadcastChannel" in window ? new BroadcastChannel("lms-client-session") : null;
  let identity = null;
  let check = 0;
  let sessionChecks = Promise.resolve();
  let signingOut = false;
  let logoutRequestActive = false;

  portal.append(main);
  main.hidden = false;
  for (const item of portal.querySelectorAll("[data-client-page]")) {
    if (item.dataset.clientPage === activePage) {
      item.setAttribute("aria-current", "page");
    }
  }

  function setMenu(open) {
    menu.setAttribute("aria-expanded", String(open));
    portal.classList.toggle("client-menu-open", open);
  }

  function conceal() {
    portal.hidden = true;
    entry.hidden = false;
    setMenu(false);
    if (dialog.open) {
      window.sharedCloseDialog(dialog);
    }
  }

  function clearIdentity() {
    identity = null;
    username.textContent = "";
    onClear();
  }

  function redirect(destination) {
    check++;
    conceal();
    clearIdentity();
    status.textContent = "Leaving the Client Portal…";
    window.location.replace(destination);
  }

  function handleAccessError(error) {
    if (error?.status === 401) {
      redirect(error.code === "SHARED_SESSION_ENDED" ? `${signIn}?sharedLoginUserNotice=SESSION_ENDED` : signIn);
    } else if (error?.code === "SHARED_PASSWORD_CHANGE_REQUIRED") {
      redirect(passwordPage);
    } else if (error?.code === "SHARED_ACCESS_DENIED") {
      redirect(accessDenied);
    } else {
      return false;
    }
    return true;
  }

  async function verifySession() {
    if (signingOut || document.hidden) {
      return;
    }
    const attempt = ++check;
    conceal();
    status.textContent = "Checking your session…";
    retrySession.hidden = true;
    let session;
    try {
      // Shared requests store the CSRF token before returning. Serialize probes so an old response cannot replace it.
      const request = sessionChecks.then(() => attempt === check ? readClientSession() : null);
      sessionChecks = request.catch(() => {});
      session = await request;
    } catch (error) {
      if (attempt === check && !handleAccessError(error)) {
        // Keep a pending mutation in memory while the portal is hidden and the Client retries verification.
        if (!preservePendingRequest()) clearIdentity();
        status.textContent = clientErrorMessage(error);
        retrySession.hidden = false;
      }
      return;
    }
    if (attempt !== check) {
      return;
    }
    if (!session.sharedViewSessionIsLoggedIn) {
      redirect(signIn);
      return;
    }
    if (session.mustChangePassword) {
      redirect(passwordPage);
      return;
    }
    if (session.role !== "CLIENT") {
      redirect(accessDenied);
      return;
    }
    if (identity?.userID !== session.userID || identity?.username !== session.username) {
      clearIdentity();
      identity = { userID: session.userID, username: session.username, role: session.role };
    }
    username.textContent = session.username;
    window.sharedStartSessionCountdown(session.sharedViewSessionIdleTimeoutSeconds);
    entry.hidden = true;
    portal.hidden = false;
    onReady(identity);
  }

  async function logout(event) {
    if (logoutRequestActive) {
      return;
    }
    const button = event.currentTarget;
    const retrying = signingOut;
    logoutRequestActive = true;
    signingOut = true;
    check++;
    conceal();
    clearIdentity();
    status.textContent = "Logging out…";
    retrySession.hidden = true;
    retryLogout.hidden = true;
    logoutError.textContent = "";
    logoutError.parentElement.hidden = true;
    dialogError.hidden = true;
    try {
      await sessionChecks;
      // A failed logout may have ended the session or left its CSRF token out of date.
      if (retrying && !(await readClientSession()).sharedViewSessionIsLoggedIn) {
        channel?.postMessage("signed-out");
        redirect(signIn + "?sharedLoginUserNotice=LOGGED_OUT");
        return;
      }
      await window.sharedLogoutUser(button, logoutError);
      if (!dialogError.hidden) {
        logoutError.textContent = dialogError.querySelector("p").textContent;
        logoutError.parentElement.hidden = false;
        conceal();
      }
      if (!logoutError.textContent) {
        channel?.postMessage("signed-out");
        return;
      }
    } catch (error) {
      if (handleAccessError(error)) {
        return;
      }
      logoutError.textContent = clientErrorMessage(error);
      logoutError.parentElement.hidden = false;
    } finally {
      logoutRequestActive = false;
    }
    if (logoutError.textContent) {
      status.textContent = "Logout could not be confirmed.";
      retryLogout.hidden = false;
    }
  }

  menu.addEventListener("click", () => setMenu(menu.getAttribute("aria-expanded") === "false"));
  portal.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && menu.getAttribute("aria-expanded") === "true" && !dialog.open) {
      setMenu(false);
      menu.focus();
    }
  });
  retrySession.addEventListener("click", verifySession);
  retryLogout.addEventListener("click", logout);
  document.getElementById("clientLogout").addEventListener("click", logout);
  document.getElementById("clientDialogLogout").addEventListener("click", logout);
  document.getElementById("clientExtendSession").addEventListener("click", window.sharedExtendSession);
  document.addEventListener("visibilitychange", () => {
    if (document.hidden) {
      check++;
      conceal();
    } else {
      verifySession();
    }
  });
  // shared-common reloads a restored back/forward page. Clear its private DOM before it can be cached.
  window.addEventListener("pagehide", () => {
    check++;
    conceal();
    clearIdentity();
  });
  channel?.addEventListener("message", (event) => {
    if (event.data === "signed-out") {
      check++;
      conceal();
      clearIdentity();
      verifySession();
    }
  });

  verifySession();
  return { handleAccessError, recheckSession: verifySession, currentIdentity: () => portal.hidden ? null : identity,
    isCurrent: (candidate) => candidate === identity };
}
