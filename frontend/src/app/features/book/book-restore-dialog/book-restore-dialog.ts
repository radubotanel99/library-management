import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { ApiError } from '../../../core/error/api-error';
import { NotificationService } from '../../../core/error/notification.service';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { BookResponse } from '../book.model';
import { BookService } from '../book.service';

/** The archived copy being restored. Never null: there is no create mode. */
export type BookRestoreDialogData = BookResponse;

export const RESTORE_BOOK_NUMBER_MIN = 1;

/**
 * The retry step after a `BOOK_NUMBER_TAKEN_ON_RESTORE` collision.
 *
 * The archive list calls `restore` directly for the happy path and only opens
 * this dialog on that one error, prefilled with the number that was rejected —
 * submitting here folds the new number into the same `POST /restore` call, so
 * there is no separate `PUT` (`API_CONTRACT.md` §5).
 */
@Component({
  selector: 'app-book-restore-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    TranslatePipe,
  ],
  templateUrl: './book-restore-dialog.html',
  styleUrl: './book-restore-dialog.css',
})
export class BookRestoreDialog {
  private readonly bookService = inject(BookService);
  private readonly notifications = inject(NotificationService);
  private readonly dialogRef = inject<MatDialogRef<BookRestoreDialog, BookResponse>>(MatDialogRef);
  private readonly formBuilder = inject(FormBuilder);

  protected readonly book = inject<BookRestoreDialogData>(MAT_DIALOG_DATA);
  protected readonly saving = signal(false);
  protected readonly bookNumberMin = RESTORE_BOOK_NUMBER_MIN;

  protected readonly form = this.formBuilder.nonNullable.group({
    bookNumber: [
      this.book.bookNumber,
      [Validators.required, Validators.min(RESTORE_BOOK_NUMBER_MIN)],
    ],
  });

  protected submit(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }

    const { bookNumber } = this.form.getRawValue();
    this.saving.set(true);
    this.bookService.restore(this.book.id, { bookNumber }).subscribe({
      next: (restored) => this.dialogRef.close(restored),
      error: (error: ApiError) => {
        this.saving.set(false);
        this.handleRestoreError(error);
      },
    });
  }

  /**
   * A repeat collision (or a stray `VALIDATION_ERROR` on the same field) keeps
   * the dialog open with the number highlighted so the user can try another
   * one. Anything else — the copy having meanwhile been restored elsewhere, or
   * its category having since been archived — is a snackbar: no different
   * number would fix either of those.
   */
  private handleRestoreError(error: ApiError): void {
    if (
      error.code === 'BOOK_NUMBER_TAKEN_ON_RESTORE' ||
      (error.code === 'VALIDATION_ERROR' && error.field === 'bookNumber')
    ) {
      this.form.controls.bookNumber.setErrors({ server: true });
      // Without this the message stays hidden on a field the user never focused.
      this.form.controls.bookNumber.markAsTouched();
      return;
    }
    this.notifications.showError(error);
  }
}
