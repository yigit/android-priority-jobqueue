package com.birbit.android.jobqueue.test.jobmanager;

import android.annotation.TargetApi;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Pair;

import com.birbit.android.jobqueue.CallbackManager;
import com.birbit.android.jobqueue.CancelReason;
import com.birbit.android.jobqueue.Job;
import com.birbit.android.jobqueue.JobManager;
import com.birbit.android.jobqueue.JobManagerTrojan;
import com.birbit.android.jobqueue.Params;
import com.birbit.android.jobqueue.RetryConstraint;
import com.birbit.android.jobqueue.callback.JobManagerCallbackAdapter;
import com.birbit.android.jobqueue.log.JqLog;
import com.birbit.android.jobqueue.test.jobs.DummyJob;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(RobolectricTestRunner.class)
@Config(constants = com.birbit.android.jobqueue.BuildConfig.class)
public class RetryLogicTest extends JobManagerTestBase {

    static RetryProvider retryProvider;

    static boolean canRun;

    static int runCount;

    static CountDownLatch onRunLatch;

    static Callback onRunCallback;

    static CancelCallback onCancelCallback;

    static CountDownLatch cancelLatch;

    static CountDownLatch dummyJobRunLatch;

    @Before
    public void clear() {
        retryProvider = null;
        canRun = false;
        runCount = 0;
        onRunLatch = null;
        onRunCallback = null;
        onCancelCallback = null;
        cancelLatch = new CountDownLatch(1);
        dummyJobRunLatch = new CountDownLatch(1);
    }

    @Test
    public void testExponential() {
        assertThat("exp 1",RetryConstraint.createExponentialBackoff(1, 10).getNewDelayInMs(),
                is(10L));
        assertThat("exp 2",RetryConstraint.createExponentialBackoff(2, 10).getNewDelayInMs(),
                is(20L));
        assertThat("exp 3",RetryConstraint.createExponentialBackoff(3, 10).getNewDelayInMs(),
                is(40L));

        assertThat("exp 1", RetryConstraint.createExponentialBackoff(1, 5).getNewDelayInMs(),
                is(5L));
        assertThat("exp 2",RetryConstraint.createExponentialBackoff(2, 5).getNewDelayInMs(),
                is(10L));
        assertThat("exp 3", RetryConstraint.createExponentialBackoff(3, 5).getNewDelayInMs(),
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
    public void testChangeDelayOfTheGroup() throws InterruptedException {
        testChangeDelayOfTheGroup(null);
    }

    @Test
    public void testChangeDelayOfTheGroupPersistent() throws InterruptedException {
        testChangeDelayOfTheGroup(true);
    }

    @Test
    public void testChangeDelayOfTheGroupNonPersistent() throws InterruptedException {
        testChangeDelayOfTheGroup(false);
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    public void testChangeDelayOfTheGroup(Boolean persistent) throws InterruptedException {
        final JobManager jobManager = createJobManager();
        canRun = true;
        final RetryJob job1 = new RetryJob(new Params(2).setPersistent(Boolean.TRUE.equals(persistent)).groupBy("g1"));
        job1.identifier = "job 1 id";
        RetryJob job2 = new RetryJob(new Params(2).setPersistent(!Boolean.FALSE.equals(persistent)).groupBy("g1"));
        job2.identifier = "job 2 id";
        job1.retryLimit = 2;
        job2.retryLimit = 2;
        final String job1Id = job1.identifier;
        final String job2Id = job2.identifier;
        final PersistableDummyJob postTestJob = new PersistableDummyJob(new Params(1)
                .groupBy("g1").setPersistent(Boolean.TRUE.equals(persistent)));
        final Semaphore jobsCanRun = new Semaphore(4);
        jobsCanRun.acquire(3);
        final Job[] unexpectedRun = new Job[1];
        final CallbackManager callbackManager = JobManagerTrojan.getCallbackManager(jobManager);
        retryProvider = new RetryProvider() {
            @Override
            public RetryConstraint build(Job job, Throwable throwable, int runCount,
                    int maxRunCount) {
                RetryConstraint constraint = new RetryConstraint(true);
                constraint.setNewDelayInMs(2000L);
                JqLog.d("setting new delay in mS to %s. now is %s. job is %s", 2000, mockTimer.nanoTime(), ((RetryJob)job).identifier);
                constraint.setApplyNewDelayToGroup(true);
                return constraint;
            }
        };
        final List<Pair<String, Long>> runTimes = new ArrayList<>();
        final Map<String, Long> cancelTimes = new HashMap<>();
        final Throwable[] lastJobRunOrder = new Throwable[1];
        onRunCallback = new Callback() {
            @Override
            public void on(Job job) {
                if (!callbackManager.waitUntilAllMessagesAreConsumed(30)) {
                    lastJobRunOrder[0] = new RuntimeException("consumers did not finish in 30 seconds");
                }

                RetryJob retryJob = (RetryJob) job;
                if (!jobsCanRun.tryAcquire() && unexpectedRun[0] == null) {
                    unexpectedRun[0] = job;
                }
                runTimes.add(new Pair<>(retryJob.identifier, mockTimer.nanoTime()));
            }
        };
        onCancelCallback = new CancelCallback() {
            @Override
            public void on(Job job, int cancelReason, Throwable throwable) {
                JqLog.d("on cancel of job %s", job);
                RetryJob retryJob = (RetryJob) job;
                assertThat("Job should cancel only once",
                        cancelTimes.containsKey(retryJob.identifier), is(false));
                cancelTimes.put(retryJob.identifier, mockTimer.nanoTime());
                if (!job.isPersistent() || postTestJob.isPersistent()) {
                    if (dummyJobRunLatch.getCount() != 1) {
                        lastJobRunOrder[0] = new Exception("the 3rd job should not run until others cancel fully");
                    }
                }
            }
        };
        cancelLatch = new CountDownLatch(2);
        final CountDownLatch jobRunLatch = new CountDownLatch(5);
        final Throwable[] afterJobError = new Throwable[1];
        jobManager.addCallback(new JobManagerCallbackAdapter() {
            @Override
            public void onJobRun(@NonNull Job job, int resultCode) {
                synchronized (this) {
                    try {
                        if (job instanceof RetryJob) {
                            RetryJob retryJob = (RetryJob) job;
                            if (retryJob.identifier.equals(job1Id) &&
                                    retryJob.getCurrentRunCount() == retryJob.getRetryLimit()) {
                                jobsCanRun.release();
                            }
                        }
                    } catch (Throwable t) {
                        afterJobError[0] = t;
                    }
                }
            }

            @Override
            public void onAfterJobRun(@NonNull Job job, int resultCode) {
                synchronized (this) {
                    try {
                        if (job instanceof RetryJob) {
                            assertThat("no job should have run unexpectedly " + jobRunLatch.getCount(),
                                    unexpectedRun[0], nullValue());
                            RetryJob retryJob = (RetryJob) job;
                            if (retryJob.getCurrentRunCount() == 2) {
                                // next job should be ready, asserted in onRun
                            } else {
                                mockTimer.incrementMs(1999);
                                assertThat("no jobs should be ready", jobManager.countReadyJobs(), is(0));
                                jobsCanRun.release();
                                mockTimer.incrementMs(2);
                            }
                        }
                    } catch (Throwable t) {
                        afterJobError[0] = t;
                        jobRunLatch.countDown();
                        jobRunLatch.countDown();
                        jobRunLatch.countDown();
                        jobRunLatch.countDown();
                        jobRunLatch.countDown();
                    } finally {
                        jobRunLatch.countDown();
                    }
                }
            }
        });
        jobManager.addJob(job1);
        jobManager.addJob(job2);
        jobManager.addJob(postTestJob);
        assertThat("all expected jobs should run", jobRunLatch.await(5, TimeUnit.MINUTES), is(true));
        assertThat("on run assertions should all pass", afterJobError[0], nullValue());
        assertThat("jobs should be canceled", cancelLatch.await(1, TimeUnit.MILLISECONDS), is(true));
        assertThat("should run 4 times", runTimes.size(), is(4));
        for (int i = 0; i < 4; i ++) {
            assertThat("first two runs should be job1, last two jobs should be job 2. checking " + i,
                    runTimes.get(i).first, is(i < 2 ? job1Id : job2Id));
        }
        long timeInBetween = TimeUnit.NANOSECONDS.toSeconds(
                runTimes.get(1).second - runTimes.get(0).second);
        assertThat("time between two runs should be at least 2 seconds. job 1 and 2" + ":"
                + timeInBetween, 2 <= timeInBetween, is(true));
        timeInBetween = TimeUnit.NANOSECONDS.toSeconds(
                runTimes.get(3).second - runTimes.get(2).second);
        assertThat("time between two runs should be at least 2 seconds. job 3 and 4" + ":"
                + timeInBetween, 2 <= timeInBetween, is(true));
        assertThat("the other job should run after others are cancelled",
                dummyJobRunLatch.await(1, TimeUnit.SECONDS), is(true));
        // another job should just run
        dummyJobRunLatch = new CountDownLatch(1);
        jobManager.addJob(new PersistableDummyJob(new Params(1).groupBy("g1")));
        assertThat("a newly added job should just run quickly", dummyJobRunLatch.await(500,
                TimeUnit.MILLISECONDS),is

            (true));
        assertThat(lastJobRunOrder[0], nullValue());
    }

    @Test
    public void testChangeDelayPersistent() throws InterruptedException {
        testChangeDelay(true);
    }

    @Test
    public void testChangeDelayNonPersistent() throws InterruptedException {
        testChangeDelay(false);
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
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
                runTimes.add(mockTimer.nanoTime());
            }
        };
        final Throwable[] callbackError = new Throwable[1];
        final CountDownLatch runLatch = new CountDownLatch(2);
        final JobManager jobManager = createJobManager();
        jobManager.addCallback(new JobManagerCallbackAdapter() {
            @Override
            public void onAfterJobRun(@NonNull Job job, int resultCode) {
                try {
                    mockTimer.incrementMs(1999);
                    assertThat("no jobs should be ready", jobManager.countReadyJobs(), is(0));
                    mockTimer.incrementMs(2);
                } catch (Throwable t) {
                    callbackError[0] = t;
                } finally {
                    runLatch.countDown();
                }
            }
        });
        jobManager.addJob(job);
        assertThat("on run callbacks should arrive", runLatch.await(100, TimeUnit.MINUTES), is(true));
        assertThat("run callback should not have any errors", callbackError[0], nullValue());
        assertThat("job should be canceled", cancelLatch.await(1, TimeUnit.SECONDS), is(true));
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

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
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
        final Throwable[] retryThrowable = new Throwable[1];
        final Throwable[] cancelThrowable = new Throwable[1];
        retryProvider = new RetryProvider() {
            @Override
            public RetryConstraint build(Job job, Throwable throwable, int runCount, int maxRunCount) {
                retryThrowable[0] = throwable;
                return RetryConstraint.CANCEL;
            }
        };
        onCancelCallback = new CancelCallback() {
            @Override
            public void on(Job job, @CancelReason int cancelReason, @Nullable Throwable throwable) {
                assertThat("should call cancel only once", cancelThrowable[0], is(nullValue()));
                cancelThrowable[0] = throwable;
            }
        };
        RetryJob job = new RetryJob(new Params(1).setPersistent(persistent));
        job.retryLimit = 3;
        onRunLatch = new CountDownLatch(3);
        createJobManager().addJob(job);
        assertThat(onRunLatch.await(2, TimeUnit.SECONDS), is(false));
        assertThat("it should run 1 time", runCount, is(1));
        assertThat(cancelLatch.await(2, TimeUnit.SECONDS), is(true));
        assertThat(retryThrowable[0], instanceOf(RuntimeException.class));
        assertThat(cancelThrowable[0], instanceOf(RuntimeException.class));
        assertThat(retryThrowable[0], is(cancelThrowable[0]));
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
            throw new RuntimeException("i like to fail please " + identifier);
        }

        @Override
        public String toString() {
            return "RETRY_JOB[" + identifier + "]";
        }

        @Override
        protected int getRetryLimit() {
            return retryLimit;
        }

        @Override
        protected void onCancel(@CancelReason int cancelReason, @Nullable Throwable throwable) {
            if (onCancelCallback != null) {
                onCancelCallback.on(this, cancelReason, throwable);
            }
            cancelLatch.countDown();
        }

        @Override
        protected RetryConstraint shouldReRunOnThrowable(@NonNull Throwable throwable, int runCount,
                                                         int maxRunCount) {
            if (retryProvider != null) {
                return retryProvider.build(this, throwable, runCount, maxRunCount);
            }
            return RetryConstraint.createExponentialBackoff(runCount, 1000);
        }
    }

    private static class PersistableDummyJob extends DummyJob {
        public PersistableDummyJob(Params params) {
            super(params);
        }

        @Override
        public void onRun() throws Throwable {
            super.onRun();
            dummyJobRunLatch.countDown();
        }
    }


    interface RetryProvider {
        RetryConstraint build(Job job, Throwable throwable, int runCount,
                int maxRunCount);
    }

    interface Callback {
        public void on(Job job);
    }

    interface CancelCallback {
        public void on(Job job,@CancelReason int cancelReason, @Nullable Throwable throwable);
    }
}
