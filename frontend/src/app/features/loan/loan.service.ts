import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { PagedResponse } from '../../core/http/paged-response';
import { environment } from '../../../environments/environment';
import { LoanRequest, LoanResponse, LoanSearchCriteria } from './loan.model';

/**
 * The only place in the loan feature that touches `HttpClient`.
 *
 * No error handling here on purpose: `apiErrorInterceptor` normalises failures
 * into `ApiError`, and the components decide what to do with each code.
 */
@Injectable({ providedIn: 'root' })
export class LoanService {
  private readonly baseUrl = `${environment.apiBaseUrl}/loans`;

  constructor(private readonly http: HttpClient) {}

  /** Paged, filtered by state/member/book/search (`API_CONTRACT.md` §8). */
  list(criteria: LoanSearchCriteria): Observable<PagedResponse<LoanResponse>> {
    return this.http.get<PagedResponse<LoanResponse>>(this.baseUrl, {
      params: this.buildListParams(criteria),
    });
  }

  get(id: number): Observable<LoanResponse> {
    return this.http.get<LoanResponse>(`${this.baseUrl}/${id}`);
  }

  create(request: LoanRequest): Observable<LoanResponse> {
    return this.http.post<LoanResponse>(this.baseUrl, request);
  }

  /**
   * Brings a copy back. A `POST` to a sub-resource rather than a `PUT`: it is an
   * action, not a full-resource replacement (`API_CONTRACT.md` §8).
   */
  returnLoan(id: number): Observable<LoanResponse> {
    return this.http.post<LoanResponse>(`${this.baseUrl}/${id}/return`, {});
  }

  private buildListParams(criteria: LoanSearchCriteria): HttpParams {
    let params = new HttpParams();
    // Each parameter is omitted rather than sent empty: `?memberId=` would reach
    // the backend as a bad Long rather than as "no filter".
    if (criteria.state) {
      params = params.set('state', criteria.state);
    }
    if (criteria.memberId != null) {
      params = params.set('memberId', criteria.memberId);
    }
    if (criteria.bookId != null) {
      params = params.set('bookId', criteria.bookId);
    }
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
