package com.birbit.android.jobqueue.scheduling;

import android.annotation.TargetApi;
import android.app.job.JobParameters;
import android.app.job.JobService;

/**
 * The service implementation for the framework job scheduler
 */
@TargetApi(21)
abstract public class FrameworkJobSchedulerService extends JobService {
    @Override
    public void onCreate() {
        super.onCreate();
        getScheduler().setJobService(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getScheduler().setJobService(null);
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        return getScheduler().onStartJob(params);
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return getScheduler().onStopJob(params);
    }

    abstract public FrameworkScheduler getScheduler();
}
