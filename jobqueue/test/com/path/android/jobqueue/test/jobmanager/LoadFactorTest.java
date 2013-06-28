package com.path.android.jobqueue.test.jobmanager;

import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.executor.JobConsumerExecutor;
import com.path.android.jobqueue.test.jobs.DummyJob;
import org.hamcrest.MatcherAssert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;

@RunWith(RobolectricTestRunner.class)
public class LoadFactorTest extends JobManagerTestBase {
    @Test
    public void testLoadFactor() throws Exception {
        //test adding zillions of jobs from the same group and ensure no more than 1 thread is created
        int maxConsumerCount = 2;
        int loadFactor = 5;
        JobManager jobManager = createJobManager(JobManager.createDefaultConfiguration()
                .maxConsumerCount(maxConsumerCount)
                .loadFactor(loadFactor));
        JobConsumerExecutor consumerExecutor = getConsumerExecutor(jobManager);
        org.fest.reflect.field.Invoker<AtomicInteger> activeConsumerCnt = getActiveConsumerCount(consumerExecutor);
        Object runLock = new Object();
        Semaphore semaphore = new Semaphore(maxConsumerCount);
        int totalJobCount = loadFactor * maxConsumerCount * 5;
        List<DummyJob> runningJobs = new ArrayList<DummyJob>(totalJobCount);
        for(int i = 0; i < totalJobCount; i ++) {
            DummyJob job = new NeverEndingDummyJob(runLock, semaphore);
            runningJobs.add(job);
            jobManager.addJob((int)(Math.random() * 3), job);

            int expectedConsumerCount = Math.min(maxConsumerCount, (int)Math.ceil((float)i / loadFactor));
            //wait till enough jobs start
            long now = System.nanoTime();
            long waitTill = now + TimeUnit.SECONDS.toNanos(10);
            while(System.nanoTime() < waitTill) {
                if(semaphore.availablePermits() == maxConsumerCount - expectedConsumerCount) {
                    //enough # of jobs started
                    break;
                }
            }
            if(i < loadFactor) {
                //make sure there is only 1 job running
                MatcherAssert.assertThat("while below load factor, active consumer count should be <= 1",
                        activeConsumerCnt.get().get() <= 1, is(true));
            }
            if(i > loadFactor) {
                //make sure there is only 1 job running
                MatcherAssert.assertThat("while above load factor. there should be more job consumers",
                        activeConsumerCnt.get().get(), equalTo(expectedConsumerCount));
            }
        }

        //finish all jobs
        long now = System.nanoTime();
        long waitTill = now + TimeUnit.SECONDS.toNanos(10);
        while(System.nanoTime() < waitTill) {
            synchronized (runLock) {
                runLock.notifyAll();
            }
            long totalRunningCount = 0;
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
