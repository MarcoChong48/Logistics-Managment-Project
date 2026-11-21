// Read the three logs and the cleanup summary, with one filter and page state per tab.

// ===== SECTION 1: PAGE ELEMENTS =====
const adminShowLogTabAuditButton = document.getElementById("adminShowLogTabAuditButton");
const adminShowLogTabSecurityButton = document.getElementById("adminShowLogTabSecurityButton");
const adminShowLogTabPickingScanButton = document.getElementById("adminShowLogTabPickingScanButton");
const adminShowLogTabButtonList = adminShowLogTabAuditButton.parentElement;
const adminViewLogCleanupStatusLastRunTimeText = document.getElementById("adminViewLogCleanupStatusLastRunTimeText");
const adminViewLogCleanupStatusCountText = document.getElementById("adminViewLogCleanupStatusCountText");
const adminViewLogCleanupStatusKeepRuleText = document.getElementById("adminViewLogCleanupStatusKeepRuleText");

// ===== SECTION 2: PAGE STATE =====
const adminShowLogTabList = [
  adminShowLogTabReadPanel("AUDIT", adminShowLogTabAuditButton, "adminSearchAuditLog", "audit", adminSearchAuditLog),
  adminShowLogTabReadPanel("SECURITY", adminShowLogTabSecurityButton, "adminSearchSecurityLog", "security",
    adminSearchSecurityLog),
  adminShowLogTabReadPanel("PICKING_SCAN", adminShowLogTabPickingScanButton, "adminSearchPickingScanHistory",
    "picking-scan", adminSearchPickingScanHistory)
];
let adminShowLogTabCurrent;

// ===== SECTION 3: START OF PAGE =====

// What it does: Open the authorized Logs frame and restore the chosen tab, filters and page.
// Input: None. Reads list state from the page address.
// Output: The restored list and latest cleanup status.
adminBuildPageFrame("Logs").then(() => {
  if (document.querySelector("main").hidden) {
    return;
  }
  for (const adminShowLogTabEntry of adminShowLogTabList) {
    adminShowLogTabRestore(adminShowLogTabEntry);
    adminShowLogTabListen(adminShowLogTabEntry);
  }
  sharedWatchTableScroll();

  const adminShowLogTabRestoredName = sharedReadPageParameter("adminShowLogTabName");
  // What it does: Find the tab named in the address. Input: One tab. Output: Whether its name matches.
  const adminShowLogTabRestored = adminShowLogTabList.find((adminShowLogTabEntry) => {
    return adminShowLogTabEntry.adminShowLogTabName === adminShowLogTabRestoredName;
  });
  adminShowLogTab(adminShowLogTabRestored ?? adminShowLogTabList[0]);
  adminShowLogTabButtonList.classList.add("shared-tab-list-moving");
  window.addEventListener("resize", adminShowLogTabPlaceBar);
  adminViewLogCleanupStatus();
});

// ===== SECTION 4: adminShowLogTab =====

// What it does: Activate a log tab and restore its own list state.
// Input: The tab entry to show.
// Output: Only that panel is visible with its tab in the keyboard order.
function adminShowLogTab(adminShowLogTabSelected) {
  adminShowLogTabCurrent = adminShowLogTabSelected;

  // Keep the visible panel, active class and accessibility state in agreement.
  for (const adminShowLogTabEntry of adminShowLogTabList) {
    const adminShowLogTabIsSelected = adminShowLogTabEntry === adminShowLogTabSelected;
    const adminShowLogTabButton = adminShowLogTabEntry.adminShowLogTabButton;
    adminShowLogTabButton.classList.toggle("shared-tab-active", adminShowLogTabIsSelected);
    adminShowLogTabButton.setAttribute("aria-selected", String(adminShowLogTabIsSelected));
    adminShowLogTabButton.tabIndex = adminShowLogTabIsSelected ? 0 : -1;
    adminShowLogTabEntry.adminShowLogTabPanel.hidden = !adminShowLogTabIsSelected;
  }
  adminShowLogTabPlaceBar();
  adminShowLogTabSearch(adminShowLogTabSelected, adminShowLogTabSelected.adminShowLogTabPageNumber);
}

// ===== SECTION 5: adminSearchAuditLog =====

// What it does: Draw an audit row and keep the record's raw masked values as text.
// Input: An audit search result row.
// Output: One table row with the values on separate lines and no action controls.
function adminSearchAuditLog(adminSearchAuditLogRow) {
  const adminSearchAuditLogValues = document.createDocumentFragment();
  for (const [adminSearchAuditLogLabel, adminSearchAuditLogValue] of [
    ["Old", adminSearchAuditLogRow.oldValue], ["New", adminSearchAuditLogRow.newValue]
  ]) {
    const adminSearchAuditLogLine = document.createElement("span");
    adminSearchAuditLogLine.className = "shared-value-line";
    adminSearchAuditLogLine.append(adminShowLogTabBuildText("shared-cell-note", adminSearchAuditLogLabel),
      adminShowLogTabBuildText("shared-fixed-text", adminSearchAuditLogValue));
    adminSearchAuditLogValues.append(adminSearchAuditLogLine);
  }

  // Use plain words for the action and type. Leave the target ID exactly as it was stored.
  const adminSearchAuditLogTarget = [
    sharedFormatStatusWord(adminSearchAuditLogRow.targetType), adminSearchAuditLogRow.targetID
  ].filter(Boolean).join(" ");
  return adminShowLogTabBuildRow(adminSearchAuditLogRow.timestamp, [
    [adminSearchAuditLogRow.adminSearchAuditLogActorUsername],
    [sharedFormatStatusWord(adminSearchAuditLogRow.action), "shared-table-wrap"],
    [adminSearchAuditLogTarget], [adminSearchAuditLogValues, "shared-table-wrap"]
  ]);
}

// ===== SECTION 6: adminSearchSecurityLog =====

// What it does: Draw a security event with its result and request details.
// Input: A security search result row.
// Output: One table row, with device text shown below details when present.
function adminSearchSecurityLog(adminSearchSecurityLogRow) {
  const adminSearchSecurityLogDetails = document.createDocumentFragment();
  adminSearchSecurityLogDetails.append(adminShowLogTabBuildText("shared-cell-main", adminSearchSecurityLogRow.details));
  if (adminSearchSecurityLogRow.deviceInfo) {
    adminSearchSecurityLogDetails.append(
      adminShowLogTabBuildText("shared-cell-sub", adminSearchSecurityLogRow.deviceInfo)
    );
  }
  return adminShowLogTabBuildRow(adminSearchSecurityLogRow.timestamp, [
    [adminSearchSecurityLogRow.adminSearchSecurityLogUsername],
    [sharedFormatStatusWord(adminSearchSecurityLogRow.eventType)],
    [adminBuildStatusBadge(adminSearchSecurityLogRow.result)],
    [adminSearchSecurityLogRow.ipAddress, "shared-table-time"],
    [adminSearchSecurityLogDetails, "shared-table-wrap"]
  ]);
}

// ===== SECTION 7: adminSearchPickingScanHistory =====

// What it does: Draw a picking scan with the server's masked text and its original scan when reversed.
// Input: A picking scan search result row.
// Output: One table row with the scan's result, reason and any reversal reference.
function adminSearchPickingScanHistory(adminSearchPickingScanHistoryRow) {
  const adminSearchPickingScanHistoryReason = document.createDocumentFragment();
  if (adminSearchPickingScanHistoryRow.reason) {
    adminSearchPickingScanHistoryReason.append(
      adminShowLogTabBuildText("shared-cell-main", adminSearchPickingScanHistoryRow.reason)
    );
  }
  if (adminSearchPickingScanHistoryRow.originalScanID != null) {
    adminSearchPickingScanHistoryReason.append(
      adminShowLogTabBuildText("shared-cell-sub", "Reverses scan " + adminSearchPickingScanHistoryRow.originalScanID)
    );
  }
  const adminSearchPickingScanHistoryReasonValue = adminSearchPickingScanHistoryReason.hasChildNodes()
    ? adminSearchPickingScanHistoryReason : "-";

  // Each scan has its own link ID, even when several scans refer to the same order.
  const adminSearchPickingScanHistoryOrderLink = document.createElement("a");
  adminSearchPickingScanHistoryOrderLink.id =
    "adminSearchPickingScanHistoryOrderLink-" + adminSearchPickingScanHistoryRow.scanID;
  adminSearchPickingScanHistoryOrderLink.href =
    "admin-order-detail.html?orderID=" + encodeURIComponent(adminSearchPickingScanHistoryRow.orderID);
  adminSearchPickingScanHistoryOrderLink.textContent =
    adminSearchPickingScanHistoryRow.adminSearchPickingScanHistoryOrderReference;
  return adminShowLogTabBuildRow(adminSearchPickingScanHistoryRow.scanTime, [
    [adminSearchPickingScanHistoryOrderLink],
    [adminSearchPickingScanHistoryRow.adminSearchPickingScanHistoryWorkerUsername],
    [adminSearchPickingScanHistoryRow.scannedIdentifier],
    [adminSearchPickingScanHistoryRow.adminSearchPickingScanHistoryProductSKU],
    [adminBuildStatusBadge(adminSearchPickingScanHistoryRow.result)],
    [adminSearchPickingScanHistoryReasonValue, "shared-table-wrap"]
  ]);
}

// ===== SECTION 8: adminViewLogCleanupStatus =====

// What it does: Show the latest logged cleanup and the retention rules.
// Input: None. Reads the cleanup status endpoint.
// Output: The cleanup facts, or a corner message if they cannot be loaded.
async function adminViewLogCleanupStatus() {
  try {
    const adminViewLogCleanupStatusResponse = await sharedSendRequest("GET", "/api/admin/logs/cleanup-status");
    adminViewLogCleanupStatusKeepRuleText.textContent =
      adminViewLogCleanupStatusResponse.adminViewLogCleanupStatusKeepRuleText;
    if (!adminViewLogCleanupStatusResponse.adminViewLogCleanupStatusHasRun) {
      adminViewLogCleanupStatusLastRunTimeText.textContent = "Not run yet";
      return;
    }

    // The audit entry contains UTC time and the three delete counts from the same run.
    adminViewLogCleanupStatusLastRunTimeText.textContent = sharedFormatTime(
      adminViewLogCleanupStatusResponse.adminViewLogCleanupStatusLastRunTime
    );
    adminViewLogCleanupStatusCountText.textContent = "Security log "
      + adminViewLogCleanupStatusResponse.adminViewLogCleanupStatusSecurityLogDeletedCount + ", picking scans "
      + adminViewLogCleanupStatusResponse.adminViewLogCleanupStatusPickingScanHistoryDeletedCount + ", audit log "
      + adminViewLogCleanupStatusResponse.adminViewLogCleanupStatusAuditLogDeletedCount;
  } catch (adminViewLogCleanupStatusError) {
    sharedShowMessage("ERROR", adminViewLogCleanupStatusError.message);
  }
}

// ===== SECTION 9: HELPER FUNCTIONS =====

// What it does: Gather the controls and mutable list state for one panel.
// Input: The stored tab name, tab control, search function name, route suffix and row renderer.
// Output: The tab's controls and list state used to search and render that log.
function adminShowLogTabReadPanel(adminShowLogTabName, adminShowLogTabButton, adminShowLogTabSearchName,
  adminShowLogTabRoute, adminShowLogTabBuildResultRow) {
  const adminShowLogTabPanel = document.getElementById(adminShowLogTabButton.dataset.tab);
  return {
    adminShowLogTabName,
    adminShowLogTabButton,
    adminShowLogTabPanel,
    adminShowLogTabSearchName,
    adminShowLogTabWebAddress: "/api/admin/logs/" + adminShowLogTabRoute,
    adminShowLogTabBuildResultRow,
    adminShowLogTabForm: adminShowLogTabPanel.querySelector("form"),
    adminShowLogTabResultTableBody: document.getElementById(adminShowLogTabSearchName + "ResultTableBody"),
    adminShowLogTabEmptyMessage: document.getElementById(adminShowLogTabSearchName + "EmptyMessage"),
    adminShowLogTabTotalRowCountText: document.getElementById(adminShowLogTabSearchName + "TotalRowCountText"),
    adminShowLogTabPager: document.getElementById(adminShowLogTabSearchName + "Pager"),
    adminShowLogTabFilterList: [],
    adminShowLogTabPageNumber: 1,
    adminShowLogTabRequestCount: 0
  };
}

// What it does: Restore one tab's filter controls and search state from the page address.
// Input: The tab to restore.
// Output: Datetimes are displayed in local time and the corresponding UTC instants stay in the filters.
function adminShowLogTabRestore(adminShowLogTabEntry) {
  for (const adminShowLogTabControl of adminShowLogTabEntry.adminShowLogTabForm.elements) {
    if (!adminShowLogTabControl.name) {
      continue;
    }
    const adminShowLogTabFilterValue = sharedReadPageParameter(adminShowLogTabControl.name);
    if (adminShowLogTabControl.type === "datetime-local") {
      adminShowLogTabControl.value = sharedFormatTime(adminShowLogTabFilterValue).replace(" ", "T");
    } else {
      adminShowLogTabControl.value = adminShowLogTabFilterValue;
    }
    adminShowLogTabEntry.adminShowLogTabFilterList.push([
      adminShowLogTabControl.name, adminShowLogTabControl.value ? adminShowLogTabFilterValue : ""
    ]);
  }

  const adminShowLogTabRestoredPageNumber = Number(
    sharedReadPageParameter(adminShowLogTabEntry.adminShowLogTabSearchName + "PageNumber")
  );
  if (Number.isSafeInteger(adminShowLogTabRestoredPageNumber) && adminShowLogTabRestoredPageNumber > 0) {
    adminShowLogTabEntry.adminShowLogTabPageNumber = adminShowLogTabRestoredPageNumber;
  }
}

// What it does: Connect a panel's tab, filter and download controls.
// Input: A tab with restored state.
// Output: Tab navigation, search, clear and CSV download respond to their controls.
function adminShowLogTabListen(adminShowLogTabEntry) {
  const adminShowLogTabForm = adminShowLogTabEntry.adminShowLogTabForm;

  // What it does: Activate this tab. Input: None. Output: The tab's saved search opens.
  adminShowLogTabEntry.adminShowLogTabButton.addEventListener("click", () => adminShowLogTab(adminShowLogTabEntry));
  adminShowLogTabEntry.adminShowLogTabButton.addEventListener("keydown", adminShowLogTabMoveFocus);

  // What it does: Apply the filter bar. Input: A submit event. Output: A search starting on page one.
  adminShowLogTabForm.addEventListener("submit", (adminShowLogTabSubmitEvent) => {
    adminShowLogTabSubmitEvent.preventDefault();
    adminShowLogTabEntry.adminShowLogTabFilterList = adminShowLogTabReadFilters(adminShowLogTabEntry);
    adminShowLogTabSearch(adminShowLogTabEntry, 1);
  });

  // What it does: Clear this panel's filters. Input: A button click. Output: The first unfiltered page.
  const adminShowLogTabClear = (adminShowLogTabClearEvent) => {
    adminShowLogTabForm.reset();
    adminShowLogTabEntry.adminShowLogTabFilterList = adminShowLogTabReadFilters(adminShowLogTabEntry);
    if (adminShowLogTabEntry.adminShowLogTabEmptyMessage.contains(adminShowLogTabClearEvent.target)) {
      adminShowLogTabForm.querySelector("input").focus();
    }
    adminShowLogTabSearch(adminShowLogTabEntry, 1);
  };
  adminShowLogTabForm.querySelector(".shared-filter-buttons button[type=button]")
    .addEventListener("click", adminShowLogTabClear);
  adminShowLogTabEntry.adminShowLogTabEmptyMessage.querySelector("button")
    .addEventListener("click", adminShowLogTabClear);

  // What it does: Export this panel's current filters. Input: The button click. Output: A CSV download.
  const adminShowLogTabDownloadButton = adminShowLogTabForm.querySelector(".shared-filter-end button");
  adminShowLogTabDownloadButton.addEventListener("click", () => {
    adminShowLogTabDownload(adminShowLogTabEntry, adminShowLogTabDownloadButton);
  });
}

// What it does: Move keyboard focus and selection within the tab list.
// Input: A key press on a tab button.
// Output: Arrow keys wrap and Home and End select the first and last tabs.
function adminShowLogTabMoveFocus(adminShowLogTabKeyEvent) {
  const adminShowLogTabIndex = adminShowLogTabList.indexOf(adminShowLogTabCurrent);
  const adminShowLogTabCount = adminShowLogTabList.length;
  const adminShowLogTabKeyIndex = {
    ArrowRight: (adminShowLogTabIndex + 1) % adminShowLogTabCount,
    ArrowLeft: (adminShowLogTabIndex + adminShowLogTabCount - 1) % adminShowLogTabCount,
    Home: 0,
    End: adminShowLogTabCount - 1
  };
  if (!Object.hasOwn(adminShowLogTabKeyIndex, adminShowLogTabKeyEvent.key)) {
    return;
  }
  adminShowLogTabKeyEvent.preventDefault();
  const adminShowLogTabSelected = adminShowLogTabList[adminShowLogTabKeyIndex[adminShowLogTabKeyEvent.key]];
  adminShowLogTab(adminShowLogTabSelected);
  adminShowLogTabSelected.adminShowLogTabButton.focus();
}

// What it does: Size and position the design's moving underline.
// Input: None. Uses the selected tab.
// Output: The two CSS values that put the underline under that tab.
function adminShowLogTabPlaceBar() {
  const adminShowLogTabButton = adminShowLogTabCurrent.adminShowLogTabButton;
  adminShowLogTabButtonList.style.setProperty("--shared-tab-bar-left", adminShowLogTabButton.offsetLeft + "px");
  adminShowLogTabButtonList.style.setProperty("--shared-tab-bar-width", adminShowLogTabButton.offsetWidth + "px");
}

// What it does: Read current filter controls, including UTC times for the two date fields.
// Input: The tab holding the filter controls and previous list state.
// Output: The list of parameter names and values for the current controls.
function adminShowLogTabReadFilters(adminShowLogTabEntry) {
  const adminShowLogTabFilterList = [];
  const adminShowLogTabAppliedFilters = new Map(adminShowLogTabEntry.adminShowLogTabFilterList);
  for (const adminShowLogTabControl of adminShowLogTabEntry.adminShowLogTabForm.elements) {
    if (!adminShowLogTabControl.name) {
      continue;
    }
    let adminShowLogTabFilterValue = adminShowLogTabControl.value.trim();
    if (adminShowLogTabControl.type === "datetime-local" && adminShowLogTabFilterValue) {
      // Reuse an unchanged instant when a copied link includes seconds or an ambiguous local time.
      const adminShowLogTabSavedTime = adminShowLogTabAppliedFilters.get(adminShowLogTabControl.name);
      if (sharedFormatTime(adminShowLogTabSavedTime).replace(" ", "T") === adminShowLogTabFilterValue) {
        adminShowLogTabFilterValue = adminShowLogTabSavedTime;
      } else {
        const adminShowLogTabLocalTime = new Date(adminShowLogTabFilterValue);
        if (!Number.isNaN(adminShowLogTabLocalTime.getTime())) {
          adminShowLogTabFilterValue = adminShowLogTabLocalTime.toISOString();
        }
      }
    }
    adminShowLogTabFilterList.push([adminShowLogTabControl.name, adminShowLogTabFilterValue]);
  }
  return adminShowLogTabFilterList;
}

// What it does: Search one log and keep the address in sync with its applied filters and page number.
// Input: The tab and requested page number.
// Output: True if the current rows were loaded, or false if the request failed or was replaced.
async function adminShowLogTabSearch(adminShowLogTabEntry, adminShowLogTabPageNumber) {
  const adminShowLogTabRequestNumber = ++adminShowLogTabEntry.adminShowLogTabRequestCount;
  const adminShowLogTabPageParameterName = adminShowLogTabEntry.adminShowLogTabSearchName + "PageNumber";
  adminShowLogTabEntry.adminShowLogTabPageNumber = adminShowLogTabPageNumber;
  const adminShowLogTabParameterList = new URLSearchParams(adminShowLogTabEntry.adminShowLogTabFilterList);
  adminShowLogTabParameterList.set(adminShowLogTabPageParameterName, adminShowLogTabPageNumber);
  adminShowLogTabWriteAddress(adminShowLogTabEntry, adminShowLogTabPageParameterName);

  try {
    const adminShowLogTabResponse = await sharedSendRequest("GET", adminShowLogTabEntry.adminShowLogTabWebAddress
      + "?" + adminShowLogTabParameterList);
    if (adminShowLogTabRequestNumber !== adminShowLogTabEntry.adminShowLogTabRequestCount) {
      return false;
    }
    adminShowLogTabEntry.adminShowLogTabPageNumber = adminShowLogTabResponse[adminShowLogTabPageParameterName];
    adminShowLogTabWriteAddress(adminShowLogTabEntry, adminShowLogTabPageParameterName);
    adminShowLogTabRender(adminShowLogTabEntry, adminShowLogTabResponse);
    return true;
  } catch (adminShowLogTabError) {
    if (adminShowLogTabEntry === adminShowLogTabCurrent
        && adminShowLogTabRequestNumber === adminShowLogTabEntry.adminShowLogTabRequestCount) {
      sharedShowMessage("ERROR", adminShowLogTabError.message);
    }
    return false;
  }
}

// What it does: Apply any edited filters before exporting the current log.
// Input: The tab and its Download CSV button.
// Output: A CSV using the same filters as the table and no page limit from the pager.
async function adminShowLogTabDownload(adminShowLogTabEntry, adminShowLogTabDownloadButton) {
  const adminShowLogTabFilterList = adminShowLogTabReadFilters(adminShowLogTabEntry);
  const adminShowLogTabFilters = new URLSearchParams(adminShowLogTabFilterList);
  const adminShowLogTabAppliedFilters = new URLSearchParams(adminShowLogTabEntry.adminShowLogTabFilterList);
  adminShowLogTabDownloadButton.disabled = true;
  try {
    if (adminShowLogTabFilters.toString() !== adminShowLogTabAppliedFilters.toString()) {
      adminShowLogTabEntry.adminShowLogTabFilterList = adminShowLogTabFilterList;
      if (!await adminShowLogTabSearch(adminShowLogTabEntry, 1)) {
        return;
      }
    }
    await adminDownloadCSV(adminShowLogTabEntry.adminShowLogTabWebAddress + "/csv?" + adminShowLogTabFilters);
  } finally {
    adminShowLogTabDownloadButton.disabled = false;
  }
}

// What it does: Save a tab's list state without creating a browser history entry.
// Input: The tab to save and its page number parameter name.
// Output: The updated address, leaving the selected tab and other saved filters intact.
function adminShowLogTabWriteAddress(adminShowLogTabEntry, adminShowLogTabPageParameterName) {
  sharedWritePageParameter([
    ["adminShowLogTabName", adminShowLogTabCurrent.adminShowLogTabName],
    ...adminShowLogTabEntry.adminShowLogTabFilterList,
    [adminShowLogTabPageParameterName, adminShowLogTabEntry.adminShowLogTabPageNumber]
  ]);
}

// What it does: Draw the filtered rows, live total and page controls.
// Input: The tab and its search response.
// Output: The updated list with an empty state when nothing matched.
function adminShowLogTabRender(adminShowLogTabEntry, adminShowLogTabResponse) {
  const adminShowLogTabSearchName = adminShowLogTabEntry.adminShowLogTabSearchName;
  const adminShowLogTabRowList = adminShowLogTabResponse[adminShowLogTabSearchName + "RowList"];
  const adminShowLogTabTotalRowCount = adminShowLogTabResponse[adminShowLogTabSearchName + "TotalRowCount"];
  const adminShowLogTabTableBody = adminShowLogTabEntry.adminShowLogTabResultTableBody;
  const adminShowLogTabEmptyMessage = adminShowLogTabEntry.adminShowLogTabEmptyMessage;
  adminShowLogTabTableBody.replaceChildren(
    ...adminShowLogTabRowList.map(adminShowLogTabEntry.adminShowLogTabBuildResultRow)
  );
  adminShowLogTabTableBody.closest(".shared-table-scroll").hidden = adminShowLogTabRowList.length === 0;
  adminShowLogTabEmptyMessage.hidden = adminShowLogTabRowList.length > 0;
  // What it does: Detect a set filter. Input: One name and value pair. Output: Whether the value is nonempty.
  adminShowLogTabEmptyMessage.querySelector("button").hidden = !adminShowLogTabEntry.adminShowLogTabFilterList.some(
    ([, adminShowLogTabFilterValue]) => adminShowLogTabFilterValue !== ""
  );

  // Keep the live count in place while refreshing the other controls.
  const adminShowLogTabPager = adminShowLogTabEntry.adminShowLogTabPager;
  const adminShowLogTabPageControls = document.createDocumentFragment();
  sharedBuildPager(adminShowLogTabPageControls, adminShowLogTabEntry.adminShowLogTabPageNumber,
    adminShowLogTabResponse[adminShowLogTabSearchName + "TotalPageCount"],
    // What it does: Navigate within this log. Input: Page number. Output: The requested list page.
    (adminShowLogTabPageNumber) => adminShowLogTabSearch(adminShowLogTabEntry, adminShowLogTabPageNumber)
  );
  const adminShowLogTabTotalRowCountText = adminShowLogTabEntry.adminShowLogTabTotalRowCountText;
  while (adminShowLogTabTotalRowCountText.nextElementSibling) {
    adminShowLogTabTotalRowCountText.nextElementSibling.remove();
  }
  adminShowLogTabPager.append(adminShowLogTabPageControls);
  adminShowLogTabPager.hidden = false;
  adminShowLogTabTotalRowCountText.textContent = adminShowLogTabTotalRowCount
    + (adminShowLogTabTotalRowCount === 1 ? " row" : " rows");
}

// What it does: Build one list table row, always starting with a row heading for the local time.
// Input: An ISO timestamp and a list of other cell values with optional design classes.
// Output: A table row containing only text or elements built by this page.
function adminShowLogTabBuildRow(adminShowLogTabTime, adminShowLogTabCellList) {
  const adminShowLogTabRow = document.createElement("tr");
  const adminShowLogTabTimeCell = document.createElement("th");
  adminShowLogTabTimeCell.scope = "row";
  adminShowLogTabTimeCell.className = "shared-table-time";
  adminShowLogTabTimeCell.textContent = sharedFormatTime(adminShowLogTabTime);
  adminShowLogTabRow.append(adminShowLogTabTimeCell);

  // Append log values as text so their contents cannot create markup.
  for (const [adminShowLogTabCellValue, adminShowLogTabCellClass] of adminShowLogTabCellList) {
    const adminShowLogTabCell = document.createElement("td");
    if (adminShowLogTabCellClass) {
      adminShowLogTabCell.className = adminShowLogTabCellClass;
    }
    adminShowLogTabCell.append(adminShowLogTabCellValue || "-");
    adminShowLogTabRow.append(adminShowLogTabCell);
  }
  return adminShowLogTabRow;
}

// What it does: Build a styled line with escaped text.
// Input: A design class and the line's words.
// Output: A span containing text or a dash for an empty value.
function adminShowLogTabBuildText(adminShowLogTabTextClass, adminShowLogTabWords) {
  const adminShowLogTabText = document.createElement("span");
  adminShowLogTabText.className = adminShowLogTabTextClass;
  adminShowLogTabText.textContent = adminShowLogTabWords || "-";
  return adminShowLogTabText;
}
