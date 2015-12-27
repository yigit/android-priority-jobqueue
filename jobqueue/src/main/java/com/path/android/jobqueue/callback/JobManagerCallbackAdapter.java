package com.path.android.jobqueue.callback;

import com.path.android.jobqueue.Job;

/**
 * An empty implementation of {@link JobManagerCallback}. You are advice to override this one
 * instead so that if new methods are added to the interface, your code won't break.
 */
public class JobManagerCallbackAdapter implements JobManagerCallback {
    @Override
    public void onJobAdded(Job job) {

    }

    @Override
    public void onJobRun(Job job, int resultCode) {

    }

    @Override
    public void onJobCancelled(Job job, boolean byCancelRequest) {

    }
}
