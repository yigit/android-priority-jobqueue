package com.path.android.jobqueue.test.jobmanager;

import com.path.android.jobqueue.Job;
import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.Params;
import com.path.android.jobqueue.RetryConstraint;

import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.CoreMatchers.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = com.path.android.jobqueue.BuildConfig.class)
public class RetryLogicTest extends JobManagerTestBase {

    static RetryProvider retryProvider;

    static boolean canRun;

    static int runCount;

    static CountDownLatch onRunLatch;

    static Callback onRunCallback;

    static CountDownLatch cancelLatch;

    @Before
    public void clear() {
        retryProvider = null;
        canRun = false;
        runCount = 0;
        onRunLatch = null;
        onRunCallback = null;
        cancelLatch = new CountDownLatch(1);
    }

    @Test
    public void testExponential() {
        assertThat("exp 1",RetryConstraint.createExponentialBackoff(1, 10).getNewDelayInMs(),
                is(10L));
        assertThat("exp 2",RetryConstraint.createExponentialBackoff(2, 10).getNewDelayInMs(),
                is(20L));
        assertThat("exp 3",RetryConstraint.createExponentialBackoff(3, 10).getNewDelayInMs(),
                is(40L));

        assertThat("exp 1",RetryConstraint.createExponentialBackoff(1, 5).getNewDelayInMs(),
                is(5L));
        assertThat("exp 2",RetryConstraint.createExponentialBackoff(2, 5).getNewDelayInMs(),
                is(10L));
        assertThat("exp 3",RetryConstraint.createExponentialBackoff(3, 5).getNewDelayInMs(),
                is(20L));
    }

    @Test
    public void testRunCountPersistent() throws InterruptedException {
        testFirstRunCount(true);
    }

    @Test
    public void testRunCountNonPersistent() throws InterruptedException {
        testFirstRunCount(false);
    }

    public void testFirstRunCount(boolean persistent) throws InterruptedException {
        final AtomicInteger runCnt = new AtomicInteger(0);
        onRunCallback = new Callback() {
            @Override
            public void on(Job job) {
                assertThat("run count should match", ((RetryJob) job).getCurrentRunCount(),
                        is(runCnt.incrementAndGet()));
            }
        };
        retryProvider = new RetryProvider() {
            @Override
            public RetryConstraint build(Job job, Throwable throwable, int runCount,
                    int maxRunCount) {
                return RetryConstraint.RETRY;
            }
        };
        canRun = true;
        RetryJob job = new RetryJob(new Params(0).setPersistent(persistent));
        job.retryLimit = 10;
        createJobManager().addJob(job);
        assertThat("", cancelLatch.await(4, TimeUnit.SECONDS), is(true));
        assertThat("", runCount, is(10));
    }

    @Test
    public void testChangeDelayPersistent() throws InterruptedException {
        testChangeDelay(true);
    }

    @Test
    public void testChangeDelayNonPersistent() throws InterruptedException {
        testChangeDelay(false);
    }

    public void testChangeDelay(boolean persistent) throws InterruptedException {
        canRun = true;
        RetryJob job = new RetryJob(new Params(1).setPersistent(persistent));
        job.retryLimit = 2;
        retryProvider = new RetryProvider() {
            @Override
            public RetryConstraint build(Job job, Throwable throwable, int runCount,
                    int maxRunCount) {
                RetryConstraint constraint = new RetryConstraint(true);
                constraint.setNewDelayInMs(2000L);
                return constraint;
            }
        };
        final List<Long> runTimes = new ArrayList<>();
        onRunCallback = new Callback() {
            @Override
            public void on(Job job) {
                runTimes.add(System.nanoTime());
            }
        };
        createJobManager().addJob(job);
        assertThat("job should be canceled", cancelLatch.await(4, TimeUnit.SECONDS), is(true));
        assertThat("should run 2 times", runCount, is(2));
        long timeInBetween = TimeUnit.NANOSECONDS.toSeconds(runTimes.get(1) - runTimes.get(0));
        assertThat("time between two runs should be at least 2 seconds. " + timeInBetween,
                 2 <= timeInBetween, is(true));
    }

    @Test
    public void testChangePriorityAndObserveExecutionOrderPersistent() throws InterruptedException {
        testChangePriorityAndObserveExecutionOrder(true);
    }

    @Test
    public void testChangePriorityAndObserveExecutionOrderNonPersistent()
            throws InterruptedException {
        testChangePriorityAndObserveExecutionOrder(false);
    }

    public void testChangePriorityAndObserveExecutionOrder(boolean persistent)
            throws InterruptedException {
        cancelLatch = new CountDownLatch(2);
        RetryJob job1 = new RetryJob(new Params(10).setPersistent(persistent).groupBy("group"));
        job1.identifier = "1";
        RetryJob job2 = new RetryJob(new Params(5).setPersistent(persistent).groupBy("group"));
        job2.identifier = "2";
        JobManager jobManager = createJobManager();
        jobManager.stop();
        jobManager.addJob(job1);
        jobManager.addJob(job2);
        retryProvider = new RetryProvider() {
            @Override
            public RetryConstraint build(Job job, Throwable throwable, int runCount,
                    int maxRunCount) {
                RetryJob retryJob = (RetryJob) job;
                if ("1".equals(retryJob.identifier)) {
                    if (retryJob.getPriority() == 1) {
                        return RetryConstraint.CANCEL;
                    }
                    RetryConstraint retryConstraint = new RetryConstraint(true);
                    retryConstraint.setNewPriority(1);
                    return retryConstraint;
                } else {
                    return RetryConstraint.CANCEL;
                }
            }
        };
        final List<String> runOrder = new ArrayList<>();
        onRunCallback = new Callback() {
            @Override
            public void on(Job job) {
                runOrder.add(((RetryJob) job).identifier);
            }
        };
        canRun = true;
        jobManager.start();
        assertThat("both jobs should be canceled eventually", cancelLatch.await(3, TimeUnit.MINUTES)
                , is(true));
        assertThat("jobs should run a total of 3 times", runCount, is(3));
        final List<String> expectedRunOrder = Arrays.asList("1", "2", "1");
        assertThat("expected run order count should match", runOrder.size(), is(expectedRunOrder.size()));
        for (int i = 0; i < expectedRunOrder.size(); i++) {
            assertThat("at iteration " + i + ", this job should run",
                    runOrder.get(i), is(expectedRunOrder.get(i)));
        }
    }

    @Test
    public void testChangePriorityPersistent() throws InterruptedException {
        testChangePriority(true);
    }

    @Test
    public void testChangePriorityNonPersistent() throws InterruptedException {
        testChangePriority(false);
    }

    @Ignore
    public void testChangePriority(boolean persistent) throws InterruptedException {
        final AtomicInteger priority = new AtomicInteger(1);
        retryProvider = new RetryProvider() {
            @Override
            public RetryConstraint build(Job job, Throwable throwable, int runCount, int maxRunCount) {
                RetryConstraint constraint = new RetryConstraint(true);
                priority.set(job.getPriority() * 2);
                constraint.setNewPriority(priority.get());
                return constraint;
            }
        };

        onRunCallback = new Callback() {
            @Override
            public void on(Job job) {
                assertThat("priority should be the expected value", job.getPriority(), is(priority.get()));
            }
        };
        RetryJob retryJob = new RetryJob(new Params(priority.get()).setPersistent(persistent));
        retryJob.retryLimit = 3;
        canRun = true;
        onRunLatch = new CountDownLatch(3);
        createJobManager().addJob(retryJob);
        assertThat(onRunLatch.await(5, TimeUnit.SECONDS), is(true));
        assertThat("it should run 3 times", runCount, is(3));
        assertThat(cancelLatch.await(5, TimeUnit.SECONDS), is(true));
    }

    @Test
    public void testCancelPersistent() throws InterruptedException {
        testCancel(true);
    }

    @Test
    public void testCancelNonPersistent() throws InterruptedException {
        testCancel(false);
    }

    public void testCancel(boolean persistent) throws InterruptedException {
        canRun = true;
        retryProvider = new RetryProvider() {
            @Override
            public RetryConstraint build(Job job, Throwable throwable, int runCount, int maxRunCount) {
                return RetryConstraint.CANCEL;
            }
        };
        RetryJob job = new RetryJob(new Params(1).setPersistent(persistent));
        job.retryLimit = 3;
        onRunLatch = new CountDownLatch(3);
        createJobManager().addJob(job);
        assertThat(onRunLatch.await(2, TimeUnit.SECONDS), is(false));
        assertThat("it should run 1 time", runCount, is(1));
        assertThat(cancelLatch.await(2, TimeUnit.SECONDS), is(true));
    }

    @Test
    public void retryPersistent() throws InterruptedException {
        testRetry(true, true);
    }

    @Test
    public void retryNonPersistent() throws InterruptedException {
        testRetry(false, true);
    }

    @Test
    public void retryPersistentWithNull() throws InterruptedException {
        testRetry(true, false);
    }

    @Test
    public void retryNonPersistentWithNull() throws InterruptedException {
        testRetry(false, false);
    }

    public void testRetry(boolean persistent, final boolean returnTrue) throws InterruptedException {
        canRun = true;
        retryProvider = new RetryProvider() {
            @Override
            public RetryConstraint build(Job job, Throwable throwable, int runCount, int maxRunCount) {
                return returnTrue ? RetryConstraint.RETRY : null;
            }
        };
        RetryJob job = new RetryJob(new Params(1).setPersistent(persistent));
        job.retryLimit = 3;
        onRunLatch = new CountDownLatch(3);
        createJobManager().addJob(job);
        assertThat(onRunLatch.await(2, TimeUnit.SECONDS), is(true));
        assertThat("it should run 3 times", runCount, is(3));
        assertThat(cancelLatch.await(2, TimeUnit.SECONDS), is(true));
    }

    public static class RetryJob extends Job {
        int retryLimit = 5;
        String identifier;
        protected RetryJob(Params params) {
            super(params);
        }

        @Override
        public void onAdded() {

        }

        @Override
        public void onRun() throws Throwable {
            assertThat("should be allowed to run", canRun, is(true));
            if (onRunCallback != null) {
                onRunCallback.on(this);
            }
            runCount++;
            if (onRunLatch != null) {
                onRunLatch.countDown();
            }
            throw new RuntimeException("i like to fail please");
        }

        @Override
        protected int getRetryLimit() {
            return retryLimit;
        }

        @Override
        protected void onCancel() {
            cancelLatch.countDown();
        }

        @Override
        protected RetryConstraint shouldReRunOnThrowable(Throwable throwable, int runCount,
                int maxRunCount) {
            if (retryProvider != null) {
                return retryProvider.build(this, throwable, runCount, maxRunCount);
            }
            return RetryConstraint.createExponentialBackoff(runCount, 1000);
        }

        @Override
        public int getCurrentRunCount() {
            return super.getCurrentRunCount();
        }
    }

    interface RetryProvider {
        RetryConstraint build(Job job, Throwable throwable, int runCount,
                int maxRunCount);
    }

    interface Callback {
        public void on(Job job);
    }
}
