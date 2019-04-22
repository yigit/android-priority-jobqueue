package com.tarkalabs.android.jobqueue.scheduling;

import com.tarkalabs.android.jobqueue.JobManager;

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
