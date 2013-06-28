package com.path.android.jobqueue.test.jobmanager;

import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.test.jobs.DummyJob;
import org.hamcrest.MatcherAssert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.equalTo;

@RunWith(RobolectricTestRunner.class)
public class ConsumerCountTest extends JobManagerTestBase {
    @Test
    public void testMaxConsumerCount() throws Exception {
        int maxConsumerCount = 2;
        JobManager jobManager = createJobManager(JobManager.createDefaultConfiguration()
                .maxConsumerCount(maxConsumerCount)
                .loadFactor(maxConsumerCount));
        Object runLock = new Object();
        Semaphore semaphore = new Semaphore(maxConsumerCount);
        int totalJobCount = maxConsumerCount * 3;
        List<DummyJob> runningJobs = new ArrayList<DummyJob>(totalJobCount);
        for(int i = 0; i < totalJobCount; i ++) {
            DummyJob job = new NeverEndingDummyJob(runLock, semaphore);
            runningJobs.add(job);
            jobManager.addJob((int)(Math.random() * 3), job);
        }
        //wait till enough jobs start
        long now = System.nanoTime();
        long waitTill = now + TimeUnit.SECONDS.toNanos(10);
        while(System.nanoTime() < waitTill) {
            if(semaphore.availablePermits() == 0) {
                //enough # of jobs started
                break;
            }
        }
        //wait some more to ensure no more jobs are started
        Thread.sleep(TimeUnit.SECONDS.toMillis(3));
        int totalRunningCount = 0;
        for(DummyJob job : runningJobs) {
            totalRunningCount += job.getOnRunCnt();
        }
        MatcherAssert.assertThat("only maxConsumerCount jobs should start", totalRunningCount, equalTo(maxConsumerCount));
        //try to finish all jobs
        //wait till enough jobs start
        now = System.nanoTime();
        waitTill = now + TimeUnit.SECONDS.toNanos(10);
        while(System.nanoTime() < waitTill) {
            synchronized (runLock) {
                runLock.notifyAll();
            }
            totalRunningCount = 0;
            for(DummyJob job : runningJobs) {
                totalRunningCount += job.getOnRunCnt();
            }
            if(totalJobCount == totalRunningCount) {
                //cool!
                break;
            }
        }
        MatcherAssert.assertThat("no jobs should remain", jobManager.count(), equalTo(0));

    }
}
