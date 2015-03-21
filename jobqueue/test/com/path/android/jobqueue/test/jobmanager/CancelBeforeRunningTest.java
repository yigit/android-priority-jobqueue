package com.path.android.jobqueue.test.jobmanager;

import com.path.android.jobqueue.CancelResult;
import com.path.android.jobqueue.Job;
import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.Params;
import com.path.android.jobqueue.TagConstraint;
import com.path.android.jobqueue.test.jobs.DummyJob;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class CancelBeforeRunningTest extends JobManagerTestBase {
    @Test
    public void testCancelBeforeRunning() {
        JobManager jobManager = createJobManager();
        jobManager.stop();
        DummyJob nonPersistentJob = new DummyJob(new Params(0).addTags("dummyTag"));
        DummyJob persistentJob = new DummyJob(new Params(0).addTags("dummyTag").persist());

        jobManager.addJob(nonPersistentJob);
        jobManager.addJob(persistentJob);
        CancelResult result = jobManager.cancelJobs(TagConstraint.ANY, "dummyTag");
        assertThat("both jobs should be cancelled", result.getCancelledJobs().size(), is(2));
        assertThat("both jobs should be cancelled", result.getFailedToCancel().size(), is(0));
        for (Job j : result.getCancelledJobs()) {
            DummyJob job = (DummyJob) j;
            if (!job.isPersistent()) {
                assertThat("job is still added", job.getOnAddedCnt(), is(1));
            }
            assertThat("job is cancelled", job.getOnCancelCnt(), is(1));
            assertThat("job is NOT run", job.getOnRunCnt(), is(0));
        }
    }
}
