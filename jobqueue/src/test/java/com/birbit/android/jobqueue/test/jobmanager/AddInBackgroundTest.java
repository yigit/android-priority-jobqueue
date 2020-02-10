package com.birbit.android.jobqueue.test.jobmanager;

import com.birbit.android.jobqueue.JobManager;
import com.birbit.android.jobqueue.JobManagerThreadRunnable;
import com.birbit.android.jobqueue.AsyncAddCallback;
import com.birbit.android.jobqueue.Job;
import com.birbit.android.jobqueue.JobHolder;
import com.birbit.android.jobqueue.Params;
import com.birbit.android.jobqueue.test.jobs.DummyJob;

import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

@RunWith(ParameterizedRobolectricTestRunner.class)

public class AddInBackgroundTest extends JobManagerTestBase {

    public AddInBackgroundTest(boolean delay, boolean useCallback) {
        this.delayed = delay;
        this.useCallback = useCallback;
    }

    final boolean delayed;
    final boolean useCallback;

    @ParameterizedRobolectricTestRunner.Parameters(name = "delay:{0} useCallback:{1}")
    public static List<Object[]> getParameters() {
        return Arrays.asList(
                new Object[]{false, false},
                new Object[]{false, true},
                new Object[]{true, false},
                new Object[]{true, true}
        );
    }

    @Test
    public void addInBackground() throws Throwable {
        long currentThreadId = Thread.currentThread().getId();
        final AtomicLong onAddedThreadId = new AtomicLong();
        final CountDownLatch addedLatch = new CountDownLatch(2);

        final Job dummyJob = new DummyJob(new Params(1).setDelayMs(delayed ? 1000 : 0)) {
            @Override
            public void onAdded() {
                super.onAdded();
                onAddedThreadId.set(Thread.currentThread().getId());
                addedLatch.countDown();
            }
        };
        JobManager jobManager = createJobManager();
        jobManager.stop();
        if(useCallback) {
            jobManager.addJobInBackground(dummyJob, new AsyncAddCallback() {
                @Override
                public void onAdded() {
                    addedLatch.countDown();
                }
            });
        } else {
            addedLatch.countDown();
            jobManager.addJobInBackground(dummyJob);
        }
        addedLatch.await();
        MatcherAssert.assertThat("thread ids should be different. delayed:" + delayed,
                currentThreadId, CoreMatchers.not(onAddedThreadId.get()));
        if(useCallback) {
            JobHolder holder = new JobManagerThreadRunnable<JobHolder>(jobManager) {

                @Override
                public JobHolder onRun() {
                    return findJobFromQueues(dummyJob.getId());
                }
            }.run();
            MatcherAssert.assertThat("there should be a job in the holder. id:" + dummyJob.getId()
                    +", delayed:" + delayed, holder, CoreMatchers.notNullValue());
            MatcherAssert.assertThat("id callback should have the proper id:", holder.getJob(),
                    CoreMatchers.is(dummyJob));
        }
    }
}
