package com.tarkalabs.android.jobqueue;


import com.tarkalabs.android.jobqueue.JobQueue;
import com.tarkalabs.android.jobqueue.config.Configuration;

public interface QueueFactory {
    JobQueue createPersistentQueue(Configuration configuration, long sessionId);
    JobQueue createNonPersistent(Configuration configuration, long sessionId);
}
