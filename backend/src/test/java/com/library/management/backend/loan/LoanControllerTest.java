package com.library.management.backend.loan;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.library.management.backend.common.dto.PagedResponse;
import com.library.management.backend.common.error.ApiException;
import com.library.management.backend.common.error.ErrorCode;
import com.library.management.backend.loan.dto.LoanRequest;
import com.library.management.backend.loan.dto.LoanResponse;
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
 * shape from {@code API_CONTRACT.md} §3/§8. The service is mocked -- its rules are
 * covered by {@link LoanServiceTest}.
 *
 * <p>These also exercise the {@code @RestControllerAdvice}, which the slice loads
 * alongside the controller.
 */
@WebMvcTest(LoanController.class)
class LoanControllerTest {

    private static final String LOAN_JSON = """
            {"bookId":41,"memberId":12}
            """;

    private static final LoanResponse ACTIVE_LOAN = new LoanResponse(
            508L,
            41L,
            "Amintiri din copilărie",
            "Ion Creangă",
            1201,
            12L,
            "Popescu Ion",
            LoanState.ACTIVE,
            Instant.parse("2026-08-01T10:15:00Z"),
            Instant.parse("2026-08-15T10:15:00Z"),
            0,
            null);

    private static final LoanResponse LATE_LOAN = new LoanResponse(
            509L,
            42L,
            "Baltagul",
            "Mihail Sadoveanu",
            1202,
            12L,
            "Popescu Ion",
            LoanState.LATE,
            Instant.parse("2026-07-01T10:15:00Z"),
            Instant.parse("2026-07-15T10:15:00Z"),
            38,
            null);

    private static final LoanResponse FINISHED_LOAN = new LoanResponse(
            508L,
            41L,
            "Amintiri din copilărie",
            "Ion Creangă",
            1201,
            12L,
            "Popescu Ion",
            LoanState.FINISHED,
            Instant.parse("2026-08-01T10:15:00Z"),
            Instant.parse("2026-08-15T10:15:00Z"),
            0,
            Instant.parse("2026-08-22T09:00:00Z"));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LoanService loanService;

    @Test
    void listReturnsTheContractEnvelopeNotSpringsPageShape() throws Exception {
        when(loanService.list(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PagedResponse<>(List.of(ACTIVE_LOAN), 0, 20, 23L, 2));

        mockMvc.perform(get("/api/loans"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(23))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content", hasSize(1)))
                // Spring's own Page JSON would carry these instead; the contract does not.
                .andExpect(jsonPath("$.number").doesNotExist())
                .andExpect(jsonPath("$.pageable").doesNotExist())
                .andExpect(jsonPath("$.content[0].id").value(508))
                .andExpect(jsonPath("$.content[0].bookId").value(41))
                .andExpect(jsonPath("$.content[0].bookTitle").value("Amintiri din copilărie"))
                .andExpect(jsonPath("$.content[0].bookAuthor").value("Ion Creangă"))
                .andExpect(jsonPath("$.content[0].bookNumber").value(1201))
                .andExpect(jsonPath("$.content[0].memberId").value(12))
                .andExpect(jsonPath("$.content[0].memberName").value("Popescu Ion"))
                .andExpect(jsonPath("$.content[0].state").value("ACTIVE"))
                .andExpect(jsonPath("$.content[0].borrowedAt").value("2026-08-01T10:15:00Z"))
                // Computed, not stored -- but it must still reach the client.
                .andExpect(jsonPath("$.content[0].dueAt").value("2026-08-15T10:15:00Z"))
                .andExpect(jsonPath("$.content[0].daysOverdue").value(0))
                .andExpect(jsonPath("$.content[0].finishedAt").isEmpty());
    }

    @Test
    void listReportsAnOverdueLoanWithItsDayCount() throws Exception {
        when(loanService.list(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PagedResponse<>(List.of(LATE_LOAN), 0, 20, 1L, 1));

        mockMvc.perform(get("/api/loans").param("state", "LATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].state").value("LATE"))
                .andExpect(jsonPath("$.content[0].daysOverdue").value(38));
    }

    @Test
    void listBindsTheOpenAliasAsAStateFilter() throws Exception {
        when(loanService.list(eq(LoanStateFilter.OPEN), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PagedResponse<>(List.of(), 0, 20, 0L, 0));

        mockMvc.perform(get("/api/loans").param("state", "OPEN"))
                .andExpect(status().isOk());

        // OPEN is not a stored LoanState -- it binds because the parameter is typed
        // as the API-level filter enum.
        verify(loanService).list(eq(LoanStateFilter.OPEN), any(), any(), any(), any(Pageable.class));
    }

    @Test
    void listPassesEveryFilterThrough() throws Exception {
        when(loanService.list(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PagedResponse<>(List.of(), 0, 20, 0L, 0));

        mockMvc.perform(get("/api/loans")
                        .param("state", "FINISHED")
                        .param("memberId", "12")
                        .param("bookId", "41")
                        .param("search", "creangă"))
                .andExpect(status().isOk());

        verify(loanService).list(
                eq(LoanStateFilter.FINISHED), eq(12L), eq(41L), eq("creangă"), any(Pageable.class));
    }

    @Test
    void listDefaultsToNewestFirst() throws Exception {
        when(loanService.list(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PagedResponse<>(List.of(), 0, 20, 0L, 0));

        mockMvc.perform(get("/api/loans")).andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(loanService).list(any(), any(), any(), any(), captor.capture());
        assertThat(captor.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
        assertThat(captor.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    void listRejectsAnUnknownStateValue() throws Exception {
        mockMvc.perform(get("/api/loans").param("state", "OVERDUE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.field").value("state"));

        verifyNoInteractions(loanService);
    }

    @Test
    void getByIdReturnsTheLoan() throws Exception {
        when(loanService.findById(508L)).thenReturn(ACTIVE_LOAN);

        mockMvc.perform(get("/api/loans/508"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(508))
                .andExpect(jsonPath("$.dueAt").value("2026-08-15T10:15:00Z"))
                .andExpect(jsonPath("$.daysOverdue").value(0));
    }

    @Test
    void getByIdReturnsTheContractErrorShapeWhenUnknown() throws Exception {
        when(loanService.findById(99L)).thenThrow(new ApiException(ErrorCode.LOAN_NOT_FOUND));

        mockMvc.perform(get("/api/loans/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LOAN_NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.field").isEmpty())
                .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    void createReturns201WithBodyAndLocation() throws Exception {
        when(loanService.create(any(LoanRequest.class))).thenReturn(ACTIVE_LOAN);

        mockMvc.perform(post("/api/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOAN_JSON))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/loans/508"))
                .andExpect(jsonPath("$.id").value(508))
                .andExpect(jsonPath("$.state").value("ACTIVE"))
                .andExpect(jsonPath("$.dueAt").value("2026-08-15T10:15:00Z"));
    }

    @Test
    void createReturns409WhenTheCopyIsAlreadyOut() throws Exception {
        when(loanService.create(any(LoanRequest.class)))
                .thenThrow(new ApiException(ErrorCode.BOOK_ALREADY_ON_LOAN));

        mockMvc.perform(post("/api/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOAN_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOK_ALREADY_ON_LOAN"))
                .andExpect(jsonPath("$.field").isEmpty())
                .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    void createReturns409WhenTheMemberIsAtTheLimit() throws Exception {
        when(loanService.create(any(LoanRequest.class)))
                .thenThrow(new ApiException(ErrorCode.MEMBER_TOO_MANY_LOANS));

        mockMvc.perform(post("/api/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOAN_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_TOO_MANY_LOANS"));
    }

    @Test
    void createReturns404WhenTheCopyIsMissingOrArchived() throws Exception {
        when(loanService.create(any(LoanRequest.class)))
                .thenThrow(new ApiException(ErrorCode.BOOK_NOT_FOUND, "bookId"));

        mockMvc.perform(post("/api/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOAN_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOK_NOT_FOUND"))
                .andExpect(jsonPath("$.field").value("bookId"));
    }

    @Test
    void createReturns404WhenTheMemberIsMissingOrArchived() throws Exception {
        when(loanService.create(any(LoanRequest.class)))
                .thenThrow(new ApiException(ErrorCode.MEMBER_NOT_FOUND, "memberId"));

        mockMvc.perform(post("/api/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOAN_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"))
                .andExpect(jsonPath("$.field").value("memberId"));
    }

    @Test
    void createRejectsAMissingBookId() throws Exception {
        mockMvc.perform(post("/api/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bookId":null,"memberId":12}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.field").value("bookId"))
                .andExpect(jsonPath("$.message").isNotEmpty());

        verifyNoInteractions(loanService);
    }

    @Test
    void createRejectsAMissingMemberId() throws Exception {
        mockMvc.perform(post("/api/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bookId":41}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.field").value("memberId"));

        verify(loanService, never()).create(any());
    }

    @Test
    void returnReturns200WithTheFinishedLoan() throws Exception {
        when(loanService.returnLoan(508L)).thenReturn(FINISHED_LOAN);

        mockMvc.perform(post("/api/loans/508/return")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(508))
                .andExpect(jsonPath("$.state").value("FINISHED"))
                .andExpect(jsonPath("$.finishedAt").value("2026-08-22T09:00:00Z"))
                .andExpect(jsonPath("$.daysOverdue").value(0));
    }

    @Test
    void returnReturns409WhenTheLoanIsAlreadyFinished() throws Exception {
        when(loanService.returnLoan(508L))
                .thenThrow(new ApiException(ErrorCode.LOAN_ALREADY_FINISHED));

        mockMvc.perform(post("/api/loans/508/return")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LOAN_ALREADY_FINISHED"))
                .andExpect(jsonPath("$.field").isEmpty());
    }

    @Test
    void returnReturns404WhenUnknown() throws Exception {
        when(loanService.returnLoan(99L)).thenThrow(new ApiException(ErrorCode.LOAN_NOT_FOUND));

        mockMvc.perform(post("/api/loans/99/return")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LOAN_NOT_FOUND"));
    }
}
