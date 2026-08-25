package com.xn.report.execution;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * 报表生成各执行阶段耗时性能指标模型。
 * <p>
 * 精确统计各阶段（{@link ExecutionStage}）的独立毫秒耗时以及流水线整体执行耗时。
 * </p>
 */
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

    /**
     * 开始计时并创建可变度量收集器。
     *
     * @param startedAt 开始时间戳
     * @return Mutable 收集器实例
     */
    public static Mutable begin(Instant startedAt) {
        return new Mutable(startedAt);
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    /**
     * 获取指定阶段的耗时（毫秒）。
     *
     * @param stage 执行阶段
     * @return 耗时毫秒数
     */
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

    /**
     * 可变执行阶段计时收集器。
     */
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
