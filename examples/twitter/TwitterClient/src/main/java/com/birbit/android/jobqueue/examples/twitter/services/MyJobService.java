package com.birbit.android.jobqueue.examples.twitter.services;

import androidx.annotation.NonNull;

import com.birbit.android.jobqueue.JobManager;
import com.birbit.android.jobqueue.scheduling.FrameworkJobSchedulerService;
import com.birbit.android.jobqueue.examples.twitter.TwitterApplication;

public class MyJobService extends FrameworkJobSchedulerService {
    @NonNull
    @Override
    protected JobManager getJobManager() {
        return TwitterApplication.getInstance().getJobManager();
    }
}
