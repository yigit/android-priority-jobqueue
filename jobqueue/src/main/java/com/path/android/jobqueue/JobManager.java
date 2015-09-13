package com.path.android.jobqueue;

import android.content.Context;

import com.path.android.jobqueue.cachedQueue.CachedJobQueue;
import com.path.android.jobqueue.config.Configuration;
import com.path.android.jobqueue.di.DependencyInjector;
import com.path.android.jobqueue.executor.JobConsumerExecutor;
import com.path.android.jobqueue.log.JqLog;
import com.path.android.jobqueue.network.NetworkEventProvider;
import com.path.android.jobqueue.network.NetworkUtil;
import com.path.android.jobqueue.nonPersistentQueue.NonPersistentPriorityQueue;
import com.path.android.jobqueue.persistentQueue.sqlite.SqliteJobQueue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

/**
 * a JobManager that supports;
 * -> Persistent / Non Persistent Jobs
 * -> Job Priority
 * -> Running Jobs in Parallel
 * -> Grouping jobs so that they won't run at the same time
 * -> Stats like waiting Job Count
 */
public class JobManager implements NetworkEventProvider.Listener {
    public static final long NS_PER_MS = 1000000;
    public static final long NOT_RUNNING_SESSION_ID = Long.MIN_VALUE;
    public static final long NOT_DELAYED_JOB_DELAY = Long.MIN_VALUE;
    @SuppressWarnings("FieldCanBeLocal")//used for testing
    private final long sessionId;
    private volatile boolean running;

    private final Context appContext;
    private final NetworkUtil networkUtil;
    private final DependencyInjector dependencyInjector;
    private final JobQueue persistentJobQueue;
    private final JobQueue nonPersistentJobQueue;
    private final CopyOnWriteGroupSet runningJobGroups;
    private final JobConsumerExecutor jobConsumerExecutor;
    private final Object newJobListeners = new Object();
    private final ConcurrentHashMap<Long, CountDownLatch> persistentOnAddedLocks;
    private final ConcurrentHashMap<Long, CountDownLatch> nonPersistentOnAddedLocks;
    private ScheduledExecutorService timedExecutor;
    // lazily created
    private final Object cancelExecutorInitLock = new Object();
    private Executor cancelExecutor;
    private final Object getNextJobLock = new Object();


    /**
     * Default constructor that will create a JobManager with 1 {@link SqliteJobQueue} and 1 {@link NonPersistentPriorityQueue}
     * @param context job manager will use applicationContext.
     */
    public JobManager(Context context) {
        this(context, "default");
    }


    /**
     * Default constructor that will create a JobManager with a default {@link Configuration}
     * @param context application context
     * @param id an id that is unique to this JobManager
     */
    public JobManager(Context context, String id) {
        this(context, new Configuration.Builder(context).id(id).build());
    }

    /**
     *
     * @param context used to acquire ApplicationContext
     * @param config
     */
    public JobManager(Context context, Configuration config) {
        if(config.getCustomLogger() != null) {
            JqLog.setCustomLogger(config.getCustomLogger());
        }
        appContext = context.getApplicationContext();
        running = true;
        runningJobGroups = new CopyOnWriteGroupSet();
        sessionId = System.nanoTime();
        this.persistentJobQueue = config.getQueueFactory()
                .createPersistentQueue(context, sessionId, config.getId(), config.isInTestMode());
        this.nonPersistentJobQueue = config.getQueueFactory()
                .createNonPersistent(context, sessionId, config.getId(), config.isInTestMode());
        persistentOnAddedLocks = new ConcurrentHashMap<Long, CountDownLatch>();
        nonPersistentOnAddedLocks = new ConcurrentHashMap<Long, CountDownLatch>();

        networkUtil = config.getNetworkUtil();
        dependencyInjector = config.getDependencyInjector();
        if(networkUtil instanceof NetworkEventProvider) {
            ((NetworkEventProvider) networkUtil).setListener(this);
        }
        //is important to initialize consumers last so that they can start running
        jobConsumerExecutor = new JobConsumerExecutor(config,consumerContract);
        timedExecutor = Executors.newSingleThreadScheduledExecutor();
        start();
    }


    /**
     * Stops consuming jobs. Currently running jobs will be finished but no new jobs will be run.
     */
    public void stop() {
        running = false;
    }

    /**
     * restarts the JobManager. Will create a new consumer if necessary.
     */
    public void start() {
        if(running) {
            return;
        }
        running = true;
        notifyJobConsumer();
    }

    /**
     * returns the # of jobs that are waiting to be executed.
     * This might be a good place to decide whether you should wake your app up on boot etc. to complete pending jobs.
     * @return # of total jobs.
     */
    public int count() {
        int cnt = 0;
        synchronized (nonPersistentJobQueue) {
            cnt += nonPersistentJobQueue.count();
        }
        synchronized (persistentJobQueue) {
            cnt += persistentJobQueue.count();
        }
        return cnt;
    }

    private int countReadyJobs(boolean hasNetwork) {
        //TODO we can cache this
        int total = 0;
        synchronized (nonPersistentJobQueue) {
            total += nonPersistentJobQueue.countReadyJobs(hasNetwork, runningJobGroups.getSafe());
        }
        synchronized (persistentJobQueue) {
            total += persistentJobQueue.countReadyJobs(hasNetwork, runningJobGroups.getSafe());
        }
        return total;
    }

    /**
     * Adds a new Job to the list and returns an ID for it.
     * @param job to add
     * @return id for the job.
     */
    public long addJob(Job job) {
        //noinspection deprecation
        JobHolder jobHolder = new JobHolder(job.getPriority(), job
                , job.getDelayInMs() > 0 ? System.nanoTime() + job.getDelayInMs() * NS_PER_MS : NOT_DELAYED_JOB_DELAY
                , NOT_RUNNING_SESSION_ID);
        long id;
        if (job.isPersistent()) {
            synchronized (persistentJobQueue) {
                id = persistentJobQueue.insert(jobHolder);
                addOnAddedLock(persistentOnAddedLocks, id);
            }
        } else {
            synchronized (nonPersistentJobQueue) {
                id = nonPersistentJobQueue.insert(jobHolder);
                addOnAddedLock(nonPersistentOnAddedLocks, id);
            }
        }
        if(JqLog.isDebugEnabled()) {
            JqLog.d("added job id: %d class: %s priority: %d delay: %d group : %s persistent: %s requires network: %s"
                    , id, job.getClass().getSimpleName(), job.getPriority(), job.getDelayInMs(), job.getRunGroupId()
                    , job.isPersistent(), job.requiresNetwork());
        }
        if(dependencyInjector != null) {
            //inject members b4 calling onAdded
            dependencyInjector.inject(job);
        }
        jobHolder.getJob().setApplicationContext(appContext);
        jobHolder.getJob().onAdded();
        if(job.isPersistent()) {
            synchronized (persistentJobQueue) {
                clearOnAddedLock(persistentOnAddedLocks, id);
            }
        } else {
            synchronized (nonPersistentJobQueue) {
                clearOnAddedLock(nonPersistentOnAddedLocks, id);
            }
        }
        ensureConsumerWhenNeeded(null);
        return id;
    }

    /**
     * Cancels all jobs matching the list of tags.
     * <p>
     * Note that, if any of the matching jobs is running, this method WILL wait for them to finish
     * or fail.
     * <p>
     * This method uses a separate single threaded executor pool just for cancelling jobs
     * because it may potentially wait for a long running job (if query matches that job). This
     * pool is lazily created when the very first cancel request arrives.
     * <p>
     * A job may be already running when cancelJob is called. In this case, JobManager will wait
     * until job fails or ends before returning from this method. If jobs succeeds before
     * JobManager can cancel it, it will be added into {@link CancelResult#getFailedToCancel()}
     * list.
     * <p>
     * If you call {@link #addJob(Job)} while {@link #cancelJobs(TagConstraint, String...)} is
     * running, the behavior of that job will be undefined. If that jobs gets added to the queue
     * before cancel query runs, it may be cancelled before running. It is up to you to sync these
     * two requests if such cases may happen for you.
     * <p/>
     * This query is not atomic. If application terminates while jobs are being cancelled, some of
     * them may be cancelled while some remain in the queue (for persistent jobs).
     * <p/>
     * This method guarantees calling {@link Job#onCancel()} before job is removed
     * from the queue. If application terminates while {@link Job#onCancel()} is running, the
     * Job will not be removed from disk (same behavior with jobs failing due to other reasons like
     * hitting retry limit).
     *
     * @param constraint The constraint to use while selecting jobs. If set to {@link TagConstraint#ANY},
     *                   any job that has one of the given tags will be cancelled. If set to
     *                   {@link TagConstraint#ALL}, jobs that has all of the given tags will be cancelled.
     * @param tags The list of tags
     */
    public void cancelJobsInBackground(final CancelResult.AsyncCancelCallback cancelCallback,
            final TagConstraint constraint, final String... tags) {
        synchronized (cancelExecutorInitLock) {
            if (cancelExecutor == null) {
                cancelExecutor = Executors.newSingleThreadExecutor();
            }
            cancelExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    CancelResult result = cancelJobs(constraint, tags);
                    if (cancelCallback != null) {
                        cancelCallback.onCancelled(result);
                    }
                }
            });
        }
    }

    /**
     * Cancel all jobs matching the list of tags.
     * <p>
     * Note that, you should NOT call this method on main thread because it queries database and
     * may also need to wait for running jobs to finish.
     * <p>
     * A job may be already running when cancelJob is called. In this case, JobManager will wait
     * until job fails or ends before returning from this method. If jobs succeeds before
     * JobManager can cancel it, it will be added into {@link CancelResult#getFailedToCancel()}
     * list.
     * <p>
     * If you call {@link #addJob(Job)} while {@link #cancelJobs(TagConstraint, String...)} is
     * running, the behavior of that job will be undefined. If that jobs gets added to the queue
     * before cancel query runs, it may be cancelled before running. It is up to you to sync these
     * two requests if such cases may happen for you.
     * <p/>
     * This query is not atomic. If application terminates while jobs are being cancelled, some of
     * them may be cancelled while some remain in the queue (for persistent jobs).
     * <p/>
     * This method guarantees calling {@link Job#onCancel()} before job is removed
     * from the queue. If application terminates while {@link Job#onCancel()} is running, the
     * Job will not be removed from disk (same behavior with jobs failing due to other reasons like
     * hitting retry limit).
     *
     * @param constraint The constraint to use while selecting jobs. If set to {@link TagConstraint#ANY},
     *                   any job that has one of the given tags will be cancelled. If set to
     *                   {@link TagConstraint#ALL}, jobs that has all of the given tags will be cancelled.
     * @param tags       The list of tags
     * @return A Cancel result containing the list of jobs.
     * @see #cancelJobsInBackground(CancelResult.AsyncCancelCallback, TagConstraint, String...)
     */
    public CancelResult cancelJobs(final TagConstraint constraint, final String... tags) {
        final List<JobHolder> jobs = new ArrayList<JobHolder>();
        final Set<Long> persistentJobIds = new HashSet<Long>();
        final Set<Long> nonPersistentJobIds = new HashSet<Long>();
        final Set<Long> runningNonPersistentJobIds = new HashSet<>();
        final Set<Long> runningPersistentJobIds = new HashSet<>();
        synchronized (getNextJobLock) {
            jobConsumerExecutor.inRunningJobHoldersLock(new Runnable() {
                @Override
                public void run() {
                    // TODO if app terminates while cancelling, job will be removed w/o receiving an onCancel call!!!
                    Set<JobHolder> nonPersistentRunningJobs = jobConsumerExecutor
                            .findRunningByTags(constraint, tags, false);
                    synchronized (nonPersistentJobQueue) {
                        markJobsAsCancelledAndFilterAlreadyCancelled(nonPersistentRunningJobs,
                                nonPersistentJobQueue, nonPersistentJobIds);
                        runningNonPersistentJobIds.addAll(nonPersistentJobIds);
                        Set<JobHolder> nonPersistentJobs = nonPersistentJobQueue
                                .findJobsByTags(constraint, true, nonPersistentJobIds, tags);
                        markJobsAsCancelledAndFilterAlreadyCancelled(nonPersistentJobs,
                                nonPersistentJobQueue, nonPersistentJobIds);
                        jobs.addAll(nonPersistentJobs);
                    }
                    jobs.addAll(nonPersistentRunningJobs);

                    Set<JobHolder> persistentRunningJobs = jobConsumerExecutor
                            .findRunningByTags(constraint, tags, true);
                    synchronized (persistentJobQueue) {
                        markJobsAsCancelledAndFilterAlreadyCancelled(persistentRunningJobs,
                                persistentJobQueue, persistentJobIds);
                        runningPersistentJobIds.addAll(persistentJobIds);
                        Set<JobHolder> persistentJobs = persistentJobQueue
                                .findJobsByTags(constraint, true, persistentJobIds, tags);
                        markJobsAsCancelledAndFilterAlreadyCancelled(persistentJobs,
                                persistentJobQueue, persistentJobIds);
                        jobs.addAll(persistentJobs);
                    }
                    jobs.addAll(persistentRunningJobs);
                }
            });
        }

        try {
            // non persistent jobs are removed from queue as soon as they are marked as cancelled
            // persistent jobs are given a running session id upon cancellation.
            // this ensures that these jobs won't show up in next job queries.
            // if subsequent cancel requests come for these jobs, they won't show up again either
            // because markJobsAsCancelledAndFilterAlreadyCancelled will filter them out
            jobConsumerExecutor.waitUntilDone(persistentJobIds, nonPersistentJobIds);
        } catch (InterruptedException e) {
            JqLog.e(e, "error while waiting for jobs to finish");
        }
        CancelResult result = new CancelResult();
        for (JobHolder holder : jobs) {
            JqLog.d("checking if I could cancel %s. Result: %s", holder.getJob(), !holder.isSuccessful());
            if (holder.isSuccessful()) {
                result.failedToCancel.add(holder.getJob());
            } else {
                result.cancelledJobs.add(holder.getJob());
                try {
                    holder.getJob().onCancel();
                } catch (Throwable t) {
                    JqLog.e(t, "cancelled job's onCancel has thrown exception");
                }
                // if job is removed while running, make sure we remove it from running job
                // groups as well. JobExecutor won't remove the job.
                if (holder.getJob().isPersistent()) {
                    synchronized (persistentJobQueue) {
                        persistentJobQueue.remove(holder);
                    }
                    if (holder.getGroupId() != null &&
                            runningPersistentJobIds.contains(holder.getId())) {
                        runningJobGroups.remove(holder.getGroupId());
                    }
                } else if (holder.getGroupId() != null &&
                        runningNonPersistentJobIds.contains(holder.getId())) {
                    runningJobGroups.remove(holder.getGroupId());
                }
            }
        }
        return result;
    }

    private void markJobsAsCancelledAndFilterAlreadyCancelled(Set<JobHolder> jobs, JobQueue queue,
            Set<Long> outIds) {
        Iterator<JobHolder> itr = jobs.iterator();
        while (itr.hasNext()) {
            JobHolder holder = itr.next();
            // although cancelled is not persistent to disk, this will still work because we would
            // receive the same job back if it was just cancelled in this session.
            if (holder.isCancelled()) {
                itr.remove();
            } else {
                holder.markAsCancelled();
                outIds.add(holder.getId());
                queue.onJobCancelled(holder);
            }
        }
    }

    /**
     * Non-blocking convenience method to add a job in background thread.
     * @see #addJob(Job)
     * @param job job to add
     *
     */
    public void addJobInBackground(Job job) {
        //noinspection deprecation
        addJobInBackground(job, null);
    }

    public void addJobInBackground(final Job job, /*nullable*/ final AsyncAddCallback callback) {
        timedExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    long id = addJob(job);
                    if(callback != null) {
                        callback.onAdded(id);
                    }
                } catch (Throwable t) {
                    JqLog.e(t, "addJobInBackground received an exception. job class: %s", job.getClass().getSimpleName());
                }
            }
        });
    }

    //need to sync on related job queue before calling this
    private void addOnAddedLock(ConcurrentHashMap<Long, CountDownLatch> lockMap, long id) {
        lockMap.put(id, new CountDownLatch(1));
    }

    //need to sync on related job queue before calling this
    private void waitForOnAddedLock(ConcurrentHashMap<Long, CountDownLatch> lockMap, long id) {
        CountDownLatch latch = lockMap.get(id);
        if(latch == null) {
            return;
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            JqLog.e(e, "could not wait for onAdded lock");
        }
    }

    //need to sync on related job queue before calling this
    private void clearOnAddedLock(ConcurrentHashMap<Long, CountDownLatch> lockMap, long id) {
        CountDownLatch latch = lockMap.get(id);
        if(latch != null) {
            latch.countDown();
        }
        lockMap.remove(id);
    }

    /**
     * checks next available job and returns when it will be available (if it will, otherwise returns {@link Long#MAX_VALUE})
     * also creates a timer to notify listeners at that time
     * @param hasNetwork .
     * @return time wait until next job (in milliseconds)
     */
    private long ensureConsumerWhenNeeded(Boolean hasNetwork) {
        if(hasNetwork == null) {
            //if network util can inform us when network is recovered, we we'll check only next job that does not
            //require network. if it does not know how to inform us, we have to keep a busy loop.
            //noinspection SimplifiableConditionalExpression
            hasNetwork = networkUtil instanceof NetworkEventProvider ? hasNetwork() : true;
        }
        //this method is called when there are jobs but job consumer was not given any
        //this may happen in a race condition or when the latest job is a delayed job
        Long nextRunNs;
        synchronized (nonPersistentJobQueue) {
            nextRunNs = nonPersistentJobQueue.getNextJobDelayUntilNs(hasNetwork);
        }
        if(nextRunNs != null && nextRunNs <= System.nanoTime()) {
            notifyJobConsumer();
            return 0L;
        }
        Long persistedJobRunNs;
        synchronized (persistentJobQueue) {
            persistedJobRunNs = persistentJobQueue.getNextJobDelayUntilNs(hasNetwork);
        }
        if(persistedJobRunNs != null) {
            if(nextRunNs == null) {
                nextRunNs = persistedJobRunNs;
            } else if(persistedJobRunNs < nextRunNs) {
                nextRunNs = persistedJobRunNs;
            }
        }
        if(nextRunNs != null) {
            //to avoid overflow, we need to check equality first
            if(nextRunNs < System.nanoTime()) {
                notifyJobConsumer();
                return 0L;
            }
            long diff = (long)Math.ceil((double)(nextRunNs - System.nanoTime()) / NS_PER_MS);
            ensureConsumerOnTime(diff);
            return diff;
        }
        return Long.MAX_VALUE;
    }

    private void notifyJobConsumer() {
        synchronized (newJobListeners) {
            newJobListeners.notifyAll();
        }
        jobConsumerExecutor.considerAddingConsumer();
    }

    private final Runnable notifyRunnable = new Runnable() {
        @Override
        public void run() {
            notifyJobConsumer();
        }
    };

    private void ensureConsumerOnTime(long waitMs) {
        timedExecutor.schedule(notifyRunnable, waitMs, TimeUnit.MILLISECONDS);
    }

    private boolean hasNetwork() {
        return networkUtil == null || networkUtil.isConnected(appContext);
    }

    private JobHolder getNextJob() {
        boolean haveNetwork = hasNetwork();
        JobHolder jobHolder;
        boolean persistent = false;
        synchronized (getNextJobLock) {
            final Collection<String> runningJobGroups = this.runningJobGroups.getSafe();
            synchronized (nonPersistentJobQueue) {
                jobHolder = nonPersistentJobQueue.nextJobAndIncRunCount(haveNetwork, runningJobGroups);
            }
            if (jobHolder == null) {
                //go to disk, there aren't any non-persistent jobs
                synchronized (persistentJobQueue) {
                    jobHolder = persistentJobQueue.nextJobAndIncRunCount(haveNetwork, runningJobGroups);
                    persistent = true;
                }
            }
            if(jobHolder == null) {
                return null;
            }
            if(persistent && dependencyInjector != null) {
                dependencyInjector.inject(jobHolder.getJob());
            }
            if(jobHolder.getGroupId() != null) {
                this.runningJobGroups.add(jobHolder.getGroupId());
            }
        }

        //wait for onAdded locks. wait for locks after job is selected so that we minimize the lock
        if(persistent) {
            waitForOnAddedLock(persistentOnAddedLocks, jobHolder.getId());
        } else {
            waitForOnAddedLock(nonPersistentOnAddedLocks, jobHolder.getId());
        }
        jobHolder.getJob().setApplicationContext(appContext);
        return jobHolder;
    }

    private void reAddJob(JobHolder jobHolder) {
        JqLog.d("re-adding job %s", jobHolder.getId());
        if (!jobHolder.isCancelled()) {
            if (jobHolder.getJob().isPersistent()) {
                synchronized (persistentJobQueue) {
                    persistentJobQueue.insertOrReplace(jobHolder);
                }
            } else {
                synchronized (nonPersistentJobQueue) {
                    nonPersistentJobQueue.insertOrReplace(jobHolder);
                }
            }
        } else {
            JqLog.d("not re-adding cancelled job " + jobHolder);
        }
        if(jobHolder.getGroupId() != null) {
            runningJobGroups.remove(jobHolder.getGroupId());
        }
    }

    /**
     * Returns the current status of a {@link Job}.
     * <p>
     *     You should not call this method on the UI thread because it may make a db request.
     * </p>
     * <p>
     *     This is not a very fast call so try not to make it unless necessary. Consider using events if you need to be
     *     informed about a job's lifecycle.
     * </p>
     * @param id the ID, returned by the addJob method
     * @param isPersistent Jobs are added to different queues depending on if they are persistent or not. This is necessary
     *                     because each queue has independent id sets.
     * @return
     */
    public JobStatus getJobStatus(long id, boolean isPersistent) {
        if(jobConsumerExecutor.isRunning(id, isPersistent)) {
            return JobStatus.RUNNING;
        }
        JobHolder holder;
        if(isPersistent) {
            synchronized (persistentJobQueue) {
                holder = persistentJobQueue.findJobById(id);
            }
        } else {
            synchronized (nonPersistentJobQueue) {
                holder = nonPersistentJobQueue.findJobById(id);
            }
        }
        if(holder == null) {
            return JobStatus.UNKNOWN;
        }
        boolean network = hasNetwork();
        if(holder.requiresNetwork() && !network) {
            return JobStatus.WAITING_NOT_READY;
        }
        if(holder.getDelayUntilNs() > System.nanoTime()) {
            return JobStatus.WAITING_NOT_READY;
        }

        return JobStatus.WAITING_READY;
    }

    private void removeJob(JobHolder jobHolder) {
        if (jobHolder.getJob().isPersistent()) {
            synchronized (persistentJobQueue) {
                persistentJobQueue.remove(jobHolder);
            }
        } else {
            synchronized (nonPersistentJobQueue) {
                nonPersistentJobQueue.remove(jobHolder);
            }
        }
        if(jobHolder.getGroupId() != null) {
            runningJobGroups.remove(jobHolder.getGroupId());
        }
    }

    public synchronized void stopAndWaitUntilConsumersAreFinished() throws InterruptedException {
        stop();
        timedExecutor.shutdownNow();
        synchronized (newJobListeners) {
            newJobListeners.notifyAll();
        }
        jobConsumerExecutor.waitUntilAllConsumersAreFinished();
        timedExecutor = Executors.newSingleThreadScheduledExecutor();
    }

    public synchronized void clear() {
        synchronized (nonPersistentJobQueue) {
            nonPersistentJobQueue.clear();
            nonPersistentOnAddedLocks.clear();
        }
        synchronized (persistentJobQueue) {
            persistentJobQueue.clear();
            persistentOnAddedLocks.clear();
        }
        runningJobGroups.clear();
    }

    /**
     * if {@link NetworkUtil} implements {@link NetworkEventProvider}, this method is called when network is recovered
     * @param isConnected network connection state.
     */
    @Override
    public void onNetworkChange(boolean isConnected) {
        ensureConsumerWhenNeeded(isConnected);
    }

    @SuppressWarnings("FieldCanBeLocal")
    private final JobConsumerExecutor.Contract consumerContract = new JobConsumerExecutor.Contract() {
        @Override
        public boolean isRunning() {
            return running;
        }

        @Override
        public void insertOrReplace(JobHolder jobHolder) {
            RetryConstraint retryConstraint = jobHolder.getJob().retryConstraint;
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
                    delay > 0 ? System.nanoTime() + delay * NS_PER_MS : NOT_DELAYED_JOB_DELAY
            );
            reAddJob(jobHolder);
        }

        @Override
        public void removeJob(JobHolder jobHolder) {
            JobManager.this.removeJob(jobHolder);
        }

        @Override
        public JobHolder getNextJob(int wait, TimeUnit waitDuration) {
            //be optimistic
            JobHolder nextJob = JobManager.this.getNextJob();
            if(nextJob != null) {
                return nextJob;
            }
            long start = System.nanoTime();
            long remainingWait = waitDuration.toNanos(wait);
            long waitUntil = remainingWait + start;
            //for delayed jobs,
            long nextJobDelay = ensureConsumerWhenNeeded(null);
            while (nextJob == null && waitUntil > System.nanoTime() && running) {
                //keep running inside here to avoid busy loop
                nextJob = running ? JobManager.this.getNextJob() : null;
                if(nextJob == null) {
                    long remaining = waitUntil - System.nanoTime();
                    if(remaining > 0) {
                        //if we can't detect network changes, we won't be notified.
                        //to avoid waiting up to give time, wait in chunks of 500 ms max
                        long maxWait = Math.min(nextJobDelay, TimeUnit.NANOSECONDS.toMillis(remaining));
                        if(maxWait < 1 || !running) {
                            continue;//wait(0) will cause infinite wait.
                        }
                        if(networkUtil instanceof NetworkEventProvider) {
                            //to handle delayed jobs, make sure we trigger this first
                            //looks like there is no job available right now, wait for an event.
                            //there is a chance that if it triggers a timer and it gets called before I enter
                            //sync block, i am going to lose it
                            //TODO fix above case where we may wait unnecessarily long if a job is about to become available
                            synchronized (newJobListeners) {
                                try {
                                    newJobListeners.wait(maxWait);
                                } catch (InterruptedException e) {
                                    JqLog.e(e, "exception while waiting for a new job.");
                                }
                            }
                        } else {
                            //we cannot detect network changes. our best option is to wait for some time and try again
                            //then trigger {@link ensureConsumerWhenNeeded)
                            synchronized (newJobListeners) {
                                try {
                                    newJobListeners.wait(Math.min(500, maxWait));
                                } catch (InterruptedException e) {
                                    JqLog.e(e, "exception while waiting for a new job.");
                                }
                            }
                        }
                    }
                }
            }
            return nextJob;
        }

        @Override
        public int countRemainingReadyJobs() {
            //if we can't detect network changes, assume we have network otherwise nothing will trigger a consumer
            //noinspection SimplifiableConditionalExpression
            return countReadyJobs(networkUtil instanceof NetworkEventProvider ? hasNetwork() : true);
        }
    };


    /**
     * Default implementation of QueueFactory that creates one {@link SqliteJobQueue} and one {@link NonPersistentPriorityQueue}
     * both are wrapped inside a {@link CachedJobQueue} to improve performance
     */
    public static class DefaultQueueFactory implements QueueFactory {
        SqliteJobQueue.JobSerializer jobSerializer;

        public DefaultQueueFactory() {
            jobSerializer = new SqliteJobQueue.JavaSerializer();
        }

        public DefaultQueueFactory(SqliteJobQueue.JobSerializer jobSerializer) {
            this.jobSerializer = jobSerializer;
        }

        @Override
        public JobQueue createPersistentQueue(Context context, Long sessionId, String id,
                boolean inTestMode) {
            return new CachedJobQueue(new SqliteJobQueue(context, sessionId, id, jobSerializer,
                    inTestMode));
        }

        @Override
        public JobQueue createNonPersistent(Context context, Long sessionId, String id,
                boolean inTestMode) {
            return new CachedJobQueue(new NonPersistentPriorityQueue(sessionId, id, inTestMode));
        }
    }
}
