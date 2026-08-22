import { Component, computed, inject, OnInit, signal } from '@angular/core';
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
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { TranslationKey } from '../../../core/i18n/translations/translation-key';
import { CategoryResponse } from '../../category/category.model';
import { CategoryService } from '../../category/category.service';
import {
  BookRestoreDialog,
  BookRestoreDialogData,
} from '../book-restore-dialog/book-restore-dialog';
import { REMOVAL_REASONS } from '../book-remove-dialog/book-remove-dialog';
import { BookArchiveSearchCriteria, BookResponse, RemovalReason } from '../book.model';
import { BookService } from '../book.service';

const DEFAULT_PAGE_SIZE = 20;
const SEARCH_DEBOUNCE_MS = 300;

@Component({
  selector: 'app-book-archive-list',
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
    TranslatePipe,
  ],
  templateUrl: './book-archive-list.html',
  styleUrl: './book-archive-list.css',
})
export class BookArchiveList implements OnInit {
  private readonly bookService = inject(BookService);
  private readonly categoryService = inject(CategoryService);
  private readonly dialog = inject(MatDialog);
  private readonly notifications = inject(NotificationService);

  protected readonly books = signal<readonly BookResponse[]>([]);
  protected readonly categories = signal<readonly CategoryResponse[]>([]);
  protected readonly totalElements = signal(0);
  protected readonly loading = signal(false);
  protected readonly loadFailed = signal(false);

  protected readonly reasons = REMOVAL_REASONS;

  protected readonly criteria = signal<BookArchiveSearchCriteria>({
    search: '',
    categoryId: null,
    status: null,
    page: 0,
    size: DEFAULT_PAGE_SIZE,
    sort: 'title,asc',
  });

  /**
   * Bumped by the retry button (and by every successful restore). It rides
   * along with the criteria so that asking for the *same* page again still
   * reaches the server — `distinctUntilChanged` would otherwise swallow an
   * identical criteria object. It is never sent.
   */
  private readonly attempt = signal(0);

  private readonly request = computed(() => ({
    criteria: this.criteria(),
    attempt: this.attempt(),
  }));

  protected readonly sortActive = computed(() => this.criteria().sort?.split(',')[0] ?? 'title');
  protected readonly sortDirection = computed(
    () => (this.criteria().sort?.split(',')[1] ?? 'asc') as 'asc' | 'desc' | '',
  );

  // bookNumber is only unique among ACTIVE books (`DATA_MODEL.md` §4): an
  // archived copy's number may already have been reused by another active
  // book, so it never identifies a row here on its own. Title and author must
  // stay in this table.
  protected readonly displayedColumns = [
    'bookNumber',
    'title',
    'author',
    'categoryName',
    'reason',
    'removalNote',
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
          this.bookService.archivedList(criteria).pipe(
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
          // The row that made this page empty is gone (restored, typically the
          // last row on the last page) -- step back rather than show an
          // impossible empty page above real data. Re-enters this same pipeline,
          // so no separate fetch logic is needed.
          this.criteria.update((criteria) => ({ ...criteria, page: requestedPage - 1 }));
          return;
        }
        this.books.set(page.content);
        this.totalElements.set(page.totalElements);
        this.loading.set(false);
      });
  }

  ngOnInit(): void {
    this.categoryService.list().subscribe({
      next: (categories) => this.categories.set(categories),
      error: (error: ApiError) => this.notifications.showError(error),
    });
  }

  /**
   * The `book.status.*` keys are named after the status values, so the key is
   * derived rather than looked up — and the declared `TranslationKey` return
   * type makes a missing translation a compile error.
   */
  protected reasonLabelKey(reason: RemovalReason): TranslationKey {
    return `book.status.${reason}`;
  }

  protected retry(): void {
    this.attempt.update((n) => n + 1);
  }

  protected onSearchInput(event: Event): void {
    const search = (event.target as HTMLInputElement).value;
    // Back to page 0: the old page index is meaningless against a new result set.
    this.criteria.update((criteria) => ({ ...criteria, search, page: 0 }));
  }

  protected onCategoryChange(categoryId: number | null): void {
    this.criteria.update((criteria) => ({ ...criteria, categoryId, page: 0 }));
  }

  protected onReasonChange(status: RemovalReason | null): void {
    this.criteria.update((criteria) => ({ ...criteria, status, page: 0 }));
  }

  /**
   * Sorting is server-side. Sorting only the rows of the current page in the
   * browser would silently mis-order the archive, because the other pages are
   * not loaded.
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

  /**
   * Restores directly, with no confirmation dialog: there is no input to
   * gather for the happy path. The dialog only opens on the one error that
   * needs one — a number now taken by another active book.
   */
  protected restore(book: BookResponse): void {
    this.bookService.restore(book.id).subscribe({
      next: () => this.retry(),
      error: (error: ApiError) => {
        if (error.code === 'BOOK_NUMBER_TAKEN_ON_RESTORE') {
          this.openRestoreDialog(book);
          return;
        }
        this.notifications.showError(error);
      },
    });
  }

  private openRestoreDialog(book: BookResponse): void {
    this.dialog
      .open<BookRestoreDialog, BookRestoreDialogData, BookResponse>(BookRestoreDialog, {
        data: book,
      })
      .afterClosed()
      .subscribe((restored) => {
        if (restored) {
          // Re-fetch rather than drop the row locally: the archive is filtered
          // server-side, and that filter is what makes the restored row
          // disappear — and the following page shift up — correctly.
          this.retry();
        }
      });
  }
}
