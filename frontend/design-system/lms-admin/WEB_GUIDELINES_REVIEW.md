# Step D: Web Interface Guidelines review

**Date:** 2026-11-22. **Scope:** every page of the app (login, dashboard, the three work pages, Users,
Products, Orders, the order page, the three Logs tabs, Test tools, Welcome, Change password, Page not found)
and every shared component in `src/components/` and `src/lib/`.

**Rules used.** On 2026-11-22 the `web-design-guidelines` skill was not installed yet, so its source, the
Vercel Web Interface Guidelines (`vercel-labs/web-interface-guidelines`, `command.md`), was fetched and
applied directly: accessibility, focus, forms, animation, typography, content handling, images, performance,
navigation and state, touch, layout, theming, locale, hover states, copy and the anti-pattern list. On
2026-11-23 the skill itself (`audit-suite:web-design-guidelines`) became available and was run over
`src/pages/*.tsx`, `src/components/*.tsx`, `src/App.tsx`, `src/index.css` and `index.html` to confirm the
fixes; its findings are at the end of this file.

**Phone touch targets.** `tests/screenshots.py` measures every button, field and stand-alone link on a
390 px touch phone. Route stops, the "need you" links, the refunds pill and text links in card titles were
under 40 px; they now reach 40 px on touch screens (`src/components/Route.tsx`, `src/components/ui.tsx:85`,
`src/pages/WelcomePage.tsx`). The check reports no small targets on any page.

**Severity.** High: a person cannot finish a task, or a keyboard or screen reader user is left out.
Medium: the task works but is harder, slower or less trustworthy than it should be. Low: polish or a small
performance gain. Every High and Medium finding is fixed. Low findings are fixed where cheap, otherwise
listed with the reason they stay.

**Checks run after the fixes**

| Check | Result |
|---|---|
| `tests/a11y_scan.py` (names of buttons and links, labels, field names, heading order, one h1, images, exposed icons) on 16 pages | 0 problems at 1440 px and at 390 px |
| Rendered text of every page scanned for straight quotes and `...` | none left (raw log values in `<code>` excluded) |
| `tests/flows.py` (57 end-to-end checks) | see the final test run in `README.md` |
| Anti-patterns: `user-scalable=no`, `onPaste` blocking, `transition: all`, click handlers on `div`/`span`, images without size, `autoFocus` | none found |

---

## Findings by file

`file:line` points at the code as it is now (after the fix).

## src/pages/UsersPage.tsx
- `src/pages/UsersPage.tsx:283` - [High] a refused "Create user" or "Save profile" showed its message without a live region, so screen readers stayed silent → fixed: `MessagePlace` (always-present `aria-live`)
- `src/pages/UsersPage.tsx:247` - [Medium] focus stayed on the button after a refused save → fixed: focus moves to the first invalid field (`useFocusFirstInvalid`)
- `src/pages/UsersPage.tsx:170` - [Medium] search box: no `name`, autofill and spell marks on, placeholder without `…` → fixed
- `src/pages/UsersPage.tsx:271` - [Medium] new username: phones capitalised its first letter → fixed (`autoCapitalize="none"`; spell marks were already off)
- `src/pages/UsersPage.tsx:278` - [Medium] employee number and vehicle plate spell-checked → fixed

## src/pages/ProductsPage.tsx
- `src/pages/ProductsPage.tsx:350` - [High] import refusal and product form refusal shown without a live region → fixed: `MessagePlace`
- `src/pages/ProductsPage.tsx:189` - [Medium] focus not moved to the first invalid field after a refused save → fixed
- `src/pages/ProductsPage.tsx:119` - [Medium] search box: no `name`, autofill on, placeholder without `…` → fixed

## src/pages/DeliveryCasesPage.tsx
- `src/pages/DeliveryCasesPage.tsx:634` - [High] "Record request" refusal shown without a live region → fixed: `MessagePlace`
- `src/pages/DeliveryCasesPage.tsx:186` - [Medium] attempt notes in straight quotes → fixed: “…”
- busy word "Recording" → fixed: "Recording…"

## src/pages/WarehouseCasesPage.tsx
- `src/pages/WarehouseCasesPage.tsx:142` - [Medium] shortage notes and decision reasons in straight quotes → fixed: “…”
- busy word "Saving" → fixed: "Saving…"

## src/pages/TestToolsPage.tsx
- `src/pages/TestToolsPage.tsx:441` - [Medium] a tool's refused field did not get focus → fixed: focus moves to it inside that tool's form
- `src/pages/TestToolsPage.tsx:88` - [Medium] 11 help lines name buttons and flags in straight quotes → fixed: “Create test user”
- busy word "Working" → fixed: "Working…"; the stage picker has a `name`

## src/pages/LogsPage.tsx
- `src/pages/LogsPage.tsx:59` - [Medium] filter fields had no `name`, no autofill rule and (before the polish pass) no visible label → fixed: `FilterText` and `FilterField` with labels, names, `autoComplete="off"`, `spellCheck={false}`
- `src/pages/LogsPage.tsx:84` - [Low] raw JSON values could be machine-translated → fixed: `translate="no"`

## src/pages/OrdersPage.tsx
- `src/pages/OrdersPage.tsx:67` - [Medium] search box, status and date filters had no `name`; placeholder without `…` → fixed

## src/pages/LoginPage.tsx
- `src/pages/LoginPage.tsx:71` - [Medium] password field had no `name` → fixed (`name="password"`, `autocomplete="current-password"` kept)
- `src/pages/LoginPage.tsx:79` - [Low] "Sample accounts in test mode" had no hover state → fixed
- busy word "Logging in" → fixed: "Logging in…"

## src/pages/ChangePasswordPage.tsx
- `src/pages/ChangePasswordPage.tsx:170` - [Medium] the three password fields had no `name` → fixed (`current-password`, `new-password`, `repeat-new-password`)
- busy word → fixed: "Changing password…"

## src/pages/OrderDetailPage.tsx
- `src/pages/OrderDetailPage.tsx:374` - [Low] straight apostrophe in "browser's" → fixed: ’

## src/pages/RefundsPage.tsx
- busy words "Asking", "Retrying" → fixed: "Asking…", "Retrying…"

## src/components/form.tsx
- `src/components/form.tsx:73` - [Medium] text fields, text areas and select fields had no `name` and let password managers fill non-sign-in fields → fixed: `name` from the label, `autoComplete="off"` unless a field asks for more
- `src/components/form.tsx:235` - [Medium] password inputs had no `name` → fixed

## src/components/ConfirmDialog.tsx
- `src/components/ConfirmDialog.tsx:67` - [Medium] a busy confirm button repeated its label without `…` → fixed: "Wait for restock…"

## src/components/Tabs.tsx
- `src/components/Tabs.tsx:77` - [Medium] the tab underline animated `left` and `width` (layout) → fixed: `transform: translateX() scaleX()` from a 100 px base

## src/components/ui.tsx
- `src/components/ui.tsx:250` - [Medium] progress fills animated `width` → fixed: full-width fill uncovered with `clip-path: inset(… round 999px)`, which keeps the rounded end

## src/components/Frame.tsx
- `src/components/Frame.tsx:132` - [Medium] the side menu's progress fill animated `width` → fixed: `clip-path`
- `src/components/Frame.tsx:156` - [Medium] "15 min" could break between number and unit → fixed: no-break space
- `src/components/Frame.tsx:228` - [Low] scrolling the side menu or the drawer could scroll the page behind → fixed: `overscroll-behavior: contain`
- `src/components/Frame.tsx:144` - [Low] the wordmark could be machine-translated → fixed: `translate="no"` (also `src/pages/AuthLayout.tsx`)

## src/components/Dialog.tsx and src/components/ActionsMenu.tsx
- `src/components/Dialog.tsx:66` - [Low] a scrolling window body chained its scroll to the page → fixed: `overscroll-behavior: contain` (same for the Actions menu, `src/components/ActionsMenu.tsx:142`, and the rejected-lines box of the CSV import)

## src/lib/format.ts
- `src/lib/format.ts:13` - [Medium] "HKD" and the amount could break across two lines → fixed: no-break space in every money value

## src/api/testTools.ts and src/components/CancelFacts.tsx
- `src/api/testTools.ts:243` - [Medium] messages named buttons in straight quotes ("Unlock", "Wait for restock", "Returned to Warehouse", "Check provider status") → fixed: curly quotes
- `src/components/CancelFacts.tsx:16` - [Low] straight apostrophes in "client's" (also two lines in `src/api/queries.ts` and one in `src/api/actions.ts`) → fixed: ’

## index.html
- `index.html:6` - [Medium] `theme-color` was the evergreen of the side menu, not the page → fixed: `#f3f2ed` (linen)

## src/App.tsx
- `src/App.tsx:80` - [Medium] "Loading the page" without `…` → fixed

## Low findings left as they are
- `src/index.css:195` - the decided-row highlight fades `background-color` once per decision (2.4 s, paint only, off with reduced motion). Kept: it is the one authored moment of a decision.
- `src/components/HowToUse.tsx:7` - whether a "How to use" is open is not in the address. Kept: it is help text, not page state.
- Windows have no "discard your changes?" guard on Escape. Kept: the forms hold 1 to 6 short fields and every window can be opened again; a second window on top would interrupt more than it protects.
- Fonts are not preloaded. Kept: they are bundled and served from the same origin with `font-display: swap`; there is no outside origin to preconnect to.
- `overscroll-behavior: none` on `<html>` for fine pointers is not set. Kept: it would also switch off scroll chaining and swipe navigation; windows, menus and the drawer already contain their own scroll.

## Deliberate differences from the guidelines (project rules)
- **Sentence case, not Title Case** for headings and buttons ("Download CSV", "Wait for restock"): the project's word rules (`MASTER.md` Section 5.1, Rules).
- **Fixed formats, not `Intl.*`**: times as `2026-11-12 09:14` in the browser's time and money as `HKD 1,234.50` (`MASTER.md` Section 13.5). The same text appears in CSV files and logs, and the Admin compares values across pages.
- **Confirm buttons stay switched off until the reason and the tick are given** (the guideline prefers an enabled button until the request starts). Project rule for decision windows; the line above the buttons says exactly what is missing and is linked to the button (`src/components/ConfirmDialog.tsx:24`, `MASTER.md` Section 12.10). Blocked row buttons stay focusable (`src/components/ui.tsx:29`).
- **Example placeholders keep no ellipsis** ("e.g. admin_main"): an ellipsis after a sample value reads as part of the value. Search placeholders do end with `…`.
- **`outline: none` on two focus targets that are not controls**: `main` after the skip link (`src/index.css:67`) and the stage headings of Test tools after a jump (`src/pages/TestToolsPage.tsx:579`). Both have `tabIndex={-1}` and are never reached by Tab.
- **Refusal wording comes from the backend contract** and is shown as sent ("The username or the password is wrong."). Most refusals already name the next step ("Please refresh the page.", "Type a number from 1 to 5.").
- **"Actions" menus are a disclosure of plain buttons**, not `role="menu"`: every line is a worded button, reachable with Tab and the arrow keys, and Escape closes it.

## Files with no findings
✓ pass: `src/components/DataTable.tsx`, `src/components/Route.tsx`, `src/components/SegmentedFilter.tsx`,
`src/components/Pager.tsx`, `src/components/Toast.tsx`, `src/components/QRLabel.tsx` (`role="img"` with a
label, fixed size), `src/components/Timeline.tsx`, `src/components/HowToUse.tsx`, `src/pages/DashboardPage.tsx`,
`src/pages/WelcomePage.tsx`, `src/pages/NotFoundPage.tsx`, `src/lib/useListParams.ts` (filters, tabs and
pages live in the address), `src/lib/csv.ts`.

---

## Confirmation run with the `web-design-guidelines` skill (2026-11-23)

Guidelines fetched fresh (`command.md`, 8,015 characters, the same 17 rule sections as on 2026-11-22).
Files: `src/pages/*.tsx`, `src/components/*.tsx`, `src/App.tsx`, `src/index.css`, `index.html`. The rules
the page scan cannot see (text inside windows, checkboxes, file inputs, units in messages) were checked in
the source. Output in the skill's format; every finding below is fixed.

## src/components/form.tsx
src/components/form.tsx:166 - checkbox input has no `name` → fixed (`name` from its label)

## src/pages/ProductsPage.tsx
src/pages/ProductsPage.tsx:327 - file input has no `name` → fixed (`name="csv-file"`)
src/pages/ProductsPage.tsx:335 - "1 MB" can break between number and unit → fixed (`1&nbsp;MB`)

## src/api/actions.ts (outside the list, same rule)
src/api/actions.ts:532 - "The file is larger than 1 MB." can break → fixed (`\u00a0`)

## Everything else
✓ pass: `src/App.tsx`, `src/index.css`, `index.html`, `src/components/ActionsMenu.tsx`,
`src/components/CancelFacts.tsx`, `src/components/ConfirmDialog.tsx`, `src/components/DataTable.tsx`,
`src/components/Dialog.tsx`, `src/components/Frame.tsx`, `src/components/HowToUse.tsx`,
`src/components/Pager.tsx`, `src/components/QRLabel.tsx`, `src/components/Route.tsx`,
`src/components/SegmentedFilter.tsx`, `src/components/Tabs.tsx`, `src/components/Timeline.tsx`,
`src/components/Toast.tsx`, `src/components/ui.tsx`, and every file in `src/pages/` apart from the one above.
The deliberate differences listed earlier (sentence case, fixed time and money formats, confirm buttons
switched off until the reason is given, example placeholders without an ellipsis, two non-control focus
targets) and the Low items kept with their reasons are unchanged.
