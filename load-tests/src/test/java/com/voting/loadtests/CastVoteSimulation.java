package com.voting.loadtests;

import com.voting.loadtests.ElectionReport.Mode;
import io.gatling.javaapi.core.Simulation;

import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.rampUsers;

/**
 * Step 2 of a load run (the paced run): {@code USERS} seeded voters each cast one vote, spread evenly over
 * {@code RAMP_SECONDS} (a steady {@code USERS / RAMP_SECONDS} votes per second).
 * Every run votes in a new election, so the same seeded voters can be reused.
 * Passes only if every vote returns 201 and p95 latency stays under 500 ms; the integrity check runs afterwards.
 */
public class CastVoteSimulation extends Simulation {

    private static final VoteRun RUN = new VoteRun();

    {
        setUp(VoteScenario.castVote("cast vote", RUN::election, RUN.tally())
                .injectOpen(rampUsers(LoadTestConfig.USERS).during(LoadTestConfig.RAMP_SECONDS)))
                .protocols(VoteScenario.gatewayProtocol())
                .assertions(
                        global().responseTime().percentile(95.0).lt(LoadTestConfig.P95_LIMIT_MS),
                        global().failedRequests().count().is(0L));
    }

    @Override
    public void before() {
        RUN.prepare(LoadTestConfig.USERS);
    }

    @Override
    public void after() {
        RUN.check(Mode.PACED);
    }
}
