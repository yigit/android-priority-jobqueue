package com.path.android.jobqueue;

/**
 * BaseJob builder object to have a more readable design.
 * Methods can be chained to have more readable code.
 */
public class Params {
    private boolean requiresNetwork = false;
    private String groupId = null;
    private boolean persistent = false;
    private int priority;
    private long delayMs;

    /**
     *
     * @param priority higher = better
     */
    public Params(int priority) {
        this.priority = priority;
    }

    /**
     * Sets the Job as requiring network
     * @return this
     */
    public Params requireNetwork() {
        requiresNetwork = true;
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
     * convenience method to set network requirement
     * @param requiresNetwork true|false
     * @return this
     */
    public Params setRequiresNetwork(boolean requiresNetwork) {
        this.requiresNetwork = requiresNetwork;
        return this;
    }

    /**
     * convenience method to set group id.
     * @param groupId
     * @return this
     */
    public Params setGroupId(String groupId) {
        this.groupId = groupId;
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

    public boolean doesRequireNetwork() {
        return requiresNetwork;
    }

    public String getGroupId() {
        return groupId;
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
}
