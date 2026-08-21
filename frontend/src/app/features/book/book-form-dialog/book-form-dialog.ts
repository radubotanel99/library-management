import { Component, inject, OnInit, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { ApiError } from '../../../core/error/api-error';
import { NotificationService } from '../../../core/error/notification.service';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { CategoryResponse } from '../../category/category.model';
import { CategoryService } from '../../category/category.service';
import { BookRequest, BookResponse } from '../book.model';
import { BookService } from '../book.service';

/** `null` opens the dialog in create mode; a book opens it in edit mode. */
export type BookFormDialogData = BookResponse | null;

export const TITLE_MAX_LENGTH = 255;
export const AUTHOR_MAX_LENGTH = 255;
export const PUBLISHER_MAX_LENGTH = 255;
export const BOOK_NUMBER_MIN = 1;
export const PRICE_MIN = 0;

@Component({
  selector: 'app-book-form-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    TranslatePipe,
  ],
  templateUrl: './book-form-dialog.html',
  styleUrl: './book-form-dialog.css',
})
export class BookFormDialog implements OnInit {
  private readonly bookService = inject(BookService);
  private readonly categoryService = inject(CategoryService);
  private readonly notifications = inject(NotificationService);
  private readonly dialogRef = inject<MatDialogRef<BookFormDialog, BookResponse>>(MatDialogRef);
  private readonly formBuilder = inject(FormBuilder);

  protected readonly book = inject<BookFormDialogData>(MAT_DIALOG_DATA);
  protected readonly saving = signal(false);
  protected readonly categories = signal<readonly CategoryResponse[]>([]);

  protected readonly titleMaxLength = TITLE_MAX_LENGTH;
  protected readonly authorMaxLength = AUTHOR_MAX_LENGTH;
  protected readonly publisherMaxLength = PUBLISHER_MAX_LENGTH;
  protected readonly bookNumberMin = BOOK_NUMBER_MIN;
  protected readonly priceMin = PRICE_MIN;

  protected readonly form = this.formBuilder.nonNullable.group({
    title: [this.book?.title ?? '', [Validators.required, Validators.maxLength(TITLE_MAX_LENGTH)]],
    author: [
      this.book?.author ?? '',
      [Validators.required, Validators.maxLength(AUTHOR_MAX_LENGTH)],
    ],
    bookNumber: [
      this.book?.bookNumber ?? (null as number | null),
      [Validators.required, Validators.min(BOOK_NUMBER_MIN)],
    ],
    categoryId: [this.book?.categoryId ?? (null as number | null), [Validators.required]],
    publisher: [this.book?.publisher ?? '', [Validators.maxLength(PUBLISHER_MAX_LENGTH)]],
    price: [this.book?.price ?? (null as number | null), [Validators.min(PRICE_MIN)]],
  });

  ngOnInit(): void {
    // Only active categories are addressable, and that is exactly what the
    // endpoint returns (`API_CONTRACT.md` §6).
    this.categoryService.list().subscribe({
      next: (categories) => this.categories.set(categories),
      error: (error: ApiError) => this.notifications.showError(error),
    });
  }

  protected submit(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }

    const { title, author, bookNumber, categoryId, publisher, price } = this.form.getRawValue();
    const request: BookRequest = {
      title: title.trim(),
      author: author.trim(),
      // Both are guarded by Validators.required above, so they are set here.
      bookNumber: bookNumber as number,
      categoryId: categoryId as number,
      // An empty box means "not recorded", which the backend stores as null.
      publisher: publisher.trim() === '' ? null : publisher.trim(),
      price: price,
    };

    this.saving.set(true);
    const saved$ = this.book
      ? this.bookService.update(this.book.id, request)
      : this.bookService.create(request);

    saved$.subscribe({
      // The contract returns the saved book so the list can update itself
      // without re-fetching (`API_CONTRACT.md` §5).
      next: (saved) => this.dialogRef.close(saved),
      error: (error: ApiError) => {
        this.saving.set(false);
        this.handleSaveError(error);
      },
    });
  }

  /**
   * Server-side failures are routed back to the field they belong to whenever
   * the backend names one; anything else is a snackbar. In every case the
   * dialog stays open with the user's input intact.
   *
   * The manual `setErrors` calls are cleared automatically the next time the
   * control's value changes, because Angular re-runs its validators then —
   * so editing the offending field re-enables submitting.
   */
  private handleSaveError(error: ApiError): void {
    switch (error.code) {
      case 'BOOK_NUMBER_ALREADY_EXISTS':
        this.markServerError(this.form.controls.bookNumber, { duplicate: true });
        break;
      case 'CATEGORY_NOT_FOUND':
        // Rare race: the category was archived while this dialog was open.
        this.markServerError(this.form.controls.categoryId, { duplicate: true });
        break;
      case 'VALIDATION_ERROR': {
        const control = this.controlFor(error.field);
        if (control) {
          this.markServerError(control, { server: true });
        } else {
          // The backend rejected something we do not render — no field to blame.
          this.notifications.showError(error);
        }
        break;
      }
      default:
        this.notifications.showError(error);
    }
  }

  private markServerError(control: AbstractControl, errors: Record<string, true>): void {
    control.setErrors(errors);
    // Without this the message stays hidden on a field the user never focused.
    control.markAsTouched();
  }

  /** `AbstractControl`, not `FormControl<string>`: number and select controls live here too. */
  private controlFor(field: string | null): AbstractControl | null {
    switch (field) {
      case 'title':
        return this.form.controls.title;
      case 'author':
        return this.form.controls.author;
      case 'bookNumber':
        return this.form.controls.bookNumber;
      case 'categoryId':
        return this.form.controls.categoryId;
      case 'publisher':
        return this.form.controls.publisher;
      case 'price':
        return this.form.controls.price;
      default:
        return null;
    }
  }
}
