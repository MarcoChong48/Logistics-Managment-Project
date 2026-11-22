"use strict";

// ===== SECTION 1: PAGE ELEMENTS =====
const adminSearchRefundIsUnresolvedOnlyCheckbox =
  document.getElementById("adminSearchRefundIsUnresolvedOnlyCheckbox");
const adminSearchRefundSubmitButton = document.getElementById("adminSearchRefundSubmitButton");
const adminSearchRefundResultTableBody = document.getElementById("adminSearchRefundResultTableBody");
const adminSearchRefundEmptyMessage = document.getElementById("adminSearchRefundEmptyMessage");
const adminSearchRefundTotalRowCountText = document.getElementById("adminSearchRefundTotalRowCountText");
const adminSearchRefundPager = document.getElementById("adminSearchRefundPager");
const adminDownloadRefundCSVButton = document.getElementById("adminDownloadRefundCSVButton");

// ===== SECTION 2: PAGE STATE =====
let adminSearchRefundIsUnresolvedOnly = sharedReadPageParameter("adminSearchRefundIsUnresolvedOnly") !== "false";
let adminSearchRefundPageNumber = Number.parseInt(sharedReadPageParameter("adminSearchRefundPageNumber"), 10) || 1;
let adminSearchRefundRequestCount = 0;
const adminRetryRefundDecidedRowSet = new WeakSet();

// ===== SECTION 3: START OF PAGE =====
adminSearchRefundIsUnresolvedOnlyCheckbox.checked = adminSearchRefundIsUnresolvedOnly;
adminSearchRefundSubmitButton.form.addEventListener("submit",
  // What it does: Apply the checkbox value from the beginning of the list.
  // Input: The filter form's submit event. Output: A new search without navigation.
  (adminSearchRefundEvent) => {
    adminSearchRefundEvent.preventDefault();
    adminSearchRefund(1, adminSearchRefundIsUnresolvedOnlyCheckbox.checked);
  });
adminDownloadRefundCSVButton.addEventListener("click", adminDownloadRefundCSV);
adminBuildPageFrame("Refunds").then(
  // What it does: Load the remembered search when the Admin frame is visible.
  // Input: None. Output: The first displayed refund page.
  () => {
    if (!document.querySelector("main").hidden) {
      sharedWatchTableScroll();
      adminSearchRefund(Math.max(adminSearchRefundPageNumber, 1));
    }
  });

// ===== SECTION 4: adminSearchRefund and adminDownloadRefundCSV =====

// What it does: Search for refunds and apply the returned list, count and page number.
// Input: Requested page number and the unresolved-only checkbox value.
// Output: A new list and its saved URL, or a corner error.
async function adminSearchRefund(adminSearchRefundRequestedPageNumber,
  adminSearchRefundRequestedIsUnresolvedOnly = adminSearchRefundIsUnresolvedOnly) {
  const adminSearchRefundRequestNumber = ++adminSearchRefundRequestCount;
  try {
    const adminSearchRefundResponse = await adminReadRefundPage(
      adminSearchRefundRequestedIsUnresolvedOnly, adminSearchRefundRequestedPageNumber);
    if (adminSearchRefundRequestNumber !== adminSearchRefundRequestCount) {
      return;
    }

    // The applied filter, pager and download describe the same saved search.
    adminSearchRefundIsUnresolvedOnly = adminSearchRefundRequestedIsUnresolvedOnly;
    adminSearchRefundResultTableBody.replaceChildren(
      ...adminSearchRefundResponse.adminSearchRefundRowList.map(adminBuildRefundRow));
    adminShowRefundPage(adminSearchRefundResponse);
    adminDownloadRefundCSVButton.disabled = false;
  } catch (adminSearchRefundError) {
    if (adminSearchRefundRequestNumber === adminSearchRefundRequestCount) {
      sharedShowMessage("ERROR", adminSearchRefundError.message);
    }
  }
}

// What it does: Download all matching refunds using the applied filter.
// Input: None. Output: The refund CSV or a corner error.
function adminDownloadRefundCSV() {
  const adminDownloadRefundCSVParameters = new URLSearchParams({ adminSearchRefundIsUnresolvedOnly });
  return adminDownloadCSV("/api/admin/refunds/csv?" + adminDownloadRefundCSVParameters);
}

// ===== SECTION 5: adminCheckRefundStatus =====

// What it does: Ask the payment provider and read back the saved result.
// Input: The refund number, its row and the pressed button.
// Output: The current status and last check in that row, or a corner error.
async function adminCheckRefundStatus(refundID, adminCheckRefundStatusRow, adminCheckRefundStatusButton) {
  adminSetRefundRowButtonsDisabled(adminCheckRefundStatusRow, true);
  try {
    const adminCheckRefundStatusResponse = await sharedSendRequest("POST", "/api/admin/refunds/" + refundID + "/check");
    adminViewMenuCounter();
    await adminRefreshRefundRow(refundID, adminCheckRefundStatusRow, adminCheckRefundStatusButton);
    sharedShowMessage("INFORMATION", adminCheckRefundStatusResponse.adminCheckRefundStatusMessage);
  } catch (adminCheckRefundStatusError) {
    sharedShowMessage("ERROR", adminCheckRefundStatusError.message);
  } finally {
    adminSetRefundRowButtonsDisabled(adminCheckRefundStatusRow, false);
  }
}

// ===== SECTION 6: adminRetryRefund =====

// What it does: Ask for a reason and request another refund attempt.
// Input: The refund data and its row.
// Output: A saved decision in the same row, or a refusal message.
async function adminRetryRefund(adminRetryRefundData, adminRetryRefundRow) {
  const adminRetryRefundReason = await adminOpenReasonDialog(
    "Retry the refund of order " + adminRetryRefundData.adminSearchRefundOrderReference + "?",
    "The payment provider is asked again first. "
      + "A new refund is requested only when the provider confirms that the old one failed.",
    "REQUIRED_REASON", "Retry refund", "Cancel", false, [], "adminRetryRefundDialog");
  if (adminRetryRefundReason === null) {
    return;
  }

  // Keep the old row visible after success, with the newly saved attempt and its decision details.
  const { refundID } = adminRetryRefundData;
  adminSetRefundRowButtonsDisabled(adminRetryRefundRow, true);
  try {
    const adminRetryRefundResponse = await sharedSendRequest("POST", "/api/admin/refunds/" + refundID + "/retry",
      { adminRetryRefundReason });
    adminViewMenuCounter();
    adminRetryRefundDecidedRowSet.add(adminRetryRefundRow);
    adminShowDecidedRow(adminRetryRefundRow, adminRetryRefundRow.querySelector("[data-decided-cell]"),
      adminRetryRefundResponse, "Refund {refundID} requested by {admin}, {time}");
    sharedShowMessage("SUCCESS", adminRetryRefundResponse.message);
  } catch (adminRetryRefundError) {
    // Show the refusal without changing the row. Search reads any provider check saved before it.
    sharedShowMessage("ERROR", adminRetryRefundError.message);
  } finally {
    adminSetRefundRowButtonsDisabled(adminRetryRefundRow, false);
  }
}

// ===== SECTION 7: HELPER FUNCTIONS =====

// What it does: Read a page of refunds without changing the current view.
// Input: Whether to hide resolved refunds and the requested page number.
// Output: The search response or an error from the server.
function adminReadRefundPage(adminReadRefundPageIsUnresolvedOnly, adminReadRefundPageNumber) {
  const adminReadRefundPageParameters = new URLSearchParams({
    adminSearchRefundIsUnresolvedOnly: adminReadRefundPageIsUnresolvedOnly,
    adminSearchRefundPageNumber: adminReadRefundPageNumber
  });
  return sharedSendRequest("GET", "/api/admin/refunds?" + adminReadRefundPageParameters);
}

// What it does: Apply the displayed page metadata and remember the search in the address.
// Input: A successful list response. Output: Updated count, pager and empty state.
function adminShowRefundPage(adminShowRefundPageResponse) {
  adminSearchRefundPageNumber = adminShowRefundPageResponse.adminSearchRefundPageNumber;
  sharedWritePageParameter([
    ["adminSearchRefundIsUnresolvedOnly", String(adminSearchRefundIsUnresolvedOnly)],
    ["adminSearchRefundPageNumber", adminSearchRefundPageNumber]
  ]);

  const adminShowRefundPageIsEmpty = adminSearchRefundResultTableBody.rows.length === 0;
  adminSearchRefundResultTableBody.closest(".shared-table-scroll").hidden = adminShowRefundPageIsEmpty;
  adminSearchRefundEmptyMessage.hidden = !adminShowRefundPageIsEmpty;
  adminSearchRefundPager.hidden = adminShowRefundPageIsEmpty;
  adminSearchRefundTotalRowCountText.textContent = adminShowRefundPageResponse.adminSearchRefundTotalRowCount;
  sharedBuildPager(adminSearchRefundPager, adminSearchRefundPageNumber,
    adminShowRefundPageResponse.adminSearchRefundTotalPageCount, adminSearchRefund);
}

// What it does: Refresh the affected row after a saved provider check.
// Input: The refund number, original row and action.
// Output: The saved data, with focus kept in the row or moved to Search if the row left the list.
async function adminRefreshRefundRow(refundID, adminRefreshRefundRowElement, adminRefreshRefundRowButton) {
  const adminRefreshRefundRowResponse = await adminReadRefundPage(
    adminSearchRefundIsUnresolvedOnly, adminSearchRefundPageNumber);
  if (!adminRefreshRefundRowElement.isConnected) {
    return;
  }

  // A completed refund leaves the unresolved search. Keep outcomes of other decisions in their own rows.
  const adminRefreshRefundRowSaved = adminRefreshRefundRowResponse.adminSearchRefundRowList.find(
    // What it does: Find the saved attempt. Input: A result row. Output: Whether its number matches.
    (adminRefreshRefundRowCandidate) => adminRefreshRefundRowCandidate.refundID === refundID
  );
  const adminRefreshRefundRowShouldFocus = document.activeElement === document.body
    || adminRefreshRefundRowElement.contains(document.activeElement);
  if (!adminRefreshRefundRowSaved) {
    adminRefreshRefundRowElement.remove();
    const adminRefreshRefundRowHasDecisions = Array.from(adminSearchRefundResultTableBody.rows).some(
      // What it does: Identify a retained retry outcome. Input: A visible row. Output: Whether it was decided.
      (adminRefreshRefundRowVisibleRow) => adminRetryRefundDecidedRowSet.has(adminRefreshRefundRowVisibleRow)
    );
    if (!adminRefreshRefundRowHasDecisions) {
      // Fill the vacant place, including when finishing the last refund on the last page changed the page number.
      adminSearchRefundResultTableBody.replaceChildren(
        ...adminRefreshRefundRowResponse.adminSearchRefundRowList.map(adminBuildRefundRow));
    }
    adminShowRefundPage(adminRefreshRefundRowResponse);
    if (adminRefreshRefundRowShouldFocus) {
      adminSearchRefundSubmitButton.focus();
    }
    return;
  }

  const adminRefreshRefundRowReplacement = adminBuildRefundRow(adminRefreshRefundRowSaved);
  adminRefreshRefundRowElement.replaceWith(adminRefreshRefundRowReplacement);
  adminShowRefundPage(adminRefreshRefundRowResponse);
  if (adminRefreshRefundRowShouldFocus) {
    const adminRefreshRefundRowFocus = document.getElementById(adminRefreshRefundRowButton.id)
      ?? adminRefreshRefundRowReplacement.querySelector("a");
    adminRefreshRefundRowFocus.focus();
  }
}

// What it does: Keep a provider check and a retry from overlapping in the same displayed row.
// Input: The row and whether its actions should be disabled. Output: Updated native button states.
function adminSetRefundRowButtonsDisabled(adminSetRefundRowButtonsDisabledRow, adminSetRefundRowButtonsDisabledValue) {
  for (const adminSetRefundRowButtonsDisabledButton of adminSetRefundRowButtonsDisabledRow.querySelectorAll("button")) {
    adminSetRefundRowButtonsDisabledButton.disabled = adminSetRefundRowButtonsDisabledValue;
  }
}

// What it does: Render one live refund attempt with its status, age, and latest provider check.
// Input: The saved search row. Output: A detached work-table row.
function adminBuildRefundRow(adminBuildRefundRowData) {
  const adminBuildRefundRowElement = document.createElement("tr");
  const { refundID, amount, refundStatus, createdTime, completedTime } = adminBuildRefundRowData;
  const adminBuildRefundRowLink = document.createElement("a");
  adminBuildRefundRowLink.id = "adminSearchRefundOrderLink-" + refundID;
  adminBuildRefundRowLink.href = "admin-order-detail.html?orderID="
    + encodeURIComponent(adminBuildRefundRowData.adminSearchRefundOrderID);
  adminBuildRefundRowLink.className = "shared-nowrap";
  adminBuildRefundRowLink.textContent = adminBuildRefundRowData.adminSearchRefundOrderReference;
  adminBuildRefundRowLink.setAttribute("aria-label", "View order " + adminBuildRefundRowLink.textContent);
  const adminBuildRefundRowOrder = adminBuildRefundCell("Order", adminBuildRefundRowLink,
    "Refund " + refundID, adminBuildStatusBadge(adminBuildRefundRowData.adminSearchRefundOrderStatus));
  const adminBuildRefundRowAmount = adminBuildRefundCell("Amount", sharedFormatMoney(amount));
  adminBuildRefundRowAmount.classList.add("shared-table-number");

  // Show why a historical attempt is resolved, and when any completed attempt finished.
  const adminBuildRefundRowReplacementID = adminBuildRefundRowData.adminSearchRefundReplacedByRefundID;
  const adminBuildRefundRowStatus = adminBuildRefundCell("Status", adminBuildStatusBadge(refundStatus),
    adminBuildRefundRowReplacementID == null ? null : "Replaced by refund " + adminBuildRefundRowReplacementID,
    completedTime ? adminBuildRefundText("Completed ", adminBuildRefundTime(completedTime)) : null);
  adminBuildRefundRowStatus.setAttribute("data-decided-cell", "");
  const adminBuildRefundRowAge = adminBuildRefundCell("Age",
    adminFormatRefundAge(adminBuildRefundRowData.adminSearchRefundAgeHours),
    adminBuildRefundText("since ", adminBuildRefundTime(createdTime)));
  const adminBuildRefundRowLastCheckTime = adminBuildRefundRowData.adminSearchRefundLastCheckTime;
  const adminBuildRefundRowOutcome = adminBuildRefundRowData.adminSearchRefundLastCheckOutcome;
  const adminBuildRefundRowLastCheck = adminBuildRefundCell("Last check",
    adminBuildRefundRowLastCheckTime ? adminBuildRefundTime(adminBuildRefundRowLastCheckTime) : "-",
    adminBuildRefundRowOutcome
      ? adminBuildRefundText("Provider answer: ", adminBuildStatusBadge(adminBuildRefundRowOutcome)) : null);
  adminBuildRefundRowElement.append(adminBuildRefundRowOrder, adminBuildRefundRowAmount, adminBuildRefundRowStatus,
    adminBuildRefundRowAge, adminBuildRefundRowLastCheck,
    adminBuildRefundActions(adminBuildRefundRowData, adminBuildRefundRowElement));
  return adminBuildRefundRowElement;
}

// What it does: Offer provider checks and retries only when the saved row allows them.
// Input: One refund's search data and its row. Output: Its actions cell or a dash.
function adminBuildRefundActions(adminBuildRefundActionsData, adminBuildRefundActionsRow) {
  const adminBuildRefundActionsCell = document.createElement("td");
  adminBuildRefundActionsCell.className = "shared-table-actions";
  adminBuildRefundActionsCell.dataset.title = "Actions";
  if (!adminBuildRefundActionsData.adminSearchRefundIsUnresolved) {
    const adminBuildRefundActionsEmptyText = document.createElement("span");
    adminBuildRefundActionsEmptyText.className = "shared-muted";
    adminBuildRefundActionsEmptyText.textContent = "-";
    adminBuildRefundActionsCell.append(adminBuildRefundActionsEmptyText);
    return adminBuildRefundActionsCell;
  }

  const { refundID, refundStatus, adminSearchRefundOrderReference } = adminBuildRefundActionsData;
  const adminBuildRefundActionsGroup = document.createElement("div");
  adminBuildRefundActionsGroup.className = "shared-cell-buttons";
  const adminCheckRefundStatusButton = adminBuildRefundButton("adminCheckRefundStatusButton-" + refundID,
    "Check provider status", adminSearchRefundOrderReference);
  adminCheckRefundStatusButton.addEventListener("click",
    // What it does: Check this refund with the provider. Input: None. Output: The latest outcome.
    () => adminCheckRefundStatus(refundID, adminBuildRefundActionsRow, adminCheckRefundStatusButton));
  adminBuildRefundActionsGroup.append(adminCheckRefundStatusButton);
  if (refundStatus === "FAILED") {
    const adminRetryRefundOpenButton = adminBuildRefundButton("adminRetryRefundOpenButton-" + refundID,
      "Retry refund", adminSearchRefundOrderReference);
    adminRetryRefundOpenButton.addEventListener("click",
      // What it does: Open the retry window for this refund. Input: None. Output: The retry flow.
      () => adminRetryRefund(adminBuildRefundActionsData, adminBuildRefundActionsRow));
    adminBuildRefundActionsGroup.append(adminRetryRefundOpenButton);
  }
  adminBuildRefundActionsCell.append(adminBuildRefundActionsGroup);
  return adminBuildRefundActionsCell;
}

// What it does: Build a row action with a label naming its order.
// Input: Button ID, visible text and order reference. Output: A detached button.
function adminBuildRefundButton(adminBuildRefundButtonID, adminBuildRefundButtonText, adminBuildRefundButtonReference) {
  const adminBuildRefundButtonElement = document.createElement("button");
  adminBuildRefundButtonElement.type = "button";
  adminBuildRefundButtonElement.className = "shared-button shared-button-small";
  adminBuildRefundButtonElement.id = adminBuildRefundButtonID;
  adminBuildRefundButtonElement.textContent = adminBuildRefundButtonText;
  adminBuildRefundButtonElement.setAttribute("aria-label",
    adminBuildRefundButtonText + " for order " + adminBuildRefundButtonReference);
  return adminBuildRefundButtonElement;
}

// What it does: Build the lines in one refund work-table cell.
// Input: Column title, main content and any secondary lines. Output: A row header or cell.
function adminBuildRefundCell(adminBuildRefundCellTitle, adminBuildRefundCellMain, ...adminBuildRefundCellSubList) {
  const adminBuildRefundCellIsOrder = adminBuildRefundCellTitle === "Order";
  const adminBuildRefundCellElement = document.createElement(adminBuildRefundCellIsOrder ? "th" : "td");
  if (adminBuildRefundCellIsOrder) {
    adminBuildRefundCellElement.scope = "row";
  }
  adminBuildRefundCellElement.dataset.title = adminBuildRefundCellTitle;
  adminBuildRefundCellElement.append(adminBuildRefundLine("shared-cell-main", adminBuildRefundCellMain));
  for (const adminBuildRefundCellSub of adminBuildRefundCellSubList) {
    if (adminBuildRefundCellSub) {
      adminBuildRefundCellElement.append(adminBuildRefundLine("shared-cell-sub", adminBuildRefundCellSub));
    }
  }
  return adminBuildRefundCellElement;
}

// What it does: Give one work-table line its shared spacing and text size.
// Input: An existing line class and safe content. Output: A detached span.
function adminBuildRefundLine(adminBuildRefundLineClass, adminBuildRefundLineContent) {
  const adminBuildRefundLineElement = document.createElement("span");
  adminBuildRefundLineElement.className = adminBuildRefundLineClass;
  adminBuildRefundLineElement.append(adminBuildRefundLineContent);
  return adminBuildRefundLineElement;
}

// What it does: Join text and inline elements without parsing HTML.
// Input: The pieces of one secondary line. Output: An inline fragment.
function adminBuildRefundText(...adminBuildRefundTextPartList) {
  const adminBuildRefundTextFragment = document.createDocumentFragment();
  adminBuildRefundTextFragment.append(...adminBuildRefundTextPartList);
  return adminBuildRefundTextFragment;
}

// What it does: Keep a saved date and time together in an inline display.
// Input: An ISO instant. Output: Its local time element.
function adminBuildRefundTime(adminBuildRefundTimeValue) {
  const adminBuildRefundTimeElement = document.createElement("time");
  adminBuildRefundTimeElement.className = "shared-nowrap";
  adminBuildRefundTimeElement.dateTime = adminBuildRefundTimeValue;
  adminBuildRefundTimeElement.textContent = sharedFormatTime(adminBuildRefundTimeValue);
  return adminBuildRefundTimeElement;
}

// What it does: Write the elapsed whole hours in plain words.
// Input: The age in hours from the server. Output: Day and hour text, with singular forms when needed.
function adminFormatRefundAge(adminFormatRefundAgeHours) {
  const adminFormatRefundAgeDayCount = Math.floor(adminFormatRefundAgeHours / 24);
  const adminFormatRefundAgeHourCount = adminFormatRefundAgeHours % 24;
  const adminFormatRefundAgeDayText = adminFormatRefundAgeDayCount
    + (adminFormatRefundAgeDayCount === 1 ? " day" : " days");
  const adminFormatRefundAgeHourText = adminFormatRefundAgeHourCount
    + (adminFormatRefundAgeHourCount === 1 ? " hour" : " hours");
  if (adminFormatRefundAgeDayCount === 0) {
    return adminFormatRefundAgeHourText;
  }
  return adminFormatRefundAgeHourCount === 0 ? adminFormatRefundAgeDayText
    : adminFormatRefundAgeDayText + " " + adminFormatRefundAgeHourText;
}
