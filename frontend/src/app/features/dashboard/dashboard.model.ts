/** Payload for `/api/dashboard` (`API_CONTRACT.md` §10). */

/**
 * One row of the most-borrowed list.
 *
 * Title and author rather than a book number: the number is unique only among
 * active copies (`DATA_MODEL.md` §4), so it never names a book on its own.
 */
export interface MostBorrowedBookResponse {
  bookId: number;
  title: string;
  author: string;
  /** Loans ever recorded against this copy — returned ones count too. */
  loanCount: number;
}

export interface DashboardResponse {
  /** Copies actually held: active books only, so a lost one is not counted. */
  totalCopies: number;
  totalMembers: number;
  /**
   * Derived server-side from `borrowedAt + DAYS_TO_KEEP_A_BOOK`, not read off the
   * stored `loan.state` column — so it agrees with the loans screen and reacts to
   * a settings change at once (`API_CONTRACT.md` §10). Never recomputed here: the
   * browser clock is not the authority on what is overdue.
   */
  loansActive: number;
  loansOverdue: number;
  /** Top 5 by all-time loan count; empty for a library that has lent nothing yet. */
  mostBorrowed: MostBorrowedBookResponse[];
}
