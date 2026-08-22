/** Payloads for `/api/members` (`API_CONTRACT.md` §7). */

export interface MemberResponse {
  id: number;
  name: string;
  email: string | null;
  address: string | null;
  phoneNumber: string | null;
  /** Books the member currently holds, so the UI can block archiving before calling. */
  openLoanCount: number;
  createdAt: string;
}

/**
 * There is deliberately no `deleted` field: archiving is `DELETE /api/members/{id}`
 * and nothing else, so an edit can never silently un-archive a member.
 */
export interface MemberRequest {
  /** Required, max 150, unique among active members (case-insensitive). */
  name: string;
  /** Optional, valid format, max 255. */
  email?: string | null;
  /** Optional, max 500. */
  address?: string | null;
  /** Optional, max 50. Free text — `+40` prefixes and leading zeros are meaningful. */
  phoneNumber?: string | null;
}

/** Query parameters for `GET /api/members`; every one is optional. */
export interface MemberSearchCriteria {
  /** Case-insensitive substring of name, email, or phone number. */
  search?: string;
  /** Zero-based. */
  page?: number;
  size?: number;
  /** e.g. `name,asc`. */
  sort?: string;
}
