import { inject, Pipe, PipeTransform } from '@angular/core';
import { I18nService } from './i18n.service';
import { TranslationKey } from './translations/translation-key';

/**
 * `{{ 'category.list.title' | translate }}` — the only way user-facing text
 * reaches a template.
 *
 * Impure because the result depends on the active language rather than on the
 * key alone; the language signal is read during transform, so a switch marks
 * the view dirty and the text flips without a reload.
 */
@Pipe({ name: 'translate', pure: false })
export class TranslatePipe implements PipeTransform {
  private readonly i18n = inject(I18nService);

  transform(key: TranslationKey, params?: Record<string, string | number>): string {
    return this.i18n.t(key, params);
  }
}
