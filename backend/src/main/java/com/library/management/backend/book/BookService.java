package com.library.management.backend.book;

import com.library.management.backend.book.dto.BookRemoveRequest;
import com.library.management.backend.book.dto.BookRequest;
import com.library.management.backend.book.dto.BookResponse;
import com.library.management.backend.category.Category;
import com.library.management.backend.category.CategoryRepository;
import com.library.management.backend.common.dto.PagedResponse;
import com.library.management.backend.common.error.ApiException;
import com.library.management.backend.common.error.ErrorCode;
import com.library.management.backend.loan.LoanRepository;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business rules for the catalogue.
 *
 * <p>The rule that shapes everything here is that {@code bookNumber} is unique only
 * among {@link BookStatus#ACTIVE} copies ({@code DATA_MODEL.md} §4): removing a copy
 * frees its number. So every uniqueness check and every by-number lookup is scoped
 * to {@code ACTIVE}, and the catalogue lists active copies only.
 *
 * <p>{@link #update} refuses to touch a copy that is not active. Editing an archived
 * copy is not a supported operation -- the previous system un-archived rows as a side
 * effect of editing, and that behaviour is deliberately not carried over
 * ({@code DATA_MODEL.md} §4.1). Restoring is explicit. {@link #findById} is the one
 * exception: the contract says it resolves a book of any status.
 *
 * <p>The service returns DTOs, so entities stop here and never reach a controller.
 */
@Service
@Transactional(readOnly = true)
public class BookService {

    /**
     * Columns a client may sort by.
     *
     * <p>A whitelist rather than a pass-through: an unknown property reaches
     * Hibernate as a JPQL fragment and fails at query-build time, so unfiltered
     * client input would turn a typo into a 500.
     *
     * <p>{@code categoryName} is absent on purpose -- sorting across the join is not
     * part of this phase.
     */
    private static final Set<String> SORTABLE =
            Set.of("title", "author", "bookNumber", "price", "publisher", "createdAt");

    private static final Sort DEFAULT_SORT = Sort.by("title").ascending();

    private final BookRepository bookRepository;
    private final CategoryRepository categoryRepository;
    private final LoanRepository loanRepository;

    public BookService(BookRepository bookRepository,
                       CategoryRepository categoryRepository,
                       LoanRepository loanRepository) {
        this.bookRepository = bookRepository;
        this.categoryRepository = categoryRepository;
        this.loanRepository = loanRepository;
    }

    /**
     * One page of the active catalogue ({@code API_CONTRACT.md} §5).
     *
     * <p>{@code onLoan} for the whole page comes from a single query rather than a
     * check per row, which would be N+1.
     */
    public PagedResponse<BookResponse> list(String search, Long categoryId, Pageable pageable) {
        String term = normalise(search);
        String pattern = term == null ? null : "%" + term.toLowerCase(Locale.ROOT) + "%";
        Integer searchNumber = parseBookNumber(term);

        Page<Book> books = bookRepository.search(
                BookStatus.ACTIVE, categoryId, pattern, searchNumber, sanitise(pageable));

        Set<Long> onLoanIds = findOnLoanIds(books.getContent());

        return PagedResponse.of(books.map(book ->
                BookMapper.toResponse(book, onLoanIds.contains(book.getId()))));
    }

    /** Resolves a copy of <em>any</em> status -- loan history must stay readable. */
    public BookResponse findById(Long id) {
        Book book = requireById(id);
        return BookMapper.toResponse(book, isOnLoan(book.getId()));
    }

    /**
     * Resolves the <em>active</em> copy carrying this number.
     *
     * <p>Archived copies are excluded because their numbers may since have been
     * reused, so including them would make the answer ambiguous.
     */
    public BookResponse findByNumber(Integer bookNumber) {
        Book book = requireActiveByNumber(bookNumber);
        return BookMapper.toResponse(book, isOnLoan(book.getId()));
    }

    @Transactional
    public BookResponse create(BookRequest request) {
        Category category = requireActiveCategory(request.categoryId());

        if (bookRepository.existsByBookNumberAndStatus(request.bookNumber(), BookStatus.ACTIVE)) {
            throw new ApiException(ErrorCode.BOOK_NUMBER_ALREADY_EXISTS, "bookNumber");
        }

        Book book = new Book();
        apply(book, request, category);

        // A brand new copy cannot be on loan yet, so the flag is known without a query.
        return BookMapper.toResponse(save(book), false);
    }

    /**
     * Edits an active copy. Never changes {@code status} or {@code removalNote}:
     * those move only through {@code /remove} and {@code /restore}.
     */
    @Transactional
    public BookResponse update(Long id, BookRequest request) {
        Book book = requireActive(id);
        Category category = requireActiveCategory(request.categoryId());

        // Unconditional: only active copies reach this line, so the number always
        // competes for the active-only unique index.
        if (bookRepository.existsByBookNumberAndStatusAndIdNot(
                request.bookNumber(), BookStatus.ACTIVE, id)) {
            throw new ApiException(ErrorCode.BOOK_NUMBER_ALREADY_EXISTS, "bookNumber");
        }

        apply(book, request, category);

        return BookMapper.toResponse(save(book), isOnLoan(id));
    }

    /**
     * Takes a copy out of the collection, recording <em>why</em>
     * ({@code API_CONTRACT.md} §5).
     *
     * <p>A copy that is already removed is reported as missing, exactly as
     * {@link #update} does -- there is nothing to remove, and a 409 would imply the
     * caller could retry differently. A copy that is still out on loan is a genuine
     * rule violation and gets {@link ErrorCode#BOOK_HAS_OPEN_LOAN}.
     *
     * <p>Removing frees the copy's number for reuse but never claims one, so this
     * saves directly rather than through {@link #save}: there is no duplicate-number
     * collision to map.
     */
    @Transactional
    public BookResponse remove(Long id, BookRemoveRequest request) {
        Book book = requireActive(id);

        if (isOnLoan(id)) {
            // No field: the payload is fine, the copy's situation is not.
            throw new ApiException(ErrorCode.BOOK_HAS_OPEN_LOAN);
        }

        book.setStatus(request.status().toBookStatus());
        book.setRemovalNote(normalise(request.removalNote()));

        Book saved = bookRepository.saveAndFlush(book);

        // The check above just proved the copy is not out, so no second query.
        return BookMapper.toResponse(saved, false);
    }

    private void apply(Book book, BookRequest request, Category category) {
        book.setTitle(normalise(request.title()));
        book.setAuthor(normalise(request.author()));
        book.setBookNumber(request.bookNumber());
        book.setCategory(category);
        book.setPublisher(normalise(request.publisher()));
        book.setPrice(request.price());
    }

    /** An archived copy is indistinguishable from a missing one to everything but {@code findById}. */
    private Book requireActive(Long id) {
        return bookRepository.findById(id)
                .filter(book -> book.getStatus() == BookStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ErrorCode.BOOK_NOT_FOUND));
    }

    private Book requireById(Long id) {
        return bookRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.BOOK_NOT_FOUND));
    }

    private Book requireActiveByNumber(Integer bookNumber) {
        return bookRepository.findByBookNumberAndStatus(bookNumber, BookStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ErrorCode.BOOK_NOT_FOUND));
    }

    /** An archived category cannot take new books, so it is reported as missing. */
    private Category requireActiveCategory(Long categoryId) {
        return categoryRepository.findByIdAndDeletedFalse(categoryId)
                .orElseThrow(() -> new ApiException(ErrorCode.CATEGORY_NOT_FOUND, "categoryId"));
    }

    private boolean isOnLoan(Long bookId) {
        return loanRepository.existsByBookIdAndStateIn(bookId, LoanRepository.OPEN_STATES);
    }

    private Set<Long> findOnLoanIds(List<Book> books) {
        if (books.isEmpty()) {
            // An empty IN list is not portable, and the answer is known anyway.
            return Set.of();
        }
        List<Long> ids = books.stream().map(Book::getId).toList();
        return loanRepository.findBookIdsWithLoanIn(ids, LoanRepository.OPEN_STATES);
    }

    /**
     * Flushes so the partial unique index reports a duplicate number here, inside the
     * service, rather than as a raw database error later.
     *
     * <p>The pre-checks above catch ordinary duplicates; this catch is the
     * race-condition net for two concurrent requests that both pass them.
     */
    private Book save(Book book) {
        try {
            return bookRepository.saveAndFlush(book);
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(ErrorCode.BOOK_NUMBER_ALREADY_EXISTS, "bookNumber", ex);
        }
    }

    /**
     * Drops any sort order the catalogue cannot honour, falling back to title
     * ascending when nothing survives, so a bad {@code sort} parameter degrades
     * instead of failing.
     */
    private Pageable sanitise(Pageable pageable) {
        List<Sort.Order> allowed = pageable.getSort().stream()
                .filter(order -> SORTABLE.contains(order.getProperty()))
                .toList();
        Sort sort = allowed.isEmpty() ? DEFAULT_SORT : Sort.by(allowed);
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }

    /** A search term that is not a whole number simply does not match on number. */
    private Integer parseBookNumber(String term) {
        if (term == null) {
            return null;
        }
        try {
            return Integer.valueOf(term);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** Trims incidental whitespace and treats an empty string as absent. */
    private String normalise(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
