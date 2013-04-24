package com.path.android.jobqueue;

/**
 * Container class to address Jobs inside job manager.
 */
public class JobHolder {
    protected Long id;
    protected int priority;
    protected int runCount;
    /**
     * When job is created, System.nanoTime() is assigned to {@code createdNs} value so that we know when job is created
     * in relation to others
     */
    protected long createdNs;
    protected long runningSessionId;
    transient BaseJob baseJob;

    /**
     * @param id               Unique ID for the job. Should be unique per queue
     * @param priority         Higher is better
     * @param runCount         Incremented each time job is fetched to run, initial value should be 0
     * @param baseJob          Actual job to run
     * @param createdNs        System.nanotim
     * @param runningSessionId
     */
    public JobHolder(Long id, int priority, int runCount, BaseJob baseJob, long createdNs, long runningSessionId) {
        this.id = id;
        this.priority = priority;
        this.runCount = runCount;
        this.createdNs = createdNs;
        this.baseJob = baseJob;
        this.runningSessionId = runningSessionId;
    }

    public JobHolder(int priority, BaseJob baseJob, long runningSessionId) {
        this(null, priority, 0, baseJob, System.nanoTime(), runningSessionId);
    }

    /**
     * runs the job w/o throwing any exceptions
     * @param currentRunCount
     * @return
     */
    public final boolean safeRun(int currentRunCount) {
        return baseJob.safeRun(currentRunCount);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public BaseJob getBaseJob() {
        return baseJob;
    }

    public void setBaseJob(BaseJob baseJob) {
        this.baseJob = baseJob;
    }
}
