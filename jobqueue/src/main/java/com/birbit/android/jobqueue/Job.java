package com.birbit.android.jobqueue;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.birbit.android.jobqueue.log.JqLog;
import com.birbit.android.jobqueue.network.NetworkUtil;
import com.birbit.android.jobqueue.timer.Timer;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Base class for all of your jobs.
 */
@SuppressWarnings("deprecation")
abstract public class Job implements Serializable {
    private static final long serialVersionUID = 3L;
    @SuppressWarnings("WeakerAccess")
    public static final int DEFAULT_RETRY_LIMIT = 20;
    static final String SINGLE_ID_TAG_PREFIX = "job-single-id:";
    // set either in constructor or by the JobHolder
    /**package**/ private transient String id;
    // values set from params
    @NetworkUtil.NetworkStatus
    transient int requiredNetworkType;
    // values set after job is covered by a JobHolder
    private transient String groupId;
    private transient boolean persistent;
    private transient Set<String> readonlyTags;

    private transient int currentRunCount;
    /**package**/ transient int priority;
    private transient long delayInMs;
    private transient long deadlineInMs;
    private transient boolean cancelOnDeadline;
    /*package*/ transient volatile boolean cancelled;

    // set when job is loaded
    private transient Context applicationContext;

    private transient volatile boolean sealed;

    // set when job is loaded
    private transient volatile boolean isDeadlineReached;


    protected Job(Params params) {
        this.id = UUID.randomUUID().toString();
        this.requiredNetworkType = params.requiredNetworkType;
        this.persistent = params.isPersistent();
        this.groupId = params.getGroupId();
        this.priority = params.getPriority();
        this.delayInMs = Math.max(0, params.getDelayMs());
        this.deadlineInMs = Math.max(0, params.getDeadlineMs());
        this.cancelOnDeadline = params.shouldCancelOnDeadline();
        final String singleId = params.getSingleId();
        if (params.getTags() != null || singleId != null) {
            final Set<String> tags = params.getTags() != null ? params.getTags() : new HashSet<String>();
            if (singleId != null) {
                final String tagForSingleId = createTagForSingleId(singleId);
                tags.add(tagForSingleId);
                if (this.groupId == null) {
                    this.groupId = tagForSingleId;
                }
            }
            this.readonlyTags = Collections.unmodifiableSet(tags);
        }
        if (deadlineInMs > 0 && deadlineInMs < delayInMs) {
            throw new IllegalArgumentException("deadline cannot be less than the delay. It" +
                    " does not make sense. deadline:" + deadlineInMs + "," +
                    "delay:" + delayInMs);
        }

    }

    public final String getId() {
        return id;
    }

    /**
     * used by {@link JobManager} to assign proper priority at the time job is added.
     * @return priority (higher = better)
     */
    public final int getPriority() {
        return priority;
    }

    /**
     * used by {@link JobManager} to assign proper delay at the time job is added.
     * This field is not persisted!
     * @return delay in ms
     */
    public final long getDelayInMs() {
        return delayInMs;
    }

    /**
     * Returns a readonly set of tags attached to this Job.
     *
     * @return Set of Tags. If tags do not exists, returns null.
     */
    @Nullable
    public final Set<String> getTags() {
        return readonlyTags;
    }

    private void writeObject(ObjectOutputStream oos) throws IOException {
        if (!sealed) {
            throw new IllegalStateException("A job cannot be serialized w/o first being added into"
                    + " a job manager.");
        }
    }

    /**
     * Job class keeps some data within itself which is later moved to the JobHolder.
     * <p>
     * When a Job is saved to disk, this information might be lost. This method is provided as a
     * convenience to the custom queues to put this data back into the Job when it is re-created.
     *
     * @param holder The JobHolder which holds this job.
     */
    final void updateFromJobHolder(JobHolder holder) {
        if (sealed) {
            throw new IllegalStateException("Cannot set a Job from JobHolder after it is sealed.");
        }
        id = holder.id;
        groupId = holder.groupId;
        priority = holder.getPriority();
        this.persistent = holder.persistent;
        readonlyTags = holder.tags;
        requiredNetworkType = holder.requiredNetworkType;
        sealed = true; //  deserialized jobs are sealed
    }

    /**
     * Whether we should add this job to disk or non-persistent queue
     *
     * @return True if this job should be persistent between app restarts
     */
    public final boolean isPersistent() {
        return persistent;
    }

    /**
     * Called when the job is added to disk and committed.
     * This means job will eventually run. This is a good time to update local database and dispatch events.
     * <p>
     * Changes to this class will not be preserved if your job is persistent !!!
     * <p>
     * Also, if your app crashes right after adding the job, {@code onRun} might be called without an {@code onAdded} call
     * <p>
     * Note that this method is called on JobManager's thread and will block any other action so
     * it should be fast and not make any web requests (File IO is OK).
     */
    abstract public void onAdded();

    /**
     * The actual method that should to the work.
     * It should finish w/o any exception. If it throws any exception,
     * {@link #shouldReRunOnThrowable(Throwable, int, int)} will be called to
     * decide either to dismiss the job or re-run it.
     *
     * @throws Throwable Can throw and exception which will mark job run as failed
     */
    abstract public void onRun() throws Throwable;

    /**
     * Called when a job is cancelled.
     * @param cancelReason It is one of:
     *                   <ul>
     *                   <li>{@link CancelReason#REACHED_RETRY_LIMIT}</li>
     *                   <li>{@link CancelReason#CANCELLED_VIA_SHOULD_RE_RUN}</li>
     *                   <li>{@link CancelReason#CANCELLED_WHILE_RUNNING}</li>
     *                   <li>{@link CancelReason#SINGLE_INSTANCE_WHILE_RUNNING}</li>
     *                   <li>{@link CancelReason#SINGLE_INSTANCE_ID_QUEUED}</li>
     *                   </ul>
     * @param throwable The exception that was thrown from the last execution of {@link #onRun()}
     */
    abstract protected void onCancel(@CancelReason int cancelReason, @Nullable Throwable throwable);

    /**
     * If {@code onRun} method throws an exception, this method is called.
     * <p>
     * If you simply want to return retry or cancel, you can use {@link RetryConstraint#RETRY} or
     * {@link RetryConstraint#CANCEL}.
     * <p>
     * You can also use a custom {@link RetryConstraint} where you can change the Job's priority or
     * add a delay until the next run (e.g. exponential back off).
     * <p>
     * Note that changing the Job's priority or adding a delay may alter the original run order of
     * the job. So if the job was added to the queue with other jobs and their execution order is
     * important (e.g. they use the same groupId), you should not change job's priority or add a
     * delay unless you really want to change their execution order.
     *
     * @param throwable The exception that was thrown from {@link #onRun()}
     * @param runCount The number of times this job run. Starts from 1.
     * @param maxRunCount The max number of times this job can run. Decided by {@link #getRetryLimit()}
     *
     * @return A {@link RetryConstraint} to decide whether this Job should be tried again or not and
     * if yes, whether we should add a delay or alter its priority. Returning null from this method
     * is equal to returning {@link RetryConstraint#RETRY}.
     */
    abstract protected RetryConstraint shouldReRunOnThrowable(@NonNull Throwable throwable, int runCount, int maxRunCount);

    /**
     * Runs the job and catches any exception
     *
     * @param currentRunCount The current run count of the job
     *
     * @return one of the RUN_RESULT ints
     */
    final int safeRun(JobHolder holder, int currentRunCount, Timer timer) {
        this.currentRunCount = currentRunCount;
        if (JqLog.isDebugEnabled()) {
            JqLog.d("running job %s", this.getClass().getSimpleName());
        }
        boolean reRun = false;
        boolean failed = false;
        Throwable throwable = null;
        boolean cancelForDeadline = false;
        try {
            onRun();
            if (JqLog.isDebugEnabled()) {
                JqLog.d("finished job %s", this);
            }
        } catch (Throwable t) {
            failed = true;
            throwable = t;
            JqLog.e(t, "error while executing job %s", this);
            cancelForDeadline = holder.shouldCancelOnDeadline()
                    && holder.getDeadlineNs() <= timer.nanoTime();
            reRun = currentRunCount < getRetryLimit() && !cancelForDeadline;
            if(reRun && !cancelled) {
                try {
                    RetryConstraint retryConstraint = shouldReRunOnThrowable(t, currentRunCount,
                            getRetryLimit());
                    if (retryConstraint == null) {
                        retryConstraint = RetryConstraint.RETRY;
                    }
                    holder.retryConstraint = retryConstraint;
                    reRun = retryConstraint.shouldRetry();
                } catch (Throwable t2) {
                    JqLog.e(t2, "shouldReRunOnThrowable did throw an exception");
                }
            }
        }
        JqLog.d("safeRunResult for %s : %s. re run:%s. cancelled: %s", this, !failed, reRun, cancelled);
        if (!failed) {
            return JobHolder.RUN_RESULT_SUCCESS;
        }
        if (holder.isCancelledSingleId()) {
            return JobHolder.RUN_RESULT_FAIL_SINGLE_ID;
        }
        if (holder.isCancelled()) {
            return JobHolder.RUN_RESULT_FAIL_FOR_CANCEL;
        }
        if (reRun) {
            return JobHolder.RUN_RESULT_TRY_AGAIN;
        }
        if (cancelForDeadline) {
            return JobHolder.RUN_RESULT_HIT_DEADLINE;
        }
        if (currentRunCount < getRetryLimit()) {
            // only set the Throwable if we are sure the Job is not gonna run again
            holder.setThrowable(throwable);
            return JobHolder.RUN_RESULT_FAIL_SHOULD_RE_RUN;
        } else {
            // only set the Throwable if we are sure the Job is not gonna run again
            holder.setThrowable(throwable);
            return JobHolder.RUN_RESULT_FAIL_RUN_LIMIT;
        }
    }

    /**
     * Before each run, JobManager sets this number.
     * <p>
     * Might be useful for the {@link com.birbit.android.jobqueue.Job#onRun()} method
     *
     * @return The current retry count of the Job
     */
    public final int getCurrentRunCount() {
        return currentRunCount;
    }

    /**
     * Some jobs may require being run synchronously. For instance, if it is a job like sending a comment, we should
     * never run them in parallel (unless they are being sent to different conversations).
     * By assigning same groupId to jobs, you can ensure that that type of jobs will be run in the order they were given
     * (if their priority is the same).
     *
     * @return The groupId of the job or null if it is not grouped
     */
    public final String getRunGroupId() {
        return groupId;
    }

    /**
     * Some jobs only need a single instance to be queued to run. For instance, if a user has made several changes
     * to a resource while offline, you can save every change locally during {@link #onAdded()}, but
     * only update the resource remotely once with the latest changes.
     *
     * @return The single instance id of the job or null if it is not a single instance job
     */
    public final String getSingleInstanceId() {
        if (readonlyTags != null) {
            for (String tag : readonlyTags) {
                if (tag.startsWith(SINGLE_ID_TAG_PREFIX)) {
                    return tag;
                }
            }
        }
        return null;
    }

    private String createTagForSingleId(String singleId) {
        return SINGLE_ID_TAG_PREFIX + singleId;
    }

    /**
     * By default, jobs will be retried {@code DEFAULT_RETRY_LIMIT}  times.
     * If job fails this many times, onCancel will be called w/o calling {@link #shouldReRunOnThrowable(Throwable, int, int)}
     *
     * @return The number of times the job should be re-tried before being cancelled automatically
     */
    protected int getRetryLimit() {
        return DEFAULT_RETRY_LIMIT;
    }

    /**
     * Returns true if job is cancelled. Note that if the job is already running when it is cancelled,
     * this flag is still set to true but job is NOT STOPPED (e.g. JobManager does not interrupt
     * the thread).
     * If you have a long job that may be cancelled, you can check this field and handle it manually.
     * <p>
     * Note that, if your job returns successfully from {@link #onRun()} method, it will be considered
     * as successfully completed, thus will be added to {@link CancelResult#getFailedToCancel()}
     * list. If you want this job to be considered as cancelled, you should throw an exception.
     * You can also use {@link #assertNotCancelled()} method to do it.
     * <p>
     * Calling this method outside {@link #onRun()} method has no meaning since {@link #onRun()} will not
     * be called if the job is cancelled before it is called.
     *
     * @return true if the Job is cancelled
     */
    public final boolean isCancelled() {
        return cancelled;
    }

    /**
     * Convenience method that checks if job is cancelled and throws a RuntimeException if it is
     * cancelled.
     */
    @SuppressWarnings({"WeakerAccess", "unused"})
    public void assertNotCancelled() {
        if (cancelled) {
            throw new RuntimeException("job is cancelled");
        }
    }

    /*package*/ void setApplicationContext(Context context) {
        this.applicationContext = context;
    }

    /*package*/ void setDeadlineReached(boolean deadlineReached) {
        isDeadlineReached = deadlineReached;
    }

    /**
     * Convenience method to get the application context in a Job.
     * <p>
     * This context is set when job is added to a JobManager.
     *
     * @return The application context
     */
    @SuppressWarnings("WeakerAccess")
    public Context getApplicationContext() {
        return applicationContext;
    }

    /**
     * Returns true if the job's deadline is reached.
     * <p>
     * Note that this method is safe to access only when the job is running. Value is undefined
     * if it is called outside the {@link #onRun()} method.
     *
     * @return true if job reached its deadline, false otherwise
     */
    public boolean isDeadlineReached() {
        return isDeadlineReached;
    }

    /**
     * Returns whether job requires a network connection to be run or not.
     *
     * @return True if job requires a network to be run, false otherwise.
     */
    @SuppressWarnings("unused")
    public final boolean requiresNetwork() {
        return requiredNetworkType >= NetworkUtil.METERED;
    }

    /**
     * Returns whether job requires a unmetered network connection to be run or not.
     *
     * @return True if job requires a unmetered network to be run, false otherwise.
     */
    @SuppressWarnings("unused")
    public final boolean requiresUnmeteredNetwork() {
        return requiredNetworkType >= NetworkUtil.UNMETERED;
    }

    /**package**/ long getDeadlineInMs() {
        return deadlineInMs;
    }

    /**package**/ boolean shouldCancelOnDeadline() {
        return cancelOnDeadline;
    }
}
