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
        jobHolder.setRunningSessionId(Long.MIN_VALUE);
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
            if(jobHolder.getRunningSessionId() == sessionId) {
                return null;//it is running right now
            }
            jobHolder.setRunningSessionId(sessionId);
            jobHolder.setRunCount(jobHolder.getRunCount() + 1);
            //add it back to the queue. it will go the end
            jobs.add(jobHolder);
        }
        return jobHolder;
    }

    public final Comparator<JobHolder> jobComparator = new Comparator<JobHolder>() {
        @Override
        public int compare(JobHolder holder1, JobHolder holder2) {
            //if a job is running, that goes to the bottom of the Q
            if(holder1.getRunningSessionId() == sessionId && holder2.getRunningSessionId() != sessionId) {
                return 1;
            }
            if(holder1.getRunningSessionId() != sessionId && holder2.getRunningSessionId() == sessionId) {
                return -1;
            }

            //high priority first
            int cmp = compareInt(holder1.getPriority(), holder2.getPriority());
            if(cmp == 0) {
                //if priorities are equal, less running job first
                cmp = - compareInt(holder1.getRunCount(), holder2.getRunCount());
            }
            if(cmp == 0) {
                //if run counts are also equal, older job first
                cmp = - compareLong(holder1.getCreatedNs(), holder2.getCreatedNs());
            }
            if(cmp == 0) {
                //if jobs were created at the same time, smaller id first
                cmp = - compareLong(holder1.getId(), holder2.getId());
            }
            return cmp;
        }
    };

    private static int compareInt(int i1, int i2) {
        if(i1 > i2) {
            return -1;
        }
        if(i2 > i1) {
            return 1;
        }
        return 0;
    }

    private static int compareLong(long l1, long l2) {
        if(l1 > l2) {
            return -1;
        }
        if(l2 > l1) {
            return 1;
        }
        return 0;
    }


}
