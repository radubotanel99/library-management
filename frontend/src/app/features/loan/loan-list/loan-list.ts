import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { catchError, debounceTime, distinctUntilChanged, EMPTY, switchMap, tap } from 'rxjs';
import { ApiError } from '../../../core/error/api-error';
import { NotificationService } from '../../../core/error/notification.service';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { provideTranslatedPaginatorIntl } from '../../../core/i18n/translated-paginator-intl';
import { TranslationKey } from '../../../core/i18n/translations/translation-key';
import { breakpointMatches, TABLET_LAYOUT } from '../../../core/layout/breakpoints';
import { ConfirmDialog, ConfirmDialogData } from '../../../core/ui/confirm-dialog/confirm-dialog';
import { StatusBadge } from '../../../core/ui/status-badge/status-badge';
import { LoanLendDialog } from '../loan-lend-dialog/loan-lend-dialog';
import { LoanResponse, LoanSearchCriteria, LoanState, LoanStateFilter } from '../loan.model';
import { LoanService } from '../loan.service';

const DEFAULT_PAGE_SIZE = 20;
const SEARCH_DEBOUNCE_MS = 300;

/**
 * Short day-month-year, as the design system asks ("29 Oct 2026"). The month
 * name comes from the locale passed alongside it, so it reads "29 oct. 2026"
 * in Romanian and switches with the UI language.
 */
const DATE_FORMAT = 'd MMM y';

// The book column carries title, author and number together: a book number is
// unique only among ACTIVE copies (`DATA_MODEL.md` §4), so it identifies
// nothing on its own. Do not split it out for a "compact" view.
//
// Only borrowedAt (the loan's createdAt) and finishedAt are sortable. state is
// a stored column the server refuses to order by, since it can disagree with
// the derived ACTIVE/LATE value shown next to it; the rest are either joined
// columns the server cannot order by, or derived values with no column at all
// (dueAt, daysOverdue).
const COLUMNS = [
  'book',
  'member',
  'state',
  'borrowedAt',
  'dueAt',
  'daysOverdue',
  'finishedAt',
  'actions',
] as const;

/**
 * Dropped at tablet width. What stays answers the desk question — which copy,
 * who has it, is it late, when is it due — and keeps the return action.
 */
const LOW_PRIORITY_COLUMNS: ReadonlySet<string> = new Set([
  'borrowedAt',
  'daysOverdue',
  'finishedAt',
]);

/**
 * `OPEN` first and selected by default: the working view of this screen is what
 * is still out, not the full history. It is the alias for `ACTIVE` + `LATE`
 * (`API_CONTRACT.md` §8).
 */
export const STATE_FILTERS: readonly LoanStateFilter[] = ['OPEN', 'ACTIVE', 'LATE', 'FINISHED'];

@Component({
  selector: 'app-loan-list',
  imports: [
    MatTableModule,
    MatSortModule,
    MatPaginatorModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    MatProgressBarModule,
    DatePipe,
    DecimalPipe,
    StatusBadge,
    TranslatePipe,
  ],
  host: { class: 'app-page' },
  providers: [provideTranslatedPaginatorIntl()],
  templateUrl: './loan-list.html',
})
export class LoanList {
  private readonly loanService = inject(LoanService);
  private readonly dialog = inject(MatDialog);
  private readonly notifications = inject(NotificationService);

  protected readonly loans = signal<readonly LoanResponse[]>([]);
  protected readonly totalElements = signal(0);
  protected readonly loading = signal(false);
  protected readonly loadFailed = signal(false);

  protected readonly dateFormat = DATE_FORMAT;
  protected readonly stateFilters = STATE_FILTERS;

  protected readonly criteria = signal<LoanSearchCriteria>({
    state: 'OPEN',
    search: '',
    page: 0,
    size: DEFAULT_PAGE_SIZE,
    // Newest first: the loans a librarian just recorded are the ones they check.
    sort: 'createdAt,desc',
  });

  /**
   * Bumped by the retry button and after every write. It rides along with the
   * criteria so that asking for the *same* page again still reaches the server —
   * `distinctUntilChanged` would otherwise swallow an identical criteria object.
   * It is never sent.
   */
  private readonly attempt = signal(0);

  private readonly request = computed(() => ({
    criteria: this.criteria(),
    attempt: this.attempt(),
  }));

  protected readonly sortActive = computed(
    () => this.criteria().sort?.split(',')[0] ?? 'createdAt',
  );
  protected readonly sortDirection = computed(
    () => (this.criteria().sort?.split(',')[1] ?? 'desc') as 'asc' | 'desc' | '',
  );

  /** Dates and numbers follow the UI language. */
  protected readonly locale = inject(I18nService).locale;

  private readonly tablet = breakpointMatches(TABLET_LAYOUT);

  protected readonly displayedColumns = computed(() =>
    this.tablet() ? COLUMNS.filter((column) => !LOW_PRIORITY_COLUMNS.has(column)) : [...COLUMNS],
  );

  constructor() {
    toObservable(this.request)
      .pipe(
        debounceTime(SEARCH_DEBOUNCE_MS),
        distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)),
        tap(() => {
          this.loading.set(true);
          this.loadFailed.set(false);
        }),
        // switchMap, not mergeMap: a slow response for an earlier keystroke must
        // never overwrite the results of a later, faster one.
        switchMap(({ criteria }) =>
          this.loanService.list(criteria).pipe(
            catchError((error: ApiError) => {
              this.loading.set(false);
              this.loadFailed.set(true);
              this.notifications.showError(error);
              // EMPTY rather than a rethrow: an error must not tear down the
              // stream, or the search box would stop working after one failure.
              return EMPTY;
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((page) => {
        const requestedPage = this.criteria().page ?? 0;
        if (page.content.length === 0 && page.totalElements > 0 && requestedPage > 0) {
          // The row that made this page empty is gone (returned, typically the
          // last row on the last page of the OPEN filter) -- step back rather
          // than show an impossible empty page above real data. Re-enters this
          // same pipeline, so no separate fetch logic is needed.
          this.criteria.update((criteria) => ({ ...criteria, page: requestedPage - 1 }));
          return;
        }
        this.loans.set(page.content);
        this.totalElements.set(page.totalElements);
        this.loading.set(false);
      });
  }

  /**
   * The `loan.state.*` keys are named after the state values, so the key is
   * derived rather than looked up — and the declared `TranslationKey` return type
   * makes a missing translation a compile error.
   */
  protected stateLabelKey(state: LoanStateFilter): TranslationKey {
    return `loan.state.${state}`;
  }

  /** Only an open loan can come back; a finished one has nothing to return. */
  protected isOpen(state: LoanState): boolean {
    return state !== 'FINISHED';
  }

  protected retry(): void {
    this.attempt.update((n) => n + 1);
  }

  protected onSearchInput(event: Event): void {
    const search = (event.target as HTMLInputElement).value;
    // Back to page 0: the old page index is meaningless against a new result set.
    this.criteria.update((criteria) => ({ ...criteria, search, page: 0 }));
  }

  protected onStateChange(state: LoanStateFilter): void {
    this.criteria.update((criteria) => ({ ...criteria, state, page: 0 }));
  }

  /**
   * Sorting is server-side. Sorting only the rows of the current page in the
   * browser would silently mis-order the list, because the other pages are not
   * loaded.
   */
  protected onSortChange(sort: Sort): void {
    const value = sort.direction ? `${sort.active},${sort.direction}` : undefined;
    this.criteria.update((criteria) => ({ ...criteria, sort: value, page: 0 }));
  }

  protected onPageChange(event: PageEvent): void {
    this.criteria.update((criteria) => ({
      ...criteria,
      page: event.pageIndex,
      size: event.pageSize,
    }));
  }

  protected openLendDialog(): void {
    this.dialog
      .open<LoanLendDialog, undefined, LoanResponse>(LoanLendDialog)
      .afterClosed()
      .subscribe((created) => {
        if (created) {
          // Re-fetch rather than push the row onto the array: with server-side
          // paging and sorting, the new loan may belong on a different page —
          // and under the FINISHED filter it does not belong here at all.
          this.retry();
        }
      });
  }

  protected confirmReturn(loan: LoanResponse): void {
    const data: ConfirmDialogData = {
      titleKey: 'loan.return.title',
      messageKey: 'loan.return.message',
      messageParams: { title: loan.bookTitle, member: loan.memberName },
      confirmLabelKey: 'loan.return.confirm',
    };
    this.dialog
      .open<ConfirmDialog, ConfirmDialogData, boolean>(ConfirmDialog, { data })
      .afterClosed()
      .subscribe((confirmed) => {
        if (confirmed) {
          this.returnLoan(loan);
        }
      });
  }

  private returnLoan(loan: LoanResponse): void {
    this.loanService.returnLoan(loan.id).subscribe({
      // Re-fetch rather than patch the row locally: under the default OPEN
      // filter the returned loan leaves the result set entirely, and that is
      // what makes the following page shift up correctly.
      next: () => this.retry(),
      error: (error: ApiError) => {
        this.notifications.showError(error);
        if (error.code === 'LOAN_ALREADY_FINISHED' || error.code === 'LOAN_NOT_FOUND') {
          // The page was loaded before someone else handled this loan; refresh
          // so the row catches up with reality.
          this.retry();
        }
      },
    });
  }
}
