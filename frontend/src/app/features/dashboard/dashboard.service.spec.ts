import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { DashboardResponse } from './dashboard.model';
import { DashboardService } from './dashboard.service';

const DASHBOARD_URL = `${environment.apiBaseUrl}/dashboard`;

const dashboard: DashboardResponse = {
  totalCopies: 137,
  totalMembers: 64,
  loansActive: 23,
  loansOverdue: 4,
  mostBorrowed: [
    { bookId: 41, title: 'Amintiri din copilărie', author: 'Ion Creangă', loanCount: 18 },
  ],
};

describe('DashboardService', () => {
  let service: DashboardService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(DashboardService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the whole screen in one GET', () => {
    let result: DashboardResponse | undefined;
    service.load().subscribe((response) => (result = response));

    const request = http.expectOne(DASHBOARD_URL);
    expect(request.request.method).toBe('GET');
    request.flush(dashboard);

    // Returned untouched: every figure is derived server-side, and nothing here
    // may recompute what is overdue from the browser clock.
    expect(result).toEqual(dashboard);
  });

  it('passes an empty most-borrowed list through as-is', () => {
    let result: DashboardResponse | undefined;
    service.load().subscribe((response) => (result = response));

    const empty: DashboardResponse = {
      totalCopies: 0,
      totalMembers: 0,
      loansActive: 0,
      loansOverdue: 0,
      mostBorrowed: [],
    };
    http.expectOne(DASHBOARD_URL).flush(empty);

    expect(result?.mostBorrowed).toEqual([]);
  });
});
