package com.birbit.android.jobqueue.test.jobmanager;

import com.birbit.android.jobqueue.Job;
import com.birbit.android.jobqueue.JobManager;
import com.birbit.android.jobqueue.Params;
import com.birbit.android.jobqueue.callback.JobManagerCallbackAdapter;
import com.birbit.android.jobqueue.config.Configuration;
import com.birbit.android.jobqueue.network.NetworkUtil;
import com.birbit.android.jobqueue.persistentQueue.sqlite.SqliteJobQueue;
import com.birbit.android.jobqueue.test.jobs.DummyJob;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(RobolectricTestRunner.class)
public class CountJobsForTagTest extends JobManagerTestBase {

    @Test
    public void testCountPersistedJobs() {
        JobManager jobManager = createJobManager(new Configuration.Builder(RuntimeEnvironment.application)
                .timer(mockTimer)
                .inTestMode()
                .maxConsumerCount(0));
        jobManager.addJob(new DummyJob(new Params(0).persist()));
        jobManager.addJob(new DummyJob(new Params(0).persist().addTags("tag")));
        jobManager.addJob(new DummyJob(new Params(0).persist().addTags("tag", "tag1")));
        jobManager.addJob(new DummyJob(new Params(0).persist().addTags("tag", "tag1", "tag2")));
        assertThat("total jobs in jobManager should be 4", jobManager.count(), is(4));
        assertThat("should count jobs for tag 1", jobManager.countJobsForTag("tag"), is(3));
        assertThat("should count jobs for tag 2", jobManager.countJobsForTag("tag", "tag1"), is(2));
        assertThat("should count jobs for tag 3", jobManager.countJobsForTag("tag", "tag1", "tag2"), is(1));
        assertThat("should return 0 ", jobManager.countJobsForTag("tag4"), is(0));
    }

    @Test
    public void testCountPersistedJobsWithActiveJob() throws InterruptedException {
        final CountDownLatch waitFor = new CountDownLatch(1);
        final CountDownLatch trigger = new CountDownLatch(1);
        final CountDownLatch deserializationLatch = new CountDownLatch(1);
        final DummyTwoLatchJob[] dummyTwoLatchJob = {new DummyTwoLatchJob(new Params(0).persist().addTags("tag1"), waitFor, trigger)};
        final JobManager jobManager = createJobManager(new Configuration.Builder(RuntimeEnvironment.application)
                .timer(mockTimer)
                .inTestMode()
                .jobSerializer(new SqliteJobQueue.JobSerializer() {
                    @Override
                    public byte[] serialize(Object object) throws IOException {
                        if (object instanceof DummyTwoLatchJob) {
                            return new byte[1];
                        } else {
                            return new byte[0];
                        }
                    }

                    @Override
                    public <T extends Job> T deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
                        if (bytes.length == 1) {
                            dummyTwoLatchJob[0] = new DummyTwoLatchJob(new Params(0).persist().addTags("tag1"), waitFor, trigger);
                            deserializationLatch.countDown();
                            return (T) dummyTwoLatchJob[0];
                        } else {
                            return (T) new DummyJob(new Params(0).persist().addTags("tag1", "job1"));
                        }
                    }
                })
                .maxConsumerCount(1));
        jobManager.stop();
        jobManager.addJob(dummyTwoLatchJob[0]);
        jobManager.addJob(new DummyJob(new Params(0).persist().addTags("tag1")));
        jobManager.addJob(new DummyJob(new Params(0).persist().addTags("tag1")));
        jobManager.start();
        deserializationLatch.await(60, TimeUnit.SECONDS);
        dummyTwoLatchJob[0].waitTillOnRun();
        assertThat("should count jobs for tag 1", jobManager.countJobsForTag("tag1"), is(3));
        waitFor.countDown();
        trigger.await();
    }

    @Test
    public void testCountPersistedJobsCompletedJob() throws InterruptedException {
        DummyNetworkUtil networkUtil = new DummyNetworkUtil();
        networkUtil.setNetworkStatus(NetworkUtil.DISCONNECTED);
        final JobManager jobManager = createJobManager(new Configuration.Builder(RuntimeEnvironment.application)
                .timer(mockTimer)
                .inTestMode()
                .networkUtil(networkUtil)
                .maxConsumerCount(1));
        jobManager.stop();
        jobManager.addJob(new DummyJob(new Params(0).persist().addTags("tag1", "job1")));
        jobManager.addJob(new DummyJob(new Params(0).persist().requireNetwork().addTags("tag1", "job2")));
        jobManager.addJob(new DummyJob(new Params(0).persist().requireNetwork().addTags("tag1", "job3")));
        final CountDownLatch waitLatch = new CountDownLatch(1);
        jobManager.addCallback(new JobManagerCallbackAdapter() {
            @Override
            public void onDone(@NonNull Job job) {
                super.onDone(job);
                assertThat("should count jobs for tag 1", jobManager.countJobsForTag("tag1"), is(2));
                waitLatch.countDown();
            }
        });
        jobManager.start();
        waitLatch.await(30, TimeUnit.SECONDS);
    }
}
