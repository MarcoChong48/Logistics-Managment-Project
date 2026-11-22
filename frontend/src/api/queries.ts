// Read side of the stand-in API: lists, counters and detail views, all counted from the rows in the store.
// Pages call these inside useMemo with useDbVersion(), so they are recomputed after every change.

import { db } from "./store";
import { hidePrivateValue } from "./privacy";
import type {
  AuditLogRow,
  ClientInstruction,
  DeliveryAttempt,
  DeliveryPackage,
  Order,
  OrderStatus,
  Payment,
  PickingAssignment,
  PickingScanRow,
  PickingTask,
  Product,
  Refund,
  Role,
  SecurityLogRow,
  ShortageCase,
  User,
} from "./types";
import { now, startOfToday } from "../lib/clock";
import { fullDays, plainWord, TIME } from "../lib/format";
import { ORDER_STATUSES } from "../lib/status";

export const SUPER_ADMIN_USERNAME = "admin_main";
export const SETTINGS = { reviewFailureCount: 2, reminderDays: 3, finalReviewDays: 7, idleTimeoutMinutes: 15 };
export const PAGE_SIZE = 20;
export const DECISION_ACTIONS = new Set(["SHORTAGE_CASE_DECIDED", "CLIENT_INSTRUCTION_DECIDED", "REFUND_RETRIED"]);

// ----- small lookups ----------------------------------------------------------------------------

export const userByID = (id: number) => db.users.find((u) => u.userID === id) ?? null;
export const userByName = (name: string) => db.users.find((u) => u.username.toLowerCase() === name.trim().toLowerCase()) ?? null;
export const orderByID = (id: number) => db.orders.find((o) => o.orderID === id) ?? null;
export const orderByReference = (reference: string) =>
  db.orders.find((o) => o.orderReference.toLowerCase() === reference.trim().toLowerCase()) ?? null;
export const productByID = (id: number) => db.products.find((p) => p.productID === id) ?? null;
export const productBySKU = (code: string) => db.products.find((p) => p.SKU.toLowerCase() === code.trim().toLowerCase()) ?? null;
export const clientOf = (o: Order) => userByID(o.clientUserID)?.username ?? "-";

export function sessionUser(): User | null {
  return db.session ? userByID(db.session.userID) : null;
}

export function isSuperAdmin(u: User | null): boolean {
  return Boolean(u && u.role === "ADMIN" && u.username === SUPER_ADMIN_USERNAME);
}

// ----- accounts ---------------------------------------------------------------------------------

export function lockMinutesLeft(u: User): number | null {
  if (u.lockUntil === null || u.lockUntil <= now()) return null;
  return Math.max(1, Math.ceil((u.lockUntil - now()) / TIME.MINUTE));
}

export function isLockedNow(u: User): boolean {
  return lockMinutesLeft(u) !== null || u.accountStatus === "LOCKED";
}

export function isStaffRole(role: Role): boolean {
  return role === "WAREHOUSE_WORKER" || role === "DELIVERY_WORKER" || role === "ADMIN";
}

export interface UserImpact {
  activePickingOrders: string[];
  openPickupListCount: number;
  heldPackageOrders: string[];
  openClientOrderCount: number;
  hasActiveWork: boolean;
}

/** The work a person still has (Admin plan F2.5). */
export function userImpact(u: User): UserImpact {
  const activePickingOrders = db.pickingAssignments
    .filter((a) => a.workerUsername === u.username && a.endedTime === null)
    .map((a) => db.pickingTasks.find((t) => t.pickingTaskID === a.pickingTaskID))
    .map((t) => (t ? orderByID(t.orderID) : null))
    .filter((o): o is Order => Boolean(o && o.orderStatus === "PICKING"))
    .map((o) => o.orderReference);
  const openPickupListCount = db.pickupLists.filter((l) => l.claimedBy === u.username && l.status !== "CLOSED").length;
  const heldPackageOrders = db.packages
    .filter((p) => p.heldBy === u.username && p.location !== "WAREHOUSE")
    .map((p) => orderByID(p.orderID))
    .filter((o): o is Order => Boolean(o && ["SHIPPED", "OUT_FOR_DELIVERY", "DELIVERY_FAILED"].includes(o.orderStatus)))
    .map((o) => o.orderReference);
  const openClientOrderCount =
    u.role === "CLIENT" ? db.orders.filter((o) => o.clientUserID === u.userID && o.orderStatus !== "DELIVERED" && o.orderStatus !== "CANCELLED").length : 0;
  return {
    activePickingOrders,
    openPickupListCount,
    heldPackageOrders,
    openClientOrderCount,
    hasActiveWork: activePickingOrders.length > 0 || openPickupListCount > 0 || heldPackageOrders.length > 0,
  };
}

export interface UserRow {
  user: User;
  isOwnAccount: boolean;
  isSuperAdmin: boolean;
  canChange: boolean;
  isLockedNow: boolean;
  lockMinutesLeft: number | null;
}

export interface UserFilters {
  keyword: string;
  role: string;
  status: string;
}

export function toUserRow(u: User): UserRow {
  const me = sessionUser();
  const meIsSuper = isSuperAdmin(me);
  const targetIsSuper = isSuperAdmin(u);
  const isOwn = me?.userID === u.userID;
  const canChange = !isOwn && !targetIsSuper && (u.role !== "ADMIN" || meIsSuper);
  return { user: u, isOwnAccount: isOwn, isSuperAdmin: targetIsSuper, canChange, isLockedNow: isLockedNow(u), lockMinutesLeft: lockMinutesLeft(u) };
}

export function searchUsers(filters: UserFilters): UserRow[] {
  const keyword = filters.keyword.trim().toLowerCase();
  return db.users
    .filter((u) => !keyword || u.username.toLowerCase().includes(keyword))
    .filter((u) => !filters.role || u.role === filters.role)
    .filter((u) => {
      if (!filters.status) return true;
      if (filters.status === "LOCKED_NOW") return isLockedNow(u);
      return u.accountStatus === filters.status;
    })
    .sort((a, b) => a.username.localeCompare(b.username))
    .map(toUserRow);
}

/** Workers that a task or package may move to, with their current work. */
export function workerChoices(role: "WAREHOUSE_WORKER" | "DELIVERY_WORKER", except: string[]): Array<{ username: string; count: number }> {
  return db.users
    .filter((u) => u.role === role && u.accountStatus === "ACTIVE" && !except.includes(u.username) && !u.mustChangePassword)
    .sort((a, b) => a.username.localeCompare(b.username))
    .map((u) => {
      const impact = userImpact(u);
      return { username: u.username, count: role === "WAREHOUSE_WORKER" ? impact.activePickingOrders.length : impact.heldPackageOrders.length };
    });
}

// ----- products ---------------------------------------------------------------------------------

export interface ProductRow {
  product: Product;
  available: number;
  isLow: boolean;
  shortfall: number;
}

export function toProductRow(p: Product): ProductRow {
  const available = Math.max(0, p.physicalStock - p.reservedStock);
  return { product: p, available, isLow: p.lowStockThreshold > 0 && available < p.lowStockThreshold, shortfall: Math.max(0, p.reservedStock - p.physicalStock) };
}

export interface ProductFilters {
  keyword: string;
  status: string;
  lowOnly: boolean;
}

export function searchProducts(filters: ProductFilters): ProductRow[] {
  const keyword = filters.keyword.trim().toLowerCase();
  return db.products
    .filter((p) => !keyword || p.SKU.toLowerCase().includes(keyword) || p.name.toLowerCase().includes(keyword))
    .filter((p) => !filters.status || p.listingStatus === filters.status)
    .map(toProductRow)
    .filter((row) => !filters.lowOnly || row.isLow)
    .sort((a, b) => a.product.SKU.localeCompare(b.product.SKU, "en", { numeric: true }));
}

export function openOrderCountForProduct(productID: number): number {
  const orderIDs = new Set(db.orderItems.filter((i) => i.productID === productID).map((i) => i.orderID));
  return db.orders.filter((o) => orderIDs.has(o.orderID) && o.orderStatus !== "DELIVERED" && o.orderStatus !== "CANCELLED").length;
}

export function lowStockProducts(): ProductRow[] {
  return db.products
    .map(toProductRow)
    .filter((r) => r.isLow)
    .sort((a, b) => a.available - b.available || a.product.SKU.localeCompare(b.product.SKU));
}

// ----- orders -----------------------------------------------------------------------------------

export const paymentsOf = (orderID: number) => db.payments.filter((p) => p.orderID === orderID).sort((a, b) => b.createdTime - a.createdTime);
export const refundsOf = (orderID: number) => db.refunds.filter((r) => r.orderID === orderID).sort((a, b) => b.createdTime - a.createdTime || b.refundID - a.refundID);
export const shortageCasesOf = (orderID: number) =>
  db.shortageCases.filter((c) => c.orderID === orderID).sort((a, b) => b.reportedTime - a.reportedTime || b.shortageCaseID - a.shortageCaseID);
export const instructionsOf = (orderID: number) => db.instructions.filter((i) => i.orderID === orderID).sort((a, b) => b.submittedTime - a.submittedTime);
export const attemptsOf = (orderID: number) => db.attempts.filter((a) => a.orderID === orderID).sort((a, b) => a.startedTime - b.startedTime);
export const packageOf = (orderID: number) => db.packages.find((p) => p.orderID === orderID) ?? null;
export const taskOf = (orderID: number) => db.pickingTasks.filter((t) => t.orderID === orderID).at(-1) ?? null;
export const assignmentsOf = (taskID: number) => db.pickingAssignments.filter((a) => a.pickingTaskID === taskID).sort((a, b) => a.assignedTime - b.assignedTime);
export const pendingInstruction = (orderID: number) => db.instructions.find((i) => i.orderID === orderID && i.instructionStatus === "PENDING") ?? null;

/** The newest accepted payment above HKD 0.00: the payment a refund would come from. */
export function paidPayment(orderID: number): Payment | null {
  return paymentsOf(orderID).find((p) => p.paymentStatus === "ACCEPTED" && p.amount > 0) ?? null;
}

/** What a cancellation of this order would do with the money, and the client request it would close. */
export function cancellationFacts(orderID: number) {
  const payment = paidPayment(orderID);
  const existingRefund = payment ? (db.refunds.filter((r) => r.paymentID === payment.paymentID).sort((a, b) => b.refundID - a.refundID)[0] ?? null) : null;
  return { payment, existingRefund, pending: pendingInstruction(orderID) };
}

export function hasOpenShortage(orderID: number): boolean {
  return db.shortageCases.some((c) => c.orderID === orderID && (c.caseStatus === "OPEN" || c.caseStatus === "WAITING_RESTOCK"));
}

export function hasOpenCase(orderID: number): boolean {
  return db.shortageCases.some((c) => c.orderID === orderID && c.caseStatus === "OPEN") || pendingInstruction(orderID) !== null;
}

export interface OrderRow {
  order: Order;
  client: string;
  hasOpenCase: boolean;
  payment: Payment | null;
  refund: Refund | null;
}

export interface OrderFilters {
  keyword: string;
  status: string;
  from: string;
  to: string;
}

export function searchOrders(filters: OrderFilters): OrderRow[] {
  const keyword = filters.keyword.trim().toLowerCase();
  const from = filters.from ? new Date(`${filters.from}T00:00`).getTime() : null;
  const to = filters.to ? new Date(`${filters.to}T23:59:59`).getTime() : null;
  return db.orders
    .filter((o) => !filters.status || o.orderStatus === filters.status)
    .filter((o) => (from === null || o.createdTime >= from) && (to === null || o.createdTime <= to))
    .map((o) => ({ order: o, client: clientOf(o) }))
    .filter(({ order, client }) => !keyword || order.orderReference.toLowerCase().includes(keyword) || client.toLowerCase().includes(keyword))
    .sort((a, b) => b.order.createdTime - a.order.createdTime)
    .map(({ order, client }) => ({
      order,
      client,
      hasOpenCase: hasOpenCase(order.orderID),
      payment: paymentsOf(order.orderID)[0] ?? null,
      refund: refundsOf(order.orderID)[0] ?? null,
    }));
}

export function statusCounts(): Record<OrderStatus, number> {
  const counts = Object.fromEntries(ORDER_STATUSES.map((s) => [s, 0])) as Record<OrderStatus, number>;
  for (const o of db.orders) counts[o.orderStatus] += 1;
  return counts;
}

export interface JourneyStop {
  key: string;
  label: string;
  state: "done" | "current" | "problem" | "upcoming" | "ended";
  time: number | null;
  note: string | null;
}

/** The route of one order: the seven stages, where it is now and what went wrong on the way. */
export function orderJourney(o: Order): { stops: JourneyStop[]; now: string } {
  const payment = paymentsOf(o.orderID).find((p) => p.paymentStatus === "ACCEPTED") ?? null;
  const task = taskOf(o.orderID);
  const attempts = attemptsOf(o.orderID);
  const failed = attempts.filter((a) => a.outcome === "FAILED");
  const delivered = attempts.find((a) => a.outcome === "DELIVERED") ?? null;
  const pickedUp = db.events.find((e) => e.orderID === o.orderID && e.text === "Package event: Picked up.") ?? null;
  const pkg = packageOf(o.orderID);
  const pending = pendingInstruction(o.orderID);
  const shortage = shortageCasesOf(o.orderID)[0] ?? null;

  const reached = [
    true,
    payment !== null,
    Boolean(task?.startedTime),
    Boolean(task?.packedTime),
    pickedUp !== null,
    attempts.length > 0,
    delivered !== null,
  ];
  const times = [o.createdTime, payment?.endedTime ?? null, task?.startedTime ?? null, task?.packedTime ?? null, pickedUp?.time ?? null, attempts.at(-1)?.startedTime ?? null, delivered?.endedTime ?? null];
  const labels = ["Placed", "Paid", "Picking", "Packed", "Shipped", "Out for delivery", "Delivered"];
  const keys = ["placed", "paid", "picking", "packed", "shipped", "out", "delivered"];
  const lastReached = reached.lastIndexOf(true);

  const stops: JourneyStop[] = labels.map((label, index) => ({
    key: keys[index],
    label,
    state: reached[index] ? "done" : "upcoming",
    time: reached[index] ? times[index] : null,
    note: null,
  }));

  let nowText = "";
  switch (o.orderStatus) {
    case "AWAITING_PAYMENT":
      stops[1].state = "current";
      stops[1].note = o.paymentDeadline ? "Waiting for payment" : null;
      nowText = "Waiting for the client’s payment.";
      break;
    case "READY_FOR_PICKING":
      stops[2].state = "current";
      stops[2].note = "Waiting for a picker";
      nowText = "Paid and waiting in the warehouse for a picker.";
      break;
    case "PICKING":
      stops[2].state = shortage && shortage.caseStatus === "OPEN" ? "problem" : "current";
      stops[2].note = shortage && (shortage.caseStatus === "OPEN" || shortage.caseStatus === "WAITING_RESTOCK") ? `Shortage case ${shortage.shortageCaseID}` : null;
      nowText = shortage && shortage.caseStatus === "OPEN" ? "Picking stopped: a shortage case waits for a decision." : "Being picked in the warehouse.";
      break;
    case "READY_FOR_PICKUP":
      stops[4].state = "current";
      stops[4].note = "Waiting for a driver";
      nowText = "Packed and waiting for a Delivery Worker.";
      break;
    case "SHIPPED":
      stops[4].state = "current";
      nowText = `With ${pkg?.heldBy ?? "a Delivery Worker"}, not out for delivery yet.`;
      break;
    case "OUT_FOR_DELIVERY":
      stops[5].state = "current";
      nowText = `On the way with ${pkg?.heldBy ?? "a Delivery Worker"}.`;
      break;
    case "DELIVERY_FAILED":
    case "READY_FOR_REDELIVERY": {
      stops[5].state = "problem";
      const latest = failed.at(-1);
      stops[5].note = latest ? `Failed ${failed.length === 1 ? "once" : `${failed.length} times`}: ${latest.reason}` : null;
      stops[5].time = latest?.endedTime ?? stops[5].time;
      if (o.orderStatus === "READY_FOR_REDELIVERY") nowText = "Redelivery approved. Warehouse sends the package out again.";
      else if (pkg && pkg.location !== "WAREHOUSE") nowText = `Delivery failed. ${pkg.heldBy} still has to bring the package back.`;
      else if (pending) nowText = `Back at the warehouse. A ${pending.instructionType === "REDELIVER" ? "redelivery" : "cancellation"} request waits for a decision.`;
      else nowText = "Back at the warehouse, waiting for the client’s answer.";
      break;
    }
    case "DELIVERED":
      stops[6].state = "done";
      nowText = "Delivered.";
      break;
    case "CANCELLED": {
      const cut = Math.min(lastReached + 1, stops.length - 1);
      const cancelEvent = [...db.events].reverse().find((e) => e.orderID === o.orderID && /cancelled/i.test(e.text)) ?? null;
      stops[cut] = { key: "cancelled", label: "Cancelled", state: "ended", time: cancelEvent?.time ?? null, note: null };
      for (let i = cut + 1; i < stops.length; i += 1) stops[i].state = "upcoming";
      nowText = "Cancelled. It will not go further.";
      break;
    }
  }
  return { stops, now: nowText };
}

export interface OrderDetail {
  order: Order;
  client: string;
  items: Array<{ code: string; name: string; quantity: number; picked: number; unitPrice: number; lineTotal: number }>;
  payments: Payment[];
  refunds: Refund[];
  task: PickingTask | null;
  assignments: PickingAssignment[];
  pkg: DeliveryPackage | null;
  attempts: DeliveryAttempt[];
  instructions: ClientInstruction[];
  shortageCases: Array<{ c: ShortageCase; product: Product | null }>;
  days: Array<{ day: string; entries: Array<{ time: number; text: string; actor: string | null }> }>;
  journey: ReturnType<typeof orderJourney>;
}

export function orderDetail(reference: string): OrderDetail | null {
  const o = orderByReference(reference);
  if (!o) return null;
  const task = taskOf(o.orderID);
  const events = db.events.filter((e) => e.orderID === o.orderID).sort((a, b) => a.time - b.time || a.eventID - b.eventID);
  const days: OrderDetail["days"] = [];
  for (const e of events) {
    const d = new Date(e.time);
    const day = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
    let block = days.at(-1);
    if (!block || block.day !== day) {
      block = { day, entries: [] };
      days.push(block);
    }
    block.entries.push({ time: e.time, text: hidePrivateValue(e.text) ?? "", actor: e.actor });
  }
  return {
    order: o,
    client: clientOf(o),
    items: db.orderItems
      .filter((i) => i.orderID === o.orderID)
      .map((i) => {
        const p = productByID(i.productID);
        return { code: p?.SKU ?? "-", name: p?.name ?? "-", quantity: i.quantity, picked: i.pickedQuantity, unitPrice: i.unitPrice, lineTotal: Math.round(i.unitPrice * i.quantity * 100) / 100 };
      }),
    payments: paymentsOf(o.orderID),
    refunds: refundsOf(o.orderID),
    task,
    assignments: task ? assignmentsOf(task.pickingTaskID) : [],
    pkg: packageOf(o.orderID),
    attempts: attemptsOf(o.orderID).map((a) => ({ ...a, note: hidePrivateValue(a.note) })),
    instructions: instructionsOf(o.orderID),
    shortageCases: shortageCasesOf(o.orderID).map((c) => ({ c, product: productByID(c.productID) })),
    days,
    journey: orderJourney(o),
  };
}

// ----- warehouse cases --------------------------------------------------------------------------

export interface ShortageRow {
  c: ShortageCase;
  order: Order;
  product: Product | null;
  isPaid: boolean;
}

export const SHORTAGE_FILTERS = ["OPEN_AND_WAITING", "OPEN", "WAITING_RESTOCK", "CANCELLED", "RESOLVED"] as const;

export function searchShortageCases(filter: string, keepIDs: Set<number> = new Set()): ShortageRow[] {
  return db.shortageCases
    .filter((c) => {
      if (keepIDs.has(c.shortageCaseID)) return true;
      if (filter === "OPEN_AND_WAITING") return c.caseStatus === "OPEN" || c.caseStatus === "WAITING_RESTOCK";
      return c.caseStatus === filter;
    })
    .sort((a, b) => a.reportedTime - b.reportedTime)
    .map((c) => {
      const order = orderByID(c.orderID)!;
      return { c, order, product: productByID(c.productID), isPaid: paidPayment(order.orderID) !== null };
    });
}

export interface PickingRow {
  task: PickingTask;
  order: Order;
  assignment: PickingAssignment;
  worker: User | null;
  workerNotActive: boolean;
  picked: number;
  total: number;
  hasOpenShortage: boolean;
}

export function pickingTasksInProgress(keepIDs: Set<number> = new Set()): PickingRow[] {
  return db.pickingTasks
    .filter((t) => (t.taskStatus === "IN_PROGRESS" && orderByID(t.orderID)?.orderStatus === "PICKING") || keepIDs.has(t.pickingTaskID))
    .map((task) => {
      const order = orderByID(task.orderID)!;
      const assignments = assignmentsOf(task.pickingTaskID);
      const assignment = assignments.find((a) => a.endedTime === null) ?? assignments.at(-1)!;
      const worker = userByName(assignment.workerUsername);
      const lines = db.orderItems.filter((i) => i.orderID === order.orderID);
      return {
        task,
        order,
        assignment,
        worker,
        workerNotActive: Boolean(worker && (worker.accountStatus === "BANNED" || worker.accountStatus === "INACTIVE")),
        picked: lines.reduce((sum, i) => sum + i.pickedQuantity, 0),
        total: lines.reduce((sum, i) => sum + i.quantity, 0),
        hasOpenShortage: hasOpenShortage(order.orderID),
      };
    })
    .sort((a, b) => assignmentsOf(a.task.pickingTaskID)[0].assignedTime - assignmentsOf(b.task.pickingTaskID)[0].assignedTime);
}

// ----- delivery cases ---------------------------------------------------------------------------

export type FailedDeliveryState = "WAITING_FOR_RETURN" | "NEEDS_DECISION" | "WAITING_FOR_CLIENT";

export interface FailedDeliveryRow {
  order: Order;
  pkg: DeliveryPackage | null;
  failedAttempts: DeliveryAttempt[];
  latest: DeliveryAttempt | null;
  state: FailedDeliveryState;
  pending: ClientInstruction | null;
  daysSinceReturn: number | null;
  flags: { review: boolean; reminderDue: boolean; finalReview: boolean };
  hasOpenShortage: boolean;
  canSendReminder: boolean;
  nextReminderTime: number | null;
  isPaid: boolean;
}

export function failedDeliveryRow(o: Order): FailedDeliveryRow {
  const pkg = packageOf(o.orderID);
  const failedAttempts = attemptsOf(o.orderID)
    .filter((a) => a.outcome === "FAILED")
    .map((a) => ({ ...a, note: hidePrivateValue(a.note) }));
  const latest = failedAttempts.at(-1) ?? null;
  const pending = pendingInstruction(o.orderID);
  const returned = Boolean(pkg && pkg.location === "WAREHOUSE" && pkg.returnedTime !== null);
  const state: FailedDeliveryState = !returned ? "WAITING_FOR_RETURN" : pending ? "NEEDS_DECISION" : "WAITING_FOR_CLIENT";
  const daysSinceReturn = returned && pkg?.returnedTime ? fullDays(pkg.returnedTime, now()) : null;
  const waitsForClient = state === "WAITING_FOR_CLIENT" && daysSinceReturn !== null;
  const finalReview = waitsForClient && daysSinceReturn! >= SETTINGS.finalReviewDays;
  const reminderDue = waitsForClient && !finalReview && daysSinceReturn! >= SETTINGS.reminderDays;
  const nextReminderTime = pkg?.lastReminderTime ? pkg.lastReminderTime + TIME.DAY : null;
  return {
    order: o,
    pkg,
    failedAttempts,
    latest,
    state,
    pending,
    daysSinceReturn,
    flags: { review: failedAttempts.length >= SETTINGS.reviewFailureCount, reminderDue, finalReview },
    hasOpenShortage: hasOpenShortage(o.orderID),
    canSendReminder: state === "WAITING_FOR_CLIENT" && (nextReminderTime === null || nextReminderTime <= now()),
    nextReminderTime: nextReminderTime && nextReminderTime > now() ? nextReminderTime : null,
    isPaid: paidPayment(o.orderID) !== null,
  };
}

export const FAILED_DELIVERY_FILTERS = ["ALL", "WAITING_FOR_RETURN", "NEEDS_DECISION", "NO_CLIENT_REQUEST", "FLAGGED"] as const;

export function searchFailedDeliveries(filter: string, keepIDs: Set<number> = new Set()): FailedDeliveryRow[] {
  return db.orders
    .filter((o) => o.orderStatus === "DELIVERY_FAILED" || keepIDs.has(o.orderID))
    .map(failedDeliveryRow)
    .filter((row) => {
      if (keepIDs.has(row.order.orderID)) return true;
      switch (filter) {
        case "WAITING_FOR_RETURN":
          return row.state === "WAITING_FOR_RETURN";
        case "NEEDS_DECISION":
          return row.state === "NEEDS_DECISION";
        case "NO_CLIENT_REQUEST":
          return row.state === "WAITING_FOR_CLIENT";
        case "FLAGGED":
          return row.flags.review || row.flags.reminderDue || row.flags.finalReview;
        default:
          return true;
      }
    })
    .sort((a, b) => a.order.createdTime - b.order.createdTime);
}

export interface HeldPackageRow {
  pkg: DeliveryPackage;
  order: Order;
  holder: User | null;
  holderNotActive: boolean;
}

export function heldPackages(): HeldPackageRow[] {
  return db.packages
    .filter((p) => p.location === "DELIVERY" && p.heldBy !== null)
    .map((pkg) => ({ pkg, order: orderByID(pkg.orderID)!, holder: userByName(pkg.heldBy!) }))
    .filter((row) => ["SHIPPED", "OUT_FOR_DELIVERY", "DELIVERY_FAILED"].includes(row.order.orderStatus))
    .map((row) => ({ ...row, holderNotActive: Boolean(row.holder && (row.holder.accountStatus === "BANNED" || row.holder.accountStatus === "INACTIVE")) }))
    .sort((a, b) => a.order.createdTime - b.order.createdTime);
}

// ----- refunds ----------------------------------------------------------------------------------

export const isUnresolvedRefund = (r: Refund) => r.refundStatus !== "REFUNDED" && r.replacedByRefundID === null;

export interface RefundRow {
  refund: Refund;
  order: Order;
  isUnresolved: boolean;
}

export function searchRefunds(unresolvedOnly: boolean, keepIDs: Set<number> = new Set()): RefundRow[] {
  return db.refunds
    .filter((r) => !unresolvedOnly || isUnresolvedRefund(r) || keepIDs.has(r.refundID))
    .sort((a, b) => (unresolvedOnly ? a.createdTime - b.createdTime : b.createdTime - a.createdTime))
    .map((refund) => ({ refund, order: orderByID(refund.orderID)!, isUnresolved: isUnresolvedRefund(refund) }));
}

// ----- dashboard and counters -------------------------------------------------------------------

export interface MenuCounters {
  warehouse: number;
  delivery: number;
  refunds: number;
}

export function menuCounters(): MenuCounters {
  return {
    warehouse: db.shortageCases.filter((c) => c.caseStatus === "OPEN").length,
    delivery: db.orders.filter((o) => o.orderStatus === "DELIVERY_FAILED").map(failedDeliveryRow).filter((r) => r.state === "NEEDS_DECISION").length,
    refunds: db.refunds.filter(isUnresolvedRefund).length,
  };
}

export function decidedToday(): AuditLogRow[] {
  const start = startOfToday();
  return db.auditLog.filter((r) => r.time >= start && DECISION_ACTIONS.has(r.action));
}

/** Plain words for a decision row of the audit log, for example "Redelivery approved for ORD-10233". */
export function decisionText(row: AuditLogRow): string {
  let value: Record<string, unknown> = {};
  try {
    value = row.newValue ? (JSON.parse(row.newValue) as Record<string, unknown>) : {};
  } catch {
    value = {};
  }
  if (row.action === "CLIENT_INSTRUCTION_DECIDED") {
    const instruction = db.instructions.find((i) => String(i.instructionID) === row.targetID);
    const ref = String(value.orderReference ?? "");
    if (value.instructionStatus === "REJECTED") return `Request rejected for ${ref}`;
    return `${instruction?.instructionType === "CANCEL" ? "Cancellation" : "Redelivery"} approved for ${ref}`;
  }
  if (row.action === "SHORTAGE_CASE_DECIDED") {
    const c = db.shortageCases.find((x) => String(x.shortageCaseID) === row.targetID);
    const ref = c ? (orderByID(c.orderID)?.orderReference ?? "") : "";
    return value.decision === "WAIT_FOR_RESTOCK" ? `Wait for restock on ${ref}` : `Order ${ref} cancelled after a shortage`;
  }
  if (row.action === "REFUND_RETRIED") return `Refund ${row.targetID} retried`;
  return plainWord(row.action);
}

/**
 * The "need you" counts of the dashboard route: orders at each stop that wait for the Admin. A failed
 * delivery counts when a client request needs a decision; any other stop counts orders with an open shortage case.
 */
export function routeNeeds(): Partial<Record<OrderStatus, { count: number; to: string; what: string }>> {
  const result: Partial<Record<OrderStatus, { count: number; to: string; what: string }>> = {};
  for (const o of db.orders) {
    if (o.orderStatus === "DELIVERED" || o.orderStatus === "CANCELLED") continue;
    const failed = o.orderStatus === "DELIVERY_FAILED";
    const waits = failed ? failedDeliveryRow(o).state === "NEEDS_DECISION" : db.shortageCases.some((c) => c.orderID === o.orderID && c.caseStatus === "OPEN");
    if (!waits) continue;
    const entry = result[o.orderStatus] ?? {
      count: 0,
      to: failed ? "/admin/delivery-cases?show=NEEDS_DECISION" : "/admin/warehouse-cases?caseStatus=OPEN",
      what: failed ? "failed deliveries" : "orders with a shortage case",
    };
    entry.count += 1;
    result[o.orderStatus] = entry;
  }
  return result;
}

/** Unfinished refunds, split into the ones that failed (the Admin can retry) and the ones the provider still handles. */
export function refundSplit(): { failed: number; waiting: number } {
  const open = db.refunds.filter(isUnresolvedRefund);
  return { failed: open.filter((r) => r.refundStatus === "FAILED").length, waiting: open.filter((r) => r.refundStatus !== "FAILED").length };
}

export function dashboardSummary() {
  const counters = menuCounters();
  const failed = db.orders.filter((o) => o.orderStatus === "DELIVERY_FAILED").map(failedDeliveryRow);
  const open = counters.warehouse + counters.delivery + counters.refunds;
  return {
    counters,
    open,
    decidedToday: decidedToday().length,
    waitingRestock: db.shortageCases.filter((c) => c.caseStatus === "WAITING_RESTOCK").length,
    waitingForClient: failed.filter((r) => r.state === "WAITING_FOR_CLIENT").length,
    flagged: failed.filter((r) => r.flags.review || r.flags.reminderDue || r.flags.finalReview).length,
    lockedUsers: db.users.filter(isLockedNow).length,
    statusCounts: statusCounts(),
    lowStock: lowStockProducts(),
  };
}

// ----- logs -------------------------------------------------------------------------------------

export const AUDIT_ACTIONS = [
  "USER_CREATED",
  "USER_ROLE_CHANGED",
  "USER_BANNED",
  "USER_UNBANNED",
  "USER_DEACTIVATED",
  "USER_REACTIVATED",
  "USER_UNLOCKED",
  "USER_PASSWORD_RESET",
  "DELIVERY_WORKER_PROFILE_EDITED",
  "PRODUCT_CREATED",
  "PRODUCT_EDITED",
  "PRODUCT_ACTIVATED",
  "PRODUCT_DEACTIVATED",
  "PRODUCT_IMPORTED",
  "SHORTAGE_CASE_DECIDED",
  "PICKING_TASK_REASSIGNED",
  "CLIENT_INSTRUCTION_RECORDED_BY_ADMIN",
  "CLIENT_INSTRUCTION_DECIDED",
  "CLIENT_REMINDER_SENT",
  "DELIVERY_REASSIGNED",
  "REFUND_STATUS_CHECKED",
  "REFUND_RETRIED",
  "LOG_CLEANUP_RUN",
  "CSV_DOWNLOADED",
] as const;

export const AUDIT_TARGETS = ["USER", "PRODUCT", "ORDER", "PICKING_TASK", "PACKAGE", "SHORTAGE_CASE", "CLIENT_INSTRUCTION", "REFUND", "LOG", "SYSTEM"] as const;

export const SECURITY_EVENTS = [
  "LOGIN_SUCCESS",
  "LOGIN_FAILURE",
  "ACCOUNT_LOCKED",
  "LOGOUT",
  "PASSWORD_CHANGED",
  "TEMPORARY_PASSWORD_ISSUED",
  "SESSION_REVOKED",
  "ACCESS_DENIED",
  "ACCOUNT_UNLOCKED_BY_ADMIN",
  "ACCOUNT_BANNED",
  "ACCOUNT_UNBANNED",
  "ACCOUNT_DEACTIVATED",
  "ACCOUNT_REACTIVATED",
  "ROLE_CHANGED",
  "REJECTED_ADMIN_ACTION",
] as const;

/** "Client instruction recorded by admin" and the other plain words of an audit action. */
export function auditActionLabel(action: string): string {
  return plainWord(action);
}

export function auditTargetLabel(row: AuditLogRow): string {
  return `${plainWord(row.targetType)} ${row.targetID}`;
}

function inDateRange(time: number, from: string, to: string): boolean {
  const start = from ? new Date(`${from}T00:00`).getTime() : null;
  const end = to ? new Date(`${to}T23:59:59`).getTime() : null;
  return (start === null || time >= start) && (end === null || time <= end);
}

export interface AuditFilters {
  from: string;
  to: string;
  actor: string;
  action: string;
  target: string;
  targetID: string;
}

export function searchAuditLog(f: AuditFilters): AuditLogRow[] {
  const actor = f.actor.trim().toLowerCase();
  return db.auditLog
    .filter((r) => inDateRange(r.time, f.from, f.to))
    .filter((r) => !actor || r.actorUsername.toLowerCase().includes(actor))
    .filter((r) => !f.action || r.action === f.action)
    .filter((r) => !f.target || r.targetType === f.target)
    .filter((r) => !f.targetID.trim() || r.targetID === f.targetID.trim())
    .sort((a, b) => b.time - a.time || b.auditLogID - a.auditLogID)
    .map((r) => ({ ...r, oldValue: hidePrivateValue(r.oldValue), newValue: hidePrivateValue(r.newValue) }));
}

export interface SecurityFilters {
  from: string;
  to: string;
  username: string;
  event: string;
  result: string;
}

export function searchSecurityLog(f: SecurityFilters): SecurityLogRow[] {
  const name = f.username.trim().toLowerCase();
  return db.securityLog
    .filter((r) => inDateRange(r.time, f.from, f.to))
    .filter((r) => !name || r.username.toLowerCase().includes(name))
    .filter((r) => !f.event || r.eventType === f.event)
    .filter((r) => !f.result || r.result === f.result)
    .sort((a, b) => b.time - a.time || b.securityLogID - a.securityLogID)
    .map((r) => ({ ...r, details: hidePrivateValue(r.details) }));
}

export interface ScanFilters {
  from: string;
  to: string;
  order: string;
  worker: string;
  result: string;
}

export interface ScanRowView extends PickingScanRow {
  orderReference: string;
  shownCode: string;
}

export function searchPickingScans(f: ScanFilters): ScanRowView[] {
  const order = f.order.trim().toLowerCase();
  const worker = f.worker.trim().toLowerCase();
  const skus = new Set(db.products.map((p) => p.SKU));
  return db.pickingScans
    .filter((r) => inDateRange(r.time, f.from, f.to))
    // A scanned code is shown only when it is the SKU of a product; the raw text never leaves this answer.
    .map((r) => {
      const shownCode = skus.has(r.scannedText) ? r.scannedText : "[unknown code]";
      return { ...r, scannedText: shownCode, orderReference: orderByID(r.orderID)?.orderReference ?? "-", shownCode };
    })
    .filter((r) => !order || r.orderReference.toLowerCase().includes(order))
    .filter((r) => !worker || r.workerUsername.toLowerCase().includes(worker))
    .filter((r) => !f.result || r.result === f.result)
    .sort((a, b) => b.time - a.time || b.scanID - a.scanID)
    .map((r) => ({ ...r, reason: hidePrivateValue(r.reason) }));
}

/** Rows on one page of a list (20 a page). */
export function pageOf<T>(rows: T[], page: number): { rows: T[]; page: number; pageCount: number } {
  const pageCount = Math.max(1, Math.ceil(rows.length / PAGE_SIZE));
  const safe = Math.min(Math.max(1, page), pageCount);
  return { rows: rows.slice((safe - 1) * PAGE_SIZE, safe * PAGE_SIZE), page: safe, pageCount };
}
