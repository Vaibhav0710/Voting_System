package com.voting.loadtests;

import com.voting.loadtests.ElectionReport.Mode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ElectionReportTest {

    private static final UUID A = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID B = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final Duration WAITED = Duration.ofSeconds(3);

    @Test
    void okWhenEverythingMatches() {
        ElectionReport report = report(Mode.PACED, 100, Map.of(A, 60L, B, 40L), true, 0, Map.of(A, 60L, B, 40L), WAITED);

        assertThat(report.problems()).isEmpty();
        assertThat(report.describe()).endsWith("INTEGRITY OK");
    }

    @Test
    void flagsLostVote() {
        ElectionReport report = report(Mode.PACED, 100, Map.of(A, 99L), true, 0, Map.of(A, 99L), WAITED);

        assertThat(report.problems()).anyMatch(p -> p.contains("1 accepted vote(s) not stored"));
        assertThat(report.describe()).contains("INTEGRITY FAILED");
    }

    @Test
    void flagsBrokenChain() {
        ElectionReport report = report(Mode.PACED, 100, Map.of(A, 100L), false, 2, Map.of(A, 100L), WAITED);

        assertThat(report.problems()).anyMatch(p -> p.contains("chain invalid (2 broken links)"));
    }

    @Test
    void flagsTallyBehindStore() {
        ElectionReport report = report(Mode.PACED, 100, Map.of(A, 100L), true, 0, Map.of(A, 90L),
                Duration.ofSeconds(120));

        assertThat(report.problems()).anyMatch(p -> p.contains("tally 90 of 100 after 120 s"));
    }

    @Test
    void flagsCandidateMismatch() {
        ElectionReport report = report(Mode.PACED, 100, Map.of(A, 60L, B, 40L), true, 0, Map.of(A, 59L, B, 41L),
                WAITED);

        assertThat(report.problems()).anyMatch(p -> p.contains(A.toString()));
    }

    @Test
    void overloadReportsStoredAfterTimeout() {
        ElectionReport report = report(Mode.OVERLOAD, 9_000, Map.of(A, 9_050L), true, 0, Map.of(A, 9_050L), WAITED);

        assertThat(report.storedAfterTimeout()).isEqualTo(50);
        assertThat(report.problems()).isEmpty();
    }

    @Test
    void pacedFlagsStoredAboveAccepted() {
        ElectionReport report = report(Mode.PACED, 100, Map.of(A, 101L), true, 0, Map.of(A, 101L), WAITED);

        assertThat(report.problems()).anyMatch(p -> p.contains("1 vote(s) stored without a 201"));
    }

    @Test
    void requireIntactPassesWhenNoProblems() {
        ElectionReport report = report(Mode.OVERLOAD, 9_000, Map.of(A, 9_050L), true, 0, Map.of(A, 9_050L), WAITED);

        assertThatCode(report::requireIntact).doesNotThrowAnyException();
    }

    @Test
    void requireIntactFailsWithEveryProblem() {
        ElectionReport report = report(Mode.PACED, 100, Map.of(A, 99L), false, 2, Map.of(A, 99L), WAITED);

        assertThatThrownBy(report::requireIntact)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INTEGRITY FAILED")
                .hasMessageContaining("1 accepted vote(s) not stored")
                .hasMessageContaining("chain invalid (2 broken links)");
    }

    private static ElectionReport report(Mode mode, long accepted, Map<UUID, Long> stored, boolean chainValid,
                                         long brokenLinks, Map<UUID, Long> tallied, Duration tallyWait) {
        return new ElectionReport(mode, accepted, sum(stored), stored, chainValid, brokenLinks, sum(tallied), tallied,
                tallyWait);
    }

    private static long sum(Map<UUID, Long> counts) {
        return counts.values().stream().mapToLong(Long::longValue).sum();
    }
}
