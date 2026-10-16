# Theme setup — wiring the design system into the frontend

The design system is in this folder (`README.md` is the brand book). The frontend files are already in the repo but **not wired in yet**; doing so belongs to **Phase 7b — Design** in `BUILD_PLAN.md`.

## Files in the repo

| File | Purpose |
|---|---|
| `frontend/src/styles/tokens.css` | Colours (light + dark), spacing, radii, type classes as CSS variables. Generated from `tokens.json` here — change the design system, then regenerate; don't hand-edit. |
| `frontend/src/styles/library-components.css` | The `.la-*` classes: sidebar, header, stat tiles, badges, ranked list, activity feed, checkout bar. |
| `frontend/src/styles/library-material-theme.scss` | Angular Material theme mapped onto the tokens. Replaces `src/material-theme.scss` once wired. |
| `frontend/src/app/core/theme/theme.service.ts` | Light/dark switch: sets `data-theme` on `<html>`, preference in `localStorage` (same pattern as `I18nService`). |

## Wiring steps (Phase 7b)

1. **`angular.json`** — in `projects.frontend.architect.build.options.styles`, replace `src/material-theme.scss` with:
   ```json
   "styles": [
     "src/styles/library-material-theme.scss",
     "src/styles/tokens.css",
     "src/styles/library-components.css",
     "src/styles.css"
   ]
   ```
   Keep the `html { height: 100% }` and `body { margin: 0; height: 100% }` rules from the old `material-theme.scss` (move them into `styles.css`), and set the body to `background: var(--paper); color: var(--ink); font-family: var(--font-sans);`.
2. **`src/index.html`** — replace the Roboto link with:
   ```html
   <link href="https://fonts.googleapis.com/css2?family=Fraunces:opsz,wght@9..144,500;9..144,600&family=Source+Sans+3:wght@400;600;700&display=swap" rel="stylesheet" />
   ```
   and add `data-theme="light"` to `<html>` (`ThemeService` overrides it on startup). `library-components.css` has no font import; the fonts come only from here.
3. **Theme toggle** — `inject(ThemeService).toggle()` from the shell. Its label goes through i18n keys (EN + RO), like every other string.
4. Remove `src/material-theme.scss` once nothing references it.

## Rules that still apply

- **No hardcoded strings.** The sample text in `components/*/preview.html` and the screenshots (library names, members, counts) is illustration only; every label in the app goes through i18n keys.
- **Only what the spec defines.** The dashboard shows what `GET /api/dashboard` returns: `totalCopies`, `totalMembers`, `loansActive`, `loansOverdue`, `mostBorrowed`. Several design-system pieces go beyond the current spec and need an explicit decision before anything is built from them:
  - **ActivityFeed**, **MemberStrip** (most active members), **ReportCard** — no endpoint or spec section.
  - **Library switcher** in `LibraryHeader` — each library runs its own deployment (`FUNCTIONAL_SPEC.md` §13).
  - **"Due soon" badge** — loan statuses are Active / Overdue / Returned only.
  - **CheckoutBar** — lending exists as `loan-lend-dialog`; a persistent bar would be a UI change to an existing flow.
- Any screen showing a book number also shows title and author (`DATA_MODEL.md` §4).
- Badge mapping: `BookStatus` `LOST`/`DAMAGED`/`WITHDRAWN` → `la-badge--muted`; loan `ACTIVE` → `la-badge--loan`; `LATE` (Overdue) → `la-badge--overdue`; `FINISHED` (Returned) and an available copy → `la-badge--available`.

## Decision — Phase 7b scope

Phase 7b builds only what the spec and `API_CONTRACT.md` already cover. The following design-system pieces are **out of scope** and are not built:

- ActivityFeed
- MemberStrip
- ReportCard
- Library switcher (`LibraryHeader`)
- "Due soon" badge
- CheckoutBar

Revisiting any of them needs a spec/contract change first.
