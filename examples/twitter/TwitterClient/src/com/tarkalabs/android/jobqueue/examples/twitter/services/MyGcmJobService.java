package com.tarkalabs.android.jobqueue.examples.twitter.services;

import android.support.annotation.NonNull;

import com.tarkalabs.android.jobqueue.JobManager;
import com.tarkalabs.android.jobqueue.examples.twitter.TwitterApplication;
import com.tarkalabs.android.jobqueue.scheduling.GcmJobSchedulerService;

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
