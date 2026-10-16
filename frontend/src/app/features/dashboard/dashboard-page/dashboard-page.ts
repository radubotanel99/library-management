import { DecimalPipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ApiError } from '../../../core/error/api-error';
import { NotificationService } from '../../../core/error/notification.service';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { DashboardResponse } from '../dashboard.model';
import { DashboardService } from '../dashboard.service';

/**
 * The landing screen: four figures (design-system StatTiles) and the
 * most-borrowed list (RankedList), from one request.
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
  imports: [MatButtonModule, MatIconModule, MatProgressBarModule, DecimalPipe, TranslatePipe],
  host: { class: 'app-page' },
  templateUrl: './dashboard-page.html',
  styleUrl: './dashboard-page.css',
})
export class DashboardPage implements OnInit {
  private readonly dashboardService = inject(DashboardService);
  private readonly notifications = inject(NotificationService);

  protected readonly stats = signal<DashboardResponse | null>(null);
  protected readonly loading = signal(false);
  protected readonly loadFailed = signal(false);

  /** Number formatting follows the UI language (12,480 / 12.480). */
  protected readonly locale = inject(I18nService).locale;

  /** The template iterates an array, and the null-before-first-load case is not one. */
  protected readonly mostBorrowed = computed(() => this.stats()?.mostBorrowed ?? []);

  ngOnInit(): void {
    this.load();
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
