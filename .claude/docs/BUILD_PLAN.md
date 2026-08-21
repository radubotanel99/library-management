# Build Plan

Ordered build sequence. Each phase ends with **something that runs** — never a large unrunnable blob.

Companion documents: `FUNCTIONAL_SPEC.md` (what), `DATA_MODEL.md` (schema), `API_CONTRACT.md` (endpoints).

---

## Ground rules

1. **Verify each phase before moving on.** The app must start, and the phase's endpoints must respond, before the next phase begins.
2. **Backend and frontend for a feature ship together.** Build the endpoint *and* the Angular service method that calls it in the same pass — that is what prevents contract drift.
3. **Contract first.** If an endpoint needs to change, edit `API_CONTRACT.md` before writing code.
4. **No feature invention.** If a requirement is absent from the specs, stop and ask rather than guessing.

---

## Phase 0 — Skeleton

**Goal:** both applications start and say hello.

- Repo layout: `/backend`, `/frontend`, `/docs`, `compose.dev.yaml` at root.
- Spring Boot 3 / Java 25 project (Maven): Web, Data JPA, Validation, Flyway, PostgreSQL driver, Lombok.
- `application.yml`: datasource from env vars with dev defaults, `jpa.hibernate.ddl-auto=validate`, Flyway on.
- Angular project with Angular Material and routing.
- CORS config allowing `http://localhost:4200` in the dev profile only.
- `.gitignore` (target, node_modules, dist, `.env`), `.env.example`, `README.md` with run instructions.

**Done when:** `mvn spring-boot:run` starts on `:8080` against the Docker Postgres; `ng serve` starts on `:4200`.

---

## Phase 1 — Schema

**Goal:** the database matches `DATA_MODEL.md` exactly.

- `V1__init_schema.sql` — `category`, `book`, `member`, `loan`, `parameter` with all constraints and indexes:
  - partial unique indexes (`ux_category_name_active`, `ux_member_name_active`, `ux_book_number_active`)
  - `ux_loan_one_open_per_book`
  - check constraints `ck_loan_state`, `ck_book_status`
- `V2__seed_parameters.sql` — `DAYS_TO_KEEP_A_BOOK=14`, `MAX_BOOKS_PER_MEMBER=3`.
- JPA entities with `@Enumerated(EnumType.STRING)`, `FetchType.LAZY`, JPA auditing enabled.

**Done when:** the app starts with `ddl-auto=validate` (proving entities and schema agree) and the tables are visible in DataGrip.

---

## Phase 2 — Categories

**Goal:** the first vertical slice, end to end. Deliberately the simplest entity — it establishes every pattern the rest of the build copies.

Backend: entity → repository → service → controller → DTOs; `@RestControllerAdvice` with the `ErrorResponse` shape from `API_CONTRACT.md` §3; `CATEGORY_NAME_ALREADY_EXISTS`, `CATEGORY_HAS_BOOKS`.

Frontend: `CategoryService`, list screen with Material table, create/edit dialog, delete confirmation, error-code → message mapping, EN/RO i18n set up (language in `localStorage`).

**Done when:** categories are fully usable in the browser, both languages work, and duplicate names show a translated error.

---

## Phase 3 — Books

**Goal:** the catalogue, including removal and restore. Split into three sub-phases so each lands something runnable before the next starts (ground rule 1) — this phase has the most moving parts of any single slice so far.

### Phase 3a — Active catalogue (CRUD + search)

Backend: `Book` entity/repository/service/controller; `BookRequest`/`BookResponse` DTOs; `POST /api/books`, `PUT /api/books/{id}`, `GET /api/books/{id}`; paged `GET /api/books` with `search` (title/author/number) + `categoryId`; `GET /api/books/by-number/{bookNumber}`; `categoryName` denormalised onto the response; `BOOK_NUMBER_ALREADY_EXISTS`, `BOOK_NOT_FOUND`.

`onLoan` also ships now even though Phase 5 (Loans) doesn't exist yet: a repository query against the `loan` table for an open (`ACTIVE`/`LATE`) row on that book id. The `loan` table already exists from Phase 1 — this is a query, not a new feature, so it isn't scope invention.

Frontend: `BookService`; catalogue table with search box and category filter; create/edit dialog.

> Any screen showing `bookNumber` must also show title and author — numbers are only unique among active books.

**Done when:** a book can be created, edited, listed, searched, and filtered by category in the browser.

### Phase 3b — Removal

Backend: `BookStatus` enum (`ACTIVE`/`LOST`/`DAMAGED`/`WITHDRAWN`); `POST /api/books/{id}/remove` with `BookRemoveRequest`; blocked by an open loan (`BOOK_HAS_OPEN_LOAN`); confirm `PUT` still never changes `status`.

Frontend: remove dialog with reason dropdown + optional note.

**Done when:** a book can be removed with a mandatory reason and immediately disappears from the active catalogue.

### Phase 3c — Archive & restore

Backend: `GET /api/books/archived` (everything not `ACTIVE`, same query params as the active list); `POST /api/books/{id}/restore`; `BOOK_NOT_REMOVED`, `BOOK_NUMBER_TAKEN_ON_RESTORE`.

Frontend: archive screen filterable by reason with a Restore action; on `BOOK_NUMBER_TAKEN_ON_RESTORE`, prompt for a new number and retry via `PUT` then `POST /restore`.

**Done when:** a removed book is visible on the archive screen and can be restored — including the number-collision path.

---

## Phase 4 — Members

**Goal:** the member register. Structurally identical to Phase 3 minus the status machinery.

Backend: paged list with `search` across name/email/phone; `openLoanCount` on the response; `MEMBER_NAME_ALREADY_EXISTS`, `MEMBER_HAS_OPEN_LOANS`.

Frontend: member table with search, create/edit dialog, archive with confirmation.

**Done when:** members are fully usable and archiving is blocked while they hold books.

---

## Phase 5 — Loans

**Goal:** the core of the system.

Backend: `POST /api/loans` enforcing **both** safeguards (copy not already lent, member under the limit) and rejecting non-`ACTIVE` books; `POST /{id}/return`; paged list filtered by `state` (including the `OPEN` alias), `memberId`, `bookId`; denormalised book/member fields; **computed `dueAt` and `daysOverdue`**.

Frontend: loans table with status filter and visual emphasis on overdue rows; a lend dialog with searchable book and member pickers; one-click return.

**Done when:** a book can be lent and returned; both safeguards produce translated errors; overdue loans are visually distinct.

---

## Phase 6 — Settings & overdue engine

**Goal:** configurable rules that re-evaluate loans.

Backend: `SettingsService` as the sole typed accessor (parse, validate, default-with-warning); `GET`/`PUT /api/parameters` saving the full set in one transaction; scheduled job flipping `ACTIVE ↔ LATE` in both directions; the same routine invoked immediately after a parameter save.

Frontend: a small settings screen with validation.

**Done when:** raising `DAYS_TO_KEEP_A_BOOK` flips affected loans from Overdue back to Active immediately after saving.

---

## Phase 7 — Dashboard

**Goal:** the home screen from §10 of the spec.

Backend: a single `GET /api/dashboard` with efficient aggregate queries — counts must not load entities into memory.

Frontend: stat cards plus a most-borrowed list, replacing the empty landing page.

**Done when:** the home screen shows live figures that change as books are lent and returned.

---

## Phase 7b — Design

**Goal:** one consistent look across every screen built so far, before the app is packaged for deployment. No new endpoints or business logic — this phase only touches the frontend.

- Pick one Angular Material theme (palette + typography) applied globally; remove any per-screen default-theme leftovers.
- Pass over every screen from Phases 2–7 (categories, books, archive, members, loans, settings, dashboard) for consistent spacing, table density, and button placement.
- Add empty, loading, and error states wherever a table or form can be in one of those states and currently isn't handled.
- Check responsive layout at common desk/tablet widths — librarians may use this from a tablet at the front desk.
- Confirm a shared navigation shell (toolbar/sidenav) links every screen, replacing any ad-hoc per-page links.
- Re-audit for hardcoded strings that slipped in while screens were built quickly — everything still routes through i18n keys, EN and RO both checked.

**Done when:** every screen shares one Material theme, handles empty/loading/error states, is usable at tablet width, and no hardcoded strings remain in either language.

---

## Phase 8 — Production packaging

**Goal:** one deployable artifact.

- Multi-stage `Dockerfile`: build Angular → copy `dist` into Spring Boot's static resources → build the jar → slim runtime image.
- `compose.yaml` (production): app + Postgres, named volume, credentials from `.env`, healthchecks, `restart: unless-stopped`.
- Production `application.yml` profile; **Postgres port not published to the host.**
- Angular `environment.prod.ts` using a relative API base (same origin — no CORS).

**Done when:** `docker compose up` serves the whole application on one port, with data surviving a restart.

---

## Phase 9 — Authentication *(last)*

**Goal:** the prerequisite for exposing the system to the internet.

- `staff_user` table (username, password hash, role) + migration.
- Spring Security with hashed passwords; every `/api/**` endpoint protected.
- Angular login screen, route guards, token attached by an HTTP interceptor, 401 handling.

Purely additive — no endpoint path or payload from `API_CONTRACT.md` changes.

> **Do not expose the application to the public internet before this phase is complete.** Member names, addresses, phone numbers, and borrowing history would otherwise be world-readable.

---

## Explicitly not in v1

Excel export · printable reports · member self-service · email reminders · reservations · per-member borrowing limits (`member.max_books_override`) · per-loan extensions (`loan.due_at_override`) · a separate `title` entity.

Each is additive. Do not build them without an explicit request.

---

## Data migration *(separate, after Phase 8)*

A one-time copy from the existing Oracle database into Postgres: column-mapped inserts, `MY_USER → member`, `RENT → loan`, and `BOOK.deleted` → `status` (`0 → ACTIVE`, `1 → WITHDRAWN`). See `DATA_MODEL.md` §10.
