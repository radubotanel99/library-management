# Library Management System

Web application for managing a library: catalogue, members, loans, and overdue tracking.
Replaces an older Java/Hibernate/Tomcat system currently in production at two libraries.

**Stack:** Spring Boot 3 (Java 25) · Angular + Angular Material · PostgreSQL 16 · Flyway · Docker

---

## Documents — read before building

| File | Contains |
|---|---|
| `docs/FUNCTIONAL_SPEC.md` | What the system does, in plain language |
| `docs/DATA_MODEL.md` | Schema: tables, columns, constraints, and the reasoning behind them |
| `docs/API_CONTRACT.md` | Every endpoint: paths, payloads, error codes |
| `docs/BUILD_PLAN.md` | Phased build order and what is out of scope |

**Read `API_CONTRACT.md` before touching any endpoint, and `DATA_MODEL.md` before touching the schema.**
These files carry the reasoning for decisions that look wrong out of context — do not "fix" them without asking.

---

## Layout

```
/backend     Spring Boot (Maven)
/frontend    Angular
/docs        The four documents above
compose.dev.yaml    Dev database only
compose.yaml        Production: app + database
```

---

## Running

```bash
# Dev database (from repo root)
docker compose -f compose.dev.yaml up -d

# Backend  → localhost:8080
cd backend && mvn spring-boot:run

# Frontend → localhost:4200
cd frontend && npm start
```

In development the two run as separate processes and CORS allows `localhost:4200`.
In production Angular is built into the Spring Boot jar and served from the same origin — no CORS.

Dev database: `localhost:5432`, db/user `library`, password `library_dev_pw`.

---

## Working rules

**Claude Code is a reviewer and boilerplate generator — it does not write feature code unprompted.**
Explain, review, scaffold, and generate repetitive plumbing. When a task involves real business
logic, outline the approach and let Radu write it, unless he explicitly asks for the implementation.
He is using this project to prepare for Java developer interviews; code he did not write teaches
him nothing.

Other rules:

- **Contract first.** Changing an endpoint means editing `API_CONTRACT.md` first, then the code.
- **Both sides together.** A new endpoint and the Angular service method calling it ship in the same pass.
- **Ask, don't invent.** If a requirement is missing from the docs, ask rather than guessing.
- **Follow the phases** in `BUILD_PLAN.md`. Each phase must run before the next begins.
- **Explain as you go.** Prefer the conventional solution over the clever one, and say why a pattern is used.

---

## Conventions

**Backend**

- Layering: `controller → service → repository`. Business rules live in services, never in controllers or entities.
- **Entities never leave the service layer.** Controllers accept and return DTOs only.
- Flyway owns the schema; `ddl-auto=validate`. Never let Hibernate create or alter tables.
- Migrations are immutable once committed — add `V{n}__…`, never edit an applied file.
- Enums are persisted as strings (`@Enumerated(EnumType.STRING)`), never ordinals.
- Relationships are `FetchType.LAZY`.
- Validation via Bean Validation annotations on request DTOs.
- One `@RestControllerAdvice` produces every error response — see `API_CONTRACT.md` §3.
- Money is `BigDecimal`/`NUMERIC(10,2)`, never `double`.
- Timestamps are `TIMESTAMPTZ` in UTC.
- Constructor injection only — no field injection.
- Package by feature (`book`, `member`, `loan`, `category`, `parameter`), not by layer.

**Frontend**

- Feature modules mirroring the backend packages.
- One typed Angular service per feature owning all HTTP calls; components never call `HttpClient` directly.
- API base URL from `environment.ts` — never hardcoded.
- Angular Material components; avoid hand-rolled CSS where a Material component exists.
- All user-facing text goes through i18n keys. **No hardcoded strings**, in either language.
- Language preference lives in `localStorage`, never on the server.

---

## Things that look like bugs but are deliberate

Each is explained where it is defined; do not change them without asking.

- **No `due_at` column.** Due dates are computed from `borrowedAt + DAYS_TO_KEEP_A_BOOK` so that changing the lending period retroactively re-evaluates open loans, flipping them between Active and Overdue. Storing the date would break that. (`DATA_MODEL.md` §6)
- **`book_number` is unique only among active books.** Removing a book frees its number for reuse. Loans reference `book.id`, so history stays unambiguous — but any screen showing a number must also show title and author. (`DATA_MODEL.md` §4)
- **`book` uses a `status` enum, while `category` and `member` use a `deleted` boolean.** Books need to record *why* a copy left the collection; the other two do not. (`DATA_MODEL.md` §4.1)
- **Restore is explicit, never a side effect of update.** The previous system un-archived books silently on edit; that behaviour is not carried over. (`DATA_MODEL.md` §4.1)
- **The backend never returns translated text.** It returns stable error `code`s that the frontend maps to EN/RO. (`API_CONTRACT.md` §3)
- **`parameter` is key/value with a typed `SettingsService` in front.** Adding a setting is an INSERT, not a migration; the service is the only place that parses raw strings. (`DATA_MODEL.md` §8)
- **Removal is `POST /{id}/remove`, not `DELETE`.** A reason is mandatory and `DELETE` has no body semantics for it. (`API_CONTRACT.md` §5)

---

## Security

- No authentication in v1 until Phase 9. **Do not deploy to the public internet before it exists** — member names, addresses, phone numbers, and borrowing history would be world-readable.
- Credentials come from environment variables. Never commit real credentials; `.env` is git-ignored and `.env.example` documents the keys.
- In production the Postgres port is not published to the host.
