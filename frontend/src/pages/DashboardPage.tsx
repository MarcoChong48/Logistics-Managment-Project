import { type ReactNode } from "react";
import { Link } from "react-router";
import { PackageSearch, RefreshCw, ReceiptText, Truck } from "lucide-react";
import { loadDashboard, loadDashboardHealth, loadToday } from "../api/liveDashboard";
import { useWorkQuery } from "../api/liveWork";
import { useLiveSession } from "../api/session";
import { WorkQueryNotice } from "../components/WorkQueryNotice";
import type { OrderStatus } from "../api/types";
import {
  Button,
  ButtonLink,
  Flag,
  PageHeader,
  ProgressBar,
  Slip,
  SlipBody,
  SlipHeader,
  StatusBadge,
  TextLink,
  TimeText,
} from "../components/ui";
import { OrdersRoute, type StopNeed } from "../components/Route";
import { formatClock, plural } from "../lib/format";
import { cn } from "../lib/cn";

function QueueRow({ icon, title, line, count, to, openLabel }: { icon: ReactNode; title: string; line: ReactNode; count: number; to: string; openLabel: string }) {
  return (
    <li className="flex items-center gap-4 px-4 py-4 md:px-5">
      <span aria-hidden="true" className={cn("hidden size-10 shrink-0 place-items-center rounded-control sm:grid", count > 0 ? "bg-apricot-tint text-terracotta" : "bg-done-bg text-done-fg")}>
        {icon}
      </span>
      <div className="min-w-0 flex-1">
        <p className="font-semibold">{title}</p>
        <p className="text-small text-ink-muted">{count > 0 ? line : "Nothing waits here."}</p>
      </div>
      <span className={cn("w-10 text-right text-[28px] font-bold leading-8 tabular", count === 0 && "text-ink-muted")}>{count}</span>
      <ButtonLink small to={to} aria-label={openLabel} level={count > 0 ? "secondary" : "quiet"}>
        Open
      </ButtonLink>
    </li>
  );
}

export function DashboardPage() {
  const dashboard = useWorkQuery(loadDashboard);
  const health = useWorkQuery(loadDashboardHealth);
  const today = useWorkQuery(loadToday);
  const session = useLiveSession();
  const summary = dashboard.data;
  const refresh = () => {
    dashboard.refresh();
    today.refresh();
    health.refresh();
  };
  const healthPanel = (
    <Slip labelledBy="dashboardHealthTitle">
      <SlipHeader title="System health" titleID="dashboardHealthTitle" />
      <WorkQueryNotice {...health} />
      {health.data && (
        <SlipBody>
          <dl className="grid grid-cols-[auto_1fr] items-center gap-x-6 gap-y-2.5">
            <dt className="text-small text-ink-muted">Database</dt>
            <dd>{health.data.connected ? <StatusBadge word="CONNECTED" /> : <Flag family="problem">Disconnected</Flag>}</dd>
            <dt className="text-small text-ink-muted">Tables</dt>
            <dd className="font-medium tabular">{health.data.tables ?? "Unavailable"}</dd>
            <dt className="text-small text-ink-muted">Server time</dt>
            <dd className="font-medium"><TimeText time={health.data.time} /></dd>
            <dt className="text-small text-ink-muted">Mode</dt>
            <dd>{session?.testModeIsEnabled ? <Flag family="waiting">Test mode</Flag> : "Normal"}</dd>
          </dl>
        </SlipBody>
      )}
    </Slip>
  );
  if (!summary) {
    return (
      <>
        <PageHeader title="Dashboard" actions={<Button onClick={refresh} small busy={dashboard.pending}>Refresh</Button>} />
        <Slip className="mb-5"><WorkQueryNotice {...dashboard} /></Slip>
        {healthPanel}
      </>
    );
  }
  const { counters, statusCounts } = summary;
  const decided = today.data?.count;
  const recent = today.data?.rows ?? [];
  const total = (decided ?? 0) + summary.open;
  const needs: Partial<Record<OrderStatus, StopNeed>> = counters.delivery
    ? { DELIVERY_FAILED: { count: counters.delivery, to: "/admin/delivery-cases?show=NEEDS_DECISION", what: "failed deliveries" } }
    : {};
  const countLine = summary.open === 0 ? "Nothing waits for a decision. All clear." : `${plural(summary.open, "case waits", "cases wait")} for a decision.`;

  return (
    <>
      <PageHeader
        title="Dashboard"
        count={countLine}
        actions={
          <div className="flex items-center gap-3 max-md:!grow-0">
            <span className="text-small text-ink-muted">
              Last loaded <span className="tabular">{formatClock(summary.loadedAt)}</span>
            </span>
            <Button small icon={<RefreshCw aria-hidden="true" className="size-4" />} onClick={refresh} busy={dashboard.pending}>
              Refresh
            </Button>
          </div>
        }
      />

      <WorkQueryNotice {...dashboard} />
      <div className="grid items-start gap-5 lg:grid-cols-[minmax(0,1.5fr)_minmax(0,1fr)]">
        <Slip labelledBy="dashboardWorkTitle" className="flex flex-col">
          <SlipHeader title="Work to do" titleID="dashboardWorkTitle" />
          {summary.open === 0 && (
            <p className="flex items-center gap-2 border-b border-line bg-done-bg/60 px-4 py-2.5 text-label font-semibold text-done-fg md:px-5">All clear. Nothing waits for a decision.</p>
          )}
          <ul className="divide-y divide-line">
            <QueueRow
              icon={<PackageSearch className="size-5" />}
              title="Shortage cases"
              line="Missing or damaged stock found while picking"
              count={counters.warehouse}
              to="/admin/warehouse-cases?caseStatus=OPEN"
              openLabel="Open shortage cases"
            />
            <QueueRow
              icon={<Truck className="size-5" />}
              title="Client requests"
              line="Redelivery or cancellation after a failed delivery"
              count={counters.delivery}
              to="/admin/delivery-cases?show=NEEDS_DECISION"
              openLabel="Open client requests"
            />
            <QueueRow
              icon={<ReceiptText className="size-5" />}
              title="Refunds not finished"
              line="Failed refunds or refunds still awaiting the payment provider"
              count={counters.refunds}
              to="/admin/refunds"
              openLabel="Open refunds that are not finished"
            />
          </ul>
          <div className="rounded-b-card border-t border-line bg-sunken/60 px-4 py-3.5 md:px-5">
            <h3 className="text-small font-semibold text-ink-muted">Also check</h3>
            <ul className="mt-2 grid gap-x-6 gap-y-1 sm:grid-cols-2">
              {[
                ["Shortage cases waiting for restock", summary.waitingRestock, "/admin/warehouse-cases?caseStatus=WAITING_RESTOCK"],
                ["Failed deliveries waiting for the client", summary.waitingForClient, "/admin/delivery-cases?show=NO_CLIENT_REQUEST"],
                ["Flagged delivery cases", summary.flagged, "/admin/delivery-cases?show=FLAGGED"],
                ["Locked users", summary.lockedUsers, "/admin/users?status=LOCKED_NOW"],
              ].map(([label, count, to]) => (
                <li key={label as string}>
                  <Link to={to as string} className="flex min-h-9 items-center justify-between gap-3 rounded-control px-2 -mx-2 hover:bg-hover pointer-coarse:min-h-11">
                    <span className="text-label">{label}</span>
                    <span className="text-label font-bold tabular">{count}</span>
                  </Link>
                </li>
              ))}
            </ul>
          </div>
        </Slip>

        <div className="grid content-start gap-5">
          <section aria-labelledby="dashboardTodayTitle" className="rounded-card border border-[#f3d7b8] bg-apricot-tint px-4 py-4 md:px-5">
            <h2 id="dashboardTodayTitle" className="text-card-title font-[650]">
              Today
            </h2>
            <WorkQueryNotice {...today} />
            {decided !== undefined && (
              <>
                <p className="mt-2 flex items-baseline gap-2">
                  <span className="text-big-number font-bold tabular">{decided}</span>
                  <span className="text-body font-semibold">decided</span>
                </p>
                <p id="dashboardTodayProgress" className="mt-1 text-small text-ink-muted">
                  {total === 0 ? "No cases today yet." : `${decided} of ${plural(total, "case")} decided, ${summary.open} still open.`}
                </p>
                <ProgressBar value={decided} max={Math.max(total, 1)} labelID="dashboardTodayProgress" className="mt-3 bg-[#f6dcbf]" />
              </>
            )}
            {recent.length > 0 && (
              <ul className="mt-4 space-y-1.5 border-t border-[#f3d7b8] pt-3">
                {recent.map((row) => (
                  <li key={row.id} className="flex gap-3 text-small">
                    <span className="w-11 shrink-0 font-semibold tabular text-terracotta">{formatClock(row.time)}</span>
                    <span className="min-w-0">
                      {row.label}
                      <span className="text-ink-muted">{row.actor ? `, ${row.actor}` : null}</span>
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </section>

          {healthPanel}
        </div>
      </div>

      <Slip labelledBy="dashboardRouteTitle" className="mt-5">
        <SlipHeader
          title="Orders on the route"
          titleID="dashboardRouteTitle"
          count={`${summary.active} active`}
          controls={
            <ButtonLink small level="quiet" className="-mx-2" to="/admin/orders">
              Open all orders
            </ButtonLink>
          }
        />
        <SlipBody className="pb-5 pt-6">
          <OrdersRoute
            counts={statusCounts}
            needs={needs}
            aside={
              <p className="flex flex-wrap items-center gap-x-2 gap-y-1 text-small text-ink-muted">
                <span>
                  <TextLink to="/admin/orders?status=CANCELLED">{plural(statusCounts.CANCELLED, "cancelled order")}</TextLink> left the route.
                </span>
                {counters.refunds > 0 && (
                  <Link
                    to="/admin/refunds"
                    className="inline-flex min-h-7 items-center rounded-full bg-turn-bg px-2.5 font-semibold text-turn-fg hover:bg-[#f8ddc0] pointer-coarse:min-h-10"
                  >
                    {plural(counters.refunds, "refund")} not finished
                  </Link>
                )}
              </p>
            }
          />
        </SlipBody>
      </Slip>

      <Slip labelledBy="dashboardLowTitle" className="mt-5">
        <SlipHeader
          title="Low stock"
          titleID="dashboardLowTitle"
          count={summary.lowStockCount}
          controls={
            <ButtonLink small level="quiet" className="-mx-2" to="/admin/products?low=true">
              Show all low-stock products
            </ButtonLink>
          }
        />
        {summary.lowStock.length === 0 ? (
          <p className="px-4 py-5 text-ink-muted md:px-5">Every product has at least its low-stock level available.</p>
        ) : (
          <ul className="grid divide-y divide-line xl:grid-cols-3 xl:divide-x xl:divide-y-0">
            {summary.lowStock.map((row) => (
              <li key={row.product.productID}>
                <Link to={`/admin/products?q=${encodeURIComponent(row.product.SKU)}`} className="flex h-full items-center justify-between gap-4 px-4 py-3.5 hover:bg-hover md:px-5">
                  <span className="min-w-0">
                    <span className="block whitespace-nowrap font-semibold tabular">{row.product.SKU}</span>
                    <span className="block text-small text-ink-muted">{row.product.name}</span>
                  </span>
                  <span className="shrink-0 text-right">
                    <span className="block text-[22px] font-bold leading-7 tabular">{row.available}</span>
                    <span className="block text-small text-ink-muted">available, low below {row.product.lowStockThreshold}</span>
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </Slip>
    </>
  );
}
