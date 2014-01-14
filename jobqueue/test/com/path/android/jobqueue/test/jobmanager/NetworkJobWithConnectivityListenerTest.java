package com.path.android.jobqueue.test.jobmanager;

import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.Params;
import com.path.android.jobqueue.config.Configuration;
import com.path.android.jobqueue.test.jobs.DummyJob;
import org.hamcrest.MatcherAssert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;

import static org.hamcrest.CoreMatchers.equalTo;

@RunWith(RobolectricTestRunner.class)
public class NetworkJobWithConnectivityListenerTest extends JobManagerTestBase {
    @Test
    public void testNetworkJobWithConnectivityListener() throws Exception {
        DummyNetworkUtilWithConnectivityEventSupport dummyNetworkUtil = new DummyNetworkUtilWithConnectivityEventSupport();
        JobManager jobManager = createJobManager(new Configuration.Builder(Robolectric.application).networkUtil(dummyNetworkUtil));
        dummyNetworkUtil.setHasNetwork(false, true);
        DummyJob dummyJob = new DummyJob(new Params(0).requireNetwork());
        long dummyJobId = jobManager.addJob(dummyJob);
        Thread.sleep(2000);//sleep a while so that consumers die. they should die since we are using a network util
        //with event support
        MatcherAssert.assertThat("count should be 1 as no jobs should be consumed w/o network", jobManager.count(), equalTo(1));
        dummyNetworkUtil.setHasNetwork(true, false);
        Thread.sleep(1000); //wait a little bit more to consumer will run
        MatcherAssert.assertThat("even though network is recovered, job manager should not consume any job because it " +
                "does not know (we did not inform)", jobManager.count(), equalTo(1));
        dummyNetworkUtil.setHasNetwork(true, true);
        Thread.sleep(1000); //wait a little bit more to consumer will run
        MatcherAssert.assertThat("job manager should consume network job after it is informed that network is recovered"
                , jobManager.count(), equalTo(0));
    }
}
