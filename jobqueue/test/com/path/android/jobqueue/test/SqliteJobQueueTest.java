package com.path.android.jobqueue.test;

import com.path.android.jobqueue.JobQueue;
import com.path.android.jobqueue.persistentQueue.sqlite.SqliteJobQueue;
import com.path.android.jobqueue.test.util.JobQueueFactory;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class SqliteJobQueueTest extends JobQueueTestBase {
    public SqliteJobQueueTest() {
        super(new JobQueueFactory() {
            @Override
            public JobQueue createNew(long sessionId, String id) {
                return new SqliteJobQueue(Robolectric.application, sessionId, id);
            }
        });
    }
}
