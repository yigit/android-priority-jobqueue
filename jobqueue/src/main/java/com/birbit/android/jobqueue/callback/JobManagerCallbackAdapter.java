package com.birbit.android.jobqueue.callback;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.birbit.android.jobqueue.Job;

/**
 * An empty implementation of {@link JobManagerCallback}. You are advice to override this one
 * instead so that if new methods are added to the interface, your code won't break.
 */
public class JobManagerCallbackAdapter implements JobManagerCallback {
    @Override
    public void onJobAdded(@NonNull Job job) {

    }

    @Override
    public void onJobRun(@NonNull Job job, int resultCode) {

    }

    @Override
    public void onJobCancelled(@NonNull Job job, boolean byCancelRequest, @Nullable Throwable throwable) {

    }

    @Override
    public void onDone(@NonNull Job job) {

    }

    @Override
    public void onAfterJobRun(@NonNull Job job, int resultCode) {

    }
}
