// The small parts of the design system (MASTER.md Section 12): buttons, badges, flags, slips, messages,
// facts and progress. Every page is put together from these and the larger parts in this folder.

import { forwardRef, type ButtonHTMLAttributes, type ReactNode } from "react";
import { Link, type LinkProps } from "react-router";
import { AlertTriangle, CheckCircle2, Info, Loader2 } from "lucide-react";
import { cn } from "../lib/cn";
import { formatTime, isoAttribute } from "../lib/format";
import type { Family, StatusLook } from "../lib/status";
import { statusLook } from "../lib/status";

// ----- buttons ----------------------------------------------------------------------------------

export type ButtonLevel = "main" | "secondary" | "quiet" | "danger" | "danger-filled";

const LEVEL: Record<ButtonLevel, string> = {
  main: "bg-teal text-white hover:bg-teal-hover active:bg-teal-press border border-transparent",
  secondary: "bg-surface text-ink border border-field-line hover:bg-hover",
  quiet: "bg-transparent text-teal hover:bg-teal-tint hover:text-teal-hover border border-transparent",
  danger: "bg-surface text-danger border border-field-line hover:border-danger hover:bg-danger-tint",
  "danger-filled": "bg-danger text-white hover:bg-danger-hover border border-transparent",
};

export function buttonClass(level: ButtonLevel = "secondary", small = false, extra?: string): string {
  return cn(
    "inline-flex items-center justify-center gap-2 rounded-control font-semibold transition-colors duration-[120ms] ease-out",
    "active:translate-y-px disabled:translate-y-0 disabled:border-transparent disabled:bg-sand disabled:text-ink-muted",
    // a blocked button (aria-disabled) looks switched off but stays a Tab stop, so its reason can be read
    "aria-disabled:cursor-not-allowed aria-disabled:border-transparent aria-disabled:bg-sand aria-disabled:text-ink-muted aria-disabled:hover:border-transparent aria-disabled:hover:bg-sand aria-disabled:active:translate-y-0",
    small ? "min-h-[34px] px-3 text-label pointer-coarse:min-h-10" : "min-h-10 px-4 text-body pointer-coarse:min-h-11",
    level === "quiet" && (small ? "px-2" : "px-3"),
    LEVEL[level],
    extra,
  );
}

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  level?: ButtonLevel;
  small?: boolean;
  icon?: ReactNode;
  iconEnd?: ReactNode;
  busy?: boolean;
  busyText?: string;
  /** Looks switched off but stays reachable by keyboard, so its reason (aria-describedby) can be read. */
  blocked?: boolean;
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { level = "secondary", small = false, icon, iconEnd, busy = false, busyText, blocked = false, className, children, disabled, type = "button", onClick, ...rest },
  ref,
) {
  return (
    <button
      ref={ref}
      type={type}
      className={buttonClass(level, small, className)}
      disabled={disabled || busy}
      aria-disabled={blocked || undefined}
      aria-busy={busy || undefined}
      onClick={blocked ? (event) => event.preventDefault() : onClick}
      {...rest}
    >
      {busy ? <Loader2 aria-hidden="true" className="size-4 shrink-0 lms-spin" /> : icon}
      <span>{busy && busyText ? busyText : children}</span>
      {!busy && iconEnd}
    </button>
  );
});

export function ButtonLink({ level = "secondary", small = false, icon, className, children, ...rest }: LinkProps & { level?: ButtonLevel; small?: boolean; icon?: ReactNode }) {
  return (
    <Link className={buttonClass(level, small, className)} {...rest}>
      {icon}
      <span>{children}</span>
    </Link>
  );
}

export function TextLink({ className, ...rest }: LinkProps) {
  return (
    <Link
      className={cn(
        "whitespace-nowrap font-semibold text-teal underline decoration-teal/35 underline-offset-[3px] hover:text-teal-hover hover:decoration-teal-hover",
        // on touch screens the hit area grows to 40 px whatever the line height; the negative margin keeps the line in place
        "pointer-coarse:inline-block pointer-coarse:-my-[calc((40px_-_1lh)/2)] pointer-coarse:py-[calc((40px_-_1lh)/2)]",
        className,
      )}
      {...rest}
    />
  );
}

// ----- status badge, flag, counter ---------------------------------------------------------------

const FAMILY: Record<Family, { badge: string; dot: string; flag: string }> = {
  turn: { badge: "bg-turn-bg text-turn-fg", dot: "bg-turn-dot", flag: "text-turn-fg border-turn-dot" },
  waiting: { badge: "bg-waiting-bg text-waiting-fg", dot: "bg-waiting-dot", flag: "text-waiting-fg border-waiting-dot" },
  warehouse: { badge: "bg-warehouse-bg text-warehouse-fg", dot: "bg-warehouse-dot", flag: "text-warehouse-fg border-warehouse-dot" },
  road: { badge: "bg-road-bg text-road-fg", dot: "bg-road-dot", flag: "text-road-fg border-road-dot" },
  done: { badge: "bg-done-bg text-done-fg", dot: "bg-done-dot", flag: "text-done-fg border-done-dot" },
  problem: { badge: "bg-problem-bg text-problem-fg", dot: "bg-problem-dot", flag: "text-problem-fg border-problem-dot" },
  closed: { badge: "bg-closed-bg text-closed-fg", dot: "bg-closed-dot", flag: "text-closed-fg border-closed-dot" },
};

export const familyDot = (family: Family) => FAMILY[family].dot;

/** A status: a pale pill with a dot and its words. Pass a stored word (DELIVERY_FAILED) or a ready look. */
export function StatusBadge({ word, look }: { word?: string; look?: StatusLook }) {
  const resolved = look ?? statusLook(word ?? "UNKNOWN");
  return (
    <span className={cn("inline-flex h-6 shrink-0 items-center gap-1.5 whitespace-nowrap rounded-full px-2.5 text-small font-semibold", FAMILY[resolved.family].badge)}>
      <span aria-hidden="true" className={cn("size-[7px] shrink-0 rounded-full", FAMILY[resolved.family].dot)} />
      {resolved.label}
    </span>
  );
}

/** An extra fact (Low stock, Super admin, Test mode): a small framed tag without a dot. */
export function Flag({ family, children }: { family: Family; children: ReactNode }) {
  return (
    <span className={cn("inline-flex h-5 shrink-0 items-center whitespace-nowrap rounded-chip border bg-surface px-1.5 text-flag font-[650]", FAMILY[family].flag)}>{children}</span>
  );
}

export function CountPill({ children, className }: { children: ReactNode; className?: string }) {
  return <span className={cn("inline-flex h-6 min-w-6 items-center justify-center rounded-full bg-sunken px-2 text-small font-semibold tabular text-ink-muted", className)}>{children}</span>;
}

// ----- slips (cards) ----------------------------------------------------------------------------

export function Slip({ children, className, id, labelledBy, as: Tag = "section" }: { children: ReactNode; className?: string; id?: string; labelledBy?: string; as?: "section" | "div" }) {
  return (
    <Tag id={id} aria-labelledby={labelledBy} className={cn("min-w-0 rounded-card border border-line bg-surface", className)}>
      {children}
    </Tag>
  );
}

export function SlipHeader({ title, titleID, count, controls, level = 2 }: { title: ReactNode; titleID?: string; count?: ReactNode; controls?: ReactNode; level?: 2 | 3 }) {
  const Heading = level === 2 ? "h2" : "h3";
  return (
    <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-2 border-b border-line px-4 py-3.5 md:px-5">
      <div className="flex min-w-0 items-center gap-2.5">
        <Heading id={titleID} className="text-card-title font-[650] text-ink">
          {title}
        </Heading>
        {count !== undefined && <CountPill>{count}</CountPill>}
      </div>
      {controls && <div className="flex flex-wrap items-center gap-2">{controls}</div>}
    </div>
  );
}

export function SlipStrip({ children }: { children: ReactNode }) {
  return <div className="border-b border-line bg-sunken px-4 py-2.5 md:px-5">{children}</div>;
}

export function SlipBody({ children, className }: { children: ReactNode; className?: string }) {
  return <div className={cn("px-4 py-4 md:px-5", className)}>{children}</div>;
}

/** A framed part inside a slip, with a grey head that holds its sub-title and count. */
export function Part({ title, count, children, id }: { title: string; count?: number; children: ReactNode; id?: string }) {
  return (
    <div id={id} className="overflow-hidden rounded-[12px] border border-line">
      <div className="flex items-center gap-2 border-b border-line bg-sunken px-3.5 py-2">
        <h3 className="text-part-title font-[650]">{title}</h3>
        {count !== undefined && <CountPill className="bg-surface">{count}</CountPill>}
      </div>
      <div>{children}</div>
    </div>
  );
}

// ----- messages ---------------------------------------------------------------------------------

export type MessageKind = "success" | "error" | "information";

const MESSAGE_STYLE: Record<MessageKind, { box: string; icon: ReactNode }> = {
  success: { box: "bg-done-bg text-done-fg", icon: <CheckCircle2 aria-hidden="true" className="mt-px size-[18px] shrink-0" /> },
  error: { box: "bg-danger-tint text-problem-fg", icon: <AlertTriangle aria-hidden="true" className="mt-px size-[18px] shrink-0" /> },
  information: { box: "bg-teal-tint text-teal-hover", icon: <Info aria-hidden="true" className="mt-px size-[18px] shrink-0" /> },
};

export function InlineMessage({ kind, children, className }: { kind: MessageKind; children: ReactNode; className?: string }) {
  return (
    <div className={cn("flex items-start gap-2.5 rounded-control px-3.5 py-2.5 text-body", MESSAGE_STYLE[kind].box, className)}>
      {MESSAGE_STYLE[kind].icon}
      <div className="min-w-0 font-medium">{children}</div>
    </div>
  );
}

/** An always-present live region: messages put into it are read out by screen readers. */
export function MessagePlace({ id, message, className }: { id?: string; message: { kind: MessageKind; text: ReactNode } | null; className?: string }) {
  return (
    <div id={id} aria-live="polite" className={className}>
      {message && <InlineMessage kind={message.kind}>{message.text}</InlineMessage>}
    </div>
  );
}

/** A fixed rule shown as quiet text with an information icon. */
export function Note({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <p className={cn("flex items-start gap-2 text-small text-ink-muted", className)}>
      <Info aria-hidden="true" className="mt-px size-4 shrink-0" />
      <span>{children}</span>
    </p>
  );
}

// ----- facts, times, progress --------------------------------------------------------------------

export function FactList({ facts, columns = 1, className }: { facts: Array<[string, ReactNode]>; columns?: 1 | 2 | 3; className?: string }) {
  return (
    <dl
      className={cn(
        "grid gap-x-8 gap-y-3",
        columns === 2 && "sm:grid-cols-2",
        columns === 3 && "sm:grid-cols-2 lg:grid-cols-3",
        className,
      )}
    >
      {facts.map(([label, value]) => (
        <div key={label} className="min-w-0">
          <dt className="text-small text-ink-muted">{label}</dt>
          <dd className="mt-0.5 break-words text-body font-medium">{value}</dd>
        </div>
      ))}
    </dl>
  );
}

/** A time that never breaks across two lines, with its full value for machines. */
export function TimeText({ time, className }: { time: number | null | undefined; className?: string }) {
  if (time === null || time === undefined) return <Empty />;
  return (
    <time dateTime={isoAttribute(time)} className={cn("whitespace-nowrap tabular", className)}>
      {formatTime(time)}
    </time>
  );
}

export function Empty() {
  return (
    <span className="text-ink-muted">
      <span aria-hidden="true">-</span>
      <span className="sr-only">None</span>
    </span>
  );
}

export function ProgressBar({ value, max, labelID, tone = "apricot", className }: { value: number; max: number; labelID: string; tone?: "apricot" | "teal"; className?: string }) {
  const percent = max > 0 ? Math.min(100, Math.round((value / max) * 100)) : 0;
  return (
    <div role="progressbar" aria-labelledby={labelID} aria-valuemin={0} aria-valuemax={max} aria-valuenow={value} className={cn("h-2 w-full overflow-hidden rounded-full bg-track", className)}>
      <div
        className={cn("h-full w-full rounded-full transition-[clip-path] duration-[400ms] ease-out", tone === "apricot" ? "bg-apricot-fill" : "bg-teal")}
        style={{ clipPath: `inset(0 ${100 - percent}% 0 0 round 999px)` }}
      />
    </div>
  );
}

// ----- page header and empty states --------------------------------------------------------------

export function PageHeader({ title, count, actions, back, extra }: { title: ReactNode; count?: ReactNode; actions?: ReactNode; back?: ReactNode; extra?: ReactNode }) {
  return (
    <div className="mb-5 lg:mb-6">
      {back && <div className="mb-3">{back}</div>}
      <div className="flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
        <div className="min-w-0">
          <h1 className="flex flex-wrap items-center gap-x-3 gap-y-1.5 text-[24px] font-bold leading-8 tracking-[-0.01em] md:text-page-title">{title}</h1>
          {count && <p className="mt-1 text-body text-ink-muted">{count}</p>}
          {extra}
        </div>
        {actions && <div className="flex flex-wrap items-center gap-2 max-md:[&>*]:grow md:shrink-0">{actions}</div>}
      </div>
    </div>
  );
}

export function EmptyState({ icon, title, children, action }: { icon?: ReactNode; title: string; children?: ReactNode; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center gap-2 px-6 py-10 text-center">
      {icon && <div className="mb-1 text-ink-muted">{icon}</div>}
      <p className="text-body font-semibold text-ink">{title}</p>
      {children && <p className="max-w-[46ch] text-small text-ink-muted">{children}</p>}
      {action && <div className="mt-2">{action}</div>}
    </div>
  );
}

/** "All clear": a queue with nothing waiting. */
export function AllClear({ title, children, action }: { title: string; children: ReactNode; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center gap-2 px-6 py-10 text-center">
      <span className="mb-1 grid size-11 place-items-center rounded-full bg-done-bg text-done-fg">
        <CheckCircle2 aria-hidden="true" className="size-6" />
      </span>
      <p className="text-body font-semibold">{title}</p>
      <p className="max-w-[46ch] text-small text-ink-muted">{children}</p>
      {action && <div className="mt-2">{action}</div>}
    </div>
  );
}

export function VisuallyHidden({ children }: { children: ReactNode }) {
  return <span className="sr-only">{children}</span>;
}

/** Main text and sub-lines inside one table cell. */
export function CellMain({ children, className }: { children: ReactNode; className?: string }) {
  return <div className={cn("flex flex-wrap items-center gap-x-2 gap-y-1", className)}>{children}</div>;
}

export function CellSub({ children, className }: { children: ReactNode; className?: string }) {
  return <div className={cn("mt-1 text-small text-ink-muted [&>span]:align-middle [&>span:not(:first-child)]:ml-1.5", className)}>{children}</div>;
}
