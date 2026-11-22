# Prompt for the chat that redesigns another LMS system

Give the new chat this prompt together with `MASTER.md` (this folder). Fill in the three brackets first:

- `[SYSTEM]`: Client, Warehouse Worker or Delivery Worker (one system per chat works best).
- `[CONTEXT FILE]`: the file that describes that system's backend.
- `[FOLDER]`: where the new app goes, for example `GrandProject/ClientWebApp`.

---

This chat builds the [SYSTEM] web app of the grand project in this SoftwareEngineering folder: a redesign of
the current [SYSTEM] web pages. Read [CONTEXT FILE]; it describes the backend these pages use. The attached
`MASTER.md` (master copy: `GrandProject/AdminWebApp/design-system/lms-admin/MASTER.md`) is the design system of
every LMS system, so the new pages must match the Admin pages already built. Start with its Section 0. Build
the app in [FOLDER].

Brief: calm, warm and motivating, with every number and every small text highly readable.

Design process, in this exact order:

1. Step A: Use the ui-ux-pro-max skill only for what this system needs and MASTER.md does not cover yet (for
   example scanning, proof photos or checkout). Do not create a second design system. Write every addition
   into Section 19 of the master copy of MASTER.md.
2. Step B: Use the frontend-design skill to write a design plan for every page within MASTER.md, check it
   against the brief, and apply it to every page.
3. Step C: Once the app is built, run /impeccable critique, fix what it finds, then run /impeccable polish.
4. Step D: Use the web-design-guidelines skill to review every page and fix all high and medium severity
   issues.
5. Step E: Use the webapp-testing skill to screenshot every page at 390px and 1440px wide, fix every layout
   problem, and confirm each fix with new screenshots.

Engineering: React with Tailwind, realistic sample data on every screen, and every page fully responsive.
MASTER.md Section 17 lists the parts and test scripts to reuse from `GrandProject/AdminWebApp`.

Work autonomously and test the app yourself before calling it finished; a mockup or partial prototype is not
finished. Keep every function of the current [SYSTEM] pages, including any test tools.
