package com.birbit.android.jobqueue.test.jobmanager;

import android.annotation.TargetApi;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.birbit.android.jobqueue.CancelReason;
import com.birbit.android.jobqueue.Job;
import com.birbit.android.jobqueue.JobManager;
import com.birbit.android.jobqueue.Params;
import com.birbit.android.jobqueue.RetryConstraint;
import com.birbit.android.jobqueue.config.Configuration;

import org.hamcrest.MatcherAssert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;

@RunWith(RobolectricTestRunner.class)
@Config(constants = com.birbit.android.jobqueue.BuildConfig.class)
public class PriorityTest extends JobManagerTestBase {
    private static CountDownLatch priorityRunLatch;

    @Test
    public void testPriority() throws Exception {
        JobManager jobManager = createJobManager(
                new Configuration.Builder(RuntimeEnvironment.application)
                        .maxConsumerCount(1)
                        .timer(mockTimer));
        testPriority(jobManager, false);
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    public void testPriority(JobManager jobManager, boolean persist) throws Exception {
        priorityRunLatch = new CountDownLatch(2);
        DummyJobWithRunOrderAssert.globalRunCount = new AtomicInteger(0);
        Job job1 = new DummyJobWithRunOrderAssert(2, new Params(1).setPersistent(persist));
        Job job2 = new DummyJobWithRunOrderAssert(1, new Params(2).setPersistent(persist));
        jobManager.stop();
        jobManager.addJob(job1);
        jobManager.addJob(job2);
        jobManager.start();
        MatcherAssert.assertThat(priorityRunLatch.await(1, TimeUnit.MINUTES), is(true));
        //ensure both jobs did run
        MatcherAssert.assertThat((int) priorityRunLatch.getCount(), equalTo(0));
    }

    public static class DummyJobWithRunOrderAssert extends Job {
        transient public static AtomicInteger globalRunCount;
        private int expectedRunOrder;

        public DummyJobWithRunOrderAssert(int expectedRunOrder, Params params) {
            super(params.requireNetwork());
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
        protected RetryConstraint shouldReRunOnThrowable(@NonNull Throwable throwable, int runCount, int maxRunCount) {
            return RetryConstraint.CANCEL;
        }

        @Override
        protected void onCancel(@CancelReason int cancelReason, @Nullable Throwable throwable) {

        }
    }
}
