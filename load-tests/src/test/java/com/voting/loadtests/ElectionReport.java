package com.voting.loadtests;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Integrity of one election after a load run: every accepted vote stored, the chain intact and the live tally equal to
 * the stored votes.
 */
record ElectionReport(Mode mode, long accepted, long stored, Map<UUID, Long> storedByCandidate, boolean chainValid,
                      long brokenLinks, long tallied, Map<UUID, Long> talliedByCandidate, Duration tallyWait) {

    /**
     * {@code PACED}: every vote should be accepted, so a stored vote without a 201 is a problem. {@code OVERLOAD}: the
     * run pushes past capacity (step test, burst), where the gateway may answer 503 after its 5 s limit while the vote
     * is still stored; those are reported, not failed.
     */
    enum Mode { PACED, OVERLOAD }

    ElectionReport {
        storedByCandidate = Map.copyOf(storedByCandidate);
        talliedByCandidate = Map.copyOf(talliedByCandidate);
    }

    long storedAfterTimeout() {
        return Math.max(0, stored - accepted);
    }

    List<String> problems() {
        List<String> problems = new ArrayList<>();
        if (stored < accepted) {
            problems.add((accepted - stored) + " accepted vote(s) not stored");
        }
        if (mode == Mode.PACED && stored > accepted) {
            problems.add(storedAfterTimeout() + " vote(s) stored without a 201");
        }
        if (!chainValid) {
            problems.add("chain invalid (" + brokenLinks + " broken links)");
        }
        if (tallied != stored) {
            problems.add("tally " + tallied + " of " + stored + " after " + tallyWait.toSeconds() + " s");
        } else {
            problems.addAll(candidateMismatches());
        }
        return problems;
    }

    String describe() {
        List<String> problems = problems();
        return String.join(System.lineSeparator(),
                "mode=" + mode + " accepted=" + accepted + " stored=" + stored
                        + " storedAfterTimeout=" + storedAfterTimeout(),
                "chainValid=" + chainValid + " brokenLinks=" + brokenLinks,
                "tally " + tallied + " of " + stored + " (waited " + tallyWait.toSeconds() + " s)",
                problems.isEmpty() ? "INTEGRITY OK" : failure(problems));
    }

    /**
     * Fails the run when integrity is broken. Thrown from a simulation's {@code after()}, it makes Gatling exit non-zero
     * but skips the HTML report; rebuild it from the run's {@code simulation.log} with {@code -Dgatling.reportsOnly}.
     */
    void requireIntact() {
        List<String> problems = problems();
        if (!problems.isEmpty()) {
            throw new IllegalStateException(failure(problems));
        }
    }

    private static String failure(List<String> problems) {
        return "INTEGRITY FAILED: " + String.join("; ", problems);
    }

    /** A candidate missing from one side has 0 votes there (vote counts list only candidates with votes). */
    private List<String> candidateMismatches() {
        Set<UUID> candidates = new HashSet<>(storedByCandidate.keySet());
        candidates.addAll(talliedByCandidate.keySet());
        List<String> mismatches = new ArrayList<>();
        for (UUID candidate : candidates) {
            long storedVotes = storedByCandidate.getOrDefault(candidate, 0L);
            long talliedVotes = talliedByCandidate.getOrDefault(candidate, 0L);
            if (storedVotes != talliedVotes) {
                mismatches.add("candidate " + candidate + ": stored " + storedVotes + ", tallied " + talliedVotes);
            }
        }
        return mismatches;
    }
}
