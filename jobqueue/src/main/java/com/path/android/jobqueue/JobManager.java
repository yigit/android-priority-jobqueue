package com.path.android.jobqueue;

import com.birbit.android.jobqueue.JobManager2;
import com.path.android.jobqueue.config.Configuration;

import android.content.Context;

import java.util.concurrent.TimeUnit;

public class JobManager extends JobManager2 {
    public static final long NS_PER_MS = 1000000;
    public static final long NOT_RUNNING_SESSION_ID = Long.MIN_VALUE;
    public static final long NOT_DELAYED_JOB_DELAY = Long.MIN_VALUE;
    public static final long NETWORK_CHECK_INTERVAL = TimeUnit.MILLISECONDS.toNanos(10000);
    /**
     * The min delay in MS which will trigger usage of JobScheduler.
     * If a job is added with a delay in less than this value, JobManager will not use the scheduler
     * to wake up the application.
     */
    public static final long MIN_DELAY_TO_USE_SCHEDULER_IN_MS = 1000 * 30;

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
