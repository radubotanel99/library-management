import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { ParameterRequest, ParameterResponse } from './settings.model';
import { SettingsService } from './settings.service';

const PARAMETERS_URL = `${environment.apiBaseUrl}/parameters`;

const daysToKeepABook: ParameterResponse = {
  key: 'DAYS_TO_KEEP_A_BOOK',
  value: '14',
  updatedAt: '2026-08-01T09:00:00Z',
};

const maxBooksPerMember: ParameterResponse = {
  key: 'MAX_BOOKS_PER_MEMBER',
  value: '3',
  updatedAt: null,
};

describe('SettingsService', () => {
  let service: SettingsService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(SettingsService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists parameters as a plain array', () => {
    let result: ParameterResponse[] | undefined;
    service.list().subscribe((parameters) => (result = parameters));

    const request = http.expectOne(PARAMETERS_URL);
    expect(request.request.method).toBe('GET');
    request.flush([daysToKeepABook, maxBooksPerMember]);

    expect(result).toEqual([daysToKeepABook, maxBooksPerMember]);
  });

  it('saves the full set in one request and returns the saved rows', () => {
    const payload: ParameterRequest[] = [
      { key: 'DAYS_TO_KEEP_A_BOOK', value: '21' },
      { key: 'MAX_BOOKS_PER_MEMBER', value: '5' },
    ];
    let result: ParameterResponse[] | undefined;
    service.save(payload).subscribe((parameters) => (result = parameters));

    const request = http.expectOne(PARAMETERS_URL);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(payload);
    request.flush([
      { ...daysToKeepABook, value: '21' },
      { ...maxBooksPerMember, value: '5', updatedAt: '2026-08-23T10:00:00Z' },
    ]);

    expect(result?.map((parameter) => parameter.value)).toEqual(['21', '5']);
  });
});
