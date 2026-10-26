package com.voting.loadtests;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ElectionCheckTest {

    @Test
    void retriesWhileGatewayAnswers503() {
        AtomicInteger calls = new AtomicInteger();

        String result = ElectionCheck.untilAvailable(() -> {
            if (calls.incrementAndGet() == 1) {
                throw new ApiSetup.UnexpectedStatusException("GET /votes", 503);
            }
            return "counts";
        }, Instant.now().plusSeconds(30));

        assertThat(result).isEqualTo("counts");
        assertThat(calls).hasValue(2);
    }

    @Test
    void doesNotRetryOtherStatuses() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> ElectionCheck.untilAvailable(() -> {
            calls.incrementAndGet();
            throw new ApiSetup.UnexpectedStatusException("GET /votes", 401);
        }, Instant.now().plusSeconds(30)))
                .isInstanceOf(ApiSetup.UnexpectedStatusException.class);
        assertThat(calls).hasValue(1);
    }

    @Test
    void givesUpOn503AfterDeadline() {
        assertThatThrownBy(() -> ElectionCheck.untilAvailable(() -> {
            throw new ApiSetup.UnexpectedStatusException("GET /votes", 503);
        }, Instant.now().minusSeconds(1)))
                .isInstanceOfSatisfying(ApiSetup.UnexpectedStatusException.class,
                        e -> assertThat(e.status()).isEqualTo(503));
    }
}
