package com.voting.loadtests;

import java.time.Clock;
import java.time.Duration;

/** What one voting simulation shares between {@code before()}, its virtual users and {@code after()}. */
final class VoteRun {

    private final StatusTally tally = new StatusTally();
    private volatile ApiSetup.Election election;
    private volatile String adminToken;

    StatusTally tally() {
        return tally;
    }

    ApiSetup.Election election() {
        return election;
    }

    /** Refuses a stale or too-short voters file, then creates this run's election as admin. */
    void prepare(long neededVoters) {
        LoadPlan.requireFreshVoters(LoadTestConfig.VOTERS_FILE, Duration.ofHours(LoadTestConfig.TOKEN_MAX_AGE_HOURS),
                Clock.systemUTC());
        LoadPlan.requireEnoughVoters(LoadTestConfig.VOTERS_FILE, neededVoters);
        adminToken = ApiSetup.login(LoadTestConfig.adminUsername(), LoadTestConfig.adminPassword());
        election = ApiSetup.createElection(adminToken, LoadTestConfig.CANDIDATES);
        System.out.println("Voting in election " + election.id() + " with " + LoadTestConfig.CANDIDATES + " candidates");
    }

    /** Prints the status breakdown and the integrity report of this run's election, and fails the run if it is broken. */
    void check(ElectionReport.Mode mode) {
        System.out.println("Vote statuses: " + tally.describe());
        ElectionCheck.run(mode, adminToken, election, tally.count(201)).requireIntact();
    }
}
