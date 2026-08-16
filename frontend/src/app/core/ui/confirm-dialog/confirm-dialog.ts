import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { TranslatePipe } from '../../i18n/translate.pipe';
import { TranslationKey } from '../../i18n/translations/translation-key';

/**
 * Everything is passed as translation keys, never as text: the dialog is opened
 * from a component, but the wording is resolved here through the pipe like any
 * other template.
 */
export interface ConfirmDialogData {
  titleKey: TranslationKey;
  messageKey: TranslationKey;
  messageParams?: Record<string, string | number>;
  confirmLabelKey: TranslationKey;
}

/** Generic yes/no dialog. Closes with `true` when confirmed, `undefined` otherwise. */
@Component({
  selector: 'app-confirm-dialog',
  imports: [MatDialogModule, MatButtonModule, TranslatePipe],
  templateUrl: './confirm-dialog.html',
  styleUrl: './confirm-dialog.css',
})
export class ConfirmDialog {
  protected readonly data = inject<ConfirmDialogData>(MAT_DIALOG_DATA);
}
