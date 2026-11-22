import { ApiError, getJson } from "./http";
import { apiPath, apiTime, type WorkPage } from "./liveWork";
import { filterDateTime } from "../lib/format";
import type { DeliveryAttempt, InstructionStatus, InstructionType, Order, OrderStatus, Payment, PickingTask, RefundStatus, ShortageCase } from "./types";
import type { JourneyStop, OrderFilters } from "./queries";

type OrderBasics = Pick<Order, "orderID" | "orderReference" | "orderStatus" | "createdTime" | "paymentDeadline" | "totalAmount">;
interface WireOrderBasics {
  orderID: number;
  orderReference: string;
  orderStatus: OrderStatus;
  createdTime: string;
  paymentDeadline: string | null;
  totalAmount: number;
}
interface WireOrderRow extends WireOrderBasics {
  adminSearchOrderClientUsername: string;
  adminSearchOrderLatestPaymentStatus: string | null;
  adminSearchOrderLatestRefundStatus: string | null;
  adminSearchOrderHasOpenCase: boolean;
}
export interface LiveOrderRow {
  order: OrderBasics;
  client: string;
  paymentStatus: string | null;
  refundStatus: string | null;
  hasOpenCase: boolean;
}

export function orderSearchPath(filters: OrderFilters, page?: number) {
  const from = filterDateTime(filters.from);
  const to = filterDateTime(filters.to, true);
  return apiPath(page === undefined ? "/api/admin/orders/csv" : "/api/admin/orders", {
    adminSearchOrderKeyword: filters.keyword.trim(),
    adminSearchOrderOrderStatus: filters.status,
    adminSearchOrderCreatedFromTime: from,
    adminSearchOrderCreatedToTime: to,
    adminSearchOrderPageNumber: page ?? "",
  });
}

const mapOrder = (o: WireOrderBasics): OrderBasics => ({
  orderID: o.orderID,
  orderReference: o.orderReference,
  orderStatus: o.orderStatus,
  createdTime: Date.parse(o.createdTime),
  paymentDeadline: apiTime(o.paymentDeadline),
  totalAmount: o.totalAmount,
});

export async function loadOrders(filters: OrderFilters, page: number): Promise<WorkPage<LiveOrderRow>> {
  const dto = await getJson<{
    adminSearchOrderRowList: WireOrderRow[];
    adminSearchOrderPageNumber: number;
    adminSearchOrderTotalPageCount: number;
    adminSearchOrderTotalRowCount: number;
  }>(orderSearchPath(filters, page));
  return {
    rows: dto.adminSearchOrderRowList.map((r) => ({
      order: mapOrder(r),
      client: r.adminSearchOrderClientUsername,
      paymentStatus: r.adminSearchOrderLatestPaymentStatus,
      refundStatus: r.adminSearchOrderLatestRefundStatus,
      hasOpenCase: r.adminSearchOrderHasOpenCase,
    })),
    page: dto.adminSearchOrderPageNumber,
    pageCount: dto.adminSearchOrderTotalPageCount,
    total: dto.adminSearchOrderTotalRowCount,
  };
}

interface WireDetail extends WireOrderBasics {
  adminViewOrderClientUsername: string;
  adminViewOrderDeliveryDetailVersionCount: number;
  adminViewOrderItemList: { orderItemID: number; adminViewOrderItemSKU: string; adminViewOrderItemProductName: string; quantity: number; pickedQuantity: number; unitPrice: number; adminViewOrderItemLineTotal: number }[];
  adminViewOrderPaymentList: { paymentID: number; amount: number; paymentStatus: Payment["paymentStatus"]; createdTime: string; acceptedTime: string | null; endedTime: string | null }[];
  adminViewOrderRefundList: { refundID: number; paymentID: number; amount: number; refundStatus: RefundStatus; createdTime: string; completedTime: string | null }[];
  adminViewOrderPickingTask: { pickingTaskID: number; taskStatus: PickingTask["taskStatus"]; startTime: string | null; confirmPackingTime: string | null } | null;
  adminViewOrderPickingAssignmentList: { assignmentID: number; adminViewOrderPickingAssignmentWorkerUsername: string; assignedTime: string; endedTime: string | null }[];
  adminViewOrderPackage: { packageID: number; packageQRCode: string; custodyStatus: string; adminViewOrderPackageCurrentHandlerUsername: string | null; adminViewOrderPackageResponsibleWorkerUsername: string | null } | null;
  adminViewOrderDeliveryAttemptList: { deliveryAttemptID: number; adminViewOrderDeliveryAttemptWorkerUsername: string; startTime: string; endTime: string | null; outcome: DeliveryAttempt["outcome"]; failureReason: string | null; internalNote: string | null; adminViewOrderDeliveryAttemptPhotoProofCount: number; adminViewOrderDeliveryAttemptSignatureProofCount: number }[];
  adminViewOrderClientInstructionList: { instructionID: number; instructionType: InstructionType; status: InstructionStatus; source: string; submittedTime: string; adminViewOrderClientInstructionResolvedByUsername: string | null; resolvedTime: string | null; resolutionNote: string | null }[];
  adminViewOrderShortageCaseList: { shortageCaseID: number; adminViewOrderShortageCaseSKU: string; missingQuantity: number; caseStatus: ShortageCase["caseStatus"]; decision: string | null; reportedTime: string; decidedTime: string | null }[];
  adminViewOrderTimelineList: { adminViewOrderTimelineTime: string; adminViewOrderTimelineKind: string; adminViewOrderTimelineText: string; adminViewOrderTimelineActorUsername: string | null }[];
}

type TimelineDay = { day: string; entries: { time: number; text: string; actor: string | null }[] };
export type LiveOrderDetail = NonNullable<Awaited<ReturnType<typeof loadOrderDetail>>>;

export async function loadOrderDetail(identifier: string) {
  const orderID = /^\d+$/.test(identifier)
    ? Number(identifier)
    : (await loadOrders({ keyword: identifier, status: "", from: "", to: "" }, 1)).rows.find(({ order }) => order.orderReference === identifier)?.order.orderID;
  if (!orderID) return null;

  let dto: WireDetail;
  try {
    dto = await getJson<WireDetail>(`/api/admin/orders/${orderID}`);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return null;
    throw error;
  }
  const order = { ...mapOrder(dto), deliveryDetailsVersionCount: dto.adminViewOrderDeliveryDetailVersionCount };
  const items = dto.adminViewOrderItemList.map((i) => ({
    id: i.orderItemID, code: i.adminViewOrderItemSKU, name: i.adminViewOrderItemProductName,
    quantity: i.quantity, picked: i.pickedQuantity, unitPrice: i.unitPrice, lineTotal: i.adminViewOrderItemLineTotal,
  }));
  const payments = dto.adminViewOrderPaymentList.map((p) => ({
    ...p, createdTime: Date.parse(p.createdTime), endedTime: apiTime(p.endedTime), acceptedTime: apiTime(p.acceptedTime),
  }));
  const refunds = dto.adminViewOrderRefundList.map((r) => ({
    ...r, createdTime: Date.parse(r.createdTime), completedTime: apiTime(r.completedTime),
  }));
  const sourceTask = dto.adminViewOrderPickingTask;
  const task = sourceTask && {
    taskStatus: sourceTask.taskStatus, startedTime: apiTime(sourceTask.startTime), packedTime: apiTime(sourceTask.confirmPackingTime),
  };
  const assignments = dto.adminViewOrderPickingAssignmentList.map((a) => ({
    assignmentID: a.assignmentID, workerUsername: a.adminViewOrderPickingAssignmentWorkerUsername,
    assignedTime: Date.parse(a.assignedTime), endedTime: apiTime(a.endedTime),
  }));
  const sourcePkg = dto.adminViewOrderPackage;
  const pkg = sourcePkg && {
    packageCode: sourcePkg.packageQRCode, location: sourcePkg.custodyStatus,
    heldBy: sourcePkg.adminViewOrderPackageCurrentHandlerUsername, responsibleWorker: sourcePkg.adminViewOrderPackageResponsibleWorkerUsername,
  };
  const attempts = dto.adminViewOrderDeliveryAttemptList.map((a) => ({
    attemptID: a.deliveryAttemptID, workerUsername: a.adminViewOrderDeliveryAttemptWorkerUsername,
    startedTime: Date.parse(a.startTime), endedTime: apiTime(a.endTime), outcome: a.outcome,
    reason: a.failureReason, note: a.internalNote, photos: a.adminViewOrderDeliveryAttemptPhotoProofCount,
    signatures: a.adminViewOrderDeliveryAttemptSignatureProofCount,
  }));
  const instructions = dto.adminViewOrderClientInstructionList.map((i) => ({
    instructionID: i.instructionID, instructionType: i.instructionType, instructionStatus: i.status, source: i.source,
    submittedTime: Date.parse(i.submittedTime), decidedBy: i.adminViewOrderClientInstructionResolvedByUsername,
    decidedTime: apiTime(i.resolvedTime), note: i.resolutionNote,
  }));
  const shortageCases = dto.adminViewOrderShortageCaseList.map((c) => ({
    c: { shortageCaseID: c.shortageCaseID, missingQuantity: c.missingQuantity, caseStatus: c.caseStatus, decision: c.decision, reportedTime: Date.parse(c.reportedTime), decidedTime: apiTime(c.decidedTime) },
    product: { SKU: c.adminViewOrderShortageCaseSKU },
  }));
  const days: TimelineDay[] = [];
  for (const e of dto.adminViewOrderTimelineList) {
    const time = Date.parse(e.adminViewOrderTimelineTime);
    const date = new Date(time);
    const day = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
    let block = days.at(-1);
    if (block?.day !== day) { block = { day, entries: [] }; days.push(block); }
    block.entries.push({ time, text: e.adminViewOrderTimelineText, actor: e.adminViewOrderTimelineActorUsername });
  }

  const stopLabels = ["Placed", "Paid", "Picking", "Packed", "Shipped", "Out for delivery", "Delivered"];
  const currentByStatus: Record<OrderStatus, number> = {
    AWAITING_PAYMENT: 1, READY_FOR_PICKING: 2, PICKING: 2, READY_FOR_PICKUP: 4, SHIPPED: 4,
    OUT_FOR_DELIVERY: 5, DELIVERY_FAILED: 5, READY_FOR_REDELIVERY: 5, DELIVERED: 6, CANCELLED: -1,
  };
  const pickup = dto.adminViewOrderTimelineList.find((e) => e.adminViewOrderTimelineKind === "PACKAGE_EVENT" && /picked up/i.test(e.adminViewOrderTimelineText));
  const times = [
    order.createdTime, payments.find((p) => p.paymentStatus === "ACCEPTED")?.acceptedTime ?? null,
    task?.startedTime ?? null, task?.packedTime ?? null, apiTime(pickup?.adminViewOrderTimelineTime),
    attempts.at(-1)?.startedTime ?? null, attempts.find((a) => a.outcome === "DELIVERED")?.endedTime ?? null,
  ];
  const current = currentByStatus[order.orderStatus];
  const isProblem = order.orderStatus === "DELIVERY_FAILED" || order.orderStatus === "READY_FOR_REDELIVERY";
  const stops: JourneyStop[] = stopLabels.map((label, index) => ({
    key: label, label, time: times[index],
    state: index === current && order.orderStatus !== "DELIVERED" ? (isProblem ? "problem" : "current") : (times[index] ? "done" : "upcoming"),
    note: index === 5 && isProblem ? attempts.at(-1)?.reason ?? null : null,
  }));
  if (order.orderStatus === "CANCELLED") {
    const at = Math.min(times.findLastIndex((t) => t !== null) + 1, stops.length - 1);
    const entry = dto.adminViewOrderTimelineList.findLast((event) => /cancel/i.test(event.adminViewOrderTimelineText));
    stops[at] = { key: "cancelled", label: "Cancelled", time: apiTime(entry?.adminViewOrderTimelineTime), state: "ended", note: null };
  }
  const notes: Record<OrderStatus, string> = {
    AWAITING_PAYMENT: "Waiting for the client's payment.", READY_FOR_PICKING: "Waiting for a Warehouse Worker to pick the order.",
    PICKING: "Being picked in the warehouse.", READY_FOR_PICKUP: "Packed and waiting for a Delivery Worker.",
    SHIPPED: "Picked up for delivery.", OUT_FOR_DELIVERY: "Out for delivery.",
    DELIVERY_FAILED: "Delivery failed. See the delivery section for the latest attempt and any client request.",
    READY_FOR_REDELIVERY: "Redelivery was approved.", DELIVERED: "Delivered.", CANCELLED: "Cancelled.",
  };
  return { order, client: dto.adminViewOrderClientUsername, items, payments, refunds, task, assignments, pkg, attempts, instructions, shortageCases, days, journey: { stops, now: notes[order.orderStatus] } };
}
