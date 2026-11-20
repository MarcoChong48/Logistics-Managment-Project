# LMS design note: how to rebuild a draft page in the new design

Version 2.5, 20 November 2026.

---

## 0. About this note

The Logistics Management System (LMS) has an old website draft: six sample screens for four roles (Client, Warehouse Worker, Delivery Worker, Admin). The website now has a new design. The Admin pages were rebuilt first. This note describes the new design so completely that any other draft page can be rebuilt in it and will look like part of the same website.

**Who this note is for.** Anyone, a person or an AI, who rebuilds the pages of a role. You need this note and the old draft pages. You need nothing else.

**What this note decides, and what it does not.** It decides how a page looks: layout, parts, colours, words, sizes, motion. It does not decide what a page must do or which data it shows. That comes from the old draft page and from the person who is responsible for that page.

**What you build.** Static sample pages: HTML files that open in a browser straight from a folder, with invented sample data.

**How this note is organised.**

| Section | Content |
|---|---|
| 1 | What you need, the files you build, and the rules for building |
| 2 | The design in short: the rules behind it, colours, words, sizes, motion |
| 3 | The page frame |
| 4 | Every part, as a tree of HTML elements and classes |
| 5 | How to change a draft page, step by step, and the kinds of page |
| 6 | What each thing in the draft becomes |
| 7 | The small script |
| 8 | A worked example (the Admin screen) and hints for the other draft screens |
| 9 | The checks to do before you hand over |
| Appendix A | The finished style sheet. You copy it unchanged. |
| Appendix B | The icons. You copy them unchanged. |

**What changed in version 2.5.** If you worked from version 2.4, these are all the differences:

| Section | Change |
|---|---|
| 4.27 | The strength levels do not overlap: "Good" is 12 to 15 characters of only letters and digits, or fewer than 12 with another character. A password of 12 to 15 characters with another character is "Strong", as before. |
| 4.27, 7 (B14) | A press first removes every earlier answer of the server, under the fields and in the message place. |

**What changed in version 2.4.** If you worked from version 2.3, these are all the differences:

| Section | Change |
|---|---|
| 4.27, 4.4, 2.3, 2.10 | New part: the password field. A "Show" button with words stands beside the input. Under a new password stand a strength scale that only advises (amber and green, never red) and the list of the password rules, ticked while the person types. Copy the style sheet again. |
| 4.15, 4.20 | The message place is also used on the pages without the frame. It is always in the page, so a screen reader reads the message that is put into it. A way out under the main button is a quiet button. |
| 7 (B14, B15, B16) | New sample behaviours: the feedback and the press of a password form, "Show" and "Hide", and a notice named in the page's address. |
| 6.6 | A password field, a strength bar or a list of password rules in the draft becomes the password field. |
| 9 | New checks G-23 and G-24. |

**What changed in version 2.3.** If you worked from version 2.2, this is the only difference. No part, page rule, style sheet or script changed, so pages built from version 2.2 need no change.

| Section | Change |
|---|---|
| 9 | G-19: a script that cannot reach the browser's print view leaves that part to a person, who does it once by hand. The report says how each part was checked. |

**What changed in version 2.2.** If you worked from version 2.1, these are all the differences:

| Section | Change |
|---|---|
| 2.5, 4.8, 4.25 | A date and time inside a line of text stands in `time.shared-nowrap [datetime]`, so it never breaks. |
| 4.8 | Every button of a row names its record (`aria-label`, for example "Reject request for order TEST-0003"). A window opened from a row is that row's window. A decided row stays in place with its outcome until the list is loaded again (B13). |
| 2.9, 4.13, 7 (B7) | Only the selected tab is reached with the Tab key; the arrow keys, Home and End move between tabs. |
| 4.16 | A badge that names a count line takes the link underline and keeps its colours and shape. Copy the style sheet again. |
| 7 (B12) | New sample behaviour: a list opened from an address that names a filter shows only the rows of that filter, with the matching count. |
| 9 | G-16 and G-18 are more exact; new checks G-21 and G-22. |

**What changed in version 2.1.** Only the colours changed. If you worked from version 2.0, these are all the differences:

| Section | Change |
|---|---|
| 2.2, 2.3, 2.10, Appendix A | New colours, chosen from six previews: a charcoal side menu, a light neutral grey page, grey ruled lines, near-black text and burnt orange `#a84200` for what can be pressed and for "you are here". The layout, the status families and the red for danger stay as they are. The contrast table is new. Copy the style sheet again. |
| 2.8, 2.9, 4.2, 4.3, 4.9, 4.13, 4.16, 4.21 | What was green for an action is now orange: the main button, links, text links, the keyboard outline (pale orange in the side menu), the bar under the tabs, the current page number, the rule across the work box and the progress steps. Status badges, success messages and "Copied." stay green. |
| 2.3 | An orange button and an amber "Waiting" badge differ in shape. A filled red button never stands in the same window as a filled orange one. |
| 3.3 | The side menu counters are light grey with charcoal numbers; on the current entry, charcoal with white numbers. |
| 6.3 | Main buttons and links are orange. |
| 9 | G-11 counts the filled orange buttons; G-16 and G-18 name the orange outline and the orange bar. |

**What changed in version 2.0.** The design was redesigned as a ledger. If you worked from version 1.5, these are all the differences:

| Section | Change |
|---|---|
| 2.2, 2.3, 2.10, Appendix A | The look is a ledger: a bookcloth-green side menu, a pale sage page, white leaves with a 1 px edge and no shadow, blue-grey ruled lines, blue-black text, Bahnschrift with figures of one width. The contrast table is new. Copy the style sheet again. |
| 2.8 | A button sits 1 px lower while it is pressed. |
| 3.3 | The current entry of the side menu is a tab in the page colour that joins the work area. Counters are cream with dark green numbers. |
| 4.5, 4.15 | Cards have a 1 px edge and no shadow. A note is a quiet line without a box. A message box takes the pale ground of its family. |
| 4.6 | A date range ("From" and "To") stands in `span.shared-filter-range` and wraps as one group. |
| 4.7, 7 (B11) | Every list table's scroll box is a named region. While the table is wider than its card, its first column stays at the left and the region can be reached with Tab. A money table that states a sum closes with a total row. |
| 2.3, 4.9 | The current page number of a pager is drawn in bold green on a pale green ground with a 2 px green outline. |
| 4.16 | A big number closes with a double rule. The box that holds the work to do has a green rule across its top. In a list of stock a 0 is not muted. |
| 4.17 | New part "decision facts" (Payment and Outcome). |
| 4.18 | The timeline is a day book: one block per day under its date, each entry a ruled line with hours and minutes. |
| 7 (B6) | Scrolling inside an open menu does not close it. |
| 1.3 | The script may also set the width of a pinned first column (B11). |
| 9 | New check G-20 for wide lists. |

**What changed in version 1.5.**

| Section | Change |
|---|---|
| 2.8, Appendix A and 9 (G-14) | With animations switched off, the dark layer behind a window also appears at once. |
| 2.9, 3.2, Appendix A and 9 (G-16) | A link "Skip to main content" is the first thing that the Tab key reaches. The `main` element has an `id` and `tabindex="-1"`. The top bar never covers the control that has the keyboard. |
| 4.12 and 9 (G-16) | Each "Actions" button is named after its row, for example `aria-label="Actions for SKU-1001"`. The menu has no `role="menu"` and no `role="menuitem"`. |
| 4.12, 7 (B6), Appendix A and 9 (G-14) | In a short window the menu stands 8 px below the top of the window, and lines that do not fit scroll inside it. |
| 4.16 | The name of a number line always stands inside `span.shared-number-line-name`, also when it is a badge, so all counts of a box stand in one column. |
| 4.8 | A decision that a rule blocks shows a switched-off button and the reason under the buttons. |
| Appendix A | Window bodies and menus no longer pass scrolling on to the page behind them. Copy the style sheet again. |

**What changed in version 1.4.**

| Section | Change |
|---|---|
| 4.15 and 7 (B1, B5) | Opening a window clears existing corner messages and cancels their timers. |
| Appendix A | Payment and a populated Refund card stack when they need more room. Copy the style sheet again. |

**What changed in version 1.3.**

| Section | Change |
|---|---|
| 4.14, 4.15 and 7 (B1, B2, B5, B8) | Copy feedback belongs to the current opening and the newest attempt. The note says when to use feedback inside an open window. |
| Appendix A | The adaptive message-area selector follows the frame to the message area at the same level. Copy the style sheet again. |
| 9 | G-09 and G-19 check narrow message placement and feedback inside windows. |

**What changed in version 1.2.**

| Section | Change |
|---|---|
| 9 | G-18 checks the existing keyboard rules for tabs and checks how tab panels and progress bars get their names. |

Appendices A and B are the same as in version 1.1.

**What changed in version 1.1.** If you worked from version 1.0, these are all the differences:

| Section | Change |
|---|---|
| 2.9, 4.13 and 7 (B7) | Tabs: every tab and every panel has an `id`; `aria-controls` and `aria-labelledby` connect them; the arrow keys move between tabs. |
| 4.14, 4.15 and 7 (B1, B5, B8) | New part "message place". The answer to "Copy" is an inline message inside the window, and it says truthfully whether the copy worked. A message box is never shown while a window is open. |
| 4.16 | The example of a group label is now "Work to do". |
| 4.24 | The progress bar names its words with `aria-labelledby`. |
| 7 (B10) | Says that an entry of the side menu also closes the menu of a small window. |
| 9 | G-16 is more exact. New checks G-18 and G-19. |
| Appendix A | Two new rules in the style sheet: the message place, and the drawn message boxes of a parts page, which no longer move in when the page opens. Copy the style sheet again. It now has 2144 lines. |

The icons (Appendix B) did not change.

---

## 1. What you need, what you build, and the rules

### 1.1 What you need

- This note.
- The old draft. It is the folder `stitch_logistics_management_system_prototype`. Each screen is a sub-folder with `code.html` (the page) and `screen.png` (a picture of it):

| Sub-folder | Screen |
|---|---|
| `client_portal_order_creation_payment_tracking_1` and `..._2` | Client: create an order, pay, follow the order (two versions of the same screen) |
| `warehouse_operations_picking_restock_mei` | Warehouse Worker: picking and stock count |
| `delivery_field_app_pickup_proof_ken` | Delivery Worker: pickup, delivery and proof |
| `system_administration_audit_center_alex_1` and `..._2` | Admin (two versions). Already rebuilt; used as the example in Section 8. |
| `logistics_operations_system/DESIGN.md` | The old design description. This note replaces it. Do not use it. |

The draft is only the source for what a page shows. Never copy its HTML, its classes, its colours or its words about itself.

### 1.2 Files

Put the pages of one role into one folder named after the role, next to the folder `shared`:

```
lms_redesign_prototype/
  shared/
    shared-common.css               the style sheet: Appendix A, copied unchanged
    shared-sample.js                the script (Section 7)
  client/
    client-new-order.html           one file per page, named <role>-<page>.html
    client-orders.html
    ...
  warehouse/
    warehouse-picking.html
    ...
  delivery/
    delivery-pickup.html
    ...
```

- Role words in file names: `client`, `warehouse`, `delivery`, `admin`. File names are lower case with hyphens.
- There is exactly one style sheet for all roles. If `shared/shared-common.css` already exists, compare it with Appendix A. It must be the same text. Never keep two different style sheets, and never add a second one for your role.
- If `shared/shared-sample.js` already exists and follows Section 7, use it as it is.
- Links between the pages are relative, so the pages work when they are opened straight from the folder.

Every page file starts like this (only the title changes), and the icon list of Appendix B is the first element of `body`:

```
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>My orders - LMS Client</title>
<link rel="stylesheet" href="../shared/shared-common.css">
<script src="../shared/shared-sample.js" defer></script>
</head>
<body>
```

### 1.3 Rules for building

1. **The look comes from this note. The content comes from the draft page and from the person responsible for it.** If the two seem to disagree, the look follows this note.
2. **Static pages only.** No server, no database, no login check, no real data.
3. **Nothing from outside.** No file may load anything from an outside web address: no script, no font, no icon font, no picture. Do not use Tailwind or any other library.
4. **The style sheet is finished.** Copy Appendix A unchanged. Do not add, remove or change a rule. No page has a `<style>` block or a `style="..."` attribute. Only the script may set a position or a width while the page runs, and only for behaviours B6, B7 and B11 of Section 7.
5. **The icons are finished.** Copy the icon list of Appendix B unchanged into every page. If a menu entry of your role needs an icon that is not there, draw it in the same way (Appendix B says how) and add it to the list of your pages only.
6. **Use only classes that exist in the style sheet.** Never invent a class name. If a page seems to need a new part, build it from the parts of Section 4.
7. **Build the HTML like the trees** of Sections 3 and 4.
8. **Sample data is invented and harmless.** No real people. No persona names from the draft. Usernames look like `client_0461` or `wh_picker_02`, order references like `TEST-0007`, products like `SKU-1001 Sample product A`.
9. **Language.** All text is English. Section 2.5 says how text is written.
10. **The script does only what Section 7 says.**
11. **Be honest about checks.** Before you say you are finished, open every page in a browser and do the checks of Section 9. If you could not do a check, say so.

### 1.4 What you hand over

A short report next to your pages (`<role>/<ROLE>_REDESIGN_REPORT.md`): the list of files; which page of the draft became which new page; what you removed from the draft; the checks of Section 9 with what you did and what you saw; open questions.

---

## 2. The design in short

This section explains the design. All sizes and colours named here are already written in the style sheet (Appendix A). You do not write them again; you only use the classes.

### 2.1 Three rules that decide everything

1. **No crowded or cut-off text.** A table has only the columns that are needed. Text is never cut and never ends in three dots. If something is still too wide, it wraps or it scrolls sideways with a visible scrollbar.
2. **Things that mean different things look different.** A status, an extra fact, a button and a link each have their own look. Colour is rare, so a coloured thing always means something.
3. **Every button has words.** No button shows only an icon.

### 2.2 The look

A ledger. The side menu is charcoal; the page is a light neutral grey; cards are white leaves with a 1 px edge and no shadow; grey ruled lines run between rows and under every timeline entry; text is near-black ink. One burnt orange for what can be pressed. Rounded corners (9 px on controls, 14 px on cards). A total closes with a double rule, as in a ledger. One font, Bahnschrift, which is built into Windows; all its figures have one width, so columns of numbers line up. A computer without it uses its own sans. Raw values use Cascadia Mono. Light pages only; there is no dark mode.

### 2.3 The three jobs of colour

Colour is used for three things only. Everything else is warm white, off-white and brown-grey.

| Job | Colour | Where |
|---|---|---|
| "You can press this" and "you are here" | Burnt orange `#a84200` | The main button of a page, links, the current tab, the current page number of a pager, a finished or current progress step, the rule across the top of the box that holds the work to do |
| "This action ends something or switches something off" | Red words `#b42318` | Dangerous buttons and dangerous lines of an Actions menu, and error text |
| "This is the state of a thing" | The six status families (Section 2.4) | Status badges, flags and messages; also the marks of a rule list and the steps of a strength scale (Section 4.27). Never as the colour of normal text, and never as the background of a card or a page |

An orange button and an amber badge (family Waiting) cannot be mixed up, because they differ in shape: a button is a filled rectangle with white words; a badge is a pale pill with a dot and dark words. A filled red button appears only as the confirm button of a dangerous window, so it never stands in the same window as a filled orange one.

The strength scale of a password field only advises, so it is amber or green and never red. Red stays for errors and for a rule that is not met (Section 4.27).

The side menu is charcoal `#262624`: it is the frame of the system, not an action. Its current entry shows "you are here" as a tab in the page colour that joins the work area (Section 3.3).

### 2.4 The six status families

A status is never shown by colour alone. It is always a badge or a flag with its word.

| Family | Class ending | Background, text | Meaning |
|---|---|---|---|
| Waiting | `-waiting` | `#fdf0d0`, `#7a4b00` (amber) | Waits for somebody else or for time to pass. |
| Warehouse | `-warehouse` | `#e2eefd`, `#154c9c` (blue) | Work is going on. For an order: it is in the warehouse. |
| Road | `-road` | `#ece6fb`, `#4b2fa8` (violet) | With a Delivery Worker, on the way. |
| Done | `-done` | `#ddf3e2`, `#17603a` (green) | Finished well, or in normal use. |
| Problem | `-problem` | `#fce1dd`, `#9a2418` (red) | Failed, blocked, or waiting for a decision of the person who looks at the page. |
| Closed | `-closed` | `#eeebe6`, `#4f4a42` (grey) | Ended, switched off, or a word the page does not know. For a flag also: a neutral fact. |

**How to choose the family of a word.** Look the word up in Section 2.11. If it is not there, ask the questions in this order and take the first "yes":

1. Did something fail, is something blocked, or must the reader decide something now? Problem.
2. Does it wait for somebody else or for time? Waiting.
3. Is it finished well, or switched on and in normal use? Done.
4. Is it with a Delivery Worker or on the way to the client? Road.
5. Is work going on, or is the thing in the warehouse? Warehouse.
6. Everything else, also an unknown word: Closed.

The same word always gets the same family on every page of every role.

### 2.5 Words

- **Language:** English, simple words.
- **Sentence writing:** the first letter of a label is a capital letter, the rest are small letters: "Download CSV", "Ready for picking", "Last login". Only these short words are written in capital letters: LMS, ID, QR, SKU, IP, HKD, CSV. Data keeps its own writing, for example `TEST-0007`, `SKU-1001`, `admin_main`.
- **The plain-word rule.** A word that is stored in capital letters with underscores is shown in plain words: replace every underscore with a space, write all letters small, write the first letter as a capital letter, and keep ID, QR, SKU, IP and CSV in capital letters. `READY_FOR_PICKING` becomes "Ready for picking". `CSV_DOWNLOADED` becomes "CSV downloaded". The few exceptions are marked in Section 2.11.
- **Raw values stay as they are.** Text that is shown as data (for example the old and new values in a log) is not changed by the plain-word rule.
- **Buttons** start with a verb and name the thing: "Create user", "Download CSV", "Approve redelivery". Never "OK", "Yes" or "Confirm".
- **Titles** are plain names: "Users", "Orders", "Delivery cases". No slogans and no describing sentence under a title, only one count line such as "42 accounts".
- **Roles** are written "Client", "Warehouse Worker", "Delivery Worker" and "Admin". They are plain text, never a badge.
- **Numbers:** a count is written "3 of 5". Money is written `HKD 1,234.50` (the letters HKD, a space, a comma between thousands, always two decimal places).
- **Times** are written `2026-11-12 09:14` (year-month-day, a space, hour:minute in 24-hour form). No time zone label is shown anywhere. Inside a sentence a time stands in `time.shared-nowrap` with its `datetime`, so it never breaks across two lines (Section 4.8).
- **An empty value** is shown as `-`.
- **Private data:** no page shows a client's address, phone number or recipient name, unless the page belongs to the person who needs it for the delivery itself.

### 2.6 Text sizes

| Use | Size | Weight | Colour |
|---|---|---|---|
| Page title | 20 px, line height 28 px | 600 | text |
| Count line under the page title | 14 px | 400 | muted |
| Card title | 15 px | 600 | text |
| Window title | 16 px | 600 | text |
| Normal text, table cells, inputs | 14 px, line height 20 px | 400 | text |
| First cell of a table row (the name of the thing) | 14 px | 600 | text |
| Buttons | 14 px (small buttons 13 px) | 600 | by level |
| Side menu entry | 14 px | 500, current entry 600 | menu colours |
| Field label | 13 px | 600 | text |
| Column title, second line in a cell, note, help text | 12.5 px | column title 600, others 400 | muted |
| Status badge | 12.5 px | 600 | family |
| Flag, group label of the side menu, counter | 12 px | 600 | family or menu colours |
| Big number in a number box | 28 px, line height 34 px | 600 | text |
| Raw values and the temporary password | fixed-width font, 13 px (password 18 px) | 400 (password 600) | text |

No text is smaller than 12 px. Nothing is written in capital letters except the short words and data of Section 2.5.

### 2.7 Spacing and sizes

Distances between parts use only these steps: 4, 8, 12, 16, 24 and 32 px.

| Place | Value |
|---|---|
| Padding of the work area | 24 px on every side (16 px in a small window) |
| Between the page header, cards and other blocks of a page | 16 px |
| Card header, filter bar, strip inside a card | 12 px top and bottom, 16 px left and right |
| Card body | 16 px |
| Table cell, left and right | 12 px |
| Window | 20 px left and right |
| Between two fields of a form | 16 px |
| Between a label and its field | 4 px |
| Between two buttons, between two filter controls | 8 px |

Fixed sizes: side menu 212 px wide; top bar 52 px high; menu entry 36 px high; button, input and select 34 px high; small button 28 px high; status badge 24 px high; flag 20 px high; row of a list table 46 px high; window 480 px wide (wide window 560 px).

### 2.8 Motion

Only these things move. Nothing loops, bounces or slides in when a page opens.

| What | Motion | Time |
|---|---|---|
| Mouse over a button, a menu entry, a link line or a table row | The colour changes softly | 0.12 s |
| A button is pressed | It sits 1 px lower while it is held | at once |
| A window opens | It fades in and grows from 96% to 100% of its size; the dark layer behind it fades in | 0.16 s |
| An Actions menu opens | It fades in and moves down 4 px | 0.12 s |
| A message box appears | It fades in and moves down 8 px; after 4 seconds it fades out | 0.2 s |
| Another tab is chosen | The orange bar under the tabs moves to the new tab | 0.15 s |
| Everything else (closing a window or a menu, opening a page, switching the content of a tab) | No motion | - |

For a person who switched animations off on the computer, all of this happens at once, also the dark layer behind a window. The style sheet does this by itself (its Section 20).

### 2.9 Mouse, keyboard and switched-off controls

- **Mouse over:** a filled button gets darker; an outlined button and a line of an Actions menu get a pale background; a table row gets a pale background.
- **Keyboard:** every control that the Tab key reaches shows an orange outline of 2 px (pale orange in the side menu), 2 px away from the control, while it has the keyboard. Never remove this outline. In a tab list only the selected tab is reached with the Tab key, and the arrow keys move from tab to tab (Section 4.13).
- **Skip link and top bar:** on a page with the frame, the first press of the Tab key shows the link "Skip to main content" at the top left (Section 3.2). Enter moves the keyboard to the work area, past the side menu and the top bar. When the keyboard moves to a control that is out of sight, the page scrolls so that the top bar never covers that control. The style sheet does this by itself.
- **Switched off:** a control that cannot be used is pale grey with grey words, and the mouse pointer shows "not allowed". Write the HTML attribute `disabled` on it.

### 2.10 Contrast

These pairs were calculated. The number is the contrast, written as "x to 1". Text needs at least 4.5. An outline needs at least 3.

| Colour | On which background | Contrast |
|---|---|---|
| Text `#1f1f1d` | page, card, pale header, pale hover | 14.60, 16.51, 15.39, 13.57 |
| Muted text `#55554f` | page, card, pale header, pale hover | 6.64, 7.50, 7.00, 6.17 |
| Hint text in an input `#65655e` | card | 5.87 |
| Side menu text `#ecebe6` | menu, menu hover | 12.70, 10.93 |
| Side menu group label `#aeada4` | menu | 6.72 |
| Text of the current entry `#1f1f1d` | current entry (the page colour) | 14.60 |
| Link orange `#a84200` | card, page, pale header, pale hover, top bar | 6.10, 5.39, 5.68, 5.01, 5.73 |
| White | orange button, darker orange button | 6.10, 8.05 |
| White | red button, darker red button | 6.57, 8.36 |
| Counter number `#262624` | counter `#ecebe6` | 12.70 |
| Counter number `#ffffff` on the current entry | counter `#262624` | 15.16 |
| Current page number `#a84200` | pale orange `#f6dccb` | 4.65 |
| Red words `#b42318` | card, pale red hover, pale hover | 6.57, 5.95, 5.40 |
| Status text on its own background | waiting, warehouse, road, done, problem, closed | 6.55, 7.02, 7.61, 6.49, 6.42, 7.39 |
| Status text of Waiting `#7a4b00` and Done `#17603a`: the strength word, the steps of the strength scale and the marks of a rule list | card | 7.41, 7.58 |
| White tick or cross in the mark of a rule | green mark `#17603a`, red mark `#b42318` | 7.58, 6.57 |
| Outline of inputs and buttons `#7a7a73` | card, page, pale header, top bar | 4.32, 3.82, 4.03, 4.06 |
| Keyboard outline `#a84200` | card, page | 6.10, 5.39 |
| Keyboard outline in the side menu `#f7c39b` | menu | 9.55 |

### 2.11 Status words

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
| Account status | `ACTIVE` | Active | Done |
| Account status | (a 15-minute lock is running) | Locked, 12 min left (exception) | Waiting |
| Account status | `LOCKED` (an old value) | Locked (old status) (exception) | Waiting |
| Account status | `BANNED` | Banned | Problem |
| Account status | `INACTIVE` | Deactivated (exception) | Closed |
| Product listing status | `ACTIVE` | Active | Done |
| Product listing status | `INACTIVE` | Inactive | Closed |
| Payment status | `PENDING` | Pending | Waiting |
| Payment status | `ACCEPTED` | Accepted | Done |
| Refund status | `REQUESTED` | Requested | Waiting |
| Refund status | `PENDING` | Pending | Waiting |
| Refund status | `REFUNDED` | Refunded | Done |
| Refund status | `FAILED` | Failed | Problem |
| Answer of the payment provider | `UNKNOWN` | Unknown | Closed |
| Shortage case status | `OPEN` | Open | Problem |
| Shortage case status | `WAITING_RESTOCK` | Waiting restock | Waiting |
| Shortage case status | `RESOLVED` | Resolved | Done |
| Picking task status | `IN_PROGRESS` | In progress | Warehouse |
| Picking task status | `PACKED` | Packed | Done |
| Where a package is | `WAREHOUSE` | Warehouse | Warehouse |
| Where a package is | `DELIVERY` | Delivery | Road |
| Scan result | `REJECTED` | Rejected | Problem |
| Scan result | `REVERSED` | Reversed | Closed |
| Client request status | `APPROVED` | Approved | Done |
| Client request status | `WITHDRAWN` | Withdrawn | Closed |
| Log result | `SUCCESS` | Success | Done |
| Log result | `FAILURE` | Failure | Problem |
| State of a failed delivery | (not returned yet) | Waiting for return | Waiting |
| State of a failed delivery | (a client request waits for a decision) | Needs decision | Problem |
| State of a failed delivery | (returned, no client request) | Waiting for client | Waiting |
| System health | (the database answers) | Connected | Done |
| System health | (the database does not answer) | Not connected | Problem |

A word that is used for several kinds of thing is listed once. `PENDING` is always Waiting, `IN_PROGRESS` is always Warehouse, `ACCEPTED` and `DELIVERED` are always Done, `FAILED` and `REJECTED` are always Problem, and `CANCELLED` is always Closed.

Flags that are already decided:

| Flag text | Family | Meaning |
|---|---|---|
| Low stock | Waiting | The available stock of a product is below its low-stock level. |
| Shortfall 2 | Problem | More units are reserved than exist. The number is the difference. |
| Open case | Problem | An order has a case that waits for a decision. |
| Open shortage case | Problem | An order has a shortage case that is not closed. |
| Review | Problem | A delivery failed at least twice. |
| Reminder due | Waiting | A returned package has waited 3 days for the client's answer. |
| Final review | Problem | A returned package has waited 7 days for the client's answer. |
| Worker not active | Problem | The worker's account is banned or deactivated. |
| Temporary password | Closed | The temporary password of the account has not been used yet. |
| Test mode | Waiting | The system runs in test mode. |
| New | Closed | A notice that was not read yet. |

---

## 3. The page frame

### 3.1 How to read a tree

Sections 3 and 4 describe every part as a tree. Build the HTML exactly like the tree: the same elements, the same classes, the same order.

- One line is one HTML element: the element name, then each class after a dot. `a.shared-button.shared-button-small` is `<a class="shared-button shared-button-small">`.
- A deeper indent means "inside the element one level above".
- Text in double quotes is the text that is shown.
- Square brackets hold attributes that must be written, for example `[type="button"]`.
- Round brackets hold a remark. "(only if ...)" means the element is left out otherwise.
- `icon(name)` stands for one icon and is always written like this: `<svg class="shared-icon" aria-hidden="true"><use href="#icon-name"/></svg>`. The names are listed in Appendix B.

### 3.2 The frame

Every page that a logged-in person works on has the same frame: a side menu on the left, a slim bar on top, and the work area.

```
+--------------+--------------------------------------------------------------------------------+
| LMS Admin    | [Test mode]     Session ends in 12:45   admin_main   Change password [Log out] |
| Dashboard    +--------------------------------------------------------------------------------+
|              |                                                                                |
| Work to do   |  Users                                            [Download CSV] [Create user] |
|  Entry    2  |  42 accounts                                                                   |
|  Entry    3  |  +--------------------------------------------------------------------------+  |
| Manage       |  | card                                                                     |  |
|  Entry       |  |                                                                          |  |
|  Entry       |  +--------------------------------------------------------------------------+  |
+--------------+--------------------------------------------------------------------------------+
    212 px                                   the rest of the window
```

```
body
  svg.shared-icon-sprite                          (the icon list of Appendix B, always the first element of body)
  a.shared-skip-link [href="#ID-OF-MAIN"]         "Skip to main content"   (the first thing that the Tab key reaches)
  div.shared-page-frame
    nav.shared-page-frame-side-menu [aria-label="... pages"]
      div.shared-page-frame-name                  "LMS Admin"   (the system name and the role)
      a.shared-page-frame-menu-item [href]        (one entry; see Section 3.3)
      div.shared-page-frame-menu-label            "Work to do"  (a group label)
      a.shared-page-frame-menu-item [href]
      ...
    header.shared-page-frame-top-bar              (see Section 3.4)
    main.shared-page-frame-main [id="ID-OF-MAIN"] [tabindex="-1"]
      (the page: a page header, then cards and other blocks)
  div.shared-message-area [id="sharedShowMessagePanel"] [aria-live="polite"]     (empty; message boxes appear here)
  dialog.shared-dialog ...                        (every window of the page, after the message area)
```

Rules:

- The side menu fills the left side from the top of the window to the bottom. The top bar starts to the right of it. Both stay in place when the work area scrolls.
- The work area holds its blocks under each other, 16 px apart. Its content is at most 1280 px wide and starts at the left.
- The frame is never narrower than 1000 px, unless it is an adaptive frame (Section 3.6).
- Every page contains the whole frame in its own HTML. The frame is the same on every page of one role; only the current entry changes.
- **Skip link.** The link "Skip to main content" stands directly after the icon list, before the frame. It cannot be seen until it has the keyboard; then it appears at the top left, above the top bar. It leads to the `main` element, which has an `id` and `tabindex="-1"`, so that the keyboard lands in the work area. The `main` element shows no outline. The `id` is the same on every page of one role, for example `adminPageFrameMain`.

### 3.3 Side menu

```
a.shared-page-frame-menu-item [href="page.html"]
  icon(name)
  span.shared-page-frame-menu-text                "Delivery cases"
  span.shared-page-frame-menu-count               "3"    (only if the entry has a counter and the count is above 0)
```

- **System name:** the first line of the menu. The text is "LMS" and the role, for example "LMS Admin". No logo.
- **Entries:** one entry per page. Every entry has an icon and words.
- **Groups:** when a role has more than five pages, the entries are put into groups by the kind of work. The first group holds the pages where something waits to be done. A group starts with a label (`div.shared-page-frame-menu-label`). The start page of the role stands first, without a group label.
- **Current entry:** the entry of the page that is open also has the class `shared-page-frame-menu-item-active` and the attribute `aria-current="page"`. It is drawn as a tab in the page colour that reaches the right edge of the menu and joins the work area; its words are bold. Exactly one entry is current on every page.
- **Counter:** a small light grey pill with a charcoal number at the right end of an entry (on the current entry the pill is charcoal with a white number). Only pages where work waits for the reader may have a counter. A count of 0 shows no pill. A count above 99 shows "99+". An entry with a counter also gets `aria-label`, for example `aria-label="Delivery cases, 3 waiting"`.
- **An entry whose page does not exist yet** (only while a system is being built): the entry is an `a` without `href`, with the classes `shared-page-frame-menu-item shared-page-frame-menu-item-disabled` and `aria-disabled="true"`. After the words it has `span.shared-page-frame-menu-stage` with a short text such as "Stage 2". It is paler, and nothing happens when it is pressed.

### 3.4 Top bar

```
header.shared-page-frame-top-bar
  span.shared-flag.shared-flag-waiting            "Test mode"        (only if the system runs in test mode)
  span.shared-page-frame-spacer                   (empty; pushes the rest to the right)
  span.shared-page-frame-countdown
    icon(clock)
    span.shared-page-frame-countdown-words        "Session ends in"
    " 12:45"                                      (minutes and seconds)
  span.shared-page-frame-user                     "admin_main"       (the username of the logged-in person)
  a.shared-page-frame-top-link [href]             "Change password"
  a.shared-button.shared-button-small [href]      "Log out"
```

The items stand in this order from left to right, 16 px apart. Nothing else is put into the top bar: no logo, no search, no bell, no picture of the person.

### 3.5 Work area and page header

Every page starts with a page header (Section 4.1): the page title, under it at most one count line, and on the right the buttons of the page. Below it come cards and other blocks.

### 3.6 Small windows (adaptive frame)

A role whose pages must also work on a phone adds the class `shared-page-frame-adaptive` to the frame. A role that works at a desk does not. In a window narrower than 900 px an adaptive frame changes like this:

- The side menu is hidden. A button "Menu" at the left end of the top bar opens it as a panel over the page. A button "Close menu" in the first line of the panel closes it. Opening and closing happens at once, without motion.
- The top bar shows only: "Menu", the flag, the clock with the time, and "Log out". The words "Session ends in", the username and "Change password" leave the top bar. The username and "Change password" stand at the bottom of the menu panel.
- The work area has 16 px padding. The page header puts its buttons under the title.
- A work table (Section 4.8) becomes a list of cards: one row is one card, and every value has its column title above it.
- A list table (Section 4.7) keeps its shape and scrolls sideways inside its card. So a page that must work on a phone should use work tables.

An adaptive frame needs three more elements. A frame that is not adaptive leaves them out.

```
div.shared-page-frame.shared-page-frame-adaptive
  nav.shared-page-frame-side-menu
    div.shared-page-frame-name
      "LMS Delivery"
      button.shared-button.shared-button-small.shared-page-frame-menu-close [type="button"]   "Close menu"
    (the entries)
    div.shared-page-frame-menu-footer             (the last element of the menu)
      span.shared-page-frame-user                 "dl_driver_01"
      a [href]                                    "Change password"
  header.shared-page-frame-top-bar
    button.shared-button.shared-button-small.shared-page-frame-menu-button [type="button"]    "Menu"   (the first element of the top bar)
    (the other items of Section 3.4)
```

The script adds the class `shared-page-frame-menu-open` to the frame when "Menu" is pressed and removes it when "Close menu" or a menu entry is pressed.

---

## 4. Parts

Every page is put together from the parts of this section. Do not invent a new part. If a page needs something that no part covers, ask.

### 4.1 Page header

```
div.shared-page-header
  div.shared-page-header-text
    a.shared-page-back [href]                     "Back to orders"   (only on a page that was opened from a list)
    h1.shared-page-title                          "Users"            (a badge or a flag may follow the words inside the h1)
    p.shared-page-subtitle                        "42 accounts"      (at most one count line; leave it out if there is nothing to count)
  div.shared-page-header-actions                  (only if the page has buttons)
    button.shared-button ...                      (other buttons first)
    button.shared-button.shared-button-primary    (the main button last, at the right end)
```

A page has exactly one `h1`. A heading between groups of cards is `h2.shared-section-title`.

### 4.2 Buttons

```
button.shared-button [type="button"]
  icon(name)                                      (only where Section 4.26 allows an icon)
  "Download CSV"
```

| Level | Classes | Look | Used for |
|---|---|---|---|
| Main | `shared-button shared-button-primary` | Filled orange, white words | The one main action of a page ("Create user") and the confirm button of a window. At most one per page and one per window. |
| Other | `shared-button` | White, grey-brown outline, dark words | Every other action: "Download CSV", "Search", "Refresh", "Actions", the decision buttons in a work table, "Cancel", "Close". |
| Dangerous | `shared-button shared-button-danger` | White, red outline, red words | An action that ends something or switches something off, shown on a page. |
| Dangerous, final | `shared-button shared-button-danger-filled` | Filled red, white words | Only the confirm button of a window that confirms a dangerous action. |
| Text link | `shared-button shared-button-quiet` | Orange underlined words, no outline | A small side action next to other buttons: "Clear", "Clear filters". |

More classes: `shared-button-small` (28 px high; used inside table rows, in the pager, for "Actions", "Copy" and in the top bar) and `shared-button-wide` (as wide as its box; used for the one button of a login card).

Rules:

- Every button has words. No button shows only an icon, also not for closing a window or copying a value.
- A label starts with a verb and names the thing.
- In a group of buttons the most important one stands at the right end.
- A button that leads to another page is an `a` with the same classes and an `href`.
- A button that cannot be used gets the attribute `disabled`.
- An action is dangerous when it ends something for good or switches something off, for example: ban a user, deactivate a user or a product, cancel an order, remove data. Everything else is not dangerous.

### 4.3 Links

A link is an `a` with `href`. It is orange and always underlined, also inside a table. Colour alone would not show a link, because the orange and the dark text colour are close to each other. The first cell of a table row is a link when it opens a page about that row.

### 4.4 Fields

```
label.shared-field
  span.shared-field-label                         "Username"
    span.shared-field-optional                    " (optional)"   (only for a field that may stay empty)
  input.shared-input [type="text"]
  span.shared-field-help                          "4 to 30 characters."   (only if there is something to explain)
```

| Field | Tree |
|---|---|
| One line of text | `input.shared-input [type="text"]`. Other types use the same class: `password`, `number`, `datetime-local`. |
| A password that is set or changed | The password field (Section 4.27): the input with a "Show" button, and under a new password the strength scale and the rule list. |
| Several lines of text | `textarea.shared-textarea` |
| A choice from a list | `span.shared-select.shared-select-wide` holding `select [aria-label]` and after it `icon(chevron)`. Outside a form leave out `shared-select-wide`. |
| A tick box | `label.shared-checkbox` holding `input [type="checkbox"]` and `span` with the words. It is not wrapped in `label.shared-field`. |
| A file | `input.shared-file-input [type="file"]` |
| A search box | `label.shared-input-search` holding `icon(search)` and `input [type="search"] [placeholder] [aria-label]` |

Rules:

- The label stands above the field. Fields of a form stand under each other and are as wide as the form.
- A field with an error also has the class `shared-input-invalid` (a select: `shared-select-invalid` on the `span`), and under it stands `span.shared-field-error` with the reason instead of the help text. A password field keeps its help (Section 4.27).
- A field that cannot be changed gets the attribute `disabled`.
- Widths outside a form: `shared-input-short` (96 px) and `shared-input-medium` (200 px).

### 4.5 Card

```
section.shared-card
  div.shared-card-header                          (only if the card has a title)
    h2.shared-card-title
      "Shortage cases"
      span.shared-card-count                      "4"   (only if the card shows a list)
    div.shared-card-actions                       (only if the card has controls of its own)
  div.shared-card-strip                           (only for a note that stands above a table)
    p.shared-note                                 "..."
  div.shared-card-body                            (text, fact lists, forms)
  (or, instead of the body: a filter bar, a table and a pager, which reach the edges of the card)
```

- A card is a white leaf with a 1 px edge and no shadow. Cards never stand inside cards.
- Inside `shared-card-body` blocks stand 12 px apart.
- A second table in the same card starts with `h3.shared-card-part-title`.

### 4.6 Filter bar

```
div.shared-filter-bar
  label.shared-input-search ...                   (the search box, if there is one)
  span.shared-select ...                          (selects)
  span.shared-filter-range                        (a date range: its two fields stay together when the bar wraps)
    label.shared-filter-label                     (a date field with its word)
      "From"
      input.shared-input [type="datetime-local"]
    label.shared-filter-label
      "To"
      input.shared-input [type="datetime-local"]
  input.shared-input [type="text"] [placeholder] [aria-label]     (other text filters)
  label.shared-checkbox ...                       (tick boxes)
  span.shared-filter-buttons
    button.shared-button [type="button"]          "Search"
    button.shared-button.shared-button-quiet [type="button"]   "Clear"
  span.shared-filter-end                          (only if a button belongs at the right end, for example "Download CSV")
```

The filter bar is the first row inside the card that holds the table. The controls stand in this order in one row. If they do not fit, they wrap onto a second row; "Search" and "Clear" always stay together. A control without a visible label says what it is through its empty text ("Search username", "All roles") and has an `aria-label`.

### 4.7 List table

For a long list that people search: users, products, orders, log rows.

```
div.shared-table-scroll [role="region"] [aria-label="Users table"]   (a named region)
  table.shared-table
    thead
      tr
        th [scope="col"]                          "Username"
        th.shared-table-number [scope="col"]      "Price"       (a number column)
        th.shared-table-actions [scope="col"]     "Actions"     (the last column, if rows have actions)
    tbody
      tr
        th [scope="row"]                          "admin_second"   (the name of the thing; bold)
        td                                        ...
        td.shared-table-number                    "HKD 80.00"
        td.shared-table-actions                   (an Actions menu, Section 4.12)
    tfoot                                         (only for a money table that states a sum)
      tr
        th [scope="row"] [colspan]                "Total"
        td.shared-table-number                    "HKD 45.00"
```

- **One line per row.** A row is 46 px high and its cells do not wrap.
- **A free-text column may wrap** (a product name, a note, a raw value). Its `th` and every `td` of it get the class `shared-table-wrap`.
- **Nothing is cut off.** If a table is wider than its card, it scrolls sideways inside `shared-table-scroll` and the scrollbar is visible.
- **A named region that keeps its first column.** The scroll box of every list table has `role="region"` and an `aria-label` that names the table, for example "Orders table". While the table is wider than its card, the script (B11) adds the class `shared-table-scroll-overflow` and `tabindex="0"`: the first column stays at the left behind a margin rule, the keyboard can reach and scroll the list, and a focused cell never hides under the first column. When the table fits, the box is not a Tab stop.
- **A sum closes with a total row.** A money table that states a sum ends with one `tfoot` row: `th [scope="row"]` "Total" across the other columns and the sum in `td.shared-table-number`. A rule stands above it and a double rule below it; 12 px of card ground under the double rule keep it apart from the edge of the card.
- **The first cell** of a row is a `th` with `scope="row"`. It holds the name of the thing.
- **Numbers and money** are right-aligned: class `shared-table-number` on the `th` and on every `td` of the column.
- **Times** get the class `shared-table-time`, so the digits stand under each other.
- **Several things in one cell** (a badge, a note and a flag) are wrapped in `span.shared-cell-line`. A small grey note next to a badge is `span.shared-cell-note`.
- **A table with no rows** shows, instead of the `div.shared-table-scroll`, one line: `p.shared-table-empty` with the words "No rows found." and, if filters are set, the text-link button "Clear filters".
- A list table has no button in its rows except the Actions menu. Rows are not sorted by clicking a column title.

In a window that is 1280 px wide the table has about 1000 px. Choose the columns so that they fit into that width.

### 4.8 Work table

For a list of cases where a person must decide something.

```
div.shared-table-scroll
  table.shared-table.shared-table-work
    thead                                         (as in a list table)
    tbody
      tr
        th [scope="row"] [data-title="Order"]
          span.shared-cell-main                   (first line)
          span.shared-cell-sub                    (second lines, small and grey)
          span.shared-cell-flags                  (flags, if any)
        td [data-title="Failure"]                 ...
        td.shared-table-actions [data-title="Actions"]
          div.shared-cell-buttons
            button.shared-button.shared-button-small [type="button"]   "Approve redelivery"
          span.shared-cell-sub                    "Decide the open shortage case first."   (only while a rule blocks a decision; its button then has [disabled])
```

- **Cells may hold several lines** and wrap. So a work table never scrolls sideways.
- **Every body cell carries `data-title`** with the title of its column. A small window uses it (Section 3.6).
- **The decision buttons are visible** in the last column as small buttons with words, right-aligned. They are level "other"; a dangerous one is level "dangerous". They wrap inside the cell when they do not fit in one line.
- **A decision that a rule blocks right now** keeps its button in the row, with the attribute `disabled`. Directly after `div.shared-cell-buttons` stands one `span.shared-cell-sub` that says why, for example "Decide the open shortage case first." Never hide a blocked decision without saying why.
- **Every button of a row names its record** for a screen reader: its `aria-label` starts with its words and adds the record, for example `aria-label="Reject request for order TEST-0003"` on the button "Reject". The words on the button stay short. A link in the row that leads to the record's details does the same, for example `aria-label="View shortage cases of order TEST-0006"`.
- **A window opened from a row is that row's window.** Its title, its facts and its message name the same record as the row.
- **A decided row stays until the list is loaded again.** After a decision in a window, the row keeps its place. The cell that holds its state shows the new badge and one line with who decided and when, for example `badge(closed, "Cancelled")` and "Cancellation approved by admin_main, 2026-11-12 09:14. Full refund requested."; the buttons are gone (`muted("-")`), and the keyboard goes to the record's link in the row. The next search or page load shows the list without the decided row (Section 7, behaviour B13).
- A text that must stay on one line (an order reference) is wrapped in `span.shared-nowrap`. A date and time inside a line of text stands in `time.shared-nowrap [datetime]`, for example `<time class="shared-nowrap" datetime="2026-11-11T16:00">2026-11-11 16:00</time>`, so it never breaks.
- A note that a person typed is shown in quotation marks.
- **A table with no rows** shows one line `p.shared-table-empty` that says there is nothing to do, for example "No shortage case is waiting."

Why two kinds of table: a list is searched, a case is decided. A list row is one line, so many rows fit. A case row shows everything that is needed for the decision, and the buttons that decide it.

### 4.9 Pager

```
div.shared-pager
  span                                            "Page 1 of 3"
  span.shared-pager-buttons
    button.shared-button.shared-button-small [type="button"] [disabled]   "Previous"   (switched off on the first page)
    span.shared-pager-current [aria-current="page"]                       "1"          (the current page is not a button)
    button.shared-button.shared-button-small [type="button"]              "2"
    button.shared-button.shared-button-small [type="button"]              "3"
    button.shared-button.shared-button-small [type="button"]              "Next"       (switched off on the last page)
```

The pager is the last row inside the card of a table. A table shows 20 rows per page. The current page number is drawn in bold orange on a pale orange ground with a 2 px orange outline: you are here. It is not a filled orange shape, so it is never taken for the main button.

### 4.10 Status badge

```
span.shared-status-badge.shared-status-badge-problem     "Delivery failed"
```

A pill with a dot in front of the word. The second class names the family (Section 2.4): `-waiting`, `-warehouse`, `-road`, `-done`, `-problem` or `-closed`. A badge says what state a thing is in. It never stands without its word, and it is never a button.

### 4.11 Flag

```
span.shared-flag.shared-flag-waiting                     "Low stock"
```

A small label without a dot. It adds one more fact about a thing. The second class names the family in the same way. A badge and a flag look different on purpose.

### 4.12 Actions menu

Used in the rows of a list table.

```
span.shared-menu
  button.shared-button.shared-button-small [type="button"] [popovertarget="ID"] [aria-label="Actions for NAME"]
    "Actions"
    icon(chevron)
  div.shared-menu-list [id="ID"] [popover]
    button.shared-menu-item [type="button"]                          "View"
    button.shared-menu-item [type="button"]                          "Change role"
    div.shared-menu-divider [role="separator"]                       (only if dangerous lines follow)
    button.shared-menu-item.shared-menu-item-danger [type="button"]  "Ban"
```

- `ID` is a name that is used only once in the page, for example `menu-user-3`.
- `NAME` is the name of the thing in the row, as its first cell shows it: `aria-label="Actions for admin_second"`, `aria-label="Actions for SKU-1001"`. A screen reader then says which row the button belongs to. The visible word stays "Actions".
- The menu is a simple panel of buttons. It has no `role="menu"` and no `role="menuitem"`, because those roles promise arrow keys that this menu does not have. The Tab key moves through its lines. Escape closes it and gives the keyboard back to its button.
- The menu lists only the actions that are possible for that row. Dangerous lines come last, after the divider, in red words.
- The menu opens under its button, 4 px below it, with its right edge in line with the button's right edge. If there is no room below, it opens above the button. If there is no room above either, it stands 8 px below the top of the window. It is never higher than the window: lines that do not fit scroll inside the menu. It is drawn above everything else and is never cut off by the card.
- It closes when a line is chosen, when Escape is pressed, when the mouse clicks somewhere else, and when the page scrolls.
- A row that has only one possible action still uses the menu. A row with no possible action shows a short grey text instead (`span.shared-muted`).

### 4.13 Tabs

For two to four views of the same kind of data on one page.

```
div.shared-tab-list [role="tablist"] [aria-label]
  button.shared-tab.shared-tab-active [type="button"] [role="tab"] [id="ID-OF-TAB"] [aria-selected="true"] [aria-controls="ID-OF-PANEL"] [data-tab="ID-OF-PANEL"]
    "Audit log"
    span.shared-tab-note                          "kept 3 years"   (only if there is a short fact to add)
  button.shared-tab [type="button"] [role="tab"] [id="ID-OF-TAB"] [aria-selected="false"] [aria-controls="ID-OF-PANEL"] [data-tab="ID-OF-PANEL"]
    ...
div.shared-tab-panel [id="ID-OF-PANEL"] [role="tabpanel"] [aria-labelledby="ID-OF-TAB"]            (the content of the first tab)
div.shared-tab-panel [id="ID-OF-PANEL"] [role="tabpanel"] [aria-labelledby="ID-OF-TAB"] [hidden]   (the content of every other tab)
```

The current tab has dark words and an orange bar under it. Different jobs are never put into tabs; they are different pages in the side menu.

Every tab and every panel has its own `id`. A tab names its panel with `aria-controls` (and with `data-tab`, which the script uses). A panel names its tab with `aria-labelledby`, so a screen reader can say which panel belongs to which tab. With the keyboard, only the selected tab is reached with the Tab key (the other tabs have `tabindex="-1"`), and the arrow keys, Home and End move from tab to tab (Section 7, behaviour B7).

### 4.14 Windows

```
dialog.shared-dialog [id] [aria-labelledby="ID-OF-TITLE"]
  h2.shared-dialog-title [id]                     "Ban wh_picker_02?"
  div.shared-dialog-body
    p                                             (what will happen, in full sentences)
    label.shared-field ...                        (fields, if any)
  div.shared-dialog-footer
    button.shared-button [type="button"] [data-close-dialog]              "Cancel"
    button.shared-button.shared-button-danger-filled [type="button"] [data-confirm]   "Ban user"
```

- A window is 480 px wide. With the class `shared-dialog-wide` it is 560 px wide (a form with many fields or a table).
- There is no "x" in the corner. A window closes with its "Cancel" or "Close" button or with the Escape key. A click on the dark layer does not close it.
- The closing button stands left of the confirm button. It is called "Cancel". In a window whose action is itself a cancellation, it is called "Close", so the two buttons cannot be mixed up.
- An action with feedback inside an open window shows it there, as specified for that action (Section 4.15). "Print label" opens the browser print window. It adds no inline message. A sample control marked as doing nothing stays that way.

| Kind | Body | Confirm button |
|---|---|---|
| Form window | The fields under each other. An error is shown as an inline message (Section 4.15) above the first field. | Main level, named after the action: "Create user", "Save product". |
| Confirm window | The title asks the question and names the thing. The body says in full sentences what will happen. If a reason is needed, a field "Reason" follows, with the help text "At least 5 characters." | Named after the action: "Ban user". Main level, or "Dangerous, final" for a dangerous action. It is switched off until every needed input is given. |
| Information window | Shows something once, for example a temporary password or a label. | "Close", main level. |

Two special boxes for an information window:

```
div.shared-secret-box                             (a value that must be read letter by letter)
  span.shared-secret-text [id="ID-OF-VALUE"]      "Kd7p-Wm3x-Qh9t"
  button.shared-button.shared-button-small [type="button"] [data-copy="ID-OF-VALUE"] [data-copy-place="ID-OF-PLACE"] [data-copy-failed="text"]   "Copy"
div.shared-message-place [id="ID-OF-PLACE"] [aria-live="polite"]   (written empty; the answer to "Copy" appears here: Section 4.15)

div.shared-label-box                              (a label that can be printed)
  svg.shared-label-picture [aria-hidden="true"] > use [href="#icon-label-sample"]
  p.shared-strong                                 "SKU-1002"
  p                                               "Sample product B"
```

### 4.15 Message box, inline message and note

```
div.shared-message-box.shared-message-box-success [role="status"]
  icon(check)
  p                                               "User wh_picker_02 was banned."
```

| Part | Classes | Icon | Used for |
|---|---|---|---|
| Message box | `shared-message-box` and `shared-message-box-success`, `-error` or `-information` | check, alert, information | The answer to an action. It appears in the top right corner inside `div.shared-message-area` and leaves after 4 seconds. |
| Inline message | `shared-inline-message` and, for an error, `shared-inline-message-error`; for a success, `shared-inline-message-success`; for information, no second class | alert, check, information | A message that stays in the page or in a window: an error above a form, a notice on the login page. The tree is the same: the icon, then `p`. |
| Message place | `div.shared-message-place [id] [aria-live="polite"]` | none | A box that is always in the page: inside a window, above the first field of a form, or under the title of a page without the frame. The script puts one inline message into it: the feedback of an action inside a window (for example "Copy"), an error of a form that belongs to no field, or a notice. Because the box is already in the page, a screen reader reads what is put into it; never show a hidden box and fill it in the same step. It takes no room while it is empty, so write nothing between its two tags, not even a space. On a page without the frame it may hold its message from the start (Section 4.20). |
| Note | `p.shared-note` | none | A fixed sentence that explains a page or a part, for example "Stock is changed only by Warehouse." It is a quiet line without a box; inside a card body it stands on a ruled line. |

Colour is never the only sign: every message has its icon and its words. A message box takes the pale ground and the line of its family; it has no coloured side stripe.

A corner message box may appear after a direct page action or after a window closes, while no modal window is open. Opening a modal clears any corner boxes already on the page. They stay gone when it closes. When an action calls for feedback inside an open window, put it in the place specified for that action. A form error goes above the first field (Section 4.14); on a page with password fields it stands under its own field (Section 4.27).

### 4.16 Number box

For a page that shows counts, for example a dashboard.

```
section.shared-card.shared-number-box
  h2.shared-number-box-title                      "Open cases"
  p.shared-number-box-value                       "8"
  p.shared-number-box-hint                        "The total counts the first group."   (only if needed)
  p.shared-number-box-group                       "Work to do"                          (a group label)
  a.shared-number-line [href]
    span.shared-number-line-name                  "Shortage cases"     (always there; it holds the words or one status badge; a second line is span.shared-cell-sub inside it)
    span.shared-number-line-count                 "3"                  (a count of 0 also has the class shared-number-line-count-zero, except in a list of stock, where 0 is the most urgent figure)
```

Every line is a link to the list it counts. The name always stands inside `span.shared-number-line-name`, also when it is a status badge. That element takes the free room of the line, so all counts of a box stand in one column at its right edge. A line whose name is a badge keeps the badge's colours and shape, and the badge's words take the link underline, so the line reads as a link; it also gets the pale background under the mouse and the orange outline with the keyboard. A badge outside a count line has no underline. The big number is never coloured and has no icon; a double rule closes it, as a ledger closes a total. The box that holds the work to do also has the class `shared-number-box-work`: an orange rule across its top shows where the day starts. Number boxes stand in `div.shared-grid-three`.

### 4.17 Fact list

For a few named facts about one thing.

```
dl.shared-fact-list
  div.shared-fact
    dt.shared-fact-label                          "Client"
    dd.shared-fact-value                          "client_0462"
```

The pairs stand next to each other, up to four in a row, and wrap.

**Decision facts.** A window whose decision cancels something starts with the two facts the decision turns on, as one ruled entry above the needed tick and the reason:

```
dl.shared-decision-facts
  div.shared-fact
    dt.shared-fact-label                          "Payment"
    dd.shared-fact-value                          "Paid"   (or "No accepted payment")
  div.shared-fact
    dt.shared-fact-label                          "Outcome"
    dd.shared-fact-value                          "The whole order is cancelled for good. A full refund is requested."
```

The label stands left of its value. The entry has a thin red-ink edge, because the decision is dangerous.

### 4.18 Timeline

For the history of one thing, oldest first, as a day book: one block per day, under its date.

```
div.shared-timeline-day                           (one block per day)
  h3.shared-timeline-date                         "2026-11-10"
  ol.shared-timeline
    li.shared-timeline-entry
      time.shared-timeline-time [datetime="2026-11-10T11:23"]   "11:23"
      span.shared-timeline-text
        "Payment accepted."
        span.shared-timeline-actor                " dl_driver_01"   (only if a person did it)
```

Each entry is a ruled line. It shows only the hour and minute; the full time stands in `datetime`. The date head is written like every date (Section 2.5). A card title is an `h2`, so the date head is an `h3`.

### 4.19 Layout helpers

| Class | What it does |
|---|---|
| `div.shared-grid-two` | Puts its blocks next to each other in two equal columns. They stand under each other when each would be narrower than 320 px. |
| `div.shared-grid-three` | The same with three columns and 280 px. |
| `div.shared-button-row` | Puts buttons, badges or other small parts in one row, 8 px apart. They wrap. |
| `h2.shared-section-title` | A heading between groups of cards. |

### 4.20 Page without the frame

For the pages before login and other pages that stand alone.

```
body
  svg.shared-icon-sprite
  main.shared-centre-page
    p.shared-centre-name                          "LMS"
    p.shared-centre-sub                           "Logistics Management System"
    section.shared-card.shared-centre-card        (form.shared-card.shared-centre-card on a page that sends a form;
                                                   also shared-centre-card-wide for a card of 420 px)
      h1.shared-centre-title                      "Log in"
      (the message place of Section 4.15 with its message, if any; then fields; then the button)
      a.shared-button.shared-button-primary.shared-button-wide [href]   "Log in"
```

A row with a link at the left and a button at the right is `div.shared-centre-links`. A way out under the main button of a form ("Cancel", "Log out") is a quiet button or button-link (`shared-button-quiet`) alone in that row. A list of links is `ul.shared-link-list` with one `li` per link.

### 4.21 Progress steps

For the way of one thing through fixed steps, for example an order from payment to delivery.

```
ol.shared-step-list
  li.shared-step.shared-step-done
    span.shared-step-mark
      icon(tick)                                  (done: a tick; problem: icon(alert); other steps: empty)
    span.shared-step-text
      span.shared-cell-main.shared-step-title     "Ready for picking"
        span.shared-visually-hidden               " (done)"
      span.shared-step-note                       "2026-11-10 11:23"   (only if there is a time or a short fact)
```

| Step | Class | Look | Hidden words after the title |
|---|---|---|---|
| Finished | `shared-step-done` | A filled orange circle with a tick, dark title | " (done)" |
| Where the thing is now | `shared-step-current` | An orange ring, bold title | " (current step)" |
| Not reached yet | no second class | A grey ring, grey title | " (not reached yet)" |
| A step that failed | `shared-step-problem` | A filled red circle with an alert icon, red bold title | " (problem)" |

The hidden words make sure that the state is not shown by colour alone.

### 4.22 Summary

For lines of a name and an amount with a total, for example the summary of an order.

```
dl.shared-summary
  div.shared-summary-row
    dt                                            "3 x Sample product A"
    dd                                            "HKD 240.00"
  div.shared-summary-row.shared-summary-total
    dt                                            "Total"
    dd                                            "HKD 285.00"
```

The total stands between a rule and a double rule.

### 4.23 Notice list

For messages to the person who is logged in.

```
ul.shared-notice-list
  li.shared-notice.shared-notice-unread           (a read notice has no second class)
    div.shared-notice-text
      p.shared-notice-title
        "Order TEST-0007 could not be delivered"
        span.shared-flag.shared-flag-closed       "New"   (only on an unread notice)
      p.shared-notice-body                        "Please choose redelivery or cancellation on the tracking page."
    span.shared-notice-time                       "2026-11-10 14:30"
```

The list stands directly inside a card, without `shared-card-body`.

### 4.24 Scan box, capture box and progress bar

```
div.shared-scan-box                               (where a code is typed or scanned)
  input.shared-input [type="text"] [placeholder="Scanned code"] [aria-label="Scanned code"]
  button.shared-button [type="button"]            "Check code"

div.shared-capture-box                            (where a photo or a signature will appear)
  p                                               "No photo yet"
  button.shared-button [type="button"]            "Take photo"

div.shared-progress
  progress.shared-progress-bar [value="3"] [max="5"] [aria-labelledby="ID-OF-TEXT"]
  span.shared-progress-text [id="ID-OF-TEXT"]     "3 of 5 picked"
```

A progress bar always has its words next to it. The bar names its words with `aria-labelledby`, so a screen reader reads them as the name of the bar.

### 4.25 Small helpers

| Class | What it does |
|---|---|
| `shared-muted` | Grey text for a remark. |
| `shared-strong` | Bold text. |
| `shared-nowrap` | Keeps a short text on one line. On `time`, it keeps a date and time together inside a sentence (Section 4.8). |
| `shared-fixed-text` | The fixed-width font, for raw values. |
| `shared-visually-hidden` | Text that only a screen reader reads. |

A raw old and new value (as in a log) is written as two lines:

```
span.shared-value-line
  span.shared-cell-note                           "Old"
  span.shared-fixed-text                          "{"accountStatus":"ACTIVE"}"    (or the plain text "-")
span.shared-value-line
  span.shared-cell-note                           "New"
  span.shared-fixed-text                          "{"accountStatus":"BANNED"}"
```

### 4.26 Icons

Icons are small line drawings. All of them are in Appendix B. An icon is allowed only in these places, and always next to words:

| Place | Icon |
|---|---|
| The entries of the side menu | One icon per entry |
| A search box | search |
| A select, and the "Actions" button | chevron |
| The session time in the top bar | clock |
| A main button that creates something ("Create user", "Add product") | plus |
| A button that downloads a file ("Download CSV") | download |
| Messages | check (success), alert (error), information |
| The mark of a progress step | tick (done), alert (problem) |

No other button, title, label or table cell gets an icon.

### 4.27 Password field

For a password that is set or changed. A password field is `div.shared-field` with `label.shared-field-label [for]`, not the `label.shared-field` of Section 4.4, because a button may not stand inside a label.

```
div.shared-field
  label.shared-field-label [for="ID"]                  "New password"
  div.shared-password-input
    input.shared-input [type="password"] [id="ID"] [autocomplete] [aria-describedby]
    button.shared-button.shared-password-show [type="button"] [aria-label="Show new password"] [data-show-password]   "Show"
  span.shared-field-help                                (only if there is something to explain)
  div.shared-strength                                   (only under a new password)
    div.shared-strength-scale [aria-hidden="true"]      four empty span
    p.shared-strength-line
      span                                              "Avoid names and common words."
      span.shared-strength-word [aria-live="polite"]    "Strength: not rated yet"
  ul.shared-rule-list [id]                              (under a new password: one line per rule; under its repeat: one line)
    li.shared-rule [data-password-rule="length"]
      span.shared-rule-mark [aria-hidden="true"]
      span
        span.shared-visually-hidden                     "Still needed: "
        (the words of the rule)                         "8 to 72 characters"
  span.shared-field-error [id] [hidden]
```

Rules:

- **"Show".** The button has words, never only an eye icon. Pressing it shows the characters and changes its words to "Hide"; its `aria-label` names the field ("Show new password", "Hide new password"). It is at least 64 px wide, so the input does not move when the words change, and it never sends the form.
- **The rule list** stands under a new password, one line per rule: "8 to 72 characters", "At least one capital letter, A to Z", "At least one small letter, a to z", "At least one digit, 0 to 9". Each line starts with a mark and hidden words: an empty ring and "Still needed:" while the rule waits; a green disc with a tick and "Done:" when it is met (the words turn grey); a red disc with a cross and "Not met:" when it fails (the words turn red). So a state is never shown by colour alone. A line turns red only after the first press of the main button, except "8 to 72 characters", which turns red at once when the password is too long. The repeat field has one line, "The same as the new password", ticked when both are the same.
- **The strength scale** stands right under the input of a new password: four steps, then one line with the advice on the left and the strength word on the right. It counts only the length and the kinds of characters, so the advice names what it cannot see. It only advises: it never stops the main button, and it is never red.

  | Level | When | Steps | Word |
  |---|---|---|---|
  | Not rated | The field is empty. | none | "Strength: not rated yet" |
  | Too weak | A rule of the list is not met. | 1, amber | "Strength: too weak" |
  | Fair | Every rule is met, fewer than 12 characters, only letters and digits. | 2, amber | "Strength: fair" |
  | Good | Every rule is met, and 12 to 15 characters of only letters and digits, or fewer than 12 with another character (a character other than A to Z, a to z and 0 to 9, for example a symbol or a space). | 3, green | "Strength: good" |
  | Strong | Every rule is met, and 16 or more characters, or 12 or more with another character. | 4, green | "Strength: strong" |

  No password fits two levels. The word stands in a polite live region and changes only when the level changes, so a screen reader hears it once.
- **Counting** is the same as the server's: a character is one whole character (`界` is one character), and the limit of 72 bytes counts the password written as UTF-8.
- **Errors** stand under their own field in `span.shared-field-error`, which is in the page from the start with `hidden`. A field with an error gets `shared-input-invalid` and `aria-invalid="true"`; so does a field whose rule line or match line is red, also without words. Two errors of a new password show at once, because typing on cannot fix them: "Too long: some of these characters count as more than one. Use fewer characters." (more than 72 bytes in at most 72 characters) and "The new password must be different from the current one." After a press with a rule still not met, the line says "Meet every rule marked in red." After a press, the keyboard goes to the first marked field. A press first removes every earlier answer of the server, under the fields and in the message place. An answer of the server then stands under the field it belongs to, in place of the page's own line, until the next press or until the person types in that field again. An error that belongs to no field goes into the message place above the first field (Section 4.15).
- **The account name.** A form with password fields also holds `input [type="text"] [autocomplete="username"] [hidden]` with the account name, so that a password manager saves the password for the right account.
- **While the form is sent,** the main button is switched off and says what is happening ("Changing password").

---

## 5. How to change a draft page

### 5.1 The steps

Do these steps in this order for every draft screen.

1. **List the jobs of the screen.** A draft screen is one long page that holds several jobs: each tab, each big section and each list is a job. Write them down, for example "create an order", "see my orders", "follow one order".
2. **Make one page per job.** Each job becomes its own page with its own entry in the side menu. Never put different jobs into tabs or into one long page. A page that shows one thing in detail (one order, one task) is opened from a list and has no menu entry of its own; the entry of its list stays the current one.
3. **Build the side menu of the role** (Section 3.3): the system name with the role, then the entries. With more than five entries, group them; the first group holds the pages where work waits.
4. **Choose the kind of page** for each page (Section 5.2).
5. **Replace every thing of the draft by its part** (Section 6.6). Throw away what Section 6.2 lists.
6. **Rewrite the words** (Section 2.5 and Section 6.4): plain titles, sentence writing, plain words on badges, buttons that start with a verb.
7. **Choose the columns of every table** so that nothing must be cut: only what a person needs to decide what to do. Everything else belongs on the page of the single thing.
8. **Put in sample data** (rule 8 of Section 1.3). Numbers that appear on two pages must agree.
9. **Do the checks** of Section 9.

### 5.2 The kinds of page

Every page is one of these. Each starts with the page header (Section 4.1).

| Kind | When | What it holds, from top to bottom |
|---|---|---|
| List page | A long list that people search | One card: filter bar, list table, pager. A main button in the page header creates a new thing. Each row has an Actions menu or is a link to its detail page. |
| Work page | A list of cases where the reader must decide or act | One card per kind of case: a card header with the title, the count and at most one filter; an optional note; a work table with the decision buttons in each row; a pager. |
| Detail page | One thing, opened from a list | A back link above the title. A badge with the state after the title. Then cards under each other: a fact list first, then the parts of the thing as small tables, a timeline or progress steps last. Two short cards may stand next to each other in `div.shared-grid-two`. |
| Form page | Entering something that needs more room than a window | Cards under each other, one per group of fields. A summary card may stand to the right of the form in `div.shared-grid-two`. One main button at the end of the last card, inside `div.shared-button-row`. |
| Overview page | The start page of a role | Number boxes in `div.shared-grid-three`; every number is a link to its list. Nothing decorative. |
| Page without the frame | Before login, or a page that stands alone | Section 4.20. |

Creating or changing one thing with a few fields happens in a form window (Section 4.14), not on a page. Asking "are you sure" happens in a confirm window. A window is always opened by a button with words.

### 5.3 Decisions you must take for every page

- **Which button is the main one?** At most one per page. A page where the reader only looks things up has none.
- **Which actions are dangerous?** Only those that end something for good or switch something off (Section 4.2).
- **List table or work table?** A list is searched; a case is decided (Sections 4.7 and 4.8). A page for a phone uses work tables.
- **Badge, flag or plain text?** The state of the thing is a badge. One more fact about it is a flag. Everything else is plain text.
- **Does the role work on a phone?** Then the frame is adaptive (Section 3.6).

---

## 6. What each thing in the draft becomes

### 6.1 Technique: remove these from every draft page

| In the draft | What to do |
|---|---|
| The Tailwind script line and the `tailwind-config` block. Tailwind classes on every element (`flex`, `px-space-md`, `bg-surface-container-low`, `text-label-status` and so on). | Delete them all. Write new HTML with the classes of this note. |
| The `<link>` lines that load two web fonts and an icon font. | Delete them. The style sheet uses the computer's own font. |
| Icons written as a word inside `<span class="material-symbols-outlined">`. | Delete them. Where Section 4.26 allows an icon, use `icon(name)`. |
| The logo picture that loads from an outside web address. | Delete it. The frame shows the system name as text. |
| The style rule that hides all scrollbars. | Delete it. Scrollbars stay visible. |
| `darkMode` in the Tailwind settings. | There is no dark mode. |
| `onclick="..."` attributes and the long script at the end of the page (pretend timers, pretend data changes, pretend passwords). | Delete them. The only script is the one of Section 7. |
| Moving parts: pulsing dots, sliding panels, `transition-all`. | Delete them. Section 2.8 lists the only motion. |
| The browser's own question box (`confirm`) and message box (`alert`). | Use a confirm window (Section 4.14) and a message box (Section 4.15). |

Keep: `<!DOCTYPE html>`, `<html lang="en">`, the `charset` line and the `viewport` line.

### 6.2 Content: remove these from every draft page

- The persona names and their job titles, the round initials, and e-mail lines under names. A person is shown by the username only.
- The brand name and the logo. The system is called "LMS" and the role.
- Every label about servers, clusters, sync, versions, certificates, encryption or security promises. The project makes no such claims.
- The tabs and menu links that lead to the screens of other roles. A role's menu holds only its own pages.
- Menu links to pages that do not exist.
- The bell button and every other button that shows only an icon.
- The big header block with a long title, a slogan and labels. A page has a plain title and at most one count line.
- A describing sentence under every section title, and the icon tile next to it.
- Buttons that copy an ID. Text can be selected and copied by the reader.
- Demonstration controls such as "Simulate next status". If the role needs them for testing, they go onto a separate page "Test tools" as tool cards (a card with a title, one sentence, its fields and one button); otherwise remove them.
- Banners that explain the draft itself ("prototype", "for grading").

### 6.3 Colours

The draft names its colours in the `tailwind-config` block. This table says what each one becomes.

| Draft name | Draft value | Used in the draft for | New |
|---|---|---|---|
| `surface`, `background` | `#f8f9ff` | Page background | `--colour-page` |
| `surface-container-lowest` | `#ffffff` | Cards | `--colour-surface` (the card part) |
| `surface-container-low` | `#eff4ff` | Side menu, table background | The side menu colours; tables are white |
| `surface-container` | `#e5eeff` | Table header, small labels | `--colour-sunken` (already in the table part) |
| `surface-container-high` | `#dce9ff` | Row or button under the mouse | Done by the style sheet |
| `surface-container-highest`, `surface-variant` | `#d3e4fe` | Background of blue badges | A status family (Section 2.4) |
| `primary-container` | `#131b2e` | Dark top bar | The light top bar |
| `primary` | `#000000` | Main buttons | `shared-button-primary` (orange) |
| `secondary` | `#0051d5` | Links, IDs, blue text, blue badges | Links are orange and underlined. IDs are normal bold text. |
| `secondary-container` | `#316bf3` | Current tab and current menu entry | The current entry and the current tab of the style sheet |
| `on-surface` | `#0b1c30` | Text | `--colour-text` |
| `on-surface-variant` | `#45464d` | Second-level text | `shared-muted`, `shared-cell-sub` |
| `outline`, `outline-variant` | `#76777d`, `#c6c6cd` | Lines | Done by the style sheet |
| `error`, `error-container`, `on-error-container` | `#ba1a1a`, `#ffdad6`, `#93000a` | Errors and failed states | The Problem family for badges and messages; `shared-button-danger` for actions |

You never write a colour yourself. You choose the part and, for a badge or a flag, the family.

### 6.4 Text

| In the draft | New |
|---|---|
| Page title, 30 px bold, with a long name | `h1.shared-page-title`, a plain name |
| Section title, 24 px | `h2.shared-card-title` or the page title of the new page |
| Button text, 16 px, with an icon in front | The button part; an icon only where Section 4.26 allows it |
| Status label, 11 px, bold, capital letters | A status badge with plain words |
| Column title, 11 px, capital letters, wide spacing | A column title in sentence writing |
| Code font (`font-label-code`) on IDs, times, SKUs and labels | Normal font. Only raw values use `shared-fixed-text`. |
| Labels of 10 px | No text is smaller than 12 px. |
| "$80.00" | "HKD 80.00" |
| A time with a time zone label | "2026-11-12 09:14", no label |
| "Courier", "Picker" and other job words | The role names of Section 2.5 |

### 6.5 Shapes and spacing

| In the draft | New |
|---|---|
| Corners of 2 to 4 px on buttons and inputs, 8 px on cards | Done by the style sheet: 9 px and 14 px |
| Sections with 32 px padding and 32 px between them | Cards with 16 px, 16 px between blocks |
| A grid of 12 columns | The page is one column. Only `shared-grid-two` and `shared-grid-three` put blocks next to each other. |
| A drawer that slides in from the right | A detail page, or a window |

### 6.6 Things on the page

| In the draft | New part |
|---|---|
| The dark top header with logo, role tabs, bell and person | Top bar (Section 3.4) and side menu (Section 3.3) |
| The light side menu with links to other roles and a status footer | Side menu with the role's own pages |
| Tabs that switch between sections of the long page | Entries of the side menu, one page per section |
| Tabs that switch between two views of the same data | Tabs (Section 4.13) |
| Cards with a big number, an icon tile and a coloured bar | Number box (Section 4.16); the bar becomes lines with counts |
| A table with long column titles and buttons that run out of the card | List table or work table (Sections 4.7 and 4.8) |
| A list of cards, one per order or case | Work table (it becomes cards by itself in a small window) |
| Coloured status chips | Status badge (Section 4.10) |
| Small coloured labels next to a value ("LOW", "LOCKED BY ...") | Flag (Section 4.11), or plain text if it is no state |
| Rows of buttons that show only an icon | Actions menu (Section 4.12) in a list table; small buttons with words in a work table |
| A switch in a table row | A line in the Actions menu and a confirm window |
| A number box or a "Save" button inside a table row | A form window, opened from the Actions menu or from a button |
| A form inside the page | Fields (Section 4.4) in a card or in a form window |
| A password field with an eye icon, a strength bar or a list of password rules | Password field (Section 4.27): a "Show" button with words, and under a new password the strength scale and the rule list |
| A pop-up (modal) | Window (Section 4.14) |
| A small message that slides in (toast) | Message box (Section 4.15) |
| A coloured banner or alert inside the page | Inline message (if it reports something) or note (if it explains something) |
| A row of steps or a vertical tracking timeline | Progress steps (Section 4.21) |
| A list of past events | Timeline (Section 4.18) |
| A box with prices and a total | Summary (Section 4.22) |
| A list of notifications | Notice list (Section 4.23) |
| A bar for typing or scanning a code | Scan box (Section 4.24) |
| A signature pad or a photo area | Capture box (Section 4.24) |
| A progress bar | Progress bar with its words (Section 4.24) |
| A few named values about one thing | Fact list (Section 4.17) |
| A sentence that explains a rule of the page | Note (Section 4.15) |

---

## 7. The script

The sample pages are static. One small script file, `shared/shared-sample.js`, makes the parts react. Every page loads it with `<script src="..." defer></script>` in the `head`. The script does exactly the things in the table and nothing else. It never changes a table row, a badge or a number.

The script finds its elements through `data-` attributes. Write these attributes in the HTML exactly as named.

| No. | Behaviour | How it is marked in the HTML | What the script does |
|---|---|---|---|
| B1 | Open a window | Any button or menu line with `data-open-dialog="ID"`, where ID is the `id` of a `dialog` | First closes every open Actions menu, removes the current corner message boxes from `#sharedShowMessagePanel` and cancels their pending fade and removal timers. Puts every field of that window back to the value and the tick state that are written in the HTML, empties every message place inside it (Section 4.15), and applies B4. Records a fresh opening for B8. Then opens the window with `showModal()`. |
| B2 | Close a window | A button with `data-close-dialog` inside the `dialog` | Calls `close()` on that window. The Escape key closes a window by itself. A click on the dark layer does nothing. Closing by any route ends that opening for B8. |
| B3 | Confirm | A button with `data-confirm` inside the `dialog` | Closes the window. Then, if the button has `data-next-dialog="ID"`, opens that window (as B1). Otherwise, if it has `data-message="text"`, shows that text as a message box (B5) of the kind in `data-message-kind` (`success`, `error` or `information`; `success` if the attribute is missing). |
| B4 | Needed input | A text box or input with `data-reason`, and a tick box with `data-required-tick`, inside the `dialog` | The `data-confirm` button of the window is switched off (`disabled`) while any `data-reason` field holds fewer than 5 characters (spaces at both ends are not counted) or any `data-required-tick` box is not ticked. Check again after every typed character and every tick. |
| B5 | Message box | A button outside a window with `data-message="text"` (and `data-message-kind`) shows the message when pressed | If a modal window is open, skips this corner message. Otherwise builds the message box of Section 4.15 inside `#sharedShowMessagePanel`, with the icon of its kind. After 4000 ms it adds the class `shared-message-box-leaving`; 200 ms later it removes the box. A message box is never shown while a window is open (Section 4.15). |
| B6 | Actions menu | The tree of Section 4.12 (`popover` and `popovertarget`) | The browser opens and closes the menu. When a menu opens (its `toggle` event with the new state `open`), the script places it: `top` = bottom edge of its button + 4 px; `left` = right edge of the button minus the width of the menu, but at least 8 px. If the menu would end lower than 8 px above the bottom of the window, `top` = top edge of the button minus the height of the menu minus 4 px. If that `top` is less than 8 px, `top` = 8 px; the style sheet keeps the menu at most as high as the window minus 16 px, and lines that do not fit scroll inside it. When the page scrolls or the window changes its size, every open menu is closed. Scrolling inside the open menu's own list does not close it. |
| B7 | Tabs | The tree of Section 4.13 | When the page has loaded, for every tab list: measure the current tab, write its left offset and its width (in px) into the two CSS values `--shared-tab-bar-left` and `--shared-tab-bar-width` on the tab list, and then add the class `shared-tab-list-moving` to the tab list. When a tab is pressed: give it the class `shared-tab-active` and `aria-selected="true"`, take both away from the other tabs (`aria-selected="false"`), show its panel (remove `hidden`), hide the other panels (set `hidden`), and write the two CSS values again. Write them again when the window changes its size. When a tab has the keyboard and Arrow Right or Arrow Left is pressed, the next or the previous tab of the same tab list gets the keyboard and is shown as if it was pressed; after the last tab comes the first, and before the first comes the last. Home does the same with the first tab, and End with the last. Only the selected tab is reached with the Tab key: when the page has loaded and whenever a tab is chosen, the script sets `tabindex="0"` on the selected tab and `tabindex="-1"` on the others. |
| B8 | Copy | A button with three attributes: `data-copy="ID"` (the `id` of the element whose text is copied), `data-copy-place="ID"` (the `id` of a message place, Section 4.15) and `data-copy-failed="text"` (the words to show if copying fails) | Before calling `navigator.clipboard.writeText(text)`, records the current dialog opening and marks this as the newest Copy attempt for it. Waits for the result when the API is available. Updates feedback only if that same opening is still open and this is its newest Copy attempt. Otherwise the result is ignored for feedback. This check cannot cancel a clipboard write that was already started. For a current result, empties the message place and builds one inline message in it (Section 4.15). If the text was copied: kind success, `icon(check)`, the text "Copied." If the browser refused, or if it has no `navigator.clipboard`: kind error, `icon(alert)`, the words of `data-copy-failed`. It never shows a message box, and it never says "Copied." when the text was not copied. |
| B9 | Print a label | A button with `data-print-label` | Adds the class `shared-print-only-label` to the `html` element, calls `window.print()`, and removes the class when printing ends (the `afterprint` event). |
| B10 | Menu of a small window | A button with `data-open-menu`, a button with `data-close-menu` (only in an adaptive frame, Section 3.6) | Adds the class `shared-page-frame-menu-open` to the frame when "Menu" is pressed. Removes it when "Close menu" or an entry of the side menu is pressed. |
| B11 | Wide lists | The scroll box of a list table with `role="region"` (Section 4.7) | Watches every such box with a `ResizeObserver`, also one inside a window or a hidden tab panel. While the table is wider than its box: adds the class `shared-table-scroll-overflow`, sets `tabindex="0"`, and writes the width of the first column title (in px) into the CSS value `--shared-table-pinned-width` on the box. When the table fits: removes all three. |
| B12 | Lists opened from an address | A control with `data-sample-filter="NAME"` inside an element with `data-sample-list`; rows with `data-sample-values="VALUE ..."` (an empty value list for a row that belongs to no filter); optional elements with `data-sample-count="... {n} ..."` and `data-sample-pager="..."` | Sample only: the stand-in for the real filters of a list. When the page's address holds `NAME=VALUE` and at least one row of that list holds VALUE: the control takes the value (a checkbox is ticked for `true`), only the rows that hold VALUE stay visible (the others get `hidden`), every `data-sample-count` element shows its text with `{n}` replaced by the number of visible rows, and every `data-sample-pager` element shows its text. When no row holds the value, the list stays as it is. |
| B13 | Decided rows | A confirm button with `data-decided-row="ID"`, `data-decided-line="..."` and either `data-decided-badge="FAMILY:WORDS"` or `data-decided-main="..."`; in the row with that `id`, one cell with `data-decided-cell` | After the window closes (B1): the decided cell shows `span.shared-cell-main` with the badge of that family and words, or with the text, and `span.shared-cell-sub` with the line, in which every date and time stands in `time.shared-nowrap`; the actions cell of the row shows `span.shared-muted` "-"; the keyboard goes to the first link of the row. The message of B5 appears as before. |
| B14 | Password feedback and the press | A `form` with `data-password-form="ADDRESS"` holding the password fields of Section 4.27: the three inputs with `data-password="current"`, `data-password="new"` and `data-password="confirm"`; the rule lines with `data-password-rule="length"`, `"capital"`, `"small"`, `"digit"` and `"match"`; the first input with `data-password-empty="text"`; the main button with `data-password-busy="text"` | After every typed character in one of the three inputs: ticks the rule lines and the match line, sets the strength scale and replaces its word when the level changes, and shows the errors of Section 4.27. Typing in a field removes the answer that stood under it. A press of the main button first removes every earlier answer, under the fields and in the message place of the form. When a field is then empty: sends nothing, shows the page's own lines (the empty first field says its `data-password-empty` text) and moves the keyboard to the first field with `aria-invalid="true"`. Sample only, when all three fields hold something: answers as the server would, in the server's order, and moves the keyboard to that field. Under "New password" the first broken rule: "Use at least 8 characters.", "Use at most 72 characters.", "Use fewer characters. The password can use at most 72 bytes.", "Use at least one capital letter from A to Z.", "Use at least one small letter from a to z." or "Use at least one number from 0 to 9."; then under the repeat "The two new passwords are not the same."; then under "New password" "The new password must be different from the current one." The current password is taken as right. With no problem it switches the main button off, writes its `data-password-busy` text and after 600 ms opens ADDRESS. |
| B15 | Show and Hide | A `button.shared-password-show` with `data-show-password` inside `div.shared-password-input` | Switches the input of the same row between `type="password"` and `type="text"`, writes "Hide" or "Show" on the button, and changes the first word of its `aria-label` to match. |
| B16 | A notice from the address | A message place with `data-sample-notice="NAME"` and one attribute `data-sample-notice-WORD="text"` per notice, where WORD is the notice word in small letters with hyphens (`PASSWORD_CHANGED` becomes `password-changed`) | Sample only: when the page's address holds `NAME=WORD` and the place has that WORD, puts an inline message of the kind information with that text into the place, in place of the message written in the HTML. |

Function names: `sharedOpenDialog`, `sharedCloseDialog`, `sharedCheckDialogInput`, `sharedShowMessage`, `sharedPlaceActionMenu`, `sharedShowTab`, `sharedCopyText`, `sharedPrintLabel`, `sharedWatchTableScroll`, `sharedApplySampleFilter`, `sharedShowDecidedRow`, `sharedWriteTextWithTimes`, `sharedCheckPasswordRuleList`, `sharedMeasurePasswordStrength`, `sharedShowPasswordRuleState`, `sharedShowFieldError`, `sharedShowInlineMessage`, `sharedFindPasswordFields`, `sharedShowPasswordFeedback`, `sharedAnswerSamplePasswordChange`, `sharedSubmitPasswordForm`, `sharedTogglePasswordVisibility`, `sharedShowSampleNotice`. Every function starts with a comment that says what it does, what input it needs and what output it gives. The file starts with a comment that says what the file is for.

A control that is not named in this section or in the page descriptions does nothing when it is pressed. Do not add a reaction to it.

---

## 8. A worked example, and hints for the other draft screens

### 8.1 Worked example: the Admin screen

The Admin draft (`system_administration_audit_center_alex_1`) is one long page. This is how it was changed. Use it as the pattern for your own screen.

**Step 1 and 2: jobs become pages.**

| Part of the draft | New page | What happened to it |
|---|---|---|
| Dark top header: logo, brand name, demo label, tabs to other roles, bell, person's name with job title | Top bar and side menu | Logo, brand name, tabs, bell and job title were removed. The demo label became the flag "Test mode". The person's name became the username. |
| Light side menu: version label, links to other roles, links to pages that do not exist, status footer | Side menu | Every entry was replaced by a page of the Admin. Label and footer were removed. |
| Header block: labels, big title, sentence, "Session Timeout", "Policy: Auto-Lock", "Lock Console" | Top bar | Only the session time stayed, as "Session ends in 12:45". The rest was removed. |
| Card "Active Pipeline" (count, coloured bar, legend) | Dashboard, number box "Active orders" | The bar and the legend became one line per order status, with its badge and its count. |
| Card "Threshold Alerts" | Dashboard, number box "Low stock" | It shows only products that are low. |
| Card "Unresolved Failures" with a pulsing dot | Dashboard, number box "Open cases" | It became the counts of all open cases, each a link. |
| Card about privacy with promises about encryption | Removed | The project makes no such claims. |
| Section "Delivery Exceptions & Resolution Desk" | Page "Delivery cases" | A work table. The line of code-like text under the table was removed. |
| Section "Product Catalogue & Inventory Thresholds" | Page "Products" | A list table. The number box, the switch and "Save" in each row were removed; editing happens in a form window. |
| Section "User Management & Security Access" with three policy sentences | Page "Users" | A list table. The policy sentences, the round initials and the e-mail lines were removed. |
| Section "System Logs & Audit Trails" with three tabs | Page "Logs" | The three tabs stayed, because they are three views of the same kind of data. |
| Four pop-ups | Windows | Each got a title that names the thing, full sentences, and a confirm button that names the action. |

The side menu that came out of it:

```
LMS Admin
  Dashboard
Work to do
  Warehouse cases   3
  Delivery cases    3
  Refunds           2
Manage
  Users
  Products
Look up
  Orders
  Logs
```

**Step 4 to 7: one section in detail.** The users section of the draft, and the Users page that replaced it:

| | Draft | New |
|---|---|---|
| Title | "User Management & Security Access", an icon tile, a describing sentence, a label "Strict Rule: Zero Stored Hashes Visible" | Page title "Users", count line "42 accounts" |
| Above the table | Three policy sentences with icons | A filter bar: search box, two selects, "Search", "Clear" |
| Columns | Identity / User, Role Authorization, Account Status, Last Login, Failed Retries, Security Operations | Username, Role, Status, Last login, Actions |
| First cell | Round initials, a full name, an e-mail line | The username, bold |
| Status | A blue label "ACTIVE"; a red label "LOCKED (5 FAILS)" | A badge "Active" (Done family); a badge "Locked, 12 min left" (Waiting family) with the note "5 of 5 tries" |
| Actions | Four buttons with only an icon | One "Actions" button; its menu lists View, Change role, Reset password, Unlock and, after a divider in red, Ban and Deactivate |
| Ban | The browser's own question box | A confirm window "Ban wh_picker_02?" with the field "Reason" and the red button "Ban user" |
| Main action | None | The main button "Create user" in the page header |

The row of the new page as a tree:

```
tr
  th [scope="row"]                                "wh_picker_01"
  td                                              "Warehouse Worker"
  td
    span.shared-cell-line
      span.shared-status-badge.shared-status-badge-waiting   "Locked, 12 min left"
      span.shared-cell-note                                  "5 of 5 tries"
  td.shared-table-time                            "2026-11-11 17:02"
  td.shared-table-actions
    span.shared-menu                              (the Actions menu of Section 4.12)
```

### 8.2 Hints for the other draft screens

These are hints, not decisions. The person responsible for a role decides its pages and what they show. The hints only say which part fits which thing in the draft.

**Client** (`client_portal_order_creation_payment_tracking_1`)

| In the draft | Hint |
|---|---|
| The tabs "create order", "my orders and tracking", "notification centre", "account settings" | Four pages in the side menu. The system name is "LMS Client". |
| Product selection with one row per product and a quantity | A form page. Card "Products": a list table whose quantity cell holds `input.shared-input.shared-input-short [type="number"]`. A warning about stock is an inline message under the table. |
| Delivery address form | A second card on the same page with fields. |
| Order summary with prices and the button that starts the payment | A card to the right of the form (`div.shared-grid-two`) with a summary, a note about the payment, and the one main button of the page. |
| The list of the client's orders, each with three coloured labels | A work page. A work table with the columns Order, Status (the order status as a badge; payment and refund as second lines with their own badges) and Actions. |
| The tracking steps of one order | A detail page "Order TEST-0001", opened from the list, with progress steps (Section 4.21) and the facts of the order. |
| The button that cancels an order | A dangerous button on the detail page that opens a confirm window. Its closing button is called "Close". |
| The box that explains when an order can be cancelled | A note. |
| The notification centre | A page with one card that holds a notice list. |
| The pop-up that pretends to pay | A form window, or a form page, with a summary of the amount and inline messages for success and failure. A sentence that says "this is a test payment" is a note. |
| The control that moves an order to its next status | A demonstration control: remove it, or move it to a page "Test tools". |

**Warehouse Worker** (`warehouse_operations_picking_restock_mei`)

| In the draft | Hint |
|---|---|
| The tabs "picking queue" and "stock count" | Two pages in the side menu. The system name is "LMS Warehouse". |
| The banner with numbers at the top | Number boxes on a start page, each a link to its list, or leave them out. |
| The warning about low stock | The flag "Low stock" on the rows concerned, and at most one note. |
| The table of orders to pick | A work page. A work table with the columns Order, Items, Status and Actions ("Start picking", "Continue picking"). |
| The large pop-up with one picking task | A detail page "Picking task of order TEST-0001": a fact list, a progress bar with its words, a scan box, and a work table of the items with the picked and the ordered quantity. The button that finishes the task is the main button; reporting a problem is another button. |
| The form for a stock count | A form page with one card of fields and one main button. |
| The card with reference numbers of the stock | A list table. |
| The pop-up that confirms packing | A confirm window. |

**Delivery Worker** (`delivery_field_app_pickup_proof_ken`)

| In the draft | Hint |
|---|---|
| The whole screen | A Delivery Worker works on a phone, so the frame is adaptive (Section 3.6) and every list is a work table. The system name is "LMS Delivery". |
| The header with the person's name and status lights | Removed. The username stands in the frame. |
| Section 1, the pickup queue with a bar for testing a code | A work page "Pickup": a card with a scan box, then a work table of the packages with the buttons that record the result. |
| Section 2, the worker's deliveries | A work page "My deliveries": a work table with the columns Order, Delivery details, State and Actions. This page belongs to the person who delivers, so it may show the delivery details. |
| Section 3, the outcome with the tabs "delivered" and "failed" | A form page "Record outcome", opened from a delivery. Tabs for the two outcomes. "Delivered": two capture boxes (signature, photo). "Failed": a select with the reason and a text box. One main button, which opens a confirm window. |
| The rule text about proof | A note. |
| The history of attempts | A list table, or a timeline, on the detail page of the delivery. |

---

## 9. Checks before you hand over

| ID | What to do | Expected result |
|---|---|---|
| G-01 | Search every HTML file and the script file for `http://`, `https://`, `@import` and `url(`. | No hit. |
| G-02 | Compare `shared/shared-common.css` with Appendix A. | The same text, rule for rule. |
| G-03 | Search the HTML files for `<style` and `style=`. | No hit. |
| G-04 | List every class name used in the HTML files and look each one up in the style sheet. | Every class exists in the style sheet. No Tailwind class is left. |
| G-05 | Switch the network off and open three pages. | They look the same as with the network. |
| G-06 | Go through the list of Section 6.2 and search your pages for each thing. | None of them is left. |
| G-07 | Read all sample data. | Invented and harmless. No real person, no persona name of the draft, no place name, no time zone label. |
| G-08 | Make the browser window 1280 px wide and 720 px high. Open every page. | The page itself never scrolls sideways. No list table scrolls sideways. No text is cut off, overlaps another text or ends in three dots. |
| G-09 | Only for an adaptive frame: make the window 390 px wide and open every page. Press "Menu" and "Close menu". If a page shows corner messages, show one. | The page does not scroll sideways. Work tables are shown as cards with a title above every value. The menu opens and closes. On an adaptive page the message area is 60 px from the top and 8 px from each side. |
| G-10 | Look at every button, also in windows and menus. | Every button has words. No button shows only an icon. |
| G-11 | Count the filled orange buttons on every page while no window is open, and in every window. | At most one per page and at most one per window. |
| G-12 | Compare every badge and every flag with Section 2.4 and Section 2.11. | The right family, plain words, a dot on badges and none on flags. The same word has the same family on every page. |
| G-13 | Open all pages of the role one after another. | The same frame on every page, and the right entry is the current one. |
| G-14 | Open every window and every Actions menu. Then make the window 320 px high and open an Actions menu again. Switch on "reduce motion" and open a window. | A window opens in the middle with the dark layer behind it; Escape closes it; a click on the dark layer does not. A menu is fully visible and closes with Escape, with a click somewhere else and when the page scrolls. In the short window the menu stays inside the window and every line can be reached. With reduce motion on, the window and its dark layer appear at once. |
| G-15 | In every window with a needed input, look at the confirm button before and after the input is given. | It is switched off until every needed input is given. |
| G-16 | Press the Tab key through one page and one window, starting with the first Tab on a fresh page with the frame. In a short window, press Shift+Tab up through a long table. | The first Tab shows "Skip to main content", and Enter moves the keyboard to the work area. Every control that is not switched off is reached and shows the orange outline. A switched-off control is skipped. The lines of an Actions menu are reached only while that menu is open, and each Actions button is named after its row. Every button of a work-table row has a name with its record, and no two buttons share a name. The top bar never covers the control that has the keyboard. |
| G-17 | Put one of your pages next to a page of another role that follows this note (for example an Admin page). | Frame, buttons, tables, badges and windows look the same. |
| G-18 | If the page has tabs, click each tab. Focus a tab and try Arrow Right, Arrow Left, Home and End, including both directions across the ends. Check the tab and panel attributes. If the page has a progress bar, check its name. | Each tab has its own `id`. Its `aria-controls` and `data-tab` name its panel, and the panel's `aria-labelledby` names that tab. The selected tab shows its panel and the orange bar follows it. Arrow keys, Home and End also move focus to the selected tab. Arrows wrap at the ends. Only the selected tab is reached with the Tab key. Each progress bar gets its name from the visible progress text through `aria-labelledby`. |
| G-19 | For an action with specified feedback inside an open window, for example "Copy", use the action and check its feedback. If "Print label" is present, open the print view and return. A script that cannot reach the print view leaves that part to a person, who does it once by hand. Say in the report how each part was checked. | The specified feedback appears inside its window. "Print label" opens the browser print view. Neither kind of action adds a corner message. |
| G-20 | If a page has a list table, make the window 1024 px wide, scroll the list fully to the right and press Tab until the list has the keyboard. | A list that is wider than its card keeps its first column at the left behind a margin rule, is a named region, is reached by Tab and scrolls with the arrow keys. A list that fits is no Tab stop. |
| G-21 | If a page has a work table, open the window of every row button and confirm one decision. | Each window names the record of its row. After confirming, the row keeps its place with its new state, who decided and when; its buttons are gone, and the keyboard is on the record's link. |
| G-22 | At 1024 px and at 1280 px look at every date and time inside a line of text. | None is split over two lines; each stands in `time.shared-nowrap` with its `datetime`. |
| G-23 | If a page has a field for a new password, type into it a short password, then one that meets every rule, then one of 16 characters or more. | The rule lines are ticked as each rule is met and turn red only after a press, or at once when the password is too long. The strength word changes with the level, and the scale is never red. |
| G-24 | On a page with password fields press the main button with the fields empty, then press "Show" beside each field. Look at the message place of the page in its elements. | Each problem stands under its own field with a red edge and `aria-invalid="true"`, and the keyboard is on the first marked field. "Show" shows the characters and becomes "Hide". The message place is in the page before the first message and is never hidden. |

---

## Appendix A. The style sheet

Copy the text inside the block below unchanged into `shared/shared-common.css`. It has 2520 lines and 20 numbered sections. Do not add, remove, reorder or reformat anything. Every colour, size and movement of the design is in this file, so no page needs any style of its own.

```css
/* ==========================================================================
   LMS shared style sheet: shared-common.css (version 2.4, the ledger look in charcoal and orange)
   One style sheet for every page of the Logistics Management System.
   Copy this text unchanged. Do not add, remove or change a rule.

   Sections
     1. Named values (colours, fonts, corners, shadows, motion)
     2. Page basics
     3. Icons
     4. Page frame (side menu, top bar, work area)
     5. Page header
     6. Buttons and links
     7. Fields (also the password field with its rule list and strength scale)
     8. Cards, filter bar and layout helpers
     9. Tables and pager
    10. Status badges and flags
    11. Actions menu
    12. Tabs
    13. Windows
    14. Messages and notes
    15. Number box, fact list and timeline
    16. Pages without the frame (login and similar)
    17. More parts (steps, summary, notices, scan box, capture box, progress)
    18. Small windows (only for a frame marked as adaptive)
    19. Printing a label
    20. People who switched animations off
   ========================================================================== */

/* ===== SECTION 1: NAMED VALUES ===== */
/* Every colour is written here once. All other rules use the names. */
:root {
  color-scheme: light;

  /* 1.1 Page and surfaces */
  --colour-page: #f1f1ef;
  --colour-surface: #ffffff;
  --colour-sunken: #f7f7f5;
  --colour-hover: #e9e9e6;

  /* 1.2 Lines. The two ledger rules: one between rows, one under column
     titles and as the double rule under a total. */
  --colour-line: #dbdbd6;
  --colour-line-strong: #c4c4bd;
  --colour-control-line: #7a7a73;
  --colour-rule: #d9d9d3;
  --colour-rule-strong: #a3a39a;

  /* 1.3 Text */
  --colour-text: #1f1f1d;
  --colour-text-muted: #55554f;
  --colour-placeholder: #65655e;
  --colour-on-fill: #ffffff;

  /* 1.4 Side menu (the charcoal rail) and top bar */
  --colour-menu: #262624;
  --colour-menu-line: #383835;
  --colour-menu-text: #ecebe6;
  --colour-menu-label: #aeada4;
  --colour-menu-hover: #31312e;
  --colour-menu-active: #f1f1ef;
  --colour-menu-active-text: #1f1f1d;
  --colour-menu-name: #ffffff;
  --colour-menu-focus: #f7c39b;
  --colour-counter: #ecebe6;
  --colour-counter-text: #262624;
  --colour-top-bar: #f8f8f6;
  --colour-top-bar-line: #dbdbd6;

  /* 1.5 Things that can be pressed */
  --colour-primary: #a84200;
  --colour-primary-hover: #8a3600;
  --colour-link: #a84200;
  --colour-focus: #a84200;
  --colour-selection: #f6dccb;

  /* 1.6 Dangerous actions and errors */
  --colour-danger: #b42318;
  --colour-danger-hover: #981c13;
  --colour-danger-line: #cf7a70;
  --colour-danger-surface: #fdf1ef;

  /* 1.7 Controls that are switched off */
  --colour-disabled-surface: #e6e6e2;
  --colour-disabled-text: #6f6f68;

  /* 1.8 The six status families */
  --colour-status-waiting-surface: #fdf0d0;
  --colour-status-waiting-text: #7a4b00;
  --colour-status-warehouse-surface: #e2eefd;
  --colour-status-warehouse-text: #154c9c;
  --colour-status-road-surface: #ece6fb;
  --colour-status-road-text: #4b2fa8;
  --colour-status-done-surface: #ddf3e2;
  --colour-status-done-text: #17603a;
  --colour-status-problem-surface: #fce1dd;
  --colour-status-problem-text: #9a2418;
  --colour-status-closed-surface: #eeebe6;
  --colour-status-closed-text: #4f4a42;

  /* 1.9 The dark layer behind an open window */
  --colour-overlay: rgba(31, 31, 29, 0.45);

  /* 1.10 Fonts */
  --font-normal: Bahnschrift, "Segoe UI Variable Text", "Segoe UI", system-ui, -apple-system, "Helvetica Neue", Arial, sans-serif;
  --font-fixed: "Cascadia Mono", ui-monospace, Consolas, "Courier New", monospace;

  /* 1.11 Corners and shadows */
  --radius-control: 9px;
  --radius-card: 14px;
  --radius-popup: 10px;
  --radius-small: 6px;
  --radius-pill: 999px;
  /* Panels lie flat on the page, edged by a 1 px line. Only what floats
     above the page (a menu, a window) casts a shadow. */
  --shadow-card: none;
  --shadow-popup: 0 2px 6px rgba(31, 31, 29, 0.10), 0 12px 28px rgba(31, 31, 29, 0.16);
  --shadow-window: 0 8px 24px rgba(31, 31, 29, 0.18), 0 24px 56px rgba(31, 31, 29, 0.22);

  /* 1.12 Motion: how long each movement takes */
  --motion-colour: 120ms;
  --motion-menu: 120ms;
  --motion-tab: 150ms;
  --motion-window: 160ms;
  --motion-message: 200ms;
}

/* ===== SECTION 2: PAGE BASICS ===== */
*,
*::before,
*::after {
  box-sizing: border-box;
}

html {
  -webkit-text-size-adjust: 100%;
  text-size-adjust: 100%;
  scrollbar-color: #b4b4ac var(--colour-page);
}

body {
  margin: 0;
  font-family: var(--font-normal);
  font-size: 14px;
  line-height: 20px;
  color: var(--colour-text);
  background: var(--colour-page);
  /* Digits have one width everywhere, so columns of figures line up. */
  font-variant-numeric: tabular-nums;
}

/* The browser's own surfaces follow the palette too. */
::selection {
  background: var(--colour-selection);
  color: var(--colour-text);
}

input,
textarea {
  caret-color: var(--colour-primary);
}

h1,
h2,
h3,
p,
ul,
ol,
dl,
dd,
figure {
  margin: 0;
}

ul,
ol {
  padding: 0;
  list-style: none;
}

button,
input,
select,
textarea {
  margin: 0;
  font: inherit;
  color: inherit;
}

button {
  cursor: pointer;
}

a {
  color: var(--colour-link);
  text-decoration: underline;
  text-decoration-thickness: 1px;
  text-underline-offset: 2px;
}

a:hover {
  color: var(--colour-primary-hover);
  text-decoration-thickness: 2px;
}

:focus-visible {
  outline: 2px solid var(--colour-focus);
  outline-offset: 2px;
}

[hidden] {
  display: none !important;
}

.shared-visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  margin: -1px;
  padding: 0;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
  border: 0;
}

.shared-muted {
  color: var(--colour-text-muted);
}

.shared-nowrap {
  white-space: nowrap;
}

.shared-strong {
  font-weight: 600;
}

.shared-fixed-text {
  font-family: var(--font-fixed);
  font-size: 13px;
  overflow-wrap: anywhere;
}

/* ===== SECTION 3: ICONS ===== */
.shared-icon-sprite {
  position: absolute;
  width: 0;
  height: 0;
  overflow: hidden;
}

.shared-icon {
  flex: none;
  width: 18px;
  height: 18px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.6;
  stroke-linecap: round;
  stroke-linejoin: round;
}

/* ===== SECTION 4: PAGE FRAME ===== */
.shared-page-frame {
  display: grid;
  grid-template-columns: 212px minmax(0, 1fr);
  grid-template-rows: 52px minmax(0, 1fr);
  min-width: 1000px;
  min-height: 100vh;
}

.shared-page-frame-side-menu {
  grid-column: 1;
  grid-row: 1 / span 2;
  position: sticky;
  top: 0;
  align-self: start;
  display: flex;
  flex-direction: column;
  gap: 2px;
  height: 100vh;
  padding: 0 0 16px 10px;
  overflow-y: auto;
  background: var(--colour-menu);
  color: var(--colour-menu-text);
  scrollbar-color: #4f4f4b transparent;
  /* On the dark rail the keyboard outline is pale. */
  --colour-focus: var(--colour-menu-focus);
}

/* A link in the rail that is not an entry (the menu footer of a small window). */
.shared-page-frame-side-menu a:not(.shared-page-frame-menu-item) {
  color: var(--colour-menu-name);
}

.shared-page-frame-name {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  height: 52px;
  margin-right: 10px;
  padding: 0 10px;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.01em;
  color: var(--colour-menu-name);
}

.shared-page-frame-menu-label {
  flex: none;
  padding: 16px 10px 4px;
  font-size: 12px;
  line-height: 16px;
  font-weight: 600;
  color: var(--colour-menu-label);
}

.shared-page-frame-menu-item {
  position: relative;
  flex: none;
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 36px;
  margin-right: 10px;
  padding: 8px 10px;
  border-radius: var(--radius-control);
  color: var(--colour-menu-text);
  font-weight: 500;
  text-decoration: none;
  transition: background-color var(--motion-colour), color var(--motion-colour);
}

.shared-page-frame-menu-item:hover {
  background: var(--colour-menu-hover);
  color: var(--colour-menu-name);
}

.shared-page-frame-menu-text {
  flex: 1;
  min-width: 0;
}

/* The current entry is an index tab cut from the page: it has the colour
   of the work area and joins it at the right edge of the rail. */
.shared-page-frame-menu-item-active,
.shared-page-frame-menu-item-active:hover {
  margin-right: 0;
  padding-right: 20px;
  border-radius: var(--radius-control) 0 0 var(--radius-control);
  background: var(--colour-menu-active);
  color: var(--colour-menu-active-text);
  font-weight: 600;
}

.shared-page-frame-menu-item-active .shared-page-frame-menu-count {
  background: var(--colour-menu);
  color: var(--colour-menu-name);
}

.shared-page-frame-menu-count {
  flex: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: var(--radius-pill);
  background: var(--colour-counter);
  color: var(--colour-counter-text);
  font-size: 12px;
  line-height: 16px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

/* A menu entry whose page does not exist yet. It cannot be pressed. */
.shared-page-frame-menu-item-disabled,
.shared-page-frame-menu-item-disabled:hover {
  background: transparent;
  color: var(--colour-menu-label);
  font-weight: 400;
  cursor: default;
}

.shared-page-frame-menu-stage {
  flex: none;
  font-size: 12px;
  line-height: 16px;
  color: var(--colour-menu-label);
}

.shared-page-frame-top-bar {
  grid-column: 2;
  grid-row: 1;
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  gap: 16px;
  height: 52px;
  padding: 0 24px;
  background: var(--colour-top-bar);
  border-bottom: 1px solid var(--colour-top-bar-line);
}

.shared-page-frame-spacer {
  flex: 1;
}

.shared-page-frame-countdown {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--colour-text-muted);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.shared-page-frame-user {
  font-weight: 600;
  white-space: nowrap;
}

/* Menu entries drawn on the parts page stand on a strip of the rail colour. */
.shared-menu-sample {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 10px 0 10px 10px;
  border-radius: var(--radius-control);
  background: var(--colour-menu);
  color: var(--colour-menu-text);
  --colour-focus: var(--colour-menu-focus);
}

/* The three parts below are used only by an adaptive frame in a small window. */
.shared-page-frame .shared-page-frame-menu-button,
.shared-page-frame .shared-page-frame-menu-close,
.shared-page-frame .shared-page-frame-menu-footer {
  display: none;
}

.shared-page-frame-main {
  grid-column: 2;
  grid-row: 2;
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
  max-width: 1328px;
  padding: 24px;
}

/* The work area is where the skip link sends the keyboard. It is not a
   control, so it shows no outline when it has the keyboard. */
.shared-page-frame-main:focus {
  outline: none;
}

/* The link "Skip to main content" is the first thing that the Tab key
   reaches on a page with the frame. It cannot be seen until it has the
   keyboard. Then it appears at the top left, above the top bar. */
.shared-skip-link {
  position: fixed;
  top: 8px;
  left: 8px;
  z-index: 50;
  padding: 6px 12px;
  border: 1px solid var(--colour-line-strong);
  border-radius: var(--radius-control);
  background: var(--colour-surface);
  box-shadow: var(--shadow-popup);
  font-weight: 600;
}

.shared-skip-link:not(:focus) {
  width: 1px;
  height: 1px;
  margin: -1px;
  padding: 0;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
  border: 0;
}

/* The top bar stays at the top while the page scrolls. When the keyboard
   moves to a control that is out of sight, the page stops 64 px from the
   top (the 52 px top bar and 12 px of space), so the top bar never covers
   that control. */
html {
  scroll-padding-top: 64px;
}

/* ===== SECTION 5: PAGE HEADER ===== */
.shared-page-header {
  display: flex;
  align-items: flex-end;
  gap: 12px;
}

.shared-page-header-text {
  min-width: 0;
}

.shared-page-back {
  display: inline-block;
  margin-bottom: 4px;
}

.shared-page-title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 20px;
  line-height: 28px;
  font-weight: 600;
  letter-spacing: -0.01em;
}

.shared-page-subtitle {
  margin-top: 2px;
  color: var(--colour-text-muted);
}

.shared-page-header-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 8px;
  margin-left: auto;
}

.shared-section-title {
  margin-top: 8px;
  font-size: 15px;
  line-height: 22px;
  font-weight: 600;
}

/* ===== SECTION 6: BUTTONS AND LINKS ===== */
.shared-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 34px;
  padding: 0 12px;
  border: 1px solid var(--colour-control-line);
  border-radius: var(--radius-control);
  background: var(--colour-surface);
  color: var(--colour-text);
  font-size: 14px;
  line-height: 20px;
  font-weight: 600;
  white-space: nowrap;
  text-decoration: none;
  transition: background-color var(--motion-colour), border-color var(--motion-colour), color var(--motion-colour);
}

.shared-button:hover {
  background: var(--colour-hover);
  color: var(--colour-text);
}

/* A button answers a press: it sits 1 px lower while it is held. */
.shared-button:active:not(:disabled) {
  transform: translateY(1px);
}

.shared-button .shared-icon {
  width: 16px;
  height: 16px;
}

.shared-button-primary {
  border-color: var(--colour-primary);
  background: var(--colour-primary);
  color: var(--colour-on-fill);
}

.shared-button-primary:hover {
  border-color: var(--colour-primary-hover);
  background: var(--colour-primary-hover);
  color: var(--colour-on-fill);
}

.shared-button-danger {
  border-color: var(--colour-danger-line);
  color: var(--colour-danger);
}

.shared-button-danger:hover {
  background: var(--colour-danger-surface);
  color: var(--colour-danger);
}

.shared-button-danger-filled {
  border-color: var(--colour-danger);
  background: var(--colour-danger);
  color: var(--colour-on-fill);
}

.shared-button-danger-filled:hover {
  border-color: var(--colour-danger-hover);
  background: var(--colour-danger-hover);
  color: var(--colour-on-fill);
}

.shared-button-quiet {
  height: auto;
  padding: 0 2px;
  border-color: transparent;
  background: transparent;
  color: var(--colour-link);
  text-decoration: underline;
  text-underline-offset: 2px;
}

.shared-button-quiet:hover {
  background: transparent;
  color: var(--colour-primary-hover);
}

.shared-button-small {
  height: 28px;
  padding: 0 10px;
  font-size: 13px;
}

.shared-button-wide {
  width: 100%;
}

.shared-button:disabled,
.shared-button:disabled:hover {
  border-color: var(--colour-line);
  background: var(--colour-disabled-surface);
  color: var(--colour-disabled-text);
  cursor: not-allowed;
}

.shared-button-quiet:disabled,
.shared-button-quiet:disabled:hover {
  border-color: transparent;
  background: transparent;
  text-decoration: none;
}

/* ===== SECTION 7: FIELDS ===== */
.shared-input,
.shared-textarea {
  width: 100%;
  height: 34px;
  padding: 0 10px;
  border: 1px solid var(--colour-control-line);
  border-radius: var(--radius-control);
  background: var(--colour-surface);
  color: var(--colour-text);
  transition: border-color var(--motion-colour);
}

.shared-textarea {
  display: block;
  height: auto;
  min-height: 72px;
  padding: 8px 10px;
  line-height: 20px;
  resize: vertical;
}

.shared-input::placeholder,
.shared-textarea::placeholder {
  color: var(--colour-placeholder);
  opacity: 1;
}

.shared-input-short {
  width: 96px;
}

.shared-input-medium {
  width: 200px;
}

.shared-input-search {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  width: 250px;
  height: 34px;
  padding: 0 10px;
  border: 1px solid var(--colour-control-line);
  border-radius: var(--radius-control);
  background: var(--colour-surface);
  color: var(--colour-text-muted);
}

.shared-input-search input {
  flex: 1;
  min-width: 0;
  height: 100%;
  padding: 0;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--colour-text);
}

.shared-input-search input::placeholder {
  color: var(--colour-placeholder);
  opacity: 1;
}

.shared-input-search:focus-within,
.shared-select:focus-within {
  outline: 2px solid var(--colour-focus);
  outline-offset: 2px;
}

.shared-select {
  position: relative;
  display: inline-flex;
  align-items: center;
  height: 34px;
  border: 1px solid var(--colour-control-line);
  border-radius: var(--radius-control);
  background: var(--colour-surface);
}

.shared-select select {
  height: 100%;
  padding: 0 34px 0 10px;
  border: 0;
  border-radius: inherit;
  outline: 0;
  background: transparent;
  color: var(--colour-text);
  cursor: pointer;
  appearance: none;
  -webkit-appearance: none;
}

.shared-select .shared-icon {
  position: absolute;
  right: 10px;
  width: 16px;
  height: 16px;
  color: var(--colour-text-muted);
  pointer-events: none;
}

.shared-select-wide {
  display: flex;
  width: 100%;
}

.shared-select-wide select {
  width: 100%;
}

.shared-checkbox {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 34px;
  cursor: pointer;
}

.shared-checkbox input {
  flex: none;
  width: 16px;
  height: 16px;
  margin: 0;
  accent-color: var(--colour-primary);
}

.shared-file-input {
  width: 100%;
  font-size: 14px;
}

.shared-file-input::file-selector-button {
  height: 34px;
  margin-right: 12px;
  padding: 0 12px;
  border: 1px solid var(--colour-control-line);
  border-radius: var(--radius-control);
  background: var(--colour-surface);
  color: var(--colour-text);
  font: inherit;
  font-weight: 600;
  cursor: pointer;
}

.shared-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.shared-field + .shared-field {
  margin-top: 16px;
}

.shared-field-label {
  font-size: 13px;
  line-height: 18px;
  font-weight: 600;
}

.shared-field-optional {
  font-weight: 400;
  color: var(--colour-text-muted);
}

.shared-field-help {
  font-size: 12.5px;
  line-height: 18px;
  color: var(--colour-text-muted);
}

.shared-field-error {
  font-size: 12.5px;
  line-height: 18px;
  color: var(--colour-danger);
}

.shared-input-invalid,
.shared-select-invalid {
  border-color: var(--colour-danger);
}

.shared-input:disabled,
.shared-textarea:disabled {
  border-color: var(--colour-line);
  background: var(--colour-disabled-surface);
  color: var(--colour-disabled-text);
  cursor: not-allowed;
}

/* A password field: the input and its "Show" button in one row. A button may
   not stand inside a label, so a password field is a div with a label[for]. */
.shared-password-input {
  display: flex;
  gap: 8px;
}

.shared-password-input > .shared-input {
  flex: 1;
  min-width: 0;
}

/* "Show" and "Hide" keep one width, so the input never jumps. */
.shared-password-show {
  flex: none;
  min-width: 64px;
}

/* The strength scale under a new password: four steps, then one line with the
   advice on the left and the strength word on the right. It only advises, so
   it is never red: amber (Waiting) for too weak and fair, green (Done) for
   good and strong. */
.shared-strength {
  margin-top: 8px;
}

.shared-strength-scale {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 4px;
}

.shared-strength-scale > span {
  height: 6px;
  border-radius: var(--radius-pill);
  background: var(--colour-rule);
  transition: background-color var(--motion-colour);
}

.shared-strength-line {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-top: 6px;
  font-size: 12.5px;
  line-height: 18px;
  color: var(--colour-text-muted);
}

.shared-strength-word {
  flex: none;
  font-weight: 600;
}

.shared-strength-too-weak .shared-strength-word,
.shared-strength-fair .shared-strength-word {
  color: var(--colour-status-waiting-text);
}

.shared-strength-too-weak .shared-strength-scale > span:nth-child(1),
.shared-strength-fair .shared-strength-scale > span:nth-child(-n+2) {
  background: var(--colour-status-waiting-text);
}

.shared-strength-good .shared-strength-word,
.shared-strength-strong .shared-strength-word {
  color: var(--colour-status-done-text);
}

.shared-strength-good .shared-strength-scale > span:nth-child(-n+3),
.shared-strength-strong .shared-strength-scale > span {
  background: var(--colour-status-done-text);
}

/* The rules of a password, ticked off while the person types. Like the rows
   of a ledger, the lines are separated by the grey rule. */
.shared-rule-list {
  margin-top: 6px;
  font-size: 12.5px;
  line-height: 18px;
}

.shared-rule {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 4px 0;
  color: var(--colour-text);
}

.shared-rule + .shared-rule {
  border-top: 1px solid var(--colour-rule);
}

/* The mark before a rule: an empty ring while the rule waits, a green disc
   with a tick when it is met, a red disc with a cross when it is not met. */
.shared-rule-mark {
  position: relative;
  flex: none;
  width: 14px;
  height: 14px;
  margin-top: 2px;
  border: 1.5px solid var(--colour-control-line);
  border-radius: 50%;
  transition: background-color var(--motion-colour), border-color var(--motion-colour);
}

.shared-rule-met {
  color: var(--colour-text-muted);
}

.shared-rule-met .shared-rule-mark {
  border-color: var(--colour-status-done-text);
  background: var(--colour-status-done-text);
}

.shared-rule-met .shared-rule-mark::after {
  content: "";
  position: absolute;
  left: 3.5px;
  top: 1px;
  width: 3.5px;
  height: 6.5px;
  border: solid var(--colour-on-fill);
  border-width: 0 1.6px 1.6px 0;
  transform: rotate(45deg);
}

.shared-rule-unmet {
  color: var(--colour-danger);
}

.shared-rule-unmet .shared-rule-mark {
  border-color: var(--colour-danger);
  background: var(--colour-danger);
}

.shared-rule-unmet .shared-rule-mark::before,
.shared-rule-unmet .shared-rule-mark::after {
  content: "";
  position: absolute;
  left: 50%;
  top: 50%;
  width: 7px;
  height: 1.6px;
  border-radius: var(--radius-pill);
  background: var(--colour-on-fill);
  transform: translate(-50%, -50%) rotate(45deg);
}

.shared-rule-unmet .shared-rule-mark::after {
  transform: translate(-50%, -50%) rotate(-45deg);
}

/* An error line under a rule list or a scale keeps a little air. */
.shared-rule-list + .shared-field-error,
.shared-strength + .shared-field-error {
  margin-top: 4px;
}

/* ===== SECTION 8: CARDS, FILTER BAR AND LAYOUT HELPERS ===== */
.shared-card {
  overflow: hidden;
  border: 1px solid var(--colour-line);
  border-radius: var(--radius-card);
  background: var(--colour-surface);
  box-shadow: var(--shadow-card);
}

.shared-card-header {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--colour-line);
}

.shared-card-title {
  font-size: 15px;
  line-height: 22px;
  font-weight: 600;
}

.shared-card-count {
  margin-left: 6px;
  font-weight: 400;
  color: var(--colour-text-muted);
}

.shared-card-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-left: auto;
}

.shared-card-body {
  padding: 16px;
}

.shared-card-body > * + * {
  margin-top: 12px;
}

.shared-card-strip {
  padding: 12px 16px;
  border-bottom: 1px solid var(--colour-line);
}

.shared-card-part-title {
  padding: 12px 16px 8px;
  border-top: 1px solid var(--colour-line);
  font-size: 13px;
  line-height: 18px;
  font-weight: 600;
  color: var(--colour-text-muted);
}

.shared-filter-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--colour-line);
}

.shared-filter-bar .shared-input {
  width: 160px;
}

.shared-filter-bar .shared-input-short {
  width: 96px;
}

.shared-filter-bar .shared-input-medium {
  width: 200px;
}

.shared-filter-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
}

.shared-filter-label .shared-input {
  width: 190px;
  font-weight: 400;
}

.shared-filter-buttons {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

/* A date range: its two fields stay together when the filter bar wraps. */
.shared-filter-range {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.shared-filter-end {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}

.shared-grid-two {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 16px;
  align-items: start;
}

.admin-order-payment-refund-grid {
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 460px), 1fr));
}

.shared-grid-three {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 16px;
  align-items: start;
}

.shared-button-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

/* ===== SECTION 9: TABLES AND PAGER ===== */
.shared-table-scroll {
  overflow-x: auto;
  overscroll-behavior-x: contain;
  scroll-padding-inline-start: var(--shared-table-pinned-width, 0px);
}

/* A list that scrolls sideways keeps the name of each row in view. The
   script adds shared-table-scroll-overflow while the table is wider than
   its card; the first column then stays at the left behind a margin rule. */
.shared-table-scroll-overflow > .shared-table > thead > tr > th:first-child,
.shared-table-scroll-overflow > .shared-table > tbody > tr > th:first-child {
  position: sticky;
  left: 0;
  z-index: 1;
  box-shadow: inset -1px 0 var(--colour-rule-strong);
}

.shared-table-scroll-overflow > .shared-table > thead > tr > th:first-child {
  background: var(--colour-sunken);
}

.shared-table-scroll-overflow > .shared-table > tbody > tr > th:first-child {
  background: var(--shared-row-background, var(--colour-surface));
}

.shared-table-scroll:focus-visible {
  outline-offset: -2px;
}

.shared-table {
  width: 100%;
  border-collapse: collapse;
}

.shared-table th,
.shared-table td {
  padding: 0 12px;
  text-align: left;
  white-space: nowrap;
}

.shared-table thead th {
  height: 36px;
  border-bottom: 1px solid var(--colour-rule-strong);
  background: var(--colour-sunken);
  color: var(--colour-text-muted);
  font-size: 12.5px;
  line-height: 18px;
  font-weight: 600;
}

.shared-table tbody th,
.shared-table tbody td {
  height: 46px;
  border-bottom: 1px solid var(--colour-rule);
  font-weight: 400;
}

.shared-table tbody th {
  font-weight: 600;
}

/* A grey remark inside the bold first cell is not bold. */
.shared-table tbody th .shared-muted {
  font-weight: 400;
}

.shared-table tbody tr:last-child th,
.shared-table tbody tr:last-child td {
  border-bottom: 0;
}

/* The total row of a money table: a rule above it and a double rule
   below it, as a ledger closes a sum. */
.shared-table tfoot th,
.shared-table tfoot td {
  height: 46px;
  border-top: 1px solid var(--colour-rule-strong);
  border-bottom: 3px double var(--colour-rule-strong);
  font-weight: 600;
}

/* Clear card ground under the double rule keeps it apart from the edge
   of the card. */
.shared-table-scroll:has(> .shared-table > tfoot) {
  padding-bottom: 12px;
}

.shared-table tbody tr {
  --shared-row-background: var(--colour-surface);
  transition: background-color var(--motion-colour);
}

.shared-table tbody tr:hover {
  --shared-row-background: var(--colour-sunken);
  background: var(--shared-row-background);
}

.shared-table .shared-table-number {
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.shared-table .shared-table-time {
  font-variant-numeric: tabular-nums;
}

.shared-table .shared-table-actions {
  text-align: right;
}

.shared-table .shared-table-wrap {
  min-width: 160px;
  padding-top: 8px;
  padding-bottom: 8px;
  white-space: normal;
}

.shared-table-work tbody th,
.shared-table-work tbody td {
  height: auto;
  padding: 12px;
  vertical-align: top;
  white-space: normal;
}

.admin-order-detail-read-only-table tbody th,
.admin-order-detail-read-only-table tbody td {
  overflow-wrap: anywhere;
}

.shared-cell-main {
  display: block;
}

.shared-cell-sub {
  display: block;
  margin-top: 2px;
  font-size: 12.5px;
  line-height: 18px;
  font-weight: 400;
  color: var(--colour-text-muted);
}

.shared-cell-flags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 4px;
}

.shared-cell-line {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.shared-cell-note {
  font-size: 12.5px;
  line-height: 18px;
  color: var(--colour-text-muted);
}

.shared-value-line {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.shared-value-line + .shared-value-line {
  margin-top: 2px;
}

.shared-value-line .shared-cell-note {
  flex: none;
  width: 28px;
}

.shared-cell-buttons {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.shared-table-empty {
  padding: 32px 16px;
  text-align: center;
  color: var(--colour-text-muted);
}

.shared-pager {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  border-top: 1px solid var(--colour-line);
  color: var(--colour-text-muted);
}

.shared-pager-buttons {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-left: auto;
}

.shared-pager-current {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 28px;
  height: 28px;
  padding: 0 8px;
  border: 2px solid var(--colour-primary);
  border-radius: var(--radius-control);
  background: var(--colour-selection);
  color: var(--colour-primary);
  font-size: 13px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

/* ===== SECTION 10: STATUS BADGES AND FLAGS ===== */
.shared-status-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 24px;
  padding: 0 10px 0 8px;
  border-radius: var(--radius-pill);
  font-size: 12.5px;
  line-height: 18px;
  font-weight: 600;
  white-space: nowrap;
  vertical-align: middle;
}

.shared-status-badge::before {
  content: "";
  flex: none;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: currentColor;
}

.shared-flag {
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 7px;
  border-radius: var(--radius-small);
  font-size: 12px;
  line-height: 16px;
  font-weight: 600;
  white-space: nowrap;
  vertical-align: middle;
}

.shared-status-badge-waiting,
.shared-flag-waiting {
  background: var(--colour-status-waiting-surface);
  color: var(--colour-status-waiting-text);
}

.shared-status-badge-warehouse,
.shared-flag-warehouse {
  background: var(--colour-status-warehouse-surface);
  color: var(--colour-status-warehouse-text);
}

.shared-status-badge-road,
.shared-flag-road {
  background: var(--colour-status-road-surface);
  color: var(--colour-status-road-text);
}

.shared-status-badge-done,
.shared-flag-done {
  background: var(--colour-status-done-surface);
  color: var(--colour-status-done-text);
}

.shared-status-badge-problem,
.shared-flag-problem {
  background: var(--colour-status-problem-surface);
  color: var(--colour-status-problem-text);
}

.shared-status-badge-closed,
.shared-flag-closed {
  background: var(--colour-status-closed-surface);
  color: var(--colour-status-closed-text);
}

/* ===== SECTION 11: ACTIONS MENU ===== */
.shared-menu {
  display: inline-block;
}

/* The menu is never higher than the window. If its lines do not fit,
   they scroll inside the menu. */
.shared-menu-list {
  position: fixed;
  inset: auto;
  margin: 0;
  min-width: 180px;
  max-height: calc(100vh - 16px);
  padding: 4px;
  overflow-y: auto;
  overscroll-behavior: contain;
  border: 1px solid var(--colour-line-strong);
  border-radius: var(--radius-popup);
  background: var(--colour-surface);
  color: var(--colour-text);
  box-shadow: var(--shadow-popup);
}

.shared-menu-list:popover-open {
  display: flex;
  flex-direction: column;
  animation: shared-menu-in var(--motion-menu) ease-out;
}

/* A menu that is drawn open inside the page, used only on the parts page. */
.shared-menu-list-shown {
  position: static;
  display: inline-flex;
  flex-direction: column;
}

.shared-menu-item {
  flex: none;
  display: flex;
  align-items: center;
  width: 100%;
  height: 32px;
  padding: 0 10px;
  border: 0;
  border-radius: var(--radius-small);
  background: transparent;
  color: inherit;
  font-size: 14px;
  font-weight: 500;
  text-align: left;
  white-space: nowrap;
  transition: background-color var(--motion-colour);
}

.shared-menu-item:hover {
  background: var(--colour-hover);
}

.shared-menu-item-danger {
  color: var(--colour-danger);
}

.shared-menu-divider {
  flex: none;
  height: 1px;
  margin: 4px 6px;
  background: var(--colour-line);
}

@keyframes shared-menu-in {
  from {
    opacity: 0;
    transform: translateY(-4px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* ===== SECTION 12: TABS ===== */
.shared-tab-list {
  position: relative;
  display: flex;
  gap: 4px;
  border-bottom: 1px solid var(--colour-line);
}

.shared-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 38px;
  padding: 0 16px;
  border: 0;
  background: transparent;
  color: var(--colour-text-muted);
  font-size: 14px;
  font-weight: 600;
  white-space: nowrap;
  transition: color var(--motion-colour);
}

.shared-tab:hover {
  color: var(--colour-text);
}

.shared-tab-active {
  color: var(--colour-text);
  box-shadow: inset 0 -2px 0 var(--colour-primary);
}

.shared-tab-note {
  font-size: 12.5px;
  font-weight: 400;
  color: var(--colour-text-muted);
}

/* When the script has measured the tabs, one bar moves between them. */
.shared-tab-list-moving .shared-tab-active {
  box-shadow: none;
}

.shared-tab-list-moving::after {
  content: "";
  position: absolute;
  left: var(--shared-tab-bar-left, 0px);
  bottom: 0;
  width: var(--shared-tab-bar-width, 0px);
  height: 2px;
  background: var(--colour-primary);
  transition: left var(--motion-tab) ease-out, width var(--motion-tab) ease-out;
}

.shared-tab-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* ===== SECTION 13: WINDOWS ===== */
.shared-dialog {
  width: 480px;
  max-width: calc(100vw - 48px);
  max-height: calc(100vh - 48px);
  padding: 0;
  border: 1px solid var(--colour-line-strong);
  border-radius: var(--radius-card);
  background: var(--colour-surface);
  color: var(--colour-text);
  box-shadow: var(--shadow-window);
}

.shared-dialog[open] {
  display: flex;
  flex-direction: column;
  animation: shared-dialog-in var(--motion-window) ease-out;
}

.shared-dialog::backdrop {
  background: var(--colour-overlay);
  animation: shared-fade-in var(--motion-window) ease-out;
}

.shared-dialog-wide {
  width: 560px;
}

/* A window that is drawn inside the page, used only on the parts page. */
.shared-dialog-shown {
  display: flex;
  flex-direction: column;
}

.shared-dialog-title {
  flex: none;
  padding: 16px 20px 0;
  font-size: 16px;
  line-height: 24px;
  font-weight: 600;
}

/* A long window body scrolls inside the window. Scrolling stops at its
   end and does not move the page behind the window. */
.shared-dialog-body {
  flex: 1;
  min-height: 0;
  padding: 12px 20px 16px;
  overflow-y: auto;
  overscroll-behavior: contain;
}

.shared-dialog-body > * + * {
  margin-top: 12px;
}

.shared-dialog-body > .shared-field + .shared-field {
  margin-top: 16px;
}

.shared-dialog-footer {
  flex: none;
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 8px;
  padding: 12px 20px 16px;
}

.shared-secret-box {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border: 1px solid var(--colour-line);
  border-radius: var(--radius-control);
  background: var(--colour-sunken);
}

.shared-secret-text {
  flex: 1;
  min-width: 0;
  font-family: var(--font-fixed);
  font-size: 18px;
  line-height: 24px;
  font-weight: 600;
  letter-spacing: 0.04em;
  user-select: all;
}

.shared-label-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  width: 220px;
  margin-left: auto;
  margin-right: auto;
  padding: 16px;
  border: 1px solid var(--colour-line-strong);
  border-radius: var(--radius-small);
  background: var(--colour-surface);
  text-align: center;
}

.shared-label-picture {
  display: block;
  width: 148px;
  height: 148px;
  fill: currentColor;
}

@keyframes shared-dialog-in {
  from {
    opacity: 0;
    transform: scale(0.96);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

@keyframes shared-fade-in {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

/* ===== SECTION 14: MESSAGES AND NOTES ===== */
.shared-message-area {
  position: fixed;
  top: 68px;
  right: 16px;
  z-index: 60;
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 360px;
  max-width: calc(100vw - 32px);
  pointer-events: none;
}

/* Message boxes drawn inside the page, used only on the parts page. */
.shared-message-area-shown {
  position: static;
}

/* These drawn boxes are there when the page opens, so they do not move in. */
.shared-message-area-shown .shared-message-box {
  animation: none;
}

.shared-message-box {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 14px 12px 12px;
  border: 1px solid var(--colour-line-strong);
  border-radius: var(--radius-popup);
  background: var(--colour-surface);
  box-shadow: var(--shadow-popup);
  pointer-events: auto;
  animation: shared-message-in var(--motion-message) ease-out;
}

.shared-message-box .shared-icon {
  margin-top: 1px;
}

/* A message box takes the pale ground and the line of its family. */
.shared-message-box-success {
  border-color: var(--colour-status-done-text);
  background: var(--colour-status-done-surface);
}

.shared-message-box-success .shared-icon {
  color: var(--colour-status-done-text);
}

.shared-message-box-error {
  border-color: var(--colour-status-problem-text);
  background: var(--colour-status-problem-surface);
}

.shared-message-box-error .shared-icon {
  color: var(--colour-status-problem-text);
}

.shared-message-box-information {
  border-color: var(--colour-line-strong);
}

.shared-message-box-information .shared-icon {
  color: var(--colour-text-muted);
}

.shared-message-box-leaving {
  animation: shared-message-out var(--motion-message) ease-in forwards;
}

@keyframes shared-message-in {
  from {
    opacity: 0;
    transform: translateY(-8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes shared-message-out {
  from {
    opacity: 1;
  }
  to {
    opacity: 0;
  }
}

.shared-inline-message {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid var(--colour-line);
  border-radius: var(--radius-control);
  background: var(--colour-sunken);
  color: var(--colour-text);
}

.shared-inline-message .shared-icon {
  margin-top: 1px;
}

.shared-inline-message-error {
  border-color: transparent;
  background: var(--colour-status-problem-surface);
  color: var(--colour-status-problem-text);
}

.shared-inline-message-success {
  border-color: transparent;
  background: var(--colour-status-done-surface);
  color: var(--colour-status-done-text);
}

/* A message place: an empty box where the script puts an inline message.
   It takes no room while it is empty. */
.shared-message-place:empty {
  margin-top: 0;
}

/* A note is a quiet annotation line, never a box inside a card. Inside a
   card body it stands on a ruled line. */
.shared-note {
  color: var(--colour-text-muted);
  font-size: 12.5px;
  line-height: 18px;
}

.shared-card-body > .shared-note {
  padding-top: 8px;
  border-top: 1px solid var(--colour-rule);
}

/* ===== SECTION 15: NUMBER BOX, FACT LIST AND TIMELINE ===== */
.shared-number-box {
  padding: 16px;
}

/* The box that holds the work to do is where the day starts: an orange rule
   across its top, inside the rounded corners. */
.shared-number-box-work {
  box-shadow: inset 0 3px 0 var(--colour-primary);
}

.shared-number-box-title {
  font-size: 13px;
  line-height: 18px;
  font-weight: 600;
  color: var(--colour-text-muted);
}

/* A total closes with a double rule, as in a ledger. */
.shared-number-box-value {
  width: max-content;
  min-width: 32px;
  margin-top: 4px;
  padding: 0 4px 2px 0;
  border-bottom: 3px double var(--colour-rule-strong);
  font-size: 28px;
  line-height: 34px;
  font-weight: 600;
}

.shared-number-box-hint {
  margin-top: 6px;
  font-size: 12.5px;
  line-height: 18px;
  color: var(--colour-text-muted);
}

.shared-number-box-group {
  margin-top: 16px;
  padding-bottom: 4px;
  font-size: 12.5px;
  line-height: 18px;
  font-weight: 600;
  color: var(--colour-text-muted);
}

.shared-number-line {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 34px;
  padding: 5px 0;
  border-top: 1px solid var(--colour-line);
  color: var(--colour-text);
  text-decoration: none;
  transition: background-color var(--motion-colour);
}

.shared-number-line:hover {
  background: var(--colour-sunken);
  color: var(--colour-text);
}

.shared-number-line-name {
  flex: 1;
  min-width: 0;
  color: var(--colour-link);
  text-decoration: underline;
  text-underline-offset: 2px;
}

/* A status badge that names a count link keeps its colours and shape, and
   its words take the link underline, so the line reads as a link. */
.shared-number-line-name .shared-status-badge {
  text-decoration: underline;
  text-decoration-thickness: 1px;
  text-underline-offset: 2px;
}

/* A second line under the name of a number line is not underlined. */
.shared-number-line-name .shared-cell-sub {
  display: inline-block;
  width: 100%;
}

.shared-number-line-count {
  flex: none;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.shared-number-line-count-zero {
  font-weight: 400;
  color: var(--colour-text-muted);
}

.shared-fact-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 12px 24px;
}

.shared-fact {
  min-width: 0;
}

.shared-fact-label {
  font-size: 12.5px;
  line-height: 18px;
  color: var(--colour-text-muted);
}

.shared-fact-value {
  margin-top: 2px;
}

/* The two facts a dangerous decision turns on (Payment, Outcome), set as
   one ruled entry above the needed tick and the reason. */
.shared-decision-facts {
  display: grid;
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid var(--colour-danger-line);
  border-radius: var(--radius-small);
  background: var(--colour-sunken);
}

.shared-decision-facts .shared-fact {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  gap: 12px;
  align-items: baseline;
}

.shared-decision-facts .shared-fact-value {
  margin-top: 0;
}

/* The entries of one day, oldest first. The list itself draws nothing;
   each entry draws its own rule. */
.shared-timeline {
  margin: 0;
  padding: 0;
}

/* Each entry is one ruled posting line. */
.shared-timeline-entry {
  display: flex;
  gap: 12px;
  padding: 6px 0;
  border-bottom: 1px solid var(--colour-rule);
}

.shared-timeline-time {
  flex: none;
  width: 130px;
  color: var(--colour-text-muted);
  font-variant-numeric: tabular-nums;
}

.shared-timeline-text {
  flex: 1;
  min-width: 0;
}

.shared-timeline-actor {
  color: var(--colour-text-muted);
}

/* A day book: the entries of one day stand under their date. */
.shared-timeline-day + .shared-timeline-day {
  margin-top: 16px;
}

.shared-timeline-date {
  margin-bottom: 4px;
  padding-bottom: 4px;
  border-bottom: 1px solid var(--colour-rule);
  font-size: 13px;
  line-height: 18px;
  font-weight: 600;
}

.shared-timeline-day .shared-timeline-time {
  width: 48px;
}

/* ===== SECTION 16: PAGES WITHOUT THE FRAME ===== */
.shared-centre-page {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 24px 16px;
}

.shared-centre-name {
  font-size: 20px;
  line-height: 28px;
  font-weight: 600;
  text-align: center;
  color: var(--colour-menu);
}

.shared-centre-sub {
  margin-top: 2px;
  color: var(--colour-text-muted);
  text-align: center;
}

.shared-centre-card {
  width: 380px;
  max-width: 100%;
  margin-top: 16px;
  padding: 24px;
}

.shared-centre-card-wide {
  width: 420px;
}

.shared-centre-card > * + * {
  margin-top: 12px;
}

.shared-centre-card > .shared-field + .shared-field {
  margin-top: 16px;
}

.shared-centre-card > .shared-button-wide {
  margin-top: 20px;
}

.shared-centre-title {
  font-size: 16px;
  line-height: 24px;
  font-weight: 600;
}

.shared-centre-links {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.shared-link-list > li + li {
  margin-top: 6px;
}

/* ===== SECTION 17: MORE PARTS ===== */
/* 17.1 Progress steps: the way of an order, one step under the other */
.shared-step-list {
  display: block;
}

.shared-step {
  position: relative;
  display: flex;
  gap: 12px;
  padding-bottom: 16px;
}

.shared-step:last-child {
  padding-bottom: 0;
}

.shared-step::before {
  content: "";
  position: absolute;
  left: 9px;
  top: 22px;
  bottom: -2px;
  width: 2px;
  background: var(--colour-line-strong);
}

.shared-step:last-child::before {
  display: none;
}

.shared-step-mark {
  flex: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border: 2px solid var(--colour-line-strong);
  border-radius: 50%;
  background: var(--colour-surface);
  color: var(--colour-on-fill);
}

.shared-step-mark .shared-icon {
  width: 12px;
  height: 12px;
  stroke-width: 2.4;
}

.shared-step-text {
  min-width: 0;
}

.shared-step-title {
  font-weight: 500;
  color: var(--colour-text-muted);
}

.shared-step-note {
  font-size: 12.5px;
  line-height: 18px;
  color: var(--colour-text-muted);
}

.shared-step-done::before {
  background: var(--colour-primary);
}

.shared-step-done .shared-step-mark {
  border-color: var(--colour-primary);
  background: var(--colour-primary);
}

.shared-step-done .shared-step-title {
  color: var(--colour-text);
}

.shared-step-current .shared-step-mark {
  border-color: var(--colour-primary);
}

.shared-step-current .shared-step-title {
  color: var(--colour-text);
  font-weight: 600;
}

.shared-step-problem .shared-step-mark {
  border-color: var(--colour-danger);
  background: var(--colour-danger);
}

.shared-step-problem .shared-step-title {
  color: var(--colour-danger);
  font-weight: 600;
}

/* 17.2 Summary: lines of name and amount with a total */
.shared-summary {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.shared-summary-row {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.shared-summary-row > :last-child {
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.shared-summary-total {
  padding-top: 12px;
  padding-bottom: 4px;
  border-top: 1px solid var(--colour-rule-strong);
  border-bottom: 3px double var(--colour-rule-strong);
  font-size: 16px;
  line-height: 24px;
  font-weight: 600;
}

/* 17.3 Notice list: messages for the person who is logged in */
.shared-notice-list {
  display: block;
}

.shared-notice {
  display: flex;
  gap: 12px;
  padding: 12px 16px;
}

.shared-notice + .shared-notice {
  border-top: 1px solid var(--colour-line);
}

.shared-notice-text {
  flex: 1;
  min-width: 0;
}

.shared-notice-title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  font-weight: 400;
}

.shared-notice-unread .shared-notice-title {
  font-weight: 600;
}

.shared-notice-body {
  margin-top: 2px;
  color: var(--colour-text-muted);
}

.shared-notice-time {
  flex: none;
  font-size: 12.5px;
  line-height: 20px;
  color: var(--colour-text-muted);
  font-variant-numeric: tabular-nums;
}

/* 17.4 Scan box and capture box */
.shared-scan-box {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 12px;
  border: 1px dashed var(--colour-control-line);
  border-radius: var(--radius-control);
  background: var(--colour-sunken);
}

.shared-scan-box .shared-input {
  flex: 1;
  width: auto;
  min-width: 160px;
}

.shared-capture-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 160px;
  padding: 16px;
  border: 1px dashed var(--colour-control-line);
  border-radius: var(--radius-control);
  background: var(--colour-sunken);
  color: var(--colour-text-muted);
  text-align: center;
}

/* 17.5 Progress bar: always with its words next to it */
.shared-progress {
  display: flex;
  align-items: center;
  gap: 12px;
}

.shared-progress-bar {
  flex: 1;
  min-width: 80px;
  height: 8px;
  overflow: hidden;
  border: 0;
  border-radius: var(--radius-pill);
  background: var(--colour-line);
  appearance: none;
  -webkit-appearance: none;
}

.shared-progress-bar::-webkit-progress-bar {
  background: var(--colour-line);
}

.shared-progress-bar::-webkit-progress-value {
  background: var(--colour-primary);
}

.shared-progress-bar::-moz-progress-bar {
  background: var(--colour-primary);
}

.shared-progress-text {
  flex: none;
  font-size: 12.5px;
  line-height: 18px;
  color: var(--colour-text-muted);
  font-variant-numeric: tabular-nums;
}

/* ===== SECTION 18: SMALL WINDOWS ===== */
/* These rules work only when the frame also has the class
   shared-page-frame-adaptive. A frame without it stays 1000 px wide. */
@media (max-width: 899px) {
  .shared-page-frame-adaptive {
    grid-template-columns: minmax(0, 1fr);
    min-width: 0;
  }

  .shared-page-frame-adaptive .shared-page-frame-side-menu {
    position: fixed;
    top: 0;
    bottom: 0;
    left: 0;
    z-index: 40;
    display: none;
    width: 260px;
    height: auto;
    box-shadow: var(--shadow-window);
  }

  .shared-page-frame-adaptive.shared-page-frame-menu-open .shared-page-frame-side-menu {
    display: flex;
  }

  .shared-page-frame-adaptive .shared-page-frame-top-bar,
  .shared-page-frame-adaptive .shared-page-frame-main {
    grid-column: 1;
  }

  .shared-page-frame-adaptive .shared-page-frame-top-bar {
    gap: 8px;
    padding: 0 16px;
  }

  .shared-page-frame-adaptive .shared-page-frame-main {
    padding: 16px;
  }

  .shared-page-frame-adaptive .shared-page-frame-menu-button,
  .shared-page-frame-adaptive .shared-page-frame-menu-close {
    display: inline-flex;
  }

  .shared-page-frame-adaptive .shared-page-frame-top-bar .shared-page-frame-user,
  .shared-page-frame-adaptive .shared-page-frame-top-link,
  .shared-page-frame-adaptive .shared-page-frame-countdown-words {
    display: none;
  }

  .shared-page-frame-adaptive .shared-page-frame-menu-footer {
    display: flex;
    flex-direction: column;
    gap: 4px;
    margin-top: auto;
    padding: 16px 10px 0;
    border-top: 1px solid var(--colour-menu-line);
  }

  /* A work table becomes a list of cards: one row, one card. */
  .shared-page-frame-adaptive .shared-table-work thead {
    position: absolute;
    width: 1px;
    height: 1px;
    overflow: hidden;
    clip-path: inset(50%);
  }

  .shared-page-frame-adaptive .shared-table-work tbody tr {
    display: block;
    padding: 12px 16px;
    border-bottom: 1px solid var(--colour-line);
  }

  .shared-page-frame-adaptive .shared-table-work tbody tr:last-child {
    border-bottom: 0;
  }

  .shared-page-frame-adaptive .shared-table-work tbody th,
  .shared-page-frame-adaptive .shared-table-work tbody td {
    display: block;
    padding: 0;
    border-bottom: 0;
    text-align: left;
  }

  .shared-page-frame-adaptive .shared-table-work tbody td {
    margin-top: 8px;
  }

  .shared-page-frame-adaptive .shared-table-work tbody [data-title]::before {
    content: attr(data-title);
    display: block;
    font-size: 12.5px;
    line-height: 18px;
    font-weight: 600;
    color: var(--colour-text-muted);
  }

  .shared-page-frame-adaptive .shared-table-work .shared-cell-buttons {
    justify-content: flex-start;
  }

  .shared-page-frame-adaptive .shared-page-header {
    flex-direction: column;
    align-items: stretch;
  }

  .shared-page-frame-adaptive .shared-page-header-actions {
    justify-content: flex-start;
    margin-left: 0;
  }

  .shared-page-frame-adaptive .shared-input-search {
    width: 100%;
  }

  .shared-page-frame-adaptive .shared-timeline-entry {
    flex-wrap: wrap;
  }

  .shared-page-frame-adaptive ~ .shared-message-area {
    top: 60px;
    right: 8px;
    left: 8px;
    width: auto;
    max-width: none;
  }
}

/* ===== SECTION 19: PRINTING A LABEL ===== */
/* The script puts the class shared-print-only-label on the html element
   just before it prints a label, and removes it afterwards. */
@media print {
  .shared-print-only-label * {
    visibility: hidden;
  }

  .shared-print-only-label .shared-label-box,
  .shared-print-only-label .shared-label-box * {
    visibility: visible;
  }

  .shared-print-only-label .shared-label-box {
    position: fixed;
    top: 0;
    left: 0;
    margin: 0;
  }

  .shared-print-only-label .shared-dialog::backdrop {
    background: transparent;
  }
}

/* ===== SECTION 20: PEOPLE WHO SWITCHED ANIMATIONS OFF ===== */
@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    animation-duration: 1ms !important;
    animation-delay: 0ms !important;
    transition-duration: 1ms !important;
  }

  /* The dark layer behind an open window is not reached by the rule
     above, so it has its own rule. */
  .shared-dialog::backdrop {
    animation-duration: 1ms !important;
    animation-delay: 0ms !important;
  }
}
```

---

## Appendix B. The icons

Copy the block below unchanged into every page, as the first element of `body`. The block itself is invisible. It only holds the drawings. A drawing is then shown wherever a tree says `icon(name)`, written as `<svg class="shared-icon" aria-hidden="true"><use href="#icon-name"/></svg>`.

```html
<svg class="shared-icon-sprite" aria-hidden="true">
  <symbol id="icon-dashboard" viewBox="0 0 20 20"><rect x="3" y="3" width="6" height="6" rx="1.5"/><rect x="11" y="3" width="6" height="6" rx="1.5"/><rect x="3" y="11" width="6" height="6" rx="1.5"/><rect x="11" y="11" width="6" height="6" rx="1.5"/></symbol>
  <symbol id="icon-users" viewBox="0 0 20 20"><circle cx="10" cy="7" r="3.2"/><path d="M3.5 17c.6-3.2 3.3-5 6.5-5s5.9 1.8 6.5 5"/></symbol>
  <symbol id="icon-products" viewBox="0 0 20 20"><path d="M3 6.5 10 3l7 3.5v7L10 17l-7-3.5z"/><path d="M3 6.5 10 10l7-3.5M10 10v7"/></symbol>
  <symbol id="icon-orders" viewBox="0 0 20 20"><rect x="4" y="3" width="12" height="14" rx="2"/><path d="M7 7.5h6M7 10.5h6M7 13.5h3.5"/></symbol>
  <symbol id="icon-warehouse" viewBox="0 0 20 20"><path d="M3 8.5 10 4l7 4.5V16H3z"/><path d="M7 16v-4.5h6V16"/></symbol>
  <symbol id="icon-delivery" viewBox="0 0 20 20"><path d="M2.5 5.5h9v7.5h-9z"/><path d="M11.5 8.5h3.2l2.8 3V13h-6z"/><circle cx="6" cy="14.6" r="1.5"/><circle cx="14.2" cy="14.6" r="1.5"/></symbol>
  <symbol id="icon-refunds" viewBox="0 0 20 20"><path d="M4 8h9a3.5 3.5 0 0 1 0 7H8"/><path d="M7 5 4 8l3 3"/></symbol>
  <symbol id="icon-logs" viewBox="0 0 20 20"><path d="M5 3.5h7l3 3v10H5z"/><path d="M12 3.5v3h3M7.5 10h5M7.5 13h5"/></symbol>
  <symbol id="icon-test-tools" viewBox="0 0 20 20"><path d="M8 3.5h4"/><path d="M8.5 3.5v4.3L4.7 14.5A1.7 1.7 0 0 0 6.2 17h7.6a1.7 1.7 0 0 0 1.5-2.5L11.5 7.8V3.5"/><path d="M6.6 11.5h6.8"/></symbol>
  <symbol id="icon-search" viewBox="0 0 20 20"><circle cx="9" cy="9" r="5"/><path d="m13 13 3.5 3.5"/></symbol>
  <symbol id="icon-plus" viewBox="0 0 20 20"><path d="M10 4.5v11M4.5 10h11"/></symbol>
  <symbol id="icon-chevron" viewBox="0 0 20 20"><path d="m5.5 8 4.5 4.5L14.5 8"/></symbol>
  <symbol id="icon-clock" viewBox="0 0 20 20"><circle cx="10" cy="10" r="6.5"/><path d="M10 6.5V10l2.5 1.5"/></symbol>
  <symbol id="icon-download" viewBox="0 0 20 20"><path d="M10 3.5v9M6.5 9.5 10 13l3.5-3.5M4 16h12"/></symbol>
  <symbol id="icon-check" viewBox="0 0 20 20"><circle cx="10" cy="10" r="6.5"/><path d="m7 10.2 2.1 2.1L13.2 8"/></symbol>
  <symbol id="icon-alert" viewBox="0 0 20 20"><path d="M10 3.6 17 16H3z"/><path d="M10 8.5v3.5"/><path d="M10 14.1v.1"/></symbol>
  <symbol id="icon-information" viewBox="0 0 20 20"><circle cx="10" cy="10" r="6.5"/><path d="M10 9.3v4"/><path d="M10 6.7v.1"/></symbol>
  <symbol id="icon-tick" viewBox="0 0 20 20"><path d="m5.5 10.5 3 3 6-6.5"/></symbol>
  <symbol id="icon-label-sample" viewBox="0 0 21 21"><path fill-rule="evenodd" d="M0 0h7v7h-7zM1 1v5h5v-5zM2 2h3v3h-3zM14 0h7v7h-7zM15 1v5h5v-5zM16 2h3v3h-3zM0 14h7v7h-7zM1 15v5h5v-5zM2 16h3v3h-3zM8 0h1v1h-1zM9 0h1v1h-1zM11 0h1v1h-1zM8 1h1v1h-1zM9 1h1v1h-1zM11 1h1v1h-1zM12 1h1v1h-1zM8 2h1v1h-1zM9 2h1v1h-1zM10 2h1v1h-1zM12 2h1v1h-1zM8 3h1v1h-1zM12 3h1v1h-1zM9 4h1v1h-1zM11 4h1v1h-1zM12 4h1v1h-1zM8 5h1v1h-1zM9 5h1v1h-1zM11 5h1v1h-1zM9 6h1v1h-1zM11 6h1v1h-1zM12 6h1v1h-1zM8 7h1v1h-1zM10 7h1v1h-1zM11 7h1v1h-1zM0 8h1v1h-1zM1 8h1v1h-1zM4 8h1v1h-1zM9 8h1v1h-1zM11 8h1v1h-1zM12 8h1v1h-1zM14 8h1v1h-1zM16 8h1v1h-1zM0 9h1v1h-1zM4 9h1v1h-1zM9 9h1v1h-1zM14 9h1v1h-1zM15 9h1v1h-1zM17 9h1v1h-1zM19 9h1v1h-1zM20 9h1v1h-1zM0 10h1v1h-1zM2 10h1v1h-1zM3 10h1v1h-1zM4 10h1v1h-1zM6 10h1v1h-1zM7 10h1v1h-1zM12 10h1v1h-1zM13 10h1v1h-1zM14 10h1v1h-1zM17 10h1v1h-1zM18 10h1v1h-1zM19 10h1v1h-1zM20 10h1v1h-1zM2 11h1v1h-1zM3 11h1v1h-1zM4 11h1v1h-1zM5 11h1v1h-1zM12 11h1v1h-1zM17 11h1v1h-1zM18 11h1v1h-1zM19 11h1v1h-1zM0 12h1v1h-1zM1 12h1v1h-1zM2 12h1v1h-1zM3 12h1v1h-1zM4 12h1v1h-1zM5 12h1v1h-1zM6 12h1v1h-1zM7 12h1v1h-1zM8 12h1v1h-1zM9 12h1v1h-1zM10 12h1v1h-1zM13 12h1v1h-1zM14 12h1v1h-1zM15 12h1v1h-1zM16 12h1v1h-1zM17 12h1v1h-1zM9 13h1v1h-1zM10 13h1v1h-1zM11 13h1v1h-1zM12 13h1v1h-1zM14 13h1v1h-1zM15 13h1v1h-1zM18 13h1v1h-1zM20 13h1v1h-1zM12 14h1v1h-1zM13 14h1v1h-1zM14 14h1v1h-1zM18 14h1v1h-1zM19 14h1v1h-1zM13 15h1v1h-1zM15 15h1v1h-1zM16 15h1v1h-1zM17 15h1v1h-1zM18 15h1v1h-1zM19 15h1v1h-1zM9 16h1v1h-1zM13 16h1v1h-1zM14 16h1v1h-1zM15 16h1v1h-1zM16 16h1v1h-1zM17 16h1v1h-1zM11 17h1v1h-1zM17 17h1v1h-1zM19 17h1v1h-1zM9 18h1v1h-1zM10 18h1v1h-1zM13 18h1v1h-1zM14 18h1v1h-1zM15 18h1v1h-1zM18 18h1v1h-1zM9 19h1v1h-1zM11 19h1v1h-1zM12 19h1v1h-1zM17 19h1v1h-1zM20 19h1v1h-1zM8 20h1v1h-1zM9 20h1v1h-1zM10 20h1v1h-1zM12 20h1v1h-1zM13 20h1v1h-1zM14 20h1v1h-1zM16 20h1v1h-1zM17 20h1v1h-1zM20 20h1v1h-1z"/></symbol>
</svg>
```

| Name | Drawing | Used for |
|---|---|---|
| `dashboard` | Four small squares | Menu entry of a start page |
| `users` | A person | Menu entry "Users" |
| `products` | A box | Menu entry "Products" |
| `orders` | A sheet with lines | Menu entry "Orders" |
| `warehouse` | A hall with a door | Menu entry about the warehouse |
| `delivery` | A van | Menu entry about deliveries |
| `refunds` | An arrow that turns back | Menu entry "Refunds" |
| `logs` | A sheet with a folded corner | Menu entry "Logs" |
| `test-tools` | A laboratory flask | Menu entry "Test tools" |
| `search` | A magnifying glass | Search box |
| `plus` | A plus sign | Main button that creates something |
| `chevron` | A small arrow that points down | Selects and the "Actions" button |
| `clock` | A clock | Session time in the top bar |
| `download` | An arrow down onto a line | Button that downloads a file |
| `check` | A tick in a circle | Success message |
| `alert` | An exclamation mark in a triangle | Error message, failed progress step |
| `information` | The letter i in a circle | Information message |
| `tick` | A tick without a circle | Finished progress step |
| `label-sample` | A square picture that looks like a QR code | Only as the placeholder picture of a label box. It is not a real code. It is used with `svg.shared-label-picture`, not with `icon(...)`. |

**If a menu entry of your role needs an icon that is not in the list**, draw it in the same way and add it to the block in your own pages:

- A new line `<symbol id="icon-NAME" viewBox="0 0 20 20">...</symbol>` inside the block. The name is lower case with hyphens.
- The drawing fits into a box of 20 by 20 and stays 2.5 away from every edge.
- Lines only: `path`, `circle` and `rect` elements without any `fill`, `stroke` or `style` attribute. The style sheet gives every icon the same line width (1.6), round ends and the colour of the text around it.
- One simple shape that can be recognised at 18 px. No text inside an icon.

Icons are used only in the places of Section 4.26.
