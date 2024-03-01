package tpool;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

public class TPoolBuilder {

    private final int maxNumWorkers;
    private int minNumWorkers;
    private long idleTimeoutNanos;

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

    public TPool build() {
        return new TPool(minNumWorkers, maxNumWorkers, idleTimeoutNanos);
    }
}
