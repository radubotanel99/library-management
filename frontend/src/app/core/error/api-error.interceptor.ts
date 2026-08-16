import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';
import { ApiError, isApiError, networkError } from './api-error';

/**
 * Normalises every HTTP failure into an {@link ApiError} so subscribers only
 * ever deal with a stable `code`, never with a raw `HttpErrorResponse` or an
 * HTTP status. A failure with no parseable body (server unreachable, CORS,
 * timeout) becomes the frontend-only `NETWORK_ERROR` code.
 */
export const apiErrorInterceptor: HttpInterceptorFn = (req, next) =>
  next(req).pipe(catchError((error: unknown) => throwError(() => toApiError(error))));

function toApiError(error: unknown): ApiError {
  if (error instanceof HttpErrorResponse && isApiError(error.error)) {
    return error.error;
  }
  return networkError();
}
