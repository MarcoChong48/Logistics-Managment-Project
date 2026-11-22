# LMS Admin web app

The LMS Admin redesign uses React, TypeScript and Tailwind CSS. It connects to the accepted Stage 9 Spring Boot backend for Admin data, decisions, downloads, and shared sign-in, password and welcome flows.

## Run the live preview

Run the build from this checkout's root and follow the [preview guide](../ADMIN_UI_PREVIEW.md) to start the isolated Spring Boot instance.

```powershell
npm ci --prefix frontend
npm run build:preview --prefix frontend
```

The production assets are staged for Spring under `.spring-preview/`. Spring serves the shared pages and the protected Admin entry on the same origin. The React client initializes the Spring session and security token from `/api/shared/session`.

## Source

| Path | Contents |
|---|---|
| `design-system/lms-admin/MASTER.md` | LMS design system v2.0, including colours, type, layout and accessibility |
| `design-system/lms-admin/tokens.css` | Design tokens imported into Tailwind |
| `design-system/lms-admin/DESIGN_PLAN.md` | Design plan and page specifications |
| `design-system/lms-admin/WEB_GUIDELINES_REVIEW.md` | Accessibility review of the reference design |
| `design-system/lms-admin/PROMPT_FOR_OTHER_SYSTEMS.md` | Design handoff for the other LMS roles |
| `src/api/` | Spring API adapters and session handling |
| `src/components/` | Shared components and Admin frame |
| `src/pages/` | Admin and shared screens |
| `scripts/stage-spring-preview.mjs` | Stages the React output at the related Spring resource paths |

The scripts under `tests/` were authored for the original stand-in API and serve as design reference. The live preview uses the seeded test tools and Stage 9 routes.

## Responsive layouts

| Width | Navigation | List tables | Work queues |
|---|---|---|---|
| Under 768 px | Menu drawer | One card per row | One card per row |
| 768–1023 px | Menu drawer | Tables with pinned first and action columns | One card per row |
| 1024–1279 px | Fixed sidebar | Tables with pinned first and action columns | One card per row |
| 1280 px and up | Fixed sidebar | Tables | Tables |
