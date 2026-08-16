/** Payloads for `/api/categories` (`API_CONTRACT.md` §6). */

export interface CategoryResponse {
  id: number;
  name: string;
  description: string | null;
  /** Number of **active** books, so the UI can block deletion before calling. */
  bookCount: number;
}

export interface CategoryRequest {
  /** Required, max 100, unique among active categories (case-insensitive). */
  name: string;
  /** Optional, max 500. */
  description?: string | null;
}
