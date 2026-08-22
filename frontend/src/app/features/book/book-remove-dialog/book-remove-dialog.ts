import { Component, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { ApiError } from '../../../core/error/api-error';
import { NotificationService } from '../../../core/error/notification.service';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { TranslationKey } from '../../../core/i18n/translations/translation-key';
import { BookRemoveRequest, BookResponse, RemovalReason } from '../book.model';
import { BookService } from '../book.service';

/** The copy being taken out of the collection. Never null: there is no create mode. */
export type BookRemoveDialogData = BookResponse;

export const REMOVAL_NOTE_MAX_LENGTH = 500;

/**
 * The reasons offered, in the order they appear in the select. `ACTIVE` is absent
 * by construction — `RemovalReason` excludes it, so restoring can never happen by
 * way of a removal (`DATA_MODEL.md` §4.1).
 */
export const REMOVAL_REASONS: readonly RemovalReason[] = ['LOST', 'DAMAGED', 'WITHDRAWN'];

/**
 * Asks for the reason a copy is leaving the collection.
 *
 * There is no separate yes/no confirmation in front of this: picking a mandatory
 * reason and submitting is deliberate enough, and removal is reversible through
 * restore.
 */
@Component({
  selector: 'app-book-remove-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    TranslatePipe,
  ],
  templateUrl: './book-remove-dialog.html',
  styleUrl: './book-remove-dialog.css',
})
export class BookRemoveDialog {
  private readonly bookService = inject(BookService);
  private readonly notifications = inject(NotificationService);
  private readonly dialogRef = inject<MatDialogRef<BookRemoveDialog, BookResponse>>(MatDialogRef);
  private readonly formBuilder = inject(FormBuilder);

  protected readonly book = inject<BookRemoveDialogData>(MAT_DIALOG_DATA);
  protected readonly saving = signal(false);

  protected readonly removalNoteMaxLength = REMOVAL_NOTE_MAX_LENGTH;
  protected readonly reasons = REMOVAL_REASONS;

  // The control names match the wire fields, so mapping a server-side
  // VALIDATION_ERROR back onto a control is a direct lookup.
  protected readonly form = this.formBuilder.nonNullable.group({
    status: [null as RemovalReason | null, [Validators.required]],
    removalNote: ['', [Validators.maxLength(REMOVAL_NOTE_MAX_LENGTH)]],
  });

  /**
   * The `book.status.*` keys are named after the status values, so the key is
   * derived rather than looked up — and the declared `TranslationKey` return type
   * makes a missing translation a compile error.
   */
  protected reasonLabelKey(reason: RemovalReason): TranslationKey {
    return `book.status.${reason}`;
  }

  protected submit(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }

    const { status, removalNote } = this.form.getRawValue();
    const request: BookRemoveRequest = {
      // Guarded by Validators.required above, so it is set here.
      status: status as RemovalReason,
      // An empty box means "nothing to add", which the backend stores as null.
      removalNote: removalNote.trim() === '' ? null : removalNote.trim(),
    };

    this.saving.set(true);
    this.bookService.remove(this.book.id, request).subscribe({
      // The contract returns the archived book, so the caller knows it succeeded.
      next: (removed) => this.dialogRef.close(removed),
      error: (error: ApiError) => {
        this.saving.set(false);
        this.handleRemoveError(error);
      },
    });
  }

  /**
   * The dialog stays open on every failure, with the typed note intact. A named
   * field is routed back to its control; everything else — including
   * `BOOK_HAS_OPEN_LOAN`, which only appears if the copy was lent while this
   * dialog was open — is a snackbar, because no single control is to blame.
   */
  private handleRemoveError(error: ApiError): void {
    if (error.code === 'VALIDATION_ERROR') {
      const control = this.controlFor(error.field);
      if (control) {
        control.setErrors({ server: true });
        // Without this the message stays hidden on a field the user never focused.
        control.markAsTouched();
        return;
      }
    }
    this.notifications.showError(error);
  }

  /** `AbstractControl`, not `FormControl<string>`: the select control lives here too. */
  private controlFor(field: string | null): AbstractControl | null {
    switch (field) {
      case 'status':
        return this.form.controls.status;
      case 'removalNote':
        return this.form.controls.removalNote;
      default:
        return null;
    }
  }
}
