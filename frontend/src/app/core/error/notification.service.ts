import { Injectable } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { I18nService } from '../i18n/i18n.service';
import { isTranslationKey } from '../i18n/translations/translation-key';
import { ApiError } from './api-error';

const ERROR_SNACKBAR_DURATION_MS = 6000;

/**
 * Turns an {@link ApiError} into a translated snackbar.
 *
 * The mapping is `code` → `error.${code}`; a code with no dictionary entry
 * (a new backend code shipped ahead of the frontend) falls back to
 * `error.UNKNOWN` rather than leaking the backend's English `message`.
 */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  constructor(
    private readonly snackBar: MatSnackBar,
    private readonly i18n: I18nService,
  ) {}

  showError(err: ApiError): void {
    const candidate = `error.${err.code}`;
    const key = isTranslationKey(candidate) ? candidate : 'error.UNKNOWN';
    this.snackBar.open(this.i18n.t(key), this.i18n.t('common.dismiss'), {
      duration: ERROR_SNACKBAR_DURATION_MS,
    });
  }
}
