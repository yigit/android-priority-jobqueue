package com.path.android.jobqueue.test.jobqueue;


import com.path.android.jobqueue.JobQueue;
import com.path.android.jobqueue.cachedQueue.CachedJobQueue;
import com.path.android.jobqueue.config.Configuration;
import com.path.android.jobqueue.nonPersistentQueue.NonPersistentPriorityQueue;
import com.path.android.jobqueue.test.timer.MockTimer;
import com.path.android.jobqueue.test.util.JobQueueFactory;
import com.path.android.jobqueue.timer.Timer;

import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

import android.test.mock.MockApplication;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = com.path.android.jobqueue.BuildConfig.class)
public class CachedNonPersistentJobQueueTest extends JobQueueTestBase {
    public CachedNonPersistentJobQueueTest() {
        super(new JobQueueFactory() {
            @Override
            public JobQueue createNew(long sessionId, String id, Timer timer) {
                return new CachedJobQueue(new NonPersistentPriorityQueue(
                        new Configuration.Builder(new MockApplication()).timer(timer)
                        .id(id).build(), sessionId));
            }
        });
    }
}
