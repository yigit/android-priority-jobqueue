package com.path.android.jobqueue;

/**
 * Created when a job fails in onRun method.
 * <p>
 *
 */
public class RetryConstraint {
    public static final RetryConstraint RETRY = new RetryConstraint(true);
    public static final RetryConstraint CANCEL = new RetryConstraint(false);
    private boolean retry;
    private Long newDelayInMs;
    private Integer newPriority;

    public RetryConstraint(boolean retry) {
        this.retry = retry;
    }

    public static RetryConstraint simpleResult(boolean reRun) {
        return reRun ? RETRY : CANCEL;
    }

    public boolean shouldRetry() {
        return retry;
    }

    public void setRetry(boolean retry) {
        this.retry = retry;
    }

    public Long getNewDelayInMs() {
        return newDelayInMs;
    }

    public void setNewDelayInMs(Long newDelayInMs) {
        this.newDelayInMs = newDelayInMs;
    }

    public Integer getNewPriority() {
        return newPriority;
    }

    public void setNewPriority(Integer newPriority) {
        this.newPriority = newPriority;
    }

    public static RetryConstraint createExponentialBackoff(int runCount, long initialBackOffInMs) {
        RetryConstraint constraint = new RetryConstraint(true);
        constraint.setNewDelayInMs((long) Math.pow(initialBackOffInMs, runCount));
        return constraint;
    }
}
