package tpool;

import static com.google.common.base.Preconditions.checkArgument;

public class TPoolBuilder {

    private final int maxNumWorkers;

    private int minNumWorkers;

    TPoolBuilder(int maxNumWorkers) {
        checkArgument(maxNumWorkers > 0, "maxWorkers: %s (expected: > 0)", maxNumWorkers);
        this.maxNumWorkers = maxNumWorkers;
    }

    public TPoolBuilder minNumWorkers(int minNumWorkers) {
        checkArgument(minNumWorkers >= 0 && minNumWorkers <= maxNumWorkers,
                "minNumWorkers: %s (expected: [0, maxNumWorkers (%s)]",
                minNumWorkers, maxNumWorkers);
        this.minNumWorkers = minNumWorkers;
        return this;
    }

    public TPool build() {
        return new TPool(minNumWorkers, maxNumWorkers);
    }
}
