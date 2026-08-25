import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';
import { ApiError } from '../../../core/error/api-error';
import { NotificationService } from '../../../core/error/notification.service';
import { I18nService, Lang } from '../../../core/i18n/i18n.service';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { TranslationKey } from '../../../core/i18n/translations/translation-key';
import { ParameterRequest, ParameterResponse } from '../settings.model';
import { SettingsService } from '../settings.service';

/** Lower bound only: the backend accepts any positive integer (`API_CONTRACT.md` §9). */
export const PARAMETER_MIN = 1;

/** Digits only — a decimal or a minus sign is a client-side error, not a round trip. */
const POSITIVE_INTEGER = /^\d+$/;

/** Same rule for both parameters, and no upper bound: the backend imposes none. */
const VALUE_VALIDATORS = [
  Validators.required,
  Validators.pattern(POSITIVE_INTEGER),
  Validators.min(PARAMETER_MIN),
];

@Component({
  selector: 'app-settings-page',
  imports: [
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    MatProgressBarModule,
    RouterLink,
    TranslatePipe,
  ],
  templateUrl: './settings-page.html',
  styleUrl: './settings-page.css',
})
export class SettingsPage implements OnInit {
  private readonly settingsService = inject(SettingsService);
  private readonly notifications = inject(NotificationService);
  private readonly formBuilder = inject(FormBuilder);
  private readonly i18n = inject(I18nService);

  protected readonly loading = signal(false);
  protected readonly saving = signal(false);
  protected readonly loadFailed = signal(false);

  protected readonly parameterMin = PARAMETER_MIN;

  protected readonly lang = this.i18n.lang;
  protected readonly langs = this.i18n.availableLangs;

  // Values are strings on the wire (`API_CONTRACT.md` §9), so the controls hold
  // strings too and nothing is converted twice.
  protected readonly form = this.formBuilder.nonNullable.group({
    daysToKeepABook: ['', VALUE_VALIDATORS],
    maxBooksPerMember: ['', VALUE_VALIDATORS],
  });

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.loadFailed.set(false);
    this.settingsService.list().subscribe({
      next: (parameters) => {
        this.apply(parameters);
        this.loading.set(false);
      },
      error: (error: ApiError) => {
        this.loading.set(false);
        this.loadFailed.set(true);
        this.notifications.showError(error);
      },
    });
  }

  protected submit(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }

    const { daysToKeepABook, maxBooksPerMember } = this.form.getRawValue();
    // Both known keys go every time: the backend rejects a request that is
    // missing one rather than applying it partially (`API_CONTRACT.md` §9).
    const requests: ParameterRequest[] = [
      { key: 'DAYS_TO_KEEP_A_BOOK', value: daysToKeepABook.trim() },
      { key: 'MAX_BOOKS_PER_MEMBER', value: maxBooksPerMember.trim() },
    ];

    this.saving.set(true);
    this.settingsService.save(requests).subscribe({
      next: (saved) => {
        this.saving.set(false);
        // The endpoint returns the saved set, so the form refreshes from the
        // response instead of re-fetching.
        this.apply(saved);
        this.notifications.showMessage('settings.saveSuccess');
      },
      error: (error: ApiError) => {
        this.saving.set(false);
        this.handleSaveError(error);
      },
    });
  }

  /**
   * Server-side failures are routed back to the field they belong to whenever
   * the backend names one; anything else is a snackbar. In every case the form
   * stays on screen with the user's input intact.
   *
   * The manual `setErrors` call is cleared automatically the next time the
   * control's value changes, because Angular re-runs its validators then —
   * so editing the offending field re-enables submitting.
   */
  private handleSaveError(error: ApiError): void {
    switch (error.code) {
      case 'PARAMETER_INVALID_VALUE': {
        // `field` carries the parameter *key*, not a form field name: the body
        // is an array, so there is no JSON path to point at (`API_CONTRACT.md` §9).
        const control = this.controlFor(error.field);
        if (control) {
          control.setErrors({ server: true });
          // Without this the message stays hidden on a field the user never focused.
          control.markAsTouched();
        } else {
          // A key we do not render — no field to blame.
          this.notifications.showError(error);
        }
        break;
      }
      default:
        this.notifications.showError(error);
    }
  }

  /** Fills the form from the loaded set, matching each control by parameter key. */
  private apply(parameters: readonly ParameterResponse[]): void {
    for (const parameter of parameters) {
      this.controlFor(parameter.key)?.setValue(parameter.value);
    }
    this.form.markAsPristine();
  }

  /** Keyed by parameter key, because that is the identifier the backend speaks. */
  private controlFor(key: string | null): FormControl<string> | null {
    switch (key) {
      case 'DAYS_TO_KEEP_A_BOOK':
        return this.form.controls.daysToKeepABook;
      case 'MAX_BOOKS_PER_MEMBER':
        return this.form.controls.maxBooksPerMember;
      default:
        return null;
    }
  }

  protected langLabel(lang: Lang): TranslationKey {
    return lang === 'ro' ? 'lang.ro' : 'lang.en';
  }

  protected setLang(lang: Lang): void {
    this.i18n.setLang(lang);
  }
}
