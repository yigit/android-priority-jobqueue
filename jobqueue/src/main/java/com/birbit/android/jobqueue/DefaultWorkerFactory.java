package com.birbit.android.jobqueue;

import android.support.annotation.NonNull;

import java.util.UUID;

final public class DefaultWorkerFactory implements WorkerFactory {

    private static WorkerFactory instance = null;

    public static WorkerFactory getInstance() {
        if (instance == null) {
            instance = new DefaultWorkerFactory();
        }
        return instance;
    }

    private DefaultWorkerFactory() {}

    @NonNull
    @Override
    final public Thread create(@NonNull ThreadGroup threadGroup, @NonNull Runnable consumer, int priority) {
        final Thread thread = new Thread(threadGroup, consumer, "job-queue-worker-" + UUID.randomUUID());
        thread.setPriority(priority);
        return thread;
    }

}
