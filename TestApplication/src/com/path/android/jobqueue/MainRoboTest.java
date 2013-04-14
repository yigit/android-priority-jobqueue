package com.path.android.jobqueue;


import org.fest.reflect.core.Reflection;
import org.hamcrest.MatcherAssert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.io.Serializable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.equalTo;

@RunWith(RobolectricTestRunner.class)
public class MainRoboTest {
    @Test
    public void runManyNonPersistentJobs() throws Exception {
        JobManager jobManager = new JobManager(Robolectric.application, "test1");
        jobManager.stop();
        int limit = 2;
        final CountDownLatch latch = new CountDownLatch(limit);
        for(int i = 0; i < limit; i++) {
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
        jobManager.addJob(0, new BaseJob() {
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
                latch.countDown();;
            }

            @Override
            protected boolean shouldReRunOnThrowable(Throwable throwable) {
                return false;
            }
        });
        latch.await(10, TimeUnit.SECONDS);
        MatcherAssert.assertThat((int) latch.getCount(), equalTo(0));
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

    @Test
    public void testCount() throws Exception {
        JobManager jobManager = new JobManager(Robolectric.application, "count"+ System.nanoTime());
        jobManager.stop();
        for(int i = 0; i < 10; i ++) {
            jobManager.addJob(0, new DummyNonPersistentJob());
            MatcherAssert.assertThat((int) jobManager.count(), equalTo(i * 2 + 1));
            jobManager.addJob(0, new DummyPersistentJob());
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
        BaseJob[] jobs = new BaseJob[] {new DummyLatchJob(new CountDownLatch(1)), new DummyPersistentJob()};
        for(BaseJob job :jobs) {
            jobManager.addJob(0, job);
        }

        for(int i = 0; i <jobs.length; i++) {
            JobHolder jobHolder = jobManager.getNextJob();
            Long holderSessionId = Reflection.field("runningSessionId").ofType(Long.class).in(jobHolder).get();
            MatcherAssert.assertThat(holderSessionId, equalTo(sessionId));
        }
    }

    private JobManager createNewJobManager(String id) {
        return new JobManager(Robolectric.application, id);
    }

    private JobManager createNewJobManager() {
        return createNewJobManager("_" + System.nanoTime());
    }


    @Test
    public void testReRunWithLimit() throws Exception {
        JobManager jobManager = createNewJobManager();
        DummyJobWithRunCount.runCount = 0;//reset
        testReRun(jobManager, false);
        testReRun(jobManager, true);
    }

    private void testReRun(JobManager jobManager, boolean persist) throws InterruptedException {
        DummyJobWithRunCount job = new DummyJobWithRunCount(persist);
        jobManager.addJob(0, job);
        int limit = 5;
        while(limit -- > 0 && DummyJobWithRunCount.runCount != 5) {
            Thread.sleep(500);
        }
        MatcherAssert.assertThat(DummyJobWithRunCount.runCount, equalTo(job.getRetryLimit()));
    }

    private static class DummyPersistentLatchJob extends BaseJob implements Serializable {

        @Override
        public void onAdded() {

        }

        @Override
        public void onRun() throws Throwable {
            MainRoboTest.persistentRunLatch.countDown();
        }

        @Override
        public boolean shouldPersist() {
            return true;
        }

        @Override
        protected void onCancel() {

        }

        @Override
        protected boolean shouldReRunOnThrowable(Throwable throwable) {
            return false;
        }
    }

    private static class DummyLatchJob extends BaseJob {
        private final CountDownLatch latch;

        private DummyLatchJob(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public void onAdded() {

        }

        @Override
        public void onRun() throws Throwable {
            latch.countDown();
        }

        @Override
        public boolean shouldPersist() {
            return false;
        }

        @Override
        protected void onCancel() {

        }

        @Override
        protected boolean shouldReRunOnThrowable(Throwable throwable) {
            return false;
        }
    }

    public static class DummyPersistentJob extends BaseJob {

        @Override
        public void onAdded() {

        }

        @Override
        public void onRun() throws Throwable {

        }

        @Override
        public boolean shouldPersist() {
            return true;
        }

        @Override
        protected void onCancel() {

        }

        @Override
        protected boolean shouldReRunOnThrowable(Throwable throwable) {
            return false;
        }
    }

    public static class DummyNonPersistentJob extends BaseJob {

        @Override
        public void onAdded() {

        }

        @Override
        public void onRun() throws Throwable {

        }

        @Override
        public boolean shouldPersist() {
            return false;
        }

        @Override
        protected void onCancel() {

        }

        @Override
        protected boolean shouldReRunOnThrowable(Throwable throwable) {
            return false;
        }
    }


    private static class DummyJobWithRunCount extends BaseJob {
        private static int runCount;
        private boolean persist;

        private DummyJobWithRunCount(boolean persist) {
            this.persist = persist;
        }

        @Override
        public void onAdded() {

        }

        @Override
        public void onRun() throws Throwable {
            runCount ++;
            throw new RuntimeException("i am dummy, i throw exception when running");
        }

        @Override
        public boolean shouldPersist() {
            return persist;
        }

        @Override
        protected void onCancel() {

        }

        private int getRunCount() {
            return runCount;
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

}
