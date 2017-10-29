package com.birbit.android.jobqueue.test.jobmanager;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.birbit.android.jobqueue.CancelReason;
import com.birbit.android.jobqueue.CancelResult;
import com.birbit.android.jobqueue.Job;
import com.birbit.android.jobqueue.JobHolder;
import com.birbit.android.jobqueue.JobManager;
import com.birbit.android.jobqueue.Params;
import com.birbit.android.jobqueue.RetryConstraint;
import com.birbit.android.jobqueue.TagConstraint;
import com.birbit.android.jobqueue.config.Configuration;
import com.birbit.android.jobqueue.di.DependencyInjector;
import com.birbit.android.jobqueue.test.jobs.DummyJob;

import org.hamcrest.MatcherAssert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.model.MultipleFailureException;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(RobolectricTestRunner.class)
@Config(constants = com.birbit.android.jobqueue.BuildConfig.class)
public class InjectorTest extends JobManagerTestBase {
    static List<Throwable> errors = new ArrayList<>();
    static final JobManagerTestBase.ObjectReference injectedJobReference = new JobManagerTestBase.ObjectReference();

    @Test
    public void testInjector() throws Throwable {
        Configuration.Builder builder = new Configuration.Builder(RuntimeEnvironment.application);
        final AtomicInteger injectionCallCount = new AtomicInteger(0);
        DependencyInjector dependencyInjector = new DependencyInjector() {
            @Override
            public void inject(Job job) {
                injectedJobReference.setObject(job);
                injectionCallCount.incrementAndGet();
            }
        };
        builder.injector(dependencyInjector);
        builder.timer(mockTimer);
        JobManager jobManager = createJobManager(builder);
        jobManager.stop();
        jobManager.addJob(new DummyJob(new Params(4)));
        MatcherAssert.assertThat("injection should be called after adding a non-persistent job", injectionCallCount.get(), equalTo(1));
        jobManager.addJob(new DummyJob(new Params(1).persist()));
        MatcherAssert.assertThat("injection should be called after adding a persistent job", injectionCallCount.get(), equalTo(2));
        JobHolder holder = nextJob(jobManager);
        MatcherAssert.assertThat("injection should NOT be called for non persistent job", holder.getJob(), not(injectedJobReference.getObject()));
        MatcherAssert.assertThat("injection should be called once for non persistent job", injectionCallCount.get(), equalTo(2));
        holder = nextJob(jobManager);
        MatcherAssert.assertThat("injection should be called for persistent job", holder.getJob(), equalTo(injectedJobReference.getObject()));
        MatcherAssert.assertThat("injection should be called two times for persistent job", injectionCallCount.get(), equalTo(3));

        jobManager.addJob(new InjectionCheckJob(new Params(1).addTags("dummyNonPersistedTag")));
        CancelResult cancelResult = jobManager.cancelJobs(TagConstraint.ANY, "dummyNonPersistedTag");
        assertThat("Could not cancel non persistent job", cancelResult.getCancelledJobs().size() == 1);
        MatcherAssert.assertThat("injection should be called only once for non persistent job", injectionCallCount.get(), equalTo(4));

        jobManager.addJob(new InjectionCheckJob(new Params(1).addTags("dummyPersistedTag").persist()));
        jobManager.addJob(new InjectionCheckJob(new Params(4))); //adding new job to override injection of above job.
        cancelResult = jobManager.cancelJobs(TagConstraint.ANY, "dummyPersistedTag");
        assertThat("Could not cancel persistent job", cancelResult.getCancelledJobs().size() == 1);
        for (Job job : cancelResult.getCancelledJobs()) {
            MatcherAssert.assertThat("injection should be called for persistent job", job, equalTo(injectedJobReference.getObject()));
            MatcherAssert.assertThat("injection should be called two times for persistent job", injectionCallCount.get(), equalTo(7));
        }
        if (!errors.isEmpty()) {
            throw new MultipleFailureException(errors);
        }
    }

    public static class InjectionCheckJob extends Job {
        protected InjectionCheckJob(Params params) {
            super(params);
        }

        private void assertInjection(String method) {
            try {
                assertThat("Injected job instance should be same as job instance in " + method,
                        injectedJobReference.getObject(), sameInstance((Object) this));
            } catch (Throwable t) {
                errors.add(t);
            }
        }

        @Override
        public void onAdded() {
        }

        @Override
        public void onRun() throws Throwable {
        }

        @Override
        protected void onCancel(@CancelReason int cancelReason, @Nullable Throwable throwable) {
            assertInjection("onCancel");
        }

        @Override
        protected RetryConstraint shouldReRunOnThrowable(@NonNull Throwable throwable, int runCount, int maxRunCount) {
            return RetryConstraint.CANCEL;
        }
    }
}
