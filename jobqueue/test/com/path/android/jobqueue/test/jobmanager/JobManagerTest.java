package com.path.android.jobqueue.test.jobmanager;


import android.content.Context;
import android.util.Log;
import com.path.android.jobqueue.BaseJob;
import com.path.android.jobqueue.JobHolder;
import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.config.Configuration;
import com.path.android.jobqueue.di.DependencyInjector;
import com.path.android.jobqueue.executor.JobConsumerExecutor;
import com.path.android.jobqueue.log.CustomLogger;
import com.path.android.jobqueue.log.JqLog;
import com.path.android.jobqueue.network.NetworkEventProvider;
import com.path.android.jobqueue.network.NetworkUtil;
import com.path.android.jobqueue.test.jobs.DummyJob;
import com.path.android.jobqueue.test.jobs.PersistentDummyJob;
import org.fest.reflect.core.Reflection;
import org.fest.reflect.method.Invoker;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowLog;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.CoreMatchers.*;

@RunWith(RobolectricTestRunner.class)
public class JobManagerTest extends JobManagerTestBase {
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
        configuration.injector(dependencyInjector);
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
        Thread.sleep(2000);
        MatcherAssert.assertThat("job count should still be 1",  jobManager.count(), equalTo(1));
        receivedJob = nextJobMethod.invoke();
        MatcherAssert.assertThat("now should be able to receive the delayed job.", receivedJob, notNullValue());
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
        JobManager jobManager = createJobManager(JobManager.createDefaultConfiguration().networkUtil(dummyNetworkUtil));
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
        JobManager jobManager = createJobManager(JobManager.createDefaultConfiguration().networkUtil(dummyNetworkUtil));
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
        JobManager jobManager = createJobManager(JobManager.createDefaultConfiguration().networkUtil(dummyNetworkUtil));
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

    private JobConsumerExecutor getConsumerExecutor(JobManager jobManager) {
        return Reflection.field("jobConsumerExecutor").ofType(JobConsumerExecutor.class).in(jobManager).get();
    }

    private org.fest.reflect.field.Invoker<AtomicInteger> getActiveConsumerCount(JobConsumerExecutor jobConsumerExecutor) {
        return Reflection.field("activeConsumerCount").ofType(AtomicInteger.class).in(jobConsumerExecutor);
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

    public static class NeverEndingDummyJob extends DummyJob {
        final Object lock;
        final Semaphore semaphore;
        public NeverEndingDummyJob(Object lock, Semaphore semaphore) {
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

    @Test
    public void testMaxConsumerCount() throws Exception {
        int maxConsumerCount = 2;
        JobManager jobManager = createJobManager(JobManager.createDefaultConfiguration()
                .maxConsumerCount(maxConsumerCount)
                .loadFactor(maxConsumerCount));
        Object runLock = new Object();
        Semaphore semaphore = new Semaphore(maxConsumerCount);
        int totalJobCount = maxConsumerCount * 3;
        List<DummyJob> runningJobs = new ArrayList<DummyJob>(totalJobCount);
        for(int i = 0; i < totalJobCount; i ++) {
            DummyJob job = new NeverEndingDummyJob(runLock, semaphore);
            runningJobs.add(job);
            jobManager.addJob((int)(Math.random() * 3), job);
        }
        //wait till enough jobs start
        long now = System.nanoTime();
        long waitTill = now + TimeUnit.SECONDS.toNanos(10);
        while(System.nanoTime() < waitTill) {
            if(semaphore.availablePermits() == 0) {
                //enough # of jobs started
                break;
            }
        }
        //wait some more to ensure no more jobs are started
        Thread.sleep(TimeUnit.SECONDS.toMillis(3));
        int totalRunningCount = 0;
        for(DummyJob job : runningJobs) {
            totalRunningCount += job.getOnRunCnt();
        }
        MatcherAssert.assertThat("only maxConsumerCount jobs should start", totalRunningCount, equalTo(maxConsumerCount));
        //try to finish all jobs
        //wait till enough jobs start
        now = System.nanoTime();
        waitTill = now + TimeUnit.SECONDS.toNanos(10);
        while(System.nanoTime() < waitTill) {
            synchronized (runLock) {
                runLock.notifyAll();
            }
            totalRunningCount = 0;
            for(DummyJob job : runningJobs) {
                totalRunningCount += job.getOnRunCnt();
            }
            if(totalJobCount == totalRunningCount) {
                //cool!
                break;
            }
        }
        MatcherAssert.assertThat("no jobs should remain", jobManager.count(), equalTo(0));

    }

    @Test
    public void testLoadFactor() throws Exception {
        //test adding zillions of jobs from the same group and ensure no more than 1 thread is created
        int maxConsumerCount = 2;
        int loadFactor = 5;
        JobManager jobManager = createJobManager(JobManager.createDefaultConfiguration()
                .maxConsumerCount(maxConsumerCount)
                .loadFactor(loadFactor));
        JobConsumerExecutor consumerExecutor = getConsumerExecutor(jobManager);
        org.fest.reflect.field.Invoker<AtomicInteger> activeConsumerCnt = getActiveConsumerCount(consumerExecutor);
        Object runLock = new Object();
        Semaphore semaphore = new Semaphore(maxConsumerCount);
        int totalJobCount = loadFactor * maxConsumerCount * 5;
        List<DummyJob> runningJobs = new ArrayList<DummyJob>(totalJobCount);
        for(int i = 0; i < totalJobCount; i ++) {
            DummyJob job = new NeverEndingDummyJob(runLock, semaphore);
            runningJobs.add(job);
            jobManager.addJob((int)(Math.random() * 3), job);

            int expectedConsumerCount = Math.min(maxConsumerCount, (int)Math.ceil((float)i / loadFactor));
            //wait till enough jobs start
            long now = System.nanoTime();
            long waitTill = now + TimeUnit.SECONDS.toNanos(10);
            while(System.nanoTime() < waitTill) {
                if(semaphore.availablePermits() == maxConsumerCount - expectedConsumerCount) {
                    //enough # of jobs started
                    break;
                }
            }
            if(i < loadFactor) {
                //make sure there is only 1 job running
                MatcherAssert.assertThat("while below load factor, active consumer count should be <= 1",
                        activeConsumerCnt.get().get() <= 1, is(true));
            }
            if(i > loadFactor) {
                //make sure there is only 1 job running
                MatcherAssert.assertThat("while above load factor. there should be more job consumers",
                        activeConsumerCnt.get().get(), equalTo(expectedConsumerCount));
            }
        }

        //finish all jobs
        long now = System.nanoTime();
        long waitTill = now + TimeUnit.SECONDS.toNanos(10);
        while(System.nanoTime() < waitTill) {
            synchronized (runLock) {
                runLock.notifyAll();
            }
            long totalRunningCount = 0;
            for(DummyJob job : runningJobs) {
                totalRunningCount += job.getOnRunCnt();
            }
            if(totalJobCount == totalRunningCount) {
                //cool!
                break;
            }
        }
        MatcherAssert.assertThat("no jobs should remain", jobManager.count(), equalTo(0));

    }

    @Test
    public void testKeepAlive() throws Exception {
        int keepAlive = 3 + (int)(Math.random() * 5);
        long id = System.nanoTime();
        DummyNetworkUtil networkUtilWithoutEventSupport = new DummyNetworkUtil();
        DummyNetworkUtilWithConnectivityEventSupport networkUtilWithEventSupport = new DummyNetworkUtilWithConnectivityEventSupport();
        JobManager jobManager1 = createJobManager(JobManager.createDefaultConfiguration()
                .consumerKeepAlive(keepAlive).id(id + "a").networkUtil(networkUtilWithoutEventSupport));
        JobManager jobManager2 = createJobManager(JobManager.createDefaultConfiguration()
                .consumerKeepAlive(keepAlive).id(id + "b")
                .networkUtil(networkUtilWithEventSupport));
        //give it a little time to create first consumer
        jobManager1.addJob(0, new DummyJob());
        jobManager2.addJob(0, new DummyJob());
        AtomicInteger activeThreadCount1 = getActiveConsumerCount(getConsumerExecutor(jobManager1)).get();
        AtomicInteger activeThreadCount2 = getActiveConsumerCount(getConsumerExecutor(jobManager2)).get();

        Thread.sleep(1000);
        MatcherAssert.assertThat("there should be 1 thread  actively waiting for jobs",
                activeThreadCount1.get(), equalTo(1));
        MatcherAssert.assertThat("there should be one thread actively waiting for jobs",
                activeThreadCount2.get(), equalTo(1));
        //sleep till it dies
        Thread.sleep((long) (TimeUnit.SECONDS.toMillis(keepAlive) * 1.33));
        MatcherAssert.assertThat("after keep alive timeout, there should NOT be any threads waiting",
                activeThreadCount1.get(), equalTo(0));
        MatcherAssert.assertThat("after keep alive timeout, there should NOT be any threads waiting",
                activeThreadCount2.get(), equalTo(0));


        //disable network and add a network bound job
        networkUtilWithoutEventSupport.setHasNetwork(false);
        networkUtilWithEventSupport.setHasNetwork(false, true);
        jobManager1.addJob(0, new DummyJob(true));
        jobManager2.addJob(0, new DummyJob(true));
        Thread.sleep(1000 + (long) (TimeUnit.SECONDS.toMillis(keepAlive) * 2));
        MatcherAssert.assertThat("when network changes cannot be detected, there should be a consumer waiting alive",
                activeThreadCount1.get(), equalTo(1));
        MatcherAssert.assertThat("when network changes can be detected, there should not be a consumer waiting alive",
                activeThreadCount2.get(), equalTo(0));
        networkUtilWithEventSupport.setHasNetwork(true, true);
        networkUtilWithoutEventSupport.setHasNetwork(true);
        Thread.sleep(500);
        MatcherAssert.assertThat("when network is recovered, job should be handled",
                jobManager2.count(), equalTo(0));
        Thread.sleep(1000);
        MatcherAssert.assertThat("when network is recovered, job should be handled",
                jobManager1.count(), equalTo(0));


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

    @Test
    public void testGrouping() throws Exception {
        JobManager jobManager = createJobManager();
        jobManager.stop();
        Invoker<JobHolder> nextJobMethod = getNextJobMethod(jobManager);
        Invoker<Void> removeJobMethod = getRemoveJobMethod(jobManager);

        long jobId1 = jobManager.addJob(0, new DummyJob("group1"));
        long jobId2 = jobManager.addJob(0, new DummyJob("group1"));
        long jobId3 = jobManager.addJob(0, new PersistentDummyJob("group2"));
        long jobId4 = jobManager.addJob(0, new PersistentDummyJob("group1"));
        JobHolder nextJob = nextJobMethod.invoke();
        MatcherAssert.assertThat("next job should be the first job from group1", nextJob.getId(), equalTo(jobId1));
        JobHolder group2Job = nextJobMethod.invoke();
        MatcherAssert.assertThat("since group 1 is running now, next job should be from group 2", group2Job.getId(), equalTo(jobId3));
        removeJobMethod.invoke(nextJob);
        JobHolder group1NextJob =nextJobMethod.invoke();
        MatcherAssert.assertThat("after removing job from group 1, another job from group1 should be returned", group1NextJob.getId(), equalTo(jobId2));
        MatcherAssert.assertThat("when jobs from both groups are running, no job should be returned from next job", nextJobMethod.invoke(), is(nullValue()));
        removeJobMethod.invoke(group2Job);
        MatcherAssert.assertThat("even after group2 job is complete, no jobs should be returned since we only have group1 jobs left", nextJobMethod.invoke(), is(nullValue()));
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

}
