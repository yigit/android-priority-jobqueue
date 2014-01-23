package com.path.android.jobqueue.test.jobmanager;

import com.path.android.jobqueue.Job;
import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.JobStatus;
import com.path.android.jobqueue.Params;
import com.path.android.jobqueue.config.Configuration;
import com.path.android.jobqueue.test.jobs.DummyJob;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

@RunWith(RobolectricTestRunner.class)
public class JobStatusTest extends JobManagerTestBase {
    @Test
    public void testJobStatus() throws InterruptedException {
        DummyNetworkUtilWithConnectivityEventSupport networkUtil = new DummyNetworkUtilWithConnectivityEventSupport();
        networkUtil.setHasNetwork(false, true);
        JobManager jobManager = createJobManager(new Configuration.Builder(Robolectric.application).networkUtil(networkUtil));
        jobManager.stop();
        List<Integer> networkRequiringJobIndices = new ArrayList<Integer>();
        Job[] jobs = new Job[] {
                new DummyJob(new Params(0)),
                new DummyJob(new Params(0).persist()),
                new DummyJob(new Params(0).persist().requireNetwork())
        };
        long[] ids = new long[jobs.length];
        for(int i = 0; i < jobs.length; i ++) {
            ids[i] = jobManager.addJob(jobs[i]);
            if(jobs[i].requiresNetwork()) {
                networkRequiringJobIndices.add(i);
            }
            JobStatus expectedStatus = (networkUtil.isConnected() || jobs[i].requiresNetwork() == false) ? JobStatus.WAITING_READY :
                    JobStatus.WAITING_NOT_READY;
            assertThat("job should have correct status after being added",
                    jobManager.getJobStatus(ids[i], jobs[i].isPersistent()), is(expectedStatus));
        }

        //create an unknown id, ensure status for that

        boolean exists;
        long unknownId;
        do {
            unknownId = (long) (Math.random() * 10000 - 5000);
            exists = false;
            for(long id : ids) {
                if(id == unknownId) {
                    exists = true;
                    continue;
                }
            }
        } while (exists);
        for(boolean persistent : new boolean[]{true, false}) {
            assertThat("job with unknown id should return as expected", jobManager.getJobStatus(unknownId, persistent), is(JobStatus.UNKNOWN));
        }

        CountDownLatch startLatch = new CountDownLatch(1), endLatch = new CountDownLatch(1);
        DummyTwoLatchJob twoLatchJob = new DummyTwoLatchJob(new Params(0), startLatch, endLatch);
        jobManager.start();
        long jobId = jobManager.addJob(twoLatchJob);
        twoLatchJob.waitTillOnRun();
        assertThat("job should be in running state", jobManager.getJobStatus(jobId, false), is(JobStatus.RUNNING));
        startLatch.countDown();//let it run
        endLatch.await();//wait till it finishes
        Thread.sleep(500);//give some time to job manager to clear the job
        assertThat("finished job should go to unknown state", jobManager.getJobStatus(jobId, false), is(JobStatus.UNKNOWN));

        //network requiring job should not be ready
        for(Integer i : networkRequiringJobIndices) {
            assertThat("network requiring job should still be not-ready", jobManager.getJobStatus(ids[i], jobs[i].isPersistent()), is(JobStatus.WAITING_NOT_READY));
        }
        jobManager.stop();
        networkUtil.setHasNetwork(true, true);
        for(Integer i : networkRequiringJobIndices) {
            assertThat("network requiring job should still be ready after network is there", jobManager.getJobStatus(ids[i], jobs[i].isPersistent()), is(JobStatus.WAITING_READY));
        }

        jobManager.start();
        int limit = 10;
        while (jobManager.count() > 0 && limit--  > 0) {
            Thread.sleep(1000);
        }
        assertThat("jobs should finish", jobManager.count(), is(0));
        for(int i = 0; i < jobs.length; i ++) {
            //after all jobs finish, state should be unknown
            assertThat("all jobs finished, states should be unknown", jobManager.getJobStatus(ids[i], jobs[i].isPersistent()), is(JobStatus.UNKNOWN));
        }
        final long SHORT_SLEEP = 1000;
        Job[] delayedJobs = new Job[]{
                new DummyJob(new Params(0).delayInMs(SHORT_SLEEP)),
                new DummyJob(new Params(0).delayInMs(SHORT_SLEEP).persist()),
                new DummyJob(new Params(0).delayInMs(SHORT_SLEEP * 10)),
                new DummyJob(new Params(0).delayInMs(SHORT_SLEEP * 10).persist())};
        long[] delayedIds = new long[delayedJobs.length];
        for(int i = 0; i < delayedJobs.length; i ++) {
            delayedIds[i] = jobManager.addJob(delayedJobs[i]);
        }

        for(int i = 0; i < delayedJobs.length; i ++) {
            assertThat("delayed job(" + i + ") should receive not ready status", jobManager.getJobStatus(delayedIds[i], delayedJobs[i].isPersistent()), is(JobStatus.WAITING_NOT_READY));
        }
        jobManager.stop();
        //sleep
        Thread.sleep(SHORT_SLEEP * 2);
        for(int i = 0; i < delayedJobs.length; i ++) {
            if(delayedJobs[i].getDelayInMs() == SHORT_SLEEP) {
                assertThat("when enough time passes, delayed jobs should move to ready state", jobManager.getJobStatus(delayedIds[i], delayedJobs[i].isPersistent()), is(JobStatus.WAITING_READY));
            } else {
                assertThat("delayed job should receive not ready status until their time comes", jobManager.getJobStatus(delayedIds[i], delayedJobs[i].isPersistent()), is(JobStatus.WAITING_NOT_READY));
            }
        }
    }
}
