"use strict";

// Show failed deliveries, client requests and package responsibility in the Admin work lists.

// ===== SECTION 1: PAGE ELEMENTS =====
const adminSearchFailedDeliveryCaseFilterSelect = document.getElementById("adminSearchFailedDeliveryCaseFilterSelect");
const adminSearchFailedDeliveryCaseSubmitButton = document.getElementById("adminSearchFailedDeliveryCaseSubmitButton");
const adminSearchFailedDeliveryCaseResultTableBody = document.getElementById("adminSearchFailedDeliveryCaseResultTableBody");
const adminSearchFailedDeliveryCaseEmptyMessage = document.getElementById("adminSearchFailedDeliveryCaseEmptyMessage");
const adminSearchFailedDeliveryCaseTotalRowCountText = document.getElementById("adminSearchFailedDeliveryCaseTotalRowCountText");
const adminSearchFailedDeliveryCaseSettingText = document.getElementById("adminSearchFailedDeliveryCaseSettingText");
const adminSearchFailedDeliveryCasePager = document.getElementById("adminSearchFailedDeliveryCasePager");
const adminDownloadFailedDeliveryCaseCSVButton = document.getElementById("adminDownloadFailedDeliveryCaseCSVButton");
const adminSearchHeldPackageResultTableBody = document.getElementById("adminSearchHeldPackageResultTableBody");
const adminSearchHeldPackageEmptyMessage = document.getElementById("adminSearchHeldPackageEmptyMessage");
const adminSearchHeldPackageTotalRowCountText = document.getElementById("adminSearchHeldPackageTotalRowCountText");
const adminSearchHeldPackagePager = document.getElementById("adminSearchHeldPackagePager");
const adminRecordClientInstructionDialog = document.getElementById("adminRecordClientInstructionDialog");
const adminRecordClientInstructionOrderReferenceText = document.getElementById("adminRecordClientInstructionOrderReferenceText");
const adminRecordClientInstructionTypeSelect = document.getElementById("adminRecordClientInstructionTypeSelect");
const adminRecordClientInstructionIdentityIsConfirmedCheckbox =
  document.getElementById("adminRecordClientInstructionIdentityIsConfirmedCheckbox");
const adminRecordClientInstructionIdentityCheckMethodInput =
  document.getElementById("adminRecordClientInstructionIdentityCheckMethodInput");
const adminRecordClientInstructionMessage = document.getElementById("adminRecordClientInstructionMessage");
const adminRecordClientInstructionRefreshButton = document.getElementById("adminRecordClientInstructionRefreshButton");
const adminRecordClientInstructionCancelButton = document.getElementById("adminRecordClientInstructionCancelButton");
const adminRecordClientInstructionConfirmButton = document.getElementById("adminRecordClientInstructionConfirmButton");
const adminReassignDeliveryDialog = document.getElementById("adminReassignDeliveryDialog");
const adminReassignDeliveryOrderReferenceText = document.getElementById("adminReassignDeliveryOrderReferenceText");
const adminReassignDeliveryCurrentHandlerText = document.getElementById("adminReassignDeliveryCurrentHandlerText");
const adminReassignDeliveryCurrentResponsibleText = document.getElementById("adminReassignDeliveryCurrentResponsibleText");
const adminReassignDeliveryNewWorkerSelect = document.getElementById("adminReassignDeliveryNewWorkerSelect");
const adminReassignDeliveryReasonInput = document.getElementById("adminReassignDeliveryReasonInput");
const adminReassignDeliveryCancelButton = document.getElementById("adminReassignDeliveryCancelButton");
const adminReassignDeliveryConfirmButton = document.getElementById("adminReassignDeliveryConfirmButton");

// ===== SECTION 2: PAGE STATE =====
let adminSearchFailedDeliveryCaseRequestCount = 0;
let adminSearchFailedDeliveryCaseAppliedFilter = "ALL";
let adminSearchFailedDeliveryCaseCurrentPageNumber = 1;
let adminSearchHeldPackageRequestCount = 0;
let adminRecordClientInstructionCase;
let adminRecordClientInstructionRow;
let adminRecordClientInstructionIsBusy = false;
let adminRecordClientInstructionCanSave = false;
let adminReassignDeliveryPackage;
let adminReassignDeliveryRow;
let adminReassignDeliveryIsBusy = false;

// ===== SECTION 3: START OF PAGE =====

// What it does: Restore the two Delivery cases lists after checking the Admin session.
// Input: The filters and pages in the browser address.
// Output: The failed-delivery and held-package searches.
adminBuildPageFrame("Delivery cases").then(() => {
  if (document.querySelector("main").hidden) {
    return;
  }

  adminSearchFailedDeliveryCaseFilterSelect.value = sharedReadPageParameter("adminSearchFailedDeliveryCaseFilter");
  if (!adminSearchFailedDeliveryCaseFilterSelect.value) {
    adminSearchFailedDeliveryCaseFilterSelect.value = adminSearchFailedDeliveryCaseAppliedFilter;
  }
  sharedWatchTableScroll();
  adminSearchFailedDeliveryCase(adminReadDeliveryPageNumber("adminSearchFailedDeliveryCasePageNumber"),
    adminSearchFailedDeliveryCaseFilterSelect.value);
  adminSearchHeldPackage(adminReadDeliveryPageNumber("adminSearchHeldPackagePageNumber"));
});

// What it does: Apply the failed-delivery filter in the search bar.
// Input: A submit event from the failed-delivery card.
// Output: The first page under the new filter.
adminSearchFailedDeliveryCaseSubmitButton.form.addEventListener("submit", (adminSearchFailedDeliveryCaseEvent) => {
  adminSearchFailedDeliveryCaseEvent.preventDefault();
  adminSearchFailedDeliveryCase(1, adminSearchFailedDeliveryCaseFilterSelect.value);
});
adminDownloadFailedDeliveryCaseCSVButton.addEventListener("click", adminDownloadFailedDeliveryCaseCSV);
adminRecordClientInstructionIdentityIsConfirmedCheckbox.addEventListener("change", adminCheckRecordClientInstructionInput);
adminRecordClientInstructionIdentityCheckMethodInput.addEventListener("input", adminCheckRecordClientInstructionInput);
adminRecordClientInstructionConfirmButton.form.addEventListener("submit", adminRecordClientInstruction);
adminRecordClientInstructionRefreshButton.addEventListener("click", adminRefreshClientInstruction);
adminRecordClientInstructionCancelButton.addEventListener("click", () => sharedCloseDialog(adminRecordClientInstructionDialog));
adminRecordClientInstructionDialog.addEventListener("cancel", adminKeepDeliveryDecisionOpen);
adminReassignDeliveryNewWorkerSelect.addEventListener("change", adminCheckReassignDeliveryInput);
adminReassignDeliveryReasonInput.addEventListener("input", adminCheckReassignDeliveryInput);
adminReassignDeliveryConfirmButton.form.addEventListener("submit", adminReassignDelivery);
adminReassignDeliveryCancelButton.addEventListener("click", () => sharedCloseDialog(adminReassignDeliveryDialog));
adminReassignDeliveryDialog.addEventListener("cancel", adminKeepDeliveryDecisionOpen);

// ===== SECTION 4: adminSearchFailedDeliveryCase and adminDownloadFailedDeliveryCaseCSV =====

// What it does: Read a page of the failed-delivery list without changing the view.
// Input: The filter word and page number.
// Output: The server's page of matching case rows.
function adminReadFailedDeliveryCasePage(adminReadFailedDeliveryCasePageFilter, adminReadFailedDeliveryCasePageNumber) {
  const adminReadFailedDeliveryCasePageParameters = new URLSearchParams({
    adminSearchFailedDeliveryCaseFilter: adminReadFailedDeliveryCasePageFilter,
    adminSearchFailedDeliveryCasePageNumber: adminReadFailedDeliveryCasePageNumber
  });
  return sharedSendRequest("GET", "/api/admin/failed-delivery-cases?" + adminReadFailedDeliveryCasePageParameters);
}

// What it does: Show one page of failed deliveries and remember the applied filter.
// Input: The page number and optional filter word.
// Output: Case rows, count and pager or a corner error.
async function adminSearchFailedDeliveryCase(adminSearchFailedDeliveryCasePageNumber,
  adminSearchFailedDeliveryCaseFilter = adminSearchFailedDeliveryCaseAppliedFilter) {
  const adminSearchFailedDeliveryCaseRequestNumber = ++adminSearchFailedDeliveryCaseRequestCount;
  try {
    const adminSearchFailedDeliveryCaseResponse = await adminReadFailedDeliveryCasePage(
      adminSearchFailedDeliveryCaseFilter, adminSearchFailedDeliveryCasePageNumber);
    if (adminSearchFailedDeliveryCaseRequestNumber !== adminSearchFailedDeliveryCaseRequestCount) {
      return;
    }

    // Remember the successful search so the address, pager and CSV match the rows.
    adminSearchFailedDeliveryCaseAppliedFilter = adminSearchFailedDeliveryCaseFilter;
    adminSearchFailedDeliveryCaseCurrentPageNumber = adminSearchFailedDeliveryCaseResponse.adminSearchFailedDeliveryCasePageNumber;
    adminShowFailedDeliveryCaseSettings(adminSearchFailedDeliveryCaseResponse);
    sharedWritePageParameter([
      ["adminSearchFailedDeliveryCaseFilter", adminSearchFailedDeliveryCaseAppliedFilter],
      ["adminSearchFailedDeliveryCasePageNumber", adminSearchFailedDeliveryCaseCurrentPageNumber]
    ]);
    const adminSearchFailedDeliveryCaseRowList = adminSearchFailedDeliveryCaseResponse.adminSearchFailedDeliveryCaseRowList;
    adminSearchFailedDeliveryCaseResultTableBody.replaceChildren(...adminSearchFailedDeliveryCaseRowList.map(adminBuildFailedDeliveryCaseRow));
    adminSearchFailedDeliveryCaseResultTableBody.closest(".shared-table-scroll").hidden = !adminSearchFailedDeliveryCaseRowList.length;
    adminSearchFailedDeliveryCaseEmptyMessage.hidden = Boolean(adminSearchFailedDeliveryCaseRowList.length);
    adminSearchFailedDeliveryCaseTotalRowCountText.textContent = adminSearchFailedDeliveryCaseResponse.adminSearchFailedDeliveryCaseTotalRowCount;
    adminSearchFailedDeliveryCasePager.hidden = !adminSearchFailedDeliveryCaseRowList.length;
    sharedBuildPager(adminSearchFailedDeliveryCasePager, adminSearchFailedDeliveryCaseCurrentPageNumber,
      adminSearchFailedDeliveryCaseResponse.adminSearchFailedDeliveryCaseTotalPageCount, adminSearchFailedDeliveryCase);
    adminDownloadFailedDeliveryCaseCSVButton.disabled = false;
  } catch (adminSearchFailedDeliveryCaseError) {
    if (adminSearchFailedDeliveryCaseRequestNumber === adminSearchFailedDeliveryCaseRequestCount) {
      sharedShowMessage("ERROR", adminSearchFailedDeliveryCaseError.message);
    }
  }
}

// What it does: Explain the actual review thresholds used by the server.
// Input: The successful failed-delivery search response, even when it has no rows.
// Output: The settings strip with the three values and their correct singular or plural words.
function adminShowFailedDeliveryCaseSettings(adminShowFailedDeliveryCaseSettingsResponse) {
  const adminShowFailedDeliveryCaseSettingsFailureCount =
    adminShowFailedDeliveryCaseSettingsResponse.adminSearchFailedDeliveryCaseReviewFailureCount;
  const adminShowFailedDeliveryCaseSettingsReminderDays =
    adminShowFailedDeliveryCaseSettingsResponse.adminSearchFailedDeliveryCaseReminderDays;
  const adminShowFailedDeliveryCaseSettingsFinalReviewDays =
    adminShowFailedDeliveryCaseSettingsResponse.adminSearchFailedDeliveryCaseFinalReviewDays;
  adminSearchFailedDeliveryCaseSettingText.textContent =
    "Review at " + adminShowFailedDeliveryCaseSettingsFailureCount
    + (adminShowFailedDeliveryCaseSettingsFailureCount === 1 ? " failure." : " failures.")
    + " Reminder due after " + adminShowFailedDeliveryCaseSettingsReminderDays
    + (adminShowFailedDeliveryCaseSettingsReminderDays === 1 ? " day." : " days.")
    + " Final review after " + adminShowFailedDeliveryCaseSettingsFinalReviewDays
    + (adminShowFailedDeliveryCaseSettingsFinalReviewDays === 1 ? " day." : " days.")
    + " Nothing is cancelled automatically.";
  adminSearchFailedDeliveryCaseSettingText.parentElement.hidden = false;
}

// What it does: Download the failed-delivery cases matching the displayed filter.
// Input: The last successfully applied filter.
// Output: A CSV file or a corner error.
async function adminDownloadFailedDeliveryCaseCSV() {
  adminDownloadFailedDeliveryCaseCSVButton.disabled = true;
  try {
    await adminDownloadCSV("/api/admin/failed-delivery-cases/csv?" + new URLSearchParams({
      adminSearchFailedDeliveryCaseFilter: adminSearchFailedDeliveryCaseAppliedFilter
    }));
  } catch (adminDownloadFailedDeliveryCaseCSVError) {
    sharedShowMessage("ERROR", adminDownloadFailedDeliveryCaseCSVError.message);
  } finally {
    adminDownloadFailedDeliveryCaseCSVButton.disabled = false;
  }
}

// ===== SECTION 5: adminSearchHeldPackage =====

// What it does: Draw one page of packages still in a worker's custody.
// Input: The requested page number.
// Output: Held packages, count and pager or a corner error.
async function adminSearchHeldPackage(adminSearchHeldPackagePageNumber) {
  const adminSearchHeldPackageRequestNumber = ++adminSearchHeldPackageRequestCount;
  try {
    const adminSearchHeldPackageResponse = await sharedSendRequest("GET", "/api/admin/held-packages?"
      + new URLSearchParams({adminSearchHeldPackagePageNumber}));
    if (adminSearchHeldPackageRequestNumber !== adminSearchHeldPackageRequestCount) {
      return;
    }

    // Keep this pager independent of the failed-delivery search above it.
    sharedWritePageParameter([
      ["adminSearchHeldPackagePageNumber", adminSearchHeldPackageResponse.adminSearchHeldPackagePageNumber]
    ]);
    const adminSearchHeldPackageRowList = adminSearchHeldPackageResponse.adminSearchHeldPackageRowList;
    adminSearchHeldPackageResultTableBody.replaceChildren(...adminSearchHeldPackageRowList.map(adminBuildHeldPackageRow));
    adminSearchHeldPackageResultTableBody.closest(".shared-table-scroll").hidden = !adminSearchHeldPackageRowList.length;
    adminSearchHeldPackageEmptyMessage.hidden = Boolean(adminSearchHeldPackageRowList.length);
    adminSearchHeldPackageTotalRowCountText.textContent = adminSearchHeldPackageResponse.adminSearchHeldPackageTotalRowCount;
    adminSearchHeldPackagePager.hidden = !adminSearchHeldPackageRowList.length;
    sharedBuildPager(adminSearchHeldPackagePager, adminSearchHeldPackageResponse.adminSearchHeldPackagePageNumber,
      adminSearchHeldPackageResponse.adminSearchHeldPackageTotalPageCount, adminSearchHeldPackage);
  } catch (adminSearchHeldPackageError) {
    if (adminSearchHeldPackageRequestNumber === adminSearchHeldPackageRequestCount) {
      sharedShowMessage("ERROR", adminSearchHeldPackageError.message);
    }
  }
}

// ===== SECTION 6: adminSendDeliveryReminder =====

// What it does: Send one reminder and refresh only that case's reminder state.
// Input: The case row data and its Send reminder button.
// Output: The saved reminder time and disabled action, or an error message.
async function adminSendDeliveryReminder(adminSendDeliveryReminderCase, adminSendDeliveryReminderButton) {
  let adminSendDeliveryReminderResponse;
  try {
    adminSendDeliveryReminderResponse = await sharedSendRequest("POST", "/api/admin/failed-delivery-cases/"
      + adminSendDeliveryReminderCase.orderID + "/reminder", null, adminSendDeliveryReminderButton);
  } catch (adminSendDeliveryReminderError) {
    sharedShowMessage("ERROR", adminSendDeliveryReminderError.message);
    return;
  }

  // The reminder is saved. Use the server's timestamps without reloading any decided rows.
  adminViewMenuCounter();
  adminSendDeliveryReminderButton.disabled = true;
  try {
    const adminSendDeliveryReminderPage = await adminReadFailedDeliveryCasePage(
      adminSearchFailedDeliveryCaseAppliedFilter, adminSearchFailedDeliveryCaseCurrentPageNumber);
    const adminSendDeliveryReminderSavedCase = adminSendDeliveryReminderPage.adminSearchFailedDeliveryCaseRowList
      .find((adminSendDeliveryReminderFoundCase) => adminSendDeliveryReminderFoundCase.orderID === adminSendDeliveryReminderCase.orderID);
    if (adminSendDeliveryReminderButton.isConnected && adminSendDeliveryReminderSavedCase?.adminSearchFailedDeliveryCaseNextReminderTime) {
      const adminSendDeliveryReminderRow = adminSendDeliveryReminderButton.closest("tr");
      adminSendDeliveryReminderRow.cells[2].replaceWith(adminBuildFailedDeliveryCaseStateCell(adminSendDeliveryReminderSavedCase));
      adminSendDeliveryReminderButton.closest(".shared-table-actions").append(
        adminBuildFailedDeliveryCaseReminderLine(adminSendDeliveryReminderSavedCase.adminSearchFailedDeliveryCaseNextReminderTime));
    }
    sharedShowMessage("SUCCESS", adminSendDeliveryReminderResponse.message);
  } catch {
    sharedShowMessage("INFORMATION", adminSendDeliveryReminderResponse.message
      + " Search again to see the saved reminder time.");
  }
}

// ===== SECTION 7: CLIENT REQUEST DECISIONS =====

// What it does: Confirm or reject the request named by a failed-delivery row.
// Input: The selected action, case data and the button that opened the window.
// Output: A decided row from the server response or an error.
async function adminDecideClientInstruction(adminDecideClientInstructionAction, adminDecideClientInstructionCase,
  adminDecideClientInstructionButton) {
  const adminDecideClientInstructionRow = adminDecideClientInstructionButton.closest("tr");
  const adminDecideClientInstructionReference = adminDecideClientInstructionCase.orderReference;
  const adminDecideClientInstructionRequestFact = [
    "Request", adminBuildClientInstructionText(adminDecideClientInstructionCase, true)
  ];
  const adminDecideClientInstructionIsPaid = adminDecideClientInstructionCase.adminSearchFailedDeliveryCaseOrderIsPaid;
  const adminDecideClientInstructionActionList = {
    "approve-redelivery": {
      title: "Approve redelivery of order " + adminDecideClientInstructionReference + "?",
      description: "The order becomes Ready for redelivery, and Warehouse sends the package out again. The client is told.",
      facts: [adminDecideClientInstructionRequestFact],
      confirm: "Approve redelivery", close: "Cancel", isDangerous: false, fieldMode: "OPTIONAL_NOTE",
      dialogID: "adminApproveRedeliveryDialog", fieldName: "adminApproveRedeliveryNote",
      decidedLine: "Redelivery approved by {admin}, {time}"
    },
    "approve-cancellation": {
      title: "Approve cancellation of order " + adminDecideClientInstructionReference + "?",
      description: "",
      facts: [
        ["Payment", adminDecideClientInstructionIsPaid ? "Paid" : "Nothing paid"],
        ["Outcome", adminDecideClientInstructionIsPaid
          ? "The order is cancelled for good and a full refund is requested. The client is told."
          : "The order is cancelled for good. Nothing is refunded. The client is told."]
      ],
      confirm: "Approve cancellation", close: "Close", isDangerous: true, fieldMode: "OPTIONAL_NOTE",
      dialogID: "adminApproveCancellationDialog", fieldName: "adminApproveCancellationNote",
      decidedLine: "Cancellation approved by {admin}, {time}."
    },
    reject: {
      title: "Reject the request for order " + adminDecideClientInstructionReference + "?",
      description: "The order stays as it is. The client is told the reason and can send a new request.",
      facts: [adminDecideClientInstructionRequestFact],
      confirm: "Reject request", close: "Cancel", isDangerous: false, fieldMode: "REQUIRED_REASON",
      dialogID: "adminRejectClientInstructionDialog", fieldName: "adminRejectClientInstructionReason",
      decidedLine: "Request rejected by {admin}, {time}"
    }
  };
  const adminDecideClientInstructionDecision = adminDecideClientInstructionActionList[adminDecideClientInstructionAction];
  const adminDecideClientInstructionAnswer = await adminOpenReasonDialog(
    adminDecideClientInstructionDecision.title, adminDecideClientInstructionDecision.description,
    adminDecideClientInstructionDecision.fieldMode, adminDecideClientInstructionDecision.confirm,
    adminDecideClientInstructionDecision.close, adminDecideClientInstructionDecision.isDangerous,
    adminDecideClientInstructionDecision.facts, adminDecideClientInstructionDecision.dialogID);
  if (adminDecideClientInstructionAnswer === null) {
    return;
  }

  // Do not redraw the list while the Admin is still reading this decision.
  try {
    const adminDecideClientInstructionResponse = await sharedSendRequest("POST", "/api/admin/client-instructions/"
      + adminDecideClientInstructionCase.adminSearchFailedDeliveryCasePendingInstructionID + "/" + adminDecideClientInstructionAction,
    {[adminDecideClientInstructionDecision.fieldName]: adminDecideClientInstructionAnswer}, adminDecideClientInstructionButton);
    adminViewMenuCounter();
    let adminDecideClientInstructionLine = adminDecideClientInstructionDecision.decidedLine;
    if (adminDecideClientInstructionAction === "approve-cancellation") {
      adminDecideClientInstructionLine += " " + adminFormatDecidedRefund(adminDecideClientInstructionResponse);
    }
    adminShowDecidedRow(adminDecideClientInstructionRow, adminDecideClientInstructionRow.cells[2],
      adminDecideClientInstructionResponse, adminDecideClientInstructionLine);
    sharedShowMessage("SUCCESS", adminDecideClientInstructionResponse.message);
  } catch (adminDecideClientInstructionError) {
    sharedShowMessage("ERROR", adminDecideClientInstructionError.message);
  }
}

// ===== SECTION 8: adminRecordClientInstruction =====

// What it does: Fill the record window with the request currently known for this order.
// Input: The displayed case and its table row.
// Output: A fresh window with the expected request ID and an empty identity check.
function adminOpenRecordClientInstructionDialog(adminOpenRecordClientInstructionCase, adminOpenRecordClientInstructionRow) {
  adminRecordClientInstructionRow = adminOpenRecordClientInstructionRow;
  adminFillRecordClientInstructionDialog(adminOpenRecordClientInstructionCase);
  sharedOpenDialog(adminRecordClientInstructionDialog);
}

// What it does: Reset the form and show the request that would be replaced.
// Input: One known failed-delivery case, whether from the list or a refresh.
// Output: The latest expected request ID and a form requiring a new identity check.
function adminFillRecordClientInstructionDialog(adminFillRecordClientInstructionCase) {
  adminRecordClientInstructionCase = adminFillRecordClientInstructionCase;
  adminRecordClientInstructionCanSave = true;
  adminRecordClientInstructionOrderReferenceText.textContent = adminFillRecordClientInstructionCase.orderReference;
  adminRecordClientInstructionTypeSelect.value = "REDELIVER";
  adminRecordClientInstructionIdentityIsConfirmedCheckbox.checked = false;
  adminRecordClientInstructionIdentityCheckMethodInput.value = "";
  adminRecordClientInstructionMessage.replaceChildren();
  adminRecordClientInstructionRefreshButton.hidden = true;

  // Name the pending request with its original submission time before asking for a replacement.
  const adminFillRecordClientInstructionPendingText = adminRecordClientInstructionDialog.querySelector(".shared-dialog-body .shared-note");
  adminFillRecordClientInstructionPendingText.replaceChildren();
  adminFillRecordClientInstructionPendingText.hidden =
    adminFillRecordClientInstructionCase.adminSearchFailedDeliveryCasePendingInstructionID == null;
  if (!adminFillRecordClientInstructionPendingText.hidden) {
    adminFillRecordClientInstructionPendingText.append("This replaces the pending request: ",
      adminFormatClientInstructionType(adminFillRecordClientInstructionCase.adminSearchFailedDeliveryCasePendingInstructionType),
      ", ", adminBuildDeliveryCaseTime(adminFillRecordClientInstructionCase.adminSearchFailedDeliveryCasePendingInstructionSubmittedTime),
      ". That request is withdrawn.");
  }
  adminCheckRecordClientInstructionInput();
}

// What it does: Enable recording only when the current request and the identity check are known.
// Input: The window's checkbox and verification method.
// Output: The confirm button's enabled state.
function adminCheckRecordClientInstructionInput() {
  adminRecordClientInstructionConfirmButton.disabled = adminRecordClientInstructionIsBusy ||
    !adminRecordClientInstructionCanSave || !adminRecordClientInstructionIdentityIsConfirmedCheckbox.checked ||
    Array.from(adminRecordClientInstructionIdentityCheckMethodInput.value.trim()).length < 5;
}

// What it does: Record an Admin-entered client request for the shown order.
// Input: A form submission and the pending instruction named when it was opened.
// Output: A decided row on success, or an inline explanation that keeps the form open.
async function adminRecordClientInstruction(adminRecordClientInstructionEvent) {
  adminRecordClientInstructionEvent.preventDefault();
  if (adminRecordClientInstructionConfirmButton.disabled) {
    return;
  }

  // Keep the window attached to this request until the server answers.
  adminRecordClientInstructionIsBusy = true;
  adminRecordClientInstructionCancelButton.disabled = true;
  adminRecordClientInstructionMessage.replaceChildren();
  try {
    const adminRecordClientInstructionResponse = await sharedSendRequest("POST", "/api/admin/failed-delivery-cases/"
      + adminRecordClientInstructionCase.orderID + "/client-instruction", {
        adminRecordClientInstructionType: adminRecordClientInstructionTypeSelect.value,
        adminRecordClientInstructionIdentityIsConfirmed: adminRecordClientInstructionIdentityIsConfirmedCheckbox.checked,
        adminRecordClientInstructionIdentityCheckMethod: adminRecordClientInstructionIdentityCheckMethodInput.value.trim(),
        adminRecordClientInstructionExpectedPendingInstructionID:
          adminRecordClientInstructionCase.adminSearchFailedDeliveryCasePendingInstructionID
      }, adminRecordClientInstructionConfirmButton);
    adminViewMenuCounter();
    sharedCloseDialog(adminRecordClientInstructionDialog);
    adminShowDecidedRow(adminRecordClientInstructionRow, adminRecordClientInstructionRow.cells[2],
      adminRecordClientInstructionResponse, "Request recorded by {admin}, {time}");
    sharedShowMessage("SUCCESS", adminRecordClientInstructionResponse.message);
  } catch (adminRecordClientInstructionError) {
    sharedShowInlineMessage(adminRecordClientInstructionMessage, "ERROR", adminRecordClientInstructionError.message);
    if (adminRecordClientInstructionError.code === "ADMIN_CLIENT_INSTRUCTION_STATE_CONFLICT") {
      // Keep the old draft visible. The Admin must refresh and check the current request before saving.
      adminRecordClientInstructionCanSave = false;
      adminRecordClientInstructionRefreshButton.hidden = false;
      adminRecordClientInstructionRefreshButton.focus();
    }
  } finally {
    adminRecordClientInstructionIsBusy = false;
    adminRecordClientInstructionCancelButton.disabled = false;
    adminCheckRecordClientInstructionInput();
  }
}

// What it does: Load the current request when the record window's version is stale.
// Input: The same order already named in the open window.
// Output: A reset form with the current request or an inline error.
async function adminRefreshClientInstruction() {
  if (adminRecordClientInstructionIsBusy) {
    return;
  }

  // Search All because recording a request can move the case out of the previous filter.
  adminRecordClientInstructionIsBusy = true;
  adminRecordClientInstructionRefreshButton.disabled = true;
  adminRecordClientInstructionCancelButton.disabled = true;
  try {
    let adminRefreshClientInstructionPageNumber = 1;
    let adminRefreshClientInstructionPage;
    do {
      adminRefreshClientInstructionPage = await adminReadFailedDeliveryCasePage("ALL", adminRefreshClientInstructionPageNumber);
      const adminRefreshClientInstructionCase = adminRefreshClientInstructionPage.adminSearchFailedDeliveryCaseRowList
        .find((adminRefreshClientInstructionFoundCase) => adminRefreshClientInstructionFoundCase.orderID === adminRecordClientInstructionCase.orderID);
      if (adminRefreshClientInstructionCase?.adminSearchFailedDeliveryCaseIsReturned) {
        adminFillRecordClientInstructionDialog(adminRefreshClientInstructionCase);
        adminRecordClientInstructionTypeSelect.focus();
        return;
      }
      if (adminRefreshClientInstructionCase) {
        break;
      }
      adminRefreshClientInstructionPageNumber += 1;
    } while (adminRefreshClientInstructionPageNumber <= adminRefreshClientInstructionPage.adminSearchFailedDeliveryCaseTotalPageCount);

    // The order's current state no longer allows recording.
    adminRecordClientInstructionRefreshButton.hidden = true;
    sharedShowInlineMessage(adminRecordClientInstructionMessage, "ERROR",
      "The case no longer allows a recorded request. Close this window and search again.");
  } catch (adminRefreshClientInstructionError) {
    sharedShowInlineMessage(adminRecordClientInstructionMessage, "ERROR", adminRefreshClientInstructionError.message);
  } finally {
    adminRecordClientInstructionIsBusy = false;
    adminRecordClientInstructionRefreshButton.disabled = false;
    adminRecordClientInstructionCancelButton.disabled = false;
    adminCheckRecordClientInstructionInput();
  }
}

// ===== SECTION 9: adminReassignDelivery =====

// What it does: Load active workers before opening the reassignment window.
// Input: A held package and its Reassign button.
// Output: A window that offers other active workers by held workload, or a corner error.
async function adminOpenReassignDeliveryDialog(adminOpenReassignDeliveryPackage, adminOpenReassignDeliveryButton) {
  try {
    const adminOpenReassignDeliveryResponse = await sharedSendRequest("GET",
      "/api/admin/held-packages/worker-workload", null, adminOpenReassignDeliveryButton);
    if (!adminOpenReassignDeliveryButton.isConnected || document.querySelector("dialog:modal")) {
      return;
    }

    // Retain the holder and prefill the current responsibility from this row.
    adminReassignDeliveryPackage = adminOpenReassignDeliveryPackage;
    adminReassignDeliveryRow = adminOpenReassignDeliveryButton.closest("tr");
    adminReassignDeliveryOrderReferenceText.textContent = adminOpenReassignDeliveryPackage.adminSearchHeldPackageOrderReference;
    adminReassignDeliveryCurrentHandlerText.textContent = adminOpenReassignDeliveryPackage.adminSearchHeldPackageCurrentHandlerUsername;
    adminReassignDeliveryCurrentResponsibleText.textContent =
      adminOpenReassignDeliveryPackage.adminSearchHeldPackageResponsibleWorkerUsername;

    const adminOpenReassignDeliveryOptionList = [];
    for (const adminOpenReassignDeliveryWorker of adminOpenReassignDeliveryResponse.adminViewDeliveryWorkerWorkloadRowList) {
      if (adminOpenReassignDeliveryWorker.userID === adminOpenReassignDeliveryPackage.adminSearchHeldPackageResponsibleWorkerUserID) {
        continue;
      }
      const adminOpenReassignDeliveryHeldCount = adminOpenReassignDeliveryWorker.adminViewDeliveryWorkerWorkloadHeldPackageCount;
      adminOpenReassignDeliveryOptionList.push(new Option(adminOpenReassignDeliveryWorker.username + " ("
        + adminOpenReassignDeliveryHeldCount + (adminOpenReassignDeliveryHeldCount === 1 ? " package held)" : " packages held)"),
      String(adminOpenReassignDeliveryWorker.userID)));
    }
    adminReassignDeliveryNewWorkerSelect.disabled = !adminOpenReassignDeliveryOptionList.length;
    adminReassignDeliveryNewWorkerSelect.replaceChildren(...(adminOpenReassignDeliveryOptionList.length
      ? adminOpenReassignDeliveryOptionList : [new Option("No other active Delivery Workers", "")]));
    adminReassignDeliveryReasonInput.value = "";
    adminCheckReassignDeliveryInput();
    sharedOpenDialog(adminReassignDeliveryDialog);
  } catch (adminOpenReassignDeliveryError) {
    sharedShowMessage("ERROR", adminOpenReassignDeliveryError.message);
  }
}

// What it does: Enable reassignment with a different eligible worker and sufficient reason.
// Input: The worker selection and reason field.
// Output: The confirm button's enabled state.
function adminCheckReassignDeliveryInput() {
  adminReassignDeliveryConfirmButton.disabled = adminReassignDeliveryIsBusy ||
    !adminReassignDeliveryNewWorkerSelect.value ||
    Array.from(adminReassignDeliveryReasonInput.value.trim()).length < 5;
}

// What it does: Save a new responsible worker while keeping the physical handler unchanged.
// Input: The reassignment form and the held package whose row opened it.
// Output: A decided row with the saved worker and time, or an inline error.
async function adminReassignDelivery(adminReassignDeliveryEvent) {
  adminReassignDeliveryEvent.preventDefault();
  if (adminReassignDeliveryConfirmButton.disabled) {
    return;
  }

  // Keep the modal in place while waiting for the saved responsibility.
  adminReassignDeliveryIsBusy = true;
  adminReassignDeliveryCancelButton.disabled = true;
  const adminReassignDeliveryMessage = adminReassignDeliveryDialog.querySelector(".shared-message-place");
  adminReassignDeliveryMessage.replaceChildren();
  try {
    const adminReassignDeliveryResponse = await sharedSendRequest("POST", "/api/admin/held-packages/"
      + adminReassignDeliveryPackage.packageID + "/reassign", {
        adminReassignDeliveryNewWorkerUserID: Number(adminReassignDeliveryNewWorkerSelect.value),
        adminReassignDeliveryReason: adminReassignDeliveryReasonInput.value.trim()
      }, adminReassignDeliveryConfirmButton);
    adminViewMenuCounter();
    sharedCloseDialog(adminReassignDeliveryDialog);
    adminShowDecidedRow(adminReassignDeliveryRow, adminReassignDeliveryRow.cells[3], adminReassignDeliveryResponse,
      "Reassigned by {admin}, {time}");
    adminReassignDeliveryRow.cells[4].querySelector(".shared-cell-main")
      .replaceChildren(adminBuildDeliveryCaseTime(adminReassignDeliveryResponse.adminDecidedRowDecidedTime));
    sharedShowMessage("SUCCESS", adminReassignDeliveryResponse.message);
  } catch (adminReassignDeliveryError) {
    sharedShowInlineMessage(adminReassignDeliveryMessage, "ERROR", adminReassignDeliveryError.message);
  } finally {
    adminReassignDeliveryIsBusy = false;
    adminReassignDeliveryCancelButton.disabled = false;
    adminCheckReassignDeliveryInput();
  }
}

// What it does: Keep a decision window open while its server answer is pending.
// Input: The native Escape cancellation event.
// Output: Prevents dismissal only while the active window is busy.
function adminKeepDeliveryDecisionOpen(adminKeepDeliveryDecisionOpenEvent) {
  if (adminRecordClientInstructionIsBusy || adminReassignDeliveryIsBusy) {
    adminKeepDeliveryDecisionOpenEvent.preventDefault();
  }
}

// ===== SECTION 10: FAILED-DELIVERY ROWS =====

// What it does: Shape one failed delivery as a work-table row.
// Input: The case's public order, failure and request details.
// Output: The readable row with its own links and allowed actions.
function adminBuildFailedDeliveryCaseRow(adminBuildFailedDeliveryCaseData) {
  const adminBuildFailedDeliveryCaseRowElement = document.createElement("tr");
  const adminBuildFailedDeliveryCaseOrderLink = adminBuildDeliveryOrderLink(
    "adminViewOrderOpenLink-" + adminBuildFailedDeliveryCaseData.orderID,
    adminBuildFailedDeliveryCaseData.orderID, adminBuildFailedDeliveryCaseData.orderReference);
  const adminBuildFailedDeliveryCaseOrderCell = adminBuildDeliveryCaseCell("Order", adminBuildFailedDeliveryCaseOrderLink);
  adminBuildDeliveryCaseFlags(adminBuildFailedDeliveryCaseOrderCell, [
    adminBuildFailedDeliveryCaseData.adminSearchFailedDeliveryCaseIsRepeatedFailure ? "Review" : null,
    adminBuildFailedDeliveryCaseData.adminSearchFailedDeliveryCaseHasOpenShortageCase ? "Open shortage case" : null
  ]);

  // Show only the public failure reason, worker and cleaned internal note returned by the server.
  const adminBuildFailedDeliveryCaseReason = document.createElement("strong");
  adminBuildFailedDeliveryCaseReason.textContent = adminBuildFailedDeliveryCaseData.adminSearchFailedDeliveryCaseLatestFailureReason
    ? sharedFormatStatusWord(adminBuildFailedDeliveryCaseData.adminSearchFailedDeliveryCaseLatestFailureReason) : "-";
  const adminBuildFailedDeliveryCaseFailureCount = adminBuildFailedDeliveryCaseData.adminSearchFailedDeliveryCaseFailureCount;
  const adminBuildFailedDeliveryCaseFailureText = adminBuildDeliveryCaseText("Failed ",
    String(adminBuildFailedDeliveryCaseFailureCount), adminBuildFailedDeliveryCaseFailureCount === 1 ? " time" : " times");
  if (adminBuildFailedDeliveryCaseData.adminSearchFailedDeliveryCaseLatestFailureTime) {
    adminBuildFailedDeliveryCaseFailureText.append(", latest ",
      adminBuildDeliveryCaseTime(adminBuildFailedDeliveryCaseData.adminSearchFailedDeliveryCaseLatestFailureTime));
  }
  if (adminBuildFailedDeliveryCaseData.adminSearchFailedDeliveryCaseLatestWorkerUsername) {
    adminBuildFailedDeliveryCaseFailureText.append(", ", adminBuildFailedDeliveryCaseData.adminSearchFailedDeliveryCaseLatestWorkerUsername);
  }
  const adminBuildFailedDeliveryCaseNote = adminBuildFailedDeliveryCaseData.adminSearchFailedDeliveryCaseLatestInternalNote;
  const adminBuildFailedDeliveryCaseFailureCell = adminBuildDeliveryCaseCell("Failure", adminBuildFailedDeliveryCaseReason,
    adminBuildFailedDeliveryCaseFailureText, adminBuildFailedDeliveryCaseNote ? "Note: “" + adminBuildFailedDeliveryCaseNote + "”" : null);
  adminBuildFailedDeliveryCaseRowElement.append(adminBuildFailedDeliveryCaseOrderCell, adminBuildFailedDeliveryCaseFailureCell,
    adminBuildFailedDeliveryCaseStateCell(adminBuildFailedDeliveryCaseData),
    adminBuildFailedDeliveryCaseActionCell(adminBuildFailedDeliveryCaseData, adminBuildFailedDeliveryCaseRowElement));
  return adminBuildFailedDeliveryCaseRowElement;
}

// What it does: Describe a failed delivery's state and any waiting review flag.
// Input: One failed-delivery case.
// Output: Its state cell with request, return or reminder details.
function adminBuildFailedDeliveryCaseStateCell(adminBuildFailedDeliveryCaseStateData) {
  if (!adminBuildFailedDeliveryCaseStateData.adminSearchFailedDeliveryCaseIsReturned) {
    return adminBuildDeliveryCaseCell("State", adminBuildStatusBadge("WAITING_FOR_RETURN"), "Waiting for return to Warehouse");
  }
  if (adminBuildFailedDeliveryCaseStateData.adminSearchFailedDeliveryCasePendingInstructionID != null) {
    return adminBuildDeliveryCaseCell("State", adminBuildStatusBadge("NEEDS_DECISION"),
      adminBuildClientInstructionText(adminBuildFailedDeliveryCaseStateData, false));
  }

  // The return age is counted in full days by the server.
  const adminBuildFailedDeliveryCaseDaysSinceReturn = adminBuildFailedDeliveryCaseStateData.adminSearchFailedDeliveryCaseDaysSinceReturn;
  const adminBuildFailedDeliveryCaseReturnText = adminBuildDeliveryCaseText("Returned ",
    String(adminBuildFailedDeliveryCaseDaysSinceReturn), adminBuildFailedDeliveryCaseDaysSinceReturn === 1 ? " day ago" : " days ago");
  switch (adminBuildFailedDeliveryCaseStateData.adminSearchFailedDeliveryCaseWaitingFlag) {
    case "REMINDER_DUE":
      adminBuildFailedDeliveryCaseReturnText.append(" ", adminBuildFlag("Reminder due", "waiting"));
      break;
    case "FINAL_REVIEW":
      adminBuildFailedDeliveryCaseReturnText.append(" ", adminBuildFlag("Final review", "problem"));
      break;
  }
  const adminBuildFailedDeliveryCaseLastReminderTime =
    adminBuildFailedDeliveryCaseStateData.adminSearchFailedDeliveryCaseLastReminderTime;
  return adminBuildDeliveryCaseCell("State", adminBuildStatusBadge("NO_CLIENT_REQUEST"), adminBuildFailedDeliveryCaseReturnText,
    adminBuildFailedDeliveryCaseLastReminderTime
      ? adminBuildDeliveryCaseText("Last reminder ", adminBuildDeliveryCaseTime(adminBuildFailedDeliveryCaseLastReminderTime))
      : null);
}

// What it does: Render the controls that the current failed-delivery state permits.
// Input: The case and the row that owns its buttons.
// Output: The actions cell with disabled reasons and a shortage link when applicable.
function adminBuildFailedDeliveryCaseActionCell(adminBuildFailedDeliveryCaseActionData, adminBuildFailedDeliveryCaseActionRow) {
  const adminBuildFailedDeliveryCaseActionCellElement = document.createElement("td");
  adminBuildFailedDeliveryCaseActionCellElement.className = "shared-table-actions";
  adminBuildFailedDeliveryCaseActionCellElement.dataset.title = "Actions";
  if (!adminBuildFailedDeliveryCaseActionData.adminSearchFailedDeliveryCaseIsReturned) {
    const adminBuildFailedDeliveryCaseActionEmpty = document.createElement("span");
    adminBuildFailedDeliveryCaseActionEmpty.className = "shared-muted";
    adminBuildFailedDeliveryCaseActionEmpty.textContent = "-";
    adminBuildFailedDeliveryCaseActionCellElement.append(adminBuildFailedDeliveryCaseActionEmpty);
    return adminBuildFailedDeliveryCaseActionCellElement;
  }

  const adminBuildFailedDeliveryCaseActionButtons = document.createElement("div");
  adminBuildFailedDeliveryCaseActionButtons.className = "shared-cell-buttons";
  adminBuildFailedDeliveryCaseActionCellElement.append(adminBuildFailedDeliveryCaseActionButtons);
  const adminBuildFailedDeliveryCaseActionReference = adminBuildFailedDeliveryCaseActionData.orderReference;
  const adminBuildFailedDeliveryCaseActionOrderID = adminBuildFailedDeliveryCaseActionData.orderID;
  const adminBuildFailedDeliveryCaseActionInstructionID =
    adminBuildFailedDeliveryCaseActionData.adminSearchFailedDeliveryCasePendingInstructionID;
  const adminBuildFailedDeliveryCaseActionNextReminderTime =
    adminBuildFailedDeliveryCaseActionData.adminSearchFailedDeliveryCaseNextReminderTime;
  const adminBuildFailedDeliveryCaseActionHasShortage =
    adminBuildFailedDeliveryCaseActionData.adminSearchFailedDeliveryCaseHasOpenShortageCase;

  // Only returned packages can move toward a decision or receive a reminder.
  if (adminBuildFailedDeliveryCaseActionInstructionID == null) {
    const adminBuildFailedDeliveryCaseReminder = adminBuildDeliveryCaseButton(
      "adminSendDeliveryReminderButton-" + adminBuildFailedDeliveryCaseActionOrderID, "Send reminder",
      "Send reminder for order " + adminBuildFailedDeliveryCaseActionReference);
    adminBuildFailedDeliveryCaseReminder.disabled = Boolean(adminBuildFailedDeliveryCaseActionNextReminderTime);
    adminBuildFailedDeliveryCaseReminder.addEventListener("click",
      () => adminSendDeliveryReminder(adminBuildFailedDeliveryCaseActionData, adminBuildFailedDeliveryCaseReminder));
    adminBuildFailedDeliveryCaseActionButtons.append(adminBuildFailedDeliveryCaseReminder);
    if (adminBuildFailedDeliveryCaseActionNextReminderTime) {
      adminBuildFailedDeliveryCaseActionCellElement.append(
        adminBuildFailedDeliveryCaseReminderLine(adminBuildFailedDeliveryCaseActionNextReminderTime));
    }
  } else {
    const adminBuildFailedDeliveryCaseActionIsCancellation =
      adminBuildFailedDeliveryCaseActionData.adminSearchFailedDeliveryCasePendingInstructionType === "CANCEL";
    const adminBuildFailedDeliveryCaseApproveText = adminBuildFailedDeliveryCaseActionIsCancellation
      ? "Approve cancellation" : "Approve redelivery";
    const adminBuildFailedDeliveryCaseApproveButton = adminBuildDeliveryCaseButton(
      (adminBuildFailedDeliveryCaseActionIsCancellation ? "adminApproveCancellationOpenButton-" : "adminApproveRedeliveryOpenButton-")
        + adminBuildFailedDeliveryCaseActionInstructionID, adminBuildFailedDeliveryCaseApproveText,
      adminBuildFailedDeliveryCaseApproveText + " for order " + adminBuildFailedDeliveryCaseActionReference,
      adminBuildFailedDeliveryCaseActionIsCancellation);
    adminBuildFailedDeliveryCaseApproveButton.disabled = adminBuildFailedDeliveryCaseActionHasShortage;
    adminBuildFailedDeliveryCaseApproveButton.addEventListener("click", () => adminDecideClientInstruction(
      adminBuildFailedDeliveryCaseActionIsCancellation ? "approve-cancellation" : "approve-redelivery",
      adminBuildFailedDeliveryCaseActionData, adminBuildFailedDeliveryCaseApproveButton));
    const adminBuildFailedDeliveryCaseRejectButton = adminBuildDeliveryCaseButton(
      "adminRejectClientInstructionOpenButton-" + adminBuildFailedDeliveryCaseActionInstructionID, "Reject",
      "Reject request for order " + adminBuildFailedDeliveryCaseActionReference);
    adminBuildFailedDeliveryCaseRejectButton.addEventListener("click", () => adminDecideClientInstruction(
      "reject", adminBuildFailedDeliveryCaseActionData, adminBuildFailedDeliveryCaseRejectButton));
    adminBuildFailedDeliveryCaseActionButtons.append(adminBuildFailedDeliveryCaseApproveButton, adminBuildFailedDeliveryCaseRejectButton);
    if (adminBuildFailedDeliveryCaseActionHasShortage) {
      const adminBuildFailedDeliveryCaseShortageLink = adminBuildDeliveryOrderLink(
        "adminViewOrderShortageCaseLink-" + adminBuildFailedDeliveryCaseActionOrderID,
        adminBuildFailedDeliveryCaseActionOrderID, adminBuildFailedDeliveryCaseActionReference, true);
      adminBuildFailedDeliveryCaseActionCellElement.append(
        adminBuildDeliveryCaseLine("shared-cell-sub", "Decide the open shortage case first."),
        adminBuildDeliveryCaseLine("shared-cell-sub", adminBuildFailedDeliveryCaseShortageLink));
    }
  }

  // Recording is available for all returned failed deliveries, with or without a pending request.
  const adminBuildFailedDeliveryCaseRecordButton = adminBuildDeliveryCaseButton(
    "adminRecordClientInstructionOpenButton-" + adminBuildFailedDeliveryCaseActionOrderID, "Record request",
    "Record request for order " + adminBuildFailedDeliveryCaseActionReference);
  adminBuildFailedDeliveryCaseRecordButton.addEventListener("click",
    () => adminOpenRecordClientInstructionDialog(adminBuildFailedDeliveryCaseActionData, adminBuildFailedDeliveryCaseActionRow));
  adminBuildFailedDeliveryCaseActionButtons.append(adminBuildFailedDeliveryCaseRecordButton);
  return adminBuildFailedDeliveryCaseActionCellElement;
}

// What it does: Describe when another reminder is allowed.
// Input: The next reminder time returned by the server.
// Output: The note that follows the disabled reminder.
function adminBuildFailedDeliveryCaseReminderLine(adminBuildFailedDeliveryCaseReminderTime) {
  return adminBuildDeliveryCaseLine("shared-cell-sub", adminBuildDeliveryCaseText(
    "Next reminder after ", adminBuildDeliveryCaseTime(adminBuildFailedDeliveryCaseReminderTime), "."));
}

// What it does: Format the request shown in a case row or decision fact.
// Input: The case's latest pending request and whether this is a fact in a window.
// Output: The type, time and origin as an inline fragment.
function adminBuildClientInstructionText(adminBuildClientInstructionCase, adminBuildClientInstructionIsFact) {
  return adminBuildDeliveryCaseText(
    adminFormatClientInstructionType(adminBuildClientInstructionCase.adminSearchFailedDeliveryCasePendingInstructionType),
    adminBuildClientInstructionIsFact ? ", requested " : " requested ",
    adminBuildDeliveryCaseTime(adminBuildClientInstructionCase.adminSearchFailedDeliveryCasePendingInstructionSubmittedTime),
    adminBuildClientInstructionCase.adminSearchFailedDeliveryCasePendingInstructionSource === "ADMIN_MANUAL"
      ? ", recorded by an Admin" : " on the tracking page");
}

// What it does: Turn a request type into the noun used in a case row.
// Input: The stored request type word.
// Output: Redelivery or Cancellation.
function adminFormatClientInstructionType(adminFormatClientInstructionTypeWord) {
  return adminFormatClientInstructionTypeWord === "CANCEL" ? "Cancellation" : "Redelivery";
}

// ===== SECTION 11: HELD-PACKAGE ROWS =====

// What it does: Separate a package's physical holder from its responsible worker.
// Input: A held package.
// Output: One table row with an order link and a reassignment action.
function adminBuildHeldPackageRow(adminBuildHeldPackageData) {
  const adminBuildHeldPackageRowElement = document.createElement("tr");
  const adminBuildHeldPackageReference = adminBuildHeldPackageData.adminSearchHeldPackageOrderReference;
  const adminBuildHeldPackageOrderLink = adminBuildDeliveryOrderLink(
    "adminSearchHeldPackageOrderLink-" + adminBuildHeldPackageData.packageID,
    adminBuildHeldPackageData.orderID, adminBuildHeldPackageReference);
  const adminBuildHeldPackageHolderCell = adminBuildDeliveryCaseCell("Held by", adminBuildHeldPackageData.adminSearchHeldPackageCurrentHandlerUsername);
  adminBuildDeliveryCaseFlags(adminBuildHeldPackageHolderCell,
    [adminBuildHeldPackageData.adminSearchHeldPackageCurrentHandlerAccountStatus === "ACTIVE" ? null : "Worker not active"]);

  // Keep the package's own link and action unique even when the order is in both tables.
  const adminBuildHeldPackageButton = adminBuildDeliveryCaseButton("adminReassignDeliveryOpenButton-" + adminBuildHeldPackageData.packageID,
    "Reassign", "Reassign delivery of order " + adminBuildHeldPackageReference);
  adminBuildHeldPackageButton.addEventListener("click",
    () => adminOpenReassignDeliveryDialog(adminBuildHeldPackageData, adminBuildHeldPackageButton));
  const adminBuildHeldPackageActionCell = document.createElement("td");
  adminBuildHeldPackageActionCell.className = "shared-table-actions";
  adminBuildHeldPackageActionCell.dataset.title = "Actions";
  const adminBuildHeldPackageActionButtons = document.createElement("div");
  adminBuildHeldPackageActionButtons.className = "shared-cell-buttons";
  adminBuildHeldPackageActionButtons.append(adminBuildHeldPackageButton);
  adminBuildHeldPackageActionCell.append(adminBuildHeldPackageActionButtons);
  adminBuildHeldPackageRowElement.append(
    adminBuildDeliveryCaseCell("Order", adminBuildHeldPackageOrderLink,
      adminBuildStatusBadge(adminBuildHeldPackageData.adminSearchHeldPackageOrderStatus)),
    adminBuildDeliveryCaseCell("Package", adminBuildHeldPackageData.packageQRCode),
    adminBuildHeldPackageHolderCell,
    adminBuildDeliveryCaseCell("Responsible worker", adminBuildHeldPackageData.adminSearchHeldPackageResponsibleWorkerUsername),
    adminBuildDeliveryCaseCell("Last reassigned", adminBuildDeliveryCaseTime(adminBuildHeldPackageData.adminSearchHeldPackageLatestReassignmentTime)),
    adminBuildHeldPackageActionCell
  );
  return adminBuildHeldPackageRowElement;
}

// ===== SECTION 12: WORK-TABLE HELPERS =====

// What it does: Restore a valid saved page number.
// Input: The page number's parameter name in the current address.
// Output: A positive page number, defaulting to one.
function adminReadDeliveryPageNumber(adminReadDeliveryPageNumberName) {
  const adminReadDeliveryPageNumberValue = Number(sharedReadPageParameter(adminReadDeliveryPageNumberName));
  return Number.isSafeInteger(adminReadDeliveryPageNumberValue) && adminReadDeliveryPageNumberValue > 0
    ? adminReadDeliveryPageNumberValue : 1;
}

// What it does: Build the lines within one work-table cell.
// Input: The column label, main content and optional secondary lines.
// Output: A row header for Order or a cell for the other columns.
function adminBuildDeliveryCaseCell(adminBuildDeliveryCaseCellTitle, adminBuildDeliveryCaseCellMain,
  ...adminBuildDeliveryCaseCellSubList) {
  const adminBuildDeliveryCaseCellIsHeader = adminBuildDeliveryCaseCellTitle === "Order";
  const adminBuildDeliveryCaseCellElement = document.createElement(adminBuildDeliveryCaseCellIsHeader ? "th" : "td");
  if (adminBuildDeliveryCaseCellIsHeader) {
    adminBuildDeliveryCaseCellElement.scope = "row";
  }
  adminBuildDeliveryCaseCellElement.dataset.title = adminBuildDeliveryCaseCellTitle;
  adminBuildDeliveryCaseCellElement.append(adminBuildDeliveryCaseLine("shared-cell-main", adminBuildDeliveryCaseCellMain ?? "-"));
  for (const adminBuildDeliveryCaseCellSub of adminBuildDeliveryCaseCellSubList) {
    if (adminBuildDeliveryCaseCellSub) {
      adminBuildDeliveryCaseCellElement.append(adminBuildDeliveryCaseLine("shared-cell-sub", adminBuildDeliveryCaseCellSub));
    }
  }
  return adminBuildDeliveryCaseCellElement;
}

// What it does: Append any applicable problem flags to one cell.
// Input: The cell and the flag labels that apply.
// Output: One flag group when at least one label is present.
function adminBuildDeliveryCaseFlags(adminBuildDeliveryCaseFlagsCell, adminBuildDeliveryCaseFlagsLabelList) {
  const adminBuildDeliveryCaseFlagsElement = document.createElement("span");
  adminBuildDeliveryCaseFlagsElement.className = "shared-cell-flags";
  for (const adminBuildDeliveryCaseFlagsLabel of adminBuildDeliveryCaseFlagsLabelList) {
    if (adminBuildDeliveryCaseFlagsLabel) {
      adminBuildDeliveryCaseFlagsElement.append(adminBuildFlag(adminBuildDeliveryCaseFlagsLabel, "problem"));
    }
  }
  if (adminBuildDeliveryCaseFlagsElement.childElementCount) {
    adminBuildDeliveryCaseFlagsCell.append(adminBuildDeliveryCaseFlagsElement);
  }
}

// What it does: Wrap one line in the matching work-table text style.
// Input: A shared cell class and text or a detached element.
// Output: One line of safe text or markup.
function adminBuildDeliveryCaseLine(adminBuildDeliveryCaseLineClass, adminBuildDeliveryCaseLineContent) {
  const adminBuildDeliveryCaseLineElement = document.createElement("span");
  adminBuildDeliveryCaseLineElement.className = adminBuildDeliveryCaseLineClass;
  adminBuildDeliveryCaseLineElement.append(adminBuildDeliveryCaseLineContent);
  return adminBuildDeliveryCaseLineElement;
}

// What it does: Join plain text and time or flag elements without interpreting data as HTML.
// Input: The ordered text and elements.
// Output: One detached inline fragment.
function adminBuildDeliveryCaseText(...adminBuildDeliveryCaseTextPartList) {
  const adminBuildDeliveryCaseTextFragment = document.createDocumentFragment();
  adminBuildDeliveryCaseTextFragment.append(...adminBuildDeliveryCaseTextPartList);
  return adminBuildDeliveryCaseTextFragment;
}

// What it does: Keep a date and a time together inside a table line.
// Input: The saved ISO time, when present.
// Output: A local time element or a dash.
function adminBuildDeliveryCaseTime(adminBuildDeliveryCaseTimeValue) {
  if (!adminBuildDeliveryCaseTimeValue) {
    return "-";
  }
  const adminBuildDeliveryCaseTimeElement = document.createElement("time");
  adminBuildDeliveryCaseTimeElement.className = "shared-nowrap";
  adminBuildDeliveryCaseTimeElement.dateTime = adminBuildDeliveryCaseTimeValue;
  adminBuildDeliveryCaseTimeElement.textContent = sharedFormatTime(adminBuildDeliveryCaseTimeValue);
  return adminBuildDeliveryCaseTimeElement;
}

// What it does: Link the order or its shortage case card.
// Input: The unique element ID, order ID and order reference, and whether to open the shortage card.
// Output: An order link with a row-specific accessible name.
function adminBuildDeliveryOrderLink(adminBuildDeliveryOrderLinkID, adminBuildDeliveryOrderLinkOrderID,
  adminBuildDeliveryOrderLinkReference, adminBuildDeliveryOrderLinkIsShortage = false) {
  const adminBuildDeliveryOrderLinkElement = document.createElement("a");
  adminBuildDeliveryOrderLinkElement.id = adminBuildDeliveryOrderLinkID;
  adminBuildDeliveryOrderLinkElement.href = "admin-order-detail.html?orderID="
    + encodeURIComponent(adminBuildDeliveryOrderLinkOrderID)
    + (adminBuildDeliveryOrderLinkIsShortage ? "#adminViewOrderShortageCasePanel" : "");
  adminBuildDeliveryOrderLinkElement.className = "shared-nowrap";
  adminBuildDeliveryOrderLinkElement.textContent =
    adminBuildDeliveryOrderLinkIsShortage ? "View shortage cases" : adminBuildDeliveryOrderLinkReference;
  adminBuildDeliveryOrderLinkElement.setAttribute("aria-label",
    (adminBuildDeliveryOrderLinkIsShortage ? "View shortage cases of order " : "View order ") + adminBuildDeliveryOrderLinkReference);
  return adminBuildDeliveryOrderLinkElement;
}

// What it does: Provide the small visible action named for its order.
// Input: The action ID, visible text, accessible label and optional danger state.
// Output: A button ready for the row to attach its behavior.
function adminBuildDeliveryCaseButton(adminBuildDeliveryCaseButtonID, adminBuildDeliveryCaseButtonText,
  adminBuildDeliveryCaseButtonLabel, adminBuildDeliveryCaseButtonIsDangerous = false) {
  const adminBuildDeliveryCaseButtonElement = document.createElement("button");
  adminBuildDeliveryCaseButtonElement.id = adminBuildDeliveryCaseButtonID;
  adminBuildDeliveryCaseButtonElement.type = "button";
  adminBuildDeliveryCaseButtonElement.className = "shared-button shared-button-small";
  if (adminBuildDeliveryCaseButtonIsDangerous) {
    adminBuildDeliveryCaseButtonElement.classList.add("shared-button-danger");
  }
  adminBuildDeliveryCaseButtonElement.textContent = adminBuildDeliveryCaseButtonText;
  adminBuildDeliveryCaseButtonElement.setAttribute("aria-label", adminBuildDeliveryCaseButtonLabel);
  return adminBuildDeliveryCaseButtonElement;
}
