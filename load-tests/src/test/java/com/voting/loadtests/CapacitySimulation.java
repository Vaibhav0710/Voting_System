package com.voting.loadtests;

import com.voting.loadtests.ElectionReport.Mode;
import io.gatling.javaapi.core.Simulation;

import java.util.StringJoiner;

import static com.voting.loadtests.LoadTestConfig.LEVEL_SECONDS;
import static com.voting.loadtests.LoadTestConfig.RAMP_BETWEEN_SECONDS;
import static com.voting.loadtests.LoadTestConfig.START_RATE;
import static com.voting.loadtests.LoadTestConfig.STEPS;
import static com.voting.loadtests.LoadTestConfig.STEP_RATE;
import static io.gatling.javaapi.core.CoreDsl.incrementUsersPerSec;

/**
 * Step test that finds the knee: the vote rate starts at {@code START_RATE} votes/s and rises by {@code STEP_RATE}
 * for {@code STEPS} levels of {@code LEVEL_SECONDS}, with {@code RAMP_BETWEEN_SECONDS} linear ramps between levels.
 * No Gatling assertions: the knee is the highest level with 0 failed votes and p95 < 500 ms in the report's charts.
 * The integrity check runs afterwards and fails the run if votes were lost or doubled, the chain broke or the tally is off.
 */
public class CapacitySimulation extends Simulation {

    /** Gatling's ramps can inject slightly more votes than the arithmetic, so keep 1 % spare voters. */
    private static final double VOTER_HEADROOM = 1.01;
    private static final long PLANNED_VOTES =
            LoadPlan.capacityArrivals(START_RATE, STEP_RATE, STEPS, LEVEL_SECONDS, RAMP_BETWEEN_SECONDS);
    private static final VoteRun RUN = new VoteRun();

    {
        setUp(VoteScenario.castVote("capacity", RUN::election, RUN.tally())
                .injectOpen(incrementUsersPerSec(STEP_RATE)
                        .times(STEPS)
                        .eachLevelLasting(LEVEL_SECONDS)
                        .separatedByRampsLasting(RAMP_BETWEEN_SECONDS)
                        .startingFrom(START_RATE)))
                .protocols(VoteScenario.gatewayProtocol());
    }

    @Override
    public void before() {
        RUN.prepare((long) Math.ceil(PLANNED_VOTES * VOTER_HEADROOM));
        System.out.println("Levels (votes/s, " + LEVEL_SECONDS + " s each): " + levels()
                + "; planned votes: " + PLANNED_VOTES);
    }

    /** Levels above the knee overload the stack on purpose, so votes stored after a gateway timeout are expected. */
    @Override
    public void after() {
        RUN.check(Mode.OVERLOAD);
    }

    private static String levels() {
        StringJoiner levels = new StringJoiner(", ");
        for (int level = 0; level < STEPS; level++) {
            levels.add(Integer.toString(START_RATE + level * STEP_RATE));
        }
        return levels.toString();
    }
}
