import { adminAddress } from "./adminLists";
import { filterDateTime } from "../lib/format";

export const AUDIT_ACTIONS = [
  "USER_CREATED", "USER_ROLE_CHANGED", "USER_BANNED", "USER_UNBANNED", "USER_DEACTIVATED", "USER_REACTIVATED",
  "USER_UNLOCKED", "USER_PASSWORD_RESET", "DELIVERY_WORKER_PROFILE_EDITED",
  "PRODUCT_CREATED", "PRODUCT_EDITED", "PRODUCT_ACTIVATED", "PRODUCT_DEACTIVATED", "PRODUCT_IMPORTED",
  "SHORTAGE_WAIT_DECIDED", "SHORTAGE_CANCEL_DECIDED", "ORDER_CANCELLED_BY_ADMIN", "PICKING_TASK_REASSIGNED",
  "REFUND_REQUESTED", "REFUND_STATUS_CHECKED", "REFUND_RETRIED", "DELIVERY_REMINDER_SENT",
  "CLIENT_INSTRUCTION_RECORDED_BY_ADMIN", "REDELIVERY_APPROVED", "CANCELLATION_APPROVED",
  "CLIENT_INSTRUCTION_REJECTED", "DELIVERY_REASSIGNED", "LOG_CLEANUP_RUN", "CSV_DOWNLOADED",
];

export const AUDIT_TARGETS = ["USER", "PRODUCT", "ORDER", "PICKING_TASK", "PACKAGE", "SHORTAGE_CASE", "CLIENT_INSTRUCTION", "REFUND", "LOG", "SYSTEM"];
export const SECURITY_EVENTS = [
  "LOGIN_SUCCESS", "LOGIN_FAILURE", "ACCOUNT_LOCKED", "LOGOUT", "PASSWORD_CHANGED", "TEMPORARY_PASSWORD_ISSUED",
  "SESSION_REVOKED", "ACCESS_DENIED", "ACCOUNT_UNLOCKED_BY_ADMIN", "ACCOUNT_BANNED", "ACCOUNT_UNBANNED",
  "ACCOUNT_DEACTIVATED", "ACCOUNT_REACTIVATED", "ROLE_CHANGED", "REJECTED_ADMIN_ACTION",
];

export const LOG_CLEANUP_ADDRESS = "/api/admin/logs/cleanup-status";

export type LogTab = "audit" | "security" | "scans";

export interface LogFilters {
  tab: string;
  from: string;
  to: string;
  actor: string;
  action: string;
  target: string;
  targetID: string;
  username: string;
  event: string;
  result: string;
  order: string;
  worker: string;
  page: string;
}

const tabs = {
  audit: { path: "audit", prefix: "adminSearchAuditLog" },
  security: { path: "security", prefix: "adminSearchSecurityLog" },
  scans: { path: "picking-scan", prefix: "adminSearchPickingScanHistory" },
};

export function logsAddress(tab: LogTab, filters: LogFilters, page?: number) {
  const { prefix, path } = tabs[tab];
  const fields = tab === "audit" ? {
    ActorUsername: filters.actor,
    Action: filters.action,
    TargetType: filters.target,
    TargetID: filters.targetID,
  } : tab === "security" ? {
    Username: filters.username,
    EventType: filters.event,
    Result: filters.result,
  } : {
    OrderReference: filters.order,
    WorkerUsername: filters.worker,
    Result: filters.result,
  };
  const params = Object.fromEntries(Object.entries(fields).map(([field, value]) => [prefix + field, value]));
  return adminAddress(`/api/admin/logs/${path}${page === undefined ? "/csv" : ""}`, {
    ...params,
    [prefix + "FromTime"]: filterDateTime(filters.from),
    [prefix + "ToTime"]: filterDateTime(filters.to, true),
    [prefix + "PageNumber"]: page,
  });
}

type LogPage<Prefix extends string, Row> =
  { [Key in `${Prefix}RowList`]: Row[] } &
  { [Key in `${Prefix}PageNumber` | `${Prefix}TotalPageCount` | `${Prefix}TotalRowCount`]: number };

export interface AuditRow {
  auditID: number;
  timestamp: string;
  adminSearchAuditLogActorUsername: string | null;
  action: string;
  targetType: string;
  targetID: string | null;
  oldValue: string | null;
  newValue: string | null;
}

export interface SecurityRow {
  securityLogID: number;
  timestamp: string;
  adminSearchSecurityLogUsername: string | null;
  eventType: string;
  result: string;
  ipAddress: string | null;
  deviceInfo: string | null;
  details: string | null;
}

export interface ScanRow {
  scanID: number;
  scanTime: string;
  orderID: number;
  adminSearchPickingScanHistoryOrderReference: string | null;
  adminSearchPickingScanHistoryWorkerUsername: string | null;
  scannedIdentifier: string | null;
  result: string;
  reason: string | null;
  originalScanID: number | null;
  adminSearchPickingScanHistoryProductSKU: string | null;
}

export type AuditPage = LogPage<"adminSearchAuditLog", AuditRow>;
export type SecurityPage = LogPage<"adminSearchSecurityLog", SecurityRow>;
export type ScanPage = LogPage<"adminSearchPickingScanHistory", ScanRow>;

export interface LogCleanup {
  adminViewLogCleanupStatusHasRun: boolean;
  adminViewLogCleanupStatusLastRunTime: string | null;
  adminViewLogCleanupStatusSecurityLogDeletedCount: number;
  adminViewLogCleanupStatusPickingScanHistoryDeletedCount: number;
  adminViewLogCleanupStatusAuditLogDeletedCount: number;
  adminViewLogCleanupStatusKeepRuleText: string;
}
