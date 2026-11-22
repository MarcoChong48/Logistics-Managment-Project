import { sendAdminMessage } from "./adminLists";
import type { AccountStatus, ProviderAnswer, RefundStatus, Role } from "./types";

const TEST_TOOLS = "/api/admin/temporary-test";
export const TEST_MODE_ADDRESS = `${TEST_TOOLS}/mode`;

export interface TestMode {
  adminTemporaryViewTestModeIsEnabled: boolean;
}

function run(path: string, body?: unknown) {
  return sendAdminMessage("POST", `${TEST_TOOLS}/${path}`, body);
}

function numericInput(value: string): number | null {
  const parsed = Number(value);
  return value.trim() && Number.isFinite(parsed) ? parsed : null;
}

export const createTestUser = (role: Role) => run("test-user", { adminTemporaryCreateTestUserRole: role });
export const endLock = (username: string) => run("end-lock", { adminTemporaryEndUserLockUsername: username });
export const expireTemporaryPassword = (username: string) => run("expire-temporary-password", { adminTemporaryExpireTemporaryPasswordUsername: username });
export const setUserStatus = (username: string, status: AccountStatus) => run("user-status", {
  adminTemporarySetUserStatusUsername: username,
  adminTemporarySetUserStatusAccountStatus: status,
});
export const createManyTestUsers = () => run("many-test-users");
export const createWorkerWithActiveWork = () => run("worker-with-active-work");
export const createPickingScanRows = () => run("picking-scan-rows");
export const createOldLogRows = () => run("old-log-rows");
export const runLogCleanupNow = () => run("run-log-cleanup");
export const createPrivateValueLogRows = () => run("private-value-log-rows");

export const setProductStock = (sku: string, physical: string, reserved: string) => run("product-stock", {
  adminTemporarySetProductStockSKU: sku,
  adminTemporarySetProductStockPhysicalStock: numericInput(physical),
  adminTemporarySetProductStockReservedStock: numericInput(reserved),
});
export const createSampleOrderSet = () => run("sample-order-set");
export const createShortageReport = (status: "AWAITING_PAYMENT" | "PICKING" | "READY_FOR_PICKUP", missing: string, orderReference: string) => run("shortage-report", {
  adminTemporaryCreateShortageReportOrderStatus: status,
  adminTemporaryCreateShortageReportMissingQuantity: numericInput(missing),
  adminTemporaryCreateShortageReportOrderReference: orderReference,
});
export const markShortageResolved = (id: string) => run("shortage-resolved", {
  adminTemporaryResolveShortageCaseShortageCaseID: numericInput(id),
});
export const createPickingOrder = (worker: string) => run("picking-order", { adminTemporaryCreatePickingOrderWorkerUsername: worker });
export const createFailedDeliveryCase = (failures: string, returned: boolean, days: string, request: "NONE" | "REDELIVER" | "CANCEL") => run("failed-delivery-case", {
  adminTemporaryCreateFailedDeliveryCaseFailureCount: numericInput(failures),
  adminTemporaryCreateFailedDeliveryCaseIsReturned: returned,
  adminTemporaryCreateFailedDeliveryCaseDaysSinceReturn: numericInput(days),
  adminTemporaryCreateFailedDeliveryCaseClientRequest: request,
});
export const createHeldPackage = (worker: string, status: "SHIPPED" | "OUT_FOR_DELIVERY") => run("held-package", {
  adminTemporaryCreateHeldPackageWorkerUsername: worker,
  adminTemporaryCreateHeldPackageOrderStatus: status,
});
export const createRefundCase = (status: RefundStatus, age: string) => run("refund-case", {
  adminTemporaryCreateRefundCaseRefundStatus: status,
  adminTemporaryCreateRefundCaseAgeHours: numericInput(age),
});
export const setProviderAnswer = (id: string, outcome: ProviderAnswer) => run("provider-answer", {
  adminTemporarySetProviderAnswerRefundID: numericInput(id),
  adminTemporarySetProviderAnswerOutcome: outcome,
});
export const runPrivacyCheck = () => run("privacy-check");
export const removeAllTestData = (confirmation: string) => run("remove-all-test-data", { adminTemporaryRemoveAllTestDataConfirmText: confirmation });
