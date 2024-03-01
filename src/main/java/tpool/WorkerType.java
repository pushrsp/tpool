package tpool;

/**
 * Types of Worker.
 */
enum WorkerType {
    /**
     * The worker that never get terminated.
     */
    CORE,
    /**
     * The worker that can be terminated.
     */
    EXTRA,
    /**
     * The worker that monitor other workers.
     */
    WATCHDOG
}
