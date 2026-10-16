import { Injectable, signal } from '@angular/core';

export type Theme = 'light' | 'dark';

/** Where the theme preference lives — on the client only, like the language (see `I18nService`). */
export const THEME_STORAGE_KEY = 'theme';

const THEMES: readonly Theme[] = ['light', 'dark'];

function isTheme(value: string | null): value is Theme {
  return value !== null && (THEMES as readonly string[]).includes(value);
}

/**
 * Browsers throw on `localStorage` when storage is blocked (private mode,
 * cookies disabled), so every access goes through here. Without storage the
 * app still works — the theme simply resets on reload.
 */
function storage(): Storage | null {
  try {
    return globalThis.localStorage ?? null;
  } catch {
    return null;
  }
}

/**
 * Light/dark theme switch for the Library Admin design system.
 *
 * The theme is applied as `data-theme` on `<html>`: `tokens.css` redefines its
 * CSS variables under `[data-theme="dark"]`, and the Material theme reads those
 * variables, so one attribute re-themes the whole app with no reload.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly currentTheme = signal<Theme>(ThemeService.initialTheme());

  /** Active theme, for templates that need to show the current choice. */
  readonly theme = this.currentTheme.asReadonly();

  constructor() {
    document.documentElement.dataset['theme'] = this.currentTheme();
  }

  setTheme(theme: Theme): void {
    this.currentTheme.set(theme);
    document.documentElement.dataset['theme'] = theme;
    try {
      storage()?.setItem(THEME_STORAGE_KEY, theme);
    } catch {
      // Storage can exist yet refuse writes (quota, some private modes). The
      // theme still applies for this session; it just will not survive a reload.
    }
  }

  toggle(): void {
    this.setTheme(this.currentTheme() === 'light' ? 'dark' : 'light');
  }

  /**
   * Stored preference wins; otherwise light, the design system's primary theme.
   * `prefers-color-scheme` is deliberately not consulted: the app starts light
   * for everyone until a librarian chooses otherwise.
   */
  private static initialTheme(): Theme {
    let stored: string | null = null;
    try {
      stored = storage()?.getItem(THEME_STORAGE_KEY) ?? null;
    } catch {
      // Same as no storage at all: fall through to the default.
    }
    return isTheme(stored) ? stored : 'light';
  }
}
