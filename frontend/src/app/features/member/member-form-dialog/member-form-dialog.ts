import { Component, inject, signal } from '@angular/core';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { ApiError } from '../../../core/error/api-error';
import { NotificationService } from '../../../core/error/notification.service';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { MemberRequest, MemberResponse } from '../member.model';
import { MemberService } from '../member.service';

/** `null` opens the dialog in create mode; a member opens it in edit mode. */
export type MemberFormDialogData = MemberResponse | null;

// These mirror the Bean Validation limits on `MemberRequest` exactly. Client-side
// checks are a convenience; the backend stays the authority, and a limit that
// drifted from it would let a valid form fail on submit.
export const NAME_MAX_LENGTH = 150;
export const EMAIL_MAX_LENGTH = 255;
export const ADDRESS_MAX_LENGTH = 500;
export const PHONE_NUMBER_MAX_LENGTH = 50;

@Component({
  selector: 'app-member-form-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    TranslatePipe,
  ],
  templateUrl: './member-form-dialog.html',
  styleUrl: './member-form-dialog.css',
})
export class MemberFormDialog {
  private readonly memberService = inject(MemberService);
  private readonly notifications = inject(NotificationService);
  private readonly dialogRef = inject<MatDialogRef<MemberFormDialog, MemberResponse>>(MatDialogRef);
  private readonly formBuilder = inject(FormBuilder);

  protected readonly member = inject<MemberFormDialogData>(MAT_DIALOG_DATA);
  protected readonly saving = signal(false);

  protected readonly nameMaxLength = NAME_MAX_LENGTH;
  protected readonly emailMaxLength = EMAIL_MAX_LENGTH;
  protected readonly addressMaxLength = ADDRESS_MAX_LENGTH;
  protected readonly phoneNumberMaxLength = PHONE_NUMBER_MAX_LENGTH;

  protected readonly form = this.formBuilder.nonNullable.group({
    name: [this.member?.name ?? '', [Validators.required, Validators.maxLength(NAME_MAX_LENGTH)]],
    email: [this.member?.email ?? '', [Validators.email, Validators.maxLength(EMAIL_MAX_LENGTH)]],
    address: [this.member?.address ?? '', [Validators.maxLength(ADDRESS_MAX_LENGTH)]],
    // A plain string, never a number: `+40` prefixes and leading zeros matter.
    phoneNumber: [this.member?.phoneNumber ?? '', [Validators.maxLength(PHONE_NUMBER_MAX_LENGTH)]],
  });

  protected submit(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }

    const { name, email, address, phoneNumber } = this.form.getRawValue();
    const request: MemberRequest = {
      name: name.trim(),
      // An empty box means "not provided", which the backend stores as null.
      email: blankToNull(email),
      address: blankToNull(address),
      phoneNumber: blankToNull(phoneNumber),
    };

    this.saving.set(true);
    const saved$ = this.member
      ? this.memberService.update(this.member.id, request)
      : this.memberService.create(request);

    saved$.subscribe({
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
      case 'MEMBER_NAME_ALREADY_EXISTS':
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
      case 'email':
        return this.form.controls.email;
      case 'address':
        return this.form.controls.address;
      case 'phoneNumber':
        return this.form.controls.phoneNumber;
      default:
        return null;
    }
  }
}

function blankToNull(value: string): string | null {
  const trimmed = value.trim();
  return trimmed === '' ? null : trimmed;
}
