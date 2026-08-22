import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { PagedResponse } from '../../core/http/paged-response';
import { environment } from '../../../environments/environment';
import { MemberRequest, MemberResponse, MemberSearchCriteria } from './member.model';

/**
 * The only place in the member feature that touches `HttpClient`.
 *
 * No error handling here on purpose: `apiErrorInterceptor` normalises failures
 * into `ApiError`, and the components decide what to do with each code.
 */
@Injectable({ providedIn: 'root' })
export class MemberService {
  private readonly baseUrl = `${environment.apiBaseUrl}/members`;

  constructor(private readonly http: HttpClient) {}

  /** Active members only, paged (`API_CONTRACT.md` §7). */
  list(criteria: MemberSearchCriteria): Observable<PagedResponse<MemberResponse>> {
    return this.http.get<PagedResponse<MemberResponse>>(this.baseUrl, {
      params: this.buildListParams(criteria),
    });
  }

  /** Active members only: an archived member is reported as missing. */
  get(id: number): Observable<MemberResponse> {
    return this.http.get<MemberResponse>(`${this.baseUrl}/${id}`);
  }

  create(request: MemberRequest): Observable<MemberResponse> {
    return this.http.post<MemberResponse>(this.baseUrl, request);
  }

  update(id: number, request: MemberRequest): Observable<MemberResponse> {
    return this.http.put<MemberResponse>(`${this.baseUrl}/${id}`, request);
  }

  /** Archives the member; soft delete, and there is no restore endpoint by design. */
  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  private buildListParams(criteria: MemberSearchCriteria): HttpParams {
    let params = new HttpParams();
    // Each parameter is omitted rather than sent empty: `?page=` would reach the
    // backend as a bad int rather than as "not specified".
    const search = criteria.search?.trim();
    if (search) {
      params = params.set('search', search);
    }
    if (criteria.page != null) {
      params = params.set('page', criteria.page);
    }
    if (criteria.size != null) {
      params = params.set('size', criteria.size);
    }
    if (criteria.sort) {
      params = params.set('sort', criteria.sort);
    }
    return params;
  }
}
