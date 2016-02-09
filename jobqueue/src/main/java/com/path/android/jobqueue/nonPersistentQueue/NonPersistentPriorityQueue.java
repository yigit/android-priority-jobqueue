package com.path.android.jobqueue.nonPersistentQueue;

import com.birbit.android.jobqueue.Constraint;
import com.path.android.jobqueue.JobHolder;
import com.path.android.jobqueue.JobManager;
import com.path.android.jobqueue.JobQueue;
import com.path.android.jobqueue.TagConstraint;
import com.path.android.jobqueue.timer.Timer;

import java.util.Collection;
import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Re-implement to support full constraints
 */
public class NonPersistentPriorityQueue implements JobQueue {
    //TODO implement a more efficient priority queue where we can mark jobs as removed but don't remove for real
    private NetworkAwarePriorityQueue jobs;
    private final long sessionId;
    private final Timer timer;
    private final AtomicLong insertionOrderCounter = new AtomicLong(0);

    public NonPersistentPriorityQueue(long sessionId,
            @SuppressWarnings("UnusedParameters") String id,
            @SuppressWarnings("UnusedParameters") boolean inTestMode,
            Timer timer) {
        this.sessionId = sessionId;
        this.timer = timer;
        jobs = new NetworkAwarePriorityQueue(5, jobComparator, timer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized boolean insert(JobHolder jobHolder) {
        jobHolder.setInsertionOrder(insertionOrderCounter.incrementAndGet());
        return jobs.offer(jobHolder);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean insertOrReplace(JobHolder jobHolder) {
        if (jobHolder.getInsertionOrder() == null) {
            return insert(jobHolder);
        }
        remove(jobHolder);
        jobHolder.setRunningSessionId(JobManager.NOT_RUNNING_SESSION_ID);
        return jobs.offer(jobHolder);
    }

    @Override
    public void substitute(JobHolder newJob, JobHolder oldJob) {
        remove(oldJob);
        insert(newJob);
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
    public int countReadyJobs(Constraint constraint) {
        return jobs.countReadyJobs(!constraint.shouldNotRequireNetwork(),
                constraint.getExcludeGroups()).getCount();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JobHolder nextJobAndIncRunCount(Constraint constraint) {
        JobHolder jobHolder = jobs.peek(!constraint.shouldNotRequireNetwork(),
                constraint.getExcludeGroups());

        if (jobHolder != null) {
            //check if job can run
            if(jobHolder.getDelayUntilNs() > timer.nanoTime()) {
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
    public Long getNextJobDelayUntilNs(Constraint constraint) {
        JobHolder next = jobs.peek(!constraint.shouldNotRequireNetwork(),
                constraint.getExcludeGroups());
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
    public JobHolder findJobById(String id) {
        return jobs.findById(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<JobHolder> findJobs(Constraint constraint) {
        //we ignore excludeCancelled because we remove them as soon as they are cancelled
        return jobs.findByTags(constraint.getTagConstraint(),
                constraint.getExcludeJobIds(), constraint.getTags());
    }

    @Override
    public void onJobCancelled(JobHolder holder) {
        // we can remove instantly.
        remove(holder);
    }

    public final Comparator<JobHolder> jobComparator = new Comparator<JobHolder>() {
        @Override
        public int compare(JobHolder holder1, JobHolder holder2) {
            if (holder1.getJob().getId().equals(holder2.getJob().getId())) {
                return 0;
            }
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
            return -compareLong(holder1.getInsertionOrder(), holder2.getInsertionOrder());
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
