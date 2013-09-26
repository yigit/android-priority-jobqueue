package com.path.android.jobqueue.test.jobqueue;


import com.path.android.jobqueue.JobQueue;
import com.path.android.jobqueue.cachedQueue.CachedJobQueue;
import com.path.android.jobqueue.nonPersistentQueue.NonPersistentPriorityQueue;
import com.path.android.jobqueue.test.util.JobQueueFactory;
import org.junit.runner.RunWith;
import org.robolectric.*;

@RunWith(RobolectricTestRunner.class)
public class CachedNonPersistentJobQueueTest extends JobQueueTestBase {
    public CachedNonPersistentJobQueueTest() {
        super(new JobQueueFactory() {
            @Override
            public JobQueue createNew(long sessionId, String id) {
                return new CachedJobQueue(new NonPersistentPriorityQueue(sessionId, id));
            }
        });
    }
}
