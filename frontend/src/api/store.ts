// The in-memory database of the stand-in API. Pages never change it directly: they call the actions in
// actions.ts and testTools.ts, which change it and then call commit(), so every page shows the new state.
// Reloading the browser builds the sample world again.

import { useSyncExternalStore } from "react";
import type {
  AuditLogRow,
  ClientInstruction,
  DeliveryAttempt,
  DeliveryPackage,
  LogCleanupRun,
  Order,
  OrderEvent,
  OrderItem,
  Payment,
  PickingAssignment,
  PickingScanRow,
  PickingTask,
  Product,
  Refund,
  SecurityLogRow,
  Session,
  ShortageCase,
  User,
} from "./types";
import { createSampleWorld } from "./seed";

export interface PickupList {
  pickupListID: number;
  claimedBy: string;
  status: "CLAIMED" | "CLOSED";
}

export interface Database {
  users: User[];
  products: Product[];
  orders: Order[];
  orderItems: OrderItem[];
  payments: Payment[];
  refunds: Refund[];
  pickingTasks: PickingTask[];
  pickingAssignments: PickingAssignment[];
  packages: DeliveryPackage[];
  attempts: DeliveryAttempt[];
  instructions: ClientInstruction[];
  shortageCases: ShortageCase[];
  events: OrderEvent[];
  auditLog: AuditLogRow[];
  securityLog: SecurityLogRow[];
  pickingScans: PickingScanRow[];
  pickupLists: PickupList[];
  logCleanup: LogCleanupRun | null;
  session: Session | null;
  /** Why the last session ended (a login notice such as LOGGED_OUT), so every page that sends the person
   * to the login page shows the same notice. */
  exitNotice: string | null;
  testModeIsEnabled: boolean;
  nextID: Record<string, number>;
}

export const db: Database = createSampleWorld();

let version = 0;
const listeners = new Set<() => void>();

/** Tells every page that the data changed. */
export function commit(): void {
  version += 1;
  listeners.forEach((listener) => listener());
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

/** Re-renders the component after every change; use the number as a memo dependency. */
export function useDbVersion(): number {
  return useSyncExternalStore(subscribe, () => version, () => version);
}

/** The next free number of a kind of row, for example nextID("refund"). */
export function nextID(kind: string): number {
  const value = db.nextID[kind] ?? 1;
  db.nextID[kind] = value + 1;
  return value;
}

/** Waits like a network request would, so busy states can be seen. */
export function respond<T>(value: T, delayMs = 260): Promise<T> {
  return new Promise((resolve) => window.setTimeout(() => resolve(value), delayMs));
}
