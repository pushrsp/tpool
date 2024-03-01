package tpool;

enum TerminationReason {
    /**
     * The reason why a worker is terminated by `{@link TPool#shutdown()}` or `{@link TPool#interruptedShutdown()}`.
     */
    SHUTDOWN,
    /**
     * The reason why worker is terminated by `Watchdog`.
     */
    WATCHDOG,
    /**
     * The reason why worker is terminated by `idleTimeoutNanos`.
     */
    IDLE_TIMEOUT
}
