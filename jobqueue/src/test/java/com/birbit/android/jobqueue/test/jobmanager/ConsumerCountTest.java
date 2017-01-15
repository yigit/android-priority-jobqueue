package com.birbit.android.jobqueue.test.jobmanager;

import com.birbit.android.jobqueue.JobManager;
import com.birbit.android.jobqueue.Params;
import com.birbit.android.jobqueue.config.Configuration;
import com.birbit.android.jobqueue.test.jobs.DummyJob;
import com.birbit.android.jobqueue.timer.SystemTimer;
import com.birbit.android.jobqueue.timer.Timer;

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

@RunWith(RobolectricTestRunner.class)
@Config(constants = com.birbit.android.jobqueue.BuildConfig.class)
public class ConsumerCountTest extends JobManagerTestBase {
    final CountDownLatch runLock = new CountDownLatch(1);
    @Test
    public void testMaxConsumerCount() throws Exception {
        int maxConsumerCount = 2;
        JobManager jobManager = createJobManager(
                new Configuration.Builder(RuntimeEnvironment.application)
                        .maxConsumerCount(maxConsumerCount)
                        .loadFactor(maxConsumerCount)
                        .timer(mockTimer));

        Semaphore semaphore = new Semaphore(maxConsumerCount);
        int totalJobCount = maxConsumerCount * 3;
        List<NeverEndingDummyJob> runningJobs = new ArrayList<>(totalJobCount);
        for(int i = 0; i < totalJobCount; i ++) {
            NeverEndingDummyJob job = new NeverEndingDummyJob(new Params((int)(Math.random() * 3)), runLock, semaphore);
            runningJobs.add(job);
            jobManager.addJob(job);
        }
        Timer timer = new SystemTimer();
        //wait till enough jobs start
        long start = timer.nanoTime();
        long tenSeconds = TimeUnit.SECONDS.toNanos(10);
        while (timer.nanoTime() - start < tenSeconds && semaphore.tryAcquire()) {
            semaphore.release();
            //noinspection SLEEP_IN_CODE
            Thread.sleep(100);
        }
        MatcherAssert.assertThat("all consumers should start in 10 seconds",
                timer.nanoTime() - start > tenSeconds, is(false));
        //wait some more to ensure no more jobs are started
        //noinspection SLEEP_IN_CODE
        Thread.sleep(TimeUnit.SECONDS.toMillis(3));
        int totalRunningCount = 0;
        for(DummyJob job : runningJobs) {
            totalRunningCount += job.getOnRunCnt();
        }
        MatcherAssert.assertThat("only maxConsumerCount jobs should start", totalRunningCount,
                equalTo(maxConsumerCount));
        waitUntilJobsAreDone(jobManager, runningJobs, new Runnable() {
            @Override
            public void run() {
                runLock.countDown();
            }
        });
        MatcherAssert.assertThat("no jobs should remain", jobManager.count(), equalTo(0));
    }
}
