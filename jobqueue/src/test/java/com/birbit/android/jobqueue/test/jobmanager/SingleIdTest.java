package com.birbit.android.jobqueue.test.jobmanager;

import com.birbit.android.jobqueue.Job;
import com.birbit.android.jobqueue.JobManager;
import com.birbit.android.jobqueue.Params;
import com.birbit.android.jobqueue.test.jobs.DummyJob;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(RobolectricTestRunner.class)
@Config(constants = com.birbit.android.jobqueue.BuildConfig.class)
public class SingleIdTest extends JobManagerTestBase {

    private String addJob(JobManager jobManager, Job job) {
        jobManager.addJob(job);
        return job.getId();
    }

    @Test
    public void testSingleIdPersistent() throws Throwable {
        testSingleId(true);
    }

    @Test
    public void testSingleIdNonPersistent() throws Throwable {
        testSingleId(false);
    }

    private void testSingleId(boolean persistent) throws Throwable {
        JobManager jobManager = createJobManager();
        jobManager.stop();
        String singleId = "forks";

        DummyJob dummyJob1 = new DummyJob(new Params(0).setPersistent(persistent).setSingleId(singleId));
        String jobId1 = addJob(jobManager, dummyJob1);

        DummyJob dummyJob2 = new DummyJob(new Params(0).setPersistent(persistent));
        String jobId2 = addJob(jobManager, dummyJob2);
        assertThat("should add job if doesn't have singleId", jobManager.countReadyJobs(), is(2));

        DummyJob dummyJob3 = new DummyJob(new Params(0).setPersistent(persistent).setSingleId("otherId"));
        String jobId3 = addJob(jobManager, dummyJob3);
        assertThat("should add job if different singleId", jobManager.countReadyJobs(), is(3));

        DummyJob dummyJob4 = new DummyJob(new Params(0).setPersistent(persistent).setSingleId(singleId));
        addJob(jobManager, dummyJob4);
        assertThat("should not add job with same singleId", jobManager.countReadyJobs(), is(3));

        assertThat("should return the same id", nextJob(jobManager).getId(), is(jobId1));
        assertThat("should return the same id", nextJob(jobManager).getId(), is(jobId2));
        assertThat("should return the same id", nextJob(jobManager).getId(), is(jobId3));
        assertThat("should return the same id", nextJob(jobManager), is(nullValue()));
    }

    @Test
    public void testSingleIdRunningPersistent() throws Exception {
        testSingleIdRunning(true);
    }

    @Test
    public void testSingleIdRunningNonPersistent() throws Exception {
        testSingleIdRunning(false);
    }

    private void testSingleIdRunning(boolean persistent) throws InterruptedException {
        JobManager jobManager = createJobManager();
        String singleId = "dorks";
        CountDownLatch latchWait = new CountDownLatch(1);
        CountDownLatch latchRunning = new CountDownLatch(1);

        DummyJob dummyJob1 = new SerializableDummyTwoLatchJob(
                new Params(0).setPersistent(persistent).setSingleId(singleId).setGroupId(singleId), latchWait, latchRunning);
        addJob(jobManager, dummyJob1);
        jobManager.start();
        latchRunning.await(5, TimeUnit.SECONDS); //let job1 start running
        jobManager.stop();
        assertThat("should not be marked ready", jobManager.count(), is(0));

        CountDownLatch latchRunning2 = new CountDownLatch(1);
        DummyJob dummyJob2 = new SerializableDummyLatchJob(new Params(0).setPersistent(persistent).setSingleId(singleId).setGroupId(singleId), latchRunning2);
        addJob(jobManager, dummyJob2);
        assertThat("should add new job if first job was running", jobManager.count(), is(1));
        DummyJob dummyJob3 = new DummyJob(new Params(0).setPersistent(persistent).setSingleId(singleId).setGroupId(singleId));
        addJob(jobManager, dummyJob3);
        assertThat("should not add new job if already queued", jobManager.count(), is(1));

        latchWait.countDown();//let job1 finish
        jobManager.start();
        assertThat("job should have run", latchRunning2.await(5, TimeUnit.SECONDS), is(true)); //wait until job2 runs
        jobManager.stopAndWaitUntilConsumersAreFinished();
        assertThat("job should not have run", dummyJob3.getOnRunCnt(), is(0));
        assertThat("should have called onCancel", dummyJob3.getOnCancelCnt(), is(1));

        DummyJob dummyJob4 = new DummyJob(new Params(0).setPersistent(persistent).setSingleId(singleId).setGroupId(singleId));
        addJob(jobManager, dummyJob4);
        assertThat("should be added if all others have run", jobManager.count(), is(1));
    }

    private static class SerializableDummyTwoLatchJob extends DummyJob {

        static CountDownLatch sLatchWait;
        static CountDownLatch sLatchRunning;

        public SerializableDummyTwoLatchJob(Params params, CountDownLatch latchWait, CountDownLatch latchRunning) {
            super(params);
            sLatchWait = latchWait;
            sLatchRunning = latchRunning;
        }

        @Override
        public void onRun() throws Throwable {
            super.onRun();
            sLatchRunning.countDown();
            sLatchWait.await();
            sLatchRunning = null;
            sLatchWait = null;
        }
    }

    private static class SerializableDummyLatchJob extends DummyJob {

        static CountDownLatch sLatchRunning;
        int onRunCnt = 0;

        public SerializableDummyLatchJob(Params params, CountDownLatch latchRunning) {
            super(params);
            sLatchRunning = latchRunning;
        }

        @Override
        public void onRun() throws Throwable {
            super.onRun();
            sLatchRunning.countDown();
            sLatchRunning = null;
        }
    }

}
