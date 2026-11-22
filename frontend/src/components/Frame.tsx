// The frame of every Admin page: the evergreen side menu (fixed from 1024 px, a drawer below), the top bar
// with the session timer, the "Today" block, and the window "Session ending soon".

import { useEffect, useRef, useState, type ReactNode } from "react";
import { NavLink, Outlet, useLocation } from "react-router";
import {
  ClipboardList,
  Clock,
  FlaskConical,
  LayoutDashboard,
  LogOut,
  Menu,
  Package,
  ReceiptText,
  ScrollText,
  Truck,
  Users,
  Warehouse,
  X,
} from "lucide-react";
import { useMenuCounters } from "../api/menuCounters";
import { CHANGE_PASSWORD_PAGE, expireSession, extendSession, logout, useLiveSession, useSessionDeadline } from "../api/session";
import { Button, Flag, MessagePlace } from "./ui";
import { Dialog } from "./Dialog";
import { useToast } from "./Toast";
import { cn } from "../lib/cn";
import { useDesktopFrame } from "../lib/useMediaQuery";

interface Entry {
  to: string;
  label: string;
  icon: ReactNode;
  counter?: "warehouse" | "delivery" | "refunds";
}

const ICON = "size-[18px] shrink-0";
const GROUPS: Array<{ label: string | null; entries: Entry[] }> = [
  { label: null, entries: [{ to: "/admin/dashboard", label: "Dashboard", icon: <LayoutDashboard aria-hidden="true" className={ICON} /> }] },
  {
    label: "Work to do",
    entries: [
      { to: "/admin/warehouse-cases", label: "Warehouse cases", icon: <Warehouse aria-hidden="true" className={ICON} />, counter: "warehouse" },
      { to: "/admin/delivery-cases", label: "Delivery cases", icon: <Truck aria-hidden="true" className={ICON} />, counter: "delivery" },
      { to: "/admin/refunds", label: "Refunds", icon: <ReceiptText aria-hidden="true" className={ICON} />, counter: "refunds" },
    ],
  },
  {
    label: "Manage",
    entries: [
      { to: "/admin/users", label: "Users", icon: <Users aria-hidden="true" className={ICON} /> },
      { to: "/admin/products", label: "Products", icon: <Package aria-hidden="true" className={ICON} /> },
    ],
  },
  {
    label: "Look up",
    entries: [
      { to: "/admin/orders", label: "Orders", icon: <ClipboardList aria-hidden="true" className={ICON} /> },
      { to: "/admin/logs", label: "Logs", icon: <ScrollText aria-hidden="true" className={ICON} /> },
    ],
  },
  { label: "Testing", entries: [{ to: "/admin/test-tools", label: "Test tools", icon: <FlaskConical aria-hidden="true" className={ICON} /> }] },
];

/** The small route mark beside the name: three stops on a line, the last one apricot. */
export function RouteMark({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 32 32" aria-hidden="true" className={className}>
      <rect width="32" height="32" rx="9" fill="#21504a" />
      <path d="M7.5 20h17" stroke="#a9c2bb" strokeWidth="2" strokeLinecap="round" />
      <circle cx="7.5" cy="20" r="3" fill="#f3ece0" />
      <circle cx="16" cy="20" r="3" fill="#f3ece0" />
      <circle cx="24.5" cy="20" r="3.6" fill="#f0b47a" />
      <path d="M16 10.5v5.5" stroke="#f0b47a" strokeWidth="2" strokeLinecap="round" />
      <circle cx="16" cy="9.5" r="2.4" fill="#f3ece0" />
    </svg>
  );
}

function SideMenu({
  summary, testMode, onNavigate,
}: {
  summary: ReturnType<typeof useMenuCounters>;
  testMode: boolean;
  onNavigate?: () => void;
}) {
  const groups = testMode ? GROUPS : GROUPS.filter((group) => group.label !== "Testing");
  const decidedToday = summary?.decidedToday;
  const total = (decidedToday ?? 0) + (summary?.open ?? 0);
  return (
    <>
      <nav aria-label="Admin pages" className="px-3">
        {groups.map((group) => (
          <div key={group.label ?? "start"} className={group.label ? "mt-5" : ""}>
            {group.label && <p className="px-3 pb-1.5 text-flag font-[650] text-menu-label">{group.label}</p>}
            <ul className="space-y-0.5">
              {group.entries.map((entry) => {
                const count = entry.counter ? summary?.counters[entry.counter] ?? 0 : 0;
                return (
                  <li key={entry.to}>
                    <NavLink
                      to={entry.to}
                      onClick={onNavigate}
                      aria-label={entry.counter && count > 0 ? `${entry.label}, ${count} waiting` : undefined}
                      className={({ isActive }) =>
                        cn(
                          "relative flex min-h-10 items-center gap-3 rounded-control px-3 text-body transition-colors duration-[120ms] pointer-coarse:min-h-11",
                          isActive ? "bg-evergreen-active font-semibold text-cream" : "font-medium text-cream/90 hover:bg-evergreen-hover hover:text-cream",
                        )
                      }
                    >
                      {({ isActive }) => (
                        <>
                          {isActive && <span aria-hidden="true" className="absolute inset-y-2 left-0 w-[3px] rounded-full bg-apricot" />}
                          {entry.icon}
                          <span className="min-w-0 flex-1">{entry.label}</span>
                          {entry.counter && count > 0 && (
                            <span className="inline-flex h-6 min-w-6 items-center justify-center rounded-full bg-apricot px-1.5 text-small font-bold tabular text-ink">{count > 99 ? "99+" : count}</span>
                          )}
                        </>
                      )}
                    </NavLink>
                  </li>
                );
              })}
            </ul>
          </div>
        ))}
      </nav>
      {summary && (
        <div className="mx-3 mb-4 mt-6 rounded-card bg-evergreen-hover/70 p-3.5">
          <p className="text-flag font-[650] text-menu-label">Today</p>
          <p id="frameTodayText" className="mt-1 text-label font-semibold text-cream">
            {decidedToday === undefined ? "" : `${decidedToday} decided, `}{summary.open} still open
          </p>
          {total > 0 && decidedToday !== undefined && (
            <div className="mt-2.5 h-1.5 overflow-hidden rounded-full bg-white/15" role="progressbar" aria-labelledby="frameTodayText" aria-valuemin={0} aria-valuemax={total} aria-valuenow={decidedToday}>
              <div
                className="h-full w-full rounded-full bg-apricot transition-[clip-path] duration-[400ms] ease-out"
                style={{ clipPath: `inset(0 ${100 - (decidedToday / total) * 100}% 0 0 round 999px)` }}
              />
            </div>
          )}
        </div>
      )}
    </>
  );
}

function Brand({ className }: { className?: string }) {
  return (
    <div className={cn("flex items-center gap-2.5", className)}>
      <RouteMark className="size-8 shrink-0" />
      <span translate="no" className="text-[17px] font-bold leading-6 text-cream">LMS Admin</span>
    </div>
  );
}

function formatCountdown(ms: number): string {
  const total = Math.max(0, Math.ceil(ms / 1000));
  return `${Math.floor(total / 60)}:${String(total % 60).padStart(2, "0")}`;
}

/** Calm while there is time ("14 min"), exact in the last two minutes ("1:52"). */
function formatSessionLeft(ms: number): string {
  if (ms > 120_000) return `${Math.ceil(ms / 60_000)}\u00a0min`;
  return formatCountdown(ms);
}

export function AdminFrame() {
  const desktop = useDesktopFrame();
  const location = useLocation();
  const session = useLiveSession();
  const summary = useMenuCounters();
  const deadline = useSessionDeadline();
  const toast = useToast();
  const [clock, setClock] = useState(() => Date.now());
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [warning, setWarning] = useState(false);
  const [sessionError, setSessionError] = useState<string | null>(null);
  const [loggingOut, setLoggingOut] = useState(false);
  const [extending, setExtending] = useState(false);
  const warnedFor = useRef(0);
  const drawerRef = useRef<HTMLDialogElement>(null);
  const username = session?.username ?? "";
  const testMode = session?.testModeIsEnabled ?? false;

  useEffect(() => {
    setDrawerOpen(false);
  }, [location.pathname, location.search]);

  useEffect(() => {
    const timer = window.setInterval(() => setClock(Date.now()), 500);
    return () => window.clearInterval(timer);
  }, []);

  const timeout = (session?.idleTimeoutSeconds ?? 0) * 1000;
  const remaining = deadline ? Math.min(timeout, deadline - clock) : timeout;

  useEffect(() => {
    if (!deadline) return;
    if (remaining <= 0) {
      setWarning(false);
      expireSession();
      return;
    }
    if (remaining <= 120_000 && warnedFor.current !== deadline && !document.querySelector("dialog[open]:modal")) {
      warnedFor.current = deadline;
      setWarning(true);
    }
    if (remaining > 120_000 && warning && !loggingOut && !extending && !sessionError) setWarning(false);
  }, [remaining, deadline, warning, loggingOut, extending, sessionError]);

  useEffect(() => {
    const drawer = drawerRef.current;
    if (!drawer) return;
    if (drawerOpen && !drawer.open) drawer.showModal();
    if (!drawerOpen && drawer.open) drawer.close();
  }, [drawerOpen]);

  const doLogout = async () => {
    setLoggingOut(true);
    setSessionError(null);
    const answer = await logout();
    if (!answer.ok) {
      setLoggingOut(false);
      if (warning || drawerOpen) setSessionError(answer.message);
      else toast.show("error", answer.message);
    }
  };

  const stayLoggedIn = async () => {
    setExtending(true);
    setSessionError(null);
    const answer = await extendSession();
    setExtending(false);
    if (answer.ok) setWarning(false);
    else setSessionError(answer.message);
  };

  const skip = (event: React.MouseEvent) => {
    event.preventDefault();
    document.getElementById("main")?.focus();
  };

  return (
    <div className="min-h-dvh lg:grid lg:grid-cols-[256px_minmax(0,1fr)]">
      <a
        href="#main"
        onClick={skip}
        className="fixed left-3 top-3 z-[80] -translate-y-24 rounded-control bg-teal px-4 py-2.5 font-semibold text-white shadow-raised focus-visible:translate-y-0"
      >
        Skip to main content
      </a>

      {desktop && (
        <aside className="on-evergreen relative z-40 bg-evergreen">
          <div className="sticky top-0 flex h-dvh flex-col overflow-y-auto overscroll-contain">
            <Brand className="px-6 pb-5 pt-5" />
            <SideMenu summary={summary} testMode={testMode} />
          </div>
        </aside>
      )}

      {!desktop && (
        <dialog
          ref={drawerRef}
          aria-label="Admin pages"
          onCancel={(event) => {
            event.preventDefault();
            setDrawerOpen(false);
          }}
          onClose={() => setDrawerOpen(false)}
          className="on-evergreen lms-drawer-in m-0 h-dvh max-h-none w-[min(320px,88vw)] max-w-none overflow-y-auto overscroll-contain bg-evergreen p-0 text-cream backdrop:bg-[rgb(23_32_30/0.45)]"
        >
          <div className="flex min-h-full flex-col">
            <div className="flex items-center justify-between gap-3 px-5 pb-4 pt-4">
              <Brand />
              <button
                type="button"
                onClick={() => setDrawerOpen(false)}
                className="inline-flex min-h-11 items-center gap-1.5 rounded-control border border-white/25 px-3 text-label font-semibold text-cream hover:bg-evergreen-hover"
              >
                <X aria-hidden="true" className="size-4" />
                Close menu
              </button>
            </div>
            <SideMenu summary={summary} testMode={testMode} onNavigate={() => setDrawerOpen(false)} />
            <div className="mt-auto border-t border-white/15 px-6 py-4">
              <p className="text-small text-menu-label">Logged in as</p>
              <p className="font-semibold text-cream">{username}</p>
              <div className="mt-3 flex flex-wrap gap-2">
                <a href={CHANGE_PASSWORD_PAGE} className="inline-flex min-h-11 items-center rounded-control border border-white/25 px-3 text-label font-semibold text-cream hover:bg-evergreen-hover">
                  Change password
                </a>
                <button type="button" disabled={loggingOut} onClick={doLogout} className="inline-flex min-h-11 items-center gap-1.5 rounded-control bg-cream px-3 text-label font-semibold text-evergreen hover:bg-white">
                  <LogOut aria-hidden="true" className="size-4" />
                  Log out
                </button>
              </div>
              <MessagePlace message={sessionError ? { kind: "error", text: sessionError } : null} />
            </div>
          </div>
        </dialog>
      )}

      <div className="min-w-0">
        <header className="sticky top-0 z-30 flex h-14 items-center gap-2 border-b border-line bg-paper/95 px-3 backdrop-blur-sm sm:gap-3 sm:px-6 lg:h-[60px] lg:gap-4 lg:px-8">
          {!desktop && (
            <Button small onClick={() => setDrawerOpen(true)} icon={<Menu aria-hidden="true" className="size-4" />} aria-haspopup="dialog">
              Menu
            </Button>
          )}
          {testMode && <Flag family="waiting">Test mode</Flag>}
          <span className="flex-1" />
          <span
            role="timer"
            aria-label={`Session ends in ${formatSessionLeft(remaining)}`}
            className={cn(
              "inline-flex items-center gap-1.5 rounded-full text-label tabular",
              remaining <= 120_000 ? "bg-apricot-tint px-2.5 py-1 font-semibold text-terracotta" : "text-ink-muted",
            )}
          >
            <Clock aria-hidden="true" className="size-4" />
            <span className="hidden md:inline">Session ends in</span>
            <span aria-hidden="true">{formatSessionLeft(remaining)}</span>
          </span>
          {desktop && (
            <>
              <span className="flex items-center gap-2">
                <span aria-hidden="true" className="grid size-8 place-items-center rounded-full bg-teal-tint text-small font-bold uppercase text-teal-hover">
                  {username.slice(0, 2)}
                </span>
                <span className="text-label font-semibold">{username}</span>
              </span>
              <a href={CHANGE_PASSWORD_PAGE} className="text-label font-semibold text-teal hover:text-teal-hover hover:underline">
                Change password
              </a>
            </>
          )}
          <Button small busy={loggingOut} onClick={doLogout} icon={<LogOut aria-hidden="true" className="size-4" />}>
            Log out
          </Button>
        </header>
        <main id="main" tabIndex={-1} className="w-full max-w-[1400px] px-4 pb-16 pt-6 sm:px-6 lg:px-8 lg:pt-8">
          <Outlet />
        </main>
      </div>

      {warning && (
        <Dialog
          title="Session ending soon"
          onClose={() => {
            setWarning(false);
            setSessionError(null);
          }}
          footer={
            <>
              <Button busy={loggingOut} disabled={extending} onClick={doLogout}>Log out</Button>
              <Button level="main" busy={extending} disabled={loggingOut} onClick={stayLoggedIn}>
                Stay logged in
              </Button>
            </>
          }
        >
          <p>
            Your session ends in <strong className="tabular">{formatCountdown(remaining)}</strong> because nothing was sent for a while. Choose “Stay logged in” to keep working.
          </p>
          <MessagePlace className="mt-3" message={sessionError ? { kind: "error", text: sessionError } : null} />
        </Dialog>
      )}
    </div>
  );
}
