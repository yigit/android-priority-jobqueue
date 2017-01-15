package com.birbit.android.jobqueue.test.jobmanager;

import com.birbit.android.jobqueue.CancelResult;
import com.birbit.android.jobqueue.JobManager;
import com.birbit.android.jobqueue.Params;
import com.birbit.android.jobqueue.TagConstraint;
import com.birbit.android.jobqueue.config.Configuration;
import com.birbit.android.jobqueue.network.NetworkUtil;
import com.birbit.android.jobqueue.test.jobs.DummyJob;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(RobolectricTestRunner.class)
@Config(constants = com.birbit.android.jobqueue.BuildConfig.class)
public class CancelFailingJobsTest extends JobManagerTestBase {
    static DummyNetworkUtilWithConnectivityEventSupport networkUtil = new
            DummyNetworkUtilWithConnectivityEventSupport();

    @Test
    public void testCancelAnyAsyncWithoutNetwork() throws InterruptedException {
        testCancelWithoutNetwork(true, TagConstraint.ANY);
    }

    @Test
    public void testCancelAnySyncWithoutNetwork() throws InterruptedException {
        testCancelWithoutNetwork(false, TagConstraint.ANY);
    }

    @Test
    public void testCancelAllAsyncWithoutNetwork() throws InterruptedException {
        testCancelWithoutNetwork(true, TagConstraint.ALL);
    }

    @Test
    public void testCancelAllSyncWithoutNetwork() throws InterruptedException {
        testCancelWithoutNetwork(false, TagConstraint.ALL);
    }


    public void testCancelWithoutNetwork(boolean async, TagConstraint constraint)
            throws InterruptedException {
        final int jobCount = 30;
        JobManager jobManager = createJobManager(
                new Configuration.Builder(RuntimeEnvironment.application)
                    .minConsumerCount(5).networkUtil(networkUtil).timer(mockTimer));
        networkUtil.setNetworkStatus(NetworkUtil.DISCONNECTED, true);
        for (int i = 0; i < jobCount; i ++) {
            jobManager.addJob(new FailingJob(new Params(i).groupBy("group").addTags("tag")));
        }

        final CancelResult[] result = new CancelResult[1];
        if (async) {
            final CountDownLatch cancelLatch = new CountDownLatch(1);
            jobManager.cancelJobsInBackground(new CancelResult.AsyncCancelCallback() {
                @Override
                public void onCancelled(CancelResult cancelResult) {
                    result[0] = cancelResult;
                    cancelLatch.countDown();
                }
            }, constraint, "tag");
            assertThat(cancelLatch.await(jobCount, TimeUnit.SECONDS), is(true));
        } else {
            result[0] = jobManager.cancelJobs(TagConstraint.ANY, "tag");
        }

        assertThat("all jobs should be cancelled", result[0].getCancelledJobs().size(), is(jobCount));
        assertThat("no jobs should fail to cancel", result[0].getFailedToCancel().size(), is(0));
        final CountDownLatch runLatch = new CountDownLatch(1);
        jobManager.addJob(new DummyJob(new Params(1).groupBy("group").addTags("tag")) {
            @Override
            public void onRun() throws Throwable {
                super.onRun();
                runLatch.countDown();
            }
        });
        networkUtil.setNetworkStatus(NetworkUtil.UNMETERED, true);
        assertThat("new job should run w/o any issues", runLatch.await(2, TimeUnit.SECONDS), is(true));
    }

    @Test
    public void testCancelAnyAsyncWithoutNetworkAndPersistent() throws InterruptedException {
        testCancelWithoutNetwork(true, TagConstraint.ANY);
    }

    @Test
    public void testCancelAnySyncWithoutNetworkAndPersistent() throws InterruptedException {
        testCancelWithoutNetwork(false, TagConstraint.ANY);
    }

    @Test
    public void testCancelAllAsyncWithoutNetworkAndPersistent() throws InterruptedException {
        testCancelWithoutNetwork(true, TagConstraint.ALL);
    }

    @Test
    public void testCancelAllSyncWithoutNetworkAndPersistent() throws InterruptedException {
        testCancelWithoutNetwork(false, TagConstraint.ALL);
    }


    static CountDownLatch[] persistentLatches = new CountDownLatch[]{new CountDownLatch(1), new CountDownLatch(1),
            new CountDownLatch(1), new CountDownLatch(1)};
    static int latchCounter = 0;
    public void testCancelWithoutNetworkPersistent(boolean async, TagConstraint constraint)
            throws InterruptedException {
        JobManager jobManager = createJobManager(new Configuration.Builder(RuntimeEnvironment.application)
                .minConsumerCount(5)
                .networkUtil(networkUtil));
        networkUtil.setNetworkStatus(NetworkUtil.DISCONNECTED, true);
        jobManager.addJob(new DummyJob(new Params(1).persist().groupBy("group").addTags("tag")));
        jobManager.addJob(new DummyJob(new Params(2).persist().groupBy("group").addTags("tag")));
        jobManager.addJob(new DummyJob(new Params(3).persist().groupBy("group").addTags("tag")));
        final CancelResult[] result = new CancelResult[1];
        if (async) {
            final CountDownLatch cancelLatch = new CountDownLatch(1);
            jobManager.cancelJobsInBackground(new CancelResult.AsyncCancelCallback() {
                @Override
                public void onCancelled(CancelResult cancelResult) {
                    result[0] = cancelResult;
                    cancelLatch.countDown();
                }
            }, constraint, "tag");
            cancelLatch.await(2, TimeUnit.SECONDS);
        } else {
            result[0] = jobManager.cancelJobs(TagConstraint.ANY, "tag");
        }

        assertThat("all jobs should be canceled", result[0].getCancelledJobs().size(), is(3));
        assertThat("no jobs should fail to cancel", result[0].getFailedToCancel().size(), is(0));
        final CountDownLatch runLatch = persistentLatches[latchCounter ++];
        jobManager.addJob(new PersistentDummyJob(new Params(3).persist().groupBy("group").addTags("tag"), latchCounter - 1));
        networkUtil.setNetworkStatus(NetworkUtil.METERED, true);
        assertThat("new job should run w/o any issues", runLatch.await(2, TimeUnit.SECONDS), is(true));
    }

    public static class FailingJob extends DummyJob {
        public FailingJob(Params params) {
            super(params);
        }

        @Override
        public int getShouldReRunOnThrowableCnt() {
            return 20;
        }

        @Override
        public void onRun() throws Throwable {
            super.onRun();
            if (networkUtil.getNetworkStatus(RuntimeEnvironment.application) == NetworkUtil.DISCONNECTED) {
                //noinspection SLEEP_IN_CODE
                Thread.sleep(getCurrentRunCount() * 200);
                throw new RuntimeException("I'm bad, i crash!");
            }
        }
    }

    public static class PersistentDummyJob extends DummyJob {
        final int latch;
        public PersistentDummyJob(Params params, int latch) {
            super(params.persist());
            this.latch = latch;
        }

        @Override
        public void onRun() throws Throwable {
            super.onRun();
            if (networkUtil.getNetworkStatus(RuntimeEnvironment.application) == NetworkUtil.DISCONNECTED) {
                //noinspection SLEEP_IN_CODE
                Thread.sleep(getCurrentRunCount() * 200);
                throw new RuntimeException("I'm bad, i crash!");
            }
            persistentLatches[latch].countDown();
        }
    }
}
