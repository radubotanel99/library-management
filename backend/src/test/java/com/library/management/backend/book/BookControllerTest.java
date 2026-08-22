package com.library.management.backend.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.library.management.backend.book.dto.BookRemoveRequest;
import com.library.management.backend.book.dto.BookRequest;
import com.library.management.backend.book.dto.BookResponse;
import com.library.management.backend.book.dto.BookRestoreRequest;
import com.library.management.backend.common.dto.PagedResponse;
import com.library.management.backend.common.error.ApiException;
import com.library.management.backend.common.error.ErrorCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-layer tests: status codes, headers, the paged envelope and the error body
 * shape from {@code API_CONTRACT.md} §3/§5. The service is mocked -- its rules are
 * covered by {@link BookServiceTest}.
 *
 * <p>These also exercise the {@code @RestControllerAdvice}, which the slice loads
 * alongside the controller.
 */
@WebMvcTest(BookController.class)
class BookControllerTest {

    private static final String BOOK_JSON = """
            {"title":"Amintiri din copilărie","author":"Ion Creangă","bookNumber":1201,
             "categoryId":3,"publisher":"Humanitas","price":29.90}
            """;

    private static final BookResponse AMINTIRI = new BookResponse(
            41L,
            "Amintiri din copilărie",
            "Ion Creangă",
            1201,
            3L,
            "Fiction",
            "Humanitas",
            new BigDecimal("29.90"),
            BookStatus.ACTIVE,
            null,
            true,
            Instant.parse("2026-01-12T10:00:00Z"),
            null);

    private static final String REMOVE_JSON = """
            {"status":"LOST","removalNote":"Not returned by member"}
            """;

    private static final BookResponse REMOVED = new BookResponse(
            41L,
            "Amintiri din copilărie",
            "Ion Creangă",
            1201,
            3L,
            "Fiction",
            "Humanitas",
            new BigDecimal("29.90"),
            BookStatus.LOST,
            "Not returned by member",
            false,
            Instant.parse("2026-01-12T10:00:00Z"),
            Instant.parse("2026-08-21T09:00:00Z"));

    private static final BookResponse RESTORED = new BookResponse(
            41L,
            "Amintiri din copilărie",
            "Ion Creangă",
            1201,
            3L,
            "Fiction",
            "Humanitas",
            new BigDecimal("29.90"),
            BookStatus.ACTIVE,
            null,
            false,
            Instant.parse("2026-01-12T10:00:00Z"),
            Instant.parse("2026-08-22T09:00:00Z"));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookService bookService;

    @Test
    void listReturnsTheContractEnvelopeNotSpringsPageShape() throws Exception {
        when(bookService.list(any(), any(), any(Pageable.class)))
                .thenReturn(new PagedResponse<>(List.of(AMINTIRI), 0, 20, 137L, 7));

        mockMvc.perform(get("/api/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(137))
                .andExpect(jsonPath("$.totalPages").value(7))
                .andExpect(jsonPath("$.content", hasSize(1)))
                // Spring's own Page JSON would carry these instead; the contract does not.
                .andExpect(jsonPath("$.number").doesNotExist())
                .andExpect(jsonPath("$.pageable").doesNotExist())
                .andExpect(jsonPath("$.content[0].id").value(41))
                .andExpect(jsonPath("$.content[0].bookNumber").value(1201))
                // The number is unique only among active copies, so title and author
                // must travel with it (DATA_MODEL.md §4).
                .andExpect(jsonPath("$.content[0].title").value("Amintiri din copilărie"))
                .andExpect(jsonPath("$.content[0].author").value("Ion Creangă"))
                .andExpect(jsonPath("$.content[0].categoryName").value("Fiction"))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.content[0].removalNote").isEmpty())
                .andExpect(jsonPath("$.content[0].updatedAt").isEmpty());
    }

    @Test
    void listSerialisesPriceAsADecimalOnLoanAsABooleanAndTimestampsAsIso8601() throws Exception {
        when(bookService.list(any(), any(), any(Pageable.class)))
                .thenReturn(new PagedResponse<>(List.of(AMINTIRI), 0, 20, 1L, 1));

        mockMvc.perform(get("/api/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].price").value(29.90))
                .andExpect(jsonPath("$.content[0].onLoan").value(true))
                .andExpect(jsonPath("$.content[0].onLoan").isBoolean())
                // Not an epoch number and not the array Jackson writes without
                // JavaTimeModule configured the way the contract needs.
                .andExpect(jsonPath("$.content[0].createdAt").isString())
                .andExpect(jsonPath("$.content[0].createdAt").value("2026-01-12T10:00:00Z"));
    }

    @Test
    void listBindsSearchCategoryAndPagingOntoTheService() throws Exception {
        when(bookService.list(any(), any(), any(Pageable.class)))
                .thenReturn(new PagedResponse<>(List.of(), 1, 5, 0L, 0));

        mockMvc.perform(get("/api/books")
                        .param("search", "x")
                        .param("categoryId", "3")
                        .param("page", "1")
                        .param("size", "5")
                        .param("sort", "author,desc"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(bookService).list(eq("x"), eq(3L), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "author"));
    }

    @Test
    void listDefaultsToTheFirstPageSortedByTitle() throws Exception {
        when(bookService.list(any(), any(), any(Pageable.class)))
                .thenReturn(new PagedResponse<>(List.of(), 0, 20, 0L, 0));

        mockMvc.perform(get("/api/books")).andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(bookService).list(eq(null), eq(null), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by("title"));
    }

    @Test
    void archivedListReturnsTheContractEnvelope() throws Exception {
        when(bookService.listArchived(any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PagedResponse<>(List.of(REMOVED), 0, 20, 1L, 1));

        mockMvc.perform(get("/api/books/archived"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].status").value("LOST"))
                .andExpect(jsonPath("$.content[0].removalNote").value("Not returned by member"));
    }

    @Test
    void archivedListBindsSearchCategoryReasonAndPagingOntoTheService() throws Exception {
        when(bookService.listArchived(any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PagedResponse<>(List.of(), 1, 5, 0L, 0));

        mockMvc.perform(get("/api/books/archived")
                        .param("search", "x")
                        .param("categoryId", "3")
                        .param("status", "LOST")
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk());

        verify(bookService).listArchived(eq("x"), eq(3L), eq(RemovalReason.LOST), any(Pageable.class));
    }

    @Test
    void archivedListRejectsActiveAsAReason() throws Exception {
        // ACTIVE is not a constant of RemovalReason, so binding fails before the
        // service is ever called -- same trick as BookRemoveRequest.
        mockMvc.perform(get("/api/books/archived").param("status", "ACTIVE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.field").value("status"));

        verifyNoInteractions(bookService);
    }

    @Test
    void getByIdReturnsTheBook() throws Exception {
        when(bookService.findById(41L)).thenReturn(AMINTIRI);

        mockMvc.perform(get("/api/books/41"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(41))
                .andExpect(jsonPath("$.categoryId").value(3));
    }

    @Test
    void getByIdReturnsTheContractErrorShapeWhenUnknown() throws Exception {
        when(bookService.findById(99L)).thenThrow(new ApiException(ErrorCode.BOOK_NOT_FOUND));

        mockMvc.perform(get("/api/books/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOK_NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.field").isEmpty())
                .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    void getByNumberReturnsTheActiveCopy() throws Exception {
        when(bookService.findByNumber(1201)).thenReturn(AMINTIRI);

        mockMvc.perform(get("/api/books/by-number/1201"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookNumber").value(1201));
    }

    @Test
    void getByNumberRejectsANonNumericNumber() throws Exception {
        mockMvc.perform(get("/api/books/by-number/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.field").value("bookNumber"));

        verifyNoInteractions(bookService);
    }

    @Test
    void createReturns201WithBodyAndLocation() throws Exception {
        when(bookService.create(any(BookRequest.class))).thenReturn(AMINTIRI);

        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BOOK_JSON))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/books/41"))
                .andExpect(jsonPath("$.id").value(41));
    }

    @Test
    void createReturns409OnADuplicateNumber() throws Exception {
        when(bookService.create(any(BookRequest.class)))
                .thenThrow(new ApiException(ErrorCode.BOOK_NUMBER_ALREADY_EXISTS, "bookNumber"));

        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BOOK_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOK_NUMBER_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.field").value("bookNumber"));
    }

    @Test
    void createReturns404WhenTheCategoryIsUnknown() throws Exception {
        when(bookService.create(any(BookRequest.class)))
                .thenThrow(new ApiException(ErrorCode.CATEGORY_NOT_FOUND, "categoryId"));

        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BOOK_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"))
                .andExpect(jsonPath("$.field").value("categoryId"));
    }

    @Test
    void createRejectsABlankTitle() throws Exception {
        // Only one field is invalid: the handler reports a single field, chosen
        // alphabetically, so a second bad field would change the expectation.
        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"   ","author":"Ion Creangă","bookNumber":1201,
                                 "categoryId":3,"publisher":null,"price":null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.field").value("title"))
                .andExpect(jsonPath("$.message").isNotEmpty());

        verify(bookService, never()).create(any());
    }

    @Test
    void createRejectsAMissingBookNumber() throws Exception {
        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Amintiri","author":"Ion Creangă","bookNumber":null,
                                 "categoryId":3,"publisher":null,"price":null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.field").value("bookNumber"));

        verify(bookService, never()).create(any());
    }

    @Test
    void updateReturnsTheSavedBook() throws Exception {
        when(bookService.update(eq(41L), any(BookRequest.class))).thenReturn(AMINTIRI);

        mockMvc.perform(put("/api/books/41")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BOOK_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(41))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void updateReturns404ForACopyThatIsNotActive() throws Exception {
        when(bookService.update(eq(41L), any(BookRequest.class)))
                .thenThrow(new ApiException(ErrorCode.BOOK_NOT_FOUND));

        mockMvc.perform(put("/api/books/41")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BOOK_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOK_NOT_FOUND"));
    }

    @Test
    void removeReturns200WithTheArchivedBook() throws Exception {
        when(bookService.remove(eq(41L), any(BookRemoveRequest.class))).thenReturn(REMOVED);

        mockMvc.perform(post("/api/books/41/remove")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REMOVE_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(41))
                .andExpect(jsonPath("$.status").value("LOST"))
                .andExpect(jsonPath("$.removalNote").value("Not returned by member"))
                .andExpect(jsonPath("$.onLoan").value(false))
                // The number is unique only among active copies, so it never
                // travels without title and author (DATA_MODEL.md §4).
                .andExpect(jsonPath("$.bookNumber").value(1201))
                .andExpect(jsonPath("$.title").value("Amintiri din copilărie"))
                .andExpect(jsonPath("$.author").value("Ion Creangă"));

        ArgumentCaptor<BookRemoveRequest> request = ArgumentCaptor.forClass(BookRemoveRequest.class);
        verify(bookService).remove(eq(41L), request.capture());
        assertThat(request.getValue().status()).isEqualTo(RemovalReason.LOST);
        assertThat(request.getValue().removalNote()).isEqualTo("Not returned by member");
    }

    @Test
    void removeReturns409ForACopyThatIsStillOnLoan() throws Exception {
        when(bookService.remove(eq(41L), any(BookRemoveRequest.class)))
                .thenThrow(new ApiException(ErrorCode.BOOK_HAS_OPEN_LOAN));

        mockMvc.perform(post("/api/books/41/remove")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REMOVE_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOK_HAS_OPEN_LOAN"))
                .andExpect(jsonPath("$.field").isEmpty());
    }

    @Test
    void removeRejectsActiveAsAReason() throws Exception {
        // ACTIVE is not a constant of RemovalReason, so this dies in the JSON
        // parser and never reaches the service -- no custom validator needed.
        mockMvc.perform(post("/api/books/41/remove")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"ACTIVE","removalNote":null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.field").isEmpty());

        verify(bookService, never()).remove(any(), any());
    }

    @Test
    void removeRejectsAMissingReason() throws Exception {
        mockMvc.perform(post("/api/books/41/remove")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":null,"removalNote":"Worn out"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.field").value("status"));

        verify(bookService, never()).remove(any(), any());
    }

    @Test
    void removeRejectsANoteLongerThanTheColumn() throws Exception {
        String tooLong = "x".repeat(501);

        mockMvc.perform(post("/api/books/41/remove")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DAMAGED\",\"removalNote\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.field").value("removalNote"));

        verify(bookService, never()).remove(any(), any());
    }

    @Test
    void removeReturns404ForAnUnknownCopy() throws Exception {
        when(bookService.remove(eq(99L), any(BookRemoveRequest.class)))
                .thenThrow(new ApiException(ErrorCode.BOOK_NOT_FOUND));

        mockMvc.perform(post("/api/books/99/remove")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REMOVE_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOK_NOT_FOUND"));
    }

    @Test
    void restoreReturns200WithTheReactivatedBookAndNoBodyRequired() throws Exception {
        when(bookService.restore(eq(41L), any())).thenReturn(RESTORED);

        mockMvc.perform(post("/api/books/41/restore")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(41))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.removalNote").isEmpty());

        verify(bookService).restore(eq(41L), eq(new BookRestoreRequest(null)));
    }

    @Test
    void restoreAcceptsAnOptionalNewNumber() throws Exception {
        when(bookService.restore(eq(41L), any())).thenReturn(RESTORED);

        mockMvc.perform(post("/api/books/41/restore")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookNumber\":1305}"))
                .andExpect(status().isOk());

        ArgumentCaptor<BookRestoreRequest> request = ArgumentCaptor.forClass(BookRestoreRequest.class);
        verify(bookService).restore(eq(41L), request.capture());
        assertThat(request.getValue().bookNumber()).isEqualTo(1305);
    }

    @Test
    void restoreReturns409WhenAlreadyActive() throws Exception {
        when(bookService.restore(eq(41L), any()))
                .thenThrow(new ApiException(ErrorCode.BOOK_NOT_REMOVED));

        mockMvc.perform(post("/api/books/41/restore")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOK_NOT_REMOVED"));
    }

    @Test
    void restoreReturns409WhenTheNumberIsTaken() throws Exception {
        when(bookService.restore(eq(41L), any()))
                .thenThrow(new ApiException(ErrorCode.BOOK_NUMBER_TAKEN_ON_RESTORE, "bookNumber"));

        mockMvc.perform(post("/api/books/41/restore")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOK_NUMBER_TAKEN_ON_RESTORE"))
                .andExpect(jsonPath("$.field").value("bookNumber"));
    }

    @Test
    void restoreReturns404WhenTheCategoryHasSinceBeenArchived() throws Exception {
        when(bookService.restore(eq(41L), any()))
                .thenThrow(new ApiException(ErrorCode.CATEGORY_NOT_FOUND));

        mockMvc.perform(post("/api/books/41/restore")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
    }

    @Test
    void restoreRejectsANonPositiveNumber() throws Exception {
        mockMvc.perform(post("/api/books/41/restore")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookNumber\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.field").value("bookNumber"));

        verify(bookService, never()).restore(any(), any());
    }
}
