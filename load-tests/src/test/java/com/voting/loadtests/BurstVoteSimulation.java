package com.voting.loadtests;

import com.voting.loadtests.ElectionReport.Mode;
import io.gatling.javaapi.core.Simulation;

import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;

/**
 * Burst: {@code USERS} seeded voters all vote at the same moment. Judged on integrity only, so there are no Gatling
 * assertions: votes may be rejected or time out (503), but none may be lost or doubled, the chain must stay valid and
 * the tally must match the stored votes, or the integrity check fails the run. Run it last: it can leave the
 * gateway's circuit breakers open for a while.
 */
public class BurstVoteSimulation extends Simulation {

    private static final VoteRun RUN = new VoteRun();

    {
        setUp(VoteScenario.castVote("burst", RUN::election, RUN.tally())
                .injectOpen(atOnceUsers(LoadTestConfig.USERS)))
                .protocols(VoteScenario.gatewayProtocol());
    }

    @Override
    public void before() {
        RUN.prepare(LoadTestConfig.USERS);
    }

    @Override
    public void after() {
        RUN.check(Mode.OVERLOAD);
    }
}
