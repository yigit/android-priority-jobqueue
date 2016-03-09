package com.birbit.android.jobqueue.test.jobmanager;

import com.birbit.android.jobqueue.JobManager;
import com.birbit.android.jobqueue.JobStatus;
import com.birbit.android.jobqueue.Params;
import com.birbit.android.jobqueue.config.Configuration;
import com.birbit.android.jobqueue.network.NetworkUtil;
import com.birbit.android.jobqueue.test.jobs.DummyJob;

import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.List;

@RunWith(ParameterizedRobolectricTestRunner.class)
@Config(constants = com.birbit.android.jobqueue.BuildConfig.class)
public class NetworkJobWithFallbackTest extends JobManagerTestBase {
    private final boolean persistent;

    public NetworkJobWithFallbackTest(boolean persistent) {
        this.persistent = persistent;
    }

    @ParameterizedRobolectricTestRunner.Parameters(name = "persistent:{0}")
    public static List<Object[]> getParams() {
        return Arrays.asList(new Object[]{true}, new Object[]{false});
    }

    @Test
    public void testFallbackFromUnmeteredToMobile() throws InterruptedException {
        final DummyNetworkUtilWithConnectivityEventSupport networkUtil =
                new DummyNetworkUtilWithConnectivityEventSupport();
        networkUtil.setNetworkStatus(NetworkUtil.METERED);
        JobManager jobManager = createJobManager(
                new Configuration.Builder(RuntimeEnvironment.application).networkUtil(networkUtil)
                .timer(mockTimer));
        DummyJob dummyJob = new DummyJob(new Params(1).setPersistent(persistent)
                .requireUnmeteredNetworkWithTimeout(2)
                .requireNetworkWithTimeout(10));
        jobManager.addJob(dummyJob);
        //noinspection SLEEP_IN_CODE
        Thread.sleep(2000);
        MatcherAssert.assertThat(jobManager.getJobStatus(dummyJob.getId()), CoreMatchers.is(
                JobStatus.WAITING_NOT_READY));
        mockTimer.incrementMs(1);
        //noinspection SLEEP_IN_CODE
        Thread.sleep(2000);
        MatcherAssert.assertThat(jobManager.getJobStatus(dummyJob.getId()), CoreMatchers.is(
                JobStatus.WAITING_NOT_READY));
        waitUntilJobsAreDone(jobManager, Arrays.asList(dummyJob), new Runnable() {
            @Override
            public void run() {
                mockTimer.incrementMs(2);
            }
        });
    }
}
