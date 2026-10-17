import { Component, computed, effect, inject, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { Title } from '@angular/platform-browser';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { I18nService } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { TranslationKey } from '../core/i18n/translations/translation-key';
import { breakpointMatches, COMPACT_LAYOUT } from '../core/layout/breakpoints';
import { ThemeService } from '../core/theme/theme.service';

/** One entry in the sidebar. */
export interface NavItem {
  path: string;
  labelKey: TranslationKey;
  /** Material Symbols ligature name. */
  icon: string;
  /**
   * Exact matching for `/books`: otherwise `/books/archive` would light up both
   * Books and Archive.
   */
  exact: boolean;
  /** Draws a divider above this item, to start a new group. */
  dividerBefore?: boolean;
}

/**
 * The seven screens, in the order a shift uses them. Actions ("Add book") stay
 * in page headers — the design system keeps the sidebar to navigation only.
 */
export const NAV_ITEMS: readonly NavItem[] = [
  { path: '/dashboard', labelKey: 'nav.dashboard', icon: 'dashboard', exact: false },
  { path: '/books', labelKey: 'nav.books', icon: 'menu_book', exact: true },
  { path: '/categories', labelKey: 'nav.categories', icon: 'sell', exact: false },
  { path: '/members', labelKey: 'nav.members', icon: 'group', exact: false },
  { path: '/loans', labelKey: 'nav.loans', icon: 'swap_horiz', exact: false },
  { path: '/books/archive', labelKey: 'nav.archive', icon: 'inventory_2', exact: false, dividerBefore: true },
  { path: '/settings', labelKey: 'nav.settings', icon: 'settings', exact: false },
];

/**
 * Application chrome: the design-system Sidebar, the theme toggle and the
 * routed content. Below 900px the sidebar collapses into a top bar whose
 * links open behind a menu button.
 */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatIconModule, TranslatePipe],
  templateUrl: './shell.html',
  styleUrl: './shell.css',
  host: { '[class.app-shell--compact]': 'compact()' },
})
export class Shell {
  // Injected here, in the component every route renders inside, so the stored
  // theme is applied on startup rather than the first time someone toggles it.
  private readonly themeService = inject(ThemeService);
  private readonly i18n = inject(I18nService);
  private readonly title = inject(Title);

  protected readonly navItems = NAV_ITEMS;
  protected readonly compact = breakpointMatches(COMPACT_LAYOUT);
  protected readonly menuOpen = signal(false);

  /** The links are always shown on a wide screen; on a narrow one only when opened. */
  protected readonly linksVisible = computed(() => !this.compact() || this.menuOpen());

  /** The label names the action, so it is the theme the user would switch *to*. */
  protected readonly themeToggleLabel = computed<TranslationKey>(() =>
    this.themeService.theme() === 'light' ? 'theme.switchToDark' : 'theme.switchToLight',
  );
  protected readonly themeToggleIcon = computed(() =>
    this.themeService.theme() === 'light' ? 'dark_mode' : 'light_mode',
  );

  constructor() {
    // The browser tab follows the UI language, like every other string.
    effect(() => this.title.setTitle(this.i18n.t('app.title')));
  }

  protected toggleMenu(): void {
    this.menuOpen.update((open) => !open);
  }

  /** After picking a page on a narrow screen, get the menu out of the way. */
  protected closeMenu(): void {
    this.menuOpen.set(false);
  }

  protected toggleTheme(): void {
    this.themeService.toggle();
  }
}
