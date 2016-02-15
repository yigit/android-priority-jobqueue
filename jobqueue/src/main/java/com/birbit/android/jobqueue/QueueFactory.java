package com.birbit.android.jobqueue;


import com.path.android.jobqueue.JobQueue;
import com.path.android.jobqueue.config.Configuration;

public interface QueueFactory {
    JobQueue createPersistentQueue(Configuration configuration, long sessionId);
    JobQueue createNonPersistent(Configuration configuration, long sessionId);
}