package com.library.management.backend.loan;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.library.management.backend.parameter.ParametersUpdatedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the wiring between the two triggers and the re-evaluation.
 *
 * <p>What this class contributes is delegation and nothing else, so that is all that
 * is asserted: each trigger calls {@code reevaluateOverdue()} exactly once and does
 * nothing further. The behaviour of the re-evaluation itself belongs to
 * {@code LoanServiceTest}.
 *
 * <p>The annotations are not exercised here -- proving that {@code @Scheduled} fires
 * at 02:00 or that {@code @EventListener} is invoked would test Spring, and would
 * need a context to do it.
 */
@ExtendWith(MockitoExtension.class)
class OverdueReevaluationSchedulerTest {

    @Mock
    private LoanService loanService;

    @InjectMocks
    private OverdueReevaluationScheduler scheduler;

    @Test
    void dailyRunReevaluatesOnce() {
        scheduler.runDaily();

        verify(loanService).reevaluateOverdue();
        verifyNoMoreInteractions(loanService);
    }

    @Test
    void parametersUpdatedReevaluatesOnce() {
        scheduler.onParametersUpdated(new ParametersUpdatedEvent());

        verify(loanService).reevaluateOverdue();
        verifyNoMoreInteractions(loanService);
    }
}
