package com.path.android.jobqueue.test.jobmanager;

import com.path.android.jobqueue.Job;
import com.path.android.jobqueue.Params;
import com.path.android.jobqueue.test.jobs.DummyJob;
import org.hamcrest.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

@RunWith(RobolectricTestRunner.class)
public class AddInBackgroundTest extends JobManagerTestBase {
    @Test
    public void testAddInBackground() {
        addInBackground(false);
        addInBackground(true);

    }
    public void addInBackground(boolean delayed) {
        long currentThreadId = Thread.currentThread().getId();
        final AtomicLong onAddedThreadId = new AtomicLong();
        final CountDownLatch addedLatch = new CountDownLatch(2);
        Job dummyJob = new DummyJob(new Params(1).setDelayMs(delayed ? 1000 : 0)) {
            @Override
            public void onAdded() {
                super.onAdded();
                onAddedThreadId.set(Thread.currentThread().getId());
                addedLatch.countDown();
            }
        };
        createJobManager().addJobInBackground(dummyJob);

        addedLatch.countDown();
        MatcherAssert.assertThat("thread ids should be different. delayed:" + delayed, currentThreadId, CoreMatchers.not(onAddedThreadId.get()));
    }
}
