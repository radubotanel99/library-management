package com.library.management.backend.parameter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.library.management.backend.common.error.ApiException;
import com.library.management.backend.common.error.ErrorCode;
import com.library.management.backend.parameter.dto.ParameterRequest;
import com.library.management.backend.parameter.dto.ParameterResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;

/**
 * Unit tests for the typed settings accessors and the write path.
 *
 * <p>The key names and the fallback values are asserted as literals rather than
 * against the constants in the class under test: they are the contract with
 * {@code V2__seed_parameters.sql} and with {@code DATA_MODEL.md} §8, so a test that
 * read them from the same source it verifies would pass after a rename that breaks
 * production.
 *
 * <p>The warning that accompanies each fallback is deliberately not asserted --
 * capturing an appender would test slf4j, not this class. What matters is that a
 * broken row degrades instead of throwing.
 */
@ExtendWith(MockitoExtension.class)
class SettingsServiceTest {

    @Mock
    private ParameterRepository parameterRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private SettingsService settingsService;

    private static Parameter parameter(String key, String value) {
        Parameter parameter = new Parameter();
        parameter.setKey(key);
        parameter.setValue(value);
        return parameter;
    }

    private void stub(String key, String value) {
        when(parameterRepository.findById(key))
                .thenReturn(Optional.of(parameter(key, value)));
    }

    private void stubMissing(String key) {
        when(parameterRepository.findById(key)).thenReturn(Optional.empty());
    }

    @Nested
    @DisplayName("getDaysToKeepABook")
    class DaysToKeepABook {

        @Test
        void parsesTheStoredValue() {
            stub("DAYS_TO_KEEP_A_BOOK", "21");

            assertThat(settingsService.getDaysToKeepABook()).isEqualTo(21);
        }

        @Test
        void toleratesSurroundingWhitespace() {
            stub("DAYS_TO_KEEP_A_BOOK", "  21  ");

            assertThat(settingsService.getDaysToKeepABook()).isEqualTo(21);
        }

        @Test
        void fallsBackToTheSeededDefaultWhenTheRowIsMissing() {
            stubMissing("DAYS_TO_KEEP_A_BOOK");

            assertThat(settingsService.getDaysToKeepABook()).isEqualTo(14);
        }

        @ParameterizedTest
        @ValueSource(strings = {"fourteen", "", "14 days", "14.5"})
        void fallsBackToTheSeededDefaultForAnUnparsableValue(String stored) {
            stub("DAYS_TO_KEEP_A_BOOK", stored);

            // Degrades rather than throwing: a library that cannot lend because
            // somebody typo'd a settings row would be the worse failure.
            assertThat(settingsService.getDaysToKeepABook()).isEqualTo(14);
        }
    }

    @Nested
    @DisplayName("getMaxBooksPerMember")
    class MaxBooksPerMember {

        @Test
        void parsesTheStoredValue() {
            stub("MAX_BOOKS_PER_MEMBER", "5");

            assertThat(settingsService.getMaxBooksPerMember()).isEqualTo(5);
        }

        @Test
        void fallsBackToTheSeededDefaultWhenTheRowIsMissing() {
            stubMissing("MAX_BOOKS_PER_MEMBER");

            assertThat(settingsService.getMaxBooksPerMember()).isEqualTo(3);
        }

        @Test
        void fallsBackToTheSeededDefaultForAnUnparsableValue() {
            stub("MAX_BOOKS_PER_MEMBER", "three");

            assertThat(settingsService.getMaxBooksPerMember()).isEqualTo(3);
        }

        @Test
        void readsItsOwnKeyAndNothingElse() {
            stub("MAX_BOOKS_PER_MEMBER", "5");

            settingsService.getMaxBooksPerMember();

            // A swapped key would otherwise pass silently: an unstubbed findById
            // returns an empty Optional, which looks exactly like a missing row.
            verify(parameterRepository).findById("MAX_BOOKS_PER_MEMBER");
            verifyNoMoreInteractions(parameterRepository);
        }
    }

    @Nested
    @DisplayName("findAll")
    class FindAll {

        @Test
        void returnsEveryRowOrderedByKey() {
            Instant updatedAt = Instant.parse("2026-08-01T09:00:00Z");
            Parameter days = parameter("DAYS_TO_KEEP_A_BOOK", "14");
            days.setUpdatedAt(updatedAt);
            when(parameterRepository.findAll(Sort.by("key")))
                    .thenReturn(List.of(days, parameter("MAX_BOOKS_PER_MEMBER", "3")));

            assertThat(settingsService.findAll()).containsExactly(
                    new ParameterResponse("DAYS_TO_KEEP_A_BOOK", "14", updatedAt),
                    // Never written since it was seeded, so the audited column is still null.
                    new ParameterResponse("MAX_BOOKS_PER_MEMBER", "3", null));
        }
    }

    @Nested
    @DisplayName("save")
    class Save {

        private static final List<ParameterRequest> VALID = List.of(
                new ParameterRequest("DAYS_TO_KEEP_A_BOOK", "21"),
                new ParameterRequest("MAX_BOOKS_PER_MEMBER", "5"));

        private void stubReadBack() {
            when(parameterRepository.findAll(Sort.by("key"))).thenReturn(List.of(
                    parameter("DAYS_TO_KEEP_A_BOOK", "21"),
                    parameter("MAX_BOOKS_PER_MEMBER", "5")));
        }

        @Test
        void writesBothKeysAndReturnsThemAsStored() {
            stubReadBack();

            assertThat(settingsService.save(VALID)).containsExactly(
                    new ParameterResponse("DAYS_TO_KEEP_A_BOOK", "21", null),
                    new ParameterResponse("MAX_BOOKS_PER_MEMBER", "5", null));

            ArgumentCaptor<List<Parameter>> saved = ArgumentCaptor.captor();
            verify(parameterRepository).saveAll(saved.capture());
            assertThat(saved.getValue())
                    .extracting(Parameter::getKey, Parameter::getValue)
                    .containsExactly(
                            tuple("DAYS_TO_KEEP_A_BOOK", "21"),
                            tuple("MAX_BOOKS_PER_MEMBER", "5"));
        }

        @Test
        void flushesBeforeReadingBackSoUpdatedAtIsPopulated() {
            stubReadBack();

            settingsService.save(VALID);

            // Without the flush the audited updated_at would still be the pre-save
            // value in the response the client gets.
            verify(parameterRepository).flush();
        }

        @Test
        void storesTheParsedIntegerRatherThanTheRawText() {
            stubReadBack();

            settingsService.save(List.of(
                    new ParameterRequest("DAYS_TO_KEEP_A_BOOK", " 021 "),
                    new ParameterRequest("MAX_BOOKS_PER_MEMBER", "5")));

            ArgumentCaptor<List<Parameter>> saved = ArgumentCaptor.captor();
            verify(parameterRepository).saveAll(saved.capture());
            assertThat(saved.getValue()).first().extracting(Parameter::getValue).isEqualTo("21");
        }

        @Test
        void publishesTheUpdateEventWhenTheLendingPeriodChanges() {
            stub("DAYS_TO_KEEP_A_BOOK", "14");
            stubReadBack();

            settingsService.save(VALID);

            // Synchronous by design: the listener runs inside this transaction, so a
            // failed re-evaluation rolls the save back (API_CONTRACT.md §9).
            verify(eventPublisher).publishEvent(any(ParametersUpdatedEvent.class));
        }

        @Test
        void doesNotPublishWhenTheLendingPeriodIsUnchanged() {
            // The event triggers a table-wide bulk update over every open loan; a save
            // that leaves DAYS_TO_KEEP_A_BOOK exactly as it was has nothing to
            // re-evaluate, and firing it anyway would just take a write lock for
            // nothing (see the class Javadoc).
            stub("DAYS_TO_KEEP_A_BOOK", "21");
            stubReadBack();

            settingsService.save(VALID);

            verifyNoInteractions(eventPublisher);
            // The write itself still happens -- only the event is gated.
            verify(parameterRepository).saveAll(any());
        }

        @Test
        void rejectsADuplicateKey() {
            List<ParameterRequest> requests = List.of(
                    new ParameterRequest("DAYS_TO_KEEP_A_BOOK", "21"),
                    new ParameterRequest("DAYS_TO_KEEP_A_BOOK", "28"),
                    new ParameterRequest("MAX_BOOKS_PER_MEMBER", "5"));

            assertRejects(requests, "DAYS_TO_KEEP_A_BOOK");
        }

        @Test
        void rejectsAnUnrecognisedKey() {
            List<ParameterRequest> requests = List.of(
                    new ParameterRequest("DAYS_TO_KEEP_A_BOOK", "21"),
                    new ParameterRequest("MAX_BOOKS_PER_MEMBER", "5"),
                    new ParameterRequest("LIBRARY_NAME", "7"));

            assertRejects(requests, "LIBRARY_NAME");
        }

        @Test
        void rejectsAMissingKey() {
            assertRejects(List.of(new ParameterRequest("DAYS_TO_KEEP_A_BOOK", "21")), "MAX_BOOKS_PER_MEMBER");
        }

        @Test
        void rejectsAnEmptyRequest() {
            // Both keys are missing; either one is a legitimate complaint, so only
            // the code is asserted here.
            assertThatThrownBy(() -> settingsService.save(List.of()))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PARAMETER_INVALID_VALUE);

            verifyNoInteractions(parameterRepository, eventPublisher);
        }

        @ParameterizedTest
        @ValueSource(strings = {"0", "-1", "fourteen", "", "  ", "14 days", "14.5", "9999999999"})
        void rejectsAValueThatIsNotAPositiveInteger(String value) {
            // Unlike the read path, which degrades to a default, a write is strict:
            // there is no reason to accept a new unusable value.
            assertRejects(
                    List.of(
                            new ParameterRequest("DAYS_TO_KEEP_A_BOOK", value),
                            new ParameterRequest("MAX_BOOKS_PER_MEMBER", "5")),
                    "DAYS_TO_KEEP_A_BOOK");
        }

        @Test
        void writesNothingWhenOnlyTheSecondValueIsInvalid() {
            assertRejects(
                    List.of(
                            new ParameterRequest("DAYS_TO_KEEP_A_BOOK", "21"),
                            new ParameterRequest("MAX_BOOKS_PER_MEMBER", "-5")),
                    "MAX_BOOKS_PER_MEMBER");
        }

        /**
         * Asserts the contract error -- code plus the offending key as {@code field}
         * ({@code API_CONTRACT.md} §9) -- and that nothing was written or announced.
         */
        private void assertRejects(List<ParameterRequest> requests, String expectedField) {
            assertThatThrownBy(() -> settingsService.save(requests))
                    .isInstanceOf(ApiException.class)
                    .satisfies(thrown -> {
                        ApiException ex = (ApiException) thrown;
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PARAMETER_INVALID_VALUE);
                        assertThat(ex.getField()).isEqualTo(expectedField);
                    });

            verify(parameterRepository, never()).saveAll(any());
            verify(parameterRepository, never()).flush();
            verifyNoInteractions(eventPublisher);
        }
    }
}
