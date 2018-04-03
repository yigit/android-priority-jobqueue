package com.tarkalabs.android.jobqueue.test.util;

import com.tarkalabs.android.jobqueue.JobQueue;
import com.tarkalabs.android.jobqueue.timer.Timer;

public interface JobQueueFactory {
    public JobQueue createNew(long sessionId, String id, Timer timer);
}
