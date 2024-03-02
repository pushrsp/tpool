package tpool;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

public class TPoolBuilder {

    private static final long DEFAULT_WATCHDOG_INTERVAL_SECONDS = 1;

    private final int maxNumWorkers;
    private int minNumWorkers;
    private long idleTimeoutNanos;
    private long taskTimeoutNanos;
    private long watchdogIntervalNanos = TimeUnit.SECONDS.toNanos(DEFAULT_WATCHDOG_INTERVAL_SECONDS);

    TPoolBuilder(int maxNumWorkers) {
        checkArgument(maxNumWorkers > 0, "maxWorkers: %s (expected: > 0)", maxNumWorkers);
        this.maxNumWorkers = maxNumWorkers;
    }

    public TPoolBuilder minNumWorkers(int minNumWorkers) {
        checkArgument(0 <= minNumWorkers && minNumWorkers <= maxNumWorkers,
                "minNumWorkers: %s (expected: [0, maxNumWorkers (%s)]",
                minNumWorkers, maxNumWorkers);
        this.minNumWorkers = minNumWorkers;
        return this;
    }

    public TPoolBuilder idleTimeout(long idleTimeout, TimeUnit unit) {
        checkArgument(idleTimeout >= 0, "idleTimeout: %s (expected: >= 0)");
        this.idleTimeoutNanos = requireNonNull(unit, "unit").toNanos(idleTimeout);
        return this;
    }

    public TPoolBuilder idleTimeout(Duration idleTimeout) {
        requireNonNull(idleTimeout, "idleTimeout");
        checkArgument(!idleTimeout.isNegative(), "idleTimeout: %s (expected: >= 0)");
        return idleTimeout(idleTimeout.toNanos(), TimeUnit.NANOSECONDS);
    }

    public TPoolBuilder taskTimeout(long taskTimeout, TimeUnit unit) {
        checkArgument(taskTimeout >= 0, "taskTimeout: %s (expected: >= 0)");
        this.taskTimeoutNanos = requireNonNull(unit, "unit").toNanos(taskTimeout);
        return this;
    }

    public TPoolBuilder taskTimeout(Duration taskTimeout) {
        requireNonNull(taskTimeout, "taskTimeout");
        checkArgument(!taskTimeout.isNegative(), "taskTimeout: %s (expected: >= 0)");
        return taskTimeout(taskTimeout.toNanos(), TimeUnit.NANOSECONDS);
    }

    public TPoolBuilder watchdogInterval(long watchdogInterval, TimeUnit unit) {
        checkArgument(watchdogInterval > 0, "watchdogInterval: %s (expected: > 0)");
        watchdogIntervalNanos = requireNonNull(unit, "unit").toNanos(watchdogInterval);
        return this;
    }

    public TPoolBuilder watchdogInterval(Duration watchdogInterval) {
        requireNonNull(watchdogInterval, "watchdogInterval");
        checkArgument(!watchdogInterval.isZero() &&
                        !watchdogInterval.isNegative(),
                "watchdogInterval: %s (expected: > 0)");
        return watchdogInterval(watchdogInterval.toNanos(), TimeUnit.NANOSECONDS);
    }

    public TPool build() {
        return new TPool(minNumWorkers, maxNumWorkers,
                idleTimeoutNanos, taskTimeoutNanos, watchdogIntervalNanos);
    }
}
