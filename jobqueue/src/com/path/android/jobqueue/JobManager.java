package com.path.android.jobqueue;

import android.content.Context;
import com.path.android.jobqueue.log.JqLog;
import com.path.android.jobqueue.nonPersistentQueue.NonPersistentPriorityQueue;
import com.path.android.jobqueue.persistentQueue.sqlite.SqliteJobQueue;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * a JobManager that supports;
 * -> Persistent / Non Persistent Jobs
 * -> Job Priority
 * -> Running Jobs in Parallel
 * -> Stats like waiting Job Count
 */
public class JobManager {
    private static final long NS_PER_MS= 1000000;
    private static final int DEFAULT_MAX_EXECUTOR_COUNT = 6;
    public static final long NOT_RUNNING_SESSION_ID = Long.MIN_VALUE;
    private AtomicInteger runningConsumerCount = new AtomicInteger(0);
    private final long sessionId;
    private final Executor executor;
    private int maxConsumerCount = DEFAULT_MAX_EXECUTOR_COUNT;
    private JobQueue persistentJobQueue;
    private JobQueue nonPersistentJobQueue;
    private boolean running;

    /**
     * Default constructor that will create a JobManager with 1 {@link SqliteJobQueue} and 1 {@link NonPersistentPriorityQueue}
     * @param context
     */
    public JobManager(Context context) {
        this(context, "default");
    }


    /**
     * Default constructor that will create a JobManager with 1 {@link SqliteJobQueue} and 1 {@link NonPersistentPriorityQueue}
     * @param context application context
     * @param id an id that is unique to this JobManager
     */
    public JobManager(Context context, String id) {
        this(context, id, new DefaultQueueFactory());
    }

    /**
     * @param context application context
     * @param id an id that is unique to this JobManager
     * @param queueFactory custom queue factory that can provide any implementation of {@link JobQueue}
     */
    public JobManager(Context context, String id, QueueFactory queueFactory) {
        running = true;
        sessionId = System.nanoTime();
        executor = new ThreadPoolExecutor(0, maxConsumerCount, 15, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(true));
        this.persistentJobQueue = queueFactory.createPersistentQueue(context, sessionId, id);
        this.nonPersistentJobQueue = queueFactory.createNonPersistent(context, sessionId, id);
        start();
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
        JobHolder jobHolder = new JobHolder(priority, baseJob, delay > 0 ? System.nanoTime() + delay * NS_PER_MS : Long.MIN_VALUE, NOT_RUNNING_SESSION_ID);
        long id;
        if (baseJob.shouldPersist()) {
            id = persistentJobQueue.insert(jobHolder);
        } else {
            id = nonPersistentJobQueue.insert(jobHolder);
        }
        jobHolder.getBaseJob().onAdded();
        if (runningConsumerCount.get() == 0) {
            addConsumer();
        }
        return id;
    }

    private void ensureConsumerWhenNeeded() {
        //this method is called when there are jobs but job consumer was not given any
        //this may happen in a race condition or when the latest job is a delayed job
        Long nextRunNs = nonPersistentJobQueue.getNextJobDelayUntilNs();
        if(nextRunNs != null && nextRunNs <= System.nanoTime()) {
            addConsumer();
            return;
        }
        Long persistedJobRunNs = persistentJobQueue.getNextJobDelayUntilNs();
        if(persistedJobRunNs != null) {
            if(nextRunNs == null) {
                nextRunNs = persistedJobRunNs;
            } else if(persistedJobRunNs < nextRunNs) {
                nextRunNs = persistedJobRunNs;
            }
        }
        if(nextRunNs != null) {
            long waitNs = nextRunNs - System.nanoTime();
            if(waitNs <= 0) {
                addConsumer();
            } else {
                ensureConsumerOnTime(waitNs);
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

    private JobHolder getNextJob() {
        return getNextJob(false);
    }

    private synchronized JobHolder getNextJob(boolean nonPersistentOnly) {
        JobHolder jobHolder = nonPersistentJobQueue.nextJobAndIncRunCount();
        if (jobHolder == null && nonPersistentOnly == false) {
            //go to disk, there aren't any non-persistent jobs
            jobHolder = persistentJobQueue.nextJobAndIncRunCount();
        }
        return jobHolder;
    }

    private synchronized void removeJob(JobHolder jobHolder) {
        if (jobHolder.getBaseJob().shouldPersist()) {
            persistentJobQueue.remove(jobHolder);
        } else {
            nonPersistentJobQueue.remove(jobHolder);
        }
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
                            persistentJobQueue.insertOrReplace(nextJob);
                        } else {
                            nonPersistentJobQueue.insertOrReplace(nextJob);
                        }
                    }
                } while (nextJob != null);
            } finally {
                if (runningConsumerCount.decrementAndGet() == 0 && running) {
                    //check count for race conditions
                    ensureConsumerWhenNeeded();
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
