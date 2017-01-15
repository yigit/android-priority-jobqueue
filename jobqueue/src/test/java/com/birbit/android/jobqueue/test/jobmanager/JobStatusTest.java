package com.birbit.android.jobqueue.test.jobmanager;

import com.birbit.android.jobqueue.Job;
import com.birbit.android.jobqueue.JobManager;
import com.birbit.android.jobqueue.JobStatus;
import com.birbit.android.jobqueue.Params;
import com.birbit.android.jobqueue.callback.JobManagerCallbackAdapter;
import com.birbit.android.jobqueue.config.Configuration;
import com.birbit.android.jobqueue.network.NetworkUtil;
import com.birbit.android.jobqueue.test.jobs.DummyJob;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

import android.annotation.TargetApi;
import android.os.Build;
import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
@Config(constants = com.birbit.android.jobqueue.BuildConfig.class)
public class JobStatusTest extends JobManagerTestBase {
    private static final String REQ_NETWORK_TAG = "reqNetwork";
    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    @Test
    public void testJobStatus() throws InterruptedException {
        DummyNetworkUtilWithConnectivityEventSupport networkUtil = new DummyNetworkUtilWithConnectivityEventSupport();
        networkUtil.setNetworkStatus(NetworkUtil.DISCONNECTED, true);
        final JobManager jobManager = createJobManager(
                new Configuration.Builder(RuntimeEnvironment.application).networkUtil(networkUtil)
                        .timer(mockTimer));
        jobManager.stop();
        List<Integer> networkRequiringJobIndices = new ArrayList<Integer>();
        Job[] jobs = new Job[] {
                new DummyJob(new Params(0)),
                new DummyJob(new Params(0).persist()),
                new DummyJob(new Params(0).persist().requireNetwork().addTags(REQ_NETWORK_TAG))
        };
        String[] ids = new String[jobs.length];
        networkRequiringJobIndices.add(2);
        for(int i = 0; i < jobs.length; i ++) {
            jobManager.addJob(jobs[i]);
            ids[i] = jobs[i].getId();
            JobStatus expectedStatus = (!networkUtil.isDisconnected() || !networkRequiringJobIndices.contains(i)) ? JobStatus.WAITING_READY :
                    JobStatus.WAITING_NOT_READY;
            assertThat("job should have correct status after being added",
                    jobManager.getJobStatus(ids[i]), is(expectedStatus));
        }

        //create an unknown id, ensure status for that

        boolean exists;
        String unknownId;
        do {
            unknownId = UUID.randomUUID().toString();
            exists = false;
            for(String id : ids) {
                if(unknownId.equals(id)) {
                    exists = true;
                }
            }
        } while (exists);
        for(boolean persistent : new boolean[]{true, false}) {
            assertThat("job with unknown id should return as expected",
                    jobManager.getJobStatus(unknownId), is(JobStatus.UNKNOWN));
        }

        final CountDownLatch startLatch = new CountDownLatch(1), endLatch = new CountDownLatch(1);
        final DummyTwoLatchJob twoLatchJob = new DummyTwoLatchJob(new Params(0), startLatch, endLatch);
        jobManager.start();
        jobManager.addJob(twoLatchJob);
        final String jobId = twoLatchJob.getId();
        twoLatchJob.waitTillOnRun();
        final CountDownLatch twoLatchJobDone = new CountDownLatch(1);
        jobManager.addCallback(new JobManagerCallbackAdapter() {
            @Override
            public void onAfterJobRun(@NonNull Job job, int resultCode) {
                if (job == twoLatchJob && resultCode == RESULT_SUCCEED) {
                    jobManager.removeCallback(this);
                    twoLatchJobDone.countDown();
                }
            }
        });
        assertThat("job should be in running state", jobManager.getJobStatus(jobId), is(JobStatus.RUNNING));
        startLatch.countDown();//let it run
        try {
            endLatch.await();//wait till it finishes
        } catch (InterruptedException ignored) {

        }
        twoLatchJobDone.await(1, TimeUnit.MINUTES);
        assertThat("finished job should go to unknown state. id: " + jobId, jobManager.getJobStatus(jobId), is(JobStatus.UNKNOWN));

        //network requiring job should not be ready
        for(Integer i : networkRequiringJobIndices) {
            assertThat("network requiring job should still be not-ready",
                    jobManager.getJobStatus(ids[i]), is(JobStatus.WAITING_NOT_READY));
        }
        jobManager.stop();
        networkUtil.setNetworkStatus(NetworkUtil.METERED, true);
        for(Integer i : networkRequiringJobIndices) {
            assertThat("network requiring job should still be ready after network is there",
                    jobManager.getJobStatus(ids[i]), is(JobStatus.WAITING_READY));
        }
        final CountDownLatch networkRequiredLatch = new CountDownLatch(networkRequiringJobIndices.size());
        jobManager.addCallback(new JobManagerCallbackAdapter() {
            @Override
            public void onDone(@NonNull Job job) {
                if (job.getTags().contains(REQ_NETWORK_TAG)) {
                    networkRequiredLatch.countDown();
                }
            }
        });
        jobManager.start();
        networkRequiredLatch.await(1, TimeUnit.MINUTES);
        assertThat("jobs should finish", jobManager.count(), is(0));
        for(int i = 0; i < jobs.length; i ++) {
            //after all jobs finish, state should be unknown
            assertThat("all jobs finished, states should be unknown", jobManager.getJobStatus(ids[i]), is(JobStatus.UNKNOWN));
        }
        final long SHORT_SLEEP = 2000;
        Job[] delayedJobs = new Job[]{
                new DummyJob(new Params(0).delayInMs(SHORT_SLEEP)),
                new DummyJob(new Params(0).delayInMs(SHORT_SLEEP).persist()),
                new DummyJob(new Params(0).delayInMs(SHORT_SLEEP * 10)),
                new DummyJob(new Params(0).delayInMs(SHORT_SLEEP * 10).persist())};
        String[] delayedIds = new String[delayedJobs.length];
        long start = mockTimer.nanoTime();
        for(int i = 0; i < delayedJobs.length; i ++) {
            jobManager.addJob(delayedJobs[i]);
            delayedIds[i] = delayedJobs[i].getId();
        }
        for(int i = 0; i < delayedJobs.length; i ++) {
            assertThat("delayed job(" + i + ") should receive not ready status. startMs:" + start,
                    jobManager.getJobStatus(delayedIds[i]), is(JobStatus.WAITING_NOT_READY));
        }
        jobManager.stop();
        //sleep
        mockTimer.incrementMs(SHORT_SLEEP * 2);
        for(int i = 0; i < delayedJobs.length; i ++) {
            if(delayedJobs[i].getDelayInMs() == SHORT_SLEEP) {
                assertThat("when enough time passes, delayed jobs should move to ready state",
                        jobManager.getJobStatus(delayedIds[i]),is(JobStatus.WAITING_READY));
            } else {
                assertThat("delayed job should receive not ready status until their time comes",
                        jobManager.getJobStatus(delayedIds[i]), is(JobStatus.WAITING_NOT_READY));
            }
        }
    }
}
