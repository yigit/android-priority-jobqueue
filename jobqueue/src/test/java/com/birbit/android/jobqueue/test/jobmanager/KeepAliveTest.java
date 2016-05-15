package com.birbit.android.jobqueue.test.jobmanager;

import com.birbit.android.jobqueue.Job;
import com.birbit.android.jobqueue.JobManager;
import com.birbit.android.jobqueue.Params;
import com.birbit.android.jobqueue.callback.JobManagerCallbackAdapter;
import com.birbit.android.jobqueue.config.Configuration;
import com.birbit.android.jobqueue.network.NetworkUtil;
import com.birbit.android.jobqueue.test.jobs.DummyJob;
import static org.hamcrest.CoreMatchers.*;
import org.hamcrest.*;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

import android.annotation.TargetApi;
import android.os.Build;
import android.support.annotation.NonNull;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = com.birbit.android.jobqueue.BuildConfig.class)
public class KeepAliveTest extends JobManagerTestBase {
    @Test
    public void testKeepAlive() throws Exception {
        testKeepAlive(new DummyNetworkUtilWithConnectivityEventSupport());
    }

    @Test
    public void testKeepAliveWithoutNetworkEvents() throws Exception {
        testKeepAlive(new DummyNetworkUtil());
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    public void testKeepAlive(final DummyNetworkUtil networkUtil) throws Exception {
        int keepAlive = 3 + (int)(Math.random() * 5);
        JobManager jobManager = createJobManager(new Configuration.Builder(RuntimeEnvironment.application)
                .consumerKeepAlive(keepAlive).networkUtil(networkUtil).timer(mockTimer));
        //give it a little time to create first consumer
        final CountDownLatch jobDone = new CountDownLatch(1);
        jobManager.addCallback(new JobManagerCallbackAdapter() {
            @Override
            public void onDone(@NonNull Job job) {
                jobDone.countDown();
            }
        });
        jobManager.addJob(new DummyJob(new Params(0)));

        // Sync on job manager to ensure it handled add requests
        jobManager.count();
        MatcherAssert.assertThat("there should be 1 thread  actively waiting for jobs",
                jobManager.getActiveConsumerCount(), equalTo(1));
        jobDone.await(1, TimeUnit.MINUTES);
        //sleep till it dies
        mockTimer.incrementNs((long) (JobManager.NETWORK_CHECK_INTERVAL + TimeUnit.SECONDS.toNanos(keepAlive) * 1.33));
        // give threads time to stop
        //noinspection SLEEP_IN_CODE
        Thread.sleep(3000);

        MatcherAssert.assertThat("after keep alive timeout, there should NOT be any threads waiting",
                jobManager.getActiveConsumerCount(), equalTo(0));

        //disable network and add a network bound job
        networkUtil.setNetworkStatus(NetworkUtil.DISCONNECTED);
        final DummyJob dj1 = new DummyJob(new Params(0).requireNetwork());
        jobManager.addJob(dj1);

        mockTimer.incrementNs(JobManager.NETWORK_CHECK_INTERVAL +
                TimeUnit.SECONDS.toNanos(keepAlive) * 2);

        waitUntilAJobIsDone(jobManager, new WaitUntilCallback() {
            @Override
            public void run() {
                networkUtil.setNetworkStatus(NetworkUtil.METERED);
            }

            @Override
            public void assertJob(Job job) {
                Assert.assertThat("it should be dj1", job, is((Job) dj1));
            }
        });
        MatcherAssert.assertThat("when network is recovered, job should be handled",
                jobManager.count(), equalTo(0));


    }

}
