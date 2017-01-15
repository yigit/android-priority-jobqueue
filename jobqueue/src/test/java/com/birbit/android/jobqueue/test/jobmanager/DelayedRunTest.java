package com.birbit.android.jobqueue.test.jobmanager;

import android.support.annotation.NonNull;

import com.birbit.android.jobqueue.Job;
import com.birbit.android.jobqueue.JobManager;
import com.birbit.android.jobqueue.Params;
import com.birbit.android.jobqueue.callback.JobManagerCallbackAdapter;
import com.birbit.android.jobqueue.config.Configuration;
import com.birbit.android.jobqueue.test.jobs.DummyJob;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.*;
import org.hamcrest.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
@Config(constants = com.birbit.android.jobqueue.BuildConfig.class)
public class DelayedRunTest extends JobManagerTestBase {
    @Test
    public void testDelayedRun() throws Exception {
        delayedRunTest(false, false);
    }

    @Test
    public void testDelayedRunPersist() throws Exception {
        delayedRunTest(true, false);
    }

    @Test
    public void testDelayedRunTryToStop() throws Exception {
        delayedRunTest(false, true);
    }

    @Test
    public void testDelayedRunPersistAndTryToStop() throws Exception {
        delayedRunTest(true, true);
    }

    @Test
    public void testDelayWith0Consumers() throws InterruptedException {
        JobManager jobManager = createJobManager(
                new Configuration.Builder(RuntimeEnvironment.application)
                        .minConsumerCount(0)
                        .maxConsumerCount(3)
                        .timer(mockTimer));
        final CountDownLatch latch = new CountDownLatch(1);
        final DummyJob dummyJob = new DummyJob(new Params(0).delayInMs(2000)) {
            @Override
            public void onRun() throws Throwable {
                super.onRun();
                latch.countDown();
            }
        };
        jobManager.addJob(dummyJob);
        mockTimer.incrementMs(1999);
        assertThat("there should not be any ready jobs", jobManager.countReadyJobs(), is(0));
        waitUntilAJobIsDone(jobManager, new WaitUntilCallback() {
            @Override
            public void run() {
                mockTimer.incrementMs(1002);
            }

            @Override
            public void assertJob(Job job) {
                assertThat("should be the dummy job", job, is((Job) dummyJob));
            }
        });
        assertThat("job should run in 3 seconds", latch.await(3, TimeUnit.NANOSECONDS),
                is(true));
    }

    public void delayedRunTest(boolean persist, boolean tryToStop) throws Exception {
        final JobManager jobManager = createJobManager();
        jobManager.addCallback(new JobManagerCallbackAdapter() {
            @Override
            public void onJobRun(@NonNull Job job, int resultCode) {
                super.onJobRun(job, resultCode);
                System.out.println("CB job run " + job.getTags().toArray()[0] + ", " + mockTimer.nanoTime());
            }

            @Override
            public void onDone(@NonNull Job job) {
                System.out.println("CB job done " + job.getTags().toArray()[0] + ", " + mockTimer.nanoTime());
            }

            @Override
            public void onAfterJobRun(@NonNull Job job, int resultCode) {
                System.out.println("CB job after run " + job.getTags().toArray()[0] + ", " + mockTimer.nanoTime());
            }
        });
        final DummyJob delayedJob = new DummyJob(new Params(10).delayInMs(2000).setPersistent(persist).addTags("delayed"));
        final DummyJob nonDelayedJob = new DummyJob(new Params(0).setPersistent(persist).addTags("notDelayed"));
        waitUntilAJobIsDone(jobManager, new WaitUntilCallback() {
            @Override
            public void run() {
                jobManager.addJob(delayedJob);
                jobManager.addJob(nonDelayedJob);
            }

            @Override
            public void assertJob(Job job) {
                assertThat("correct job should run first", (String) job.getTags().toArray()[0],
                        is("notDelayed"));
            }
        });
        MatcherAssert.assertThat("there should be 1 delayed job waiting to be run", jobManager.count(), equalTo(1));
        if(tryToStop) {//see issue #11
            jobManager.stopAndWaitUntilConsumersAreFinished();
            mockTimer.incrementMs(3000);
            MatcherAssert.assertThat("there should still be 1 delayed job waiting to be run when job manager is stopped",
                    jobManager.count(), equalTo(1));

            waitUntilAJobIsDone(jobManager, new WaitUntilCallback() {
                @Override
                public void run() {
                    jobManager.start();
                }

                @Override
                public void assertJob(Job job) {
                    assertThat("correct job should run first", (String) job.getTags().toArray()[0], is("delayed"));
                }
            });
        } else {
            waitUntilAJobIsDone(jobManager, new WaitUntilCallback() {
                @Override
                public void run() {
                    mockTimer.incrementMs(3000);
                }

                @Override
                public void assertJob(Job job) {
                    assertThat("correct job should run first", (String) job.getTags().toArray()[0], is("delayed"));
                }
            });
        }
        MatcherAssert.assertThat("all jobs should be completed", jobManager.count(), equalTo(0));
    }
}
