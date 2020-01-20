package com.birbit.android.jobqueue.test.jobmanager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(RobolectricTestRunner.class)

public class CallbackTest extends JobManagerTestBase {
    @Test
    public void successNonPersistent() throws Throwable {
        JobManagerCallback callback = mock(JobManagerCallback.class);
        final Job job = spy(new PublicJob(new Params(0)));
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
        final Throwable error = new Exception();
        JobManagerCallback callback = mock(JobManagerCallback.class);
        final PublicJob job = spy(new PublicJob(new Params(0)));
        doNothing().when(job).onAdded();
        doThrow(error).when(job).onRun();
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
        verify(callback).onJobCancelled(job, false, error);
    }

    @Test
    public void cancelViaShouldReRun() throws Throwable {
        JobManagerCallback callback = mock(JobManagerCallback.class);
        final PublicJob job = spy(new PublicJob(new Params(0)));
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
        verify(callback).onJobCancelled(eq(job), eq(false), any(Throwable.class));
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
        verify(callback).onJobCancelled(job, true, null);
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
        protected void onCancel(@CancelReason int cancelReason, @Nullable Throwable throwable) {

        }

        @Override
        protected RetryConstraint shouldReRunOnThrowable(@NonNull Throwable throwable, int runCount, int maxRunCount) {
            throw new RuntimeException("not expected to arrive here");
        }
    }

    public static class PublicJob extends Job {

        protected PublicJob(Params params) {
            super(params);
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
        protected void onCancel(@CancelReason int cancelReason, @Nullable Throwable throwable) {

        }

        @Override
        protected RetryConstraint shouldReRunOnThrowable(@NonNull Throwable throwable, int runCount, int maxRunCount) {
            throw new UnsupportedOperationException("should not be called directly");
        }

    }
}
