import { useEffect, useState } from "react";
import { useLocation } from "react-router";
import { getJson, subscribeToApiResponses } from "./http";
import { loadToday } from "./liveDashboard";

interface MenuCounterResponse {
  adminViewMenuCounterOpenShortageCaseCount: number;
  adminViewMenuCounterPendingClientInstructionCount: number;
  adminViewMenuCounterUnresolvedRefundCount: number;
}

interface MenuSummary {
  counters: { warehouse: number; delivery: number; refunds: number };
  open: number;
  decidedToday?: number;
}

export function useMenuCounters(): MenuSummary | null {
  const { pathname } = useLocation();
  const [summary, setSummary] = useState<MenuSummary | null>(null);

  useEffect(() => {
    let requestNumber = 0;
    let current = true;

    async function refresh() {
      const request = ++requestNumber;
      const decisions = loadToday().then((today) => today.count).catch(() => undefined);
      try {
        const result = await getJson<MenuCounterResponse>("/api/admin/menu-counters");
        const counters = {
          warehouse: result.adminViewMenuCounterOpenShortageCaseCount,
          delivery: result.adminViewMenuCounterPendingClientInstructionCount,
          refunds: result.adminViewMenuCounterUnresolvedRefundCount,
        };
        const decidedToday = await decisions;
        if (current && request === requestNumber) {
          setSummary({
            counters,
            open: counters.warehouse + counters.delivery + counters.refunds,
            decidedToday,
          });
        }
      } catch {
        if (current && request === requestNumber) setSummary(null);
      }
    }

    void refresh();
    const unsubscribe = subscribeToApiResponses(({ method, path, status }) => {
      if (!path.startsWith("/api/admin/")) return;
      const refundConflict = status === 409 && path.startsWith("/api/admin/refunds/");
      if (!refundConflict && (status < 200 || status >= 300)) return;
      if (method !== "GET" || path === "/api/admin/dashboard") void refresh();
    });
    return () => { current = false; unsubscribe(); };
  }, [pathname]);

  return summary;
}
