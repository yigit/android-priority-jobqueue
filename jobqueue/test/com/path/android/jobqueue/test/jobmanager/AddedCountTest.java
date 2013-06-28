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
public class AddedCountTest extends JobManagerTestBase {
    @Test
    public void testAddedCount() throws Exception {
        testAddedCount(new DummyJob());
        testAddedCount(new PersistentDummyJob());

    }

    private void testAddedCount(DummyJob dummyJob) {
        JobManager jobManager = createJobManager();
        jobManager.stop();
        jobManager.addJob(0, dummyJob);
        MatcherAssert.assertThat(1, equalTo(dummyJob.getOnAddedCnt()));
    }
}
