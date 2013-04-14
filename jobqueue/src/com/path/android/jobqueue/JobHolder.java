package com.path.android.jobqueue;

import java.util.Date;

public class JobHolder {
    protected Long id;
    protected Integer priority;
    protected Integer runCount;
    protected java.util.Date created;
    protected Long runningSessionId;
    transient BaseJob baseJob;

    public JobHolder(Long id, Integer priority, Integer runCount, BaseJob baseJob, java.util.Date created, Long runningSessionId) {
        this.id = id;
        this.priority = priority;
        this.runCount = runCount;
        this.created = created;
        this.baseJob = baseJob;
        this.runningSessionId = runningSessionId;
    }

    public final boolean safeRun(int currentRunCount) {
        BaseJob baseJob = getBaseJob();
        if(baseJob == null) {
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

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Integer getRunCount() {
        return runCount;
    }

    public void setRunCount(Integer runCount) {
        this.runCount = runCount;
    }

    public BaseJob getBaseJob() {
        return baseJob;
    }

    public void setBaseJob(BaseJob baseJob) {
        this.baseJob = baseJob;
    }

    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    public Long getRunningSessionId() {
        return runningSessionId;
    }

    public void setRunningSessionId(Long runningSessionId) {
        this.runningSessionId = runningSessionId;
    }
}
