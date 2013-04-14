package com.path.android.jobqueue.nonPersistentQueue;

import com.path.android.jobqueue.JobHolder;
import com.path.android.jobqueue.JobQueue;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

public class NonPersistentPriorityQueue implements JobQueue {
    private long nonPersistentJobIdGenerator = Integer.MIN_VALUE;
    private PriorityQueue<JobHolder> jobs;
    private final String id;
    private final long sessionId;
    public NonPersistentPriorityQueue(long sessionId, String id) {
        this.id = id;
        this.sessionId = sessionId;
        jobs = new PriorityQueue<JobHolder>(5, jobComparator);
    }
    @Override
    public synchronized long insert(JobHolder jobHolder) {
        nonPersistentJobIdGenerator ++;
        jobHolder.setId(nonPersistentJobIdGenerator);
        jobs.add(jobHolder);
        return jobHolder.getId();
    }

    @Override
    public long insertOrReplace(JobHolder jobHolder) {
        jobs.remove(jobHolder);
        jobs.add(jobHolder);
        return jobHolder.getId();
    }

    @Override
    public void remove(JobHolder jobHolder) {
        jobs.remove(jobHolder);
    }

    @Override
    public long count() {
        return jobs.size();
    }

    @Override
    public JobHolder nextJobAndIncRunCount() {
        JobHolder jobHolder = jobs.poll();
        if(jobHolder != null) {
            jobHolder.setRunningSessionId(sessionId);
            jobHolder.setRunCount(jobHolder.getRunCount() + 1);
        }
        return jobHolder;
    }

    public static final Comparator<JobHolder> jobComparator = new Comparator<JobHolder>() {
        @Override
        public int compare(JobHolder holder1, JobHolder holder2) {
            int cmp = holder1.getPriority().compareTo(holder2.getPriority());
            if(cmp == 0) {
                return holder1.getCreated().compareTo(holder2.getCreated());
            }
            return cmp;
        }
    };
}
