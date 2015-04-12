package com.path.android.jobqueue.nonPersistentQueue;

import com.path.android.jobqueue.JobHolder;

import java.util.*;

/**
 * This is a {@link MergedQueue} class that can handle queue updates based on time.
 * It uses two queues, one for jobs that can run now and the other for jobs that should wait.
 * Upon retrieval, if it detects a job in delayed queue that can run now, it removes it from there, adds it to S0
 * and re-runs the operation. This is not very efficient but provides proper ordering for delayed jobs.
 */
public class TimeAwarePriorityQueue extends MergedQueue {

    /**
     * When retrieving jobs, considers current system nanotime to check if jobs are valid. if both jobs are valid
     * or both jobs are invalid, returns based on regular comparison
     * @param initialCapacity
     * @param comparator
     */
    public TimeAwarePriorityQueue(int initialCapacity, Comparator<JobHolder> comparator) {
        super(initialCapacity, comparator, new TimeAwareComparator(comparator));
    }

    @Override
    protected SetId decideQueue(JobHolder jobHolder) {
        return jobHolder.getDelayUntilNs() <= System.nanoTime() ? SetId.S0 : SetId.S1;
    }

    /**
     * create a {@link PriorityQueue} with given comparator
     * @param setId
     * @param initialCapacity
     * @param comparator
     * @return
     */
    @Override
    protected JobSet createQueue(SetId setId, int initialCapacity, Comparator<JobHolder> comparator) {
        if(setId == SetId.S0) {
            return new NonPersistentJobSet(comparator);
        } else {
            return new NonPersistentJobSet(new ConsistentTimedComparator(comparator));
        }
    }

    @Override
    public CountWithGroupIdsResult countReadyJobs(long now, Collection<String> excludeGroups) {
        return super.countReadyJobs(SetId.S0, excludeGroups).mergeWith(super.countReadyJobs(SetId.S1, now, excludeGroups));
    }

    @Override
    public CountWithGroupIdsResult countReadyJobs(Collection<String> excludeGroups) {
        throw new UnsupportedOperationException("cannot call time aware priority queue's count ready jobs w/o providing a time");
    }
}
