import { Component, computed, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import type { BookStatus } from '../../../features/book/book.model';
import type { LoanState } from '../../../features/loan/loan.model';
import { TranslatePipe } from '../../i18n/translate.pipe';
import { TranslationKey } from '../../i18n/translations/translation-key';

/** The design system's badge colours (`la-badge--*`). `due` is not used: there is no "due soon" state. */
export type BadgeVariant = 'available' | 'loan' | 'overdue' | 'muted';

/** Everything a badge renders, derived from a state. */
export interface BadgeSpec {
  variant: BadgeVariant;
  /** Material Symbols ligature, or `null` for a word-only badge. */
  icon: string | null;
  labelKey: TranslationKey;
}

/**
 * Loan state → badge (`THEME-SETUP.md`, "Badge mapping").
 *
 * Takes the server's `state` as-is. Overdue is never derived here from dates:
 * the backend re-evaluates it against DAYS_TO_KEEP_A_BOOK (`API_CONTRACT.md`
 * §11), and a wrong browser clock must not decide what is late.
 */
export function loanStateBadge(state: LoanState): BadgeSpec {
  switch (state) {
    case 'ACTIVE':
      return { variant: 'loan', icon: 'swap_horiz', labelKey: 'loan.state.ACTIVE' };
    case 'LATE':
      return { variant: 'overdue', icon: 'warning', labelKey: 'loan.state.LATE' };
    case 'FINISHED':
      return { variant: 'available', icon: 'check', labelKey: 'loan.state.FINISHED' };
  }
}

/**
 * Book copy → badge. An active copy is either on loan or on the shelf (the
 * server's `onLoan` flag); a removed copy shows its reason, muted and word-only.
 */
export function bookBadge(status: BookStatus, onLoan: boolean): BadgeSpec {
  switch (status) {
    case 'ACTIVE':
      return onLoan
        ? { variant: 'loan', icon: 'swap_horiz', labelKey: 'book.onLoan.yes' }
        : { variant: 'available', icon: 'check', labelKey: 'book.onLoan.no' };
    case 'LOST':
      return { variant: 'muted', icon: null, labelKey: 'book.status.LOST' };
    case 'DAMAGED':
      return { variant: 'muted', icon: null, labelKey: 'book.status.DAMAGED' };
    case 'WITHDRAWN':
      return { variant: 'muted', icon: null, labelKey: 'book.status.WITHDRAWN' };
  }
}

/**
 * The design-system StatusBadge: one copy's or one loan's state as an icon and
 * a word, never colour alone.
 *
 * Give it either `loanState`, or `bookStatus` (plus `onLoan` for an active
 * copy); the label and icon follow from the state.
 */
@Component({
  selector: 'app-status-badge',
  imports: [MatIconModule, TranslatePipe],
  template: `
    @if (spec(); as badge) {
      <span class="la-badge" [class]="'la-badge--' + badge.variant">
        @if (badge.icon) {
          <mat-icon aria-hidden="true">{{ badge.icon }}</mat-icon>
        }
        {{ badge.labelKey | translate }}
      </span>
    }
  `,
  styleUrl: './status-badge.css',
})
export class StatusBadge {
  readonly loanState = input<LoanState | null>(null);
  readonly bookStatus = input<BookStatus | null>(null);
  readonly onLoan = input(false);

  protected readonly spec = computed<BadgeSpec | null>(() => {
    const loanState = this.loanState();
    if (loanState) {
      return loanStateBadge(loanState);
    }
    const bookStatus = this.bookStatus();
    return bookStatus ? bookBadge(bookStatus, this.onLoan()) : null;
  });
}
