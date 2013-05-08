package com.path.android.jobqueue.di;

import com.path.android.jobqueue.BaseJob;

/**
 * interface that can be provided to {@link com.path.android.jobqueue.JobManager} for dependency injection
 * it is called before the job is run
 */
public interface DependencyInjector {
    public void inject(BaseJob job);
}
