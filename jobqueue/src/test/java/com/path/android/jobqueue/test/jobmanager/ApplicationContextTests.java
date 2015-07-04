package com.path.android.jobqueue.test.jobmanager;

import com.path.android.jobqueue.Job;
import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.Params;

import org.hamcrest.CoreMatchers;
import org.hamcrest.Matcher;
import org.hamcrest.MatcherAssert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import android.content.Context;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.*;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = com.path.android.jobqueue.BuildConfig.class)
public class ApplicationContextTests extends JobManagerTestBase {
    static int retryCount = 0;
    static CountDownLatch doneLatch;
    @Before
    public void clear() {
        retryCount = 0;
        doneLatch = new CountDownLatch(1);
    }

    @Test
    public void getContextNonPersistent() throws InterruptedException {
        getContextTest(false);
    }

    @Test
    public void getContextPersistent() throws InterruptedException {
        getContextTest(true);
    }

    public void getContextTest(boolean persistent) throws InterruptedException {
        ContextCheckJob job = new ContextCheckJob(new Params(1).setPersistent(persistent));
        JobManager jobManager = createJobManager();
        jobManager.addJob(job);
        doneLatch.await(2, TimeUnit.SECONDS);
    }

    public static class ContextCheckJob extends Job {
        protected ContextCheckJob(Params params) {
            super(params);
        }

        private void assertContext(String method) {
            Context applicationContext = getApplicationContext();
            assertThat("Context should be application context in " + method,
                    applicationContext, sameInstance((Context)RuntimeEnvironment.application));
        }

        @Override
        public void onAdded() {
            assertContext("onAdded");
        }

        @Override
        public void onRun() throws Throwable {
            assertContext("onRun");
            if (retryCount < 2) {
                retryCount ++;
                throw new RuntimeException("failure on purpose");
            }
        }

        @Override
        protected void onCancel() {
            assertContext("onCancel");
            doneLatch.countDown();
        }

        @Override
        protected boolean shouldReRunOnThrowable(Throwable throwable) {
            assertContext("shouldReRunOnThrowable");
            return retryCount < 2;
        }
    }
}
