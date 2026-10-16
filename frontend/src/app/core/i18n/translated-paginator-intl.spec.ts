import { registerLocaleData } from '@angular/common';
import localeRo from '@angular/common/locales/ro';
import { TestBed } from '@angular/core/testing';
import { I18nService } from './i18n.service';
import { TranslatedPaginatorIntl } from './translated-paginator-intl';

describe('TranslatedPaginatorIntl', () => {
  let i18n: I18nService;
  let intl: TranslatedPaginatorIntl;

  beforeAll(() => registerLocaleData(localeRo));

  beforeEach(() => {
    TestBed.resetTestingModule();
    Object.defineProperty(navigator, 'language', { value: 'en-US', configurable: true });
    // No storage: the language comes from the (English) browser.
    Object.defineProperty(globalThis, 'localStorage', { value: undefined, configurable: true });
    TestBed.configureTestingModule({ providers: [TranslatedPaginatorIntl] });
    i18n = TestBed.inject(I18nService);
    intl = TestBed.inject(TranslatedPaginatorIntl);
  });

  it('labels the controls in the active language', () => {
    expect(intl.itemsPerPageLabel).toBe('Rows per page');
    expect(intl.nextPageLabel).toBe('Next page');
  });

  it('formats the range with locale thousands separators', () => {
    expect(intl.getRangeLabel(1, 20, 12480)).toBe('21 – 40 of 12,480');
    expect(intl.getRangeLabel(0, 20, 0)).toBe('0 of 0');
  });

  it('switches labels and notifies paginators when the language changes', () => {
    let notified = 0;
    intl.changes.subscribe(() => notified++);

    i18n.setLang('ro');
    TestBed.tick();

    expect(intl.itemsPerPageLabel).toBe('Rânduri pe pagină');
    expect(intl.getRangeLabel(0, 20, 12480)).toBe('1 – 20 din 12.480');
    expect(notified).toBeGreaterThan(0);
  });
});
