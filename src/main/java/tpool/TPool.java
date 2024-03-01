package tpool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public final class TPool implements Executor {

    private static final Logger LOGGER = LoggerFactory.getLogger(TPool.class);
    private static final Worker[] EMPTY_WORKERS_ARRAY = new Worker[0];
    private static final Runnable SHUTDOWN_TASK = () -> {
    };

    private final AtomicInteger numWorkers = new AtomicInteger(0);
    private final AtomicInteger numExtraWorkers = new AtomicInteger(0);
    private final AtomicInteger numCoreWorkers = new AtomicInteger(0);
    private final AtomicReference<ShutdownState> shutdownState = new AtomicReference<>(ShutdownState.NOT_SHUTDOWN);
    private final Set<Worker> workers = new HashSet<>();
    private final BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
    private final Lock workersLock = new ReentrantLock();

    private final int minNumWorkers;
    private final int maxNumWorkers;

    // TODO: Add idle timeout to remove workers.
    //    -> `WorkerType.EXTRA` worker should be removed after idle timeout.
    // TODO: Terminate long term task.
    TPool(int minNumWorkers, int maxNumWorkers) {
        this.minNumWorkers = minNumWorkers;
        this.maxNumWorkers = maxNumWorkers;
    }

    public static TPoolBuilder builder(int maxNumWorkers) {
        return new TPoolBuilder(maxNumWorkers);
    }

    /**
     * Returns total amount of workers.
     */
    public int workersSize() {
        return numWorkers.get();
    }

    /**
     * Returns total amount of extra workers.
     */
    public int extraWorkersSize() {
        return numExtraWorkers.get();
    }

    /**
     * Returns total amount of core workers.
     */
    public int coreWorkersSize() {
        return numCoreWorkers.get();
    }

    /**
     * Returns total amount of tasks in queue.
     */
    public int tasksSize() {
        return queue.size();
    }

    @Override
    public void execute(@Nonnull Runnable task) {
        if (isShutdown()) {
            return;
        }

        addWorkersIfNecessary();
        queue.add(task);

        // Even though we already checked above whether `shutdown` is ture or not,
        // we have to double-check for the worker that already passed before
        // `shutdown` is changed to true.
        if (isShutdown()) {
            //noinspection ResultOfMethodCallIgnored
            queue.remove(task);
        }
    }

    private void addWorkersIfNecessary() {
        if (hasEnoughWorkers() != null) {
            List<Worker> newWorkers = new ArrayList<>();
            workersLock.lock();
            try {
                WorkerType workerType;
                while (!isShutdown() && (workerType = hasEnoughWorkers()) != null) {
                    newWorkers.add(newWorker(workerType));
                }
            } finally {
                workersLock.unlock();
            }

            newWorkers.forEach(Worker::start);
        }
    }

    private Worker newWorker(@Nonnull WorkerType workerType) {
        numWorkers.incrementAndGet();
        if (workerType == WorkerType.EXTRA) {
            numExtraWorkers.incrementAndGet();
        } else {
            numCoreWorkers.incrementAndGet();
        }

        final Worker worker = new Worker(workerType, cleanupConsumer());
        workers.add(worker);
        return worker;
    }

    private Consumer<Worker> cleanupConsumer() {
        // If we reached here, it means one of followings below:
        // - This worker has been terminated by `SHUTDOWN_TASK`.
        // - This worker has been terminated by `idleTimeoutNanos`
        // - This worker is EXTRA worker so that it's terminated by `numWorkers` > `minNumWorkers`
        return (w) -> {
            workersLock.lock();
            try {
                workers.remove(w);

                // We must check whether there is still a task in queue.
                // Because below situation can be happened.
                // e.g. SHUTDOWN_TASK, SHUTDOWN_TASK, SHUTDOWN_TASK, task, SHUTDOWN_TASK.
                if (workers.isEmpty() && !queue.isEmpty()) {
                    for (Runnable task : queue) {
                        if (task != SHUTDOWN_TASK) {
                            addWorkersIfNecessary();
                            break;
                        }
                    }
                }
            } finally {
                numWorkers.decrementAndGet();
                if (w.workerType() == WorkerType.EXTRA) {
                    numExtraWorkers.decrementAndGet();
                } else {
                    numCoreWorkers.decrementAndGet();
                }

                workersLock.unlock();
                // TODO: Log the specific cause of termination.
            }
        };
    }

    /**
     * Returns {@link WorkerType} of worker if more worker is needed.
     * {@code null} is returned if no worker is needed.
     */
    @Nullable
    private WorkerType hasEnoughWorkers() {
        if (coreWorkersSize() < minNumWorkers) {
            return WorkerType.CORE;
        }

        // FIXME: We can check whether EXTRA worker should be added or not by `numExtraWorkers`.
        if (coreWorkersSize() >= minNumWorkers) {
            if (workersSize() < maxNumWorkers) {
                return WorkerType.EXTRA;
            }
        }

        return null;
    }

    private boolean isShutdown() {
        return shutdownState.get() != ShutdownState.NOT_SHUTDOWN;
    }

    public List<Runnable> interruptedShutdown() {
        doShutdown(true);

        Set<Runnable> tasks = new HashSet<>();
        while (true) {
            Runnable task = queue.poll();
            if (task == null) {
                break;
            }

            if (task != SHUTDOWN_TASK) {
                tasks.add(task);
            }
        }

        return new ArrayList<>(tasks);
    }

    public void shutdown() {
        doShutdown(false);
    }

    public void doShutdown(boolean interrupted) {
        boolean needShutdownTasks = false;
        if (interrupted) {
            if (shutdownState.compareAndSet(ShutdownState.NOT_SHUTDOWN, ShutdownState.SHUTDOWN_BY_INTERRUPT)) {
                needShutdownTasks = true;
            } else {
                shutdownState.compareAndSet(ShutdownState.SHUTDOWN, ShutdownState.SHUTDOWN_BY_INTERRUPT);
                LOGGER.debug("`interruptedShutdown()` is called after `shutdown()`");
            }
        } else {
            if (shutdownState.compareAndSet(ShutdownState.NOT_SHUTDOWN, ShutdownState.SHUTDOWN)) {
                needShutdownTasks = true;
            }
        }

        if (needShutdownTasks) {
            for (int i = 0; i < maxNumWorkers; i++) {
                queue.add(SHUTDOWN_TASK);
            }
        }

        // Blocking for all workers are finished.
        Worker[] workers;
        while (true) {
            workersLock.lock();
            try {
                workers = this.workers.toArray(EMPTY_WORKERS_ARRAY);
            } finally {
                workersLock.unlock();
            }

            if (workers.length == 0) {
                break;
            }

            if (interrupted) {
                for (Worker w : workers) {
                    w.interrupt(InterruptReason.SHUTDOWN);
                }
            }

            for (Worker w : workers) {
                w.join();
            }
        }
    }

    enum ShutdownState {
        NOT_SHUTDOWN,
        SHUTDOWN,
        SHUTDOWN_BY_INTERRUPT
    }

    final class Worker extends AbstractWorker {

        private final Consumer<Worker> cleanupHandler;

        public Worker(WorkerType workerType, Consumer<Worker> cleanupHandler) {
            super(workerType);
            this.cleanupHandler = cleanupHandler;
        }

        @Override
        void go() {
            LOGGER.debug("Started a new worker: {}, type: {}", threadName, workerType);
            try {
                while (true) {
                    Runnable task;
                    try {
                        task = queue.take();
                        if (task == SHUTDOWN_TASK) {
                            LOGGER.warn("{} received `SHUTDOWN_TASK`", workerName());
                            break;
                        }

                        LOGGER.warn("{} is ready to be processed", task);
                        task.run();
                    } catch (InterruptedException cause) {
                        if (interruptReason() == InterruptReason.SHUTDOWN) {
                            LOGGER.debug("{} is interrupted", workerName());
                            break;
                        } else {
                            LOGGER.debug("Ignore interrupt");
                        }
                    }
                }
            } finally {
                cleanup();
            }
        }

        void cleanup() {
            LOGGER.debug("Cleanup worker: {}", workerName());
            cleanupHandler.accept(this);
        }
    }
}
