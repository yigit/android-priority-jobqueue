package com.tarkalabs.android.jobqueue;

import com.tarkalabs.android.jobqueue.inMemoryQueue.SimpleInMemoryPriorityQueue;
import com.tarkalabs.android.jobqueue.*;
import com.tarkalabs.android.jobqueue.cachedQueue.CachedJobQueue;
import com.tarkalabs.android.jobqueue.config.Configuration;
import com.tarkalabs.android.jobqueue.persistentQueue.sqlite.SqliteJobQueue;

/**
 * Default implementation of QueueFactory that creates one {@link SqliteJobQueue} and
 * one {@link SimpleInMemoryPriorityQueue} both are wrapped inside a {@link CachedJobQueue} to
 * improve performance
 */
public class DefaultQueueFactory implements QueueFactory {
    SqliteJobQueue.JobSerializer jobSerializer;

    public DefaultQueueFactory() {
        jobSerializer = new SqliteJobQueue.JavaSerializer();
    }

    public DefaultQueueFactory(SqliteJobQueue.JobSerializer jobSerializer) {
        this.jobSerializer = jobSerializer;
    }

    @Override
    public JobQueue createPersistentQueue(Configuration configuration, long sessionId) {
        return new CachedJobQueue(new SqliteJobQueue(configuration, sessionId, jobSerializer));
    }

    @Override
    public JobQueue createNonPersistent(Configuration configuration, long sessionId) {
        return new CachedJobQueue(new SimpleInMemoryPriorityQueue(configuration, sessionId));
    }
}
