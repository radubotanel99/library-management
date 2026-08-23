/** Payloads for `/api/parameters` (`API_CONTRACT.md` §9). */

/**
 * The parameters the settings screen knows about. `PUT` must carry exactly
 * these two — a missing, unknown or duplicated key is rejected outright rather
 * than partially applied (`API_CONTRACT.md` §9).
 */
export type ParameterKey = 'DAYS_TO_KEEP_A_BOOK' | 'MAX_BOOKS_PER_MEMBER';

export interface ParameterResponse {
  /** Also what the backend puts in `ApiError.field` when it rejects a value. */
  key: string;
  /**
   * A string on the wire, matching how the row is stored: `parameter` is a
   * key/value table (`DATA_MODEL.md` §8). The backend validates it as a
   * positive integer with no upper bound — parse it, never trust the type.
   */
  value: string;
  /** Null until someone has saved this parameter at least once. */
  updatedAt: string | null;
}

/** `updatedAt` is stamped server-side, so it is not part of the request. */
export interface ParameterRequest {
  key: ParameterKey;
  /** Sent as a string for the same reason it comes back as one. */
  value: string;
}
