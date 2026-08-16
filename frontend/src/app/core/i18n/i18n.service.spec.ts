import { TestBed } from '@angular/core/testing';
import { I18nService, LANG_STORAGE_KEY } from './i18n.service';

/**
 * The test environment (jsdom under Node) exposes no `localStorage`, so the
 * suite installs an in-memory one — the point of these tests is that the
 * service reads and writes it, not which implementation backs it.
 */
function installMemoryStorage(): Storage {
  const entries = new Map<string, string>();
  const memory: Storage = {
    get length() {
      return entries.size;
    },
    clear: () => entries.clear(),
    getItem: (key: string) => entries.get(key) ?? null,
    key: (index: number) => [...entries.keys()][index] ?? null,
    removeItem: (key: string) => void entries.delete(key),
    setItem: (key: string, value: string) => void entries.set(key, value),
  };
  Object.defineProperty(globalThis, 'localStorage', { value: memory, configurable: true });
  return memory;
}

function stubBrowserLanguage(language: string): void {
  Object.defineProperty(navigator, 'language', { value: language, configurable: true });
}

function createService(): I18nService {
  TestBed.configureTestingModule({});
  return TestBed.inject(I18nService);
}

describe('I18nService', () => {
  let storage: Storage;

  beforeEach(() => {
    TestBed.resetTestingModule();
    storage = installMemoryStorage();
    stubBrowserLanguage('en-US');
    document.documentElement.lang = '';
  });

  it('seeds the language from localStorage', () => {
    storage.setItem(LANG_STORAGE_KEY, 'ro');

    expect(createService().lang()).toBe('ro');
  });

  it('falls back to the browser language when nothing is stored', () => {
    stubBrowserLanguage('ro-RO');

    expect(createService().lang()).toBe('ro');
  });

  it('falls back to English for an unsupported stored value', () => {
    storage.setItem(LANG_STORAGE_KEY, 'de');
    stubBrowserLanguage('en-GB');

    expect(createService().lang()).toBe('en');
  });

  it('persists the language and tags the document on setLang', () => {
    const service = createService();

    service.setLang('ro');

    expect(service.lang()).toBe('ro');
    expect(storage.getItem(LANG_STORAGE_KEY)).toBe('ro');
    expect(document.documentElement.lang).toBe('ro');
  });

  it('keeps the stored language across a fresh service instance', () => {
    createService().setLang('ro');

    TestBed.resetTestingModule();

    expect(createService().lang()).toBe('ro');
  });

  it('translates using the dictionary of the active language', () => {
    const service = createService();

    expect(service.t('nav.categories')).toBe('Categories');

    service.setLang('ro');

    expect(service.t('nav.categories')).toBe('Categorii');
  });

  it('interpolates {{param}} placeholders', () => {
    const service = createService();

    expect(service.t('validation.maxLength', { max: 100 })).toBe('Use at most 100 characters.');
    expect(service.t('category.delete.message', { name: 'Fiction' })).toContain('"Fiction"');
  });

  it('leaves a placeholder untouched when no value is supplied', () => {
    const service = createService();

    expect(service.t('validation.maxLength', { other: 1 })).toContain('{{max}}');
  });
});
