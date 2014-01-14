package com.path.android.jobqueue.test.jobmanager;

import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.Params;
import com.path.android.jobqueue.test.jobs.DummyJob;
import static org.hamcrest.CoreMatchers.*;
import org.hamcrest.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;

@RunWith(RobolectricTestRunner.class)
public class CountTest extends JobManagerTestBase {
    @Test
    public void testCount() throws Exception {
        JobManager jobManager = createJobManager();
        jobManager.stop();
        for (int i = 0; i < 10; i++) {
            jobManager.addJob(new DummyJob(new Params(0).persist()));
            MatcherAssert.assertThat((int) jobManager.count(), equalTo(i * 2 + 1));
            jobManager.addJob(new DummyJob(new Params(0).persist()));
            MatcherAssert.assertThat((int) jobManager.count(), equalTo(i * 2 + 2));
        }
        jobManager.start();
        Thread.sleep(2000);
        MatcherAssert.assertThat((int) jobManager.count(), equalTo(0));
    }
}
