/** Payloads for `/api/loans` (`API_CONTRACT.md` §8). */

/**
 * Where a loan stands. Computed server-side and re-evaluated by a scheduled job
 * (`API_CONTRACT.md` §11), so it can change without any user action — never
 * derive it in the browser.
 */
export type LoanState = 'ACTIVE' | 'LATE' | 'FINISHED';

/**
 * What the `state` query parameter accepts: the three real states plus `OPEN`,
 * a convenience alias for `ACTIVE` + `LATE` — the working view of the screen.
 */
export type LoanStateFilter = LoanState | 'OPEN';

export interface LoanResponse {
  id: number;
  bookId: number;
  /** Book and member details are denormalised so the table renders in one request. */
  bookTitle: string;
  bookAuthor: string;
  /**
   * Unique only among active books (`DATA_MODEL.md` §4), so wherever it is shown
   * `bookTitle` and `bookAuthor` must be shown with it.
   */
  bookNumber: number;
  memberId: number;
  memberName: string;
  state: LoanState;
  borrowedAt: string;
  /**
   * `borrowedAt + DAYS_TO_KEEP_A_BOOK`, computed server-side and returned for
   * display only — there is no `due_at` column (`DATA_MODEL.md` §6).
   */
  dueAt: string;
  /**
   * `0` unless the loan is `LATE`, in which case the server clamps it to
   * `[1, 365]`. Displayed verbatim: a wrong client clock must never invent it.
   */
  daysOverdue: number;
  /** Null until the copy comes back. */
  finishedAt: string | null;
}

/**
 * There is deliberately no borrow date: it is stamped server-side, never sent
 * by the client (`API_CONTRACT.md` §8).
 */
export interface LoanRequest {
  /** Required. Must be an `ACTIVE` copy that is not already on loan. */
  bookId: number;
  /** Required. Must be an active member under `MAX_BOOKS_PER_MEMBER`. */
  memberId: number;
}

/** Query parameters for `GET /api/loans`; every one is optional. */
export interface LoanSearchCriteria {
  state?: LoanStateFilter | null;
  memberId?: number | null;
  bookId?: number | null;
  /** Book title, book author, book number (exact), or member name. */
  search?: string;
  /** Zero-based. */
  page?: number;
  size?: number;
  /** e.g. `createdAt,desc`. */
  sort?: string;
}
