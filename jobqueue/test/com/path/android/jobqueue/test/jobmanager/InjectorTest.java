package com.path.android.jobqueue.test.jobmanager;

import com.path.android.jobqueue.BaseJob;
import com.path.android.jobqueue.JobHolder;
import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.Params;
import com.path.android.jobqueue.config.Configuration;
import com.path.android.jobqueue.di.DependencyInjector;
import com.path.android.jobqueue.test.jobs.DummyJob;
import com.path.android.jobqueue.test.jobs.PersistentDummyJob;
import static org.hamcrest.CoreMatchers.*;
import org.hamcrest.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;

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
        jobManager.addJob(new PersistentDummyJob(new Params(1)));
        MatcherAssert.assertThat("injection should be called after adding a persistent job", injectionCallCount.get(), equalTo(2));
        JobHolder holder = getNextJobMethod(jobManager).invoke();
        MatcherAssert.assertThat("injection should NOT be called for non persistent job", holder.getBaseJob(), not(injectedJobReference.getObject()));
        MatcherAssert.assertThat("injection should be called once for non persistent job", injectionCallCount.get(), equalTo(2));
        holder = getNextJobMethod(jobManager).invoke();
        MatcherAssert.assertThat("injection should be called for persistent job", holder.getBaseJob(), equalTo(injectedJobReference.getObject()));
        MatcherAssert.assertThat("injection should be called two times for persistent job", injectionCallCount.get(), equalTo(3));

    }
}
