package com.path.android.jobqueue;

import java.util.Collection;
import java.util.HashSet;
import com.path.android.jobqueue.JobManager;

/**
 * This class holds the result of a cancel request via {@link JobManager#cancelJobs(TagConstraint, String...)}
 * or {@link JobManager#cancelJobsInBackground(CancelResult.AsyncCancelCallback, TagConstraint, String...)}.
 * <p>
 * Cancelling jobs is an expensive operation because it requires JobManager to deserializer the job
 * from databases and call onCancel method on it.
 * <p>
 * When cancelling jobs, if you need to get the list of cancelled jobs, you can provide this
 * callback to {@link JobManager#cancelJobsInBackground(CancelResult.AsyncCancelCallback, TagConstraint, String...)}
 * method.
 */
public class CancelResult {
    Collection<Job> cancelledJobs;
    Collection<Job> failedToCancel;

    public CancelResult() {
        this.cancelledJobs = new HashSet<Job>();
        this.failedToCancel = new HashSet<Job>();
    }

    /**
     * @return The list of jobs that are cancelled before they did run
     */
    public Collection<Job> getCancelledJobs() {
        return cancelledJobs;
    }

    /**
     * @return The list of jobs that were running when cancel was called and finished running
     * successfully before they could be cancelled.
     */
    public Collection<Job> getFailedToCancel() {
        return failedToCancel;
    }

    public static interface AsyncCancelCallback {

        /**
         * When job cancellation is complete, this method is called by the JobManager.
         */
        public void onCancelled(CancelResult cancelResult);
    }
}
