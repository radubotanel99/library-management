import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { ApiError } from '../../../core/error/api-error';
import { NotificationService } from '../../../core/error/notification.service';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { DashboardResponse } from '../dashboard.model';
import { DashboardService } from '../dashboard.service';

/** Matches the `matColumnDef` names in the template. */
const MOST_BORROWED_COLUMNS = ['title', 'author', 'loanCount'] as const;

/**
 * The landing screen: four figures and the most-borrowed list, from one request.
 *
 * Nothing here is computed client-side. `loansOverdue` in particular is derived by
 * the backend from `borrowedAt + DAYS_TO_KEEP_A_BOOK` (`API_CONTRACT.md` §10) — a
 * wrong browser clock must never decide what a librarian sees as late, and the due
 * date is not stored anywhere to recompute from (`DATA_MODEL.md` §6).
 */
@Component({
  selector: 'app-dashboard-page',
  imports: [MatCardModule, MatTableModule, MatButtonModule, MatProgressBarModule, TranslatePipe],
  templateUrl: './dashboard-page.html',
  styleUrl: './dashboard-page.css',
})
export class DashboardPage implements OnInit {
  private readonly dashboardService = inject(DashboardService);
  private readonly notifications = inject(NotificationService);

  protected readonly stats = signal<DashboardResponse | null>(null);
  protected readonly loading = signal(false);
  protected readonly loadFailed = signal(false);

  protected readonly displayedColumns = MOST_BORROWED_COLUMNS;

  /** `mat-table` wants an array, and the null-before-first-load case is not one. */
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
