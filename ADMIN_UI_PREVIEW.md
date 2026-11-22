# LMS Admin React preview

The preview integrates the AdminWebApp redesign with the accepted Spring Boot backend at `0cc608d` on `feature/admin-system`. It includes the Admin workspace and the shared sign-in, change-password and welcome pages. Spring serves the interface and API on the same origin and continues to enforce sessions, security tokens, password changes and roles.

## Review

Start the preview using the instructions below, then open [the React preview](http://127.0.0.1:18092/) and sign in with your local preview Admin account. On a fresh checkout, use the bootstrap account you configured in `.env`. If you also have the accepted Stage 9 stack running, open [the Stage 9 site](http://127.0.0.1:8081/) for comparison. The preview has its own database volume and session cookie. Test mode is enabled for creating synthetic cases.

- Use the dashboard links and menu counts to check the open Warehouse, Delivery and Refund queues. Decide a case and confirm the dashboard reflects the saved state.
- Check filters, pagination and CSV downloads on Users, Products, Orders and Logs. Open an order from a queue and use the existing bookmarks if you have them.
- Try the shared password page and sign-out. Use a synthetic non-Admin account from Test tools to try the welcome page and verify role restrictions.
- Review the workspace on desktop and at a narrow viewport using the menu drawer and row cards.

Use the [seven manual test cases](testcase.md) to record review results. The `admin-react-preview` branch is available for team review. Cutover requires sign-off on the preview.

## Verification on 23 November 2026

- The preview builds against the accepted `0cc608d` Java and database sources. Both local instances reported a healthy database with 27 tables. The preview artifacts matched the production build, which used the live API adapters.
- Chrome loaded all nine Admin pages at 1440 and 390 px without JavaScript errors or horizontal page overflow. The 390 px drawer opened an actionable Warehouse queue card.
- Live browser checks covered first sign-in and forced password change for all four roles, non-Admin welcome and access denial, the Admin password screen, session extension and logout.
- Live checks covered Warehouse restock and Delivery redelivery decisions with counter changes, refund refusal and retry, order details and time-filtered legacy links, user creation and lifecycle changes, product creation and CSV import, QR image rendering, filtered CSV exports and the privacy check with zero findings.
- The preview uses the 23 November LMS design system v2.0 and carries forward the reference app’s touch-target and form accessibility fixes. [Design system](frontend/design-system/lms-admin/MASTER.md) · [Reference accessibility review](frontend/design-system/lms-admin/WEB_GUIDELINES_REVIEW.md)

The prototype's **Held by** filter needs an API that filters held packages by holder. The preview uses the accepted paginated held-packages list. The native QR print dialog is a manual review item.

## Start or restart the preview

From the branch root, start Docker Desktop in Linux containers mode. On a fresh checkout, copy `.env.example` to `.env` and set `LMS_BOOTSTRAP_ADMIN_TEMPORARY_PASSWORD` using the [Admin password requirements](ADMIN_README.md). The default username is `admin_local`. On first sign-in, choose a new password when prompted. For an existing preview database, sign in with its current Admin password.

```powershell
npm ci --prefix frontend
npm run build:preview --prefix frontend
docker compose -p lms-admin-redesign-preview -f compose.yaml -f compose.preview.yaml up -d --build backend
```

The build stages the React HTML and assets into `frontend/.spring-preview/`. Spring reads that folder first and falls back to the packaged resources. This keeps the accepted backend, its API contracts and its default Compose setup intact. The separate override also selects port `18092` and the preview session cookie.

Stop the preview while retaining its data with:

```powershell
docker compose -p lms-admin-redesign-preview -f compose.yaml -f compose.preview.yaml stop
```
