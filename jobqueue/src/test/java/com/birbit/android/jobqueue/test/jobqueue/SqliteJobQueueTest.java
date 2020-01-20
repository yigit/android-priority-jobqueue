package com.birbit.android.jobqueue.test.jobqueue;

import android.database.Cursor;
import androidx.core.util.Pair;

import com.birbit.android.jobqueue.JobHolder;
import com.birbit.android.jobqueue.TestConstraint;
import com.birbit.android.jobqueue.Job;
import com.birbit.android.jobqueue.JobQueue;
import com.birbit.android.jobqueue.Params;
import com.birbit.android.jobqueue.config.Configuration;
import com.birbit.android.jobqueue.persistentQueue.sqlite.DbOpenHelper;
import com.birbit.android.jobqueue.persistentQueue.sqlite.SqliteJobQueue;
import com.birbit.android.jobqueue.test.util.JobQueueFactory;
import com.birbit.android.jobqueue.timer.Timer;

import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.CoreMatchers.*;

@RunWith(RobolectricTestRunner.class)

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
    public void testClearTags() throws Throwable {
        SqliteJobQueue queue = (SqliteJobQueue) createNewJobQueue();
        JobHolder vh1 = createNewJobHolder(new Params(1).addTags("a", "b", "c"));
        JobHolder vh2 = createNewJobHolder(new Params(1).addTags("a", "b", "x"));
        queue.insert(vh1);
        queue.insert(vh2);
        assertTags(queue, new TagInfo(0, vh1.getId(), "a"),
                new TagInfo(0, vh1.getId(), "b"),
                new TagInfo(0, vh1.getId(), "c"),
                new TagInfo(0, vh2.getId(), "a"),
                new TagInfo(0, vh2.getId(), "b"),
                new TagInfo(0, vh2.getId(), "x"));
        queue.remove(vh2);
        assertTags(queue, new TagInfo(0, vh1.getId(), "a"),
                new TagInfo(0, vh1.getId(), "b"),
                new TagInfo(0, vh1.getId(), "c"));
        queue.clear();
        assertTags(queue);
    }

    private void assertTags(SqliteJobQueue queue, TagInfo... expected) {
        List<TagInfo> tags = loadAllTags(queue);
        assertThat(tags, hasItems(expected));
        assertThat(tags.size(), is(expected.length));
    }

    private List<TagInfo> loadAllTags(SqliteJobQueue queue) {
        Cursor cursor = queue.getDb().rawQuery("select * from job_holder_tags", new String[0]);
        List<TagInfo> result = new ArrayList<>();
        while (cursor.moveToNext()) {
            result.add(new TagInfo(
                    cursor.getInt(cursor.getColumnIndex("_id")),
                    cursor.getString(cursor.getColumnIndex("job_id")),
                    cursor.getString(cursor.getColumnIndex("tag_name"))
            ));
        }
        return result;
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

    private static class TagInfo {
        final int tagId;
        final String jobId;
        final String tagNme;

        public TagInfo(int tagId, String jobId, String tagNme) {
            this.tagId = tagId;
            this.jobId = jobId;
            this.tagNme = tagNme;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            TagInfo tagInfo = (TagInfo) o;

            if (!jobId.equals(tagInfo.jobId)) return false;
            return tagNme.equals(tagInfo.tagNme);

        }

        @Override
        public int hashCode() {
            int result = jobId.hashCode();
            result = 31 * result + tagNme.hashCode();
            return result;
        }
    }
}
