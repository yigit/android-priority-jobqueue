package com.birbit.android.jobqueue;

import android.content.Context;
import android.support.annotation.Nullable;

import com.birbit.android.jobqueue.config.Configuration;

import java.util.Collections;
import java.util.Set;

/**
 * Container class to address Jobs inside job manager.
 */
public class JobHolder {

    /**
     * Internal constant. Job's onRun method completed w/o any exception.
     */
    public static final int RUN_RESULT_SUCCESS = 1;
    /**
     * Internal constant. Job's onRun method thrown an exception and either it does not want to
     * run again or reached retry limit.
     */
    public static final int RUN_RESULT_FAIL_RUN_LIMIT = 2;

    /**
     * Internal constant. Job's onRun method has thrown an exception and it was cancelled after it
     * started.
     */
    public static final int RUN_RESULT_FAIL_FOR_CANCEL = 3;
    /**
     * Internal constant. Job's onRun method failed but wants to retry.
     */
    public static final int RUN_RESULT_TRY_AGAIN = 4;

    /**
     * The job decided not to run in shouldReRun method.
     */
    public static final int RUN_RESULT_FAIL_SHOULD_RE_RUN = 5;
    /**
     * Internal constant. Job's onRun method has thrown an exception and another job with the
     * same single instance id had been queued.
     */
    public static final int RUN_RESULT_FAIL_SINGLE_ID = 6;

    protected Long insertionOrder;
    protected String id;
    protected int priority;
    protected String groupId;
    protected int runCount;
    /**
     * job will be delayed until this nanotime
     */
    protected long delayUntilNs;
    /**
     * When job is created, Timer.nanoTime() is assigned to {@code createdNs} value so that we know
     * when job is created in relation to others
     */
    protected long createdNs;
    protected long runningSessionId;
    protected long requiresNetworkUntilNs;
    protected long requiresUnmeteredNetworkUntilNs;
    transient Job job;
    protected final Set<String> tags;
    private volatile boolean cancelled;
    private volatile boolean cancelledSingleId;
    private volatile boolean successful;
    /**
     * Eventual exception thrown from the last execution of {@link Job#onRun}
     */
    @Nullable private Throwable throwable;

    /**
     * @param id               The ID of the Job
     * @param priority         Higher is better
     * @param groupId          which group does this job belong to? default null
     * @param runCount         Incremented each time job is fetched to run, initial value should be 0
     * @param job              Actual job to run
     * @param createdNs        System.nanotime
     * @param delayUntilNs     System.nanotime value where job can be run the very first time
     * @param runningSessionId The running session id for the job
     * @param tags             The tags of the Job
     * @param requiresNetworkUntilNs Until when this job requires network
     * @param requiresUnmeteredNetworkUntilNs Until when this job requires unmetered network
     */
    private JobHolder(String id, int priority, String groupId, int runCount, Job job, long createdNs,
                      long delayUntilNs, long runningSessionId, Set<String> tags,
                      long requiresNetworkUntilNs, long requiresUnmeteredNetworkUntilNs) {
        this.id = id;
        this.priority = priority;
        this.groupId = groupId;
        this.runCount = runCount;
        this.createdNs = createdNs;
        this.delayUntilNs = delayUntilNs;
        this.job = job;
        this.runningSessionId = runningSessionId;
        this.requiresNetworkUntilNs = requiresNetworkUntilNs;
        this.requiresUnmeteredNetworkUntilNs = requiresUnmeteredNetworkUntilNs;
        this.tags = tags;
    }

    /**
     * runs the job w/o throwing any exceptions
     * @param currentRunCount The current run count of the job
     *
     * @return RUN_RESULT
     */
    public int safeRun(int currentRunCount) {
        return job.safeRun(this, currentRunCount);
    }

    public String getId() {
        return id;
    }

    /**
     * The time should be acquired from {@link Configuration#getTimer()}
     *
     * @param timeInNs The current time in ns. This should be the time used by the JobManager.
     *
     * @return True if the job requires network to be run right now, false otherwise.
     */
    public boolean requiresNetwork(long timeInNs) {
        return requiresNetworkUntilNs > timeInNs;
    }

    public long getRequiresNetworkUntilNs() {
        return requiresNetworkUntilNs;
    }

    /**
     * The time should be acquired from {@link Configuration#getTimer()}
     *
     * @param timeInNs The current time in ns. This should be the time used by the JobManager.
     *
     * @return True if the job requires a UNMETERED network to be run right now, false otherwise.
     */
    public boolean requiresUnmeteredNetwork(long timeInNs) {
        return requiresUnmeteredNetworkUntilNs > timeInNs;
    }

    public long getRequiresUnmeteredNetworkUntilNs() {
        return requiresUnmeteredNetworkUntilNs;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
        this.job.priority = this.priority;
    }

    public Long getInsertionOrder() {
        return insertionOrder;
    }

    public void setInsertionOrder(long insertionOrder) {
        this.insertionOrder = insertionOrder;
    }

    public void setDelayUntilNs(long delayUntilNs) {
        this.delayUntilNs = delayUntilNs;
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

    public long getDelayUntilNs() {
        return delayUntilNs;
    }

    public Job getJob() {
        return job;
    }

    public void setJob(Job job) {
        this.job = job;
        this.id = job.getId();
    }

    public String getGroupId() {
        return groupId;
    }

    public Set<String> getTags() {
        return tags;
    }

    public void markAsCancelled() {
        cancelled = true;
        job.cancelled = true;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void markAsCancelledSingleId() {
        cancelledSingleId = true;
        markAsCancelled();
    }

    public boolean isCancelledSingleId() {
        return cancelledSingleId;
    }

    @Override
    public int hashCode() {
        //we don't really care about overflow.
        return id.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if(!(o instanceof JobHolder)) {
            return false;
        }
        JobHolder other = (JobHolder) o;
        return id.equals(other.id);
    }

    public boolean hasTags() {
        return tags != null && tags.size() > 0;
    }

    public synchronized void markAsSuccessful() {
        successful = true;
    }

    public synchronized boolean isSuccessful() {
        return successful;
    }

    public void setApplicationContext(Context applicationContext) {
        this.job.setApplicationContext(applicationContext);
    }

    public void onCancel(@CancelReason int cancelReason) {
        job.onCancel(cancelReason, throwable);
    }

    public RetryConstraint getRetryConstraint() {
        return job.retryConstraint;
    }

    void setThrowable(@Nullable Throwable throwable) {
        this.throwable = throwable;
    }

    @Nullable
    Throwable getThrowable() {
        return throwable;
    }

    public static class Builder {
        private int priority;
        private String id;
        private String groupId;
        private int runCount;
        private Job job;
        private long createdNs;
        private long delayUntilNs = JobManager.NOT_DELAYED_JOB_DELAY;
        private Long insertionOrder;
        private long runningSessionId;
        private int providedFlags = 0;
        private Set<String> tags;
        private long requiresNetworkUntilNs;
        private long requiresUnmeteredNetworkUntilNs;

        private static final int FLAG_SESSION_ID = 1;
        private static final int FLAG_PRIORITY = 1 << 1;
        private static final int FLAG_CREATED_AT = 1 << 2;
        private static final int FLAG_ID = 1 << 3;
        private static final int FLAG_TAGS = 1 << 4;
        private static final int FLAG_REQ_NETWORK_UNTIL = 1 << 5;
        private static final int FLAG_REQ_UNMETERED_NETWORK_UNTIL = 1 << 6;

        public Builder priority(int priority) {
            this.priority = priority;
            providedFlags |= FLAG_PRIORITY;
            return this;
        }
        public Builder groupId(String groupId) {
            this.groupId = groupId;
            return this;
        }

        public Builder tags(Set<String> tags) {
            this.tags = tags;
            providedFlags |= FLAG_TAGS;
            return this;
        }

        public Builder runCount(int runCount) {
            this.runCount = runCount;
            return this;
        }

        public Builder job(Job job) {
            this.job = job;
            return this;
        }

        public Builder id(String id) {
            this.id = id;
            providedFlags |= FLAG_ID;
            return this;
        }

        public Builder requiresNetworkUntilNs(long requiresNetworkUntilNs) {
            this.requiresNetworkUntilNs = requiresNetworkUntilNs;
            providedFlags |= FLAG_REQ_NETWORK_UNTIL;
            return this;
        }

        public Builder requiresUnmeteredNetworkUntil(long requiresUnmeteredNetworkUntilNs) {
            this.requiresUnmeteredNetworkUntilNs = requiresUnmeteredNetworkUntilNs;
            providedFlags |= FLAG_REQ_UNMETERED_NETWORK_UNTIL;
            return this;
        }

        public Builder createdNs(long createdNs) {
            this.createdNs = createdNs;
            providedFlags |= FLAG_CREATED_AT;
            return this;
        }
        public Builder delayUntilNs(long delayUntilNs) {
            this.delayUntilNs = delayUntilNs;
            return this;
        }
        public Builder insertionOrder(long insertionOrder) {
            this.insertionOrder = insertionOrder;
            return this;
        }
        public Builder runningSessionId(long runningSessionId) {
            this.runningSessionId = runningSessionId;
            providedFlags |= FLAG_SESSION_ID;
            return this;
        }
        public JobHolder build() {
            if (job == null) {
                throw new IllegalArgumentException("must provide a job");
            }
            if ((providedFlags & FLAG_PRIORITY) == 0) {
                throw new IllegalArgumentException("must provide a priority");
            }
            if ((providedFlags & FLAG_SESSION_ID) == 0) {
                throw new IllegalArgumentException("must provide a session id");
            }
            if ((providedFlags & FLAG_CREATED_AT) == 0) {
                throw new IllegalArgumentException("must provide a created timestamp");
            }
            if ((providedFlags & FLAG_ID) == 0) {
                throw new IllegalArgumentException("must provide the JobId");
            }
            if ((providedFlags & FLAG_TAGS) == 0) {
                throw new IllegalArgumentException("must provide tags");
            }
            if ((providedFlags & FLAG_REQ_NETWORK_UNTIL) == 0) {
                throw new IllegalArgumentException("must provide requires network until");
            }
            if ((providedFlags & FLAG_REQ_NETWORK_UNTIL) == 0) {
                throw new IllegalArgumentException("must provide requires unmetered network until");
            }
            JobHolder jobHolder = new JobHolder(id, priority, groupId, runCount, job, createdNs,
                    delayUntilNs, runningSessionId, tags, requiresNetworkUntilNs,
                    requiresUnmeteredNetworkUntilNs);
            if (insertionOrder != null) {
                jobHolder.setInsertionOrder(insertionOrder);
            }
            return jobHolder;
        }
    }
}
