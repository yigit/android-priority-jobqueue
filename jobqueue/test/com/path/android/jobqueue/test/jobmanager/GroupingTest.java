package com.path.android.jobqueue.test.jobmanager;

import com.path.android.jobqueue.JobHolder;
import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.Params;
import com.path.android.jobqueue.config.Configuration;
import com.path.android.jobqueue.test.jobs.DummyJob;
import org.fest.reflect.method.*;
import static org.hamcrest.CoreMatchers.*;
import org.hamcrest.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
public class GroupingTest extends JobManagerTestBase {
    @Test
    public void testGrouping() throws Exception {
        JobManager jobManager = createJobManager();
        jobManager.stop();
        Invoker<JobHolder> nextJobMethod = getNextJobMethod(jobManager);
        Invoker<Void> removeJobMethod = getRemoveJobMethod(jobManager);

        long jobId1 = jobManager.addJob(new DummyJob(new Params(0).groupBy("group1")));
        long jobId2 = jobManager.addJob(new DummyJob(new Params(0).groupBy("group1")));
        long jobId3 = jobManager.addJob(new DummyJob(new Params(0).persist().groupBy("group2")));
        long jobId4 = jobManager.addJob(new DummyJob(new Params(0).persist().groupBy("group1")));
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

    @Test
    public void testGroupingRaceCondition() throws Exception {
        DummyNetworkUtilWithConnectivityEventSupport dummyNetworkUtil = new DummyNetworkUtilWithConnectivityEventSupport();
        JobManager jobManager = createJobManager(new Configuration.Builder(Robolectric.application)
                .minConsumerCount(5).maxConsumerCount(10)
                .networkUtil(dummyNetworkUtil));
        dummyNetworkUtil.setHasNetwork(false, true);
        //add a bunch of network requring jobs
        final String GROUP_ID = "shared_group_id";
        final int AFTER_ADDED_JOBS_COUNT = 5;
        final int NOT_SET_JOB_ID = -1;
        final AtomicInteger firstRunJob = new AtomicInteger(NOT_SET_JOB_ID);
        final int FIRST_JOB_ID = -10;
        final CountDownLatch onAddedCalled = new CountDownLatch(1);
        final CountDownLatch remainingJobsOnAddedCalled = new CountDownLatch(AFTER_ADDED_JOBS_COUNT);
        jobManager.addJobInBackground(new DummyJob(new Params(10).requireNetwork().groupBy(GROUP_ID)) {
            @Override
            public void onAdded() {
                super.onAdded();
                onAddedCalled.countDown();
                try {
                    //wait until all other jobs are added
                    remainingJobsOnAddedCalled.await();
                    //wait a bit after all are added,
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                }
            }

            @Override
            public void onRun() throws Throwable {
                super.onRun();
                firstRunJob.compareAndSet(NOT_SET_JOB_ID, FIRST_JOB_ID);
            }
        });
        //ensure first jobs on added is called
        onAddedCalled.await();
        for(int i = 0; i < AFTER_ADDED_JOBS_COUNT; i ++) {
            final int finalI = i;
            jobManager.addJob(new DummyJob(new Params(5).groupBy(GROUP_ID).requireNetwork()) {
                final int id = finalI + 1;

                @Override
                public void onAdded() {
                    super.onAdded();
                    remainingJobsOnAddedCalled.countDown();
                }

                @Override
                public void onRun() throws Throwable {
                    super.onRun();
                    firstRunJob.compareAndSet(NOT_SET_JOB_ID, id);
                }
            });
        }
        dummyNetworkUtil.setHasNetwork(true, true);
        //wait until all jobs are completed
        while(firstRunJob.get() == NOT_SET_JOB_ID) {
            Thread.sleep(100);
        }
        MatcherAssert.assertThat("highest priority job should run if it is added before others", firstRunJob.get(), is(FIRST_JOB_ID));

    }
}
