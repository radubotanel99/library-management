package com.library.management.backend.parameter;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.library.management.backend.common.error.ApiException;
import com.library.management.backend.common.error.ErrorCode;
import com.library.management.backend.parameter.dto.ParameterRequest;
import com.library.management.backend.parameter.dto.ParameterResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-layer tests: status codes and the JSON shapes from
 * {@code API_CONTRACT.md} §9. The service is mocked -- its validation rules are
 * covered by {@link SettingsServiceTest}.
 *
 * <p>These also exercise the {@code @RestControllerAdvice}, which the slice loads
 * alongside the controller.
 */
@WebMvcTest(ParameterController.class)
class ParameterControllerTest {

    private static final String VALID_JSON = """
            [{"key":"DAYS_TO_KEEP_A_BOOK","value":"21"},{"key":"MAX_BOOKS_PER_MEMBER","value":"5"}]
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SettingsService settingsService;

    @Test
    void listReturnsAPlainArrayWithTheContractFields() throws Exception {
        when(settingsService.findAll()).thenReturn(List.of(
                new ParameterResponse("DAYS_TO_KEEP_A_BOOK", "14", Instant.parse("2026-08-01T09:00:00Z")),
                new ParameterResponse("MAX_BOOKS_PER_MEMBER", "3", null)));

        mockMvc.perform(get("/api/parameters"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].key").value("DAYS_TO_KEEP_A_BOOK"))
                // A string on the wire even though it is an integer -- the column is text.
                .andExpect(jsonPath("$[0].value").value("14"))
                .andExpect(jsonPath("$[0].updatedAt").value("2026-08-01T09:00:00Z"))
                .andExpect(jsonPath("$[1].updatedAt").isEmpty());
    }

    @Test
    void saveReturnsTheStoredSet() throws Exception {
        when(settingsService.save(any())).thenReturn(List.of(
                new ParameterResponse("DAYS_TO_KEEP_A_BOOK", "21", Instant.parse("2026-08-23T10:00:00Z")),
                new ParameterResponse("MAX_BOOKS_PER_MEMBER", "5", Instant.parse("2026-08-23T10:00:00Z"))));

        mockMvc.perform(put("/api/parameters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].value").value("21"))
                .andExpect(jsonPath("$[1].value").value("5"));

        verify(settingsService).save(List.of(
                new ParameterRequest("DAYS_TO_KEEP_A_BOOK", "21"),
                new ParameterRequest("MAX_BOOKS_PER_MEMBER", "5")));
    }

    @Test
    void saveReturns400WithTheOffendingKeyAsTheField() throws Exception {
        when(settingsService.save(any()))
                .thenThrow(new ApiException(ErrorCode.PARAMETER_INVALID_VALUE, "DAYS_TO_KEEP_A_BOOK"));

        mockMvc.perform(put("/api/parameters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PARAMETER_INVALID_VALUE"))
                // The body is an array, so the key identifies the input to highlight
                // where a JSON path could not.
                .andExpect(jsonPath("$.field").value("DAYS_TO_KEEP_A_BOOK"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    void saveRejectsABlankValueBeforeReachingTheService() throws Exception {
        mockMvc.perform(put("/api/parameters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"key":"DAYS_TO_KEEP_A_BOOK","value":"  "}]
                                """))
                .andExpect(status().isBadRequest())
                // Structural, so it is the generic code -- PARAMETER_INVALID_VALUE is
                // reserved for the business rule the service applies.
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(settingsService, never()).save(any());
    }

    @Test
    void saveRejectsAMalformedBody() throws Exception {
        mockMvc.perform(put("/api/parameters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(settingsService);
    }
}
