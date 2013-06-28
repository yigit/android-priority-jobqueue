package com.path.android.jobqueue.test.jobmanager;

import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.test.jobs.DummyJob;
import com.path.android.jobqueue.test.jobs.PersistentDummyJob;
import org.hamcrest.MatcherAssert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.hamcrest.CoreMatchers.equalTo;

@RunWith(RobolectricTestRunner.class)
public class DelayedRunTest extends JobManagerTestBase {
    @Test
    public void testDelayedRun() throws Exception {
        testDelayedRun(false);
        testDelayedRun(true);
    }
    public void testDelayedRun(boolean persist) throws Exception {
        JobManager jobManager = createJobManager();
        DummyJob delayedJob = persist ? new PersistentDummyJob() : new DummyJob();
        DummyJob nonDelayedJob = persist ? new PersistentDummyJob() : new DummyJob();
        jobManager.addJob(10, 2000, delayedJob);
        jobManager.addJob(0, 0, nonDelayedJob);
        Thread.sleep(500);
        MatcherAssert.assertThat("there should be 1 delayed job waiting to be run", jobManager.count(), equalTo(1));
        Thread.sleep(3000);
        MatcherAssert.assertThat("all jobs should be completed", jobManager.count(), equalTo(0));

    }
}
