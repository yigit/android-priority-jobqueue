package com.path.android.jobqueue.test.jobmanager;

import android.content.Context;
import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.config.Configuration;
import com.path.android.jobqueue.network.NetworkEventProvider;
import com.path.android.jobqueue.network.NetworkUtil;
import com.path.android.jobqueue.test.TestBase;
import com.path.android.jobqueue.test.jobs.DummyJob;
import com.path.android.jobqueue.test.jobs.PersistentDummyJob;
import org.robolectric.Robolectric;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;

public class JobManagerTestBase extends TestBase {
    protected JobManager createJobManager() {
        return new JobManager(Robolectric.application, UUID.randomUUID().toString());
    }

    protected JobManager createJobManager(Configuration configuration) {
        return new JobManager(Robolectric.application, configuration.id(UUID.randomUUID().toString()));
    }




    protected static class DummyPersistentLatchJob extends PersistentDummyJob {

        @Override
        public void onRun() throws Throwable {
            JobManagerTest.persistentRunLatch.countDown();
        }
    }

    protected static class DummyLatchJob extends DummyJob {
        private final CountDownLatch latch;

        protected DummyLatchJob(CountDownLatch latch) {
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
        private boolean persist;

        protected DummyJobWithRunCount(boolean persist) {
            this.persist = persist;
        }

        @Override
        public void onRun() throws Throwable {
            runCount++;
            super.onRun();
            throw new RuntimeException("i am dummy, i throw exception when running");
        }

        @Override
        public boolean shouldPersist() {
            return persist;
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
}
