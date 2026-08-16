package com.library.management.backend.category;

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

import com.library.management.backend.category.dto.CategoryRequest;
import com.library.management.backend.category.dto.CategoryResponse;
import com.library.management.backend.common.error.ApiException;
import com.library.management.backend.common.error.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-layer tests: status codes, headers, and the error body shape from
 * {@code API_CONTRACT.md} §3. The service is mocked -- its rules are covered by
 * {@link CategoryServiceTest}.
 *
 * <p>These also exercise the {@code @RestControllerAdvice}, which the slice loads
 * alongside the controller.
 */
@WebMvcTest(CategoryController.class)
class CategoryControllerTest {

    private static final String FICTION_JSON = """
            {"name":"Fiction","description":"Novels and short stories"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryService categoryService;

    @Test
    void listReturnsAPlainArray() throws Exception {
        when(categoryService.findAll())
                .thenReturn(List.of(new CategoryResponse(3L, "Fiction", "Novels and short stories", 42L)));

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(3))
                .andExpect(jsonPath("$[0].name").value("Fiction"))
                .andExpect(jsonPath("$[0].description").value("Novels and short stories"))
                .andExpect(jsonPath("$[0].bookCount").value(42));
    }

    @Test
    void getByIdReturnsTheCategory() throws Exception {
        when(categoryService.findById(3L))
                .thenReturn(new CategoryResponse(3L, "Fiction", null, 0L));

        mockMvc.perform(get("/api/categories/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.description").isEmpty());
    }

    @Test
    void getByIdReturnsTheContractErrorShapeWhenUnknown() throws Exception {
        when(categoryService.findById(99L)).thenThrow(new ApiException(ErrorCode.CATEGORY_NOT_FOUND));

        mockMvc.perform(get("/api/categories/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.field").isEmpty())
                .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    void getByIdRejectsANonNumericId() throws Exception {
        mockMvc.perform(get("/api/categories/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.field").value("id"));

        verifyNoInteractions(categoryService);
    }

    @Test
    void createReturns201WithBodyAndLocation() throws Exception {
        when(categoryService.create(any(CategoryRequest.class)))
                .thenReturn(new CategoryResponse(7L, "Fiction", "Novels and short stories", 0L));

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FICTION_JSON))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/categories/7"))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.bookCount").value(0));
    }

    @Test
    void createReturns409OnADuplicateName() throws Exception {
        when(categoryService.create(any(CategoryRequest.class)))
                .thenThrow(new ApiException(ErrorCode.CATEGORY_NAME_ALREADY_EXISTS, "name"));

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FICTION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_NAME_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.field").value("name"))
                .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    void createRejectsABlankName() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"   ","description":null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.field").value("name"))
                .andExpect(jsonPath("$.message").isNotEmpty());

        verify(categoryService, never()).create(any());
    }

    @Test
    void createReportsTheAlphabeticallyFirstFieldWhenSeveralAreInvalid() throws Exception {
        String tooLongDescription = "d".repeat(501);

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"description\":\"" + tooLongDescription + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.field").value("description"));
    }

    @Test
    void createRejectsAMalformedBody() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(categoryService);
    }

    @Test
    void updateReturnsTheSavedCategory() throws Exception {
        when(categoryService.update(eq(3L), any(CategoryRequest.class)))
                .thenReturn(new CategoryResponse(3L, "Fiction", "Novels and short stories", 42L));

        mockMvc.perform(put("/api/categories/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FICTION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.bookCount").value(42));
    }

    @Test
    void updateReturns409OnADuplicateName() throws Exception {
        when(categoryService.update(eq(3L), any(CategoryRequest.class)))
                .thenThrow(new ApiException(ErrorCode.CATEGORY_NAME_ALREADY_EXISTS, "name"));

        mockMvc.perform(put("/api/categories/3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FICTION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_NAME_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.field").value("name"));
    }

    @Test
    void updateReturns404WhenUnknown() throws Exception {
        when(categoryService.update(eq(99L), any(CategoryRequest.class)))
                .thenThrow(new ApiException(ErrorCode.CATEGORY_NOT_FOUND));

        mockMvc.perform(put("/api/categories/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FICTION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
    }

    @Test
    void deleteReturns204WithNoBody() throws Exception {
        mockMvc.perform(delete("/api/categories/3"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(categoryService).delete(3L);
    }

    @Test
    void deleteReturns409WhenTheCategoryStillHasBooks() throws Exception {
        doThrow(new ApiException(ErrorCode.CATEGORY_HAS_BOOKS)).when(categoryService).delete(3L);

        mockMvc.perform(delete("/api/categories/3"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_HAS_BOOKS"))
                .andExpect(jsonPath("$.field").isEmpty())
                .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    void deleteReturns404WhenUnknown() throws Exception {
        doThrow(new ApiException(ErrorCode.CATEGORY_NOT_FOUND)).when(categoryService).delete(99L);

        mockMvc.perform(delete("/api/categories/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
    }
}
