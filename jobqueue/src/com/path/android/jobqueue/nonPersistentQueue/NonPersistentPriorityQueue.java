package com.path.android.jobqueue.nonPersistentQueue;

import com.path.android.jobqueue.JobHolder;
import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.JobQueue;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

public class NonPersistentPriorityQueue implements JobQueue {
    private long nonPersistentJobIdGenerator = Integer.MIN_VALUE;
    private PriorityQueue<JobHolder> jobs;
    private Map<Long, JobHolder> runningJobs;
    private final String id;
    private final long sessionId;

    public NonPersistentPriorityQueue(long sessionId, String id) {
        this.id = id;
        this.sessionId = sessionId;
        jobs = new PriorityQueue<JobHolder>(5, jobComparator);
        runningJobs = new HashMap<Long, JobHolder>();
    }

    @Override
    public synchronized long insert(JobHolder jobHolder) {
        nonPersistentJobIdGenerator++;
        jobHolder.setId(nonPersistentJobIdGenerator);
        jobs.add(jobHolder);
        return jobHolder.getId();
    }

    @Override
    public long insertOrReplace(JobHolder jobHolder) {
        remove(jobHolder);
        jobHolder.setRunningSessionId(JobManager.NOT_RUNNING_SESSION_ID);
        jobs.add(jobHolder);
        return jobHolder.getId();
    }

    @Override
    public void remove(JobHolder jobHolder) {
        jobs.remove(jobHolder);
        if (jobHolder.getId() != null) {
            runningJobs.remove(jobHolder.getId());
        }
    }

    @Override
    public long count() {
        return jobs.size();
    }

    @Override
    public JobHolder nextJobAndIncRunCount() {
        JobHolder jobHolder = jobs.poll();
        if (jobHolder != null) {
            jobHolder.setRunningSessionId(sessionId);
            jobHolder.setRunCount(jobHolder.getRunCount() + 1);
            //add it back to the queue. it will go the end
            runningJobs.put(jobHolder.getId(), jobHolder);
        }
        return jobHolder;
    }

    public final Comparator<JobHolder> jobComparator = new Comparator<JobHolder>() {
        @Override
        public int compare(JobHolder holder1, JobHolder holder2) {
            //high priority first
            int cmp = compareInt(holder1.getPriority(), holder2.getPriority());
            if (cmp == 0) {
                //if priorities are equal, less running job first
                cmp = -compareInt(holder1.getRunCount(), holder2.getRunCount());
            }
            if (cmp == 0) {
                //if run counts are also equal, older job first
                cmp = -compareLong(holder1.getCreatedNs(), holder2.getCreatedNs());
            }
            if (cmp == 0) {
                //if jobs were created at the same time, smaller id first
                cmp = -compareLong(holder1.getId(), holder2.getId());
            }
            return cmp;
        }
    };

    private static int compareInt(int i1, int i2) {
        if (i1 > i2) {
            return -1;
        }
        if (i2 > i1) {
            return 1;
        }
        return 0;
    }

    private static int compareLong(long l1, long l2) {
        if (l1 > l2) {
            return -1;
        }
        if (l2 > l1) {
            return 1;
        }
        return 0;
    }


}
