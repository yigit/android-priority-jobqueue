package com.path.android.jobqueue;

import java.util.Collection;
import java.util.HashSet;

/**
 * Canceling jobs is an expensive operation because it requires JobManager to deserializer the job
 * from databases and call onCancel method on it.
 * <p>
 * When cancelling jobs, if you need to get the list of canceled jobs, you can provide this
 * callback to {@link JobManager#cancelJobsInBackground(CancelResult.AsyncCancelCallback, TagConstraint, String...)}
 * method.
 */
public class CancelResult {
    Collection<Job> canceledJobs;
    Collection<Job> failedToCancel;

    public CancelResult() {
        this.canceledJobs = new HashSet<Job>();
        this.failedToCancel = new HashSet<Job>();
    }

    /**
     * @return The list of jobs that are canceled before they did run
     */
    public Collection<Job> getCanceledJobs() {
        return canceledJobs;
    }

    /**
     * @return The list of jobs that were running when cancel was called and finished running
     * successfully before they could be canceled.
     */
    public Collection<Job> getFailedToCancel() {
        return failedToCancel;
    }

    public static interface AsyncCancelCallback {

        /**
         * When job cancellation is complete, this method is called by the JobManager.
         */
        public void onCanceled(CancelResult cancelResult);
    }
}
