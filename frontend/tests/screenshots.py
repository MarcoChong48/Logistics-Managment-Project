"""Screenshots of every page of the LMS Admin web app, made with Playwright, plus a layout check.

Usage (the dev server must run on http://127.0.0.1:5173, start it with `npm run dev`, or let the
webapp-testing helper start it: `python with_server.py --server "npm run dev" --port 5173 -- python ...`):

    python tests/screenshots.py 1440 900 test-output/screenshots/1440
    python tests/screenshots.py 390 844 test-output/screenshots/390
    python tests/screenshots.py 390 844 test-output/screenshots/390 "02-dashboard,06-users"   (only these)

Below 768 px the browser behaves like a phone (touch, coarse pointer), so touch-sized controls are shot
the way a phone shows them. The script logs in with the sample account admin_main, opens every page (the
app keeps its sample data in memory, so it moves between pages without reloading), waits until the network
is idle and the page title is shown, and saves one full-page screenshot per page.

For every page it reports layout problems:
  sidewaysScroll  pixels the page scrolls sideways (must be 0)
  spill           elements that stick out of their card or out of the window (outside scroll boxes)
  clipped         text cut off by its own box (overflow hidden, content wider than the box)
  smallTargets    on phones: buttons, fields and stand-alone links lower or narrower than 40 px
and the console errors of the whole run.
"""

import json
import pathlib
import sys

from playwright.sync_api import sync_playwright

BASE = "http://127.0.0.1:5173/"
PASSWORD = "Linen7Route"

PAGES = [
    ("01-login", "#/login", False),
    ("02-dashboard", "#/admin/dashboard", True),
    ("03-warehouse-cases", "#/admin/warehouse-cases", True),
    ("04-delivery-cases", "#/admin/delivery-cases", True),
    ("05-refunds", "#/admin/refunds", True),
    ("06-users", "#/admin/users", True),
    ("07-products", "#/admin/products", True),
    ("08-orders", "#/admin/orders", True),
    ("09-order-detail", "#/admin/orders/ORD-10229", True),
    ("10-logs-audit", "#/admin/logs", True),
    ("11-logs-security", "#/admin/logs?tab=security", True),
    ("12-logs-scans", "#/admin/logs?tab=scans", True),
    ("13-test-tools", "#/admin/test-tools", True),
    ("14-not-found", "#/admin/nowhere", True),
    ("15-welcome", "#/welcome?notice=NOT_ALLOWED", True),
    ("16-change-password", "#/change-password", True),
]

LAYOUT_CHECK = """(phone) => {
  const vw = document.documentElement.clientWidth;
  const inScrollBox = (el) => { for (let p = el.parentElement; p && p !== document.body; p = p.parentElement) { const o = getComputedStyle(p).overflowX; if (o === 'auto' || o === 'scroll') return true; } return false; };
  const shown = (el) => { const r = el.getBoundingClientRect(); const cs = getComputedStyle(el); return r.width > 0 && r.height > 0 && cs.visibility !== 'hidden' && cs.display !== 'none'; };
  const label = (el) => (el.getAttribute('aria-label') || el.textContent || el.tagName).trim().replace(/\\s+/g, ' ').slice(0, 50);
  const out = { spill: [], clipped: [], smallTargets: [] };
  for (const el of document.querySelectorAll('main *, header *, body > div *')) {
    if (!shown(el) || el.closest('.sr-only, [aria-hidden=true], dialog:not([open])')) continue;
    if (inScrollBox(el)) continue;
    const r = el.getBoundingClientRect();
    const card = el.parentElement?.closest('section, article, [data-slip], .rounded-card');
    if (r.right > vw + 1 || r.left < -1) out.spill.push(`${label(el)} (window ${Math.round(r.left)}..${Math.round(r.right)})`);
    else if (card && card !== el) { const c = card.getBoundingClientRect(); if (r.right > c.right + 1 || r.left < c.left - 1) out.spill.push(`${label(el)} (card ${Math.round(r.left - c.left)}..${Math.round(r.right - c.right)})`); }
    const cs = getComputedStyle(el);
    if ((cs.overflowX === 'hidden' || cs.overflowX === 'clip' || cs.textOverflow === 'ellipsis') && el.scrollWidth > el.clientWidth + 1
        && !['INPUT', 'SELECT', 'TEXTAREA', 'svg'].includes(el.tagName) && el.getAttribute('role') !== 'progressbar' && el.children.length < 6)
      out.clipped.push(`${label(el)} (${el.scrollWidth} in ${el.clientWidth})`);
    if (phone && el.matches('button, select, input:not([type=checkbox]):not([type=radio]), textarea, a[href]')) {
      const display = cs.display;
      const inlineText = el.tagName === 'A' && display === 'inline';
      if (!inlineText && (r.height < 39.5 || r.width < 39.5)) out.smallTargets.push(`${label(el)} (${Math.round(r.width)}x${Math.round(r.height)})`);
    }
  }
  for (const k of Object.keys(out)) out[k] = [...new Set(out[k])].slice(0, 15);
  return out;
}"""


def go(page, hash_route):
    page.evaluate("(h) => { location.hash = h; }", hash_route)
    page.wait_for_load_state("networkidle")
    page.wait_for_selector("h1", state="visible")
    page.wait_for_timeout(300)
    # Park the mouse on the empty middle of the top bar, so no row shows its hover colour.
    page.mouse.move(page.viewport_size["width"] // 2, 8)


def main():
    width, height, out = int(sys.argv[1]), int(sys.argv[2]), pathlib.Path(sys.argv[3])
    only = set(sys.argv[4].split(",")) if len(sys.argv) > 4 else None
    phone = width < 768
    out.mkdir(parents=True, exist_ok=True)
    report = []
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        context = browser.new_context(
            viewport={"width": width, "height": height},
            device_scale_factor=1,
            reduced_motion="reduce",
            is_mobile=phone,
            has_touch=phone,
        )
        page = context.new_page()
        errors = []
        page.on("console", lambda m: errors.append(m.text) if m.type == "error" else None)
        page.on("pageerror", lambda e: errors.append(str(e)))
        page.goto(BASE + "#/login")
        page.wait_for_load_state("networkidle")
        page.wait_for_selector("#sharedLoginUserUsernameInput")
        if not only or "01-login" in only:
            page.screenshot(path=str(out / "01-login.png"), full_page=True)
            report.append({"page": "01-login", "sidewaysScroll": page.evaluate("document.documentElement.scrollWidth - document.documentElement.clientWidth"), **page.evaluate(LAYOUT_CHECK, phone)})
        page.fill("#sharedLoginUserUsernameInput", "admin_main")
        page.fill("#sharedLoginUserPasswordInput", PASSWORD)
        page.click("form button[type=submit]")
        page.wait_for_function("location.hash.includes('/admin/dashboard')")
        page.wait_for_load_state("networkidle")
        page.wait_for_timeout(500)
        for name, route, needs_login in PAGES:
            if not needs_login or (only and name not in only):
                continue
            go(page, route)
            overflow = page.evaluate("document.documentElement.scrollWidth - document.documentElement.clientWidth")
            page.screenshot(path=str(out / f"{name}.png"), full_page=True)
            report.append({"page": name, "sidewaysScroll": overflow, **page.evaluate(LAYOUT_CHECK, phone)})
        browser.close()
    problems = [r for r in report if r["sidewaysScroll"] or r["spill"] or r["clipped"] or r["smallTargets"]]
    print(json.dumps({"width": width, "phone": phone, "pages": [r["page"] for r in report], "problems": problems, "consoleErrors": errors}, indent=1))


if __name__ == "__main__":
    main()
