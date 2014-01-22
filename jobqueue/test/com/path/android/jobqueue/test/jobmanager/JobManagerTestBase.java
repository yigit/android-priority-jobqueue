package com.path.android.jobqueue.test.jobmanager;

import android.content.Context;
import com.path.android.jobqueue.JobHolder;
import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.Params;
import com.path.android.jobqueue.config.Configuration;
import com.path.android.jobqueue.executor.JobConsumerExecutor;
import com.path.android.jobqueue.network.NetworkEventProvider;
import com.path.android.jobqueue.network.NetworkUtil;
import com.path.android.jobqueue.test.TestBase;
import com.path.android.jobqueue.test.jobs.DummyJob;
import org.fest.reflect.core.*;
import org.fest.reflect.method.*;
import static org.hamcrest.CoreMatchers.*;
import org.hamcrest.*;
import org.robolectric.*;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class JobManagerTestBase extends TestBase {
    protected JobManager createJobManager() {
        return new JobManager(Robolectric.application, UUID.randomUUID().toString());
    }

    protected JobManager createJobManager(Configuration.Builder configurationBuilder) {
        return new JobManager(Robolectric.application, configurationBuilder.id(UUID.randomUUID().toString()).build());
    }



    protected static class DummyTwoLatchJob extends DummyJob {
        private final CountDownLatch waitFor;
        private final CountDownLatch trigger;
        private final CountDownLatch onRunLatch;

        protected DummyTwoLatchJob(Params params, CountDownLatch waitFor, CountDownLatch trigger) {
            super(params);
            this.waitFor = waitFor;
            this.trigger = trigger;
            onRunLatch = new CountDownLatch(1);
        }

        public void waitTillOnRun() throws InterruptedException {
            onRunLatch.await();
        }

        @Override
        public void onRun() throws Throwable {
            onRunLatch.countDown();
            waitFor.await();
            super.onRun();
            trigger.countDown();
        }
    }

    protected static class DummyLatchJob extends DummyJob {
        private final CountDownLatch latch;

        protected DummyLatchJob(Params params, CountDownLatch latch) {
            super(params);
            this.latch = latch;
        }

        @Override
        public void onRun() throws Throwable {
            super.onRun();
            latch.countDown();
        }
    }


    protected static class DummyJobWithRunCount extends DummyJob {
        public static int runCount;
        protected DummyJobWithRunCount(boolean persistent) {
            super(new Params(0).setPersistent(persistent));
        }

        @Override
        public void onRun() throws Throwable {
            runCount++;
            super.onRun();
            throw new RuntimeException("i am dummy, i throw exception when running");
        }

        @Override
        protected boolean shouldReRunOnThrowable(Throwable throwable) {
            return true;
        }

        @Override
        protected int getRetryLimit() {
            return 5;
        }
    }




    protected static class DummyNetworkUtil implements NetworkUtil {
        private boolean hasNetwork;

        protected void setHasNetwork(boolean hasNetwork) {
            this.hasNetwork = hasNetwork;
        }

        @Override
        public boolean isConnected(Context context) {
            return hasNetwork;
        }
    }

    protected static class DummyNetworkUtilWithConnectivityEventSupport implements NetworkUtil, NetworkEventProvider {
        private boolean hasNetwork;
        private Listener listener;

        protected void setHasNetwork(boolean hasNetwork, boolean notifyListener) {
            this.hasNetwork = hasNetwork;
            if(notifyListener && listener != null) {
                listener.onNetworkChange(hasNetwork);
            }
        }

        @Override
        public boolean isConnected(Context context) {
            return hasNetwork;
        }

        @Override
        public void setListener(Listener listener) {
            this.listener = listener;
        }

        public boolean isConnected() {
            return hasNetwork;
        }
    }

    protected static class ObjectReference {
        Object object;

        Object getObject() {
            return object;
        }

        void setObject(Object object) {
            this.object = object;
        }
    }

    protected Invoker<JobHolder> getNextJobMethod(JobManager jobManager) {
        return Reflection.method("getNextJob").withReturnType(JobHolder.class).in(jobManager);
    }

    protected Invoker<Void> getRemoveJobMethod(JobManager jobManager) {
        return Reflection.method("removeJob").withParameterTypes(JobHolder.class).in(jobManager);
    }

    protected JobConsumerExecutor getConsumerExecutor(JobManager jobManager) {
        return Reflection.field("jobConsumerExecutor").ofType(JobConsumerExecutor.class).in(jobManager).get();
    }

    protected org.fest.reflect.field.Invoker<AtomicInteger> getActiveConsumerCount(JobConsumerExecutor jobConsumerExecutor) {
        return Reflection.field("activeConsumerCount").ofType(AtomicInteger.class).in(jobConsumerExecutor);
    }

    public static class NeverEndingDummyJob extends DummyJob {
        final Object lock;
        final Semaphore semaphore;
        public NeverEndingDummyJob(Params params, Object lock, Semaphore semaphore) {
            super(params);
            this.lock = lock;
            this.semaphore = semaphore;
        }

        @Override
        public void onRun() throws Throwable {
            super.onRun();
            MatcherAssert.assertThat("job should be able to acquire a semaphore",
                    semaphore.tryAcquire(), equalTo(true));
            synchronized (lock) {
                lock.wait();
            }
            semaphore.release();
        }
    }
}
