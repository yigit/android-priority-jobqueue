package com.path.android.jobqueue;

import com.path.android.jobqueue.log.JqLog;

import java.util.Date;

public class JobHolder {
    protected Long id;
    protected int priority;
    protected int runCount;
    protected long createdNs;
    protected long runningSessionId;
    transient BaseJob baseJob;

    public JobHolder(Long id, int priority, int runCount, BaseJob baseJob, long createdNs, long runningSessionId) {
        this.id = id;
        this.priority = priority;
        this.runCount = runCount;
        this.createdNs = createdNs;
        this.baseJob = baseJob;
        this.runningSessionId = runningSessionId;
    }

    public final boolean safeRun(int currentRunCount) {
        if(baseJob == null) {
            JqLog.e("base job is null, skipping job on run");
            return true;
        }
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
