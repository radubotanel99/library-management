package com.library.management.backend.parameter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the typed settings accessors.
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
}
