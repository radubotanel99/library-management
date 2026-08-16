import { Component, inject, signal } from '@angular/core';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { ApiError } from '../../../core/error/api-error';
import { NotificationService } from '../../../core/error/notification.service';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { CategoryRequest, CategoryResponse } from '../category.model';
import { CategoryService } from '../category.service';

/** `null` opens the dialog in create mode; a category opens it in edit mode. */
export type CategoryFormDialogData = CategoryResponse | null;

export const NAME_MAX_LENGTH = 100;
export const DESCRIPTION_MAX_LENGTH = 500;

@Component({
  selector: 'app-category-form-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    TranslatePipe,
  ],
  templateUrl: './category-form-dialog.html',
  styleUrl: './category-form-dialog.css',
})
export class CategoryFormDialog {
  private readonly categoryService = inject(CategoryService);
  private readonly notifications = inject(NotificationService);
  private readonly dialogRef =
    inject<MatDialogRef<CategoryFormDialog, CategoryResponse>>(MatDialogRef);
  private readonly formBuilder = inject(FormBuilder);

  protected readonly category = inject<CategoryFormDialogData>(MAT_DIALOG_DATA);
  protected readonly saving = signal(false);

  protected readonly nameMaxLength = NAME_MAX_LENGTH;
  protected readonly descriptionMaxLength = DESCRIPTION_MAX_LENGTH;

  protected readonly form = this.formBuilder.nonNullable.group({
    name: [this.category?.name ?? '', [Validators.required, Validators.maxLength(NAME_MAX_LENGTH)]],
    description: [this.category?.description ?? '', [Validators.maxLength(DESCRIPTION_MAX_LENGTH)]],
  });

  protected submit(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }

    const { name, description } = this.form.getRawValue();
    const request: CategoryRequest = {
      name: name.trim(),
      // An empty box means "no description", which the backend stores as null.
      description: description.trim() === '' ? null : description.trim(),
    };

    this.saving.set(true);
    const saved$ = this.category
      ? this.categoryService.update(this.category.id, request)
      : this.categoryService.create(request);

    saved$.subscribe({
      // The contract returns the saved category so the list can update itself
      // without re-fetching (`API_CONTRACT.md` §6).
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
      case 'CATEGORY_NAME_ALREADY_EXISTS':
        this.markServerError(this.form.controls.name, { duplicate: true });
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

  private markServerError(control: FormControl<string>, errors: Record<string, true>): void {
    control.setErrors(errors);
    // Without this the message stays hidden on a field the user never focused.
    control.markAsTouched();
  }

  private controlFor(field: string | null): FormControl<string> | null {
    switch (field) {
      case 'name':
        return this.form.controls.name;
      case 'description':
        return this.form.controls.description;
      default:
        return null;
    }
  }
}
