package com.birbit.android.jobqueue.test.jobmanager;

import com.birbit.android.jobqueue.Job;
import com.birbit.android.jobqueue.JobHolder;
import com.birbit.android.jobqueue.JobManager;
import com.birbit.android.jobqueue.Params;
import com.birbit.android.jobqueue.callback.JobManagerCallbackAdapter;
import com.birbit.android.jobqueue.config.Configuration;
import com.birbit.android.jobqueue.network.NetworkUtil;
import com.birbit.android.jobqueue.test.jobs.DummyJob;

import static org.hamcrest.CoreMatchers.*;
import org.hamcrest.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

import android.annotation.TargetApi;
import android.os.Build;
import android.support.annotation.NonNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
@Config(constants = com.birbit.android.jobqueue.BuildConfig.class)
public class GroupingTest extends JobManagerTestBase {
    private String addJob(JobManager jobManager, Job job) {
        jobManager.addJob(job);
        return job.getId();
    }
    @Test
    public void testGrouping() throws Throwable {
        JobManager jobManager = createJobManager();
        jobManager.stop();
        String jobId1 = addJob(jobManager, new DummyJob(new Params(0).groupBy("group1")));
        String jobId2 = addJob(jobManager, new DummyJob(new Params(0).groupBy("group1")));
        String jobId3 = addJob(jobManager, new DummyJob(new Params(0).persist().groupBy("group2")));
        String jobId4 = addJob(jobManager, new DummyJob(new Params(0).persist().groupBy("group1")));
        JobHolder nextJob = nextJob(jobManager);
        MatcherAssert.assertThat("next job should be the first job from group1", nextJob.getId(), equalTo(jobId1));
        JobHolder group2Job = nextJob(jobManager, Collections.singletonList("group1"));
        MatcherAssert.assertThat("since group 1 is running now, next job should be from group 2", group2Job.getId(), equalTo(jobId3));
        removeJob(jobManager, nextJob);
        JobHolder group1NextJob = nextJob(jobManager, Arrays.asList("group2"));
        MatcherAssert.assertThat("after removing job from group 1, another job from group1 should be returned", group1NextJob.getId(), equalTo(jobId2));
        MatcherAssert.assertThat("when jobs from both groups are running, no job should be returned from next job",
                nextJob(jobManager, Arrays.asList("group1", "group2")), is(nullValue()));
        removeJob(jobManager, group2Job);
        MatcherAssert.assertThat("even after group2 job is complete, no jobs should be returned"
                + "since we only have group1 jobs left",
                nextJob(jobManager, Arrays.asList("group1")), is(nullValue()));
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    @Test
    public void testGroupingRaceCondition() throws Exception {
        DummyNetworkUtilWithConnectivityEventSupport dummyNetworkUtil = new DummyNetworkUtilWithConnectivityEventSupport();
        JobManager jobManager = createJobManager(new Configuration.Builder(RuntimeEnvironment.application)
                .minConsumerCount(5).maxConsumerCount(10)
                .networkUtil(dummyNetworkUtil)
                .timer(mockTimer));
        dummyNetworkUtil.setNetworkStatus(NetworkUtil.DISCONNECTED, true);
        //add a bunch of network requring jobs
        final String GROUP_ID = "shared_group_id";
        final int AFTER_ADDED_JOBS_COUNT = 5;
        final int NOT_SET_JOB_ID = -1;
        final AtomicInteger firstRunJob = new AtomicInteger(NOT_SET_JOB_ID);
        final int FIRST_JOB_ID = -10;
        final CountDownLatch onAddedCalled = new CountDownLatch(1);
        final CountDownLatch remainingJobsOnAddedCalled = new CountDownLatch(AFTER_ADDED_JOBS_COUNT);
        final CountDownLatch aJobRun = new CountDownLatch(1);
        jobManager.addCallback(new JobManagerCallbackAdapter() {
            @Override
            public void onJobRun(@NonNull Job job, int resultCode) {
                aJobRun.countDown();
            }
        });
        jobManager.addJobInBackground(new DummyJob(new Params(10).requireNetwork().groupBy(GROUP_ID)) {
            @Override
            public void onAdded() {
                super.onAdded();
                onAddedCalled.countDown();
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
        dummyNetworkUtil.setNetworkStatus(NetworkUtil.METERED, true);
        //wait until all jobs are completed
        aJobRun.await(1, TimeUnit.MINUTES);
        MatcherAssert.assertThat("highest priority job should run if it is added before others", firstRunJob.get(), is(FIRST_JOB_ID));

    }
}
