import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { PagedResponse } from '../../core/http/paged-response';
import { environment } from '../../../environments/environment';
import { BookRemoveRequest, BookRequest, BookResponse, BookSearchCriteria } from './book.model';

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
    return this.http.get<PagedResponse<BookResponse>>(this.baseUrl, { params });
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
}
