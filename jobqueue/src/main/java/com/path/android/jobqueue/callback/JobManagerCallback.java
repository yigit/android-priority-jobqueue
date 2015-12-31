package com.path.android.jobqueue.callback;

import com.path.android.jobqueue.CancelResult;
import com.path.android.jobqueue.Job;
import com.path.android.jobqueue.JobHolder;
import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.TagConstraint;

/**
 * A callback class that you can attach to the JobManager to get notified as Jobs change states.
 */
public interface JobManagerCallback {
    /**
     * Used in {@link #onJobRun(Job, int)} when properly completes {@link Job#onRun()}.
     */
    int RESULT_SUCCEED = JobHolder.RUN_RESULT_SUCCESS;
    /**
     * Used in {@link #onJobRun(Job, int)} when job throws an exception in {@link Job#onRun()}
     * and will be cancelled because it has reached its retry limit.
     *
     * @see Job#getRetryLimit()
     */
    int RESULT_CANCEL_REACHED_RETRY_LIMIT = JobHolder.RUN_RESULT_FAIL_RUN_LIMIT;
    /**
     * Used in {@link #onJobRun(Job, int)} when job throws an exception in {@link Job#onRun()}
     * and will be cancelled because it was cancelled via
     * {@link JobManager#cancelJobs(TagConstraint, String...)} while it was running.
     *
     * @see JobManager#cancelJobs(TagConstraint, String...)
     * @see JobManager#cancelJobsInBackground(CancelResult.AsyncCancelCallback, TagConstraint, String...)
     */
    int RESULT_CANCEL_CANCELLED_WHILE_RUNNING = JobHolder.RUN_RESULT_FAIL_FOR_CANCEL;
    /**
     * Used in {@link #onJobRun(Job, int)} when job throws an exception in {@link Job#onRun()}
     * and will be cancelled because it decided not to run again via
     * {@link Job#shouldReRunOnThrowable(Throwable, int, int)}.
     */
    int RESULT_CANCEL_CANCELLED_VIA_SHOULD_RE_RUN = JobHolder.RUN_RESULT_FAIL_SHOULD_RE_RUN;
    /**
     * Used in {@link #onJobRun(Job, int)} when job throws an exception in {@link Job#onRun()}
     * and wanted to retry via {@link Job#shouldReRunOnThrowable(Throwable, int, int)}.
     *
     * @see Job#getRetryLimit()
     */
    int RESULT_FAIL_WILL_RETRY = JobHolder.RUN_RESULT_TRY_AGAIN;

    /**
     * Called when a Job is added to the JobManager. This method is called <b>after</b> Job's
     * onAdded method is called.
     *
     * @param job The Job that was added to the JobManager.
     */
    void onJobAdded(Job job);

    /**
     * Called after a Job has been Run. Might be called multiple times if the Job runs multiple
     * times (due to failures).
     *
     * @param job        The Job that did just run.
     * @param resultCode The result of the {@link Job#onRun()}. It is one of:
     *                   <ul>
     *                   <li>{@link #RESULT_SUCCEED}</li>
     *                   <li>{@link #RESULT_CANCEL_REACHED_RETRY_LIMIT}</li>
     *                   <li>{@link #RESULT_CANCEL_CANCELLED_WHILE_RUNNING}</li>
     *                   <li>{@link #RESULT_CANCEL_CANCELLED_VIA_SHOULD_RE_RUN}</li>
     *                   <li>{@link #RESULT_FAIL_WILL_RETRY}</li>
     *                   </ul>
     */
    void onJobRun(Job job, int resultCode);

    /**
     * Called when a job is cancelled.
     *
     * @param job              The Job that was cancelled.
     * @param byCancelRequest  If true, the Job was cancelled in response to a
     *                         {@link JobManager#cancelJobs(TagConstraint, String...)} request.
     */
    void onJobCancelled(Job job, boolean byCancelRequest);
}
