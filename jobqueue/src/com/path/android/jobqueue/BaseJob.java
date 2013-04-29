package com.path.android.jobqueue;

import com.path.android.jobqueue.log.JqLog;

import java.io.Serializable;

abstract public class BaseJob implements Serializable {
    public static final int DEFAULT_RETRY_LIMIT = 20;
    private final boolean requiresNetwork;

    protected BaseJob(boolean requiresNetwork) {
        this.requiresNetwork = requiresNetwork;
    }

    /**
     * called when the job is added to disk and committed.
     * this means job will eventually run. this is a good time to update local database and dispatch events
     * Changes to this class will not be preserved if your job is persistent !!!
     * Also, if your app crashes right after adding the job, {@code onRun} might be called without an {@code onAdded} call
     */
    abstract public void onAdded();

    /**
     * The actual method that should to the work
     * It should finish w/o any exception. If it throws any exception, {@code shouldReRunOnThrowable} will be called to
     * decide either to dismiss the job or re-run it.
     * @throws Throwable
     */
    abstract public void onRun() throws Throwable;

    /**
     * defines if we should add this job to disk or non-persistent queue
     *
     * @return
     */
    abstract public boolean shouldPersist();

    /**
     * called when a job is cancelled.
     */
    abstract protected void onCancel();

    /**
     * if {@code onRun} method throws an exception, this method is called.
     * return true if you want to run your job again, return false if you want to dismiss it. If you return false,
     * onCancel will be called.
     */
    abstract protected boolean shouldReRunOnThrowable(Throwable throwable);

    /**
     * Runs the job and catches any exception
     * @param currentRunCount
     * @return
     */
    public final boolean safeRun(int currentRunCount) {
        if (JqLog.isDebugEnabled()) {
            JqLog.d("running job %s", this.getClass().getSimpleName());
        }
        boolean reRun = false;
        boolean failed = false;
        try {
            onRun();
        } catch (Throwable t) {
            failed = true;
            JqLog.e(t, "error while executing job");
            reRun = currentRunCount < getRetryLimit() && shouldReRunOnThrowable(t);
        } finally {
            if (reRun) {
                return false;
            } else if (failed) {
                try {
                    onCancel();
                } catch (Throwable ignored) {
                }
            }
        }
        return true;
    }

    public boolean requiresNetwork() {
        return requiresNetwork;
    }

    /**
     * By default, jobs will be retried {@code DEFAULT_RETRY_LIMIT}  times.
     * If job fails this many times, onCancel will be called w/o calling {@code shouldReRunOnThrowable}
     * @return
     */
    protected int getRetryLimit() {
        return DEFAULT_RETRY_LIMIT;
    }
}
