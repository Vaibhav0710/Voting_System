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
    static final int P95_LIMIT_MS = 500;

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
