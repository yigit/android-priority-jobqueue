package com.path.android.jobqueue;

import com.birbit.android.jobqueue.JobManager2;
import com.path.android.jobqueue.cachedQueue.CachedJobQueue;
import com.path.android.jobqueue.config.Configuration;
import com.path.android.jobqueue.nonPersistentQueue.NonPersistentPriorityQueue;
import com.path.android.jobqueue.persistentQueue.sqlite.SqliteJobQueue;
import com.path.android.jobqueue.timer.Timer;

import android.content.Context;

import java.util.concurrent.TimeUnit;

public class JobManager extends JobManager2 {
    public static final long NS_PER_MS = 1000000;
    public static final long NOT_RUNNING_SESSION_ID = Long.MIN_VALUE;
    public static final long NOT_DELAYED_JOB_DELAY = Long.MIN_VALUE;
    public static final long NETWORK_CHECK_INTERVAL = TimeUnit.MILLISECONDS.toNanos(10000);

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
}
