package com.birbit.android.jobqueue;

import androidx.annotation.Nullable;

import com.birbit.android.jobqueue.network.NetworkUtil;

import java.util.Collections;
import java.util.HashSet;

/**
 * Job builder object to have a more readable design.
 * Methods can be chained to have more readable code.
 */
public class Params {
    /**
     * Used in delay / override deadline.
     */
    public static final long FOREVER = Long.MAX_VALUE;

    /**
     * Used in delay / override deadline.
     */
    public static final long NEVER = Long.MIN_VALUE;

    @NetworkUtil.NetworkStatus
    /* package */int requiredNetworkType = NetworkUtil.DISCONNECTED;
    private String groupId = null;
    private String singleId = null;
    private boolean persistent = false;
    private int priority;
    private long delayMs;
    private HashSet<String> tags;
    private long deadlineMs = 0;
    private Boolean cancelOnDeadline; // this also serve as a field set check

    /**
     *
     * @param priority higher = better
     */
    public Params(int priority) {
        this.priority = priority;
    }

    /**
     * Sets the Job as requiring network.
     * <p>
     * This method has no effect if you've already called {@link #requireUnmeteredNetwork()}.
     * @return this
     */
    public Params requireNetwork() {
        if (requiredNetworkType != NetworkUtil.UNMETERED) {
            requiredNetworkType = NetworkUtil.METERED;
        }
        return this;
    }

    /**
     * Sets the Job as requiring UNMETERED network.
     * @return this
     */
    public Params requireUnmeteredNetwork() {
        requiredNetworkType = NetworkUtil.UNMETERED;
        return this;
    }

    /**
     * Sets the group id. Jobs in the same group are guaranteed to execute sequentially.
     * @param groupId which group this job belongs (can be null of course)
     * @return this
     */
    public Params groupBy(String groupId) {
        this.groupId = groupId;
        return this;
    }

    /**
     * Sets the single instance id. If there is another Job with the same single id queued and
     * not yet running, this Job will get {@link Job#onCancel(int, Throwable)} called immediately after
     * {@link Job#onAdded()} and only the previous Job will run. That is, {@link Job#onRun()}
     * will only be called once.
     * <p>If no group id was set, one will be set automatically.
     * @param singleId which single instance group this job belongs to (can be null of course)
     * @return this
     */
    public Params singleInstanceBy(String singleId) {
        this.singleId = singleId;
        return this;
    }

    /**
     * Marks the job as persistent. Make sure your job is serializable.
     * @return this
     */
    public Params persist() {
        this.persistent = true;
        return this;
    }

    /**
     * Delays the job in given ms.
     * @param delayMs .
     * @return this
     */
    public Params delayInMs(long delayMs) {
        this.delayMs = delayMs;
        return this;
    }

    /**
     * Convenience method to set network requirement.
     * <p>
     * If you call this method with <code>true</code> and you've already called
     * {@link #requireUnmeteredNetwork()}, this method has no effect.
     *
     * @param requiresNetwork true|false
     * @return this
     * @see #requireNetwork()
     */
    public Params setRequiresNetwork(boolean requiresNetwork) {
        if (requiresNetwork) {
            if (requiredNetworkType == NetworkUtil.DISCONNECTED) {
                requiredNetworkType = NetworkUtil.METERED;
            }
        } else {
            this.requiredNetworkType = NetworkUtil.DISCONNECTED;
        }
        return this;
    }

    /**
     * Convenience method to set unmetered network requirement.
     * <p>
     * If you call this method with <code>false</code> and you've already called
     * {@link #requireNetwork()}, this method has no effect.
     *
     * @param requiresUnmeteredNetwork true|false
     * @return this
     * @see #requireUnmeteredNetwork()
     */
    public Params setRequiresUnmeteredNetwork(boolean requiresUnmeteredNetwork) {
        if (requiresUnmeteredNetwork) {
            this.requiredNetworkType = NetworkUtil.UNMETERED;
        } else if (this.requiredNetworkType != NetworkUtil.METERED){
            this.requiredNetworkType = NetworkUtil.DISCONNECTED;
        }
        return this;
    }

    /**
     * convenience method to set group id.
     * @param groupId The group id for the job
     * @return this
     */
    public Params setGroupId(String groupId) {
        this.groupId = groupId;
        return this;
    }

    /**
     * convenience method to set single id.
     * @param singleId The single instance run id for the job
     * @return this
     */
    public Params setSingleId(String singleId) {
        this.singleId = singleId;
        return this;
    }

    /**
     * convenience method to set whether {@link JobManager} should persist this job or not.
     * @param persistent true|false
     * @return this
     */
    public Params setPersistent(boolean persistent) {
        this.persistent = persistent;
        return this;
    }

    /**
     * convenience method to set delay
     * @param delayMs in ms
     * @return this
     */
    public Params setDelayMs(long delayMs) {
        this.delayMs = delayMs;
        return this;
    }

    /**
     * Attaches given tags to the Job.
     * These are initially used for cancelling or querying jobs but usage will be extended
     *
     * @param newTags List of tags to add
     * @return this
     */
    public Params addTags(String... newTags) {
        if(tags == null) {
            tags = new HashSet<>();
        }
        Collections.addAll(tags, newTags);
        return this;
    }

    /**
     * Removes the given tags from the Job.
     *
     * @param oldTags List of tags to be removed
     * @return this
     */
    @SuppressWarnings("unused")
    public Params removeTags(String... oldTags) {
        if(tags == null) {
            return this;
        }
        for(String tag : oldTags) {
            tags.remove(tag);
        }
        return this;
    }

    @SuppressWarnings("unused")
    public Params clearTags() {
        tags = null;
        return this;
    }

    /**
     * Set a deadline on the job's constraints. After this deadline is reached, the job is run
     * regardless of its constraints.
     * <p>
     * Note that even if a job reaches its deadline, JobManager still respects constraints like
     * groupId because when multiple jobs use the same groupId, they usually access shared resources
     * so it is important to respect groupId while running jobs in parallel.
     * <p>
     * You can check if a job reached its deadline or not via {@link Job#isDeadlineReached()}.
     * <p>
     * If you call this method, you cannot call {@link #overrideDeadlineToCancelInMs(long)}.
     *
     * @param deadlineInMs The deadline in milliseconds for the constraints.
     *
     * @return this
     *
     * @see #overrideDeadlineToCancelInMs(long)
     */
    public Params overrideDeadlineToRunInMs(long deadlineInMs) {
        if (Boolean.TRUE.equals(cancelOnDeadline)) {
            throw new IllegalArgumentException("cannot set deadline to cancel and run. You need" +
                    " to pick one");
        }
        deadlineMs = deadlineInMs;
        cancelOnDeadline = false;
        return this;
    }

    /**
     * Set a deadline on the job's constraints. After this deadline is reached, the job will be
     * cancelled regardless of its constraints with {@link CancelReason#REACHED_DEADLINE}.
     * <p>
     * For instance, if you have a job that requires network and if network becomes available at the
     * time deadline is reached, the job will still be cancelled without being run.
     * <p>
     * You can check if a job reached its deadline or not via {@link Job#isDeadlineReached()}.
     * <p>
     * Note that even if a job reaches its deadline, JobManager still respects constraints like
     * groupId because when multiple jobs use the same groupId, they usually access shared resources
     * so it is important to respect groupId while running jobs in parallel.
     * <p>
     * If you call this method, you cannot call {@link #overrideDeadlineToRunInMs(long)}.
     *
     * @param deadlineInMs The deadline in milliseconds for the constraints.
     *
     * @return this
     *
     * @see #overrideDeadlineToRunInMs(long)
     */
    public Params overrideDeadlineToCancelInMs(long deadlineInMs) {
        if (Boolean.FALSE.equals(cancelOnDeadline)) {
            throw new IllegalArgumentException("cannot set deadline to cancel and run. You need" +
                    " to pick one");
        }
        deadlineMs = deadlineInMs;
        cancelOnDeadline = true;
        return this;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getSingleId() {
        return singleId;
    }

    public boolean isPersistent() {
        return persistent;
    }

    public int getPriority() {
        return priority;
    }

    public long getDelayMs() {
        return delayMs;
    }

    public long getDeadlineMs() {
        return deadlineMs;
    }

    /**
     * Returns what JobManager will do if job reaches its deadline.
     * <p>
     *
     * It will be null if Job does not have a deadline.
     *
     * @return null if job does not have a deadline, true if it will be cancelled when it hits the
     * deadline, false if it will be run when it hits the deadline.
     */
    @SuppressWarnings("unused")
    @Nullable
    public Boolean getCancelOnDeadline() {
        return cancelOnDeadline;
    }

    public HashSet<String> getTags() {
        return tags;
    }

    public boolean shouldCancelOnDeadline() {
        return Boolean.TRUE.equals(cancelOnDeadline);
    }

    public boolean isNetworkRequired() {
        return requiredNetworkType >= NetworkUtil.METERED;
    }

    public boolean isUnmeteredNetworkRequired() {
        return requiredNetworkType >= NetworkUtil.UNMETERED;
    }
}
