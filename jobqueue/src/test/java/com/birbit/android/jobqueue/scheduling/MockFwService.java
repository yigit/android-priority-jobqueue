package com.birbit.android.jobqueue.scheduling;

import androidx.annotation.NonNull;

import com.birbit.android.jobqueue.JobManager;

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
