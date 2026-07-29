package com.xn.report.execution;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class ExecutionMetrics {

    private final Instant startedAt;
    private final Instant finishedAt;
    private final Map<ExecutionStage, Long> durationsMillis;

    private ExecutionMetrics(
            Instant startedAt,
            Instant finishedAt,
            Map<ExecutionStage, Long> durationsMillis) {
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.durationsMillis = Collections.unmodifiableMap(
                new EnumMap<ExecutionStage, Long>(durationsMillis));
    }

    public static Mutable begin(Instant startedAt) {
        return new Mutable(startedAt);
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public long getDurationMillis(ExecutionStage stage) {
        Long value = durationsMillis.get(stage);
        return value == null ? 0L : value.longValue();
    }

    public Map<ExecutionStage, Long> getStageDurationsMillis() {
        return durationsMillis;
    }

    public long getTotalDurationMillis() {
        return Math.max(0L, Duration.between(startedAt, finishedAt).toMillis());
    }

    public static final class Mutable {
        private final Instant startedAt;
        private final EnumMap<ExecutionStage, Long> durations =
                new EnumMap<ExecutionStage, Long>(ExecutionStage.class);

        private Mutable(Instant startedAt) {
            this.startedAt = startedAt;
        }

        public long start() {
            return System.nanoTime();
        }

        public void finish(ExecutionStage stage, long startedNanos) {
            durations.put(
                    stage,
                    Math.max(0L,
                            (System.nanoTime() - startedNanos) / 1_000_000L));
        }

        public ExecutionMetrics snapshot(Instant finishedAt) {
            return new ExecutionMetrics(startedAt, finishedAt, durations);
        }
    }
}
