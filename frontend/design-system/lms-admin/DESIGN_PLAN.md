# LMS Admin web app: design plan

Written with the `frontend-design` skill, inside the design system `MASTER.md` (same folder). Copy follows
the copy rules of the taste skill (buttons are a verb and an object, headings name things, no exclamation
marks, no all-caps labels, no filler or virtue words). Section 3 records where the first plan read like a
generic default and what changed.

---

## 1. Subject, audience, job

- **Subject:** the Admin desk of a Logistics Management System. A shop sells its own homeware and
  stationery, picks orders in its warehouse and delivers them with its own drivers. Orders travel a route:
  awaiting payment, ready for picking, picking, ready for pickup, shipped, out for delivery, delivered. Some
  leave the route: a delivery fails, stock is missing, a refund does not finish.
- **Audience:** Admin staff, at a desk for long sessions, and sometimes on a phone between rounds in the
  warehouse.
- **Primary job:** unblock the parcels that are stuck. Decide each exception safely (a reason, a record,
  a truthful message), and manage accounts and products.
- **Brief:** calm, warm, motivating; numbers and small text highly readable; every page responsive from
  390 px to 1440 px; keep every function of the current Admin pages, including the test tools.

---

## 2. The plan

### 2.1 Palette (six named colours)

| Name | Hex | Role |
|---|---|---|
| Evergreen | `#173A36` | The frame: side menu, brand panel of the login page. Deep, quiet, grounding. |
| Linen | `#F3F2ED` | The desk: page background. A warm neutral with a faint green note that belongs with evergreen. |
| Slip white | `#FFFFFF` | Work surfaces: tables, cards, windows. |
| Ink | `#24201C` | Text, a warm near-black. |
| Teal | `#0F6E66` | Action and place: main buttons, links, current tab, focus. |
| Signal apricot | `#F0B47A` (fill `#B5651D`) | Progress and things waiting for you: counters, today's progress, the current stop of a route. |

Plus the six status families of `MASTER.md` (amber, blue, violet, green, red, stone), only inside badges,
flags, route stops and messages.

### 2.2 Type

One family, chosen for the brief's demand on legibility: **Atkinson Hyperlegible Next** for everything
(variable 200-800, tabular figures on), and its sibling **Atkinson Hyperlegible Mono** only for raw values
(log old and new values, the temporary password, the CSV header). Hierarchy comes from size and weight:

| Level | Size, weight | Tracking |
|---|---|---|
| Page title | 28/36 (24/32 on phones), 700 | -0.01 em |
| Section title | 20/28, 650 | -0.005 em |
| Card title | 17/24, 650 | 0 |
| Body and table cells | 15/22, 400 | 0 |
| Small text (sub-lines, help, column titles) | 13/18, 400 or 600 | 0.005 em |
| Big numbers | 34/40, 700, tabular | -0.01 em |

Left-aligned throughout (people read the pages left to right as forms and lists). Numbers right-aligned in
tables. The login card is the only centred block.

### 2.3 Layout concept

**The frame.** An evergreen side menu with the work queues first and their counters, a linen desk, white
slips on it. A "Today" block at the foot of the side menu counts the decisions made today.

```
desktop (1024 px and up)
+-------------------+-------------------------------------------------------------+
| (mark) LMS Admin  | [Test mode]          (clock) Session ends in 14:32  AM admin_main |
|                   |                                   Change password  [Log out]  |
| Dashboard         +-------------------------------------------------------------+
| Work to do        |  Page title                           [Secondary] [Main]      |
|  Warehouse cases 3|  one count line                                              |
|  Delivery cases 3 |  +-------------------------------------------------------+  |
|  Refunds       2  |  | white slip: filter bar, table, pager                  |  |
| Manage            |  +-------------------------------------------------------+  |
|  Users            |                                                             |
|  Products         |                                                             |
| Look up           |                                                             |
|  Orders           |                                                             |
|  Logs             |                                                             |
| Testing           |                                                             |
|  Test tools       |                                                             |
| Today             |                                                             |
|  3 decided, 8 open|                                                             |
+-------------------+-------------------------------------------------------------+

phone (390 px)
+--------------------------------------+
| [Menu] (mark) LMS Admin   (clock)14:32 |
| [Test mode]                          |
+--------------------------------------+
| Page title                           |
| count line                           |
| [Main button        ] [Secondary]    |
| +----------------------------------+ |
| | one row = one card               | |
| | label     value                  | |
| | [Row button] [Row button]        | |
| +----------------------------------+ |
+--------------------------------------+
```

**The signature: the route.** The one bold element of the app. Order statuses are drawn as stops on a route
line, the way a depot draws a delivery round. It appears in two places only:

1. **Dashboard, "Orders on the route":** every active status is a stop with its count; the failed branch
   (delivery failed, ready for redelivery) leaves the line and rejoins it; delivered and cancelled stand at
   the end of the line. A count is a link to the filtered Orders list.
2. **Order page, "Journey":** the stops this order passed, with times; a failed attempt is a red stop with
   its reason; the next step that waits (for example a redelivery request) is drawn as an open stop.

```
Dashboard route (desktop)
 Awaiting      Ready for     Picking    Ready for    Shipped    Out for       Delivered
 payment 4 ---- picking 6 ---- 4 ------- pickup 2 ---- 5 -------- delivery 7 ---- 212
                                                                   |
                                     Ready for redelivery 1 <--- Delivery failed 6
 Cancelled 18 (left the route)

Phone: the same stops stacked top to bottom, the line running down the left edge.
```

Everything else stays quiet: flat white slips with a hairline, no card shadows, few colours.

### 2.4 Principles

1. **Stuck parcels first.** Each work page opens with what waits for a decision; the dashboard opens with
   the queue and the route.
2. **Progress you can trust.** "3 decided today", "2 of 4 cases open", "All clear" are counted from the data,
   never decorative. No streaks, points or confetti.
3. **Decide in the row.** A decision opens a window beside every fact it needs, asks for a reason when it
   ends something, and lands back in its row with its outcome and the time.
4. **Readable at a glance.** One legible family, tabular figures, sub-lines at 13 px, generous row height.
5. **Same functions everywhere.** Phones get the same decisions as desktops: tables turn into cards, never
   into "use a computer".

---

## 3. Review against the brief

I wrote a first plan, then compared it with what I would produce for any "calm, warm admin dashboard" brief.

| First plan | Reads as a default? | Revised to | Why |
|---|---|---|---|
| Cream page `#F6F3EE`, Literata serif titles, terracotta and apricot accents | Yes: the warm cream page with a serif display and a clay accent is the most common generated "warm" look | Linen `#F3F2ED` with a green note, one family (Atkinson Hyperlegible Next), apricot only for progress | The brief asks for warmth and legibility; warmth now comes from linen, warm ink, apricot signals and plain friendly words, not from a borrowed editorial look |
| Dashboard hero: a greeting, then three number tiles with big numbers | Yes: big number, small label, supporting stats | Queue panel (three work queues with counts and an "Open" action each) and the route of orders | The Admin's first need is what is stuck and where; the route is drawn from the subject itself |
| Every block a rounded card with the same soft shadow | Yes: the SaaS card kit | Flat white slips with a hairline; shadows only on things that float (menus, windows, toasts); radius by level (8, 10, 14, 18) | Fewer effects, clearer layers |
| Side menu group labels in small capitals | Yes: all-caps eyebrows | Sentence case, 12.5 px, 650, `menu-label` colour | All-caps labels are a template tell and harder to read |
| Meta lines joined with middle dots ("Failed 2 times · latest ...") | Yes | Commas and separate sub-lines | Plain sentences read better aloud and in a screen reader |
| Mono font for small data labels (order references, SKUs) | Yes | Mono only for raw values; references and SKUs in the UI font with tabular figures | Atkinson already separates 0/O and 1/l/I |
| "Good morning, admin_main" greeting | A stock opener | The page title "Dashboard" and a count line built from the data: "8 cases wait for a decision. 3 decided today." | Says what matters; motivates through facts |

The plan was checked again after the revision: the route, the evergreen frame with its "Today" block and the
queue panel are specific to an Admin who unblocks parcels; the rest is disciplined and quiet.

---

## 4. Words

- Page titles match the side menu entries ("Warehouse cases", "Delivery cases").
- Buttons: a verb and an object ("Approve redelivery", "Download CSV", "Create user"). A toast repeats the
  button's verb ("Redelivery of order ORD-10258 was approved.").
- Empty states give direction: "No shortage case waits for a decision." with "Show all cases".
- Errors say what went wrong and what to do ("Type a reason of at least 5 characters.").
- No exclamation marks, no "Oops", no filler.
- Sentence case everywhere; HKD, SKU, CSV, ID, QR, IP, LMS stay in capitals.

---

## 5. Applying the plan to every page

The app has 16 screens and keeps every function of the current Admin pages (the live pages under
`AdminSystem/backend/src/main/resources/static`). All data comes from a stand-in API (`src/api/`) that holds
realistic sample data in memory and answers like the real endpoints. Reloading the browser restores the
sample data.

### 5.1 Frame (every Admin page)
- Side menu entries and groups as in the current pages; counters on Warehouse cases (open shortage cases),
  Delivery cases (failed deliveries that need a decision) and Refunds (unfinished refunds), updated after
  every decision. "Today" block at the foot: decisions made today and cases still open, with a thin progress
  bar.
- Top bar: "Test mode" flag, session timer (15 minutes, restarts after every answered request), user chip,
  "Change password", "Log out".
- Session window "Session ending soon" at 2 minutes left, with "Log out" and "Stay logged in"; at 0 the app
  opens the login page with "You were logged out because you were inactive."
- Under 1024 px: "Menu" opens the side menu as a drawer; the username, "Change password" and "Log out" move
  into its footer.

### 5.2 Log in, Change password, Choose your own password, Welcome
- Split layout on desktop: an evergreen panel with the wordmark and a drawn route, the form on linen. On
  phones the panel becomes a short evergreen band above the form.
- Log in: username, password with Show, notices from the address (logged out, idle, session ended,
  password changed, password not changed), 5 wrong tries lock the account for 15 minutes, a temporary
  password leads to "Choose your own password".
- Change password (both cases): live rule list, advisory strength scale (amber or green, never red), Show
  and Hide on each field, errors under each field, "Cancel" or "Log out".
- Welcome: "You are not allowed to open that page." with "Back to your start page".

### 5.3 Dashboard
1. Page header: "Dashboard", count line built from the data, "Last loaded" time and "Refresh".
2. **Queue panel** "Work to do": Shortage cases, Client requests, Refund problems, each with its count, one
   plain line on what it is, and "Open" (a link to the filtered work page). Under it "Also check": waiting for
   restock, failed deliveries waiting for the client, flagged delivery cases, locked users. When all three
   queues are empty: "All clear. Nothing waits for a decision."
3. **Today's progress** band in apricot tint: "3 decided today", bar "3 of 11 cases decided".
4. **Orders on the route** (the signature, Section 2.3) with 35 active orders; each count links to the
   filtered Orders list.
5. Two slips side by side: **Low stock** (products below their low-stock level, lowest available first,
   "Show all low-stock products") and **System health** (database, tables, server time).

### 5.4 Warehouse cases
- Header count line: "3 open, 1 waiting for restock".
- Slip "Shortage cases": filter (Open and waiting, Open, Waiting restock, Cancelled, Resolved), "Download
  CSV"; work table (Order, Shortage, Reported, Status, Actions); row buttons "Wait for restock", "Cancel and
  refund" (paid) or "Cancel order" (not paid); decided rows show the outcome line.
- Slip "Picking tasks in progress": Order, Worker (flag "Worker not active"), Assigned, Started, Progress (a
  small bar "3 of 4 picked"), "Reassign".
- Windows: wait for restock, cancel and refund (tick + reason), cancel order (unpaid), reassign picking task.
- Phones: each row a card with the decision buttons at its foot.

### 5.5 Delivery cases
- Header count line: "3 need a decision, 6 failed deliveries".
- Slip "Failed deliveries": filter (All, Waiting for return, Needs decision, Waiting for client, Flagged),
  "Download CSV", the settings strip ("Review at 2 failures. Reminder due after 3 days. Final review after 7
  days. Nothing is cancelled automatically."). Buttons: "Send reminder" (switched off for 24 hours after a
  reminder, with "Next reminder after ..."), "Record request", "Approve redelivery" (switched off while a
  shortage case is open, with the reason and "View shortage cases"), "Approve cancellation", "Reject".
- Slip "Held packages": Order with its status, Package, Held by (flag "Worker not active"), Responsible
  worker, Last reassigned, "Reassign".
- Windows: approve redelivery, approve cancellation (paid, unpaid or an existing refund), reject request,
  record a client request (identity check, replaces a pending request), reassign delivery.

### 5.6 Refunds
- Slip "Refunds": tick box "Unresolved only", "Download CSV", strip "A refund is shown as Refunded only after
  the payment provider confirmed it. It cannot be set by hand." Columns Order (with refund number and order
  status), Amount, Status, Age, Last check (with the provider's answer), Actions: "Check provider status"
  (answers with the provider's word and updates the row) and "Retry refund" (reason).

### 5.7 Users
- Header: "42 accounts", "Download CSV", "Create user".
- Filter bar: search, role, status (All, Active, Deactivated, Banned, Locked now), "Search", "Clear".
- Table: Username (you, Super admin flag), Role, Status (lock time left, tries), Last login, Actions menu:
  View, Change role, Reset password, Unlock, Unban, Reactivate, Ban, Deactivate (the lines that fit the
  account). The super admin's own row says "Your own account".
- Windows: create user (Delivery Worker profile fields), temporary password with Copy, view user (save the
  Delivery Worker profile), change role (reason, active work), reset password (identity check), ban, unban,
  deactivate, reactivate, unlock. Only the super admin manages Admin accounts.

### 5.8 Products
- Header: "128 products", "Download CSV", "Import CSV", "Add product".
- Filter bar: search SKU or name, status, "Low stock only". Note "Stock is changed only by Warehouse."
- Table: SKU, Name (flags Low stock, Shortfall), Price, Physical, Reserved, Available, Low below, Status,
  Actions menu (Edit, Show QR code, Activate or Deactivate).
- Windows: add product (validation), edit product (SKU change warning), QR label (a real QR code of the SKU,
  Print label), import CSV (file check, result with rejected lines), deactivate (open orders), activate.

### 5.9 Orders and Order
- Orders: search, order status, date range, "Download CSV"; table with the "Open case" flag; the reference
  opens the order page.
- Order: back link, title with the status badge, **Journey** (the route of this order), Order facts with the
  privacy note, Items with total, Payment and Refund, Picking (task, workers), Delivery (package, delivery
  attempts, client requests), Shortage cases (with "Decide on Warehouse cases"), Timeline grouped by day.

### 5.10 Logs
- Note "Log rows cannot be changed or deleted here." Tabs Audit log (kept 3 years), Security log (kept 90
  days), Picking scans (kept 90 days), each with its filters, "Download CSV", table and pager; raw values in
  the mono font with private parts hidden; slip "Log clean-up" (last run, rows deleted, keeping time).

### 5.11 Test tools
- Title with the flag "Test mode", count line "These tools exist only in test mode."
- A stage index (Stage 1 to Stage 9) beside the tools on desktop, a jump list on phones.
- All 21 tools as slips in their stage: title, one sentence, "How to use", fields with examples and help,
  one button, and the result written under it. Every tool changes the sample data, so its result can be seen
  on the page it names (for example "Create test user" adds a `test_` account to Users).
- "Remove all test data" is a danger button that needs "REMOVE" typed.

### 5.12 Not found
- Inside the frame: "Page not found", one line, "Open the dashboard".

---

## 6. Responsive rules

| Width | Rules |
|---|---|
| 390 px | One column; page header buttons full width under the title; tables become row cards; windows full width minus 16 px; filter bars stack; the route runs vertically. |
| 768 px | Two-column grids where they help (dashboard slips); tables are real tables, wide list tables scroll inside their slip with the first column pinned. |
| 1024 px | Fixed side menu; work tables wrap. |
| 1440 px | Content up to 1400 px; dashboard route in one row. |

---

## 7. Build notes

- React 19, TypeScript, Vite, Tailwind CSS 4, React Router (hash routes, so the build works from any static
  host), Lucide icons, `qrcode` for real QR labels. Fonts from `@fontsource-variable`.
- `src/api/` is the stand-in for the live endpoints (`/api/admin/...`, `/api/shared/...`): same actions,
  same rules (reasons of at least 5 characters, the super admin rules, blocked decisions), same messages. A
  real backend can replace it later without changing the pages.
- Sample world: 42 accounts, 128 products, 265 orders (35 active), at the sample moment 2026-11-12 09:14;
  the clock runs on from there.
