package tpool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Watchdog keeps finding out the worker that is
 * processing the task more than `taskTimeoutNanos`.
 */
final class Watchdog extends AbstractWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(Watchdog.class);

    private final TPool tPool;
    private final long taskTimeoutNanos;
    private final long watchdogIntervalMillis;
    private final int watchdogIntervalRemainingNanos;

    Watchdog(TPool tPool, long taskTimeoutNanos, long watchdogIntervalNanos) {
        super(WorkerType.WATCHDOG, "Watchdog");
        this.tPool = tPool;
        this.taskTimeoutNanos = taskTimeoutNanos;

        final long nanosPerMilli = TimeUnit.MILLISECONDS.toNanos(1); // 1,000,000
        this.watchdogIntervalMillis = watchdogIntervalNanos / nanosPerMilli;
        this.watchdogIntervalRemainingNanos = (int) (watchdogIntervalNanos % nanosPerMilli);
    }

    @Override
    void go() {
        LOGGER.debug("Started a watchdog {}", workerName());
        try {
            while (!tPool.isShutdown()) {
                try {
                    sleep(watchdogIntervalMillis, watchdogIntervalRemainingNanos);
                } catch (InterruptedException ignore) {
                    continue;
                }

                tPool.forEachWorker(w -> {
                    final long taskStartTimeNanos = w.taskStartTimeNanos();
                    if (taskStartTimeNanos == TPool.TASK_NOT_STARTED_MARKER) {
                        return;
                    }

                    if (System.nanoTime() - taskStartTimeNanos > taskTimeoutNanos) {
                        w.interrupt(TerminationReason.WATCHDOG);
                    }
                });
            }
        } catch (Throwable cause) {
            LOGGER.warn("Unexpected exception from a watchdog: {}", terminationReason());
        } finally {
            LOGGER.debug("{} has been terminated", workerName());
        }
    }
}
