package com.path.android.jobqueue.test.jobmanager;

import com.path.android.jobqueue.Params;
import com.path.android.jobqueue.config.Configuration;
import com.path.android.jobqueue.executor.JobConsumerExecutor;
import com.path.android.jobqueue.log.CustomLogger;
import com.path.android.jobqueue.test.jobs.DummyJob;
import static org.hamcrest.CoreMatchers.*;
import org.hamcrest.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
public class LoadFactorTest extends JobManagerTestBase {
    @Test
    public void testLoadFactor() throws Exception {
        //test adding zillions of jobs from the same group and ensure no more than 1 thread is created
        int maxConsumerCount = 5;
        int minConsumerCount = 2;
        int loadFactor = 5;
        com.path.android.jobqueue.JobManager jobManager = createJobManager(new Configuration.Builder(Robolectric.application)
                .maxConsumerCount(maxConsumerCount)
                .minConsumerCount(minConsumerCount)
                .customLogger(new CustomLogger() {
                    public boolean isDebugEnabled() {return true;}
                    public void d(String text, Object... args) {System.out.println(String.format(text, args));}
                    public void e(Throwable t, String text, Object... args) {t.printStackTrace(); System.out.println(String.format(text, args));}
                    public void e(String text, Object... args) {System.out.println(String.format(text, args));}
                })
                .loadFactor(loadFactor));
        JobConsumerExecutor consumerExecutor = getConsumerExecutor(jobManager);
        org.fest.reflect.field.Invoker<AtomicInteger> activeConsumerCnt = getActiveConsumerCount(consumerExecutor);
        Object runLock = new Object();
        Semaphore semaphore = new Semaphore(maxConsumerCount);
        int totalJobCount = loadFactor * maxConsumerCount * 5;
        List<DummyJob> runningJobs = new ArrayList<DummyJob>(totalJobCount);
        for(int i = 0; i < totalJobCount; i ++) {
            DummyJob job = new NeverEndingDummyJob(new Params((int)(Math.random() * 3)), runLock, semaphore);
            runningJobs.add(job);
            jobManager.addJob(job);

            int expectedConsumerCount = Math.min(maxConsumerCount, (int)Math.ceil((float)(i+1) / loadFactor));
            if(i >= minConsumerCount) {
                expectedConsumerCount = Math.max(minConsumerCount, expectedConsumerCount);
            }
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
                //make sure there is only min job running
                MatcherAssert.assertThat("while below load factor, active consumer count should be = min",
                        activeConsumerCnt.get().get(), equalTo(Math.min(i + 1, minConsumerCount)));
            }
            if(i > loadFactor) {
                //make sure there is only 1 job running
                MatcherAssert.assertThat("while above load factor. there should be more job consumers. i=" + i,
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
