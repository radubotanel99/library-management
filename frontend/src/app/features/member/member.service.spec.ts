import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { PagedResponse } from '../../core/http/paged-response';
import { environment } from '../../../environments/environment';
import { MemberRequest, MemberResponse } from './member.model';
import { MemberService } from './member.service';

const MEMBERS_URL = `${environment.apiBaseUrl}/members`;

const popescu: MemberResponse = {
  id: 12,
  name: 'Popescu Ion',
  email: 'ion.popescu@example.ro',
  address: 'Str. Victoriei 14, Timișoara',
  phoneNumber: '+40 721 000 000',
  openLoanCount: 2,
  createdAt: '2026-02-01T08:00:00Z',
};

const page: PagedResponse<MemberResponse> = {
  content: [popescu],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
};

describe('MemberService', () => {
  let service: MemberService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(MemberService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists members without any query parameters when the criteria are empty', () => {
    let result: PagedResponse<MemberResponse> | undefined;
    service.list({}).subscribe((members) => (result = members));

    const request = http.expectOne(MEMBERS_URL);
    expect(request.request.method).toBe('GET');
    expect(request.request.params.keys()).toEqual([]);
    request.flush(page);

    expect(result).toEqual(page);
  });

  it('omits a blank search but keeps the populated parameters', () => {
    service.list({ search: '   ', page: 1, size: 5, sort: 'createdAt,desc' }).subscribe();

    const request = http.expectOne((req) => req.url === MEMBERS_URL);
    expect(request.request.params.has('search')).toBe(false);
    expect(request.request.params.get('page')).toBe('1');
    expect(request.request.params.get('size')).toBe('5');
    expect(request.request.params.get('sort')).toBe('createdAt,desc');
    request.flush(page);
  });

  it('trims the search term before sending it', () => {
    service.list({ search: '  Popescu  ' }).subscribe();

    const request = http.expectOne((req) => req.url === MEMBERS_URL);
    expect(request.request.params.get('search')).toBe('Popescu');
    request.flush(page);
  });

  it('keeps page 0 rather than dropping it as falsy', () => {
    service.list({ page: 0, size: 20 }).subscribe();

    const request = http.expectOne((req) => req.url === MEMBERS_URL);
    expect(request.request.params.get('page')).toBe('0');
    request.flush(page);
  });

  it('gets a single member by id', () => {
    let result: MemberResponse | undefined;
    service.get(12).subscribe((member) => (result = member));

    const request = http.expectOne(`${MEMBERS_URL}/12`);
    expect(request.request.method).toBe('GET');
    request.flush(popescu);

    expect(result).toEqual(popescu);
  });

  it('creates a member and returns the saved row', () => {
    const payload: MemberRequest = {
      name: 'Popescu Ion',
      email: 'ion.popescu@example.ro',
      address: 'Str. Victoriei 14, Timișoara',
      phoneNumber: '+40 721 000 000',
    };
    let result: MemberResponse | undefined;
    service.create(payload).subscribe((member) => (result = member));

    const request = http.expectOne(MEMBERS_URL);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(payload);
    request.flush({ ...popescu, openLoanCount: 0 });

    expect(result?.openLoanCount).toBe(0);
  });

  it('updates a member, sending the cleared optional fields as null', () => {
    const payload: MemberRequest = {
      name: 'Popescu Ioana',
      email: null,
      address: null,
      phoneNumber: null,
    };
    let result: MemberResponse | undefined;
    service.update(12, payload).subscribe((member) => (result = member));

    const request = http.expectOne(`${MEMBERS_URL}/12`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(payload);
    request.flush({ ...popescu, name: 'Popescu Ioana', email: null });

    expect(result?.name).toBe('Popescu Ioana');
  });

  it('archives a member with a DELETE and no body', () => {
    // A plain DELETE, unlike a book's POST /{id}/remove: archiving a member
    // records no reason (`API_CONTRACT.md` §7).
    service.delete(12).subscribe();

    const request = http.expectOne(`${MEMBERS_URL}/12`);
    expect(request.request.method).toBe('DELETE');
    expect(request.request.body).toBeNull();
    request.flush(null, { status: 204, statusText: 'No Content' });
  });
});
