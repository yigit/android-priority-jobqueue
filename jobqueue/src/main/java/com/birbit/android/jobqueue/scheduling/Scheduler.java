package com.birbit.android.jobqueue.scheduling;

import android.content.Context;

/**
 * This class handles communication & tracking with a scheduler that can wake up the app / job
 * manager based on external events.
 * <p>
 * JobManager will call attached scheduler every time it thinks that the app should be waken up for
 * the given job. Each request comes with a {@link SchedulerConstraint} which should be reported
 * back to the JobManager when the system service wakes it up.
 */
abstract public class Scheduler {
    private Callback callback;
    private Context context;

    protected Scheduler() {

    }

    public void init(Context context, Callback callback) {
        this.context = context.getApplicationContext();
        this.callback = callback;
    }

    public Context getApplicationContext() {
        return context;
    }

    abstract public void request(SchedulerConstraint constraint);

    /**
     * Triggers the JobManager to handle the given constraint. JobManager always call
     * {@link #onFinished(SchedulerConstraint, boolean)} with an async callback.
     *
     * @param constraint The constraint
     */
    public final boolean start(SchedulerConstraint constraint) {
        if (callback == null) {
            throw new IllegalStateException("JobManager callback is not configured");
        }
        return callback.start(constraint);
    }

    public final boolean stop(SchedulerConstraint constraint) {
        if (callback == null) {
            throw new IllegalStateException("JobManager callback is not configured");
        }
        return callback.stop(constraint);
    }

    /**
     * Called by the JobManager when a scheduled constraint is handled.
     *
     * @param constraint The original constraint
     * @param reschedule True if the job should be rescheduled
     */
    abstract public void onFinished(SchedulerConstraint constraint, boolean reschedule);

    /**
     * When called, should cancel all pending jobs
     */
    public abstract void cancelAll();

    /**
     * Internal class that handles the communication between the JobManager and the scheduler
     */
    public interface Callback {
        /**
         * @param constraint
         * @return True if no jobs to run, false otherwise
         */
        boolean start(SchedulerConstraint constraint);

        /**
         * @param constraint
         * @return True if job should be rescheduled, false otherwise
         */
        boolean stop(SchedulerConstraint constraint);
    }
}
