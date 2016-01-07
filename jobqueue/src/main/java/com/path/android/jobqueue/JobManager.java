package com.path.android.jobqueue;

import com.birbit.android.jobqueue.JobManager2;
import com.path.android.jobqueue.cachedQueue.CachedJobQueue;
import com.path.android.jobqueue.config.Configuration;
import com.path.android.jobqueue.nonPersistentQueue.NonPersistentPriorityQueue;
import com.path.android.jobqueue.persistentQueue.sqlite.SqliteJobQueue;
import com.path.android.jobqueue.timer.Timer;

import android.content.Context;

public class JobManager extends JobManager2 {
    public static final long NS_PER_MS = 1000000;
    public static final long NOT_RUNNING_SESSION_ID = Long.MIN_VALUE;
    public static final long NOT_DELAYED_JOB_DELAY = Long.MIN_VALUE;

    public JobManager(Configuration configuration) {
        super(configuration);
    }

    public JobManager(Context context, Configuration configuration) {
        super(configuration);
    }

    public JobManager(Context context) {
        this(context, "default");
    }


    public JobManager(Context context, String id) {
        this(new Configuration.Builder(context).id(id).build());
    }

    /**
     * Default implementation of QueueFactory that creates one {@link SqliteJobQueue} and one {@link NonPersistentPriorityQueue}
     * both are wrapped inside a {@link CachedJobQueue} to improve performance
     */
    public static class DefaultQueueFactory implements QueueFactory {
        SqliteJobQueue.JobSerializer jobSerializer;

        public DefaultQueueFactory() {
            jobSerializer = new SqliteJobQueue.JavaSerializer();
        }

        public DefaultQueueFactory(SqliteJobQueue.JobSerializer jobSerializer) {
            this.jobSerializer = jobSerializer;
        }

        @Override
        public JobQueue createPersistentQueue(Context context, Long sessionId, String id,
                boolean inTestMode, Timer timer) {
            return new CachedJobQueue(new SqliteJobQueue(context, sessionId, id, jobSerializer,
                    inTestMode, timer));
        }

        @Override
        public JobQueue createNonPersistent(Context context, Long sessionId, String id,
                boolean inTestMode, Timer timer) {
            return new CachedJobQueue(new NonPersistentPriorityQueue(sessionId, id, inTestMode, timer));
        }
    }
}
