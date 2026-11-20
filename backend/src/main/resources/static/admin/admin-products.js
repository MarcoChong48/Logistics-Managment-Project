"use strict";

// ===== SECTION 1: PAGE ELEMENTS =====
const adminSearchProductKeywordInput = document.getElementById("adminSearchProductKeywordInput");
const adminSearchProductListingStatusSelect = document.getElementById("adminSearchProductListingStatusSelect");
const adminSearchProductIsLowStockOnlyCheckbox = document.getElementById("adminSearchProductIsLowStockOnlyCheckbox");
const adminSearchProductSubmitButton = document.getElementById("adminSearchProductSubmitButton");
const adminSearchProductClearButton = document.getElementById("adminSearchProductClearButton");
const adminSearchProductResultTable = document.getElementById("adminSearchProductResultTable");
const adminSearchProductResultTableBody = document.getElementById("adminSearchProductResultTableBody");
const adminSearchProductEmptyMessage = document.getElementById("adminSearchProductEmptyMessage");
const adminSearchProductTotalRowCountText = document.getElementById("adminSearchProductTotalRowCountText");
const adminSearchProductPager = document.getElementById("adminSearchProductPager");
const adminDownloadProductCSVButton = document.getElementById("adminDownloadProductCSVButton");

const adminCreateProductOpenButton = document.getElementById("adminCreateProductOpenButton");
const adminCreateProductDialog = document.getElementById("adminCreateProductDialog");
const adminCreateProductSKUInput = document.getElementById("adminCreateProductSKUInput");
const adminCreateProductNameInput = document.getElementById("adminCreateProductNameInput");
const adminCreateProductCurrentPriceInput = document.getElementById("adminCreateProductCurrentPriceInput");
const adminCreateProductLowStockThresholdInput = document.getElementById("adminCreateProductLowStockThresholdInput");
const adminCreateProductListingStatusSelect = document.getElementById("adminCreateProductListingStatusSelect");
const adminCreateProductMessage = document.getElementById("adminCreateProductMessage");
const adminCreateProductConfirmButton = document.getElementById("adminCreateProductConfirmButton");

const adminEditProductDialog = document.getElementById("adminEditProductDialog");
const adminEditProductDialogTitle = document.getElementById("adminEditProductDialogTitle");
const adminEditProductSKUInput = document.getElementById("adminEditProductSKUInput");
const adminEditProductNameInput = document.getElementById("adminEditProductNameInput");
const adminEditProductCurrentPriceInput = document.getElementById("adminEditProductCurrentPriceInput");
const adminEditProductLowStockThresholdInput = document.getElementById("adminEditProductLowStockThresholdInput");
const adminEditProductMessage = document.getElementById("adminEditProductMessage");
const adminEditProductConfirmButton = document.getElementById("adminEditProductConfirmButton");

const adminViewProductQRCodeDialog = document.getElementById("adminViewProductQRCodeDialog");
const adminViewProductQRCodeDialogTitle = document.getElementById("adminViewProductQRCodeDialogTitle");
const adminViewProductQRCodeImage = document.getElementById("adminViewProductQRCodeImage");
const adminViewProductQRCodeSKUText = document.getElementById("adminViewProductQRCodeSKUText");
const adminViewProductQRCodeNameText = document.getElementById("adminViewProductQRCodeNameText");
const adminViewProductQRCodePrintButton = document.getElementById("adminViewProductQRCodePrintButton");

const adminImportProductCSVOpenButton = document.getElementById("adminImportProductCSVOpenButton");
const adminImportProductCSVDialog = document.getElementById("adminImportProductCSVDialog");
const adminImportProductCSVFileInput = document.getElementById("adminImportProductCSVFileInput");
const adminImportProductCSVResultText = document.getElementById("adminImportProductCSVResultText");
const adminImportProductCSVRejectedTableBody = document.getElementById("adminImportProductCSVRejectedTableBody");
const adminImportProductCSVConfirmButton = document.getElementById("adminImportProductCSVConfirmButton");

// ===== SECTION 2: PAGE STATE =====
let adminSearchProductPageNumber = 1;
let adminSearchProductFilterList = [];
let adminSearchProductRequestCount = 0;
let adminSearchProductIsActionPending = false;
let adminEditProductID;

// ===== SECTION 3: START OF PAGE =====

// What it does: Restore the saved list after the shared frame authorizes this page.
// Input: None. Reads the filters and page number from the address.
// Output: The matching products and pager.
adminBuildPageFrame("Products").then(() => {
  if (document.querySelector("main").hidden) {
    return;
  }
  adminSearchProductKeywordInput.value = sharedReadPageParameter("adminSearchProductKeyword");
  adminSearchProductListingStatusSelect.value = sharedReadPageParameter("adminSearchProductListingStatus");
  adminSearchProductIsLowStockOnlyCheckbox.checked =
    sharedReadPageParameter("adminSearchProductIsLowStockOnly") === "true";
  sharedWatchTableScroll();
  adminSearchProductApplyFilters();
  const adminSearchProductRestoredPageNumber = Number(sharedReadPageParameter("adminSearchProductPageNumber"));
  adminSearchProduct(Number.isSafeInteger(adminSearchProductRestoredPageNumber)
    && adminSearchProductRestoredPageNumber > 0 ? adminSearchProductRestoredPageNumber : 1);
});

// What it does: Apply the filter bar and start search from its first page.
// Input: The search form's submit event.
// Output: An in-place update.
adminSearchProductSubmitButton.form.addEventListener("submit", (adminSearchProductSubmitEvent) => {
  adminSearchProductSubmitEvent.preventDefault();
  adminSearchProductApplyFilters();
  adminSearchProduct(1);
});
adminSearchProductClearButton.addEventListener("click", adminSearchProductClearFilters);
adminSearchProductEmptyMessage.querySelector("button").addEventListener("click", adminSearchProductClearFilters);
adminCreateProductOpenButton.addEventListener("click", adminCreateProductOpen);
adminCreateProductConfirmButton.form.addEventListener("submit", adminCreateProduct);
adminEditProductConfirmButton.form.addEventListener("submit", adminEditProduct);
adminImportProductCSVOpenButton.addEventListener("click", adminImportProductCSVOpen);
adminImportProductCSVConfirmButton.form.addEventListener("submit", adminImportProductCSV);
adminDownloadProductCSVButton.addEventListener("click", adminDownloadProductCSV);
adminViewProductQRCodeImage.addEventListener("load", adminViewProductQRCodeCheckImage);
adminViewProductQRCodeImage.addEventListener("error", adminViewProductQRCodeCheckImage);
adminViewProductQRCodePrintButton.addEventListener("click", adminViewProductQRCodePrint);

for (const adminSearchProductDialog of [
  adminCreateProductDialog, adminEditProductDialog, adminViewProductQRCodeDialog, adminImportProductCSVDialog
]) {
  // What it does: Close one product window.
  // Input: The window's closing button click.
  // Output: The browser restores focus to the opener.
  adminSearchProductDialog.querySelector("[data-close-dialog]").addEventListener("click", () => {
    sharedCloseDialog(adminSearchProductDialog);
  });
}

// ===== SECTION 4: adminSearchProduct =====

// What it does: Find products with the applied filters and draw the latest requested page.
// Input: The requested page number.
// Output: A refreshed list, address and pager, or a corner error.
async function adminSearchProduct(adminSearchProductRequestedPageNumber) {
  // Ignore an older response if a newer search was sent after it.
  const adminSearchProductCurrentRequestNumber = ++adminSearchProductRequestCount;
  const adminSearchProductParameterList = new URLSearchParams(adminSearchProductFilterList);
  adminSearchProductParameterList.set("adminSearchProductPageNumber", adminSearchProductRequestedPageNumber);

  try {
    const adminSearchProductResponse = await sharedSendRequest("GET", "/api/admin/products?" +
      adminSearchProductParameterList);
    if (adminSearchProductCurrentRequestNumber !== adminSearchProductRequestCount) {
      return;
    }
    adminSearchProductPageNumber = adminSearchProductResponse.adminSearchProductPageNumber;
    sharedWritePageParameter(adminSearchProductFilterList.concat([
      ["adminSearchProductPageNumber", adminSearchProductPageNumber]
    ]));
    adminSearchProductRenderRows(adminSearchProductResponse);
  } catch (adminSearchProductError) {
    if (adminSearchProductCurrentRequestNumber === adminSearchProductRequestCount) {
      sharedShowMessage("ERROR", adminSearchProductError.message);
    }
  }
}

// ===== SECTION 5: adminCreateProduct =====

// What it does: Reset the add form for a new product.
// Input: None. Uses the add form.
// Output: An empty form with Active selected.
function adminCreateProductOpen() {
  if (!adminSearchProductIsActionPending) {
    adminCreateProductConfirmButton.form.reset();
    sharedOpenDialog(adminCreateProductDialog);
  }
}

// What it does: Request a new product without supplying stock values.
// Input: The add form's submit event.
// Output: A refreshed list after success or validation in the open window.
function adminCreateProduct(adminCreateProductEvent) {
  adminCreateProductEvent.preventDefault();
  const adminCreateProductRequest = {
    adminCreateProductSKU: adminCreateProductSKUInput.value,
    adminCreateProductName: adminCreateProductNameInput.value,
    adminCreateProductCurrentPrice: adminCreateProductCurrentPriceInput.value,
    adminCreateProductLowStockThreshold: adminCreateProductLowStockThresholdInput.value,
    adminCreateProductListingStatus: adminCreateProductListingStatusSelect.value
  };
  return adminSearchProductSubmitDialog("POST", "/api/admin/products", adminCreateProductRequest,
    adminCreateProductConfirmButton, adminCreateProductMessage);
}

// ===== SECTION 6: adminEditProduct =====

// What it does: Fill the edit window from the row the Admin chose.
// Input: The selected product row.
// Output: A window with its existing SKU, name, price and threshold.
function adminEditProductOpen(adminEditProductRow) {
  adminEditProductID = adminEditProductRow.productID;
  adminEditProductDialogTitle.textContent = "Edit product " + adminEditProductRow.SKU;
  adminEditProductSKUInput.value = adminEditProductRow.SKU;
  adminEditProductNameInput.value = adminEditProductRow.name;
  adminEditProductCurrentPriceInput.value = Number(adminEditProductRow.currentPrice).toFixed(2);
  adminEditProductLowStockThresholdInput.value = adminEditProductRow.lowStockThreshold;
  sharedOpenDialog(adminEditProductDialog);
}

// What it does: Send the selected product's edited catalogue values.
// Input: The edit form's submit event.
// Output: A refreshed list after success or validation in the open window.
function adminEditProduct(adminEditProductEvent) {
  adminEditProductEvent.preventDefault();
  const adminEditProductRequest = {
    adminEditProductSKU: adminEditProductSKUInput.value,
    adminEditProductName: adminEditProductNameInput.value,
    adminEditProductCurrentPrice: adminEditProductCurrentPriceInput.value,
    adminEditProductLowStockThreshold: adminEditProductLowStockThresholdInput.value
  };
  return adminSearchProductSubmitDialog("PUT", "/api/admin/products/" + adminEditProductID, adminEditProductRequest,
    adminEditProductConfirmButton, adminEditProductMessage);
}

// ===== SECTION 7: adminPreviewProductImpact and adminDeactivateProduct =====

// What it does: Explain how deactivation affects the product and the orders already placed.
// Input: The selected product row.
// Output: Deactivation after confirmation with a reason, or no change if dismissed.
async function adminDeactivateProduct(adminDeactivateProductRow) {
  const adminDeactivateProductWebAddress = "/api/admin/products/" + adminDeactivateProductRow.productID;
  const adminPreviewProductImpactResponse = await sharedSendRequest("GET", adminDeactivateProductWebAddress + "/impact");
  const adminPreviewProductImpactOpenOrderCount = adminPreviewProductImpactResponse.adminPreviewProductImpactOpenOrderCount;
  const adminPreviewProductImpactText = adminPreviewProductImpactOpenOrderCount === 1
    ? "1 open order contains this product. It continues normally."
    : adminPreviewProductImpactOpenOrderCount + " open orders contain this product. They continue normally.";
  const adminDeactivateProductReason = await adminOpenReasonDialog(
    "Deactivate " + adminDeactivateProductRow.SKU + "?",
    "Clients can no longer order this product. Nothing is deleted and no stock number changes.\n"
      + adminPreviewProductImpactText,
    "REQUIRED_REASON", "Deactivate product", "Cancel", true);

  // Wait for the server before changing the listing badge or available actions.
  if (adminDeactivateProductReason !== null) {
    const adminDeactivateProductResponse = await sharedSendRequest("POST", adminDeactivateProductWebAddress
      + "/deactivate", { adminDeactivateProductReason });
    sharedShowMessage("SUCCESS", adminDeactivateProductResponse.message);
    await adminSearchProduct(adminSearchProductPageNumber);
  }
}

// ===== SECTION 8: adminActivateProduct =====

// What it does: Confirm that customers can order the selected product again.
// Input: The selected inactive product row.
// Output: Activation after confirmation, or no change if dismissed.
async function adminActivateProduct(adminActivateProductRow) {
  const adminActivateProductConfirmation = await adminOpenReasonDialog(
    "Activate " + adminActivateProductRow.SKU + "?", "Clients can order this product again.",
    "NONE", "Activate product", "Cancel", false);
  if (adminActivateProductConfirmation !== null) {
    const adminActivateProductWebAddress = "/api/admin/products/" + adminActivateProductRow.productID + "/activate";
    const adminActivateProductResponse = await sharedSendRequest("POST", adminActivateProductWebAddress);
    sharedShowMessage("SUCCESS", adminActivateProductResponse.message);
    await adminSearchProduct(adminSearchProductPageNumber);
  }
}

// ===== SECTION 9: adminViewProductQRCode =====

// What it does: Fill the label window with the selected product and its real QR image.
// Input: The selected product row.
// Output: A QR label window with a working Print label button once the image loads.
function adminViewProductQRCode(adminViewProductQRCodeRow) {
  adminViewProductQRCodeDialogTitle.textContent = "QR label of " + adminViewProductQRCodeRow.SKU;
  adminViewProductQRCodeSKUText.textContent = adminViewProductQRCodeRow.SKU;
  adminViewProductQRCodeNameText.textContent = adminViewProductQRCodeRow.name;
  adminViewProductQRCodeImage.alt = "QR code for SKU " + adminViewProductQRCodeRow.SKU;
  adminViewProductQRCodeImage.src = "/api/admin/products/" + adminViewProductQRCodeRow.productID + "/qr-code";
  adminViewProductQRCodeCheckImage();
  sharedOpenDialog(adminViewProductQRCodeDialog);
}

// What it does: Update the print button when the label picture is available.
// Input: None. Reads the label image's load state.
// Output: The print button stays disabled until there is a picture to print.
function adminViewProductQRCodeCheckImage() {
  adminViewProductQRCodePrintButton.disabled = !adminViewProductQRCodeImage.complete
    || adminViewProductQRCodeImage.naturalWidth === 0;
}

// What it does: Open the browser print view with just the label visible.
// Input: None.
// Output: The dialog stays open and the print style is cleared when printing ends.
function adminViewProductQRCodePrint() {
  document.documentElement.classList.add("shared-print-only-label");

  // What it does: Restore the ordinary page after printing or cancelling.
  // Input: None.
  // Output: The temporary print class is removed.
  window.addEventListener("afterprint", () => {
    document.documentElement.classList.remove("shared-print-only-label");
  }, { once: true });
  window.print();
}

// ===== SECTION 10: adminImportProductCSV =====

// What it does: Reset the upload window for a new file.
// Input: None.
// Output: A fresh import window.
function adminImportProductCSVOpen() {
  if (!adminSearchProductIsActionPending) {
    adminImportProductCSVConfirmButton.form.reset();
    adminImportProductCSVRenderRejectedLines([]);
    sharedOpenDialog(adminImportProductCSVDialog);
  }
}

// What it does: Upload one CSV file and keep the result in the import window.
// Input: The import form's submit event.
// Output: An import summary and rejected lines, or an inline error.
async function adminImportProductCSV(adminImportProductCSVEvent) {
  adminImportProductCSVEvent.preventDefault();
  if (adminSearchProductIsActionPending || adminImportProductCSVConfirmButton.disabled) {
    return;
  }
  adminImportProductCSVResultText.replaceChildren();
  adminImportProductCSVRenderRejectedLines([]);
  if (adminImportProductCSVFileInput.files.length === 0) {
    sharedShowInlineMessage(adminImportProductCSVResultText, "ERROR", "Choose a CSV file.");
    adminImportProductCSVFileInput.focus();
    return;
  }

  // Keep other product writes from opening while the file is being processed.
  adminSearchProductIsActionPending = true;
  try {
    const adminImportProductCSVRequest = new FormData(adminImportProductCSVConfirmButton.form);
    const adminImportProductCSVResponse = await sharedSendRequest("POST", "/api/admin/products/import",
      adminImportProductCSVRequest, adminImportProductCSVConfirmButton);
    const adminImportProductCSVCreatedCount = adminImportProductCSVResponse.adminImportProductCSVCreatedCount;
    const adminImportProductCSVRejectedCount = adminImportProductCSVResponse.adminImportProductCSVRejectedCount;
    const adminImportProductCSVSummary = adminImportProductCSVCreatedCount
      + (adminImportProductCSVCreatedCount === 1 ? " product added, " : " products added, ")
      + adminImportProductCSVRejectedCount
      + (adminImportProductCSVRejectedCount === 1 ? " line rejected." : " lines rejected.");
    adminImportProductCSVFileInput.value = "";
    if (adminImportProductCSVDialog.open) {
      sharedShowInlineMessage(adminImportProductCSVResultText, "SUCCESS", adminImportProductCSVSummary);
      adminImportProductCSVRenderRejectedLines(adminImportProductCSVResponse.adminImportProductCSVRejectedLineList);
    } else {
      sharedShowMessage("SUCCESS", adminImportProductCSVSummary);
    }
    await adminSearchProduct(adminSearchProductPageNumber);
  } catch (adminImportProductCSVError) {
    adminSearchProductShowDialogError(adminImportProductCSVDialog, adminImportProductCSVResultText,
      adminImportProductCSVError);
  } finally {
    adminSearchProductIsActionPending = false;
  }
}

// ===== SECTION 11: adminDownloadProductCSV =====

// What it does: Download every product matching the active search.
// Input: None. Uses the applied filters without the page number.
// Output: A CSV download or a corner error.
async function adminDownloadProductCSV() {
  const adminDownloadProductCSVParameterList = new URLSearchParams(adminSearchProductFilterList);
  adminDownloadProductCSVButton.disabled = true;
  try {
    await adminDownloadCSV("/api/admin/products/csv?" + adminDownloadProductCSVParameterList);
  } finally {
    adminDownloadProductCSVButton.disabled = false;
  }
}

// ===== SECTION 12: HELPER FUNCTIONS =====

// What it does: Capture the controls for searching, paging and downloading the same products.
// Input: None. Reads the live filter bar.
// Output: The applied filter names and values.
function adminSearchProductApplyFilters() {
  adminSearchProductFilterList = [
    ["adminSearchProductKeyword", adminSearchProductKeywordInput.value.trim()],
    ["adminSearchProductListingStatus", adminSearchProductListingStatusSelect.value],
    ["adminSearchProductIsLowStockOnly", adminSearchProductIsLowStockOnlyCheckbox.checked ? "true" : ""]
  ];
}

// What it does: Remove every search filter.
// Input: None.
// Output: An unfiltered search from the first page.
function adminSearchProductClearFilters() {
  adminSearchProductSubmitButton.form.reset();
  adminSearchProductApplyFilters();
  adminSearchProduct(1);
}

// What it does: Display the latest product results.
// Input: The list answer, including its row and page counts.
// Output: A populated table or empty state, and updated pager and count.
function adminSearchProductRenderRows(adminSearchProductResponse) {
  const adminSearchProductFocusedControl = document.activeElement;
  const adminSearchProductRowList = adminSearchProductResponse.adminSearchProductRowList;
  const adminSearchProductTotalRowCount = adminSearchProductResponse.adminSearchProductTotalRowCount;
  adminSearchProductTotalRowCountText.textContent = adminSearchProductTotalRowCount
    + (adminSearchProductTotalRowCount === 1 ? " product" : " products");
  adminSearchProductResultTable.parentElement.hidden = adminSearchProductRowList.length === 0;
  adminSearchProductEmptyMessage.hidden = adminSearchProductRowList.length > 0;
  adminSearchProductEmptyMessage.querySelector("button").hidden = !adminSearchProductFilterList
    .some(([, adminSearchProductFilterValue]) => adminSearchProductFilterValue !== "");
  adminSearchProductResultTableBody.replaceChildren(...adminSearchProductRowList.map(adminSearchProductBuildRow));
  sharedBuildPager(adminSearchProductPager, adminSearchProductPageNumber,
    adminSearchProductResponse.adminSearchProductTotalPageCount, adminSearchProduct);
  adminSearchProductPager.hidden = false;

  // Preserve the focused menu trigger when the server refreshes that row.
  if (adminSearchProductFocusedControl.id.startsWith("adminBuildActionMenuButton-")) {
    const adminSearchProductReplacementControl = document.getElementById(adminSearchProductFocusedControl.id);
    (adminSearchProductReplacementControl ?? adminSearchProductKeywordInput).focus({ preventScroll: true });
  }
}

// What it does: Build catalogue, read-only stock and actions columns for one product.
// Input: The product row returned by the server.
// Output: One table row with status, flags and its own Actions menu.
function adminSearchProductBuildRow(adminSearchProductRow) {
  const adminSearchProductTableRow = document.createElement("tr");
  const adminSearchProductSKUCell = document.createElement("th");
  adminSearchProductSKUCell.scope = "row";
  adminSearchProductSKUCell.textContent = adminSearchProductRow.SKU;
  const adminSearchProductNameCell = document.createElement("td");
  adminSearchProductNameCell.className = "shared-table-wrap";
  adminSearchProductNameCell.textContent = adminSearchProductRow.name;
  if (adminSearchProductRow.adminSearchProductIsLowStock) {
    adminSearchProductNameCell.append(" ", adminBuildFlag("Low stock", "waiting"));
  }
  if (adminSearchProductRow.adminSearchProductReservationShortfall > 0) {
    adminSearchProductNameCell.append(" ", adminBuildFlag(
      "Shortfall " + adminSearchProductRow.adminSearchProductReservationShortfall, "problem"));
  }
  adminSearchProductTableRow.append(adminSearchProductSKUCell, adminSearchProductNameCell);

  // Draw the read-only stock amounts and threshold in the sample's column order.
  for (const adminSearchProductNumberText of [
    sharedFormatMoney(adminSearchProductRow.currentPrice), adminSearchProductRow.physicalStock,
    adminSearchProductRow.reservedStock, adminSearchProductRow.adminSearchProductAvailableStock,
    adminSearchProductRow.lowStockThreshold
  ]) {
    const adminSearchProductNumberCell = document.createElement("td");
    adminSearchProductNumberCell.className = "shared-table-number";
    adminSearchProductNumberCell.textContent = adminSearchProductNumberText;
    adminSearchProductTableRow.append(adminSearchProductNumberCell);
  }

  const adminSearchProductStatusCell = document.createElement("td");
  adminSearchProductStatusCell.append(adminBuildStatusBadge(adminSearchProductRow.listingStatus));
  const adminSearchProductActionsCell = document.createElement("td");
  adminSearchProductActionsCell.className = "shared-table-actions";
  const adminSearchProductActionList = [
    adminSearchProductBuildAction(adminSearchProductRow, "Edit", "adminEditProduct", false, adminEditProductOpen),
    adminSearchProductBuildAction(adminSearchProductRow, "Show QR code", "adminViewProductQRCode",
      false, adminViewProductQRCode)
  ];
  if (adminSearchProductRow.listingStatus === "ACTIVE") {
    adminSearchProductActionList.push(adminSearchProductBuildAction(adminSearchProductRow, "Deactivate",
      "adminDeactivateProduct", true, adminDeactivateProduct));
  } else if (adminSearchProductRow.listingStatus === "INACTIVE") {
    adminSearchProductActionList.push(adminSearchProductBuildAction(adminSearchProductRow, "Activate",
      "adminActivateProduct", false, adminActivateProduct));
  }
  adminSearchProductActionsCell.append(adminBuildActionMenu(adminSearchProductRow.productID, adminSearchProductRow.SKU,
    adminSearchProductActionList));
  adminSearchProductTableRow.append(adminSearchProductStatusCell, adminSearchProductActionsCell);
  return adminSearchProductTableRow;
}

// What it does: Bind the chosen product to one possible menu action.
// Input: A product row, action label, plan name, danger state and handler.
// Output: A menu descriptor with an ID unique to the selected product.
function adminSearchProductBuildAction(adminSearchProductRow, adminSearchProductActionLabel,
  adminSearchProductActionName, adminSearchProductActionIsDangerous, adminSearchProductAction) {
  return {
    adminBuildActionMenuEntryText: adminSearchProductActionLabel,
    adminBuildActionMenuEntryID: adminSearchProductActionName + "OpenButton-" + adminSearchProductRow.productID,
    adminBuildActionMenuEntryIsDangerous: adminSearchProductActionIsDangerous,

    // What it does: Prevent another row action while a product request or confirmation is pending.
    // Input: None. Uses the chosen row and handler.
    // Output: A dialog or action result, or a visible error.
    async adminBuildActionMenuEntryAction() {
      if (adminSearchProductIsActionPending) {
        return;
      }
      adminSearchProductIsActionPending = true;
      try {
        await adminSearchProductAction(adminSearchProductRow);
      } catch (adminSearchProductActionError) {
        sharedShowMessage("ERROR", adminSearchProductActionError.message);
      } finally {
        adminSearchProductIsActionPending = false;
      }
    }
  };
}

// What it does: Submit the add or edit form with the common request helper.
// Input: The request method, address and body, and the form's confirmation button and message place.
// Output: A refreshed list after success, or an error in the window if still open.
async function adminSearchProductSubmitDialog(adminSearchProductMethod, adminSearchProductWebAddress,
  adminSearchProductRequest, adminSearchProductConfirmButton, adminSearchProductMessage) {
  if (adminSearchProductIsActionPending || adminSearchProductConfirmButton.disabled) {
    return;
  }
  const adminSearchProductDialog = adminSearchProductConfirmButton.closest("dialog");
  adminSearchProductMessage.replaceChildren();
  adminSearchProductIsActionPending = true;
  try {
    const adminSearchProductResponse = await sharedSendRequest(adminSearchProductMethod, adminSearchProductWebAddress,
      adminSearchProductRequest, adminSearchProductConfirmButton);
    if (adminSearchProductDialog.open) {
      sharedCloseDialog(adminSearchProductDialog);
    }
    sharedShowMessage("SUCCESS", adminSearchProductResponse.message);
    await adminSearchProduct(adminSearchProductPageNumber);
  } catch (adminSearchProductError) {
    adminSearchProductShowDialogError(adminSearchProductDialog, adminSearchProductMessage, adminSearchProductError);
  } finally {
    adminSearchProductIsActionPending = false;
  }
}

// What it does: Put a refused request's message where the Admin can read it.
// Input: The dialog, its message place and the request error.
// Output: An inline error if the window is open, otherwise a corner error.
function adminSearchProductShowDialogError(adminSearchProductDialog, adminSearchProductMessage,
  adminSearchProductError) {
  if (adminSearchProductDialog.open) {
    sharedShowInlineMessage(adminSearchProductMessage, "ERROR", adminSearchProductError.message);
  } else {
    sharedShowMessage("ERROR", adminSearchProductError.message);
  }
}

// What it does: Replace the rejected lines from the most recent import.
// Input: The list of line numbers and explanations.
// Output: A table of rejections, hidden when the list is empty.
function adminImportProductCSVRenderRejectedLines(adminImportProductCSVRejectedLineList) {
  adminImportProductCSVRejectedTableBody.replaceChildren();
  for (const adminImportProductCSVRejectedLine of adminImportProductCSVRejectedLineList) {
    const adminImportProductCSVRejectedRow = document.createElement("tr");
    const adminImportProductCSVLineNumberCell = document.createElement("th");
    adminImportProductCSVLineNumberCell.scope = "row";
    adminImportProductCSVLineNumberCell.className = "shared-table-number";
    adminImportProductCSVLineNumberCell.textContent = adminImportProductCSVRejectedLine.adminImportProductCSVLineNumber;
    const adminImportProductCSVRejectReasonCell = document.createElement("td");
    adminImportProductCSVRejectReasonCell.className = "shared-table-wrap";
    adminImportProductCSVRejectReasonCell.textContent = adminImportProductCSVRejectedLine.adminImportProductCSVRejectReason;
    adminImportProductCSVRejectedRow.append(adminImportProductCSVLineNumberCell, adminImportProductCSVRejectReasonCell);
    adminImportProductCSVRejectedTableBody.append(adminImportProductCSVRejectedRow);
  }
  adminImportProductCSVRejectedTableBody.closest(".shared-table-scroll").hidden =
    adminImportProductCSVRejectedLineList.length === 0;
}
