package com.path.android.jobqueue;

import android.content.Context;
import android.util.Log;
import com.path.android.jobqueue.persistentQueue.sqlite.SqliteJobDb;
import com.path.android.jobqueue.log.JqLog;

import java.util.Comparator;
import java.util.Date;
import java.util.PriorityQueue;
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
    private long nonPersistentJobIdGenerator = Integer.MIN_VALUE;

    private final long sessionId;
    private PriorityQueue<JobHolder> nonPersistentJobs;

    private final Executor executor;
    private int maxConsumerCount = DEFAULT_MAX_EXECUTOR_COUNT;
    private com.path.android.jobqueue.persistentQueue.JobDb jobDb;
    private boolean running;

    public JobManager(Context context, String id) {
        running = true;
        sessionId = System.nanoTime();
        nonPersistentJobs = new PriorityQueue<JobHolder>(5, jobComparator);
        executor = new ThreadPoolExecutor(0, maxConsumerCount, 15, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(true));
        jobDb = new SqliteJobDb(context, sessionId, id);
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
        return nonPersistentJobs.size() + jobDb.count();
    }

    public long addJob(int priority, BaseJob baseJob) {
        JobHolder jobHolder = new JobHolder(null, priority, 0, null, new Date(), Long.MIN_VALUE);
        jobHolder.setBaseJob(baseJob);
        long id;
        if(baseJob.shouldPersist()) {
            id = jobDb.insert(jobHolder);
        } else {
            id = nonPersistentJobIdGenerator ++;
            nonPersistentJobs.add(jobHolder);
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
        JobHolder jobHolder = nonPersistentJobs.poll();
        if(jobHolder != null) {
            jobHolder.setRunningSessionId(sessionId);
        }
        if(jobHolder == null && nonPersistentOnly == false) {
            //go to disk, there aren't any non-persistent jobs
            jobHolder = jobDb.nextJob();
            if(jobHolder != null) {
                jobHolder.setRunningSessionId(sessionId);
                jobDb.insertOrReplace(jobHolder);
            }
        }
        return jobHolder;
    }

    public synchronized void removeJob(JobHolder jobHolder) {
        if(jobHolder.getBaseJob().shouldPersist()) {
            jobDb.remove(jobHolder);
        } else {
            nonPersistentJobs.remove(jobHolder);
        }
    }

    private final Comparator<JobHolder> jobComparator = new Comparator<JobHolder>() {
        @Override
        public int compare(JobHolder holder1, JobHolder holder2) {
            int cmp = holder1.getPriority().compareTo(holder2.getPriority());
            if(cmp == 0) {
                return holder1.getCreated().compareTo(holder2.getCreated());
            }
            return cmp;
        }
    };

    private class JobConsumer implements Runnable {
        public JobConsumer() {
            runningConsumerCount.incrementAndGet();
        }
        @Override
        public void run() {
            try {
                JobHolder nextJob;
                do {
                    nextJob = running ? getNextJob() : null;
                    if(nextJob != null) {
                        if(nextJob.safeRun(JobManager.this)) {
                            removeJob(nextJob);
                        }
                    }
                } while (nextJob != null);
            } finally {
                runningConsumerCount.decrementAndGet();
            }
        }
    }


}
