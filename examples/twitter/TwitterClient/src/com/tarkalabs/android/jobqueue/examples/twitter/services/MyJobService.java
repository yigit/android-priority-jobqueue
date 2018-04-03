package com.tarkalabs.android.jobqueue.examples.twitter.services;

import android.support.annotation.NonNull;

import com.tarkalabs.android.jobqueue.JobManager;
import com.tarkalabs.android.jobqueue.scheduling.FrameworkJobSchedulerService;
import com.tarkalabs.android.jobqueue.examples.twitter.TwitterApplication;

public class MyJobService extends FrameworkJobSchedulerService {
    @NonNull
    @Override
    protected JobManager getJobManager() {
        return TwitterApplication.getInstance().getJobManager();
    }
}
