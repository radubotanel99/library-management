import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { DashboardResponse } from './dashboard.model';

/**
 * The only place in the dashboard feature that touches `HttpClient`.
 *
 * No error handling here on purpose: `apiErrorInterceptor` normalises failures
 * into `ApiError`, and the components decide what to do with each code.
 */
@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly baseUrl = `${environment.apiBaseUrl}/dashboard`;

  constructor(private readonly http: HttpClient) {}

  /**
   * One request for the whole screen: the dashboard renders as a unit, and four
   * separate calls would let its tiles disagree while they arrive
   * (`API_CONTRACT.md` §10).
   */
  load(): Observable<DashboardResponse> {
    return this.http.get<DashboardResponse>(this.baseUrl);
  }
}
