---
name: Logistics Operations System
colors:
  surface: '#f8f9ff'
  surface-dim: '#cbdbf5'
  surface-bright: '#f8f9ff'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#eff4ff'
  surface-container: '#e5eeff'
  surface-container-high: '#dce9ff'
  surface-container-highest: '#d3e4fe'
  on-surface: '#0b1c30'
  on-surface-variant: '#45464d'
  inverse-surface: '#213145'
  inverse-on-surface: '#eaf1ff'
  outline: '#76777d'
  outline-variant: '#c6c6cd'
  surface-tint: '#565e74'
  primary: '#000000'
  on-primary: '#ffffff'
  primary-container: '#131b2e'
  on-primary-container: '#7c839b'
  inverse-primary: '#bec6e0'
  secondary: '#0051d5'
  on-secondary: '#ffffff'
  secondary-container: '#316bf3'
  on-secondary-container: '#fefcff'
  tertiary: '#000000'
  on-tertiary: '#ffffff'
  tertiary-container: '#111c2d'
  on-tertiary-container: '#79849a'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#dae2fd'
  primary-fixed-dim: '#bec6e0'
  on-primary-fixed: '#131b2e'
  on-primary-fixed-variant: '#3f465c'
  secondary-fixed: '#dbe1ff'
  secondary-fixed-dim: '#b4c5ff'
  on-secondary-fixed: '#00174b'
  on-secondary-fixed-variant: '#003ea8'
  tertiary-fixed: '#d8e3fb'
  tertiary-fixed-dim: '#bcc7de'
  on-tertiary-fixed: '#111c2d'
  on-tertiary-fixed-variant: '#3c475a'
  background: '#f8f9ff'
  on-background: '#0b1c30'
  surface-variant: '#d3e4fe'
typography:
  headline-xl:
    fontFamily: Inter
    fontSize: 30px
    fontWeight: '700'
    lineHeight: 38px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Inter
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
    letterSpacing: -0.015em
  headline-md:
    fontFamily: Inter
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 28px
    letterSpacing: -0.01em
  headline-sm:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '600'
    lineHeight: 24px
  body-lg:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  body-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  body-sm:
    fontFamily: Inter
    fontSize: 13px
    fontWeight: '400'
    lineHeight: 18px
  body-xs:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 16px
  label-code:
    fontFamily: JetBrains Mono
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.02em
  label-status:
    fontFamily: Inter
    fontSize: 11px
    fontWeight: '700'
    lineHeight: 14px
    letterSpacing: 0.04em
  label-table-head:
    fontFamily: Inter
    fontSize: 11px
    fontWeight: '600'
    lineHeight: 14px
    letterSpacing: 0.05em
rounded:
  sm: 0.125rem
  DEFAULT: 0.25rem
  md: 0.375rem
  lg: 0.5rem
  xl: 0.75rem
  full: 9999px
spacing:
  gutter: 1rem
  gutter-desktop: 1.5rem
  margin: 1rem
  margin-desktop: 2rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2rem
  space-2xl: 3rem
---

## Brand & Style

This design system is engineered for high-density, mission-critical enterprise supply chain management and fulfillment command centers. The visual style follows a **Corporate / Modern** aesthetic, combining Swiss utilitarian precision with contemporary digital product architecture.

### Personality & Demeanor
- **Operational Clarity:** Zero visual fluff; every pixel contributes to wayfinding, data density, or task completion.
- **Reliable Authority:** Slate and navy deep-tones establish an environment of structure, permanence, and enterprise rigor.
- **State Transparency:** High-visibility contextual chips, badges, and progress lines reduce cognitive fatigue for dispatchers, warehouse pickers, and fleet managers operating under high throughput conditions.

### Target Audience
Fulfillment specialists, warehouse dispatch supervisors, inventory coordinators, and freight operations directors monitoring multi-channel shipping pipelines across high-resolution desktop and workstation interfaces.

## Colors

The palette operates on high functional contrast, separating structural interface architecture from dynamic logistical states.

### Core Canvas & Structure
- **Canvas Base:** `#f8fafc` (Slate 50) creates a soft, low-glare work surface for all-day operational use.
- **Surface Elevation (Cards, Panels, Modals):** `#ffffff` (Pure White).
- **Subtle Dividing Lines / Borders:** `#e2e8f0` (Slate 200).
- **Deep Navy Primary (Chrome, Top Navigation, Modals):** `#0f172a` (Slate 900).
- **Secondary Actions & Interactive Anchors:** `#2563eb` (Blue 600) with hover state `#1d4ed8` (Blue 700).
- **Tertiary Surface & Data Labels:** `#1e293b` (Slate 800) and muted text at `#64748b` (Slate 500).

### Operational Status States
Logistical workflows require immediate scanning without reliance solely on textual parsing. Each state utilizes dedicated 50-tint backgrounds, 200-tint borders, and 700/800-tint foreground text:
- **AWAITING_PAYMENT:** Amber warning (`#fffbeb` bg, `#fde68a` border, `#b45309` text).
- **READY_FOR_PICKING:** Sky blue (`#f0f9ff` bg, `#bae6fd` border, `#0369a1` text).
- **PICKING:** Indigo active (`#eef2ff` bg, `#c7d2fe` border, `#4338ca` text).
- **READY_FOR_PICKUP:** Standard operational blue (`#eff6ff` bg, `#bfdbfe` border, `#1d4ed8` text).
- **SHIPPED:** Violet transit (`#f5f3ff` bg, `#ddd6fe` border, `#6d28d9` text).
- **OUT_FOR_DELIVERY:** Purple route phase (`#faf5ff` bg, `#f3e8ff` border, `#7e22ce` text).
- **DELIVERED:** Emerald terminal success (`#ecfdf5` bg, `#a7f3d0` border, `#047857` text).
- **CANCELLED / DELIVERY_FAILED:** Rose terminal danger (`#fff1f2` bg, `#fecdd3` border, `#be123c` text).
- **READY_FOR_REDELIVERY:** Warm amber alert (`#fffbeb` bg, `#fcd34d` border, `#92400e` text).

## Typography

The type system prioritizes scannability across dense data matrices and tracking monitors.

- **Primary Typeface (`Inter`):** Selected for its tall x-height, neutral tone, and contextual geometric alternates. Headings use tight negative tracking (`-0.02em` to `-0.01em`) to maintain solidity in tight operational dashboard rows.
- **Monospaced Numerical System (`JetBrains Mono`):** Applied exclusively to tracking IDs, consignment numbers, SKU barcodes, timestamps, and geolocation coordinates. This ensures tabular alignment without column jitter during dynamic updates.
- **Case Application:** Section headers and data use standard sentence case. Status badges and table header labels enforce uppercase styling with expanded letter spacing for quick horizontal column scanning.

## Layout & Spacing

The layout is built desktop-first, using a 12-column grid designed for 1080p, 1440p, and ultrawide terminal screens, scaling down gracefully to tablet diagnostics.

### Layout Model
- **Application Shell:** Fixed 64px collapsed / 240px expanded left vertical sidebar containing role navigation (Admin, Dispatcher, Picker, Auditor) alongside a persistent 56px utility top bar.
- **Main Canvas:** A fluid 12-column grid container using `gutter-desktop` (24px) and `margin-desktop` (32px) margins to preserve data density without claustrophobia.
- **Information Density:** Row heights are optimized to 44px (compact) or 52px (standard) to display a minimum of 15 records in the primary viewport without requiring infinite scrolling.
- **Breakpoints:**
  - `desktop-xl` (≥1600px): Extended split views with simultaneous table and inspection drawer visibility.
  - `desktop` (1280px–1599px): Primary operational workspace; filters collapse into dropdown menus.
  - `tablet` (768px–1279px): Collapsed rail navigation, modal-driven inspections, horizontally scrolling data tables.

## Elevation & Depth

This system avoids expressive blur and deep ambient shading, relying instead on **low-contrast outlines** paired with **shallow, architectural drop shadows** to ensure rapid rendering on resource-constrained warehouse hardware.

### Layer Stack
- **Level 0 (Surface Base):** `#f8fafc` flat canvas with no shadow.
- **Level 1 (Card & Module Layer):** `#ffffff` surface, bounded by a 1px border (`#e2e8f0`). Box shadow: `0 1px 2px 0 rgba(15, 23, 42, 0.05)`.
- **Level 2 (Hover States, Flyouts & Action Menus):** `#ffffff` surface, 1px border (`#cbd5e1`). Box shadow: `0 4px 6px -1px rgba(15, 23, 42, 0.08), 0 2px 4px -2px rgba(15, 23, 42, 0.04)`.
- **Level 3 (Modal Dialogs & Slide-out Inspection Drawers):** `#ffffff` surface, 1px border (`#94a3b8`). Box shadow: `0 20px 25px -5px rgba(15, 23, 42, 0.12), 0 8px 10px -6px rgba(15, 23, 42, 0.08)`. Backdrop layer employs `rgba(15, 23, 42, 0.60)` solid overlay without background blur filters to maximize frame rate during rapid updates.

## Shapes

The design uses a **Soft (Level 1)** radius hierarchy to reinforce systematic enterprise stability.

### Radius Rules
- **Base Components (Inputs, Buttons, Badges, Table Rows):** `0.25rem` (4px). Avoids playfulness while eliminating harsh corner clip points.
- **Containers & Panels (`rounded-lg`):** `0.5rem` (8px). Applied to cards, slide-over modals, QR code simulation blocks, and contextual popovers.
- **Operational Status Chips:** Fully pill-shaped or strictly `0.25rem` (4px) with uppercase text.
- **Interactive Focus Rings:** 2px offset with a 2px solid `#2563eb` outline on focused elements to support fast keyboard tab navigation in high-volume data-entry environments.

## Components

### Buttons
- **Primary:** Dark navy (`#0f172a`) background, white text, 4px border radius. Hover: `#1e293b`. Focus: 2px offset blue ring.
- **Action / Create:** Vibrant blue (`#2563eb`) background, white text. Hover: `#1d4ed8`.
- **Secondary / Outline:** White surface with 1px `#cbd5e1` border and `#1e293b` text. Hover: `#f8fafc` background.
- **Destructive:** Rose red (`#e11d48`) background or flat border with `#be123c` text.

### Operational Status Chips & Badges
- Displayed as a self-contained inline flex container with a fixed height of 22px, horizontal padding of 8px, and 4px corner radius.
- Includes a 6px circular indicator dot positioned left of the label, matching the border/text color tint.
- Typography: `label-status` (11px, bold, uppercase, `Inter`).

### Data Tables
- **Header:** Sticky positioning, `#f8fafc` background, 1px bottom border (`#e2e8f0`), `label-table-head` typography, vertical separator options for column resizing.
- **Rows:** Alternating subtle row-hover state (`#f1f5f9`), 44px height, integrated row-select checkboxes, right-aligned monetary values and left-aligned tracking identifiers.
- **Tracking ID Cells:** Monospaced (`JetBrains Mono`), bold weight, accompanied by a 1-click clipboard copy utility button.

### Form Inputs & Filters
- **Height:** 36px standard for all search bars, select inputs, and date pickers.
- **Surfaces:** `#ffffff` background, 1px border (`#cbd5e1`), `#0f172a` input text, `#94a3b8` placeholder.
- **Active State:** Border transitions to `#2563eb` with a 1px matching inner glow ring.

### QR Simulation Controls
- Embedded operational diagnostic modules featuring an SVG QR representation framed in a 1px slate-border card.
- Controls include a monospaced payload viewer, a "Simulate Scan" action button, and a transient state indicator that flashes green (`#10b981`) upon successful payload dispatch.

### Modal Dialogs & Inspection Drawers
- **Slide-out Right Drawer:** 480px width, pinned to the right edge with a clean dividing border. Used for tracking timeline reviews and waypoint logs without losing table context.
- **Confirmation Modals:** Centered dialogs restricted to 440px width with an explicit dual-action footer ("Cancel" / "Confirm Transition").

### Toast Notifications
- Floated top-right at 16px offset, stacked with 8px gaps.
- Features a left 4px accent border color-coded to the operational result (Success: `#10b981`, Error: `#e11d48`, Dispatch Alert: `#2563eb`), dismissible via keyboard (`Escape`) or click.