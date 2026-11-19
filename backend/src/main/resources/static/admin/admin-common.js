// Build the shared Admin frame, badges, flags, Actions menus and confirm windows.

// ===== SECTION 1: PAGE ELEMENTS =====
// Each page supplies its main content. The frame places it inside the shared layout.

// ===== SECTION 2: PAGE STATE =====
// The frame uses the current session for the top bar and test-mode menu.

// ===== SECTION 3: START OF PAGE =====
// Each page calls adminBuildPageFrame with its active menu entry.

// Listen during capture so scrolling a list table closes its open menu too.
window.addEventListener("scroll", adminBuildActionMenuClose, true);
window.addEventListener("resize", adminBuildActionMenuClose);

// ===== SECTION 4: adminBuildPageFrame =====

// What it does: Verify the session, then draw the Admin frame for that user.
// Input: The label of the active side menu entry.
// Output: The health record used to draw the frame, or null if the server's state could not be read.
async function adminBuildPageFrame(adminBuildPageFrameActivePageName) {
  // Confirm this page belongs to an active Admin who can use the system.
  let sharedViewSessionResponse;
  try {
    sharedViewSessionResponse = await sharedSendRequest("GET", "/api/shared/session");
  } catch (sharedViewSessionError) {
    if (sharedViewSessionError.status !== 401) {
      window.location.replace("/shared/shared-login.html");
    }
    return null;
  }
  if (!sharedViewSessionResponse.sharedViewSessionIsLoggedIn) {
    window.location.replace("/shared/shared-login.html");
    return null;
  }
  if (sharedViewSessionResponse.mustChangePassword) {
    window.location.replace("/shared/shared-change-password.html");
    return null;
  }
  if (sharedViewSessionResponse.role !== "ADMIN") {
    window.location.replace("/shared/shared-welcome.html?sharedShowWelcomeNotice=ACCESS_DENIED");
    return null;
  }

  // Use the local icon sprite and trusted frame markup. The page itself supplies the work area.
  document.body.insertAdjacentHTML("afterbegin", `
<svg class="shared-icon-sprite" aria-hidden="true">
  <symbol id="icon-dashboard" viewBox="0 0 20 20"><rect x="3" y="3" width="6" height="6" rx="1.5"/><rect x="11" y="3" width="6" height="6" rx="1.5"/><rect x="3" y="11" width="6" height="6" rx="1.5"/><rect x="11" y="11" width="6" height="6" rx="1.5"/></symbol>
  <symbol id="icon-users" viewBox="0 0 20 20"><circle cx="10" cy="7" r="3.2"/><path d="M3.5 17c.6-3.2 3.3-5 6.5-5s5.9 1.8 6.5 5"/></symbol>
  <symbol id="icon-products" viewBox="0 0 20 20"><path d="M3 6.5 10 3l7 3.5v7L10 17l-7-3.5z"/><path d="M3 6.5 10 10l7-3.5M10 10v7"/></symbol>
  <symbol id="icon-orders" viewBox="0 0 20 20"><rect x="4" y="3" width="12" height="14" rx="2"/><path d="M7 7.5h6M7 10.5h6M7 13.5h3.5"/></symbol>
  <symbol id="icon-warehouse" viewBox="0 0 20 20"><path d="M3 8.5 10 4l7 4.5V16H3z"/><path d="M7 16v-4.5h6V16"/></symbol>
  <symbol id="icon-delivery" viewBox="0 0 20 20"><path d="M2.5 5.5h9v7.5h-9z"/><path d="M11.5 8.5h3.2l2.8 3V13h-6z"/><circle cx="6" cy="14.6" r="1.5"/><circle cx="14.2" cy="14.6" r="1.5"/></symbol>
  <symbol id="icon-refunds" viewBox="0 0 20 20"><path d="M4 8h9a3.5 3.5 0 0 1 0 7H8"/><path d="M7 5 4 8l3 3"/></symbol>
  <symbol id="icon-logs" viewBox="0 0 20 20"><path d="M5 3.5h7l3 3v10H5z"/><path d="M12 3.5v3h3M7.5 10h5M7.5 13h5"/></symbol>
  <symbol id="icon-test-tools" viewBox="0 0 20 20"><path d="M8 3.5h4"/><path d="M8.5 3.5v4.3L4.7 14.5A1.7 1.7 0 0 0 6.2 17h7.6a1.7 1.7 0 0 0 1.5-2.5L11.5 7.8V3.5"/><path d="M6.6 11.5h6.8"/></symbol>
  <symbol id="icon-search" viewBox="0 0 20 20"><circle cx="9" cy="9" r="5"/><path d="m13 13 3.5 3.5"/></symbol>
  <symbol id="icon-plus" viewBox="0 0 20 20"><path d="M10 4.5v11M4.5 10h11"/></symbol>
  <symbol id="icon-chevron" viewBox="0 0 20 20"><path d="m5.5 8 4.5 4.5L14.5 8"/></symbol>
  <symbol id="icon-clock" viewBox="0 0 20 20"><circle cx="10" cy="10" r="6.5"/><path d="M10 6.5V10l2.5 1.5"/></symbol>
  <symbol id="icon-download" viewBox="0 0 20 20"><path d="M10 3.5v9M6.5 9.5 10 13l3.5-3.5M4 16h12"/></symbol>
  <symbol id="icon-check" viewBox="0 0 20 20"><circle cx="10" cy="10" r="6.5"/><path d="m7 10.2 2.1 2.1L13.2 8"/></symbol>
  <symbol id="icon-alert" viewBox="0 0 20 20"><path d="M10 3.6 17 16H3z"/><path d="M10 8.5v3.5"/><path d="M10 14.1v.1"/></symbol>
  <symbol id="icon-information" viewBox="0 0 20 20"><circle cx="10" cy="10" r="6.5"/><path d="M10 9.3v4"/><path d="M10 6.7v.1"/></symbol>
  <symbol id="icon-tick" viewBox="0 0 20 20"><path d="m5.5 10.5 3 3 6-6.5"/></symbol>
  <symbol id="icon-label-sample" viewBox="0 0 21 21"><path fill-rule="evenodd" d="M0 0h7v7h-7zM1 1v5h5v-5zM2 2h3v3h-3zM14 0h7v7h-7zM15 1v5h5v-5zM16 2h3v3h-3zM0 14h7v7h-7zM1 15v5h5v-5zM2 16h3v3h-3zM8 0h1v1h-1zM9 0h1v1h-1zM11 0h1v1h-1zM8 1h1v1h-1zM9 1h1v1h-1zM11 1h1v1h-1zM12 1h1v1h-1zM8 2h1v1h-1zM9 2h1v1h-1zM10 2h1v1h-1zM12 2h1v1h-1zM8 3h1v1h-1zM12 3h1v1h-1zM9 4h1v1h-1zM11 4h1v1h-1zM12 4h1v1h-1zM8 5h1v1h-1zM9 5h1v1h-1zM11 5h1v1h-1zM9 6h1v1h-1zM11 6h1v1h-1zM12 6h1v1h-1zM8 7h1v1h-1zM10 7h1v1h-1zM11 7h1v1h-1zM0 8h1v1h-1zM1 8h1v1h-1zM4 8h1v1h-1zM9 8h1v1h-1zM11 8h1v1h-1zM12 8h1v1h-1zM14 8h1v1h-1zM16 8h1v1h-1zM0 9h1v1h-1zM4 9h1v1h-1zM9 9h1v1h-1zM14 9h1v1h-1zM15 9h1v1h-1zM17 9h1v1h-1zM19 9h1v1h-1zM20 9h1v1h-1zM0 10h1v1h-1zM2 10h1v1h-1zM3 10h1v1h-1zM4 10h1v1h-1zM6 10h1v1h-1zM7 10h1v1h-1zM12 10h1v1h-1zM13 10h1v1h-1zM14 10h1v1h-1zM17 10h1v1h-1zM18 10h1v1h-1zM19 10h1v1h-1zM20 10h1v1h-1zM2 11h1v1h-1zM3 11h1v1h-1zM4 11h1v1h-1zM5 11h1v1h-1zM12 11h1v1h-1zM17 11h1v1h-1zM18 11h1v1h-1zM19 11h1v1h-1zM0 12h1v1h-1zM1 12h1v1h-1zM2 12h1v1h-1zM3 12h1v1h-1zM4 12h1v1h-1zM5 12h1v1h-1zM6 12h1v1h-1zM7 12h1v1h-1zM8 12h1v1h-1zM9 12h1v1h-1zM10 12h1v1h-1zM13 12h1v1h-1zM14 12h1v1h-1zM15 12h1v1h-1zM16 12h1v1h-1zM17 12h1v1h-1zM9 13h1v1h-1zM10 13h1v1h-1zM11 13h1v1h-1zM12 13h1v1h-1zM14 13h1v1h-1zM15 13h1v1h-1zM18 13h1v1h-1zM20 13h1v1h-1zM12 14h1v1h-1zM13 14h1v1h-1zM14 14h1v1h-1zM18 14h1v1h-1zM19 14h1v1h-1zM13 15h1v1h-1zM15 15h1v1h-1zM16 15h1v1h-1zM17 15h1v1h-1zM18 15h1v1h-1zM19 15h1v1h-1zM9 16h1v1h-1zM13 16h1v1h-1zM14 16h1v1h-1zM15 16h1v1h-1zM16 16h1v1h-1zM17 16h1v1h-1zM11 17h1v1h-1zM17 17h1v1h-1zM19 17h1v1h-1zM9 18h1v1h-1zM10 18h1v1h-1zM13 18h1v1h-1zM14 18h1v1h-1zM15 18h1v1h-1zM18 18h1v1h-1zM9 19h1v1h-1zM11 19h1v1h-1zM12 19h1v1h-1zM17 19h1v1h-1zM20 19h1v1h-1zM8 20h1v1h-1zM9 20h1v1h-1zM10 20h1v1h-1zM12 20h1v1h-1zM13 20h1v1h-1zM14 20h1v1h-1zM16 20h1v1h-1zM17 20h1v1h-1zM20 20h1v1h-1z"/></symbol>
</svg>
<a class="shared-skip-link" id="adminPageFrameSkipLink" href="#adminPageFrameMain">Skip to main content</a>
<div class="shared-page-frame">
  <nav class="shared-page-frame-side-menu" id="adminPageFrameSideMenu" aria-label="Admin pages">
    <div class="shared-page-frame-name">LMS Admin</div>
    <a class="shared-page-frame-menu-item" href="/admin/admin-dashboard.html">
      <svg class="shared-icon" aria-hidden="true"><use href="#icon-dashboard"/></svg>
      <span class="shared-page-frame-menu-text">Dashboard</span>
    </a>
    <div class="shared-page-frame-menu-label">Work to do</div>
    <a class="shared-page-frame-menu-item shared-page-frame-menu-item-disabled" aria-disabled="true">
      <svg class="shared-icon" aria-hidden="true"><use href="#icon-warehouse"/></svg>
      <span class="shared-page-frame-menu-text">Warehouse cases</span>
      <span class="shared-page-frame-menu-stage">Stage 6</span>
    </a>
    <a class="shared-page-frame-menu-item shared-page-frame-menu-item-disabled" aria-disabled="true">
      <svg class="shared-icon" aria-hidden="true"><use href="#icon-delivery"/></svg>
      <span class="shared-page-frame-menu-text">Delivery cases</span>
      <span class="shared-page-frame-menu-stage">Stage 7</span>
    </a>
    <a class="shared-page-frame-menu-item shared-page-frame-menu-item-disabled" aria-disabled="true">
      <svg class="shared-icon" aria-hidden="true"><use href="#icon-refunds"/></svg>
      <span class="shared-page-frame-menu-text">Refunds</span>
      <span class="shared-page-frame-menu-stage">Stage 8</span>
    </a>
    <div class="shared-page-frame-menu-label">Manage</div>
    <a class="shared-page-frame-menu-item" href="/admin/admin-users.html">
      <svg class="shared-icon" aria-hidden="true"><use href="#icon-users"/></svg>
      <span class="shared-page-frame-menu-text">Users</span>
    </a>
    <a class="shared-page-frame-menu-item shared-page-frame-menu-item-disabled" aria-disabled="true">
      <svg class="shared-icon" aria-hidden="true"><use href="#icon-products"/></svg>
      <span class="shared-page-frame-menu-text">Products</span>
      <span class="shared-page-frame-menu-stage">Stage 4</span>
    </a>
    <div class="shared-page-frame-menu-label">Look up</div>
    <a class="shared-page-frame-menu-item shared-page-frame-menu-item-disabled" aria-disabled="true">
      <svg class="shared-icon" aria-hidden="true"><use href="#icon-orders"/></svg>
      <span class="shared-page-frame-menu-text">Orders</span>
      <span class="shared-page-frame-menu-stage">Stage 5</span>
    </a>
    <a class="shared-page-frame-menu-item shared-page-frame-menu-item-disabled" aria-disabled="true">
      <svg class="shared-icon" aria-hidden="true"><use href="#icon-logs"/></svg>
      <span class="shared-page-frame-menu-text">Logs</span>
      <span class="shared-page-frame-menu-stage">Stage 3</span>
    </a>
  </nav>
  <header class="shared-page-frame-top-bar" id="adminPageFrameTopBar">
    <span class="shared-page-frame-spacer"></span>
    <span class="shared-page-frame-countdown" role="timer">
      <svg class="shared-icon" aria-hidden="true"><use href="#icon-clock"/></svg>
      <span class="shared-page-frame-countdown-words">Session ends in</span>
      <span id="sharedStartSessionCountdownText"></span>
    </span>
    <span class="shared-page-frame-user" id="adminPageFrameUsernameText"></span>
    <a class="shared-page-frame-top-link" id="adminPageFrameChangePasswordLink"
      href="/shared/shared-change-password.html">Change password</a>
    <button class="shared-button shared-button-small" id="adminPageFrameLogoutButton" type="button">Log out</button>
  </header>
</div>`);
  document.body.insertAdjacentHTML("beforeend", `
<div class="shared-message-area" id="sharedShowMessagePanel" aria-live="polite"></div>
<dialog class="shared-dialog" id="sharedStartSessionCountdownDialog"
  aria-labelledby="sharedStartSessionCountdownDialogTitle">
  <h2 class="shared-dialog-title" id="sharedStartSessionCountdownDialogTitle">Session ending soon</h2>
  <div class="shared-dialog-body">
    <p id="sharedStartSessionCountdownDialogText">Your session will end soon.</p>
    <div class="shared-inline-message shared-inline-message-error" aria-live="polite" hidden>
      <svg class="shared-icon" aria-hidden="true"><use href="#icon-alert"/></svg>
      <p></p>
    </div>
  </div>
  <div class="shared-dialog-footer">
    <button class="shared-button" type="button" id="sharedStartSessionCountdownLogoutButton">Log out</button>
    <button class="shared-button shared-button-primary" type="button" id="sharedExtendSessionButton">
      Stay logged in
    </button>
  </div>
</dialog>`);

  // Put the authorized user's name in the top bar and mount the page in the frame.
  document.getElementById("adminPageFrameUsernameText").textContent = sharedViewSessionResponse.username;
  const adminPageFrameMain = document.querySelector("main");
  adminPageFrameMain.classList.add("shared-page-frame-main");
  adminPageFrameMain.id = "adminPageFrameMain";
  adminPageFrameMain.tabIndex = -1;
  document.querySelector(".shared-page-frame").append(adminPageFrameMain);

  // TEMPORARY TEST CODE - remove before integration
  // Stage 0 and later expose the Testing group and flag only while the test profile is on.
  const adminPageFrameSideMenu = document.getElementById("adminPageFrameSideMenu");
  if (sharedViewSessionResponse.sharedViewSessionTestModeIsEnabled) {
    adminPageFrameSideMenu.insertAdjacentHTML("beforeend", `
    <div class="shared-page-frame-menu-label">Testing</div>
    <a class="shared-page-frame-menu-item" href="/admin/admin-temporary-test-tools.html">
      <svg class="shared-icon" aria-hidden="true"><use href="#icon-test-tools"/></svg>
      <span class="shared-page-frame-menu-text">Test tools</span>
    </a>`);
    const adminPageFrameTestModeBadge = adminBuildFlag("Test mode", "waiting");
    adminPageFrameTestModeBadge.id = "adminPageFrameTestModeBadge";
    document.getElementById("adminPageFrameTopBar").prepend(adminPageFrameTestModeBadge);
  }

  // Mark only the current live page. Unbuilt entries are skipped by the keyboard because they have no href.
  const adminBuildPageFrameMenuLinkList = adminPageFrameSideMenu.querySelectorAll("a[href]");
  for (const adminBuildPageFrameMenuLink of adminBuildPageFrameMenuLinkList) {
    const adminBuildPageFrameMenuText = adminBuildPageFrameMenuLink.querySelector(".shared-page-frame-menu-text");
    if (adminBuildPageFrameMenuText.textContent === adminBuildPageFrameActivePageName) {
      adminBuildPageFrameMenuLink.classList.add("shared-page-frame-menu-item-active");
      adminBuildPageFrameMenuLink.setAttribute("aria-current", "page");
      break;
    }
  }

  // Hook up the countdown controls and show the authorized page.
  const adminPageFrameLogoutButton = document.getElementById("adminPageFrameLogoutButton");
  const sharedStartSessionCountdownLogoutButton = document.getElementById("sharedStartSessionCountdownLogoutButton");

  // What it does: Log out from either the top bar or the session warning.
  // Input: The click event.
  // Output: The logout request and its result.
  const sharedLogoutUserAction = (sharedLogoutUserEvent) => {
    sharedLogoutUser(sharedLogoutUserEvent.currentTarget);
  };
  adminPageFrameLogoutButton.addEventListener("click", sharedLogoutUserAction);
  sharedStartSessionCountdownLogoutButton.addEventListener("click", sharedLogoutUserAction);
  document.getElementById("sharedExtendSessionButton").addEventListener("click", sharedExtendSession);
  adminPageFrameMain.hidden = false;
  sharedStartSessionCountdown(sharedViewSessionResponse.sharedViewSessionIdleTimeoutSeconds);

  // A healthy and a disconnected database both return health facts for the dashboard.
  let adminBuildPageFrameHealthResponse = null;
  try {
    adminBuildPageFrameHealthResponse = await sharedSendRequest("GET", "/api/shared/health");
  } catch (adminBuildPageFrameHealthError) {
    if (adminBuildPageFrameHealthError.status === 503
        && adminBuildPageFrameHealthError.sharedCheckSystemHealthDatabaseIsConnected === false) {
      adminBuildPageFrameHealthResponse = adminBuildPageFrameHealthError;
    }
  }
  if (typeof adminBuildPageFrameHealthResponse?.sharedCheckSystemHealthTestModeIsEnabled !== "boolean") {
    adminBuildPageFrameHealthResponse = null;
    sharedShowMessage("ERROR", "Could not read the system health.");
  }
  return adminBuildPageFrameHealthResponse;
}

// ===== SECTION 5: adminBuildStatusBadge =====

// What it does: Build a status badge with the words and colour family from the design note.
// Input: A stored status word or the known status text used on a page.
// Output: A span with plain text and the matching family. Unknown values use the Closed family.
function adminBuildStatusBadge(adminBuildStatusBadgeStatusWord) {
  // Use the dictionary of approved status families and default to Closed.
  const adminBuildStatusBadgeFamilyMap = {
    waiting: ["AWAITING_PAYMENT", "LOCKED", "PENDING", "REQUESTED", "WAITING_RESTOCK", "WAITING_FOR_RETURN",
      "WAITING_FOR_CLIENT"],
    warehouse: ["READY_FOR_PICKING", "PICKING", "READY_FOR_PICKUP", "READY_FOR_REDELIVERY", "IN_PROGRESS",
      "WAREHOUSE"],
    road: ["SHIPPED", "OUT_FOR_DELIVERY", "DELIVERY"],
    done: ["DELIVERED", "ACTIVE", "ACCEPTED", "REFUNDED", "RESOLVED", "PACKED", "APPROVED", "SUCCESS", "CONNECTED"],
    problem: ["DELIVERY_FAILED", "BANNED", "FAILED", "OPEN", "REJECTED", "FAILURE", "NEEDS_DECISION", "NOT_CONNECTED"]
  };
  const adminBuildStatusBadgeLookupWord = adminBuildStatusBadgeStatusWord.toUpperCase().replaceAll(" ", "_");
  let adminBuildStatusBadgeFamily = "closed";
  for (const [adminBuildStatusBadgeFamilyName, adminBuildStatusBadgeWordList]
    of Object.entries(adminBuildStatusBadgeFamilyMap)) {
    if (adminBuildStatusBadgeWordList.includes(adminBuildStatusBadgeLookupWord)) {
      adminBuildStatusBadgeFamily = adminBuildStatusBadgeFamilyName;
      break;
    }
  }

  // The label is always inserted as text, including for words outside the dictionary.
  const adminBuildStatusBadgeElement = document.createElement("span");
  adminBuildStatusBadgeElement.classList.add("shared-status-badge", "shared-status-badge-" + adminBuildStatusBadgeFamily);
  adminBuildStatusBadgeElement.textContent = sharedFormatStatusWord(adminBuildStatusBadgeStatusWord);
  return adminBuildStatusBadgeElement;
}

// ===== SECTION 6: adminBuildFlag =====

// What it does: Build a flag for an extra fact.
// Input: The flag's text and one of the design's six families.
// Output: A flag span.
function adminBuildFlag(adminBuildFlagText, adminBuildFlagFamily) {
  // Keep the text visible so colour is never the only sign of a flag.
  const adminBuildFlagElement = document.createElement("span");
  adminBuildFlagElement.classList.add("shared-flag", "shared-flag-" + adminBuildFlagFamily);
  adminBuildFlagElement.textContent = adminBuildFlagText;
  return adminBuildFlagElement;
}

// ===== SECTION 7: adminBuildActionMenu =====

// What it does: Build a named native Actions popover for one table row.
// Input: The row ID and name, and each possible action's label, ID, danger state and callback.
// Output: A detached menu with keyboard-accessible entries and native light dismissal.
function adminBuildActionMenu(adminBuildActionMenuRowID, adminBuildActionMenuRowName, adminBuildActionMenuEntryList) {
  // Link the Actions button to its own popover in the top layer.
  const adminBuildActionMenuElement = document.createElement("span");
  adminBuildActionMenuElement.className = "shared-menu";
  const adminBuildActionMenuButton = document.createElement("button");
  adminBuildActionMenuButton.type = "button";
  adminBuildActionMenuButton.id = "adminBuildActionMenuButton-" + adminBuildActionMenuRowID;
  adminBuildActionMenuButton.className = "shared-button shared-button-small";
  adminBuildActionMenuButton.setAttribute("aria-label", "Actions for " + adminBuildActionMenuRowName);
  adminBuildActionMenuButton.setAttribute("popovertarget", "adminBuildActionMenuList-" + adminBuildActionMenuRowID);
  adminBuildActionMenuButton.innerHTML = 'Actions <svg class="shared-icon" aria-hidden="true">'
    + '<use href="#icon-chevron"/></svg>';

  // Keep dangerous options below a single divider.
  const adminBuildActionMenuList = document.createElement("div");
  adminBuildActionMenuList.id = "adminBuildActionMenuList-" + adminBuildActionMenuRowID;
  adminBuildActionMenuList.className = "shared-menu-list";
  adminBuildActionMenuList.setAttribute("popover", "auto");
  for (const adminBuildActionMenuEntry of adminBuildActionMenuEntryList.filter(
    // What it does: Select safe entries. Input: One entry. Output: Whether it is safe.
    (adminBuildActionMenuEntry) => !adminBuildActionMenuEntry.adminBuildActionMenuEntryIsDangerous
  )) {
    adminBuildActionMenuAddEntry(adminBuildActionMenuList, adminBuildActionMenuButton, adminBuildActionMenuEntry);
  }
  const adminBuildActionMenuDangerousEntryList = adminBuildActionMenuEntryList.filter(
    // What it does: Select dangerous entries. Input: One entry. Output: Whether it is dangerous.
    (adminBuildActionMenuEntry) => adminBuildActionMenuEntry.adminBuildActionMenuEntryIsDangerous
  );
  if (adminBuildActionMenuDangerousEntryList.length > 0) {
    const adminBuildActionMenuDivider = document.createElement("div");
    adminBuildActionMenuDivider.className = "shared-menu-divider";
    adminBuildActionMenuDivider.setAttribute("role", "separator");
    adminBuildActionMenuList.append(adminBuildActionMenuDivider);
    for (const adminBuildActionMenuEntry of adminBuildActionMenuDangerousEntryList) {
      adminBuildActionMenuAddEntry(adminBuildActionMenuList, adminBuildActionMenuButton, adminBuildActionMenuEntry);
    }
  }

  // What it does: Position the popover once it opens.
  // Input: The native popover toggle event.
  // Output: The open popover sits beside its own row.
  adminBuildActionMenuList.addEventListener("toggle", (adminBuildActionMenuEvent) => {
    if (adminBuildActionMenuEvent.newState === "open") {
      sharedPlaceActionMenu(adminBuildActionMenuList);
    }
  });
  adminBuildActionMenuElement.append(adminBuildActionMenuButton, adminBuildActionMenuList);
  return adminBuildActionMenuElement;
}

// ===== SECTION 8: adminOpenReasonDialog =====

// What it does: Ask the Admin to confirm an action with an optional or required field.
// Input: Title, explanation, field mode, action and closing labels, danger state and optional fact pairs.
// Output: A promise for the field text, an empty string on a fieldless confirmation or null when dismissed.
function adminOpenReasonDialog(adminOpenReasonDialogTitle, adminOpenReasonDialogText, adminOpenReasonDialogFieldMode,
  adminOpenReasonDialogConfirmButtonText, adminOpenReasonDialogCloseButtonText, adminOpenReasonDialogIsDangerous,
  adminOpenReasonDialogFactList = []) {
  // Reuse the same window for all confirmation actions.
  const adminOpenReasonDialogElement = adminOpenReasonDialogFindElement();
  const adminOpenReasonDialogTitleText = document.getElementById("adminOpenReasonDialogTitleText");
  const adminOpenReasonDialogBodyText = document.getElementById("adminOpenReasonDialogBodyText");
  const adminOpenReasonDialogFacts = document.getElementById("adminOpenReasonDialogFactList");
  const adminOpenReasonDialogReasonInput = document.getElementById("adminOpenReasonDialogReasonInput");
  const adminOpenReasonDialogNoteInput = document.getElementById("adminOpenReasonDialogNoteInput");
  const adminOpenReasonDialogCancelButton = document.getElementById("adminOpenReasonDialogCancelButton");
  const adminOpenReasonDialogConfirmButton = document.getElementById("adminOpenReasonDialogConfirmButton");

  // Put any additional lines in the note directly after the explanation, above the fields.
  adminOpenReasonDialogTitleText.textContent = adminOpenReasonDialogTitle;
  const [adminOpenReasonDialogFirstLine, ...adminOpenReasonDialogNoteList] = adminOpenReasonDialogText.split("\n");
  adminOpenReasonDialogBodyText.textContent = adminOpenReasonDialogFirstLine;
  const adminOpenReasonDialogBodyNote = adminOpenReasonDialogBodyText.nextElementSibling;
  adminOpenReasonDialogBodyNote.textContent = adminOpenReasonDialogNoteList.join(" ");
  adminOpenReasonDialogBodyNote.hidden = adminOpenReasonDialogNoteList.length === 0;
  adminOpenReasonDialogFillFacts(adminOpenReasonDialogFacts, adminOpenReasonDialogFactList);

  // Reset both fields and select the one that belongs to this action.
  adminOpenReasonDialogReasonInput.value = "";
  adminOpenReasonDialogNoteInput.value = "";
  adminOpenReasonDialogReasonInput.closest("label").hidden = adminOpenReasonDialogFieldMode !== "REQUIRED_REASON";
  adminOpenReasonDialogNoteInput.closest("label").hidden = adminOpenReasonDialogFieldMode !== "OPTIONAL_NOTE";
  adminOpenReasonDialogCancelButton.textContent = adminOpenReasonDialogCloseButtonText;
  adminOpenReasonDialogConfirmButton.textContent = adminOpenReasonDialogConfirmButtonText;
  adminOpenReasonDialogConfirmButton.className = adminOpenReasonDialogIsDangerous
    ? "shared-button shared-button-danger-filled" : "shared-button shared-button-primary";
  adminOpenReasonDialogConfirmButton.disabled = adminOpenReasonDialogFieldMode === "REQUIRED_REASON";

  // Keep the validation and dismissal listeners for just this opening.
  const adminOpenReasonDialogInput = adminOpenReasonDialogFieldMode === "OPTIONAL_NOTE"
    ? adminOpenReasonDialogNoteInput : adminOpenReasonDialogReasonInput;
  return adminOpenReasonDialogWait(adminOpenReasonDialogElement, adminOpenReasonDialogInput,
    adminOpenReasonDialogFieldMode, adminOpenReasonDialogCancelButton, adminOpenReasonDialogConfirmButton);
}

// ===== SECTION 9: HELPER FUNCTIONS =====

// What it does: Close row menus on a page scroll or resize.
// Input: The scroll or resize event.
// Output: Menus close unless the user is scrolling within the menu itself.
function adminBuildActionMenuClose(adminBuildActionMenuEvent) {
  // A tall menu has its own scroll box. Leave that box usable.
  for (const adminBuildActionMenuList of document.querySelectorAll(".shared-menu-list:popover-open")) {
    if (adminBuildActionMenuEvent.target !== adminBuildActionMenuList) {
      adminBuildActionMenuList.hidePopover();
    }
  }
}

// What it does: Add a row action to a native popover.
// Input: The popover, its opening button and the entry's details.
// Output: An entry that closes the menu before running the action.
function adminBuildActionMenuAddEntry(adminBuildActionMenuList, adminBuildActionMenuButton, adminBuildActionMenuEntry) {
  // Use ordinary buttons so the browser handles Tab and distinguishes danger with words and colour.
  const adminBuildActionMenuItem = document.createElement("button");
  adminBuildActionMenuItem.type = "button";
  adminBuildActionMenuItem.id = adminBuildActionMenuEntry.adminBuildActionMenuEntryID;
  adminBuildActionMenuItem.className = "shared-menu-item";
  if (adminBuildActionMenuEntry.adminBuildActionMenuEntryIsDangerous) {
    adminBuildActionMenuItem.classList.add("shared-menu-item-danger");
  }
  adminBuildActionMenuItem.textContent = adminBuildActionMenuEntry.adminBuildActionMenuEntryText;

  // What it does: Dismiss the menu and run the chosen action for its row.
  // Input: None. Uses the row's menu, opening button and entry.
  // Output: Focus returns to Actions until the action opens the next window.
  adminBuildActionMenuItem.addEventListener("click", () => {
    adminBuildActionMenuList.hidePopover();
    adminBuildActionMenuButton.focus({ preventScroll: true });
    adminBuildActionMenuEntry.adminBuildActionMenuEntryAction();
  });
  adminBuildActionMenuList.append(adminBuildActionMenuItem);
}

// What it does: Find or create the shared confirmation window on the current page.
// Input: None.
// Output: One dialog with the approved fields, facts and buttons.
function adminOpenReasonDialogFindElement() {
  // Static markup supplies the structure. User data is filled as text when it opens.
  let adminOpenReasonDialogElement = document.getElementById("adminOpenReasonDialog");
  if (!adminOpenReasonDialogElement) {
    document.body.insertAdjacentHTML("beforeend", `
<dialog class="shared-dialog" id="adminOpenReasonDialog" aria-labelledby="adminOpenReasonDialogTitleText">
  <h2 class="shared-dialog-title" id="adminOpenReasonDialogTitleText"></h2>
  <div class="shared-dialog-body">
    <p id="adminOpenReasonDialogBodyText"></p>
    <p class="shared-note" hidden></p>
    <dl class="shared-decision-facts" id="adminOpenReasonDialogFactList" hidden></dl>
    <label class="shared-field" hidden>
      <span class="shared-field-label">Reason</span>
      <textarea class="shared-textarea" id="adminOpenReasonDialogReasonInput" data-reason></textarea>
      <span class="shared-field-help">At least 5 characters.</span>
    </label>
    <label class="shared-field" hidden>
      <span class="shared-field-label">Note<span class="shared-field-optional"> (optional)</span></span>
      <textarea class="shared-textarea" id="adminOpenReasonDialogNoteInput"></textarea>
    </label>
  </div>
  <div class="shared-dialog-footer">
    <button type="button" class="shared-button" id="adminOpenReasonDialogCancelButton" data-close-dialog></button>
    <button type="button" id="adminOpenReasonDialogConfirmButton" data-confirm></button>
  </div>
</dialog>`);
    adminOpenReasonDialogElement = document.getElementById("adminOpenReasonDialog");
  }
  return adminOpenReasonDialogElement;
}

// What it does: Fill the fact list above the action field.
// Input: The list element and its label and value pairs.
// Output: The empty list is hidden. Each supplied fact appears as its own row.
function adminOpenReasonDialogFillFacts(adminOpenReasonDialogFacts, adminOpenReasonDialogFactList) {
  // Replace facts from the previous opening rather than carrying them forward.
  adminOpenReasonDialogFacts.replaceChildren();
  for (const [adminOpenReasonDialogLabel, adminOpenReasonDialogValue] of adminOpenReasonDialogFactList) {
    const adminOpenReasonDialogFact = document.createElement("div");
    adminOpenReasonDialogFact.className = "shared-fact";
    const adminOpenReasonDialogTerm = document.createElement("dt");
    adminOpenReasonDialogTerm.className = "shared-fact-label";
    adminOpenReasonDialogTerm.textContent = adminOpenReasonDialogLabel;
    const adminOpenReasonDialogDescription = document.createElement("dd");
    adminOpenReasonDialogDescription.className = "shared-fact-value";
    adminOpenReasonDialogDescription.append(adminOpenReasonDialogValue);
    adminOpenReasonDialogFact.append(adminOpenReasonDialogTerm, adminOpenReasonDialogDescription);
    adminOpenReasonDialogFacts.append(adminOpenReasonDialogFact);
  }
  adminOpenReasonDialogFacts.hidden = adminOpenReasonDialogFactList.length === 0;
}

// What it does: Wait for a confirmation, a close button press or Escape.
// Input: A configured dialog, the field mode and the input and buttons for this opening.
// Output: Resolves once with the trimmed field, an empty string or null on dismissal.
function adminOpenReasonDialogWait(adminOpenReasonDialogElement, adminOpenReasonDialogInput,
  adminOpenReasonDialogFieldMode, adminOpenReasonDialogCancelButton, adminOpenReasonDialogConfirmButton) {
  // What it does: Wire the window for one opening.
  // Input: The promise resolve function.
  // Output: Resolves when a user accepts or dismisses the active confirmation.
  return new Promise((adminOpenReasonDialogResolve) => {
    const adminOpenReasonDialogListeners = new AbortController();
    const adminOpenReasonDialogListenerOptions = { signal: adminOpenReasonDialogListeners.signal };

    // What it does: Recheck the reason length after typing.
    // Input: None. Reads the active reason field and mode.
    // Output: Enables confirmation only when a required reason is long enough.
    adminOpenReasonDialogInput.addEventListener("input", () => {
      adminOpenReasonDialogConfirmButton.disabled = adminOpenReasonDialogFieldMode === "REQUIRED_REASON"
        && Array.from(adminOpenReasonDialogInput.value.trim()).length < 5;
    }, adminOpenReasonDialogListenerOptions);

    // What it does: Accept the confirmed field and close before sending the change.
    // Input: None. Reads the field and mode from this opening.
    // Output: Resolves with the field text or an empty string.
    adminOpenReasonDialogConfirmButton.addEventListener("click", () => {
      adminOpenReasonDialogConfirmButton.disabled = true;
      const adminOpenReasonDialogAnswer = adminOpenReasonDialogFieldMode === "NONE"
        ? "" : adminOpenReasonDialogInput.value.trim();
      adminOpenReasonDialogListeners.abort();
      sharedCloseDialog(adminOpenReasonDialogElement);
      adminOpenReasonDialogResolve(adminOpenReasonDialogAnswer);
    }, adminOpenReasonDialogListenerOptions);

    // What it does: Dismiss the confirmation from its closing control.
    // Input: None. Uses the open dialog.
    // Output: Closes the dialog, which triggers the dismissal result.
    adminOpenReasonDialogCancelButton.addEventListener("click", () => {
      sharedCloseDialog(adminOpenReasonDialogElement);
    }, adminOpenReasonDialogListenerOptions);

    // What it does: End a dismissed opening, including when Escape closed it.
    // Input: None. Uses this opening's listeners and result.
    // Output: Removes the listeners and resolves to null.
    adminOpenReasonDialogElement.addEventListener("close", () => {
      adminOpenReasonDialogListeners.abort();
      adminOpenReasonDialogResolve(null);
    }, adminOpenReasonDialogListenerOptions);
    sharedOpenDialog(adminOpenReasonDialogElement);
  });
}
