package com.birbit.android.jobqueue.test.jobqueue;

import com.birbit.android.jobqueue.TestConstraint;
import com.birbit.android.jobqueue.Job;
import com.birbit.android.jobqueue.JobQueue;
import com.birbit.android.jobqueue.Params;
import com.birbit.android.jobqueue.config.Configuration;
import com.birbit.android.jobqueue.persistentQueue.sqlite.SqliteJobQueue;
import com.birbit.android.jobqueue.test.util.JobQueueFactory;
import com.birbit.android.jobqueue.timer.Timer;

import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = com.birbit.android.jobqueue.BuildConfig.class)
public class SqliteJobQueueTest extends JobQueueTestBase {
    public SqliteJobQueueTest() {
        super(new JobQueueFactory() {
            @Override
            public JobQueue createNew(long sessionId, String id, Timer timer) {
                SqliteJobQueue.JavaSerializer serializer = new SqliteJobQueue.JavaSerializer();
                return new SqliteJobQueue(
                        new Configuration.Builder(RuntimeEnvironment.application)
                                .id(id).jobSerializer(serializer).inTestMode()
                                .timer(timer).build(), sessionId, serializer);
            }
        });
    }

    @Test
    public void testCustomSerializer() throws Exception {
        final CountDownLatch calledForSerialize = new CountDownLatch(1);
        final CountDownLatch calledForDeserialize = new CountDownLatch(1);
        SqliteJobQueue.JobSerializer jobSerializer = new SqliteJobQueue.JavaSerializer() {
            @Override
            public byte[] serialize(Object object) throws IOException {
                calledForSerialize.countDown();
                return super.serialize(object);
            }

            @Override
            public <T extends Job> T deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
                calledForDeserialize.countDown();
                return super.deserialize(bytes);
            }
        };

        SqliteJobQueue jobQueue = new SqliteJobQueue(new Configuration.Builder(RuntimeEnvironment.application)
                .id("__" + mockTimer.nanoTime()).jobSerializer(jobSerializer).inTestMode()
                .timer(mockTimer).build(), mockTimer.nanoTime(), jobSerializer);
        jobQueue.insert(createNewJobHolder(new Params(0)));
        calledForSerialize.await(1, TimeUnit.SECONDS);
        MatcherAssert.assertThat("custom serializer should be called for serialize", (int) calledForSerialize.getCount(), CoreMatchers.equalTo(0));
        MatcherAssert.assertThat("custom serializer should NOT be called for deserialize", (int) calledForDeserialize.getCount(), CoreMatchers.equalTo(1));
        jobQueue.nextJobAndIncRunCount(new TestConstraint(mockTimer));
        MatcherAssert.assertThat("custom serializer should be called for deserialize", (int) calledForDeserialize.getCount(), CoreMatchers.equalTo(0));

    }
}
