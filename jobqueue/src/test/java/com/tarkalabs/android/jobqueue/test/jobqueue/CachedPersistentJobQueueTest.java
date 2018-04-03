package com.tarkalabs.android.jobqueue.test.jobqueue;

import com.tarkalabs.android.jobqueue.JobQueue;
import com.tarkalabs.android.jobqueue.config.Configuration;
import com.tarkalabs.android.jobqueue.persistentQueue.sqlite.SqliteJobQueue;
import com.tarkalabs.android.jobqueue.test.util.JobQueueFactory;
import com.tarkalabs.android.jobqueue.timer.Timer;

import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(constants = com.tarkalabs.android.jobqueue.BuildConfig.class)
public class CachedPersistentJobQueueTest extends JobQueueTestBase {
    public CachedPersistentJobQueueTest() {
        super(new JobQueueFactory() {
            @Override
            public JobQueue createNew(long sessionId, String id, Timer timer) {
                SqliteJobQueue.JavaSerializer jobSerializer = new SqliteJobQueue.JavaSerializer();
                return new SqliteJobQueue(
                        new Configuration.Builder(RuntimeEnvironment.application)
                        .id(id).jobSerializer(jobSerializer).inTestMode()
                        .timer(timer).build(), sessionId, jobSerializer);
            }
        });
    }
}
