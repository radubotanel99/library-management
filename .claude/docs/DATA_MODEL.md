# Data Model

Source of truth for the database schema of the Library Management System.
Any schema change starts by editing this file, then implementing the migration.

- **Database:** PostgreSQL 16
- **Schema management:** Flyway (versioned SQL migrations under `backend/src/main/resources/db/migration`)
- **JPA:** Hibernate maps entities to these tables. `ddl-auto` is **`validate`** — Hibernate never creates or alters tables; Flyway owns the schema.

---

## 1. Conventions

| Topic | Rule |
|---|---|
| Table / column names | `snake_case`, singular table names (`book`, not `books`) |
| Java entity names | `PascalCase` singular (`Book`, `Loan`) |
| Primary keys | `id BIGSERIAL PRIMARY KEY` on every table (except `parameter`) |
| Timestamps | `TIMESTAMPTZ`, always stored in **UTC**; converted for display in the frontend |
| Booleans | `NOT NULL DEFAULT FALSE` |
| Enums | Stored as `VARCHAR`, never as an integer ordinal (see §7) |
| Money | `NUMERIC(10,2)` — never `float`/`double` |
| Soft delete | `deleted BOOLEAN` on master data (see §2) |
| Foreign keys | Always declared with a real FK constraint |

### Timestamp columns

- `created_at` — set once on insert, never changed.
- `updated_at` — set on every update (null until first update).

Both are managed by JPA auditing (`@CreatedDate` / `@LastModifiedDate`), not by database triggers.

---

## 2. Soft delete

`book`, `category`, and `member` are **never physically deleted**.

- `category` and `member` use a `deleted BOOLEAN` flag.
- `book` uses a **`status` enum** instead (§4.1) — it needs to record *why* a copy left the collection, which a boolean cannot express. There is no `deleted` column on `book`; "active" means `status = 'ACTIVE'`.
This preserves loan history: an archived book that was borrowed in 2019 must still resolve on that old loan record.

Rules:

- All normal list/search queries filter `WHERE deleted = false`.
- Archived records remain reachable through dedicated "archived" views and through historical loans.
- `loan` has **no** `deleted` column — a loan's lifecycle is expressed by `state` (§7). Loans are permanent history.
- Uniqueness constraints on soft-deleted tables use **partial unique indexes** scoped to `deleted = false`, so a name freed by archiving can be reused (see §3, §5).

---

## 3. `category`

A subject grouping for books.

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `BIGSERIAL` | PK | |
| `name` | `VARCHAR(100)` | NOT NULL, unique among active | Partial unique index |
| `description` | `VARCHAR(500)` | NULL | |
| `created_at` | `TIMESTAMPTZ` | NOT NULL | |
| `updated_at` | `TIMESTAMPTZ` | NULL | |
| `deleted` | `BOOLEAN` | NOT NULL DEFAULT FALSE | |

```sql
CREATE UNIQUE INDEX ux_category_name_active
  ON category (lower(name)) WHERE deleted = false;
```

`lower(name)` makes the uniqueness case-insensitive, so "Fiction" and "fiction" collide.

**Business rule:** a category with at least one active book (`book.status = 'ACTIVE'`) cannot be archived (enforced in the service layer, not the DB).

---

## 4. `book`

**One row = one physical copy on the shelf.** Two copies of the same title are two rows with two different `book_number` values. There is no separate "title" entity.

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `BIGSERIAL` | PK | Internal surrogate key |
| `title` | `VARCHAR(255)` | NOT NULL | |
| `author` | `VARCHAR(255)` | NOT NULL | |
| `book_number` | `INTEGER` | NOT NULL, **unique among active books only** | The number physically written on the copy |
| `category_id` | `BIGINT` | NOT NULL, FK → `category(id)` | |
| `publisher` | `VARCHAR(255)` | NULL | |
| `price` | `NUMERIC(10,2)` | NULL | |
| `status` | `VARCHAR(20)` | NOT NULL DEFAULT `'ACTIVE'` | `ACTIVE` \| `LOST` \| `DAMAGED` \| `WITHDRAWN` — see §4.1 |
| `removal_note` | `VARCHAR(500)` | NULL | Free-text detail captured when removed |
| `created_at` | `TIMESTAMPTZ` | NOT NULL | |
| `updated_at` | `TIMESTAMPTZ` | NULL | |

```sql
ALTER TABLE book ADD CONSTRAINT ck_book_status
  CHECK (status IN ('ACTIVE', 'LOST', 'DAMAGED', 'WITHDRAWN'));

CREATE UNIQUE INDEX ux_book_number_active
  ON book (book_number) WHERE status = 'ACTIVE';

CREATE INDEX ix_book_category ON book (category_id);
CREATE INDEX ix_book_status ON book (status);
CREATE INDEX ix_book_title ON book (lower(title));
CREATE INDEX ix_book_author ON book (lower(author));
```

**`book_number` is unique among *active* books only** — removing a book frees its number for reuse.

Rationale: the number is a physical label written on the copy. When a book is lost, damaged, or withdrawn, that number is genuinely available again, and refusing to reuse it leaves permanent gaps in a hand-numbered sequence for no benefit.

This does **not** make loan history ambiguous. Loans reference `book.id` (the surrogate key), never `book_number`. An old loan on archived book `id=57` (number 1234) and a new loan on book `id=890` (also number 1234) are distinct, unambiguous rows. The only risk is a human misreading a screen that shows a bare number — so **any UI displaying `book_number` must also display title and author.**

The existing production database may already contain an archived and an active book sharing a number; a global unique constraint would fail on import.

### 4.1 Book status — removal and restore

`book` uses a **status enum instead of a `deleted` boolean**. A boolean records *that* a book left the collection but throws away *why*, and "we lost 12 books" versus "we retired 12 worn-out books" are materially different facts to a librarian.

| Status | Meaning |
|---|---|
| `ACTIVE` | In the collection, may be lent |
| `LOST` | Missing / not returned and written off |
| `DAMAGED` | Physically unusable |
| `WITHDRAWN` | Deliberately retired from the collection |

Rules:

- Only `ACTIVE` books appear in the catalogue and may be lent. Everything else appears on the **archive screen**, which shows the status and note.
- Removal requires a **reason** (`LOST` / `DAMAGED` / `WITHDRAWN`) and accepts an optional `removal_note`. `ACTIVE` is never offered as a removal reason.
- A book with an open loan (`ACTIVE` or `LATE`) cannot be removed.
- Dashboard "copies held" counts **only `ACTIVE`** books. A lost book is not held.

**Restore** returns a removed book to `ACTIVE`:

- Restore is an **explicit, separate action** — never a side effect of editing. *(The previous system set `deleted = false` inside its update method, so editing an archived book silently un-archived it. That behaviour is not carried over.)*
- If another active book has taken the same `book_number` in the meantime, `ux_book_number_active` rejects the restore. The application must catch that specific collision and prompt the user for a new number rather than surfacing a database error.
- Restore clears `removal_note`.

Restore applies to **books only**. Categories and members have no restore function (matching the existing system); if one is archived in error it is re-created.

### 4.2 Status values are an enum, not a lookup table

The status list lives in three places: a Java enum (`BookStatus`), the `ck_book_status` check constraint, and frontend translation keys (`book.status.LOST` → EN/RO).

Adding a reason later is a small code change — extend the enum, add a migration updating the check constraint, add two translation entries — but it **requires a release**, not a client-side toggle. That is the deliberate trade-off:

- A client-editable lookup table would arrive as untranslated free text, breaking the rule that all translation lives in frontend files.
- `ACTIVE` carries behaviour (only active books can be lent), so the list is not inert data.
- The list changes rarely; `removal_note` already absorbs most "we need to record something else" requests without a release.



---

## 5. `member`

A person entitled to borrow. (Called "User" in the previous system; renamed — see §10.)

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `BIGSERIAL` | PK | |
| `name` | `VARCHAR(150)` | NOT NULL, unique among active | Partial unique index |
| `email` | `VARCHAR(255)` | NULL | |
| `address` | `VARCHAR(500)` | NULL | |
| `phone_number` | `VARCHAR(50)` | NULL | `VARCHAR`, never numeric — leading zeros, `+40`, spacing |
| `created_at` | `TIMESTAMPTZ` | NOT NULL | |
| `updated_at` | `TIMESTAMPTZ` | NULL | |
| `deleted` | `BOOLEAN` | NOT NULL DEFAULT FALSE | |

```sql
CREATE UNIQUE INDEX ux_member_name_active
  ON member (lower(name)) WHERE deleted = false;
```

**Business rule:** a member holding an open loan cannot be archived.

---

## 6. `loan`

One book copy borrowed by one member. Permanent history — never deleted.

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `BIGSERIAL` | PK | |
| `book_id` | `BIGINT` | NOT NULL, FK → `book(id)` | |
| `member_id` | `BIGINT` | NOT NULL, FK → `member(id)` | |
| `state` | `VARCHAR(20)` | NOT NULL | `ACTIVE` \| `LATE` \| `FINISHED` |
| `created_at` | `TIMESTAMPTZ` | NOT NULL | The borrow date — drives the overdue calculation |
| `updated_at` | `TIMESTAMPTZ` | NULL | |
| `finished_at` | `TIMESTAMPTZ` | NULL | Set when returned; null while open |

```sql
CREATE UNIQUE INDEX ux_loan_one_open_per_book
  ON loan (book_id) WHERE state IN ('ACTIVE', 'LATE');

CREATE INDEX ix_loan_member ON loan (member_id);
CREATE INDEX ix_loan_state ON loan (state);
CREATE INDEX ix_loan_book ON loan (book_id);
```

`ux_loan_one_open_per_book` enforces **"one open loan per physical copy" at the database level**, not just in Java. Even a race condition or a direct SQL insert cannot double-lend a copy.

### No stored due date — deliberate

There is intentionally **no `due_at` column** (this matches the previous system, which also derived it). A loan is overdue when
`created_at + parameter(DAYS_TO_KEEP_A_BOOK) < now()`.

Because the due date is *derived* rather than frozen at borrow time, changing the lending period retroactively re-evaluates existing loans — raising it from 14 to 21 days flips affected loans back from `LATE` to `ACTIVE`. That behaviour is required (see Functional Spec, "Automatic correction").

*Trade-off:* the library cannot grant a per-loan extension without changing the rule for everyone. If per-loan due dates are ever needed, add a nullable `due_at_override` column — the derived rule stays the default.

---

## 7. Enum storage

`loan.state` and `book.status` are stored as **strings**, mapped in JPA with `@Enumerated(EnumType.STRING)`.

Never `EnumType.ORDINAL`: ordinals store `0`, `1`, `2`, so reordering the Java enum silently corrupts every existing row. Strings are also readable in DataGrip and in raw SQL.

Enforced with a check constraint:

```sql
ALTER TABLE loan ADD CONSTRAINT ck_loan_state
  CHECK (state IN ('ACTIVE', 'LATE', 'FINISHED'));
```

---

## 8. `parameter`

Library-wide business settings, editable by staff at runtime. Key/value so a new setting is an `INSERT`, not a migration.

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `key` | `VARCHAR(100)` | **PK** | Natural key — no surrogate `id` |
| `value` | `VARCHAR(255)` | NOT NULL | Always stored as text |
| `updated_at` | `TIMESTAMPTZ` | NULL | |

### Seeded values (migration `V2__seed_parameters.sql`)

| Key | Value | Meaning |
|---|---|---|
| `DAYS_TO_KEEP_A_BOOK` | `14` | Lending period in days |
| `MAX_BOOKS_PER_MEMBER` | `3` | Max open loans per member |

### Typed access layer — required

Values are text in the database, so the rest of the application must **never** read them raw. A single `SettingsService` owns parsing:

- exposes typed accessors: `getDaysToKeepABook(): int`, `getMaxBooksPerMember(): int`
- validates on write (must be a positive integer) and rejects bad input
- falls back to a hardcoded default if a key is missing, and logs a warning
- is the only class that touches the `parameter` repository

This contains the one real weakness of key/value storage (no type safety) in exactly one place.

**Not cached.** Reading two rows is sub-millisecond and Postgres/JPA already cache within a transaction. If it ever measurably matters, add Spring's `@Cacheable` with eviction on save.

### What does *not* belong here

- **Language / UI preferences** → browser `localStorage`, frontend only. Per-user, not per-library; two staff must be able to use different languages simultaneously.
- **Infrastructure config** (DB URL, credentials, ports) → environment variables via `application.yml`. Set per deployment by the developer, never at runtime by a user.

---

## 9. Relationships

```
category ──1───∞── book ──1───∞── loan ──∞───1── member

parameter   (standalone key/value, no relationships)
```

- A **category** has many **books**; a book has exactly one category (`NOT NULL`).
- A **book** has many **loans** over its lifetime, but at most one open (`ACTIVE`/`LATE`) at a time.
- A **member** has many **loans**, capped at `MAX_BOOKS_PER_MEMBER` open at once.

All relationships are mapped **lazily** (`FetchType.LAZY`) in JPA. Entities are never returned directly from controllers — DTOs only (see `API_CONTRACT.md`).

---

## 10. Renamed from the previous system

The domain is unchanged; two names were updated to standard library terminology (and to drop a reserved-word workaround). Relevant when migrating existing data.

| Previous | New | Reason |
|---|---|---|
| `MY_USER` table | `member` | `USER` is reserved in Postgres, forcing the `MY_` prefix; `member` is not reserved and reads better |
| `RENT` table | `loan` | Standard library terminology; matches the client-facing spec |
| `RentState.FINISHED` etc. | unchanged | Same three states |
| `MaxBooksPerUser` (json) | `MAX_BOOKS_PER_MEMBER` (db row) | Moved from file to database |
| `DaysToKeepABook` (json) | `DAYS_TO_KEEP_A_BOOK` (db row) | Moved from file to database |
| `BOOK.deleted` (boolean) | `book.status` (enum) | Records *why* a copy left the collection |

Data migration from the existing Oracle database is a **one-time column-mapped copy**; no structural conversion is required.

One value conversion is needed: `BOOK.deleted = 0` maps to `status = 'ACTIVE'`, and `BOOK.deleted = 1` maps to `status = 'WITHDRAWN'` — a safe generic default, since the old data does not record a reason.

---

## 11. Migrations

| File | Contents |
|---|---|
| `V1__init_schema.sql` | All five tables, constraints, indexes |
| `V2__seed_parameters.sql` | The two seed rows in `parameter` |

Rules:

- Migrations are **immutable once committed** — never edit an applied file; add `V3__…` instead.
- Every schema change ships as a new numbered migration.
- `spring.jpa.hibernate.ddl-auto=validate` guarantees the entities and the migrated schema agree at startup; a mismatch fails fast rather than silently.

---

## 12. Deferred — not in v1

Recorded so the shape is known, but **not built now**:

- **`staff_user` table** (username, password hash, role) — arrives with authentication, which is the last v1 item and a prerequisite for public internet deployment.
- **`member.max_books_override`** (nullable) — per-member borrowing limit; the global parameter stays the default.
- **`loan.due_at_override`** (nullable) — per-loan extension.
- **Reservations**, **notification log** — see Functional Spec §15.
- **Distinct-title count and lost/damaged/withdrawn breakdown on the dashboard** — `totalTitles` required an approximate `(title, author)` grouping with no clean fix short of a `title` entity; the removed-book breakdown is already visible on the archive screen. Both dropped from `GET /api/dashboard` to keep the endpoint to figures the archive/catalogue screens don't already cover. Add back if requested.

Each is additive: a new nullable column or a new table, with no restructuring of what is built now.
