package com.voting.loadtests;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

/**
 * Step 1 of a load run: registers {@code USERS} voters (paced under the gateway's per-IP auth limit) and writes
 * their JWTs to {@code VOTERS_FILE}. The tokens stay valid for {@code JWT_EXPIRATION} (24 h by default), and the
 * file is git-ignored because it holds live tokens.
 */
public class SeedVotersSimulation extends Simulation {

    private static final String RUN_ID = Long.toString(System.currentTimeMillis(), Character.MAX_RADIX);
    private static final Queue<String> TOKENS = new ConcurrentLinkedQueue<>();

    private final HttpProtocolBuilder httpProtocol = http
            .baseUrl(LoadTestConfig.GATEWAY_URL + "/api/v1")
            .contentTypeHeader("application/json");

    private final ScenarioBuilder seed = scenario("seed voters")
            .exec(session -> session
                    .set("username", "load_" + RUN_ID + "_" + session.userId())
                    .set("password", "Pw-load-" + RUN_ID + "-" + session.userId()))
            .exec(http("register").post("/auth/register")
                    .body(StringBody("{\"username\":\"#{username}\",\"email\":\"#{username}@example.test\","
                            + "\"password\":\"#{password}\"}"))
                    .check(status().is(201)))
            .exec(http("login").post("/auth/login")
                    .body(StringBody("{\"usernameOrEmail\":\"#{username}\",\"password\":\"#{password}\"}"))
                    .check(status().is(200), jsonPath("$.data.token").saveAs("token")))
            .exec(session -> {
                TOKENS.add(session.getString("token"));
                return session;
            });

    {
        long seconds = (LoadTestConfig.USERS + LoadTestConfig.SEED_RATE - 1) / LoadTestConfig.SEED_RATE;
        setUp(seed.injectOpen(constantUsersPerSec(LoadTestConfig.SEED_RATE).during(Duration.ofSeconds(seconds))))
                .protocols(httpProtocol)
                .assertions(global().failedRequests().count().is(0L));
    }

    /** Writes the file only after a complete seed, so a failed run never replaces a good voter file. */
    @Override
    public void after() {
        if (TOKENS.size() < LoadTestConfig.USERS) {
            System.err.println("Seeded only " + TOKENS.size() + " of " + LoadTestConfig.USERS
                    + " voters; " + LoadTestConfig.VOTERS_FILE + " left unchanged");
            return;
        }
        List<String> lines = new ArrayList<>();
        lines.add("token");
        lines.addAll(TOKENS);
        try {
            Files.write(LoadTestConfig.VOTERS_FILE, lines);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write " + LoadTestConfig.VOTERS_FILE, e);
        }
        System.out.println("Wrote " + TOKENS.size() + " voter tokens to " + LoadTestConfig.VOTERS_FILE.toAbsolutePath());
    }
}
