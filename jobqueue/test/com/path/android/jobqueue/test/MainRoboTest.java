package com.path.android.jobqueue.test;


import com.path.android.jobqueue.BaseJob;
import com.path.android.jobqueue.JobHolder;
import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.test.jobs.DummyJob;
import com.path.android.jobqueue.test.jobs.PersistentDummyJob;
import org.fest.reflect.core.Reflection;
import org.fest.reflect.method.Invoker;
import org.hamcrest.MatcherAssert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;

@RunWith(RobolectricTestRunner.class)
public class MainRoboTest {
    //TEST parallel running
    @Test
    public void runManyNonPersistentJobs() throws Exception {
        JobManager jobManager = new JobManager(Robolectric.application, "test1");
        jobManager.stop();
        int limit = 2;
        final CountDownLatch latch = new CountDownLatch(limit);
        for (int i = 0; i < limit; i++) {
            jobManager.addJob(i, new DummyLatchJob(latch));
        }
        jobManager.start();
        latch.await(10, TimeUnit.SECONDS);
        MatcherAssert.assertThat((int) latch.getCount(), equalTo(0));
    }

    @Test
    public void runFailingJob() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        JobManager jobManager = new JobManager(Robolectric.application, "test2");
        jobManager.addJob(0, new BaseJob(true) {
            @Override
            public void onAdded() {

            }

            @Override
            public void onRun() throws Throwable {
                throw new RuntimeException();
            }

            @Override
            public boolean shouldPersist() {
                return false;
            }

            @Override
            protected void onCancel() {
                latch.countDown();
            }

            @Override
            protected boolean shouldReRunOnThrowable(Throwable throwable) {
                return false;
            }
        });
        latch.await(10, TimeUnit.SECONDS);
        MatcherAssert.assertThat((int) latch.getCount(), equalTo(0));
    }

    @Test
    public void testDelay() throws Exception {
        testDelay(false);
        testDelay(true);
    }

    public void testDelay(boolean persist) throws Exception {
        JobManager jobManager = createNewJobManager();
        jobManager.stop();
        DummyJob delayedJob = persist ? new PersistentDummyJob() : new DummyJob();
        DummyJob nonDelayedJob = persist ? new PersistentDummyJob() : new DummyJob();
        long jobId = jobManager.addJob(10, 1000, delayedJob);
        long nonDelayedJobId = jobManager.addJob(0, 0, nonDelayedJob);

        Invoker<JobHolder> nextJobMethod = getNextJobMethod(jobManager);
        Invoker<Void> removeJobMethod = getRemoveJobMethod(jobManager);

        JobHolder receivedJob = nextJobMethod.invoke();
        MatcherAssert.assertThat("non-delayed job should be served",  receivedJob, notNullValue());
        MatcherAssert.assertThat("non-delayed job should id should match",  receivedJob.getId(), equalTo(nonDelayedJobId));
        removeJobMethod.invoke(receivedJob);
        MatcherAssert.assertThat("delayed job should not be served",  nextJobMethod.invoke(), nullValue());
        MatcherAssert.assertThat("job count should still be 1",  jobManager.count(), equalTo(1));
        Thread.sleep(500);
        MatcherAssert.assertThat("delayed job should not be served",  nextJobMethod.invoke(), nullValue());
        MatcherAssert.assertThat("job count should still be 1",  jobManager.count(), equalTo(1));
        Thread.sleep(1000);
        receivedJob = nextJobMethod.invoke();
        MatcherAssert.assertThat("now should be able to receive the delayed job", receivedJob, notNullValue());
        if(receivedJob != null) {
            MatcherAssert.assertThat("received job should be the delayed job", receivedJob.getId(), equalTo(jobId));
        }
    }

    @Test
    public void testDelayedRun() throws Exception {
        testDelayedRun(false);
        testDelayedRun(true);
    }
    public void testDelayedRun(boolean persist) throws Exception {
        JobManager jobManager = createNewJobManager();
        DummyJob delayedJob = persist ? new PersistentDummyJob() : new DummyJob();
        DummyJob nonDelayedJob = persist ? new PersistentDummyJob() : new DummyJob();
        jobManager.addJob(10, 2000, delayedJob);
        jobManager.addJob(0, 0, nonDelayedJob);
        Thread.sleep(500);
        MatcherAssert.assertThat("there should be 1 delayed job waiting to be run", jobManager.count(), equalTo(1));
        Thread.sleep(3000);
        MatcherAssert.assertThat("all jobs should be completed", jobManager.count(), equalTo(0));

    }

    public static CountDownLatch persistentRunLatch = new CountDownLatch(1);

    @Test
    public void testPersistentJob() throws Exception {
        String managerId = "persistentTest";
        JobManager jobManager = new JobManager(Robolectric.application, managerId);
        jobManager.addJob(0, new DummyPersistentLatchJob());
        persistentRunLatch.await(5, TimeUnit.SECONDS);
        MatcherAssert.assertThat((int) persistentRunLatch.getCount(), equalTo(0));
    }

    private static CountDownLatch priorityRunLatch;

    @Test
    public void testPriority() throws Exception {
        JobManager jobManager = createNewJobManager();
        jobManager.setMaxConsumerCount(1);
        testPriority(jobManager, false);
    }

    public void testPriority(JobManager jobManager, boolean persist) throws Exception {
        priorityRunLatch = new CountDownLatch(2);
        DummyJobWithRunOrderAssert.globalRunCount = new AtomicInteger(0);
        BaseJob job1 = new DummyJobWithRunOrderAssert(2, persist);
        BaseJob job2 = new DummyJobWithRunOrderAssert(1, persist);
        jobManager.stop();
        jobManager.addJob(1, job1);
        jobManager.addJob(2, job2);
        jobManager.start();
        priorityRunLatch.await(4, TimeUnit.SECONDS);
        //ensure both jobs did run
        MatcherAssert.assertThat((int) priorityRunLatch.getCount(), equalTo(0));
    }

    @Test
    public void testCount() throws Exception {
        JobManager jobManager = new JobManager(Robolectric.application, "count" + System.nanoTime());
        jobManager.stop();
        for (int i = 0; i < 10; i++) {
            jobManager.addJob(0, new PersistentDummyJob());
            MatcherAssert.assertThat((int) jobManager.count(), equalTo(i * 2 + 1));
            jobManager.addJob(0, new PersistentDummyJob());
            MatcherAssert.assertThat((int) jobManager.count(), equalTo(i * 2 + 2));
        }
        jobManager.start();
        Thread.sleep(2000);
        MatcherAssert.assertThat((int) jobManager.count(), equalTo(0));
    }

    @Test
    public void testSessionId() throws Exception {
        JobManager jobManager = createNewJobManager();
        Long sessionId = Reflection.field("sessionId").ofType(long.class)
                .in(jobManager).get();
        jobManager.stop();
        BaseJob[] jobs = new BaseJob[]{new DummyJob(), new PersistentDummyJob()};
        for (BaseJob job : jobs) {
            jobManager.addJob(0, job);
        }

        Invoker<JobHolder> nextJobMethod = getNextJobMethod(jobManager);
        for (int i = 0; i < jobs.length; i++) {
            JobHolder jobHolder = nextJobMethod.invoke();
            MatcherAssert.assertThat("session id should be correct for job " + i, jobHolder.getRunningSessionId(), equalTo(sessionId));
        }
    }

    private Invoker<JobHolder> getNextJobMethod(JobManager jobManager) {
        return Reflection.method("getNextJob").withReturnType(JobHolder.class).in(jobManager);
    }

    private Invoker<Void> getRemoveJobMethod(JobManager jobManager) {
        return Reflection.method("removeJob").withParameterTypes(JobHolder.class).in(jobManager);
    }

    private JobManager createNewJobManager(String id) {
        return new JobManager(Robolectric.application, id);
    }

    private JobManager createNewJobManager() {
        return createNewJobManager("_" + System.nanoTime());
    }

    @Test
    public void testAddedCount() throws Exception {
        testAddedCount(new DummyJob());
        testAddedCount(new PersistentDummyJob());

    }

    private void testAddedCount(DummyJob dummyJob) {
        JobManager jobManager = createNewJobManager();
        jobManager.stop();
        jobManager.addJob(0, dummyJob);
        MatcherAssert.assertThat(1, equalTo(dummyJob.getOnAddedCnt()));
    }


    @Test
    public void testReRunWithLimit() throws Exception {
        JobManager jobManager = createNewJobManager();
        testReRun(jobManager, false);
        testReRun(jobManager, true);
    }

    private void testReRun(JobManager jobManager, boolean persist) throws InterruptedException {
        DummyJobWithRunCount.runCount = 0;//reset
        DummyJobWithRunCount job = new DummyJobWithRunCount(persist);
        jobManager.addJob(0, job);
        int limit = 25;
        while (limit-- > 0 && DummyJobWithRunCount.runCount != 5) {
            Thread.sleep(100);
        }
        MatcherAssert.assertThat(DummyJobWithRunCount.runCount, equalTo(job.getRetryLimit()));
        MatcherAssert.assertThat((int) jobManager.count(), equalTo(0));
    }

    private static class DummyPersistentLatchJob extends PersistentDummyJob {

        @Override
        public void onRun() throws Throwable {
            MainRoboTest.persistentRunLatch.countDown();
        }
    }

    private static class DummyLatchJob extends DummyJob {
        private final CountDownLatch latch;

        private DummyLatchJob(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public void onRun() throws Throwable {
            super.onRun();
            latch.countDown();
        }
    }


    private static class DummyJobWithRunCount extends DummyJob {
        public static int runCount;
        private boolean persist;

        private DummyJobWithRunCount(boolean persist) {
            this.persist = persist;
        }

        @Override
        public void onRun() throws Throwable {
            runCount++;
            super.onRun();
            throw new RuntimeException("i am dummy, i throw exception when running");
        }

        @Override
        public boolean shouldPersist() {
            return persist;
        }

        @Override
        protected boolean shouldReRunOnThrowable(Throwable throwable) {
            return true;
        }

        @Override
        protected int getRetryLimit() {
            return 5;
        }
    }


    public static class DummyJobWithRunOrderAssert extends BaseJob {
        transient public static AtomicInteger globalRunCount;
        private int expectedRunOrder;
        private boolean persist;

        public DummyJobWithRunOrderAssert(int expectedRunOrder, boolean persist) {
            super(true);
            this.expectedRunOrder = expectedRunOrder;
            this.persist = persist;
        }

        @Override
        public void onAdded() {
        }

        @Override
        public void onRun() throws Throwable {
            final int cnt = globalRunCount.incrementAndGet();
            MatcherAssert.assertThat(expectedRunOrder, equalTo(cnt));
            priorityRunLatch.countDown();
        }

        @Override
        public boolean shouldPersist() {
            return persist;
        }

        @Override
        protected void onCancel() {

        }

        @Override
        protected boolean shouldReRunOnThrowable(Throwable throwable) {
            return false;
        }
    }
}
