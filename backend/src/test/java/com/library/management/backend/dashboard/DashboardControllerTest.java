package com.library.management.backend.dashboard;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.library.management.backend.dashboard.dto.DashboardResponse;
import com.library.management.backend.dashboard.dto.MostBorrowedBookResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-layer tests: the status code and the exact JSON shape from
 * {@code API_CONTRACT.md} §10. The service is mocked -- how the figures are derived
 * is covered by {@link DashboardServiceTest}.
 *
 * <p>Field names are asserted one by one on purpose. This is the only place the wire
 * contract is checked, and the Angular {@code DashboardResponse} interface is written
 * against these exact names: a rename on either side has to fail here.
 */
@WebMvcTest(DashboardController.class)
class DashboardControllerTest {

    private static final DashboardResponse DASHBOARD = new DashboardResponse(
            137L,
            64L,
            23L,
            4L,
            List.of(
                    new MostBorrowedBookResponse(41L, "Amintiri din copilărie", "Ion Creangă", 18L),
                    new MostBorrowedBookResponse(42L, "Baltagul", "Mihail Sadoveanu", 11L)));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;

    @Test
    void returnsEveryFigureUnderItsContractName() throws Exception {
        when(dashboardService.load()).thenReturn(DASHBOARD);

        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.totalCopies").value(137))
                .andExpect(jsonPath("$.totalMembers").value(64))
                .andExpect(jsonPath("$.loansActive").value(23))
                .andExpect(jsonPath("$.loansOverdue").value(4))
                .andExpect(jsonPath("$.mostBorrowed", hasSize(2)))
                .andExpect(jsonPath("$.mostBorrowed[0].bookId").value(41))
                .andExpect(jsonPath("$.mostBorrowed[0].title").value("Amintiri din copilărie"))
                .andExpect(jsonPath("$.mostBorrowed[0].author").value("Ion Creangă"))
                .andExpect(jsonPath("$.mostBorrowed[0].loanCount").value(18))
                .andExpect(jsonPath("$.mostBorrowed[1].bookId").value(42))
                .andExpect(jsonPath("$.mostBorrowed[1].loanCount").value(11));
    }

    /** The order the service returns is the order the client renders -- no re-sorting. */
    @Test
    void keepsTheMostBorrowedOrderTheServiceProduced() throws Exception {
        when(dashboardService.load()).thenReturn(DASHBOARD);

        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mostBorrowed[0].title").value("Amintiri din copilărie"))
                .andExpect(jsonPath("$.mostBorrowed[1].title").value("Baltagul"));
    }

    /**
     * A library that has not lent anything yet is a real state, not an edge case, and
     * the frontend renders {@code []} and {@code null} differently -- so the empty
     * list has to survive serialisation as an empty array.
     */
    @Test
    void serialisesAnEmptyMostBorrowedListAsAnArrayNotNull() throws Exception {
        when(dashboardService.load())
                .thenReturn(new DashboardResponse(0L, 0L, 0L, 0L, List.of()));

        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mostBorrowed").isArray())
                .andExpect(jsonPath("$.mostBorrowed", hasSize(0)))
                .andExpect(jsonPath("$.totalCopies").value(0))
                .andExpect(jsonPath("$.loansOverdue").value(0));
    }
}
