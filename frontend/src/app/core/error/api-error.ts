/**
 * The single error shape every backend endpoint returns (`API_CONTRACT.md` §3).
 *
 * `code` is the stable machine key the frontend keys its translations off.
 * `message` is English developer text and is never shown to a user.
 * `field` is populated for `VALIDATION_ERROR` and `CATEGORY_NAME_ALREADY_EXISTS`,
 * otherwise `null`.
 */
export interface ApiError {
  code: string;
  message: string;
  field: string | null;
  timestamp: string;
}

/**
 * Frontend-only pseudo code for "no response body at all" — the request never
 * reached the backend, so there is no `code` to map.
 */
export const NETWORK_ERROR_CODE = 'NETWORK_ERROR';

export function isApiError(value: unknown): value is ApiError {
  if (value === null || typeof value !== 'object') {
    return false;
  }
  const candidate = value as Partial<ApiError>;
  return typeof candidate.code === 'string' && typeof candidate.message === 'string';
}

export function networkError(): ApiError {
  return {
    code: NETWORK_ERROR_CODE,
    message: 'Network error',
    field: null,
    timestamp: new Date().toISOString(),
  };
}
