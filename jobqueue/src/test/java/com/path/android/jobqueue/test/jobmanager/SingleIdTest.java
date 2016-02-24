package com.path.android.jobqueue.test.jobmanager;

import com.path.android.jobqueue.JobHolder;
import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.Params;
import com.path.android.jobqueue.test.jobs.DummyJob;

import org.fest.reflect.method.Invoker;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = com.path.android.jobqueue.BuildConfig.class)
public class SingleIdTest extends JobManagerTestBase {

    @Test
    public void testSingleIdPersistent() throws Exception {
        testSingleId(true);
    }

    @Test
    public void testSingleIdNonPersistent() throws Exception {
        testSingleId(false);
    }

    private void testSingleId(boolean persistent) {
        JobManager jobManager = createJobManager();
        Invoker<JobHolder> nextJobMethod = getNextJobMethod(jobManager);
        jobManager.stop();
        String singleId = "forks";

        DummyJob dummyJob1 = new DummyJob(new Params(0).setPersistent(persistent).setSingleId(singleId));
        long jobId1 = jobManager.addJob(dummyJob1);

        DummyJob dummyJob2 = new DummyJob(new Params(0).setPersistent(persistent));
        long jobId2 = jobManager.addJob(dummyJob2);
        assertThat("should get a new id if doesn't have singleId", jobId2, is(not(jobId1)));

        DummyJob dummyJob3 = new DummyJob(new Params(0).setPersistent(persistent).setSingleId("otherId"));
        long jobId3 = jobManager.addJob(dummyJob3);
        assertThat("should get a new id if different singleId", jobId3, is(not(jobId1)));

        DummyJob dummyJob4 = new DummyJob(new Params(0).setPersistent(persistent).setSingleId(singleId));
        long jobId4 = jobManager.addJob(dummyJob4);
        assertThat("should get same id with same singleId", jobId4, is(jobId1));

        assertThat("should return the same id", nextJobMethod.invoke().getId(), is(jobId4));
        assertThat("should return the same id", nextJobMethod.invoke().getId(), is(jobId2));
        assertThat("should return the same id", nextJobMethod.invoke().getId(), is(jobId3));
        assertThat("should return the same id", nextJobMethod.invoke(), is(nullValue()));
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
        long jobId1 = jobManager.addJob(dummyJob1);
        latchRunning.await(5, TimeUnit.SECONDS); //let job1 start running
        jobManager.stop();

        DummyJob dummyJob2 = new DummyJob(new Params(0).setPersistent(persistent).setSingleId(singleId).setGroupId(singleId));
        long jobId2 = jobManager.addJob(dummyJob2);
        assertThat("should get a new id if first job is running", jobId2, is(not(jobId1)));

        DummyJob dummyJob3 = new DummyJob(new Params(0).setPersistent(persistent).setSingleId(singleId).setGroupId(singleId));
        long jobId3 = jobManager.addJob(dummyJob3);
        assertThat("should get same id with same singleId if already queued", jobId3, is(jobId2));

        latchWait.countDown();//let job1 finish
        jobManager.start();
        busyDrain(jobManager, 2);
        DummyJob dummyJob4 = new DummyJob(new Params(0).setPersistent(persistent).setSingleId(singleId).setGroupId(singleId));
        long jobId4 = jobManager.addJob(dummyJob4);
        assertThat("should get new id if all have run", jobId4, is(not(jobId2)));
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
}
