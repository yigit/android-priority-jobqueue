package com.birbit.android.jobqueue.callback;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.birbit.android.jobqueue.CancelResult;
import com.birbit.android.jobqueue.Job;
import com.birbit.android.jobqueue.JobHolder;
import com.birbit.android.jobqueue.TagConstraint;

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
     * {@link com.birbit.android.jobqueue.JobManager#cancelJobs(TagConstraint, String...)} while it was running.
     *
     * @see com.birbit.android.jobqueue.JobManager#cancelJobs(TagConstraint, String...)
     * @see com.birbit.android.jobqueue.JobManager#cancelJobsInBackground(CancelResult.AsyncCancelCallback, TagConstraint, String...)
     */
    int RESULT_CANCEL_CANCELLED_WHILE_RUNNING = JobHolder.RUN_RESULT_FAIL_FOR_CANCEL;
    /**
     * Used in {@link #onJobRun(Job, int)} when job throws an exception in {@link Job#onRun()}
     * and will be cancelled because another job with the same single instance id was
     * queued while it was running.
     *
     * @see Job#getSingleInstanceId()
     */
    int RESULT_CANCEL_SINGLE_INSTANCE_WHILE_RUNNING = JobHolder.RUN_RESULT_FAIL_SINGLE_ID;
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
    void onJobAdded(@NonNull Job job);

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
     *                   <li>{@link #RESULT_CANCEL_SINGLE_INSTANCE_WHILE_RUNNING}</li>
     *                   <li>{@link #RESULT_CANCEL_CANCELLED_VIA_SHOULD_RE_RUN}</li>
     *                   <li>{@link #RESULT_FAIL_WILL_RETRY}</li>
     *                   </ul>
     */
    void onJobRun(@NonNull Job job, int resultCode);

    /**
     * Called when a job is cancelled.
     *
     * @param job              The Job that was cancelled.
     * @param byCancelRequest  If true, the Job was cancelled in response to a
     *                         {@link com.birbit.android.jobqueue.JobManager#cancelJobs(TagConstraint, String...)} request.
     * @param throwable        The exception that was thrown from the last execution of {@link Job#onRun()}
     */
    void onJobCancelled(@NonNull Job job, boolean byCancelRequest, @Nullable Throwable throwable);

    /**
     * Called <b>after</b> a Job is removed from the JobManager. It might be cancelled or onFinished.
     * This call is a good place to be sure that JobManager is done with the Job.
     *
     * @param job The Job that was just removed from the JobManager.
     */
    void onDone(@NonNull Job job);

    /**
     * Called <b>after</b> a Job is run and its run result has been handled. For instance, if the
     * Job is cancelled, this method is called after the job is removed from the queue.
     *
     * @param job The Job that just onFinished a run call.
     * @param resultCode The result of the run call.
     *
     * @see #onJobRun(Job, int)
     */
    void onAfterJobRun(@NonNull Job job, int resultCode);
}
