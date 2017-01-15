package com.birbit.android.jobqueue.test.jobmanager;

import com.birbit.android.jobqueue.Job;
import com.birbit.android.jobqueue.JobManager;
import com.birbit.android.jobqueue.Params;
import com.birbit.android.jobqueue.TagConstraint;
import com.birbit.android.jobqueue.WrongThreadException;
import com.birbit.android.jobqueue.test.jobs.DummyJob;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(constants = com.birbit.android.jobqueue.BuildConfig.class)
public class AssertThreadsTest extends JobManagerTestBase {
    JobManager jobManager;

    @Test
    public void testGetActiveConsumerCount() throws InterruptedException {
        assertFailure(new Runnable() {
            @Override
            public void run() {
                jobManager.getActiveConsumerCount();
            }
        });
    }

    @Test
    public void testAddJob() throws InterruptedException {
        assertFailure(new Runnable() {
            @Override
            public void run() {
                jobManager.addJob(new DummyJob(new Params(0)));
            }
        });
    }

    @Test
    public void testCancelJobs() throws InterruptedException {
        assertFailure(new Runnable() {
            @Override
            public void run() {
                jobManager.cancelJobs(TagConstraint.ANY, "blah");
            }
        });
    }

    @Test
    public void testCount() throws InterruptedException {
        assertFailure(new Runnable() {
            @Override
            public void run() {
                jobManager.count();
            }
        });
    }

    @Test
    public void testCountReady() throws InterruptedException {
        assertFailure(new Runnable() {
            @Override
            public void run() {
                jobManager.countReadyJobs();
            }
        });
    }

    @Test
    public void testJobStatus() throws InterruptedException {
        assertFailure(new Runnable() {
            @Override
            public void run() {
                jobManager.getJobStatus("abc");
            }
        });
    }

    @Test
    public void testClear() throws InterruptedException {
        assertFailure(new Runnable() {
            @Override
            public void run() {
                jobManager.clear();
            }
        });
    }

    private void assertFailure(final Runnable runnable) throws InterruptedException {
        final Throwable[] throwable = new Throwable[1];
        jobManager = createJobManager();
        final DummyJob dummyJob = new DummyJob(new Params(0)) {
            @Override
            public void onAdded() {
                super.onAdded();
                try {
                    runnable.run();
                } catch (Throwable t) {
                    throwable[0] = t;
                }
            }
        };
        waitUntilAJobIsDone(jobManager, new WaitUntilCallback() {
            @Override
            public void run() {
                jobManager.addJob(dummyJob);
            }

            @Override
            public void assertJob(Job job) {
            }
        });
        assertThat(throwable[0] instanceof WrongThreadException, is(true));
    }
}
