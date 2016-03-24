package com.birbit.android.jobqueue.test.jobmanager;

import com.birbit.android.jobqueue.CancelReason;
import com.birbit.android.jobqueue.Job;
import com.birbit.android.jobqueue.JobManager;
import com.birbit.android.jobqueue.Params;
import com.birbit.android.jobqueue.RetryConstraint;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.model.MultipleFailureException;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.*;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = com.birbit.android.jobqueue.BuildConfig.class)
public class ApplicationContextTests extends JobManagerTestBase {
    static int retryCount = 0;
    static List<Throwable> errors = new ArrayList<>();
    @Before
    public void clear() {
        retryCount = 0;
        errors.clear();
    }

    @Test
    public void getContextNonPersistent() throws InterruptedException, MultipleFailureException {
        getContextTest(false);
    }

    @Test
    public void getContextPersistent() throws InterruptedException, MultipleFailureException {
        getContextTest(true);
    }

    public void getContextTest(boolean persistent)
            throws InterruptedException, MultipleFailureException {
        final ContextCheckJob addedJob = new ContextCheckJob(new Params(1).setPersistent(persistent));
        final JobManager jobManager = createJobManager();
        waitUntilAJobIsDone(jobManager, new WaitUntilCallback() {
            @Override
            public void run() {
                jobManager.addJob(addedJob);
            }

            @Override
            public void assertJob(Job job) {

            }
        });
        if (!errors.isEmpty()) {
            throw new MultipleFailureException(errors);
        }
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
        protected void onCancel(@CancelReason int cancelReason) {
            assertContext("onCancel");
        }

        @Override
        protected RetryConstraint shouldReRunOnThrowable(Throwable throwable, int runCount, int maxRunCount) {
            assertContext("shouldReRunOnThrowable");
            return retryCount < 2 ? RetryConstraint.RETRY : RetryConstraint.CANCEL;
        }
    }
}
