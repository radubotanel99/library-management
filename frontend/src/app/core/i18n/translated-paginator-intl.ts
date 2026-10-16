import { formatNumber } from '@angular/common';
import { effect, inject, Injectable, Provider, untracked } from '@angular/core';
import { MatPaginatorIntl } from '@angular/material/paginator';
import { I18nService } from './i18n.service';

/**
 * `MatPaginator` ships English labels ("Items per page", "1 – 20 of 100").
 * This replaces them with translated ones and tells every paginator to
 * re-render when the language switches — `changes` is the hook MatPaginator
 * listens to.
 *
 * Provided per paged list through {@link provideTranslatedPaginatorIntl} in
 * the component's `providers`, so it lives in the lazy route chunks rather
 * than the initial bundle.
 */
@Injectable()
export class TranslatedPaginatorIntl extends MatPaginatorIntl {
  private readonly i18n = inject(I18nService);

  constructor() {
    super();
    this.applyLabels();
    effect(() => {
      // Tracks the language only; the label writes and the notification are
      // side effects that must not become dependencies themselves.
      this.i18n.lang();
      untracked(() => {
        this.applyLabels();
        this.changes.next();
      });
    });
  }

  override getRangeLabel = (page: number, pageSize: number, length: number): string => {
    const locale = this.i18n.locale();
    const total = formatNumber(Math.max(length, 0), locale, '1.0-0');
    if (length === 0 || pageSize === 0) {
      return this.i18n.t('paginator.rangeEmpty', { length: total });
    }
    const startIndex = page * pageSize;
    // Same arithmetic as Material's default: a page past the end still shows a
    // full-size range rather than a negative one.
    const endIndex =
      startIndex < length ? Math.min(startIndex + pageSize, length) : startIndex + pageSize;
    return this.i18n.t('paginator.range', {
      start: formatNumber(startIndex + 1, locale, '1.0-0'),
      end: formatNumber(endIndex, locale, '1.0-0'),
      length: total,
    });
  };

  private applyLabels(): void {
    this.itemsPerPageLabel = this.i18n.t('paginator.itemsPerPage');
    this.firstPageLabel = this.i18n.t('paginator.firstPage');
    this.previousPageLabel = this.i18n.t('paginator.previousPage');
    this.nextPageLabel = this.i18n.t('paginator.nextPage');
    this.lastPageLabel = this.i18n.t('paginator.lastPage');
  }
}

/** `providers: [provideTranslatedPaginatorIntl()]` on a component that renders a `mat-paginator`. */
export function provideTranslatedPaginatorIntl(): Provider {
  return { provide: MatPaginatorIntl, useClass: TranslatedPaginatorIntl };
}
