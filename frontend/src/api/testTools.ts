// The 21 temporary test tools (Admin plan Section 12). They exist only in test mode. Every tool changes the
// sample data the way the real tool does, so its result can be seen on the page its "How to use" names.
// Everything a tool makes is recognisable: usernames start with test_, SKUs and order references with TEST-.

import { commit, db, nextID, respond } from "./store";
import { restartSessionTimer } from "./session";
import { audit, makeTemporaryPassword, me, runLogCleanup, security } from "./actions";
import { looksPrivate } from "./privacy";
import {
  dashboardSummary,
  heldPackages,
  isSuperAdmin,
  orderDetail,
  pickingTasksInProgress,
  productBySKU,
  searchAuditLog,
  searchFailedDeliveries,
  searchOrders,
  searchPickingScans,
  searchProducts,
  searchRefunds,
  searchSecurityLog,
  searchShortageCases,
  searchUsers,
  userByName,
} from "./queries";
import type { AccountStatus, ActionResult, Order, OrderStatus, Product, ProviderAnswer, RefundStatus, Role, User } from "./types";
import { now } from "../lib/clock";
import { formatMoney, plainWord, plural, TIME } from "../lib/format";
import { roleLabel } from "../lib/status";

export interface ToolAnswer {
  /** Values to show in the fixed-width font after the message, such as a temporary password. */
  secrets?: string[];
}

function run(work: () => ActionResult<ToolAnswer>): Promise<ActionResult<ToolAnswer>> {
  if (!db.testModeIsEnabled) return respond({ ok: false, message: "Test mode is switched off, so the test tools do not work." });
  const result = work();
  commit();
  restartSessionTimer();
  return respond(result, 340);
}

const ok = (message: string, secrets?: string[]): ActionResult<ToolAnswer> => ({ ok: true, message, data: { secrets } });
const fail = (message: string, field?: string): ActionResult<ToolAnswer> => ({ ok: false, message, field });
const actor = () => me()?.username ?? "system";

const ROLE_WORD: Record<Role, string> = { CLIENT: "client", WAREHOUSE_WORKER: "warehouse", DELIVERY_WORKER: "delivery", ADMIN: "admin" };

function nextTestUsername(role: Role): string {
  const word = ROLE_WORD[role];
  let n = 1;
  while (userByName(`test_${word}_${n}`)) n += 1;
  return `test_${word}_${n}`;
}

function makeTestUser(role: Role): { user: User; password: string } {
  const password = makeTemporaryPassword();
  const user: User = {
    userID: nextID("user"),
    username: nextTestUsername(role),
    role,
    accountStatus: "ACTIVE",
    createdTime: now(),
    lastLoginTime: null,
    failedLoginCount: 0,
    lockUntil: null,
    password: "",
    temporaryPassword: password,
    temporaryPasswordExpiryTime: now() + TIME.DAY,
    mustChangePassword: true,
    isTestAccount: true,
    deliveryWorkerProfile: role === "DELIVERY_WORKER" ? { employeeNumber: "", vehicleType: "", vehiclePlate: "", equipmentNote: "" } : null,
  };
  db.users.push(user);
  audit(actor(), "USER_CREATED", "USER", user.userID, null, { username: user.username, role }, true);
  security(user.username, "TEMPORARY_PASSWORD_ISSUED", "SUCCESS", "issued by a test tool", true);
  return { user, password };
}

/** Logged in as a worker once, so the account can be given work (a temporary password blocks nothing else). */
function readyWorker(role: "WAREHOUSE_WORKER" | "DELIVERY_WORKER", username: string): { user: User; password?: string } | string {
  if (username.trim()) {
    const u = userByName(username);
    if (!u) return `No account is named ${username.trim()}.`;
    if (u.role !== role) return `${u.username} is not a ${roleLabel(role)}.`;
    if (u.accountStatus !== "ACTIVE") return `${u.username} is not active.`;
    return { user: u };
  }
  return makeTestUser(role);
}

function testProduct(kind: "MUG" | "PEN"): Product {
  const code = `TEST-${kind}-0001`;
  const existing = productBySKU(code);
  if (existing) return existing;
  const product: Product = {
    productID: nextID("product"),
    SKU: code,
    name: kind === "MUG" ? "Test mug, 350 ml" : "Test pen, black",
    currentPrice: kind === "MUG" ? 80 : 12.5,
    physicalStock: 60,
    reservedStock: 0,
    lowStockThreshold: 5,
    listingStatus: "ACTIVE",
    createdTime: now(),
    isTest: true,
  };
  db.products.push(product);
  audit(actor(), "PRODUCT_CREATED", "PRODUCT", product.productID, null, { SKU: code, name: product.name, currentPrice: product.currentPrice }, true);
  return product;
}

function testClient(): User {
  const existing = db.users.find((u) => u.username === "test_client_order");
  if (existing) return existing;
  const { user } = makeTestUser("CLIENT");
  user.username = "test_client_order";
  user.mustChangePassword = false;
  user.temporaryPassword = null;
  user.temporaryPasswordExpiryTime = null;
  return user;
}

interface TestOrderOptions {
  status: OrderStatus;
  mugs?: number;
  pens?: number;
  pickedMugs?: number;
  picker?: string;
  driver?: string;
  paid?: boolean;
}

/** Builds one TEST- order with the rows its status needs, with times just before now. */
function makeTestOrder(options: TestOrderOptions): Order {
  const number = db.nextID.testOrder ?? 1;
  db.nextID.testOrder = number + 1;
  const t = now();
  const client = testClient();
  const order: Order = {
    orderID: nextID("order"),
    orderReference: `TEST-${String(number).padStart(4, "0")}`,
    clientUserID: client.userID,
    orderStatus: options.status,
    createdTime: t - 50 * TIME.MINUTE,
    paymentDeadline: null,
    totalAmount: 0,
    deliveryDetailsVersionCount: 1,
    isTest: true,
  };
  db.orders.push(order);
  const lines: Array<[Product, number, number]> = [];
  if ((options.mugs ?? 3) > 0) lines.push([testProduct("MUG"), options.mugs ?? 3, options.pickedMugs ?? 0]);
  if ((options.pens ?? 1) > 0) lines.push([testProduct("PEN"), options.pens ?? 1, 0]);
  let total = 0;
  for (const [product, quantity, picked] of lines) {
    db.orderItems.push({ orderItemID: nextID("orderItem"), orderID: order.orderID, productID: product.productID, quantity, pickedQuantity: picked, unitPrice: product.currentPrice });
    total += product.currentPrice * quantity;
    if (["AWAITING_PAYMENT", "READY_FOR_PICKING", "PICKING"].includes(options.status)) product.reservedStock += quantity - picked;
  }
  order.totalAmount = Math.round(total * 100) / 100;
  const event = (minutesAgo: number, text: string, who: string | null = null) =>
    db.events.push({ eventID: nextID("event"), orderID: order.orderID, time: t - minutesAgo * TIME.MINUTE, text, actor: who });
  event(50, `Order created by client ${client.username}.`);
  event(50, "Delivery details version 1 saved.");
  const paid = options.paid ?? options.status !== "AWAITING_PAYMENT";
  if (options.status === "AWAITING_PAYMENT") order.paymentDeadline = t + 60 * TIME.MINUTE;
  db.payments.push({
    paymentID: nextID("payment"),
    orderID: order.orderID,
    amount: order.totalAmount,
    paymentStatus: paid ? "ACCEPTED" : "PENDING",
    createdTime: t - 49 * TIME.MINUTE,
    endedTime: paid ? t - 48 * TIME.MINUTE : null,
  });
  event(49, `Checkout started (${formatMoney(order.totalAmount)}).`);
  if (paid) event(48, "Payment accepted.");
  const stages: OrderStatus[] = ["PICKING", "READY_FOR_PICKUP", "SHIPPED", "OUT_FOR_DELIVERY", "DELIVERY_FAILED", "READY_FOR_REDELIVERY", "DELIVERED"];
  if (stages.includes(options.status) || (options.status === "CANCELLED" && options.picker)) {
    const picker = options.picker ?? "wh_picker_02";
    const packed = options.status !== "PICKING";
    const task = { pickingTaskID: nextID("pickingTask"), orderID: order.orderID, taskStatus: packed ? ("PACKED" as const) : ("IN_PROGRESS" as const), startedTime: t - 40 * TIME.MINUTE, packedTime: packed ? t - 30 * TIME.MINUTE : null };
    db.pickingTasks.push(task);
    db.pickingAssignments.push({ assignmentID: nextID("assignment"), pickingTaskID: task.pickingTaskID, workerUsername: picker, assignedTime: t - 45 * TIME.MINUTE, endedTime: packed ? t - 30 * TIME.MINUTE : null, assignedBy: null, reason: null });
    event(45, `Picking task assigned to ${picker}.`);
    event(40, "Picking started.");
    if (packed) {
      db.orderItems.filter((i) => i.orderID === order.orderID).forEach((i) => (i.pickedQuantity = i.quantity));
      event(30, "Packing confirmed.");
    }
  }
  if (["READY_FOR_PICKUP", "SHIPPED", "OUT_FOR_DELIVERY", "DELIVERY_FAILED", "READY_FOR_REDELIVERY", "DELIVERED"].includes(options.status)) {
    const driver = options.driver ?? "dl_driver_01";
    const onTheRoad = ["SHIPPED", "OUT_FOR_DELIVERY", "DELIVERY_FAILED"].includes(options.status);
    db.packages.push({
      packageID: nextID("package"),
      packageCode: `PKG-${order.orderReference}`,
      orderID: order.orderID,
      location: onTheRoad ? "DELIVERY" : "WAREHOUSE",
      heldBy: onTheRoad ? driver : null,
      responsibleWorker: driver,
      lastReassignedTime: null,
      lastReassignedBy: null,
      returnedTime: null,
      lastReminderTime: null,
    });
    if (options.status !== "READY_FOR_PICKUP") event(25, "Package event: Picked up.", driver);
    if (["OUT_FOR_DELIVERY", "DELIVERED"].includes(options.status)) {
      db.attempts.push({
        attemptID: nextID("attempt"),
        orderID: order.orderID,
        workerUsername: driver,
        startedTime: t - 15 * TIME.MINUTE,
        endedTime: options.status === "DELIVERED" ? t - 5 * TIME.MINUTE : null,
        outcome: options.status === "DELIVERED" ? "DELIVERED" : null,
        reason: null,
        note: null,
      });
      event(15, `Delivery attempt started by ${driver}.`);
      if (options.status === "DELIVERED") event(5, "Delivery attempt ended: Delivered.");
    }
  }
  return order;
}

// ----- Stage 1 ----------------------------------------------------------------------------------

export function createTestUser(role: Role) {
  return run(() => {
    if (role === "ADMIN" && !isSuperAdmin(me())) return fail("Only the super admin can create an Admin account.", "role");
    const { user, password } = makeTestUser(role);
    return ok(`Created ${user.username}. Temporary password:`, [password]);
  });
}

export function endLock(username: string) {
  return run(() => {
    if (!username.trim()) return fail("Type a username.", "username");
    const u = userByName(username);
    if (!u) return fail(`No account is named ${username.trim()}.`, "username");
    if (u.accountStatus === "LOCKED") return fail(`${u.username} has the old stored status Locked. Use “Unlock” on Users.`, "username");
    if (u.lockUntil === null || u.lockUntil <= now()) return fail(`${u.username} is not locked.`, "username");
    u.lockUntil = now() - 1000;
    return ok(`The lock of ${u.username} ended. The account can log in again.`);
  });
}

export function expireTemporaryPassword(username: string) {
  return run(() => {
    if (!username.trim()) return fail("Type a username.", "username");
    const u = userByName(username);
    if (!u) return fail(`No account is named ${username.trim()}.`, "username");
    if (isSuperAdmin(u)) return fail("This tool does not change the super admin.", "username");
    if (!u.mustChangePassword || !u.temporaryPassword) return fail(`${u.username} has no temporary password.`, "username");
    u.temporaryPasswordExpiryTime = now() - 60_000;
    return ok(`The temporary password of ${u.username} has run out.`);
  });
}

export function setUserStatus(username: string, status: AccountStatus) {
  return run(() => {
    if (!username.trim()) return fail("Type a username.", "username");
    const u = userByName(username);
    if (!u) return fail(`No account is named ${username.trim()}.`, "username");
    if (isSuperAdmin(u)) return fail("This tool does not change the super admin.", "username");
    u.accountStatus = status;
    const words = status === "INACTIVE" ? "Deactivated" : status === "LOCKED" ? "Locked (old status)" : plainWord(status);
    return ok(`${u.username} now has the stored status ${words}.`);
  });
}

// ----- Stage 2 ----------------------------------------------------------------------------------

export function createManyTestUsers() {
  return run(() => {
    const names: string[] = [];
    for (let i = 0; i < 25; i += 1) names.push(makeTestUser("CLIENT").user.username);
    return ok(`Created 25 Client accounts, from ${names[0]} to ${names.at(-1)}.`);
  });
}

export function createWorkerWithActiveWork() {
  return run(() => {
    const picker = makeTestUser("WAREHOUSE_WORKER");
    const driver = makeTestUser("DELIVERY_WORKER");
    const first = makeTestOrder({ status: "PICKING", picker: picker.user.username, pickedMugs: 1 });
    const second = makeTestOrder({ status: "PICKING", picker: picker.user.username });
    const held = makeTestOrder({ status: "SHIPPED", driver: driver.user.username });
    db.pickupLists.push({ pickupListID: nextID("pickupList"), claimedBy: driver.user.username, status: "CLAIMED" });
    return ok(
      `Created ${picker.user.username}, who is picking ${first.orderReference} and ${second.orderReference}, and ${driver.user.username}, who holds the package of ${held.orderReference} and has claimed 1 pickup list. Temporary passwords, in the same order:`,
      [picker.password, driver.password],
    );
  });
}

// ----- Stage 3 ----------------------------------------------------------------------------------

export function createPickingScanRows() {
  return run(() => {
    const worker = makeTestUser("WAREHOUSE_WORKER").user.username;
    const order = makeTestOrder({ status: "PICKING", picker: worker, mugs: 3, pens: 1, pickedMugs: 2 });
    const t = now();
    const rows: Array<[number, string, string | null, "ACCEPTED" | "REJECTED" | "REVERSED", string | null]> = [
      [9, "TEST-MUG-0001", "TEST-MUG-0001", "ACCEPTED", null],
      [8, "TEST-MUG-0001", "TEST-MUG-0001", "ACCEPTED", null],
      [7, "TEST-PEN-0001", "TEST-PEN-0001", "ACCEPTED", null],
      [6, "TEST-PEN-0001", "TEST-PEN-0001", "REVERSED", null],
      [5, "4006381333931", null, "REJECTED", "not part of this order"],
      [4, "5550 0000 1234", null, "REJECTED", "not part of this order"],
    ];
    let firstPen = 0;
    for (const [minutesAgo, text, product, result, reason] of rows) {
      const scanID = nextID("scan");
      if (text === "TEST-PEN-0001" && result === "ACCEPTED") firstPen = scanID;
      db.pickingScans.push({ scanID, time: t - minutesAgo * TIME.MINUTE, orderID: order.orderID, workerUsername: worker, scannedText: text, productSKU: product, result, reason: result === "REVERSED" ? `reverses scan ${firstPen}` : reason });
    }
    return ok(`Created ${order.orderReference}, being picked by ${worker}, with 6 scan rows: 3 accepted, 1 reversed and 2 rejected.`);
  });
}

const OLD_ROW_NOTE = "test row near the keeping time";

export function createOldLogRows() {
  return run(() => {
    db.securityLog = db.securityLog.filter((r) => r.details !== OLD_ROW_NOTE);
    db.pickingScans = db.pickingScans.filter((r) => r.reason !== OLD_ROW_NOTE);
    db.auditLog = db.auditLog.filter((r) => !(r.newValue ?? "").includes(OLD_ROW_NOTE));
    const t = now();
    const day = TIME.DAY;
    const order = db.orders[0];
    for (const offset of [-TIME.HOUR, TIME.HOUR]) {
      db.securityLog.push({ securityLogID: nextID("security"), time: t - 90 * day + offset, username: "test_old_rows", eventType: "LOGIN_SUCCESS", result: "SUCCESS", ipAddress: "192.0.2.99", details: OLD_ROW_NOTE, userAgent: navigator.userAgent, isTest: true });
      db.pickingScans.push({ scanID: nextID("scan"), time: t - 90 * day + offset, orderID: order.orderID, workerUsername: "test_old_rows", scannedText: "TEST-MUG-0001", productSKU: "TEST-MUG-0001", result: "ACCEPTED", reason: OLD_ROW_NOTE });
      db.auditLog.push({ auditLogID: nextID("audit"), time: t - 3 * 365 * day + offset, actorUsername: "test_old_rows", action: "CSV_DOWNLOADED", targetType: "SYSTEM", targetID: "CSV", oldValue: null, newValue: JSON.stringify({ note: OLD_ROW_NOTE }), isTest: true });
    }
    return ok("Added 6 log rows: in each log one just older and one just newer than its keeping time. Earlier rows of this tool were replaced.");
  });
}

export function runLogCleanupNow() {
  return run(() => {
    const counts = runLogCleanup(actor());
    return ok(`Clean-up finished. Rows deleted: security log ${counts.security}, picking scans ${counts.scans}, audit log ${counts.audit}.`);
  });
}

export function createPrivateValueLogRows() {
  return run(() => {
    audit(actor(), "CLIENT_INSTRUCTION_RECORDED_BY_ADMIN", "CLIENT_INSTRUCTION", "test", null, { phone: "PRIVATE-PHONE-MARKER", note: "caller asked us to ring 5550 0000 1234", recipientName: "PRIVATE-RECIPIENT-MARKER" }, true);
    security(actor(), "ACCESS_DENIED", "REJECTED", "caller gave 5550 0000 1234 as a contact number", true);
    return ok("Added 1 audit row and 1 security row that hold private test values. Logs shows them hidden.");
  });
}

// ----- Stage 4 ----------------------------------------------------------------------------------

export function setProductStock(code: string, physical: string, reserved: string) {
  return run(() => {
    if (!code.trim()) return fail("Type a SKU.", "sku");
    const p = productBySKU(code);
    if (!p) return fail(`No product has the SKU ${code.trim().toUpperCase()}.`, "sku");
    if (!/^\d+$/.test(physical.trim())) return fail("Type a whole number of 0 or more.", "physical");
    if (!/^\d+$/.test(reserved.trim())) return fail("Type a whole number of 0 or more.", "reserved");
    p.physicalStock = Number(physical);
    p.reservedStock = Number(reserved);
    const available = Math.max(0, p.physicalStock - p.reservedStock);
    return ok(`${p.SKU} now has physical stock ${p.physicalStock} and reserved stock ${p.reservedStock}, so ${available} available.`);
  });
}

// ----- Stage 5 ----------------------------------------------------------------------------------

export function createSampleOrderSet() {
  return run(() => {
    const statuses: OrderStatus[] = ["AWAITING_PAYMENT", "READY_FOR_PICKING", "PICKING", "READY_FOR_PICKUP", "SHIPPED", "OUT_FOR_DELIVERY", "DELIVERY_FAILED", "READY_FOR_REDELIVERY", "DELIVERED", "CANCELLED"];
    const made: string[] = [];
    for (const status of statuses) {
      const o = makeTestOrder({ status: status === "DELIVERY_FAILED" || status === "READY_FOR_REDELIVERY" ? "OUT_FOR_DELIVERY" : status });
      if (status === "DELIVERY_FAILED" || status === "READY_FOR_REDELIVERY") {
        const attempt = db.attempts.filter((a) => a.orderID === o.orderID).at(-1)!;
        attempt.endedTime = now() - 3 * TIME.MINUTE;
        attempt.outcome = "FAILED";
        attempt.reason = "Client not home";
        db.events.push({ eventID: nextID("event"), orderID: o.orderID, time: now() - 3 * TIME.MINUTE, text: "Delivery attempt ended: Failed. Reason: Client not home.", actor: null });
        if (status === "READY_FOR_REDELIVERY") {
          const pkg = db.packages.find((p) => p.orderID === o.orderID)!;
          pkg.location = "WAREHOUSE";
          pkg.heldBy = null;
          pkg.returnedTime = now() - TIME.MINUTE;
        }
      }
      if (status === "CANCELLED") db.events.push({ eventID: nextID("event"), orderID: o.orderID, time: now() - 2 * TIME.MINUTE, text: `Order cancelled by client ${testClient().username} within the cancellation window.`, actor: null });
      o.orderStatus = status;
      made.push(`${o.orderReference} ${plainWord(status)}`);
    }
    return ok(`Created 10 orders: ${made.join(", ")}.`);
  });
}

// ----- Stage 6 ----------------------------------------------------------------------------------

export function createShortageReport(orderStatus: "AWAITING_PAYMENT" | "PICKING" | "READY_FOR_PICKUP", missing: string, reference: string) {
  return run(() => {
    const quantity = Number(missing);
    if (!/^\d+$/.test(missing.trim()) || quantity < 1) return fail("Type a whole number of 1 or more.", "missing");
    let order: Order;
    let product: Product;
    let ordered: number;
    if (reference.trim()) {
      const found = db.orders.find((o) => o.orderReference.toLowerCase() === reference.trim().toLowerCase());
      if (!found) return fail(`No order has the reference ${reference.trim().toUpperCase()}.`, "reference");
      if (!found.isTest) return fail("Use the reference of an order that a test tool made (it starts with TEST-).", "reference");
      const pen = testProduct("PEN");
      const line = db.orderItems.find((i) => i.orderID === found.orderID && i.productID === pen.productID);
      if (!line) return fail(`${found.orderReference} has no test pen.`, "reference");
      if (quantity > line.quantity) return fail(`The order has ${line.quantity} pen, so at most ${line.quantity} can be missing.`, "missing");
      order = found;
      product = pen;
      ordered = line.quantity;
    } else {
      if (quantity > 3) return fail("The new order has 3 mugs, so at most 3 can be missing.", "missing");
      order = makeTestOrder({ status: orderStatus, picker: orderStatus === "AWAITING_PAYMENT" ? undefined : "wh_picker_02" });
      product = testProduct("MUG");
      ordered = 3;
    }
    const id = nextID("shortageCase");
    db.shortageCases.push({
      shortageCaseID: id,
      orderID: order.orderID,
      productID: product.productID,
      missingQuantity: quantity,
      orderedQuantity: ordered,
      caseStatus: "OPEN",
      reportedTime: now(),
      reportedBy: "wh_picker_02",
      note: "reported by a test tool",
      decision: null,
      decidedBy: null,
      decidedTime: null,
      decisionReason: null,
      refundNote: null,
    });
    db.events.push({ eventID: nextID("event"), orderID: order.orderID, time: now(), text: `Shortage reported: ${quantity} of ${product.SKU}.`, actor: "wh_picker_02" });
    return ok(`Created shortage case ${id} for ${order.orderReference}: ${quantity} of ${ordered} ${product.SKU} missing.`);
  });
}

export function markShortageResolved(caseNumber: string) {
  return run(() => {
    if (!/^\d+$/.test(caseNumber.trim())) return fail("Type a case number.", "case");
    const c = db.shortageCases.find((x) => x.shortageCaseID === Number(caseNumber));
    if (!c) return fail(`There is no shortage case ${caseNumber.trim()}.`, "case");
    if (c.caseStatus !== "WAITING_RESTOCK") return fail(`Shortage case ${c.shortageCaseID} does not wait for restock. Decide “Wait for restock” for it first.`, "case");
    c.caseStatus = "RESOLVED";
    const o = db.orders.find((x) => x.orderID === c.orderID)!;
    db.events.push({ eventID: nextID("event"), orderID: o.orderID, time: now(), text: `Shortage case ${c.shortageCaseID} resolved after restock.`, actor: "wh_picker_02" });
    return ok(`Shortage case ${c.shortageCaseID} of ${o.orderReference} is resolved.`);
  });
}

export function createPickingOrder(workerUsername: string) {
  return run(() => {
    const worker = readyWorker("WAREHOUSE_WORKER", workerUsername);
    if (typeof worker === "string") return fail(worker, "worker");
    const order = makeTestOrder({ status: "PICKING", picker: worker.user.username, mugs: 3, pens: 0, pickedMugs: 1 });
    return ok(`Created ${order.orderReference}, being picked by ${worker.user.username}: 1 of 3 mugs picked.`, worker.password ? [worker.password] : undefined);
  });
}

// ----- Stage 7 ----------------------------------------------------------------------------------

export function createFailedDeliveryCase(failures: string, returned: boolean, days: string, request: "NONE" | "REDELIVER" | "CANCEL") {
  return run(() => {
    const count = Number(failures);
    if (!/^\d+$/.test(failures.trim()) || count < 1 || count > 5) return fail("Type a number from 1 to 5.", "failures");
    const dayCount = Number(days || "0");
    if (returned && (!/^\d+$/.test((days || "0").trim()) || dayCount > 30)) return fail("Type a number from 0 to 30.", "days");
    if (!returned && request !== "NONE") return fail("A client request needs the package back at the warehouse. Tick “Returned to Warehouse”.", "request");
    const order = makeTestOrder({ status: "SHIPPED", driver: "dl_driver_01" });
    order.orderStatus = "DELIVERY_FAILED";
    const t = now();
    const returnTime = returned ? t - dayCount * TIME.DAY - 30 * TIME.MINUTE : null;
    const lastFail = (returnTime ?? t) - 2 * TIME.HOUR;
    for (let i = 0; i < count; i += 1) {
      const end = lastFail - (count - 1 - i) * TIME.DAY;
      db.attempts.push({ attemptID: nextID("attempt"), orderID: order.orderID, workerUsername: "dl_driver_01", startedTime: end - 30 * TIME.MINUTE, endedTime: end, outcome: "FAILED", reason: "Client not home", note: null });
      db.events.push({ eventID: nextID("event"), orderID: order.orderID, time: end, text: "Delivery attempt ended: Failed. Reason: Client not home.", actor: null });
    }
    const pkg = db.packages.find((p) => p.orderID === order.orderID)!;
    if (returned && returnTime) {
      pkg.location = "WAREHOUSE";
      pkg.heldBy = null;
      pkg.returnedTime = returnTime;
      db.events.push({ eventID: nextID("event"), orderID: order.orderID, time: returnTime, text: "Package event: Return received.", actor: "wh_picker_02" });
    }
    let requestText = "";
    if (request !== "NONE") {
      const id = nextID("instruction");
      db.instructions.push({ instructionID: id, orderID: order.orderID, instructionType: request, instructionStatus: "PENDING", source: "TRACKING_PAGE", submittedTime: t - 5 * TIME.MINUTE, recordedBy: null, identityCheckMethod: null, decidedBy: null, decidedTime: null, note: null });
      requestText = ` Request ${id} (${request === "REDELIVER" ? "redelivery" : "cancellation"}) waits for a decision.`;
    }
    const where = returned ? `returned ${plural(dayCount, "day")} ago` : "not returned yet";
    return ok(`Created ${order.orderReference} with ${plural(count, "failed attempt")}, ${where}.${requestText}`);
  });
}

export function createHeldPackage(workerUsername: string, orderStatus: "SHIPPED" | "OUT_FOR_DELIVERY") {
  return run(() => {
    const worker = readyWorker("DELIVERY_WORKER", workerUsername);
    if (typeof worker === "string") return fail(worker, "worker");
    const order = makeTestOrder({ status: orderStatus, driver: worker.user.username });
    const pkg = db.packages.find((p) => p.orderID === order.orderID)!;
    return ok(`Created ${order.orderReference}. Package ${pkg.packageCode} is held by ${worker.user.username}.`, worker.password ? [worker.password] : undefined);
  });
}

// ----- Stage 8 ----------------------------------------------------------------------------------

export function createRefundCase(status: RefundStatus, ageHours: string) {
  return run(() => {
    const hours = Number(ageHours);
    if (!/^\d+$/.test(ageHours.trim()) || hours > 720) return fail("Type a number from 0 to 720.", "age");
    const order = makeTestOrder({ status: "CANCELLED" });
    const payment = db.payments.filter((p) => p.orderID === order.orderID).at(-1)!;
    const id = nextID("refund");
    const created = now() - hours * TIME.HOUR;
    db.refunds.push({
      refundID: id,
      paymentID: payment.paymentID,
      orderID: order.orderID,
      amount: payment.amount,
      refundStatus: status,
      createdTime: created,
      completedTime: status === "REFUNDED" ? created + 30 * TIME.MINUTE : null,
      requestedBy: actor(),
      lastCheckTime: null,
      lastProviderAnswer: null,
      providerAnswer: status === "REQUESTED" ? "PENDING" : (status as ProviderAnswer),
      replacedByRefundID: null,
    });
    db.events.push({ eventID: nextID("event"), orderID: order.orderID, time: created, text: `Refund ${id} requested (${formatMoney(payment.amount)}).`, actor: null });
    return ok(`Created ${order.orderReference} with payment ${payment.paymentID} and refund ${id} (${plainWord(status)}, ${plural(hours, "hour")} old).`);
  });
}

export function setProviderAnswer(refundNumber: string, outcome: ProviderAnswer) {
  return run(() => {
    if (!/^\d+$/.test(refundNumber.trim())) return fail("Type a refund number.", "refund");
    const r = db.refunds.find((x) => x.refundID === Number(refundNumber));
    if (!r) return fail(`There is no refund ${refundNumber.trim()}.`, "refund");
    r.providerAnswer = outcome;
    return ok(`The stand-in payment provider now answers ${plainWord(outcome)} for refund ${r.refundID}. Press “Check provider status” in its row on Refunds.`);
  });
}

// ----- Stage 9 ----------------------------------------------------------------------------------

export function runPrivacyCheck() {
  return run(() => {
    const answers: Array<[string, unknown]> = [
      ["adminSearchUser", searchUsers({ keyword: "", role: "", status: "" }).map((r) => ({ ...r.user, password: undefined, temporaryPassword: undefined }))],
      ["adminSearchProduct", searchProducts({ keyword: "", status: "", lowOnly: false })],
      ["adminSearchOrder", searchOrders({ keyword: "", status: "", from: "", to: "" })],
      ["adminViewOrder", db.orders.slice(-40).map((o) => orderDetail(o.orderReference))],
      ["adminSearchShortageCase", searchShortageCases("OPEN_AND_WAITING")],
      ["adminSearchPickingTask", pickingTasksInProgress()],
      ["adminSearchFailedDeliveryCase", searchFailedDeliveries("ALL")],
      ["adminSearchHeldPackage", heldPackages()],
      ["adminSearchRefund", searchRefunds(false)],
      ["adminSearchAuditLog", searchAuditLog({ from: "", to: "", actor: "", action: "", target: "", targetID: "" })],
      ["adminSearchSecurityLog", searchSecurityLog({ from: "", to: "", username: "", event: "", result: "" })],
      ["adminSearchPickingScanHistory", searchPickingScans({ from: "", to: "", order: "", worker: "", result: "" })],
      ["adminViewDashboard", dashboardSummary()],
      ["adminViewMenuCounter", dashboardSummary().counters],
    ];
    // Only the texts of an answer are searched: times are numbers of milliseconds, not phone numbers.
    const texts = (value: unknown, out: string[] = []): string[] => {
      if (typeof value === "string") out.push(value);
      else if (Array.isArray(value)) value.forEach((v) => texts(v, out));
      else if (value && typeof value === "object") Object.values(value as Record<string, unknown>).forEach((v) => texts(v, out));
      return out;
    };
    const findings: string[] = [];
    for (const [name, answer] of answers) {
      const found = texts(answer).find(looksPrivate);
      if (found !== undefined) findings.push(`${name} (“${found.slice(0, 40)}”)`);
    }
    if (findings.length === 0) return ok(`0 findings, ${answers.length} answers checked.`);
    return { ok: false, message: `${plural(findings.length, "finding")} in ${answers.length} answers: ${findings.join(", ")} hold a value that looks private.` };
  });
}

export function removeAllTestData(confirmText: string) {
  return run(() => {
    if (confirmText !== "REMOVE") return fail("Type REMOVE in capital letters to confirm.", "confirm");
    const own = me();
    const tables = [
      db.orders,
      db.orderItems,
      db.payments,
      db.refunds,
      db.pickingTasks,
      db.pickingAssignments,
      db.packages,
      db.attempts,
      db.instructions,
      db.shortageCases,
      db.events,
      db.products,
      db.auditLog,
      db.securityLog,
      db.pickingScans,
      db.pickupLists,
    ];
    const rowCount = tables.reduce((sum, table) => sum + table.length, 0);
    for (const table of tables) table.length = 0;
    const testAccounts = db.users.filter((u) => u.isTestAccount && u.userID !== own?.userID);
    db.users = db.users.filter((u) => !u.isTestAccount || u.userID === own?.userID);
    db.logCleanup = null;
    return ok(
      `Removed ${rowCount.toLocaleString("en")} rows and ${plural(testAccounts.length, "test account")}. Your own account and ${plural(db.users.length - 1, "other account")} stay. Reload the browser to bring back the sample data.`,
    );
  });
}
