import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';

import { apiErrorInterceptor } from './core/error/api-error.interceptor';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    // Every response passes through apiErrorInterceptor, so services and
    // components only ever see the ApiError shape from API_CONTRACT.md §3.
    provideHttpClient(withFetch(), withInterceptors([apiErrorInterceptor])),
  ],
};
