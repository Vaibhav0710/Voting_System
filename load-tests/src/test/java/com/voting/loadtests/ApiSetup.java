package com.voting.loadtests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Plain HTTP calls through the gateway that prepare a run before Gatling starts measuring:
 * admin login, a fresh election with candidates (so voters can be reused across runs) and test voters; also the
 * reads used by the post-run integrity check.
 */
final class ApiSetup {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final String API = LoadTestConfig.GATEWAY_URL + "/api/v1";

    record Election(UUID id, List<UUID> candidateIds) {
    }

    /** The gateway answered with a status other than the expected one. */
    static final class UnexpectedStatusException extends IllegalStateException {

        private final int status;

        UnexpectedStatusException(String message, int status) {
            super(message);
            this.status = status;
        }

        int status() {
            return status;
        }
    }

    private ApiSetup() {
    }

    static String login(String username, String password) {
        JsonNode body = post("/auth/login", Map.of("usernameOrEmail", username, "password", password), null, 200);
        return body.path("data").path("token").asText();
    }

    static void registerVoter(String username, String password) {
        post("/auth/register", Map.of("username", username, "email", username + "@example.test", "password", password),
                null, 201);
    }

    /** Creates {@code candidates} ACTIVE candidates in a new random election, in one bulk call. */
    static Election createElection(String adminToken, int candidates) {
        UUID electionId = UUID.randomUUID();
        List<Map<String, Object>> requests = new ArrayList<>();
        for (int i = 1; i <= candidates; i++) {
            requests.add(Map.of("name", "Load Candidate " + i, "party", "Load Party", "electionId", electionId));
        }
        JsonNode body = post("/candidates/bulk", Map.of("candidates", requests), adminToken, 201);
        List<UUID> ids = new ArrayList<>();
        body.path("data").forEach(candidate -> ids.add(UUID.fromString(candidate.path("id").asText())));
        return new Election(electionId, List.copyOf(ids));
    }

    static JsonNode post(String path, Object payload, String token, int expectedStatus) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(API + path))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(payload)));
            return send(request, "POST " + path, token, expectedStatus);
        } catch (IOException e) {
            throw new IllegalStateException("POST " + path + " failed: " + e.getMessage(), e);
        }
    }

    static JsonNode get(String path, String token, int expectedStatus) {
        return send(HttpRequest.newBuilder(URI.create(API + path)).GET(), "GET " + path, token, expectedStatus);
    }

    private static JsonNode send(HttpRequest.Builder request, String description, String token, int expectedStatus) {
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        try {
            HttpResponse<String> response = CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode body = JSON.readTree(response.body());
            if (response.statusCode() != expectedStatus) {
                throw new UnexpectedStatusException(description + ": expected " + expectedStatus + ", got "
                        + response.statusCode() + " (" + body.path("message").asText() + ")", response.statusCode());
            }
            return body;
        } catch (IOException e) {
            throw new IllegalStateException(description + " failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during " + description, e);
        }
    }
}
