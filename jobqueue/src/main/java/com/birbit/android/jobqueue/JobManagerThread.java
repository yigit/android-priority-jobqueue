package com.birbit.android.jobqueue;

import android.content.Context;
import android.support.annotation.Nullable;

import com.birbit.android.jobqueue.callback.JobManagerCallback;
import com.birbit.android.jobqueue.config.Configuration;
import com.birbit.android.jobqueue.di.DependencyInjector;
import com.birbit.android.jobqueue.log.JqLog;
import com.birbit.android.jobqueue.messaging.Message;
import com.birbit.android.jobqueue.messaging.MessageFactory;
import com.birbit.android.jobqueue.messaging.MessageQueueConsumer;
import com.birbit.android.jobqueue.messaging.PriorityMessageQueue;
import com.birbit.android.jobqueue.messaging.message.AddJobMessage;
import com.birbit.android.jobqueue.messaging.message.CancelMessage;
import com.birbit.android.jobqueue.messaging.message.CommandMessage;
import com.birbit.android.jobqueue.messaging.message.ConstraintChangeMessage;
import com.birbit.android.jobqueue.messaging.message.JobConsumerIdleMessage;
import com.birbit.android.jobqueue.messaging.message.PublicQueryMessage;
import com.birbit.android.jobqueue.messaging.message.RunJobResultMessage;
import com.birbit.android.jobqueue.messaging.message.SchedulerMessage;
import com.birbit.android.jobqueue.network.NetworkEventProvider;
import com.birbit.android.jobqueue.network.NetworkUtil;
import com.birbit.android.jobqueue.scheduling.Scheduler;
import com.birbit.android.jobqueue.scheduling.SchedulerConstraint;
import com.birbit.android.jobqueue.timer.Timer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

class JobManagerThread implements Runnable, NetworkEventProvider.Listener {
    public static final long NS_PER_MS = 1000000;
    public static final long NOT_RUNNING_SESSION_ID = Long.MIN_VALUE;
    public static final long NOT_DELAYED_JOB_DELAY = Long.MIN_VALUE;


    final Timer timer;
    private final Context appContext;
    @SuppressWarnings("FieldCanBeLocal")
    private final long sessionId;
    final JobQueue persistentJobQueue;
    final JobQueue nonPersistentJobQueue;
    private final NetworkUtil networkUtil;
    private final DependencyInjector dependencyInjector;
    private final MessageFactory messageFactory;
    final ConsumerManager consumerManager;
    @Nullable private List<CancelHandler> pendingCancelHandlers;
    @Nullable private List<SchedulerConstraint> pendingSchedulerCallbacks;
    final Constraint queryConstraint = new Constraint();

    final CallbackManager callbackManager;

    private boolean running = true;
    /**
     * We set this to true whenever we schedule a wake up and set to false whenever we call
     * cancelAll. It is not precise, does not cover scheduling across reboots but a fair compromise
     * for simplicity.
     */
    private boolean shouldCancelAllScheduledWhenEmpty = false;
    // see https://github.com/yigit/android-priority-jobqueue/issues/262
    private boolean canScheduleConstraintChangeOnIdle = true;

    final PriorityMessageQueue messageQueue;
    @Nullable
    Scheduler scheduler;

    JobManagerThread(Configuration config, PriorityMessageQueue messageQueue,
            MessageFactory messageFactory) {
        this.messageQueue = messageQueue;
        if(config.getCustomLogger() != null) {
            JqLog.setCustomLogger(config.getCustomLogger());
        }
        this.messageFactory = messageFactory;
        timer = config.getTimer();
        appContext = config.getAppContext();
        sessionId = timer.nanoTime();
        scheduler = config.getScheduler();
        if (scheduler != null && config.batchSchedulerRequests() &&
                !(scheduler instanceof BatchingScheduler)) {
            scheduler = new BatchingScheduler(scheduler, timer);
        }
        this.persistentJobQueue = config.getQueueFactory()
                .createPersistentQueue(config, sessionId);
        this.nonPersistentJobQueue = config.getQueueFactory()
                .createNonPersistent(config, sessionId);
        networkUtil = config.getNetworkUtil();
        dependencyInjector = config.getDependencyInjector();
        if(networkUtil instanceof NetworkEventProvider) {
            ((NetworkEventProvider) networkUtil).setListener(this);
        }
        consumerManager = new ConsumerManager(this, timer, messageFactory, config);
        callbackManager = new CallbackManager(messageFactory, timer);
    }

    void addCallback(JobManagerCallback callback) {
        callbackManager.addCallback(callback);
    }

    boolean removeCallback(JobManagerCallback callback) {
        return callbackManager.removeCallback(callback);
    }

    boolean canListenToNetwork() {
        return networkUtil instanceof NetworkEventProvider;
    }

    private void handleAddJob(AddJobMessage message) {
        Job job = message.getJob();
        //noinspection deprecation
        long now = timer.nanoTime();
        long delayUntilNs = job.getDelayInMs() > 0
                ? now + job.getDelayInMs() * NS_PER_MS
                : NOT_DELAYED_JOB_DELAY;
        long deadline = job.getDeadlineInMs() > 0
                ? now + job.getDeadlineInMs() * NS_PER_MS
                : Params.FOREVER;
        JobHolder jobHolder = new JobHolder.Builder()
                .priority(job.getPriority())
                .job(job)
                .groupId(job.getRunGroupId())
                .createdNs(now)
                .delayUntilNs(delayUntilNs)
                .id(job.getId())
                .tags(job.getTags())
                .persistent(job.isPersistent())
                .runCount(0)
                .deadline(deadline, job.shouldCancelOnDeadline())
                .requiredNetworkType(job.requiredNetworkType)
                .runningSessionId(NOT_RUNNING_SESSION_ID).build();

        JobHolder oldJob = findJobBySingleId(job.getSingleInstanceId());
        final boolean insert = oldJob == null || consumerManager.isJobRunning(oldJob.getId());
        if (insert) {
            JobQueue queue = job.isPersistent() ? persistentJobQueue : nonPersistentJobQueue;
            if (oldJob != null) { //the other job was running, will be cancelled if it fails
                consumerManager.markJobsCancelledSingleId(TagConstraint.ANY, new String[]{job.getSingleInstanceId()});
                queue.substitute(jobHolder, oldJob);
            } else {
                queue.insert(jobHolder);
            }
            if (JqLog.isDebugEnabled()) {
                JqLog.d("added job class: %s priority: %d delay: %d group : %s persistent: %s"
                        , job.getClass().getSimpleName(), job.getPriority(), job.getDelayInMs()
                        , job.getRunGroupId(), job.isPersistent());
            }
        } else {
            JqLog.d("another job with same singleId: %s was already queued", job.getSingleInstanceId());
        }
        if(dependencyInjector != null) {
            //inject members b4 calling onAdded
            dependencyInjector.inject(job);
        }
        jobHolder.setApplicationContext(appContext);
        jobHolder.getJob().onAdded();
        callbackManager.notifyOnAdded(jobHolder.getJob());
        if (insert) {
            consumerManager.onJobAdded();
            if (job.isPersistent()) {
                scheduleWakeUpFor(jobHolder, now);
            }
        } else {
            cancelSafely(jobHolder, CancelReason.SINGLE_INSTANCE_ID_QUEUED);
            callbackManager.notifyOnDone(jobHolder.getJob());
        }
    }

    private void scheduleWakeUpFor(JobHolder holder, long now) {
        if (scheduler == null) {
            return;
        }
        int requiredNetwork = holder.requiredNetworkType;
        long delayUntilNs = holder.getDelayUntilNs();
        long deadlineNs = holder.getDeadlineNs();
        long delay = delayUntilNs > now ? TimeUnit.NANOSECONDS.toMillis(delayUntilNs - now) : 0;
        Long deadline = deadlineNs != Params.FOREVER
                ? TimeUnit.NANOSECONDS.toMillis(deadlineNs - now)
                : null;
        boolean hasLargeDelay = delayUntilNs > now && delay >= JobManager.MIN_DELAY_TO_USE_SCHEDULER_IN_MS;
        boolean hasLargeDeadline = deadline != null && deadline >= JobManager.MIN_DELAY_TO_USE_SCHEDULER_IN_MS;
        if (requiredNetwork == NetworkUtil.DISCONNECTED && !hasLargeDelay && !hasLargeDeadline) {
            return;
        }

        SchedulerConstraint constraint = new SchedulerConstraint(UUID.randomUUID().toString());
        constraint.setNetworkStatus(requiredNetwork);
        constraint.setDelayInMs(delay);
        constraint.setOverrideDeadlineInMs(deadline);
        scheduler.request(constraint);
        shouldCancelAllScheduledWhenEmpty = true;
    }

    /**
     * Returns a queued job with the same single id. If any matching non-running job is found,
     * that one is returned. Otherwise any matching running job will be returned.
     */
    private JobHolder findJobBySingleId(/*Nullable*/String singleIdTag) {
        if (singleIdTag != null) {
            queryConstraint.clear();
            queryConstraint.setTags(new String[]{singleIdTag});
            queryConstraint.setTagConstraint(TagConstraint.ANY);
            queryConstraint.setMaxNetworkType(NetworkUtil.UNMETERED);
            Set<JobHolder> jobs = nonPersistentJobQueue.findJobs(queryConstraint);
            jobs.addAll(persistentJobQueue.findJobs(queryConstraint));
            if (!jobs.isEmpty()) {
                for (JobHolder job : jobs) {
                    if (!consumerManager.isJobRunning(job.getId())) {
                        return job;
                    }
                }
                return jobs.iterator().next();
            }
        }
        return null;
    }

    @Override
    public void run() {
        messageQueue.consume(new MessageQueueConsumer() {
            @Override
            public void handleMessage(Message message) {
                canScheduleConstraintChangeOnIdle = true;
                switch (message.type) {
                    case ADD_JOB:
                        handleAddJob((AddJobMessage) message);
                        break;
                    case JOB_CONSUMER_IDLE:
                        boolean busy = consumerManager.handleIdle((JobConsumerIdleMessage) message);
                        if (!busy) {
                            invokeSchedulersIfIdle();
                        }
                        break;
                    case RUN_JOB_RESULT:
                        handleRunJobResult((RunJobResultMessage) message);
                        break;
                    case CONSTRAINT_CHANGE:
                        boolean handled = consumerManager.handleConstraintChange();
                        ConstraintChangeMessage constraintChangeMessage =
                                (ConstraintChangeMessage) message;
                        canScheduleConstraintChangeOnIdle = handled ||
                                !constraintChangeMessage.isForNextJob();
                        break;
                    case CANCEL:
                        handleCancel((CancelMessage) message);
                        break;
                    case PUBLIC_QUERY:
                        handlePublicQuery((PublicQueryMessage) message);
                        break;
                    case COMMAND:
                        handleCommand((CommandMessage) message);
                        break;
                    case SCHEDULER:
                        handleSchedulerMessage((SchedulerMessage) message);
                        break;
                }
            }

            @Override
            public void onIdle() {
                JqLog.v("joq idle. running:? %s", running);
                if (!running) {
                    return;
                }
                if (!canScheduleConstraintChangeOnIdle) {
                    JqLog.v("skipping scheduling a new idle callback because looks like last one"
                            + " did not do anything");
                    return;
                }
                Long nextJobTimeNs = getNextWakeUpNs(true);
                // TODO check network should be another message which goes idle if network is the
                // same as now
                JqLog.d("Job queue idle. next job at: %s", nextJobTimeNs);
                if (nextJobTimeNs != null) {
                    ConstraintChangeMessage constraintMessage =
                            messageFactory.obtain(ConstraintChangeMessage.class);
                    constraintMessage.setForNextJob(true);
                    messageQueue.postAt(constraintMessage, nextJobTimeNs);
                } else if (scheduler != null) {
                    // if we have a scheduler but the queue is empty, just clean them all.
                    if (shouldCancelAllScheduledWhenEmpty && persistentJobQueue.count() == 0) {
                        shouldCancelAllScheduledWhenEmpty = false;
                        scheduler.cancelAll();
                    }
                }
            }
        });
    }

    private void invokeSchedulersIfIdle() {
        if (scheduler == null || pendingSchedulerCallbacks == null
                || pendingSchedulerCallbacks.isEmpty() || !consumerManager.areAllConsumersIdle()) {
            return;
        }
        for (int i = pendingSchedulerCallbacks.size() - 1; i >= 0; i--) {
            SchedulerConstraint constraint = pendingSchedulerCallbacks.remove(i);
            boolean reschedule = hasJobsWithSchedulerConstraint(constraint);
            scheduler.onFinished(constraint, reschedule);
        }
    }

    private void handleSchedulerMessage(SchedulerMessage message) {
        final int what = message.getWhat();
        if (what == SchedulerMessage.START) {
            handleSchedulerStart(message.getConstraint());
        } else if (what == SchedulerMessage.STOP) {
            handleSchedulerStop(message.getConstraint());
        } else {
            throw new IllegalArgumentException("Unknown scheduler message with what " + what);
        }
    }

    private boolean hasJobsWithSchedulerConstraint(SchedulerConstraint constraint) {
        if (consumerManager.hasJobsWithSchedulerConstraint(constraint)) {
            return true;
        }

        queryConstraint.clear();
        queryConstraint.setNowInNs(timer.nanoTime());
        queryConstraint.setMaxNetworkType(constraint.getNetworkStatus());
        return persistentJobQueue.countReadyJobs(queryConstraint) > 0;
    }

    private void handleSchedulerStop(SchedulerConstraint constraint) {
        final List<SchedulerConstraint> pendingCallbacks = this.pendingSchedulerCallbacks;
        if (pendingCallbacks != null) {
            for (int i = pendingCallbacks.size() - 1; i >= 0; i--) {
                SchedulerConstraint pendingConstraint = pendingCallbacks.get(i);
                if (pendingConstraint.getUuid().equals(constraint.getUuid())) {
                    pendingCallbacks.remove(i);
                }
            }
        }
        if (scheduler == null) {
            return;//nothing to do
        }
        final boolean hasMatchingJobs = hasJobsWithSchedulerConstraint(constraint);
        if (hasMatchingJobs) {
            // reschedule
            scheduler.request(constraint);
        }
    }


    private void handleSchedulerStart(SchedulerConstraint constraint) {
        if (!isRunning()) {
            if (scheduler != null) {
                scheduler.onFinished(constraint, true);
            }
            return;
        }
        boolean hasMatchingJobs = hasJobsWithSchedulerConstraint(constraint);
        if (!hasMatchingJobs) {
            if (scheduler != null) {
                scheduler.onFinished(constraint, false);
            }
            return;
        }
        if (pendingSchedulerCallbacks == null) {
            pendingSchedulerCallbacks = new ArrayList<>();
        }
        // add this to callbacks to be invoked when job runs
        pendingSchedulerCallbacks.add(constraint);
        consumerManager.handleConstraintChange();
    }

    private void handleCommand(CommandMessage message) {
        if (message.getWhat() == CommandMessage.QUIT) {
            messageQueue.stop();
            messageQueue.clear();
        }
    }

    int count() {
        return persistentJobQueue.count() + nonPersistentJobQueue.count();
    }

    private void handlePublicQuery(PublicQueryMessage message) {
        switch (message.getWhat()) {
            case PublicQueryMessage.COUNT:
                message.getCallback().onResult(count());
                break;
            case PublicQueryMessage.COUNT_READY:
                message.getCallback().onResult(countReadyJobs(getNetworkStatus()));
                break;
            case PublicQueryMessage.START:
                JqLog.d("handling start request...");
                if (running) {
                    return;
                }
                running = true;
                consumerManager.handleConstraintChange();
                break;
            case PublicQueryMessage.STOP:
                JqLog.d("handling stop request...");
                running = false;
                consumerManager.handleStop();
                break;
            case PublicQueryMessage.JOB_STATUS:
                JobStatus status = getJobStatus(message.getStringArg());
                message.getCallback().onResult(status.ordinal());
                break;
            case PublicQueryMessage.CLEAR:
                clear();
                if (message.getCallback() != null) {
                    message.getCallback().onResult(0);
                }
                break;
            case PublicQueryMessage.ACTIVE_CONSUMER_COUNT:
                message.getCallback().onResult(consumerManager.getWorkerCount());
                break;
            case PublicQueryMessage.INTERNAL_RUNNABLE:
                message.getCallback().onResult(0);
                break;
            default:
                throw new IllegalArgumentException("cannot handle public query with type " +
                message.getWhat());
        }
    }

    private void clear() {
        nonPersistentJobQueue.clear();
        persistentJobQueue.clear();
    }

    private JobStatus getJobStatus(String id) {
        if (consumerManager.isJobRunning(id)) {
            return JobStatus.RUNNING;
        }
        JobHolder holder;
        holder = nonPersistentJobQueue.findJobById(id);
        if (holder == null) {
            holder = persistentJobQueue.findJobById(id);
        }
        if(holder == null) {
            return JobStatus.UNKNOWN;
        }
        final int networkStatus = getNetworkStatus();
        final long now = timer.nanoTime();
        if(networkStatus < holder.requiredNetworkType) {
            return JobStatus.WAITING_NOT_READY;
        }
        if(holder.getDelayUntilNs() > now) {
            return JobStatus.WAITING_NOT_READY;
        }
        return JobStatus.WAITING_READY;
    }

    private void handleCancel(CancelMessage message) {
        CancelHandler handler = new CancelHandler(message.getConstraint(), message.getTags(),
                message.getCallback());
        handler.query(this, consumerManager);
        if (handler.isDone()) {
            handler.commit(this);
        } else {
            if (pendingCancelHandlers == null) {
                pendingCancelHandlers = new ArrayList<>();
            }
            pendingCancelHandlers.add(handler);
        }
    }

    private void handleRunJobResult(RunJobResultMessage message) {
        final int result = message.getResult();
        final JobHolder jobHolder = message.getJobHolder();
        callbackManager.notifyOnRun(jobHolder.getJob(), result);
        RetryConstraint retryConstraint = null;
        switch (result) {
            case JobHolder.RUN_RESULT_SUCCESS:
                removeJob(jobHolder);
                break;
            case JobHolder.RUN_RESULT_FAIL_RUN_LIMIT:
                cancelSafely(jobHolder, CancelReason.REACHED_RETRY_LIMIT);
                removeJob(jobHolder);
                break;
            case JobHolder.RUN_RESULT_FAIL_SHOULD_RE_RUN:
                cancelSafely(jobHolder, CancelReason.CANCELLED_VIA_SHOULD_RE_RUN);
                removeJob(jobHolder);
                break;
            case JobHolder.RUN_RESULT_FAIL_SINGLE_ID:
                cancelSafely(jobHolder, CancelReason.SINGLE_INSTANCE_WHILE_RUNNING);
                removeJob(jobHolder);
                break;
            case JobHolder.RUN_RESULT_HIT_DEADLINE:
                cancelSafely(jobHolder, CancelReason.REACHED_DEADLINE);
                removeJob(jobHolder);
                break;
            case JobHolder.RUN_RESULT_TRY_AGAIN:
                retryConstraint = jobHolder.getRetryConstraint();
                insertOrReplace(jobHolder);
                break;
            case JobHolder.RUN_RESULT_FAIL_FOR_CANCEL:
                JqLog.d("running job failed and cancelled, doing nothing. "
                        + "Will be removed after it's onCancel is called by the "
                        + "CancelHandler");
                break;
            default:
                throw new IllegalArgumentException("unknown job holder result");
        }
        consumerManager.handleRunJobResult(message, jobHolder, retryConstraint);
        callbackManager.notifyAfterRun(jobHolder.getJob(), result);
        if (pendingCancelHandlers != null) {
            int limit = pendingCancelHandlers.size();
            for (int i = 0; i < limit; i ++) {
                CancelHandler handler = pendingCancelHandlers.get(i);
                handler.onJobRun(jobHolder, result);
                if (handler.isDone()) {
                    handler.commit(this);
                    pendingCancelHandlers.remove(i);
                    i--;
                    limit--;
                }
            }
        }
    }

    private void cancelSafely(JobHolder jobHolder, @CancelReason int cancelReason) {
        try {
            jobHolder.onCancel(cancelReason);
        } catch (Throwable t) {
            JqLog.e(t, "job's onCancel did throw an exception, ignoring...");
        }
        callbackManager.notifyOnCancel(jobHolder.getJob(), false, jobHolder.getThrowable());
    }

    private void insertOrReplace(JobHolder jobHolder) {
        RetryConstraint retryConstraint = jobHolder.getRetryConstraint();
        if (retryConstraint == null) {
            reAddJob(jobHolder);
            return;
        }
        if (retryConstraint.getNewPriority() != null) {
            jobHolder.setPriority(retryConstraint.getNewPriority());
        }
        long delay = -1;
        if (retryConstraint.getNewDelayInMs() != null) {
            delay = retryConstraint.getNewDelayInMs();
        }
        jobHolder.setDelayUntilNs(
                delay > 0 ? timer.nanoTime() + delay * NS_PER_MS : NOT_DELAYED_JOB_DELAY
        );
        reAddJob(jobHolder);
    }

    private void reAddJob(JobHolder jobHolder) {
        if (!jobHolder.isCancelled()) {
            if (jobHolder.getJob().isPersistent()) {
                persistentJobQueue.insertOrReplace(jobHolder);
            } else {
                nonPersistentJobQueue.insertOrReplace(jobHolder);
            }
        } else {
            JqLog.d("not re-adding cancelled job " + jobHolder);
        }
    }

    private void removeJob(JobHolder jobHolder) {
        if (jobHolder.getJob().isPersistent()) {
            persistentJobQueue.remove(jobHolder);
        } else {
            nonPersistentJobQueue.remove(jobHolder);
        }
        callbackManager.notifyOnDone(jobHolder.getJob());
    }

    @Override
    public void onNetworkChange(@NetworkUtil.NetworkStatus int networkStatus) {
        ConstraintChangeMessage constraint = messageFactory.obtain(ConstraintChangeMessage.class);
        messageQueue.post(constraint);
    }

    boolean isRunning() {
        return running;
    }

    int countRemainingReadyJobs() {
        return countReadyJobs(getNetworkStatus());
    }

    private int countReadyJobs(@NetworkUtil.NetworkStatus int networkStatus) {
        final Collection<String> runningJobs = consumerManager.runningJobGroups.getSafe();
        queryConstraint.clear();
        queryConstraint.setNowInNs(timer.nanoTime());
        queryConstraint.setMaxNetworkType(networkStatus);
        queryConstraint.setExcludeGroups(runningJobs);
        queryConstraint.setExcludeRunning(true);
        queryConstraint.setTimeLimit(timer.nanoTime());
        //TODO we can cache this
        int total = 0;
        total += nonPersistentJobQueue.countReadyJobs(queryConstraint);
        total += persistentJobQueue.countReadyJobs(queryConstraint);
        return total;
    }

    @NetworkUtil.NetworkStatus
    private int getNetworkStatus() {
        return networkUtil == null ? NetworkUtil.UNMETERED : networkUtil.getNetworkStatus(appContext);
    }

    Long getNextWakeUpNs(boolean includeNetworkWatch) {
        final Long groupDelay = consumerManager.runningJobGroups.getNextDelayForGroups();
        final int networkStatus = getNetworkStatus();
        final Collection<String> groups = consumerManager.runningJobGroups.getSafe();
        queryConstraint.clear();
        queryConstraint.setNowInNs(timer.nanoTime());
        queryConstraint.setMaxNetworkType(networkStatus);
        queryConstraint.setExcludeGroups(groups);
        queryConstraint.setExcludeRunning(true);
        final Long nonPersistent = nonPersistentJobQueue.getNextJobDelayUntilNs(queryConstraint);
        final Long persistent = persistentJobQueue.getNextJobDelayUntilNs(queryConstraint);
        Long delay = null;
        if (groupDelay != null) {
            delay = groupDelay;
        }
        if (nonPersistent != null) {
            delay = delay == null ? nonPersistent : Math.min(nonPersistent, delay);
        }
        if (persistent != null) {
            delay = delay == null ? persistent : Math.min(persistent, delay);
        }
        if (includeNetworkWatch && !(networkUtil instanceof NetworkEventProvider)) {
            // if network cannot provide events, we need to wake up :/
            long checkNetworkAt = timer.nanoTime() + JobManager.NETWORK_CHECK_INTERVAL;
            delay = delay == null ? checkNetworkAt : Math.min(checkNetworkAt, delay);
        }
        return delay;
    }

    // Used for testing
    JobHolder getNextJobForTesting() {
        return getNextJobForTesting(null);
    }

    // Used for testing
    JobHolder getNextJobForTesting(Collection<String> runningJobGroups) {
        return getNextJob(runningJobGroups, true);
    }

    JobHolder getNextJob(Collection<String> runningJobGroups) {
        return getNextJob(runningJobGroups, false);
    }

    JobHolder getNextJob(Collection<String> runningJobGroups, boolean ignoreRunning) {
        if (!running && !ignoreRunning) {
            return null;
        }
        JobHolder jobHolder = null;
        while (jobHolder == null) {
            final int networkStatus = getNetworkStatus();
            boolean persistent = false;
            JqLog.v("looking for next job");
            queryConstraint.clear();
            long now = timer.nanoTime();
            queryConstraint.setNowInNs(now);
            queryConstraint.setMaxNetworkType(networkStatus);
            queryConstraint.setExcludeGroups(runningJobGroups);
            queryConstraint.setExcludeRunning(true);
            queryConstraint.setTimeLimit(now);
            jobHolder = nonPersistentJobQueue.nextJobAndIncRunCount(queryConstraint);
            JqLog.v("non persistent result %s", jobHolder);
            if (jobHolder == null) {
                //go to disk, there aren't any non-persistent jobs
                jobHolder = persistentJobQueue.nextJobAndIncRunCount(queryConstraint);
                persistent = true;
                JqLog.v("persistent result %s", jobHolder);
            }
            if (jobHolder == null) {
                return null;
            }
            if (persistent && dependencyInjector != null) {
                dependencyInjector.inject(jobHolder.getJob());
            }
            jobHolder.setApplicationContext(appContext);
            jobHolder.setDeadlineIsReached(jobHolder.getDeadlineNs() <= now);
            if (jobHolder.getDeadlineNs() <= now
                    && jobHolder.shouldCancelOnDeadline()) {
                cancelSafely(jobHolder, CancelReason.REACHED_DEADLINE);
                removeJob(jobHolder);
                jobHolder = null;
            }
        }
        return jobHolder;
    }
}
