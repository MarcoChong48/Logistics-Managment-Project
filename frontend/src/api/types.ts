// The records of the stand-in API. Field names follow the Admin plan and the database draft
// (userID, orderReference, accountStatus, lowStockThreshold ...), so a real backend can replace it later.
// Times are numbers (milliseconds, browser time).

export type Role = "CLIENT" | "WAREHOUSE_WORKER" | "DELIVERY_WORKER" | "ADMIN";
export type AccountStatus = "ACTIVE" | "INACTIVE" | "BANNED" | "LOCKED";

export interface DeliveryWorkerProfile {
  employeeNumber: string;
  vehicleType: string;
  vehiclePlate: string;
  equipmentNote: string;
}

export interface User {
  userID: number;
  username: string;
  role: Role;
  accountStatus: AccountStatus;
  createdTime: number;
  lastLoginTime: number | null;
  failedLoginCount: number;
  lockUntil: number | null;
  /** Sample only: the stand-in keeps plain passwords. A real backend keeps only hashes. */
  password: string;
  temporaryPassword: string | null;
  temporaryPasswordExpiryTime: number | null;
  mustChangePassword: boolean;
  isTestAccount: boolean;
  deliveryWorkerProfile: DeliveryWorkerProfile | null;
}

export type ListingStatus = "ACTIVE" | "INACTIVE";

export interface Product {
  productID: number;
  SKU: string;
  name: string;
  currentPrice: number;
  physicalStock: number;
  reservedStock: number;
  lowStockThreshold: number;
  listingStatus: ListingStatus;
  createdTime: number;
  isTest: boolean;
}

export type OrderStatus =
  | "AWAITING_PAYMENT"
  | "READY_FOR_PICKING"
  | "PICKING"
  | "READY_FOR_PICKUP"
  | "SHIPPED"
  | "OUT_FOR_DELIVERY"
  | "DELIVERY_FAILED"
  | "READY_FOR_REDELIVERY"
  | "DELIVERED"
  | "CANCELLED";

export interface Order {
  orderID: number;
  orderReference: string;
  clientUserID: number;
  orderStatus: OrderStatus;
  createdTime: number;
  paymentDeadline: number | null;
  totalAmount: number;
  deliveryDetailsVersionCount: number;
  isTest: boolean;
}

export interface OrderItem {
  orderItemID: number;
  orderID: number;
  productID: number;
  quantity: number;
  pickedQuantity: number;
  unitPrice: number;
}

export interface Payment {
  paymentID: number;
  orderID: number;
  amount: number;
  paymentStatus: "PENDING" | "ACCEPTED" | "FAILED";
  createdTime: number;
  endedTime: number | null;
}

export type RefundStatus = "REQUESTED" | "PENDING" | "REFUNDED" | "FAILED";
export type ProviderAnswer = "REFUNDED" | "FAILED" | "PENDING" | "UNKNOWN";

export interface Refund {
  refundID: number;
  paymentID: number;
  orderID: number;
  amount: number;
  refundStatus: RefundStatus;
  createdTime: number;
  completedTime: number | null;
  requestedBy: string;
  lastCheckTime: number | null;
  lastProviderAnswer: ProviderAnswer | null;
  /** What the stand-in payment provider answers when it is asked next. */
  providerAnswer: ProviderAnswer;
  replacedByRefundID: number | null;
}

export interface PickingTask {
  pickingTaskID: number;
  orderID: number;
  taskStatus: "IN_PROGRESS" | "PACKED" | "CANCELLED";
  startedTime: number | null;
  packedTime: number | null;
}

export interface PickingAssignment {
  assignmentID: number;
  pickingTaskID: number;
  workerUsername: string;
  assignedTime: number;
  endedTime: number | null;
  assignedBy: string | null;
  reason: string | null;
}

export interface DeliveryPackage {
  packageID: number;
  packageCode: string;
  orderID: number;
  location: "WAREHOUSE" | "DELIVERY";
  heldBy: string | null;
  responsibleWorker: string | null;
  lastReassignedTime: number | null;
  lastReassignedBy: string | null;
  returnedTime: number | null;
  lastReminderTime: number | null;
}

export interface DeliveryAttempt {
  attemptID: number;
  orderID: number;
  workerUsername: string;
  startedTime: number;
  /** Empty while the attempt is still on the way. */
  endedTime: number | null;
  outcome: "DELIVERED" | "FAILED" | null;
  reason: string | null;
  note: string | null;
}

export type InstructionType = "REDELIVER" | "CANCEL";
export type InstructionStatus = "PENDING" | "APPROVED" | "REJECTED" | "WITHDRAWN";

export interface ClientInstruction {
  instructionID: number;
  orderID: number;
  instructionType: InstructionType;
  instructionStatus: InstructionStatus;
  source: "TRACKING_PAGE" | "ADMIN_MANUAL";
  submittedTime: number;
  recordedBy: string | null;
  identityCheckMethod: string | null;
  decidedBy: string | null;
  decidedTime: number | null;
  note: string | null;
}

export type ShortageStatus = "OPEN" | "WAITING_RESTOCK" | "CANCELLED" | "RESOLVED";

export interface ShortageCase {
  shortageCaseID: number;
  orderID: number;
  productID: number;
  missingQuantity: number;
  orderedQuantity: number;
  caseStatus: ShortageStatus;
  reportedTime: number;
  reportedBy: string;
  note: string | null;
  decision: "WAIT_FOR_RESTOCK" | "CANCEL_AND_REFUND" | "CANCEL_ORDER" | null;
  decidedBy: string | null;
  decidedTime: number | null;
  decisionReason: string | null;
  refundNote: string | null;
}

export interface OrderEvent {
  eventID: number;
  orderID: number;
  time: number;
  text: string;
  actor: string | null;
}

export type AuditTarget =
  | "USER"
  | "PRODUCT"
  | "ORDER"
  | "PICKING_TASK"
  | "PACKAGE"
  | "SHORTAGE_CASE"
  | "CLIENT_INSTRUCTION"
  | "REFUND"
  | "LOG"
  | "SYSTEM";

export interface AuditLogRow {
  auditLogID: number;
  time: number;
  actorUsername: string;
  action: string;
  targetType: AuditTarget;
  targetID: string;
  oldValue: string | null;
  newValue: string | null;
  isTest: boolean;
}

export interface SecurityLogRow {
  securityLogID: number;
  time: number;
  username: string;
  eventType: string;
  result: "SUCCESS" | "FAILURE" | "REJECTED";
  ipAddress: string;
  details: string | null;
  userAgent: string;
  isTest: boolean;
}

export interface PickingScanRow {
  scanID: number;
  time: number;
  orderID: number;
  workerUsername: string;
  scannedText: string;
  productSKU: string | null;
  result: "ACCEPTED" | "REJECTED" | "REVERSED";
  reason: string | null;
}

export interface LogCleanupRun {
  time: number;
  securityLogDeletedCount: number;
  pickingScanHistoryDeletedCount: number;
  auditLogDeletedCount: number;
}

export interface Session {
  userID: number;
  username: string;
  role: Role;
  mustChangePassword: boolean;
  loginTime: number;
}

/** Every answer of an action: a message for the person, and whether it worked. */
export interface ActionResult<T = undefined> {
  ok: boolean;
  message: string;
  data?: T;
  /** Set when the problem belongs to one field of a form. */
  field?: string;
}
