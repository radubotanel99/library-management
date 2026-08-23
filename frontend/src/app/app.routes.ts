import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', redirectTo: 'categories', pathMatch: 'full' },
  {
    path: 'categories',
    loadComponent: () =>
      import('./features/category/category-list/category-list').then((m) => m.CategoryList),
  },
  {
    path: 'books',
    loadComponent: () => import('./features/book/book-list/book-list').then((m) => m.BookList),
  },
  {
    path: 'books/archive',
    loadComponent: () =>
      import('./features/book/book-archive-list/book-archive-list').then(
        (m) => m.BookArchiveList,
      ),
  },
  {
    path: 'members',
    loadComponent: () =>
      import('./features/member/member-list/member-list').then((m) => m.MemberList),
  },
  {
    path: 'loans',
    loadComponent: () => import('./features/loan/loan-list/loan-list').then((m) => m.LoanList),
  },
  {
    path: 'settings',
    loadComponent: () =>
      import('./features/settings/settings-page/settings-page').then((m) => m.SettingsPage),
  },
  { path: '**', redirectTo: 'categories' },
];
