package com.path.android.jobqueue.test.jobmanager;

import com.path.android.jobqueue.Job;
import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.Params;
import com.path.android.jobqueue.RetryConstraint;
import com.path.android.jobqueue.TagConstraint;
import com.path.android.jobqueue.callback.JobManagerCallback;
import com.path.android.jobqueue.test.jobs.DummyJob;

import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.mockito.Mockito.*;

import org.mockito.ArgumentCaptor;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = com.path.android.jobqueue.BuildConfig.class)
public class CallbackTest extends JobManagerTestBase {
    @Test
    public void successNonPersistent() throws Throwable {
        JobManagerCallback callback = mock(JobManagerCallback.class);
        Job job = mock(Job.class);
        doNothing().when(job).onAdded();
        doNothing().when(job).onRun();
        JobManager jobManager = createJobManager();
        jobManager.addCallback(callback);
        jobManager.addJob(job);
        busyDrain(jobManager, 2);
        verify(job).onAdded();
        verify(job).onRun();
        verify(callback).onJobAdded(job);
        verify(callback).onJobRun(job, JobManagerCallback.RESULT_SUCCEED);
    }

    @Test
    public void cancelViaRetryLimit() throws Throwable {
        JobManagerCallback callback = mock(JobManagerCallback.class);
        PublicJob job = mock(PublicJob.class);
        doNothing().when(job).onAdded();
        doThrow(new Exception()).when(job).onRun();
        doReturn(3).when(job).getRetryLimit();
        doReturn(RetryConstraint.RETRY).when(job).shouldReRunOnThrowable(any(Throwable.class), anyInt(), anyInt());
        JobManager jobManager = createJobManager();
        jobManager.addCallback(callback);
        jobManager.addJob(job);
        busyDrain(jobManager, 2);
        verify(callback).onJobAdded(job);
        verify(callback, times(2)).onJobRun(job, JobManagerCallback.RESULT_FAIL_WILL_RETRY);
        verify(callback, times(1)).onJobRun(job, JobManagerCallback.RESULT_CANCEL_REACHED_RETRY_LIMIT);
        verify(callback).onJobCancelled(job, false);
    }

    @Test
    public void cancelViaShouldReRun() throws Throwable {
        JobManagerCallback callback = mock(JobManagerCallback.class);
        PublicJob job = mock(PublicJob.class);
        doNothing().when(job).onAdded();
        doThrow(new Exception()).when(job).onRun();
        doReturn(3).when(job).getRetryLimit();
        doReturn(RetryConstraint.CANCEL).when(job).shouldReRunOnThrowable(any(Throwable.class), anyInt(), anyInt());
        JobManager jobManager = createJobManager();
        jobManager.addCallback(callback);
        jobManager.addJob(job);
        busyDrain(jobManager, 2);
        verify(callback).onJobAdded(job);
        verify(callback, times(1)).onJobRun(job, JobManagerCallback.RESULT_CANCEL_CANCELLED_VIA_SHOULD_RE_RUN);
        verify(callback).onJobCancelled(job, false);
    }

    @Test
    public void cancelViaCancelCall() throws Throwable {
        JobManagerCallback callback = mock(JobManagerCallback.class);
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch endLatch = new CountDownLatch(1);
        PublicJob job = spy(new PublicJob(new Params(1).addTags("tag1")) {
            @Override
            public void onRun() throws Throwable {
                startLatch.countDown();
                Assert.assertThat(endLatch.await(2, TimeUnit.SECONDS), CoreMatchers.is(true));
                throw new Exception("blah");
            }
        });
        doCallRealMethod().when(job).onRun();
        doReturn(3).when(job).getRetryLimit();
        verify(job, times(0)).shouldReRunOnThrowable(any(Throwable.class), anyInt(), anyInt());
        JobManager jobManager = createJobManager();
        jobManager.addCallback(callback);
        jobManager.addJob(job);
        Assert.assertThat(startLatch.await(2, TimeUnit.SECONDS), CoreMatchers.is(true));
        jobManager.cancelJobsInBackground(null, TagConstraint.ANY, "tag1");
        Thread.sleep(500); // to ensure cancel request has reached
        endLatch.countDown();
        busyDrain(jobManager, 2);
        Thread.sleep(500); // wait until cancel finishes
        verify(callback).onJobAdded(job);
        verify(callback, times(1)).onJobRun(job, JobManagerCallback.RESULT_CANCEL_CANCELLED_WHILE_RUNNING);
        verify(callback).onJobCancelled(job, true);
    }

    @Test
    public void successPersistent() throws Throwable {
        JobManagerCallback callback = mock(JobManagerCallback.class);
        Job job = new PersistentDummyJob();
        JobManager jobManager = createJobManager();
        jobManager.addCallback(callback);
        jobManager.addJob(job);
        busyDrain(jobManager, 2);
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
        protected void onCancel() {

        }
    }

    public static class PublicJob extends Job {

        protected PublicJob(Params params) {
            super(params);
        }

        @Override
        public RetryConstraint shouldReRunOnThrowable(Throwable throwable, int runCount, int maxRunCount) {
            return super.shouldReRunOnThrowable(throwable, runCount, maxRunCount);
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
        protected void onCancel() {

        }
    }
}
