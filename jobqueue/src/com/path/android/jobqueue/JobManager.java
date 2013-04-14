package com.path.android.jobqueue;

import android.content.Context;
import android.util.Log;
import com.path.android.jobqueue.nonPersistentQueue.NonPersistentPriorityQueue;
import com.path.android.jobqueue.persistentQueue.sqlite.SqliteJobQueue;
import com.path.android.jobqueue.log.JqLog;

import java.util.Date;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Job Manager that keeps non-persistent jobs in a priority queue and persistent jobs in db.
 * User: yigit
 * Date: 4/9/13
 * Time: 2:15 AM
 *
 * TODO: after attaching a session id,if job fails and goes back to disk, we need to clean session id
 */
public class JobManager {

    private static final int DEFAULT_MAX_EXECUTOR_COUNT = 6;
    public static final long NOT_RUNNING_SESSION_ID = Long.MIN_VALUE;
    private AtomicInteger runningConsumerCount = new AtomicInteger(0);

    private final long sessionId;


    private final Executor executor;
    private int maxConsumerCount = DEFAULT_MAX_EXECUTOR_COUNT;
    private JobQueue persistentJobQueue;
    private JobQueue nonPersistentJobQueue;
    private boolean running;

    public JobManager(Context context, String id) {
        running = true;
        sessionId = System.nanoTime();

        executor = new ThreadPoolExecutor(0, maxConsumerCount, 15, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(true));
        persistentJobQueue = new SqliteJobQueue(context, sessionId, id);
        nonPersistentJobQueue = new NonPersistentPriorityQueue(sessionId, id);
        JqLog.getConfig().setLoggingLevel(Log.VERBOSE);
        start();
    }

    public JobManager(Context context) {
        this(context, "default");
    }

    public void stop() {
        running = false;
    }

    public void start() {
        running = true;
        addConsumer();
    }

    public JobHolder getNextJob() {
        return getNextJob(false);
    }

    public long count() {
        return nonPersistentJobQueue.count() + persistentJobQueue.count();
    }

    public long addJob(int priority, BaseJob baseJob) {
        JobHolder jobHolder = new JobHolder(null, priority, 0, null, new Date(), Long.MIN_VALUE);
        jobHolder.setBaseJob(baseJob);
        long id;
        if(baseJob.shouldPersist()) {
            id = persistentJobQueue.insert(jobHolder);
        } else {
            id = nonPersistentJobQueue.insert(jobHolder);
        }
        if(runningConsumerCount.get() == 0) {
            addConsumer();
        }
        return id;
    }

    private void addConsumer() {
        executor.execute(new JobConsumer());
    }

    public synchronized JobHolder getNextJob(boolean nonPersistentOnly) {
        JobHolder jobHolder = nonPersistentJobQueue.nextJobAndIncRunCount();
        if(jobHolder == null && nonPersistentOnly == false) {
            //go to disk, there aren't any non-persistent jobs
            jobHolder = persistentJobQueue.nextJobAndIncRunCount();
            if(jobHolder != null) {
                jobHolder.setRunningSessionId(sessionId);
                persistentJobQueue.insertOrReplace(jobHolder);
            }
        }
        return jobHolder;
    }

    public synchronized void removeJob(JobHolder jobHolder) {
        if(jobHolder.getBaseJob().shouldPersist()) {
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
            do {
                nextJob = running ? getNextJob() : null;
                if(nextJob != null) {
                    runningConsumerCount.incrementAndGet();
                    if(nextJob.safeRun(nextJob.getRunCount())) {
                        removeJob(nextJob);
                    } else if(nextJob.getBaseJob().shouldPersist()) {
                        //delete session id and add it back to disk
                        nextJob.setRunningSessionId(Long.MIN_VALUE);
                        persistentJobQueue.insertOrReplace(nextJob);
                    } else {
                        nextJob.setRunningSessionId(Long.MIN_VALUE);
                        nonPersistentJobQueue.insertOrReplace(nextJob);
                    }
                    runningConsumerCount.decrementAndGet();
                }
            } while (nextJob != null);
        }
    }


}
