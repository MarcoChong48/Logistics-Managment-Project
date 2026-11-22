// Write side of the stand-in API. Each action checks the rules of the Admin plan, changes the rows,
// writes the audit and security logs, and answers with a message for the person (and, for a form,
// the field the problem belongs to). The pages show these messages word for word.

import { commit, db, nextID, respond } from "./store";
import { restartSessionTimer, stopSessionTimer } from "./session";
import {
  failedDeliveryRow,
  isLockedNow,
  isStaffRole,
  isSuperAdmin,
  lockMinutesLeft,
  orderByID,
  paidPayment,
  pendingInstruction,
  productBySKU,
  sessionUser,
  userByID,
  userByName,
  userImpact,
} from "./queries";
import type {
  ActionResult,
  AuditTarget,
  ClientInstruction,
  InstructionType,
  Order,
  Product,
  Refund,
  Role,
  SecurityLogRow,
  User,
} from "./types";
import { now } from "../lib/clock";
import { formatMoney, formatTime, plainWord, TIME } from "../lib/format";
import { roleLabel } from "../lib/status";
import { parseCSV } from "../lib/csv";

// ----- shared helpers ---------------------------------------------------------------------------

const ok = <T = undefined,>(message: string, data?: T): ActionResult<T> => ({ ok: true, message, data });
const fail = <T = undefined,>(message: string, field?: string): ActionResult<T> => ({ ok: false, message, field });

/** Runs an action like a request: changes the store, restarts the session timer, answers after a short wait. */
function request<T>(work: () => ActionResult<T>, delayMs = 280): Promise<ActionResult<T>> {
  const result = work();
  commit();
  if (db.session) restartSessionTimer();
  return respond(result, delayMs);
}

export function audit(actor: string, action: string, targetType: AuditTarget, targetID: string | number, oldValue: object | null, newValue: object | null, isTest = false) {
  db.auditLog.push({
    auditLogID: nextID("audit"),
    time: now(),
    actorUsername: actor,
    action,
    targetType,
    targetID: String(targetID),
    oldValue: oldValue ? JSON.stringify(oldValue) : null,
    newValue: newValue ? JSON.stringify(newValue) : null,
    isTest,
  });
}

export function security(username: string, eventType: string, result: SecurityLogRow["result"], details: string | null = null, isTest = false) {
  db.securityLog.push({
    securityLogID: nextID("security"),
    time: now(),
    username,
    eventType,
    result,
    ipAddress: "192.0.2.10",
    details,
    userAgent: navigator.userAgent,
    isTest,
  });
}

export function orderEvent(o: Order, text: string, actor: string | null = null) {
  db.events.push({ eventID: nextID("event"), orderID: o.orderID, time: now(), text, actor });
}

const reasonIsValid = (text: string) => text.trim().length >= 5;
const REASON_MESSAGE = "Type a reason of at least 5 characters.";

function me(): User | null {
  return sessionUser();
}

/** A temporary password such as "Kd7p-Wm3x-Qh9t". */
export function makeTemporaryPassword(): string {
  const capitals = "ABCDEFGHJKLMNPQRSTUVWXYZ";
  const smalls = "abcdefghijkmnpqrstuvwxyz";
  const digits = "23456789";
  const values = new Uint32Array(12);
  crypto.getRandomValues(values);
  const char = (set: string, index: number) => set[values[index] % set.length];
  const group = (offset: number) => char(capitals, offset) + char(smalls, offset + 1) + char(digits, offset + 2) + char(smalls, offset + 3);
  return `${group(0)}-${group(4)}-${group(8)}`;
}

/** The super admin guard (Admin plan 18.8). Gives the refusal text, or null when the change is allowed. */
function superAdminGuard(target: User | null, newRole: Role | null): string | null {
  const admin = me();
  if (!admin) return "Your session has ended. Please log in again.";
  if (target && isSuperAdmin(target)) return "The super admin account cannot be changed by another Admin.";
  if ((target?.role === "ADMIN" || newRole === "ADMIN") && !isSuperAdmin(admin)) return "Only the super admin can create or change Admin accounts.";
  return null;
}

function refuse<T>(admin: User | null, functionName: string, code: string, message: string, field?: string): ActionResult<T> {
  if (admin) security(admin.username, "REJECTED_ADMIN_ACTION", "REJECTED", `${functionName}: ${code}`);
  return fail(message, field);
}

// ----- session, login and passwords (shared pages) ----------------------------------------------

export interface LoginAnswer {
  next: "dashboard" | "change-password" | "welcome";
}

export function login(username: string, password: string): Promise<ActionResult<LoginAnswer>> {
  return request(() => {
    if (!username.trim() || !password) return fail("Type your username and your password.", !username.trim() ? "username" : "password");
    const u = userByName(username);
    if (!u) {
      security(username.trim(), "LOGIN_FAILURE", "FAILURE", "unknown username");
      return fail("The username or the password is wrong.");
    }
    if (u.accountStatus === "BANNED") {
      security(u.username, "LOGIN_FAILURE", "REJECTED", "account is banned");
      return fail("This account is banned. Please contact an Admin.");
    }
    if (u.accountStatus === "INACTIVE") {
      security(u.username, "LOGIN_FAILURE", "REJECTED", "account is deactivated");
      return fail("This account is deactivated. Please contact an Admin.");
    }
    if (u.accountStatus === "LOCKED") {
      security(u.username, "LOGIN_FAILURE", "REJECTED", "account is locked (old status)");
      return fail("This account is locked. Please contact an Admin.");
    }
    const minutes = lockMinutesLeft(u);
    if (minutes !== null) {
      security(u.username, "LOGIN_FAILURE", "REJECTED", "account is locked");
      return fail(`This account is locked after 5 wrong passwords. Try again in ${minutes} ${minutes === 1 ? "minute" : "minutes"}, or ask an Admin to unlock it.`);
    }
    const usesTemporary = u.mustChangePassword && u.temporaryPassword !== null;
    const expected = usesTemporary ? u.temporaryPassword : u.password;
    if (password !== expected) {
      u.failedLoginCount += 1;
      security(u.username, "LOGIN_FAILURE", "FAILURE", "wrong password");
      if (u.failedLoginCount >= 5) {
        u.lockUntil = now() + 15 * TIME.MINUTE;
        security(u.username, "ACCOUNT_LOCKED", "FAILURE", "5 wrong passwords");
        return fail("Too many wrong passwords. This account is locked for 15 minutes.");
      }
      return fail("The username or the password is wrong.");
    }
    if (usesTemporary && u.temporaryPasswordExpiryTime !== null && u.temporaryPasswordExpiryTime < now()) {
      security(u.username, "LOGIN_FAILURE", "REJECTED", "temporary password expired");
      return fail("This temporary password has run out. Please ask an Admin for a new one.");
    }
    u.failedLoginCount = 0;
    u.lockUntil = null;
    u.lastLoginTime = now();
    db.session = { userID: u.userID, username: u.username, role: u.role, mustChangePassword: u.mustChangePassword, loginTime: now() };
    db.exitNotice = null;
    security(u.username, "LOGIN_SUCCESS", "SUCCESS");
    restartSessionTimer();
    if (u.mustChangePassword) return ok("Logged in.", { next: "change-password" });
    return ok("Logged in.", { next: u.role === "ADMIN" ? "dashboard" : "welcome" });
  }, 420);
}

export function logout(reason: "LOGGED_OUT" | "IDLE" = "LOGGED_OUT"): Promise<ActionResult> {
  const result = (() => {
    if (db.session) security(db.session.username, reason === "IDLE" ? "SESSION_REVOKED" : "LOGOUT", "SUCCESS", reason === "IDLE" ? "idle timeout" : null);
    db.session = null;
    db.exitNotice = reason;
    stopSessionTimer();
    return ok("You are logged out.");
  })();
  commit();
  return respond(result, 120);
}

export function extendSession(): Promise<ActionResult> {
  return request(() => (db.session ? ok("Your session continues.") : fail("Your session has ended. Please log in again.")), 160);
}

export function passwordRuleProblem(password: string): string | null {
  const bytes = new TextEncoder().encode(password).length;
  if (password.length < 8) return "Use at least 8 characters.";
  if (password.length > 72) return "Use at most 72 characters.";
  if (bytes > 72) return "Use fewer characters. The password can use at most 72 bytes.";
  if (!/[A-Z]/.test(password)) return "Use at least one capital letter from A to Z.";
  if (!/[a-z]/.test(password)) return "Use at least one small letter from a to z.";
  if (!/[0-9]/.test(password)) return "Use at least one number from 0 to 9.";
  return null;
}

export function changePassword(current: string, next: string, repeat: string): Promise<ActionResult> {
  return request(() => {
    const u = me();
    if (!u || !db.session) return fail("Your session ended, so your new password was not saved. Please log in again.", "session");
    const usesTemporary = u.mustChangePassword && u.temporaryPassword !== null;
    const expected = usesTemporary ? u.temporaryPassword : u.password;
    if (current !== expected) return fail(usesTemporary ? "The temporary password is wrong." : "The current password is wrong.", "current");
    const problem = passwordRuleProblem(next);
    if (problem) return fail(problem, "new");
    if (next !== repeat) return fail("The two new passwords are not the same.", "confirm");
    if (next === current) return fail("The new password must be different from the current one.", "new");
    u.password = next;
    u.temporaryPassword = null;
    u.temporaryPasswordExpiryTime = null;
    u.mustChangePassword = false;
    security(u.username, "PASSWORD_CHANGED", "SUCCESS");
    db.session = null;
    db.exitNotice = "PASSWORD_CHANGED";
    stopSessionTimer();
    return ok("Password changed. Please log in with your new password.");
  }, 600);
}

// ----- users ------------------------------------------------------------------------------------

export interface CreateUserInput {
  username: string;
  role: Role;
  employeeNumber: string;
  vehicleType: string;
  vehiclePlate: string;
  equipmentNote: string;
}

export interface TemporaryPasswordAnswer {
  username: string;
  temporaryPassword: string;
  validUntil: number;
}

function profileProblem(input: Pick<CreateUserInput, "employeeNumber" | "vehicleType" | "vehiclePlate" | "equipmentNote">, exceptUserID: number | null): { message: string; field: string } | null {
  if (input.employeeNumber.length > 60) return { message: "Use at most 60 characters.", field: "employeeNumber" };
  if (input.vehicleType.length > 60) return { message: "Use at most 60 characters.", field: "vehicleType" };
  if (input.vehiclePlate.length > 40) return { message: "Use at most 40 characters.", field: "vehiclePlate" };
  if (input.equipmentNote.length > 600) return { message: "Use at most 600 characters.", field: "equipmentNote" };
  const number = input.employeeNumber.trim();
  if (number && db.users.some((u) => u.userID !== exceptUserID && u.deliveryWorkerProfile?.employeeNumber === number)) {
    return { message: "Another worker already has this employee number.", field: "employeeNumber" };
  }
  return null;
}

export function createUser(input: CreateUserInput, isTest = false): Promise<ActionResult<TemporaryPasswordAnswer>> {
  return request(() => {
    const admin = me();
    const username = input.username.trim();
    if (!/^[A-Za-z0-9_]{4,30}$/.test(username)) return fail("Use 4 to 30 characters: letters, digits and underscore.", "username");
    if (userByName(username)) return fail("Another account already has this username.", "username");
    const guard = superAdminGuard(null, input.role);
    if (guard) return refuse(admin, "adminCreateUser", "ADMIN_SUPER_ADMIN_REQUIRED", guard, "role");
    if (input.role === "DELIVERY_WORKER") {
      const problem = profileProblem(input, null);
      if (problem) return fail(problem.message, problem.field);
    }
    const temporaryPassword = makeTemporaryPassword();
    const validUntil = now() + TIME.DAY;
    const user: User = {
      userID: nextID("user"),
      username,
      role: input.role,
      accountStatus: "ACTIVE",
      createdTime: now(),
      lastLoginTime: null,
      failedLoginCount: 0,
      lockUntil: null,
      password: "",
      temporaryPassword,
      temporaryPasswordExpiryTime: validUntil,
      mustChangePassword: true,
      isTestAccount: isTest || username.startsWith("test_"),
      deliveryWorkerProfile:
        input.role === "DELIVERY_WORKER"
          ? { employeeNumber: input.employeeNumber.trim(), vehicleType: input.vehicleType.trim(), vehiclePlate: input.vehiclePlate.trim(), equipmentNote: input.equipmentNote.trim() }
          : null,
    };
    db.users.push(user);
    audit(admin?.username ?? "system", "USER_CREATED", "USER", user.userID, null, { username, role: input.role }, user.isTestAccount);
    security(username, "TEMPORARY_PASSWORD_ISSUED", "SUCCESS", `issued by ${admin?.username ?? "system"}`, user.isTestAccount);
    return ok(`User ${username} was created.`, { username, temporaryPassword, validUntil });
  });
}

export function saveDeliveryProfile(userID: number, input: Pick<CreateUserInput, "employeeNumber" | "vehicleType" | "vehiclePlate" | "equipmentNote">): Promise<ActionResult> {
  return request(() => {
    const u = userByID(userID);
    if (!u) return fail("This account no longer exists. Please refresh the page.");
    if (u.role !== "DELIVERY_WORKER") return fail("Only a Delivery Worker has a profile.");
    const problem = profileProblem(input, u.userID);
    if (problem) return fail(problem.message, problem.field);
    const before = u.deliveryWorkerProfile ?? { employeeNumber: "", vehicleType: "", vehiclePlate: "", equipmentNote: "" };
    const after = { employeeNumber: input.employeeNumber.trim(), vehicleType: input.vehicleType.trim(), vehiclePlate: input.vehiclePlate.trim(), equipmentNote: input.equipmentNote.trim() };
    const changedOld: Record<string, string> = {};
    const changedNew: Record<string, string> = {};
    (Object.keys(after) as Array<keyof typeof after>).forEach((key) => {
      if (before[key] !== after[key]) {
        changedOld[key] = before[key];
        changedNew[key] = after[key];
      }
    });
    u.deliveryWorkerProfile = after;
    if (Object.keys(changedNew).length === 0) return ok("Nothing was changed.");
    audit(me()?.username ?? "system", "DELIVERY_WORKER_PROFILE_EDITED", "USER", u.userID, changedOld, changedNew);
    return ok(`The profile of ${u.username} was saved.`);
  });
}

function workText(impact: ReturnType<typeof userImpact>): string {
  const parts: string[] = [];
  if (impact.activePickingOrders.length) parts.push(`${impact.activePickingOrders.length} active picking ${impact.activePickingOrders.length === 1 ? "task" : "tasks"} (${impact.activePickingOrders.join(", ")})`);
  if (impact.openPickupListCount) parts.push(`${impact.openPickupListCount} open pickup ${impact.openPickupListCount === 1 ? "list" : "lists"}`);
  if (impact.heldPackageOrders.length) parts.push(`${impact.heldPackageOrders.length} held ${impact.heldPackageOrders.length === 1 ? "package" : "packages"} (${impact.heldPackageOrders.join(", ")})`);
  return parts.join(", ");
}

export { workText };

export function changeUserRole(userID: number, newRole: Role, reason: string): Promise<ActionResult> {
  return request(() => {
    const admin = me();
    const u = userByID(userID);
    if (!reasonIsValid(reason)) return fail(REASON_MESSAGE, "reason");
    if (!u) return fail("This account no longer exists. Please refresh the page.");
    if (admin?.userID === u.userID) return refuse(admin, "adminChangeUserRole", "ADMIN_OWN_ACCOUNT_NOT_ALLOWED", "You cannot change your own role.");
    const guard = superAdminGuard(u, newRole);
    if (guard) return refuse(admin, "adminChangeUserRole", "ADMIN_SUPER_ADMIN_PROTECTED", guard);
    if (u.accountStatus !== "ACTIVE") return fail("Only an active account can change role.");
    if (!isStaffRole(u.role) || !isStaffRole(newRole)) return fail("A Client account cannot become staff, and staff cannot become a Client.");
    if (u.role === newRole) return fail("The user already has this role.", "role");
    const impact = userImpact(u);
    if (impact.hasActiveWork) return refuse(admin, "adminChangeUserRole", "ADMIN_USER_HAS_ACTIVE_WORK", `${u.username} still has work: ${workText(impact)}. Reassign it first.`);
    const old = u.role;
    u.role = newRole;
    if (newRole === "DELIVERY_WORKER" && !u.deliveryWorkerProfile) u.deliveryWorkerProfile = { employeeNumber: "", vehicleType: "", vehiclePlate: "", equipmentNote: "" };
    audit(admin!.username, "USER_ROLE_CHANGED", "USER", u.userID, { role: old }, { role: newRole, reason: reason.trim() });
    security(u.username, "ROLE_CHANGED", "SUCCESS", `${old} to ${newRole}, by ${admin!.username}`);
    return ok(`The role of ${u.username} was changed from ${roleLabel(old)} to ${roleLabel(newRole)}. The user must log in again.`);
  });
}

export function resetPassword(userID: number, identityConfirmed: boolean, method: string): Promise<ActionResult<TemporaryPasswordAnswer>> {
  return request(() => {
    const admin = me();
    const u = userByID(userID);
    if (!identityConfirmed) return fail("Please confirm that you checked the person’s identity.", "tick");
    if (!reasonIsValid(method)) return fail("Type how you checked the person, at least 5 characters.", "reason");
    if (!u) return fail("This account no longer exists. Please refresh the page.");
    if (admin?.userID === u.userID) return refuse(admin, "adminResetPassword", "ADMIN_OWN_ACCOUNT_NOT_ALLOWED", "Use Change password for your own account.");
    const guard = superAdminGuard(u, null);
    if (guard) return refuse(admin, "adminResetPassword", "ADMIN_SUPER_ADMIN_PROTECTED", guard);
    if (u.accountStatus !== "ACTIVE") return fail("Unban or reactivate the account first.");
    const temporaryPassword = makeTemporaryPassword();
    const validUntil = now() + TIME.DAY;
    u.temporaryPassword = temporaryPassword;
    u.temporaryPasswordExpiryTime = validUntil;
    u.mustChangePassword = true;
    u.failedLoginCount = 0;
    u.lockUntil = null;
    audit(admin!.username, "USER_PASSWORD_RESET", "USER", u.userID, null, { identityCheckMethod: method.trim() });
    security(u.username, "TEMPORARY_PASSWORD_ISSUED", "SUCCESS", `issued by ${admin!.username}`);
    return ok(`A temporary password was issued for ${u.username}.`, { username: u.username, temporaryPassword, validUntil });
  });
}

type StatusChange = "ban" | "unban" | "deactivate" | "reactivate";

export function changeUserStatus(userID: number, change: StatusChange, reason: string): Promise<ActionResult> {
  return request(() => {
    const admin = me();
    const u = userByID(userID);
    if (!reasonIsValid(reason)) return fail(REASON_MESSAGE, "reason");
    if (!u) return fail("This account no longer exists. Please refresh the page.");
    const fn = { ban: "adminBanUser", unban: "adminUnbanUser", deactivate: "adminDeactivateUser", reactivate: "adminReactivateUser" }[change];
    if (admin?.userID === u.userID) return refuse(admin, fn, "ADMIN_OWN_ACCOUNT_NOT_ALLOWED", "You cannot change your own account here.");
    const guard = superAdminGuard(u, null);
    if (guard) return refuse(admin, fn, "ADMIN_SUPER_ADMIN_PROTECTED", guard);
    const impact = userImpact(u);
    const counts = { activePickingTaskCount: impact.activePickingOrders.length, openPickupListCount: impact.openPickupListCount, heldPackageCount: impact.heldPackageOrders.length };
    const old = u.accountStatus;
    if (change === "ban") {
      if (u.accountStatus !== "ACTIVE") return fail("Only an active account can be banned.");
      u.accountStatus = "BANNED";
      audit(admin!.username, "USER_BANNED", "USER", u.userID, { accountStatus: old }, { accountStatus: "BANNED", reason: reason.trim(), ...counts });
      security(u.username, "ACCOUNT_BANNED", "SUCCESS", `by ${admin!.username}`);
      security(u.username, "SESSION_REVOKED", "SUCCESS", "account banned");
      return ok(`User ${u.username} was banned.`);
    }
    if (change === "unban") {
      if (u.accountStatus !== "BANNED") return fail("This account is not banned.");
      u.accountStatus = "ACTIVE";
      u.failedLoginCount = 0;
      u.lockUntil = null;
      audit(admin!.username, "USER_UNBANNED", "USER", u.userID, { accountStatus: old }, { accountStatus: "ACTIVE", reason: reason.trim() });
      security(u.username, "ACCOUNT_UNBANNED", "SUCCESS", `by ${admin!.username}`);
      return ok(`User ${u.username} was unbanned.`);
    }
    if (change === "deactivate") {
      if (u.accountStatus !== "ACTIVE" && u.accountStatus !== "BANNED") return fail("Only an active or banned account can be deactivated.");
      u.accountStatus = "INACTIVE";
      audit(admin!.username, "USER_DEACTIVATED", "USER", u.userID, { accountStatus: old }, { accountStatus: "INACTIVE", reason: reason.trim(), ...counts });
      security(u.username, "ACCOUNT_DEACTIVATED", "SUCCESS", `by ${admin!.username}`);
      security(u.username, "SESSION_REVOKED", "SUCCESS", "account deactivated");
      return ok(`User ${u.username} was deactivated.`);
    }
    if (u.accountStatus !== "INACTIVE") return fail("This account is not deactivated.");
    u.accountStatus = "ACTIVE";
    u.failedLoginCount = 0;
    u.lockUntil = null;
    audit(admin!.username, "USER_REACTIVATED", "USER", u.userID, { accountStatus: old }, { accountStatus: "ACTIVE", reason: reason.trim() });
    security(u.username, "ACCOUNT_REACTIVATED", "SUCCESS", `by ${admin!.username}`);
    return ok(`User ${u.username} was reactivated.`);
  });
}

export function unlockUser(userID: number): Promise<ActionResult> {
  return request(() => {
    const admin = me();
    const u = userByID(userID);
    if (!u) return fail("This account no longer exists. Please refresh the page.");
    const guard = superAdminGuard(u, null);
    if (guard) return refuse(admin, "adminUnlockUser", "ADMIN_SUPER_ADMIN_PROTECTED", guard);
    if (!isLockedNow(u)) return fail("This account is not locked.");
    const wasOld = u.accountStatus === "LOCKED";
    u.failedLoginCount = 0;
    u.lockUntil = null;
    if (wasOld) u.accountStatus = "ACTIVE";
    audit(admin!.username, "USER_UNLOCKED", "USER", u.userID, wasOld ? { accountStatus: "LOCKED" } : null, wasOld ? { accountStatus: "ACTIVE" } : null);
    security(u.username, "ACCOUNT_UNLOCKED_BY_ADMIN", "SUCCESS", `by ${admin!.username}`);
    return ok(`User ${u.username} was unlocked.`);
  });
}

// ----- products ---------------------------------------------------------------------------------

export interface ProductInput {
  SKU: string;
  name: string;
  price: string;
  lowBelow: string;
  status: "ACTIVE" | "INACTIVE";
}

function productProblem(input: ProductInput, exceptID: number | null): { message: string; field: string } | null {
  const code = input.SKU.trim().toUpperCase();
  if (!/^[A-Z0-9-]{3,60}$/.test(code)) return { message: "Use 3 to 60 characters: letters, digits and hyphens.", field: "SKU" };
  const same = productBySKU(code);
  if (same && same.productID !== exceptID) return { message: "This SKU already exists.", field: "SKU" };
  const name = input.name.trim();
  if (name.length < 2 || name.length > 200) return { message: "Use 2 to 200 characters.", field: "name" };
  if (!/^\d+(\.\d{1,2})?$/.test(input.price.trim()) || Number(input.price) <= 0) return { message: "The price must be above 0, with at most 2 decimal places.", field: "price" };
  if (!/^\d+$/.test(input.lowBelow.trim())) return { message: "Type a whole number of 0 or more.", field: "lowBelow" };
  return null;
}

export function createProduct(input: ProductInput, isTest = false): Promise<ActionResult<Product>> {
  return request(() => {
    const problem = productProblem(input, null);
    if (problem) return fail(problem.message, problem.field);
    const product: Product = {
      productID: nextID("product"),
      SKU: input.SKU.trim().toUpperCase(),
      name: input.name.trim(),
      currentPrice: Number(input.price),
      physicalStock: 0,
      reservedStock: 0,
      lowStockThreshold: Number(input.lowBelow),
      listingStatus: input.status,
      createdTime: now(),
      isTest: isTest || input.SKU.trim().toUpperCase().startsWith("TEST-"),
    };
    db.products.push(product);
    audit(me()?.username ?? "system", "PRODUCT_CREATED", "PRODUCT", product.productID, null, { SKU: product.SKU, name: product.name, currentPrice: product.currentPrice }, product.isTest);
    return ok(`Product ${product.SKU} was added with stock 0.`, product);
  });
}

export function editProduct(productID: number, input: Omit<ProductInput, "status">): Promise<ActionResult> {
  return request(() => {
    const p = db.products.find((x) => x.productID === productID);
    if (!p) return fail("This product no longer exists. Please refresh the page.");
    const problem = productProblem({ ...input, status: p.listingStatus }, p.productID);
    if (problem) return fail(problem.message, problem.field);
    const next = { SKU: input.SKU.trim().toUpperCase(), name: input.name.trim(), currentPrice: Number(input.price), lowStockThreshold: Number(input.lowBelow) };
    const changedOld: Record<string, unknown> = {};
    const changedNew: Record<string, unknown> = {};
    (Object.keys(next) as Array<keyof typeof next>).forEach((key) => {
      if (p[key] !== next[key]) {
        changedOld[key] = p[key];
        changedNew[key] = next[key];
      }
    });
    if (Object.keys(changedNew).length === 0) return ok("Nothing was changed.");
    Object.assign(p, next);
    audit(me()?.username ?? "system", "PRODUCT_EDITED", "PRODUCT", p.productID, changedOld, changedNew, p.isTest);
    return ok(`Product ${p.SKU} was saved.`);
  });
}

export function setProductListing(productID: number, status: "ACTIVE" | "INACTIVE", reason: string): Promise<ActionResult> {
  return request(() => {
    const p = db.products.find((x) => x.productID === productID);
    if (!p) return fail("This product no longer exists. Please refresh the page.");
    if (status === "INACTIVE" && !reasonIsValid(reason)) return fail(REASON_MESSAGE, "reason");
    if (p.listingStatus === status) return fail(status === "ACTIVE" ? "This product is already active." : "This product is already inactive.");
    p.listingStatus = status;
    audit(me()?.username ?? "system", status === "ACTIVE" ? "PRODUCT_ACTIVATED" : "PRODUCT_DEACTIVATED", "PRODUCT", p.productID, { listingStatus: status === "ACTIVE" ? "INACTIVE" : "ACTIVE" }, status === "ACTIVE" ? { listingStatus: "ACTIVE" } : { listingStatus: "INACTIVE", reason: reason.trim() }, p.isTest);
    return ok(status === "ACTIVE" ? `Product ${p.SKU} was activated.` : `Product ${p.SKU} was deactivated.`);
  });
}

export const IMPORT_HEADER = "SKU,name,currentPrice,lowStockThreshold";

export interface ImportAnswer {
  added: number;
  rejected: Array<{ line: number; reason: string }>;
}

export function importProducts(fileName: string, fileSize: number, text: string): Promise<ActionResult<ImportAnswer>> {
  return request(() => {
    if (!fileName.toLowerCase().endsWith(".csv")) return fail("Choose a CSV file (a name that ends in .csv).", "file");
    if (fileSize > 1024 * 1024) return fail("The file is larger than 1 MB.", "file");
    const rows = parseCSV(text).filter((r) => r.some((cell) => cell.trim() !== ""));
    if (rows.length === 0) return fail("The file is empty.", "file");
    if (rows[0].map((c) => c.trim()).join(",") !== IMPORT_HEADER) return fail(`The first line must be exactly: ${IMPORT_HEADER}`, "file");
    if (rows.length - 1 > 500) return fail("The file has more than 500 lines after the first line.", "file");
    const seen = new Set<string>();
    const rejected: ImportAnswer["rejected"] = [];
    let added = 0;
    rows.slice(1).forEach((cells, index) => {
      const line = index + 2;
      if (cells.length !== 4) return rejected.push({ line, reason: "The line must hold exactly 4 values." });
      const [rawSKU, name, price, low] = cells.map((c) => c.trim());
      const code = rawSKU.toUpperCase();
      if (!/^[A-Z0-9-]{3,60}$/.test(code)) return rejected.push({ line, reason: "The SKU may hold only letters, digits and hyphens (3 to 60)." });
      if (seen.has(code)) return rejected.push({ line, reason: "This SKU appears earlier in the file." });
      seen.add(code);
      if (productBySKU(code)) return rejected.push({ line, reason: "This SKU already exists." });
      if (name.length < 2 || name.length > 200) return rejected.push({ line, reason: "The name must have 2 to 200 characters." });
      if (!/^-?\d+(\.\d+)?$/.test(price)) return rejected.push({ line, reason: "The price is not a number." });
      if (Number(price) <= 0 || !/^\d+(\.\d{1,2})?$/.test(price)) return rejected.push({ line, reason: "The price must be above 0, with at most 2 decimal places." });
      if (!/^\d+$/.test(low)) return rejected.push({ line, reason: "Low below must be a whole number of 0 or more." });
      db.products.push({
        productID: nextID("product"),
        SKU: code,
        name,
        currentPrice: Number(price),
        physicalStock: 0,
        reservedStock: 0,
        lowStockThreshold: Number(low),
        listingStatus: "ACTIVE",
        createdTime: now(),
        isTest: code.startsWith("TEST-"),
      });
      added += 1;
      return undefined;
    });
    audit(me()?.username ?? "system", "PRODUCT_IMPORTED", "PRODUCT", "-", null, { addedCount: added, rejectedCount: rejected.length, fileName });
    return ok(`${added} ${added === 1 ? "product" : "products"} added, ${rejected.length} ${rejected.length === 1 ? "line" : "lines"} rejected.`, { added, rejected });
  }, 500);
}

// ----- shared parts of a cancellation -----------------------------------------------------------

/** Cancels an order for good and requests the full refund if something was paid. Gives the end of the decided line. */
function cancelOrderWithRefund(o: Order, admin: string, reasonText: string): { line: string; message: string; refund: Refund | null } {
  o.orderStatus = "CANCELLED";
  const task = db.pickingTasks.filter((t) => t.orderID === o.orderID && t.taskStatus === "IN_PROGRESS");
  for (const t of task) {
    t.taskStatus = "CANCELLED";
    db.pickingAssignments.filter((a) => a.pickingTaskID === t.pickingTaskID && a.endedTime === null).forEach((a) => (a.endedTime = now()));
  }
  // Units that were reserved for this order are free again.
  for (const item of db.orderItems.filter((i) => i.orderID === o.orderID)) {
    const product = db.products.find((p) => p.productID === item.productID);
    if (product) product.reservedStock = Math.max(0, product.reservedStock - (item.quantity - item.pickedQuantity));
  }
  for (const c of db.shortageCases.filter((x) => x.orderID === o.orderID && (x.caseStatus === "OPEN" || x.caseStatus === "WAITING_RESTOCK"))) {
    c.caseStatus = "CANCELLED";
    c.decidedBy = c.decidedBy ?? admin;
    c.decidedTime = c.decidedTime ?? now();
  }
  for (const i of db.instructions.filter((x) => x.orderID === o.orderID && x.instructionStatus === "PENDING")) i.instructionStatus = "WITHDRAWN";
  const payment = paidPayment(o.orderID);
  const ref = o.orderReference;
  if (!payment) {
    orderEvent(o, "Order cancelled. Nothing had been paid, so no refund was needed.", admin);
    return { line: "No refund needed.", message: `Order ${ref} was cancelled. Nothing had been paid, so no refund was needed.`, refund: null };
  }
  const existing = db.refunds.filter((r) => r.paymentID === payment.paymentID).sort((a, b) => b.refundID - a.refundID)[0];
  if (existing) {
    orderEvent(o, `Order cancelled. Refund ${existing.refundID} already existed.`, admin);
    return { line: `Refund ${existing.refundID} already existed.`, message: `Order ${ref} was cancelled. Refund ${existing.refundID} of this payment already existed, so no new refund was requested.`, refund: existing };
  }
  const refund: Refund = {
    refundID: nextID("refund"),
    paymentID: payment.paymentID,
    orderID: o.orderID,
    amount: payment.amount,
    refundStatus: "REQUESTED",
    createdTime: now(),
    completedTime: null,
    requestedBy: admin,
    lastCheckTime: null,
    lastProviderAnswer: null,
    providerAnswer: "PENDING",
    replacedByRefundID: null,
  };
  db.refunds.push(refund);
  orderEvent(o, `Order cancelled. A full refund was requested (${formatMoney(payment.amount)}).`, admin);
  orderEvent(o, `Refund ${refund.refundID} requested (${formatMoney(payment.amount)}).`);
  void reasonText;
  return { line: "Full refund requested.", message: `Order ${ref} was cancelled. A full refund was requested.`, refund };
}

// ----- warehouse cases --------------------------------------------------------------------------

export function decideShortageWait(caseID: number, reason: string): Promise<ActionResult> {
  return request(() => {
    const admin = me();
    const c = db.shortageCases.find((x) => x.shortageCaseID === caseID);
    if (!reasonIsValid(reason)) return fail(REASON_MESSAGE, "reason");
    if (!c || c.caseStatus !== "OPEN") return fail("This case was already handled. Please refresh the page.");
    const o = orderByID(c.orderID)!;
    c.caseStatus = "WAITING_RESTOCK";
    c.decision = "WAIT_FOR_RESTOCK";
    c.decidedBy = admin!.username;
    c.decidedTime = now();
    c.decisionReason = reason.trim();
    orderEvent(o, `Shortage case ${c.shortageCaseID}: wait for restock. The client was told about the delay.`, admin!.username);
    audit(admin!.username, "SHORTAGE_CASE_DECIDED", "SHORTAGE_CASE", c.shortageCaseID, { caseStatus: "OPEN" }, { decision: "WAIT_FOR_RESTOCK", reason: reason.trim() });
    return ok(`The shortage case of order ${o.orderReference} now waits for restock.`);
  });
}

export function decideShortageCancel(caseID: number, reason: string, stockCorrectionConfirmed: boolean): Promise<ActionResult> {
  return request(() => {
    const admin = me();
    const c = db.shortageCases.find((x) => x.shortageCaseID === caseID);
    if (!stockCorrectionConfirmed) return fail("Please confirm that Warehouse recorded the stock correction.", "tick");
    if (!reasonIsValid(reason)) return fail(REASON_MESSAGE, "reason");
    if (!c || (c.caseStatus !== "OPEN" && c.caseStatus !== "WAITING_RESTOCK")) return fail("This case was already handled. Please refresh the page.");
    const o = orderByID(c.orderID)!;
    const decisionTime = now();
    const result = cancelOrderWithRefund(o, admin!.username, reason);
    c.caseStatus = "CANCELLED";
    c.decision = result.refund || !result.line.startsWith("No") ? "CANCEL_AND_REFUND" : "CANCEL_ORDER";
    c.decidedBy = admin!.username;
    c.decidedTime = decisionTime;
    c.decisionReason = reason.trim();
    c.refundNote = result.line;
    audit(admin!.username, "SHORTAGE_CASE_DECIDED", "SHORTAGE_CASE", c.shortageCaseID, { caseStatus: "OPEN" }, { decision: c.decision, reason: reason.trim(), refundID: result.refund?.refundID ?? null });
    return ok(result.message);
  });
}

export function reassignPickingTask(taskID: number, worker: string, reason: string): Promise<ActionResult> {
  return request(() => {
    const admin = me();
    const task = db.pickingTasks.find((t) => t.pickingTaskID === taskID);
    if (!reasonIsValid(reason)) return fail(REASON_MESSAGE, "reason");
    if (!task || task.taskStatus !== "IN_PROGRESS") return fail("This picking task is no longer in progress. Please refresh the page.");
    const target = userByName(worker);
    if (!target || target.role !== "WAREHOUSE_WORKER" || target.accountStatus !== "ACTIVE") return fail("Choose an active Warehouse Worker.", "worker");
    const current = db.pickingAssignments.find((a) => a.pickingTaskID === taskID && a.endedTime === null);
    if (current?.workerUsername === target.username) return fail("This worker already has the task.", "worker");
    if (current) current.endedTime = now();
    db.pickingAssignments.push({ assignmentID: nextID("assignment"), pickingTaskID: taskID, workerUsername: target.username, assignedTime: now(), endedTime: null, assignedBy: admin!.username, reason: reason.trim() });
    const o = orderByID(task.orderID)!;
    orderEvent(o, `Picking task reassigned to ${target.username}.`, admin!.username);
    audit(admin!.username, "PICKING_TASK_REASSIGNED", "PICKING_TASK", taskID, { workerUsername: current?.workerUsername ?? null }, { workerUsername: target.username, reason: reason.trim() });
    return ok(`The picking task of order ${o.orderReference} was reassigned to ${target.username}.`);
  });
}

// ----- delivery cases ---------------------------------------------------------------------------

export function sendReminder(orderID: number): Promise<ActionResult> {
  return request(() => {
    const admin = me();
    const o = orderByID(orderID);
    if (!o || o.orderStatus !== "DELIVERY_FAILED") return fail("This case changed. Please refresh the page.");
    const row = failedDeliveryRow(o);
    if (row.state !== "WAITING_FOR_CLIENT") return fail("A reminder can be sent only while the case waits for the client.");
    if (!row.canSendReminder) return fail(`A reminder was sent less than 24 hours ago. Next reminder after ${formatTime(row.nextReminderTime)}.`);
    row.pkg!.lastReminderTime = now();
    orderEvent(o, "Reminder sent to the client.", admin!.username);
    audit(admin!.username, "CLIENT_REMINDER_SENT", "ORDER", o.orderID, null, { orderReference: o.orderReference });
    return ok(`A reminder was sent to the client of order ${o.orderReference}.`);
  });
}

export function recordClientInstruction(orderID: number, type: InstructionType, identityConfirmed: boolean, method: string, expectedPendingID: number | null): Promise<ActionResult> {
  return request(() => {
    const admin = me();
    const o = orderByID(orderID);
    if (!identityConfirmed) return fail("Please confirm that you checked the person’s identity.", "tick");
    if (!reasonIsValid(method)) return fail("Type how you checked the person, at least 5 characters.", "reason");
    if (!o || o.orderStatus !== "DELIVERY_FAILED") return fail("This case changed. Please refresh the page.");
    const row = failedDeliveryRow(o);
    if (row.state === "WAITING_FOR_RETURN") return fail("The package is not back at the warehouse yet.");
    const pending = pendingInstruction(o.orderID);
    if ((pending?.instructionID ?? null) !== expectedPendingID) return fail("The pending request changed while this window was open. Refresh the request and check it again.", "conflict");
    if (pending) pending.instructionStatus = "WITHDRAWN";
    const instruction: ClientInstruction = {
      instructionID: nextID("instruction"),
      orderID: o.orderID,
      instructionType: type,
      instructionStatus: "PENDING",
      source: "ADMIN_MANUAL",
      submittedTime: now(),
      recordedBy: admin!.username,
      identityCheckMethod: method.trim(),
      decidedBy: null,
      decidedTime: null,
      note: null,
    };
    db.instructions.push(instruction);
    orderEvent(o, `Client request: ${type === "REDELIVER" ? "Redeliver" : "Cancel"} (entered through Admin).`);
    audit(admin!.username, "CLIENT_INSTRUCTION_RECORDED_BY_ADMIN", "CLIENT_INSTRUCTION", instruction.instructionID, pending ? { withdrawnInstructionID: pending.instructionID } : null, {
      instructionType: type,
      source: "ADMIN_MANUAL",
      identityCheckMethod: method.trim(),
      orderReference: o.orderReference,
    });
    return ok(`The request for order ${o.orderReference} was recorded.`);
  });
}

export function approveRedelivery(orderID: number, note: string): Promise<ActionResult> {
  return request(() => {
    const admin = me();
    const o = orderByID(orderID);
    const pending = o ? pendingInstruction(o.orderID) : null;
    if (!o || o.orderStatus !== "DELIVERY_FAILED" || !pending || pending.instructionType !== "REDELIVER") return fail("This request was already handled. Please refresh the page.");
    if (failedDeliveryRow(o).hasOpenShortage) return fail("Decide the open shortage case first.");
    pending.instructionStatus = "APPROVED";
    pending.decidedBy = admin!.username;
    pending.decidedTime = now();
    pending.note = note.trim() || null;
    o.orderStatus = "READY_FOR_REDELIVERY";
    orderEvent(o, "Redelivery approved.", admin!.username);
    audit(admin!.username, "CLIENT_INSTRUCTION_DECIDED", "CLIENT_INSTRUCTION", pending.instructionID, { instructionStatus: "PENDING" }, { instructionStatus: "APPROVED", orderReference: o.orderReference, note: pending.note });
    return ok(`Redelivery of order ${o.orderReference} was approved.`);
  });
}

export function approveCancellation(orderID: number, note: string): Promise<ActionResult<{ line: string }>> {
  return request(() => {
    const admin = me();
    if (!reasonIsValid(note)) return fail(REASON_MESSAGE, "reason");
    const o = orderByID(orderID);
    const pending = o ? pendingInstruction(o.orderID) : null;
    if (!o || o.orderStatus !== "DELIVERY_FAILED" || !pending || pending.instructionType !== "CANCEL") return fail("This request was already handled. Please refresh the page.");
    pending.instructionStatus = "APPROVED";
    pending.decidedBy = admin!.username;
    pending.decidedTime = now();
    pending.note = note.trim() || null;
    const result = cancelOrderWithRefund(o, admin!.username, note);
    audit(admin!.username, "CLIENT_INSTRUCTION_DECIDED", "CLIENT_INSTRUCTION", pending.instructionID, { instructionStatus: "PENDING" }, { instructionStatus: "APPROVED", orderReference: o.orderReference, refundID: result.refund?.refundID ?? null });
    return ok(result.message, { line: result.line });
  });
}

export function rejectClientInstruction(orderID: number, reason: string): Promise<ActionResult> {
  return request(() => {
    const admin = me();
    const o = orderByID(orderID);
    if (!reasonIsValid(reason)) return fail(REASON_MESSAGE, "reason");
    const pending = o ? pendingInstruction(o.orderID) : null;
    if (!o || !pending) return fail("This request was already handled. Please refresh the page.");
    pending.instructionStatus = "REJECTED";
    pending.decidedBy = admin!.username;
    pending.decidedTime = now();
    pending.note = reason.trim();
    orderEvent(o, `Client request rejected. Reason: ${reason.trim()}.`, admin!.username);
    audit(admin!.username, "CLIENT_INSTRUCTION_DECIDED", "CLIENT_INSTRUCTION", pending.instructionID, { instructionStatus: "PENDING" }, { instructionStatus: "REJECTED", orderReference: o.orderReference, reason: reason.trim() });
    return ok(`The ${pending.instructionType === "REDELIVER" ? "redelivery" : "cancellation"} request for order ${o.orderReference} was rejected.`);
  });
}

export function reassignDelivery(packageID: number, worker: string, reason: string): Promise<ActionResult> {
  return request(() => {
    const admin = me();
    const pkg = db.packages.find((p) => p.packageID === packageID);
    if (!reasonIsValid(reason)) return fail(REASON_MESSAGE, "reason");
    if (!pkg || pkg.location !== "DELIVERY") return fail("This package is no longer held by a Delivery Worker. Please refresh the page.");
    const target = userByName(worker);
    if (!target || target.role !== "DELIVERY_WORKER" || target.accountStatus !== "ACTIVE") return fail("Choose an active Delivery Worker.", "worker");
    if (pkg.responsibleWorker === target.username) return fail("This worker is already responsible for the delivery.", "worker");
    const old = pkg.responsibleWorker;
    pkg.responsibleWorker = target.username;
    pkg.lastReassignedTime = now();
    pkg.lastReassignedBy = admin!.username;
    const o = orderByID(pkg.orderID)!;
    orderEvent(o, `Delivery reassigned to ${target.username}.`, admin!.username);
    audit(admin!.username, "DELIVERY_REASSIGNED", "PACKAGE", pkg.packageID, { responsibleWorker: old }, { responsibleWorker: target.username, reason: reason.trim() });
    return ok(`Delivery of order ${o.orderReference} is now the responsibility of ${target.username}.`);
  });
}

// ----- refunds ----------------------------------------------------------------------------------

function applyProviderAnswer(r: Refund): void {
  r.lastCheckTime = now();
  r.lastProviderAnswer = r.providerAnswer;
  if (r.providerAnswer === "REFUNDED") {
    r.refundStatus = "REFUNDED";
    r.completedTime = now();
  } else if (r.providerAnswer === "FAILED") {
    r.refundStatus = "FAILED";
  } else if (r.providerAnswer === "PENDING") {
    r.refundStatus = "PENDING";
  }
}

export function checkProviderStatus(refundID: number): Promise<ActionResult> {
  return request(() => {
    const r = db.refunds.find((x) => x.refundID === refundID);
    if (!r) return fail("This refund no longer exists. Please refresh the page.");
    applyProviderAnswer(r);
    const o = orderByID(r.orderID)!;
    if (r.refundStatus === "REFUNDED") orderEvent(o, `Refund ${r.refundID} confirmed by the payment provider.`);
    audit(me()?.username ?? "system", "REFUND_STATUS_CHECKED", "REFUND", r.refundID, null, { providerAnswer: r.lastProviderAnswer });
    return ok(`The payment provider answers: ${plainWord(r.lastProviderAnswer ?? "UNKNOWN")}.`);
  });
}

export function retryRefund(refundID: number, reason: string): Promise<ActionResult<{ refundID: number | null }>> {
  return request(() => {
    const admin = me();
    const r = db.refunds.find((x) => x.refundID === refundID);
    if (!reasonIsValid(reason)) return fail(REASON_MESSAGE, "reason");
    if (!r || r.refundStatus !== "FAILED" || r.replacedByRefundID !== null) return fail("This refund was already handled. Please refresh the page.");
    applyProviderAnswer(r);
    const o = orderByID(r.orderID)!;
    if (r.refundStatus !== "FAILED") {
      audit(admin!.username, "REFUND_STATUS_CHECKED", "REFUND", r.refundID, null, { providerAnswer: r.lastProviderAnswer });
      return ok(`The payment provider now answers: ${plainWord(r.lastProviderAnswer ?? "UNKNOWN")}. No new refund was requested.`, { refundID: null });
    }
    const fresh: Refund = { ...r, refundID: nextID("refund"), refundStatus: "REQUESTED", createdTime: now(), completedTime: null, requestedBy: admin!.username, lastCheckTime: null, lastProviderAnswer: null, providerAnswer: "PENDING", replacedByRefundID: null };
    db.refunds.push(fresh);
    r.replacedByRefundID = fresh.refundID;
    orderEvent(o, `Refund ${r.refundID} retried. Refund ${fresh.refundID} requested (${formatMoney(fresh.amount)}).`, admin!.username);
    audit(admin!.username, "REFUND_RETRIED", "REFUND", r.refundID, { refundStatus: "FAILED" }, { newRefundID: fresh.refundID, reason: reason.trim() });
    return ok(`A new refund was requested for order ${o.orderReference}.`, { refundID: fresh.refundID });
  });
}

// ----- logs and CSV -----------------------------------------------------------------------------

export function recordCSVDownload(list: string, rowCount: number): void {
  const admin = me();
  audit(admin?.username ?? "system", "CSV_DOWNLOADED", "SYSTEM", "CSV", null, { list, rowCount });
  commit();
  if (db.session) restartSessionTimer();
}

const KEEP = { security: 90 * TIME.DAY, scans: 90 * TIME.DAY, audit: 3 * 365 * TIME.DAY };

/** The daily clean-up: removes log rows older than their keeping time. */
export function runLogCleanup(actor = "system"): { security: number; scans: number; audit: number } {
  const t = now();
  const beforeSecurity = db.securityLog.length;
  const beforeScans = db.pickingScans.length;
  const beforeAudit = db.auditLog.length;
  db.securityLog = db.securityLog.filter((r) => r.time >= t - KEEP.security);
  db.pickingScans = db.pickingScans.filter((r) => r.time >= t - KEEP.scans);
  db.auditLog = db.auditLog.filter((r) => r.time >= t - KEEP.audit);
  const counts = { security: beforeSecurity - db.securityLog.length, scans: beforeScans - db.pickingScans.length, audit: beforeAudit - db.auditLog.length };
  db.logCleanup = { time: t, securityLogDeletedCount: counts.security, pickingScanHistoryDeletedCount: counts.scans, auditLogDeletedCount: counts.audit };
  audit(actor, "LOG_CLEANUP_RUN", "LOG", "LOG_CLEANUP", null, { securityLogDeletedCount: counts.security, pickingScanHistoryDeletedCount: counts.scans, auditLogDeletedCount: counts.audit });
  return counts;
}

export { reasonIsValid, REASON_MESSAGE, request, ok, fail, me };
