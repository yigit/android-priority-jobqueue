package com.path.android.jobqueue.test.jobqueue;

import com.birbit.android.jobqueue.inMemoryQueue.SimpleInMemoryPriorityQueue;
import com.path.android.jobqueue.JobQueue;
import com.path.android.jobqueue.test.util.JobQueueFactory;
import com.path.android.jobqueue.timer.Timer;

import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = com.path.android.jobqueue.BuildConfig.class)
public class SimpleInMemoryJobQueueTest extends JobQueueTestBase {
    public SimpleInMemoryJobQueueTest() {
        super(new JobQueueFactory() {
            @Override
            public JobQueue createNew(long sessionId, String id, Timer timer) {
                return new SimpleInMemoryPriorityQueue(sessionId);
            }
        });
    }
}
