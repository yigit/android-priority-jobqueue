package com.tarkalabs.android.jobqueue.test.jobqueue;

import com.tarkalabs.android.jobqueue.inMemoryQueue.SimpleInMemoryPriorityQueue;
import com.tarkalabs.android.jobqueue.JobQueue;
import com.tarkalabs.android.jobqueue.test.util.JobQueueFactory;
import com.tarkalabs.android.jobqueue.timer.Timer;

import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(constants = com.tarkalabs.android.jobqueue.BuildConfig.class)
public class SimpleInMemoryJobQueueTest extends JobQueueTestBase {
    public SimpleInMemoryJobQueueTest() {
        super(new JobQueueFactory() {
            @Override
            public JobQueue createNew(long sessionId, String id, Timer timer) {
                return new SimpleInMemoryPriorityQueue(null, sessionId);
            }
        });
    }
}
