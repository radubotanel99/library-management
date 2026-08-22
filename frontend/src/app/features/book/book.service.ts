import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { PagedResponse } from '../../core/http/paged-response';
import { environment } from '../../../environments/environment';
import {
  BookArchiveSearchCriteria,
  BookRemoveRequest,
  BookRequest,
  BookResponse,
  BookRestoreRequest,
  BookSearchCriteria,
} from './book.model';

/**
 * The only place in the book feature that touches `HttpClient`.
 *
 * No error handling here on purpose: `apiErrorInterceptor` normalises failures
 * into `ApiError`, and the components decide what to do with each code.
 */
@Injectable({ providedIn: 'root' })
export class BookService {
  private readonly baseUrl = `${environment.apiBaseUrl}/books`;

  constructor(private readonly http: HttpClient) {}

  /** Active catalogue only, paged (`API_CONTRACT.md` §5). */
  list(criteria: BookSearchCriteria): Observable<PagedResponse<BookResponse>> {
    return this.http.get<PagedResponse<BookResponse>>(this.baseUrl, {
      params: this.buildListParams(criteria),
    });
  }

  /**
   * Everything not `ACTIVE`, paged (`API_CONTRACT.md` §5). Same query params as
   * {@link list}, plus `status` to narrow to a single removal reason.
   */
  archivedList(criteria: BookArchiveSearchCriteria): Observable<PagedResponse<BookResponse>> {
    let params = this.buildListParams(criteria);
    if (criteria.status) {
      params = params.set('status', criteria.status);
    }
    return this.http.get<PagedResponse<BookResponse>>(`${this.baseUrl}/archived`, { params });
  }

  /** Common query params shared by the active and archived listings. */
  private buildListParams(criteria: BookSearchCriteria): HttpParams {
    let params = new HttpParams();
    // Each parameter is omitted rather than sent empty: `?categoryId=` would
    // reach the backend as a bad Long rather than as "no filter".
    const search = criteria.search?.trim();
    if (search) {
      params = params.set('search', search);
    }
    if (criteria.categoryId != null) {
      params = params.set('categoryId', criteria.categoryId);
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

  /** Resolves a copy of any status, so an archived copy stays addressable. */
  get(id: number): Observable<BookResponse> {
    return this.http.get<BookResponse>(`${this.baseUrl}/${id}`);
  }

  /** Active copies only: an archived copy's number may have been reused. */
  getByNumber(bookNumber: number): Observable<BookResponse> {
    return this.http.get<BookResponse>(`${this.baseUrl}/by-number/${bookNumber}`);
  }

  create(request: BookRequest): Observable<BookResponse> {
    return this.http.post<BookResponse>(this.baseUrl, request);
  }

  update(id: number, request: BookRequest): Observable<BookResponse> {
    return this.http.put<BookResponse>(`${this.baseUrl}/${id}`, request);
  }

  /**
   * Takes a copy out of the collection. A `POST` to a sub-resource rather than a
   * `DELETE`: the reason is mandatory and `DELETE` has no body semantics for it.
   */
  remove(id: number, request: BookRemoveRequest): Observable<BookResponse> {
    return this.http.post<BookResponse>(`${this.baseUrl}/${id}/remove`, request);
  }

  /**
   * Puts a copy back into the collection. No body is required; an optional
   * `bookNumber` retries after a `BOOK_NUMBER_TAKEN_ON_RESTORE` collision without
   * a separate `PUT` (`API_CONTRACT.md` §5).
   */
  restore(id: number, request?: BookRestoreRequest): Observable<BookResponse> {
    return this.http.post<BookResponse>(`${this.baseUrl}/${id}/restore`, request ?? {});
  }
}
