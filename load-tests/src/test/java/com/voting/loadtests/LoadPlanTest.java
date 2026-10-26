package com.voting.loadtests;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoadPlanTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-27T10:00:00Z"), ZoneOffset.UTC);
    private static final Duration MAX_AGE = Duration.ofHours(23);

    @TempDir
    Path dir;

    @Test
    void capacityArrivalsAddsLevelsAndRamps() {
        assertThat(LoadPlan.capacityArrivals(20, 20, 6, 15, 5)).isEqualTo(8050);
        assertThat(LoadPlan.capacityArrivals(2, 1, 2, 5, 1)).isEqualTo(28);
    }

    @Test
    void refusesWhenVotersFileIsShort() throws IOException {
        Path voters = votersFile(3);

        assertThat(LoadPlan.countVoters(voters)).isEqualTo(3);
        assertThatThrownBy(() -> LoadPlan.requireEnoughVoters(voters, 4))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("needs 4 voters")
                .hasMessageContaining("has 3");
        assertThatCode(() -> LoadPlan.requireEnoughVoters(voters, 3)).doesNotThrowAnyException();
    }

    @Test
    void refusesStaleVotersFile() throws IOException {
        Path voters = votersFile(1);

        Files.setLastModifiedTime(voters, FileTime.from(CLOCK.instant().minus(Duration.ofHours(24))));
        assertThatThrownBy(() -> LoadPlan.requireFreshVoters(voters, MAX_AGE, CLOCK))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("re-seed");

        Files.setLastModifiedTime(voters, FileTime.from(CLOCK.instant().minus(Duration.ofHours(1))));
        assertThatCode(() -> LoadPlan.requireFreshVoters(voters, MAX_AGE, CLOCK)).doesNotThrowAnyException();
    }

    private Path votersFile(int tokens) throws IOException {
        Path file = dir.resolve("voters.csv");
        List<String> lines = new ArrayList<>();
        lines.add("token");
        for (int i = 0; i < tokens; i++) {
            lines.add("token-" + i);
        }
        return Files.write(file, lines);
    }
}
