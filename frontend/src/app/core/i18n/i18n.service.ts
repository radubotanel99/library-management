import { computed, Injectable, signal } from '@angular/core';
import { en } from './translations/en';
import { ro } from './translations/ro';
import { TranslationKey } from './translations/translation-key';

export type Lang = 'en' | 'ro';

/** Where the language preference lives — on the client only, never on the server. */
export const LANG_STORAGE_KEY = 'lang';

const LANGS: readonly Lang[] = ['en', 'ro'];

const DICTIONARIES: Record<Lang, Record<TranslationKey, string>> = { en, ro };

const PARAM_PATTERN = /\{\{\s*(\w+)\s*\}\}/g;

function isLang(value: string | null): value is Lang {
  return value !== null && (LANGS as readonly string[]).includes(value);
}

/**
 * Browsers throw on `localStorage` when storage is blocked (private mode,
 * cookies disabled), so every access goes through here. Without storage the
 * app still works — the language simply resets on reload.
 */
function storage(): Storage | null {
  try {
    return globalThis.localStorage ?? null;
  } catch {
    return null;
  }
}

/**
 * Translation lookup. The backend never returns translated text (see
 * `API_CONTRACT.md` §3), so every user-facing string — including error messages
 * derived from error codes — is resolved here.
 *
 * The active language is a signal: templates that read it through
 * `TranslatePipe` re-render on switch, with no page reload.
 */
@Injectable({ providedIn: 'root' })
export class I18nService {
  private readonly currentLang = signal<Lang>(I18nService.initialLang());
  private readonly dictionary = computed(() => DICTIONARIES[this.currentLang()]);

  /** Active language, for templates that need to highlight the current choice. */
  readonly lang = this.currentLang.asReadonly();

  readonly availableLangs = LANGS;

  constructor() {
    document.documentElement.lang = this.currentLang();
  }

  setLang(lang: Lang): void {
    this.currentLang.set(lang);
    storage()?.setItem(LANG_STORAGE_KEY, lang);
    document.documentElement.lang = lang;
  }

  /**
   * Resolves `key` in the active dictionary, replacing `{{param}}` placeholders.
   * Unknown placeholders are left untouched so a missing parameter is visible
   * rather than silently blank.
   */
  t(key: TranslationKey, params?: Record<string, string | number>): string {
    const template = this.dictionary()[key];
    if (!params) {
      return template;
    }
    return template.replace(PARAM_PATTERN, (match, name: string) => {
      const value = params[name];
      return value === undefined ? match : String(value);
    });
  }

  /** Stored preference wins; otherwise fall back to the browser, then English. */
  private static initialLang(): Lang {
    const stored = storage()?.getItem(LANG_STORAGE_KEY) ?? null;
    if (isLang(stored)) {
      return stored;
    }
    return navigator.language.toLowerCase().startsWith('ro') ? 'ro' : 'en';
  }
}
