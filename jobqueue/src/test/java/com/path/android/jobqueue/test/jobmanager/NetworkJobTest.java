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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = com.path.android.jobqueue.BuildConfig.class)
public class NetworkJobTest extends JobManagerTestBase {
    @Test
    public void testNetworkJob() throws Exception {
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
                    MatcherAssert.assertThat("should be a no network job", job.requiresNetwork(), is(false));
                    noNetworkLatch.countDown();
                    if (noNetworkLatch.getCount() == 0) {
                        jobManager.removeCallback(this);
                    }
                }
            }
        });
        jobManager.start();
        noNetworkLatch.await(1, TimeUnit.MINUTES);
        MatcherAssert.assertThat("no network jobs should be executed even if there is no network", jobManager.count(), equalTo(2));
        final CountDownLatch networkLatch = new CountDownLatch(2);
        jobManager.addCallback(new JobManagerCallbackAdapter() {
            @Override
            public void onAfterJobRun(Job job, int resultCode) {
                if (resultCode == JobManagerCallback.RESULT_SUCCEED) {
                    MatcherAssert.assertThat("should be a network job", job.requiresNetwork(), is(true));
                    networkLatch.countDown();
                    if (networkLatch.getCount() == 0) {
                        jobManager.removeCallback(this);
                    }
                }
            }
        });
        dummyNetworkUtil.setHasNetwork(true);
        networkLatch.await(1, TimeUnit.MINUTES);
        MatcherAssert.assertThat("when network is recovered, all network jobs should be automatically consumed", jobManager.count(), equalTo(0));
    }
}
