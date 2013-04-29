package com.path.android.jobqueue;

/**
 * Interface that any JobQueue should implement
 * These job queues can be given to JobManager.
 */
public interface JobQueue {
    /**
     * Inserts the given JobHolder,
     *   assigns it a unique id
     *   and returns the id back
     *   Is called when a job is added
     * @param jobHolder
     * @return
     */
    long insert(JobHolder jobHolder);

    /**
     * Does the same thing with insert but the only difference is that
     * if job has an ID, it should replace the existing one
     *  should also reset running session id to {@link JobManager#NOT_RUNNING_SESSION_ID}
     *  Is called when a job is re-added (due to exception during run)
     * @param jobHolder
     * @return
     */
    long insertOrReplace(JobHolder jobHolder);

    /**
     * Removes the job from the data store.
     * Is called after a job is completed (or cancelled)
     * @param jobHolder
     */
    void remove(JobHolder jobHolder);

    /**
     * Returns the # of jobs that are waiting to be run
     * @return
     */
    int count();

    /**
     * Returns the next available job in the data set
     * It should also assign the sessionId as the RunningSessionId and persist that data if necessary.
     * It should filter out all running jobs and
     * @return
     */
    JobHolder nextJobAndIncRunCount(boolean hasNetwork);

    /**
     * returns when the next job should run, should return null if there are no jobs to run.
     * @return
     */
    Long getNextJobDelayUntilNs();
}
