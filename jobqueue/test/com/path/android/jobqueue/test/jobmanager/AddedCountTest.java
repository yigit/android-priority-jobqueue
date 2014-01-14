package com.path.android.jobqueue.test.jobmanager;

import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.Params;
import com.path.android.jobqueue.test.jobs.DummyJob;
import org.hamcrest.MatcherAssert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.hamcrest.CoreMatchers.equalTo;

@RunWith(RobolectricTestRunner.class)
public class AddedCountTest extends JobManagerTestBase {
    @Test
    public void testAddedCount() throws Exception {
        testAddedCount(new DummyJob(new Params(0)));
        testAddedCount(new DummyJob(new Params(0).persist()));

    }

    private void testAddedCount(DummyJob dummyJob) {
        JobManager jobManager = createJobManager();
        jobManager.stop();
        jobManager.addJob(dummyJob);
        MatcherAssert.assertThat(1, equalTo(dummyJob.getOnAddedCnt()));
    }
}
