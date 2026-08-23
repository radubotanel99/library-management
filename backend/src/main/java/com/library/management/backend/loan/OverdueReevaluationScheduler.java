package com.library.management.backend.loan;

import com.library.management.backend.parameter.ParametersUpdatedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The two triggers for {@link LoanService#reevaluateOverdue()}
 * ({@code API_CONTRACT.md} §11): a daily job, and a settings save.
 *
 * <p>The daily run catches loans that quietly came due overnight. The event run
 * catches the other cause of a state change -- {@code DAYS_TO_KEEP_A_BOOK} moving --
 * which shifts every due date at once and must not be left waiting until 02:00
 * ({@code DATA_MODEL.md} §6).
 *
 * <p>Both triggers call the one method; none of its logic is repeated here. This
 * class holds nothing but the wiring, so that "when does it run" stays separate from
 * "what does it do".
 *
 * <p>It lives in {@code loan} rather than {@code parameter} on purpose.
 * {@code LoanService} already injects {@code SettingsService}; a listener on the
 * {@code parameter} side would have to inject {@code LoanService} back and Spring
 * would refuse to start. Depending on {@code parameter} only for the event type keeps
 * the arrow pointing one way.
 *
 * <p>{@code @EventListener}, deliberately not {@code @TransactionalEventListener} and
 * not {@code @Async}: the publisher's {@code publishEvent} is synchronous, so this
 * runs on the caller's thread inside the caller's transaction. That is the required
 * semantics -- settings and loan state may never disagree, so a re-evaluation that
 * fails has to roll the settings save back with it. An after-commit or asynchronous
 * listener would let the save survive its own follow-up.
 */
@Component
public class OverdueReevaluationScheduler {

    private final LoanService loanService;

    public OverdueReevaluationScheduler(LoanService loanService) {
        this.loanService = loanService;
    }

    /**
     * Daily sweep at 02:00 UTC -- off-hours, and explicitly UTC because every
     * timestamp in this application is stored and compared in UTC, so the job's day
     * boundary matches the arithmetic it is refreshing.
     */
    @Scheduled(cron = "0 0 2 * * *", zone = "UTC")
    public void runDaily() {
        loanService.reevaluateOverdue();
    }

    @EventListener
    public void onParametersUpdated(ParametersUpdatedEvent event) {
        loanService.reevaluateOverdue();
    }
}
