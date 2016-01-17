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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.*;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = com.path.android.jobqueue.BuildConfig.class)
public class ApplicationContextTests extends JobManagerTestBase {
    static int retryCount = 0;
    static List<Throwable> errors = new ArrayList<>();
    @Before
    public void clear() {
        retryCount = 0;
        errors.clear();
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
        final ContextCheckJob addedJob = new ContextCheckJob(new Params(1).setPersistent(persistent));
        final JobManager jobManager = createJobManager();
        waitUntilAJobIsDone(jobManager, new WaitUntilCallback() {
            @Override
            public void run() {
                jobManager.addJob(addedJob);
            }

            @Override
            public void assertJob(Job job) {
                job.getId().equals(addedJob.getId());
            }
        });
    }

    public static class ContextCheckJob extends Job {
        protected ContextCheckJob(Params params) {
            super(params);
        }

        private void assertContext(String method) {
            Context applicationContext = getApplicationContext();
            try {
                assertThat("Context should be application context in " + method,
                        applicationContext, sameInstance((Context) RuntimeEnvironment.application));
            } catch (Throwable t) {
                errors.add(t);
            }
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
        }

        @Override
        protected boolean shouldReRunOnThrowable(Throwable throwable) {
            assertContext("shouldReRunOnThrowable");
            return retryCount < 2;
        }
    }
}
