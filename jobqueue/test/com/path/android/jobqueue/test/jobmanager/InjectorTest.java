package com.path.android.jobqueue.test.jobmanager;

import com.path.android.jobqueue.BaseJob;
import com.path.android.jobqueue.JobHolder;
import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.Params;
import com.path.android.jobqueue.config.Configuration;
import com.path.android.jobqueue.di.DependencyInjector;
import com.path.android.jobqueue.log.CustomLogger;
import com.path.android.jobqueue.test.jobs.DummyJob;
import static org.hamcrest.CoreMatchers.*;
import org.hamcrest.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
public class InjectorTest extends JobManagerTestBase {
    @Test
    public void testInjector() throws Exception {
        Configuration.Builder builder = new Configuration.Builder(Robolectric.application);
        final JobManagerTestBase.ObjectReference injectedJobReference = new JobManagerTestBase.ObjectReference();
        final AtomicInteger injectionCallCount = new AtomicInteger(0);
        DependencyInjector dependencyInjector = new DependencyInjector() {
            @Override
            public void inject(BaseJob job) {
                injectedJobReference.setObject(job);
                injectionCallCount.incrementAndGet();
            }
        };
        builder.injector(dependencyInjector);
        JobManager jobManager = createJobManager(builder);
        jobManager.stop();
        jobManager.addJob(new DummyJob(new Params(4)));
        MatcherAssert.assertThat("injection should be called after adding a non-persistent job", injectionCallCount.get(), equalTo(1));
        jobManager.addJob(new DummyJob(new Params(1).persist()));
        MatcherAssert.assertThat("injection should be called after adding a persistent job", injectionCallCount.get(), equalTo(2));
        JobHolder holder = getNextJobMethod(jobManager).invoke();
        MatcherAssert.assertThat("injection should NOT be called for non persistent job", holder.getBaseJob(), not(injectedJobReference.getObject()));
        MatcherAssert.assertThat("injection should be called once for non persistent job", injectionCallCount.get(), equalTo(2));
        holder = getNextJobMethod(jobManager).invoke();
        MatcherAssert.assertThat("injection should be called for persistent job", holder.getBaseJob(), equalTo(injectedJobReference.getObject()));
        MatcherAssert.assertThat("injection should be called two times for persistent job", injectionCallCount.get(), equalTo(3));
    }

    @Test
    public void testInjectorCrash() throws Exception {
        final String EXCEPTION_MESSAGE = "could not inject for whatever reason :)";
        DependencyInjector dummyDependencyInjector = new DependencyInjector() {
            @Override
            public void inject(BaseJob baseJob) {
                throw new RuntimeException(EXCEPTION_MESSAGE);
            }
        };

        final ObjectReference objectReference = new ObjectReference();
        final CountDownLatch exceptionLatch = new CountDownLatch(1);
        CustomLogger customLogger = new CustomLogger() {
            @Override
            public boolean isDebugEnabled() {
                return false;
            }

            @Override
            public void d(String s, Object... objects) {

            }

            @Override
            public void e(Throwable throwable, String s, Object... objects) {
                objectReference.setObject(throwable);
                exceptionLatch.countDown();
            }

            @Override
            public void e(String s, Object... objects) {
                //
            }
        };
        JobManager jobManager = createJobManager(new Configuration.Builder(Robolectric.application).injector(dummyDependencyInjector).customLogger(customLogger));
        Throwable addException = null;
        try {
            jobManager.addJob(new DummyJob(new Params(0)));
        } catch (Throwable t) {
            addException = t;
        }
        MatcherAssert.assertThat("addJob should throw exception if dependency injector throws exception", addException, notNullValue());
        jobManager.addJobInBackground(new DummyJob(new Params(0)));
        exceptionLatch.await(2, TimeUnit.SECONDS);
        MatcherAssert.assertThat("there should be a received exception", objectReference.getObject(), notNullValue());
        MatcherAssert.assertThat("logged exception should be a runtime exception", objectReference.getObject(), instanceOf(RuntimeException.class));
        MatcherAssert.assertThat("logged exception should have expected message", ((Throwable)objectReference.getObject()).getMessage(), is(EXCEPTION_MESSAGE));

    }
}
