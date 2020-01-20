package com.birbit.android.jobqueue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Set;

/**
 * Interface that any JobQueue should implement
 * These job queues can be given to JobManager.
 */
public interface JobQueue {
    /**
     * Inserts the given JobHolder.
     *
     * @param jobHolder The JobHolder to be inserted
     *
     * @return True if job is added, false otherwise
     */
    boolean insert(@NonNull JobHolder jobHolder);

    /**
     * Does the same thing with insert but the only difference is that
     * if job has an insertion ID, it should replace the existing one
     *  should also reset running session id to {@link JobManager#NOT_RUNNING_SESSION_ID}
     *  Is called when a job is re-added (due to exception during run)
     *
     * @param jobHolder The JobHolder to be added
     * @return True if job is added, false otherwise
     */
    boolean insertOrReplace(@NonNull JobHolder jobHolder);

    /**
     * Remove the old job from the queue while inserting the new one.
     *
     * @param newJob To be inserted
     * @param oldJob To be removed
     */
    void substitute(@NonNull JobHolder newJob, @NonNull JobHolder oldJob);

    /**
     * Removes the job from the data store.
     * Is called after a job is completed (or cancelled)
     *
     * @param jobHolder The JobHolder to be removed
     */
    void remove(@NonNull JobHolder jobHolder);

    /**
     * Returns the # of jobs that are waiting to be run
     * @return The number of jobs that are waiting in the queue
     */
    int count();

    /**
     * counts the # of jobs that can run now. if there are more jobs from the same group,
     * they are count as 1 since they cannot be run in parallel.
     * <p>
     * Exclude groups are guaranteed to be ordered in natural order.
     *
     * @param constraint The constraint to match the jobs
     *
     * @return The number of jobs that are ready to run
     */
    int countReadyJobs(@NonNull Constraint constraint);

    /**
     * Returns the next available job in the data set
     * It should also assign the sessionId as the RunningSessionId and persist that data if necessary.
     * It should filter out all running jobs and exclude groups are guaranteed to be ordered in natural order
     *
     * @param constraint The constraint to match the job.
     * @return The next job to be run that matches the constraint or null if there is no such job
     */
    @Nullable
    JobHolder nextJobAndIncRunCount(@NonNull Constraint constraint);

    /**
     * Returns when the next job should run (in nanoseconds), should return null if there are no
     * jobs to run.
     * <p>
     * This method should check both delayed jobs and jobs that require network with a timeout.
     * @param constraint The constraint to match the job.
     * @return The time until next job is ready or null if there aren't any jobs matching the
     * constraint
     */
    @Nullable
    Long getNextJobDelayUntilNs(@NonNull Constraint constraint);

    /**
     * clear all jobs in the queue. should probably be called when user logs out.
     */
    void clear();

    /**
     * returns the job with the given id if it exists in the queue
     * @param id id of the job
     *
     * @return JobHolder with the given id or null if it does not exists
     */
    @Nullable
    JobHolder findJobById(@NonNull String id);

    /**
     * Returns jobs that matches the given constraints
     *
     * @param constraint The constraint to match the job.
     * @return The set of jobs that matches the given constraint
     */
    @NonNull
    Set<JobHolder> findJobs(@NonNull Constraint constraint);

    /**
     * Called when a job is cancelled by the user.
     * <p>
     * It is important to not return this job from queries anymore.
     *
     * @param holder The JobHolder that is being cancelled
     */
    void onJobCancelled(JobHolder holder);
}
