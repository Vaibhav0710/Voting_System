package com.voting.loadtests;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.csv;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

/**
 * The vote every voting simulation sends: each seeded voter casts one vote for a random candidate of the run's
 * election. The HTTP status of every answer (or {@link StatusTally#NO_RESPONSE}) goes into a {@link StatusTally}.
 */
final class VoteScenario {

    private VoteScenario() {
    }

    static HttpProtocolBuilder gatewayProtocol() {
        return http.baseUrl(LoadTestConfig.GATEWAY_URL + "/api/v1").contentTypeHeader("application/json");
    }

    static ScenarioBuilder castVote(String name, Supplier<ApiSetup.Election> election, StatusTally tally) {
        return scenario(name)
                .feed(csv(votersFile()).queue())
                .exec(session -> session
                        .set("electionId", election.get().id().toString())
                        .set("candidateId", randomCandidate(election.get()).toString())
                        .set("idempotencyKey", UUID.randomUUID().toString()))
                .exec(http("POST /votes").post("/votes")
                        .header("Authorization", "Bearer #{token}")
                        .body(StringBody("{\"candidateId\":\"#{candidateId}\",\"electionId\":\"#{electionId}\","
                                + "\"idempotencyKey\":\"#{idempotencyKey}\"}"))
                        // Save the status first: a failing 201 check stops the checks after it, not the ones before
                        .check(status().saveAs("status"), status().is(201)))
                .exec(session -> {
                    tally.record(session.getIntegerWrapper("status"));
                    return session;
                });
    }

    /** Fails with a clear message instead of Gatling's "Resource not found" when seeding was skipped. */
    private static String votersFile() {
        if (!Files.isRegularFile(LoadTestConfig.VOTERS_FILE)) {
            throw new IllegalStateException(LoadTestConfig.VOTERS_FILE.toAbsolutePath()
                    + " not found: run SeedVotersSimulation first");
        }
        return LoadTestConfig.VOTERS_FILE.toAbsolutePath().toString();
    }

    private static UUID randomCandidate(ApiSetup.Election election) {
        return election.candidateIds().get(ThreadLocalRandom.current().nextInt(election.candidateIds().size()));
    }
}
