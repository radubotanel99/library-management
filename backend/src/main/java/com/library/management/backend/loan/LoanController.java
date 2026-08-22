package com.library.management.backend.loan;

import com.library.management.backend.common.dto.PagedResponse;
import com.library.management.backend.loan.dto.LoanRequest;
import com.library.management.backend.loan.dto.LoanResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Loan endpoints ({@code API_CONTRACT.md} §8).
 *
 * <p>Pure HTTP plumbing: bind, delegate, map the result onto a status code. No
 * business rules and no try/catch -- rule violations arrive as
 * {@code ApiException} and are turned into responses by the single
 * {@code @RestControllerAdvice}.
 *
 * <p>Which sort properties are actually honoured is a service decision, not a
 * binding one, so the {@link Pageable} is passed through untouched. The default
 * ordering is newest first, unlike the alphabetical catalogue and member list: a
 * loans screen is a worklist.
 */
@RestController
@RequestMapping("/api/loans")
public class LoanController {

    private final LoanService loanService;

    public LoanController(LoanService loanService) {
        this.loanService = loanService;
    }

    /**
     * {@code state} accepts {@code OPEN} as well as the three stored values -- it is
     * an alias for "still out", resolved by the service.
     */
    @GetMapping
    public PagedResponse<LoanResponse> list(
            @RequestParam(required = false) LoanStateFilter state,
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) Long bookId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return loanService.list(state, memberId, bookId, search, pageable);
    }

    @GetMapping("/{id}")
    public LoanResponse findById(@PathVariable Long id) {
        return loanService.findById(id);
    }

    /** The borrow date is stamped server-side; the payload carries ids only. */
    @PostMapping
    public ResponseEntity<LoanResponse> create(@Valid @RequestBody LoanRequest request) {
        LoanResponse created = loanService.create(request);
        return ResponseEntity
                .created(URI.create("/api/loans/" + created.id()))
                .body(created);
    }

    /**
     * Takes a copy back. A {@code POST} to a sub-resource rather than a {@code PUT}
     * because it is an action, not a full-resource replacement -- and it needs no
     * body at all.
     *
     * <p>{@code consumes} is set even though no body is bound: without it, this
     * bodyless {@code POST} is a CORS "simple request" -- no auth exists yet to
     * block it, so a cross-origin auto-submitting HTML form (urlencoded, no
     * preflight) could invoke it. Requiring a JSON content type forces a preflight,
     * which the CORS policy rejects for foreign origins. The frontend already sends
     * {@code {}} as the body, so this is not a contract change.
     */
    @PostMapping(path = "/{id}/return", consumes = MediaType.APPLICATION_JSON_VALUE)
    public LoanResponse returnLoan(@PathVariable Long id) {
        return loanService.returnLoan(id);
    }
}
