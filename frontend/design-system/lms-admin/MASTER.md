# LMS design system: "Evergreen and apricot on a linen desk"

> **This file is the design system of every web page of the Logistics Management System (LMS):** the Admin
> pages, built first in `GrandProject/AdminWebApp/`, and the pages of the Client, the Warehouse Worker and the
> Delivery Worker, plus the shared pages (log in, welcome, change password). A person who works in two roles
> must find the same frame, parts, words and colours in both. Start with Section 0.

**Version:** 2.0, 2026-11-23. Versions 1.x described the Admin web app only (history in Section 20).
**Reference build:** `GrandProject/AdminWebApp/` (React 19, Tailwind CSS 4, Vite). Every rule in this file is
used there, so any rule can be seen working by running that app (`npm install`, `npm run dev`; the sample
accounts are in its `README.md`). Appendices A to C hold the code of the look, so this file also works alone.
**Feel (the brief):** calm, warm and motivating; every number and every small text highly readable. Calm comes
from a linen desk, an evergreen frame, generous spacing and very little motion. Warmth comes from warm
neutrals, warm ink and apricot signals. Motivation comes from progress counted from real data: what waits for
the person, what they finished today, and a clear "All clear" when a queue is empty.

---

## Contents

0. How to use this file
1. Principles
2. Decision guide
3. Colour
4. Status words, badges and flags
5. Typography
6. Spacing, sizes and radius
7. Elevation
8. Motion
9. Layout, breakpoints and devices
10. The frame
11. Page kinds
12. Parts
13. Words
14. Privacy and roles
15. Accessibility
16. Anti-patterns
17. Engineering
18. Checklists
19. Additions by system
20. History
- Appendix A. Tokens (`tokens.css`)
- Appendix B. Base styles (`src/index.css`)
- Appendix C. Class recipes of the parts

---

## 0. How to use this file

### 0.1 What is fixed and what each system decides

| Fixed for every system (never changed for one system) | Decided by each system, inside these rules |
|---|---|
| Colour tokens and the seven status families | Which pages exist, from the system's backend context and its current pages |
| Fonts, type scale, spacing, radius, shadows, motion | The entries and groups of its side menu |
| The frame: side menu, top bar, drawer, skip link | Which parts each page uses (Section 2 decides most cases) |
| Every part of Section 12 and how it behaves | New status words for new stored values (Section 4.3), added to Section 4 |
| The words of Section 13 and the status words of Section 4 | The main device of its role (Section 9.3) |
| Breakpoints and how tables turn into cards | What its role may see (from its backend context, Section 14) |
| Accessibility, privacy and test rules | Its sample data, inside the rules of Section 17.4 |

### 0.2 Quick start for a new system

1. Read the system's backend context and its current pages. List every function, test tools included: a
   redesign keeps all of them.
2. Start from the reference build: copy `design-system/lms-admin/tokens.css`, `src/index.css`,
   `src/components/`, `src/lib/` and the scripts in `tests/` from `GrandProject/AdminWebApp/`. Without that
   folder, rebuild the same base from Appendices A to C. Never change a token value for one system.
3. Build the frame of the role (Section 10): the wordmark, the menu groups, counters only on pages where work
   waits for the person.
4. Give every job its own page and choose its kind (Section 11).
5. Look up every design choice in the decision guide (Section 2) and use what it says.
6. Write the words with Section 13 and the status words with Section 4.
7. Fill every page with realistic sample data (Section 17.4).
8. Check with the tests and the checklists (Sections 17.5 and 18).
9. Write every new part, status word or rule into Section 19 of the master copy, with the date and the reason.

### 0.3 One master copy

The master copy is `GrandProject/AdminWebApp/design-system/lms-admin/MASTER.md`. A system that adds something
writes it into its entry in Section 19, so every system reads one file. Changing an existing rule or token
changes every system: record it in Section 20 and bring every built system up to date, or do not make the
change. A chat that has only an attached copy of this file writes its additions into its Section 19 entry and
hands that entry back to be merged into the master copy.

### 0.4 What decides what

1. The role's backend context and the agreed plans decide which data, actions and rules exist.
2. This file decides how they look, read and behave.
3. Where this file has no answer, Section 2.2 ("When nothing fits") says what to do.

---

## 1. Principles

1. **The next action is the most visible thing on the page.** Work that waits for the person comes first;
   facts sit beside the decision or action they inform.
2. **Calm by default, colour only with meaning.** Large areas are linen and white. Colour marks an action
   (teal), a state (the seven status families), a warning about loss (red), or progress and "your turn"
   (apricot).
3. **Readable numbers and small text.** One highly legible font with tabular figures. No text under 12 px;
   help text and sub-lines are at least 13 px.
4. **Every button has words.** An icon may stand next to the words, never alone.
5. **Honest and deliberate.** A blocked action says why. An action that ends something needs a confirm
   window, and a decision that ends something needs a reason. Nothing reports a success that did not
   happen, and every progress count comes from real data.
6. **Privacy by role.** A page shows only what its role needs (Section 14).
7. **One LMS.** The same thing has the same name, badge, place and part on every page of every role.
8. **Works everywhere.** Phone (390 px) to wide desktop (1440 px and more); keyboard first; screen readers;
   reduced motion; fonts bundled, nothing loaded from an outside address.

---

## 2. Decision guide

### 2.1 Common decisions

**Pages**

| You need | Use | Not | See |
|---|---|---|---|
| A new job for the role | Its own page with its own menu entry | Tabs or sections that hold different jobs on one page | 11 |
| One thing in detail (an order, a task) | A detail page opened from its list, with "Back to …" above the title; its list stays the current menu entry | A menu entry for the detail page | 11.3 |
| The first page after log in | A start page: what waits for the person first, then today's progress, then what to keep an eye on | Decorative charts, greetings, slogans | 11.5 |
| Creating or changing one thing with up to about six fields | A form window opened by a worded button | A separate page | 12.10 |
| Entering something with many fields or groups (an order, a delivery outcome) | A form page: one card per group of fields, a summary card beside it, one main button at the end | A long window that scrolls | 11.4 |
| "Are you sure?" before something ends | A confirm window that names the thing and lists what changes | The browser's own `confirm()` | 12.10 |
| A page before log in, or one that stands alone | A page without the frame (brand panel and form) | The full frame | 11.7 |
| Tools that make test data | A "Test tools" page in the menu group "Testing", only in test mode | Test buttons on work pages | 11.6 |

**Actions**

| You need | Use | Not | See |
|---|---|---|---|
| The one action the page exists for | The main button (teal): at most one per page and one per window; a page that is only read has none | Two teal buttons | 12.3 |
| Any other action | A secondary button | | 12.3 |
| "Clear", "Show all" and other text-like actions | A quiet button | A link that looks like a button | 12.3 |
| One to three decisions on each row of a work list | Small buttons in the row, named with their row ("Reject request for order ORD-10227") | An Actions menu | 12.7 |
| Several or rarely used actions on each row of a long list | One "Actions" menu per row; dangerous lines last, in red, after a divider | A row of many buttons | 12.8 |
| An action in a row that ends something or switches it off | A danger (outline) button that opens a confirm window, whose confirm button is danger (filled) | A red button that acts at once | 12.3, 12.10 |
| An action the rules do not allow yet | A blocked button: it looks switched off, stays reachable, and a line under it says why and links to where it is fixed | Hiding the button; a switched-off button without a reason | 12.3 |
| A decision that needs a reason | A reason field (at least 5 characters); the confirm button stays off until it is ready, and a line says what is missing | Checking only after the click | 12.10 |
| Going to another page | A link (`TextLink`, `ButtonLink`) | A button that changes the address | 12.4 |
| Downloading the rows of a list | "Download CSV" in the page header (list pages) or in the card header (work pages); it downloads the rows the filters show | A download hidden in a menu | 12.3 |

**Lists and tables**

| You need | Use | Not | See |
|---|---|---|---|
| A long list people search | A list page: search and filters with labels, a list table, a pager of 20 rows; filters, tab and page live in the address | Endless scrolling; filters that vanish on reload | 11.2, 12.7 |
| Cases someone must decide or act on | A work page: rows that wait for the reader first, a segmented filter with counts, a decided row that keeps its place and shows its outcome | Sorting by date alone | 11.1, 12.7 |
| Rows on a phone | Cards: below 768 px for list tables, below 1280 px for work tables | A table that scrolls sideways on a phone | 9, 12.7 |
| A list with nothing in it | An empty state (icon, one sentence, "Clear filters"), or "All clear" for an empty work queue | An empty table head | 12.12 |
| A few counts on a start page | A queue panel, number lines or the route | Charts | 12.11 |

**States**

| You need | Use | Not | See |
|---|---|---|---|
| The state of a thing | A status badge with plain words and its family | Coloured text; a badge without words | 4, 12.6 |
| One more fact about a thing ("Low stock") | A flag | A second badge | 4.4, 12.6 |
| The normal, in-use state ("Active") | Muted text | A green badge on every row | 4.2 |
| A case that waits for the reader's own decision | The Turn family (apricot), a counter on the menu entry, "need you" links on the start page | Red | 4.3, 10.1 |
| Something that waits for somebody else or for time | The Waiting family | | 4.3 |
| Something that went wrong or is blocked | The Problem family; red error text | | 4.3 |
| How far one thing has come through fixed steps | Progress steps (the journey) with a "Now:" line | A row of badges | 12.11 |
| A count that grows ("3 of 5 picked") | A progress bar with its words beside it | A bar without words | 12.11 |
| An empty value | A muted "-" (screen readers hear "None") | "N/A", "null", an empty cell | 13.6 |

**Input**

| You need | Use | Not | See |
|---|---|---|---|
| A field | The label above, the input, a help line, an error line; the placeholder only shows an example | A placeholder as the label | 12.5 |
| Choosing one of up to about five filters of a list | A segmented filter with counts | A select | 12.7 |
| Choosing one of many options | A select | A long column of radio buttons | 12.5 |
| Yes or no | A tick box with words | A switch | 12.5 |
| A scanned or typed code (SKU, package code) | The scan box | A plain text field | 12.14 |
| Photo or signature proof | The capture box | A plain file field | 12.15 |
| A quantity on each row | A number field in the row | A select of numbers | 12.16 |
| A password | The password field with "Show", the rules checked while typing, a strength scale that only advises | A strength meter that blocks | 12.5 |
| A secret shown once (a temporary password) | A secret box with "Copy" | A toast | 12.5 |

**Feedback**

| You need | Use | Not | See |
|---|---|---|---|
| A decision or action on a page worked | A toast that names the thing and the result; the row shows its new state | A toast that only says "Done" | 12.9 |
| An answer while a window stays open | A message inside the window (`MessagePlace`); focus moves to the first field to fix | A toast behind the window | 12.9, 12.10 |
| A fixed rule the reader should know | A note (information icon, muted words) | A warning colour | 12.9 |
| Work that runs | The button shows a spinner and "Saving…" and keeps its width | A full-page spinner | 12.3 |
| A page that loads | "Loading the page…" | A blank page | 17.3 |
| A session that will end | The "Session ending soon" window two minutes before | A silent log-out | 10.3 |

**Words**

| You need | Use | Not | See |
|---|---|---|---|
| A button | A verb and the thing, 1 to 5 words ("Approve redelivery") | OK, Yes, Submit, Continue, Learn more, Click here | 13.2 |
| A stored word on a page | Its plain words from Section 4 | The stored word (`READY_FOR_PICKUP`) | 4.2 |
| A new stored word | Split at the underscores, first letter capital ("Ready for pickup"); its family from Section 4.3; written into Section 4 | A new colour | 4.3 |
| A time | `2026-11-12 09:14` | "2 hours ago" alone (an age may stand beside the time) | 13.5 |
| Money | `HKD 1,234.50` | `$1234.5` | 13.5 |

### 2.2 When nothing fits

1. Use an existing part with other content.
2. Combine existing parts (a card with a fact list and a progress bar).
3. Make a new part from the tokens only: no new colour, font, size, radius, shadow or motion. It has words on
   every control, the focus ring, 40 px targets (44 px on touch screens), a phone layout and reduced motion.
4. Write it into Section 19 (what it is, when to use it, why, where it is used), so the next system reuses it.

### 2.3 Questions to answer for every page

- What is the one job of this page, and which is its main button, if any?
- What waits for the reader here, and does it come first?
- Which actions are dangerous (they end something for good or switch it off)?
- List page or work page? Table or cards at this width?
- Badge, flag or plain text for each value?
- What does a phone show first, and what does the person do there?
- What does the page say when it is empty, loading, refused or blocked?

---

## 3. Colour

All pairs below were measured (WCAG 2 contrast ratio). Text needs 4.5:1; meaningful graphics and control
edges need 3:1. The code of every token is in Appendix A.

### 3.1 Neutrals (warm paper)

| Token | Hex | Use | Measured |
|---|---|---|---|
| `paper` (linen) | `#F3F2ED` | Page background | ink 14.43, muted 6.32, subtle 5.09, teal 5.44 |
| `surface` | `#FFFFFF` | Cards, tables, windows, inputs | ink 16.17, muted 7.09, subtle 5.71 |
| `sunken` | `#F1EFE9` | Table header row, inset blocks, heads of parts | ink 14.07, muted 6.16, subtle 4.96 |
| `sand` | `#E8E5DB` | Switched-off buttons, quiet fills (never behind an input) | ink 12.83, muted 5.62 |
| `track` | `#ECE8DF` | Progress bar track | apricot fill 3.55, teal 4.99 |
| `hover` | `#F7F6F1` | Row and menu-line hover | ink 14.95, muted 6.55 |
| `line` | `#E2DED3` | Hairlines between rows and around cards (decorative) | - |
| `line-strong` | `#CDC7B8` | Dividers on sunken areas, the route line (decorative) | - |
| `field-line` | `#8C8278` | Edges of inputs, selects, tick boxes, secondary buttons | 3.76 on surface, 3.36 on paper, 3.27 on sunken |
| `ink` | `#24201C` | Main text (warm near-black) | see above |
| `ink-muted` | `#5F574F` | Secondary text, column titles, help text | 7.09 on surface |
| `ink-subtle` | `#6E655C` | Placeholders and examples in inputs | 5.71 on surface |

### 3.2 Brand and action colours

| Token | Hex | Use | Measured |
|---|---|---|---|
| `teal` | `#0F6E66` | Main button, links, current tab, chosen state, focus ring | white on it 6.10; on surface 6.10; on paper 5.51 |
| `teal-hover` | `#0B5B54` | Hover of teal buttons and links | white on it 7.96 |
| `teal-press` | `#094A45` | Pressed teal button | - |
| `teal-tint` | `#E2F0EC` | Chosen filter, information message background | teal-hover text on it 6.79 |
| `evergreen` | `#173A36` | Side menu and brand panel (the frame) | cream 10.55, white 12.39 |
| `evergreen-hover` | `#21504A` | Side menu hover | cream 7.74 |
| `evergreen-active` | `#2A5E57` | Current side menu entry | cream 6.30, apricot bar 4.06 |
| `cream` | `#F3ECE0` | Text on the side menu | 10.55 |
| `menu-label` | `#A9C2BB` | Group labels in the side menu | 6.56 |
| `apricot` | `#F0B47A` | Focus ring and current-entry bar in the side menu; counters | 6.79 on evergreen |
| `apricot-fill` | `#B5651D` | Meaningful progress fills | 4.34 on surface, 3.55 on track, 3.72 on apricot-tint |
| `apricot-tint` | `#FCEBD8` | Warm highlight backgrounds (today's progress, "need you" links) | - |
| `terracotta` | `#8A4310` | Text on `apricot-tint` | 6.23 |
| `danger` | `#B42318` | Dangerous buttons and menu lines, error text | white on it 6.57; on surface 6.57 |
| `danger-hover` | `#931B12` | Hover of danger buttons | white on it 8.73 |
| `danger-tint` | `#FCE9E6` | Error message background, danger line hover | danger on it 5.62 |

### 3.3 Rules for colour

- Teal means "you can press this" or "you are here". It is never a status.
- Red means "this ends something or switches something off" (buttons) or "this went wrong" (error text). A
  filled red button only appears as the confirm button of a dangerous window.
- Apricot means progress and things that wait for you (counters, today's progress, the Turn family, the
  "3 need you" links). It never marks an error.
- The status families appear only inside badges, flags, status dots, the route and messages. Never as the
  background of a card or a page.
- **No role gets its own colour.** The role shows in the wordmark and the menu, never in an accent colour.
- No dark mode: every LMS page is light (`color-scheme: light`).

---

## 4. Status words, badges and flags

A status is never shown by colour alone: it is always a badge or a flag with its words. The same stored word
always has the same words on every page of every role.

### 4.1 The seven families

| Family | Meaning | Background | Text | Dot | Text on background |
|---|---|---|---|---|---|
| Turn (apricot) | A case that waits for the reader's own decision (Section 4.3) | `#FCEBD8` | `#8A4310` | `#B5651D` | 6.23 |
| Waiting (amber) | Waits for somebody else or for time | `#FBEFD7` | `#774700` | `#C98516` | 6.86 |
| Warehouse (blue) | Work is going on; the thing is in the warehouse | `#E3EDFA` | `#1C4C8A` | `#3A78D0` | 7.25 |
| Road (violet) | With a Delivery Worker, on the way | `#EDE7FA` | `#4C3394` | `#7656D2` | 7.88 |
| Done (green) | Finished well, or in normal use | `#E0F1E5` | `#1C5F35` | `#3A9659` | 6.53 |
| Problem (red) | Failed or blocked | `#FBE4DF` | `#972A1D` | `#D2493B` | 6.48 |
| Closed (stone) | Ended, switched off, unknown, or a neutral fact | `#EEEAE4` | `#544D46` | `#968C82` | 6.94 |

### 4.2 Status words

The words on the badge are the stored word split at the underscores, with a capital first letter, except where
the table says otherwise. Families marked "Turn (Admin)" are Turn on the pages of the role that decides the
case and Waiting on every other role's pages (Section 4.3).

| Kind of thing | Stored word | Words on the badge | Family |
|---|---|---|---|
| Order status | `AWAITING_PAYMENT` | Awaiting payment | Waiting |
| Order status | `READY_FOR_PICKING` | Ready for picking | Warehouse |
| Order status | `PICKING` | Picking | Warehouse |
| Order status | `READY_FOR_PICKUP` | Ready for pickup | Warehouse |
| Order status | `SHIPPED` | Shipped | Road |
| Order status | `OUT_FOR_DELIVERY` | Out for delivery | Road |
| Order status | `DELIVERY_FAILED` | Delivery failed | Problem |
| Order status | `READY_FOR_REDELIVERY` | Ready for redelivery | Warehouse |
| Order status | `DELIVERED` | Delivered | Done |
| Order status | `CANCELLED` | Cancelled | Closed |
| Account status | `ACTIVE` | Active (muted text in lists, Section 4.5) | Done |
| Account status | (a 15-minute lock is running) | Locked, 12 min left | Waiting |
| Account status | `LOCKED` (an old stored value) | Locked (old status) | Waiting |
| Account status | `BANNED` | Banned | Problem |
| Account status | `INACTIVE` | Deactivated | Closed |
| Product listing | `ACTIVE` | Active (muted text in lists) | Done |
| Product listing | `INACTIVE` | Inactive | Closed |
| Payment | `PENDING` | Pending | Waiting |
| Payment | `ACCEPTED` | Accepted | Done |
| Refund | `REQUESTED` | Requested | Waiting |
| Refund | `PENDING` | Pending | Waiting |
| Refund | `REFUNDED` | Refunded | Done |
| Refund | `FAILED` | Failed | Problem |
| Payment provider's answer | `UNKNOWN` | Unknown | Closed |
| Shortage case | `OPEN` | Open | Turn (Admin) |
| Shortage case | `WAITING_RESTOCK` | Waiting restock | Waiting |
| Shortage case | `RESOLVED` | Resolved | Done |
| Picking task | `IN_PROGRESS` | In progress | Warehouse |
| Picking task | `PACKED` | Packed | Done |
| Where a package is | `WAREHOUSE` | Warehouse | Warehouse |
| Where a package is | `DELIVERY` | Delivery | Road |
| Scan result | `ACCEPTED` | Accepted | Done |
| Scan result | `REJECTED` | Rejected | Problem |
| Scan result | `REVERSED` | Reversed | Closed |
| Client request | `PENDING` | Pending | Waiting |
| Client request | `APPROVED` | Approved | Done |
| Client request | `REJECTED` | Rejected | Problem |
| Client request | `WITHDRAWN` | Withdrawn | Closed |
| Log result | `SUCCESS` | Success | Done |
| Log result | `FAILURE` | Failure | Problem |
| Failed delivery (built by the page) | not returned yet | Waiting for return | Waiting |
| Failed delivery (built by the page) | a client request waits for a decision | Needs decision | Turn (Admin) |
| Failed delivery (built by the page) | returned, no client request | Waiting for client | Waiting |
| System health | the database answers | Connected | Done |
| System health | the database does not answer | Not connected | Problem |

A word used for several kinds of thing has one family everywhere: `PENDING` is always Waiting, `IN_PROGRESS`
always Warehouse, `ACCEPTED` and `DELIVERED` always Done, `FAILED` and `REJECTED` always Problem, `CANCELLED`
always Closed. An unknown word is Closed. Role names ("Client", "Warehouse Worker", "Delivery Worker",
"Admin") are plain text, never badges.

### 4.3 Choosing the family of a new word

Go down this list and take the first family that fits:

1. **Turn**: a case that stops the normal flow until the reader of this page chooses between outcomes (a
   shortage case, a client request after a failed delivery). Turn is the one family that depends on the
   reader: on the pages of the role that decides, the case is Turn; on every other role's pages the same
   word is Waiting. Normal work in the flow is never Turn: an order that is ready for picking stays
   Warehouse on the Warehouse Worker's pages too, and the person's next step shows as the row's button and
   the menu counter, not as a colour.
2. **Waiting**: waits for somebody else or for time.
3. **Problem**: failed, refused or blocked.
4. **Warehouse**: work is going on inside the warehouse.
5. **Road**: with a Delivery Worker, on the way.
6. **Done**: finished well, or the normal in-use state.
7. **Closed**: ended, switched off, unknown, or a neutral fact.

Write every new word into the table of Section 4.2 (through the system's entry in Section 19).

### 4.4 Flags

A flag is one more fact about a thing, never its state.

| Flag | Family | Meaning |
|---|---|---|
| Low stock | Waiting | The available stock of a product is below its low-stock level |
| Shortfall 2 | Problem | More units are reserved than exist; the number is the difference |
| Open case | Turn (Admin) | An order has a case that waits for a decision |
| Open shortage case | Turn (Admin) | An order has a shortage case that is not closed |
| Review | Turn (Admin) | A delivery failed at least twice |
| Reminder due | Waiting | A returned package has waited 3 days for the client's answer |
| Final review | Turn (Admin) | A returned package has waited 7 days for the client's answer |
| Worker not active | Problem | The worker's account is banned or deactivated |
| Temporary password | Closed | The temporary password of the account has not been used yet |
| Super admin | Closed | The account is the super admin; no other Admin can change it |
| Test mode | Waiting | The system runs in test mode (in the top bar and on Test tools) |
| New | Closed | A notice that was not read yet (notice list, Section 12.18) |

### 4.5 Badge, flag or plain text

- The state of the thing is a badge; one more fact is a flag; everything else is plain text.
- The normal, in-use state of a list ("Active" accounts and products) is muted text: badges are kept for the
  states the reader should notice (locked, banned, deactivated, inactive).
- A badge and a flag never look alike: a badge is a pale pill with a dot; a flag is a white tag with a thin
  edge of its family's dot colour and no dot.
- At most one badge per value; a second status of the same row (payment, refund) is a sub-line with plain
  words ("Refund 12, order cancelled"), not a second pill, unless it is the column's own value.

---

## 5. Typography

| Role | Font | Why |
|---|---|---|
| Everything: titles, UI, body, tables, numbers | **Atkinson Hyperlegible Next** (variable 200-800, bundled with `@fontsource-variable/atkinson-hyperlegible-next`) | Built for legibility: 0 and O, 1, l and I are clearly different; large x-height; tabular figures (`tnum`), which every table and number turns on. Hierarchy comes from size and weight. |
| Raw values and codes typed by machines (log old and new values, temporary password, scanned codes) | **Atkinson Hyperlegible Mono** (variable, `@fontsource-variable/atkinson-hyperlegible-mono`) | Same family, monospaced, clear zero. Never used for labels. |

Fonts are installed from npm and bundled into the build. Nothing loads from an outside address.

### 5.1 Type scale

| Use | Token | Size / line height | Weight |
|---|---|---|---|
| Page title (h1) | `text-page-title` | 24/32 on phones, 28/36 from 768 px, tracking -0.01 em | 700 |
| Section title (h2, for example "Stage 6") | `text-section-title` | 20/28 | 650 |
| Card title (h2 or h3) | `text-card-title` | 17/24 | 650 |
| Title of a part inside a card | `text-part-title` | 15/22 | 650 |
| Body, table cells, inputs | `text-body` | 15/22 (inputs 16 px on phones, so browsers do not zoom) | 400 |
| First cell of a row (the thing's name) | `text-body` | 15/22 | 650 |
| Buttons | `text-body`, small buttons `text-label` | 15/22 and 14/20 | 600 |
| Field label | `text-label` | 14/20 | 600 |
| Column title, sub-line, help text, notes | `text-small` | 13/18 | 400 (column title 600) |
| Status badge | `text-small` | 13/18 | 600 |
| Flag, counter, menu group label | `text-flag` | 12.5/16 | 650 |
| Big number | `text-big-number` | 34/40 (28/32 in the queue panel) | 700 |
| Raw values | `font-mono` | 13/20 | 400 |

The pages without the frame use the page title as well; `text-greeting` (32/40) is defined but not used.

Rules: sentence case everywhere ("Download CSV", "Last login"); only LMS, ID, QR, SKU, IP, HKD, CSV and data
such as `ORD-10229` keep capitals. Tables, counters, pagers and every money value use tabular figures. Numbers
in tables are right-aligned. Reading text stays under 75 characters a line. Headings use `text-wrap: balance`,
paragraphs `text-wrap: pretty`.

---

## 6. Spacing, sizes and radius

- **Base unit 4 px.** Steps used: 4, 8, 12, 16, 20, 24, 32, 40, 48.
- **Page padding:** 16 px (phone), 24 px (from 640 px), 32 px (from 1024 px). Blocks of a page stand 20 px
  apart (24 px from 1024 px).
- **Card:** padding 16 px (phone), 20 px (from 768 px). Card header 14 px above and below.
- **Table:** cells 12 px left and right (the first and last 16 px, 20 px from 768 px), 12 px above and below
  (column titles 10 px); a row of one line is about 47 px high.
- **Controls:** buttons, inputs and selects 40 px high (44 px on touch screens); small buttons 34 px (40 px on
  touch). Every touch target is at least 40 px, with 8 px between targets. On touch screens a text link gets a
  40 px tall hit area without moving the line.
- **Frame:** side menu 256 px wide from 1024 px; top bar 60 px high (56 px on phones).
- **Windows:** 520 px wide (wide windows 640 px), full width minus 16 px on phones, scrolling inside.
- **Radius:** `chip` 8 px (flags, small boxes), `control` 10 px (buttons, inputs, menu lines), `card` 14 px
  (cards, menus, toasts), `window` 18 px (windows), full (badges, counters, avatars, segmented filters).

---

## 7. Elevation

Warm, soft and rare. Cards are flat: a 1 px `line` hairline and no shadow. Only things that float have a
shadow.

| Token | Value | Use |
|---|---|---|
| (none) | 1 px `line` border, no shadow | Cards, tables, panels |
| `shadow-raised` | `0 10px 28px -8px rgb(36 32 28 / 0.20), 0 2px 6px rgb(36 32 28 / 0.06)` | Actions menus, toasts, the skip link |
| `shadow-window` | `0 28px 56px -16px rgb(36 32 28 / 0.32), 0 4px 12px rgb(36 32 28 / 0.08)` | Windows |
| Backdrop | `rgb(23 32 30 / 0.45)` | Behind a window or the drawer |
| Pinned table column | 1 px line and a soft 8 px shadow on its inner side | The first and the Actions column of a table that scrolls sideways |

---

## 8. Motion

Only state changes move. Nothing loops or bounces, nothing moves because the page scrolled, and nothing slides
in when a page opens.

| What | Motion | Time and easing |
|---|---|---|
| Hover on buttons, links, rows, menu lines | Colour change | 120 ms ease-out |
| Button press | Moves 1 px down | at once |
| Window opens | Fades in, grows from 97 % to 100 %; backdrop fades | 180 ms `cubic-bezier(0.2, 0.7, 0.2, 1)` (`ease-calm`) |
| Actions menu opens | Fades in and moves 4 px down | 140 ms ease-out |
| Toast appears | Fades in and moves 8 px down; leaves after 6 s, waits while the pointer rests on it, leaves 3 s after the pointer moves away | 200 ms |
| Tab change | The teal underline slides to the new tab (`transform` only) | 160 ms |
| Progress bar fills | The fill is uncovered with `clip-path`, keeping its rounded end | 400 ms ease-out |
| Side menu drawer (phones, tablets) | Slides in from the left | 200 ms |
| A decided row | Its background fades from pale green to white | 2.4 s ease-out |
| Busy button | A small spinner turns | 0.8 s, only while the request runs |

With `prefers-reduced-motion: reduce` every duration becomes 0.01 ms. Scroll reveals, counting-up numbers and
parallax are not used: in a working tool, content must be there at once.

---

## 9. Layout, breakpoints and devices

### 9.1 Breakpoints

| Width | Frame | List tables | Work tables |
|---|---|---|---|
| under 768 px (phones) | Top bar with "Menu"; the side menu is a drawer over the page | Cards: one row is one card, each value with its column title above it, two values side by side | Cards, as for list tables |
| 768 to 1023 px (tablets) | Same as phones | Real tables; a wide one scrolls sideways inside its card with the first and the Actions column pinned | Cards, four values to a line, decision buttons in one row |
| 1024 to 1279 px | Fixed side menu (256 px) and top bar | As on tablets | Cards (the work area is 704 to 960 px wide, too narrow for facts and decision buttons side by side) |
| 1280 px and more | Same | Real tables that fit without scrolling | Real tables that wrap instead of scrolling |

### 9.2 Layout rules

- The work area is at most 1400 px wide and starts at the left. The page never scrolls sideways.
- The page header puts its buttons beside the title from 768 px and below it on phones (full width).
- Two cards side by side only from 1024 px; below that they stack in reading order (the decision first).
- Tiles or number lines stand three side by side only from 1280 px; below that they are full-width lines.
- A card header keeps its controls on the right when they fit and starts them at the left edge when they wrap.
  A quiet text button in a header is pulled out by its own padding (`-mx-2`), so its words line up with the
  card's edge.
- A list table has a minimum width that fits the work area at 1280 px (940 px is the widest used).

### 9.3 Devices by role

Plan every page for the main device of its role first, then check every width. These are the team's working
assumptions; the role's owner confirms them.

| Role | Main device | What follows |
|---|---|---|
| Admin | Desktop or laptop (1280 px and more), sometimes a phone | Real tables from 1280 px; cards below; the dashboard puts the work queue first |
| Client | Phone first, also desktop | One column on phones; the summary of an order stands under the form on phones and beside it from 1024 px; the one main action (pay, choose redelivery) is visible without searching |
| Warehouse Worker | Tablet or handheld scanner (768 to 1024 px), also desktop | Touch targets 44 px; the scan box at the top of a task page; work rows as cards below 1280 px |
| Delivery Worker | Phone (390 px), on the move | Every list is cards; actions at the bottom of each card; capture boxes full width; nothing depends on hover |

---

## 10. The frame

Every page after log in sits in the same frame. Reference: `src/components/Frame.tsx`.

### 10.1 Side menu

- Evergreen background, cream words. At the top the **wordmark**: the route mark (three stops on a line, the
  last one apricot) and the system name. The names are "LMS Admin", "LMS Client", "LMS Warehouse" and
  "LMS Delivery". The wordmark carries `translate="no"`.
- Entries: a Lucide icon (18 px) and words. The start page comes first, without a group label. The other
  entries stand in groups named by the kind of work (Admin: "Work to do", "Manage", "Look up", "Testing").
  The first group, "Work to do", holds the pages where work waits for the person. The group "Testing" with
  "Test tools" comes last and only in test mode.
- Current entry: `evergreen-active` background, a 3 px apricot bar on its left edge, bold words,
  `aria-current="page"`. A detail page keeps its list's entry current.
- **Counters** only on the entries of "Work to do": an apricot pill with ink numbers, "99+" above 99, none at
  0, and an `aria-label` such as "Delivery cases, 3 waiting".
- **Today block** at the foot of the menu: a truthful count of the person's work today with a progress bar
  (Admin: "2 decided, 8 still open"). A role shows it only when the count comes from real data.
- From 1024 px the menu is fixed and 256 px wide; below 1024 px it is a drawer (a `<dialog>`) opened by the
  "Menu" button, closed by "Close menu", Escape or choosing a page. The drawer's foot holds "Logged in as",
  the username, "Change password" and "Log out".

### 10.2 Top bar

Linen background, a hairline below. From left to right: "Menu" (below 1024 px), the flag "Test mode" (only in
test mode), a spacer, the session timer ("Session ends in 14 min"; only the last two minutes count seconds,
"1:45", on an apricot pill), the user chip (initials and username), the link "Change password" and the small
button "Log out". On phones the bar keeps "Menu", "Test mode", the timer as "14 min" and "Log out"; the rest
moves into the drawer.

### 10.3 Session ending

Where a role has a timed session, two minutes before it ends a window "Session ending soon" says when it
ends, with "Stay logged in" (main) and "Log out". When the time runs out the person lands on the log in page
with a notice that says why. Each request the person makes starts the timer again.

### 10.4 Skip link and landmarks

"Skip to main content" is the first focus stop; it slides in on focus and leads to `main`. The frame uses the
landmarks `nav` (named, for example "Admin pages"), `header` and `main`.

---

## 11. Page kinds

Every page is one of these kinds and starts with the page header (Section 12.1).

### 11.1 Work page

Cases or tasks the reader must decide or act on (Admin: Warehouse cases, Delivery cases, Refunds).

- Page header: the title and a count line that says what waits ("3 need a decision").
- One card per kind of case: a card header with the title, the count and at most one control (a filter or
  "Download CSV"); under it a segmented filter with counts; an optional strip with a fixed rule (a note); then
  the work table (cards below 1280 px).
- Rows that wait for the reader come first. Each row shows the facts that the decision needs and its buttons.
- After a decision the row keeps its place for this visit, shows its outcome badge and a line such as
  "Waiting restock, by admin_main, 2026-11-12 09:16", and a toast names the result.
- A blocked decision keeps its button (blocked) with the reason and a link to where it is fixed; the page it
  leads to highlights that row and offers a way back ("Back to delivery cases").
- An empty queue shows "All clear" and one sentence of what that means.

### 11.2 List page

A long list people search (Admin: Users, Products, Orders, Logs).

- Page header: the title, a count line ("42 accounts", or "12 orders of 265" while filtered), the actions of
  the page ("Download CSV", and the main button that creates a new thing, "Create user").
- One card: the filter bar (a search box and labelled filters, "Clear" while a filter is on), an optional
  strip with a note, the list table, the pager.
- Each row is a link to its detail page or has one "Actions" menu. Filters, tabs and the page number live in
  the address, so a reload or a shared link keeps them.

### 11.3 Detail page

One thing, opened from a list (Admin: the order page).

- "Back to …" above the title; the title names the thing ("Order ORD-10229") with its state badge beside it;
  a count line with its key facts ("Client client_0459, HKD 80.00").
- First the journey or progress steps with a "Now:" line that says where the thing is and, when something
  waits, links to where it is decided.
- Then cards: a fact card first, then the parts of the thing as cards with small tables, a timeline last.
  Two short cards may stand side by side from 1024 px.

### 11.4 Form page

Entering something that needs more room than a window (for example placing an order, recording a delivery
outcome, a stock count). Not used by the Admin pages yet.

- One card per group of fields. A summary card stands beside the form from 1024 px and under it on phones.
- One main button at the end of the last card (or in the summary card), named with the action ("Place
  order").
- Errors stay next to their fields; after a refused save, focus moves to the first field to fix.
- Prefer one page of cards to steps. When steps cannot be avoided, the count line says "Step 2 of 3: Delivery
  details".

### 11.5 Start page

The first page of a role (Admin: Dashboard).

- Page header: the title and one count line ("8 cases wait for a decision", or "Nothing waits for a decision.
  All clear."), "Last loaded 09:14" and "Refresh".
- First the work queue panel ("Work to do"): one line per queue with what it holds, its count and "Open" to
  the filtered work page; "Also check" lines under it.
- Beside it from 1024 px: today's progress ("2 decided"), then reference blocks (system health).
- Then the route of the role's things (Section 12.11) and "keep an eye on" lists (low stock). Every number is a
  link to its list. Nothing decorative.

### 11.6 Tools page

Test tools (Admin: Test tools). Only in test mode, in the menu group "Testing".

- Page header with the flag "Test mode" and one line on what the tools do.
- A stage list (sticky beside the tools from 1280 px, a select "Go to a stage" below that) and one section per
  stage.
- One card per tool: title, one sentence, "How to use" (a disclosure with Creates, Before, See it on, Next),
  the fields, one button named with the tool, and the result as a message inside the card naming what was
  made. A tool that removes data is a danger card with a typed confirmation ("Type REMOVE to confirm").

### 11.7 Page without the frame

Log in, welcome, change password, and other pages that stand alone (reference: `src/pages/AuthLayout.tsx`).

- From 1024 px an evergreen brand panel (wordmark, "Logistics Management System", a drawn delivery round)
  stands beside the form; on phones the panel is a band above it.
- The form is one column, at most 420 px wide. A message place sits above the form and is always in the page
  (it starts empty), so its messages are read out.
- A page shared by all roles shows "LMS" and "Logistics Management System" without a role name; a role's own
  app may show its wordmark.

### 11.8 States every page needs

| State | What the page shows |
|---|---|
| Loading | "Loading the page…" where the content will be |
| Empty | An empty state or "All clear" (Section 12.12) |
| Filtered to nothing | "No … match these filters." and "Clear filters" |
| Refused | The message of the refusal inside the window or above the form; focus on the field to fix |
| Blocked | The blocked button and its reason line |
| Not found | "Page not found": "This address does not lead to an … page." and a way back |
| Not allowed | The welcome page with "You are not allowed to open that page." |
| Long content | Long names wrap; codes and dates never break inside; nothing is cut off with "..." |

### 11.9 Hints for the pages of the other roles

From the team's general design note (`lms_redesign_prototype/DESIGN_NOTES_GENERAL.md`, Section 8.2), mapped to
these page kinds. They are hints, not decisions: each role's owner decides its pages from its backend context.

| Role | Page (hint) | Kind | Parts |
|---|---|---|---|
| Client | Place an order | Form page | Card "Products" with a list table and a quantity field per row; card "Delivery details"; a summary card with the total, a note on the payment and the main button |
| Client | My orders | Work page | Work table: Order, Status (order badge; payment and refund as sub-lines), Actions; orders that wait for the client first |
| Client | Order (detail) | Detail page | Progress steps, the facts, the items with the summary; "Cancel order" as a danger button that opens a confirm window whose dismiss button is "Close" |
| Client | Notifications | List page | One card with the notice list |
| Client | Payment | Form window or page | Summary of the amount, messages for success and failure, a note when it is a test payment |
| Warehouse Worker | Picking queue | Work page | Work table: Order, Items, Status, Actions ("Start picking", "Continue picking") |
| Warehouse Worker | Picking task | Detail page | Fact list, a progress bar with its words ("3 of 5 picked"), the scan box, a work table of the items with picked and ordered quantities; the main button finishes the task, another button reports a problem |
| Warehouse Worker | Stock count | Form page | One card of fields and one main button |
| Warehouse Worker | Packing | Confirm window | Names the order and what happens next |
| Delivery Worker | Pickup | Work page | The scan box, then the packages with the buttons that record each result |
| Delivery Worker | My deliveries | Work page | Order, Delivery details (this role may see them for its own deliveries), State, Actions |
| Delivery Worker | Record outcome | Form page | Tabs "Delivered" and "Failed"; "Delivered": two capture boxes (signature, photo); "Failed": a reason select and a text field; one main button that opens a confirm window |
| Delivery Worker | Delivery (detail) | Detail page | The attempts as a list table or a timeline |
| Every role | Start page | Start page | What waits for the person first, every count a link |

---

## 12. Parts

Each part is a React component of the reference build (file in brackets). The class recipes are in
Appendix C.

### 12.1 Page header (`ui.tsx`: `PageHeader`)

The title (h1), at most one count line under it, an optional extra line (a note), and the page's buttons on
the right (below on phones, full width). A detail page has its back link above the title ("Back to orders").

### 12.2 Card (`ui.tsx`: `Slip`, `SlipHeader`, `SlipStrip`, `SlipBody`, `Part`)

- White, 14 px radius, 1 px `line` edge, no shadow: a slip of paper on the linen desk.
- Optional **header**: the title (h2, `text-card-title`), a count pill, controls on the right.
- Optional **strip** under the header: a sunken band for a note or a fixed rule.
- A **part** inside a card is a framed block with a sunken head holding its title and count ("Delivery
  attempts 1"). Cards are never nested inside cards; parts are the one inner frame.
- A card is never clickable as a whole; its links and buttons are.

### 12.3 Buttons (`ui.tsx`: `Button`, `buttonClass`)

| Level | Look | Use |
|---|---|---|
| Main | Teal fill, white words | At most one per page and one per window |
| Secondary | White fill, `field-line` edge, ink words | Every other action |
| Quiet | No fill, teal words | "Clear", "Show all", text-like actions |
| Danger (outline) | White fill, `field-line` edge, red words; red edge and `danger-tint` fill on hover | Row actions that end something and open a window ("Cancel and refund"); quiet, so a table of them does not shout |
| Danger (filled) | Red fill, white words | Only the confirm button of a dangerous window ("Ban user") |

- Sizes: normal 40 px (44 px on touch); small 34 px (40 px on touch) for buttons in rows and card headers.
- An icon (16 px) may stand before or after the words, never alone.
- States: hover darker, pressed 1 px down, focus ring; switched off: sand fill, muted words, the "not
  allowed" pointer.
- **Busy:** keeps its width, shows a spinner and words that end with an ellipsis ("Saving…").
- **Blocked:** an action the rules do not allow yet ("Approve redelivery" while the order has an open
  shortage case) looks switched off but stays a Tab stop with `aria-disabled="true"`; the line under it says
  why ("Decide the open shortage case first.") and links to where that is decided.
- "Download CSV" (with a download icon) downloads the rows the filters show.

### 12.4 Links (`ui.tsx`: `TextLink`, `ButtonLink`)

- A text link is teal, semibold, underlined with a pale teal line 3 px under the words, darker on hover; it
  never breaks inside ("ORD-10229").
- A link that looks like a button (`ButtonLink`) uses the button levels; "Open" on the start page is one.
- Every move to another page is a real link, so opening it in a new tab works.

### 12.5 Fields (`form.tsx`)

- `TextField`, `TextAreaField`, `SelectField` and `CheckboxField`: the label above (14/600), then the input,
  a help line (13, muted) and an error line (13, red, with an alert icon). "(optional)" follows the label of
  an optional field.
- The placeholder only shows an example ("e.g. test_client_4"), never the label. A search box with a
  magnifier icon is the one exception: its placeholder says what it searches and ends with an ellipsis
  ("Search username…").
- Selects fill their field and show a chevron. Tick boxes are 20 px, and the whole line of words is the hit
  area.
- Every field has a `name`; fields that are not for signing in have `autocomplete="off"`; usernames, codes and
  searches have `spellCheck={false}`.
- Errors set `aria-invalid="true"` and are linked with `aria-describedby`. After a refused save, focus moves to
  the first invalid field (`useFocusFirstInvalid`).
- **Reason field** (`ReasonField`): "Reason", help "At least 5 characters.".
- **Filter bar:** every filter has its label above it (From, To, Actor, Action, Target, Target ID); "Clear"
  appears while a filter is on.
- **Password field** (`PasswordInput`): a "Show"/"Hide" button with words, the rules ticked while typing, and a
  strength scale that only advises (amber or green, never red).
- **Secret box:** a value shown once (a temporary password) in the mono font with "Copy"; "Copied." appears
  inside the window.

### 12.6 Badge, flag and count pill (`ui.tsx`: `StatusBadge`, `Flag`, `CountPill`)

- **Badge:** a pale pill of its family with a 7 px dot and its words (13/600), 24 px high.
- **Flag:** a white tag, 8 px radius, a 1 px edge of its family's dot colour, its family's text, no dot
  (12.5/650), 20 px high.
- **Count pill:** a sunken pill with a muted tabular number beside a card or part title.

### 12.7 Tables and cards (`DataTable.tsx`, `Pager.tsx`, `SegmentedFilter.tsx`)

- **List table:** a sunken header row with 13/600 muted column titles, rows split by hairlines, a hover
  tint, the first cell bold. Numbers right-aligned and tabular. A table that is wider than its card scrolls
  inside it with the first column and the Actions column pinned, and the scroll box is a named region that
  the keyboard can scroll.
- **Work table:** each row shows the facts and the decision buttons of that row. A cell holds a main line and
  sub-lines (`CellMain`, `CellSub`). Rows that wait for a decision come first. A row reached from a link on
  another page is highlighted with a pale apricot tint and scrolled into view.
- **Cards (phones and narrow screens):** one card per row: the title value at the top, the status badge at its
  right, the other values as label and value, two to a line (four from 768 px; a wide value takes two),
  buttons at the bottom in a wrapping row. Columns choose how they appear on a card: `title`, `aside`,
  `field`, `wide`, `actions` or `hidden`. List tables turn into cards below 768 px, work tables below 1280 px
  (`cardsUntil="xl"`).
- **Segmented filter:** pill buttons with counts ("Needs decision 3", "All 6"), `aria-pressed`; the chosen one
  teal tint. Up to about five filters.
- **Pager:** "Page 1 of 3", "Previous", page numbers (the current one teal), "Next"; 20 rows a page.
- **Empty table:** Section 12.12.
- **Totals** of a table (the items of an order) are a last row with "Total" and the amount, bold.

### 12.8 Actions menu (`ActionsMenu.tsx`)

One small "Actions" button with a chevron per row of a long list. It opens a menu of worded lines: normal
lines first, a divider, then dangerous lines in red. The button is named after its row ("Actions for
client_0450"). Arrow keys move between lines; Escape, a click outside and scrolling the button out of view
close it; focus returns to the button. The menu flips above its button when there is no room below and never
leaves the window. A row with nothing to change shows a muted line instead ("Your own account").

### 12.9 Messages (`Toast.tsx`; `ui.tsx`: `InlineMessage`, `MessagePlace`, `Note`)

- **Toast** after a decision on a page: top right on desktop, top centre under the top bar on phones; an icon
  (success, error or information) and words that name the thing and the result; it leaves after 6 s and waits
  while the pointer rests on it; `role="status"` in a polite live region. Never shown while a window is open.
- **Inline message:** a tinted box of its kind with an icon, inside a card or a window.
- **Message place:** an always-present live region that holds the message of a form or window, so a screen
  reader reads it when it appears.
- **Note:** muted words with an information icon, for fixed rules ("Stock is changed only by Warehouse.").

### 12.10 Windows (`Dialog.tsx`, `ConfirmDialog.tsx`)

- The native `<dialog>` opened with `showModal()`: focus moves inside, Tab stays inside, Escape closes, focus
  returns to the opener. A click on the backdrop does nothing. 520 px wide (640 px for wide windows).
- The title names the action and the thing, and a confirm window's title asks it ("Wait for restock for order
  ORD-10229?"). The body says what happens. The footer has the dismiss button on the left of the confirm
  button; on phones the buttons stack full width with the confirm button on top.
- The dismiss button says "Cancel", or "Close" when the confirm button itself starts with "Cancel" ("Cancel
  and refund").
- A window with a reason field or a needed tick box keeps its confirm button switched off until the reason has
  5 characters and the box is ticked; until then a muted line above the buttons says what is still missing
  ("Tick the box and type a reason of at least 5 characters to continue."), linked to the button.
- A refusal is shown inside the window, which stays open. A success closes the window and shows a toast.
- A window that changes money or ends an order lists the facts the decision changes (Admin: Payment, Refund
  and Outcome, and which client request it withdraws; `CancelFacts.tsx`).

### 12.11 Start-page and progress parts (`Route.tsx`, `ui.tsx`: `ProgressBar`)

- **Queue panel** ("Work to do"): one line per work queue with an icon tile (from 640 px), its name, a plain
  line on what it holds, its count (big, tabular) and "Open"; "Nothing waits here." at 0; "Also check" lines
  below.
- **Route** (the signature of this design): the order statuses as stops on a line. A stop is a 16 px ring in
  its family's dot colour, its words and its count; "Delivered" ends the line with a 20 px ring and a muted
  count (finished work, not work to do). A stop where things wait for the reader carries an apricot "3 need
  you →" link to where they are decided. Failed deliveries branch off under "Out for delivery" on a thin red
  rule. Vertical on phones. Used on start pages and detail pages only.
- **Journey (progress steps)** of one thing: the steps in order, joined by a line that is teal where the thing
  has passed and dashed where it has not; a done step is a teal circle with a white tick, the current step an
  apricot ring with an apricot dot, a problem step a pale red circle with a red cross, an ended step (cancelled)
  a stone circle with a cross, a step not reached yet an empty grey ring; the time of each step under it; the state is also written for screen readers ("done", "now", "problem",
  "not reached yet"). A "Now:" line under the steps says where the thing is and links to where it is decided.
- **Today's progress:** an apricot-tint band with "2 decided" and a bar "2 of 10 cases decided, 8 still open".
- **Progress bar:** `apricot-fill` (or teal) on the `track`, 8 px high, always with its words beside it, which
  the bar names with `aria-labelledby`.
- **Number lines** (low stock): the name and its fact on the left, the count big on the right, each line a
  link to the filtered list.

### 12.12 Empty and loading states (`ui.tsx`: `EmptyState`, `AllClear`)

- **Empty state:** a muted icon, one sentence ("No users match these filters."), and "Clear filters".
- **All clear:** a pale green circle with a green tick, "All clear", and one sentence of what that means ("No shortage case
  waits for a decision."), with a quiet button to see the rest ("Show resolved cases").
- **Loading:** "Loading the page…" where the page will be.

### 12.13 Other parts

- **Tabs** (`Tabs.tsx`): only for views of the same list (the three logs); a teal underline under the chosen
  tab; arrow keys, Home and End move between tabs; the tab lives in the address.
- **Fact list** (`ui.tsx`: `FactList`): label (13, muted) above its value (15, medium), in 1 to 3 columns.
- **Time** (`ui.tsx`: `TimeText`): `2026-11-12 09:14` in a `<time>` element, never broken.
- **Empty value** (`ui.tsx`: `Empty`): a muted "-" that screen readers read as "None".
- **Timeline** (`Timeline.tsx`): grouped by day (a date chip), then time, text and who did it.
- **How to use** (`HowToUse.tsx`): a disclosure with worded lines.
- **QR label** (`QRLabel.tsx`): the QR code of a SKU with the SKU and the name under it, "Print label".

### 12.14 Scan box (for Warehouse and Delivery pages; not built yet)

The place where a code is scanned or typed.

- A field labelled with what is scanned ("Scanned code", "Package code"), 48 px high, the code in the mono
  font, `autocomplete="off"`, `spellCheck={false}`, and a secondary button "Check code" beside it (below it on
  phones). Enter checks the code as well.
- Focus moves into the field when the page opens only on a page whose one job is scanning; after each scan the
  field empties and keeps focus, so a hardware scanner can send the next code.
- The answer appears under it in a message place: accepted, as a success message that names the thing and
  the progress ("SKU-1002 Gel pens 0.5 mm, black: 2 of 3 picked"); refused, as an error that names why and
  what to do next ("SKU-1009 is not in this order. Put it back and scan the next item.").
- Under it "Last scans" (up to five): time, code and the result badge (Accepted, Rejected, Reversed).
- Codes that are not known are shown as "[unknown code]", never as the raw scanned text.

### 12.15 Capture box (for Delivery pages; not built yet)

The place where photo or signature proof appears.

- A sunken box, at least 160 px high, 1 px `field-line` edge, 10 px radius, with a muted line ("No photo yet")
  and a secondary button "Take photo" (on phones it opens the camera: a file input with `accept="image/*"` and
  `capture="environment"` behind the button) or "Add signature" (a window with a drawing area, "Clear" and
  "Save signature").
- After capture the picture fills the box (whole, not cropped) with "Take again" and "Remove".
- The rule "signature or photo" decides the record button: blocked, with the reason "Add a signature or a
  photo first.". Drawing needs a pointer, so the photo is always offered as the other way.

### 12.16 Quantity field in a row (not built yet)

A number field 96 px wide (full width on cards), the number right-aligned and tabular, `inputMode="numeric"`,
16 px text on phones, its limit as a help line ("Up to 3"). An error stands under its row.

### 12.17 Summary (not built yet)

Lines of a name and an amount (an order before payment): names left, amounts right-aligned and tabular, the
total last, bold, above a `line-strong` rule ("Total HKD 285.00"). Money as in Section 13.5.

### 12.18 Notice list (not built yet)

Messages to the person who is logged in, inside one card: each notice is a row with its title (bold), the flag
"New" while unread, one line of text (muted) and its time on the right. The row is a link to the thing it is
about. A quiet "Mark all as read" sits in the card header.

---

## 13. Words

Rules of voice and copy for every page. They also follow the copy rules of the taste skill for app pages:
buttons are a verb and what the person gets, headings name things, labels say what goes in, errors name the
fix.

### 13.1 Voice

- Plain English, short sentences, sentence case, active voice.
- Speak to the reader as "you" ("Your own account"); never "we" or "I".
- Staff pages use the LMS words of the glossary (Section 13.7). Client pages use everyday words for the same
  things and never internal names (shortage case, held package, instruction, picking task, audit log).
- Encouraging, never cheesy: "All clear", "2 decided". No exclamation marks, no jokes, no slang.

### 13.2 Buttons and links

- A verb and the thing, 1 to 5 words: "Approve redelivery", "Download CSV", "Wait for restock".
- The same action has the same words everywhere ("Record request" on every page that records a request).
- Verbs in use: Approve, Reject, Record, Send, Reassign, Wait for, Cancel and refund, Cancel order, Retry,
  Check, Create, Add, Edit, Save, Import, Download, Print, Copy, Open, Show, Clear, Log in, Log out, Change
  password, Stay logged in.
- "Cancel" and "Close" only dismiss a window.
- Never: OK, Yes, No, Submit, Continue, Learn more, Read more, Click here, Discover, Explore, More.
- A link names where it goes ("Decide shortage case 9", "Back to orders", "Open all orders").

### 13.3 Headings and titles

- A page title is the words of its menu entry. A detail page names the thing ("Order ORD-10229").
- A card title names what it holds ("Failed deliveries", "Held packages"), 1 to 6 words.
- No questions as headings; the one question is a confirm window's title ("Approve cancellation of order
  ORD-10227?").
- No colons, no figures of speech, no capitalised invented names.

### 13.4 Labels, help, errors and success

- A label says what goes in, as a noun ("Username", "Missing quantity"); a short question only when the field
  asks how something was done ("How was the person checked?").
- Help says the rule: "At least 5 characters.", "From 1 to the number ordered.".
- An error names the field's problem and what to type: "Type a number from 1 to 5.", "Use 4 to 30
  characters: letters, digits and underscore." Never only "Invalid".
- A refusal names what happened and the next step: "This case was already handled. Please refresh the page."
- A success names the thing and the result in the words of its button: "The shortage case of order
  ORD-10229 now waits for restock.", "Order ORD-10225 was cancelled. A full refund was requested."
- A blocked action says what to do first: "Decide the open shortage case first."
- A requirement line says what is still missing: "Type a reason of at least 5 characters to continue."

### 13.5 Formats

| Value | Format | Note |
|---|---|---|
| Money | `HKD 1,234.50` | A no-break space after HKD; always two decimals; tabular |
| Time | `2026-11-12 09:14` | The browser's own time, no zone label; never broken inside |
| Date | `2026-11-12` | Date chips of a timeline |
| Clock | `09:14` | "Last loaded 09:14" |
| Age | "2 days 3 hours", "5 hours" | Beside its time ("since 2026-11-10 06:10") |
| Count | "3 of 5", "1 failure", "2 failures" | Singular and plural spelled out; numerals, never words |
| Minutes | "15 min", "Locked, 12 min left" | A no-break space before "min" |
| Order | `ORD-10229`; test orders `TEST-0007` | Capitals as stored |
| Product | `SKU-1001` | Capitals as stored |
| Package | `PKG-10229` | Capitals as stored |
| Username | `admin_main` | As typed; never capitalised |
| Quotes and apostrophes | “box was crushed”, client’s | Curly in copy; raw values keep their own characters |
| Running work | "Saving…", "Loading the page…" | One ellipsis character |
| File size | "1 MB" | A no-break space |

### 13.6 Empty values and empty states

- An empty value is a muted "-" (screen readers hear "None").
- An empty card says what is missing in one sentence: "No refund.", "Nothing waits here.", "Not run yet".
- A filtered list with no rows: "No … match these filters." with "Clear filters".
- An empty work queue: "All clear" and one sentence.

### 13.7 Glossary

Use these words, with these meanings, on every page.

| Word | Meaning | Who sees it |
|---|---|---|
| Order | What a client buys: products and quantities, one package, one total with delivery included | Everyone |
| Order reference | The name of an order (`ORD-10229`) | Everyone |
| Item | One product line of an order, with its quantity | Everyone |
| Product, SKU | A thing the retailer sells; SKU is its code | Everyone |
| Physical, reserved, available stock | Units on the shelves; units held for orders; physical minus reserved, never below 0 | Staff |
| Low stock | Available stock below the product's low-stock level | Staff |
| Shortfall | More units reserved than exist | Staff |
| Picking task | The work of picking one order's items | Warehouse Worker, Admin |
| Shortage case | Missing or damaged stock found while picking, waiting for the Admin's decision | Warehouse Worker, Admin |
| Wait for restock / Cancel and refund | The two decisions of a shortage case | Admin |
| Package, package code | The one package of an order and its code (`PKG-10229`) | Staff |
| Pickup list | The packages that one Delivery Worker collects | Warehouse Worker, Delivery Worker, Admin |
| Held package | A package that a Delivery Worker holds | Delivery Worker, Admin |
| Delivery attempt | One try to deliver a package, with its outcome | Staff; the client sees the outcome |
| Failed delivery | A delivery attempt that did not reach the client | Everyone |
| Return | A failed package going back to the warehouse | Staff; the client sees "back at the warehouse" |
| Client request | The client's choice after a failed delivery: redelivery or cancellation | Everyone |
| Redelivery | Delivering the package again after a return | Everyone |
| Refund | Money going back to the client's payment | Everyone |
| Payment provider | The outside service that takes the payment | Admin |
| Test mode, test data | The system state that shows Test tools; data made by them (`test_` usernames, `TEST-` references) | Staff |
| Super admin | The first Admin, which no other Admin can change | Admin |
| Temporary password | A password an Admin gives, which must be changed at the next log in | Everyone |
| Session | The time a person stays logged in without a request | Everyone |

### 13.8 Words never used

Metaphors and "where X meets Y"; slogans; questions as headings; exclamation marks; capitals for labels (only
the codes of Section 5); invented capitalised concepts; filler and hype (seamless, effortless, cutting-edge,
unlock, elevate, supercharge, reimagine, next-generation, game-changing, powerful, robust); virtue words about
the system (honest, calm, quiet, simply, actually); "Oops" and apologies in place of a reason.

---

## 14. Privacy and roles

- Each page shows only what its role needs. The role's backend context decides who may see what; the page
  never shows more.
- Admin pages never show a client's address, phone number, recipient name or delivery proof. Where a person
  might look for them, a note says so: "Address, phone number, recipient name and delivery proof are not shown
  to Admin."
- A Delivery Worker's pages may show the delivery details of that worker's own deliveries (team general
  design note).
- A client's pages show that client's own orders only.
- Values that look private in logs and notes are hidden: a run of 8 or more digits becomes "[hidden number]",
  private fields become "[hidden]" (`src/api/privacy.ts`).
- Private data never goes into the address (URL), page titles, CSV names or console output.
- Sample data uses invented people and test markers only (Section 17.4).

---

## 15. Accessibility

- Text 4.5:1, control edges and meaningful graphics 3:1 (every token pair is measured in Section 3).
- Visible focus on every control: 2 px teal outline, 2 px away (apricot inside the side menu). Never removed
  from a control; only `main` and jump targets (`tabIndex={-1}`) have no ring.
- The skip link first; landmarks `nav`, `header`, `main`; one h1 per page; headings one level at a time.
- Every button and link has a name; row buttons and Actions buttons are named with their row ("Reject request
  for order ORD-10227", "Actions for wh_picker_01").
- Every field has a visible label, a `name` and its help and error linked; after a refused save focus moves to
  the first invalid field.
- Windows trap focus and return it; Escape closes windows and menus; tabs use `role="tablist"` with arrow keys.
- Toasts and form messages sit in polite live regions that are always in the page.
- A table that scrolls sideways is a named region that the keyboard can scroll; on cards every value keeps its
  column title.
- `prefers-reduced-motion` turns motion off. Zoom to 200 % keeps everything usable.
- Touch targets at least 40 px (44 px for controls on touch screens), 8 px apart; nothing depends on hover.
- A state is never shown by colour alone: badges have words, progress steps have hidden words.
- Gestures always have another way: a signature can be replaced by a photo, a drag by a button.
- `translate="no"` on the wordmark and raw values; `lang="en"` on the page.

---

## 16. Anti-patterns

- A second design system, palette, font, radius scale or button style for one role.
- A colour for a role; colour as decoration; status colours as card or page backgrounds.
- Emoji or drawn icons in place of the Lucide icons (18 px in the menu, 16 px in buttons, 24 to 28 px in empty
  states, the library's own stroke).
- Icon-only buttons; placeholder-only labels; text cut off with "...".
- Tabs or long pages that hold different jobs; a detail page in the side menu.
- Red for anything that is not loss or error; a filled red button outside a confirm window.
- Scroll reveals, counting-up numbers, parallax, looping or bouncing motion.
- Grey text under 4.5:1; text under 12 px.
- Layout shift on hover (no scaling cards); cards that are clickable as a whole and also hold buttons; cards
  inside cards.
- A coloured stripe on the left of cards, rows or messages.
- A table that scrolls sideways on a phone; a decision button scrolled out of view.
- A success message for something that did not happen; progress that is not counted from data.
- Dark mode (every LMS page is light).

---

## 17. Engineering

### 17.1 Stack

React 19.3, TypeScript 7 (`tsc --noEmit` in the build), Vite 8 with `@vitejs/plugin-react`, Tailwind CSS 4.3
(`@tailwindcss/vite`, tokens in `@theme`), `react-router` 8 with a `HashRouter` (the build works from any
static folder), `lucide-react` icons, the two Atkinson Hyperlegible fonts from `@fontsource-variable`, and
`qrcode` (loaded only where a QR label is shown). Python with Playwright runs the checks.

### 17.2 Files of the reference build to reuse

| Path in `GrandProject/AdminWebApp/` | What it holds |
|---|---|
| `design-system/lms-admin/tokens.css` | The tokens (Appendix A) |
| `src/index.css` | Base styles: type, focus ring, windows, motion, scroll boxes, print, reduced motion (Appendix B) |
| `src/components/ui.tsx` | Buttons, links, badges, flags, cards, messages, facts, times, progress, page header, empty states |
| `src/components/form.tsx` | Fields, selects, tick boxes, reason field, password input |
| `src/components/DataTable.tsx`, `Pager.tsx`, `SegmentedFilter.tsx` | Tables, cards, pager, filters |
| `src/components/Dialog.tsx`, `ConfirmDialog.tsx` | Windows |
| `src/components/Toast.tsx` | Toasts |
| `src/components/ActionsMenu.tsx`, `Tabs.tsx`, `Timeline.tsx`, `HowToUse.tsx`, `QRLabel.tsx`, `Route.tsx` | The other parts |
| `src/components/Frame.tsx`, `src/pages/AuthLayout.tsx` | The frame and the pages without the frame (change the menu entries, the wordmark and the session parts for the role) |
| `src/lib/format.ts`, `status.ts`, `csv.ts`, `useListParams.ts`, `useMediaQuery.ts`, `useFocusFirstInvalid.ts`, `cn.ts` | Formats, status words, CSV, list state in the address, breakpoints, focus after a refusal, class joining |
| `src/api/` | The stand-in API with sample data (a pattern to follow, not to copy) |
| `tests/flows.py`, `screenshots.py`, `states.py`, `a11y_scan.py` | The checks (Section 17.5) |

### 17.3 Patterns

- **Pages never change data directly.** They read through `queries.ts` and act through `actions.ts`; every
  action answers `{ ok, message, data?, field? }`, like the real endpoint, and `field` names the field a
  refusal belongs to. To connect the real backend, replace the bodies of these functions with `fetch` calls.
- **List state in the address:** `useListParams` keeps filters, tabs and the page in the hash address.
- **Breakpoint hooks:** `useWideTables` (768 px), `useDesktopFrame` (1024 px), `useWideWorkTables` (1280 px).
- **Status words** come only from `status.ts`; **formats** only from `format.ts`.
- **Pages load on demand** (`React.lazy`) with "Loading the page…" while they load; each page sets its title
  ("Delivery cases - LMS Admin").
- **Classes** are joined with `cn()`, a plain join: two classes for the same property do not override each
  other by order. A state that must win uses a variant (`aria-disabled:bg-sand`, `disabled:`), as the
  blocked button does.
- Screen-reader-only text inside a scroll box needs the box positioned (`.lms-scroll-x` is), or it pushes the
  page sideways.

### 17.4 Sample data

- Every screen is filled with realistic data from a seeded generator, so every reload shows the same world;
  a sample clock (2026-11-12 09:14 in the Admin build) moves on in real time.
- Realistic amounts: the Admin build has 42 accounts, 128 products and 265 orders, with every status of the
  route present; numbers that appear on two pages agree.
- Invented people and usernames (`client_0451`, `wh_picker_02`, `dl_driver_01`); no real person, phone number
  or address. Data made by test tools is marked (`test_` usernames, `TEST-` references and SKUs).
- Every state a page can show exists in the data at least once (blocked, refused, empty, long names).
- In test mode the log in page lists the sample accounts.

### 17.5 Checks

With the dev server running (`npm run dev`), from the app folder:

| Script | What it checks |
|---|---|
| `python tests/flows.py 1440 900` (also `390 844`, `768 1024`, `1024 768`) | Every page and decision end to end |
| `python tests/screenshots.py 1440 900 test-output/screenshots/1440` (also `390 844`) | A full-page screenshot of every page and a layout check: no sideways scroll, nothing sticking out of its card, no cut-off text, no touch target under 40 px (390 px runs as a touch phone) |
| `python tests/states.py 1440 900 test-output/states/1440` (also `390 844`) | Screenshots of errors, windows, messages, menus and the drawer |
| `python tests/a11y_scan.py 1440 900` (also `390 844`) | Names of buttons and links, labels and names of fields, heading order, one h1, icons |
| `npm run build` | Type check and production build |

A new system copies the scripts and changes their list of pages, the sample account and the selectors of its
flows.

### 17.6 Definition of done for a system

- Every function of the current pages of the role works on the new pages, test tools included.
- Every page passes the checklists of Section 18 at 390, 768, 1024 and 1440 px.
- The flows, screenshots, states and accessibility scripts pass; the build passes.
- Every addition is written into Section 19.

---

## 18. Checklists

### 18.1 Before handing over a page

- [ ] Fonts and icons are bundled; nothing loads from an outside address.
- [ ] Checked at 390, 768, 1024 and 1440 px: no sideways page scroll, nothing cut off, no decision button out
  of view.
- [ ] Every button has words; at most one main button per page and per window.
- [ ] Every status is a badge or a flag with the words and family of Section 4.
- [ ] Focus visible everywhere; windows trap and return focus; Escape closes windows and menus.
- [ ] Tables use tabular figures; money, times and counts follow Section 13.5.
- [ ] Loading, empty, refused and blocked states exist (Section 11.8).
- [ ] Reduced motion respected; nothing moves when the page opens.
- [ ] No private data beyond what the role may see.
- [ ] The accessibility scan reports no problems at 390 and 1440 px.

### 18.2 Unification check against the Admin pages

Open a page of the new system beside the Admin pages and compare:

- [ ] The frame: wordmark, menu look, group labels, counters, current entry, top bar, drawer.
- [ ] Page header, card, card header, part, strip.
- [ ] Button levels, sizes and words; blocked and busy buttons.
- [ ] Badge and flag look; the same stored word has the same words.
- [ ] Tables and cards at every width; pager; segmented filter; empty states.
- [ ] Windows: titles, "Cancel"/"Close", the reason rule, the requirement line.
- [ ] Toasts, inline messages, notes.
- [ ] Money, times, counts, codes, quotes, ellipses.
- [ ] Focus ring, touch targets, motion.
- [ ] The test mode flag and the Test tools page.

---

## 19. Additions by system

Each system writes here what it adds: new parts (with their look in tokens), new status words with their
families, new flags, new words for the glossary, and new rules. Format of an entry:

> **YYYY-MM-DD, System, Section it extends:** what was added, when to use it, why nothing existing fitted, where
> it is used (page and file).

### 19.1 Admin

The Admin pages are the reference build; everything in Sections 1 to 18 is used there.

### 19.2 Client

(None yet.)

### 19.3 Warehouse Worker

(None yet.)

### 19.4 Delivery Worker

(None yet.)

### 19.5 Shared pages

(None yet.)

---

## 20. History

### 20.1 Versions

| Version | Date | Change |
|---|---|---|
| 1.0 | 2026-11-22 | Step A: generated with the `ui-ux-pro-max` skill (product type "Productivity Tool", variance 3, motion 3, density 7), then adapted by hand (Section 20.2) |
| 1.1 | 2026-11-22 | Step B (`DESIGN_PLAN.md` Section 3): the cream page and the Literata titles became a linen page and one type family; cards became flat slips; the dashboard got the queue panel and the route |
| 1.2 | 2026-11-22 | Step C, `/impeccable critique`: the Turn family; normal states as quiet text; quieter danger row buttons; a calm session timer; route stops link to their decisions; work tables sort what waits first and filter with counts; blocked buttons stay reachable and say why |
| 1.3 | 2026-11-22 | Step C, `/impeccable polish`: work tables as cards below 1280 px; pinned first and Actions columns; labelled Logs filters; Logs download in the page header |
| 1.4 | 2026-11-23 | Step D, web interface guidelines (`WEB_GUIDELINES_REVIEW.md`): field names, focus on the first error, live regions in windows, typographic quotes, ellipses, no-break spaces, compositor-only motion; Step E: 40 px touch targets on touch screens |
| 2.0 | 2026-11-23 | One design system for every LMS system: decision guide, full status words and the reader rule of Turn, page kinds, the parts other roles need, words and glossary, privacy by role, engineering, unification checks, additions by system, tokens and base styles as appendices |

### 20.2 What was adapted from the generated recommendation, and why

The skill's raw output was: style "Minimalism & Swiss Style"; colours teal `#0D9488` with black words, teal
`#14B8A6`, orange `#EA580C`, a mint page `#F0FDFA` and aqua borders `#99F6E4`; fonts Varela Round and Nunito
Sans; motion "scroll reveal" with GSAP; page pattern "Product Demo + Features". A first query ("logistics
operations admin dashboard calm warm") returned a dark navy theme with neon green and Fira Code, which did not
fit "calm, warm" and was retried as the skill advises.

| Kept | Changed | Reason |
|---|---|---|
| Minimalism and Swiss structure, grid, clear hierarchy, subtle hover | - | Fits a data-dense working tool |
| Teal as the colour of focus and action | Darkened to `#0F6E66` with white words | White on `#0D9488` is only 3.74:1; black words on a teal button look like an error |
| A warm contrast colour (orange) | Became apricot for progress and counters, not for buttons | One action colour keeps the page calm; apricot carries the warm, motivating note |
| - | Mint page and aqua borders became linen `#F3F2ED` and warm hairlines `#E2DED3` | Mint reads cool and clinical; the brief asks for warm |
| A friendly type mood | Varela Round and Nunito Sans became Atkinson Hyperlegible Next (and, until Step B, Literata titles) | The brief asks for highly readable numbers and small text: Atkinson separates look-alike characters and has tabular figures |
| Subtle motion | Scroll reveal removed | Content in a working tool must be there at once |
| - | Page pattern "Product Demo + Features" not used | It is a landing-page pattern; this is an application frame |

---

## Appendix A. Tokens (`tokens.css`)

`src/index.css` imports this file after `@import "tailwindcss";`, so every token becomes Tailwind utilities
(`--color-teal` gives `bg-teal`, `text-teal`, `border-teal`; `--text-small` gives `text-small`).

```css
/*
  LMS Admin design tokens ("Evergreen and apricot on a linen desk").
  Source of truth: design-system/lms-admin/MASTER.md. Every colour pair here was measured for contrast
  (see MASTER.md Section 2). This file is imported by src/index.css, so Tailwind turns each token into
  utilities, for example --color-teal gives bg-teal, text-teal and border-teal.
*/
@theme {
  /* Fonts (bundled from npm, nothing loads from an outside address) */
  --font-sans: "Atkinson Hyperlegible Next Variable", "Atkinson Hyperlegible Next", ui-sans-serif, system-ui, sans-serif;
  --font-mono: "Atkinson Hyperlegible Mono Variable", "Atkinson Hyperlegible Mono", ui-monospace, "Cascadia Mono", monospace;

  /* Neutrals: the linen desk */
  --color-paper: #f3f2ed;
  --color-surface: #ffffff;
  --color-sunken: #f1efe9;
  --color-sand: #e8e5db;
  --color-track: #ece8df;
  --color-hover: #f7f6f1;
  --color-line: #e2ded3;
  --color-line-strong: #cdc7b8;
  --color-field-line: #8c8278;
  --color-ink: #24201c;
  --color-ink-muted: #5f574f;
  --color-ink-subtle: #6e655c;

  /* Brand and action */
  --color-teal: #0f6e66;
  --color-teal-hover: #0b5b54;
  --color-teal-press: #094a45;
  --color-teal-tint: #e2f0ec;
  --color-evergreen: #173a36;
  --color-evergreen-hover: #21504a;
  --color-evergreen-active: #2a5e57;
  --color-cream: #f3ece0;
  --color-menu-label: #a9c2bb;
  --color-apricot: #f0b47a;
  --color-apricot-fill: #b5651d;
  --color-apricot-tint: #fcebd8;
  --color-terracotta: #8a4310;
  --color-danger: #b42318;
  --color-danger-hover: #931b12;
  --color-danger-tint: #fce9e6;

  /* "Your turn": waits for a decision of the reader (apricot tint, terracotta words: 6.23 to 1) */
  --color-turn-bg: #fcebd8;
  --color-turn-fg: #8a4310;
  --color-turn-dot: #b5651d;

  /* The six status families: background, text, dot */
  --color-waiting-bg: #fbefd7;
  --color-waiting-fg: #774700;
  --color-waiting-dot: #c98516;
  --color-warehouse-bg: #e3edfa;
  --color-warehouse-fg: #1c4c8a;
  --color-warehouse-dot: #3a78d0;
  --color-road-bg: #ede7fa;
  --color-road-fg: #4c3394;
  --color-road-dot: #7656d2;
  --color-done-bg: #e0f1e5;
  --color-done-fg: #1c5f35;
  --color-done-dot: #3a9659;
  --color-problem-bg: #fbe4df;
  --color-problem-fg: #972a1d;
  --color-problem-dot: #d2493b;
  --color-closed-bg: #eeeae4;
  --color-closed-fg: #544d46;
  --color-closed-dot: #968c82;

  /* Type scale (font size and line height) */
  --text-flag: 12.5px;
  --text-flag--line-height: 16px;
  --text-small: 13px;
  --text-small--line-height: 18px;
  --text-label: 14px;
  --text-label--line-height: 20px;
  --text-body: 15px;
  --text-body--line-height: 22px;
  --text-part-title: 15px;
  --text-part-title--line-height: 22px;
  --text-card-title: 17px;
  --text-card-title--line-height: 24px;
  --text-section-title: 20px;
  --text-section-title--line-height: 28px;
  --text-page-title: 28px;
  --text-page-title--line-height: 36px;
  --text-greeting: 32px;
  --text-greeting--line-height: 40px;
  --text-big-number: 34px;
  --text-big-number--line-height: 40px;

  /* Radius */
  --radius-chip: 8px;
  --radius-control: 10px;
  --radius-card: 14px;
  --radius-window: 18px;

  /* Elevation */
  --shadow-raised: 0 10px 28px -8px rgb(36 32 28 / 0.2), 0 2px 6px rgb(36 32 28 / 0.06);
  --shadow-window: 0 28px 56px -16px rgb(36 32 28 / 0.32), 0 4px 12px rgb(36 32 28 / 0.08);

  /* Motion */
  --ease-calm: cubic-bezier(0.2, 0.7, 0.2, 1);
}
```

## Appendix B. Base styles (`src/index.css`)

The fonts are imported in `src/main.tsx`:

```ts
import "@fontsource-variable/atkinson-hyperlegible-next/wght.css";
import "@fontsource-variable/atkinson-hyperlegible-mono/wght.css";
import "./index.css";
```

```css
@import "tailwindcss";
@import "../design-system/lms-admin/tokens.css";

/*
  Base styles of the LMS Admin web app. The tokens come from design-system/lms-admin/tokens.css.
  Components use Tailwind utilities; this file holds only what utilities cannot express well:
  the base type, focus rings, the window (dialog) motion, the print label and reduced motion.
*/

@layer base {
  html {
    -webkit-text-size-adjust: 100%;
    text-size-adjust: 100%;
    scroll-padding-top: 76px;
    color-scheme: light;
    scrollbar-color: var(--color-line-strong) var(--color-paper);
    -webkit-tap-highlight-color: transparent;
    touch-action: manipulation;
  }

  input,
  textarea {
    caret-color: var(--color-teal);
  }

  body {
    margin: 0;
    background: var(--color-paper);
    color: var(--color-ink);
    font-family: var(--font-sans);
    font-size: var(--text-body);
    line-height: var(--text-body--line-height);
    font-synthesis: none;
    text-rendering: optimizeLegibility;
    -webkit-font-smoothing: antialiased;
  }

  /* Tabular figures wherever numbers are compared. */
  table,
  .tabular,
  time,
  input[type="number"] {
    font-variant-numeric: tabular-nums;
  }

  h1,
  h2,
  h3,
  h4 {
    text-wrap: balance;
  }

  p {
    text-wrap: pretty;
  }

  /* One focus ring for the whole app: 2 px teal, 2 px away from the control. */
  :focus-visible {
    outline: 2px solid var(--color-teal);
    outline-offset: 2px;
  }

  .on-evergreen :focus-visible {
    outline-color: var(--color-apricot);
  }

  main:focus {
    outline: none;
  }

  button,
  [role="button"],
  summary,
  label[for],
  select {
    cursor: pointer;
  }

  button:disabled,
  select:disabled,
  input:disabled {
    cursor: not-allowed;
  }

  ::selection {
    background: #cbe5de;
    color: var(--color-ink);
  }

  input::placeholder,
  textarea::placeholder {
    color: var(--color-ink-subtle);
    opacity: 1;
  }
}

/* Windows (native dialog): fade and grow in, backdrop fades in. */
dialog.lms-dialog {
  overscroll-behavior: contain;
  padding: 0;
  border: none;
  background: transparent;
  max-width: none;
  max-height: none;
  overflow: visible;
}

dialog.lms-dialog[open] {
  animation: lms-dialog-in 180ms var(--ease-calm);
}

dialog.lms-dialog::backdrop {
  background: rgb(23 32 30 / 0.45);
  animation: lms-fade-in 180ms var(--ease-calm);
}

@keyframes lms-dialog-in {
  from {
    opacity: 0;
    transform: scale(0.97);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

@keyframes lms-fade-in {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

@keyframes lms-menu-in {
  from {
    opacity: 0;
    transform: translateY(-4px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes lms-toast-in {
  from {
    opacity: 0;
    transform: translateY(-8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes lms-drawer-in {
  from {
    transform: translateX(-100%);
  }
  to {
    transform: translateX(0);
  }
}

@keyframes lms-decided {
  from {
    background-color: var(--color-done-bg);
  }
  to {
    background-color: transparent;
  }
}

@keyframes lms-spin {
  to {
    transform: rotate(360deg);
  }
}

.lms-menu-in {
  animation: lms-menu-in 140ms ease-out;
}

.lms-toast-in {
  animation: lms-toast-in 200ms var(--ease-calm);
}

.lms-drawer-in {
  animation: lms-drawer-in 200ms var(--ease-calm);
}

.lms-decided-row {
  animation: lms-decided 2.4s ease-out;
}

.lms-spin {
  animation: lms-spin 0.8s linear infinite;
}

/* Thin, visible scrollbars for wide tables (a table that scrolls must show that it scrolls). */
.lms-scroll-x {
  /* positioned, so screen-reader-only text inside a wide table stays inside the scroll box */
  position: relative;
  overflow-x: auto;
  scrollbar-width: thin;
  scrollbar-color: var(--color-line-strong) transparent;
}

.lms-scroll-x:focus-visible {
  outline-offset: -2px;
}

/* Print only the QR label (Products, "Show QR code", "Print label"). */
@media print {
  html.lms-print-label body * {
    visibility: hidden !important;
  }

  html.lms-print-label .lms-print-area,
  html.lms-print-label .lms-print-area * {
    visibility: visible !important;
  }

  html.lms-print-label .lms-print-area {
    position: fixed;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    background: white;
  }

  html.lms-print-label dialog::backdrop {
    background: white;
  }
}

/* People who switched animations off get every change at once. */
@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after,
  dialog.lms-dialog::backdrop {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
    scroll-behavior: auto !important;
  }
}
```

## Appendix C. Class recipes of the parts

Tailwind classes of the reference build. `cn()` joins classes and leaves out empty ones.

```ts
// ----- Buttons (src/components/ui.tsx)

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

// ----- Text link (src/components/ui.tsx)

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

// ----- Badge, flag and count pill (src/components/ui.tsx)

const FAMILY: Record<Family, { badge: string; dot: string; flag: string }> = {
  turn: { badge: "bg-turn-bg text-turn-fg", dot: "bg-turn-dot", flag: "text-turn-fg border-turn-dot" },
  waiting: { badge: "bg-waiting-bg text-waiting-fg", dot: "bg-waiting-dot", flag: "text-waiting-fg border-waiting-dot" },
  warehouse: { badge: "bg-warehouse-bg text-warehouse-fg", dot: "bg-warehouse-dot", flag: "text-warehouse-fg border-warehouse-dot" },
  road: { badge: "bg-road-bg text-road-fg", dot: "bg-road-dot", flag: "text-road-fg border-road-dot" },
  done: { badge: "bg-done-bg text-done-fg", dot: "bg-done-dot", flag: "text-done-fg border-done-dot" },
  problem: { badge: "bg-problem-bg text-problem-fg", dot: "bg-problem-dot", flag: "text-problem-fg border-problem-dot" },
  closed: { badge: "bg-closed-bg text-closed-fg", dot: "bg-closed-dot", flag: "text-closed-fg border-closed-dot" },
};
export function StatusBadge({ word, look }: { word?: string; look?: StatusLook }) {
  const resolved = look ?? statusLook(word ?? "UNKNOWN");
  return (
    <span className={cn("inline-flex h-6 shrink-0 items-center gap-1.5 whitespace-nowrap rounded-full px-2.5 text-small font-semibold", FAMILY[resolved.family].badge)}>
      <span aria-hidden="true" className={cn("size-[7px] shrink-0 rounded-full", FAMILY[resolved.family].dot)} />
      {resolved.label}
    </span>
  );
}
export function Flag({ family, children }: { family: Family; children: ReactNode }) {
  return (
    <span className={cn("inline-flex h-5 shrink-0 items-center whitespace-nowrap rounded-chip border bg-surface px-1.5 text-flag font-[650]", FAMILY[family].flag)}>{children}</span>
  );
}
export function CountPill({ children, className }: { children: ReactNode; className?: string }) {
  return <span className={cn("inline-flex h-6 min-w-6 items-center justify-center rounded-full bg-sunken px-2 text-small font-semibold tabular text-ink-muted", className)}>{children}</span>;
}

// ----- Card, card header, strip, body and part (src/components/ui.tsx)

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

// ----- Messages (src/components/ui.tsx)

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
export function Note({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <p className={cn("flex items-start gap-2 text-small text-ink-muted", className)}>
      <Info aria-hidden="true" className="mt-px size-4 shrink-0" />
      <span>{children}</span>
    </p>
  );
}

// ----- Page header and cell lines (src/components/ui.tsx)

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
export function CellMain({ children, className }: { children: ReactNode; className?: string }) {
  return <div className={cn("flex flex-wrap items-center gap-x-2 gap-y-1", className)}>{children}</div>;
}
export function CellSub({ children, className }: { children: ReactNode; className?: string }) {
  return <div className={cn("mt-1 text-small text-ink-muted [&>span]:align-middle [&>span:not(:first-child)]:ml-1.5", className)}>{children}</div>;
}

// ----- Progress bar (src/components/ui.tsx)

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

// ----- Fields (src/components/form.tsx)

export const inputClass =
  "w-full min-h-10 rounded-control border border-field-line bg-surface px-3 py-2 text-body text-ink max-md:text-[16px] pointer-coarse:min-h-11 " +
  "hover:border-ink-muted disabled:cursor-not-allowed disabled:bg-sunken disabled:text-ink-muted aria-[invalid=true]:border-danger aria-[invalid=true]:border-2";
// label: "text-label font-semibold text-ink"; help: "text-small text-ink-muted"; error: "flex items-start gap-1.5 text-small font-semibold text-danger" with an alert icon

// ----- Side menu (src/components/Frame.tsx), inside <aside className="on-evergreen bg-evergreen">

const menuGroupLabel = "px-3 pb-1.5 text-flag font-[650] text-menu-label";
const menuEntry = (isActive: boolean) =>
  cn(
    "relative flex min-h-10 items-center gap-3 rounded-control px-3 text-body transition-colors duration-[120ms] pointer-coarse:min-h-11",
    isActive ? "bg-evergreen-active font-semibold text-cream" : "font-medium text-cream/90 hover:bg-evergreen-hover hover:text-cream",
  );
const currentEntryBar = "absolute inset-y-2 left-0 w-[3px] rounded-full bg-apricot"; // aria-hidden, only on the current entry
const menuCounter = "inline-flex h-6 min-w-6 items-center justify-center rounded-full bg-apricot px-1.5 text-small font-bold tabular text-ink";

// ----- Segmented filter button (src/components/SegmentedFilter.tsx)

const segment = (pressed: boolean, empty: boolean) =>
  cn(
    "inline-flex min-h-9 items-center gap-2 rounded-full border px-3 text-label font-semibold transition-colors duration-[120ms] pointer-coarse:min-h-10",
    pressed ? "border-teal bg-teal-tint text-teal-hover" : "border-line bg-surface text-ink hover:border-field-line hover:bg-hover",
    !pressed && empty && "text-ink-muted",
  );

// ----- Toasts (src/components/Toast.tsx)

const toastPlace = "pointer-events-none fixed inset-x-2 top-[64px] z-[60] flex flex-col items-stretch gap-2 sm:inset-x-auto sm:right-6 sm:top-[72px] sm:w-[400px]"; // aria-live="polite"
const toast = "lms-toast-in pointer-events-auto flex items-start gap-3 rounded-card border border-line bg-surface px-4 py-3 text-body font-medium shadow-raised"; // role="status"

// ----- Window (src/components/Dialog.tsx), inside <dialog className="lms-dialog m-auto"> opened with showModal()

const windowPanel = (wide: boolean) =>
  cn(
    "flex max-h-[calc(100dvh-24px)] w-[calc(100vw-16px)] flex-col rounded-window bg-surface text-ink shadow-window sm:max-h-[calc(100dvh-48px)]",
    wide ? "sm:w-[640px]" : "sm:w-[520px]",
  );
const windowTitleBox = "px-5 pb-2 pt-5"; // holds <h2 className="text-card-title font-[650]">
const windowBody = "flex min-h-0 flex-col gap-4 overflow-y-auto overscroll-contain px-5 pb-5 pt-2";
const windowFooter = "flex flex-col-reverse gap-2 border-t border-line px-5 py-4 sm:flex-row sm:justify-end [&>*]:max-sm:w-full";

// ----- Tables (src/components/DataTable.tsx)

const cellPad = "px-3 first:pl-4 last:pr-4 md:first:pl-5 md:last:pr-5";
const headCell = "whitespace-nowrap border-b border-line bg-sunken py-2.5 text-small font-semibold text-ink-muted"; // numbers: + text-right
const bodyCell = "border-b border-line py-3 align-top transition-colors duration-[120ms] group-last:border-b-0 group-hover:bg-hover"; // first cell: + font-semibold; numbers: + whitespace-nowrap text-right tabular
const pinnedFirst = "sticky left-0 z-[1] bg-surface shadow-[1px_0_0_var(--color-line),6px_0_8px_-6px_rgb(36_32_28/0.14)]";
const pinnedActions = "sticky right-0 z-[1] bg-surface shadow-[-1px_0_0_var(--color-line),-6px_0_8px_-6px_rgb(36_32_28/0.14)]";
// the box around a table: <div role="region" aria-label="Users table" tabIndex={overflowing ? 0 : undefined} className="lms-scroll-x">
```
