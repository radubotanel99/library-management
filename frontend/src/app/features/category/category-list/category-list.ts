import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ApiError } from '../../../core/error/api-error';
import { NotificationService } from '../../../core/error/notification.service';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { ConfirmDialog, ConfirmDialogData } from '../../../core/ui/confirm-dialog/confirm-dialog';
import {
  CategoryFormDialog,
  CategoryFormDialogData,
} from '../category-form-dialog/category-form-dialog';
import { CategoryResponse } from '../category.model';
import { CategoryService } from '../category.service';

@Component({
  selector: 'app-category-list',
  imports: [
    MatTableModule,
    MatSortModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    MatProgressBarModule,
    TranslatePipe,
  ],
  templateUrl: './category-list.html',
  styleUrl: './category-list.css',
})
export class CategoryList implements OnInit {
  private readonly categoryService = inject(CategoryService);
  private readonly dialog = inject(MatDialog);
  private readonly notifications = inject(NotificationService);

  protected readonly categories = signal<readonly CategoryResponse[]>([]);
  protected readonly loading = signal(false);
  protected readonly loadFailed = signal(false);
  protected readonly sort = signal<Sort>({ active: 'name', direction: 'asc' });

  // No paginator: the endpoint returns a plain array (`API_CONTRACT.md` §6),
  // so sorting is a pure function of the loaded rows.
  protected readonly sortedCategories = computed(() =>
    sortCategories(this.categories(), this.sort()),
  );

  protected readonly displayedColumns = ['name', 'description', 'bookCount', 'actions'];

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.loadFailed.set(false);
    this.categoryService.list().subscribe({
      next: (categories) => {
        this.categories.set(categories);
        this.loading.set(false);
      },
      error: (error: ApiError) => {
        this.loading.set(false);
        this.loadFailed.set(true);
        this.notifications.showError(error);
      },
    });
  }

  protected onSortChange(sort: Sort): void {
    this.sort.set(sort);
  }

  protected openCreateDialog(): void {
    this.openFormDialog(null);
  }

  protected openEditDialog(category: CategoryResponse): void {
    this.openFormDialog(category);
  }

  protected confirmDelete(category: CategoryResponse): void {
    const data: ConfirmDialogData = {
      titleKey: 'category.delete.title',
      messageKey: 'category.delete.message',
      messageParams: { name: category.name },
      confirmLabelKey: 'common.delete',
    };
    this.dialog
      .open<ConfirmDialog, ConfirmDialogData, boolean>(ConfirmDialog, { data })
      .afterClosed()
      .subscribe((confirmed) => {
        if (confirmed) {
          this.delete(category);
        }
      });
  }

  private openFormDialog(category: CategoryFormDialogData): void {
    this.dialog
      .open<CategoryFormDialog, CategoryFormDialogData, CategoryResponse>(CategoryFormDialog, {
        data: category,
      })
      .afterClosed()
      .subscribe((saved) => {
        if (saved) {
          this.apply(saved);
        }
      });
  }

  /** Create and update both return the saved row, so the table updates in place. */
  private apply(saved: CategoryResponse): void {
    this.categories.update((categories) =>
      categories.some((category) => category.id === saved.id)
        ? categories.map((category) => (category.id === saved.id ? saved : category))
        : [...categories, saved],
    );
  }

  private delete(category: CategoryResponse): void {
    this.categoryService.delete(category.id).subscribe({
      next: () =>
        this.categories.update((categories) => categories.filter((c) => c.id !== category.id)),
      // CATEGORY_HAS_BOOKS can still come back if a book was added meanwhile,
      // even though the button is disabled for a non-zero count.
      error: (error: ApiError) => this.notifications.showError(error),
    });
  }
}

function sortCategories(
  categories: readonly CategoryResponse[],
  sort: Sort,
): readonly CategoryResponse[] {
  if (!sort.direction) {
    return categories;
  }
  const factor = sort.direction === 'asc' ? 1 : -1;
  return [...categories].sort((a, b) => factor * compare(a, b, sort.active));
}

function compare(a: CategoryResponse, b: CategoryResponse, column: string): number {
  switch (column) {
    case 'description':
      return (a.description ?? '').localeCompare(b.description ?? '');
    case 'bookCount':
      return a.bookCount - b.bookCount;
    default:
      return a.name.localeCompare(b.name);
  }
}
