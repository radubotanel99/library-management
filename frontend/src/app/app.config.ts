import { registerLocaleData } from '@angular/common';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import localeRo from '@angular/common/locales/ro';
import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { MatIconRegistry } from '@angular/material/icon';
import { provideRouter } from '@angular/router';

import { apiErrorInterceptor } from './core/error/api-error.interceptor';
import { routes } from './app.routes';

// `en-US` is built into Angular; Romanian number and date formats (12.480,
// "29 oct. 2026") must be registered before a pipe asks for the `ro` locale.
registerLocaleData(localeRo);

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    // Every response passes through apiErrorInterceptor, so services and
    // components only ever see the ApiError shape from API_CONTRACT.md §3.
    provideHttpClient(withFetch(), withInterceptors([apiErrorInterceptor])),
    // <mat-icon>name</mat-icon> renders from Material Symbols Outlined (loaded in
    // index.html) instead of the legacy Material Icons font.
    provideAppInitializer(() => {
      inject(MatIconRegistry).setDefaultFontSetClass(
        'material-symbols-outlined',
        'mat-ligature-font',
      );
    }),
    // Paginator labels are translated too, but provided by each paged list
    // (`provideTranslatedPaginatorIntl`), not here: a root provider would pull
    // the paginator and its dependencies into the initial bundle.
  ],
};
