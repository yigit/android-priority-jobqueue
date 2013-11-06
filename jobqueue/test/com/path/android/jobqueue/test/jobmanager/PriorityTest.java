package com.path.android.jobqueue.test.jobmanager;

import com.path.android.jobqueue.BaseJob;
import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.config.Configuration;
import static org.hamcrest.CoreMatchers.*;
import org.hamcrest.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
public class PriorityTest extends JobManagerTestBase {
    private static CountDownLatch priorityRunLatch;

    @Test
    public void testPriority() throws Exception {
        JobManager jobManager = createJobManager(new Configuration.Builder(Robolectric.application).maxConsumerCount(1));
        testPriority(jobManager, false);
    }

    public void testPriority(JobManager jobManager, boolean persist) throws Exception {
        priorityRunLatch = new CountDownLatch(2);
        DummyJobWithRunOrderAssert.globalRunCount = new AtomicInteger(0);
        BaseJob job1 = new DummyJobWithRunOrderAssert(2, persist);
        BaseJob job2 = new DummyJobWithRunOrderAssert(1, persist);
        jobManager.stop();
        jobManager.addJob(1, job1);
        jobManager.addJob(2, job2);
        jobManager.start();
        priorityRunLatch.await(4, TimeUnit.SECONDS);
        //ensure both jobs did run
        MatcherAssert.assertThat((int) priorityRunLatch.getCount(), equalTo(0));
    }

    public static class DummyJobWithRunOrderAssert extends BaseJob {
        transient public static AtomicInteger globalRunCount;
        private int expectedRunOrder;

        public DummyJobWithRunOrderAssert(int expectedRunOrder, boolean persist) {
            super(true, persist);
            this.expectedRunOrder = expectedRunOrder;
        }

        @Override
        public void onAdded() {
        }

        @Override
        public void onRun() throws Throwable {
            final int cnt = globalRunCount.incrementAndGet();
            MatcherAssert.assertThat(expectedRunOrder, equalTo(cnt));
            priorityRunLatch.countDown();
        }

        @Override
        protected void onCancel() {

        }

        @Override
        protected boolean shouldReRunOnThrowable(Throwable throwable) {
            return false;
        }
    }
}
