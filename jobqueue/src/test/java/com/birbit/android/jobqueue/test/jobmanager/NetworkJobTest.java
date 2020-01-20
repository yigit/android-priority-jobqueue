package com.birbit.android.jobqueue.test.jobmanager;

import android.annotation.TargetApi;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.birbit.android.jobqueue.CancelReason;
import com.birbit.android.jobqueue.Job;
import com.birbit.android.jobqueue.JobManager;
import com.birbit.android.jobqueue.Params;
import com.birbit.android.jobqueue.RetryConstraint;
import com.birbit.android.jobqueue.callback.JobManagerCallback;
import com.birbit.android.jobqueue.callback.JobManagerCallbackAdapter;
import com.birbit.android.jobqueue.config.Configuration;
import com.birbit.android.jobqueue.network.NetworkUtil;
import com.birbit.android.jobqueue.test.jobs.DummyJob;

import org.hamcrest.MatcherAssert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;

@RunWith(ParameterizedRobolectricTestRunner.class)

public class NetworkJobTest extends JobManagerTestBase {
    final boolean unmetered;
    static CountDownLatch persistentDummyJobRunLatch;

    public NetworkJobTest(boolean unmetered) {
        this.unmetered = unmetered;
    }

    @Before
    public void cleanup() {
        persistentDummyJobRunLatch = new CountDownLatch(1);
    }

    @ParameterizedRobolectricTestRunner.Parameters(name = "unmetered:{0}")
    public static List<Object[]> getParams() {
        return Arrays.asList(new Object[]{true}, new Object[]{false});
    }

    private Params addRequirement(Params params) {
        if (unmetered) {
            return params.requireUnmeteredNetwork();
        } else {
            return params.requireNetwork();
        }
    }

    private Params addRequirement(Params params, long timeoutMs) {
        if (unmetered) {
            params.requireUnmeteredNetwork();
        } else {
            params.requireNetwork();
        }
        return params.overrideDeadlineToRunInMs(timeoutMs);
    }

    @Test
    public void testNetworkJobWithTimeout() throws InterruptedException {
        JobManagerTestBase.DummyNetworkUtil dummyNetworkUtil = new JobManagerTestBase.DummyNetworkUtil();
        dummyNetworkUtil.setNetworkStatus(NetworkUtil.DISCONNECTED);
        final JobManager jobManager = createJobManager(
                new Configuration.Builder(RuntimeEnvironment.application)
                        .networkUtil(dummyNetworkUtil)
                        .timer(mockTimer));
        final CountDownLatch runLatch = new CountDownLatch(1);
        DummyJob networkDummyJob = new DummyJob(addRequirement(new Params(1), 4)){
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
        dummyNetworkUtil.setNetworkStatus(NetworkUtil.DISCONNECTED);
        final JobManager jobManager = createJobManager(
                new Configuration.Builder(RuntimeEnvironment.application)
                        .networkUtil(dummyNetworkUtil)
                        .timer(mockTimer));
        PersistentDummyJob networkDummyJob = new PersistentDummyJob(addRequirement(new Params(1),
                4));
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

        DummyJob networkDummyJob = new DummyJob(addRequirement(new Params(5)));
        jobManager.addJob(networkDummyJob);

        DummyJob noNetworkDummyJob = new DummyJob(new Params(2));
        jobManager.addJob(noNetworkDummyJob);

        DummyJob networkPersistentJob = new DummyJob(addRequirement(new Params(6).persist()));
        jobManager.addJob(networkPersistentJob);

        DummyJob noNetworkPersistentJob = new DummyJob(new Params(1).persist());
        jobManager.addJob(noNetworkPersistentJob);

        MatcherAssert.assertThat("count should be correct if there are network and non-network jobs w/o network", jobManager.count(), equalTo(4));
        dummyNetworkUtil.setNetworkStatus(NetworkUtil.METERED);
        MatcherAssert.assertThat("count should be correct if there is network and non-network jobs w/o network", jobManager.count(), equalTo(4));
        dummyNetworkUtil.setNetworkStatus(NetworkUtil.DISCONNECTED);
        final CountDownLatch noNetworkLatch = new CountDownLatch(2);
        jobManager.addCallback(new JobManagerCallbackAdapter() {
            @Override
            public void onAfterJobRun(@NonNull Job job, int resultCode) {
                if (resultCode == JobManagerCallback.RESULT_SUCCEED) {
                    MatcherAssert.assertThat("should be a no network job", job.requiresNetwork(), is(false));
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
            public void onAfterJobRun(@NonNull Job job, int resultCode) {
                if (resultCode == JobManagerCallback.RESULT_SUCCEED) {
                    MatcherAssert.assertThat("should be a network job", job.requiresNetwork(), is(true));
                    networkLatch.countDown();
                    if (networkLatch.getCount() == 0) {
                        jobManager.removeCallback(this);
                    }
                }
            }
        });
        dummyNetworkUtil.setNetworkStatus(NetworkUtil.METERED);
        mockTimer.incrementMs(10000); // network check delay, make public?
        if (unmetered) {
            MatcherAssert.assertThat("if jobs require unmetered, they should not be run",
                    networkLatch.await(10, TimeUnit.SECONDS), is(false));
            MatcherAssert.assertThat(networkLatch.getCount(), is(2L));
            dummyNetworkUtil.setNetworkStatus(NetworkUtil.UNMETERED);
            mockTimer.incrementMs(10000); // network check delay
        }
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
        protected void onCancel(@CancelReason int cancelReason, @Nullable Throwable throwable) {

        }

        @Override
        protected RetryConstraint shouldReRunOnThrowable(@NonNull Throwable throwable, int runCount, int maxRunCount) {
            throw new RuntimeException("not expected arrive here");
        }
    }
}
