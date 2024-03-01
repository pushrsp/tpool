package tpool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicBoolean;

abstract class AbstractWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractWorker.class);

    private final AtomicBoolean started = new AtomicBoolean(false);

    final Thread thread;
    final String threadName;
    final WorkerType workerType;

    @Nullable
    private volatile InterruptReason interruptReason;

    AbstractWorker(WorkerType workerType) {
        this.workerType = workerType;
        this.thread = new Thread(this::go);
        this.thread.setName(String.format("Worker %s", this.thread.getId()));
        this.threadName = thread.getName();
    }

    String workerName() {
        return threadName;
    }

    void start() {
        if (started.compareAndSet(false, true)) {
            thread.start();
        }
    }

    WorkerType workerType() {
        return workerType;
    }

    boolean isAlive() {
        return thread.isAlive();
    }

    void interrupt(InterruptReason reason) {
        interruptReason = reason;
        thread.interrupt();
    }

    @Nullable
    InterruptReason interruptReason() {
        return interruptReason;
    }

    void join() {
        try {
            thread.join();
        } catch (InterruptedException e) {
            // Do not propagate to prevent incomplete shutdown.
        }
    }

    abstract void go();
}
