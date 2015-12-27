package com.path.android.jobqueue;

import com.path.android.jobqueue.log.JqLog;

import android.content.Context;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Base class for all of your jobs.
 */
@SuppressWarnings("deprecation")
abstract public class Job implements Serializable {
    private static final long serialVersionUID = 3L;
    public static final int DEFAULT_RETRY_LIMIT = 20;

    private boolean requiresNetwork;
    private String groupId;
    private boolean persistent;
    private Set<String> readonlyTags;

    private transient int currentRunCount;
    transient int priority;
    private transient long delayInMs;
    transient boolean cancelled;

    private transient Context applicationContext;

    /**
     * Only set if a job fails. Will be cleared by JobManager after it is handled
     */
    transient RetryConstraint retryConstraint;


    protected Job(Params params) {
        this.requiresNetwork = params.doesRequireNetwork();
        this.persistent = params.isPersistent();
        this.groupId = params.getGroupId();
        this.priority = params.getPriority();
        this.delayInMs = params.getDelayMs();
        final Set<String> tags = params.getTags();
        this.readonlyTags = tags == null ? null : Collections.unmodifiableSet(tags);
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
     * @return Set of Tags. If tags do not exists, returns null.
     */
    public final Set<String> getTags() {
        return readonlyTags;
    }

    private void writeObject(ObjectOutputStream oos) throws IOException {
        oos.writeBoolean(requiresNetwork);
        oos.writeObject(groupId);
        oos.writeBoolean(persistent);
        final int tagCount = readonlyTags == null ? 0 : readonlyTags.size();
        oos.writeInt(tagCount);
        if (tagCount > 0) {
            for (String tag : readonlyTags) {
                oos.writeUTF(tag);
            }
        }
    }


    private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
        requiresNetwork = ois.readBoolean();
        groupId = (String) ois.readObject();
        persistent = ois.readBoolean();
        final int tagCount = ois.readInt();
        if (tagCount > 0) {
            readonlyTags = new HashSet<String>(tagCount);
            for (int i = 0; i < tagCount; i ++) {
                readonlyTags.add(ois.readUTF());
            }
        }
    }

    /**
     * defines if we should add this job to disk or non-persistent queue
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
     * Note that this method is called on the thread the job is added. (e.g. {@link JobManager#addJob(Job)})
     * is called. If job was added via {@link  JobManager#addJobInBackground(Job)} or
     * {@link JobManager#addJobInBackground(Job, AsyncAddCallback)}, it will be run on JobManager's
     * utility thread.
     */
    abstract public void onAdded();

    /**
     * The actual method that should to the work.
     * It should finish w/o any exception. If it throws any exception,
     * {@link #shouldReRunOnThrowable(Throwable, int, int)} will be called to
     * decide either to dismiss the job or re-run it.
     * @throws Throwable
     */
    abstract public void onRun() throws Throwable;

    /**
     * Called when a job is cancelled.
     */
    abstract protected void onCancel();

    /**
     * @deprecated use {@link #shouldReRunOnThrowable(Throwable, int, int)}
     * This method will be removed in v2.0 and {@link #shouldReRunOnThrowable(Throwable, int, int)}
     * will become abstract.
     */
    @Deprecated
    protected boolean shouldReRunOnThrowable(Throwable throwable) {
        return true;
    }

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
     * @return A {@link RetryConstraint} to decide whether this Job should be tried again or not and
     * if yes, whether we should add a delay or alter its priority. Returning null from this method
     * is equal to returning {@link RetryConstraint#RETRY}. Default implementation calls
     * {@link #shouldReRunOnThrowable(Throwable)}.
     */
    protected RetryConstraint shouldReRunOnThrowable(Throwable throwable, int runCount, int maxRunCount) {
        boolean reRun = shouldReRunOnThrowable(throwable);
        return reRun ? RetryConstraint.RETRY : RetryConstraint.CANCEL;
    }

    /**
     * Runs the job and catches any exception
     * @param currentRunCount
     * @return one of the RUN_RESULT ints
     */
    final int safeRun(JobHolder holder, int currentRunCount) {
        this.currentRunCount = currentRunCount;
        if (JqLog.isDebugEnabled()) {
            JqLog.d("running job %s", this.getClass().getSimpleName());
        }
        boolean reRun = false;
        boolean failed = false;
        try {
            onRun();
            if (JqLog.isDebugEnabled()) {
                JqLog.d("finished job %s", this);
            }
        } catch (Throwable t) {
            failed = true;
            JqLog.e(t, "error while executing job %s", this);
            reRun = currentRunCount < getRetryLimit();
            if(reRun && !cancelled) {
                try {
                    RetryConstraint retryConstraint = shouldReRunOnThrowable(t, currentRunCount,
                            getRetryLimit());
                    if (retryConstraint == null) {
                        retryConstraint = RetryConstraint.RETRY;
                    }
                    this.retryConstraint = retryConstraint;
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
        if (holder.isCancelled()) {
            return JobHolder.RUN_RESULT_FAIL_FOR_CANCEL;
        }
        if (reRun) {
            return JobHolder.RUN_RESULT_TRY_AGAIN;
        }
        if (currentRunCount < getRetryLimit()) {
            return JobHolder.RUN_RESULT_FAIL_SHOULD_RE_RUN;
        } else {
            return JobHolder.RUN_RESULT_FAIL_RUN_LIMIT;
        }
    }

    /**
     * before each run, JobManager sets this number. Might be useful for the {@link com.path.android.jobqueue.Job#onRun()}
     * method
     */
    protected int getCurrentRunCount() {
        return currentRunCount;
    }

    /**
     * if job is set to require network, it will not be called unless {@link com.path.android.jobqueue.network.NetworkUtil}
     * reports that there is a network connection
     */
    public final boolean requiresNetwork() {
        return requiresNetwork;
    }

    /**
     * Some jobs may require being run synchronously. For instance, if it is a job like sending a comment, we should
     * never run them in parallel (unless they are being sent to different conversations).
     * By assigning same groupId to jobs, you can ensure that that type of jobs will be run in the order they were given
     * (if their priority is the same).
     * @return
     */
    public final String getRunGroupId() {
        return groupId;
    }

    /**
     * By default, jobs will be retried {@code DEFAULT_RETRY_LIMIT}  times.
     * If job fails this many times, onCancel will be called w/o calling {@link #shouldReRunOnThrowable(Throwable, int, int)}
     * @return
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
     */
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * Convenience method that checks if job is cancelled and throws a RuntimeException if it is
     * cancelled.
     */
    public void assertNotCancelled() {
        if (cancelled) {
            throw new RuntimeException("job is cancelled");
        }
    }

    /*package*/ void setApplicationContext(Context context) {
        this.applicationContext = context;
    }

    /**
     * Convenience method to get the application context in a Job.
     * <p>
     * This context is set when job is added to a JobManager.
     *
     * @return The application context
     */
    public Context getApplicationContext() {
        return applicationContext;
    }
}
