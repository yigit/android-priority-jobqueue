package com.birbit.android.jobqueue.scheduling;

import androidx.annotation.NonNull;

import com.birbit.android.jobqueue.JobManager;

public class MockGcmService extends GcmJobSchedulerService {
    private JobManager jobManager;
    public MockGcmService(JobManager jobManager) {
        this.jobManager = jobManager;
    }

    @NonNull
    @Override
    protected JobManager getJobManager() {
        return jobManager;
    }
}
