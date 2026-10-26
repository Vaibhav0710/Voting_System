package com.voting.loadtests;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StatusTallyTest {

    @Test
    void countsEachStatus() {
        StatusTally tally = new StatusTally();

        tally.record(201);
        tally.record(503);
        tally.record(201);

        assertThat(tally.count(201)).isEqualTo(2);
        assertThat(tally.count(503)).isEqualTo(1);
        assertThat(tally.total()).isEqualTo(3);
        assertThat(tally.describe()).isEqualTo("201=2, 503=1");
    }

    @Test
    void countsMissingStatusAsNoResponse() {
        StatusTally tally = new StatusTally();

        tally.record(null);

        assertThat(tally.count(StatusTally.NO_RESPONSE)).isEqualTo(1);
        assertThat(tally.describe()).isEqualTo("no-response=1");
    }
}
