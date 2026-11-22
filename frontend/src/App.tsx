import { Suspense, lazy, useEffect, type ReactNode } from "react";
import { HashRouter, Navigate, Route, Routes, useLocation } from "react-router";
import { AdminFrame } from "./components/Frame";
import { ToastProvider } from "./components/Toast";
import { Button, InlineMessage } from "./components/ui";
import {
  CHANGE_PASSWORD_PAGE,
  LOGIN_PAGE,
  WELCOME_PAGE,
  loadLiveSession,
  observeSessionRequests,
  useLiveSession,
  useSessionState,
  welcomeAddress,
} from "./api/session";
import { LoginPage } from "./pages/LoginPage";
import { ChangePasswordPage } from "./pages/ChangePasswordPage";
import { WelcomePage } from "./pages/WelcomePage";


// Each Admin page is loaded when it is first opened, so the login page appears quickly.
const DashboardPage = lazy(() => import("./pages/DashboardPage").then((m) => ({ default: m.DashboardPage })));
const WarehouseCasesPage = lazy(() => import("./pages/WarehouseCasesPage").then((m) => ({ default: m.WarehouseCasesPage })));
const DeliveryCasesPage = lazy(() => import("./pages/DeliveryCasesPage").then((m) => ({ default: m.DeliveryCasesPage })));
const RefundsPage = lazy(() => import("./pages/RefundsPage").then((m) => ({ default: m.RefundsPage })));
const UsersPage = lazy(() => import("./pages/UsersPage").then((m) => ({ default: m.UsersPage })));
const ProductsPage = lazy(() => import("./pages/ProductsPage").then((m) => ({ default: m.ProductsPage })));
const OrdersPage = lazy(() => import("./pages/OrdersPage").then((m) => ({ default: m.OrdersPage })));
const OrderDetailPage = lazy(() => import("./pages/OrderDetailPage").then((m) => ({ default: m.OrderDetailPage })));
const LogsPage = lazy(() => import("./pages/LogsPage").then((m) => ({ default: m.LogsPage })));
const TestToolsPage = lazy(() => import("./pages/TestToolsPage").then((m) => ({ default: m.TestToolsPage })));
const NotFoundPage = lazy(() => import("./pages/NotFoundPage").then((m) => ({ default: m.NotFoundPage })));

const TITLES: Array<[RegExp, string]> = [
  [/^\/admin\/dashboard/, "Dashboard - LMS Admin"],
  [/^\/admin\/warehouse-cases/, "Warehouse cases - LMS Admin"],
  [/^\/admin\/delivery-cases/, "Delivery cases - LMS Admin"],
  [/^\/admin\/refunds/, "Refunds - LMS Admin"],
  [/^\/admin\/users/, "Users - LMS Admin"],
  [/^\/admin\/products/, "Products - LMS Admin"],
  [/^\/admin\/orders\/(.+)/, "Order $1 - LMS Admin"],
  [/^\/admin\/orders/, "Orders - LMS Admin"],
  [/^\/admin\/logs/, "Logs - LMS Admin"],
  [/^\/admin\/test-tools/, "Test tools - LMS Admin"],
];

const SHARED_TITLES: Record<string, string> = {
  [LOGIN_PAGE]: "Log in - LMS",
  [CHANGE_PASSWORD_PAGE]: "Change password - LMS",
  [WELCOME_PAGE]: "Welcome - LMS",
};

/** Sets the browser tab title of each page and moves the keyboard to the top of a new page. */
function PageTitle() {
  const location = useLocation();
  useEffect(() => {
    const match = TITLES.find(([pattern]) => pattern.test(location.pathname));
    if (!match) document.title = "Page not found - LMS Admin";
    else if (match[1].startsWith("Order $1")) document.title = `Order ${decodeURIComponent(location.pathname.split("/").pop() ?? "")} - LMS Admin`;
    else document.title = match[1];
    window.scrollTo(0, 0);
  }, [location.pathname]);
  return null;
}

/** Admin pages need a session of an Admin who has chosen their own password. */
function RequireAdmin({ children }: { children: ReactNode }) {
  const session = useLiveSession();
  if (!session) return <OpenDocument address={LOGIN_PAGE} />;
  if (session.mustChangePassword) return <OpenDocument address={CHANGE_PASSWORD_PAGE} />;
  if (session.role !== "ADMIN") return <OpenDocument address={welcomeAddress(true)} />;
  return <>{children}</>;
}

function OpenDocument({ address }: { address: string }) {
  useEffect(() => {
    window.location.replace(address);
  }, [address]);
  return null;
}

function SessionReady({ children }: { children: ReactNode }) {
  const state = useSessionState();
  if (state.status === "loading") return <p className="px-8 py-10 text-ink-muted">Checking your session…</p>;
  if (state.status === "error") {
    return (
      <main className="mx-auto max-w-[480px] space-y-4 px-6 py-10">
        <h1 className="text-page-title font-bold">Could not check your session</h1>
        <InlineMessage kind="error">{state.message}</InlineMessage>
        <Button onClick={() => void loadLiveSession()}>Try again</Button>
      </main>
    );
  }
  return <>{children}</>;
}

function SharedSessionPage({ isPasswordPage }: { isPasswordPage: boolean }) {
  const session = useLiveSession();
  if (!session) return <OpenDocument address={LOGIN_PAGE} />;
  if (isPasswordPage) return <ChangePasswordPage session={session} />;
  if (session.mustChangePassword) return <OpenDocument address={CHANGE_PASSWORD_PAGE} />;
  return <WelcomePage session={session} />;
}

function TestToolsRoute() {
  const session = useLiveSession();
  if (!session?.testModeIsEnabled) return <Navigate to="/admin/dashboard" replace />;
  return <TestToolsPage />;
}

export function App() {
  const path = window.location.pathname;
  useEffect(() => {
    const stopObserving = observeSessionRequests();
    const checkRestoredPage = (event: PageTransitionEvent) => {
      if (event.persisted) window.location.reload();
    };
    window.addEventListener("pageshow", checkRestoredPage);
    void loadLiveSession();
    return () => {
      stopObserving();
      window.removeEventListener("pageshow", checkRestoredPage);
    };
  }, []);

  useEffect(() => {
    if (SHARED_TITLES[path]) document.title = SHARED_TITLES[path];
  }, [path]);

  if (path === LOGIN_PAGE) return <LoginPage />;
  if (path === CHANGE_PASSWORD_PAGE || path === WELCOME_PAGE) {
    return <SessionReady><SharedSessionPage isPasswordPage={path === CHANGE_PASSWORD_PAGE} /></SessionReady>;
  }

  return (
    <SessionReady><HashRouter>
      <ToastProvider>
        <PageTitle />
        <Suspense fallback={<p className="px-8 py-10 text-ink-muted">Loading the page…</p>}>
        <Routes>
          <Route path="/" element={<Navigate to="/admin/dashboard" replace />} />
          <Route
            path="/admin"
            element={
              <RequireAdmin>
                <AdminFrame />
              </RequireAdmin>
            }
          >
            <Route index element={<Navigate to="/admin/dashboard" replace />} />
            <Route path="dashboard" element={<DashboardPage />} />
            <Route path="warehouse-cases" element={<WarehouseCasesPage />} />
            <Route path="delivery-cases" element={<DeliveryCasesPage />} />
            <Route path="refunds" element={<RefundsPage />} />
            <Route path="users" element={<UsersPage />} />
            <Route path="products" element={<ProductsPage />} />
            <Route path="orders" element={<OrdersPage />} />
            <Route path="orders/:reference" element={<OrderDetailPage />} />
            <Route path="logs" element={<LogsPage />} />
            <Route path="test-tools" element={<TestToolsRoute />} />
            <Route path="*" element={<NotFoundPage />} />
          </Route>
          <Route
            path="*"
            element={
              <RequireAdmin>
                <AdminFrame />
              </RequireAdmin>
            }
          >
            <Route path="*" element={<NotFoundPage />} />
          </Route>
        </Routes>
        </Suspense>
      </ToastProvider>
    </HashRouter></SessionReady>
  );
}
