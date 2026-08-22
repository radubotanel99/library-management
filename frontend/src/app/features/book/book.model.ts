/** Payloads for `/api/books` (`API_CONTRACT.md` §5). */

/**
 * Why a copy is no longer on the shelf. `ACTIVE` copies are the catalogue; the
 * other three record *why* the copy left, which is why books use a status enum
 * where categories use a `deleted` flag (`DATA_MODEL.md` §4.1).
 */
export type BookStatus = 'ACTIVE' | 'LOST' | 'DAMAGED' | 'WITHDRAWN';

export interface BookResponse {
  id: number;
  title: string;
  author: string;
  /**
   * The number written on the copy. **Unique only among active books**, so every
   * screen showing it must also show `title` and `author` (`DATA_MODEL.md` §4).
   */
  bookNumber: number;
  categoryId: number;
  /** Denormalised so the catalogue table needs no second request. */
  categoryName: string;
  publisher: string | null;
  price: number | null;
  status: BookStatus;
  /** Why the copy left the collection; null while it is active. */
  removalNote: string | null;
  /** Derived server-side: does this copy have an `ACTIVE`/`LATE` loan? */
  onLoan: boolean;
  createdAt: string;
  /** Null until the row is actually edited. */
  updatedAt: string | null;
}

/**
 * There is deliberately no `status` field: status moves only through
 * `/remove` and `/restore`, so an edit can never silently un-archive a copy.
 */
export interface BookRequest {
  /** Required, max 255. */
  title: string;
  /** Required, max 255. */
  author: string;
  /** Required, positive, unique among active books. */
  bookNumber: number;
  /** Required, must reference an active category. */
  categoryId: number;
  /** Optional, max 255. */
  publisher?: string | null;
  /** Optional, >= 0. */
  price?: number | null;
}

/**
 * Why a copy is being removed. `ACTIVE` is excluded on purpose: removal always
 * records a reason, and restoring is its own endpoint (`API_CONTRACT.md` §5).
 */
export type RemovalReason = Exclude<BookStatus, 'ACTIVE'>;

/** Payload for `POST /api/books/{id}/remove`. */
export interface BookRemoveRequest {
  /** Required. Named `status` because that is the wire field. */
  status: RemovalReason;
  /** Optional, max 500. */
  removalNote?: string | null;
}

/** Query parameters for `GET /api/books`; every one is optional. */
export interface BookSearchCriteria {
  /** Case-insensitive match on title or author, or an exact book number. */
  search?: string;
  categoryId?: number | null;
  /** Zero-based. */
  page?: number;
  size?: number;
  /** e.g. `title,asc`. */
  sort?: string;
}
