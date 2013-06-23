package com.path.android.jobqueue.test;


import android.content.Context;
import android.util.Log;
import com.path.android.jobqueue.BaseJob;
import com.path.android.jobqueue.JobHolder;
import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.JobQueue;
import com.path.android.jobqueue.config.Configuration;
import com.path.android.jobqueue.di.DependencyInjector;
import com.path.android.jobqueue.log.JqLog;
import com.path.android.jobqueue.network.NetworkEventProvider;
import com.path.android.jobqueue.network.NetworkUtil;
import com.path.android.jobqueue.persistentQueue.sqlite.SqliteJobQueue;
import com.path.android.jobqueue.test.jobs.DummyJob;
import com.path.android.jobqueue.test.jobs.PersistentDummyJob;
import org.fest.reflect.core.Reflection;
import org.fest.reflect.method.Invoker;
import org.hamcrest.MatcherAssert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowLog;

import java.util.Collection;
import java.util.LinkedList;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.CoreMatchers.*;

@RunWith(RobolectricTestRunner.class)
public class MainRoboTest {
    //TEST parallel running
    @Test
    public void runManyNonPersistentJobs() throws Exception {
        JobManager jobManager = createJobManager();
        jobManager.stop();
        int limit = 2;
        final CountDownLatch latch = new CountDownLatch(limit);
        for (int i = 0; i < limit; i++) {
            jobManager.addJob(i, new DummyLatchJob(latch));
        }
        jobManager.start();
        latch.await(10, TimeUnit.SECONDS);
        MatcherAssert.assertThat((int) latch.getCount(), equalTo(0));
    }

    private JobManager createJobManager() {
        return new JobManager(Robolectric.application, UUID.randomUUID().toString());
    }

    private JobManager createJobManager(Configuration configuration) {
        return new JobManager(Robolectric.application, configuration.withId(UUID.randomUUID().toString()));
    }


    @Before
    public void setUp() throws Exception {
        ShadowLog.stream = System.out;
        JqLog.getConfig().setLoggingLevel(Log.DEBUG);
    }


    private static AtomicInteger multiThreadedJobCounter;
    @Test
    public void testMultiThreaded() throws Exception {
        multiThreadedJobCounter = new AtomicInteger(0);
        final JobManager jobManager = createJobManager();
        int limit = 200;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        Collection<Future<?>> futures = new LinkedList<Future<?>>();
        for(int i = 0; i < limit; i++) {
            final int id = i;
            futures.add(executor.submit(new Runnable() {
                @Override
                public void run() {
                    final boolean persistent = Math.round(Math.random()) % 2 == 0;
                    boolean requiresNetwork = Math.round(Math.random()) % 2 == 0;
                    int priority = (int) (Math.round(Math.random()) % 10);
                    multiThreadedJobCounter.incrementAndGet();
                    jobManager.addJob(priority, new DummyJobForMultiThread(id, requiresNetwork, persistent));
                }
            }));
        }
        for (Future<?> future:futures) {
            future.get();
        }
        Log.d("TAG", "added all jobs");
        //wait until all jobs are added
        long start = System.nanoTime();
        long timeLimit = JobManager.NS_PER_MS * 20000;//20 seconds
        while(System.nanoTime() - start < timeLimit && multiThreadedJobCounter.get() != 0) {
            Thread.sleep(1000);
        }
        Log.d("TAG", "did we reach timeout? " + (System.nanoTime() - start >= timeLimit));

        MatcherAssert.assertThat("jobmanager count should be 0",
                jobManager.count(), equalTo(0));

        MatcherAssert.assertThat("multiThreadedJobCounter should be 0",
                multiThreadedJobCounter.get(), equalTo(0));

    }

    public static class DummyJobForMultiThread extends DummyJob {
        private int id;
        private boolean persist;
        private DummyJobForMultiThread(int id, boolean requiresNetwork, boolean persist) {
            super(requiresNetwork);
            this.persist = persist;
            this.id = id;
        }

        @Override
        public boolean shouldPersist() {
            return persist;
        }

        @Override
        public void onRun() throws Throwable {
            super.onRun();
            int remaining = multiThreadedJobCounter.decrementAndGet();
            Log.d("DummyJobForMultiThread", "persistent:" + persist + ", requires network:" + requiresNetwork() + ", running " + id + ", remaining: " + remaining);
        }
    };

    @Test
    public void runFailingJob() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        JobManager jobManager = createJobManager();
        jobManager.addJob(0, new BaseJob(true) {
            @Override
            public void onAdded() {

            }

            @Override
            public void onRun() throws Throwable {
                throw new RuntimeException();
            }

            @Override
            public boolean shouldPersist() {
                return false;
            }

            @Override
            protected void onCancel() {
                latch.countDown();
            }

            @Override
            protected boolean shouldReRunOnThrowable(Throwable throwable) {
                return false;
            }
        });
        latch.await(10, TimeUnit.SECONDS);
        MatcherAssert.assertThat((int) latch.getCount(), equalTo(0));
    }

    @Test
    public void testInjector() throws Exception {
        Configuration configuration = JobManager.createDefaultConfiguration();
        final ObjectReference injectedJobReference = new ObjectReference();
        final AtomicInteger injectionCallCount = new AtomicInteger(0);
        DependencyInjector dependencyInjector = new DependencyInjector() {
            @Override
            public void inject(BaseJob job) {
                injectedJobReference.setObject(job);
                injectionCallCount.incrementAndGet();
            }
        };
        configuration.withInjector(dependencyInjector);
        JobManager jobManager = createJobManager(configuration);
        jobManager.stop();
        jobManager.addJob(4, new DummyJob());
        MatcherAssert.assertThat("injection should be called after adding a non-persistent job", injectionCallCount.get(), equalTo(1));
        jobManager.addJob(1, new PersistentDummyJob());
        MatcherAssert.assertThat("injection should be called after adding a persistent job", injectionCallCount.get(), equalTo(2));
        JobHolder holder = getNextJobMethod(jobManager).invoke();
        MatcherAssert.assertThat("injection should NOT be called for non persistent job", holder.getBaseJob(), not(injectedJobReference.getObject()));
        MatcherAssert.assertThat("injection should be called once for non persistent job", injectionCallCount.get(), equalTo(2));
        holder = getNextJobMethod(jobManager).invoke();
        MatcherAssert.assertThat("injection should be called for persistent job", holder.getBaseJob(), equalTo(injectedJobReference.getObject()));
        MatcherAssert.assertThat("injection should be called two times for persistent job", injectionCallCount.get(), equalTo(3));

    }

    @Test
    public void testClear() throws Exception {
        JobManager jobManager = createJobManager();
        final int LIMIT = 20;
        for(int i = 0; i < LIMIT; i++) {
            if(i % 2 == 0) {
                jobManager.addJob(0, new DummyJob());
            } else {
                jobManager.addJob(0, new PersistentDummyJob());
            }
        }
        jobManager.clear();
        MatcherAssert.assertThat("after clear, count should be 0", jobManager.count(), equalTo(0));
    }

    @Test
    public void testDelay() throws Exception {
        testDelay(false);
        testDelay(true);
    }

    public void testDelay(boolean persist) throws Exception {
        JobManager jobManager = createJobManager();
        jobManager.stop();
        DummyJob delayedJob = persist ? new PersistentDummyJob() : new DummyJob();
        DummyJob nonDelayedJob = persist ? new PersistentDummyJob() : new DummyJob();
        long jobId = jobManager.addJob(10, 1000, delayedJob);
        long nonDelayedJobId = jobManager.addJob(0, 0, nonDelayedJob);

        Invoker<JobHolder> nextJobMethod = getNextJobMethod(jobManager);
        Invoker<Void> removeJobMethod = getRemoveJobMethod(jobManager);

        JobHolder receivedJob = nextJobMethod.invoke();
        MatcherAssert.assertThat("non-delayed job should be served",  receivedJob, notNullValue());
        MatcherAssert.assertThat("non-delayed job should id should match",  receivedJob.getId(), equalTo(nonDelayedJobId));
        removeJobMethod.invoke(receivedJob);
        MatcherAssert.assertThat("delayed job should not be served",  nextJobMethod.invoke(), nullValue());
        MatcherAssert.assertThat("job count should still be 1",  jobManager.count(), equalTo(1));
        Thread.sleep(500);
        MatcherAssert.assertThat("delayed job should not be served",  nextJobMethod.invoke(), nullValue());
        MatcherAssert.assertThat("job count should still be 1",  jobManager.count(), equalTo(1));
        Thread.sleep(1000);
        receivedJob = nextJobMethod.invoke();
        MatcherAssert.assertThat("now should be able to receive the delayed job", receivedJob, notNullValue());
        if(receivedJob != null) {
            MatcherAssert.assertThat("received job should be the delayed job", receivedJob.getId(), equalTo(jobId));
        }
    }

    @Test
    public void testDelayedRun() throws Exception {
        testDelayedRun(false);
        testDelayedRun(true);
    }
    public void testDelayedRun(boolean persist) throws Exception {
        JobManager jobManager = createJobManager();
        DummyJob delayedJob = persist ? new PersistentDummyJob() : new DummyJob();
        DummyJob nonDelayedJob = persist ? new PersistentDummyJob() : new DummyJob();
        jobManager.addJob(10, 2000, delayedJob);
        jobManager.addJob(0, 0, nonDelayedJob);
        Thread.sleep(500);
        MatcherAssert.assertThat("there should be 1 delayed job waiting to be run", jobManager.count(), equalTo(1));
        Thread.sleep(3000);
        MatcherAssert.assertThat("all jobs should be completed", jobManager.count(), equalTo(0));

    }

    @Test
    public void testNetworkNextJob() throws Exception {
        DummyNetworkUtil dummyNetworkUtil = new DummyNetworkUtil();
        JobManager jobManager = createJobManager(JobManager.createDefaultConfiguration().withNetworkUtil(dummyNetworkUtil));
        jobManager.stop();
        DummyJob dummyJob = new DummyJob(true);
        long dummyJobId = jobManager.addJob(0, dummyJob);
        dummyNetworkUtil.setHasNetwork(false);
        Invoker<JobHolder> nextJobMethod = getNextJobMethod(jobManager);
        MatcherAssert.assertThat("when there isn't any network, next job should return null", nextJobMethod.invoke(), nullValue());
        MatcherAssert.assertThat("even if there is network, job manager should return correct count", jobManager.count(), equalTo(1));
        dummyNetworkUtil.setHasNetwork(true);
        JobHolder retrieved = nextJobMethod.invoke();
        MatcherAssert.assertThat("when network is recovered, next job should be retrieved", retrieved, notNullValue());
    }


    @Test
    public void testNetworkJobWithConnectivityListener() throws Exception {
        DummyNetworkUtilWithConnectivityEventSupport dummyNetworkUtil = new DummyNetworkUtilWithConnectivityEventSupport();
        JobManager jobManager = createJobManager(JobManager.createDefaultConfiguration().withNetworkUtil(dummyNetworkUtil));
        dummyNetworkUtil.setHasNetwork(false, true);
        DummyJob dummyJob = new DummyJob(true);
        long dummyJobId = jobManager.addJob(0, dummyJob);
        Thread.sleep(2000);//sleep a while so that consumers die. they should die since we are using a network util
                           //with event support
        MatcherAssert.assertThat("count should be 1 as no jobs should be consumed w/o network", jobManager.count(), equalTo(1));
        dummyNetworkUtil.setHasNetwork(true, false);
        Thread.sleep(1000); //wait a little bit more to consumer will run
        MatcherAssert.assertThat("even though network is recovered, job manager should not consume any job because it " +
                "does not know (we did not inform)", jobManager.count(), equalTo(1));
        dummyNetworkUtil.setHasNetwork(true, true);
        Thread.sleep(1000); //wait a little bit more to consumer will run
        MatcherAssert.assertThat("job manager should consume network job after it is informed that network is recovered"
                , jobManager.count(), equalTo(0));
    }

    @Test
    public void testNetworkJob() throws Exception {
        DummyNetworkUtil dummyNetworkUtil = new DummyNetworkUtil();
        JobManager jobManager = createJobManager(JobManager.createDefaultConfiguration().withNetworkUtil(dummyNetworkUtil));
        jobManager.stop();

        DummyJob networkDummyJob = new DummyJob(true);
        jobManager.addJob(5, networkDummyJob);

        DummyJob noNetworkDummyJob = new DummyJob(false);
        jobManager.addJob(2, noNetworkDummyJob);

        PersistentDummyJob networkPersistentJob = new PersistentDummyJob(true);
        jobManager.addJob(6, networkPersistentJob);

        PersistentDummyJob noNetworkPersistentJob = new PersistentDummyJob(false);
        jobManager.addJob(1, noNetworkPersistentJob);

        MatcherAssert.assertThat("count should be correct if there are network and non-network jobs w/o network", jobManager.count(), equalTo(4));
        dummyNetworkUtil.setHasNetwork(true);
        MatcherAssert.assertThat("count should be correct if there is network and non-network jobs w/o network", jobManager.count(), equalTo(4));
        dummyNetworkUtil.setHasNetwork(false);
        jobManager.start();
        Thread.sleep(1000);//this should be enough to consume dummy jobs
        MatcherAssert.assertThat("no network jobs should be executed even if there is no network", jobManager.count(), equalTo(2));
        dummyNetworkUtil.setHasNetwork(true);
        Thread.sleep(1000);//this should be enough to consume dummy jobs
        MatcherAssert.assertThat("when network is recovered, all network jobs should be automatically consumed", jobManager.count(), equalTo(0));
    }

    public static CountDownLatch persistentRunLatch = new CountDownLatch(1);

    @Test
    public void testPersistentJob() throws Exception {
        JobManager jobManager = createJobManager();
        jobManager.addJob(0, new DummyPersistentLatchJob());
        persistentRunLatch.await(5, TimeUnit.SECONDS);
        MatcherAssert.assertThat((int) persistentRunLatch.getCount(), equalTo(0));
    }

    private static CountDownLatch priorityRunLatch;

    @Test
    public void testPriority() throws Exception {
        JobManager jobManager = createJobManager();
        jobManager.setMaxConsumerCount(1);
        testPriority(jobManager, false);
    }

    public void testPriority(JobManager jobManager, boolean persist) throws Exception {
        priorityRunLatch = new CountDownLatch(2);
        DummyJobWithRunOrderAssert.globalRunCount = new AtomicInteger(0);
        BaseJob job1 = new DummyJobWithRunOrderAssert(2, persist);
        BaseJob job2 = new DummyJobWithRunOrderAssert(1, persist);
        jobManager.stop();
        jobManager.addJob(1, job1);
        jobManager.addJob(2, job2);
        jobManager.start();
        priorityRunLatch.await(4, TimeUnit.SECONDS);
        //ensure both jobs did run
        MatcherAssert.assertThat((int) priorityRunLatch.getCount(), equalTo(0));
    }

    @Test
    public void testCount() throws Exception {
        JobManager jobManager = createJobManager();
        jobManager.stop();
        for (int i = 0; i < 10; i++) {
            jobManager.addJob(0, new PersistentDummyJob());
            MatcherAssert.assertThat((int) jobManager.count(), equalTo(i * 2 + 1));
            jobManager.addJob(0, new PersistentDummyJob());
            MatcherAssert.assertThat((int) jobManager.count(), equalTo(i * 2 + 2));
        }
        jobManager.start();
        Thread.sleep(2000);
        MatcherAssert.assertThat((int) jobManager.count(), equalTo(0));
    }

    @Test
    public void testSessionId() throws Exception {
        JobManager jobManager = createJobManager();
        Long sessionId = Reflection.field("sessionId").ofType(long.class)
                .in(jobManager).get();
        jobManager.stop();
        BaseJob[] jobs = new BaseJob[]{new DummyJob(), new PersistentDummyJob()};
        for (BaseJob job : jobs) {
            jobManager.addJob(0, job);
        }

        Invoker<JobHolder> nextJobMethod = getNextJobMethod(jobManager);
        for (int i = 0; i < jobs.length; i++) {
            JobHolder jobHolder = nextJobMethod.invoke();
            MatcherAssert.assertThat("session id should be correct for job " + i, jobHolder.getRunningSessionId(), equalTo(sessionId));
        }
    }

    private Invoker<JobHolder> getNextJobMethod(JobManager jobManager) {
        return Reflection.method("getNextJob").withReturnType(JobHolder.class).in(jobManager);
    }

    private Invoker<Void> getRemoveJobMethod(JobManager jobManager) {
        return Reflection.method("removeJob").withParameterTypes(JobHolder.class).in(jobManager);
    }

    @Test
    public void testAddedCount() throws Exception {
        testAddedCount(new DummyJob());
        testAddedCount(new PersistentDummyJob());

    }

    private void testAddedCount(DummyJob dummyJob) {
        JobManager jobManager = createJobManager();
        jobManager.stop();
        jobManager.addJob(0, dummyJob);
        MatcherAssert.assertThat(1, equalTo(dummyJob.getOnAddedCnt()));
    }


    @Test
    public void testReRunWithLimit() throws Exception {
        JobManager jobManager = createJobManager();
        testReRun(jobManager, false);
        testReRun(jobManager, true);
    }

    private void testReRun(JobManager jobManager, boolean persist) throws InterruptedException {
        DummyJobWithRunCount.runCount = 0;//reset
        DummyJobWithRunCount job = new DummyJobWithRunCount(persist);
        jobManager.addJob(0, job);
        int limit = 25;
        while (limit-- > 0 && DummyJobWithRunCount.runCount != 5) {
            Thread.sleep(100);
        }
        MatcherAssert.assertThat(DummyJobWithRunCount.runCount, equalTo(job.getRetryLimit()));
        MatcherAssert.assertThat((int) jobManager.count(), equalTo(0));
    }

    private static class DummyPersistentLatchJob extends PersistentDummyJob {

        @Override
        public void onRun() throws Throwable {
            MainRoboTest.persistentRunLatch.countDown();
        }
    }

    private static class DummyLatchJob extends DummyJob {
        private final CountDownLatch latch;

        private DummyLatchJob(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public void onRun() throws Throwable {
            super.onRun();
            latch.countDown();
        }
    }


    private static class DummyJobWithRunCount extends DummyJob {
        public static int runCount;
        private boolean persist;

        private DummyJobWithRunCount(boolean persist) {
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


    public static class DummyJobWithRunOrderAssert extends BaseJob {
        transient public static AtomicInteger globalRunCount;
        private int expectedRunOrder;
        private boolean persist;

        public DummyJobWithRunOrderAssert(int expectedRunOrder, boolean persist) {
            super(true);
            this.expectedRunOrder = expectedRunOrder;
            this.persist = persist;
        }

        @Override
        public void onAdded() {
        }

        @Override
        public void onRun() throws Throwable {
            final int cnt = globalRunCount.incrementAndGet();
            MatcherAssert.assertThat(expectedRunOrder, equalTo(cnt));
            priorityRunLatch.countDown();
        }

        @Override
        public boolean shouldPersist() {
            return persist;
        }

        @Override
        protected void onCancel() {

        }

        @Override
        protected boolean shouldReRunOnThrowable(Throwable throwable) {
            return false;
        }
    }

    private static class DummyNetworkUtil implements NetworkUtil {
        private boolean hasNetwork;

        private void setHasNetwork(boolean hasNetwork) {
            this.hasNetwork = hasNetwork;
        }

        @Override
        public boolean isConnected(Context context) {
            return hasNetwork;
        }
    }

    private static class DummyNetworkUtilWithConnectivityEventSupport implements NetworkUtil, NetworkEventProvider {
        private boolean hasNetwork;
        private Listener listener;

        private void setHasNetwork(boolean hasNetwork, boolean notifyListener) {
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

    private static class ObjectReference {
        Object object;

        private Object getObject() {
            return object;
        }

        private void setObject(Object object) {
            this.object = object;
        }
    }

}
