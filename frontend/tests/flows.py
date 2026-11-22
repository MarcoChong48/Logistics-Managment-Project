"""End-to-end checks of the LMS Admin web app (Playwright, Chromium).

Run with the dev server on http://127.0.0.1:5173:

    python tests/flows.py            # desktop, 1440 x 900
    python tests/flows.py 390 844    # phone size

Every check prints PASS or FAIL. The script exits with 1 when a check fails.
"""

import re
import sys
import tempfile
import pathlib

from playwright.sync_api import Page, expect, sync_playwright

BASE = "http://127.0.0.1:5173/"
PASSWORD = "Linen7Route"
results: list[tuple[str, bool, str]] = []


def check(name: str, condition: bool, detail: str = "") -> None:
    results.append((name, bool(condition), detail))
    print(("PASS " if condition else "FAIL ") + name + (f"  ({detail})" if detail and not condition else ""))


def go(page: Page, route: str) -> None:
    page.evaluate("(h) => { location.hash = h; }", route)
    page.wait_for_timeout(350)


def toast_text(page: Page) -> str:
    page.wait_for_timeout(500)
    return " ".join(page.locator("[role=status]").all_inner_texts())


def counter(page: Page, label: str) -> str:
    link = page.locator(f"nav[aria-label='Admin pages'] a:has-text('{label}')").first
    return link.inner_text().replace(label, "").strip()


def open_menu_if_phone(page: Page) -> None:
    menu = page.get_by_role("button", name="Menu", exact=True)
    if menu.count() and menu.is_visible():
        menu.click()
        page.wait_for_timeout(200)


def close_menu_if_phone(page: Page) -> None:
    close = page.get_by_role("button", name="Close menu")
    if close.count() and close.is_visible():
        close.click()
        page.wait_for_timeout(150)


def login(page: Page, username: str, password: str) -> None:
    go(page, "#/login")
    page.fill("#sharedLoginUserUsernameInput", username)
    page.fill("#sharedLoginUserPasswordInput", password)
    page.click("form button[type=submit]")
    page.wait_for_timeout(700)


def main() -> None:
    width = int(sys.argv[1]) if len(sys.argv) > 1 else 1440
    height = int(sys.argv[2]) if len(sys.argv) > 2 else 900
    phone = width < 1024
    with sync_playwright() as p:
        browser = p.chromium.launch()
        context = browser.new_context(viewport={"width": width, "height": height}, reduced_motion="reduce")
        page = context.new_page()
        errors: list[str] = []
        page.on("pageerror", lambda e: errors.append(str(e)))
        page.on("console", lambda m: errors.append(m.text) if m.type == "error" else None)
        page.goto(BASE + "#/login")
        page.wait_for_selector("#sharedLoginUserUsernameInput")

        # ----- login
        login(page, "admin_main", "wrong-password")
        check("Wrong password is refused with a message", "The username or the password is wrong." in page.locator("#sharedLoginUserMessage").inner_text())
        login(page, "admin_main", PASSWORD)
        check("Login opens the dashboard", "/admin/dashboard" in page.url)
        check("Dashboard counts 8 open cases", "8 cases wait for a decision" in page.locator("main").inner_text())

        # ----- warehouse cases
        go(page, "#/admin/warehouse-cases")
        wait_button = page.get_by_role("button", name=re.compile(r"^Wait for restock for order ")).first
        order = wait_button.get_attribute("aria-label").split("order ")[1]
        wait_button.click()
        dialog = page.get_by_role("dialog")
        confirm = dialog.get_by_role("button", name="Wait for restock")
        check("Wait for restock: confirm is off without a reason", confirm.is_disabled())
        dialog.get_by_label("Reason").fill("abcd")
        check("Wait for restock: 4 characters are not enough", confirm.is_disabled())
        dialog.get_by_label("Reason").fill("new stock arrives on Monday")
        confirm.click()
        check("Wait for restock: message names the order", f"The shortage case of order {order} now waits for restock." in toast_text(page))
        check("Wait for restock: window closed", page.get_by_role("dialog").count() == 0)
        open_menu_if_phone(page)
        check("Warehouse counter goes from 3 to 2", counter(page, "Warehouse cases") == "2", counter(page, "Warehouse cases"))
        close_menu_if_phone(page)

        cancel = page.get_by_role("button", name=re.compile(r"^Cancel and refund order ")).last
        cancel_order = cancel.get_attribute("aria-label").split("order ")[1]
        cancel.click()
        dialog = page.get_by_role("dialog")
        confirm = dialog.get_by_role("button", name="Cancel and refund")
        dialog.get_by_label("Reason").fill("supplier stopped this item")
        check("Cancel and refund: needs the tick as well", confirm.is_disabled())
        dialog.get_by_label("Warehouse has recorded the stock correction for this case").check()
        confirm.click()
        check("Cancel and refund: full refund requested", f"Order {cancel_order} was cancelled. A full refund was requested." in toast_text(page))
        open_menu_if_phone(page)
        check("Refunds counter goes from 2 to 3", counter(page, "Refunds") == "3", counter(page, "Refunds"))
        close_menu_if_phone(page)

        page.get_by_role("button", name=re.compile(r"^Reassign picking task of order ")).first.click()
        dialog = page.get_by_role("dialog")
        dialog.get_by_label("Reason").fill("worker went home sick")
        dialog.get_by_role("button", name="Reassign task").click()
        check("Reassign picking task: message", "was reassigned to" in toast_text(page))

        # ----- delivery cases
        go(page, "#/admin/delivery-cases")
        approve = page.locator("button[aria-label^='Approve redelivery for order']:not([aria-disabled=true])").first
        approve_order = approve.get_attribute("aria-label").split("order ")[1]
        approve.click()
        page.get_by_role("dialog").get_by_role("button", name="Approve redelivery").click()
        check("Approve redelivery: message", f"Redelivery of order {approve_order} was approved." in toast_text(page))
        check("Blocked redelivery says why", page.get_by_text("Decide the open shortage case first.").count() >= 1)

        page.get_by_role("button", name=re.compile(r"^Send reminder for order ")).first.click()
        check("Send reminder: message", "A reminder was sent to the client of order" in toast_text(page))
        check("Send reminder: switched off for 24 hours", page.get_by_text(re.compile(r"Next reminder after")).count() >= 1)

        page.get_by_role("button", name=re.compile(r"^Reject request for order ")).first.click()
        dialog = page.get_by_role("dialog")
        dialog.get_by_label("Reason").fill("the client confirmed by phone that they still want it")
        dialog.get_by_role("button", name="Reject request").click()
        check("Reject request: message", "request for order" in toast_text(page) and "was rejected" in toast_text(page))

        page.get_by_role("button", name=re.compile(r"^Record request for order ")).first.click()
        dialog = page.get_by_role("dialog")
        record = dialog.get_by_role("button", name="Record request")
        dialog.get_by_label("I have checked who this person is").check()
        check("Record request: needs how the person was checked", record.is_disabled())
        dialog.get_by_label("How was the person checked?").fill("caller answered the account questions")
        record.click()
        check("Record request: message", "was recorded." in toast_text(page))

        page.get_by_role("button", name=re.compile(r"^Reassign delivery of order ")).first.click()
        dialog = page.get_by_role("dialog")
        dialog.get_by_label("Reason").fill("route change for the afternoon")
        dialog.get_by_role("button", name="Reassign delivery").click()
        check("Reassign delivery: message", "is now the responsibility of" in toast_text(page))

        # ----- refunds
        go(page, "#/admin/refunds")
        page.get_by_role("button", name=re.compile(r"^Check provider status for order ")).first.click()
        check("Check provider status: answer shown", "The payment provider answers:" in toast_text(page))
        page.get_by_role("button", name=re.compile(r"^Retry refund for order ")).first.click()
        dialog = page.get_by_role("dialog")
        dialog.get_by_label("Reason").fill("provider fixed the bank link")
        dialog.get_by_role("button", name="Retry refund").click()
        check("Retry refund: new refund requested", "A new refund was requested for order" in toast_text(page))

        # ----- users
        go(page, "#/admin/users")
        page.get_by_role("button", name="Create user").click()
        dialog = page.get_by_role("dialog")
        dialog.get_by_label("Username").fill("new_driver_07")
        dialog.get_by_label("Role").select_option("DELIVERY_WORKER")
        dialog.get_by_label("Employee number").fill("E-107")
        dialog.get_by_role("button", name="Create user").click()
        page.wait_for_timeout(500)
        password_dialog = page.get_by_role("dialog", name="Temporary password for new_driver_07")
        check("Create user: temporary password shown once", password_dialog.count() == 1 and re.search(r"[A-Z][a-z]\d[a-z]-", password_dialog.inner_text()) is not None)
        password_dialog.get_by_role("button", name="Copy").click()
        page.wait_for_timeout(300)
        copy_text = password_dialog.inner_text()
        check("Copy gives feedback inside the window", "Copied." in copy_text or "Could not copy" in copy_text)
        password_dialog.get_by_role("button", name="Close").click()
        page.get_by_label("Search username").fill("new_driver")
        page.wait_for_timeout(300)
        check("Search finds the new account", "new_driver_07" in page.locator("main").inner_text())
        page.get_by_label("Search username").fill("")
        page.get_by_label("Search username").fill("wh_picker_02")
        page.wait_for_timeout(300)
        actions = page.get_by_role("button", name="Actions for wh_picker_02")
        actions.click()
        check("Actions menu opens", page.get_by_role("button", name="Ban", exact=True).is_visible())
        page.keyboard.press("Escape")
        check("Escape closes the Actions menu", page.get_by_role("button", name="Ban", exact=True).count() == 0)
        actions.click()
        page.get_by_role("button", name="Change role", exact=True).click()
        dialog = page.get_by_role("dialog")
        dialog.get_by_label("Reason").fill("moving to the delivery team")
        check("Change role is refused while the worker has work", dialog.get_by_role("button", name="Change role").is_disabled() and "still has work" in dialog.inner_text())
        dialog.get_by_role("button", name="Cancel").click()
        actions.click()
        page.get_by_role("button", name="Ban", exact=True).click()
        dialog = page.get_by_role("dialog")
        check("Ban window names the active work", "active picking" in dialog.inner_text())
        dialog.get_by_label("Reason").fill("repeated wrong scans")
        dialog.get_by_role("button", name="Ban user").click()
        check("Ban: message", "User wh_picker_02 was banned." in toast_text(page))
        page.get_by_label("Search username").fill("")
        page.get_by_label("Status").select_option("LOCKED_NOW")
        page.wait_for_timeout(300)
        check("Status filter Locked now shows 2 accounts", "2 accounts of" in page.locator("main").inner_text())

        # ----- products
        go(page, "#/admin/products")
        page.get_by_role("button", name="Add product").click()
        dialog = page.get_by_role("dialog")
        dialog.get_by_label("SKU").fill("sku-3001")
        dialog.get_by_label("Name").fill("Linen bread bag")
        dialog.get_by_label("Price").fill("68.5")
        dialog.get_by_label("Low below").fill("4")
        dialog.get_by_role("button", name="Add product").click()
        check("Add product: SKU in capitals, stock 0", "Product SKU-3001 was added with stock 0." in toast_text(page))
        page.get_by_label("Low stock only").check()
        page.wait_for_timeout(300)
        low_text = page.locator("main").inner_text()
        check("Low stock only shows the low products, the new one too", all(code in low_text for code in ["SKU-1002", "SKU-1005", "SKU-1017", "SKU-3001"]) and "4 products of" in low_text)
        page.get_by_role("button", name="Actions for SKU-1002").click()
        page.get_by_role("button", name="Show QR code").click()
        dialog = page.get_by_role("dialog")
        page.wait_for_timeout(300)
        check("QR label shows a real QR code", dialog.locator("svg path").count() >= 1 and "SKU-1002" in dialog.inner_text())
        dialog.get_by_role("button", name="Close").click()
        page.get_by_label("Low stock only").uncheck()
        page.get_by_role("button", name="Import CSV").click()
        dialog = page.get_by_role("dialog")
        sample = pathlib.Path(tempfile.gettempdir()) / "lms-import-test.csv"
        sample.write_text("SKU,name,currentPrice,lowStockThreshold\nSKU-4001,Glass oil bottle,92.50,4\nSKU-4002,Cotton napkin,18,2\nSKU-1001,Duplicate mug,80,3\nbad sku,Broken line,abc,1\n", encoding="utf-8")
        dialog.locator("input[type=file]").set_input_files(str(sample))
        page.wait_for_timeout(200)
        dialog.get_by_role("button", name="Import file").click()
        page.wait_for_timeout(700)
        check("Import CSV: 2 added, 2 rejected with reasons", "2 products added, 2 lines rejected." in dialog.inner_text() and "This SKU already exists." in dialog.inner_text())
        dialog.get_by_role("button", name="Close").click()

        # ----- orders and order page
        go(page, "#/admin/orders?status=DELIVERY_FAILED")
        text = page.locator("main").inner_text()
        check("Orders filter by status", "orders of" in text)
        page.locator("main a[href*='/admin/orders/ORD-']").first.click()
        page.wait_for_timeout(400)
        check("Order page opens with its journey", "Journey" in page.locator("main").inner_text() and page.locator("h1").inner_text().startswith("Order ORD-"))
        check("Order page hides private numbers", "5550 0000 1234" not in page.locator("main").inner_text())

        # ----- logs
        go(page, "#/admin/logs")
        page.get_by_label("Action").select_option("USER_BANNED")
        page.wait_for_timeout(300)
        check("Audit log shows the ban", "repeated wrong scans" in page.locator("main").inner_text())
        page.get_by_role("tab", name=re.compile("Security log")).click()
        page.wait_for_timeout(300)
        check("Security log tab opens", page.get_by_role("tabpanel").inner_text().count("Login") >= 1)
        page.get_by_role("tab", name=re.compile("Picking scans")).click()
        page.wait_for_timeout(300)
        check("Picking scans hide codes that are not SKUs", "[unknown code]" in page.get_by_role("tabpanel").inner_text())

        # ----- test tools
        go(page, "#/admin/test-tools")
        card = page.get_by_role("region", name="Create test user", exact=True)
        card.get_by_label("Role").select_option("WAREHOUSE_WORKER")
        card.get_by_role("button", name="Create test user").click()
        page.wait_for_timeout(600)
        check("Create test user", "Created test_warehouse_" in card.inner_text())
        card = page.get_by_role("region", name="Create failed delivery case", exact=True)
        card.get_by_label("Failures").fill("2")
        card.get_by_label("Returned to Warehouse").check()
        card.get_by_label("Days since return").fill("8")
        card.get_by_role("button", name="Create failed delivery case").click()
        page.wait_for_timeout(600)
        check("Create failed delivery case", "Created TEST-" in card.inner_text())
        card = page.get_by_role("region", name="Set provider answer", exact=True)
        card.get_by_label("Refund number").fill("15")
        card.get_by_label("Outcome").select_option("REFUNDED")
        card.get_by_role("button", name="Set provider answer").click()
        page.wait_for_timeout(600)
        check("Set provider answer", "now answers Refunded for refund 15" in card.inner_text())
        card = page.get_by_role("region", name="Run privacy check", exact=True)
        card.get_by_role("button", name="Run privacy check").click()
        page.wait_for_timeout(700)
        check("Privacy check finds nothing", "0 findings" in card.inner_text(), card.inner_text())
        go(page, "#/admin/delivery-cases")
        check("Tool result shows on Delivery cases", "TEST-" in page.locator("main").inner_text() and "Final review" in page.locator("main").inner_text())

        # ----- forced password change with a temporary password
        page.get_by_role("button", name="Log out").first.click()
        page.wait_for_timeout(500)
        check("Log out shows the notice", "You are logged out." in page.locator("#sharedLoginUserMessage").inner_text())
        login(page, "wh_picker_03", "Kd7p-Wm3x-Qh9t")
        check("Temporary password leads to Choose your own password", page.get_by_role("heading", name="Choose your own password").count() == 1)
        page.click("button[type=submit]")
        page.wait_for_timeout(300)
        check("Empty press shows field errors", "Type your temporary password." in page.locator("main").inner_text())
        page.fill("#sharedChangePasswordCurrentPasswordInput", "Kd7p-Wm3x-Qh9t")
        page.fill("#sharedChangePasswordNewPasswordInput", "Harbour7lamps!")
        check("Strength scale says strong", "Strength: strong" in page.locator("#sharedMeasurePasswordStrengthText").inner_text())
        page.fill("#sharedChangePasswordConfirmPasswordInput", "Harbour7lamps!")
        page.click("button[type=submit]")
        page.wait_for_timeout(1100)
        check("Password changed notice on the login page", "Password changed. Please log in with your new password." in page.locator("#sharedLoginUserMessage").inner_text())
        login(page, "wh_picker_03", "Harbour7lamps!")
        check("Warehouse Worker lands on the welcome page", page.get_by_role("heading", name="Welcome, wh_picker_03").count() == 1)
        go(page, "#/admin/users")
        check("Admin pages are refused for other roles", "You are not allowed to open that page." in page.locator("main").inner_text())
        page.get_by_role("button", name="Log out").click()
        page.wait_for_timeout(400)

        # ----- remove all test data (last, it empties the tables)
        login(page, "admin_main", PASSWORD)
        go(page, "#/admin/test-tools")
        card = page.get_by_role("region", name="Remove all test data", exact=True)
        remove = card.get_by_role("button", name="Remove all test data")
        check("Remove all test data needs REMOVE", remove.is_disabled())
        card.get_by_label("Type REMOVE to confirm").fill("REMOVE")
        remove.click()
        page.wait_for_timeout(700)
        check("Remove all test data reports what it removed", "Removed" in card.inner_text())
        go(page, "#/admin/orders")
        check("Orders shows its empty state", "No orders match these filters." in page.locator("main").inner_text() or "0 orders" in page.locator("main").inner_text())

        check("No console errors", not errors, "; ".join(errors[:3]))
        browser.close()

    failed = [r for r in results if not r[1]]
    print(f"\n{len(results) - len(failed)} of {len(results)} checks passed at {width} px.")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
