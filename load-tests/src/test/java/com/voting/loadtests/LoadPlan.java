package com.voting.loadtests;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;

/** Checks run before a simulation injects anyone, so a bad voters file fails clearly instead of mid-run. */
final class LoadPlan {

    private LoadPlan() {
    }

    /**
     * Votes a step test sends: every level at its rate, plus the {@code steps - 1} linear ramps between levels,
     * rounded up (matches Gatling's {@code incrementUsersPerSec(...).separatedByRampsLasting(...)}).
     */
    static long capacityArrivals(int startRate, int stepRate, int steps, int levelSeconds, int rampSeconds) {
        double arrivals = 0;
        for (int level = 0; level < steps; level++) {
            int rate = startRate + level * stepRate;
            arrivals += (double) rate * levelSeconds;
            if (level < steps - 1) {
                arrivals += (rate + stepRate / 2.0) * rampSeconds;
            }
        }
        return (long) Math.ceil(arrivals);
    }

    /** Seeded voters in the file: every line except the {@code token} header. */
    static int countVoters(Path votersFile) {
        try (Stream<String> lines = Files.lines(votersFile)) {
            return (int) Math.max(0, lines.count() - 1);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + votersFile, e);
        }
    }

    static void requireEnoughVoters(Path votersFile, long needed) {
        int voters = countVoters(votersFile);
        if (voters < needed) {
            throw new IllegalStateException("This run needs " + needed + " voters but " + votersFile.getFileName()
                    + " has " + voters + ": seed more voters first");
        }
    }

    /** Voter tokens expire ({@code JWT_EXPIRATION}, 24 h by default); stale tokens would turn every vote into a 401. */
    static void requireFreshVoters(Path votersFile, Duration maxAge, Clock clock) {
        Instant written = lastModified(votersFile);
        if (written.plus(maxAge).isBefore(clock.instant())) {
            throw new IllegalStateException(votersFile.getFileName() + " is older than " + maxAge.toHours()
                    + " h, so its tokens may have expired: re-seed first");
        }
    }

    private static Instant lastModified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toInstant();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file, e);
        }
    }
}
