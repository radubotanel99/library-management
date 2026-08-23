import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ParameterRequest, ParameterResponse } from './settings.model';

/**
 * The only place in the settings feature that touches `HttpClient`.
 *
 * No error handling here on purpose: `apiErrorInterceptor` normalises failures
 * into `ApiError`, and the components decide what to do with each code.
 */
@Injectable({ providedIn: 'root' })
export class SettingsService {
  private readonly baseUrl = `${environment.apiBaseUrl}/parameters`;

  constructor(private readonly http: HttpClient) {}

  /** Plain array, not paged — there are only a handful of parameters (`API_CONTRACT.md` §9). */
  list(): Observable<ParameterResponse[]> {
    return this.http.get<ParameterResponse[]>(this.baseUrl);
  }

  /**
   * Saves the full set in one transaction: a partial save would leave the
   * library half-configured, so the request must carry every known key
   * (`API_CONTRACT.md` §9).
   */
  save(requests: ParameterRequest[]): Observable<ParameterResponse[]> {
    return this.http.put<ParameterResponse[]>(this.baseUrl, requests);
  }
}
