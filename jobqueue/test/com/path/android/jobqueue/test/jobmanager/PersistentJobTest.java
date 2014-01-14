package com.path.android.jobqueue.test.jobmanager;

import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.Params;
import com.path.android.jobqueue.test.jobs.DummyJob;
import static org.hamcrest.CoreMatchers.*;
import org.hamcrest.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
public class PersistentJobTest extends JobManagerTestBase {
    //TEST parallel running
    public static CountDownLatch persistentRunLatch = new CountDownLatch(1);

    @Test
    public void testPersistentJob() throws Exception {
        JobManager jobManager = createJobManager();
        jobManager.addJob(0, new DummyPersistentLatchJob());
        persistentRunLatch.await(5, TimeUnit.SECONDS);
        MatcherAssert.assertThat((int) persistentRunLatch.getCount(), equalTo(0));
    }

    protected static class DummyPersistentLatchJob extends DummyJob {

        public DummyPersistentLatchJob() {
            super(new Params(0).persist());
        }

        @Override
        public void onRun() throws Throwable {
            PersistentJobTest.persistentRunLatch.countDown();
        }
    }
}
