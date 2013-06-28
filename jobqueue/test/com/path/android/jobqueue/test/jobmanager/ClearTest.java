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
public class ClearTest extends JobManagerTestBase {
    @Test
    public void testClear() throws Exception {
        JobManager jobManager = createJobManager();
        final int LIMIT = 20;
        for(int i = 0; i < LIMIT; i++) {
            if(i % 2 == 0) {
                jobManager.addJob(0, new DummyJob());
            } else {
                jobManager.addJob(0, new PersistentDummyJob());
            }
        }
        jobManager.clear();
        MatcherAssert.assertThat("after clear, count should be 0", jobManager.count(), equalTo(0));
    }
}
