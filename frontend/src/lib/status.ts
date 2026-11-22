// Status words and their families (design system MASTER.md, Section 4).
// The same stored word always gets the same family on every page. An unknown word is "closed".

import { plainWord } from "./format";

/** The six status families of the old pages, plus "turn": waits for a decision of the person reading the page. */
export type Family = "turn" | "waiting" | "warehouse" | "road" | "done" | "problem" | "closed";

export interface StatusLook {
  label: string;
  family: Family;
}

const FAMILY_BY_WORD: Record<string, Family> = {
  // Order status
  AWAITING_PAYMENT: "waiting",
  READY_FOR_PICKING: "warehouse",
  PICKING: "warehouse",
  READY_FOR_PICKUP: "warehouse",
  SHIPPED: "road",
  OUT_FOR_DELIVERY: "road",
  DELIVERY_FAILED: "problem",
  READY_FOR_REDELIVERY: "warehouse",
  DELIVERED: "done",
  CANCELLED: "closed",
  // Accounts and listings
  ACTIVE: "done",
  BANNED: "problem",
  INACTIVE: "closed",
  LOCKED: "waiting",
  // Payments, refunds and the provider's answer
  PENDING: "waiting",
  ACCEPTED: "done",
  REQUESTED: "waiting",
  REFUNDED: "done",
  FAILED: "problem",
  UNKNOWN: "closed",
  // Shortage cases and picking
  OPEN: "turn",
  WAITING_RESTOCK: "waiting",
  RESOLVED: "done",
  IN_PROGRESS: "warehouse",
  PACKED: "done",
  // Packages and scans
  WAREHOUSE: "warehouse",
  DELIVERY: "road",
  REJECTED: "problem",
  REVERSED: "closed",
  // Client requests and logs
  APPROVED: "done",
  WITHDRAWN: "closed",
  SUCCESS: "done",
  FAILURE: "problem",
  // Failed delivery states (built by the page, not stored)
  WAITING_FOR_RETURN: "waiting",
  NEEDS_DECISION: "turn",
  WAITING_FOR_CLIENT: "waiting",
  // System health
  CONNECTED: "done",
  NOT_CONNECTED: "problem",
};

/** The badge of a stored word: its plain words and its family. */
export function statusLook(stored: string): StatusLook {
  return { label: plainWord(stored), family: FAMILY_BY_WORD[stored] ?? "closed" };
}

/** Account status, with the three exceptions of the word list. */
export function accountStatusLook(stored: string, lockMinutesLeft: number | null): StatusLook {
  if (lockMinutesLeft !== null && lockMinutesLeft > 0 && stored === "ACTIVE") {
    return { label: `Locked, ${lockMinutesLeft} min left`, family: "waiting" };
  }
  if (stored === "LOCKED") return { label: "Locked (old status)", family: "waiting" };
  if (stored === "INACTIVE") return { label: "Deactivated", family: "closed" };
  return statusLook(stored);
}

/** Order status options in route order, as the Orders filter lists them. */
export const ORDER_STATUSES = [
  "AWAITING_PAYMENT",
  "READY_FOR_PICKING",
  "PICKING",
  "READY_FOR_PICKUP",
  "SHIPPED",
  "OUT_FOR_DELIVERY",
  "DELIVERY_FAILED",
  "READY_FOR_REDELIVERY",
  "DELIVERED",
  "CANCELLED",
] as const;

export const ACTIVE_ORDER_STATUSES = ORDER_STATUSES.filter((s) => s !== "DELIVERED" && s !== "CANCELLED");

/** Role words as plain text (never a badge). */
export function roleLabel(role: string): string {
  switch (role) {
    case "CLIENT":
      return "Client";
    case "WAREHOUSE_WORKER":
      return "Warehouse Worker";
    case "DELIVERY_WORKER":
      return "Delivery Worker";
    case "ADMIN":
      return "Admin";
    default:
      return plainWord(role);
  }
}
