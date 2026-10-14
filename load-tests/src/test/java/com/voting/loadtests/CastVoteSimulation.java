package com.voting.loadtests;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.csv;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.rampUsers;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

/**
 * Step 2 of a load run: {@code USERS} seeded voters each cast one vote, ramped over {@code RAMP_SECONDS}.
 * Every run votes in a new election, so the same seeded voters can be reused.
 * Passes only if every vote returns 201 and p95 latency stays under 500 ms.
 */
public class CastVoteSimulation extends Simulation {

    private static ApiSetup.Election election;

    private final HttpProtocolBuilder httpProtocol = http
            .baseUrl(LoadTestConfig.GATEWAY_URL + "/api/v1")
            .contentTypeHeader("application/json");

    private final ScenarioBuilder castVote = scenario("cast vote")
            .feed(csv(votersFile()).queue())
            .exec(session -> session
                    .set("electionId", election.id().toString())
                    .set("candidateId", randomCandidate().toString())
                    .set("idempotencyKey", UUID.randomUUID().toString()))
            .exec(http("POST /votes").post("/votes")
                    .header("Authorization", "Bearer #{token}")
                    .body(StringBody("{\"candidateId\":\"#{candidateId}\",\"electionId\":\"#{electionId}\","
                            + "\"idempotencyKey\":\"#{idempotencyKey}\"}"))
                    .check(status().is(201)));

    {
        setUp(castVote.injectOpen(rampUsers(LoadTestConfig.USERS).during(LoadTestConfig.RAMP_SECONDS)))
                .protocols(httpProtocol)
                .assertions(
                        global().responseTime().percentile(95.0).lt(LoadTestConfig.P95_LIMIT_MS),
                        global().failedRequests().count().is(0L));
    }

    @Override
    public void before() {
        String adminToken = ApiSetup.login(LoadTestConfig.adminUsername(), LoadTestConfig.adminPassword());
        election = ApiSetup.createElection(adminToken, LoadTestConfig.CANDIDATES);
        System.out.println("Voting in election " + election.id() + " with " + LoadTestConfig.CANDIDATES + " candidates");
    }

    /** Fails with a clear message instead of Gatling's "Resource not found" when seeding was skipped. */
    private static String votersFile() {
        if (!Files.isRegularFile(LoadTestConfig.VOTERS_FILE)) {
            throw new IllegalStateException(LoadTestConfig.VOTERS_FILE.toAbsolutePath()
                    + " not found: run SeedVotersSimulation first");
        }
        return LoadTestConfig.VOTERS_FILE.toAbsolutePath().toString();
    }

    private static UUID randomCandidate() {
        return election.candidateIds().get(ThreadLocalRandom.current().nextInt(election.candidateIds().size()));
    }
}
