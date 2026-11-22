import { adminAddress, failedAction, parseTime, sendAdminMessage } from "./adminLists";
import { sendJson } from "./http";
import type { AccountStatus, ActionResult, DeliveryWorkerProfile, Role } from "./types";

const USERS = "/api/admin/users";

interface UserFields {
  userID: number;
  username: string;
  role: Role;
  accountStatus: AccountStatus;
  failedLoginCount: number;
  lockUntil: string | null;
  mustChangePassword: boolean;
  createdTime: string;
}

interface SearchUserRow extends UserFields {
  adminSearchUserIsLockedNow: boolean;
  adminSearchUserLastLoginTime: string | null;
  adminSearchUserIsOwnAccount: boolean;
  adminSearchUserIsSuperAdmin: boolean;
  adminSearchUserCanChange: boolean;
}

export interface SearchUsers {
  adminSearchUserRowList: SearchUserRow[];
  adminSearchUserPageNumber: number;
  adminSearchUserTotalPageCount: number;
  adminSearchUserTotalRowCount: number;
  adminSearchUserCanManageAdminAccount: boolean;
}

export type UserDetail = UserFields & {
  temporaryPasswordExpiryTime: string | null;
  adminViewUserIsLockedNow: boolean;
  adminViewUserIsOwnAccount: boolean;
  adminViewUserIsSuperAdmin: boolean;
  adminViewUserCanChange: boolean;
} & { [P in keyof DeliveryWorkerProfile]: string | null };

export interface UserRow {
  userID: number;
  username: string;
  role: Role;
  accountStatus: AccountStatus;
  failedLoginCount: number;
  mustChangePassword: boolean;
  createdTime: number;
  lastLoginTime: number | null;
  lockMinutesLeft: number | null;
  isLockedNow: boolean;
  isOwnAccount: boolean;
  isSuperAdmin: boolean;
  canChange: boolean;
}

function commonUser(user: UserFields, locked: boolean, lastLoginTime: number | null) {
  const until = parseTime(user.lockUntil);
  return {
    userID: user.userID,
    username: user.username,
    role: user.role,
    accountStatus: user.accountStatus,
    failedLoginCount: user.failedLoginCount,
    mustChangePassword: user.mustChangePassword,
    createdTime: Date.parse(user.createdTime),
    lastLoginTime,
    isLockedNow: locked,
    lockMinutesLeft: locked && until !== null ? Math.max(1, Math.ceil((until - Date.now()) / 60000)) : null,
  };
}

export function searchRow(user: SearchUserRow): UserRow {
  return {
    ...commonUser(user, user.adminSearchUserIsLockedNow, parseTime(user.adminSearchUserLastLoginTime)),
    isOwnAccount: user.adminSearchUserIsOwnAccount,
    isSuperAdmin: user.adminSearchUserIsSuperAdmin,
    canChange: user.adminSearchUserCanChange,
  };
}

export function detailRow(user: UserDetail, lastLoginTime: number | null): UserRow {
  return {
    ...commonUser(user, user.adminViewUserIsLockedNow, lastLoginTime),
    isOwnAccount: user.adminViewUserIsOwnAccount,
    isSuperAdmin: user.adminViewUserIsSuperAdmin,
    canChange: user.adminViewUserCanChange,
  };
}

export function usersAddress(filters: { q: string; role: string; status: string }, page?: number) {
  return adminAddress(page === undefined ? `${USERS}/csv` : USERS, {
    adminSearchUserKeyword: filters.q,
    adminSearchUserRole: filters.role,
    adminSearchUserAccountStatus: filters.status,
    adminSearchUserPageNumber: page,
  });
}

export const userDetailAddress = (userID: number) => `${USERS}/${userID}`;
export const userImpactAddress = (userID: number) => `${USERS}/${userID}/impact`;

export interface UserImpact {
  adminPreviewUserImpactActivePickingTaskCount: number;
  adminPreviewUserImpactActivePickingOrderReferenceList: string[];
  adminPreviewUserImpactOpenPickupListCount: number;
  adminPreviewUserImpactHeldPackageCount: number;
  adminPreviewUserImpactHeldPackageOrderReferenceList: string[];
  adminPreviewUserImpactOpenClientOrderCount: number;
  adminPreviewUserImpactHasActiveWork: boolean;
}

export function workText(impact: UserImpact): string {
  const lines: string[] = [];
  const addWork = (count: number, name: string, refs?: string[]) => {
    if (count) lines.push(`${count} ${name}${count === 1 ? "" : "s"}${refs?.length ? ` (${refs.join(", ")})` : ""}`);
  };
  addWork(impact.adminPreviewUserImpactActivePickingTaskCount, "active picking task", impact.adminPreviewUserImpactActivePickingOrderReferenceList);
  addWork(impact.adminPreviewUserImpactOpenPickupListCount, "open pickup list");
  addWork(impact.adminPreviewUserImpactHeldPackageCount, "held package", impact.adminPreviewUserImpactHeldPackageOrderReferenceList);
  return lines.join(", ");
}

export interface TemporaryPasswordAnswer {
  username: string;
  temporaryPassword: string;
  validUntil: number;
}

export function emptyProfile(detail?: UserDetail): DeliveryWorkerProfile {
  return {
    employeeNumber: detail?.employeeNumber ?? "",
    vehicleType: detail?.vehicleType ?? "",
    vehiclePlate: detail?.vehiclePlate ?? "",
    equipmentNote: detail?.equipmentNote ?? "",
  };
}

const PROFILE_ERRORS = { ADMIN_EMPLOYEE_NUMBER_TAKEN: "employeeNumber" };

export async function createUser(form: { username: string; role: Role } & DeliveryWorkerProfile): Promise<ActionResult<TemporaryPasswordAnswer>> {
  const profile = form.role === "DELIVERY_WORKER" ? {
    adminCreateUserEmployeeNumber: form.employeeNumber,
    adminCreateUserVehicleType: form.vehicleType,
    adminCreateUserVehiclePlate: form.vehiclePlate,
    adminCreateUserEquipmentNote: form.equipmentNote,
  } : {};
  try {
    const answer = await sendJson<{
      username: string;
      adminCreateUserTemporaryPassword: string;
      adminCreateUserTemporaryPasswordExpiryTime: string;
    }>("POST", USERS, { adminCreateUserUsername: form.username, adminCreateUserRole: form.role, ...profile });
    return { ok: true, message: "User created.", data: {
      username: answer.username,
      temporaryPassword: answer.adminCreateUserTemporaryPassword,
      validUntil: Date.parse(answer.adminCreateUserTemporaryPasswordExpiryTime),
    } };
  } catch (error) {
    return failedAction(error, { ...PROFILE_ERRORS, ADMIN_USERNAME_INVALID: "username", ADMIN_USERNAME_TAKEN: "username" });
  }
}

export function saveDeliveryProfile(userID: number, profile: DeliveryWorkerProfile) {
  return sendAdminMessage("PUT", `${USERS}/${userID}/delivery-worker-profile`, {
    adminEditDeliveryWorkerProfileEmployeeNumber: profile.employeeNumber,
    adminEditDeliveryWorkerProfileVehicleType: profile.vehicleType,
    adminEditDeliveryWorkerProfileVehiclePlate: profile.vehiclePlate,
    adminEditDeliveryWorkerProfileEquipmentNote: profile.equipmentNote,
  }, PROFILE_ERRORS);
}

export function changeUserRole(userID: number, role: Role, reason: string) {
  return sendAdminMessage("POST", `${USERS}/${userID}/role`, { adminChangeUserRoleNewRole: role, adminChangeUserRoleReason: reason });
}

export type StatusChange = "ban" | "unban" | "deactivate" | "reactivate";
const REASON_FIELD = {
  ban: "adminBanUserReason",
  unban: "adminUnbanUserReason",
  deactivate: "adminDeactivateUserReason",
  reactivate: "adminReactivateUserReason",
};
export function changeUserStatus(userID: number, change: StatusChange, reason: string) {
  return sendAdminMessage("POST", `${USERS}/${userID}/${change}`, { [REASON_FIELD[change]]: reason });
}

export function unlockUser(userID: number) {
  return sendAdminMessage("POST", `${USERS}/${userID}/unlock`);
}

export async function resetPassword(userID: number, confirmed: boolean, method: string): Promise<ActionResult<TemporaryPasswordAnswer>> {
  try {
    const answer = await sendJson<{
      username: string;
      adminResetPasswordTemporaryPassword: string;
      adminResetPasswordTemporaryPasswordExpiryTime: string;
    }>("POST", `${USERS}/${userID}/reset-password`, {
      adminResetPasswordIdentityIsConfirmed: confirmed,
      adminResetPasswordIdentityCheckMethod: method,
    });
    return { ok: true, message: "Password reset.", data: {
      username: answer.username,
      temporaryPassword: answer.adminResetPasswordTemporaryPassword,
      validUntil: Date.parse(answer.adminResetPasswordTemporaryPasswordExpiryTime),
    } };
  } catch (error) {
    return failedAction(error);
  }
}
