package com.birbit.android.jobqueue.test.jobmanager;

import android.support.annotation.NonNull;

import com.birbit.android.jobqueue.CancelReason;
import com.birbit.android.jobqueue.Job;
import com.birbit.android.jobqueue.JobManager;
import com.birbit.android.jobqueue.Params;
import com.birbit.android.jobqueue.RetryConstraint;
import com.birbit.android.jobqueue.WorkerFactory;
import com.birbit.android.jobqueue.config.Configuration;

import org.hamcrest.MatcherAssert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.UUID;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = com.birbit.android.jobqueue.BuildConfig.class)
public class WorkerThreadTest extends JobManagerTestBase {

    private Throwable error;

    @Test
    public void testWorkerThread() throws Throwable {
        final JobManager jobManager = createJobManager(
                new Configuration.Builder(RuntimeEnvironment.application)
                        .timer(mockTimer)
                        .workerFactory(new WorkerFactory() {
                            @NonNull
                            @Override
                            public Thread create(@NonNull ThreadGroup threadGroup, @NonNull Runnable consumer, int priority) {
                                return new DummyThread(threadGroup, consumer, priority);
                            }
                        })
        );
        final Job job = new CheckWorkerJob();
        waitUntilAJobIsDone(jobManager, new WaitUntilCallback() {
            @Override
            public void run() {
                jobManager.addJob(job);
            }

            @Override
            public void assertJob(Job job) {}
        });
        if (error != null) {
            throw error;
        }
    }

    static class DummyThread extends Thread {

        public DummyThread(ThreadGroup threadGroup, Runnable runnable, int priority) {
            super(threadGroup, runnable, "dummy-worker-" + UUID.randomUUID().toString());
            setPriority(priority);
        }

    }

    class CheckWorkerJob extends Job {

        protected CheckWorkerJob() {
            super(new Params(1));
        }

        @Override
        public void onAdded() {}

        @Override
        public void onRun() throws Throwable {
            try {
                MatcherAssert.assertThat("Worker thread should be an instance of DummyThread",
                                         Thread.currentThread() instanceof DummyThread);
            } catch (Throwable e) {
                error = e;
            }
        }

        @Override
        protected void onCancel(@CancelReason int cancelReason) {}

        @Override
        protected RetryConstraint shouldReRunOnThrowable(Throwable throwable, int runCount, int maxRunCount) {
            return RetryConstraint.CANCEL;
        }
    }

}
