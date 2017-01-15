package com.birbit.android.jobqueue.test.jobmanager;

import com.birbit.android.jobqueue.Constraint;
import com.birbit.android.jobqueue.Job;
import com.birbit.android.jobqueue.JobHolder;
import com.birbit.android.jobqueue.JobManager;
import com.birbit.android.jobqueue.JobQueue;
import com.birbit.android.jobqueue.Params;
import com.birbit.android.jobqueue.QueueFactory;
import com.birbit.android.jobqueue.config.Configuration;
import com.birbit.android.jobqueue.inMemoryQueue.SimpleInMemoryPriorityQueue;
import com.birbit.android.jobqueue.persistentQueue.sqlite.SqliteJobQueue;
import com.birbit.android.jobqueue.test.jobs.DummyJob;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

import android.annotation.SuppressLint;
import android.support.annotation.NonNull;

import org.hamcrest.*;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
@Config(constants = com.birbit.android.jobqueue.BuildConfig.class)
public class LoadFactorTest extends JobManagerTestBase {
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch canEndLatch = new CountDownLatch(1);
    @SuppressLint("SLEEP_IN_CODE")
    @Test
    public void testGoIdleIfNextJobCannotBeRunNow() throws InterruptedException {
        // see: https://github.com/yigit/android-priority-jobqueue/issues/262
        final AtomicInteger nextJobDelayCall = new AtomicInteger(1);
        JobManager jobManager = createJobManager(new Configuration.Builder(RuntimeEnvironment.application)
                .maxConsumerCount(3)
                .minConsumerCount(1)
                .loadFactor(3)
                .queueFactory(new QueueFactory() {
                    @Override
                    public JobQueue createPersistentQueue(Configuration configuration,
                            long sessionId) {
                        return new SqliteJobQueue(configuration, sessionId, new SqliteJobQueue
                                .JavaSerializer());
                    }

                    @Override
                    public JobQueue createNonPersistent(Configuration configuration,
                            long sessionId) {
                        return new SimpleInMemoryPriorityQueue(configuration, sessionId) {
                            @Override
                            public Long getNextJobDelayUntilNs(@NonNull Constraint constraint) {
                                nextJobDelayCall.incrementAndGet();
                                return super.getNextJobDelayUntilNs(constraint);
                            }
                        };
                    }
                })
                .timer(mockTimer));
        final DummyJobWithStartEndLatch job1 = new DummyJobWithStartEndLatch(new Params(1));
        final DummyJobWithStartEndLatch job2 = new DummyJobWithStartEndLatch(new Params(1));
        jobManager.addJob(job1);
        jobManager.addJob(job2);
        assertThat(startLatch.await(5, TimeUnit.MINUTES), is(true));
        // give it some time to cool down, ugly but nothing to do
        Thread.sleep(2000);
        int startCount = nextJobDelayCall.get();
        Thread.sleep(5000);
        assertThat("JobManager should not query any more next jobs", nextJobDelayCall.get(),
                is(startCount));
        waitUntilAJobIsDone(jobManager, new WaitUntilCallback() {
            @Override
            public void run() {
                canEndLatch.countDown();
            }

            @Override
            public void assertJob(Job job) {

            }
        });
    }

    @After
    public void clearLatches() {
        canEndLatch.countDown();
    }
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
                assertThat("waiting for another job to start",
                        onRunCount.tryAcquire(1, 10, TimeUnit.SECONDS), is(true));
            }
            assertThat("Consumer count should match expected value at " + (i+1) + " jobs",
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
        assertThat("no jobs should remain", jobManager.count(), equalTo(0));
    }

    class DummyJobWithStartEndLatch extends DummyJob {
        public DummyJobWithStartEndLatch(Params params) {
            super(params);
        }

        @Override
        public void onRun() throws Throwable {
            super.onRun();
            startLatch.countDown();
            canEndLatch.await();
        }
    }
}
