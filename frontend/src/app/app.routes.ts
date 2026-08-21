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
  { path: '**', redirectTo: 'categories' },
];
