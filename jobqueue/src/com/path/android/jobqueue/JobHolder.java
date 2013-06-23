package com.path.android.jobqueue;

/**
 * Container class to address Jobs inside job manager.
 */
public class JobHolder {
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
    transient BaseJob baseJob;

    /**
     * @param id               Unique ID for the job. Should be unique per queue
     * @param priority         Higher is better
     * @param groupId          which group does this job belong to? default null
     * @param runCount         Incremented each time job is fetched to run, initial value should be 0
     * @param baseJob          Actual job to run
     * @param createdNs        System.nanotime
     * @param delayUntilNs     System.nanotime value where job can be run the very first time
     * @param runningSessionId
     */
    public JobHolder(Long id, int priority, String groupId, int runCount, BaseJob baseJob, long createdNs, long delayUntilNs, long runningSessionId) {
        this.id = id;
        this.priority = priority;
        this.groupId = groupId;
        this.runCount = runCount;
        this.createdNs = createdNs;
        this.delayUntilNs = delayUntilNs;
        this.baseJob = baseJob;
        this.runningSessionId = runningSessionId;
        this.requiresNetwork = baseJob.requiresNetwork();
    }

    public JobHolder(int priority, BaseJob baseJob, long runningSessionId) {
        this(null, priority, null, 0, baseJob, System.nanoTime(), Long.MIN_VALUE, runningSessionId);
    }

    public JobHolder(int priority, BaseJob baseJob, long delayUntilNs, long runningSessionId) {
        this(null, priority, baseJob.getRunGroupId(), 0, baseJob, System.nanoTime(), delayUntilNs, runningSessionId);
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

    public BaseJob getBaseJob() {
        return baseJob;
    }

    public void setBaseJob(BaseJob baseJob) {
        this.baseJob = baseJob;
    }

    public String getGroupId() {
        return groupId;
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
}
