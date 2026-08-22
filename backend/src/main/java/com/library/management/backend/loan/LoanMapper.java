package com.library.management.backend.loan;

import com.library.management.backend.loan.dto.LoanResponse;
import java.time.Instant;

/**
 * Entity to DTO conversion for loans.
 *
 * <p>Package-private and static, like {@code BookMapper} and {@code MemberMapper}:
 * the mapping is a pure function with no state and no dependencies, so making it a
 * Spring bean would buy nothing. Keeping it out of the {@code dto} package and out
 * of public view means only the service can call it -- the entity never gets a
 * chance to leave the service layer.
 *
 * <p>The three derived values are passed in rather than read off the entity, for the
 * same reason {@code onLoan} is on {@code BookMapper}: none of them is a column.
 * {@code dueAt} and {@code daysOverdue} do not exist on {@code loan} at all, and the
 * effective state depends on the current lending period rather than on
 * {@code loan.state}. Computing them needs the settings and the clock, which belong
 * to the service.
 */
final class LoanMapper {

    private LoanMapper() {
    }

    /**
     * <p>{@code getBook()} and {@code getMember()} initialise the lazy associations
     * if they are still proxies. Every path that reaches here either fetch-joins both
     * or runs inside the service transaction, so it never fails -- but it is the
     * reason the loan queries fetch-join rather than relying on lazy loading per row.
     *
     * @param effectiveState the state as of now, <em>not</em> {@code loan.getState()}
     */
    static LoanResponse toResponse(Loan loan,
                                   LoanState effectiveState,
                                   Instant dueAt,
                                   int daysOverdue) {
        return new LoanResponse(
                loan.getId(),
                loan.getBook().getId(),
                loan.getBook().getTitle(),
                loan.getBook().getAuthor(),
                loan.getBook().getBookNumber(),
                loan.getMember().getId(),
                loan.getMember().getName(),
                effectiveState,
                loan.getCreatedAt(),
                dueAt,
                daysOverdue,
                loan.getFinishedAt());
    }
}
