package com.path.android.jobqueue;

import com.path.android.jobqueue.log.JqLog;

import java.util.Collections;
import java.util.Set;

/**
 * Container class to address Jobs inside job manager.
 */
public class JobHolder {

    /**
     * Internal constant. Job's onRun method completed w/o any exception.
     */
    public static final int RUN_RESULT_SUCCESS = 1;
    /**
     * Internal constant. Job's onRun method thrown an exception and either it does not want to
     * run again or reached retry limit.
     */
    public static final int RUN_RESULT_FAIL_RUN_LIMIT = 2;

    /**
     * Internal constant. Job's onRun method has thrown an exception and it was cancelled after it
     * started.
     */
    public static final int RUN_RESULT_FAIL_FOR_CANCEL = 3;
    /**
     * Internal constant. Job's onRun method failed but wants to retry.
     */
    public static final int RUN_RESULT_TRY_AGAIN = 4;

    protected Long id;
    protected int priority;
    protected String groupId;
    protected int runCount;
    /**
     * job will be delayed until this nanotime
     */
    protected long delayUntilNs;
    /**
     * When job is created, System.nanoTime() is assigned to {@code createdNs} value so that we know when job is created
     * in relation to others
     */
    protected long createdNs;
    protected long runningSessionId;
    protected boolean requiresNetwork;
    transient Job job;
    protected final Set<String> tags;
    private boolean cancelled;
    private boolean successful;

    /**
     * @param id               Unique ID for the job. Should be unique per queue
     * @param priority         Higher is better
     * @param groupId          which group does this job belong to? default null
     * @param runCount         Incremented each time job is fetched to run, initial value should be 0
     * @param job              Actual job to run
     * @param createdNs        System.nanotime
     * @param delayUntilNs     System.nanotime value where job can be run the very first time
     * @param runningSessionId
     */
    public JobHolder(Long id, int priority, String groupId, int runCount, Job job, long createdNs, long delayUntilNs, long runningSessionId) {
        this.id = id;
        this.priority = priority;
        this.groupId = groupId;
        this.runCount = runCount;
        this.createdNs = createdNs;
        this.delayUntilNs = delayUntilNs;
        this.job = job;
        this.runningSessionId = runningSessionId;
        this.requiresNetwork = job.requiresNetwork();
        this.tags = job.getTags() == null ? null : Collections.unmodifiableSet(job.getTags());
    }

    public JobHolder(int priority, Job job, long runningSessionId) {
        this(null, priority, null, 0, job, System.nanoTime(), Long.MIN_VALUE, runningSessionId);
    }

    public JobHolder(int priority, Job job, long delayUntilNs, long runningSessionId) {
        this(null, priority, job.getRunGroupId(), 0, job, System.nanoTime(), delayUntilNs, runningSessionId);
    }

    /**
     * runs the job w/o throwing any exceptions
     * @param currentRunCount
     * @return RUN_RESULT*
     */
    public final int safeRun(int currentRunCount) {
        return job.safeRun(this, currentRunCount);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public boolean requiresNetwork() {
        return requiresNetwork;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public int getRunCount() {
        return runCount;
    }

    public void setRunCount(int runCount) {
        this.runCount = runCount;
    }

    public long getCreatedNs() {
        return createdNs;
    }

    public void setCreatedNs(long createdNs) {
        this.createdNs = createdNs;
    }

    public long getRunningSessionId() {
        return runningSessionId;
    }

    public void setRunningSessionId(long runningSessionId) {
        this.runningSessionId = runningSessionId;
    }

    public long getDelayUntilNs() {
        return delayUntilNs;
    }

    public Job getJob() {
        return job;
    }

    public void setJob(Job job) {
        this.job = job;
    }

    public String getGroupId() {
        return groupId;
    }

    public Set<String> getTags() {
        return tags;
    }

    public void markAsCancelled() {
        cancelled = true;
        job.cancelled = true;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public int hashCode() {
        //we don't really care about overflow.
        if(id == null) {
            return super.hashCode();
        }
        return id.intValue();
    }

    @Override
    public boolean equals(Object o) {
        if(o instanceof JobHolder == false) {
            return false;
        }
        JobHolder other = (JobHolder) o;
        if(id == null || other.id == null) {
            return false;
        }
        return id.equals(other.id);
    }

    public boolean hasTags() {
        return tags != null && tags.size() > 0;
    }

    public synchronized void markAsSuccessful() {
        successful = true;
    }

    public synchronized boolean isSuccessful() {
        return successful;
    }
}
