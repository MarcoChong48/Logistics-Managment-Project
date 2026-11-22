"use strict";

// ===== SECTION 1: PAGE ELEMENTS =====
const adminViewOrderBackLink = document.getElementById("adminViewOrderBackLink");
const adminViewOrderMessage = document.getElementById("adminViewOrderMessage");
const adminViewOrderHeaderPanel = document.getElementById("adminViewOrderHeaderPanel");
const adminViewOrderReferenceText = document.getElementById("adminViewOrderReferenceText");
const adminViewOrderClientUsernameText = document.getElementById("adminViewOrderClientUsernameText");
const adminViewOrderCreatedTimeText = document.getElementById("adminViewOrderCreatedTimeText");
const adminViewOrderPaymentDeadlineText = document.getElementById("adminViewOrderPaymentDeadlineText");
const adminViewOrderTotalAmountText = document.getElementById("adminViewOrderTotalAmountText");
const adminViewOrderItemTableBody = document.getElementById("adminViewOrderItemTableBody");
const adminViewOrderItemTotalAmountText = document.getElementById("adminViewOrderItemTotalAmountText");
const adminViewOrderRefundPanel = document.getElementById("adminViewOrderRefundPanel");
const adminViewOrderPaymentTableBody = document.getElementById("adminViewOrderPaymentTableBody");
const adminViewOrderRefundTableBody = document.getElementById("adminViewOrderRefundTableBody");
const adminViewOrderPickingPanel = document.getElementById("adminViewOrderPickingPanel");
const adminViewOrderPickingAssignmentTableBody = document.getElementById("adminViewOrderPickingAssignmentTableBody");
const adminViewOrderPackagePanel = document.getElementById("adminViewOrderPackagePanel");
const adminViewOrderDeliveryAttemptTableBody = document.getElementById("adminViewOrderDeliveryAttemptTableBody");
const adminViewOrderClientInstructionTableBody = document.getElementById("adminViewOrderClientInstructionTableBody");
const adminViewOrderShortageCasePanel = document.getElementById("adminViewOrderShortageCasePanel");
const adminViewOrderShortageCaseDecideLink = document.getElementById("adminViewOrderShortageCaseDecideLink");
const adminViewOrderShortageCaseTableBody = document.getElementById("adminViewOrderShortageCaseTableBody");
const adminViewOrderTimelineList = document.getElementById("adminViewOrderTimelineList");

// ===== SECTION 2: PAGE STATE =====
// This page displays a single response and shows no controls that change the order.

// ===== SECTION 3: START OF PAGE =====

// What it does: Open the order named in the address after the Admin check.
// Input: None. Uses the current page's order ID and the previous page's address.
// Output: A read-only order or an inline error message.
adminBuildPageFrame("Orders").then(() => {
  if (document.querySelector("main").hidden) {
    return;
  }
  adminViewOrderSetBackLink();
  sharedWatchTableScroll();
  adminViewOrder(sharedReadPageParameter("orderID"));
});

// ===== SECTION 4: adminViewOrder =====

// What it does: Draw the restricted view of one order and follow a shortage card fragment.
// Input: The order ID from the page address.
// Output: Visible detail cards, or the server's error in the order message place.
async function adminViewOrder(adminViewOrderID) {
  // Without a numeric ID the page still supplies the usual missing-order feedback.
  if (!/^[0-9]+$/.test(adminViewOrderID)) {
    sharedShowInlineMessage(adminViewOrderMessage, "ERROR", "Order not found.");
    return;
  }

  try {
    const adminViewOrderResponse = await sharedSendRequest("GET", "/api/admin/orders/" + adminViewOrderID);
    adminViewOrderRenderHeader(adminViewOrderResponse);
    adminViewOrderRenderItems(adminViewOrderResponse);
    adminViewOrderRenderPaymentAndRefund(adminViewOrderResponse);
    adminViewOrderRenderPicking(adminViewOrderResponse);
    adminViewOrderRenderDelivery(adminViewOrderResponse);
    adminViewOrderRenderShortageCases(adminViewOrderResponse.adminViewOrderShortageCaseList);
    adminViewOrderRenderTimeline(adminViewOrderResponse.adminViewOrderTimelineList);

    // Reveal the complete order together, then scroll to the requested card if present.
    const adminViewOrderCardList = document.querySelectorAll("main > section[hidden], main > .shared-grid-two[hidden]");
    for (const adminViewOrderCard of adminViewOrderCardList) {
      adminViewOrderCard.hidden = false;
    }
    if (location.hash === "#adminViewOrderShortageCasePanel") {
      adminViewOrderShortageCasePanel.scrollIntoView();
    }
  } catch (adminViewOrderError) {
    sharedShowInlineMessage(adminViewOrderMessage, "ERROR", adminViewOrderError.message);
  }
}

// ===== SECTION 5: HELPER FUNCTIONS =====

// What it does: Preserve a recognized Admin list's full address, including its filters and page number.
// Input: None. Reads the browser referrer.
// Output: A back link to the previous list, or the original Orders link when opened some other way.
function adminViewOrderSetBackLink() {
  if (!document.referrer) {
    return;
  }
  const adminViewOrderPreviousAddress = new URL(document.referrer);
  if (adminViewOrderPreviousAddress.origin !== location.origin) {
    return;
  }
  const adminViewOrderPreviousPageName = new Map([
    ["/admin/admin-orders.html", "Back to orders"],
    ["/admin/admin-warehouse-cases.html", "Back to warehouse cases"],
    ["/admin/admin-delivery-cases.html", "Back to delivery cases"],
    ["/admin/admin-refunds.html", "Back to refunds"],
    ["/admin/admin-logs.html", "Back to logs"]
  ]).get(adminViewOrderPreviousAddress.pathname);
  if (adminViewOrderPreviousPageName) {
    adminViewOrderBackLink.textContent = adminViewOrderPreviousPageName;
    adminViewOrderBackLink.href = adminViewOrderPreviousAddress.href;
  }
}

// What it does: Display the order's reference and facts, without delivery detail contents.
// Input: The order detail response.
// Output: The title, status badge, totals and version count.
function adminViewOrderRenderHeader(adminViewOrderResponse) {
  adminViewOrderReferenceText.textContent = "Order " + adminViewOrderResponse.orderReference;
  document.title = adminViewOrderReferenceText.textContent + " - LMS Admin";
  const adminViewOrderStatusBadge = adminBuildStatusBadge(adminViewOrderResponse.orderStatus);
  adminViewOrderStatusBadge.id = "adminViewOrderStatusBadge";
  adminViewOrderReferenceText.after(" ", adminViewOrderStatusBadge);

  adminViewOrderClientUsernameText.textContent = adminViewOrderResponse.adminViewOrderClientUsername;
  adminViewOrderCreatedTimeText.textContent = sharedFormatTime(adminViewOrderResponse.createdTime);
  adminViewOrderTotalAmountText.textContent = sharedFormatMoney(adminViewOrderResponse.totalAmount);
  adminViewOrderItemTotalAmountText.textContent = adminViewOrderTotalAmountText.textContent;
  const adminViewOrderDeliveryDetailVersionCount = adminViewOrderResponse.adminViewOrderDeliveryDetailVersionCount;
  const adminViewOrderDeliveryDetailVersionCountText =
    adminViewOrderHeaderPanel.querySelector(".shared-fact:last-child dd");
  adminViewOrderDeliveryDetailVersionCountText.textContent = adminViewOrderDeliveryDetailVersionCount
    + (adminViewOrderDeliveryDetailVersionCount === 1 ? " version" : " versions") + ", content not shown to Admin";

  // Only an order still awaiting payment needs the deadline in its facts.
  if (adminViewOrderResponse.orderStatus === "AWAITING_PAYMENT") {
    adminViewOrderPaymentDeadlineText.textContent = sharedFormatTime(adminViewOrderResponse.paymentDeadline);
    adminViewOrderPaymentDeadlineText.parentElement.hidden = false;
  }
}

// What it does: Show the products, quantities and historical prices for the order.
// Input: The order detail response.
// Output: Read-only item rows beneath the order's item table headings.
function adminViewOrderRenderItems(adminViewOrderResponse) {
  // What it does: Select the six visible columns of one order item.
  // Input: An item record.
  // Output: An array of cell values in table order.
  const adminViewOrderItemRowList = adminViewOrderResponse.adminViewOrderItemList.map((adminViewOrderItem) => [
    adminViewOrderItem.adminViewOrderItemSKU, adminViewOrderItem.adminViewOrderItemProductName,
    adminViewOrderItem.quantity, adminViewOrderItem.pickedQuantity, sharedFormatMoney(adminViewOrderItem.unitPrice),
    sharedFormatMoney(adminViewOrderItem.adminViewOrderItemLineTotal)
  ]);
  adminViewOrderFillTable(adminViewOrderItemTableBody, adminViewOrderItemRowList);
}

// What it does: Show separate payment and refund histories, with their empty states.
// Input: The order detail response.
// Output: The two cards with populated tables or their empty messages.
function adminViewOrderRenderPaymentAndRefund(adminViewOrderResponse) {
  // What it does: Format one payment as the card's three columns.
  // Input: A payment record.
  // Output: Amount, status and the relevant checkout timestamps.
  const adminViewOrderPaymentRowList = adminViewOrderResponse.adminViewOrderPaymentList.map((adminViewOrderPayment) => [
    sharedFormatMoney(adminViewOrderPayment.amount), adminViewOrderBuildBadge(adminViewOrderPayment.paymentStatus),
    adminViewOrderBuildTimes([
      ["Started", adminViewOrderPayment.createdTime], ["Accepted", adminViewOrderPayment.acceptedTime],
      ["Ended", adminViewOrderPayment.acceptedTime ? null : adminViewOrderPayment.endedTime],
      ["Checkout expires", !adminViewOrderPayment.acceptedTime && !adminViewOrderPayment.endedTime
        ? adminViewOrderPayment.checkoutExpiryTime : null]
    ])
  ]);
  adminViewOrderFillTable(adminViewOrderPaymentTableBody, adminViewOrderPaymentRowList);

  // What it does: Format one refund as the card's five columns.
  // Input: A refund record, already ordered newest first by the server.
  // Output: The refund and payment numbers, amount, state and times.
  const adminViewOrderRefundRowList = adminViewOrderResponse.adminViewOrderRefundList.map((adminViewOrderRefund) => [
    adminViewOrderRefund.refundID, adminViewOrderRefund.paymentID, sharedFormatMoney(adminViewOrderRefund.amount),
    adminViewOrderBuildBadge(adminViewOrderRefund.refundStatus),
    adminViewOrderBuildTimes([
      ["Requested", adminViewOrderRefund.createdTime], ["Completed", adminViewOrderRefund.completedTime]
    ])
  ]);
  adminViewOrderFillTable(adminViewOrderRefundTableBody, adminViewOrderRefundRowList);
  adminViewOrderRefundPanel.parentElement.classList.toggle("admin-order-payment-refund-grid",
    adminViewOrderRefundRowList.length > 0);
}

// What it does: Show the picking task facts and the workers assigned to the task.
// Input: The order detail response.
// Output: The picking card and its assignment table or empty message.
function adminViewOrderRenderPicking(adminViewOrderResponse) {
  const adminViewOrderPickingTask = adminViewOrderResponse.adminViewOrderPickingTask;
  // A missing task hides its facts. Its Workers part still shows the count and empty line.
  adminViewOrderPickingPanel.querySelector(".shared-card-body").hidden = !adminViewOrderPickingTask;
  adminViewOrderFillFacts(adminViewOrderPickingPanel, [
    adminViewOrderBuildBadge(adminViewOrderPickingTask?.taskStatus),
    sharedFormatTime(adminViewOrderPickingTask?.startTime),
    sharedFormatTime(adminViewOrderPickingTask?.confirmPackingTime)
  ]);

  // What it does: Format one picking assignment.
  // Input: An assignment record.
  // Output: Worker, assigned time and ended time.
  const adminViewOrderPickingAssignmentRowList = adminViewOrderResponse.adminViewOrderPickingAssignmentList
    .map((adminViewOrderPickingAssignment) => [
      adminViewOrderPickingAssignment.adminViewOrderPickingAssignmentWorkerUsername,
      sharedFormatTime(adminViewOrderPickingAssignment.assignedTime),
      sharedFormatTime(adminViewOrderPickingAssignment.endedTime)
    ]);
  adminViewOrderFillTable(adminViewOrderPickingAssignmentTableBody, adminViewOrderPickingAssignmentRowList);
}

// What it does: Show custody, delivery attempts and client requests from the restricted response.
// Input: The order detail response.
// Output: The delivery card with separate tables for attempts and requests.
function adminViewOrderRenderDelivery(adminViewOrderResponse) {
  const adminViewOrderPackage = adminViewOrderResponse.adminViewOrderPackage;
  // A missing package hides its facts while the delivery history parts stay visible.
  adminViewOrderPackagePanel.querySelector(".shared-card-body").hidden = !adminViewOrderPackage;
  adminViewOrderFillFacts(adminViewOrderPackagePanel, [
    adminViewOrderPackage?.packageQRCode, adminViewOrderBuildBadge(adminViewOrderPackage?.custodyStatus),
    adminViewOrderPackage?.adminViewOrderPackageCurrentHandlerUsername,
    adminViewOrderPackage?.adminViewOrderPackageResponsibleWorkerUsername
  ]);

  // What it does: Format one delivery attempt using the exposed proof counts.
  // Input: A delivery attempt record.
  // Output: The attempt columns with no proof references.
  const adminViewOrderDeliveryAttemptRowList = adminViewOrderResponse.adminViewOrderDeliveryAttemptList
    .map((adminViewOrderDeliveryAttempt) => [
      adminViewOrderDeliveryAttempt.adminViewOrderDeliveryAttemptWorkerUsername,
      sharedFormatTime(adminViewOrderDeliveryAttempt.startTime),
      sharedFormatTime(adminViewOrderDeliveryAttempt.endTime),
      adminViewOrderBuildBadge(adminViewOrderDeliveryAttempt.outcome),
      adminViewOrderDeliveryAttempt.failureReason === "UNABLE_TO_CONTACT"
        ? "Unable to contact client" : adminViewOrderFormatWord(adminViewOrderDeliveryAttempt.failureReason),
      adminViewOrderDeliveryAttempt.internalNote, adminViewOrderFormatProof(adminViewOrderDeliveryAttempt)
    ]);
  adminViewOrderFillTable(adminViewOrderDeliveryAttemptTableBody, adminViewOrderDeliveryAttemptRowList);

  // What it does: Format one client request for the history table.
  // Input: A client instruction record.
  // Output: The type, status, source, submitted time and resolution columns.
  const adminViewOrderClientInstructionRowList = adminViewOrderResponse.adminViewOrderClientInstructionList
    .map((adminViewOrderClientInstruction) => [
      adminViewOrderFormatWord(adminViewOrderClientInstruction.instructionType),
      adminViewOrderBuildBadge(adminViewOrderClientInstruction.status),
      adminViewOrderClientInstruction.source === "ADMIN_MANUAL" ? "Admin"
        : adminViewOrderFormatWord(adminViewOrderClientInstruction.source),
      sharedFormatTime(adminViewOrderClientInstruction.submittedTime),
      adminViewOrderClientInstruction.adminViewOrderClientInstructionResolvedByUsername,
      sharedFormatTime(adminViewOrderClientInstruction.resolvedTime), adminViewOrderClientInstruction.resolutionNote
    ]);
  adminViewOrderFillTable(adminViewOrderClientInstructionTableBody, adminViewOrderClientInstructionRowList);
}

// What it does: Draw all shortage cases and link to the matching Warehouse cases filter.
// Input: The server's newest-first shortage case list.
// Output: A read-only table or empty state, and the decision link when it applies.
function adminViewOrderRenderShortageCases(adminViewOrderShortageCaseList) {
  let adminViewOrderShortageCaseFilterStatus = "";
  const adminViewOrderShortageCaseRowList = [];
  for (const adminViewOrderShortageCase of adminViewOrderShortageCaseList) {
    // An open case takes precedence over a case that is waiting for restock.
    if (adminViewOrderShortageCase.caseStatus === "OPEN" ||
        (!adminViewOrderShortageCaseFilterStatus && adminViewOrderShortageCase.caseStatus === "WAITING_RESTOCK")) {
      adminViewOrderShortageCaseFilterStatus = adminViewOrderShortageCase.caseStatus;
    }
    adminViewOrderShortageCaseRowList.push([
      "Case " + adminViewOrderShortageCase.shortageCaseID, adminViewOrderShortageCase.adminViewOrderShortageCaseSKU,
      adminViewOrderShortageCase.missingQuantity, adminViewOrderBuildBadge(adminViewOrderShortageCase.caseStatus),
      adminViewOrderFormatWord(adminViewOrderShortageCase.decision), adminViewOrderBuildTimes([
        ["Reported", adminViewOrderShortageCase.reportedTime], ["Decided", adminViewOrderShortageCase.decidedTime]
      ])
    ]);
  }
  adminViewOrderFillTable(adminViewOrderShortageCaseTableBody, adminViewOrderShortageCaseRowList);
  if (adminViewOrderShortageCaseFilterStatus) {
    adminViewOrderShortageCaseDecideLink.href =
      "admin-warehouse-cases.html?adminSearchShortageCaseCaseStatus=" + adminViewOrderShortageCaseFilterStatus;
    adminViewOrderShortageCaseDecideLink.parentElement.hidden = false;
  }
}

// What it does: Group the chronological timeline by the browser's local calendar day.
// Input: The server's timeline entries in ascending time order.
// Output: Day blocks with local hours and minutes and the original datetime attributes.
function adminViewOrderRenderTimeline(adminViewOrderTimelineRowList) {
  let adminViewOrderTimelineDay = "";
  let adminViewOrderTimelineDayList;
  for (const adminViewOrderTimelineRow of adminViewOrderTimelineRowList) {
    const adminViewOrderTimelineISOTime = adminViewOrderTimelineRow.adminViewOrderTimelineTime;
    const [adminViewOrderTimelineDate, adminViewOrderTimelineHours] =
      sharedFormatTime(adminViewOrderTimelineISOTime).split(" ");
    if (!adminViewOrderTimelineHours) {
      continue;
    }

    // Make one date heading and ordered list for each day in the timeline.
    if (adminViewOrderTimelineDay !== adminViewOrderTimelineDate) {
      adminViewOrderTimelineDay = adminViewOrderTimelineDate;
      const adminViewOrderTimelineDayBlock = document.createElement("div");
      adminViewOrderTimelineDayBlock.className = "shared-timeline-day";
      const adminViewOrderTimelineDateHeading = document.createElement("h3");
      adminViewOrderTimelineDateHeading.className = "shared-timeline-date";
      adminViewOrderTimelineDateHeading.textContent = adminViewOrderTimelineDate;
      adminViewOrderTimelineDayList = document.createElement("ol");
      adminViewOrderTimelineDayList.className = "shared-timeline";
      adminViewOrderTimelineDayBlock.append(adminViewOrderTimelineDateHeading, adminViewOrderTimelineDayList);
      adminViewOrderTimelineList.append(adminViewOrderTimelineDayBlock);
    }

    // Use text nodes for server-provided labels and keep the timestamp machine-readable.
    const adminViewOrderTimelineEntry = document.createElement("li");
    adminViewOrderTimelineEntry.className = "shared-timeline-entry";
    const adminViewOrderTimelineTime = document.createElement("time");
    adminViewOrderTimelineTime.className = "shared-timeline-time";
    adminViewOrderTimelineTime.dateTime = adminViewOrderTimelineISOTime;
    adminViewOrderTimelineTime.textContent = adminViewOrderTimelineHours;
    const adminViewOrderTimelineText = document.createElement("span");
    adminViewOrderTimelineText.className = "shared-timeline-text";
    adminViewOrderTimelineText.textContent = adminViewOrderTimelineRow.adminViewOrderTimelineText;
    if (adminViewOrderTimelineRow.adminViewOrderTimelineActorUsername) {
      const adminViewOrderTimelineActor = document.createElement("span");
      adminViewOrderTimelineActor.className = "shared-timeline-actor";
      adminViewOrderTimelineActor.textContent = " " + adminViewOrderTimelineRow.adminViewOrderTimelineActorUsername;
      adminViewOrderTimelineText.append(adminViewOrderTimelineActor);
    }
    adminViewOrderTimelineEntry.append(adminViewOrderTimelineTime, adminViewOrderTimelineText);
    adminViewOrderTimelineDayList.append(adminViewOrderTimelineEntry);
  }
}

// What it does: Fill facts from left to right in the card's displayed order.
// Input: The card and the list of values for its facts.
// Output: Updated facts, with a dash for an absent value.
function adminViewOrderFillFacts(adminViewOrderFactPanel, adminViewOrderFactValues) {
  const adminViewOrderFactElements = adminViewOrderFactPanel.querySelectorAll(".shared-fact-value");
  for (const [adminViewOrderFactIndex, adminViewOrderFactValue] of adminViewOrderFactValues.entries()) {
    adminViewOrderFactElements[adminViewOrderFactIndex].replaceChildren(adminViewOrderFactValue || "-");
  }
}

// What it does: Draw read-only rows, sharing the sizing and headings already declared in the table.
// Input: The table body and rows of cell values, each text, number or a safe DOM node.
// Output: Rows with semantic headings or the matching empty message, plus a count for card parts.
function adminViewOrderFillTable(adminViewOrderTableBody, adminViewOrderTableRowList) {
  const adminViewOrderTable = adminViewOrderTableBody.closest("table");
  const adminViewOrderColumnList = adminViewOrderTable.tHead.rows[0].cells;
  const adminViewOrderIsWorkTable = adminViewOrderTable.classList.contains("shared-table-work");
  for (const adminViewOrderTableRowValues of adminViewOrderTableRowList) {
    const adminViewOrderTableRow = document.createElement("tr");
    for (const [adminViewOrderCellIndex, adminViewOrderCellValue] of adminViewOrderTableRowValues.entries()) {
      const adminViewOrderCell = document.createElement(adminViewOrderCellIndex === 0 ? "th" : "td");
      const adminViewOrderColumn = adminViewOrderColumnList[adminViewOrderCellIndex];
      if (adminViewOrderCellIndex === 0) {
        adminViewOrderCell.scope = "row";
      }
      // Width classes belong only to the headings. Keep each cell's alignment and wrapping.
      adminViewOrderCell.className = adminViewOrderColumn.className.replace(/(?:^|\s)shared-column-\S+/g, "").trim();
      if (adminViewOrderIsWorkTable) {
        adminViewOrderCell.dataset.title = adminViewOrderColumn.textContent;
      }
      adminViewOrderCell.append(adminViewOrderCellValue === "" ? "-" : adminViewOrderCellValue ?? "-");
      adminViewOrderTableRow.append(adminViewOrderCell);
    }
    adminViewOrderTableBody.append(adminViewOrderTableRow);
  }

  // Card parts show their row count even when an empty message replaces the table.
  const adminViewOrderTableScroll = adminViewOrderTable.parentElement;
  const adminViewOrderTablePartTitle = adminViewOrderTableScroll.previousElementSibling;
  if (adminViewOrderTablePartTitle?.classList.contains("shared-card-part-title")) {
    adminViewOrderTablePartTitle.querySelector(".shared-card-count").textContent = adminViewOrderTableRowList.length;
  }
  const adminViewOrderTableEmpty = adminViewOrderTableScroll.nextElementSibling;
  if (adminViewOrderTableEmpty?.classList.contains("shared-table-empty")) {
    adminViewOrderTableScroll.hidden = adminViewOrderTableRowList.length === 0;
    adminViewOrderTableEmpty.hidden = adminViewOrderTableRowList.length > 0;
  }
}

// What it does: Build labeled lines for the available timestamps in a work table cell.
// Input: The labels paired with their stored ISO times.
// Output: A fragment of lines with local times and original datetime attributes, or a dash.
function adminViewOrderBuildTimes(adminViewOrderTimePairs) {
  const adminViewOrderTimeLines = document.createDocumentFragment();
  for (const [adminViewOrderTimeLabel, adminViewOrderISOTime] of adminViewOrderTimePairs) {
    if (!adminViewOrderISOTime) {
      continue;
    }
    const adminViewOrderTimeLine = document.createElement("span");
    adminViewOrderTimeLine.className = "shared-cell-main";
    const adminViewOrderTimeElement = document.createElement("time");
    adminViewOrderTimeElement.className = "shared-nowrap";
    adminViewOrderTimeElement.dateTime = adminViewOrderISOTime;
    adminViewOrderTimeElement.textContent = sharedFormatTime(adminViewOrderISOTime);
    adminViewOrderTimeLine.append(adminViewOrderTimeLabel + " ", adminViewOrderTimeElement);
    adminViewOrderTimeLines.append(adminViewOrderTimeLine);
  }
  return adminViewOrderTimeLines.hasChildNodes() ? adminViewOrderTimeLines : "-";
}

// What it does: Show an optional status in the design's badge.
// Input: A stored status word, possibly absent.
// Output: A status badge or a dash.
function adminViewOrderBuildBadge(adminViewOrderStatusWord) {
  return adminViewOrderStatusWord ? adminBuildStatusBadge(adminViewOrderStatusWord) : "-";
}

// What it does: Show an optional type, reason or decision in plain words.
// Input: A stored word, possibly absent.
// Output: A plain label or a dash.
function adminViewOrderFormatWord(adminViewOrderStoredWord) {
  return adminViewOrderStoredWord ? sharedFormatStatusWord(adminViewOrderStoredWord) : "-";
}

// What it does: Show the proof counts of an attempt while keeping the proof itself private.
// Input: An attempt with photo and signature counts.
// Output: Count text when there is proof, otherwise a dash.
function adminViewOrderFormatProof(adminViewOrderProofAttempt) {
  const adminViewOrderPhotoCount = adminViewOrderProofAttempt.adminViewOrderDeliveryAttemptPhotoProofCount;
  const adminViewOrderSignatureCount = adminViewOrderProofAttempt.adminViewOrderDeliveryAttemptSignatureProofCount;
  if (adminViewOrderPhotoCount === 0 && adminViewOrderSignatureCount === 0) {
    return "-";
  }
  return adminViewOrderPhotoCount + (adminViewOrderPhotoCount === 1 ? " photo, " : " photos, ")
    + adminViewOrderSignatureCount + (adminViewOrderSignatureCount === 1 ? " signature" : " signatures")
    + " (not viewable by Admin)";
}
