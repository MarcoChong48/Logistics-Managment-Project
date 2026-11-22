"use strict";

// ===== SECTION 1: PAGE ELEMENTS =====
const adminSearchShortageCaseCaseStatusSelect =
  document.getElementById("adminSearchShortageCaseCaseStatusSelect");
const adminSearchShortageCaseSubmitButton =
  document.getElementById("adminSearchShortageCaseSubmitButton");
const adminSearchShortageCaseResultTableBody =
  document.getElementById("adminSearchShortageCaseResultTableBody");
const adminSearchShortageCaseEmptyMessage =
  document.getElementById("adminSearchShortageCaseEmptyMessage");
const adminSearchShortageCaseTotalRowCountText =
  document.getElementById("adminSearchShortageCaseTotalRowCountText");
const adminSearchShortageCasePager =
  document.getElementById("adminSearchShortageCasePager");
const adminDownloadShortageCaseCSVButton =
  document.getElementById("adminDownloadShortageCaseCSVButton");
const adminSearchPickingTaskResultTableBody =
  document.getElementById("adminSearchPickingTaskResultTableBody");
const adminSearchPickingTaskEmptyMessage =
  document.getElementById("adminSearchPickingTaskEmptyMessage");
const adminSearchPickingTaskTotalRowCountText =
  document.getElementById("adminSearchPickingTaskTotalRowCountText");
const adminSearchPickingTaskPager =
  document.getElementById("adminSearchPickingTaskPager");
const adminDecideShortageCancelDialog =
  document.getElementById("adminDecideShortageCancelDialog");
const adminDecideShortageCancelDialogTitle =
  document.getElementById("adminDecideShortageCancelDialogTitle");
const adminDecideShortageCancelSummaryText =
  document.getElementById("adminDecideShortageCancelSummaryText");
const adminDecideShortageCancelStockCorrectionIsConfirmedCheckbox =
  document.getElementById("adminDecideShortageCancelStockCorrectionIsConfirmedCheckbox");
const adminDecideShortageCancelReasonInput =
  document.getElementById("adminDecideShortageCancelReasonInput");
const adminDecideShortageCancelCancelButton =
  document.getElementById("adminDecideShortageCancelCancelButton");
const adminDecideShortageCancelConfirmButton =
  document.getElementById("adminDecideShortageCancelConfirmButton");
const adminReassignPickingTaskDialog =
  document.getElementById("adminReassignPickingTaskDialog");
const adminReassignPickingTaskOrderReferenceText =
  document.getElementById("adminReassignPickingTaskOrderReferenceText");
const adminReassignPickingTaskCurrentWorkerText =
  document.getElementById("adminReassignPickingTaskCurrentWorkerText");
const adminReassignPickingTaskProgressText =
  document.getElementById("adminReassignPickingTaskProgressText");
const adminReassignPickingTaskNewWorkerSelect =
  document.getElementById("adminReassignPickingTaskNewWorkerSelect");
const adminReassignPickingTaskReasonInput =
  document.getElementById("adminReassignPickingTaskReasonInput");
const adminReassignPickingTaskCancelButton =
  document.getElementById("adminReassignPickingTaskCancelButton");
const adminReassignPickingTaskConfirmButton =
  document.getElementById("adminReassignPickingTaskConfirmButton");

// ===== SECTION 2: PAGE STATE =====
let adminSearchShortageCaseRequestCount = 0;
let adminSearchPickingTaskRequestCount = 0;
let adminSearchShortageCaseAppliedStatus = "OPEN_AND_WAITING";
let adminDecideShortageCancelRow;
let adminReassignPickingTaskRow;

// ===== SECTION 3: START OF PAGE =====

// What it does: Restore each list after the frame verifies the session.
// Input: The saved filter and page numbers in the browser address.
// Output: Both live Warehouse cases lists.
adminBuildPageFrame("Warehouse cases").then(() => {
  if (document.querySelector("main").hidden) {
    return;
  }

  const adminSearchShortageCaseSavedStatus = sharedReadPageParameter("adminSearchShortageCaseCaseStatus");
  adminSearchShortageCaseCaseStatusSelect.value = adminSearchShortageCaseSavedStatus;
  if (!adminSearchShortageCaseCaseStatusSelect.value) {
    adminSearchShortageCaseCaseStatusSelect.value = adminSearchShortageCaseAppliedStatus;
  }
  sharedWatchTableScroll();
  adminSearchShortageCase(adminReadWarehouseCasePage("adminSearchShortageCasePageNumber"),
    adminSearchShortageCaseCaseStatusSelect.value);
  adminSearchPickingTask(adminReadWarehouseCasePage("adminSearchPickingTaskPageNumber"));
});

// What it does: Apply the selected case status.
// Input: A submit event from the Shortage cases card.
// Output: The first page for the selected status.
adminSearchShortageCaseSubmitButton.form.addEventListener("submit", (adminSearchShortageCaseEvent) => {
  adminSearchShortageCaseEvent.preventDefault();
  adminSearchShortageCase(1, adminSearchShortageCaseCaseStatusSelect.value);
});
adminDownloadShortageCaseCSVButton.addEventListener("click", adminDownloadShortageCaseCSV);
adminDecideShortageCancelStockCorrectionIsConfirmedCheckbox.addEventListener("change", adminCheckShortageCancelInput);
adminDecideShortageCancelReasonInput.addEventListener("input", adminCheckShortageCancelInput);
adminDecideShortageCancelConfirmButton.addEventListener("click", adminDecideShortageCancel);
adminDecideShortageCancelCancelButton.addEventListener("click", () => sharedCloseDialog(adminDecideShortageCancelDialog));
adminReassignPickingTaskNewWorkerSelect.addEventListener("change", adminCheckPickingTaskReassignInput);
adminReassignPickingTaskReasonInput.addEventListener("input", adminCheckPickingTaskReassignInput);
adminReassignPickingTaskConfirmButton.addEventListener("click", adminReassignPickingTask);
adminReassignPickingTaskCancelButton.addEventListener("click", () => sharedCloseDialog(adminReassignPickingTaskDialog));
adminDecideShortageCancelDialog.addEventListener("cancel", adminKeepWarehouseDecisionOpen);
adminReassignPickingTaskDialog.addEventListener("cancel", adminKeepWarehouseDecisionOpen);

// ===== SECTION 4: adminSearchShortageCase =====

// What it does: Reads and shows the shortage cases matching the stored filter.
// Input: A page number and optional case status; the pager uses the active filter.
// Output: Rows, count and pager, or an error. The applied search stays in the address.
async function adminSearchShortageCase(adminSearchShortageCaseRequestedPageNumber,
  adminSearchShortageCaseRequestedStatus = adminSearchShortageCaseAppliedStatus) {
  const adminSearchShortageCaseRequestNumber = ++adminSearchShortageCaseRequestCount;
  const adminSearchShortageCaseParameters = new URLSearchParams({
    adminSearchShortageCaseCaseStatus: adminSearchShortageCaseRequestedStatus,
    adminSearchShortageCasePageNumber: adminSearchShortageCaseRequestedPageNumber
  });
  try {
    const adminSearchShortageCaseResponse = await sharedSendRequest("GET",
      "/api/admin/shortage-cases?" + adminSearchShortageCaseParameters);
    if (adminSearchShortageCaseRequestNumber !== adminSearchShortageCaseRequestCount) {
      return;
    }
    adminSearchShortageCaseAppliedStatus = adminSearchShortageCaseRequestedStatus;
    sharedWritePageParameter([
      ["adminSearchShortageCaseCaseStatus", adminSearchShortageCaseAppliedStatus],
      ["adminSearchShortageCasePageNumber", adminSearchShortageCaseResponse.adminSearchShortageCasePageNumber]
    ]);

    const adminSearchShortageCaseRows = adminSearchShortageCaseResponse.adminSearchShortageCaseRowList;
    adminSearchShortageCaseResultTableBody.replaceChildren(...adminSearchShortageCaseRows.map(adminBuildShortageCaseRow));
    adminSearchShortageCaseResultTableBody.closest(".shared-table-scroll").hidden = !adminSearchShortageCaseRows.length;
    adminSearchShortageCaseEmptyMessage.hidden = Boolean(adminSearchShortageCaseRows.length);
    adminSearchShortageCaseTotalRowCountText.textContent = adminSearchShortageCaseResponse.adminSearchShortageCaseTotalRowCount;
    adminSearchShortageCasePager.hidden = !adminSearchShortageCaseRows.length;
    sharedBuildPager(adminSearchShortageCasePager, adminSearchShortageCaseResponse.adminSearchShortageCasePageNumber,
      adminSearchShortageCaseResponse.adminSearchShortageCaseTotalPageCount, adminSearchShortageCase);
    adminDownloadShortageCaseCSVButton.disabled = false;
  } catch (adminSearchShortageCaseError) {
    if (adminSearchShortageCaseRequestNumber === adminSearchShortageCaseRequestCount) {
      sharedShowMessage("ERROR", adminSearchShortageCaseError.message);
    }
  }
}

// ===== SECTION 5: adminDownloadShortageCaseCSV =====

// What it does: Downloads all cases in the displayed search.
// Input: The applied status, without page limits.
// Output: A CSV download or a corner error.
async function adminDownloadShortageCaseCSV() {
  adminDownloadShortageCaseCSVButton.disabled = true;
  try {
    await adminDownloadCSV("/api/admin/shortage-cases/csv?" + new URLSearchParams({
      adminSearchShortageCaseCaseStatus: adminSearchShortageCaseAppliedStatus
    }));
  } finally {
    adminDownloadShortageCaseCSVButton.disabled = false;
  }
}

// ===== SECTION 6: adminDecideShortageWait =====

// What it does: Asks the Admin for a reason before sending a wait decision.
// Input: The selected shortage case and its row action.
// Output: The saved decision message or a rule error.
async function adminDecideShortageWait(adminDecideShortageWaitCase, adminDecideShortageWaitButton) {
  const adminDecideShortageWaitOrderReference = adminDecideShortageWaitCase.adminSearchShortageCaseOrderReference;
  const adminDecideShortageWaitReason = await adminOpenReasonDialog(
    "Wait for restock for order " + adminDecideShortageWaitOrderReference + "?",
    "The case waits for new stock. The order is not cancelled, and the client is told that it is delayed.",
    "REQUIRED_REASON", "Wait for restock", "Cancel", false,
    [["Shortage", adminFormatShortageCase(adminDecideShortageWaitCase)]]
  );
  if (adminDecideShortageWaitReason === null) {
    return;
  }

  try {
    const adminDecideShortageWaitResponse = await sharedSendRequest("POST",
      "/api/admin/shortage-cases/" + adminDecideShortageWaitCase.shortageCaseID + "/wait",
      {adminDecideShortageWaitReason}, adminDecideShortageWaitButton);
    adminViewMenuCounter();
    const adminDecideShortageWaitElement = adminDecideShortageWaitButton.closest("tr");
    adminShowDecidedRow(adminDecideShortageWaitElement,
      adminDecideShortageWaitElement.querySelector('[data-title="Status"]'),
      adminDecideShortageWaitResponse, "Wait for restock, by {admin}, {time}");
    sharedShowMessage("SUCCESS", adminDecideShortageWaitResponse.message);
  } catch (adminDecideShortageWaitError) {
    sharedShowMessage("ERROR", adminDecideShortageWaitError.message);
  }
}

// ===== SECTION 7: adminDecideShortageCancel =====

// What it does: Prepares the cancellation window for its selected case.
// Input: The saved shortage case and payment flag.
// Output: The paid or unpaid decision facts and a reset confirmation window.
function adminOpenShortageCancelDialog(adminOpenShortageCancelDialogCase) {
  adminDecideShortageCancelRow = adminOpenShortageCancelDialogCase;
  const adminOpenShortageCancelDialogReference = adminOpenShortageCancelDialogCase.adminSearchShortageCaseOrderReference;
  const adminOpenShortageCancelDialogIsPaid = adminOpenShortageCancelDialogCase.adminSearchShortageCaseOrderIsPaid;
  adminDecideShortageCancelDialogTitle.textContent = "Cancel order " + adminOpenShortageCancelDialogReference
    + (adminOpenShortageCancelDialogIsPaid ? " and refund?" : "?");
  adminDecideShortageCancelConfirmButton.textContent = adminOpenShortageCancelDialogIsPaid
    ? "Cancel and refund" : "Cancel order";

  const [adminOpenShortageCancelDialogPayment, adminOpenShortageCancelDialogOutcome] =
    adminDecideShortageCancelSummaryText.querySelectorAll("dd");
  adminOpenShortageCancelDialogPayment.textContent = adminOpenShortageCancelDialogIsPaid ? "Paid" : "Nothing paid";
  adminOpenShortageCancelDialogOutcome.textContent = "The whole order is cancelled for good. "
    + (adminOpenShortageCancelDialogIsPaid ? "A full refund is requested." : "Nothing is refunded.");
  adminDecideShortageCancelStockCorrectionIsConfirmedCheckbox.checked = false;
  adminDecideShortageCancelReasonInput.value = "";
  adminCheckShortageCancelInput();
  sharedOpenDialog(adminDecideShortageCancelDialog);
}

// What it does: Enforces the two cancellation confirmation fields.
// Input: The current stock correction tick and reason, read from the window.
// Output: The confirmation stays disabled until both requirements are met.
function adminCheckShortageCancelInput() {
  if (adminDecideShortageCancelDialog.dataset.saving) {
    return;
  }
  adminDecideShortageCancelConfirmButton.disabled = !adminDecideShortageCancelStockCorrectionIsConfirmedCheckbox.checked
    || Array.from(adminDecideShortageCancelReasonInput.value.trim()).length < 5;
}

// What it does: Submits the stock confirmation and reason.
// Input: The selected case, its confirmation checkbox and reason.
// Output: The success message, or a reason the active window remains open.
async function adminDecideShortageCancel() {
  if (adminDecideShortageCancelConfirmButton.disabled) {
    return;
  }
  const shortageCaseID = adminDecideShortageCancelRow.shortageCaseID;
  const adminDecideShortageCancelElement = document.getElementById("adminSearchShortageCaseOrderLink-" + shortageCaseID)
    ?.closest("tr");
  const adminDecideShortageCancelResponse = await adminSubmitWarehouseCaseDialog(
    adminDecideShortageCancelDialog, adminDecideShortageCancelConfirmButton,
    "/api/admin/shortage-cases/" + shortageCaseID + "/cancel", {
      adminDecideShortageCancelStockCorrectionIsConfirmed: adminDecideShortageCancelStockCorrectionIsConfirmedCheckbox.checked,
      adminDecideShortageCancelReason: adminDecideShortageCancelReasonInput.value.trim()
    }, adminCheckShortageCancelInput);
  if (adminDecideShortageCancelResponse && adminDecideShortageCancelElement?.isConnected) {
    const adminDecideShortageCancelPaid = adminDecideShortageCancelResponse.refundID != null;
    adminShowDecidedRow(adminDecideShortageCancelElement,
      adminDecideShortageCancelElement.querySelector('[data-title="Status"]'), adminDecideShortageCancelResponse,
      "Cancelled by {admin}, {time}. " + adminFormatDecidedRefund(adminDecideShortageCancelResponse));
    adminDecideShortageCancelElement.querySelector('[data-title="Order"] .shared-cell-sub').textContent =
      "Order status: Cancelled, " + (adminDecideShortageCancelPaid ? "paid" : "not paid");
  }
}

// ===== SECTION 8: adminSearchPickingTask =====

// What it does: Loads the picking tasks whose orders are being picked and have a current assignment.
// Input: The requested page number.
// Output: Rows, count and pager, or an error.
async function adminSearchPickingTask(adminSearchPickingTaskRequestedPageNumber) {
  const adminSearchPickingTaskRequestNumber = ++adminSearchPickingTaskRequestCount;
  try {
    const adminSearchPickingTaskResponse = await sharedSendRequest("GET",
      "/api/admin/picking-tasks?" + new URLSearchParams({
        adminSearchPickingTaskPageNumber: adminSearchPickingTaskRequestedPageNumber
      }));
    if (adminSearchPickingTaskRequestNumber !== adminSearchPickingTaskRequestCount) {
      return;
    }
    sharedWritePageParameter([["adminSearchPickingTaskPageNumber", adminSearchPickingTaskResponse.adminSearchPickingTaskPageNumber]]);
    const adminSearchPickingTaskRows = adminSearchPickingTaskResponse.adminSearchPickingTaskRowList;
    adminSearchPickingTaskResultTableBody.replaceChildren(...adminSearchPickingTaskRows.map(adminBuildPickingTaskRow));
    adminSearchPickingTaskResultTableBody.closest(".shared-table-scroll").hidden = !adminSearchPickingTaskRows.length;
    adminSearchPickingTaskEmptyMessage.hidden = Boolean(adminSearchPickingTaskRows.length);
    adminSearchPickingTaskTotalRowCountText.textContent = adminSearchPickingTaskResponse.adminSearchPickingTaskTotalRowCount;
    adminSearchPickingTaskPager.hidden = !adminSearchPickingTaskRows.length;
    sharedBuildPager(adminSearchPickingTaskPager, adminSearchPickingTaskResponse.adminSearchPickingTaskPageNumber,
      adminSearchPickingTaskResponse.adminSearchPickingTaskTotalPageCount, adminSearchPickingTask);
  } catch (adminSearchPickingTaskError) {
    if (adminSearchPickingTaskRequestNumber === adminSearchPickingTaskRequestCount) {
      sharedShowMessage("ERROR", adminSearchPickingTaskError.message);
    }
  }
}

// ===== SECTION 9: adminReassignPickingTask =====

// What it does: Loads current workloads and prepares the reassignment window.
// Input: The selected task row and its Reassign button.
// Output: A window containing the other active workers, or an error.
async function adminOpenPickingTaskReassignDialog(adminOpenPickingTaskReassignDialogRow,
  adminOpenPickingTaskReassignDialogButton) {
  try {
    const adminOpenPickingTaskReassignDialogResponse = await sharedSendRequest("GET",
      "/api/admin/picking-tasks/worker-workload", null, adminOpenPickingTaskReassignDialogButton);
    if (!adminOpenPickingTaskReassignDialogButton.isConnected || document.querySelector("dialog:modal")) {
      return;
    }
    adminReassignPickingTaskRow = adminOpenPickingTaskReassignDialogRow;
    adminReassignPickingTaskOrderReferenceText.textContent = adminOpenPickingTaskReassignDialogRow
      .adminSearchPickingTaskOrderReference;
    adminReassignPickingTaskCurrentWorkerText.textContent = adminOpenPickingTaskReassignDialogRow
      .adminSearchPickingTaskCurrentWorkerUsername;
    adminReassignPickingTaskProgressText.textContent = adminFormatPickingTaskProgress(adminOpenPickingTaskReassignDialogRow);
    adminReassignPickingTaskReasonInput.value = "";

    const adminOpenPickingTaskReassignDialogOptionList = [];
    for (const adminOpenPickingTaskReassignDialogWorker
      of adminOpenPickingTaskReassignDialogResponse.adminViewWarehouseWorkerWorkloadRowList) {
      if (adminOpenPickingTaskReassignDialogWorker.userID ===
          adminOpenPickingTaskReassignDialogRow.adminSearchPickingTaskCurrentWorkerUserID) {
        continue;
      }
      const adminOpenPickingTaskReassignDialogActiveTaskCount = adminOpenPickingTaskReassignDialogWorker
        .adminViewWarehouseWorkerWorkloadActiveTaskCount;
      adminOpenPickingTaskReassignDialogOptionList.push(new Option(adminOpenPickingTaskReassignDialogWorker.username
        + " (" + adminOpenPickingTaskReassignDialogActiveTaskCount + " active "
        + (adminOpenPickingTaskReassignDialogActiveTaskCount === 1 ? "task)" : "tasks)"),
      String(adminOpenPickingTaskReassignDialogWorker.userID)));
    }
    adminReassignPickingTaskNewWorkerSelect.disabled = !adminOpenPickingTaskReassignDialogOptionList.length;
    adminReassignPickingTaskNewWorkerSelect.replaceChildren(...(adminOpenPickingTaskReassignDialogOptionList.length
      ? adminOpenPickingTaskReassignDialogOptionList : [new Option("No other active Warehouse Workers", "")]));
    adminCheckPickingTaskReassignInput();
    sharedOpenDialog(adminReassignPickingTaskDialog);
  } catch (adminOpenPickingTaskReassignDialogError) {
    sharedShowMessage("ERROR", adminOpenPickingTaskReassignDialogError.message);
  }
}

// What it does: Enforces the reassignment confirmation fields.
// Input: The current worker selection and reason in the window.
// Output: Confirmation is available only for an eligible selection and a reason.
function adminCheckPickingTaskReassignInput() {
  if (adminReassignPickingTaskDialog.dataset.saving) {
    return;
  }
  adminReassignPickingTaskConfirmButton.disabled = !adminReassignPickingTaskNewWorkerSelect.value
    || Array.from(adminReassignPickingTaskReasonInput.value.trim()).length < 5;
}

// What it does: Sends the selected worker and reason for reassignment.
// Input: The currently selected task and reassignment fields.
// Output: The success message, or a rule error inside the window.
async function adminReassignPickingTask() {
  if (adminReassignPickingTaskConfirmButton.disabled) {
    return;
  }
  const pickingTaskID = adminReassignPickingTaskRow.pickingTaskID;
  const adminReassignPickingTaskElement = document.getElementById("adminSearchPickingTaskOrderLink-" + pickingTaskID)
    ?.closest("tr");
  const adminReassignPickingTaskResponse = await adminSubmitWarehouseCaseDialog(
    adminReassignPickingTaskDialog, adminReassignPickingTaskConfirmButton,
    "/api/admin/picking-tasks/" + pickingTaskID + "/reassign", {
      adminReassignPickingTaskNewWorkerUserID: Number(adminReassignPickingTaskNewWorkerSelect.value),
      adminReassignPickingTaskReason: adminReassignPickingTaskReasonInput.value.trim()
    }, adminCheckPickingTaskReassignInput);
  if (adminReassignPickingTaskResponse && adminReassignPickingTaskElement?.isConnected) {
    adminShowDecidedRow(adminReassignPickingTaskElement,
      adminReassignPickingTaskElement.querySelector('[data-title="Worker"]'), adminReassignPickingTaskResponse,
      "Reassigned by {admin}, {time}");
    adminReassignPickingTaskElement.querySelector('[data-title="Assigned"] .shared-cell-main').replaceChildren(
      adminBuildWarehouseCaseTime(adminReassignPickingTaskResponse.adminDecidedRowDecidedTime));
  }
}

// ===== SECTION 10: HELPER FUNCTIONS =====

// What it does: Reads a stored page number.
// Input: The name of the address parameter.
// Output: A positive page number, defaulting to 1.
function adminReadWarehouseCasePage(adminReadWarehouseCasePageName) {
  const adminReadWarehouseCasePageNumber = Number(sharedReadPageParameter(adminReadWarehouseCasePageName));
  return Number.isSafeInteger(adminReadWarehouseCasePageNumber) && adminReadWarehouseCasePageNumber > 0
    ? adminReadWarehouseCasePageNumber : 1;
}

// What it does: Renders a shortage case in the order, item, reporter, status and actions columns.
// Input: A shortage search record.
// Output: The table row with its case actions and order detail link.
function adminBuildShortageCaseRow(adminBuildShortageCaseRowCase) {
  const adminBuildShortageCaseRowElement = document.createElement("tr");
  const adminBuildShortageCaseRowReference = adminBuildShortageCaseRowCase.adminSearchShortageCaseOrderReference;
  const adminBuildShortageCaseRowOrderLink = adminBuildWarehouseOrderLink(adminBuildShortageCaseRowCase.orderID,
    adminBuildShortageCaseRowReference, "adminSearchShortageCaseOrderLink-" + adminBuildShortageCaseRowCase.shortageCaseID);
  const adminBuildShortageCaseRowPayment = adminBuildShortageCaseRowCase.adminSearchShortageCaseOrderIsPaid
    ? "paid" : "not paid";
  adminBuildShortageCaseRowElement.append(adminBuildWarehouseCaseCell("Order", adminBuildShortageCaseRowOrderLink,
    "Order status: " + sharedFormatStatusWord(adminBuildShortageCaseRowCase.adminSearchShortageCaseOrderStatus)
      + ", " + adminBuildShortageCaseRowPayment));

  const adminBuildShortageCaseRowQuantity = document.createElement("span");
  adminBuildShortageCaseRowQuantity.className = "shared-strong";
  adminBuildShortageCaseRowQuantity.textContent = adminBuildShortageCaseRowCase.missingQuantity + " of "
    + adminBuildShortageCaseRowCase.adminSearchShortageCaseOrderedQuantity + " missing";
  const adminBuildShortageCaseRowItem = document.createDocumentFragment();
  adminBuildShortageCaseRowItem.append(adminBuildShortageCaseRowQuantity,
    ", " + adminBuildShortageCaseRowCase.adminSearchShortageCaseSKU
      + " " + adminBuildShortageCaseRowCase.adminSearchShortageCaseProductName);
  adminBuildShortageCaseRowElement.append(adminBuildWarehouseCaseCell("Shortage", adminBuildShortageCaseRowItem,
    adminBuildShortageCaseRowCase.reportNote ? "Note: “" + adminBuildShortageCaseRowCase.reportNote + "”" : null));
  adminBuildShortageCaseRowElement.append(adminBuildWarehouseCaseCell("Reported",
    adminBuildWarehouseCaseTime(adminBuildShortageCaseRowCase.reportedTime),
    "by " + adminBuildShortageCaseRowCase.adminSearchShortageCaseReportedByUsername));

  adminBuildShortageCaseRowElement.append(adminBuildShortageCaseStatusCell(adminBuildShortageCaseRowCase));
  adminBuildShortageCaseRowElement.append(adminBuildShortageCaseActionCell(adminBuildShortageCaseRowCase));
  return adminBuildShortageCaseRowElement;
}

// What it does: Shows a case state, its decision and any resolution timestamp.
// Input: A shortage search record.
// Output: A status cell that uses saved rather than current times.
function adminBuildShortageCaseStatusCell(adminBuildShortageCaseStatusCellCase) {
  const adminBuildShortageCaseStatusCellLines = [];
  if (adminBuildShortageCaseStatusCellCase.decidedTime) {
    const adminBuildShortageCaseStatusCellDecision = document.createDocumentFragment();
    adminBuildShortageCaseStatusCellDecision.append(
      adminBuildShortageCaseStatusCellCase.decision === "WAIT_FOR_RESTOCK" ? "Wait for restock, by " : "Cancelled by ",
      adminBuildShortageCaseStatusCellCase.adminSearchShortageCaseDecidedByUsername ?? "—", ", ",
      adminBuildWarehouseCaseTime(adminBuildShortageCaseStatusCellCase.decidedTime));
    adminBuildShortageCaseStatusCellLines.push(adminBuildShortageCaseStatusCellDecision);
  }
  if (adminBuildShortageCaseStatusCellCase.decisionNote) {
    adminBuildShortageCaseStatusCellLines.push("Reason: “" + adminBuildShortageCaseStatusCellCase.decisionNote + "”");
  }
  if (adminBuildShortageCaseStatusCellCase.resolvedTime) {
    const adminBuildShortageCaseStatusCellResolution = document.createDocumentFragment();
    adminBuildShortageCaseStatusCellResolution.append("Resolved ",
      adminBuildWarehouseCaseTime(adminBuildShortageCaseStatusCellCase.resolvedTime));
    adminBuildShortageCaseStatusCellLines.push(adminBuildShortageCaseStatusCellResolution);
  }
  return adminBuildWarehouseCaseCell("Status", adminBuildStatusBadge(adminBuildShortageCaseStatusCellCase.caseStatus),
    ...adminBuildShortageCaseStatusCellLines);
}

// What it does: Adds the appropriate row actions for the saved case and order status.
// Input: A shortage search record.
// Output: A cell, with buttons bound to this case only.
function adminBuildShortageCaseActionCell(adminBuildShortageCaseActionCellCase) {
  const adminBuildShortageCaseActionCellElement = document.createElement("td");
  adminBuildShortageCaseActionCellElement.className = "shared-table-actions";
  adminBuildShortageCaseActionCellElement.dataset.title = "Actions";
  const adminBuildShortageCaseActionCellButtons = document.createElement("div");
  adminBuildShortageCaseActionCellButtons.className = "shared-cell-buttons";
  const adminBuildShortageCaseActionCellReference = adminBuildShortageCaseActionCellCase.adminSearchShortageCaseOrderReference;
  const adminBuildShortageCaseActionCellStatus = adminBuildShortageCaseActionCellCase.adminSearchShortageCaseOrderStatus;

  if (adminBuildShortageCaseActionCellCase.caseStatus === "OPEN") {
    const adminBuildShortageCaseActionCellWait = adminBuildWarehouseCaseButton(
      "adminDecideShortageWaitOpenButton-" + adminBuildShortageCaseActionCellCase.shortageCaseID,
      "Wait for restock", "Wait for restock for order " + adminBuildShortageCaseActionCellReference);
    adminBuildShortageCaseActionCellWait.disabled = ["CANCELLED", "DELIVERED"].includes(adminBuildShortageCaseActionCellStatus);
    adminBuildShortageCaseActionCellWait.addEventListener("click",
      () => adminDecideShortageWait(adminBuildShortageCaseActionCellCase, adminBuildShortageCaseActionCellWait));
    adminBuildShortageCaseActionCellButtons.append(adminBuildShortageCaseActionCellWait);
  }
  if (["OPEN", "WAITING_RESTOCK"].includes(adminBuildShortageCaseActionCellCase.caseStatus)) {
    const adminBuildShortageCaseActionCellIsPaid = adminBuildShortageCaseActionCellCase.adminSearchShortageCaseOrderIsPaid;
    const adminBuildShortageCaseActionCellCancel = adminBuildWarehouseCaseButton(
      "adminDecideShortageCancelOpenButton-" + adminBuildShortageCaseActionCellCase.shortageCaseID,
      adminBuildShortageCaseActionCellIsPaid ? "Cancel and refund" : "Cancel order",
      "Cancel " + (adminBuildShortageCaseActionCellIsPaid ? "and refund " : "") + "order "
        + adminBuildShortageCaseActionCellReference, true);
    adminBuildShortageCaseActionCellCancel.disabled = ["SHIPPED", "OUT_FOR_DELIVERY", "DELIVERED", "CANCELLED"]
      .includes(adminBuildShortageCaseActionCellStatus);
    adminBuildShortageCaseActionCellCancel.addEventListener("click",
      () => adminOpenShortageCancelDialog(adminBuildShortageCaseActionCellCase));
    adminBuildShortageCaseActionCellButtons.append(adminBuildShortageCaseActionCellCancel);
    adminBuildShortageCaseActionCellElement.append(adminBuildShortageCaseActionCellButtons);
    if (adminBuildShortageCaseActionCellCancel.disabled) {
      const adminBuildShortageCaseActionCellExplanation = document.createElement("span");
      adminBuildShortageCaseActionCellExplanation.className = "shared-cell-sub";
      adminBuildShortageCaseActionCellExplanation.textContent = "The order cannot be cancelled in its current state.";
      adminBuildShortageCaseActionCellElement.append(adminBuildShortageCaseActionCellExplanation);
    }
  } else {
    const adminBuildShortageCaseActionCellEmpty = document.createElement("span");
    adminBuildShortageCaseActionCellEmpty.className = "shared-muted";
    adminBuildShortageCaseActionCellEmpty.textContent = "-";
    adminBuildShortageCaseActionCellElement.append(adminBuildShortageCaseActionCellEmpty);
  }
  return adminBuildShortageCaseActionCellElement;
}

// What it does: Renders the picking task and current open assignment.
// Input: A picking list record.
// Output: The picking row, its flags and reassignment action.
function adminBuildPickingTaskRow(adminBuildPickingTaskRowTask) {
  const adminBuildPickingTaskRowElement = document.createElement("tr");
  const adminBuildPickingTaskRowReference = adminBuildPickingTaskRowTask.adminSearchPickingTaskOrderReference;
  const adminBuildPickingTaskRowOrderCell = adminBuildWarehouseCaseCell("Order", adminBuildWarehouseOrderLink(
    adminBuildPickingTaskRowTask.orderID, adminBuildPickingTaskRowReference,
    "adminSearchPickingTaskOrderLink-" + adminBuildPickingTaskRowTask.pickingTaskID));
  if (adminBuildPickingTaskRowTask.adminSearchPickingTaskHasOpenShortageCase) {
    adminBuildPickingTaskRowOrderCell.append(adminBuildWarehouseCaseFlag("Open shortage case"));
  }
  const adminBuildPickingTaskRowWorkerCell = adminBuildWarehouseCaseCell("Worker",
    adminBuildPickingTaskRowTask.adminSearchPickingTaskCurrentWorkerUsername);
  if (adminBuildPickingTaskRowTask.adminSearchPickingTaskCurrentWorkerAccountStatus !== "ACTIVE") {
    adminBuildPickingTaskRowWorkerCell.append(adminBuildWarehouseCaseFlag("Worker not active"));
  }
  const adminBuildPickingTaskRowButton = adminBuildWarehouseCaseButton(
    "adminReassignPickingTaskOpenButton-" + adminBuildPickingTaskRowTask.pickingTaskID,
    "Reassign", "Reassign picking task of order " + adminBuildPickingTaskRowReference);
  adminBuildPickingTaskRowButton.addEventListener("click",
    () => adminOpenPickingTaskReassignDialog(adminBuildPickingTaskRowTask, adminBuildPickingTaskRowButton));
  const adminBuildPickingTaskRowActionCell = document.createElement("td");
  adminBuildPickingTaskRowActionCell.className = "shared-table-actions";
  adminBuildPickingTaskRowActionCell.dataset.title = "Actions";
  const adminBuildPickingTaskRowButtons = document.createElement("div");
  adminBuildPickingTaskRowButtons.className = "shared-cell-buttons";
  adminBuildPickingTaskRowButtons.append(adminBuildPickingTaskRowButton);
  adminBuildPickingTaskRowActionCell.append(adminBuildPickingTaskRowButtons);
  adminBuildPickingTaskRowElement.append(adminBuildPickingTaskRowOrderCell, adminBuildPickingTaskRowWorkerCell,
    adminBuildWarehouseCaseCell("Assigned",
      adminBuildWarehouseCaseTime(adminBuildPickingTaskRowTask.adminSearchPickingTaskAssignedTime)),
    adminBuildWarehouseCaseCell("Started", adminBuildWarehouseCaseTime(adminBuildPickingTaskRowTask.startTime)),
    adminBuildWarehouseCaseCell("Progress", adminFormatPickingTaskProgress(adminBuildPickingTaskRowTask)),
    adminBuildPickingTaskRowActionCell);
  return adminBuildPickingTaskRowElement;
}

// What it does: Creates a work-table cell with a primary line and optional supporting lines.
// Input: The column title and safe text or a DOM node for each line.
// Output: One detached table cell.
function adminBuildWarehouseCaseCell(adminBuildWarehouseCaseCellTitle, adminBuildWarehouseCaseCellMain,
  ...adminBuildWarehouseCaseCellSubList) {
  const adminBuildWarehouseCaseCellIsHeading = adminBuildWarehouseCaseCellTitle === "Order";
  const adminBuildWarehouseCaseCellElement = document.createElement(adminBuildWarehouseCaseCellIsHeading ? "th" : "td");
  if (adminBuildWarehouseCaseCellIsHeading) {
    adminBuildWarehouseCaseCellElement.scope = "row";
  }
  adminBuildWarehouseCaseCellElement.dataset.title = adminBuildWarehouseCaseCellTitle;
  const adminBuildWarehouseCaseCellMainLine = document.createElement("span");
  adminBuildWarehouseCaseCellMainLine.className = "shared-cell-main";
  adminBuildWarehouseCaseCellMainLine.append(adminBuildWarehouseCaseCellMain);
  adminBuildWarehouseCaseCellElement.append(adminBuildWarehouseCaseCellMainLine);
  for (const adminBuildWarehouseCaseCellSub of adminBuildWarehouseCaseCellSubList) {
    if (adminBuildWarehouseCaseCellSub) {
      const adminBuildWarehouseCaseCellSubLine = document.createElement("span");
      adminBuildWarehouseCaseCellSubLine.className = "shared-cell-sub";
      adminBuildWarehouseCaseCellSubLine.append(adminBuildWarehouseCaseCellSub);
      adminBuildWarehouseCaseCellElement.append(adminBuildWarehouseCaseCellSubLine);
    }
  }
  return adminBuildWarehouseCaseCellElement;
}

// What it does: Links to one order from its Warehouse table row.
// Input: The order ID, its reference and the link's row-specific ID.
// Output: A named link to the read-only order page.
function adminBuildWarehouseOrderLink(orderID, orderReference, adminBuildWarehouseOrderLinkID) {
  const adminBuildWarehouseOrderLinkElement = document.createElement("a");
  adminBuildWarehouseOrderLinkElement.className = "shared-nowrap";
  adminBuildWarehouseOrderLinkElement.id = adminBuildWarehouseOrderLinkID;
  adminBuildWarehouseOrderLinkElement.href = "/admin/admin-order-detail.html?" + new URLSearchParams({orderID});
  adminBuildWarehouseOrderLinkElement.textContent = orderReference;
  adminBuildWarehouseOrderLinkElement.setAttribute("aria-label", "View order " + orderReference);
  return adminBuildWarehouseOrderLinkElement;
}

// What it does: Displays an extra picking task fact beneath the main cell content.
// Input: The flag's visible label.
// Output: The design's work-table flag container.
function adminBuildWarehouseCaseFlag(adminBuildWarehouseCaseFlagLabel) {
  const adminBuildWarehouseCaseFlagContainer = document.createElement("span");
  adminBuildWarehouseCaseFlagContainer.className = "shared-cell-flags";
  adminBuildWarehouseCaseFlagContainer.append(adminBuildFlag(adminBuildWarehouseCaseFlagLabel, "problem"));
  return adminBuildWarehouseCaseFlagContainer;
}

// What it does: Formats an ISO timestamp for the local browser.
// Input: The stored time or an empty value.
// Output: An unbreakable time element or a dash.
function adminBuildWarehouseCaseTime(adminBuildWarehouseCaseTimeValue) {
  if (!adminBuildWarehouseCaseTimeValue) {
    return "—";
  }
  const adminBuildWarehouseCaseTimeElement = document.createElement("time");
  adminBuildWarehouseCaseTimeElement.className = "shared-nowrap";
  adminBuildWarehouseCaseTimeElement.dateTime = adminBuildWarehouseCaseTimeValue;
  adminBuildWarehouseCaseTimeElement.textContent = sharedFormatTime(adminBuildWarehouseCaseTimeValue);
  return adminBuildWarehouseCaseTimeElement;
}

// What it does: Creates a work-table action named for its order.
// Input: Button ID, caption, accessible label and optional dangerous state.
// Output: An unbound button.
function adminBuildWarehouseCaseButton(adminBuildWarehouseCaseButtonID, adminBuildWarehouseCaseButtonText,
  adminBuildWarehouseCaseButtonLabel, adminBuildWarehouseCaseButtonIsDangerous = false) {
  const adminBuildWarehouseCaseButtonElement = document.createElement("button");
  adminBuildWarehouseCaseButtonElement.type = "button";
  adminBuildWarehouseCaseButtonElement.className = "shared-button shared-button-small"
    + (adminBuildWarehouseCaseButtonIsDangerous ? " shared-button-danger" : "");
  adminBuildWarehouseCaseButtonElement.id = adminBuildWarehouseCaseButtonID;
  adminBuildWarehouseCaseButtonElement.textContent = adminBuildWarehouseCaseButtonText;
  adminBuildWarehouseCaseButtonElement.setAttribute("aria-label", adminBuildWarehouseCaseButtonLabel);
  return adminBuildWarehouseCaseButtonElement;
}

// What it does: Formats a shortage for the wait confirmation.
// Input: A shortage case with the missing and ordered quantities, SKU and name.
// Output: The shortage facts as plain text.
function adminFormatShortageCase(adminFormatShortageCaseRow) {
  return adminFormatShortageCaseRow.missingQuantity + " of "
    + adminFormatShortageCaseRow.adminSearchShortageCaseOrderedQuantity + " missing, "
    + adminFormatShortageCaseRow.adminSearchShortageCaseSKU + " " + adminFormatShortageCaseRow.adminSearchShortageCaseProductName;
}

// What it does: Formats saved picking progress.
// Input: An active picking task.
// Output: The picked and ordered quantities as plain text.
function adminFormatPickingTaskProgress(adminFormatPickingTaskProgressRow) {
  return adminFormatPickingTaskProgressRow.adminSearchPickingTaskPickedQuantityTotal + " of "
    + adminFormatPickingTaskProgressRow.adminSearchPickingTaskOrderedQuantityTotal + " picked";
}

// What it does: Sends one decision from an open dialog and keeps errors inside that dialog.
// Input: The dialog, pressed button, endpoint, body and function that checks the form again.
// Output: A successful response and corner message, or an in-dialog error.
async function adminSubmitWarehouseCaseDialog(adminSubmitWarehouseCaseDialogElement, adminSubmitWarehouseCaseDialogButton,
  adminSubmitWarehouseCaseDialogWebAddress, adminSubmitWarehouseCaseDialogBody, adminSubmitWarehouseCaseDialogCheckInput) {
  const adminSubmitWarehouseCaseDialogMessage = adminSubmitWarehouseCaseDialogElement.querySelector(".shared-message-place");
  adminSubmitWarehouseCaseDialogMessage.replaceChildren();
  adminSubmitWarehouseCaseDialogElement.dataset.saving = "true";
  const adminSubmitWarehouseCaseDialogClose = adminSubmitWarehouseCaseDialogButton.previousElementSibling;
  adminSubmitWarehouseCaseDialogClose.disabled = true;
  try {
    const adminSubmitWarehouseCaseDialogResponse = await sharedSendRequest("POST",
      adminSubmitWarehouseCaseDialogWebAddress, adminSubmitWarehouseCaseDialogBody, adminSubmitWarehouseCaseDialogButton);
    adminViewMenuCounter();
    sharedCloseDialog(adminSubmitWarehouseCaseDialogElement);
    sharedShowMessage("SUCCESS", adminSubmitWarehouseCaseDialogResponse.message);
    return adminSubmitWarehouseCaseDialogResponse;
  } catch (adminSubmitWarehouseCaseDialogError) {
    if (adminSubmitWarehouseCaseDialogElement.open) {
      sharedShowInlineMessage(adminSubmitWarehouseCaseDialogMessage, "ERROR", adminSubmitWarehouseCaseDialogError.message);
    } else {
      sharedShowMessage("ERROR", adminSubmitWarehouseCaseDialogError.message);
    }
    return null;
  } finally {
    delete adminSubmitWarehouseCaseDialogElement.dataset.saving;
    adminSubmitWarehouseCaseDialogClose.disabled = false;
    adminSubmitWarehouseCaseDialogCheckInput();
  }
}

// What it does: Keeps the active confirmation open while the decision is being saved.
// Input: The dialog's native cancellation event.
// Output: An Escape press is ignored while the request is pending.
function adminKeepWarehouseDecisionOpen(adminKeepWarehouseDecisionOpenEvent) {
  if (adminKeepWarehouseDecisionOpenEvent.currentTarget.dataset.saving) {
    adminKeepWarehouseDecisionOpenEvent.preventDefault();
  }
}
