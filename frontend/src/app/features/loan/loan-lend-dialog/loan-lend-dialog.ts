import { Component, inject, signal, WritableSignal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import {
  catchError,
  debounceTime,
  distinctUntilChanged,
  EMPTY,
  filter,
  Observable,
  startWith,
  switchMap,
  tap,
} from 'rxjs';
import { ApiError } from '../../../core/error/api-error';
import { NotificationService } from '../../../core/error/notification.service';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { BookResponse } from '../../book/book.model';
import { BookService } from '../../book/book.service';
import { MemberResponse } from '../../member/member.model';
import { MemberService } from '../../member/member.service';
import { LoanRequest, LoanResponse } from '../loan.model';
import { LoanService } from '../loan.service';

const SEARCH_DEBOUNCE_MS = 300;

/**
 * How many options each picker offers. Both catalogues are paged server-side and
 * a drop-down is not a place to browse: the user narrows the search instead.
 */
export const OPTION_LIMIT = 20;

/**
 * Rejects text that was typed but never picked from the list.
 *
 * The autocomplete writes the whole selected object into the control and plain
 * text otherwise, so "is this an object?" is exactly "did the user choose an
 * entry?". Without it a member id could be invented by typing a matching name.
 */
function requireSelection(control: AbstractControl): ValidationErrors | null {
  const value: unknown = control.value;
  if (value === null || value === '') {
    // Empty is `Validators.required`'s business, not this validator's.
    return null;
  }
  return typeof value === 'object' ? null : { notSelected: true };
}

/**
 * The typed part of a picker's value stream: an option that was picked arrives
 * as an object and is not a search term, so it must not re-run the search.
 */
function searchTerms(values: Observable<unknown>): Observable<string> {
  return values.pipe(
    // Seeds the first, unfiltered page so the list is not empty on open.
    startWith(''),
    filter((value): value is string => typeof value === 'string'),
    debounceTime(SEARCH_DEBOUNCE_MS),
    distinctUntilChanged(),
  );
}

/**
 * Lends a copy to a member.
 *
 * Both fields are pickers over server-side searches rather than free-text ids:
 * the user can only lend a copy and to a member that actually exist. That is a
 * convenience, not the rule — the backend still owns `BOOK_NOT_FOUND`,
 * `BOOK_ALREADY_ON_LOAN`, `MEMBER_NOT_FOUND` and `MEMBER_TOO_MANY_LOANS`
 * (`API_CONTRACT.md` §8), which is what catches a picker gone stale.
 */
@Component({
  selector: 'app-loan-lend-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatAutocompleteModule,
    MatProgressSpinnerModule,
    MatButtonModule,
    TranslatePipe,
  ],
  templateUrl: './loan-lend-dialog.html',
  styleUrl: './loan-lend-dialog.css',
})
export class LoanLendDialog {
  private readonly loanService = inject(LoanService);
  private readonly bookService = inject(BookService);
  private readonly memberService = inject(MemberService);
  private readonly notifications = inject(NotificationService);
  private readonly i18n = inject(I18nService);
  private readonly dialogRef = inject<MatDialogRef<LoanLendDialog, LoanResponse>>(MatDialogRef);
  private readonly formBuilder = inject(FormBuilder);

  protected readonly saving = signal(false);
  protected readonly books = signal<readonly BookResponse[]>([]);
  protected readonly members = signal<readonly MemberResponse[]>([]);
  protected readonly booksLoading = signal(false);
  protected readonly membersLoading = signal(false);

  /**
   * Each control holds the picked entity, not its id: the autocomplete needs the
   * whole row to render its label, and "the value is an object" is what proves
   * the entry was chosen rather than typed.
   */
  protected readonly form = this.formBuilder.group({
    book: this.formBuilder.control<BookResponse | string | null>(null, [
      Validators.required,
      requireSelection,
    ]),
    member: this.formBuilder.control<MemberResponse | string | null>(null, [
      Validators.required,
      requireSelection,
    ]),
  });

  constructor() {
    // `/api/books` returns the active catalogue only, so an archived copy can
    // never be offered here (`API_CONTRACT.md` §5).
    searchTerms(this.form.controls.book.valueChanges)
      .pipe(
        tap(() => this.booksLoading.set(true)),
        // switchMap, not mergeMap: a slow response for an earlier keystroke must
        // never overwrite the results of a later, faster one.
        switchMap((search) =>
          this.bookService
            .list({ search, page: 0, size: OPTION_LIMIT, sort: 'title,asc' })
            .pipe(
              catchError((error: ApiError) => this.reportAndContinue(error, this.booksLoading)),
            ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((page) => {
        this.books.set(page.content);
        this.booksLoading.set(false);
      });

    // `/api/members` returns active members only, which is exactly the set a
    // book may be lent to (`API_CONTRACT.md` §7).
    searchTerms(this.form.controls.member.valueChanges)
      .pipe(
        tap(() => this.membersLoading.set(true)),
        switchMap((search) =>
          this.memberService
            .list({ search, page: 0, size: OPTION_LIMIT, sort: 'name,asc' })
            .pipe(
              catchError((error: ApiError) => this.reportAndContinue(error, this.membersLoading)),
            ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((page) => {
        this.members.set(page.content);
        this.membersLoading.set(false);
      });
  }

  /** Title, author and number together: the number alone identifies nothing. */
  protected bookLabel(book: BookResponse): string {
    return this.i18n.t('loan.book', {
      title: book.title,
      author: book.author,
      bookNumber: book.bookNumber,
    });
  }

  /**
   * Bound as fields, not methods: `displayWith` is called by the autocomplete
   * without a receiver, so an arrow property is what keeps `this` alive.
   */
  protected readonly displayBook = (value: BookResponse | string | null): string =>
    typeof value === 'object' && value !== null ? this.bookLabel(value) : (value ?? '');

  protected readonly displayMember = (value: MemberResponse | string | null): string =>
    typeof value === 'object' && value !== null ? value.name : (value ?? '');

  protected submit(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }

    const book = this.selected(this.form.controls.book);
    const member = this.selected(this.form.controls.member);
    if (!book || !member) {
      // Unreachable while `requireSelection` holds; it keeps the payload typed
      // without an assertion.
      this.form.markAllAsTouched();
      return;
    }

    // No borrow date: it is stamped server-side (`API_CONTRACT.md` §8).
    const request: LoanRequest = { bookId: book.id, memberId: member.id };

    this.saving.set(true);
    this.loanService.create(request).subscribe({
      next: (created) => this.dialogRef.close(created),
      error: (error: ApiError) => {
        this.saving.set(false);
        this.handleSaveError(error);
      },
    });
  }

  /**
   * Server-side failures are routed back to the field they belong to; anything
   * else is a snackbar. In every case the dialog stays open with the user's
   * choices intact.
   *
   * The manual `setErrors` calls are cleared automatically the next time the
   * control's value changes, because Angular re-runs its validators then — so
   * picking a different copy or member re-enables submitting.
   */
  private handleSaveError(error: ApiError): void {
    switch (error.code) {
      case 'BOOK_NOT_FOUND':
        // The copy was archived while this dialog was open.
        this.markServerError(this.form.controls.book, { notFound: true });
        break;
      case 'BOOK_ALREADY_ON_LOAN':
        // Someone else lent it first, or the picker's `onLoan` flag was stale.
        this.markServerError(this.form.controls.book, { onLoan: true });
        break;
      case 'MEMBER_NOT_FOUND':
        this.markServerError(this.form.controls.member, { notFound: true });
        break;
      case 'MEMBER_TOO_MANY_LOANS':
        this.markServerError(this.form.controls.member, { tooManyLoans: true });
        break;
      case 'VALIDATION_ERROR': {
        const control = this.controlFor(error.field);
        if (control) {
          this.markServerError(control, { server: true });
        } else {
          // The backend rejected something we do not render — no field to blame.
          this.notifications.showError(error);
        }
        break;
      }
      default:
        this.notifications.showError(error);
    }
  }

  private markServerError(control: AbstractControl, errors: Record<string, true>): void {
    control.setErrors(errors);
    // Without this the message stays hidden on a field the user never focused.
    control.markAsTouched();
  }

  /** The wire fields are `bookId`/`memberId`; the controls hold whole entities. */
  private controlFor(field: string | null): AbstractControl | null {
    switch (field) {
      case 'bookId':
        return this.form.controls.book;
      case 'memberId':
        return this.form.controls.member;
      default:
        return null;
    }
  }

  private selected<T extends object>(control: AbstractControl<T | string | null>): T | null {
    const value = control.value;
    return typeof value === 'object' ? value : null;
  }

  /**
   * A failed option lookup is reported and swallowed: `EMPTY` rather than a
   * rethrow, or the picker would stop searching after one failure.
   */
  private reportAndContinue(error: ApiError, loading: WritableSignal<boolean>): Observable<never> {
    loading.set(false);
    this.notifications.showError(error);
    return EMPTY;
  }
}
