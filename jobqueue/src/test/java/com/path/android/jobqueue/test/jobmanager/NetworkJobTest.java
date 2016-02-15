package com.path.android.jobqueue.test.jobmanager;

import com.path.android.jobqueue.Job;
import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.Params;
import com.path.android.jobqueue.callback.JobManagerCallback;
import com.path.android.jobqueue.callback.JobManagerCallbackAdapter;
import com.path.android.jobqueue.config.Configuration;
import com.path.android.jobqueue.test.jobs.DummyJob;
import static org.hamcrest.CoreMatchers.*;
import org.hamcrest.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

import android.annotation.TargetApi;
import android.os.Build;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = com.path.android.jobqueue.BuildConfig.class)
public class NetworkJobTest extends JobManagerTestBase {
    static CountDownLatch persistentDummyJobRunLatch;
    @Before
    public void cleanup() {
        persistentDummyJobRunLatch = new CountDownLatch(1);
    }

    @Test
    public void testNetworkJobWithTimeout() throws InterruptedException {
        JobManagerTestBase.DummyNetworkUtil dummyNetworkUtil = new JobManagerTestBase.DummyNetworkUtil();
        dummyNetworkUtil.setHasNetwork(false);
        final JobManager jobManager = createJobManager(
                new Configuration.Builder(RuntimeEnvironment.application)
                        .networkUtil(dummyNetworkUtil)
                        .timer(mockTimer));
        final CountDownLatch runLatch = new CountDownLatch(1);
        DummyJob networkDummyJob = new DummyJob(new Params(1).requireNetworkWithTimeout(4)){
            @Override
            public void onRun() throws Throwable {
                runLatch.countDown();
                super.onRun();
            }
        };
        jobManager.addJob(networkDummyJob);
        MatcherAssert.assertThat("job should not run", runLatch.await(3, TimeUnit.SECONDS),
                is(false));
        mockTimer.incrementMs(4);
        MatcherAssert.assertThat("job should run because network wait timed out",
                runLatch.await(3, TimeUnit.SECONDS), is(true));
    }

    @Test
    public void testPersistentNetworkJobWithTimeout() throws InterruptedException {
        JobManagerTestBase.DummyNetworkUtil dummyNetworkUtil = new JobManagerTestBase.DummyNetworkUtil();
        dummyNetworkUtil.setHasNetwork(false);
        final JobManager jobManager = createJobManager(
                new Configuration.Builder(RuntimeEnvironment.application)
                        .networkUtil(dummyNetworkUtil)
                        .timer(mockTimer));
        PersistentDummyJob networkDummyJob = new PersistentDummyJob(new Params(1)
                .requireNetworkWithTimeout(4));
        jobManager.addJob(networkDummyJob);
        MatcherAssert.assertThat("job should not run",
                persistentDummyJobRunLatch.await(3, TimeUnit.SECONDS), is(false));
        mockTimer.incrementMs(4);
        MatcherAssert.assertThat("job should run because network wait timed out",
                persistentDummyJobRunLatch.await(3, TimeUnit.SECONDS), is(true));
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    @Test
    public void testNetworkJob() throws Exception {
        enableDebug();
        JobManagerTestBase.DummyNetworkUtil dummyNetworkUtil = new JobManagerTestBase.DummyNetworkUtil();
        final JobManager jobManager = createJobManager(
                new Configuration.Builder(RuntimeEnvironment.application)
                        .networkUtil(dummyNetworkUtil)
                        .timer(mockTimer));
        jobManager.stop();

        DummyJob networkDummyJob = new DummyJob(new Params(5).requireNetwork());
        jobManager.addJob(networkDummyJob);

        DummyJob noNetworkDummyJob = new DummyJob(new Params(2));
        jobManager.addJob(noNetworkDummyJob);

        DummyJob networkPersistentJob = new DummyJob(new Params(6).persist().requireNetwork());
        jobManager.addJob(networkPersistentJob);

        DummyJob noNetworkPersistentJob = new DummyJob(new Params(1).persist());
        jobManager.addJob(noNetworkPersistentJob);

        MatcherAssert.assertThat("count should be correct if there are network and non-network jobs w/o network", jobManager.count(), equalTo(4));
        dummyNetworkUtil.setHasNetwork(true);
        MatcherAssert.assertThat("count should be correct if there is network and non-network jobs w/o network", jobManager.count(), equalTo(4));
        dummyNetworkUtil.setHasNetwork(false);
        final CountDownLatch noNetworkLatch = new CountDownLatch(2);
        jobManager.addCallback(new JobManagerCallbackAdapter() {
            @Override
            public void onAfterJobRun(Job job, int resultCode) {
                if (resultCode == JobManagerCallback.RESULT_SUCCEED) {
                    MatcherAssert.assertThat("should be a no network job", job.requiresNetwork(mockTimer), is(false));
                    noNetworkLatch.countDown();
                    if (noNetworkLatch.getCount() == 0) {
                        jobManager.removeCallback(this);
                    }
                }
            }
        });
        jobManager.start();
        MatcherAssert.assertThat(noNetworkLatch.await(1, TimeUnit.MINUTES), is(true));
        MatcherAssert.assertThat("no network jobs should be executed even if there is no network", jobManager.count(), equalTo(2));
        final CountDownLatch networkLatch = new CountDownLatch(2);
        jobManager.addCallback(new JobManagerCallbackAdapter() {
            @Override
            public void onAfterJobRun(Job job, int resultCode) {
                if (resultCode == JobManagerCallback.RESULT_SUCCEED) {
                    MatcherAssert.assertThat("should be a network job", job.requiresNetwork(mockTimer), is(true));
                    networkLatch.countDown();
                    if (networkLatch.getCount() == 0) {
                        jobManager.removeCallback(this);
                    }
                }
            }
        });
        dummyNetworkUtil.setHasNetwork(true);
        mockTimer.incrementMs(10000); // network check delay, make public?
        MatcherAssert.assertThat(networkLatch.await(1, TimeUnit.MINUTES), is(true));
        MatcherAssert.assertThat("when network is recovered, all network jobs should be automatically consumed", jobManager.count(), equalTo(0));
    }

    public static class PersistentDummyJob extends Job {

        public PersistentDummyJob(Params params) {
            super(params.persist());
        }

        @Override
        public void onAdded() {

        }

        @Override
        public void onRun() throws Throwable {
            persistentDummyJobRunLatch.countDown();
        }

        @Override
        protected void onCancel() {

        }
    }
}
