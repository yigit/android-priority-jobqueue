package com.path.android.jobqueue;

/**
 * Identifies the current status of a job if it is in the queue
 */
public enum JobStatus {
    /**
     * Job is in the queue but requires network connection to be run and there is no network available right now
     */
    WAITING_FOR_NETWORK,
    /**
     * Job is in the queue, ready to be run. Waiting for an available consumer
     */
    WAITING,
    /**
     * Job is executed by one of the runners
     */
    RUNNING,
    /**
     * Job is not know by job queue.
     * <p>This can be:
     * <ul>
     *     <li>Invalid ID</li>
     *     <li>Job has been completed</li>
     *     <li>Job has failed</li>
     *     <li>Job has just been added, about to be delivered into a queue</li>
     * </ul>
     * </p>
     */
    DOES_NOT_EXISTS
}
