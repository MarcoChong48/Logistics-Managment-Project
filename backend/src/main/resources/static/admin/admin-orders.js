"use strict";

// ===== SECTION 1: PAGE ELEMENTS =====
const adminSearchOrderSubmitButton = document.getElementById("adminSearchOrderSubmitButton");
const adminSearchOrderClearButton = document.getElementById("adminSearchOrderClearButton");
const adminSearchOrderResultTable = document.getElementById("adminSearchOrderResultTable");
const adminSearchOrderResultTableBody = document.getElementById("adminSearchOrderResultTableBody");
const adminSearchOrderEmptyMessage = document.getElementById("adminSearchOrderEmptyMessage");
const adminSearchOrderTotalRowCountText = document.getElementById("adminSearchOrderTotalRowCountText");
const adminSearchOrderPager = document.getElementById("adminSearchOrderPager");
const adminDownloadOrderCSVButton = document.getElementById("adminDownloadOrderCSVButton");

// ===== SECTION 2: PAGE STATE =====
let adminSearchOrderFilterList = [];
let adminSearchOrderRequestCount = 0;
let adminDownloadOrderCSVIsPending = false;

// ===== SECTION 3: START OF PAGE =====

// What it does: Restore the list after the shared frame authorizes this page.
// Input: None. Reads search parameters from the address.
// Output: Requests the saved order list and page number.
adminBuildPageFrame("Orders").then(() => {
  if (document.querySelector("main").hidden) {
    return;
  }

  // Keep each saved instant alongside its displayed local time.
  for (const adminSearchOrderFilterControl of adminSearchOrderSubmitButton.form.elements) {
    if (!adminSearchOrderFilterControl.name) {
      continue;
    }
    const adminSearchOrderFilterValue = sharedReadPageParameter(adminSearchOrderFilterControl.name);
    adminSearchOrderFilterControl.value = adminSearchOrderFilterControl.type === "datetime-local"
      ? sharedFormatTime(adminSearchOrderFilterValue).replace(" ", "T") : adminSearchOrderFilterValue;
    adminSearchOrderFilterList.push([
      adminSearchOrderFilterControl.name, adminSearchOrderFilterControl.value ? adminSearchOrderFilterValue : ""
    ]);
  }

  sharedWatchTableScroll();
  const adminSearchOrderRestoredPageNumber = Number(sharedReadPageParameter("adminSearchOrderPageNumber"));
  adminSearchOrder(Number.isSafeInteger(adminSearchOrderRestoredPageNumber)
    && adminSearchOrderRestoredPageNumber > 0 ? adminSearchOrderRestoredPageNumber : 1);
});

// What it does: Apply the filter bar and return to the first page.
// Input: The filter form's submit event.
// Output: A new order search.
adminSearchOrderSubmitButton.form.addEventListener("submit", (adminSearchOrderSubmitEvent) => {
  adminSearchOrderSubmitEvent.preventDefault();
  adminSearchOrder(1, adminSearchOrderReadFilters());
});
adminSearchOrderClearButton.addEventListener("click", adminSearchOrderClearFilters);
adminSearchOrderEmptyMessage.querySelector("button").addEventListener("click", adminSearchOrderClearFilters);
adminDownloadOrderCSVButton.addEventListener("click", adminDownloadOrderCSV);

// ===== SECTION 4: adminSearchOrder =====

// What it does: Load the latest requested order search and keep its address in sync.
// Input: The requested page number and the filter values, defaulting to the filters already applied.
// Output: A refreshed list, address and pager, or a corner error.
async function adminSearchOrder(adminSearchOrderRequestedPageNumber,
  adminSearchOrderRequestedFilterList = adminSearchOrderFilterList) {
  // An older response must never replace a newer search.
  const adminSearchOrderRequestNumber = ++adminSearchOrderRequestCount;
  const adminSearchOrderParameterList = new URLSearchParams(adminSearchOrderRequestedFilterList);
  adminSearchOrderParameterList.set("adminSearchOrderPageNumber", adminSearchOrderRequestedPageNumber);
  try {
    const adminSearchOrderResponse = await sharedSendRequest("GET", "/api/admin/orders?" +
      adminSearchOrderParameterList);
    if (adminSearchOrderRequestNumber !== adminSearchOrderRequestCount) {
      return;
    }

    // Save the successful filters and page so reload, both Back controls and CSV refer to this list.
    adminSearchOrderFilterList = adminSearchOrderRequestedFilterList;
    sharedWritePageParameter(adminSearchOrderFilterList.concat([
      ["adminSearchOrderPageNumber", adminSearchOrderResponse.adminSearchOrderPageNumber]
    ]));
    adminSearchOrderRenderRows(adminSearchOrderResponse);
    adminDownloadOrderCSVButton.disabled = adminDownloadOrderCSVIsPending;
  } catch (adminSearchOrderError) {
    if (adminSearchOrderRequestNumber === adminSearchOrderRequestCount) {
      sharedShowMessage("ERROR", adminSearchOrderError.message);
    }
  }
}

// ===== SECTION 5: adminDownloadOrderCSV =====

// What it does: Download every matching order with the filters of the displayed list.
// Input: None. Uses the applied filters without the page number.
// Output: A CSV download or a corner error.
async function adminDownloadOrderCSV() {
  const adminDownloadOrderCSVParameterList = new URLSearchParams(adminSearchOrderFilterList);
  adminDownloadOrderCSVIsPending = true;
  adminDownloadOrderCSVButton.disabled = true;
  try {
    await adminDownloadCSV("/api/admin/orders/csv?" + adminDownloadOrderCSVParameterList);
  } finally {
    adminDownloadOrderCSVIsPending = false;
    adminDownloadOrderCSVButton.disabled = false;
  }
}

// ===== SECTION 6: HELPER FUNCTIONS =====

// What it does: Read filter controls and turn entered local times into UTC instants.
// Input: None. Uses the filter form and applied search state.
// Output: A list of filter names and values for the API and page address.
function adminSearchOrderReadFilters() {
  const adminSearchOrderFormFilterList = [];
  const adminSearchOrderAppliedFilters = new Map(adminSearchOrderFilterList);
  for (const adminSearchOrderFilterControl of adminSearchOrderSubmitButton.form.elements) {
    if (!adminSearchOrderFilterControl.name) {
      continue;
    }
    let adminSearchOrderFilterValue = adminSearchOrderFilterControl.value.trim();
    if (adminSearchOrderFilterControl.type === "datetime-local" && adminSearchOrderFilterValue) {
      // An unchanged control keeps its original seconds and any ambiguous local-time offset.
      const adminSearchOrderSavedTime = adminSearchOrderAppliedFilters.get(adminSearchOrderFilterControl.name);
      if (sharedFormatTime(adminSearchOrderSavedTime).replace(" ", "T") === adminSearchOrderFilterValue) {
        adminSearchOrderFilterValue = adminSearchOrderSavedTime;
      } else {
        const adminSearchOrderLocalTime = new Date(adminSearchOrderFilterValue);
        if (!Number.isNaN(adminSearchOrderLocalTime.getTime())) {
          adminSearchOrderFilterValue = adminSearchOrderLocalTime.toISOString();
        }
      }
    }
    adminSearchOrderFormFilterList.push([adminSearchOrderFilterControl.name, adminSearchOrderFilterValue]);
  }
  return adminSearchOrderFormFilterList;
}

// What it does: Clear every order filter.
// Input: None.
// Output: An unfiltered search from the first page.
function adminSearchOrderClearFilters() {
  adminSearchOrderSubmitButton.form.reset();
  adminSearchOrder(1, adminSearchOrderReadFilters());
}

// What it does: Draw the orders, or the empty message when no order matches.
// Input: The search response containing the rows and page counts.
// Output: The current list, accessible count line and pager.
function adminSearchOrderRenderRows(adminSearchOrderResponse) {
  const adminSearchOrderRowList = adminSearchOrderResponse.adminSearchOrderRowList;
  const adminSearchOrderTotalRowCount = adminSearchOrderResponse.adminSearchOrderTotalRowCount;
  adminSearchOrderTotalRowCountText.textContent = adminSearchOrderTotalRowCount
    + (adminSearchOrderTotalRowCount === 1 ? " order" : " orders");
  adminSearchOrderResultTable.parentElement.hidden = adminSearchOrderRowList.length === 0;
  adminSearchOrderEmptyMessage.hidden = adminSearchOrderRowList.length > 0;
  adminSearchOrderEmptyMessage.querySelector("button").hidden = !adminSearchOrderFilterList
    .some(([, adminSearchOrderFilterValue]) => adminSearchOrderFilterValue !== "");
  adminSearchOrderResultTableBody.replaceChildren(...adminSearchOrderRowList.map(adminSearchOrderBuildRow));
  sharedBuildPager(adminSearchOrderPager, adminSearchOrderResponse.adminSearchOrderPageNumber,
    adminSearchOrderResponse.adminSearchOrderTotalPageCount, adminSearchOrder);
  adminSearchOrderPager.hidden = false;
}

// What it does: Draw a linked order reference and the three independent state columns.
// Input: An order search row.
// Output: One read-only Orders table row.
function adminSearchOrderBuildRow(adminSearchOrderRow) {
  const adminSearchOrderTableRow = document.createElement("tr");
  const adminSearchOrderReferenceCell = document.createElement("th");
  const adminViewOrderOpenLink = document.createElement("a");
  adminViewOrderOpenLink.id = "adminViewOrderOpenLink-" + adminSearchOrderRow.orderID;
  adminViewOrderOpenLink.href = "admin-order-detail.html?orderID=" + encodeURIComponent(adminSearchOrderRow.orderID);
  adminViewOrderOpenLink.textContent = adminSearchOrderRow.orderReference;
  adminSearchOrderReferenceCell.scope = "row";
  adminSearchOrderReferenceCell.append(adminViewOrderOpenLink);
  if (adminSearchOrderRow.adminSearchOrderHasOpenCase) {
    adminSearchOrderReferenceCell.append(" ", adminBuildFlag("Open case", "problem"));
  }
  adminSearchOrderTableRow.append(adminSearchOrderReferenceCell);

  // Place plain values in the same column order as the sample.
  for (const [adminSearchOrderCellValue, adminSearchOrderCellClass] of [
    [adminSearchOrderRow.adminSearchOrderClientUsername, ""],
    [sharedFormatTime(adminSearchOrderRow.createdTime), "shared-table-time"],
    [sharedFormatMoney(adminSearchOrderRow.totalAmount), "shared-table-number"]
  ]) {
    const adminSearchOrderCell = document.createElement("td");
    adminSearchOrderCell.className = adminSearchOrderCellClass;
    adminSearchOrderCell.textContent = adminSearchOrderCellValue || "-";
    adminSearchOrderTableRow.append(adminSearchOrderCell);
  }
  for (const adminSearchOrderStatusWord of [adminSearchOrderRow.orderStatus,
    adminSearchOrderRow.adminSearchOrderLatestPaymentStatus, adminSearchOrderRow.adminSearchOrderLatestRefundStatus]) {
    const adminSearchOrderStatusCell = document.createElement("td");
    adminSearchOrderStatusCell.append(
      adminSearchOrderStatusWord ? adminBuildStatusBadge(adminSearchOrderStatusWord) : "-"
    );
    adminSearchOrderTableRow.append(adminSearchOrderStatusCell);
  }
  return adminSearchOrderTableRow;
}
