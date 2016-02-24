package com.path.android.jobqueue.nonPersistentQueue;

import com.path.android.jobqueue.JobHolder;
import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.JobQueue;
import com.path.android.jobqueue.TagConstraint;

import java.util.*;

public class NonPersistentPriorityQueue implements JobQueue {
    private long nonPersistentJobIdGenerator = Integer.MIN_VALUE;
    //TODO implement a more efficient priority queue where we can mark jobs as removed but don't remove for real
    private NetworkAwarePriorityQueue jobs;
    private final String id;
    private final long sessionId;

    public NonPersistentPriorityQueue(long sessionId, String id, boolean inTestMode) {
        this.id = id;
        this.sessionId = sessionId;
        jobs = new NetworkAwarePriorityQueue(5, jobComparator);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized long insert(JobHolder jobHolder) {
        nonPersistentJobIdGenerator++;
        jobHolder.setId(nonPersistentJobIdGenerator);
        jobs.offer(jobHolder);
        return jobHolder.getId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long insertOrReplace(JobHolder jobHolder) {
        remove(jobHolder);
        jobHolder.setRunningSessionId(JobManager.NOT_RUNNING_SESSION_ID);
        jobs.offer(jobHolder);
        return jobHolder.getId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void remove(JobHolder jobHolder) {
        jobs.remove(jobHolder);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int count() {
        return jobs.size();
    }

    @Override
    public int countReadyJobs(boolean hasNetwork, Collection<String> excludeGroups) {
        return jobs.countReadyJobs(hasNetwork, excludeGroups).getCount();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JobHolder nextJobAndIncRunCount(boolean hasNetwork, Collection<String> excludeGroups) {
        JobHolder jobHolder = jobs.peek(hasNetwork, excludeGroups);

        if (jobHolder != null) {
            //check if job can run
            if(jobHolder.getDelayUntilNs() > System.nanoTime()) {
                jobHolder = null;
            } else {
                jobHolder.setRunningSessionId(sessionId);
                jobHolder.setRunCount(jobHolder.getRunCount() + 1);
                jobs.remove(jobHolder);
            }
        }
        return jobHolder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long getNextJobDelayUntilNs(boolean hasNetwork, Collection<String> excludeGroups) {
        JobHolder next = jobs.peek(hasNetwork, excludeGroups);
        return next == null ? null : next.getDelayUntilNs();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        jobs.clear();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JobHolder findJobById(long id) {
        return jobs.findById(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<JobHolder> findJobsByTags(TagConstraint constraint, boolean excludeCancelled,
            Collection<Long> exclude, String... tags) {
        //we ignore excludeCancelled because we remove them as soon as they are cancelled
        return jobs.findByTags(constraint, exclude, tags);
    }

    @Override
    public Set<JobHolder> findAllJobs(boolean excludeCancelled, Collection<Long> exclude) {
        //we ignore excludeCancelled because we remove them as soon as they are cancelled
        return jobs.findAll(exclude);
    }

    @Override
    public void onJobCancelled(JobHolder holder) {
        // we can remove instantly.
        remove(holder);
    }

    public final Comparator<JobHolder> jobComparator = new Comparator<JobHolder>() {
        @Override
        public int compare(JobHolder holder1, JobHolder holder2) {
            //we should not check delay here. TimeAwarePriorityQueue does it for us.
            //high priority first
            int cmp = compareInt(holder1.getPriority(), holder2.getPriority());
            if(cmp != 0) {
                return cmp;
            }

            //if run counts are also equal, older job first
            cmp = -compareLong(holder1.getCreatedNs(), holder2.getCreatedNs());
            if(cmp != 0) {
                return cmp;
            }

            //if jobs were created at the same time, smaller id first
            return -compareLong(holder1.getId(), holder2.getId());
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
