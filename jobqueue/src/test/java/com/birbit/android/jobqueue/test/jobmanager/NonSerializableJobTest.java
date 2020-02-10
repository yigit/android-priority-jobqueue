package com.birbit.android.jobqueue.test.jobmanager;

import com.birbit.android.jobqueue.JobManager;
import com.birbit.android.jobqueue.Params;
import com.birbit.android.jobqueue.test.jobs.DummyJob;

import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)

public class NonSerializableJobTest extends JobManagerTestBase {
    @Test
    public void test() throws InterruptedException {
        final Throwable[] throwable = new Throwable[1];
        JobManager jobManager = createJobManager();
        final CountDownLatch latch = new CountDownLatch(1);
        jobManager.getJobManagerExecutionThread().setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable ex) {
                throwable[0] = ex;
                latch.countDown();
            }
        });
        jobManager.addJobInBackground(new DummyJob(new Params(0).persist()) {
            ICannotBeSerialized iCannotBeSerialized = new ICannotBeSerialized();

        });
        MatcherAssert.assertThat(latch.await(30, TimeUnit.SECONDS), CoreMatchers.is(true));
        MatcherAssert.assertThat(throwable[0] instanceof RuntimeException, CoreMatchers.is(true));
        MatcherAssert.assertThat(throwable[0].getCause() instanceof IOException, CoreMatchers.is(true));
    }

    static class ICannotBeSerialized {

    }
}
