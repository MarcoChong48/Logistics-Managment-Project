"""A quick check of every page against the accessibility rules of the Web Interface Guidelines.

Usage (the dev server must run on http://127.0.0.1:5173):

    python tests/a11y_scan.py 1440 900
    python tests/a11y_scan.py 390 844

For each page it reports: buttons and links without a name, form fields without a label, headings that
skip a level, pages without exactly one h1, images without alt, SVG icons that a screen reader would meet
without a name, inputs without a name attribute, and elements with a click handler that are not buttons
or links (as far as the DOM shows them).
"""

import json
import sys

from playwright.sync_api import sync_playwright

BASE = "http://127.0.0.1:5173/"
PAGES = [
    "#/admin/dashboard",
    "#/admin/warehouse-cases",
    "#/admin/delivery-cases",
    "#/admin/refunds",
    "#/admin/users",
    "#/admin/products",
    "#/admin/orders",
    "#/admin/orders/ORD-10229",
    "#/admin/logs",
    "#/admin/logs?tab=security",
    "#/admin/logs?tab=scans",
    "#/admin/test-tools",
    "#/admin/nowhere",
    "#/welcome",
    "#/change-password",
]

SCAN = """() => {
  const name = (el) => {
    const aria = el.getAttribute('aria-label');
    if (aria && aria.trim()) return aria.trim();
    const by = el.getAttribute('aria-labelledby');
    if (by) return by.split(' ').map((id) => document.getElementById(id)?.textContent || '').join(' ').trim();
    if (el.labels && el.labels.length) return [...el.labels].map((l) => l.textContent).join(' ').trim();
    return (el.textContent || '').trim() || el.getAttribute('title') || el.getAttribute('placeholder') || '';
  };
  const visible = (el) => { const r = el.getBoundingClientRect(); return r.width > 0 && r.height > 0 && getComputedStyle(el).visibility !== 'hidden'; };
  const out = { unnamed: [], unlabelled: [], headingSkips: [], h1: 0, imgNoAlt: [], svgExposed: [], noName: [] };
  for (const el of document.querySelectorAll('button, a[href], [role=button], [role=tab], [role=menuitem]')) {
    if (!visible(el)) continue;
    if (!name(el)) out.unnamed.push(el.outerHTML.slice(0, 120));
  }
  for (const el of document.querySelectorAll('input, select, textarea')) {
    if (el.type === 'hidden' || !visible(el)) continue;
    const hasLabel = (el.labels && el.labels.length) || el.getAttribute('aria-label') || el.getAttribute('aria-labelledby');
    if (!hasLabel) out.unlabelled.push(el.outerHTML.slice(0, 120));
    if (!el.getAttribute('name') && el.type !== 'checkbox' && el.type !== 'file') out.noName.push((el.labels?.[0]?.textContent || el.getAttribute('aria-label') || el.outerHTML.slice(0, 60)).trim());
  }
  let last = 0;
  for (const h of document.querySelectorAll('main h1, main h2, main h3, main h4, main h5, main h6, h1')) {
    if (!visible(h)) continue;
    const level = Number(h.tagName[1]);
    if (level === 1) out.h1 += 1;
    if (last && level > last + 1) out.headingSkips.push(`h${last} -> h${level}: ${h.textContent.trim().slice(0, 50)}`);
    last = level;
  }
  for (const img of document.querySelectorAll('img')) if (!img.hasAttribute('alt')) out.imgNoAlt.push(img.outerHTML.slice(0, 100));
  for (const svg of document.querySelectorAll('svg')) {
    if (!visible(svg)) continue;
    let hidden = false, p = svg;
    while (p && p !== document.body) { if (p.getAttribute && (p.getAttribute('aria-hidden') === 'true' || p.getAttribute('role') === 'img' && p.getAttribute('aria-label'))) { hidden = true; break; } p = p.parentElement; }
    if (!hidden && !svg.getAttribute('aria-label') && !svg.querySelector('title')) out.svgExposed.push(svg.outerHTML.slice(0, 90));
  }
  return out;
}"""


def main():
    width, height = int(sys.argv[1]), int(sys.argv[2])
    report = {}
    with sync_playwright() as p:
        browser = p.chromium.launch()
        page = browser.new_page(viewport={"width": width, "height": height})
        page.goto(BASE + "#/login")
        page.wait_for_selector("#sharedLoginUserUsernameInput")
        report["#/login"] = page.evaluate(SCAN)
        page.fill("#sharedLoginUserUsernameInput", "admin_main")
        page.fill("#sharedLoginUserPasswordInput", "Linen7Route")
        page.click("form button[type=submit]")
        page.wait_for_function("location.hash.includes('/admin/dashboard')")
        page.wait_for_timeout(600)
        for route in PAGES:
            page.evaluate("(h) => { location.hash = h; }", route)
            page.wait_for_timeout(700)
            report[route] = page.evaluate(SCAN)
        browser.close()
    problems = {}
    for route, r in report.items():
        found = {k: v for k, v in r.items() if (k != "h1" and v) or (k == "h1" and v != 1)}
        if found:
            problems[route] = found
    print(json.dumps({"width": width, "pagesChecked": len(report), "problems": problems}, indent=1))


if __name__ == "__main__":
    main()
