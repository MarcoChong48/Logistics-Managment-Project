import { ApiError, getJson } from "./http";
import { apiPath } from "./liveWork";
import type { OrderStatus } from "./types";
import { ORDER_STATUSES } from "../lib/status";

interface WireDashboard {
  adminViewDashboardOrderStatusCountList: { orderStatus: OrderStatus; adminViewDashboardOrderCount: number }[];
  adminViewDashboardActiveOrderCount: number;
  adminViewDashboardLowStockProductCount: number;
  adminViewDashboardLowStockProductList: { productID: number; SKU: string; name: string; lowStockThreshold: number; adminViewDashboardLowStockProductAvailableStock: number }[];
  adminViewDashboardOpenShortageCaseCount: number;
  adminViewDashboardWaitingShortageCaseCount: number;
  adminViewDashboardPendingClientInstructionCount: number;
  adminViewDashboardWaitingClientCaseCount: number;
  adminViewDashboardFlaggedDeliveryCaseCount: number;
  adminViewDashboardUnresolvedRefundCount: number;
  adminViewDashboardLockedUserCount: number;
  adminViewDashboardOpenCaseTotalCount: number;
}

export async function loadDashboard() {
  const dto = await getJson<WireDashboard>("/api/admin/dashboard");
  const statusCounts = Object.fromEntries(ORDER_STATUSES.map((s) => [s, 0])) as Record<OrderStatus, number>;
  for (const row of dto.adminViewDashboardOrderStatusCountList) statusCounts[row.orderStatus] = row.adminViewDashboardOrderCount;
  return {
    statusCounts,
    active: dto.adminViewDashboardActiveOrderCount,
    lowStockCount: dto.adminViewDashboardLowStockProductCount,
    lowStock: dto.adminViewDashboardLowStockProductList.map((product) => ({
      product, available: product.adminViewDashboardLowStockProductAvailableStock,
    })),
    counters: { warehouse: dto.adminViewDashboardOpenShortageCaseCount, delivery: dto.adminViewDashboardPendingClientInstructionCount, refunds: dto.adminViewDashboardUnresolvedRefundCount },
    waitingRestock: dto.adminViewDashboardWaitingShortageCaseCount,
    waitingForClient: dto.adminViewDashboardWaitingClientCaseCount,
    flagged: dto.adminViewDashboardFlaggedDeliveryCaseCount,
    lockedUsers: dto.adminViewDashboardLockedUserCount,
    open: dto.adminViewDashboardOpenCaseTotalCount,
    loadedAt: Date.now(),
  };
}

const decisionActions = {
  SHORTAGE_WAIT_DECIDED: "Wait for restock",
  SHORTAGE_CANCEL_DECIDED: "Order cancelled after a shortage",
  REDELIVERY_APPROVED: "Redelivery approved",
  CANCELLATION_APPROVED: "Cancellation approved",
  CLIENT_INSTRUCTION_REJECTED: "Client request rejected",
  REFUND_RETRIED: "Refund retried",
} as const;

interface AuditPage {
  adminSearchAuditLogTotalRowCount: number;
  adminSearchAuditLogRowList: { auditID: number; timestamp: string; adminSearchAuditLogActorUsername: string | null }[];
}

async function readToday() {
  const midnight = new Date();
  midnight.setHours(0, 0, 0, 0);
  const pages = await Promise.all(Object.entries(decisionActions).map(async ([action, label]) => {
    const result = await getJson<AuditPage>(apiPath("/api/admin/logs/audit", { adminSearchAuditLogFromTime: midnight.toISOString(), adminSearchAuditLogAction: action }));
    return {
      count: result.adminSearchAuditLogTotalRowCount,
      rows: result.adminSearchAuditLogRowList.map((r) => ({
        id: r.auditID, time: Date.parse(r.timestamp), actor: r.adminSearchAuditLogActorUsername, label,
      })),
    };
  }));
  return {
    count: pages.reduce((sum, page) => sum + page.count, 0),
    rows: pages.flatMap((p) => p.rows).sort((a, b) => b.time - a.time || b.id - a.id).slice(0, 4),
  };
}

let todayRead: ReturnType<typeof readToday> | undefined;

export function loadToday() {
  todayRead ??= readToday().finally(() => { todayRead = undefined; });
  return todayRead;
}

export async function loadDashboardHealth() {
  try {
    const health = await getJson<{
      sharedCheckSystemHealthDatabaseIsConnected: boolean;
      sharedCheckSystemHealthTableCount: number;
      sharedCheckSystemHealthServerTime: string;
    }>("/api/shared/health");
    return {
      connected: health.sharedCheckSystemHealthDatabaseIsConnected,
      tables: health.sharedCheckSystemHealthTableCount,
      time: Date.parse(health.sharedCheckSystemHealthServerTime),
    };
  } catch (error) {
    if (error instanceof ApiError && error.status === 503) return { connected: false, tables: null, time: null };
    throw error;
  }
}
