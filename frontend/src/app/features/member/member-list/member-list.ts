import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { catchError, debounceTime, distinctUntilChanged, EMPTY, switchMap, tap } from 'rxjs';
import { ApiError } from '../../../core/error/api-error';
import { NotificationService } from '../../../core/error/notification.service';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { ConfirmDialog, ConfirmDialogData } from '../../../core/ui/confirm-dialog/confirm-dialog';
import { MemberFormDialog, MemberFormDialogData } from '../member-form-dialog/member-form-dialog';
import { MemberResponse, MemberSearchCriteria } from '../member.model';
import { MemberService } from '../member.service';

const DEFAULT_PAGE_SIZE = 20;
const SEARCH_DEBOUNCE_MS = 300;

@Component({
  selector: 'app-member-list',
  imports: [
    MatTableModule,
    MatSortModule,
    MatPaginatorModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    MatProgressBarModule,
    TranslatePipe,
  ],
  templateUrl: './member-list.html',
  styleUrl: './member-list.css',
})
export class MemberList {
  private readonly memberService = inject(MemberService);
  private readonly dialog = inject(MatDialog);
  private readonly notifications = inject(NotificationService);

  protected readonly members = signal<readonly MemberResponse[]>([]);
  protected readonly totalElements = signal(0);
  protected readonly loading = signal(false);
  protected readonly loadFailed = signal(false);

  protected readonly criteria = signal<MemberSearchCriteria>({
    search: '',
    page: 0,
    size: DEFAULT_PAGE_SIZE,
    sort: 'name,asc',
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

  protected readonly sortActive = computed(() => this.criteria().sort?.split(',')[0] ?? 'name');
  protected readonly sortDirection = computed(
    () => (this.criteria().sort?.split(',')[1] ?? 'asc') as 'asc' | 'desc' | '',
  );

  // openLoanCount is shown but not sortable: it is not a column on `member` but
  // the result of a grouped query over loans, so the server cannot order by it.
  protected readonly displayedColumns = [
    'name',
    'email',
    'address',
    'phoneNumber',
    'openLoanCount',
    'actions',
  ];

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
          this.memberService.list(criteria).pipe(
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
          // The row that made this page empty is gone (archived, typically the
          // last row on the last page) -- step back rather than show an
          // impossible empty page above real data. Re-enters this same pipeline,
          // so no separate fetch logic is needed.
          this.criteria.update((criteria) => ({ ...criteria, page: requestedPage - 1 }));
          return;
        }
        this.members.set(page.content);
        this.totalElements.set(page.totalElements);
        this.loading.set(false);
      });
  }

  protected retry(): void {
    this.attempt.update((n) => n + 1);
  }

  protected onSearchInput(event: Event): void {
    const search = (event.target as HTMLInputElement).value;
    // Back to page 0: the old page index is meaningless against a new result set.
    this.criteria.update((criteria) => ({ ...criteria, search, page: 0 }));
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

  protected openCreateDialog(): void {
    this.openFormDialog(null);
  }

  protected openEditDialog(member: MemberResponse): void {
    this.openFormDialog(member);
  }

  /**
   * The button is disabled for a member who still holds a book, so this normally
   * only opens for an archivable one; the server's `MEMBER_HAS_OPEN_LOANS` covers
   * the stale-list race.
   */
  protected confirmDelete(member: MemberResponse): void {
    const data: ConfirmDialogData = {
      titleKey: 'member.delete.title',
      messageKey: 'member.delete.message',
      messageParams: { name: member.name },
      confirmLabelKey: 'common.delete',
    };
    this.dialog
      .open<ConfirmDialog, ConfirmDialogData, boolean>(ConfirmDialog, { data })
      .afterClosed()
      .subscribe((confirmed) => {
        if (confirmed) {
          this.delete(member);
        }
      });
  }

  private openFormDialog(member: MemberFormDialogData): void {
    this.dialog
      .open<MemberFormDialog, MemberFormDialogData, MemberResponse>(MemberFormDialog, {
        data: member,
      })
      .afterClosed()
      .subscribe((saved) => {
        if (saved) {
          // Re-fetch rather than patch the array: with server-side paging and
          // sorting, the saved row may belong on a different page entirely.
          this.retry();
        }
      });
  }

  private delete(member: MemberResponse): void {
    this.memberService.delete(member.id).subscribe({
      // Re-fetch rather than drop the row locally: the list is filtered
      // server-side to active members, and that filter is what makes the row
      // disappear -- and the following page shift up -- correctly.
      next: () => this.retry(),
      error: (error: ApiError) => {
        if (error.code === 'MEMBER_HAS_OPEN_LOANS') {
          // The page was loaded before someone lent this member a book. Say what
          // the screen means -- "you cannot archive them" -- rather than the
          // generic mapping, and refresh so the count on the row catches up.
          this.notifications.showMessage('member.delete.blocked');
          this.retry();
          return;
        }
        this.notifications.showError(error);
      },
    });
  }
}
