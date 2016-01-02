package com.path.android.jobqueue.test.util;

import com.path.android.jobqueue.JobQueue;
import com.path.android.jobqueue.timer.Timer;

public interface JobQueueFactory {
    public JobQueue createNew(long sessionId, String id, Timer timer);
}
