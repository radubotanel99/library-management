import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { PagedResponse } from '../../core/http/paged-response';
import { environment } from '../../../environments/environment';
import { BookRemoveRequest, BookRequest, BookResponse, BookRestoreRequest } from './book.model';
import { BookService } from './book.service';

const BOOKS_URL = `${environment.apiBaseUrl}/books`;

const amintiri: BookResponse = {
  id: 41,
  title: 'Amintiri din copilărie',
  author: 'Ion Creangă',
  bookNumber: 1201,
  categoryId: 3,
  categoryName: 'Fiction',
  publisher: 'Humanitas',
  price: 29.9,
  status: 'ACTIVE',
  removalNote: null,
  onLoan: true,
  createdAt: '2026-01-12T10:00:00Z',
  updatedAt: null,
};

const page: PagedResponse<BookResponse> = {
  content: [amintiri],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
};

describe('BookService', () => {
  let service: BookService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(BookService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists books without any query parameters when the criteria are empty', () => {
    let result: PagedResponse<BookResponse> | undefined;
    service.list({}).subscribe((books) => (result = books));

    const request = http.expectOne(BOOKS_URL);
    expect(request.request.method).toBe('GET');
    expect(request.request.params.keys()).toEqual([]);
    request.flush(page);

    expect(result).toEqual(page);
  });

  it('omits a blank search but keeps the populated parameters', () => {
    service
      .list({ search: '   ', categoryId: 3, page: 1, size: 5, sort: 'author,desc' })
      .subscribe();

    const request = http.expectOne((req) => req.url === BOOKS_URL);
    expect(request.request.params.has('search')).toBe(false);
    expect(request.request.params.get('categoryId')).toBe('3');
    expect(request.request.params.get('page')).toBe('1');
    expect(request.request.params.get('size')).toBe('5');
    expect(request.request.params.get('sort')).toBe('author,desc');
    request.flush(page);
  });

  it('trims the search term before sending it', () => {
    service.list({ search: '  Creangă  ' }).subscribe();

    const request = http.expectOne((req) => req.url === BOOKS_URL);
    expect(request.request.params.get('search')).toBe('Creangă');
    request.flush(page);
  });

  it('keeps page 0 rather than dropping it as falsy', () => {
    service.list({ page: 0, size: 20 }).subscribe();

    const request = http.expectOne((req) => req.url === BOOKS_URL);
    expect(request.request.params.get('page')).toBe('0');
    request.flush(page);
  });

  it('gets a single book by id', () => {
    let result: BookResponse | undefined;
    service.get(41).subscribe((book) => (result = book));

    const request = http.expectOne(`${BOOKS_URL}/41`);
    expect(request.request.method).toBe('GET');
    request.flush(amintiri);

    expect(result).toEqual(amintiri);
  });

  it('gets a book by its number', () => {
    let result: BookResponse | undefined;
    service.getByNumber(1201).subscribe((book) => (result = book));

    const request = http.expectOne(`${BOOKS_URL}/by-number/1201`);
    expect(request.request.method).toBe('GET');
    request.flush(amintiri);

    expect(result?.bookNumber).toBe(1201);
  });

  it('creates a book and returns the saved row', () => {
    const payload: BookRequest = {
      title: 'Amintiri din copilărie',
      author: 'Ion Creangă',
      bookNumber: 1201,
      categoryId: 3,
      publisher: 'Humanitas',
      price: 29.9,
    };
    let result: BookResponse | undefined;
    service.create(payload).subscribe((book) => (result = book));

    const request = http.expectOne(BOOKS_URL);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(payload);
    request.flush(amintiri);

    expect(result).toEqual(amintiri);
  });

  it('updates a book', () => {
    const payload: BookRequest = {
      title: 'Amintiri',
      author: 'Ion Creangă',
      bookNumber: 1202,
      categoryId: 3,
      publisher: null,
      price: null,
    };
    let result: BookResponse | undefined;
    service.update(41, payload).subscribe((book) => (result = book));

    const request = http.expectOne(`${BOOKS_URL}/41`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(payload);
    request.flush({ ...amintiri, bookNumber: 1202 });

    expect(result?.bookNumber).toBe(1202);
  });

  it('removes a book by posting the reason to the remove sub-resource', () => {
    const payload: BookRemoveRequest = {
      status: 'LOST',
      removalNote: 'Not returned by member',
    };
    let result: BookResponse | undefined;
    service.remove(41, payload).subscribe((book) => (result = book));

    // A POST to /{id}/remove, not a DELETE: the reason is mandatory and DELETE
    // has no body semantics for it (`API_CONTRACT.md` §5).
    const request = http.expectOne(`${BOOKS_URL}/41/remove`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(payload);
    request.flush({ ...amintiri, status: 'LOST', removalNote: payload.removalNote, onLoan: false });

    expect(result?.status).toBe('LOST');
    expect(result?.removalNote).toBe('Not returned by member');
  });

  it('lists archived books without any query parameters when the criteria are empty', () => {
    let result: PagedResponse<BookResponse> | undefined;
    service.archivedList({}).subscribe((books) => (result = books));

    const request = http.expectOne(`${BOOKS_URL}/archived`);
    expect(request.request.method).toBe('GET');
    expect(request.request.params.keys()).toEqual([]);
    request.flush(page);

    expect(result).toEqual(page);
  });

  it('narrows the archive listing to a single removal reason', () => {
    service.archivedList({ status: 'LOST', categoryId: 3 }).subscribe();

    const request = http.expectOne((req) => req.url === `${BOOKS_URL}/archived`);
    expect(request.request.params.get('status')).toBe('LOST');
    expect(request.request.params.get('categoryId')).toBe('3');
    request.flush(page);
  });

  it('restores a book with no body when no new number is given', () => {
    const restored: BookResponse = { ...amintiri, status: 'ACTIVE', removalNote: null };
    let result: BookResponse | undefined;
    service.restore(41).subscribe((book) => (result = book));

    // POST to /{id}/restore, not a PUT: the retry-with-a-new-number path is
    // folded into this same call (`API_CONTRACT.md` §5).
    const request = http.expectOne(`${BOOKS_URL}/41/restore`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({});
    request.flush(restored);

    expect(result?.status).toBe('ACTIVE');
  });

  it('restores a book with a new number after a number collision', () => {
    const payload: BookRestoreRequest = { bookNumber: 1305 };
    service.restore(41, payload).subscribe();

    const request = http.expectOne(`${BOOKS_URL}/41/restore`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(payload);
    request.flush({ ...amintiri, status: 'ACTIVE', removalNote: null, bookNumber: 1305 });
  });
});
