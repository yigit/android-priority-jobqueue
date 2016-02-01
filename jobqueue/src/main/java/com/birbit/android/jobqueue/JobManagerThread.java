package com.birbit.android.jobqueue;

import android.content.Context;

import com.birbit.android.jobqueue.messaging.Message;
import com.birbit.android.jobqueue.messaging.MessageFactory;
import com.birbit.android.jobqueue.messaging.MessageQueueConsumer;
import com.birbit.android.jobqueue.messaging.PriorityMessageQueue;
import com.birbit.android.jobqueue.messaging.message.AddJobMessage;
import com.birbit.android.jobqueue.messaging.message.CancelMessage;
import com.birbit.android.jobqueue.messaging.message.CommandMessage;
import com.birbit.android.jobqueue.messaging.message.ConstraintChangeMessage;
import com.birbit.android.jobqueue.messaging.message.PublicQueryMessage;
import com.birbit.android.jobqueue.messaging.message.JobConsumerIdleMessage;
import com.birbit.android.jobqueue.messaging.message.RunJobResultMessage;
import com.path.android.jobqueue.CancelReason;
import com.path.android.jobqueue.Job;
import com.path.android.jobqueue.JobHolder;
import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.JobQueue;
import com.path.android.jobqueue.JobStatus;
import com.path.android.jobqueue.RetryConstraint;
import com.path.android.jobqueue.TagConstraint;
import com.path.android.jobqueue.callback.JobManagerCallback;
import com.path.android.jobqueue.config.Configuration;
import com.path.android.jobqueue.di.DependencyInjector;
import com.path.android.jobqueue.log.JqLog;
import com.path.android.jobqueue.network.NetworkEventProvider;
import com.path.android.jobqueue.network.NetworkUtil;
import com.path.android.jobqueue.timer.Timer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.path.android.jobqueue.network.NetworkUtil.DISCONNECTED;
import static com.path.android.jobqueue.network.NetworkUtil.UNMETERED;

class JobManagerThread implements Runnable, NetworkEventProvider.Listener {
    public static final long NS_PER_MS = 1000000;
    public static final long NOT_RUNNING_SESSION_ID = Long.MIN_VALUE;
    public static final long NOT_DELAYED_JOB_DELAY = Long.MIN_VALUE;


    final Timer timer;
    private final Context appContext;
    private final long sessionId;
    final JobQueue persistentJobQueue;
    final JobQueue nonPersistentJobQueue;
    private final NetworkUtil networkUtil;
    private final DependencyInjector dependencyInjector;
    private final MessageFactory messageFactory;
    final ConsumerManager consumerManager;
    private List<CancelHandler> pendingCancelHandlers;
    final Constraint queryConstraint = new Constraint();

    final CallbackManager callbackManager;

    private boolean running = true;

    final PriorityMessageQueue messageQueue;

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

    private void handleAddJob(AddJobMessage message) {
        Job job = message.getJob();
        //noinspection deprecation
        long delayUntilNs = job.getDelayInMs() > 0
                ? timer.nanoTime() + job.getDelayInMs() * NS_PER_MS
                : NOT_DELAYED_JOB_DELAY;
        job.seal(timer);
        JobHolder jobHolder = new JobHolder.Builder()
                .priority(job.getPriority())
                .job(job)
                .groupId(job.getRunGroupId())
                .createdNs(timer.nanoTime())
                .delayUntilNs(delayUntilNs)
                .runningSessionId(NOT_RUNNING_SESSION_ID).build();

        JobHolder oldJob = findJobBySingleId(job.getSingleInstanceId());
        final boolean insert = oldJob == null || consumerManager.isJobRunning(oldJob.getId());
        if (insert) {
            if (job.isPersistent()) {
                persistentJobQueue.insert(jobHolder);
            } else {
                nonPersistentJobQueue.insert(jobHolder);
            }
            if (JqLog.isDebugEnabled()) {
                JqLog.d("added job class: %s priority: %d delay: %d group : %s persistent: %s requires network: %s"
                        , job.getClass().getSimpleName(), job.getPriority(), job.getDelayInMs(), job.getRunGroupId()
                        , job.isPersistent(), job.requiresNetwork(timer));
            }
        } else {
            JqLog.d("another job id: %d with same singleId: %s was already queued",
                    oldJob.getId(), job.getSingleInstanceId());
        }
        if(dependencyInjector != null) {
            //inject members b4 calling onAdded
            dependencyInjector.inject(job);
        }
        jobHolder.setApplicationContext(appContext);
        try {
            jobHolder.getJob().onAdded();
        } catch (Throwable t) {
            JqLog.e(t, "job's onAdded did throw an exception, ignoring...");
        }
        callbackManager.notifyOnAdded(jobHolder.getJob());
        if (insert) {
            consumerManager.onJobAdded();
            if (oldJob != null) { //the job was running, will be cancelled if it fails
                oldJob.markAsCancelledSingleId();
            }
        } else {
            cancelSafely(jobHolder, CancelReason.SINGLE_INSTANCE_ID_QUEUED);
            callbackManager.notifyOnDone(jobHolder.getJob());
        }
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
                switch (message.type) {
                    case ADD_JOB:
                        handleAddJob((AddJobMessage) message);
                        break;
                    case JOB_CONSUMER_IDLE:
                        consumerManager.handleIdle((JobConsumerIdleMessage) message);
                        break;
                    case RUN_JOB_RESULT:
                        handleRunJobResult((RunJobResultMessage) message);
                        break;
                    case CONSTRAINT_CHANGE:
                        consumerManager.handleConstraintChange();
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
                }
            }

            @Override
            public void onIdle() {
                JqLog.d("joq idle. running:? %s", running);
                if (!running) {
                    return;
                }
                Long nextJobTimeNs = getNextWakeUpNs(true);
                // TODO check network should be another message which goes idle if network is the
                // same as now
                JqLog.d("Job queue idle. next job at: %s", nextJobTimeNs);
                if (nextJobTimeNs != null) {
                    ConstraintChangeMessage constraintMessage = messageFactory.obtain(ConstraintChangeMessage.class);
                    messageQueue.postAt(constraintMessage, nextJobTimeNs);
                }
            }
        });
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
        long now = timer.nanoTime();
        if(networkStatus == DISCONNECTED && holder.requiresNetwork(now)) {
            return JobStatus.WAITING_NOT_READY;
        }
        if(networkStatus != UNMETERED && holder.requiresUnmeteredNetwork(now)) {
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
                jobHolder.markAsSuccessful();
                removeJob(jobHolder);
                break;
            case JobHolder.RUN_RESULT_FAIL_RUN_LIMIT:
            case JobHolder.RUN_RESULT_FAIL_SHOULD_RE_RUN:
            case JobHolder.RUN_RESULT_FAIL_SINGLE_ID:
                cancelSafely(jobHolder, result);
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

    private void cancelSafely(JobHolder jobHolder, int cancelReason) {
        try {
            jobHolder.onCancel(cancelReason);
        } catch (Throwable t) {
            JqLog.e(t, "job's onCancel did throw an exception, ignoring...");
        }
        callbackManager.notifyOnCancel(jobHolder.getJob(), false);
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
        queryConstraint.setNetworkStatus(networkStatus);
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
        queryConstraint.setNetworkStatus(networkStatus);
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
        final int networkStatus = getNetworkStatus();
        JobHolder jobHolder;
        boolean persistent = false;
        JqLog.d("looking for next job");
        queryConstraint.clear();
        queryConstraint.setNowInNs(timer.nanoTime());
        queryConstraint.setNetworkStatus(networkStatus);
        queryConstraint.setExcludeGroups(runningJobGroups);
        queryConstraint.setExcludeRunning(true);
        queryConstraint.setTimeLimit(timer.nanoTime());
        jobHolder = nonPersistentJobQueue.nextJobAndIncRunCount(queryConstraint);
        JqLog.d("non persistent result %s", jobHolder);
        if (jobHolder == null) {
            //go to disk, there aren't any non-persistent jobs
            jobHolder = persistentJobQueue.nextJobAndIncRunCount(queryConstraint);
            persistent = true;
            JqLog.d("persistent result %s", jobHolder);
        }
        if(jobHolder == null) {
            return null;
        }
        if(persistent && dependencyInjector != null) {
            dependencyInjector.inject(jobHolder.getJob());
        }
        jobHolder.setApplicationContext(appContext);
        return jobHolder;
    }
}
