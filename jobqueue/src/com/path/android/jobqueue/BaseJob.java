package com.path.android.jobqueue;

import com.path.android.jobqueue.log.JqLog;

import java.io.Serializable;

abstract public class BaseJob implements Serializable {
    /**
     * called when the job is added to disk and committed.
     * this means job will eventually run. this is a good time to update local database and dispatch events
     */
    abstract public void onAdded();

    /**
     * Time to do whatever your job needs to do
     * @throws Throwable
     */
    abstract public void onRun() throws Throwable;

    // called if this job should be persistent in case of an app crash / close
    // not to add extra load of persisting jobs in db if they just
    // wrappers for simple async tasks (e.g. loading feed, friend lists etc)

    /**
     * defines if we should add this job to disk or non-persistent queue
     * @return
     */
    abstract public boolean shouldPersist();

    /**
     * called when a job is cancelled.
     */
    abstract protected void onCancel();

    /**
     * if {onRun} method throws an exception, this method is called.
     * return true if you wan't to run your job again, return false if you want to dismiss it.
     *
     */
    abstract protected boolean shouldReRunOnThrowable(Throwable throwable);

    public final boolean safeRun(int currentRunCount) {
        if(JqLog.isDebugEnabled()) {
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
            if(reRun) {
                return false;
            } else if(failed) {
                try {
                    onCancel();
                } catch (Throwable ignored){}
            }
        }
        return true;
    }

    protected int getRetryLimit() {
        return 20;
    }
}
