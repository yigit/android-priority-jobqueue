package com.birbit.android.jobqueue.test.jobmanager;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;

import com.birbit.android.jobqueue.BuildConfig;
import com.birbit.android.jobqueue.JobManager;
import com.birbit.android.jobqueue.JobManagerThreadRunnable;
import com.birbit.android.jobqueue.RetryConstraint;
import com.birbit.android.jobqueue.testing.CleanupRule;
import com.birbit.android.jobqueue.Job;
import com.birbit.android.jobqueue.JobHolder;
import com.birbit.android.jobqueue.Params;
import com.birbit.android.jobqueue.callback.JobManagerCallbackAdapter;
import com.birbit.android.jobqueue.config.Configuration;
import com.birbit.android.jobqueue.network.NetworkEventProvider;
import com.birbit.android.jobqueue.network.NetworkUtil;
import com.birbit.android.jobqueue.test.TestBase;
import com.birbit.android.jobqueue.test.jobs.DummyJob;
import com.birbit.android.jobqueue.test.timer.MockTimer;
import com.birbit.android.jobqueue.testing.CollectLogsRule;

import static org.hamcrest.CoreMatchers.*;

import org.hamcrest.*;
import org.junit.Rule;
import org.junit.rules.Timeout;
import org.robolectric.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;


public class JobManagerTestBase extends TestBase {
    List<JobManager> createdJobManagers = new ArrayList<JobManager>();
    final MockTimer mockTimer = new MockTimer();
    @Rule public CleanupRule cleanup = new CleanupRule(this);
    @Rule public Timeout timeout = Timeout.seconds(getTimeout());
    @Rule public CollectLogsRule collectLogsRule = new CollectLogsRule();


    protected int getActiveConsumerCount(JobManager jobManager) {
        return jobManager.getActiveConsumerCount();
    }

    protected long getTimeout() {
        return 60;
    }

    protected JobManager createJobManager() {
        if(createdJobManagers.size() > 0) {
            throw new AssertionError("only 1 job manager per test");
        }
        final JobManager jobManager = createJobManager(new Configuration.Builder(RuntimeEnvironment.application)
            .timer(mockTimer)
            .inTestMode());
        createdJobManagers.add(jobManager);
        return jobManager;
    }

    protected JobManager createJobManager(Configuration.Builder configurationBuilder) {
        if(createdJobManagers.size() > 0) {
            throw new AssertionError("only 1 job manager per test");
        }
        if (BuildConfig.DEBUG) {
            configurationBuilder.customLogger(collectLogsRule.logger);
        }
        Configuration config = configurationBuilder.inTestMode().id(UUID.randomUUID().toString())
                .build();
        if (config.getTimer() != mockTimer && !canUseRealTimer()) {
            throw new IllegalArgumentException("must use mock timer");
        }
        final JobManager jobManager = new JobManager(config);
        createdJobManagers.add(jobManager);

        return jobManager;
    }

    public List<JobManager> getCreatedJobManagers() {
        return createdJobManagers;
    }

    public MockTimer getMockTimer() {
        return mockTimer;
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

    protected JobHolder nextJob(JobManager jobManager) throws Throwable {
        return new JobManagerThreadRunnable<JobHolder>(jobManager) {
            @Override
            public JobHolder onRun() {
                return getNextJob();
            }
        }.run();
    }

    protected JobHolder nextJob(JobManager jobManager, final Collection<String> exclude) throws Throwable {
        return new JobManagerThreadRunnable<JobHolder>(jobManager) {
            @Override
            public JobHolder onRun() {
                return getNextJob(exclude);
            }
        }.run();
    }

    protected void removeJob(final JobManager jobManager, final JobHolder holder) throws Throwable {
        new JobManagerThreadRunnable<Void>(jobManager) {
            @Override
            public Void onRun() {
                removeJob(holder);
                return null;
            }
        }.run();
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
            throw new RuntimeException("i am dummy, i throw exception when running " + runCount);
        }

        @Override
        protected RetryConstraint shouldReRunOnThrowable(@NonNull Throwable throwable, int runCount, int maxRunCount) {
            return RetryConstraint.RETRY;
        }

        @Override
        protected int getRetryLimit() {
            return 5;
        }
    }

    protected static class DummyNetworkUtil implements NetworkUtil {
        private int networkStatus;

        protected void setNetworkStatus(int networkStatus) {
            this.networkStatus = networkStatus;
        }

        @Override
        public int getNetworkStatus(Context context) {
            return networkStatus;
        }
    }

    protected static class DummyNetworkUtilWithConnectivityEventSupport extends DummyNetworkUtil
            implements NetworkUtil, NetworkEventProvider {
        private int networkStatus;
        private Listener listener;

        protected void setNetworkStatus(int networkStatus, boolean notifyListener) {
            this.networkStatus = networkStatus;
            if(notifyListener && listener != null) {
                listener.onNetworkChange(networkStatus);
            }
        }

        @Override
        protected void setNetworkStatus(int networkStatus) {
            setNetworkStatus(networkStatus, true);
        }

        @Override
        public int getNetworkStatus(Context context) {
            return networkStatus;
        }

        public boolean isDisconnected() {
            return networkStatus == DISCONNECTED;
        }

        @Override
        public void setListener(Listener listener) {
            this.listener = listener;
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

    public static class NeverEndingDummyJob extends DummyJob {
        // used for cleanup
        static List<NeverEndingDummyJob> createdJobs = new ArrayList<>();
        final CountDownLatch lock;
        final Semaphore semaphore;
        public NeverEndingDummyJob(Params params, CountDownLatch lock, Semaphore semaphore) {
            super(params);
            this.lock = lock;
            this.semaphore = semaphore;
            createdJobs.add(this);
        }

        @TargetApi(Build.VERSION_CODES.GINGERBREAD)
        @Override
        public void onRun() throws Throwable {
            super.onRun();
            MatcherAssert.assertThat("job should be able to acquire a semaphore",
                    semaphore.tryAcquire(), equalTo(true));
            lock.await(1, TimeUnit.MINUTES);
            semaphore.release();
        }

        public static void unlockAll() {
            for (NeverEndingDummyJob job : createdJobs) {
                job.lock.countDown();
            }
        }
    }

    protected boolean canUseRealTimer() {
        return false;
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    protected void waitUntilJobsAreDone(final JobManager jobManager, List<? extends Job> jobs, Runnable action)
            throws InterruptedException {
        final Set<String> uuids = new HashSet<>();
        for (Job job : jobs) {
            uuids.add(job.getId());
        }
        final CountDownLatch latch = new CountDownLatch(uuids.size());
        jobManager.addCallback(new JobManagerCallbackAdapter() {
            @Override
            public void onDone(@NonNull Job job) {
                if (uuids.remove(job.getId())) {
                    latch.countDown();
                }
            }
        });
        if (action != null) {
            action.run();
        }
        MatcherAssert.assertThat("Jobs should be done", latch.await(1, TimeUnit.MINUTES), is(true));
    }

    @SuppressLint("NewApi")
    protected void waitUntilAJobIsDone(final JobManager jobManager, final WaitUntilCallback callback) throws InterruptedException {
        final CountDownLatch runJob = new CountDownLatch(1);
        final Throwable[] throwable = new Throwable[1];
        jobManager.addCallback(new JobManagerCallbackAdapter() {
            @Override
            public void onDone(@NonNull Job job) {
                synchronized (this) {
                    super.onDone(job);
                    if (callback != null) {
                        try {
                            callback.assertJob(job);
                        } catch (Throwable t) {
                            throwable[0] = t;
                        }
                    }
                    runJob.countDown();
                    jobManager.removeCallback(this);
                }
            }
        });
        if (callback != null) {
            callback.run();
        }
        MatcherAssert.assertThat("The job should be done", runJob.await(1, TimeUnit.MINUTES), is(true));
        MatcherAssert.assertThat("Job assertion failed", throwable[0], CoreMatchers.nullValue());
    }
    protected interface WaitUntilCallback {
        void run();
        void assertJob(Job job);
    }
}
