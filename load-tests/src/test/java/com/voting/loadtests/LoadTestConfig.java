package com.voting.loadtests;

import java.nio.file.Path;

/** Settings for every simulation, read from environment variables with laptop defaults. */
final class LoadTestConfig {

    static final String GATEWAY_URL = env("GATEWAY_URL", "http://localhost:8080");
    static final int USERS = Integer.parseInt(env("USERS", "1000"));
    static final int RAMP_SECONDS = Integer.parseInt(env("RAMP_SECONDS", "60"));
    static final int CANDIDATES = Integer.parseInt(env("CANDIDATES", "5"));
    /** Voters registered per second by the seeding run; each costs 2 auth requests (gateway limit 10/s per IP). */
    static final int SEED_RATE = Integer.parseInt(env("SEED_RATE", "4"));
    /** Seeded voter tokens; the pom points the default at load-tests/voters.csv. */
    static final Path VOTERS_FILE = Path.of(env("VOTERS_FILE", System.getProperty("voters.file", "voters.csv")));
    /** Where each run's integrity report is written; the pom points it at load-tests/target/integrity. */
    static final Path INTEGRITY_DIR = Path.of(System.getProperty("integrity.dir", "target/integrity"));
    static final int P95_LIMIT_MS = 500;
    /** Step test (CapacitySimulation): votes/s of the first level, added per level, number of levels, their lengths. */
    static final int START_RATE = Integer.parseInt(env("START_RATE", "20"));
    static final int STEP_RATE = Integer.parseInt(env("STEP_RATE", "20"));
    static final int STEPS = Integer.parseInt(env("STEPS", "6"));
    static final int LEVEL_SECONDS = Integer.parseInt(env("LEVEL_SECONDS", "15"));
    static final int RAMP_BETWEEN_SECONDS = Integer.parseInt(env("RAMP_BETWEEN_SECONDS", "5"));
    /** How long the post-run check waits for result-service's tally to catch up with the stored votes. */
    static final int TALLY_WAIT_SECONDS = Integer.parseInt(env("TALLY_WAIT_SECONDS", "120"));
    /** Voter tokens live 24 h (JWT_EXPIRATION); refuse files older than this so a run never starts with dead tokens. */
    static final int TOKEN_MAX_AGE_HOURS = Integer.parseInt(env("TOKEN_MAX_AGE_HOURS", "23"));

    private LoadTestConfig() {
    }

    static String adminUsername() {
        return required("ADMIN_USERNAME");
    }

    static String adminPassword() {
        return required("ADMIN_PASSWORD");
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set (same value as in .env)");
        }
        return value;
    }
}
