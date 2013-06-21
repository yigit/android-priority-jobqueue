package com.path.android.jobqueue;

import android.content.Context;
import com.path.android.jobqueue.config.Configuration;
import com.path.android.jobqueue.di.DependencyInjector;
import com.path.android.jobqueue.log.JqLog;
import com.path.android.jobqueue.network.NetworkEventProvider;
import com.path.android.jobqueue.network.NetworkUtil;
import com.path.android.jobqueue.nonPersistentQueue.NonPersistentPriorityQueue;
import com.path.android.jobqueue.persistentQueue.sqlite.SqliteJobQueue;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * a JobManager that supports;
 * -> Persistent / Non Persistent Jobs
 * -> Job Priority
 * -> Running Jobs in Parallel
 * -> Stats like waiting Job Count
 */
public class JobManager implements NetworkEventProvider.Listener {
    public static final long NS_PER_MS = 1000000;
    public static final long NOT_RUNNING_SESSION_ID = Long.MIN_VALUE;
    public static final long NOT_DELAYED_JOB_DELAY = Long.MIN_VALUE;
    private AtomicInteger runningConsumerCount = new AtomicInteger(0);
    private final long sessionId;
    private final Executor executor;
    private int maxConsumerCount;
    private JobQueue persistentJobQueue;
    private JobQueue nonPersistentJobQueue;
    private boolean running;
    private NetworkUtil networkUtil;
    private final Context appContext;
    private DependencyInjector dependencyInjector;

    /**
     * Default constructor that will create a JobManager with 1 {@link SqliteJobQueue} and 1 {@link NonPersistentPriorityQueue}
     * @param context
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
        this(context, createDefaultConfiguration().withId(id));
    }

    /**
     *
     * @param context used to acquire ApplicationContext
     * @param config
     */
    public JobManager(Context context, Configuration config) {
        appContext = context.getApplicationContext();
        maxConsumerCount = config.getMaxConsumerCount();
        running = true;
        sessionId = System.nanoTime();
        //by providing an array blocking queue w/ maxConsumerCount size, we let it queue new items while some are about
        //to die.
        executor = new ThreadPoolExecutor(0, maxConsumerCount, config.getThreadKeepAlive()
                , TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(maxConsumerCount));
        this.persistentJobQueue = config.getQueueFactory().createPersistentQueue(context, sessionId, config.getId());
        this.nonPersistentJobQueue = config.getQueueFactory().createNonPersistent(context, sessionId, config.getId());
        networkUtil = config.getNetworkUtil();
        dependencyInjector = config.getDependencyInjector();
        if(networkUtil instanceof NetworkEventProvider) {
            ((NetworkEventProvider) networkUtil).setListener(this);
        }
        start();
    }

    public static Configuration createDefaultConfiguration() {
        return new Configuration()
                .withDefaultQueueFactory()
                .withDefaultNetworkUtil();
    }

    /**
     * Sets the max # of consumers. Existing consumers will NOT be killed until queue is empty.
     * @param maxConsumerCount
     */
    public void setMaxConsumerCount(int maxConsumerCount) {
        this.maxConsumerCount = maxConsumerCount;
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
        if (runningConsumerCount.get() == 0 && count() > 0) {
            addConsumer();
        }
    }

    /**
     * returns the # of jobs that are waiting to be executed.
     * This might be a good place to decide whether you should wake your app up on boot etc. to complete pending jobs.
     * @return
     */
    public int count() {
        return nonPersistentJobQueue.count() + persistentJobQueue.count();
    }

    /**
     * Adds a job with given priority and returns the JobId.
     * @param priority Higher runs first
     * @param baseJob The actual job to run
     * @return
     */
    public long addJob(int priority, BaseJob baseJob) {
        return addJob(priority, 0, baseJob);
    }

    /**
     * Adds a job with given priority and returns the JobId.
     * @param priority Higher runs first
     * @param delay number of milliseconds that this job should be delayed
     * @param baseJob The actual job to run
     * @return
     */
    public long addJob(int priority, long delay, BaseJob baseJob) {
        JobHolder jobHolder = new JobHolder(priority, baseJob, delay > 0 ? System.nanoTime() + delay * NS_PER_MS : NOT_DELAYED_JOB_DELAY, NOT_RUNNING_SESSION_ID);
        long id;
        if (baseJob.shouldPersist()) {
            synchronized (persistentJobQueue) {
                id = persistentJobQueue.insert(jobHolder);
            }
        } else {
            synchronized (nonPersistentJobQueue) {
                id = nonPersistentJobQueue.insert(jobHolder);
            }
        }
        JqLog.d("added job %d", id);
        if(dependencyInjector != null) {
            //inject members b4 calling onAdded
            dependencyInjector.inject(baseJob);
        }
        jobHolder.getBaseJob().onAdded();
        if (runningConsumerCount.get() < maxConsumerCount) {
            addConsumer();
        }
        return id;
    }

    private void ensureConsumerWhenNeeded(Boolean hasNetwork) {
        if(hasNetwork == null) {
            //if network util can inform us when network is recovered, we we'll check only next job that does not
            //require network. if it does not know how to inform us, we have to keep a busy loop.
            hasNetwork = networkUtil instanceof NetworkEventProvider ? hasNetwork() : true;
        }
        //this method is called when there are jobs but job consumer was not given any
        //this may happen in a race condition or when the latest job is a delayed job
        Long nextRunNs;
        synchronized (nonPersistentJobQueue) {
            nextRunNs = nonPersistentJobQueue.getNextJobDelayUntilNs(hasNetwork);
        }
        if(nextRunNs != null && nextRunNs <= System.nanoTime()) {
            addConsumer();
            return;
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
            if(nextRunNs <= System.nanoTime()) {
                addConsumer();
            } else {
                ensureConsumerOnTime(nextRunNs - System.nanoTime());
            }
        }
    }

    private void ensureConsumerOnTime(long waitNs) {
        long delay = (long)Math.ceil((double)(waitNs) / NS_PER_MS);
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                if(runningConsumerCount.get() == 0) {
                    addConsumer();
                }
            }
        }, delay);
    }

    private void addConsumer() {
        try {
            executor.execute(new JobConsumer());
            runningConsumerCount.incrementAndGet();
        } catch (Throwable t) {
            JqLog.e(t, "error while adding consumer for JobManager");
        }
    }

    private boolean hasNetwork() {
        return networkUtil == null ? true : networkUtil.isConnected(appContext);
    }

    private JobHolder getNextJob() {
        return getNextJob(false);
    }

    private JobHolder getNextJob(boolean nonPersistentOnly) {
        boolean haveNetwork = hasNetwork();
        JobHolder jobHolder;
        synchronized (nonPersistentJobQueue) {
            jobHolder = nonPersistentJobQueue.nextJobAndIncRunCount(haveNetwork);
        }
        if (jobHolder == null && nonPersistentOnly == false) {
            //go to disk, there aren't any non-persistent jobs
            synchronized (persistentJobQueue) {
                jobHolder = persistentJobQueue.nextJobAndIncRunCount(haveNetwork);
            }
        }
        if(jobHolder != null && dependencyInjector != null) {
            dependencyInjector.inject(jobHolder.getBaseJob());
        }
        return jobHolder;
    }

    private synchronized void removeJob(JobHolder jobHolder) {
        if (jobHolder.getBaseJob().shouldPersist()) {
            synchronized (persistentJobQueue) {
                persistentJobQueue.remove(jobHolder);
            }
        } else {
            synchronized (nonPersistentJobQueue) {
                nonPersistentJobQueue.remove(jobHolder);
            }
        }
    }

    public void clear() {
        synchronized (nonPersistentJobQueue) {
            nonPersistentJobQueue.clear();
        }
        synchronized (persistentJobQueue) {
            persistentJobQueue.clear();
        }
    }

    /**
     * if {@link NetworkUtil} implements {@link NetworkEventProvider}, this method is called when network is recovered
     * @param isConnected
     */
    @Override
    public void onNetworkChange(boolean isConnected) {
        ensureConsumerWhenNeeded(isConnected);
    }

    private class JobConsumer implements Runnable {
        public JobConsumer() {

        }

        @Override
        public void run() {
            JobHolder nextJob;
            try {
                do {
                    nextJob = running ? getNextJob() : null;
                    if (nextJob != null) {
                        if (nextJob.safeRun(nextJob.getRunCount())) {
                            removeJob(nextJob);
                        } else if (nextJob.getBaseJob().shouldPersist()) {
                            synchronized (persistentJobQueue) {
                                persistentJobQueue.insertOrReplace(nextJob);
                            }
                        } else {
                            synchronized (nonPersistentJobQueue) {
                                nonPersistentJobQueue.insertOrReplace(nextJob);
                            }
                        }
                    }
                } while (nextJob != null);
            } finally {
                if (runningConsumerCount.decrementAndGet() == 0 && running) {
                    //check count for race conditions
                    ensureConsumerWhenNeeded(null);
                }
            }
        }
    }


    /**
     * Default implementation of QueueFactory that creates one {@link SqliteJobQueue} and one {@link NonPersistentPriorityQueue}
     */
    public static class DefaultQueueFactory implements QueueFactory {
        @Override
        public JobQueue createPersistentQueue(Context context, Long sessionId, String id) {
            return new SqliteJobQueue(context, sessionId, id);
        }
        @Override
        public JobQueue createNonPersistent(Context context, Long sessionId, String id) {
            return new NonPersistentPriorityQueue(sessionId, id);
        }
    }

    /**
     * Interface to supply custom {@link JobQueue}s for JobManager
     */
    public static interface QueueFactory {
        public JobQueue createPersistentQueue(Context context, Long sessionId, String id);
        public JobQueue createNonPersistent(Context context, Long sessionId, String id);
    }

}
