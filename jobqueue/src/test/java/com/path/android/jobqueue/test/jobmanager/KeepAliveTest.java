package com.path.android.jobqueue.test.jobmanager;

import com.path.android.jobqueue.Job;
import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.Params;
import com.path.android.jobqueue.config.Configuration;
import com.path.android.jobqueue.test.jobs.DummyJob;
import static org.hamcrest.CoreMatchers.*;
import org.hamcrest.*;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = com.path.android.jobqueue.BuildConfig.class)
public class KeepAliveTest extends JobManagerTestBase {
    @Test
    public void testKeepAlive() throws Exception {
        int keepAlive = 3 + (int)(Math.random() * 5);
        final DummyNetworkUtil networkUtilWithoutEventSupport = new DummyNetworkUtil();
        final DummyNetworkUtilWithConnectivityEventSupport networkUtilWithEventSupport = new DummyNetworkUtilWithConnectivityEventSupport();
        JobManager jobManager1 = createJobManager(new Configuration.Builder(RuntimeEnvironment.application)
                .consumerKeepAlive(keepAlive).networkUtil(networkUtilWithoutEventSupport)
                .timer(mockTimer));
        JobManager jobManager2 = createJobManager(new Configuration.Builder(RuntimeEnvironment.application)
                .consumerKeepAlive(keepAlive)
                .networkUtil(networkUtilWithEventSupport)
                .timer(mockTimer));
        //give it a little time to create first consumer
        jobManager1.addJob(new DummyJob(new Params(0)));
        jobManager2.addJob(new DummyJob(new Params(0)));
        AtomicInteger activeThreadCount1 = getActiveConsumerCount(getConsumerExecutor(jobManager1)).get();
        AtomicInteger activeThreadCount2 = getActiveConsumerCount(getConsumerExecutor(jobManager2)).get();
        // give threads time to start
        //noinspection SLEEP_IN_CODE
        Thread.sleep(1000);
        MatcherAssert.assertThat("there should be 1 thread  actively waiting for jobs",
                activeThreadCount1.get(), equalTo(1));
        MatcherAssert.assertThat("there should be one thread actively waiting for jobs",
                activeThreadCount2.get(), equalTo(1));
        //sleep till it dies
        mockTimer.incrementMs((long) (TimeUnit.SECONDS.toMillis(keepAlive) * 1.33));
        // give threads time to stop
        //noinspection SLEEP_IN_CODE
        Thread.sleep(1000);
        MatcherAssert.assertThat("after keep alive timeout, there should NOT be any threads waiting",
                activeThreadCount1.get(), equalTo(0));
        MatcherAssert.assertThat("after keep alive timeout, there should NOT be any threads waiting",
                activeThreadCount2.get(), equalTo(0));


        //disable network and add a network bound job
        networkUtilWithoutEventSupport.setHasNetwork(false);
        networkUtilWithEventSupport.setHasNetwork(false, true);
        final DummyJob dj1 = new DummyJob(new Params(0).requireNetwork());
        jobManager1.addJob(dj1);
        final DummyJob dj2 = new DummyJob(new Params(0).requireNetwork());
        jobManager2.addJob(dj2);
        mockTimer.incrementMs(TimeUnit.SECONDS.toMillis(keepAlive) * 2);
        // give thread time to stop (hope it wont)
        //noinspection SLEEP_IN_CODE
        Thread.sleep(1000);
        MatcherAssert.assertThat("when network changes cannot be detected, there should be a consumer waiting alive",
                activeThreadCount1.get(), equalTo(1));
        MatcherAssert.assertThat("when network changes can be detected, there should not be a consumer waiting alive",
                activeThreadCount2.get(), equalTo(0));

        waitUntilAJobIsDone(jobManager1, new WaitUntilCallback() {
            @Override
            public void run() {
                networkUtilWithoutEventSupport.setHasNetwork(true);
            }

            @Override
            public void assertJob(Job job) {
                Assert.assertThat("it should be dj1", job, is((Job) dj1));
            }
        });
        waitUntilAJobIsDone(jobManager2, new WaitUntilCallback() {
            @Override
            public void run() {
                networkUtilWithEventSupport.setHasNetwork(true, true);
            }

            @Override
            public void assertJob(Job job) {
                Assert.assertThat("it should be dj2", job, is((Job) dj2));
            }
        });
        MatcherAssert.assertThat("when network is recovered, job should be handled",
                jobManager2.count(), equalTo(0));
        MatcherAssert.assertThat("when network is recovered, job should be handled",
                jobManager1.count(), equalTo(0));


    }

}
