package com.birbit.android.jobqueue.test.jobmanager;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.birbit.android.jobqueue.CancelReason;
import com.birbit.android.jobqueue.Job;
import com.birbit.android.jobqueue.JobManager;
import com.birbit.android.jobqueue.Params;
import com.birbit.android.jobqueue.RetryConstraint;

import org.hamcrest.MatcherAssert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.equalTo;

@RunWith(RobolectricTestRunner.class)
@Config(constants = com.birbit.android.jobqueue.BuildConfig.class)
public class RunFailingJobTest extends JobManagerTestBase {
    @Test
    public void runFailingJob() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        JobManager jobManager = createJobManager();
        jobManager.addJob(new Job(new Params(0).requireNetwork()) {
            @Override
            public void onAdded() {

            }

            @Override
            public void onRun() throws Throwable {
                throw new RuntimeException();
            }

            @Override
            protected void onCancel(@CancelReason int cancelReason, @Nullable Throwable throwable) {
                latch.countDown();
            }

            @Override
            protected RetryConstraint shouldReRunOnThrowable(@NonNull Throwable throwable, int runCount, int maxRunCount) {
                return RetryConstraint.CANCEL;
            }
        });
        latch.await(10, TimeUnit.SECONDS);
        MatcherAssert.assertThat((int) latch.getCount(), equalTo(0));
    }

}
