package com.birbit.android.jobqueue;

/**
 * Created by {@link Job#shouldReRunOnThrowable(Throwable, int, int)}.
 * <p>
 * This object keeps additional data about handling job failures. You can simply use
 * {@link #RETRY} or {@link #CANCEL} if you just want to retry or cancel a job. Alternatively,
 * you can create your own instance where you can add a delay {@link #setNewDelayInMs(Long)} or
 * change Job's prioritiy {@link #setNewPriority(Integer)}.
 * <p>
 * A common use case is exponentially backing off a Job and you can use
 * {@link #createExponentialBackoff(int, long)} method to do that.
 */
public class RetryConstraint {
    public static final RetryConstraint RETRY = new ImmutableRetryConstraint(true);
    public static final RetryConstraint CANCEL = new ImmutableRetryConstraint(false);
    private boolean retry;
    private Long newDelayInMs;
    private Integer newPriority;
    private boolean applyNewDelayToGroup = false;

    public RetryConstraint(boolean retry) {
        this.retry = retry;
    }

    public boolean shouldRetry() {
        return retry;
    }

    /**
     * Set whether the Job should be run again or cancelled.
     * @param retry
     */
    public void setRetry(boolean retry) {
        this.retry = retry;
    }

    public Long getNewDelayInMs() {
        return newDelayInMs;
    }

    /**
     * Sets a timeout until the Job is tried again.
     * @param newDelayInMs
     */
    public void setNewDelayInMs(Long newDelayInMs) {
        this.newDelayInMs = newDelayInMs;
    }

    public Integer getNewPriority() {
        return newPriority;
    }

    /**
     * Updates the Job's prioritiy.
     * @param newPriority
     */
    public void setNewPriority(Integer newPriority) {
        this.newPriority = newPriority;
    }

    /**
     * Creates a response that will exponentially back off the job.
     *
     * @param runCount The run count that was passed to
     * {@link Job#shouldReRunOnThrowable(Throwable, int, int)}
     * @param initialBackOffInMs The initial back off time. This will be the back off for the inital
     *                           run and then it will exponentially grow from this number.
     *
     * @return A RetryContraint that will report exponential back off.
     */
    public static RetryConstraint createExponentialBackoff(int runCount, long initialBackOffInMs) {
        RetryConstraint constraint = new RetryConstraint(true);
        constraint.setNewDelayInMs(initialBackOffInMs *
                (long) Math.pow(2, Math.max(0, runCount - 1)));
        return constraint;
    }

    /**
     * Sets whether the delay in the constraint should be applied to the whole group.
     * <p>
     * Note that the delay will effect any Job that is added after this call until the delay ends.
     * This will ensure that the Job execution order will be preserved.
     * <p>
     * If the job does not have a group id ({@link Job#getRunGroupId()}, calling this method has no
     * effect.
     * <p>
     * The group delay is global so even after you cancel the jobs, it will still affect the group
     * until delay times out.
     *
     * @param applyDelayToGroup Sets whether the delay should be applied to all jobs in this group.
     *
     */
    public void setApplyNewDelayToGroup(boolean applyDelayToGroup) {
        this.applyNewDelayToGroup = applyDelayToGroup;
    }

    /**
     * Returns whether the delay in this retry constraint will be applied to all jobs in this group.
     *
     * @return Whether the delay will be applied to all jobs in this group or not. Defaults to
     * false.
     */
    public boolean willApplyNewDelayToGroup() {
        return applyNewDelayToGroup;
    }

    private static class ImmutableRetryConstraint extends RetryConstraint {
        private static final String MESSAGE = "This object is immutable. Create a new one using the"
                + " constructor.";
        public ImmutableRetryConstraint(boolean retry) {
            super(retry);
        }

        @Override
        public void setRetry(boolean retry) {
            throw new IllegalStateException(MESSAGE);
        }

        @Override
        public void setNewDelayInMs(Long newDelayInMs) {
            throw new IllegalStateException(MESSAGE);
        }

        @Override
        public void setNewPriority(Integer newPriority) {
            throw new IllegalStateException(MESSAGE);
        }
    }
}
