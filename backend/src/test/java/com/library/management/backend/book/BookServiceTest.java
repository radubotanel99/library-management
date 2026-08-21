package com.library.management.backend.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.library.management.backend.book.dto.BookRequest;
import com.library.management.backend.book.dto.BookResponse;
import com.library.management.backend.category.Category;
import com.library.management.backend.category.CategoryRepository;
import com.library.management.backend.common.dto.PagedResponse;
import com.library.management.backend.common.error.ApiException;
import com.library.management.backend.common.error.ErrorCode;
import com.library.management.backend.loan.LoanRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Unit tests for the catalogue business rules.
 *
 * <p>Plain JUnit + Mockito, no Spring context: these rules are pure logic over three
 * repositories, so a container would only make the suite slower.
 */
@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    private static final BookRequest REQUEST =
            new BookRequest("Amintiri din copilărie", "Ion Creangă", 1201, 3L, "Humanitas",
                    new BigDecimal("29.90"));

    @Mock
    private BookRepository bookRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private LoanRepository loanRepository;

    @InjectMocks
    private BookService bookService;

    private static Category category(Long id, String name) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        return category;
    }

    private static Book book(Long id, Integer bookNumber, BookStatus status) {
        Book book = new Book();
        book.setId(id);
        book.setTitle("Amintiri din copilărie");
        book.setAuthor("Ion Creangă");
        book.setBookNumber(bookNumber);
        book.setCategory(category(3L, "Fiction"));
        book.setStatus(status);
        return book;
    }

    /** Stubs {@code saveAndFlush} to return the entity it was handed, with an id. */
    private void stubSaveReturningId(Long id) {
        when(bookRepository.saveAndFlush(any(Book.class))).thenAnswer(invocation -> {
            Book saved = invocation.getArgument(0);
            saved.setId(id);
            return saved;
        });
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        void savesTheBookAndReportsItAsNotOnLoan() {
            when(categoryRepository.findByIdAndDeletedFalse(3L))
                    .thenReturn(Optional.of(category(3L, "Fiction")));
            when(bookRepository.existsByBookNumberAndStatus(1201, BookStatus.ACTIVE)).thenReturn(false);
            stubSaveReturningId(41L);

            BookResponse result = bookService.create(REQUEST);

            ArgumentCaptor<Book> captor = ArgumentCaptor.forClass(Book.class);
            verify(bookRepository).saveAndFlush(captor.capture());
            Book saved = captor.getValue();
            assertThat(saved.getTitle()).isEqualTo("Amintiri din copilărie");
            assertThat(saved.getBookNumber()).isEqualTo(1201);
            assertThat(saved.getCategory().getId()).isEqualTo(3L);
            assertThat(saved.getStatus()).isEqualTo(BookStatus.ACTIVE);
            assertThat(saved.getRemovalNote()).isNull();

            assertThat(result.id()).isEqualTo(41L);
            assertThat(result.categoryName()).isEqualTo("Fiction");
            assertThat(result.onLoan()).isFalse();
            // A brand new copy cannot be out, so no query should be needed to know that.
            verifyNoInteractions(loanRepository);
        }

        @Test
        void trimsIncidentalWhitespace() {
            when(categoryRepository.findByIdAndDeletedFalse(3L))
                    .thenReturn(Optional.of(category(3L, "Fiction")));
            when(bookRepository.existsByBookNumberAndStatus(1201, BookStatus.ACTIVE)).thenReturn(false);
            stubSaveReturningId(41L);

            bookService.create(new BookRequest("  Ion  ", "  Creangă ", 1201, 3L, "   ", null));

            ArgumentCaptor<Book> captor = ArgumentCaptor.forClass(Book.class);
            verify(bookRepository).saveAndFlush(captor.capture());
            assertThat(captor.getValue().getTitle()).isEqualTo("Ion");
            assertThat(captor.getValue().getAuthor()).isEqualTo("Creangă");
            assertThat(captor.getValue().getPublisher()).isNull();
        }

        @Test
        void throwsWhenAnActiveBookAlreadyUsesTheNumber() {
            when(categoryRepository.findByIdAndDeletedFalse(3L))
                    .thenReturn(Optional.of(category(3L, "Fiction")));
            when(bookRepository.existsByBookNumberAndStatus(1201, BookStatus.ACTIVE)).thenReturn(true);

            assertThatThrownBy(() -> bookService.create(REQUEST))
                    .isInstanceOf(ApiException.class)
                    .satisfies(thrown -> {
                        ApiException ex = (ApiException) thrown;
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BOOK_NUMBER_ALREADY_EXISTS);
                        assertThat(ex.getField()).isEqualTo("bookNumber");
                    });

            verify(bookRepository, never()).saveAndFlush(any());
        }

        @Test
        void throwsWhenTheCategoryIsMissingOrArchived() {
            when(categoryRepository.findByIdAndDeletedFalse(3L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> bookService.create(REQUEST))
                    .isInstanceOf(ApiException.class)
                    .satisfies(thrown -> {
                        ApiException ex = (ApiException) thrown;
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CATEGORY_NOT_FOUND);
                        assertThat(ex.getField()).isEqualTo("categoryId");
                    });

            verify(bookRepository, never()).saveAndFlush(any());
        }

        @Test
        void mapsAConcurrentUniqueIndexViolationOntoTheSameCode() {
            when(categoryRepository.findByIdAndDeletedFalse(3L))
                    .thenReturn(Optional.of(category(3L, "Fiction")));
            when(bookRepository.existsByBookNumberAndStatus(1201, BookStatus.ACTIVE)).thenReturn(false);
            when(bookRepository.saveAndFlush(any(Book.class)))
                    .thenThrow(new DataIntegrityViolationException("ux_book_number_active"));

            assertThatThrownBy(() -> bookService.create(REQUEST))
                    .isInstanceOf(ApiException.class)
                    .satisfies(thrown -> {
                        ApiException ex = (ApiException) thrown;
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BOOK_NUMBER_ALREADY_EXISTS);
                        assertThat(ex.getField()).isEqualTo("bookNumber");
                    });
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        void changesTheEditableFieldsOnly() {
            Book existing = book(41L, 1000, BookStatus.ACTIVE);
            when(bookRepository.findById(41L)).thenReturn(Optional.of(existing));
            when(categoryRepository.findByIdAndDeletedFalse(3L))
                    .thenReturn(Optional.of(category(3L, "Fiction")));
            when(bookRepository.existsByBookNumberAndStatusAndIdNot(1201, BookStatus.ACTIVE, 41L))
                    .thenReturn(false);
            when(bookRepository.saveAndFlush(existing)).thenReturn(existing);
            when(loanRepository.existsByBookIdAndStateIn(41L, LoanRepository.OPEN_STATES))
                    .thenReturn(true);

            BookResponse result = bookService.update(41L, REQUEST);

            assertThat(existing.getBookNumber()).isEqualTo(1201);
            assertThat(existing.getPublisher()).isEqualTo("Humanitas");
            assertThat(existing.getPrice()).isEqualByComparingTo("29.90");
            assertThat(result.onLoan()).isTrue();
        }

        @Test
        void neverTouchesStatusOrRemovalNote() {
            Book existing = book(41L, 1000, BookStatus.ACTIVE);
            existing.setRemovalNote(null);
            when(bookRepository.findById(41L)).thenReturn(Optional.of(existing));
            when(categoryRepository.findByIdAndDeletedFalse(3L))
                    .thenReturn(Optional.of(category(3L, "Fiction")));
            when(bookRepository.existsByBookNumberAndStatusAndIdNot(1201, BookStatus.ACTIVE, 41L))
                    .thenReturn(false);
            when(bookRepository.saveAndFlush(existing)).thenReturn(existing);

            bookService.update(41L, REQUEST);

            // The request has no status field, and an edit must not become a restore.
            assertThat(existing.getStatus()).isEqualTo(BookStatus.ACTIVE);
            assertThat(existing.getRemovalNote()).isNull();
        }

        @Test
        void refusesToEditACopyThatIsNoLongerActive() {
            Book archived = book(41L, 1201, BookStatus.LOST);
            archived.setRemovalNote("Not returned by member");
            when(bookRepository.findById(41L)).thenReturn(Optional.of(archived));

            assertThatThrownBy(() -> bookService.update(41L, REQUEST))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.BOOK_NOT_FOUND);

            verify(bookRepository, never()).saveAndFlush(any());
            assertThat(archived.getStatus()).isEqualTo(BookStatus.LOST);
            assertThat(archived.getRemovalNote()).isEqualTo("Not returned by member");
        }

        @Test
        void throwsNotFoundWhenTheCopyDoesNotExist() {
            when(bookRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> bookService.update(99L, REQUEST))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.BOOK_NOT_FOUND);

            verify(bookRepository, never()).saveAndFlush(any());
        }

        @Test
        void checksTheNumberAgainstOtherActiveBooksEvenWhenItIsUnchanged() {
            Book existing = book(41L, 1201, BookStatus.ACTIVE);
            when(bookRepository.findById(41L)).thenReturn(Optional.of(existing));
            when(categoryRepository.findByIdAndDeletedFalse(3L))
                    .thenReturn(Optional.of(category(3L, "Fiction")));
            when(bookRepository.existsByBookNumberAndStatusAndIdNot(1201, BookStatus.ACTIVE, 41L))
                    .thenReturn(true);

            assertThatThrownBy(() -> bookService.update(41L, REQUEST))
                    .isInstanceOf(ApiException.class)
                    .satisfies(thrown -> {
                        ApiException ex = (ApiException) thrown;
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BOOK_NUMBER_ALREADY_EXISTS);
                        assertThat(ex.getField()).isEqualTo("bookNumber");
                    });

            // The check is unconditional: only active copies get here, so the number
            // always competes for the active-only unique index.
            verify(bookRepository, never()).saveAndFlush(any());
        }

        @Test
        void throwsWhenTheCategoryIsMissingOrArchived() {
            when(bookRepository.findById(41L))
                    .thenReturn(Optional.of(book(41L, 1201, BookStatus.ACTIVE)));
            when(categoryRepository.findByIdAndDeletedFalse(3L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> bookService.update(41L, REQUEST))
                    .isInstanceOf(ApiException.class)
                    .satisfies(thrown -> {
                        ApiException ex = (ApiException) thrown;
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CATEGORY_NOT_FOUND);
                        assertThat(ex.getField()).isEqualTo("categoryId");
                    });
        }

        @Test
        void mapsAConcurrentUniqueIndexViolationOntoTheSameCode() {
            Book existing = book(41L, 1000, BookStatus.ACTIVE);
            when(bookRepository.findById(41L)).thenReturn(Optional.of(existing));
            when(categoryRepository.findByIdAndDeletedFalse(3L))
                    .thenReturn(Optional.of(category(3L, "Fiction")));
            when(bookRepository.existsByBookNumberAndStatusAndIdNot(1201, BookStatus.ACTIVE, 41L))
                    .thenReturn(false);
            when(bookRepository.saveAndFlush(existing))
                    .thenThrow(new DataIntegrityViolationException("ux_book_number_active"));

            assertThatThrownBy(() -> bookService.update(41L, REQUEST))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.BOOK_NUMBER_ALREADY_EXISTS);
        }
    }

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        void resolvesACopyOfAnyStatus() {
            Book archived = book(41L, 1201, BookStatus.WITHDRAWN);
            archived.setRemovalNote("Superseded edition");
            when(bookRepository.findById(41L)).thenReturn(Optional.of(archived));

            BookResponse result = bookService.findById(41L);

            assertThat(result.status()).isEqualTo(BookStatus.WITHDRAWN);
            assertThat(result.removalNote()).isEqualTo("Superseded edition");
            assertThat(result.onLoan()).isFalse();
        }

        @Test
        void throwsNotFoundWhenTheCopyDoesNotExist() {
            when(bookRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> bookService.findById(99L))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.BOOK_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("findByNumber")
    class FindByNumber {

        @Test
        void resolvesTheActiveCopyCarryingTheNumber() {
            when(bookRepository.findByBookNumberAndStatus(1201, BookStatus.ACTIVE))
                    .thenReturn(Optional.of(book(41L, 1201, BookStatus.ACTIVE)));
            when(loanRepository.existsByBookIdAndStateIn(41L, LoanRepository.OPEN_STATES))
                    .thenReturn(true);

            BookResponse result = bookService.findByNumber(1201);

            assertThat(result.id()).isEqualTo(41L);
            assertThat(result.onLoan()).isTrue();
        }

        @Test
        void throwsNotFoundWhenOnlyAnArchivedCopyCarriesTheNumber() {
            // Archived copies are excluded by the query itself: their numbers may
            // since have been reused, so including them would be ambiguous.
            when(bookRepository.findByBookNumberAndStatus(1201, BookStatus.ACTIVE))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> bookService.findByNumber(1201))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.BOOK_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("list")
    class ListBooks {

        private static final Pageable FIRST_PAGE = PageRequest.of(0, 20, Sort.by("title"));

        private void stubSearch(Page<Book> page) {
            when(bookRepository.search(any(), any(), any(), any(), any())).thenReturn(page);
        }

        private Pageable capturedPageable() {
            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(bookRepository).search(any(), any(), any(), any(), captor.capture());
            return captor.getValue();
        }

        @Test
        void marksOnlyTheCopiesWithAnOpenLoan() {
            List<Book> books = List.of(
                    book(41L, 1201, BookStatus.ACTIVE), book(42L, 1202, BookStatus.ACTIVE));
            stubSearch(new PageImpl<>(books, FIRST_PAGE, 2));
            when(loanRepository.findBookIdsWithLoanIn(anyCollection(), eq(LoanRepository.OPEN_STATES)))
                    .thenReturn(Set.of(42L));

            PagedResponse<BookResponse> result = bookService.list(null, null, FIRST_PAGE);

            assertThat(result.content())
                    .extracting(BookResponse::id, BookResponse::onLoan)
                    .containsExactly(tuple(41L, false), tuple(42L, true));
            assertThat(result.page()).isZero();
            assertThat(result.size()).isEqualTo(20);
            assertThat(result.totalElements()).isEqualTo(2L);
        }

        @Test
        void skipsTheLoanQueryForAnEmptyPage() {
            stubSearch(new PageImpl<>(List.of(), FIRST_PAGE, 0));

            PagedResponse<BookResponse> result = bookService.list(null, null, FIRST_PAGE);

            assertThat(result.content()).isEmpty();
            verify(loanRepository, never()).findBookIdsWithLoanIn(any(), any());
        }

        @Test
        void passesNoSearchFiltersForABlankTerm() {
            stubSearch(new PageImpl<>(List.of(), FIRST_PAGE, 0));

            bookService.list("   ", null, FIRST_PAGE);

            verify(bookRepository).search(
                    eq(BookStatus.ACTIVE), eq(null), eq(null), eq(null), any(Pageable.class));
        }

        @Test
        void searchesTitleAuthorAndTheExactNumberForANumericTerm() {
            stubSearch(new PageImpl<>(List.of(), FIRST_PAGE, 0));

            bookService.list(" 1201 ", 3L, FIRST_PAGE);

            verify(bookRepository).search(
                    eq(BookStatus.ACTIVE), eq(3L), eq("%1201%"), eq(1201), any(Pageable.class));
        }

        @Test
        void searchesTitleAndAuthorOnlyForANonNumericTerm() {
            stubSearch(new PageImpl<>(List.of(), FIRST_PAGE, 0));

            bookService.list("Creangă", null, FIRST_PAGE);

            ArgumentCaptor<String> pattern = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Integer> number = ArgumentCaptor.forClass(Integer.class);
            verify(bookRepository).search(
                    any(), any(), pattern.capture(), number.capture(), any(Pageable.class));
            assertThat(pattern.getValue()).isEqualTo("%creangă%");
            assertThat(number.getValue()).isNull();
        }

        @Test
        void keepsAWhitelistedSort() {
            stubSearch(new PageImpl<>(List.of(), FIRST_PAGE, 0));

            bookService.list(null, null, PageRequest.of(1, 5, Sort.by(Sort.Direction.DESC, "author")));

            Pageable used = capturedPageable();
            assertThat(used.getPageNumber()).isEqualTo(1);
            assertThat(used.getPageSize()).isEqualTo(5);
            assertThat(used.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "author"));
        }

        @Test
        void fallsBackToTitleAscendingForAnUnknownSortProperty() {
            stubSearch(new PageImpl<>(List.of(), FIRST_PAGE, 0));

            // categoryName is deliberately not sortable in this phase, and an
            // unfiltered property would blow up at query-build time.
            bookService.list(null, null, PageRequest.of(0, 20, Sort.by("categoryName")));

            assertThat(capturedPageable().getSort()).isEqualTo(Sort.by("title").ascending());
        }
    }
}
