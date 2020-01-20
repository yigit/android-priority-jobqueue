package com.birbit.android.jobqueue.examples.twitter.services;

import androidx.annotation.NonNull;

import com.birbit.android.jobqueue.JobManager;
import com.birbit.android.jobqueue.examples.twitter.TwitterApplication;
import com.birbit.android.jobqueue.scheduling.GcmJobSchedulerService;

/**
 * Created by yboyar on 3/20/16.
 */
public class MyGcmJobService extends GcmJobSchedulerService {
    @NonNull
    @Override
    protected JobManager getJobManager() {
        return TwitterApplication.getInstance().getJobManager();
    }
}
