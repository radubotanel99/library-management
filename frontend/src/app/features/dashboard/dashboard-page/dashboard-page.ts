import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';
import { ApiError } from '../../../core/error/api-error';
import { NotificationService } from '../../../core/error/notification.service';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { LoanLendDialog } from '../../loan/loan-lend-dialog/loan-lend-dialog';
import { LoanResponse } from '../../loan/loan.model';
import { DashboardResponse } from '../dashboard.model';
import { DashboardHeroArt } from '../dashboard-hero-art/dashboard-hero-art';
import { DashboardSearch } from '../dashboard-search/dashboard-search';
import { DashboardService } from '../dashboard.service';

/** Generated cover colours for the most-borrowed list, by rank (design-system `la-cover`). */
const COVER_CLASSES = ['', 'la-cover--ink', 'la-cover--gold', 'la-cover--teal', 'la-cover--oxblood'] as const;

/**
 * The landing screen: a hero with the two desk actions, four figures
 * (design-system StatTiles), the most-borrowed list (RankedList) and a ring
 * of open loans split into on time / overdue — all from one request.
 *
 * Nothing here is computed client-side. `loansOverdue` in particular is derived by
 * the backend from `borrowedAt + DAYS_TO_KEEP_A_BOOK` (`API_CONTRACT.md` §10) — a
 * wrong browser clock must never decide what a librarian sees as late, and the due
 * date is not stored anywhere to recompute from (`DATA_MODEL.md` §6).
 *
 * Only what `GET /api/dashboard` returns is shown: no covers, deltas or "See
 * all" link (`THEME-SETUP.md`, "Decision — Phase 7b scope").
 */
@Component({
  selector: 'app-dashboard-page',
  imports: [
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    RouterLink,
    DashboardSearch,
    DashboardHeroArt,
    DatePipe,
    DecimalPipe,
    TranslatePipe,
  ],
  host: { class: 'app-page' },
  templateUrl: './dashboard-page.html',
  styleUrl: './dashboard-page.css',
})
export class DashboardPage implements OnInit {
  private readonly dashboardService = inject(DashboardService);
  private readonly notifications = inject(NotificationService);
  private readonly dialog = inject(MatDialog);

  protected readonly stats = signal<DashboardResponse | null>(null);
  protected readonly loading = signal(false);
  protected readonly loadFailed = signal(false);

  /** Number formatting follows the UI language (12,480 / 12.480). */
  protected readonly locale = inject(I18nService).locale;

  /** The template iterates an array, and the null-before-first-load case is not one. */
  protected readonly mostBorrowed = computed(() => this.stats()?.mostBorrowed ?? []);

  /** Shown above the title; formatted per UI language by DatePipe. */
  protected readonly today = new Date();

  /** Open loans = on time + overdue, both exactly as the server sent them. */
  protected readonly openLoans = computed(() => {
    const stats = this.stats();
    return stats ? stats.loansActive + stats.loansOverdue : 0;
  });

  /** Overdue share of the open loans, 0–100, for the ring chart. */
  protected readonly overduePercent = computed(() => {
    const open = this.openLoans();
    return open === 0 ? 0 : ((this.stats()?.loansOverdue ?? 0) / open) * 100;
  });

  private readonly maxLoanCount = computed(() =>
    Math.max(1, ...this.mostBorrowed().map((book) => book.loanCount)),
  );

  ngOnInit(): void {
    this.load();
  }

  /** Bar length relative to the most-borrowed book, so the leader fills its bar. */
  protected barWidth(loanCount: number): number {
    return (loanCount / this.maxLoanCount()) * 100;
  }

  protected coverClass(rank: number): string {
    return COVER_CLASSES[rank % COVER_CLASSES.length];
  }

  /** The same lend dialog as the loans screen; the figures refresh after a loan is made. */
  protected openLendDialog(): void {
    this.dialog
      .open<LoanLendDialog, undefined, LoanResponse>(LoanLendDialog)
      .afterClosed()
      .subscribe((created) => {
        if (created) {
          this.load();
        }
      });
  }

  protected load(): void {
    this.loading.set(true);
    this.loadFailed.set(false);
    this.dashboardService.load().subscribe({
      next: (stats) => {
        this.stats.set(stats);
        this.loading.set(false);
      },
      error: (error: ApiError) => {
        this.loading.set(false);
        this.loadFailed.set(true);
        this.notifications.showError(error);
      },
    });
  }
}
