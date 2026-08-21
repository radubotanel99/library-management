import { DecimalPipe } from '@angular/common';
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
import { CategoryResponse } from '../../category/category.model';
import { CategoryService } from '../../category/category.service';
import { BookFormDialog, BookFormDialogData } from '../book-form-dialog/book-form-dialog';
import { BookResponse, BookSearchCriteria } from '../book.model';
import { BookService } from '../book.service';

const DEFAULT_PAGE_SIZE = 20;
const SEARCH_DEBOUNCE_MS = 300;

@Component({
  selector: 'app-book-list',
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
    DecimalPipe,
    TranslatePipe,
  ],
  templateUrl: './book-list.html',
  styleUrl: './book-list.css',
})
export class BookList implements OnInit {
  private readonly bookService = inject(BookService);
  private readonly categoryService = inject(CategoryService);
  private readonly dialog = inject(MatDialog);
  private readonly notifications = inject(NotificationService);

  protected readonly books = signal<readonly BookResponse[]>([]);
  protected readonly categories = signal<readonly CategoryResponse[]>([]);
  protected readonly totalElements = signal(0);
  protected readonly loading = signal(false);
  protected readonly loadFailed = signal(false);

  protected readonly criteria = signal<BookSearchCriteria>({
    search: '',
    categoryId: null,
    page: 0,
    size: DEFAULT_PAGE_SIZE,
    sort: 'title,asc',
  });

  /**
   * Bumped by the retry button. It rides along with the criteria so that asking
   * for the *same* page again still reaches the server — `distinctUntilChanged`
   * would otherwise swallow an identical criteria object. It is never sent.
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

  // bookNumber is only unique among ACTIVE books (`DATA_MODEL.md` §4, and the
  // "things that look like bugs" list in CLAUDE.md): a removed copy frees its
  // number for reuse. So the number alone never identifies a book to a human —
  // title and author must stay in this table. Do not strip them for a
  // "compact" view.
  protected readonly displayedColumns = [
    'bookNumber',
    'title',
    'author',
    'categoryName',
    'publisher',
    'price',
    'onLoan',
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
          this.bookService.list(criteria).pipe(
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

  /**
   * Sorting is server-side. Sorting only the rows of the current page in the
   * browser would silently mis-order the catalogue, because the other pages are
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

  protected openCreateDialog(): void {
    this.openFormDialog(null);
  }

  protected openEditDialog(book: BookResponse): void {
    this.openFormDialog(book);
  }

  private openFormDialog(book: BookFormDialogData): void {
    this.dialog
      .open<BookFormDialog, BookFormDialogData, BookResponse>(BookFormDialog, { data: book })
      .afterClosed()
      .subscribe((saved) => {
        if (saved) {
          // Re-fetch rather than patch the array: with server-side paging and
          // sorting, the saved row may belong on a different page entirely.
          this.retry();
        }
      });
  }
}
