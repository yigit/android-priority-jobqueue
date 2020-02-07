package com.birbit.android.jobqueue.test.jobmanager;

import com.birbit.android.jobqueue.Job;
import com.birbit.android.jobqueue.JobManager;
import com.birbit.android.jobqueue.Params;
import com.birbit.android.jobqueue.RetryConstraint;
import com.birbit.android.jobqueue.TagConstraint;
import com.birbit.android.jobqueue.callback.JobManagerCallbackAdapter;
import com.birbit.android.jobqueue.config.Configuration;
import com.birbit.android.jobqueue.di.DependencyInjector;
import com.birbit.android.jobqueue.scheduling.Scheduler;
import com.birbit.android.jobqueue.scheduling.SchedulerConstraint;
import com.birbit.android.jobqueue.test.jobs.DummyJob;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static org.mockito.Mockito.times;

@RunWith(RobolectricTestRunner.class)
public class SchedulerLimitTestCase extends JobManagerTestBase {

    @Test
    public void testSchedulerLimitsAreRespected() {
        Scheduler scheduler = Mockito.mock(Scheduler.class);
        JobManager jobManager = createJobManager(scheduler, null);
        jobManager.stop();
        for (int i = 0; i < Configuration.DEFAULT_MAX_JOBS_TO_SCHEDULE_USING_SCHEDULER + 5; i++) {
            jobManager.addJob(new DummyJob(new Params(1).setPersistent(true).setRequiresNetwork(true)));
        }
        Mockito.verify(scheduler, times(Configuration.DEFAULT_MAX_JOBS_TO_SCHEDULE_USING_SCHEDULER)).request(ArgumentMatchers.any(SchedulerConstraint.class));
    }

    @Test
    public void testScheduleAfterJobCompletion() throws InterruptedException {
        final Scheduler scheduler = Mockito.mock(Scheduler.class);
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch endLatch = new CountDownLatch(1);
        final CountDownLatch onRunLatch = new CountDownLatch(1);
        final JobManager jobManager = createJobManager(scheduler, new DependencyInjector() {
            @Override
            public void inject(Job job) {
                if (job instanceof InnerDummyTwoLatchJob) {
                    ((InnerDummyTwoLatchJob) job).setLatches(onRunLatch, startLatch, endLatch);
                }
            }
        });

        final InnerDummyTwoLatchJob twoLatchJob = new InnerDummyTwoLatchJob(new Params(1).groupBy("Group").setPersistent(true).setRequiresNetwork(true));
        final DummyJob dummyJob = new DummyJob(new Params(1).groupBy("Group").setPersistent(true).setRequiresNetwork(true));
        jobManager.addJob(twoLatchJob);
        onRunLatch.await();
        jobManager.addJob(dummyJob);
        Mockito.verify(scheduler, times(1)).request(ArgumentMatchers.any(SchedulerConstraint.class));
        final CountDownLatch twoLatchJobDone = new CountDownLatch(1);
        jobManager.addCallback(new JobManagerCallbackAdapter() {
            @Override
            public void onAfterJobRun(@NonNull Job job, int resultCode) {
                jobManager.removeCallback(this);
                twoLatchJobDone.countDown();
            }
        });
        startLatch.countDown();//let it run
        try {
            endLatch.await();//wait till it finishes
        } catch (InterruptedException ignored) {

        }
        twoLatchJobDone.await(30, TimeUnit.SECONDS);
        //TODO Remove this sleep hack and try something solid.
        // it is needed because JobManager tries to remove canceled job after #onAfterJobRun in handleRunJobResult method.
        Thread.sleep(500);
        Mockito.verify(scheduler, times(2)).request(ArgumentMatchers.any(SchedulerConstraint.class));
    }

    @Test
    public void testScheduleAfterJobCancelled() throws InterruptedException {
        final Scheduler scheduler = Mockito.mock(Scheduler.class);
        final JobManager jobManager = createJobManager(scheduler, null);

        final DummyJob dummyJob1 = new DummyJob(new Params(1).groupBy("Group").setPersistent(true).addTags("dummyTag").setRequiresNetwork(true));
        final DummyJob dummyJob2 = new DummyJob(new Params(1).groupBy("Group").setPersistent(true).setRequiresNetwork(true));
        jobManager.stop();
        jobManager.addJob(dummyJob1);
        jobManager.addJob(dummyJob2);
        Thread.sleep(500);
        Mockito.verify(scheduler, times(1)).request(ArgumentMatchers.any(SchedulerConstraint.class));
        jobManager.cancelJobs(TagConstraint.ALL, "dummyTag");
        Thread.sleep(500);
        Mockito.verify(scheduler, times(2)).request(ArgumentMatchers.any(SchedulerConstraint.class));
    }

    @Test
    public void testScheduleAfterJobCancelledFromRetry() throws InterruptedException {
        final Scheduler scheduler = Mockito.mock(Scheduler.class);
        final JobManager jobManager = createJobManager(scheduler, null);
        final CancelOnRetryJob cancelOnRetryJob = new CancelOnRetryJob(new Params(1).groupBy("Group").setPersistent(true).setRequiresNetwork(true));
        final DummyJob dummyJob2 = new DummyJob(new Params(1).groupBy("Group").setPersistent(true).setRequiresNetwork(true));
        final CountDownLatch cancelLatch = new CountDownLatch(1);
        jobManager.addCallback(new JobManagerCallbackAdapter() {
            @Override
            public void onJobCancelled(@NonNull Job job, boolean byCancelRequest, @Nullable Throwable throwable) {
                if (!byCancelRequest && job.getId().equals(cancelOnRetryJob.getId())) {
                    cancelLatch.countDown();
                }
            }
        });

        jobManager.stop();
        jobManager.addJob(cancelOnRetryJob);
        jobManager.addJob(dummyJob2);
        jobManager.start();
        Mockito.verify(scheduler, times(1)).request(ArgumentMatchers.any(SchedulerConstraint.class));

        cancelLatch.await(30, TimeUnit.SECONDS);
        Thread.sleep(500);
        Mockito.verify(scheduler, times(2)).request(ArgumentMatchers.any(SchedulerConstraint.class));
    }

    protected JobManager createJobManager(Scheduler scheduler, @Nullable DependencyInjector dependencyInjector) {
        Configuration.Builder builder = new Configuration.Builder(RuntimeEnvironment.application)
                .timer(mockTimer)
                .inTestMode()
                .injector(dependencyInjector)
                .scheduler(scheduler, false);
        return createJobManager(builder);
    }

    static class InnerDummyTwoLatchJob extends DummyJob {

        private transient CountDownLatch onRunLatch;
        private transient CountDownLatch startLatch;
        private transient CountDownLatch endLatch;

        protected InnerDummyTwoLatchJob(Params params) {
            super(params);
        }

        @Override
        public void onAdded() {

        }

        public void setLatches(CountDownLatch onRunLatch, CountDownLatch startLatch, CountDownLatch endLatch) {
            this.onRunLatch = onRunLatch;
            this.startLatch = startLatch;
            this.endLatch = endLatch;
        }

        @Override
        public void onRun() throws Throwable {
            onRunLatch.countDown();
            startLatch.await();
            super.onRun();
            endLatch.countDown();
        }

        @Override
        protected void onCancel(int cancelReason, @Nullable Throwable throwable) {

        }

        @Override
        protected RetryConstraint shouldReRunOnThrowable(@NonNull Throwable throwable, int runCount, int maxRunCount) {
            return null;
        }
    }

    static class CancelOnRetryJob extends DummyJob {
        protected CancelOnRetryJob(Params params) {
            super(params);
        }

        @Override
        public void onAdded() {

        }

        @Override
        public void onRun() throws Throwable {
            throw new Exception("");
        }

        @Override
        protected void onCancel(int cancelReason, @Nullable Throwable throwable) {

        }

        @Override
        protected RetryConstraint shouldReRunOnThrowable(@NonNull Throwable throwable, int runCount, int maxRunCount) {
            return RetryConstraint.CANCEL;
        }
    }
}
