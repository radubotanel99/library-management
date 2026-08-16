import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { CategoryRequest, CategoryResponse } from './category.model';
import { CategoryService } from './category.service';

const CATEGORIES_URL = `${environment.apiBaseUrl}/categories`;

const fiction: CategoryResponse = {
  id: 3,
  name: 'Fiction',
  description: 'Novels and short stories',
  bookCount: 42,
};

describe('CategoryService', () => {
  let service: CategoryService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(CategoryService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists categories as a plain array', () => {
    let result: CategoryResponse[] | undefined;
    service.list().subscribe((categories) => (result = categories));

    const request = http.expectOne(CATEGORIES_URL);
    expect(request.request.method).toBe('GET');
    request.flush([fiction]);

    expect(result).toEqual([fiction]);
  });

  it('gets a single category by id', () => {
    let result: CategoryResponse | undefined;
    service.get(3).subscribe((category) => (result = category));

    const request = http.expectOne(`${CATEGORIES_URL}/3`);
    expect(request.request.method).toBe('GET');
    request.flush(fiction);

    expect(result).toEqual(fiction);
  });

  it('creates a category and returns the saved row', () => {
    const payload: CategoryRequest = { name: 'Fiction', description: 'Novels and short stories' };
    let result: CategoryResponse | undefined;
    service.create(payload).subscribe((category) => (result = category));

    const request = http.expectOne(CATEGORIES_URL);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(payload);
    request.flush(fiction);

    expect(result).toEqual(fiction);
  });

  it('updates a category', () => {
    const payload: CategoryRequest = { name: 'Non-fiction', description: null };
    let result: CategoryResponse | undefined;
    service.update(3, payload).subscribe((category) => (result = category));

    const request = http.expectOne(`${CATEGORIES_URL}/3`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(payload);
    request.flush({ ...fiction, name: 'Non-fiction', description: null });

    expect(result?.name).toBe('Non-fiction');
  });

  it('deletes a category and completes on 204', () => {
    let completed = false;
    service.delete(3).subscribe({ complete: () => (completed = true) });

    const request = http.expectOne(`${CATEGORIES_URL}/3`);
    expect(request.request.method).toBe('DELETE');
    request.flush(null, { status: 204, statusText: 'No Content' });

    expect(completed).toBe(true);
  });
});
