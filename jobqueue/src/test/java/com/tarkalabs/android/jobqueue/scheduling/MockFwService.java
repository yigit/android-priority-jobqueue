package com.tarkalabs.android.jobqueue.scheduling;

import android.support.annotation.NonNull;

import com.tarkalabs.android.jobqueue.JobManager;

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
