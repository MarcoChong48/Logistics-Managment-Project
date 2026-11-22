// Show the live dashboard and link each number to its filtered list.

// ===== SECTION 1: PAGE ELEMENTS =====
const adminViewDashboardRefreshButton = document.getElementById("adminViewDashboardRefreshButton");
const adminViewDashboardLastLoadedTimeText = document.getElementById("adminViewDashboardLastLoadedTimeText");
const adminViewDashboardActiveOrderCountText = document.getElementById("adminViewDashboardActiveOrderCountText");
const adminViewDashboardOrderStatusCountList = document.getElementById("adminViewDashboardOrderStatusCountList");
const adminViewDashboardLowStockProductCountText =
  document.getElementById("adminViewDashboardLowStockProductCountText");
const adminViewDashboardLowStockProductList = document.getElementById("adminViewDashboardLowStockProductList");
const adminViewDashboardLowStockLink = document.getElementById("adminViewDashboardLowStockLink");
const adminViewDashboardOpenCaseTotalCountText = document.getElementById("adminViewDashboardOpenCaseTotalCountText");
const sharedCheckSystemHealthDatabaseText = document.getElementById("sharedCheckSystemHealthDatabaseText");
const sharedCheckSystemHealthTableCountText = document.getElementById("sharedCheckSystemHealthTableCountText");
const sharedCheckSystemHealthServerTimeText = document.getElementById("sharedCheckSystemHealthServerTimeText");

// ===== SECTION 2: PAGE STATE =====
// The Refresh button stays disabled while a load is in progress. The page uses no refresh timer.

// ===== SECTION 3: START OF PAGE =====

// What it does: Reload the dashboard when the Admin presses Refresh.
// Input: None.
// Output: The latest counts, health and load time.
adminViewDashboardRefreshButton.addEventListener("click", () => adminViewDashboard(true));

// What it does: Draw the initial health card and read the dashboard once the frame is ready.
// Input: The health facts already read for the frame.
// Output: The live dashboard on an authorized page.
adminBuildPageFrame("Dashboard").then((sharedCheckSystemHealthResponse) => {
  if (!document.querySelector("main").hidden) {
    adminShowDashboardHealth(sharedCheckSystemHealthResponse);
    adminViewDashboard();
  }
});

// ===== SECTION 4: adminViewDashboard =====

// What it does: Read the dashboard numbers and update the time only after a successful load.
// Input: Whether the Admin pressed Refresh for another load.
// Output: The current numbers and their links, or a corner error with the previous numbers left in place.
async function adminViewDashboard(adminViewDashboardIsRefresh = false) {
  adminViewDashboardRefreshButton.disabled = true;
  try {
    // The dashboard counts are read together so the groups and their totals agree.
    const adminViewDashboardResponse = await sharedSendRequest("GET", "/api/admin/dashboard");
    adminShowDashboardCounts(adminViewDashboardResponse);
    const adminViewDashboardLastLoadedTime = new Date().toISOString();
    adminViewDashboardLastLoadedTimeText.dateTime = adminViewDashboardLastLoadedTime;
    adminViewDashboardLastLoadedTimeText.textContent = sharedFormatTime(adminViewDashboardLastLoadedTime);
  } catch (adminViewDashboardError) {
    sharedShowMessage("ERROR", adminViewDashboardError.message);
  } finally {
    // The initial frame already loaded health and the menu. Refresh reads both again.
    if (adminViewDashboardIsRefresh) {
      const [adminViewDashboardHealthResponse] = await Promise.all([sharedCheckSystemHealth(), adminViewMenuCounter()]);
      adminShowDashboardHealth(adminViewDashboardHealthResponse);
    }
    adminViewDashboardRefreshButton.disabled = false;
  }
}

// ===== SECTION 5: HELPER FUNCTIONS =====

// What it does: Fill the number boxes with the saved counts and their matching links.
// Input: The dashboard response.
// Output: The work counts, ten order statuses and the returned low-stock products.
function adminShowDashboardCounts(adminShowDashboardCountsResponse) {
  adminViewDashboardActiveOrderCountText.textContent =
    adminShowDashboardCountsResponse.adminViewDashboardActiveOrderCount;
  adminViewDashboardLowStockProductCountText.textContent =
    adminShowDashboardCountsResponse.adminViewDashboardLowStockProductCount;
  adminViewDashboardOpenCaseTotalCountText.textContent =
    adminShowDashboardCountsResponse.adminViewDashboardOpenCaseTotalCount;

  // Use the same class for zero as the design's other count lines.
  const adminShowDashboardCountsOpenCaseList = [
    ["adminViewDashboardOpenShortageCaseLink",
      adminShowDashboardCountsResponse.adminViewDashboardOpenShortageCaseCount],
    ["adminViewDashboardWaitingShortageCaseLink",
      adminShowDashboardCountsResponse.adminViewDashboardWaitingShortageCaseCount],
    ["adminViewDashboardPendingClientInstructionLink",
      adminShowDashboardCountsResponse.adminViewDashboardPendingClientInstructionCount],
    ["adminViewDashboardWaitingClientCaseLink",
      adminShowDashboardCountsResponse.adminViewDashboardWaitingClientCaseCount],
    ["adminViewDashboardFlaggedDeliveryCaseLink",
      adminShowDashboardCountsResponse.adminViewDashboardFlaggedDeliveryCaseCount],
    ["adminViewDashboardUnresolvedRefundLink",
      adminShowDashboardCountsResponse.adminViewDashboardUnresolvedRefundCount],
    ["adminViewDashboardLockedUserLink", adminShowDashboardCountsResponse.adminViewDashboardLockedUserCount]
  ];
  for (const [adminShowDashboardCountsLinkID, adminShowDashboardCountsCount] of adminShowDashboardCountsOpenCaseList) {
    const adminShowDashboardCountsText = document.getElementById(adminShowDashboardCountsLinkID)
      .querySelector(".shared-number-line-count");
    adminShowDashboardCountsText.textContent = adminShowDashboardCountsCount;
    adminShowDashboardCountsText.classList.toggle("shared-number-line-count-zero", adminShowDashboardCountsCount === 0);
  }

  // Keep the two closed statuses visible in their own group. Every status is a filter link.
  adminViewDashboardOrderStatusCountList.replaceChildren();
  for (const { orderStatus, adminViewDashboardOrderCount }
    of adminShowDashboardCountsResponse.adminViewDashboardOrderStatusCountList) {
    if (orderStatus === "DELIVERED") {
      const adminShowDashboardCountsClosedGroupText = document.createElement("p");
      adminShowDashboardCountsClosedGroupText.className = "shared-number-box-group";
      adminShowDashboardCountsClosedGroupText.textContent = "Not counted as active";
      adminViewDashboardOrderStatusCountList.append(adminShowDashboardCountsClosedGroupText);
    }
    const adminShowDashboardCountsOrderWebAddress = "admin-orders.html?adminSearchOrderOrderStatus="
      + encodeURIComponent(orderStatus);
    adminViewDashboardOrderStatusCountList.append(adminBuildDashboardNumberLine(adminShowDashboardCountsOrderWebAddress,
      [adminBuildStatusBadge(orderStatus)], adminViewDashboardOrderCount));
  }

  // A product with no available stock is urgent, so its zero is not muted.
  adminViewDashboardLowStockProductList.replaceChildren();
  for (const { SKU, name, lowStockThreshold, adminViewDashboardLowStockProductAvailableStock }
    of adminShowDashboardCountsResponse.adminViewDashboardLowStockProductList) {
    const adminShowDashboardCountsSKUText = document.createElement("span");
    adminShowDashboardCountsSKUText.className = "shared-strong";
    adminShowDashboardCountsSKUText.textContent = SKU;
    const adminShowDashboardCountsThresholdText = document.createElement("span");
    adminShowDashboardCountsThresholdText.className = "shared-cell-sub";
    adminShowDashboardCountsThresholdText.textContent = "Low below " + lowStockThreshold;
    adminViewDashboardLowStockProductList.append(adminBuildDashboardNumberLine(adminViewDashboardLowStockLink.href,
      [adminShowDashboardCountsSKUText, " " + name, adminShowDashboardCountsThresholdText],
      adminViewDashboardLowStockProductAvailableStock, true));
  }
}

// What it does: Build a number line using the shared dashboard layout.
// Input: The list URL, name content, count and whether the count represents stock.
// Output: A filter link with the name and count in their own columns.
function adminBuildDashboardNumberLine(adminBuildDashboardNumberLineWebAddress, adminBuildDashboardNumberLineNameList,
  adminBuildDashboardNumberLineCount, adminBuildDashboardNumberLineIsStock = false) {
  // Insert names as text or as elements already built by the page.
  const adminBuildDashboardNumberLineLink = document.createElement("a");
  adminBuildDashboardNumberLineLink.className = "shared-number-line";
  adminBuildDashboardNumberLineLink.href = adminBuildDashboardNumberLineWebAddress;
  const adminBuildDashboardNumberLineNameText = document.createElement("span");
  adminBuildDashboardNumberLineNameText.className = "shared-number-line-name";
  adminBuildDashboardNumberLineNameText.append(...adminBuildDashboardNumberLineNameList);

  // Give zero counts their muted style except when they show available stock.
  const adminBuildDashboardNumberLineCountText = document.createElement("span");
  adminBuildDashboardNumberLineCountText.className = "shared-number-line-count";
  adminBuildDashboardNumberLineCountText.classList.toggle("shared-number-line-count-zero",
    adminBuildDashboardNumberLineCount === 0 && !adminBuildDashboardNumberLineIsStock);
  adminBuildDashboardNumberLineCountText.textContent = adminBuildDashboardNumberLineCount;
  adminBuildDashboardNumberLineLink.append(
    adminBuildDashboardNumberLineNameText, adminBuildDashboardNumberLineCountText);
  return adminBuildDashboardNumberLineLink;
}

// What it does: Keep the existing System health card in sync with the latest health response.
// Input: The shared health response, or null if the request was unavailable.
// Output: The connection badge, table count and server time in the browser's local time.
function adminShowDashboardHealth(adminShowDashboardHealthResponse) {
  if (adminShowDashboardHealthResponse === null) {
    return;
  }

  // A disconnected database still returns a health record and a server time.
  const adminShowDashboardHealthStatus = adminShowDashboardHealthResponse.sharedCheckSystemHealthDatabaseIsConnected
    ? "CONNECTED" : "NOT_CONNECTED";
  sharedCheckSystemHealthDatabaseText.replaceChildren(adminBuildStatusBadge(adminShowDashboardHealthStatus));
  sharedCheckSystemHealthTableCountText.textContent =
    adminShowDashboardHealthResponse.sharedCheckSystemHealthTableCount;
  const adminShowDashboardHealthServerTime = adminShowDashboardHealthResponse.sharedCheckSystemHealthServerTime;
  sharedCheckSystemHealthServerTimeText.dateTime = adminShowDashboardHealthServerTime;
  sharedCheckSystemHealthServerTimeText.textContent = sharedFormatTime(adminShowDashboardHealthServerTime);
}
