package com.voting.loadtests;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

/** Thread-safe count of HTTP statuses seen by a simulation; a request without any response counts as {@link #NO_RESPONSE}. */
final class StatusTally {

    static final int NO_RESPONSE = 0;

    private final Map<Integer, LongAdder> counts = new ConcurrentHashMap<>();

    void record(Integer status) {
        counts.computeIfAbsent(status == null ? NO_RESPONSE : status, key -> new LongAdder()).increment();
    }

    long count(int status) {
        LongAdder adder = counts.get(status);
        return adder == null ? 0 : adder.sum();
    }

    long total() {
        return counts.values().stream().mapToLong(LongAdder::sum).sum();
    }

    /** Ascending by status, e.g. {@code "201=9998, 503=2"}. */
    String describe() {
        return new TreeMap<>(counts).entrySet().stream()
                .map(entry -> label(entry.getKey()) + "=" + entry.getValue().sum())
                .collect(Collectors.joining(", "));
    }

    private static String label(int status) {
        return status == NO_RESPONSE ? "no-response" : Integer.toString(status);
    }
}
