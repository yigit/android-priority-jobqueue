package com.birbit.android.jobqueue.test.jobmanager;

import com.birbit.android.jobqueue.JobManager;
import com.birbit.android.jobqueue.Params;
import com.birbit.android.jobqueue.test.jobs.DummyJob;
import org.hamcrest.MatcherAssert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.hamcrest.CoreMatchers.equalTo;

@RunWith(RobolectricTestRunner.class)
@Config(constants = com.birbit.android.jobqueue.BuildConfig.class)
public class AddedCountTest extends JobManagerTestBase {
    @Test
    public void testAddedCount() throws Exception {
        testAddedCount(new DummyJob(new Params(0)));

    }

    @Test
    public void testAddedCountPersistent() {
        testAddedCount(new DummyJob(new Params(0).persist()));
    }

    private void testAddedCount(DummyJob dummyJob) {
        JobManager jobManager = createJobManager();
        jobManager.stop();
        jobManager.addJob(dummyJob);
        MatcherAssert.assertThat(1, equalTo(dummyJob.getOnAddedCnt()));
    }
}
