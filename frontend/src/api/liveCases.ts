import { getJson } from "./http";
import { apiPath, apiTime, workAction, type DecidedRow, type WorkPage } from "./liveWork";
import type { InstructionType, OrderStatus, RefundStatus, ShortageStatus } from "./types";

interface CaseOrder { orderID: number; orderReference: string; orderStatus: OrderStatus }
export interface LiveShortageRow {
  c: { shortageCaseID: number; missingQuantity: number; orderedQuantity: number; note: string | null; reportedBy: string; reportedTime: number; caseStatus: ShortageStatus; decidedBy: string | null; decidedTime: number | null; decisionReason: string | null; refundNote?: string };
  order: CaseOrder;
  product: { SKU: string; name: string };
  isPaid: boolean;
}

interface WireShortageRow {
  shortageCaseID: number;
  orderID: number;
  adminSearchShortageCaseOrderReference: string;
  adminSearchShortageCaseOrderStatus: OrderStatus;
  adminSearchShortageCaseOrderIsPaid: boolean;
  adminSearchShortageCaseSKU: string;
  adminSearchShortageCaseProductName: string;
  missingQuantity: number;
  adminSearchShortageCaseOrderedQuantity: number;
  reportNote: string | null;
  adminSearchShortageCaseReportedByUsername: string;
  reportedTime: string;
  caseStatus: ShortageStatus;
  adminSearchShortageCaseDecidedByUsername: string | null;
  decidedTime: string | null;
  decisionNote: string | null;
}

export const shortagePath = (status: string, page?: number) => apiPath(page === undefined ? "/api/admin/shortage-cases/csv" : "/api/admin/shortage-cases", {
  adminSearchShortageCaseCaseStatus: status,
  adminSearchShortageCasePageNumber: page ?? "",
});

export async function loadShortages(status: string, page: number): Promise<WorkPage<LiveShortageRow>> {
  const dto = await getJson<{
    adminSearchShortageCaseRowList: WireShortageRow[];
    adminSearchShortageCasePageNumber: number;
    adminSearchShortageCaseTotalPageCount: number;
    adminSearchShortageCaseTotalRowCount: number;
  }>(shortagePath(status, page));
  return {
    rows: dto.adminSearchShortageCaseRowList.map((r) => ({
      c: {
        shortageCaseID: r.shortageCaseID, missingQuantity: r.missingQuantity, orderedQuantity: r.adminSearchShortageCaseOrderedQuantity,
        note: r.reportNote, reportedBy: r.adminSearchShortageCaseReportedByUsername, reportedTime: Date.parse(r.reportedTime),
        caseStatus: r.caseStatus, decidedBy: r.adminSearchShortageCaseDecidedByUsername, decidedTime: apiTime(r.decidedTime), decisionReason: r.decisionNote,
      },
      order: { orderID: r.orderID, orderReference: r.adminSearchShortageCaseOrderReference, orderStatus: r.adminSearchShortageCaseOrderStatus },
      product: { SKU: r.adminSearchShortageCaseSKU, name: r.adminSearchShortageCaseProductName },
      isPaid: r.adminSearchShortageCaseOrderIsPaid,
    })),
    page: dto.adminSearchShortageCasePageNumber,
    pageCount: dto.adminSearchShortageCaseTotalPageCount,
    total: dto.adminSearchShortageCaseTotalRowCount,
  };
}

export async function loadShortageTarget(status: string, page: number, caseID: number) {
  const current = await loadShortages(status, page);
  if (!caseID || current.rows.some((row) => row.c.shortageCaseID === caseID)) return current;
  for (let n = 1; n <= current.pageCount; n += 1) {
    if (n === current.page) continue;
    const result = await loadShortages(status, n);
    if (result.rows.some((row) => row.c.shortageCaseID === caseID)) return result;
  }
  return current;
}

export interface LivePickingRow {
  order: Pick<CaseOrder, "orderID" | "orderReference">;
  task: { pickingTaskID: number; taskStatus: string; startedTime: number | null };
  assignment: { workerID: number; workerUsername: string; assignedTime: number | null };
  hasOpenShortage: boolean;
  workerNotActive: boolean;
  picked: number;
  total: number;
}

interface WirePickingRow {
  pickingTaskID: number;
  orderID: number;
  adminSearchPickingTaskOrderReference: string;
  taskStatus: string;
  startTime: string | null;
  adminSearchPickingTaskCurrentWorkerUserID: number;
  adminSearchPickingTaskCurrentWorkerUsername: string;
  adminSearchPickingTaskCurrentWorkerAccountStatus: string;
  adminSearchPickingTaskAssignedTime: string | null;
  adminSearchPickingTaskPickedQuantityTotal: number;
  adminSearchPickingTaskOrderedQuantityTotal: number;
  adminSearchPickingTaskHasOpenShortageCase: boolean;
}

export async function loadPickingTasks(page: number): Promise<WorkPage<LivePickingRow>> {
  const dto = await getJson<{
    adminSearchPickingTaskRowList: WirePickingRow[];
    adminSearchPickingTaskPageNumber: number;
    adminSearchPickingTaskTotalPageCount: number;
    adminSearchPickingTaskTotalRowCount: number;
  }>(apiPath("/api/admin/picking-tasks", { adminSearchPickingTaskPageNumber: page }));
  return {
    rows: dto.adminSearchPickingTaskRowList.map((r) => ({
      order: { orderID: r.orderID, orderReference: r.adminSearchPickingTaskOrderReference },
      task: { pickingTaskID: r.pickingTaskID, taskStatus: r.taskStatus, startedTime: apiTime(r.startTime) },
      assignment: {
        workerID: r.adminSearchPickingTaskCurrentWorkerUserID, workerUsername: r.adminSearchPickingTaskCurrentWorkerUsername,
        assignedTime: apiTime(r.adminSearchPickingTaskAssignedTime),
      },
      workerNotActive: r.adminSearchPickingTaskCurrentWorkerAccountStatus !== "ACTIVE",
      hasOpenShortage: r.adminSearchPickingTaskHasOpenShortageCase,
      picked: r.adminSearchPickingTaskPickedQuantityTotal, total: r.adminSearchPickingTaskOrderedQuantityTotal,
    })),
    page: dto.adminSearchPickingTaskPageNumber,
    pageCount: dto.adminSearchPickingTaskTotalPageCount,
    total: dto.adminSearchPickingTaskTotalRowCount,
  };
}

export async function loadWarehouseWorkers() {
  const dto = await getJson<{
    adminViewWarehouseWorkerWorkloadRowList: { userID: number; username: string; adminViewWarehouseWorkerWorkloadActiveTaskCount: number }[];
  }>("/api/admin/picking-tasks/worker-workload");
  return dto.adminViewWarehouseWorkerWorkloadRowList.map((w) => ({ id: w.userID, username: w.username, count: w.adminViewWarehouseWorkerWorkloadActiveTaskCount }));
}

export interface LiveInstruction {
  instructionID: number;
  instructionType: InstructionType;
  source: string;
  submittedTime: number;
}
export interface LiveFailedDeliveryRow {
  order: CaseOrder;
  state: "NEEDS_DECISION" | "WAITING_FOR_CLIENT" | "WAITING_FOR_RETURN";
  latest: { reason: string | null; endedTime: number | null; note: string | null; workerUsername: string | null } | null;
  failureCount: number;
  daysSinceReturn: number | null;
  pending: LiveInstruction | null;
  flags: { review: boolean; reminderDue: boolean; finalReview: boolean };
  pkg: { lastReminderTime: number | null };
  nextReminderTime: number | null;
  canSendReminder: boolean;
  hasOpenShortage: boolean;
  isPaid: boolean;
}

interface WireFailedRow {
  orderID: number;
  orderReference: string;
  adminSearchFailedDeliveryCaseFailureCount: number;
  adminSearchFailedDeliveryCaseLatestFailureReason: string | null;
  adminSearchFailedDeliveryCaseLatestFailureTime: string | null;
  adminSearchFailedDeliveryCaseLatestInternalNote: string | null;
  adminSearchFailedDeliveryCaseLatestWorkerUsername: string | null;
  adminSearchFailedDeliveryCaseIsReturned: boolean;
  adminSearchFailedDeliveryCaseDaysSinceReturn: number | null;
  adminSearchFailedDeliveryCasePendingInstructionID: number | null;
  adminSearchFailedDeliveryCasePendingInstructionType: InstructionType;
  adminSearchFailedDeliveryCasePendingInstructionSource: string;
  adminSearchFailedDeliveryCasePendingInstructionSubmittedTime: string;
  adminSearchFailedDeliveryCaseIsRepeatedFailure: boolean;
  adminSearchFailedDeliveryCaseWaitingFlag: "NONE" | "REMINDER_DUE" | "FINAL_REVIEW";
  adminSearchFailedDeliveryCaseLastReminderTime: string | null;
  adminSearchFailedDeliveryCaseNextReminderTime: string | null;
  adminSearchFailedDeliveryCaseHasOpenShortageCase: boolean;
  adminSearchFailedDeliveryCaseOrderIsPaid: boolean;
}
interface ReviewThresholds { reviewFailureCount: number; reminderDays: number; finalReviewDays: number }
export const failedCasesPath = (filter: string, page?: number) => apiPath(page === undefined ? "/api/admin/failed-delivery-cases/csv" : "/api/admin/failed-delivery-cases", {
  adminSearchFailedDeliveryCaseFilter: filter,
  adminSearchFailedDeliveryCasePageNumber: page ?? "",
});

export async function loadFailedCases(filter: string, page: number): Promise<WorkPage<LiveFailedDeliveryRow> & { thresholds: ReviewThresholds }> {
  const dto = await getJson<{
    adminSearchFailedDeliveryCaseRowList: WireFailedRow[];
    adminSearchFailedDeliveryCasePageNumber: number;
    adminSearchFailedDeliveryCaseTotalPageCount: number;
    adminSearchFailedDeliveryCaseTotalRowCount: number;
    adminSearchFailedDeliveryCaseReviewFailureCount: number;
    adminSearchFailedDeliveryCaseReminderDays: number;
    adminSearchFailedDeliveryCaseFinalReviewDays: number;
  }>(failedCasesPath(filter, page));
  return {
    rows: dto.adminSearchFailedDeliveryCaseRowList.map((r) => {
      const pending = r.adminSearchFailedDeliveryCasePendingInstructionID === null ? null : {
        instructionID: r.adminSearchFailedDeliveryCasePendingInstructionID,
        instructionType: r.adminSearchFailedDeliveryCasePendingInstructionType,
        source: r.adminSearchFailedDeliveryCasePendingInstructionSource,
        submittedTime: Date.parse(r.adminSearchFailedDeliveryCasePendingInstructionSubmittedTime),
      };
      const state = !r.adminSearchFailedDeliveryCaseIsReturned ? "WAITING_FOR_RETURN" : pending ? "NEEDS_DECISION" : "WAITING_FOR_CLIENT";
      const nextReminderTime = apiTime(r.adminSearchFailedDeliveryCaseNextReminderTime);
      return {
        order: { orderID: r.orderID, orderReference: r.orderReference, orderStatus: "DELIVERY_FAILED" },
        state, pending, nextReminderTime,
        latest: r.adminSearchFailedDeliveryCaseFailureCount > 0 ? {
          reason: r.adminSearchFailedDeliveryCaseLatestFailureReason, endedTime: apiTime(r.adminSearchFailedDeliveryCaseLatestFailureTime),
          note: r.adminSearchFailedDeliveryCaseLatestInternalNote, workerUsername: r.adminSearchFailedDeliveryCaseLatestWorkerUsername,
        } : null,
        failureCount: r.adminSearchFailedDeliveryCaseFailureCount,
        daysSinceReturn: r.adminSearchFailedDeliveryCaseDaysSinceReturn,
        flags: {
          review: r.adminSearchFailedDeliveryCaseIsRepeatedFailure,
          reminderDue: r.adminSearchFailedDeliveryCaseWaitingFlag === "REMINDER_DUE",
          finalReview: r.adminSearchFailedDeliveryCaseWaitingFlag === "FINAL_REVIEW",
        },
        pkg: { lastReminderTime: apiTime(r.adminSearchFailedDeliveryCaseLastReminderTime) },
        canSendReminder: state === "WAITING_FOR_CLIENT" && nextReminderTime === null,
        hasOpenShortage: r.adminSearchFailedDeliveryCaseHasOpenShortageCase,
        isPaid: r.adminSearchFailedDeliveryCaseOrderIsPaid,
      };
    }),
    page: dto.adminSearchFailedDeliveryCasePageNumber, pageCount: dto.adminSearchFailedDeliveryCaseTotalPageCount, total: dto.adminSearchFailedDeliveryCaseTotalRowCount,
    thresholds: { reviewFailureCount: dto.adminSearchFailedDeliveryCaseReviewFailureCount, reminderDays: dto.adminSearchFailedDeliveryCaseReminderDays, finalReviewDays: dto.adminSearchFailedDeliveryCaseFinalReviewDays },
  };
}

export async function findFailedCase(orderID: number) {
  for (let n = 1; ; n += 1) {
    const page = await loadFailedCases("ALL", n);
    const row = page.rows.find((r) => r.order.orderID === orderID);
    if (row || n >= page.pageCount) return row ?? null;
  }
}

export interface LiveHeldRow {
  order: CaseOrder;
  pkg: { packageID: number; packageCode: string; heldBy: string; responsibleWorkerID: number | null; responsibleWorker: string | null; lastReassignedTime: number | null };
  holderNotActive: boolean;
}
interface WireHeldRow {
  packageID: number;
  packageQRCode: string;
  orderID: number;
  adminSearchHeldPackageOrderReference: string;
  adminSearchHeldPackageOrderStatus: OrderStatus;
  adminSearchHeldPackageCurrentHandlerUsername: string;
  adminSearchHeldPackageCurrentHandlerAccountStatus: string;
  adminSearchHeldPackageResponsibleWorkerUserID: number | null;
  adminSearchHeldPackageResponsibleWorkerUsername: string | null;
  adminSearchHeldPackageLatestReassignmentTime: string | null;
}
export async function loadHeldPackages(page: number): Promise<WorkPage<LiveHeldRow>> {
  const dto = await getJson<{
    adminSearchHeldPackageRowList: WireHeldRow[];
    adminSearchHeldPackagePageNumber: number;
    adminSearchHeldPackageTotalPageCount: number;
    adminSearchHeldPackageTotalRowCount: number;
  }>(apiPath("/api/admin/held-packages", { adminSearchHeldPackagePageNumber: page }));
  return {
    rows: dto.adminSearchHeldPackageRowList.map((r) => ({
      order: { orderID: r.orderID, orderReference: r.adminSearchHeldPackageOrderReference, orderStatus: r.adminSearchHeldPackageOrderStatus },
      pkg: {
        packageID: r.packageID, packageCode: r.packageQRCode, heldBy: r.adminSearchHeldPackageCurrentHandlerUsername,
        responsibleWorkerID: r.adminSearchHeldPackageResponsibleWorkerUserID,
        responsibleWorker: r.adminSearchHeldPackageResponsibleWorkerUsername, lastReassignedTime: apiTime(r.adminSearchHeldPackageLatestReassignmentTime),
      },
      holderNotActive: r.adminSearchHeldPackageCurrentHandlerAccountStatus !== "ACTIVE",
    })),
    page: dto.adminSearchHeldPackagePageNumber, pageCount: dto.adminSearchHeldPackageTotalPageCount, total: dto.adminSearchHeldPackageTotalRowCount,
  };
}

export async function loadDeliveryWorkers() {
  const dto = await getJson<{
    adminViewDeliveryWorkerWorkloadRowList: { userID: number; username: string; adminViewDeliveryWorkerWorkloadHeldPackageCount: number; adminViewDeliveryWorkerWorkloadResponsiblePackageCount: number }[];
  }>("/api/admin/held-packages/worker-workload");
  return dto.adminViewDeliveryWorkerWorkloadRowList.map((w) => ({
    id: w.userID, username: w.username, held: w.adminViewDeliveryWorkerWorkloadHeldPackageCount, responsible: w.adminViewDeliveryWorkerWorkloadResponsiblePackageCount,
  }));
}

export interface LiveRefundRow {
  order: CaseOrder;
  refund: { refundID: number; amount: number; refundStatus: RefundStatus; ageHours: number; createdTime: number; completedTime: number | null; lastCheckTime: number | null; lastProviderAnswer: string | null; replacedByRefundID: number | null };
  isUnresolved: boolean;
}
interface WireRefundRow {
  refundID: number;
  adminSearchRefundOrderID: number;
  adminSearchRefundOrderReference: string;
  adminSearchRefundOrderStatus: OrderStatus;
  amount: number;
  refundStatus: RefundStatus;
  createdTime: string;
  completedTime: string | null;
  adminSearchRefundAgeHours: number;
  adminSearchRefundIsUnresolved: boolean;
  adminSearchRefundLastCheckTime: string | null;
  adminSearchRefundLastCheckOutcome: string | null;
  adminSearchRefundReplacedByRefundID: number | null;
}
export const refundsPath = (unresolved: boolean, page?: number) => apiPath(page === undefined ? "/api/admin/refunds/csv" : "/api/admin/refunds", {
  adminSearchRefundIsUnresolvedOnly: unresolved,
  adminSearchRefundPageNumber: page ?? "",
});
export async function loadRefunds(unresolved: boolean, page: number): Promise<WorkPage<LiveRefundRow>> {
  const dto = await getJson<{
    adminSearchRefundRowList: WireRefundRow[];
    adminSearchRefundPageNumber: number;
    adminSearchRefundTotalPageCount: number;
    adminSearchRefundTotalRowCount: number;
  }>(refundsPath(unresolved, page));
  return {
    rows: dto.adminSearchRefundRowList.map((r) => ({
      order: { orderID: r.adminSearchRefundOrderID, orderReference: r.adminSearchRefundOrderReference, orderStatus: r.adminSearchRefundOrderStatus },
      refund: {
        refundID: r.refundID, amount: r.amount, refundStatus: r.refundStatus, ageHours: r.adminSearchRefundAgeHours, createdTime: Date.parse(r.createdTime), completedTime: apiTime(r.completedTime),
        lastCheckTime: apiTime(r.adminSearchRefundLastCheckTime), lastProviderAnswer: r.adminSearchRefundLastCheckOutcome,
        replacedByRefundID: r.adminSearchRefundReplacedByRefundID,
      },
      isUnresolved: r.adminSearchRefundIsUnresolved,
    })),
    page: dto.adminSearchRefundPageNumber, pageCount: dto.adminSearchRefundTotalPageCount, total: dto.adminSearchRefundTotalRowCount,
  };
}

const decide = (path: string, body: unknown) => workAction<DecidedRow, DecidedRow>(path, body, (data) => ({ ok: true, message: data.message, data }));

export const decideShortageWait = (id: number, reason: string) => decide(`/api/admin/shortage-cases/${id}/wait`, { adminDecideShortageWaitReason: reason.trim() });
export const decideShortageCancel = (id: number, reason: string, confirmed: boolean) => decide(`/api/admin/shortage-cases/${id}/cancel`, { adminDecideShortageCancelReason: reason.trim(), adminDecideShortageCancelStockCorrectionIsConfirmed: confirmed });
export const reassignPickingTask = (id: number, worker: number, reason: string) => decide(`/api/admin/picking-tasks/${id}/reassign`, { adminReassignPickingTaskNewWorkerUserID: worker, adminReassignPickingTaskReason: reason.trim() });
export const sendReminder = (id: number) => workAction<{ message: string }, undefined>(`/api/admin/failed-delivery-cases/${id}/reminder`, undefined, ({ message }) => ({ ok: true, message }));
export const recordClientInstruction = (id: number, type: InstructionType, confirmed: boolean, method: string, pendingID: number | null) =>
  decide(`/api/admin/failed-delivery-cases/${id}/client-instruction`, {
    adminRecordClientInstructionType: type, adminRecordClientInstructionIdentityIsConfirmed: confirmed,
    adminRecordClientInstructionIdentityCheckMethod: method.trim(), adminRecordClientInstructionExpectedPendingInstructionID: pendingID,
  });
export const approveRedelivery = (id: number, note: string) => decide(`/api/admin/client-instructions/${id}/approve-redelivery`, { adminApproveRedeliveryNote: note.trim() });
export const approveCancellation = (id: number, note: string) => decide(`/api/admin/client-instructions/${id}/approve-cancellation`, { adminApproveCancellationNote: note.trim() });
export const rejectClientInstruction = (id: number, reason: string) => decide(`/api/admin/client-instructions/${id}/reject`, { adminRejectClientInstructionReason: reason.trim() });
export const reassignDelivery = (id: number, worker: number, reason: string) => decide(`/api/admin/held-packages/${id}/reassign`, { adminReassignDeliveryNewWorkerUserID: worker, adminReassignDeliveryReason: reason.trim() });
export const retryRefund = (id: number, reason: string) => decide(`/api/admin/refunds/${id}/retry`, { adminRetryRefundReason: reason.trim() });

interface RefundCheckDto { refundStatus: RefundStatus; adminCheckRefundStatusMessage: string; adminCheckRefundStatusProviderOutcome: string }
export const checkProviderStatus = (id: number) => workAction<RefundCheckDto, RefundCheckDto>(`/api/admin/refunds/${id}/check`, undefined, (data) => ({ ok: true, message: data.adminCheckRefundStatusMessage, data }));

export function decidedRefundLine(decision?: DecidedRow) {
  if (!decision?.refundID) return "Nothing is refunded.";
  return decision.adminDecidedRowRefundIsNew ? `Refund ${decision.refundID} was requested.` : `Refund ${decision.refundID} already exists.`;
}


export async function findRefund(refundID: number) {
  for (let n = 1; ; n += 1) {
    const page = await loadRefunds(false, n);
    const row = page.rows.find((r) => r.refund.refundID === refundID);
    if (row || n >= page.pageCount) return row ?? null;
  }
}
