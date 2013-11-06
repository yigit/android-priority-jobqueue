package com.path.android.jobqueue.test.jobmanager;

import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.config.Configuration;
import com.path.android.jobqueue.test.jobs.DummyJob;
import com.path.android.jobqueue.test.jobs.PersistentDummyJob;
import org.hamcrest.MatcherAssert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;

import static org.hamcrest.CoreMatchers.equalTo;

@RunWith(RobolectricTestRunner.class)
public class NetworkJobTest extends JobManagerTestBase {
    @Test
    public void testNetworkJob() throws Exception {
        JobManagerTestBase.DummyNetworkUtil dummyNetworkUtil = new JobManagerTestBase.DummyNetworkUtil();
        JobManager jobManager = createJobManager(new Configuration.Builder(Robolectric.application).networkUtil(dummyNetworkUtil));
        jobManager.stop();

        DummyJob networkDummyJob = new DummyJob(true, false);
        jobManager.addJob(5, networkDummyJob);

        DummyJob noNetworkDummyJob = new DummyJob(false, false);
        jobManager.addJob(2, noNetworkDummyJob);

        PersistentDummyJob networkPersistentJob = new PersistentDummyJob(true);
        jobManager.addJob(6, networkPersistentJob);

        PersistentDummyJob noNetworkPersistentJob = new PersistentDummyJob(false);
        jobManager.addJob(1, noNetworkPersistentJob);

        MatcherAssert.assertThat("count should be correct if there are network and non-network jobs w/o network", jobManager.count(), equalTo(4));
        dummyNetworkUtil.setHasNetwork(true);
        MatcherAssert.assertThat("count should be correct if there is network and non-network jobs w/o network", jobManager.count(), equalTo(4));
        dummyNetworkUtil.setHasNetwork(false);
        jobManager.start();
        Thread.sleep(1000);//this should be enough to consume dummy jobs
        MatcherAssert.assertThat("no network jobs should be executed even if there is no network", jobManager.count(), equalTo(2));
        dummyNetworkUtil.setHasNetwork(true);
        Thread.sleep(1000);//this should be enough to consume dummy jobs
        MatcherAssert.assertThat("when network is recovered, all network jobs should be automatically consumed", jobManager.count(), equalTo(0));
    }
}
