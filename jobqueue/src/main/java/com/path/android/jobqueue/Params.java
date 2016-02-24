package com.path.android.jobqueue;

import java.util.HashSet;

/**
 * Job builder object to have a more readable design.
 * Methods can be chained to have more readable code.
 */
public class Params {
    private boolean requiresNetwork = false;
    private String groupId = null;
    private String singleId = null;
    private boolean persistent = false;
    private int priority;
    private long delayMs;
    private HashSet<String> tags;

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
     * Sets the single id. Jobs with the same single id will always get {@link Job#onAdded()}
     * called, but {@link Job#onRun()} will only run once if there are other non-running
     * Jobs queued. You will probably also want to use {@link #groupBy(String)}.
     * @param singleId which group this job belongs (can be null of course)
     * @return this
     */
    public Params singleWith(String singleId) {
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
     * convenience method to set single id.
     * @param singleId
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
     * @param newTags List of tags to add
     * @return
     */
    public Params addTags(String... newTags) {
        if(tags == null) {
            tags = new HashSet<String>();
        }
        for(String tag : newTags) {
            tags.add(tag);
        }
        return this;
    }

    /**
     * Removes the given tags from the Job.
     *
     * @param oldTags List of tags to be removed
     * @return
     */
    public Params removeTags(String... oldTags) {
        if(tags == null) {
            return this;
        }
        for(String tag : oldTags) {
            tags.remove(tag);
        }
        return this;
    }

    public Params clearTags() {
        tags = null;
        return this;
    }

    public boolean doesRequireNetwork() {
        return requiresNetwork;
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

    public HashSet<String> getTags() {
        return tags;
    }
}
