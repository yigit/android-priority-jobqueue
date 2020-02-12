package com.birbit.android.jobqueue.test.jobqueue;

import com.birbit.android.jobqueue.inMemoryQueue.SimpleInMemoryPriorityQueue;
import com.birbit.android.jobqueue.JobQueue;
import com.birbit.android.jobqueue.test.util.JobQueueFactory;
import com.birbit.android.jobqueue.timer.Timer;

import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)

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
