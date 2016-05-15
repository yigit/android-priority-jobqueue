package com.birbit.android.jobqueue;

import android.support.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * A list of possible reasons why a Job was cancelled. A reason will be passed to {@link Job#onCancel(int, Throwable)}.
 */
@Retention(RetentionPolicy.SOURCE)
@IntDef({CancelReason.REACHED_RETRY_LIMIT,
        CancelReason.CANCELLED_WHILE_RUNNING,
        CancelReason.SINGLE_INSTANCE_WHILE_RUNNING,
        CancelReason.CANCELLED_VIA_SHOULD_RE_RUN,
        CancelReason.SINGLE_INSTANCE_ID_QUEUED})
public @interface CancelReason {

    /**
     * Used when job throws an exception in {@link Job#onRun()}
     * and will be cancelled because it has reached its retry limit.
     *
     * @see Job#getRetryLimit()
     */
    int REACHED_RETRY_LIMIT = JobHolder.RUN_RESULT_FAIL_RUN_LIMIT;

    /**
     * Used when job throws an exception in {@link Job#onRun()}
     * and will be cancelled because it was cancelled via
     * {@link JobManager#cancelJobs(TagConstraint, String...)} while it was running.
     *
     * @see JobManager#cancelJobs(TagConstraint, String...)
     * @see JobManager#cancelJobsInBackground(CancelResult.AsyncCancelCallback, TagConstraint, String...)
     */
    int CANCELLED_WHILE_RUNNING = JobHolder.RUN_RESULT_FAIL_FOR_CANCEL;

    /**
     * Used when job throws an exception in {@link Job#onRun()}
     * and will be cancelled because another job with the same single instance id was
     * queued while it was running.
     *
     * @see Job#getSingleInstanceId()
     */
    int SINGLE_INSTANCE_WHILE_RUNNING = JobHolder.RUN_RESULT_FAIL_SINGLE_ID;

    /**
     * Used when job throws an exception in {@link Job#onRun()}
     * and will be cancelled because it decided not to run again via
     * {@link Job#shouldReRunOnThrowable(Throwable, int, int)}.
     */
    int CANCELLED_VIA_SHOULD_RE_RUN = JobHolder.RUN_RESULT_FAIL_SHOULD_RE_RUN;

    /**
     * Used when a job was added while another job with the same single instance ID was already
     * queued and not running. This job got cancelled immediately after being added and will not run.
     */
    int SINGLE_INSTANCE_ID_QUEUED = 1;

}
