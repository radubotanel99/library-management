import { Component, computed, ElementRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, forkJoin, map, of, Subject, switchMap } from 'rxjs';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { StatusBadge } from '../../../core/ui/status-badge/status-badge';
import { BookResponse } from '../../book/book.model';
import { BookService } from '../../book/book.service';
import { LoanResponse } from '../../loan/loan.model';
import { LoanService } from '../../loan/loan.service';
import { MemberResponse } from '../../member/member.model';
import { MemberService } from '../../member/member.service';

/** Same debounce as the list screens' search boxes. */
const SEARCH_DEBOUNCE_MS = 300;
/** Fewer characters match almost everything and only add noise. */
const MIN_QUERY_LENGTH = 2;
/** A quick look-up, not a list: the full results live on each screen. */
const RESULTS_PER_GROUP = 3;

interface SearchResults {
  books: readonly BookResponse[];
  members: readonly MemberResponse[];
  loans: readonly LoanResponse[];
}

type SearchState =
  | { kind: 'idle' }
  | { kind: 'loading' }
  | { kind: 'failed' }
  | { kind: 'done'; results: SearchResults };

/**
 * The dashboard's quick search: one box that looks up books, members and open
 * loans at once, through the same services and `search` parameters the list
 * screens use (`API_CONTRACT.md` §5, §7, §8). No new endpoint — three existing
 * list calls, each limited to a few rows.
 */
@Component({
  selector: 'app-dashboard-search',
  imports: [MatIconModule, MatProgressBarModule, RouterLink, StatusBadge, TranslatePipe],
  templateUrl: './dashboard-search.html',
  styleUrl: './dashboard-search.css',
  host: {
    '(document:click)': 'onDocumentClick($event)',
    '(keydown.escape)': 'close()',
  },
})
export class DashboardSearch {
  private readonly bookService = inject(BookService);
  private readonly memberService = inject(MemberService);
  private readonly loanService = inject(LoanService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  protected readonly query = signal('');
  protected readonly open = signal(false);
  protected readonly state = signal<SearchState>({ kind: 'idle' });

  protected readonly results = computed(() => {
    const state = this.state();
    return state.kind === 'done' ? state.results : null;
  });

  protected readonly empty = computed(() => {
    const results = this.results();
    return !!results && !results.books.length && !results.members.length && !results.loans.length;
  });

  private readonly queries = new Subject<string>();

  constructor() {
    this.queries
      .pipe(
        map((query) => query.trim()),
        debounceTime(SEARCH_DEBOUNCE_MS),
        distinctUntilChanged(),
        switchMap((query) => {
          if (query.length < MIN_QUERY_LENGTH) {
            return of<SearchState>({ kind: 'idle' });
          }
          this.state.set({ kind: 'loading' });
          return this.search(query);
        }),
        takeUntilDestroyed(),
      )
      .subscribe((state) => this.state.set(state));
  }

  protected onInput(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.query.set(value);
    this.open.set(true);
    this.queries.next(value);
  }

  protected onFocus(): void {
    if (this.query().trim().length >= MIN_QUERY_LENGTH) {
      this.open.set(true);
    }
  }

  protected close(): void {
    this.open.set(false);
  }

  /** A click anywhere outside the search closes its results. */
  protected onDocumentClick(event: MouseEvent): void {
    if (!this.host.nativeElement.contains(event.target as Node)) {
      this.close();
    }
  }

  protected showPanel(): boolean {
    return this.open() && this.query().trim().length >= MIN_QUERY_LENGTH;
  }

  private search(query: string) {
    const page = { search: query, page: 0, size: RESULTS_PER_GROUP };
    return forkJoin({
      books: this.bookService.list(page).pipe(map((response) => response.content)),
      members: this.memberService.list(page).pipe(map((response) => response.content)),
      // Open loans only: the desk asks "who has this right now", not for history.
      loans: this.loanService
        .list({ ...page, state: 'OPEN' })
        .pipe(map((response) => response.content)),
    }).pipe(
      map((results): SearchState => ({ kind: 'done', results })),
      // A failed look-up shows an inline message; no snackbar for a quick search.
      catchError(() => of<SearchState>({ kind: 'failed' })),
    );
  }
}
