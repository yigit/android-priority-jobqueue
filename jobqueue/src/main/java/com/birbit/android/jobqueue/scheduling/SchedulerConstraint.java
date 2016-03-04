package com.birbit.android.jobqueue.scheduling;

/**
 * The constraints that are passed into Scheduler from JobManager
 */
public class SchedulerConstraint {
    private long delayInMs;
    private boolean requireUnmeteredNetwork;
    private boolean requireNetwork;
    // arbitrary data that can be used by the scheduler
    private Object data;

    public SchedulerConstraint() {
    }

    public long getDelayInMs() {
        return delayInMs;
    }

    public void setDelayInMs(long delayInMs) {
        this.delayInMs = delayInMs;
    }

    public boolean requireUnmeteredNetwork() {
        return requireUnmeteredNetwork;
    }

    public void setRequireUnmeteredNetwork(boolean requireUnmeteredNetwork) {
        this.requireUnmeteredNetwork = requireUnmeteredNetwork;
    }

    public boolean requireNetwork() {
        return requireNetwork;
    }

    public void setRequireNetwork(boolean requireNetwork) {
        this.requireNetwork = requireNetwork;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "SchedulerConstraint{" +
                "delayInMs=" + delayInMs +
                ", requireUnmeteredNetwork=" + requireUnmeteredNetwork +
                ", requireNetwork=" + requireNetwork +
                ", data=" + data +
                '}';
    }
}
