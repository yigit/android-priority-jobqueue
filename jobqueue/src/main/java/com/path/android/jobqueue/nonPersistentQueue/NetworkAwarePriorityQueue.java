package com.path.android.jobqueue.nonPersistentQueue;

import com.path.android.jobqueue.JobHolder;

import java.util.Collection;
import java.util.Comparator;
import java.util.Queue;

/**
 * A {@link MergedQueue} class that can separate jobs based on their network requirement
 */
public class NetworkAwarePriorityQueue extends MergedQueue {

    /**
     * create a network aware priority queue with given initial capacity * 2 and comparator
     * @param initialCapacity
     * @param comparator
     */
    public NetworkAwarePriorityQueue(int initialCapacity, Comparator<JobHolder> comparator) {
        super(initialCapacity, comparator, new TimeAwareComparator(comparator));
    }

    /**
     * {@link java.util.Queue#peek()} implementation with network requirement filter
     * @param canUseNetwork if {@code true}, does not check network requirement if {@code false}, returns only from
     *                      no network queue
     * @return
     */
    public JobHolder peek(boolean canUseNetwork, Collection<String> excludeGroupIds) {
        if(canUseNetwork) {
            return super.peek(excludeGroupIds);
        } else {
            return super.peekFromQueue(SetId.S1, excludeGroupIds);
        }
    }

    /**
     * {@link java.util.Queue#poll()} implementation with network requirement filter
     * @param canUseNetwork if {@code true}, does not check network requirement if {@code false}, returns only from
     *                      no network queue
     * @return
     */
    public JobHolder poll(boolean canUseNetwork, Collection<String> excludeGroupIds) {
        if(canUseNetwork) {
            return super.peek(excludeGroupIds);
        } else {
            return super.peekFromQueue(SetId.S1, excludeGroupIds);
        }
    }

    @Override
    protected SetId decideQueue(JobHolder jobHolder) {
        return jobHolder.requiresNetwork() ? SetId.S0 : SetId.S1;
    }

    /**
     * create a {@link TimeAwarePriorityQueue}
     * @param ignoredQueueId
     * @param initialCapacity
     * @param comparator
     * @return
     */
    @Override
    protected JobSet createQueue(SetId ignoredQueueId, int initialCapacity, Comparator<JobHolder> comparator) {
        return new TimeAwarePriorityQueue(initialCapacity, comparator);
    }


    public CountWithGroupIdsResult countReadyJobs(boolean hasNetwork, Collection<String> excludeGroups) {
        long now = System.nanoTime();
        if(hasNetwork) {
            return super.countReadyJobs(SetId.S0, now, excludeGroups).mergeWith(super.countReadyJobs(SetId.S1, now, excludeGroups));
        } else {
            return super.countReadyJobs(SetId.S1, now, excludeGroups);
        }
    }

    @Override
    public CountWithGroupIdsResult countReadyJobs(long now, Collection<String> excludeGroups) {
        throw new UnsupportedOperationException("cannot call network aware priority queue count w/o providing network status");
    }

    @Override
    public CountWithGroupIdsResult countReadyJobs(Collection<String> excludeGroups) {
        throw new UnsupportedOperationException("cannot call network aware priority queue count w/o providing network status");
    }
}
