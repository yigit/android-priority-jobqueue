package com.path.android.jobqueue.test.jobmanager;

import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.Params;
import com.path.android.jobqueue.config.Configuration;
import com.path.android.jobqueue.test.jobs.DummyJob;
import static org.hamcrest.CoreMatchers.*;
import org.hamcrest.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
public class KeepAliveTest extends JobManagerTestBase {
    @Test
    public void testKeepAlive() throws Exception {
        int keepAlive = 3 + (int)(Math.random() * 5);
        DummyNetworkUtil networkUtilWithoutEventSupport = new DummyNetworkUtil();
        DummyNetworkUtilWithConnectivityEventSupport networkUtilWithEventSupport = new DummyNetworkUtilWithConnectivityEventSupport();
        JobManager jobManager1 = createJobManager(new Configuration.Builder(Robolectric.application)
                .consumerKeepAlive(keepAlive).networkUtil(networkUtilWithoutEventSupport));
        JobManager jobManager2 = createJobManager(new Configuration.Builder(Robolectric.application)
                .consumerKeepAlive(keepAlive)
                .networkUtil(networkUtilWithEventSupport));
        //give it a little time to create first consumer
        jobManager1.addJob(new DummyJob(new Params(0)));
        jobManager2.addJob(new DummyJob(new Params(0)));
        AtomicInteger activeThreadCount1 = getActiveConsumerCount(getConsumerExecutor(jobManager1)).get();
        AtomicInteger activeThreadCount2 = getActiveConsumerCount(getConsumerExecutor(jobManager2)).get();

        Thread.sleep(1000);
        MatcherAssert.assertThat("there should be 1 thread  actively waiting for jobs",
                activeThreadCount1.get(), equalTo(1));
        MatcherAssert.assertThat("there should be one thread actively waiting for jobs",
                activeThreadCount2.get(), equalTo(1));
        //sleep till it dies
        Thread.sleep((long) (TimeUnit.SECONDS.toMillis(keepAlive) * 1.33));
        MatcherAssert.assertThat("after keep alive timeout, there should NOT be any threads waiting",
                activeThreadCount1.get(), equalTo(0));
        MatcherAssert.assertThat("after keep alive timeout, there should NOT be any threads waiting",
                activeThreadCount2.get(), equalTo(0));


        //disable network and add a network bound job
        networkUtilWithoutEventSupport.setHasNetwork(false);
        networkUtilWithEventSupport.setHasNetwork(false, true);
        jobManager1.addJob(new DummyJob(new Params(0).requireNetwork()));
        jobManager2.addJob(new DummyJob(new Params(0).requireNetwork()));
        Thread.sleep(1000 + (long) (TimeUnit.SECONDS.toMillis(keepAlive) * 2));
        MatcherAssert.assertThat("when network changes cannot be detected, there should be a consumer waiting alive",
                activeThreadCount1.get(), equalTo(1));
        MatcherAssert.assertThat("when network changes can be detected, there should not be a consumer waiting alive",
                activeThreadCount2.get(), equalTo(0));
        networkUtilWithEventSupport.setHasNetwork(true, true);
        networkUtilWithoutEventSupport.setHasNetwork(true);
        Thread.sleep(500);
        MatcherAssert.assertThat("when network is recovered, job should be handled",
                jobManager2.count(), equalTo(0));
        Thread.sleep(1000);
        MatcherAssert.assertThat("when network is recovered, job should be handled",
                jobManager1.count(), equalTo(0));


    }

}
