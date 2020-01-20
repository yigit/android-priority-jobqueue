package com.birbit.android.jobqueue.test.jobmanager;


import com.birbit.android.jobqueue.JobHolder;
import com.birbit.android.jobqueue.JobManager;
import com.birbit.android.jobqueue.Params;
import com.birbit.android.jobqueue.test.jobs.DummyJob;

import static org.hamcrest.CoreMatchers.*;
import org.hamcrest.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)

public class DelayTest extends JobManagerTestBase {
    @Test
    public void testDelay() throws Throwable {
        testDelay(false);
    }

    @Test
    public void testDelayPersistent() throws Throwable {
        testDelay(true);
    }

    public void testDelay(boolean persist) throws Throwable {
        JobManager jobManager = createJobManager();
        jobManager.stop();
        DummyJob delayedJob = new DummyJob(new Params(10).delayInMs(1000).setPersistent(persist));
        DummyJob nonDelayedJob = new DummyJob(new Params(0).setPersistent(persist));
        jobManager.addJob(delayedJob);
        jobManager.addJob(nonDelayedJob);

        JobHolder receivedJob = nextJob(jobManager);
        MatcherAssert.assertThat("non-delayed job should be served", receivedJob, notNullValue());
        MatcherAssert.assertThat("non-delayed job should id should match",  receivedJob.getId(), equalTo(nonDelayedJob.getId()));
        removeJob(jobManager, receivedJob);
        MatcherAssert.assertThat("delayed job should not be served", nextJob(jobManager), nullValue());
        MatcherAssert.assertThat("job count should still be 1",  jobManager.count(), equalTo(1));
        mockTimer.incrementMs(500);
        MatcherAssert.assertThat("delayed job should not be served", nextJob(jobManager), nullValue());
        MatcherAssert.assertThat("job count should still be 1",  jobManager.count(), equalTo(1));
        mockTimer.incrementMs(2000);
        MatcherAssert.assertThat("job count should still be 1",  jobManager.count(), equalTo(1));
        receivedJob = nextJob(jobManager);
        MatcherAssert.assertThat("now should be able to receive the delayed job.", receivedJob, notNullValue());
        if(receivedJob != null) {
            MatcherAssert.assertThat("received job should be the delayed job", receivedJob.getId(), equalTo(delayedJob.getId()));
        }
    }
}
