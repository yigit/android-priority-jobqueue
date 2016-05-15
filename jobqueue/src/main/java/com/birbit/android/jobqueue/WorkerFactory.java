package com.birbit.android.jobqueue;

import android.support.annotation.NonNull;

/**
 * Interface to generate new worker {@link Thread}
 */
public interface WorkerFactory {

    /**
     * Create a new instance of a {@link Thread} that will be used to execute the {@code consumer}
     *
     * @param threadGroup The {@link ThreadGroup} used to group all JobManager's workers
     * @param consumer The {@link Runnable} that will be executed by this {@link Thread}
     * @param priority The priority at which the {@link Thread} should be run
     *
     * @return a {@link Thread} that will execute the {@code consumer}
     */
    @NonNull
    Thread create(@NonNull final ThreadGroup threadGroup, @NonNull final Runnable consumer, final int priority);

}
