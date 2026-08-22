package com.library.management.backend.member;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.library.management.backend.common.dto.PagedResponse;
import com.library.management.backend.common.error.ApiException;
import com.library.management.backend.common.error.ErrorCode;
import com.library.management.backend.member.dto.MemberRequest;
import com.library.management.backend.member.dto.MemberResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-layer tests: status codes, headers, the paged envelope and the error body
 * shape from {@code API_CONTRACT.md} §3/§7. The service is mocked -- its rules are
 * covered by {@link MemberServiceTest}.
 *
 * <p>These also exercise the {@code @RestControllerAdvice}, which the slice loads
 * alongside the controller.
 */
@WebMvcTest(MemberController.class)
class MemberControllerTest {

    private static final String MEMBER_JSON = """
            {"name":"Popescu Ion","email":"ion.popescu@example.ro",
             "address":"Str. Victoriei 14, Timișoara","phoneNumber":"+40 721 000 000"}
            """;

    private static final MemberResponse POPESCU = new MemberResponse(
            12L,
            "Popescu Ion",
            "ion.popescu@example.ro",
            "Str. Victoriei 14, Timișoara",
            "+40 721 000 000",
            2L,
            Instant.parse("2026-02-01T08:00:00Z"));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberService memberService;

    @Test
    void listReturnsTheContractEnvelopeNotSpringsPageShape() throws Exception {
        when(memberService.list(any(), any(Pageable.class)))
                .thenReturn(new PagedResponse<>(List.of(POPESCU), 0, 20, 64L, 4));

        mockMvc.perform(get("/api/members"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(64))
                .andExpect(jsonPath("$.totalPages").value(4))
                .andExpect(jsonPath("$.content", hasSize(1)))
                // Spring's own Page JSON would carry these instead; the contract does not.
                .andExpect(jsonPath("$.number").doesNotExist())
                .andExpect(jsonPath("$.pageable").doesNotExist())
                .andExpect(jsonPath("$.content[0].id").value(12))
                .andExpect(jsonPath("$.content[0].name").value("Popescu Ion"))
                .andExpect(jsonPath("$.content[0].email").value("ion.popescu@example.ro"))
                .andExpect(jsonPath("$.content[0].address").value("Str. Victoriei 14, Timișoara"))
                .andExpect(jsonPath("$.content[0].phoneNumber").value("+40 721 000 000"))
                .andExpect(jsonPath("$.content[0].openLoanCount").value(2))
                .andExpect(jsonPath("$.content[0].createdAt").value("2026-02-01T08:00:00Z"))
                // No updatedAt on a member: the contract lists seven fields exactly.
                .andExpect(jsonPath("$.content[0].updatedAt").doesNotExist());
    }

    @Test
    void listPassesTheSearchTermThrough() throws Exception {
        when(memberService.list(eq("popescu"), any(Pageable.class)))
                .thenReturn(new PagedResponse<>(List.of(), 0, 20, 0L, 0));

        mockMvc.perform(get("/api/members").param("search", "popescu"))
                .andExpect(status().isOk());

        verify(memberService).list(eq("popescu"), any(Pageable.class));
    }

    @Test
    void getByIdReturnsTheMember() throws Exception {
        when(memberService.findById(12L)).thenReturn(POPESCU);

        mockMvc.perform(get("/api/members/12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(12))
                .andExpect(jsonPath("$.openLoanCount").value(2));
    }

    @Test
    void getByIdReturnsTheContractErrorShapeWhenUnknownOrArchived() throws Exception {
        when(memberService.findById(99L)).thenThrow(new ApiException(ErrorCode.MEMBER_NOT_FOUND));

        mockMvc.perform(get("/api/members/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.field").isEmpty())
                .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    void createReturns201WithBodyAndLocation() throws Exception {
        when(memberService.create(any(MemberRequest.class)))
                .thenReturn(new MemberResponse(12L, "Popescu Ion", "ion.popescu@example.ro",
                        "Str. Victoriei 14, Timișoara", "+40 721 000 000", 0L,
                        Instant.parse("2026-02-01T08:00:00Z")));

        mockMvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MEMBER_JSON))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/members/12"))
                .andExpect(jsonPath("$.id").value(12))
                .andExpect(jsonPath("$.openLoanCount").value(0));
    }

    @Test
    void createReturns409OnADuplicateName() throws Exception {
        when(memberService.create(any(MemberRequest.class)))
                .thenThrow(new ApiException(ErrorCode.MEMBER_NAME_ALREADY_EXISTS, "name"));

        mockMvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MEMBER_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_NAME_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.field").value("name"))
                .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    void createRejectsABlankName() throws Exception {
        mockMvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"   ","email":null,"address":null,"phoneNumber":null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.field").value("name"))
                .andExpect(jsonPath("$.message").isNotEmpty());

        verifyNoInteractions(memberService);
    }

    @Test
    void createRejectsAMalformedEmail() throws Exception {
        mockMvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Popescu Ion","email":"not-an-email","address":null,"phoneNumber":null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.field").value("email"));

        verify(memberService, never()).create(any());
    }

    @Test
    void updateReturnsTheSavedMember() throws Exception {
        when(memberService.update(eq(12L), any(MemberRequest.class))).thenReturn(POPESCU);

        mockMvc.perform(put("/api/members/12")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MEMBER_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(12))
                .andExpect(jsonPath("$.openLoanCount").value(2));
    }

    @Test
    void updateReturns404WhenUnknown() throws Exception {
        when(memberService.update(eq(99L), any(MemberRequest.class)))
                .thenThrow(new ApiException(ErrorCode.MEMBER_NOT_FOUND));

        mockMvc.perform(put("/api/members/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MEMBER_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }

    @Test
    void deleteReturns204WithNoBody() throws Exception {
        mockMvc.perform(delete("/api/members/12"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(memberService).delete(12L);
    }

    @Test
    void deleteReturns409WhenTheMemberStillHoldsABook() throws Exception {
        doThrow(new ApiException(ErrorCode.MEMBER_HAS_OPEN_LOANS)).when(memberService).delete(12L);

        mockMvc.perform(delete("/api/members/12"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_HAS_OPEN_LOANS"))
                .andExpect(jsonPath("$.field").isEmpty())
                .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    void deleteReturns404WhenUnknown() throws Exception {
        doThrow(new ApiException(ErrorCode.MEMBER_NOT_FOUND)).when(memberService).delete(99L);

        mockMvc.perform(delete("/api/members/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }
}
