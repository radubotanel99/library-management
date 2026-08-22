package com.library.management.backend.book;

import com.library.management.backend.book.dto.BookRemoveRequest;
import com.library.management.backend.book.dto.BookRequest;
import com.library.management.backend.book.dto.BookResponse;
import com.library.management.backend.common.dto.PagedResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Book endpoints ({@code API_CONTRACT.md} §5).
 *
 * <p>Pure HTTP plumbing: bind, delegate, map the result onto a status code. No
 * business rules and no try/catch -- rule violations arrive as
 * {@code ApiException} and are turned into responses by the single
 * {@code @RestControllerAdvice}.
 *
 * <p>The catalogue is paged, unlike categories: a library holds thousands of copies.
 * Which sort properties are actually honoured is a service decision, not a binding
 * one, so the {@link Pageable} is passed through untouched.
 */
@RestController
@RequestMapping("/api/books")
public class BookController {

    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    @GetMapping
    public PagedResponse<BookResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @PageableDefault(size = 20, sort = "title") Pageable pageable) {
        return bookService.list(search, categoryId, pageable);
    }

    /** Resolves a copy of any status, so a loan can always name the book it points at. */
    @GetMapping("/{id}")
    public BookResponse findById(@PathVariable Long id) {
        return bookService.findById(id);
    }

    /** Active copies only: an archived copy's number may have been reused. */
    @GetMapping("/by-number/{bookNumber}")
    public BookResponse findByNumber(@PathVariable Integer bookNumber) {
        return bookService.findByNumber(bookNumber);
    }

    @PostMapping
    public ResponseEntity<BookResponse> create(@Valid @RequestBody BookRequest request) {
        BookResponse created = bookService.create(request);
        return ResponseEntity
                .created(URI.create("/api/books/" + created.id()))
                .body(created);
    }

    /** Never changes {@code status}: removal and restore are their own endpoints. */
    @PutMapping("/{id}")
    public BookResponse update(@PathVariable Long id, @Valid @RequestBody BookRequest request) {
        return bookService.update(id, request);
    }

    /**
     * Takes a copy out of the collection. A {@code POST} to a sub-resource rather
     * than a {@code DELETE} because the reason is mandatory and {@code DELETE} has
     * no body semantics for it.
     */
    @PostMapping("/{id}/remove")
    public BookResponse remove(@PathVariable Long id, @Valid @RequestBody BookRemoveRequest request) {
        return bookService.remove(id, request);
    }
}
