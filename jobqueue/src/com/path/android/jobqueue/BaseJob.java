package com.path.android.jobqueue;

import com.path.android.jobqueue.log.JqLog;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * This class has been deprecated and will soon be removed from public api.
 * Please use {@link Job} instead which provider a cleaner constructor API.
 * Deprecated. Use {@link Job}
 */
@Deprecated
abstract public class BaseJob implements Serializable {
    public static final int DEFAULT_RETRY_LIMIT = 20;
    private boolean requiresNetwork;
    private String groupId;
    private boolean persistent;
    private transient int currentRunCount;

    protected BaseJob(boolean requiresNetwork) {
        this(requiresNetwork, false, null);
    }

    protected BaseJob(String groupId) {
        this(false, false, groupId);
    }

    protected BaseJob(boolean requiresNetwork, String groupId) {
        this(requiresNetwork, false, groupId);
    }

    public BaseJob(boolean requiresNetwork, boolean persistent) {
        this(requiresNetwork, persistent, null);
    }

    protected BaseJob(boolean requiresNetwork, boolean persistent, String groupId) {
        this.requiresNetwork = requiresNetwork;
        this.persistent = persistent;
        this.groupId = groupId;
    }

    private void writeObject(ObjectOutputStream oos) throws IOException {
        oos.writeBoolean(requiresNetwork);
        oos.writeObject(groupId);
        oos.writeBoolean(persistent);
    }


    private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
        requiresNetwork = ois.readBoolean();
        groupId = (String) ois.readObject();
        persistent = ois.readBoolean();
    }

    /**
     * defines if we should add this job to disk or non-persistent queue
     *
     * @return
     */
    public final boolean isPersistent() {
        return persistent;
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
        this.currentRunCount = currentRunCount;
        if (JqLog.isDebugEnabled()) {
            JqLog.d("running job %s", this.getClass().getSimpleName());
        }
        boolean reRun = false;
        boolean failed = false;
        try {
            onRun();
            if (JqLog.isDebugEnabled()) {
                JqLog.d("finished job %s", this.getClass().getSimpleName());
            }
        } catch (Throwable t) {
            failed = true;
            JqLog.e(t, "error while executing job");
            reRun = currentRunCount < getRetryLimit();
            if(reRun) {
                try {
                    reRun = shouldReRunOnThrowable(t);
                } catch (Throwable t2) {
                    JqLog.e(t2, "shouldReRunOnThrowable did throw an exception");
                }
            }
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

    /**
     * before each run, JobManager sets this number. Might be useful for the {@link com.path.android.jobqueue.BaseJob#onRun()}
     * method
     * @return
     */
    protected int getCurrentRunCount() {
        return currentRunCount;
    }

    /**
     * if job is set to require network, it will not be called unless {@link com.path.android.jobqueue.network.NetworkUtil}
     * reports that there is a network connection
     * @return
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
     * If job fails this many times, onCancel will be called w/o calling {@code shouldReRunOnThrowable}
     * @return
     */
    protected int getRetryLimit() {
        return DEFAULT_RETRY_LIMIT;
    }
}
