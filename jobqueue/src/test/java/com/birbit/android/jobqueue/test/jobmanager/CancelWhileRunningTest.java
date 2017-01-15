package com.birbit.android.jobqueue.test.jobmanager;

import com.birbit.android.jobqueue.CancelResult;
import com.birbit.android.jobqueue.Job;
import com.birbit.android.jobqueue.JobManager;
import com.birbit.android.jobqueue.Params;
import com.birbit.android.jobqueue.TagConstraint;
import com.birbit.android.jobqueue.config.Configuration;
import com.birbit.android.jobqueue.log.JqLog;
import com.birbit.android.jobqueue.test.jobs.DummyJob;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(RobolectricTestRunner.class)
@Config(constants = com.birbit.android.jobqueue.BuildConfig.class)
public class CancelWhileRunningTest extends JobManagerTestBase {
    @Test
    public void testCancelBeforeRunning() throws InterruptedException {
        JobManager jobManager = createJobManager(
                new Configuration.Builder(RuntimeEnvironment.application)
                        .minConsumerCount(5)
                        .timer(mockTimer));
        JobWithEndLatch nonPersistent1 = new JobWithEndLatch(new Params(0).addTags("dummyTag"), true);
        JobWithEndLatch nonPersistent2 = new JobWithEndLatch(new Params(0).addTags("dummyTag"), false);
        DummyJob persistentJob1 = new PersistentJobWithEndLatch(new Params(0).addTags("dummyTag"), false);
        DummyJob persistentJob2 = new PersistentJobWithEndLatch(new Params(0).addTags("dummyTag"), true);

        jobManager.addJob(nonPersistent1);
        jobManager.addJob(nonPersistent2);
        jobManager.addJob(persistentJob1);
        jobManager.addJob(persistentJob2);

        onStartLatch.await();
        nonPersistent1.onStartLatch.await();
        nonPersistent2.onStartLatch.await();
        final CancelResult[] resultHolder = new CancelResult[2];
        final CountDownLatch cancelLatch = new CountDownLatch(1);
        jobManager.cancelJobsInBackground(new CancelResult.AsyncCancelCallback() {
            @Override
            public void onCancelled(CancelResult cancelResult) {
                resultHolder[0] = cancelResult;
                cancelLatch.countDown();
            }
        }, TagConstraint.ANY, "dummyTag");

        jobManager.cancelJobsInBackground(new CancelResult.AsyncCancelCallback() {
            @Override
            public void onCancelled(CancelResult cancelResult) {
                resultHolder[1] = cancelResult;
            }
        }, TagConstraint.ANY, "dummyTag");

        assertThat("result should not arrive until existing jobs finish",
                cancelLatch.await(4, TimeUnit.SECONDS), is(false));
        onEndLatch.countDown();
        nonPersistent1.onEndLatch.countDown();
        nonPersistent2.onEndLatch.countDown();
        assertThat("when jobs in question are finished, cancel callback should be triggered",
                cancelLatch.await(10, TimeUnit.SECONDS), is(true));
        final CancelResult result = resultHolder[0];
        JqLog.d("cancelled jobs %s", result.getCancelledJobs());
        JqLog.d("failed to cancel %s", result.getFailedToCancel());
        assertThat("two jobs should be cancelled", result.getCancelledJobs().size(), is(2));
        assertThat("two jobs should fail to cancel", result.getFailedToCancel().size(), is(2));

        for (Job j : result.getCancelledJobs()) {
            FailingJob job = (FailingJob) j;
            if (!job.isPersistent()) {
                assertThat("job is still added", job.getOnAddedCnt(), is(1));
            }
            if (job.fail) {
                assertThat("job is cancelled", job.getOnCancelCnt(), is(1));
            } else {
                assertThat("job could not be cancelled", job.getOnCancelCnt(), is(0));
            }
        }

        for (Job j : result.getFailedToCancel()) {
            FailingJob job = (FailingJob) j;
            if (!job.isPersistent()) {
                assertThat("job is still added", job.getOnAddedCnt(), is(1));
            }
            if (job.fail) {
                assertThat("job is cancelled", job.getOnCancelCnt(), is(1));
            } else {
                assertThat("job could not be cancelled", job.getOnCancelCnt(), is(0));
            }
        }

        assertThat("second cancel should not cancel anything",
                resultHolder[1].getCancelledJobs().size(), is(0));
        assertThat("second cancel should not cancel anything",
                resultHolder[1].getFailedToCancel().size(), is(0));
    }

    public static CountDownLatch onStartLatch = new CountDownLatch(2);
    public static CountDownLatch onEndLatch = new CountDownLatch(1);

    public static class PersistentJobWithEndLatch extends FailingJob {

        public PersistentJobWithEndLatch(Params params, boolean fail) {
            super(params.persist(), fail);
        }

        @Override
        public void onRun() throws Throwable {
            JqLog.d("starting running %s", this);
            onStartLatch.countDown();
            onEndLatch.await();
            if (fail) {
                throw new RuntimeException("been asked to fail");
            }
            JqLog.d("finished w/ success %s", this);
        }
    }

    public static class JobWithEndLatch extends FailingJob {
        public final CountDownLatch onStartLatch = new CountDownLatch(1);
        public final CountDownLatch onEndLatch = new CountDownLatch(1);

        public JobWithEndLatch(Params params, boolean fail) {
            super(params, fail);
        }

        @Override
        public void onRun() throws Throwable {
            JqLog.d("starting running %s", this);
            onStartLatch.countDown();
            onEndLatch.await();
            if (fail) {
                throw new RuntimeException("been asked to fail");
            }
            JqLog.d("finished w/ success %s", this);
        }
    }

    public static class FailingJob extends DummyJob {
        private static int idCounter = 0;
        final boolean fail;
        final int id = idCounter++;

        public FailingJob(Params params, boolean fail) {
            super(params);
            this.fail = fail;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "[" +id + "](" + System.identityHashCode(this) + ")";
        }
    }
}
