package com.path.android.jobqueue.test.jobmanager;

import com.path.android.jobqueue.JobHolder;
import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.Params;
import com.path.android.jobqueue.executor.JobConsumerExecutor;
import com.path.android.jobqueue.test.jobs.DummyJob;

import org.fest.reflect.core.Reflection;
import org.fest.reflect.method.Invoker;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = com.path.android.jobqueue.BuildConfig.class)
public class JobConsumerExecutorTest extends JobManagerTestBase {

    protected Invoker<Void> getOnBeforeRunMethod(JobConsumerExecutor executor) {
        return Reflection.method("onBeforeRun").withParameterTypes(JobHolder.class).in(executor);
    }

    protected Invoker<Void> getOnAfterRunMethod(JobConsumerExecutor executor) {
        return Reflection.method("onAfterRun").withParameterTypes(JobHolder.class).in(executor);
    }

    @Test
    public void testFindAllPersistent() throws Exception {
        testFindAll(true);
    }

    @Test
    public void testFindAllNonPersistent() throws Exception {
        testFindAll(false);
    }

    private void testFindAll(boolean persistent) {
        JobManager jobManager = createJobManager();
        jobManager.stop();
        JobConsumerExecutor executor = getConsumerExecutor(jobManager);
        assertThat("empty executor should return 0", executor.findRunning(persistent).size(), is(0));

        DummyJob dummyJob1 = new DummyJob(new Params(0).setPersistent(persistent));
        jobManager.addJob(dummyJob1);
        JobHolder jobHolder1 = getNextJobMethod(jobManager).invoke();
        getOnBeforeRunMethod(executor).invoke(jobHolder1);
        assertThat("should return inserted job", executor.findRunning(persistent).size(), is(1));

        DummyJob dummyJob2 = new DummyJob(new Params(0).setPersistent(!persistent));
        jobManager.addJob(dummyJob2);
        JobHolder jobHolder2 = getNextJobMethod(jobManager).invoke();
        getOnBeforeRunMethod(executor).invoke(jobHolder2);
        assertThat("should not increase count if persistent is " + persistent,
                executor.findRunning(persistent).size(), is(1));

        getOnAfterRunMethod(executor).invoke(jobHolder1);
        assertThat("should not return job if stopped running", executor.findRunning(persistent).size(), is(0));
    }
}
