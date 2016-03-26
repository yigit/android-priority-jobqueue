package com.birbit.android.jobqueue;

import java.util.Collections;
import java.util.HashSet;

/**
 * Job builder object to have a more readable design.
 * Methods can be chained to have more readable code.
 */
public class Params {

    /**
     * Used in requireNetwork / requireUnmeteredNetwork configuration if the requirement should be
     * kept forever.
     */
    public static final long FOREVER = Long.MAX_VALUE;

    /**
     * Used in requireNetwork / requireUnmeteredNetwork configuration if the constraint is disabled.
     */
    public static final long NEVER = Long.MIN_VALUE;

    private long requiresNetworkWithTimeout = NEVER;
    private long requiresUnmeteredNetworkWithTimeout = NEVER;
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
     * Sets the Job as requiring network.
     * @return this
     */
    public Params requireNetwork() {
        return requireNetworkWithTimeout(FOREVER);
    }

    /**
     * Sets the Job as requiring UNMETERED network.
     * @return this
     */
    public Params requireUnmeteredNetwork() {
        return requireUnmeteredNetworkWithTimeout(FOREVER);
    }

    /**
     * Sets the Job as requiring a unmetered network connection with the given timeoutMs. The Job
     * will not be run until a network connection is detected. If {@code timeoutMs} is not
     * {@link #FOREVER}, the Job will be available to run without an unmetered connection if it
     * cannot be run with unmetered network connection in the given time period.
     * <p>
     * If you want the job to require unmetered network for a limited time then fall back to metered
     * network, you can do so by
     * {@code requireUnmeteredNetworkWithTimeout(timeout).requireNetwork()}. You can even specify
     * a timeout for the unmetered connection as well if you wish the job to be run if enough time
     * passes and no desired network is available.
     *
     * @param timeoutMs The timeout in milliseconds after which the Job will be run even if there is
     *                no unmetered connection.
     *
     * @return The Params
     */
    public Params requireUnmeteredNetworkWithTimeout(long timeoutMs) {
        requiresUnmeteredNetworkWithTimeout = timeoutMs;
        return this;
    }

    /**
     * Sets the Job as requiring a network connection with the given timeoutMs. The Job will not be
     * run until a network connection is detected. If {@code timeoutMs} is not {@link #FOREVER}, the
     * Job will available to run without a network connection if it cannot be run in the given time
     * period.
     * <p>In case this timeout is set to a value smaller than
     * {@link #requireUnmeteredNetworkWithTimeout(long)}, the unmetered network timeout will
     * override this one.
     *
     * @param timeoutMs The timeout in milliseconds after which the Job will be run even if there is
     *                no network connection.
     *
     * @return The Params
     */
    public Params requireNetworkWithTimeout(long timeoutMs) {
        requiresNetworkWithTimeout = timeoutMs;
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
     * not yet running, this Job will get {@link Job#onCancel(int)} called immediately after
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
     * @param requiresNetwork true|false
     * @return this
     * @see #setRequiresNetwork(boolean, long)
     * @see #requireNetwork()
     */
    public Params setRequiresNetwork(boolean requiresNetwork) {
        return setRequiresNetwork(requiresNetwork, FOREVER);
    }

    /**
     * Convenience method to set unmetered network requirement.
     *
     * @param requiresUnmeteredNetwork true|false
     * @param timeout The timeout(ms) after which Job should be run without checking unmetered network
     *                status. If {@code requiresUnmeteredNetwork} is {@code false}, this value is
     *                ignored.
     * @return this
     * @see #setRequiresUnmeteredNetwork(boolean)
     * @see #requireUnmeteredNetwork()
     */
    public Params setRequiresUnmeteredNetwork(boolean requiresUnmeteredNetwork, long timeout) {
        if (!requiresUnmeteredNetwork) {
            this.requiresUnmeteredNetworkWithTimeout = NEVER;
        } else {
            this.requiresUnmeteredNetworkWithTimeout = timeout;
        }
        return this;
    }

    /**
     * Convenience method to set unmetered network requirement.
     * @param requiresUnmeteredNetwork true|false
     * @return this
     * @see #setRequiresUnmeteredNetwork(boolean, long)
     * @see #requireUnmeteredNetwork()
     */
    public Params setRequiresUnmeteredNetwork(boolean requiresUnmeteredNetwork) {
        return setRequiresUnmeteredNetwork(requiresUnmeteredNetwork, FOREVER);
    }

    /**
     * Returns when the Job's network requirement will timeout.
     * <ul>
     * <li>If the job does not require network, it will return {@link #NEVER}.</li>
     * <li>If the job should never be run without network, it will return {@link #FOREVER}.</li>
     * <li>Otherwise, it will return the timeout in ms until which the job should require network
     * to be run and after that timeout it will be run regardless of the network requirements.</li>
     * </ul>
     *
     * @return The network requirement constraint
     */
    public long getRequiresNetworkTimeoutMs() {
        return requiresNetworkWithTimeout;
    }

    /**
     * Returns when the Job's UNMETERED network requirement will timeout.
     * <ul>
     * <li>If the job does not require UNMETERED network, it will return {@link #NEVER}.</li>
     * <li>If the job should never be run without UNMETERED network, it will return {@link #FOREVER}.</li>
     * <li>Otherwise, it will return the timeout in ms until which the job should require network
     * to be run and after that timeout it will be run regardless of the UNMETERED network requirements.
     * It may still be requiring a network connection via {@link #requireNetwork()} or
     * {@link #requireNetworkWithTimeout(long)}</li>
     * </ul>
     *
     * @return The network requirement constraint
     */
    public long getRequiresUnmeteredNetworkTimeoutMs() {
        return requiresUnmeteredNetworkWithTimeout;
    }

    /**
     * Convenience method to set network requirement.
     * <p>In case this timeout is set to a value smaller than
     * {@link #requireUnmeteredNetworkWithTimeout(long)}, the unmetered network timeout will override
     * this one.
     *
     * @param requiresNetwork True if Job should not be run without a network, false otherwise.
     * @param timeout The timeout after which Job should be run without checking network status.
     *                If {@code requiresNetwork} is {@code false}, this value is ignored.
     *
     * @return The params
     * @see #setRequiresNetwork(boolean)
     * @see #requireNetwork()
     */
    public Params setRequiresNetwork(boolean requiresNetwork, long timeout) {
        if (requiresNetwork) {
            requiresNetworkWithTimeout = timeout;
        } else {
            requiresNetworkWithTimeout = NEVER;
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
