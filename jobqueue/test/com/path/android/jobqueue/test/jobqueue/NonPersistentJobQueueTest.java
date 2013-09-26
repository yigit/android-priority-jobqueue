package com.path.android.jobqueue.test.jobqueue;

import com.path.android.jobqueue.JobQueue;
import com.path.android.jobqueue.nonPersistentQueue.NonPersistentPriorityQueue;
import com.path.android.jobqueue.test.util.JobQueueFactory;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class NonPersistentJobQueueTest extends JobQueueTestBase {
    public NonPersistentJobQueueTest() {
        super(new JobQueueFactory() {
            @Override
            public JobQueue createNew(long sessionId, String id) {
                return new NonPersistentPriorityQueue(sessionId, id);
            }
        });
    }
}
