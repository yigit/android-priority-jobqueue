package com.birbit.android.jobqueue.test.util;

import com.birbit.android.jobqueue.JobQueue;
import com.birbit.android.jobqueue.timer.Timer;

public interface JobQueueFactory {
    public JobQueue createNew(long sessionId, String id, Timer timer);
}
