package com.birbit.android.jobqueue.test.jobmanager;

import com.birbit.android.jobqueue.JobHolder;
import com.birbit.android.jobqueue.JobManager;
import com.birbit.android.jobqueue.Params;
import com.birbit.android.jobqueue.config.Configuration;
import com.birbit.android.jobqueue.network.NetworkUtil;
import com.birbit.android.jobqueue.test.jobs.DummyJob;

import org.hamcrest.MatcherAssert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;

@RunWith(RobolectricTestRunner.class)

public class NetworkNextJobTest extends JobManagerTestBase {

    @Test
    public void testNetworkNextJob() throws Throwable {
        DummyNetworkUtil dummyNetworkUtil = new DummyNetworkUtil();
        JobManager jobManager = createJobManager(
                new Configuration.Builder(RuntimeEnvironment.application)
                        .networkUtil(dummyNetworkUtil)
                        .timer(mockTimer));
        jobManager.stop();
        DummyJob dummyJob = new DummyJob(new Params(0).requireNetwork());
        jobManager.addJob(dummyJob);
        dummyNetworkUtil.setNetworkStatus(NetworkUtil.DISCONNECTED);
        MatcherAssert.assertThat("when there isn't any network, next job should return null",
                nextJob(jobManager), nullValue());
        MatcherAssert
                .assertThat("even if there is network, job manager should return correct count",
                        jobManager.count(), equalTo(1));
        dummyNetworkUtil.setNetworkStatus(NetworkUtil.METERED);
        JobHolder retrieved = nextJob(jobManager);
        MatcherAssert
                .assertThat("when network is recovered, next job should be retrieved", retrieved,
                        notNullValue());
    }
}
