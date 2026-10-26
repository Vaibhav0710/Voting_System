package com.voting.loadtests;

import com.fasterxml.jackson.databind.JsonNode;
import com.voting.loadtests.ElectionReport.Mode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Post-run integrity check through the gateway: stored votes (voting-service), the hash chain (admin validation) and
 * the live tally (result-service). Votes can still commit after the gateway gave up on them, so the check waits until
 * the stored count has been steady for {@link #STABLE_FOR} and the tally matches it, or until
 * {@code TALLY_WAIT_SECONDS}.
 */
final class ElectionCheck {

    private static final Duration POLL_INTERVAL = Duration.ofSeconds(1);
    private static final Duration STABLE_FOR = Duration.ofSeconds(5);
    private static final int SERVICE_UNAVAILABLE = 503;

    private record Counts(long total, Map<UUID, Long> byCandidate) {
    }

    private record Settled(Counts stored, Counts tallied, Duration tallyWait) {
    }

    private ElectionCheck() {
    }

    static ElectionReport run(Mode mode, String adminToken, ApiSetup.Election election, long accepted) {
        Instant deadline = Instant.now().plusSeconds(LoadTestConfig.TALLY_WAIT_SECONDS);
        Settled settled = waitUntilSettled(adminToken, election.id(), deadline);
        JsonNode chain = untilAvailable(() -> ApiSetup.post("/votes/chain/validate/" + election.id(), Map.of(),
                adminToken, 200), deadline).path("data");
        ElectionReport report = new ElectionReport(mode, accepted,
                settled.stored().total(), settled.stored().byCandidate(),
                chain.path("chainValid").asBoolean(), chain.path("brokenLinks").asLong(),
                settled.tallied().total(), settled.tallied().byCandidate(), settled.tallyWait());
        publish(election.id(), report);
        return report;
    }

    /**
     * Calls the gateway until it stops answering 503, which it does while a circuit breaker is open after an overload
     * run; any other failure, or a 503 after {@code deadline}, is rethrown.
     */
    static <T> T untilAvailable(Supplier<T> call, Instant deadline) {
        while (true) {
            try {
                return call.get();
            } catch (ApiSetup.UnexpectedStatusException e) {
                if (e.status() != SERVICE_UNAVAILABLE || !Instant.now().isBefore(deadline)) {
                    throw e;
                }
                pause();
            }
        }
    }

    /** {@code tallyWait} is the time until the tally first matched the final stored count (or the full wait). */
    private static Settled waitUntilSettled(String adminToken, UUID electionId, Instant deadline) {
        Instant start = Instant.now();
        Counts stored = storedVotes(adminToken, electionId, deadline);
        Counts tallied = talliedVotes(adminToken, electionId, deadline);
        Instant storedSince = start;
        Instant matchedAt = null;
        while (Instant.now().isBefore(deadline)) {
            if (tallied.total() != stored.total()) {
                matchedAt = null;
            } else if (matchedAt == null) {
                matchedAt = Instant.now();
            }
            if (matchedAt != null && Duration.between(storedSince, Instant.now()).compareTo(STABLE_FOR) >= 0) {
                break;
            }
            pause();
            Counts latest = storedVotes(adminToken, electionId, deadline);
            if (latest.total() != stored.total()) {
                stored = latest;
                storedSince = Instant.now();
                matchedAt = null;
            }
            tallied = talliedVotes(adminToken, electionId, deadline);
        }
        Instant end = matchedAt == null ? Instant.now() : matchedAt;
        return new Settled(stored, tallied, Duration.between(start, end));
    }

    private static Counts storedVotes(String adminToken, UUID electionId, Instant deadline) {
        JsonNode data = untilAvailable(() -> ApiSetup.get("/votes/election/" + electionId + "/count", adminToken, 200),
                deadline).path("data");
        return new Counts(data.path("totalVotes").asLong(), byCandidate(data.path("candidateVoteCounts")));
    }

    private static Counts talliedVotes(String adminToken, UUID electionId, Instant deadline) {
        JsonNode data = untilAvailable(() -> ApiSetup.get("/results/election/" + electionId, adminToken, 200),
                deadline).path("data");
        return new Counts(data.path("totalVotes").asLong(), byCandidate(data.path("candidateResults")));
    }

    private static Map<UUID, Long> byCandidate(JsonNode rows) {
        Map<UUID, Long> counts = new HashMap<>();
        rows.forEach(row -> counts.put(UUID.fromString(row.path("candidateId").asText()),
                row.path("voteCount").asLong()));
        return counts;
    }

    private static void pause() {
        try {
            Thread.sleep(POLL_INTERVAL.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the tally", e);
        }
    }

    private static void publish(UUID electionId, ElectionReport report) {
        String text = "Integrity check, election " + electionId + System.lineSeparator() + report.describe();
        System.out.println(text);
        Path file = LoadTestConfig.INTEGRITY_DIR.resolve(electionId + ".txt");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, text + System.lineSeparator());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write " + file, e);
        }
    }
}
