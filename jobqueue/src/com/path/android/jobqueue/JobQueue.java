package com.path.android.jobqueue;

import java.util.Collection;

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
     * counts the # of jobs that can run now. if there are more jobs from the same group, they are count as 1 since
     * they cannot be run in parallel
     * exclude groups are guaranteed to be ordered in natural order
     * @return
     */
    int countReadyJobs(boolean hasNetwork, Collection<String> excludeGroups);

    /**
     * Returns the next available job in the data set
     * It should also assign the sessionId as the RunningSessionId and persist that data if necessary.
     * It should filter out all running jobs and
     * exclude groups are guaranteed to be ordered in natural order
     * @param hasNetwork if true, should return any job, if false, should return jobs that do NOT require network
     * @param excludeGroups if provided, jobs from these groups will NOT be returned
     * @return
     */
    JobHolder nextJobAndIncRunCount(boolean hasNetwork, Collection<String> excludeGroups);

    /**
     * returns when the next job should run (in nanoseconds), should return null if there are no jobs to run.
     * @param hasNetwork if true, should return nanoseconds for any job, if false, should return nanoseconds for next
     *                   job's delay until.
     * @return
     */
    Long getNextJobDelayUntilNs(boolean hasNetwork);

    /**
     * clear all jobs in the queue. should probably be called when user logs out.
     */
    void clear();

    /**
     * returns the job with the given id if it exists in the queue
     * @param id id of the job, returned by insert method
     * @return JobHolder with the given id or null if it does not exists
     */
    JobHolder findJobById(long id);

}
