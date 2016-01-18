package com.path.android.jobqueue.test.jobmanager;

import com.path.android.jobqueue.Job;
import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.Params;
import com.path.android.jobqueue.config.Configuration;
import com.path.android.jobqueue.test.jobs.DummyJob;

import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

import static org.hamcrest.CoreMatchers.equalTo;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = com.path.android.jobqueue.BuildConfig.class)
public class NetworkJobWithConnectivityListenerTest extends JobManagerTestBase {
    @Test
    public void testNetworkJobWithConnectivityListener() throws Exception {
        final DummyNetworkUtilWithConnectivityEventSupport dummyNetworkUtil = new DummyNetworkUtilWithConnectivityEventSupport();
        final JobManager jobManager = createJobManager(
                new Configuration.Builder(RuntimeEnvironment.application)
                        .networkUtil(dummyNetworkUtil)
                        .timer(mockTimer));
        dummyNetworkUtil.setHasNetwork(false, true);
        final DummyJob dummyJob = new DummyJob(new Params(0).requireNetwork());
        jobManager.addJob(dummyJob);
        // no job to run so consumers should finish
        jobManager.waitUntilConsumersAreFinished();
        MatcherAssert.assertThat("count should be 1 as no jobs should be consumed w/o network", jobManager.count(), equalTo(1));
        // JobManager may wake up as idle right here and see the new network value. sleep to avoid it
        // count will trigger the queue and will result in another IDLE call. We need to wait until
        // it is handled.
        //noinspection SLEEP_IN_CODE
        Thread.sleep(2000);

        dummyNetworkUtil.setHasNetwork(true, false);
        //noinspection SLEEP_IN_CODE
        Thread.sleep(5000); //wait a little bit more to let consumer run
        MatcherAssert.assertThat("even though network is recovered, job manager should not consume any job because it " +
                "does not know (we did not inform)", jobManager.count(), equalTo(1));

        waitUntilAJobIsDone(jobManager, new WaitUntilCallback() {
            @Override
            public void run() {
                dummyNetworkUtil.setHasNetwork(true, true);
            }

            @Override
            public void assertJob(Job job) {
                MatcherAssert.assertThat("should be the added job", job, CoreMatchers.is((Job) dummyJob));
            }
        });
        MatcherAssert.assertThat("job manager should consume network job after it is informed that network is recovered"
                , jobManager.count(), equalTo(0));
    }
}
