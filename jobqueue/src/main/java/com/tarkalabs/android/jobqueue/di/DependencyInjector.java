package com.tarkalabs.android.jobqueue.di;

import com.tarkalabs.android.jobqueue.Job;

/**
 * interface that can be provided to {@link com.tarkalabs.android.jobqueue.JobManager} for dependency injection
 * it is called before the job's onAdded method is called. for persistent jobs, also run after job is brought
 * back from disk.
 */
public interface DependencyInjector {
    void inject(Job job);
}
