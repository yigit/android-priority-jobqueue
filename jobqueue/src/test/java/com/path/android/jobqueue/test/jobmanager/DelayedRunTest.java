package com.path.android.jobqueue.test.jobmanager;

import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.Params;
import com.path.android.jobqueue.config.Configuration;
import com.path.android.jobqueue.test.jobs.DummyJob;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.*;
import org.hamcrest.*;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = com.path.android.jobqueue.BuildConfig.class)
public class DelayedRunTest extends JobManagerTestBase {
    @Test
    public void testDelayedRun() throws Exception {
        testDelayedRun(false, false);
        testDelayedRun(true, false);
        testDelayedRun(false, true);
        testDelayedRun(true, true);
    }

    @Test
    public void testDelayWith0Consumers() throws InterruptedException {
        JobManager jobManager = createJobManager(
                new Configuration.Builder(RuntimeEnvironment.application)
                        .minConsumerCount(0)
                        .maxConsumerCount(3));
        final CountDownLatch latch = new CountDownLatch(1);
        DummyJob dummyJob = new DummyJob(new Params(0).delayInMs(2000)) {
            @Override
            public void onRun() throws Throwable {
                super.onRun();
                latch.countDown();
            }
        };
        jobManager.addJob(dummyJob);
        assertThat("job should run in 3 seconds", latch.await(3, TimeUnit.DAYS),
                is(true));
    }

    public void testDelayedRun(boolean persist, boolean tryToStop) throws Exception {
        JobManager jobManager = createJobManager();
        DummyJob delayedJob = new DummyJob(new Params(10).delayInMs(2000).setPersistent(persist));
        DummyJob nonDelayedJob = new DummyJob(new Params(0).setPersistent(persist));
        jobManager.addJob(delayedJob);
        jobManager.addJob(nonDelayedJob);
        Thread.sleep(500);
        MatcherAssert.assertThat("there should be 1 delayed job waiting to be run", jobManager.count(), equalTo(1));
        if(tryToStop) {//see issue #11
            jobManager.stop();
            Thread.sleep(3000);
            MatcherAssert.assertThat("there should still be 1 delayed job waiting to be run when job manager is stopped",
                    jobManager.count(), equalTo(1));
            jobManager.start();
        }
        Thread.sleep(3000);
        MatcherAssert.assertThat("all jobs should be completed", jobManager.count(), equalTo(0));
    }
}
