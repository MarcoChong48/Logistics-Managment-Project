// The signature of the design (DESIGN_PLAN.md Section 2.3): order statuses drawn as stops on a route, the
// way a depot draws a delivery round. Used on the dashboard ("Orders on the route") and on the order page
// ("Journey"). Lines are decoration; the stops are an ordered list that a screen reader reads in order.

import type { ReactNode } from "react";
import { Link } from "react-router";
import { ArrowRight, Check, CornerDownRight, X } from "lucide-react";
import { cn } from "../lib/cn";
import { formatTime } from "../lib/format";
import { statusLook } from "../lib/status";
import type { OrderStatus } from "../api/types";
import type { JourneyStop } from "../api/queries";
import { familyDot } from "./ui";

const MAIN: OrderStatus[] = ["AWAITING_PAYMENT", "READY_FOR_PICKING", "PICKING", "READY_FOR_PICKUP", "SHIPPED", "OUT_FOR_DELIVERY", "DELIVERED"];
const BRANCH: OrderStatus[] = ["DELIVERY_FAILED", "READY_FOR_REDELIVERY"];

/** Orders stuck at a stop that wait for the Admin, and where to decide them. */
export interface StopNeed {
  count: number;
  to: string;
  what: string;
}

function Ring({ status, big = false }: { status: OrderStatus; big?: boolean }) {
  const family = statusLook(status).family;
  return (
    <span aria-hidden="true" className={cn("relative z-[1] grid shrink-0 place-items-center rounded-full bg-surface", big ? "size-5" : "size-4")}>
      <span className={cn("rounded-full", familyDot(family), big ? "size-5" : "size-4")} />
      <span className={cn("absolute rounded-full bg-surface", big ? "size-2" : "size-1.5")} />
    </span>
  );
}

function StopLink({ status, count, className, quiet = false, twoLines = false }: { status: OrderStatus; count: number; className?: string; quiet?: boolean; twoLines?: boolean }) {
  const look = statusLook(status);
  return (
    <Link
      to={`/admin/orders?status=${status}`}
      aria-label={`${look.label}: ${count} ${count === 1 ? "order" : "orders"}`}
      className={cn("group/stop block rounded-control px-1.5 py-1 hover:bg-hover pointer-coarse:py-2", className)}
    >
      {/* where some stop names wrap (768 to 1279 px), every name keeps two lines, so counts and links line up */}
      <span className={cn("block text-small text-ink-muted group-hover/stop:text-ink", twoLines && "md:max-xl:min-h-9")}>{look.label}</span>
      <span className={cn("block font-bold tabular group-hover/stop:text-teal-hover", quiet ? "text-[18px] leading-7 text-ink-muted" : "text-[22px] leading-7 text-ink")}>
        {count.toLocaleString("en")}
      </span>
    </Link>
  );
}

/** "3 need you": the apricot link from a stuck stop straight to the page where it is decided. */
function NeedLink({ need }: { need: StopNeed }) {
  return (
    <Link
      to={need.to}
      aria-label={`${need.count} ${need.what} need your decision`}
      className="inline-flex min-h-7 items-center gap-1 whitespace-nowrap rounded-full bg-turn-bg px-2.5 text-small font-semibold text-turn-fg hover:bg-[#f8ddc0] pointer-coarse:min-h-10"
    >
      {need.count} need you
      <ArrowRight aria-hidden="true" className="size-3.5" />
    </Link>
  );
}

/**
 * Dashboard: every active status as a stop with its count. Stops where orders wait for the Admin carry a
 * "need you" link to the work page. Failed deliveries branch off below "Out for delivery" and go out again.
 */
export function OrdersRoute({ counts, needs, aside }: { counts: Record<OrderStatus, number>; needs: Partial<Record<OrderStatus, StopNeed>>; aside?: ReactNode }) {
  const branch = (
    <ol aria-label="Failed deliveries" className="space-y-1">
      {BRANCH.map((status, index) => (
        <li key={status} className="flex items-start gap-2">
          <span aria-hidden="true" className="mt-2 flex shrink-0 items-center">
            <CornerDownRight className={cn("size-4", index === 0 ? "text-problem-dot" : "text-warehouse-dot")} />
          </span>
          <div className="min-w-0">
            <StopLink status={status} count={counts[status]} className="text-left" />
            {needs[status] && <NeedLink need={needs[status]!} />}
            {status === "READY_FOR_REDELIVERY" && <p className="px-1.5 text-small text-ink-muted">then out for delivery again</p>}
          </div>
        </li>
      ))}
    </ol>
  );

  return (
    <div>
      {/* From 768 px: one line of seven stops; the failed branch hangs below "Out for delivery" */}
      <div className="relative hidden md:block">
        <span aria-hidden="true" className="absolute left-[calc(100%/14)] right-[calc(100%/14)] top-[7px] h-0.5 rounded-full bg-line-strong" />
        <ol className="relative grid grid-cols-7">
          {MAIN.map((status) => (
            <li key={status} className="flex flex-col items-center text-center">
              {/* every ring sits in the same 16 px band, so the labels under the stops share one line */}
              <span className="flex h-4 items-center">
                <Ring status={status} big={status === "DELIVERED"} />
              </span>
              <StopLink status={status} count={counts[status]} className="mt-2" quiet={status === "DELIVERED"} twoLines />
              {needs[status] && <NeedLink need={needs[status]!} />}
            </li>
          ))}
        </ol>
        <div className="grid grid-cols-7">
          {aside && <div className="col-span-5 flex items-end pb-2 pr-6">{aside}</div>}
          <div className="col-span-2 col-start-6">
            <div className="ml-[25%] mt-1 border-l-2 border-problem-dot/60 py-1 pl-2">{branch}</div>
          </div>
        </div>
      </div>

      {/* Phones: the same stops from top to bottom, the line down the left edge */}
      <ol className="relative md:hidden">
        <span aria-hidden="true" className="absolute bottom-6 left-[7px] top-6 w-0.5 bg-line-strong" />
        {MAIN.map((status) => (
          <li key={status}>
            <div className="flex items-center gap-3 py-1">
              <Ring status={status} big={status === "DELIVERED"} />
              <div className="flex min-w-0 flex-1 flex-wrap items-center justify-between gap-x-3 gap-y-1">
                <StopLink status={status} count={counts[status]} quiet={status === "DELIVERED"} className="flex flex-1 items-baseline justify-between gap-3 [&>span:last-child]:text-[20px]" />
                {needs[status] && <NeedLink need={needs[status]!} />}
              </div>
            </div>
            {status === "OUT_FOR_DELIVERY" && <div className="ml-[7px] border-l-2 border-problem-dot/60 py-1 pl-3">{branch}</div>}
          </li>
        ))}
      </ol>
      {aside && <div className="mt-4 md:hidden">{aside}</div>}
    </div>
  );
}

function JourneyMark({ stop }: { stop: JourneyStop }) {
  const base = "relative z-[1] grid size-6 shrink-0 place-items-center rounded-full border-2";
  switch (stop.state) {
    case "done":
      return (
        <span aria-hidden="true" className={cn(base, "border-teal bg-teal text-white")}>
          <Check className="size-3.5" strokeWidth={3} />
        </span>
      );
    case "current":
      return (
        <span aria-hidden="true" className={cn(base, "border-apricot-fill bg-apricot-tint")}>
          <span className="size-2.5 rounded-full bg-apricot-fill" />
        </span>
      );
    case "problem":
      return (
        <span aria-hidden="true" className={cn(base, "border-danger bg-danger-tint text-danger")}>
          <X className="size-3.5" strokeWidth={3} />
        </span>
      );
    case "ended":
      return (
        <span aria-hidden="true" className={cn(base, "border-closed-dot bg-closed-bg text-closed-fg")}>
          <X className="size-3.5" strokeWidth={3} />
        </span>
      );
    default:
      return <span aria-hidden="true" className={cn(base, "border-line-strong bg-surface")} />;
  }
}

const STATE_WORDS: Record<JourneyStop["state"], string> = {
  done: "done",
  current: "now",
  problem: "problem",
  upcoming: "not reached yet",
  ended: "ended here",
};

/** Order page: the seven stages of this order, where it is now and what went wrong on the way. */
export function JourneyRoute({ stops, now, decide }: { stops: JourneyStop[]; now: string; decide?: { to: string; label: string } | null }) {
  const segment = (index: number) => {
    const next = stops[index + 1];
    const reached = stops[index].state === "done" && next && next.state !== "upcoming";
    return reached ? "bg-teal" : "bg-[repeating-linear-gradient(90deg,var(--color-line-strong)_0_6px,transparent_6px_11px)]";
  };
  return (
    <div>
      <ol className="relative hidden grid-cols-7 md:grid">
        {stops.map((stop, index) => (
          <li key={stop.key} className="relative flex flex-col items-center px-1 text-center">
            {index < stops.length - 1 && <span aria-hidden="true" className={cn("absolute left-1/2 top-[11px] h-0.5 w-full", segment(index))} />}
            <JourneyMark stop={stop} />
            <span className="sr-only">{`${stop.label}, ${STATE_WORDS[stop.state]}`}</span>
            <span aria-hidden="true" className={cn("mt-2 text-label font-semibold", stop.state === "upcoming" ? "text-ink-muted" : "text-ink", stop.state === "problem" && "text-danger")}>
              {stop.label}
            </span>
            {stop.time !== null && <span className="text-small tabular text-ink-muted">{formatTime(stop.time)}</span>}
            {stop.note && <span className={cn("mt-0.5 max-w-[16ch] text-small", stop.state === "problem" ? "font-semibold text-danger" : "text-terracotta")}>{stop.note}</span>}
          </li>
        ))}
      </ol>
      <ol className="relative md:hidden">
        {stops.map((stop, index) => (
          <li key={stop.key} className="relative flex gap-3 pb-4 last:pb-0">
            {index < stops.length - 1 && (
              <span aria-hidden="true" className={cn("absolute left-[11px] top-6 h-[calc(100%-24px)] w-0.5", stops[index].state === "done" && stops[index + 1].state !== "upcoming" ? "bg-teal" : "bg-line-strong")} />
            )}
            <JourneyMark stop={stop} />
            <div className="min-w-0 pt-0.5">
              <p className={cn("text-label font-semibold", stop.state === "upcoming" && "text-ink-muted", stop.state === "problem" && "text-danger")}>
                {stop.label}
                <span className="sr-only">{`, ${STATE_WORDS[stop.state]}`}</span>
              </p>
              {stop.time !== null && <p className="text-small tabular text-ink-muted">{formatTime(stop.time)}</p>}
              {stop.note && <p className={cn("text-small", stop.state === "problem" ? "font-semibold text-danger" : "text-terracotta")}>{stop.note}</p>}
            </div>
          </li>
        ))}
      </ol>
      <p className="mt-5 rounded-control bg-sunken px-3.5 py-2.5 text-body">
        <span className="font-semibold">Now: </span>
        {now}
        {decide && (
          <>
            {" "}
            <Link to={decide.to} className="whitespace-nowrap font-semibold text-teal underline decoration-teal/35 underline-offset-[3px] hover:text-teal-hover">
              {decide.label}
            </Link>
          </>
        )}
      </p>
    </div>
  );
}
