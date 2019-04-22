package com.tarkalabs.android.jobqueue.scheduling;

import com.tarkalabs.android.jobqueue.JobManager;

import androidx.annotation.NonNull;

public class MockFwService extends FrameworkJobSchedulerService {
    private JobManager jobManager;
    public MockFwService(JobManager jobManager) {
        this.jobManager = jobManager;
    }

    @NonNull
    @Override
    protected JobManager getJobManager() {
        return jobManager;
    }
}
