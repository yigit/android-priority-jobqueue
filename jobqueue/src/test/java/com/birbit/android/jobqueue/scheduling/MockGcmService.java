package com.birbit.android.jobqueue.scheduling;

import com.birbit.android.jobqueue.JobManager;

import androidx.annotation.NonNull;

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
