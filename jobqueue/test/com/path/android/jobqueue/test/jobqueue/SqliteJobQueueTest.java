package com.path.android.jobqueue.test.jobqueue;

import com.path.android.jobqueue.BaseJob;
import com.path.android.jobqueue.JobQueue;
import com.path.android.jobqueue.Params;
import com.path.android.jobqueue.persistentQueue.sqlite.SqliteJobQueue;
import com.path.android.jobqueue.test.util.JobQueueFactory;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
public class SqliteJobQueueTest extends JobQueueTestBase {
    public SqliteJobQueueTest() {
        super(new JobQueueFactory() {
            @Override
            public JobQueue createNew(long sessionId, String id) {
                return new SqliteJobQueue(Robolectric.application, sessionId, id, new SqliteJobQueue.JavaSerializer());
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
            public <T extends BaseJob> T deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
                calledForDeserialize.countDown();
                return super.deserialize(bytes);
            }
        };
        SqliteJobQueue jobQueue = new SqliteJobQueue(Robolectric.application, System.nanoTime(), "__" + System.nanoTime(),
                jobSerializer);
        jobQueue.insert(createNewJobHolder(new Params(0)));
        calledForSerialize.await(1, TimeUnit.SECONDS);
        MatcherAssert.assertThat("custom serializer should be called for serialize", (int) calledForSerialize.getCount(), CoreMatchers.equalTo(0));
        MatcherAssert.assertThat("custom serializer should NOT be called for deserialize", (int) calledForDeserialize.getCount(), CoreMatchers.equalTo(1));
        jobQueue.nextJobAndIncRunCount(true, null);
        MatcherAssert.assertThat("custom serializer should be called for deserialize", (int) calledForDeserialize.getCount(), CoreMatchers.equalTo(0));

    }
}
