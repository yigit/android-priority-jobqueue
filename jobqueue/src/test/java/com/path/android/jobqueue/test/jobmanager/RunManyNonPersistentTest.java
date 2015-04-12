package com.path.android.jobqueue.test.jobmanager;

import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.Params;
import static org.hamcrest.CoreMatchers.*;
import org.hamcrest.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
public class RunManyNonPersistentTest extends JobManagerTestBase {
    @Test
    public void runManyNonPersistentJobs() throws Exception {
        JobManager jobManager = createJobManager();
        jobManager.stop();
        int limit = 2;
        final CountDownLatch latch = new CountDownLatch(limit);
        for (int i = 0; i < limit; i++) {
            jobManager.addJob(new DummyLatchJob(new Params(i), latch));
        }
        jobManager.start();
        latch.await(10, TimeUnit.SECONDS);
        MatcherAssert.assertThat((int) latch.getCount(), equalTo(0));
    }
}
