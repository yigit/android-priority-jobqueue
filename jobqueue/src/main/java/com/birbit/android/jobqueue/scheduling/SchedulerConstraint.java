package com.birbit.android.jobqueue.scheduling;

import androidx.annotation.Nullable;

import com.birbit.android.jobqueue.network.NetworkUtil;

/**
 * The constraints that are passed into Scheduler from JobManager
 */
public class SchedulerConstraint {
    private String uuid;
    private long delayInMs;
    private int networkStatus;
    private Long overrideDeadlineInMs;
    // arbitrary data that can be used by the scheduler
    private Object data;

    public SchedulerConstraint(String uuid) {
        this.uuid = uuid;
    }

    /**
     * The unique id assigned by the job manager. This is different from the ID that is assigned
     * by the third party scheduler.
     * @return The unique id assigned by the job manager
     */
    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    /**
     * The delay for the job
     * @return The delay before running the job
     */
    public long getDelayInMs() {
        return delayInMs;
    }

    public void setDelayInMs(long delayInMs) {
        this.delayInMs = delayInMs;
    }

    /**
     *
     * @return The network status required to run the job.
     */
    @NetworkUtil.NetworkStatus
    public int getNetworkStatus() {
        return networkStatus;
    }

    public void setNetworkStatus(int networkStatus) {
        this.networkStatus = networkStatus;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    /**
     * The deadline in ms after which the job should be run even if the constraints are not match.
     * <p>
     * If there is no deadline, this will be null.
     *
     * @return The time in ms until the constraints timeout
     */
    @Nullable
    public Long getOverrideDeadlineInMs() {
        return overrideDeadlineInMs;
    }

    public void setOverrideDeadlineInMs(Long overrideDeadlineInMs) {
        this.overrideDeadlineInMs = overrideDeadlineInMs;
    }

    @Override
    public String toString() {
        return "SchedulerConstraint{" +
                "uuid='" + uuid + '\'' +
                ", delayInMs=" + delayInMs +
                ", networkStatus=" + networkStatus +
                ", overrideDeadlineInMs=" + overrideDeadlineInMs +
                ", data=" + data +
                '}';
    }
}
