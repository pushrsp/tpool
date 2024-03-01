package tpool;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TPoolTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TPoolTest.class);

    @Test
    public void canCreateWorkerUntilMaxNumWorkers() throws Exception {
        //given
        final int maxNumWorkers = 10;
        final int minNumWorkers = 5;

        final TPool pool = TPool.builder(maxNumWorkers)
                .minNumWorkers(minNumWorkers)
                .build();

        final int numTasks = 10000;
        for (int i = 0; i < numTasks; i++) {
            final int finalI = i;
            pool.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }

                @Override
                public String toString() {
                    return "Task #" + finalI;
                }
            });
        }
        //when
        int coreSize = pool.coreWorkersSize();
        int extraSize = pool.extraWorkersSize();
        int poolSize = pool.workersSize();

        //then
        assertThat(coreSize).isEqualTo(minNumWorkers);
        assertThat(extraSize).isEqualTo(maxNumWorkers - minNumWorkers);
        assertThat(poolSize).isEqualTo(maxNumWorkers);
    }

    @Test
    public void canInterruptedShutdown() throws Exception {
        //given
        final int maxNumWorkers = 10;
        final int minNumWorkers = 5;

        final TPool pool = TPool.builder(maxNumWorkers)
                .minNumWorkers(minNumWorkers)
                .build();

        final int numTasks = 10000;
        for (int i = 0; i < numTasks; i++) {
            final int finalI = i;
            pool.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(100000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }

                @Override
                public String toString() {
                    return "Task #" + finalI;
                }
            });
        }

        //when
        pool.interruptedShutdown();

        //then
    }

    @Test
    public void canShutdown() throws Exception {
        //given
        final int maxNumWorkers = 10;
        final int minNumWorkers = 7;

        final TPool pool = TPool.builder(maxNumWorkers)
                .minNumWorkers(minNumWorkers)
                .build();

        final int numTasks = 10000;
        for (int i = 0; i < numTasks; i++) {
            final int finalI = i;
            pool.execute(new Runnable() {
                @Override
                public void run() {
                    LOGGER.debug("Started task: {}", this);
                }

                @Override
                public String toString() {
                    return "Task #" + finalI;
                }
            });
        }

        //when
        pool.shutdown();

        int coreSize = pool.coreWorkersSize();
        int extraSize = pool.extraWorkersSize();
        int poolSize = pool.workersSize();

        //then
        assertThat(coreSize).isEqualTo(0);
        assertThat(extraSize).isEqualTo(0);
        assertThat(poolSize).isEqualTo(0);
    }

    @Test
    public void cannotAddTaskAfterPoolIsShutdown() throws Exception {
        //given
        final int maxNumWorkers = 1;
        final int minNumWorkers = 1;

        final TPool pool = TPool.builder(maxNumWorkers)
                .minNumWorkers(minNumWorkers)
                .build();

        final int numTasks = 1000;
        for (int i = 0; i < numTasks; i++) {
            final int finalI = i;
            pool.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(30000);
                    } catch (InterruptedException e) {
                        LOGGER.warn("{} is interrupted", this);
                        Thread.currentThread().interrupt();
                    }
                }

                @Override
                public String toString() {
                    return "Task #" + finalI;
                }
            });
        }

        //when
        Thread.sleep(500);
        int tasksSize = pool.tasksSize();
        List<Runnable> remainedTasks = pool.interruptedShutdown();
        int tasksSizeAfterShutdown = pool.tasksSize();

        //then
        assertThat(tasksSize).isEqualTo(numTasks - maxNumWorkers);
        assertThat(remainedTasks).hasSize(numTasks - maxNumWorkers);
        assertThat(tasksSizeAfterShutdown).isEqualTo(0);
    }
}
