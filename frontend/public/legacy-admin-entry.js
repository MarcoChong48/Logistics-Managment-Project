const path = window.location.pathname.split("/").at(-1);
const query = new URLSearchParams(window.location.search);

const routes = {
  "admin-dashboard.html": "dashboard",
  "admin-warehouse-cases.html": "warehouse-cases",
  "admin-delivery-cases.html": "delivery-cases",
  "admin-refunds.html": "refunds",
  "admin-users.html": "users",
  "admin-products.html": "products",
  "admin-orders.html": "orders",
  "admin-order-detail.html": "order-detail",
  "admin-logs.html": "logs",
  "admin-temporary-test-tools.html": "test-tools",
};

const filters = {
  "warehouse-cases": { adminSearchShortageCaseCaseStatus: "caseStatus", adminSearchShortageCasePageNumber: "casePage", adminSearchPickingTaskPageNumber: "taskPage" },
  "delivery-cases": { adminSearchFailedDeliveryCaseFilter: "show", adminSearchFailedDeliveryCasePageNumber: "page", adminSearchHeldPackagePageNumber: "heldPage" },
  refunds: { adminSearchRefundIsUnresolvedOnly: "unresolved", adminSearchRefundPageNumber: "page" },
  users: { adminSearchUserKeyword: "q", adminSearchUserRole: "role", adminSearchUserAccountStatus: "status", adminSearchUserPageNumber: "page" },
  products: { adminSearchProductKeyword: "q", adminSearchProductListingStatus: "status", adminSearchProductIsLowStockOnly: "low", adminSearchProductPageNumber: "page" },
  orders: { adminSearchOrderKeyword: "q", adminSearchOrderOrderStatus: "status", adminSearchOrderCreatedFromTime: "from", adminSearchOrderCreatedToTime: "to", adminSearchOrderPageNumber: "page" },
};

const logTabs = {
  AUDIT: ["audit", "adminSearchAuditLog", { ActorUsername: "actor", Action: "action", TargetType: "target", TargetID: "targetID" }],
  SECURITY: ["security", "adminSearchSecurityLog", { Username: "username", EventType: "event", Result: "result" }],
  PICKING_SCAN: ["scans", "adminSearchPickingScanHistory", { OrderReference: "order", WorkerUsername: "worker", Result: "result" }],
};
const applyFilters = (names, destination) => {
  for (const [from, to] of Object.entries(names)) {
    if (!query.has(from)) continue;
    destination.set(to, query.get(from));
  }
};

let route = Object.hasOwn(routes, path) ? routes[path] : "dashboard";
const next = new URLSearchParams();
if (route === "order-detail") {
  const id = query.get("orderID");
  route = id && /^\d+$/.test(id) ? `orders/${id}` : "orders";
} else if (route === "logs") {
  const tabKey = query.get("adminShowLogTabName");
  const [tab, prefix, fields] = Object.hasOwn(logTabs, tabKey) ? logTabs[tabKey] : logTabs.AUDIT;
  next.set("tab", tab);
  const names = { FromTime: "from", ToTime: "to", PageNumber: "page", ...fields };
  applyFilters(Object.fromEntries(Object.entries(names).map(([suffix, name]) => [prefix + suffix, name])), next);
} else if (Object.hasOwn(filters, route)) {
  applyFilters(filters[route], next);
}
window.location.replace(`/admin/redesign/index.html#/admin/${route}${next.size ? `?${next}` : ""}`);
