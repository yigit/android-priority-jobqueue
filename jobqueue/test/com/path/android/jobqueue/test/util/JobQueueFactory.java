package com.path.android.jobqueue.test.util;

import com.path.android.jobqueue.JobQueue;

public interface JobQueueFactory {
    public JobQueue createNew(long sessionId, String id);
}
