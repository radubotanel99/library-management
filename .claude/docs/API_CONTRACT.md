# API Contract

Source of truth for every HTTP endpoint. **Both** the Spring Boot backend and the Angular frontend are built against this file.

Rule: to change an endpoint, edit this file **first**, then update backend and frontend to match. Never let one side drift.

---

## 1. Conventions

| Topic | Rule |
|---|---|
| Base path | All endpoints are prefixed `/api` |
| Format | JSON request and response bodies; `Content-Type: application/json` |
| Naming | Paths use plural nouns (`/api/books`); JSON fields use `camelCase` |
| IDs | `number` (maps to `BIGSERIAL`) |
| Dates | ISO-8601 UTC strings, e.g. `2026-08-14T09:30:00Z` — the frontend converts for display |
| Money | `number` with 2 decimals |
| Entities | **Never** returned directly — controllers return DTOs only |
| Validation | Bean Validation on request DTOs; failures return `400` (§3) |

### Development vs production

- **Dev:** backend at `http://localhost:8080`, Angular dev server at `http://localhost:4200`. CORS allows `localhost:4200`.
- **Prod:** Angular is built into the Spring Boot jar and served from the same origin. No CORS.

Angular stores the base URL in `environment.ts` / `environment.prod.ts` — never hardcoded in services.

---

## 2. HTTP status codes

| Code | Used for |
|---|---|
| `200 OK` | Successful GET, PUT, and state-changing POST (e.g. return a loan) |
| `201 Created` | Successful resource creation |
| `204 No Content` | Successful removal |
| `400 Bad Request` | Validation failure |
| `404 Not Found` | Unknown id |
| `409 Conflict` | Business rule violation (all codes in §4) |

`409` is used for *rule* violations — the request was well-formed but the operation is not allowed. `400` is for *malformed* input.

---

## 3. Error response

Every error, at every endpoint, returns this shape:

```json
{
  "code": "MEMBER_TOO_MANY_LOANS",
  "message": "Member has reached the maximum number of open loans",
  "field": null,
  "timestamp": "2026-08-14T09:30:00Z"
}
```

| Field | Notes |
|---|---|
| `code` | Stable machine-readable identifier — **the frontend keys translations off this** |
| `message` | English developer-facing text. **Never displayed to users.** |
| `field` | Field name for validation errors, otherwise `null` |
| `timestamp` | ISO-8601 UTC |

**The backend never returns translated text.** It returns a `code`; Angular maps it to an EN/RO message. This keeps all translation in the frontend i18n files (see `DATA_MODEL.md` §8).

Validation errors (`400`) return `code: "VALIDATION_ERROR"` with `field` populated.

Implemented once as a `@RestControllerAdvice` — not per-controller.

---

## 4. Business error codes

| Code | HTTP | Raised when |
|---|---|---|
| `BOOK_NUMBER_ALREADY_EXISTS` | 409 | Creating/updating a book with a number already used by an **active** book |
| `BOOK_ALREADY_ON_LOAN` | 409 | Lending a copy that has an open loan |
| `BOOK_HAS_OPEN_LOAN` | 409 | Removing a book that has an open loan |
| `BOOK_NOT_REMOVED` | 409 | Restoring a book whose status is already `ACTIVE` |
| `BOOK_NUMBER_TAKEN_ON_RESTORE` | 409 | Restoring a book whose number has since been taken by an active book |
| `BOOK_NOT_FOUND` | 404 | No book with the given id (or, for `by-number`, no **active** book with that number) |
| `MEMBER_TOO_MANY_LOANS` | 409 | Member already holds `MAX_BOOKS_PER_MEMBER` open loans |
| `MEMBER_NAME_ALREADY_EXISTS` | 409 | Duplicate active member name |
| `MEMBER_HAS_OPEN_LOANS` | 409 | Archiving a member with open loans |
| `MEMBER_NOT_FOUND` | 404 | No active member with the given id |
| `CATEGORY_NAME_ALREADY_EXISTS` | 409 | Duplicate active category name |
| `CATEGORY_HAS_BOOKS` | 409 | Archiving a category that still has active books |
| `CATEGORY_NOT_FOUND` | 404 | No active category with the given id (also raised restoring a book whose category has since been archived) |
| `LOAN_ALREADY_FINISHED` | 409 | Returning a loan already in `FINISHED` |
| `LOAN_NOT_FOUND` | 404 | No loan with the given id |
| `PARAMETER_INVALID_VALUE` | 400 | Parameter value not a positive integer |
| `VALIDATION_ERROR` | 400 | Bean Validation failure, unreadable JSON body, or a path/query parameter of the wrong type |
| `RESOURCE_NOT_FOUND` | 404 | No endpoint matches the request path (fallback — not a business rule) |
| `DATA_INTEGRITY_VIOLATION` | 409 | A database constraint rejected the write and no more specific code applies (fallback) |
| `INTERNAL_ERROR` | 500 | Unhandled server-side failure — the `message` is deliberately generic and never exposes internals |

Every error carries a code: the `@RestControllerAdvice` maps unexpected failures onto the three fallback codes above rather than letting a framework error page escape, so the frontend can key its translations off `code` alone and never has to branch on the HTTP status.

---

## 5. Books

`GET /api/books` — active catalogue only (`status = 'ACTIVE'`).

Query parameters (all optional, combinable):

| Param | Type | Notes |
|---|---|---|
| `search` | string | Case-insensitive match on title, author, or book number |
| `categoryId` | number | Filter by category |
| `page` | number | 0-based, default `0` |
| `size` | number | Default `20` |
| `sort` | string | e.g. `title,asc` |

`GET /api/books/archived` additionally accepts `status` (one of `LOST`/`DAMAGED`/`WITHDRAWN`) to filter by removal reason.

Response `200` — paged:

```json
{
  "content": [ { "...BookResponse": "" } ],
  "page": 0,
  "size": 20,
  "totalElements": 137,
  "totalPages": 7
}
```

### `BookResponse`

```json
{
  "id": 41,
  "title": "Amintiri din copilărie",
  "author": "Ion Creangă",
  "bookNumber": 1201,
  "categoryId": 3,
  "categoryName": "Fiction",
  "publisher": "Humanitas",
  "price": 29.90,
  "status": "ACTIVE",
  "removalNote": null,
  "onLoan": true,
  "createdAt": "2026-01-12T10:00:00Z",
  "updatedAt": null
}
```

`categoryName` is denormalised into the response so the catalogue table needs no second request.
`onLoan` is derived (does this copy have an `ACTIVE`/`LATE` loan?) so the UI can disable the Lend action without extra calls.

> **`bookNumber` is only unique among active books.** Any screen displaying it must also display `title` and `author` (see `DATA_MODEL.md` §4).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| `GET` | `/api/books/{id}` | — | `200` `BookResponse` | Any status |
| `GET` | `/api/books/by-number/{bookNumber}` | — | `200` `BookResponse` | Active books only; `404` otherwise |
| `GET` | `/api/books/archived` | — | `200` paged | Everything **not** `ACTIVE`; same query params as `/api/books` |
| `POST` | `/api/books` | `BookRequest` | `201` `BookResponse` | |
| `PUT` | `/api/books/{id}` | `BookRequest` | `200` `BookResponse` | **Never changes `status`** |
| `POST` | `/api/books/{id}/remove` | `BookRemoveRequest` | `200` `BookResponse` | Sets status + note |
| `POST` | `/api/books/{id}/restore` | `BookRestoreRequest?` (optional) | `200` `BookResponse` | Back to `ACTIVE`, clears note |

### `BookRequest`

```json
{
  "title": "Amintiri din copilărie",
  "author": "Ion Creangă",
  "bookNumber": 1201,
  "categoryId": 3,
  "publisher": "Humanitas",
  "price": 29.90
}
```

Validation: `title` required, max 255 · `author` required, max 255 · `bookNumber` required, positive · `categoryId` required, must exist · `publisher` optional, max 255 · `price` optional, ≥ 0.

There is deliberately **no `status` field** — status changes go through `/remove` and `/restore` only. This is why removal is a `POST` to a sub-resource rather than a `DELETE`: a reason is mandatory, and a plain `DELETE` has no body semantics for it.

### `BookRemoveRequest`

```json
{ "status": "LOST", "removalNote": "Not returned by member, written off" }
```

`status` required, one of `LOST` | `DAMAGED` | `WITHDRAWN` (**not** `ACTIVE`) · `removalNote` optional, max 500.

Errors: `BOOK_HAS_OPEN_LOAN` (409).

### Restore

Body (optional):

```json
{ "bookNumber": 1305 }
```

`bookNumber` optional, positive. Omit to restore with the book's existing number. Supply it to retry after a `BOOK_NUMBER_TAKEN_ON_RESTORE` collision — this replaces the number in the same call, no separate `PUT` needed.

Errors: `BOOK_NOT_REMOVED` (409) if already active; `CATEGORY_NOT_FOUND` (404) if the book's category has since been archived; `BOOK_NUMBER_TAKEN_ON_RESTORE` (409) if the number (existing or supplied) is now in use by an active book — the frontend prompts for a new number and retries `POST /restore` with `bookNumber` set.

---

## 6. Categories

| Method | Path | Body | Success | Errors |
|---|---|---|---|---|
| `GET` | `/api/categories` | — | `200` `CategoryResponse[]` | — |
| `GET` | `/api/categories/{id}` | — | `200` `CategoryResponse` | `CATEGORY_NOT_FOUND` |
| `POST` | `/api/categories` | `CategoryRequest` | `201` `CategoryResponse` | `CATEGORY_NAME_ALREADY_EXISTS` |
| `PUT` | `/api/categories/{id}` | `CategoryRequest` | `200` `CategoryResponse` | `CATEGORY_NAME_ALREADY_EXISTS`, `CATEGORY_NOT_FOUND` |
| `DELETE` | `/api/categories/{id}` | — | `204` no body | `CATEGORY_HAS_BOOKS`, `CATEGORY_NOT_FOUND` |

Returns a plain array, not paged — a library has few categories.

`POST` and `PUT` both return the saved `CategoryResponse` (`POST` also sets `Location: /api/categories/{id}`), so the frontend can update its table from the response instead of re-fetching the list.

Only **active** categories are addressable: an archived one is `CATEGORY_NOT_FOUND` on every endpoint. There is no restore — categories archived in error are re-created (`DATA_MODEL.md` §4.1).

### `CategoryResponse`

```json
{ "id": 3, "name": "Fiction", "description": "Novels and short stories", "bookCount": 42 }
```

`bookCount` counts **active** books, so the UI can warn before archiving.

`CategoryRequest`: `name` required, max 100, unique among active (case-insensitive) · `description` optional, max 500.

---

## 7. Members

| Method | Path | Body | Success | Errors |
|---|---|---|---|---|
| `GET` | `/api/members` | — | `200` paged | Params: `search`, `page`, `size`, `sort` |
| `GET` | `/api/members/{id}` | — | `200` | `404` |
| `POST` | `/api/members` | `MemberRequest` | `201` | `MEMBER_NAME_ALREADY_EXISTS` |
| `PUT` | `/api/members/{id}` | `MemberRequest` | `200` | `MEMBER_NAME_ALREADY_EXISTS` |
| `DELETE` | `/api/members/{id}` | — | `204` | `MEMBER_HAS_OPEN_LOANS` |

```json
{
  "id": 12,
  "name": "Popescu Ion",
  "email": "ion.popescu@example.ro",
  "address": "Str. Victoriei 14, Timișoara",
  "phoneNumber": "+40 721 000 000",
  "openLoanCount": 2,
  "createdAt": "2026-02-01T08:00:00Z"
}
```

`openLoanCount` lets the UI show remaining allowance without a second call.

`MemberRequest`: `name` required, max 150, unique among active (case-insensitive) · `email` optional, valid format, max 255 · `address` optional, max 500 · `phoneNumber` optional, max 50 (string — leading zeros and `+40`).

`search` matches name, email, or phone number.

---

## 8. Loans

| Method | Path | Body | Success | Errors |
|---|---|---|---|---|
| `GET` | `/api/loans` | — | `200` paged | Params: `state`, `memberId`, `bookId`, `search`, `page`, `size`, `sort` |
| `GET` | `/api/loans/{id}` | — | `200` | `404` |
| `POST` | `/api/loans` | `LoanRequest` | `201` | `BOOK_NOT_FOUND`, `MEMBER_NOT_FOUND`, `BOOK_ALREADY_ON_LOAN`, `MEMBER_TOO_MANY_LOANS` |
| `POST` | `/api/loans/{id}/return` | — | `200` | `LOAN_ALREADY_FINISHED` |

`state` accepts `ACTIVE`, `LATE`, `FINISHED`, or `OPEN` (a convenience alias for `ACTIVE` + `LATE`).

`search` matches book title, book author, book number (exact), or member name.

### `LoanResponse`

```json
{
  "id": 508,
  "bookId": 41,
  "bookTitle": "Amintiri din copilărie",
  "bookAuthor": "Ion Creangă",
  "bookNumber": 1201,
  "memberId": 12,
  "memberName": "Popescu Ion",
  "state": "ACTIVE",
  "borrowedAt": "2026-08-01T10:15:00Z",
  "dueAt": "2026-08-15T10:15:00Z",
  "daysOverdue": 0,
  "finishedAt": null
}
```

Book and member details are denormalised so the loans table renders in one request.

**`dueAt` is computed, not stored** — `borrowedAt + DAYS_TO_KEEP_A_BOOK`. It is returned for display only. Because it is derived, changing the parameter re-dates every open loan (see `DATA_MODEL.md` §6).

`daysOverdue` is `0` unless the loan is `LATE`, in which case it is clamped to `[1, 365]` — a loan minutes past due still counts as at least 1 day, and an ancient loan does not produce an absurd number.

### `LoanRequest`

```json
{ "bookId": 41, "memberId": 12 }
```

Both required and must exist. The book must be `ACTIVE` and not on loan (`BOOK_NOT_FOUND` if missing, archived, or otherwise non-`ACTIVE` — an archived copy is treated as not existing, the same rule `PUT`/`GET /api/books/{id}` already apply); the member must not be archived (`MEMBER_NOT_FOUND`) and must be under `MAX_BOOKS_PER_MEMBER`.

The frontend restricts the member field to a picker over active members only — free-text member IDs are never accepted — so `MEMBER_NOT_FOUND` on this endpoint is a defense against a stale picker, not a normal user path.

The borrow date is set server-side — **never** supplied by the client.

### Return

`POST /api/loans/{id}/return` sets `state = FINISHED` and stamps `finishedAt`. A `POST` to a sub-resource rather than a `PUT`, because it is an action, not a full-resource replacement.

---

## 9. Parameters

| Method | Path | Body | Success | Errors |
|---|---|---|---|---|
| `GET` | `/api/parameters` | — | `200` `ParameterResponse[]` | — |
| `PUT` | `/api/parameters` | `ParameterRequest[]` | `200` `ParameterResponse[]` | `PARAMETER_INVALID_VALUE` |

```json
[
  { "key": "DAYS_TO_KEEP_A_BOOK", "value": "14", "updatedAt": "2026-08-01T09:00:00Z" },
  { "key": "MAX_BOOKS_PER_MEMBER", "value": "3", "updatedAt": null }
]
```

`PUT` accepts the full set and saves them in **one transaction** — a partial save could leave the library in a half-configured state. The request must contain exactly the known parameter keys (`DAYS_TO_KEEP_A_BOOK`, `MAX_BOOKS_PER_MEMBER`) — a missing key, an unrecognised key, or a duplicate key is rejected with `PARAMETER_INVALID_VALUE` rather than partially applied.

Values are strings on the wire (matching storage); the backend validates each as a positive integer (no upper bound) and rejects the whole request otherwise. On `PARAMETER_INVALID_VALUE`, `field` is the offending parameter's `key` (e.g. `"DAYS_TO_KEEP_A_BOOK"`) rather than a JSON path, since the request body is an array — this lets the frontend highlight the right input.

**Saving parameters immediately triggers loan re-evaluation** (§11) **in the same transaction** — if re-evaluation fails, the parameter save rolls back too, so settings and loan state can never disagree.

Language is **not** a parameter — it lives in browser `localStorage`.

---

## 10. Dashboard

`GET /api/dashboard` — everything the home screen needs in one request.

```json
{
  "totalCopies": 137,
  "totalTitles": 118,
  "totalMembers": 64,
  "loansActive": 23,
  "loansOverdue": 4,
  "booksLost": 3,
  "booksDamaged": 2,
  "booksWithdrawn": 7,
  "mostBorrowed": [
    { "bookId": 41, "title": "Amintiri din copilărie", "author": "Ion Creangă", "loanCount": 18 }
  ]
}
```

- `totalCopies` counts `ACTIVE` books only — a lost book is not held.
- `totalTitles` counts distinct `(title, author)` pairs among active books. **An approximation** — inconsistent data entry inflates it (`DATA_MODEL.md` §11).
- `mostBorrowed` returns the top 5 by all-time loan count.

One endpoint rather than several, because the dashboard renders as a unit.

---

## 11. Overdue re-evaluation (no endpoint)

A scheduled backend job compares each open loan's `created_at` against `DAYS_TO_KEEP_A_BOOK`:

- `ACTIVE` → `LATE` once the period has passed
- `LATE` → `ACTIVE` if the period is raised and the loan is no longer late

It runs on a schedule (daily) **and** immediately after `PUT /api/parameters`. There is no public endpoint — it is internal behaviour, listed here so the frontend knows loan states can change without user action.

---

## 12. Not in v1

Recorded so the shape is known; **not built now**:

- **Authentication** — `POST /api/auth/login`, plus a bearer token on every request. The last v1 item and a prerequisite for public internet deployment. Adding it is additive: every endpoint above keeps its path and shape.
- **Excel export** — `GET /api/books/export`, etc.
- **Member self-service**, **email reminders**, **reservations** — see Functional Spec §15.
