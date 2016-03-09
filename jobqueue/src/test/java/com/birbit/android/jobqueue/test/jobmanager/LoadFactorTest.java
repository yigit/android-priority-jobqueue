package com.birbit.android.jobqueue.test.jobmanager;

import com.birbit.android.jobqueue.JobManager;
import com.birbit.android.jobqueue.Params;
import com.birbit.android.jobqueue.config.Configuration;
import com.birbit.android.jobqueue.test.jobs.DummyJob;

import static org.hamcrest.CoreMatchers.*;
import org.hamcrest.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = com.birbit.android.jobqueue.BuildConfig.class)
public class LoadFactorTest extends JobManagerTestBase {
    @Test
    public void testLoadFactor() throws Exception {
        //test adding zillions of jobs from the same group and ensure no more than 1 thread is created
        int maxConsumerCount = 5;
        int minConsumerCount = 2;
        int loadFactor = 5;
        enableDebug();
        JobManager jobManager = createJobManager(new Configuration.Builder(RuntimeEnvironment.application)
                .maxConsumerCount(maxConsumerCount)
                .minConsumerCount(minConsumerCount)
                .loadFactor(loadFactor)
                .timer(mockTimer));
        final CountDownLatch runLock = new CountDownLatch(1);
        Semaphore semaphore = new Semaphore(maxConsumerCount);
        int totalJobCount = loadFactor * maxConsumerCount * 5;
        List<DummyJob> runningJobs = new ArrayList<DummyJob>(totalJobCount);
        int prevConsumerCount = 0;
        final Semaphore onRunCount = new Semaphore(totalJobCount);
        onRunCount.acquire(totalJobCount);
        for(int i = 0; i < totalJobCount; i ++) {

            DummyJob job =
                    new NeverEndingDummyJob(new Params((int)(Math.random() * 3)),runLock, semaphore) {
                        @Override
                        public void onRun() throws Throwable {
                            onRunCount.release();
                            super.onRun();
                        }
                    };
            runningJobs.add(job);
            jobManager.addJob(job);
            final int wantedConsumers = (int) Math.ceil((i + 1f) / loadFactor);
            final int expectedConsumerCount = Math.max(Math.min(i+1, minConsumerCount),
                    Math.min(maxConsumerCount, wantedConsumers));

            if (prevConsumerCount != expectedConsumerCount) {
                MatcherAssert.assertThat("waiting for another job to start",
                        onRunCount.tryAcquire(1, 10, TimeUnit.SECONDS), is(true));
            }
            MatcherAssert.assertThat("Consumer count should match expected value at " + (i+1) + " jobs",
                    jobManager.getActiveConsumerCount(), equalTo(expectedConsumerCount));
            prevConsumerCount = expectedConsumerCount;
        }

        //finish all jobs
        waitUntilJobsAreDone(jobManager, runningJobs, new Runnable() {
            @Override
            public void run() {
                runLock.countDown();
            }
        });
        MatcherAssert.assertThat("no jobs should remain", jobManager.count(), equalTo(0));

    }
}
