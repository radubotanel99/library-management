import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { PagedResponse } from '../../core/http/paged-response';
import { environment } from '../../../environments/environment';
import { LoanRequest, LoanResponse } from './loan.model';
import { LoanService } from './loan.service';

const LOANS_URL = `${environment.apiBaseUrl}/loans`;

const loan: LoanResponse = {
  id: 508,
  bookId: 41,
  bookTitle: 'Amintiri din copilărie',
  bookAuthor: 'Ion Creangă',
  bookNumber: 1201,
  memberId: 12,
  memberName: 'Popescu Ion',
  state: 'ACTIVE',
  borrowedAt: '2026-08-01T10:15:00Z',
  dueAt: '2026-08-15T10:15:00Z',
  daysOverdue: 0,
  finishedAt: null,
};

const page: PagedResponse<LoanResponse> = {
  content: [loan],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
};

describe('LoanService', () => {
  let service: LoanService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(LoanService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists loans without any query parameters when the criteria are empty', () => {
    let result: PagedResponse<LoanResponse> | undefined;
    service.list({}).subscribe((loans) => (result = loans));

    const request = http.expectOne(LOANS_URL);
    expect(request.request.method).toBe('GET');
    expect(request.request.params.keys()).toEqual([]);
    request.flush(page);

    expect(result).toEqual(page);
  });

  it('omits a blank search but keeps the populated parameters', () => {
    service
      .list({
        state: 'OPEN',
        memberId: 12,
        bookId: 41,
        search: '   ',
        page: 1,
        size: 5,
        sort: 'createdAt,desc',
      })
      .subscribe();

    const request = http.expectOne((req) => req.url === LOANS_URL);
    expect(request.request.params.has('search')).toBe(false);
    expect(request.request.params.get('state')).toBe('OPEN');
    expect(request.request.params.get('memberId')).toBe('12');
    expect(request.request.params.get('bookId')).toBe('41');
    expect(request.request.params.get('page')).toBe('1');
    expect(request.request.params.get('size')).toBe('5');
    expect(request.request.params.get('sort')).toBe('createdAt,desc');
    request.flush(page);
  });

  it('trims the search term before sending it', () => {
    service.list({ search: '  Creangă  ' }).subscribe();

    const request = http.expectOne((req) => req.url === LOANS_URL);
    expect(request.request.params.get('search')).toBe('Creangă');
    request.flush(page);
  });

  it('keeps page 0 rather than dropping it as falsy', () => {
    service.list({ page: 0, size: 20 }).subscribe();

    const request = http.expectOne((req) => req.url === LOANS_URL);
    expect(request.request.params.get('page')).toBe('0');
    request.flush(page);
  });

  it('drops a null state rather than sending "no filter" as an empty value', () => {
    service.list({ state: null, memberId: null, bookId: null }).subscribe();

    const request = http.expectOne((req) => req.url === LOANS_URL);
    expect(request.request.params.keys()).toEqual([]);
    request.flush(page);
  });

  it('gets a single loan by id', () => {
    let result: LoanResponse | undefined;
    service.get(508).subscribe((found) => (result = found));

    const request = http.expectOne(`${LOANS_URL}/508`);
    expect(request.request.method).toBe('GET');
    request.flush(loan);

    expect(result).toEqual(loan);
  });

  it('creates a loan from a book and a member id only', () => {
    // No borrow date in the payload: it is stamped server-side (`API_CONTRACT.md` §8).
    const payload: LoanRequest = { bookId: 41, memberId: 12 };
    let result: LoanResponse | undefined;
    service.create(payload).subscribe((created) => (result = created));

    const request = http.expectOne(LOANS_URL);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(payload);
    request.flush(loan);

    expect(result).toEqual(loan);
  });

  it('returns a loan by posting to the return sub-resource', () => {
    const finished: LoanResponse = {
      ...loan,
      state: 'FINISHED',
      finishedAt: '2026-08-10T09:00:00Z',
    };
    let result: LoanResponse | undefined;
    service.returnLoan(508).subscribe((returned) => (result = returned));

    // A POST to /{id}/return, not a PUT: returning is an action, not a
    // full-resource replacement (`API_CONTRACT.md` §8).
    const request = http.expectOne(`${LOANS_URL}/508/return`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({});
    request.flush(finished);

    expect(result?.state).toBe('FINISHED');
    expect(result?.finishedAt).toBe('2026-08-10T09:00:00Z');
  });
});
