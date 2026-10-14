package com.voting.loadtests;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.bodyString;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

/**
 * Race-condition check through the whole stack: one voter sends {@code ATTEMPTS} votes at the same instant,
 * each with its own idempotency key. Exactly one may return 201; the rest must be 409 (already voted) or 429
 * (the gateway's per-user rate limit, burst 10). Afterwards the admin view must show exactly one stored vote.
 * Any 5xx or other status fails the run.
 */
public class DoubleVoteSimulation extends Simulation {

    private static final int ATTEMPTS = 100;
    private static final int CREATED = 201;
    private static final int CONFLICT = 409;
    private static final int TOO_MANY_REQUESTS = 429;
    private static final AtomicInteger CREATED_COUNT = new AtomicInteger();

    private static ApiSetup.Election election;
    private static String voterToken;
    private static String adminToken;

    private final HttpProtocolBuilder httpProtocol = http
            .baseUrl(LoadTestConfig.GATEWAY_URL + "/api/v1")
            .contentTypeHeader("application/json");

    private final ScenarioBuilder burst = scenario("same voter, " + ATTEMPTS + " votes at once")
            .exec(session -> session
                    .set("token", voterToken)
                    .set("electionId", election.id().toString())
                    .set("candidateId", election.candidateIds().get(0).toString())
                    .set("idempotencyKey", UUID.randomUUID().toString()))
            .exec(http("POST /votes (same voter)").post("/votes")
                    .header("Authorization", "Bearer #{token}")
                    .body(StringBody("{\"candidateId\":\"#{candidateId}\",\"electionId\":\"#{electionId}\","
                            + "\"idempotencyKey\":\"#{idempotencyKey}\"}"))
                    .check(status().in(CREATED, CONFLICT, TOO_MANY_REQUESTS).saveAs("status")))
            .exec(session -> {
                if (session.getInt("status") == CREATED) {
                    CREATED_COUNT.incrementAndGet();
                }
                return session;
            });

    // Runs after the burst: the stored count comes from the database, the 201 count from the responses.
    private final ScenarioBuilder verify = scenario("verify one stored vote")
            .exec(session -> session
                    .set("adminToken", adminToken)
                    .set("electionId", election.id().toString()))
            .exec(http("GET /votes/election (admin)").get("/votes/election/#{electionId}")
                    .header("Authorization", "Bearer #{adminToken}")
                    .check(status().is(200),
                            jsonPath("$.data.totalElements").ofLong().is(1L),
                            bodyString().transform(ignored -> CREATED_COUNT.get()).is(1)));

    {
        setUp(burst.injectOpen(atOnceUsers(ATTEMPTS)).andThen(verify.injectOpen(atOnceUsers(1))))
                .protocols(httpProtocol)
                .assertions(global().failedRequests().count().is(0L));
    }

    @Override
    public void before() {
        adminToken = ApiSetup.login(LoadTestConfig.adminUsername(), LoadTestConfig.adminPassword());
        election = ApiSetup.createElection(adminToken, 1);
        String username = "double_" + Long.toString(System.currentTimeMillis(), Character.MAX_RADIX);
        String password = "Pw-" + UUID.randomUUID();
        ApiSetup.registerVoter(username, password);
        voterToken = ApiSetup.login(username, password);
    }
}
