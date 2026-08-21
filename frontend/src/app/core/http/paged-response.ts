/**
 * The paged envelope every paged endpoint returns (`API_CONTRACT.md` §5).
 *
 * Lives in `core/` rather than in a feature model because members and loans page
 * identically. Note the field is `page`, not Spring's `number` — the backend maps
 * its `Page` onto this shape deliberately.
 */
export interface PagedResponse<T> {
  content: T[];
  /** Zero-based page index. */
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
