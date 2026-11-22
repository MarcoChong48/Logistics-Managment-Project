"""Screenshots of the states between pages: errors, decision windows, messages, menus and the phone drawer.

Usage (the dev server must run on http://127.0.0.1:5173):

    python tests/states.py 1440 900 test-output/states/1440
    python tests/states.py 390 844 test-output/states/390

Each state is saved as one screenshot of the window (not the full page), the way a person sees it.
"""

import json
import pathlib
import re
import sys

from playwright.sync_api import sync_playwright

BASE = "http://127.0.0.1:5173/"
PASSWORD = "Linen7Route"


def main():
    width, height, out = int(sys.argv[1]), int(sys.argv[2]), pathlib.Path(sys.argv[3])
    out.mkdir(parents=True, exist_ok=True)
    shots = []
    with sync_playwright() as p:
        browser = p.chromium.launch()
        context = browser.new_context(viewport={"width": width, "height": height}, device_scale_factor=1, reduced_motion="reduce")
        page = context.new_page()
        errors = []
        page.on("console", lambda m: errors.append(m.text) if m.type == "error" else None)
        page.on("pageerror", lambda e: errors.append(str(e)))

        def shot(name):
            page.wait_for_timeout(250)
            page.screenshot(path=str(out / f"{name}.png"))
            shots.append(name)

        def go(route):
            page.evaluate("(h) => { location.hash = h; }", route)
            page.wait_for_timeout(600)
            page.mouse.move(width // 2, 8)

        page.goto(BASE + "#/login")
        page.wait_for_selector("#sharedLoginUserUsernameInput")
        page.fill("#sharedLoginUserUsernameInput", "admin_main")
        page.fill("#sharedLoginUserPasswordInput", "not-the-password")
        page.click("form button[type=submit]")
        page.wait_for_timeout(600)
        shot("s01-login-error")

        page.fill("#sharedLoginUserPasswordInput", PASSWORD)
        page.click("form button[type=submit]")
        page.wait_for_function("location.hash.includes('/admin/dashboard')")
        page.wait_for_timeout(600)

        if width < 1024:
            page.get_by_role("button", name="Menu", exact=True).click()
            page.wait_for_timeout(300)
            shot("s02-phone-drawer")
            page.get_by_role("button", name="Close menu").click()
            page.wait_for_timeout(200)

        go("#/admin/warehouse-cases")
        page.get_by_role("button", name=re.compile(r"^Wait for restock for order ")).first.click()
        page.wait_for_timeout(300)
        shot("s03-wait-window-needs-reason")
        dialog = page.get_by_role("dialog")
        dialog.get_by_label("Reason").fill("new stock arrives on Monday")
        shot("s04-wait-window-ready")
        dialog.get_by_role("button", name="Wait for restock").click()
        page.wait_for_timeout(500)
        shot("s05-decided-with-message")

        page.get_by_role("button", name=re.compile(r"^Cancel and refund order ")).first.click()
        page.wait_for_timeout(300)
        shot("s06-cancel-and-refund-window")
        page.keyboard.press("Escape")
        page.wait_for_timeout(300)

        go("#/admin/delivery-cases?show=NEEDS_DECISION")
        page.get_by_role("button", name=re.compile(r"^Approve cancellation for order ")).first.click()
        page.wait_for_timeout(300)
        shot("s07-approve-cancellation-window")
        page.keyboard.press("Escape")
        page.wait_for_timeout(300)

        go("#/admin/users")
        page.get_by_role("button", name=re.compile(r"^Actions for client_")).first.click()
        page.wait_for_timeout(300)
        shot("s08-users-actions-menu")
        page.keyboard.press("Escape")

        go("#/admin/products")
        page.get_by_role("button", name=re.compile(r"^Actions for SKU-")).first.click()
        page.wait_for_timeout(200)
        page.get_by_role("button", name="Show QR code").click()
        page.wait_for_timeout(500)
        shot("s09-qr-window")
        page.keyboard.press("Escape")

        go("#/change-password")
        page.get_by_role("button", name="Change password").last.click()
        page.wait_for_timeout(400)
        shot("s10-change-password-errors")

        browser.close()
    print(json.dumps({"width": width, "shots": shots, "consoleErrors": errors}, indent=1))


if __name__ == "__main__":
    main()
