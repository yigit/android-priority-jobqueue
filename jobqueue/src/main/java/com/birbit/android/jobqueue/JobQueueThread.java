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
import com.path.android.jobqueue.Job;
import com.path.android.jobqueue.JobHolder;
import com.path.android.jobqueue.JobQueue;
import com.path.android.jobqueue.JobStatus;
import com.path.android.jobqueue.RetryConstraint;
import com.path.android.jobqueue.callback.JobManagerCallback;
import com.path.android.jobqueue.config.Configuration;
import com.path.android.jobqueue.di.DependencyInjector;
import com.path.android.jobqueue.log.JqLog;
import com.path.android.jobqueue.network.NetworkEventProvider;
import com.path.android.jobqueue.network.NetworkUtil;
import com.path.android.jobqueue.persistentQueue.sqlite.SqlHelper;
import com.path.android.jobqueue.timer.Timer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

class JobQueueThread implements Runnable, NetworkEventProvider.Listener {
    public static final long NS_PER_MS = 1000000;
    public static final long NOT_RUNNING_SESSION_ID = Long.MIN_VALUE;
    public static final long NOT_DELAYED_JOB_DELAY = Long.MIN_VALUE;
    public static final long NETWORK_CHECK_INTERVAL = TimeUnit.MILLISECONDS.toNanos(10000);

    private final Timer timer;
    private final Context appContext;
    private final long sessionId;
    final JobQueue persistentJobQueue;
    final JobQueue nonPersistentJobQueue;
    private final NetworkUtil networkUtil;
    private final DependencyInjector dependencyInjector;
    private final MessageFactory messageFactory;
    final ConsumerController consumerController;
    private List<CancelHandler> pendingCancelHandlers;

    final CallbackManager callbackManager;

    private boolean running = true;

    final PriorityMessageQueue messageQueue;

    JobQueueThread(Configuration config, PriorityMessageQueue messageQueue,
            MessageFactory messageFactory) {
        this.messageQueue = messageQueue;
        if(config.getCustomLogger() != null) {
            JqLog.setCustomLogger(config.getCustomLogger());
        }
        this.messageFactory = messageFactory;
        timer = config.timer();
        appContext = config.getAppContext();
        sessionId = timer.nanoTime();
        this.persistentJobQueue = config.getQueueFactory()
                .createPersistentQueue(appContext, sessionId, config.getId(), config.isInTestMode(),
                        timer);
        this.nonPersistentJobQueue = config.getQueueFactory()
                .createNonPersistent(appContext, sessionId, config.getId(), config.isInTestMode(),
                        timer);
        networkUtil = config.getNetworkUtil();
        dependencyInjector = config.getDependencyInjector();
        if(networkUtil instanceof NetworkEventProvider) {
            ((NetworkEventProvider) networkUtil).setListener(this);
        }
        consumerController = new ConsumerController(this, timer, messageFactory, config);
        callbackManager = new CallbackManager(messageFactory, timer);
        consumerController.handleConstraintChange();
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
        JobHolder jobHolder = new JobHolder.Builder()
                .priority(job.getPriority())
                .job(job)
                .groupId(job.getRunGroupId())
                .createdNs(timer.nanoTime())
                .delayUntilNs(delayUntilNs)
                .runningSessionId(NOT_RUNNING_SESSION_ID).build();

        if (job.isPersistent()) {
            persistentJobQueue.insert(jobHolder);
        } else {
            nonPersistentJobQueue.insert(jobHolder);
        }
        if(JqLog.isDebugEnabled()) {
            JqLog.d("added job class: %s priority: %d delay: %d group : %s persistent: %s requires network: %s"
                    , job.getClass().getSimpleName(), job.getPriority(), job.getDelayInMs(), job.getRunGroupId()
                    , job.isPersistent(), job.requiresNetwork());
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
        consumerController.onJobAdded();
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
                        consumerController.handleIdle((JobConsumerIdleMessage) message);
                        break;
                    case RUN_JOB_RESULT:
                        handleRunJobResult((RunJobResultMessage) message);
                        break;
                    case CONSTRAINT_CHANGE:
                        consumerController.handleConstraintChange();
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
                Long nextJobTimeNs = getNextWakeUpNs();
                JqLog.d("Job queue idle. next job at: %s", nextJobTimeNs);
                if (nextJobTimeNs != null) {
                    long waitInNs = nextJobTimeNs - timer.nanoTime();
                    ConstraintChangeMessage constraintMessage = messageFactory.obtain(ConstraintChangeMessage.class);
                    if (waitInNs > 0) {
                        messageQueue.postAt(constraintMessage, nextJobTimeNs);
                        JqLog.d("wake up jobque at %s", nextJobTimeNs);
                    }
                }
            }
        });
    }

    private void handleCommand(CommandMessage message) {
        if (message.getWhat() == CommandMessage.QUIT) {
            messageQueue.stop();
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
                message.getCallback().onResult(countReadyJobs(hasNetwork()));
                break;
            case PublicQueryMessage.START:
                if (running) {
                    return;
                }
                running = true;
                consumerController.handleConstraintChange();
                break;
            case PublicQueryMessage.STOP:
                JqLog.d("handling stop request...");
                running = false;
                consumerController.handleStop();
                break;
            case PublicQueryMessage.JOB_STATUS:
                JobStatus status = getJobStatus(message.getLongArg(), message.getBooleanArg());
                message.getCallback().onResult(status.ordinal());
                break;
            case PublicQueryMessage.CLEAR:
                clear();
                if (message.getCallback() != null) {
                    message.getCallback().onResult(0);
                }
                break;
        }
    }

    private void clear() {
        nonPersistentJobQueue.clear();
        persistentJobQueue.clear();
    }

    private JobStatus getJobStatus(long id, boolean isPersistent) {
        if (consumerController.isJobRunning(id, isPersistent)) {
            return JobStatus.RUNNING;
        }
        JobHolder holder;
        if(isPersistent) {
            holder = persistentJobQueue.findJobById(id);
        } else {
            holder = nonPersistentJobQueue.findJobById(id);
        }
        if(holder == null) {
            return JobStatus.UNKNOWN;
        }
        boolean network = hasNetwork();
        if(holder.requiresNetwork() && !network) {
            return JobStatus.WAITING_NOT_READY;
        }
        if(holder.getDelayUntilNs() > timer.nanoTime()) {
            return JobStatus.WAITING_NOT_READY;
        }
        return JobStatus.WAITING_READY;
    }

    private void handleCancel(CancelMessage message) {
        CancelHandler handler = new CancelHandler(message.getConstraint(), message.getTags(),
                message.getCallback());
        handler.query(this, consumerController);
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
                try {
                    jobHolder.onCancel();
                } catch (Throwable t) {
                    JqLog.e(t, "job's onCancel did throw an exception, ignoring...");
                }
                callbackManager.notifyOnCancel(jobHolder.getJob(), false);
                removeJob(jobHolder);
                break;
            case JobHolder.RUN_RESULT_TRY_AGAIN:
                retryConstraint = jobHolder.getRetryConstraint();
                insertOrReplace(jobHolder);
                break;
            case JobHolder.RUN_RESULT_FAIL_FOR_CANCEL:
                JqLog.d("running job failed and cancelled, doing nothing. "
                        + "Will be removed after it's onCancel is called by the "
                        + "JobManager");
                break;
        }
        consumerController.handleRunJobResult(message, jobHolder, retryConstraint);
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
    public void onNetworkChange(boolean isConnected) {
        ConstraintChangeMessage constraint = messageFactory.obtain(ConstraintChangeMessage.class);
        constraint.setNetwork(isConnected);
        messageQueue.post(constraint);
    }

    boolean isRunning() {
        return running;
    }

    int countRemainingReadyJobs() {
        return countReadyJobs(hasNetwork());
    }

    private int countReadyJobs(boolean hasNetwork) {
        final Collection<String> runningJobs = consumerController.runningJobGroups.getSafe();
        //TODO we can cache this
        int total = 0;
        total += nonPersistentJobQueue.countReadyJobs(hasNetwork, runningJobs);
        total += persistentJobQueue.countReadyJobs(hasNetwork, runningJobs);
        return total;
    }

    private boolean hasNetwork() {
        return networkUtil == null || networkUtil.isConnected(appContext);
    }

    Long getNextWakeUpNs() {
        final Long groupDelay = consumerController.runningJobGroups.getNextDelayForGroups();
        final boolean hasNetwork = hasNetwork();
        final Collection<String> groups = consumerController.runningJobGroups.getSafe();
        final Long nonPersistent = nonPersistentJobQueue.getNextJobDelayUntilNs(hasNetwork, groups);
        final Long persistent = persistentJobQueue.getNextJobDelayUntilNs(hasNetwork, groups);
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
        if (!(networkUtil instanceof NetworkEventProvider)) {
            // if network cannot provide events, we need to wake up :/
            long checkNetworkAt = timer.nanoTime() + NETWORK_CHECK_INTERVAL;
            delay = delay == null ? checkNetworkAt : Math.min(checkNetworkAt, delay);
        }
        return delay;
    }

    JobHolder getNextJob(Collection<String> runningJobGroups) {
        if (!running) {
            return null;
        }
        boolean haveNetwork = hasNetwork();
        JobHolder jobHolder;
        boolean persistent = false;
        JqLog.d("looking for next job");
        if (JqLog.isDebugEnabled()) {
            JqLog.d("running groups %s", SqlHelper.joinStrings(",", runningJobGroups));
        }
        jobHolder = nonPersistentJobQueue.nextJobAndIncRunCount(haveNetwork, runningJobGroups);
        JqLog.d("non persistent result %s", jobHolder);
        if (jobHolder == null) {
            //go to disk, there aren't any non-persistent jobs
            jobHolder = persistentJobQueue.nextJobAndIncRunCount(haveNetwork, runningJobGroups);
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
