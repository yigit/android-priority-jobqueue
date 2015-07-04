package com.path.android.jobqueue.test.jobqueue;

import com.path.android.jobqueue.JobQueue;
import com.path.android.jobqueue.persistentQueue.sqlite.SqliteJobQueue;
import com.path.android.jobqueue.test.util.JobQueueFactory;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = com.path.android.jobqueue.BuildConfig.class)
public class CachedPersistentJobQueueTest extends JobQueueTestBase {
    public CachedPersistentJobQueueTest() {
        super(new JobQueueFactory() {
            @Override
            public JobQueue createNew(long sessionId, String id) {
                return new SqliteJobQueue(RuntimeEnvironment.application, sessionId, id, new SqliteJobQueue.JavaSerializer(), true);
            }
        });
    }
}
