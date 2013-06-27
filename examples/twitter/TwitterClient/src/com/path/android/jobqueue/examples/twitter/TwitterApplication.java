package com.path.android.jobqueue.examples.twitter;

import android.app.Application;
import com.path.android.jobqueue.JobManager;

public class TwitterApplication extends Application {
    private static TwitterApplication instance;
    private JobManager jobManager;

    public TwitterApplication() {
        instance = this;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        jobManager = new JobManager(this, JobManager.createDefaultConfiguration().maxConsumerCount(1));
    }

    public JobManager getJobManager() {
        return jobManager;
    }

    public static TwitterApplication getInstance() {
        return instance;
    }
}
