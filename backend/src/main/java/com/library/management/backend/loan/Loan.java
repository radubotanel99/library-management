package com.library.management.backend.loan;

import com.library.management.backend.book.Book;
import com.library.management.backend.common.AuditableEntity;
import com.library.management.backend.member.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * One book lent to one member.
 *
 * <p>The inherited {@code createdAt} field <em>is</em> the borrow date. The due
 * date is not stored: a later phase derives it as
 * {@code createdAt + parameter(DAYS_TO_KEEP_A_BOOK)} at read time, so that
 * changing the lending period retroactively re-evaluates every open loan,
 * flipping it between active and overdue. Storing it would freeze that.
 *
 * <p>Therefore: do not add a {@code dueAt} field, nor an {@code @Formula} or
 * {@code @Transient} stand-in for one. That derivation belongs in the service
 * layer, not on this entity.
 *
 * <p>The partial unique index {@code ux_loan_one_open_per_book} guarantees a book
 * has at most one unfinished loan.
 */
@Entity
@Table(name = "loan")
@Getter
@Setter
public class Loan extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LoanState state;

    /** When the book was returned; null while the loan is open. */
    @Column(name = "finished_at")
    private Instant finishedAt;
}
