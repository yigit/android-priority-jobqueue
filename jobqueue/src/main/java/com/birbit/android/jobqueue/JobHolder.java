package com.birbit.android.jobqueue;

import android.content.Context;

import com.birbit.android.jobqueue.network.NetworkUtil;
import com.birbit.android.jobqueue.timer.Timer;

import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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

    /**
     * Internal constant. Used when job's onRun method has thrown an exception and it hit its
     * cancel deadline.
     */
    public static final int RUN_RESULT_HIT_DEADLINE = 7;

    /**
     * Internal constant. Used to find if job is scheduled in {@link com.birbit.android.jobqueue.scheduling.Scheduler Scheduler}.
     */
    public static final long DEFAULT_SCHEDULE_REQUEST_AT_NS_VALUE = -1;

    private Long insertionOrder;
    public final String id;
    public final boolean persistent;
    private int priority;
    public final String groupId;
    private int runCount;
    /**
     * job will be delayed until this nanotime
     */
    private long delayUntilNs;
    /**
     * When job is created, Timer.nanoTime() is assigned to {@code createdNs} value so that we know
     * when job is created in relation to others
     */
    private long createdNs;
    private long runningSessionId;
    /* package */ int requiredNetworkType;
    /**
     * When we should ignore the constraints
     */
    private long deadlineNs;
    /**
     * What to do when deadline is reached
     */
    private boolean cancelOnDeadline;
    transient final Job job;
    protected final Set<String> tags;
    protected final Set<String> dependeeTags;
    private volatile boolean cancelled;
    private volatile boolean cancelledSingleId;
    private long scheduleRequestedAtNs;

    /**
     * may be set after a job is run and cleared by the JobManager
     */
    RetryConstraint retryConstraint;
    /**
     * Eventual exception thrown from the last execution of {@link Job#onRun}.
     * Or {@link Throwable} of dependee job, if dependee job return {@link RetryConstraint#CANCEL}
     * from {@link Job#shouldReRunOnThrowable(Throwable, int, int)}.
     */
    @Nullable
    private Throwable throwable;

    /**
     * @param id                  The ID of the Job
     * @param persistent          Is the job persistent
     * @param priority            Higher is better
     * @param groupId             which group does this job belong to? default null
     * @param runCount            Incremented each time job is fetched to run, initial value should be 0
     * @param job                 Actual job to run
     * @param createdNs           System.nanotime
     * @param delayUntilNs        System.nanotime value: when job can be run the very first time
     * @param runningSessionId    The running session id for the job
     * @param tags                The tags of the Job
     * @param dependeeTags        The dependent tags for the Job
     * @param requiredNetworkType The minimum type of network that is required to run this job
     * @param deadlineNs          System.nanotime value: when the job will ignore its constraints
     * @param cancelOnDeadline    true if job should be cancelled when deadline is reached, false otherwise
     */
    private JobHolder(String id, boolean persistent, int priority, String groupId, int runCount, Job job, long createdNs,
                      long delayUntilNs, long runningSessionId, Set<String> tags, Set<String> dependeeTags,
                      int requiredNetworkType, long deadlineNs, boolean cancelOnDeadline, long scheduleRequestedAtNs) {
        this.id = id;
        this.persistent = persistent;
        this.priority = priority;
        this.groupId = groupId;
        this.runCount = runCount;
        this.createdNs = createdNs;
        this.delayUntilNs = delayUntilNs;
        this.job = job;
        this.runningSessionId = runningSessionId;
        this.requiredNetworkType = requiredNetworkType;
        this.tags = tags;
        this.dependeeTags = dependeeTags;
        this.deadlineNs = deadlineNs;
        this.cancelOnDeadline = cancelOnDeadline;
        this.scheduleRequestedAtNs = scheduleRequestedAtNs;
    }

    /**
     * runs the job w/o throwing any exceptions
     *
     * @param currentRunCount The current run count of the job
     * @return RUN_RESULT
     */
    int safeRun(int currentRunCount, Timer timer) {
        return job.safeRun(this, currentRunCount, timer);
    }

    @NonNull
    public String getId() {
        return id;
    }

    public final String getSingleInstanceId() {
        if (tags != null) {
            for (String tag : tags) {
                if (tag.startsWith(Job.SINGLE_ID_TAG_PREFIX)) {
                    return tag;
                }
            }
        }
        return null;
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

    public long getRunningSessionId() {
        return runningSessionId;
    }

    public void setRunningSessionId(long runningSessionId) {
        this.runningSessionId = runningSessionId;
    }

    public long getDeadlineNs() {
        return deadlineNs;
    }

    public boolean shouldCancelOnDeadline() {
        return cancelOnDeadline;
    }

    public long getDelayUntilNs() {
        return delayUntilNs;
    }

    public Job getJob() {
        return job;
    }

    public String getGroupId() {
        return groupId;
    }

    public Set<String> getTags() {
        return tags;
    }

    public Set<String> getDependeeTags() {
        return dependeeTags;
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

    public long getScheduleRequestedAtNs() {
        return scheduleRequestedAtNs;
    }

    public void setScheduleRequestedAtNs(long scheduleRequestedAtNs) {
        this.scheduleRequestedAtNs = scheduleRequestedAtNs;
    }

    @Override
    public int hashCode() {
        //we don't really care about overflow.
        return id.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof JobHolder)) {
            return false;
        }
        JobHolder other = (JobHolder) o;
        return id.equals(other.id);
    }

    public boolean hasTags() {
        return tags != null && tags.size() > 0;
    }

    public boolean hasDependeeTags() {
        return dependeeTags != null && dependeeTags.size() > 0;
    }

    public void setApplicationContext(Context applicationContext) {
        this.job.setApplicationContext(applicationContext);
    }

    public void setDeadlineIsReached(boolean didReachDeadline) {
        this.job.setDeadlineReached(didReachDeadline);
    }

    public boolean hasDeadline() {
        return deadlineNs != Params.FOREVER;
    }

    public boolean hasDelay() {
        return delayUntilNs != JobManager.NOT_DELAYED_JOB_DELAY;
    }

    public void onCancel(@CancelReason int cancelReason) {
        job.onCancel(cancelReason, throwable);
    }

    public RetryConstraint getRetryConstraint() {
        return retryConstraint;
    }

    void setThrowable(@Nullable Throwable throwable) {
        this.throwable = throwable;
    }

    @Nullable
    Throwable getThrowable() {
        return throwable;
    }

    /**
     * Returns the type of network required by this job.
     * <p>
     * Note that these network status can be compared to each other and higher network type is a
     * larger requirement. For instance, if this method returns
     * {@link com.birbit.android.jobqueue.network.NetworkUtil.NetworkStatus#DISCONNECTED}, that does
     * not mean job requires no network to run. Instead, it means it does not require any network
     * to run.
     *
     * @return The minimum type of network connection that is required to run this job.
     */
    @NetworkUtil.NetworkStatus
    public int getRequiredNetworkType() {
        return requiredNetworkType;
    }

    public static class Builder {
        private int priority;
        private static final int FLAG_PRIORITY = 1;
        private String id;
        private static final int FLAG_PERSISTENT = FLAG_PRIORITY << 1;
        private boolean persistent;
        private static final int FLAG_ID = FLAG_PERSISTENT << 1;
        private String groupId;
        private static final int FLAG_GROUP_ID = FLAG_ID << 1;
        private int runCount = 0;
        private Job job;
        private static final int FLAG_JOB = FLAG_GROUP_ID << 1;
        private long createdNs;
        private static final int FLAG_CREATED_NS = FLAG_JOB << 1;
        private long delayUntilNs = JobManager.NOT_DELAYED_JOB_DELAY;
        private static final int FLAG_DELAY_UNTIL = FLAG_CREATED_NS << 1;
        private Long insertionOrder;
        private long runningSessionId;
        private long deadlineNs = Params.FOREVER;
        private boolean cancelOnDeadline = false;
        private static final int FLAG_DEADLINE = FLAG_DELAY_UNTIL << 1;
        private static final int FLAG_RUNNING_SESSION_ID = FLAG_DEADLINE << 1;
        private int providedFlags = 0;
        private Set<String> tags;
        private static final int FLAG_TAGS = FLAG_RUNNING_SESSION_ID << 1;
        private Set<String> dependeeTags;
        private static final int FLAG_DEPENDEE_TAGS = FLAG_TAGS << 1;
        @NetworkUtil.NetworkStatus
        private int requiredNetworkType;
        private static final int FLAG_REQ_NETWORK = FLAG_DEPENDEE_TAGS << 1;

        private static final int FLAG_SCHEDULE_REQUEST_AT_NS = FLAG_REQ_NETWORK << 1;
        private static final int REQUIRED_FLAGS = (FLAG_SCHEDULE_REQUEST_AT_NS << 1) - 1;
        private long scheduleRequestedAtNs;

        public Builder priority(int priority) {
            this.priority = priority;
            providedFlags |= FLAG_PRIORITY;
            return this;
        }

        public Builder groupId(String groupId) {
            this.groupId = groupId;
            providedFlags |= FLAG_GROUP_ID;
            return this;
        }

        public Builder tags(Set<String> tags) {
            this.tags = tags;
            providedFlags |= FLAG_TAGS;
            return this;
        }

        public Builder dependeeTags(Set<String> tags) {
            this.dependeeTags = tags;
            providedFlags |= FLAG_DEPENDEE_TAGS;
            return this;
        }

        public Builder runCount(int runCount) {
            this.runCount = runCount;
            return this;
        }

        public Builder persistent(boolean persistent) {
            this.persistent = persistent;
            providedFlags |= FLAG_PERSISTENT;
            return this;
        }

        public Builder job(Job job) {
            this.job = job;
            providedFlags |= FLAG_JOB;
            return this;
        }

        public Builder id(String id) {
            this.id = id;
            providedFlags |= FLAG_ID;
            return this;
        }

        public Builder requiredNetworkType(@NetworkUtil.NetworkStatus int requiredNetworkType) {
            this.requiredNetworkType = requiredNetworkType;
            providedFlags |= FLAG_REQ_NETWORK;
            return this;
        }

        public Builder createdNs(long createdNs) {
            this.createdNs = createdNs;
            providedFlags |= FLAG_CREATED_NS;
            return this;
        }

        public Builder delayUntilNs(long delayUntilNs) {
            this.delayUntilNs = delayUntilNs;
            providedFlags |= FLAG_DELAY_UNTIL;
            return this;
        }

        public Builder insertionOrder(long insertionOrder) {
            this.insertionOrder = insertionOrder;
            return this;
        }

        public Builder runningSessionId(long runningSessionId) {
            this.runningSessionId = runningSessionId;
            providedFlags |= FLAG_RUNNING_SESSION_ID;
            return this;
        }

        public Builder deadline(long deadlineNs, boolean cancelOnDeadline) {
            this.deadlineNs = deadlineNs;
            this.cancelOnDeadline = cancelOnDeadline;
            providedFlags |= FLAG_DEADLINE;
            return this;
        }

        public Builder scheduleRequestedAt(long scheduleRequestedAtNs) {
            this.scheduleRequestedAtNs = scheduleRequestedAtNs;
            providedFlags |= FLAG_SCHEDULE_REQUEST_AT_NS;
            return this;
        }

        public JobHolder build() {
            if (job == null) {
                throw new IllegalArgumentException("must provide a job");
            }
            int flagCheck = REQUIRED_FLAGS & providedFlags;
            if (flagCheck != REQUIRED_FLAGS) {
                throw new IllegalArgumentException("must provide all required fields. your result:" + Long.toBinaryString(flagCheck));
            }

            JobHolder jobHolder = new JobHolder(id, persistent, priority, groupId, runCount, job, createdNs,
                    delayUntilNs, runningSessionId, tags, dependeeTags, requiredNetworkType, deadlineNs, cancelOnDeadline,
                    scheduleRequestedAtNs);
            if (insertionOrder != null) {
                jobHolder.setInsertionOrder(insertionOrder);
            }
            job.updateFromJobHolder(jobHolder);
            return jobHolder;
        }
    }
}
