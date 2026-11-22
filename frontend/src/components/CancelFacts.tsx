import { useCallback } from "react";
import { loadOrderDetail } from "../api/liveOrders";
import { useWorkQuery } from "../api/liveWork";
import { FactList } from "./ui";
import { formatTime } from "../lib/format";

export function CancelFacts({ orderID, isPaid, closesPendingRequest = true }: { orderID: number; isPaid: boolean; closesPendingRequest?: boolean }) {
  const detail = useWorkQuery(useCallback(() => closesPendingRequest ? loadOrderDetail(String(orderID)) : Promise.resolve(null), [orderID, closesPendingRequest]));
  const pending = detail.data?.instructions.find((i) => i.instructionStatus === "PENDING");
  return (
    <div className="space-y-3 rounded-control bg-sunken px-3.5 py-3">
      <FactList
        facts={[
          ["Payment", isPaid ? "Paid" : "Nothing paid"],
          ["Refund", isPaid ? "A full refund is requested unless one already exists." : "Nothing is refunded."],
          ["Outcome", "The whole order is cancelled for good. The client is told."],
        ]}
      />
      {pending && (
        <p className="text-small text-turn-fg">
          The client’s {pending.instructionType === "REDELIVER" ? "redelivery" : "cancellation"} request of {formatTime(pending.submittedTime)} is withdrawn.
        </p>
      )}
    </div>
  );
}
