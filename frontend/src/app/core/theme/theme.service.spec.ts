import { TestBed } from '@angular/core/testing';
import { THEME_STORAGE_KEY, ThemeService } from './theme.service';

/**
 * jsdom under Node exposes no `localStorage`, so the suite installs an
 * in-memory one (same approach as `i18n.service.spec.ts`).
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

/** Storage that blows up on every access, like a browser with storage blocked. */
function installThrowingStorage(): void {
  Object.defineProperty(globalThis, 'localStorage', {
    configurable: true,
    get: () => {
      throw new DOMException('Storage is disabled', 'SecurityError');
    },
  });
}

/** Storage that reads fine but rejects writes, like a full quota. */
function installReadOnlyStorage(): void {
  const memory = installMemoryStorage();
  memory.setItem = () => {
    throw new DOMException('Quota exceeded', 'QuotaExceededError');
  };
}

function createService(): ThemeService {
  TestBed.configureTestingModule({});
  return TestBed.inject(ThemeService);
}

function documentTheme(): string | undefined {
  return document.documentElement.dataset['theme'];
}

describe('ThemeService', () => {
  let storage: Storage;

  beforeEach(() => {
    TestBed.resetTestingModule();
    storage = installMemoryStorage();
    delete document.documentElement.dataset['theme'];
  });

  it('starts light when nothing is stored', () => {
    const service = createService();

    expect(service.theme()).toBe('light');
    expect(documentTheme()).toBe('light');
  });

  it('applies the stored preference on startup', () => {
    storage.setItem(THEME_STORAGE_KEY, 'dark');

    const service = createService();

    expect(service.theme()).toBe('dark');
    expect(documentTheme()).toBe('dark');
  });

  it('falls back to light for an invalid stored value', () => {
    storage.setItem(THEME_STORAGE_KEY, 'sepia');

    expect(createService().theme()).toBe('light');
    expect(documentTheme()).toBe('light');
  });

  it('toggle flips data-theme and persists the choice', () => {
    const service = createService();

    service.toggle();

    expect(service.theme()).toBe('dark');
    expect(documentTheme()).toBe('dark');
    expect(storage.getItem(THEME_STORAGE_KEY)).toBe('dark');

    service.toggle();

    expect(service.theme()).toBe('light');
    expect(documentTheme()).toBe('light');
    expect(storage.getItem(THEME_STORAGE_KEY)).toBe('light');
  });

  it('keeps the chosen theme across a fresh service instance', () => {
    createService().setTheme('dark');

    TestBed.resetTestingModule();

    expect(createService().theme()).toBe('dark');
  });

  it('still works when storage access throws', () => {
    installThrowingStorage();

    const service = createService();
    expect(service.theme()).toBe('light');

    expect(() => service.toggle()).not.toThrow();
    expect(service.theme()).toBe('dark');
    expect(documentTheme()).toBe('dark');
  });

  it('still switches when storage rejects writes', () => {
    installReadOnlyStorage();
    const service = createService();

    expect(() => service.setTheme('dark')).not.toThrow();
    expect(documentTheme()).toBe('dark');
  });
});
