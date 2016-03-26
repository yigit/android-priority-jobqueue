package com.birbit.android.jobqueue.test.jobmanager;

import com.birbit.android.jobqueue.CancelReason;
import com.birbit.android.jobqueue.Job;
import com.birbit.android.jobqueue.JobManager;
import com.birbit.android.jobqueue.JobStatus;
import com.birbit.android.jobqueue.Params;
import com.birbit.android.jobqueue.RetryConstraint;
import com.birbit.android.jobqueue.TagConstraint;
import com.birbit.android.jobqueue.callback.JobManagerCallback;

import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.mockito.Mockito.*;

import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = com.birbit.android.jobqueue.BuildConfig.class)
public class CallbackTest extends JobManagerTestBase {
    @Test
    public void successNonPersistent() throws Throwable {
        JobManagerCallback callback = mock(JobManagerCallback.class);
        final Job job = mock(Job.class);
        doReturn("a").when(job).getId();
        doNothing().when(job).onAdded();
        doNothing().when(job).onRun();
        final JobManager jobManager = createJobManager();
        jobManager.addCallback(callback);
        waitUntilAJobIsDone(jobManager, new WaitUntilCallback() {
            @Override
            public void run() {
                jobManager.addJob(job);
            }

            @Override
            public void assertJob(Job job) {

            }
        });
        verify(job).onAdded();
        verify(job).onRun();
        verify(callback).onJobAdded(job);
        verify(callback).onJobRun(job, JobManagerCallback.RESULT_SUCCEED);
    }

    @Test
    public void cancelViaRetryLimit() throws Throwable {
        JobManagerCallback callback = mock(JobManagerCallback.class);
        final PublicJob job = mock(PublicJob.class);
        doNothing().when(job).onAdded();
        doReturn("a").when(job).getId();
        doThrow(new Exception()).when(job).onRun();
        doReturn(3).when(job).getRetryLimit();
        doReturn(RetryConstraint.RETRY).when(job)
                .shouldReRunOnThrowable(any(Throwable.class), anyInt(), anyInt());
        final JobManager jobManager = createJobManager();
        jobManager.addCallback(callback);
        waitUntilAJobIsDone(jobManager, new WaitUntilCallback() {
            @Override
            public void run() {
                jobManager.addJob(job);
            }

            @Override
            public void assertJob(Job job) {

            }
        });


        verify(callback).onJobAdded(job);
        verify(callback, times(2)).onJobRun(job, JobManagerCallback.RESULT_FAIL_WILL_RETRY);
        verify(callback, times(1)).onJobRun(job, JobManagerCallback.RESULT_CANCEL_REACHED_RETRY_LIMIT);
        verify(callback).onJobCancelled(job, false);
    }

    @Test
    public void cancelViaShouldReRun() throws Throwable {
        JobManagerCallback callback = mock(JobManagerCallback.class);
        final PublicJob job = mock(PublicJob.class);
        doReturn("a").when(job).getId();
        doNothing().when(job).onAdded();
        doThrow(new Exception()).when(job).onRun();
        doReturn(3).when(job).getRetryLimit();
        doReturn(RetryConstraint.CANCEL).when(job).shouldReRunOnThrowable(any(Throwable.class), anyInt(), anyInt());
        final JobManager jobManager = createJobManager();
        jobManager.addCallback(callback);
        waitUntilAJobIsDone(jobManager, new WaitUntilCallback() {
            @Override
            public void run() {
                jobManager.addJob(job);
            }

            @Override
            public void assertJob(Job job) {

            }
        });

        verify(callback).onJobAdded(job);
        verify(callback, times(1)).onJobRun(job, JobManagerCallback.RESULT_CANCEL_CANCELLED_VIA_SHOULD_RE_RUN);
        verify(callback).onJobCancelled(job, false);
    }

    @Test
    public void cancelViaCancelCall() throws Throwable {
        JobManagerCallback callback = mock(JobManagerCallback.class);
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch endLatch = new CountDownLatch(1);
        final Throwable[] jobError = new Throwable[1];
        PublicJob job = spy(new PublicJob(new Params(1).addTags("tag1")) {
            @Override
            public void onRun() throws Throwable {
                startLatch.countDown();
                try {
                    Assert.assertThat(endLatch.await(30, TimeUnit.SECONDS), CoreMatchers.is(true));
                } catch (Throwable t) {
                    jobError[0] = t;
                }
                throw new Exception("blah");
            }
        });
        doCallRealMethod().when(job).onRun();
        doReturn(3).when(job).getRetryLimit();
        JobManager jobManager = createJobManager();
        jobManager.addCallback(callback);

        jobManager.addJob(job);
        Assert.assertThat(startLatch.await(30, TimeUnit.SECONDS), CoreMatchers.is(true));

        jobManager.cancelJobsInBackground(null, TagConstraint.ANY, "tag1");
        //noinspection StatementWithEmptyBody
        while (!job.isCancelled()) {
            // busy wait until cancel arrives
            //noinspection SLEEP_IN_CODE
            Thread.sleep(100);
        }
        endLatch.countDown();

        while (jobManager.getJobStatus(job.getId()) != JobStatus.UNKNOWN) {
            // busy wait until job cancel is handled
            //noinspection SLEEP_IN_CODE
            Thread.sleep(100);
        }
        MatcherAssert.assertThat(jobError[0], CoreMatchers.nullValue());
        verify(job, times(0)).shouldReRunOnThrowable(any(Throwable.class), anyInt(), anyInt());
        jobManager.stopAndWaitUntilConsumersAreFinished();
        verify(callback).onJobAdded(job);
        verify(callback, times(1)).onJobRun(job, JobManagerCallback.RESULT_CANCEL_CANCELLED_WHILE_RUNNING);
        verify(callback).onJobCancelled(job, true);
    }

    @Test
    public void successPersistent() throws Throwable {
        JobManagerCallback callback = mock(JobManagerCallback.class);
        final Job job = new PersistentDummyJob();
        final JobManager jobManager = createJobManager();
        jobManager.addCallback(callback);
        waitUntilAJobIsDone(jobManager, new WaitUntilCallback() {
            @Override
            public void run() {
                jobManager.addJob(job);
            }

            @Override
            public void assertJob(Job job) {

            }
        });
        verify(callback).onJobAdded(any(PersistentDummyJob.class));
        verify(callback).onJobRun(any(PersistentDummyJob.class), eq(JobManagerCallback.RESULT_SUCCEED));
    }

    public static class PersistentDummyJob extends Job {
        public PersistentDummyJob() {
            super(new Params(1).persist());
        }

        @Override
        public void onAdded() {

        }

        @Override
        public void onRun() throws Throwable {

        }

        @Override
        protected void onCancel(@CancelReason int cancelReason) {

        }

        @Override
        protected RetryConstraint shouldReRunOnThrowable(Throwable throwable, int runCount, int maxRunCount) {
            throw new RuntimeException("not expected to arrive here");
        }
    }

    public static class PublicJob extends Job {

        protected PublicJob(Params params) {
            super(params);
        }

        @Override
        public int getCurrentRunCount() {
            return super.getCurrentRunCount();
        }

        @Override
        public int getRetryLimit() {
            return super.getRetryLimit();
        }

        @Override
        public void onAdded() {

        }

        @Override
        public void onRun() throws Throwable {

        }

        @Override
        protected void onCancel(@CancelReason int cancelReason) {

        }

        @Override
        protected RetryConstraint shouldReRunOnThrowable(Throwable throwable, int runCount, int maxRunCount) {
            throw new UnsupportedOperationException("should not be called directly");
        }

    }
}
